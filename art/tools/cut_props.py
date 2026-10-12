#!/usr/bin/env python3
"""Нарезка служебных деталей (пропсов) из листов art/promo/*-topdown.jpg.

Листы — вид строго сверху на чёрном фоне, по четыре детали в сетке 2×2:

  * `arrows-topdown.jpg` — плиты-указатели потока: вверх, вправо, вниз, влево
    (подсказка «куда течёт» и маркеры пути в обучении);
  * `spare-topdown.jpg` — запасные детали поля: глухая плита, муфта, фланец,
    маховик (заглушки ячеек, стыки, декор квартала).

Скрипт делает фон прозрачным, обрезает по фактической детали, приводит к квадрату
512×512 и пишет `art/tiles/props.json` — контракт для движка и витрины.

Запуск: python3 art/tools/cut_props.py        (нужен pillow)
"""
import json
import os

from PIL import Image, ImageFilter

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
PROMO = os.path.join(ROOT, "promo")
OUT = os.path.join(ROOT, "tiles")
PROP = 512

# Лист → четыре детали в порядке ячеек (верх-лево, верх-право, низ-лево, низ-право).
SHEETS = {
    "arrows-topdown.jpg": [
        {"key": "arrow-up", "kind": "waypoint", "points": "up", "caption": "вверх"},
        {"key": "arrow-right", "kind": "waypoint", "points": "right", "caption": "вправо"},
        {"key": "arrow-down", "kind": "waypoint", "points": "down", "caption": "вниз"},
        {"key": "arrow-left", "kind": "waypoint", "points": "left", "caption": "влево"},
    ],
    "spare-topdown.jpg": [
        {"key": "spare-blind", "kind": "spare", "caption": "глухая плита"},
        {"key": "spare-coupling", "kind": "spare", "caption": "муфта"},
        {"key": "spare-flange", "kind": "spare", "caption": "фланец"},
        {"key": "spare-wheel", "kind": "spare", "caption": "маховик"},
    ],
}


def part_mask(img, threshold=16):
    """Чёрный фон → 0, деталь → 255. Морфология закрывает дырки и срезает шум."""
    mask = img.convert("L").point(lambda v: 255 if v > threshold else 0)
    mask = mask.filter(ImageFilter.MaxFilter(3))
    mask = mask.filter(ImageFilter.MinFilter(3))
    return mask


def cut_cell(cell, key):
    """Ячейка листа → RGBA-деталь 512×512 с прозрачным фоном."""
    mask = part_mask(cell)
    box = mask.getbbox()
    if box is None:
        raise SystemExit(f"{key}: на листе пустая ячейка — проверь раскладку")
    pad = max(6, int(max(box[2] - box[0], box[3] - box[1]) * 0.04))
    box = (max(0, box[0] - pad), max(0, box[1] - pad),
           min(cell.width, box[2] + pad), min(cell.height, box[3] + pad))
    art = cell.crop(box).convert("RGB")
    art.putalpha(mask.crop(box))

    side = max(art.width, art.height)
    square = Image.new("RGBA", (side, side), (0, 0, 0, 0))
    square.paste(art, ((side - art.width) // 2, (side - art.height) // 2), art)
    return square.resize((PROP, PROP), Image.LANCZOS)


def main():
    os.makedirs(OUT, exist_ok=True)
    props = {}
    for sheet, cells in SHEETS.items():
        path = os.path.join(PROMO, sheet)
        img = Image.open(path).convert("RGB")
        w, h = img.width // 2, img.height // 2
        boxes = [(0, 0, w, h), (w, 0, img.width, h), (0, h, w, img.height), (w, h, img.width, img.height)]
        for spec, box in zip(cells, boxes):
            art = cut_cell(img.crop(box), spec["key"])
            name = f"prop-{spec['key']}.png"
            art.save(os.path.join(OUT, name))
            props[f"prop-{spec['key']}"] = {
                "file": name,
                "kind": spec["kind"],
                "caption": spec["caption"],
                "source": f"promo/{sheet}",
            }
            if "points" in spec:
                props[f"prop-{spec['key']}"]["points"] = spec["points"]
            print(f"нарезано: {name} ({spec['caption']})")

    with open(os.path.join(OUT, "props.json"), "w", encoding="utf-8") as fh:
        json.dump(props, fh, ensure_ascii=False, indent=2)
        fh.write("\n")
    print(f"props.json записан: {len(props)} деталей")


if __name__ == "__main__":
    main()
