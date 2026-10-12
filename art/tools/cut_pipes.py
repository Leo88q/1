#!/usr/bin/env python3
"""Нарезка спрайтов труб из листа art/pipes/*.jpg.

Лист `pieces-topdown.jpg` — четыре детали на чёрном фоне: прямая с фланцами,
отвод, тройник, крест. Скрипт вырезает каждую деталь, делает фон прозрачным,
приводит к квадрату 512×512 и прижимает к тем краям, где у детали порты, —
чтобы при укладке в сетку трубы соседних ячеек визуально стыковались.

Порты заданы явно (они же — контракт для художника) и проверяются по маске:
если деталь не доходит до нужного края, скрипт об этом скажет.

Запуск: python3 art/tools/cut_pipes.py      (нужен pillow)
"""
import json
import os
from PIL import Image, ImageFilter

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(ROOT, "tiles")
TILE = 512

ROUND_PIECES = {"mouth": "open-end-topdown.jpg", "cap": "cap-topdown.jpg"}

# Материал поля по ярусам: у каждого свой лист с той же раскладкой деталей.
# Это позволяет менять «металл» квартала вместе с фоном, не перерисовывая поле.
MATERIAL_SETS = {
    "base": "pieces-topdown.jpg",          # канон: чугун + цинк (промо и витрина)
    "zinc": "tier-1-zinc.jpg",             # Плато Первого Контакта
    "chrome": "tier-2-chrome.jpg",         # Ртутный ярус
    "rust": "tier-3-rust.jpg",             # Терминальный ярус
    "cast": "tier-4-cast.jpg",             # Старый Диод
    "glass": "tier-5-glass.jpg",           # Жилы массива
    # Вторые материалы тех же ярусов: то же поле, другой металл — уровни внутри
    # яруса не читаются клонами. Набор выбирается по варианту кадра (см. mock_board).
    "zinc2": "tier-1-zinc-b.jpg",          # Плато: цинк с патиной и потёками
    "rust2": "tier-3-rust-b.jpg",          # Терминальный: глубокая ржавчина, отслоения
}
# Раскладка деталей на каждом листе: верхний ряд — прямая, нижний — отвод/тройник/крест.
LAYOUT = {
    "straight": {"row": "top", "index": 0},
    "elbow": {"row": "bottom", "index": 0},
    "tee": {"row": "bottom", "index": 1},
    "cross": {"row": "bottom", "index": 2},
}
MASK_OF = {"straight": 5, "elbow": 3, "tee": 7, "cross": 15}
PORTS_OF = {"straight": [2, 8], "elbow": [2, 4], "tee": [1, 2, 8], "cross": [1, 2, 4, 8]}


def mask_of(img, threshold=22):
    """Чёрный фон → 0, деталь → 255."""
    return img.convert("L").point(lambda v: 255 if v > threshold else 0)


def side_profile(mask, axis):
    """Полосы, где есть деталь (по колонкам или строкам)."""
    w, h = mask.size
    small = mask.resize((w, 1) if axis == "col" else (1, h), Image.BOX)
    values = list(small.get_flattened_data())
    strips, start = [], None
    for i, v in enumerate(values):
        if v > 2 and start is None:
            start = i
        elif v <= 2 and start is not None:
            strips.append((start, i))
            start = None
    if start is not None:
        strips.append((start, len(values)))
    merged = []
    for s, e in strips:
        if merged and s - merged[-1][1] <= 6:
            merged[-1] = (merged[-1][0], e)
        else:
            merged.append((s, e))
    return [s for s in merged if s[1] - s[0] > 40]


def rotate_cw_steps(mask, steps):
    """Маска после steps поворотов по часовой — та же формула, что в движке."""
    m = mask & 0xF
    for _ in range(steps % 4):
        m = ((m << 1) | (m >> 3)) & 0xF
    return m


def pre_rotate(sprite, ports, engine):
    """Разворачивает спрайт так, чтобы его порты совпали с маской движка.

    Спрайт рисуется в одном положении, а движок хранит маску: например, отвод в
    движке — это север+восток (3), а на листе он лежит ветвями вправо-вниз (6).
    Доворачиваем картинку по часовой, пока маска не совпадёт, — и дальше игровое
    поле крутит спрайт так же, как крутит маску.
    """
    sprite_mask = 0
    for bit, want in ((1, 1), (2, 2), (4, 4), (8, 8)):
        if bit in ports:
            sprite_mask |= want
    for steps in range(4):
        if rotate_cw_steps(sprite_mask, steps) == engine:
            return sprite.rotate(-90 * steps, resample=Image.BICUBIC, expand=False), steps
    raise SystemExit(f"не смог совместить маску спрайта {sprite_mask} с движковой {engine}")


def touches(alpha, bit, edge=3):
    """Доля покрытия края: у порта деталь подходит к самому краю картинки."""
    w, h = alpha.size
    px = alpha.load()
    hits = 0
    span = w if bit in (1, 4) else h
    for i in range(span):
        if bit == 1:
            probe = [(i, d) for d in range(edge)]
        elif bit == 4:
            probe = [(i, h - 1 - d) for d in range(edge)]
        elif bit == 2:
            probe = [(w - 1 - d, i) for d in range(edge)]
        else:
            probe = [(d, i) for d in range(edge)]
        if any(px[x, y] > 25 for x, y in probe):
            hits += 1
    return hits / span


def fit_piece(img, box, ports=()):
    """Вырезает деталь, сохраняет пропорции и прижимает к краям её портов.

    Масштаб считается так, чтобы деталь точно заняла тайл по тем осям, где у неё
    есть порты (иначе труба не дотянется до соседней ячейки), а затем деталь
    прижимается к нужным сторонам: север → вверх, восток → вправо и так далее.
    """
    piece = img.crop(box)
    alpha = mask_of(piece, threshold=45)
    tight = alpha.getbbox()
    if tight:
        piece = piece.crop(tight)
        alpha = alpha.crop(tight)
    alpha = alpha.filter(ImageFilter.MaxFilter(3))
    w, h = piece.size

    north, east, south, west = (bit in ports for bit in (1, 2, 4, 8))
    # Оси с портами растягиваем ровно до тайла (труба обязана дойти до соседа),
    # остальные — по пропорции. Искажение при этом в пределах нескольких процентов.
    scale = TILE / max(w, h)
    nw = TILE if (west or east) else max(1, round(w * scale))
    nh = TILE if (north or south) else max(1, round(h * scale))
    piece = piece.resize((nw, nh), Image.LANCZOS)
    alpha = alpha.resize((nw, nh), Image.LANCZOS)

    x = 0 if west and not east else (TILE - nw if east and not west else (TILE - nw) // 2)
    y = 0 if north and not south else (TILE - nh if south and not north else (TILE - nh) // 2)
    canvas = Image.new("RGBA", (TILE, TILE), (0, 0, 0, 0))
    layer = piece.convert("RGBA")
    layer.putalpha(alpha)
    canvas.paste(layer, (max(0, min(TILE - nw, x)), max(0, min(TILE - nh, y))))
    return canvas


def boxes_for(sheet, row):
    """Куда на листе лежат детали: ряд сверху или снизу, слева направо."""
    mask = mask_of(sheet)
    rows = side_profile(mask, "row")
    if len(rows) < 2:
        raise SystemExit(f"не нашёл ряды деталей на листе: {rows}")
    y0, y1 = rows[0] if row == "top" else rows[-1]
    band = mask.crop((0, y0, mask.width, y1))
    return [(x0, y0, x1, y1) for x0, x1 in side_profile(band, "col")]


def main():
    os.makedirs(OUT, exist_ok=True)
    manifest = {}

    for set_name, filename in MATERIAL_SETS.items():
        path = os.path.join(ROOT, "pipes", filename)
        if not os.path.exists(path):
            print(f"{set_name}: листа {filename} нет — пропускаю")
            continue
        sheet = Image.open(path)
        for piece, place in LAYOUT.items():
            boxes = boxes_for(sheet, place["row"])
            if place["index"] >= len(boxes):
                raise SystemExit(f"{set_name}/{piece}: на листе нашёл {len(boxes)} деталей")
            ports = PORTS_OF[piece]
            sprite = fit_piece(sheet, boxes[place["index"]], ports)
            sprite, steps = pre_rotate(sprite, ports, MASK_OF[piece])
            # После доворота деталь лежит канонически, поэтому проверяем не порты
            # исходного листа, а биты маски движка: именно они обязаны дойти до края.
            canonical = [bit for bit in (1, 2, 4, 8) if MASK_OF[piece] & bit]
            coverage = {bit: round(touches(sprite.getchannel("A"), bit), 2) for bit in (1, 2, 4, 8)}
            name = f"{set_name}-{piece}"
            sprite.save(os.path.join(OUT, f"{name}.png"), optimize=True)
            reached = [bit for bit in canonical if coverage[bit] > 0.02]
            warn = "" if len(reached) == len(canonical) else f"  ⚠ маска {MASK_OF[piece]}: не к краю {coverage}"
            manifest[name] = {
                "mask": MASK_OF[piece],
                "file": f"art/tiles/{name}.png",
                "preRotation": steps,
                "set": set_name,
                "edge": coverage,
            }
            print(f"{name}: маска {MASK_OF[piece]} (доворот {steps}) → art/tiles/{name}.png{warn}")

    # Вторая прямая труба (цинк) — только для канонного набора: даёт разнообразие поля.
    extra = os.path.join(ROOT, "pipes", "straight-topdown.jpg")
    if os.path.exists(extra):
        sheet = Image.open(extra)
        boxes = boxes_for(sheet, "top")
        sprite = fit_piece(sheet, boxes[0], PORTS_OF["straight"])
        sprite, steps = pre_rotate(sprite, PORTS_OF["straight"], MASK_OF["straight"])
        sprite.save(os.path.join(OUT, "base-straight2.png"), optimize=True)
        manifest["base-straight2"] = {
            "mask": 5,
            "file": "art/tiles/base-straight2.png",
            "preRotation": steps,
            "set": "base",
            "edge": {},
        }
        print("base-straight2: вторая прямая (цинк) → art/tiles/base-straight2.png")

    for name, filename in ROUND_PIECES.items():
        img = Image.open(os.path.join(ROOT, "pipes", filename))
        sprite = fit_piece(img, mask_of(img).getbbox())
        sprite.save(os.path.join(OUT, f"{name}.png"), optimize=True)
        manifest[name] = {"mask": None, "file": f"art/tiles/{name}.png", "preRotation": 0, "set": "base", "edge": {}}
        print(f"{name}: круглый элемент → art/tiles/{name}.png")

    with open(os.path.join(OUT, "manifest.json"), "w", encoding="utf-8") as fh:
        json.dump(manifest, fh, ensure_ascii=False, indent=2)
    print(f"manifest.json записан: {len(manifest)} деталей")


if __name__ == "__main__":
    main()
