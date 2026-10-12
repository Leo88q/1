#!/usr/bin/env python3
"""Проверка стыков: трубы в игре должны соединяться, а не почти соединяться.

Отдельно спрайт каждой детали выглядит трубой — вопрос в том, что выходит на
поле. Поэтому скрипт берёт настоящие уровни из тест-векторов ядра, собирает их
в **решённом** виде (solutionRot — там все соединения замкнуты) и измеряет
каждый стык:

  • труба доходит до самого края тайла (альфа на границе ≥ 250, иначе виден зазор);
  • поперечник трубы одинаков у всех деталей и наборов (иначе на стыке уступ);
  • ось трубы идёт по центру тайла (иначе соседние детали разъезжаются);
  • на границе двух соседей труба есть с обеих сторон, их ширина и центр
    совпадают — это и есть соединение.

Запуск: python3 packages/lab/scripts/check-seams.py     (нужны pillow и numpy)
"""

import json
import os
import sys

import numpy as np
from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
LAB = os.path.dirname(HERE)
REPO = os.path.dirname(os.path.dirname(LAB))
ART = os.path.join(LAB, "public", "art")
WEB = os.path.join(ART, "web.json")
LEVELS = os.path.join(REPO, "packages", "engine", "vectors", "levels.json")

# Порт → на сколько четвертей против часовой повернуть картинку, чтобы этот порт
# смотрел вверх (к северу). np.rot90(k) крутит против часовой, поэтому:
#   N (сверху)  — не крутим;
#   E (справа)  — против часовой 90°, правое становится верхним;
#   S (снизу)   — 180°;
#   W (слева)   — против часовой 270° (то есть по часовой 90°), левое становится верхним.
TURN = {1: 0, 2: 1, 4: 2, 8: 3}
BACK = {1: 4, 2: 8, 4: 1, 8: 2}

problems = []


def note(msg):
    problems.append(msg)


def rotate_cw(mask, turns):
    """Поворот маски по часовой стрелке: N→E→S→W→N."""
    turns %= 4
    return ((mask << turns) | (mask >> (4 - turns))) & 0xF


def piece_for(mask):
    """Деталь под маску — как её выбирает рендер (kindOf в движке)."""
    count = bin(mask).count("1")
    if count == 1:
        return "stub"
    if count == 2:
        return "straight" if mask in (5, 10) else "elbow"
    if count == 3:
        return "tee"
    return "cross"


def view_north(arr, port):
    """Картинка, повёрнутая так, что порт `port` смотрит вверх."""
    return np.rot90(arr, k=TURN[port])


def mouth(arr):
    """Профиль выхода к краю: плотность, поперечник и центр полосы."""
    alpha = arr[:, :, 3]
    width = alpha.shape[1]
    band = alpha[0:3, :].max(axis=0)      # не одна строка: у края бывает сглаживание
    solid = np.where(band >= 200)[0]
    if len(solid) == 0:
        return 0.0, 0, None
    return float(band.max()), int(solid[-1] - solid[0] + 1), float((solid[0] + solid[-1]) / 2 - (width - 1) / 2)


def main():
    web = json.load(open(WEB, encoding="utf-8"))
    levels = json.load(open(LEVELS, encoding="utf-8"))["levels"]
    tile_size = web["tile"]
    cache = {}

    def sprite(set_name, piece):
        key = (set_name, piece)
        if key not in cache:
            ref = web["sets"][set_name].get(piece) or web["sets"]["base"][piece]
            cache[key] = (
                np.asarray(Image.open(os.path.join(ART, ref["file"])).convert("RGBA")).astype(np.float32),
                ref["file"],
            )
        return cache[key]

    # 1. Каждый выход каждой детали: доходит ли труба до края, какой у неё поперечник.
    spans = []
    for set_name, pieces in sorted(web["sets"].items()):
        for piece, ref in sorted(pieces.items()):
            arr = np.asarray(Image.open(os.path.join(ART, ref["file"])).convert("RGBA")).astype(np.float32)
            if arr.shape[0] != tile_size:
                note(f"{set_name}/{piece}: размер {arr.shape[0]}, а в контракте {tile_size}")
            for bit in (1, 2, 4, 8):
                if not (ref["mask"] & bit):
                    continue
                peak, width, center = mouth(view_north(arr, bit))
                spans.append(width)
                if peak < 250:
                    note(f"{set_name}/{piece} порт {bit}: у края альфа {peak:.0f} — на стыке будет зазор")
                if width < 4:
                    note(f"{set_name}/{piece} порт {bit}: труба к краю не выходит ({width} px)")
                if center is not None and abs(center) > 1.5:
                    note(f"{set_name}/{piece} порт {bit}: ось смещена на {center:+.1f} px от центра тайла")

    spread = max(spans) - min(spans)
    if spread > 2:
        note(f"поперечник трубы плавает: {min(spans)}…{max(spans)} px (разброс {spread}) — на стыке уступ")
    print(f"поперечник трубы: {min(spans)}…{max(spans)} px (разброс {spread}), выходов к краю: {len(spans)}")

    # 2. Горловина приёмника: её ширина у края обязана совпадать с трубой, иначе
    #    труба «обрывается» в узкий раструб.
    mouth_ref = web["round"].get("mouth")
    if mouth_ref:
        file = mouth_ref["file"] if isinstance(mouth_ref, dict) else mouth_ref
        arr = np.asarray(Image.open(os.path.join(ART, file)).convert("RGBA")).astype(np.float32)
        _, width, center = mouth(view_north(arr, 1))
        target = max(spans)
        if abs(width - target) > 3:
            note(f"горловина приёмника: {width} px против трубы {target} px — труба обрывается в раструб")
        if center is not None and abs(center) > 1.5:
            note(f"горловина приёмника: ось смещена на {center:+.1f} px")
        print(f"горловина приёмника: {width} px (труба {target} px), ось {center:+.1f} px")

    # 3. Поле целиком: каждый стык двух соседей в решённом виде уровня.
    joined = 0
    checked = 0
    for level in levels:
        size = level["size"]
        base = level["base"]
        rot = level["solutionRot"]
        number = level["level"]
        tier = web["tiers"][min(len(web["tiers"]) - 1, max(0, (number - 1) // 15))]
        sets = tier["sets"]

        for i in range(size * size):
            x, y = i % size, i // size
            screen = rotate_cw(base[i], rot[i])      # маска ячейки на экране
            set_name = sets[(x + y + i) % len(sets)]
            for bit in (1, 2, 4, 8):
                if not (screen & bit):
                    continue
                nx, ny = x + (1 if bit == 2 else -1 if bit == 8 else 0), y + (1 if bit == 4 else -1 if bit == 1 else 0)
                if not (0 <= nx < size and 0 <= ny < size):
                    continue
                j = ny * size + nx
                other_screen = rotate_cw(base[j], rot[j])
                if not (other_screen & BACK[bit]):
                    continue

                # Чем нарисована каждая сторона стыка: деталь по канонической маске,
                # повёрнутая так, чтобы нужный порт смотрел в сторону соседа.
                a_arr, a_file = sprite(set_name, piece_for(base[i]))
                b_set = sets[(nx + ny + j) % len(sets)]
                b_arr, b_file = sprite(b_set, piece_for(base[j]))
                a_port = rotate_cw(bit, -rot[i])
                b_port = rotate_cw(BACK[bit], -rot[j])
                _, w1, c1 = mouth(view_north(a_arr, a_port))
                _, w2, c2 = mouth(view_north(b_arr, b_port))
                checked += 1
                if w1 == 0 or w2 == 0:
                    note(f"уровень {number}: стык ({x},{y})→{bit}: разрыв — {a_file} {w1} px, {b_file} {w2} px")
                elif abs(w1 - w2) > 2:
                    note(f"уровень {number}: стык ({x},{y})→{bit}: поперечник {w1} и {w2} px — уступ")
                elif c1 is None or c2 is None or abs(c1 - c2) > 2:
                    note(f"уровень {number}: стык ({x},{y})→{bit}: оси разъезжаются")
                else:
                    joined += 1

    print(f"соединений на полях: {joined} из {checked}")
    if checked and joined < checked:
        note(f"разорвано соединений: {checked - joined} из {checked}")

    if problems:
        print("\nстыки труб не сходятся:", file=sys.stderr)
        for p in problems[:25]:
            print(f"  ✗ {p}", file=sys.stderr)
        sys.exit(1)
    print("стыки ок: трубы доходят до края, поперечник один, оси совпадают")


if __name__ == "__main__":
    main()
