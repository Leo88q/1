#!/usr/bin/env python3
"""Кует спрайты деталей с точной геометрией стыка из нарисованного арта.

Зачем это нужно
---------------
Трубы в игре стыкуются, только если у всех деталей одинаковая толщина, ось идёт
ровно по центру тайла, а сама труба доходит до самого края. Нарисованный арт
такой точности не даёт и не может: у наборов разная толщина (12–25 % тайла),
ось гуляет до 12 px, у отводов труба к краю не выходит, а по краям идёт
прозрачная кайма. Проверялось численно: 84 пробы из 98 — с разрывом стыка.

Поэтому геометрию задаём кодом, а материал берём из арта:
  • профиль трубы (цвет, блики, ржавчина, стекло) вырезается поперёк настоящей
    нарисованной детали `straight` — это и есть текстура металла;
  • форма трубы строится из «скелета» (отрезки и четверть окружности), поэтому
    толщина везде одна, ось — по центру, а труба доходит до края тайла;
  • у края добавляется муфта (небольшое утолщение) — на стыке двух деталей
    получается валик, как у настоящих труб, и шов читается как соединение.

Исходный арт (`art/tiles/*.png`) не трогаем: результат ложится рядом, в
`art/tiles/forged/`, и его берёт и игра, и макет поля.

Запуск: python3 art/tools/forge_tiles.py        (нужны pillow и numpy)
"""

import json
import os

import numpy as np
from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
TILES = os.path.join(ROOT, "tiles")
FORGED = os.path.join(TILES, "forged")
MANIFEST = os.path.join(TILES, "manifest.json")

SIZE = 512                 # рабочее разрешение: считаем крупно, отдаём 256
SS = 2                     # во столько раз больше итогового — антиалиасинг
TILE = 256                 # итоговый размер спрайта в игре
THICKNESS = 0.215          # толщина трубы в долях тайла (единая для всех наборов)
COLLAR = 0.20              # муфта у края: доля тайла и сила утолщения
COLLAR_GAIN = 0.20
FLARE = 1.85               # во сколько раз раструб приёмника шире трубы

# Порт → сторона: N=1 сверху, E=2 справа, S=4 снизу, W=8 слева.
PORTS = {1: "N", 2: "E", 4: "S", 8: "W"}


def skeleton(mask, s):
    """Скелет детали: отрезки и дуги, по которым течёт среда.

    Возвращает список элементов: ("line", x0, y0, x1, y1) и
    ("arc", cx, cy, r). Расстояние до скелета — это и есть «поперечина» трубы.
    """
    c = s / 2.0
    half = s / 2.0
    parts = []
    if mask & 1:  # N — вниз от верхнего края
        parts.append(("line", c, -s, c, c))
    if mask & 4:  # S — вниз до нижнего края
        parts.append(("line", c, c, c, s + s))
    if mask & 2:  # E — вправо до правого края
        parts.append(("line", c, c, s + s, c))
    if mask & 8:  # W — влево до левого края
        parts.append(("line", c, c, -s, c))
    # Отвод: вместо прямого угла в центре — четверть окружности, чтобы изгиб был
    # плавным. Радиус равен половине тайла, центр — в том углу, куда поворачиваем.
    if mask in (3, 6, 9, 12):
        parts = [p for p in parts if p[0] == "arc"]  # на всякий случай
        corners = {3: (s, -0.0), 6: (s, s), 9: (-0.0, -0.0), 12: (-0.0, s)}
        cx, cy = corners[mask]
        parts = [("arc", cx, cy, half)]
    return parts


def distance_field(parts, s):
    """Расстояние со знаком от каждого пикселя до скелета (в пикселях)."""
    ys, xs = np.mgrid[0:s, 0:s].astype(np.float32)
    best = np.full((s, s), 1e9, dtype=np.float32)
    sign = np.zeros((s, s), dtype=np.float32)

    for part in parts:
        if part[0] == "line":
            _, x0, y0, x1, y1 = part
            dx, dy = x1 - x0, y1 - y0
            length2 = dx * dx + dy * dy
            t = ((xs - x0) * dx + (ys - y0) * dy) / length2
            t = np.clip(t, 0.0, 1.0)
            px, py = x0 + t * dx, y0 + t * dy
            dist = np.hypot(xs - px, ys - py)
            # Знак — по какую сторону от направления трубы лежит пиксель.
            cross = (xs - x0) * dy - (ys - y0) * dx
        else:
            _, cx, cy, r = part
            dist = np.abs(np.hypot(xs - cx, ys - cy) - r)
            cross = np.hypot(xs - cx, ys - cy) - r
        closer = dist < best
        best = np.where(closer, dist, best)
        sign = np.where(closer, np.sign(cross), sign)

    return best, np.where(sign == 0, 1.0, sign)


def cut_profile(path, thickness_px):
    """Поперечный профиль трубы из нарисованной детали: цвет, блики, материал."""
    img = Image.open(path).convert("RGBA")
    w, h = img.size
    arr = np.asarray(img).astype(np.float32)
    y0, y1 = h // 2 - 24, h // 2 + 24      # усредняем по полосе, чтобы не шумело
    strip = arr[y0:y1, :, :]
    alpha = strip[:, :, 3]
    # Границы трубы — там, где материал плотный.
    column = alpha.mean(axis=0)
    solid = np.where(column >= 200)[0]
    if len(solid) < 8:
        raise ValueError(f"в {os.path.basename(path)} не нашлась труба (альфа среза {column.max():.0f})")
    lo, hi = int(solid[0]), int(solid[-1])
    span = hi - lo + 1
    # Тянем профиль к единой толщине: индекс по поперечине трубы.
    src = np.linspace(0.0, 1.0, span, dtype=np.float32)
    dst = np.linspace(0.0, 1.0, thickness_px, dtype=np.float32)
    profile = np.empty((thickness_px, 4), dtype=np.float32)
    for ch in range(4):
        profile[:, ch] = np.interp(dst, src, strip[:, lo : hi + 1, ch].mean(axis=0))
    # Материал внутри трубы непрозрачен: полупрозрачность была лишь сглаживанием
    # краёв нарисованной детали, а в игре край задаёт форма.
    profile[:, 3] = 255.0
    return profile


def collar_map(s):
    """Муфта у края тайла: множитель толщины. Им пользуются и труба, и
    раструб, поэтому на стыке они выходят одной ширины."""
    ys, xs = np.mgrid[0:s, 0:s].astype(np.float32)
    edge = np.minimum(np.minimum(xs, ys), np.minimum(s - 1 - xs, s - 1 - ys))
    t = np.clip(1.0 - edge / (s * COLLAR), 0.0, 1.0)
    return 1.0 + COLLAR_GAIN * (t * t * (3.0 - 2.0 * t))


def forge(profile, mask, s, thickness_px):
    """Собирает спрайт детали: форма из скелета, цвет из профиля нарисованной трубы."""
    parts = skeleton(mask, s)
    dist, sign = distance_field(parts, s)

    # Муфта у края тайла: труба утолщается, и на стыке двух деталей выходит валик.
    thick = thickness_px * collar_map(s)
    half_thick = thick / 2.0

    # Поперечина со знаком: от -половины до +половины толщины.
    across = np.clip(dist * sign, -half_thick, half_thick)
    inside = (dist <= half_thick).astype(np.float32)

    idx = ((across / thick + 0.5) * (profile.shape[0] - 1)).astype(np.int32)
    idx = np.clip(idx, 0, profile.shape[0] - 1)
    rgba = profile[idx]

    # Край трубы: мягкий на один пиксель рабочего разрешения, чтобы после
    # уменьшения не осталось «лесенки».
    soft = np.clip((half_thick - dist) / (s / float(TILE)), 0.0, 1.0)
    alpha = np.where(inside > 0, 255.0 * np.minimum(1.0, soft), 0.0)
    out = np.dstack((rgba[:, :, :3], alpha)).astype(np.uint8)
    return Image.fromarray(out, "RGBA")


def forge_mouth(profile, s, thickness_px):
    """Горловина приёмника: раструб с открытым концом.

    Нарисованная горловина сужалась к краю до 24 px при трубе в 64 px — на стыке
    с трубой получался обрыв. Поэтому у края раструб равен трубе ровно, а
    расширяется уже внутрь ячейки, к отверстию.
    """
    arr = forge(profile, 1, s, thickness_px)          # заготовка: труба от края к центру
    out = np.asarray(arr).astype(np.float32)
    ys, xs = np.mgrid[0:s, 0:s].astype(np.float32)
    c = s / 2.0

    # Раструб: от единицы у края до FLARE в центре ячейки.
    along = np.clip(1.0 - ys / c, 0.0, 1.0)           # 0 у центра, 1 у верхнего края
    widen = 1.0 + (FLARE - 1.0) * (1.0 - along)
    half = thickness_px * collar_map(s) * widen / 2.0
    dist = np.abs(xs - c)
    keep = np.clip((half - dist) / (s / float(TILE)), 0.0, 1.0)
    out[:, :, 3] = np.where(dist <= half, 255.0 * np.minimum(1.0, keep), 0.0)
    # Края раструба дотягиваем цветом: профиль берём по пересчитанной поперечине.
    across = np.clip(dist * np.sign(xs - c + 1e-6) / np.maximum(widen, 1e-6), -thickness_px / 2.0, thickness_px / 2.0)
    idx = ((across / thickness_px + 0.5) * (profile.shape[0] - 1)).astype(np.int32)
    idx = np.clip(idx, 0, profile.shape[0] - 1)
    out[:, :, :3] = profile[idx][:, :, :3]

    # Отверстие: тёмный эллипс с подсветкой по ободу — видно, что конец открытый.
    ry, rx = thickness_px * 0.30, thickness_px * FLARE * 0.52
    inside = ((xs - c) / rx) ** 2 + ((ys - (c + thickness_px * 0.30)) / ry) ** 2
    hole = np.clip(1.0 - inside, 0.0, 1.0)
    rim = np.clip(1.0 - np.abs(inside - 1.0) * 6.0, 0.0, 1.0)
    out[:, :, :3] *= (1.0 - 0.62 * hole)[..., None]
    out[:, :, :3] += (70.0 * rim)[..., None]
    out[:, :, 3] = np.maximum(out[:, :, 3], 255.0 * np.clip(hole + rim, 0.0, 1.0))
    return Image.fromarray(np.clip(out, 0, 255).astype(np.uint8), "RGBA")


def main():
    with open(MANIFEST, encoding="utf-8") as fh:
        manifest = json.load(fh)

    sets = sorted({meta["set"] for meta in manifest.values() if meta.get("mask")})
    thickness_px = int(round(SIZE * THICKNESS))
    os.makedirs(FORGED, exist_ok=True)
    made = 0

    for set_name in sets:
        # Текстура набора — из его нарисованной прямой трубы.
        src = None
        for name, meta in manifest.items():
            if meta.get("set") == set_name and meta.get("mask") == 5 and "straight" in name:
                src = name
                break
        if src is None:
            print(f"  {set_name}: нет нарисованной прямой трубы — набор пропущен")
            continue
        profile = cut_profile(os.path.join(TILES, f"{src}.png"), thickness_px)

        for name, meta in sorted(manifest.items()):
            if meta.get("set") != set_name or not meta.get("mask"):
                continue
            mask = meta["mask"]
            img = forge(profile, mask, SIZE, thickness_px)
            img = img.resize((TILE, TILE), Image.LANCZOS)
            img.save(os.path.join(FORGED, f"{name}.png"))
            made += 1
        print(f"  {set_name}: перековано из {src}.png")

    # Приёмник стыкуется с трубой своей горловиной — его куём. Колпак шины просто
    # накрывает деталь, портов у него нет, поэтому его оставляем нарисованным.
    for set_name in ("base",):
        src = None
        for name, meta in manifest.items():
            if meta.get("set") == set_name and meta.get("mask") == 5 and "straight" in name:
                src = name
                break
        if src is None:
            print("  горловина: нет исходной прямой трубы — приёмник остался нарисованным")
        else:
            profile = cut_profile(os.path.join(TILES, f"{src}.png"), thickness_px)
            img = forge_mouth(profile, SIZE, thickness_px).resize((TILE, TILE), Image.LANCZOS)
            img.save(os.path.join(FORGED, "mouth.png"))
            made += 1
            print(f"  горловина приёмника: перекована из {src}.png")

    print(f"готово: {made} деталей в art/tiles/forged/ (толщина {THICKNESS * 100:.1f}% тайла)")


if __name__ == "__main__":
    main()
