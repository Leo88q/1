#!/usr/bin/env python3
"""Собирает веб-ассеты игры из исходного арта (`art/`) в `packages/lab/public/art/`.

Что делает:
  * спрайты труб 512×512 PNG → 256×256 WebP (деталь в ячейке редко крупнее 100 px,
    а на телефоне — меньше; 256 хватает с запасом даже при devicePixelRatio 2);
  * добавляет «заглушку» (маска 1): половина прямой трубы — так же, как это делает
    макет поля в art/tools/mock_board.py, чтобы тупик читался трубой, а не обрубком;
  * фоны ярусов и экранов 768×1376 JPG → WebP шириной 720;
  * медальоны наград (пломбы за 1/2/3 звезды) → 256×256 WebP;
  * пишет `web.json` — контракт для рендера: какие спрайты у какого набора, фоны,
    цвет среды яруса и медальоны.

Запуск: python3 packages/lab/scripts/build-art.py     (нужен pillow)
Результат коммитится: игра запускается обычным `npm run dev`, без сборки ассетов.
"""
import json
import os

from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
LAB = os.path.dirname(HERE)
REPO = os.path.dirname(os.path.dirname(LAB))
ART = os.path.join(REPO, "art")
OUT = os.path.join(LAB, "public", "art")

TILE = 256
BACK_W = 720

# Ярусы: файлы фонов и материалы поля заданы здесь (это про картинки), а имена и
# идентификаторы берём из канона лора — иначе HUD веб-игры и канон расходятся
# (так однажды и вышло: арт называл пятый ярус «ЖИЛЫ МАССИВА», канон — «ПЛАВИЛЬНЯ»).
TIERS = [
    {"id": "plateau", "sets": ["zinc", "zinc2"], "flow": "#B6FF3C",
     "bg": ["tier-1-plateau.jpg", "tier-1-plateau-b.jpg"]},
    {"id": "mercury", "sets": ["chrome"], "flow": "#16E5D9",
     "bg": ["tier-2-mercury.jpg", "tier-2-mercury-b.jpg"]},
    {"id": "terminal", "sets": ["rust", "rust2"], "flow": "#FF7A1A",
     "bg": ["tier-3-terminal.jpg", "tier-3-terminal-b.jpg"]},
    {"id": "old-diode", "sets": ["cast"], "flow": "#FFB040",
     "bg": ["tier-4-old-diode.jpg", "tier-4-old-diode-b.jpg"]},
    {"id": "melter", "sets": ["glass"], "flow": "#B6FF3C",
     "bg": ["tier-5-veins.jpg", "tier-5-veins-b.jpg"]},
]

# Резервные имена, если канон не собран (клон без Node): игра остаётся играбельной.
FALLBACK_NAMES = {
    "plateau": "ПЛАТО ПЕРВОГО КОНТАКТА",
    "mercury": "РТУТНЫЙ ЯРУС",
    "terminal": "ТЕРМИНАЛЬНЫЙ ЯРУС",
    "old-diode": "СТАРЫЙ ДИОД",
    "melter": "ПЛАВИЛЬНЯ (ЯДРО)",
}


def canon_tier_names():
    """Имена ярусов из канона лора (`packages/engine/content/lore.json`)."""
    path = os.path.join(REPO, "packages", "engine", "content", "lore.json")
    try:
        with open(path, encoding="utf-8") as fh:
            tiers = json.load(fh)["tiers"]
    except (OSError, KeyError, ValueError) as err:
        print(f"канон лора не прочитан ({err}) — имена берём из резервного списка")
        return {}, False
    names = {}
    for tier in tiers:
        name = tier.get("name") or ""
        names[tier["id"]] = name.upper()
    print(f"имена ярусов: из канона ({len(names)} шт.)")
    return names, True

SCREENS = {"menu": "screen-menu.jpg", "victory": "screen-victory.jpg", "shop": "screen-shop.jpg"}
REWARDS = {"1": "reward-disc.png", "2": "reward-rings.png", "3": "reward-star.png"}


def save_webp(img, path, quality):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    img.save(path, "WEBP", quality=quality, method=6)
    return os.path.relpath(path, OUT)


def make_stub(straight):
    """Заглушка (маска 1): верхняя половина прямой — труба входит с севера и кончается в центре."""
    art = straight.copy()
    width, height = art.size
    alpha = art.getchannel("A")
    half = alpha.crop((0, 0, width, height // 2))
    alpha.paste(0, (0, height // 2, width, height))
    alpha.paste(half, (0, height // 2, width, height))
    art.putalpha(alpha)
    return art


def main():
    with open(os.path.join(ART, "tiles", "manifest.json"), encoding="utf-8") as fh:
        manifest = json.load(fh)

    pack = {"tile": TILE, "sets": {}, "round": {}, "tiers": [], "screens": {}, "rewards": {}}
    total = 0

    # 1. Спрайты деталей по наборам. `file` — путь внутри public/art.
    sets = sorted({meta["set"] for meta in manifest.values() if meta.get("mask")})
    for set_name in sets:
        pieces = {}
        for name, meta in manifest.items():
            if meta.get("set") != set_name or not meta.get("mask"):
                continue
            piece = name[len(set_name) + 1:]
            img = Image.open(os.path.join(ART, "tiles", f"{name}.png")).convert("RGBA")
            img = img.resize((TILE, TILE), Image.LANCZOS)
            rel = save_webp(img, os.path.join(OUT, "tiles", f"{name}.webp"), 85)
            total += os.path.getsize(os.path.join(OUT, rel))
            pieces[piece] = {"file": rel, "mask": meta["mask"]}
            if piece == "straight":
                stub = make_stub(img)
                rel_stub = save_webp(stub, os.path.join(OUT, "tiles", f"{set_name}-stub.webp"), 85)
                total += os.path.getsize(os.path.join(OUT, rel_stub))
                pieces["stub"] = {"file": rel_stub, "mask": 1}
        pack["sets"][set_name] = pieces
    print(f"наборов: {len(pack['sets'])} —", ", ".join(sorted(pack["sets"])))

    # 2. Круглые детали: приёмник (открытый конец) и колпак шины.
    for name in ("mouth", "cap"):
        img = Image.open(os.path.join(ART, "tiles", f"{name}.png")).convert("RGBA")
        img = img.resize((TILE, TILE), Image.LANCZOS)
        rel = save_webp(img, os.path.join(OUT, "tiles", f"{name}.webp"), 85)
        total += os.path.getsize(os.path.join(OUT, rel))
        pack["round"][name] = {"file": rel}
    print("круглые детали: mouth, cap")

    # 3. Фоны ярусов и экранов.
    names, from_canon = canon_tier_names()
    for tier in TIERS:
        bgs = []
        for name in tier["bg"]:
            img = Image.open(os.path.join(ART, "backgrounds", name)).convert("RGB")
            h = round(img.height * BACK_W / img.width)
            img = img.resize((BACK_W, h), Image.LANCZOS)
            rel = save_webp(img, os.path.join(OUT, "back", name.replace(".jpg", ".webp")), 76)
            total += os.path.getsize(os.path.join(OUT, rel))
            bgs.append(rel)
        name = names.get(tier["id"]) or FALLBACK_NAMES[tier["id"]]
        pack["tiers"].append({"id": tier["id"], "name": name, "sets": tier["sets"],
                              "bg": bgs, "flow": tier["flow"], "accent": tier["flow"]})
    for key, name in SCREENS.items():
        img = Image.open(os.path.join(ART, "backgrounds", name)).convert("RGB")
        h = round(img.height * BACK_W / img.width)
        img = img.resize((BACK_W, h), Image.LANCZOS)
        rel = save_webp(img, os.path.join(OUT, "screens", name.replace(".jpg", ".webp")), 74)
        total += os.path.getsize(os.path.join(OUT, rel))
        pack["screens"][key] = rel
    print("фоны:", len(pack["tiers"]), "ярусов +", len(pack["screens"]), "экрана")
    if not from_canon:
        print("внимание: имена ярусов — резервные, пересоберите контент: npm run content")
    elif names and set(names) != {t["id"] for t in TIERS}:
        print("внимание: состав ярусов в каноне и в арте разошёлся:", sorted(names))

    # 4. Пломбы за звёзды.
    for stars, name in REWARDS.items():
        img = Image.open(os.path.join(ART, "tiles", name)).convert("RGBA")
        img = img.resize((TILE, TILE), Image.LANCZOS)
        rel = save_webp(img, os.path.join(OUT, "rewards", name.replace(".png", ".webp")), 85)
        total += os.path.getsize(os.path.join(OUT, rel))
        pack["rewards"][stars] = rel
    print("пломбы:", ", ".join(sorted(pack["rewards"])))

    with open(os.path.join(OUT, "web.json"), "w", encoding="utf-8") as fh:
        json.dump(pack, fh, ensure_ascii=False, indent=2)
        fh.write("\n")
    print(f"web.json записан · вес ассетов: {total // 1024} КБ")


if __name__ == "__main__":
    main()
