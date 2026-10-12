#!/usr/bin/env python3
"""Макет экрана победы со всеми шестью пломбами.

Зачем: правило выбора пломбы (chooseSeal) проверяется тестами, но «как это
выглядит» тест не покажет. Этот макет собирает экран победыsix раз — по одному
на каждую пломбу — тем же артом и теми же цветами, что использует приложение:
подложка `screens/screen-victory`, карточка цвета Grad.Panel, плашка цвета
Grad.Mint, медальон 104 dp (как в Compose).

Запуск: python3 art/tools/mock_seals.py
Результат: art/preview/seals-victory.jpg
"""
import os
from PIL import Image, ImageDraw, ImageFont

HERE = os.path.dirname(os.path.abspath(__file__))
ART = os.path.dirname(HERE)
REPO = os.path.dirname(ART)
BUILT = os.path.join(REPO, "packages", "lab", "public", "art")
OUT = os.path.join(ART, "preview", "seals-victory.jpg")

# Цвета — один в один с Android-темой (ui/Theme.kt) и веб-карточкой (style.css).
PANEL = (0x0D, 0x14, 0x21)
MINT = (0x00, 0xE5, 0xC7)
INK_DARK = (0x04, 0x21, 0x1D)
INK_LIGHT = (0xEA, 0xFF, 0xF9)
GOLD = (0xFF, 0xC2, 0x4B)
STAR_OFF = (0x22, 0x30, 0x3F)
DIM_COLOR = (0x03, 0x05, 0x0A)
DIM = 0.76  # затемнение подложки на экране победы (App.kt)

TITLE = "Квартал дышит светом"

# Пломба → (файл спрайта, подпись, звёзды, за что выдаётся).
SEALS = [
    ("reward-star.png", "ПЛОМБА", 3, "уложился в план"),
    ("reward-rings.png", "ПЛОМБА", 2, "до полутора планов"),
    ("reward-disc.png", "ПЛОМБА", 1, "остальное"),
    ("reward-resin.png", "ПЛОМБА · ЗАДЕЛАНО СМОЛОЙ", 2, "взята подсказка"),
    ("reward-crack.png", "ПЛОМБА · С ТРЕЩИНОЙ", 1, "ходов больше плана ×3"),
    ("reward-magma.png", "ПЛОМБА · ПЛАВЛЕНАЯ", 3, "3 звезды в Плавильне"),
]

CELL_W, CELL_H = 380, 470
COLS = 3
SEAL_SIZE = 104  # dp, как Modifier.size(104.dp) в App.kt


def font(path: str, size: int) -> ImageFont.FreeTypeFont:
    try:
        return ImageFont.truetype(path, size)
    except OSError:
        return ImageFont.load_default()


def load_backdrop() -> Image.Image:
    path = os.path.join(BUILT, "screens", "screen-victory.webp")
    img = Image.open(path).convert("RGB")
    # Кадр экрана — как в приложении: обрезаем по центру (Crop), а не растягиваем.
    scale = max(CELL_W / img.width, CELL_H / img.height)
    img = img.resize(
        (max(1, int(img.width * scale)), max(1, int(img.height * scale))),
        Image.LANCZOS,
    )
    left = (img.width - CELL_W) // 2
    top = (img.height - CELL_H) // 2
    img = img.crop((left, top, left + CELL_W, top + CELL_H))
    # Затемнение: подписи на граффити иначе не читаются.
    veil = Image.new("RGB", img.size, DIM_COLOR)
    return Image.blend(img, veil, DIM)


def card(backdrop: Image.Image, sprite: str, label: str, stars: int) -> Image.Image:
    tile = Image.new("RGB", (CELL_W, CELL_H), (0, 0, 0))
    tile.paste(backdrop, (0, 0))
    draw = ImageDraw.Draw(tile)

    # Карточка по ширине содержимого — как Column с padding 22 dp в App.kt.
    pad = 22
    seal = Image.open(os.path.join(ART, "tiles", sprite)).convert("RGBA")
    seal = seal.resize((SEAL_SIZE, SEAL_SIZE), Image.LANCZOS)

    f_label = font("/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf", 11)
    f_title = font("/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf", 17)
    f_star = font("/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf", 30)
    f_note = font("/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf", 12)

    lbox = draw.textbbox((0, 0), label, font=f_label)
    tbox = draw.textbbox((0, 0), TITLE, font=f_title)
    star_text = "★" * stars + "☆" * (3 - stars)
    sbox = draw.textbbox((0, 0), star_text, font=f_star)

    content_w = max(lbox[2] + 24, tbox[2], SEAL_SIZE, sbox[2])
    card_w = content_w + pad * 2
    card_h = (
        pad + (lbox[3] + 8) + 12 + tbox[3] + 10 + SEAL_SIZE + 6 + sbox[3] + pad
    )
    left = (CELL_W - card_w) // 2
    top = (CELL_H - card_h) // 2
    draw.rounded_rectangle(
        [left, top, left + card_w, top + card_h], radius=18, fill=PANEL
    )

    cx = left + card_w // 2
    y = top + pad

    # Плашка с названием пломбы (mint, скруглённая, текст тёмный).
    pill_w, pill_h = lbox[2] + 24, lbox[3] + 8
    draw.rounded_rectangle(
        [cx - pill_w / 2, y, cx + pill_w / 2, y + pill_h],
        radius=pill_h / 2,
        fill=MINT,
    )
    draw.text((cx - lbox[2] / 2, y + 4 - lbox[1]), label, font=f_label, fill=INK_DARK)
    y += pill_h + 12

    draw.text((cx - tbox[2] / 2, y - tbox[1]), TITLE, font=f_title, fill=INK_LIGHT)
    y += tbox[3] + 10

    tile.paste(seal, (int(cx - SEAL_SIZE / 2), y), seal)
    y += SEAL_SIZE + 6

    draw.text((cx - sbox[2] / 2, y - sbox[1]), star_text, font=f_star, fill=GOLD)
    return tile


def main() -> None:
    backdrop = load_backdrop()
    rows = (len(SEALS) + COLS - 1) // COLS
    sheet = Image.new("RGB", (CELL_W * COLS, CELL_H * rows), (0x06, 0x08, 0x0E))
    f_note = font("/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf", 13)

    for i, (sprite, label, stars, note) in enumerate(SEALS):
        tile = card(backdrop, sprite, label, stars)
        x, y = (i % COLS) * CELL_W, (i // COLS) * CELL_H
        sheet.paste(tile, (x, y))
        # Подпись под экраном: за что именно эта пломба.
        draw = ImageDraw.Draw(sheet)
        box = draw.textbbox((0, 0), note, font=f_note)
        draw.text((x + (CELL_W - box[2]) / 2, y + CELL_H - 24), note, font=f_note, fill=(0x9F, 0xB3, 0xC8))

    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    # JPEG: лист для просмотра человеком, а не ассет — 200 КБ вместо 490 КБ.
    sheet.save(OUT, "JPEG", quality=88, optimize=True)
    print(f"макет: {os.path.relpath(OUT, REPO)} ({sheet.width}×{sheet.height})")


if __name__ == "__main__":
    main()
