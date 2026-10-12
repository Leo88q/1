#!/usr/bin/env python3
"""Один лист-обзор всего арта: что сгенерировано, как называется и где лежит.

Для быстрого просмотра: не надо открывать 60 файлов — по одной картинке из каждой
группы плюс подписи. Полные кадры лежат рядом (см. art/README.md).

  * `preview/index.jpg` — обзор: фоны ярусов и экранов, поля, материалы, детали,
    награды, витрина;

Запуск: python3 art/tools/make_index.py        (нужен pillow)
"""
import os

from PIL import Image, ImageDraw, ImageFont

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
BG = (22, 21, 26)
PANEL = (30, 29, 35)
INK = (238, 238, 242)
DIM = (150, 150, 162)
ACID = (182, 255, 60)
CYAN = (22, 229, 217)
ORANGE = (255, 122, 26)

FONTS = {
    "bold": "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf",
    "regular": "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf",
    "mono": "/usr/share/fonts/truetype/dejavu/DejaVuSansMono-Bold.ttf",
}


def font(kind, size):
    try:
        return ImageFont.truetype(FONTS[kind], size)
    except OSError:
        return ImageFont.load_default()


# Группы: (заголовок, цвет, [(файл, подпись)], ширина кадра, высота кадра)
GROUPS = [
    ("ФОНЫ ЯРУСОВ · большая канализационная труба с граффити", CYAN, [
        ("backgrounds/tier-1-plateau.jpg", "Плато · a"),
        ("backgrounds/tier-1-plateau-b.jpg", "Плато · b"),
        ("backgrounds/tier-2-mercury.jpg", "Ртутный · a"),
        ("backgrounds/tier-2-mercury-b.jpg", "Ртутный · b"),
        ("backgrounds/tier-3-terminal.jpg", "Терминальный · a"),
        ("backgrounds/tier-3-terminal-b.jpg", "Терминальный · b"),
        ("backgrounds/tier-4-old-diode.jpg", "Старый Диод · a"),
        ("backgrounds/tier-4-old-diode-b.jpg", "Старый Диод · b"),
        ("backgrounds/tier-5-veins.jpg", "Жилы · a"),
        ("backgrounds/tier-5-veins-b.jpg", "Жилы · b"),
    ], 150, 269),
    ("ФОНЫ ЭКРАНОВ · под интерфейс, середина тёмная", CYAN, [
        ("backgrounds/screen-menu.jpg", "меню"),
        ("backgrounds/screen-victory.jpg", "победа"),
        ("backgrounds/screen-shop.jpg", "гардероб/магазин"),
        ("backgrounds/screen-wardrobe.jpg", "шкафчики"),
        ("backgrounds/screen-registry.jpg", "реестр"),
        ("backgrounds/screen-rating.jpg", "рейтинг"),
        ("backgrounds/screen-error.jpg", "ошибка/оффлайн"),
    ], 150, 269),
    ("ПОЛЕ · настоящие уровни движка, решение верно", ACID, [
        ("preview/level-3.jpg", "3 · Плато"),
        ("preview/level-9.jpg", "9 · Плато, кадр b"),
        ("preview/level-31.jpg", "31 · Терминальный"),
        ("preview/level-46.jpg", "46 · Старый Диод"),
        ("preview/level-61.jpg", "61 · Жилы"),
        ("preview/level-75.jpg", "75 · Жилы"),
    ], 150, 269),
    ("МАТЕРИАЛЫ ТРУБ · 8 наборов × 4 детали (лист целиком в tileset.jpg)", ORANGE, [
        ("pipes/tier-1-zinc.jpg", "цинк · Плато"),
        ("pipes/tier-1-zinc-b.jpg", "цинк с патиной · b"),
        ("pipes/tier-2-chrome.jpg", "хром · Ртутный"),
        ("pipes/tier-3-rust.jpg", "ржавчина · Терм."),
        ("pipes/tier-3-rust-b.jpg", "глуб. ржавчина · b"),
        ("pipes/tier-4-cast.jpg", "чугун · Ст. Диод"),
        ("pipes/tier-5-glass.jpg", "стекло · Жилы"),
        ("pipes/pieces-topdown.jpg", "канон · витрина"),
    ], 210, 115),
    ("НАГРАДЫ И СЛУЖЕБНЫЕ ДЕТАЛИ · листы preview/rewards.jpg и preview/props.jpg", ACID, [
        ("rewards/plombs-topdown.jpg", "пломбы квартала · 6"),
        ("promo/arrows-topdown.jpg", "указатели потока · 4"),
        ("promo/spare-topdown.jpg", "запасные детали · 4"),
        ("pipes/open-end-topdown.jpg", "приёмник"),
        ("pipes/cap-topdown.jpg", "колпак · шина"),
    ], 230, 125),
    ("ВИТРИНА И ПРОМО · готовые кадры с вордмарком", ORANGE, [
        ("promo/listing/og-ru-1200x630.jpg", "обложка ru"),
        ("promo/listing/feature-ru-1024x500.jpg", "фича ru"),
        ("promo/listing/season-ru-1024x500.jpg", "сезон ru"),
        ("promo/listing/square-ru-1080x1080.jpg", "квадрат ru"),
        ("promo/hero-city.jpg", "город · промо"),
        ("promo/season-key-art.jpg", "подъём воды · сезон"),
        ("promo/map-city.jpg", "карта ярусов"),
        ("promo/hero-control.jpg", "пульт · промо"),
    ], 300, 160),
]


def main():
    margin, gap, pad = 60, 18, 12
    columns = 10
    usable = 2400 - margin * 2
    cell_w = (usable - gap * (columns - 1)) // columns

    rows = []
    for title, color, items, w, h in GROUPS:
        rows.append((title, color, items, w, h))

    # Высота считается по фактическим строкам: число строк в группе × высота ячейки.
    total_h = 150
    for _title, _color, items, _w, h in rows:
        lines = (len(items) + columns - 1) // columns
        total_h += 56 + lines * (h + 30) + 24
    total_h += 60

    img = Image.new("RGB", (2400, total_h), BG)
    draw = ImageDraw.Draw(img)
    for x in range(0, img.width, 40):
        draw.line([(x, 0), (x, img.height)], fill=(32, 31, 37), width=1)
    for y in range(0, img.height, 40):
        draw.line([(0, y), (img.width, y)], fill=(32, 31, 37), width=1)

    draw.text((margin, 48), "АМПЕРГРАД · весь арт", font=font("bold", 64), fill=ACID)
    draw.text((margin, 124), "одна картинка из каждой группы · полные кадры — в art/README.md",
              font=font("mono", 26), fill=DIM)

    y = 190
    for title, color, items, w, h in rows:
        draw.text((margin, y), title, font=font("bold", 30), fill=color)
        y += 52
        for i, (path, caption) in enumerate(items):
            col, row = i % columns, i // columns
            x = margin + col * (cell_w + gap)
            cy = y + row * (h + 30)
            draw.rectangle([x - pad, cy - pad, x + cell_w + pad, cy + h + pad], fill=PANEL)
            try:
                art = Image.open(os.path.join(ROOT, path)).convert("RGB")
            except OSError:
                draw.text((x + 8, cy + h // 2), "нет файла", font=font("regular", 20), fill=DIM)
                continue
            art.thumbnail((cell_w, h), Image.LANCZOS)
            img.paste(art, (x + (cell_w - art.width) // 2, cy + (h - art.height) // 2))
            # Подпись обязана поместиться в свою ячейку: если не влезает в 20 px,
            # уменьшаем кегль, а не даём строке заехать под соседа.
            cap_font = font("regular", 20)
            size = 20
            while size > 13 and draw.textlength(caption, font=cap_font) > cell_w + 2 * pad:
                size -= 1
                cap_font = font("regular", size)
            draw.text((x + cell_w // 2, cy + h + 12), caption, font=cap_font,
                      fill=INK, anchor="ma")
        lines = (len(items) + columns - 1) // columns
        y += lines * (h + 30) + 24

    # Контроль: ни одна подпись не шире своей ячейки.
    over = []
    for _title, _color, items, _w, _h in rows:
        for path, caption in items:
            if ImageDraw.Draw(img).textlength(caption, font=font("regular", 20)) > cell_w + 2 * pad:
                over.append(caption)
    if over:
        print("подписи, которым не хватило места (кегль уменьшен):", ", ".join(over))

    out = os.path.join(ROOT, "preview", "index.jpg")
    img.save(out, "JPEG", quality=88)
    print("записано:", os.path.relpath(out, ROOT), img.size)


if __name__ == "__main__":
    main()
