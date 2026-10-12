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

# Биты как в движке: 1 = север, 2 = восток, 4 = юг, 8 = запад.
# Для каждой детали: где лежит на листе, куда смотрят её порты и к какой маске
# движка деталь приводится (5 = прямая, 3 = отвод, 7 = тройник, 15 = крест).
SHEET_PIECES = {
    "straight": {"sheet": "pieces-topdown.jpg", "row": "top", "index": 0, "ports": [2, 8], "engine": 5},
    "elbow": {"sheet": "pieces-topdown.jpg", "row": "bottom", "index": 0, "ports": [2, 4], "engine": 3},
    "tee": {"sheet": "pieces-topdown.jpg", "row": "bottom", "index": 1, "ports": [1, 2, 8], "engine": 7},
    "cross": {"sheet": "pieces-topdown.jpg", "row": "bottom", "index": 2, "ports": [1, 2, 4, 8], "engine": 15},
    "straight2": {"sheet": "straight-topdown.jpg", "row": "top", "index": 0, "ports": [2, 8], "engine": 5},
}
ROUND_PIECES = {"mouth": "open-end-topdown.jpg", "cap": "cap-topdown.jpg"}


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


def touches(alpha, bit, edge=2):
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
        if any(px[x, y] > 40 for x, y in probe):
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
    sheets = {}
    manifest = {}

    for name, spec in SHEET_PIECES.items():
        if spec["sheet"] not in sheets:
            sheets[spec["sheet"]] = Image.open(os.path.join(ROOT, "pipes", spec["sheet"]))
        sheet = sheets[spec["sheet"]]
        boxes = boxes_for(sheet, spec["row"])
        if spec["index"] >= len(boxes):
            raise SystemExit(f"{name}: на листе {spec['sheet']} нашёл {len(boxes)} деталей, нужен индекс {spec['index']}")
        ports = spec["ports"]
        sprite = fit_piece(sheet, boxes[spec["index"]], ports)
        sprite, steps = pre_rotate(sprite, ports, spec["engine"])
        coverage = {bit: round(touches(sprite.getchannel("A"), bit), 2) for bit in (1, 2, 4, 8)}
        sprite.save(os.path.join(OUT, f"{name}.png"))
        reached = [bit for bit in ports if coverage[bit] > 0.02]
        warn = "" if len(reached) == len(ports) else f"  ⚠ порты {ports} не на краю: {coverage}"
        manifest[name] = {
            "mask": spec["engine"],
            "file": f"tiles/{name}.png",
            "preRotation": steps,
            "edge": coverage,
        }
        print(f"{name}: маска движка {spec['engine']} (доворот {steps}) → art/tiles/{name}.png{warn}")

    for name, filename in ROUND_PIECES.items():
        img = Image.open(os.path.join(ROOT, "pipes", filename))
        sprite = fit_piece(img, mask_of(img).getbbox())
        sprite.save(os.path.join(OUT, f"{name}.png"))
        manifest[name] = {"mask": None, "file": f"tiles/{name}.png", "preRotation": 0, "edge": {}}
        print(f"{name}: круглый элемент → art/tiles/{name}.png")

    with open(os.path.join(OUT, "manifest.json"), "w", encoding="utf-8") as fh:
        json.dump(manifest, fh, ensure_ascii=False, indent=2)
    print("manifest.json записан")


if __name__ == "__main__":
    main()
