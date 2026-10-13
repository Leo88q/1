#!/usr/bin/env python3
"""Сверяет ассеты витрины с тем, что рисует рендерер прямо сейчас.

Запуск:  python3 store/check_assets.py   (после store/render_assets.py)

Зачем: картинки витрины лежат в репозитории готовыми файлами. Если рендерер
поменяли, а ассеты не перерисовали, листинг уедет с устаревшим видом игры —
и этого никто не заметит до публикации.

Почему не байтовое сравнение: ассеты рисуются шрифтами DejaVu, и версия
шрифта (а значит сглаживание и ширина глифов) отличается от машины к машине.
На другой системе картинки не совпадут побайтово, хотя визуально одинаковы,
— поэтому сравниваем попиксельно с допуском.
"""

import io
import os
import subprocess
import sys

from PIL import Image, ImageChops, ImageStat

ROOT = os.path.dirname(os.path.abspath(__file__))

# Что рендерер обязан положить в каждый набор.
FILES = [
    "icon-512.png",
    "feature-graphic-1024x500.png",
    "screenshot-1.png",
    "screenshot-2.png",
    "screenshot-3.png",
    "screenshot-4.png",
    "screenshot-5.png",
]

# Среднее расхождение по каналу (0…255), которое считаем «тот же кадр».
#
# Порог подобран по наблюдению: одна и та же версия кода на двух машинах даёт
# расхождение 1.5–2.5 — это отрисовка текста (версия Pillow, её сборочные
# флаги вроде raqm, сглаживание). Кадр без текста (иконка) расходится на 0.
# Структурные расхождения — сдвинутая раскладка, другой размер, потерянный
# элемент, лишний скриншот — дают десятки. Поэтому порог 6: он ловит
# устаревшие ассеты и не ломает сборку из-за другой версии Pillow.
TOLERANCE = 6.0


def committed(rel: str):
    """Картинка из коммита (HEAD), а не из рабочей папки."""
    raw = subprocess.run(
        ["git", "show", f"HEAD:{rel}"], capture_output=True, check=False, cwd=os.path.dirname(ROOT)
    )
    if raw.returncode != 0:
        return None
    try:
        return Image.open(io.BytesIO(raw.stdout)).convert("RGB")
    except OSError:
        return None


def mean_diff(a: Image.Image, b: Image.Image) -> float:
    if a.size != b.size:
        return 999.0
    stat = ImageStat.Stat(ImageChops.difference(a, b))
    return sum(stat.mean) / len(stat.mean)


def main() -> int:
    offenders = []
    missing = []
    for lang in ("assets", "assets-en"):
        for name in FILES:
            rel = os.path.join("store", lang, name).replace(os.sep, "/")
            path = os.path.join(ROOT, lang, name)
            if not os.path.exists(path):
                missing.append(rel)
                continue
            fresh = Image.open(path).convert("RGB")
            old = committed(rel)
            if old is None:
                print(f"  {rel}: нет в коммите — пропускаем")
                continue
            delta = mean_diff(old, fresh)
            print(f"  {rel}: расхождение {delta:.3f}")
            if delta > TOLERANCE:
                offenders.append((rel, delta, old.size, fresh.size))

    if missing:
        print("::error::не отрисованы: " + ", ".join(missing))
        return 1
    if offenders:
        for rel, delta, a, b in offenders:
            print(
                f"::error::{rel}: отличается от отрисованного "
                f"(расхождение {delta:.1f}, было {a}, стало {b}) — "
                f"выполните python3 store/render_assets.py и python3 store/render_assets.py --lang en"
            )
        return 1
    print("ассеты витрины совпадают с рендерером")
    return 0


if __name__ == "__main__":
    sys.exit(main())
