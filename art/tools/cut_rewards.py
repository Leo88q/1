#!/usr/bin/env python3
"""Нарезка медальонов наград из листа art/rewards/plombs-topdown.jpg.

Награда за квартал — «пломба»: круглый медальон. На листе их шесть, и каждая —
вид награды: звезда за чистый проход, кольца за серию дней, диск за проход ровно
по плану, смола за челлендж дня, трещина за сложный квартал, маджента-трещина за
недельный вызов.

Раскладку скрипт **находит сам**: порог по яркости, связные области, каждая область —
медальон. Так нарезка не ломается, если в следующей партии деталей станет больше или
сетка окажется не 2×2 (на этом листе она как раз 3×2). Для каждой области берётся
квадратный бокс вокруг центра — медальоны круглые, и квадрат сохраняет пропорции.

Запуск: python3 art/tools/cut_rewards.py        (нужен pillow)
"""
import json
import os
from collections import deque

from PIL import Image, ImageFilter

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
REWARDS = os.path.join(ROOT, "rewards")
OUT = os.path.join(ROOT, "tiles")
SIZE = 512
PROBE = 4  # во сколько раз уменьшаем маску перед поиском областей

SHEET = "plombs-topdown.jpg"
# Имена по порядку чтения листа: слева направо, сверху вниз.
NAMES = [
    {"key": "reward-star", "caption": "звезда · квартал без подсказок"},
    {"key": "reward-rings", "caption": "кольца · серия дней"},
    {"key": "reward-disc", "caption": "диск · проход ровно по плану"},
    {"key": "reward-resin", "caption": "смола · челлендж дня"},
    {"key": "reward-crack", "caption": "трещина · сложный квартал"},
    {"key": "reward-magma", "caption": "магма · недельный вызов"},
]


def regions(mask):
    """Связные области маски: список боксов на полном разрешении, сорт по строкам."""
    small = mask.resize((mask.width // PROBE, mask.height // PROBE), Image.BOX)
    px = small.load()
    w, h = small.size
    seen = [[False] * w for _ in range(h)]
    found = []
    for y0 in range(h):
        for x0 in range(w):
            if seen[y0][x0] or px[x0, y0] < 8:
                continue
            queue = deque([(x0, y0)])
            seen[y0][x0] = True
            minx, miny, maxx, maxy = x0, y0, x0, y0
            while queue:
                x, y = queue.popleft()
                minx, miny = min(minx, x), min(miny, y)
                maxx, maxy = max(maxx, x), max(maxy, y)
                for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                    nx, ny = x + dx, y + dy
                    if 0 <= nx < w and 0 <= ny < h and not seen[ny][nx] and px[nx, ny] >= 8:
                        seen[ny][nx] = True
                        queue.append((nx, ny))
            box = (minx * PROBE, miny * PROBE, (maxx + 1) * PROBE, (maxy + 1) * PROBE)
            if (box[2] - box[0]) > 80 and (box[3] - box[1]) > 80:  # мелочь и мусор — мимо
                found.append(box)
    # Порядок чтения: сначала верхние, внутри строки — слева направо.
    found.sort(key=lambda b: ((b[1] + b[3]) // 2 // 200, b[0]))
    return found


def cut(cell):
    """Квадрат вокруг области → RGBA 512×512 с прозрачным фоном."""
    mask = cell.convert("L").point(lambda v: 255 if v > 16 else 0)
    mask = mask.filter(ImageFilter.MaxFilter(3)).filter(ImageFilter.MinFilter(3))
    box = mask.getbbox()
    if box is None:
        return None
    art = cell.crop(box).convert("RGB")
    art.putalpha(mask.crop(box))
    side = max(art.width, art.height)
    square = Image.new("RGBA", (side, side), (0, 0, 0, 0))
    square.paste(art, ((side - art.width) // 2, (side - art.height) // 2), art)
    return square.resize((SIZE, SIZE), Image.LANCZOS)


def main():
    os.makedirs(OUT, exist_ok=True)
    sheet = Image.open(os.path.join(REWARDS, SHEET)).convert("RGB")
    mask = sheet.convert("L").point(lambda v: 255 if v > 24 else 0)
    boxes = regions(mask)
    if len(boxes) != len(NAMES):
        raise SystemExit(f"нашёл {len(boxes)} медальонов, а имён {len(NAMES)} — сверь раскладку листа")

    rewards = {}
    for spec, box in zip(NAMES, boxes):
        art = cut(sheet.crop(box))
        if art is None:
            raise SystemExit(f"{spec['key']}: пустая область {box}")
        name = f"{spec['key']}.png"
        art.save(os.path.join(OUT, name), optimize=True)
        rewards[spec["key"]] = {
            "file": name,
            "caption": spec["caption"],
            "source": f"rewards/{SHEET}",
        }
        print(f"нарезано: {name} ({spec['caption']})")

    with open(os.path.join(OUT, "rewards.json"), "w", encoding="utf-8") as fh:
        json.dump(rewards, fh, ensure_ascii=False, indent=2)
        fh.write("\n")
    print(f"rewards.json записан: {len(rewards)} медальонов")


if __name__ == "__main__":
    main()
