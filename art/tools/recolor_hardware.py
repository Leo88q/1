#!/usr/bin/env python3
"""
Фурнитура поля — нейтральная сталь: горловина приёмника и колпак шины.

Зачем: эти две детали стояли на поле инородными предметами. Они нарисованы
бронзой (у колпака треть пикселей в зоне 30°, у горловины — красные и
оранжевые акценты), а бронзы нет в палитре Guttercaps: asphalt #16151A,
magenta #FF2E8A, cyan #16E5D9, acid #B6FF3C, orange #FF7A1A. На первом уровне
две горловины спорили с цинковыми трубами сильнее всего.

Фурнитура — не труба: она не обязана повторять материал яруса (цинк, хром,
ржавчина, чугун, стекло), поэтому красится в нейтральную сталь. Сталь не
спорит ни с одним ярусом, а цвет на поле несут среда, ток и отметка цели.
Геометрию не трогаем: меняется только цвет, стыки остаются прежними.

Запуск: python3 art/tools/recolor_hardware.py        (нужен pillow + numpy)
"""
import os

import numpy as np
from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
ART = os.path.dirname(HERE)
TILES = os.path.join(ART, "tiles")

# Что перекрашиваем и сколько цвета оставляем (0 — совсем серое, 1 — как было).
HARDWARE = {
    "mouth.png": 0.12,  # горловина приёмника: было много красного и оранжевого
    "cap.png": 0.15,  # колпак шины: треть пикселей — бронза
}


def steel(path: str, keep: float) -> tuple[float, float]:
    """Гасит насыщенность, сохраняя яркость: деталь остаётся читаемой, но нейтральной."""
    im = Image.open(path).convert("RGBA")
    data = np.array(im).astype(np.float32)
    rgb = data[..., :3] / 255.0
    # Яркость по восприятию: чтобы после обесцвечивания деталь не потемнела.
    lum = (0.299 * rgb[..., 0] + 0.587 * rgb[..., 1] + 0.114 * rgb[..., 2])[..., None]
    flat = lum + (rgb - lum) * keep
    data[..., :3] = np.clip(flat * 255.0, 0, 255)
    out = Image.fromarray(data.astype(np.uint8), "RGBA")
    out.save(path)

    # Насколько бронзы стало меньше: доля пикселей с тёплым и насыщенным оттенком.
    def warm_share(arr: np.ndarray) -> float:
        r, g, b = arr[..., 0], arr[..., 1], arr[..., 2]
        mx = arr.max(-1)
        mn = arr.min(-1)
        sat = np.where(mx > 0, (mx - mn) / np.maximum(mx, 1e-6), 0.0)
        warm = (r >= g) & (g >= b)
        alpha = im.split()[3]
        visible = np.array(alpha) > 200
        return float(((sat > 0.18) & warm & visible).sum()) / max(1, int(visible.sum()))

    return warm_share(np.array(im).astype(np.float32)[..., :3] / 255.0), warm_share(flat)


def main() -> None:
    for name, keep in HARDWARE.items():
        path = os.path.join(TILES, name)
        if not os.path.exists(path):
            print(f"нет файла: {name}")
            continue
        before, after = steel(path, keep)
        print(f"{name}: тёплых насыщенных пикселей {before * 100:.0f}% → {after * 100:.0f}%")


if __name__ == "__main__":
    main()
