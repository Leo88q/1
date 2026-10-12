#!/usr/bin/env python3
"""Витринные форматы из арта: обложка OG и фича-графика листинга.

Из исходников (`art/promo/og-amperograd.jpg`, `art/promo/hero-city.jpg`) делаются
готовые к загрузке кадры с вордмарком «АМПЕРГРАД» / «AMPERGRAD»:

  * `listing/og-{ru,en}-1200x630.jpg`      — карточка для ссылок и соцсетей;
  * `listing/feature-{ru,en}-1024x500.jpg` — фича-графика Solana dApp Store.

Текст рисуем сами (в генерациях буквы не используем): шрифты DejaVu, палитра
Guttercaps, вордмарк кладём на затемнение, чтобы читался на любом фоне.

Запуск: python3 art/tools/make_promo.py       (нужен pillow)
"""
import os

from PIL import Image, ImageDraw, ImageFilter, ImageFont

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
PROMO = os.path.join(ROOT, "promo")
OUT = os.path.join(PROMO, "listing")

ASPHALT = (22, 21, 26)
MAGENTA = (255, 46, 138)
CYAN = (22, 229, 217)
ACID = (182, 255, 60)
INK = (238, 238, 242)
DIM = (150, 150, 162)

FONTS = {
    "bold": ["/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf",
             "/System/Library/Fonts/Supplemental/Arial Bold.ttf"],
    "regular": ["/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf",
                "/System/Library/Fonts/Supplemental/Arial.ttf"],
    "mono": ["/usr/share/fonts/truetype/dejavu/DejaVuSansMono-Bold.ttf",
             "/System/Library/Fonts/Menlo.ttc"],
}

# Тексты листинга: русский (основной рынок) и английский (dApp Store).
TEXTS = {
    "ru": {
        "title": "АМПЕРГРАД",
        "tagline": "ВЕРНИ ГОРОДУ ТОК",
        "footer": "1–3 минуты · без кошелька · награды — сжатые NFT",
    },
    "en": {
        "title": "AMPERGRAD",
        "tagline": "BRING THE CURRENT BACK",
        "footer": "1–3 minutes · no wallet · rewards are compressed NFTs",
    },
}


def font(kind, size):
    for path in FONTS[kind]:
        try:
            return ImageFont.truetype(path, size)
        except OSError:
            continue
    return ImageFont.load_default()


def cover(source, size, texts, title_size, tagline_size, footer_size):
    """Кадр нужного формата + вордмарк на затемнении слева."""
    img = Image.open(source).convert("RGB")
    need = size[0] / size[1]
    have = img.width / img.height
    if have > need:                      # шире нужного — режем по бокам
        w = round(img.height * need)
        img = img.crop(((img.width - w) // 2, 0, (img.width + w) // 2, img.height))
    else:                                # выше нужного — режем сверху и снизу
        h = round(img.width / need)
        img = img.crop((0, (img.height - h) // 2, img.width, (img.height + h) // 2))
    img = img.resize(size, Image.LANCZOS)

    # Затемнение под текст: горизонтальный градиент слева направо.
    scrim = Image.new("RGBA", size, (0, 0, 0, 0))
    draw = ImageDraw.Draw(scrim)
    band = int(size[0] * 0.62)
    for x in range(band):
        alpha = int(228 * (1 - x / band) ** 1.35)
        draw.line([(x, 0), (x, size[1])], fill=(12, 11, 15, alpha))
    img = Image.alpha_composite(img.convert("RGBA"), scrim)

    draw = ImageDraw.Draw(img)
    x = int(size[0] * 0.055)
    f_title = font("bold", title_size)
    f_tag = font("mono", tagline_size)
    f_foot = font("mono", footer_size)

    y = int(size[1] * 0.30)
    draw.text((x, y), texts["title"], font=f_title, fill=ACID)
    y += title_size + int(title_size * 0.28)
    draw.rectangle([x, y, x + int(size[0] * 0.16), y + max(3, title_size // 22)], fill=MAGENTA)
    y += int(title_size * 0.42)
    draw.text((x, y), texts["tagline"], font=f_tag, fill=CYAN)
    y += tagline_size + int(tagline_size * 0.85)
    draw.text((x, y), texts["footer"], font=f_foot, fill=INK)

    # Кадрирующая рамка в палитре: тонкая линия яруса по периметру.
    draw.rectangle([0, 0, size[0] - 1, size[1] - 1], outline=(38, 37, 44), width=2)
    # Мягкая виньетка, чтобы кадр не смотрелся «сырым» на светлой витрине.
    vig = Image.new("L", size, 0)
    ImageDraw.Draw(vig).rectangle([int(size[0] * 0.04)] * 2 + [size[0], size[1]], fill=255)
    vig = vig.filter(ImageFilter.GaussianBlur(size[0] // 12))
    dark = Image.new("RGBA", size, (0, 0, 0, 120))
    dark.putalpha(Image.eval(vig, lambda v: 120 - v * 120 // 255))
    img = Image.alpha_composite(img, dark)
    return img.convert("RGB")


def main():
    os.makedirs(OUT, exist_ok=True)
    jobs = [
        ("og-amperograd.jpg", "og", (1200, 630), 92, 34, 24),
        ("hero-city.jpg", "feature", (1024, 500), 68, 26, 19),
    ]
    for source, kind, size, t, g, f in jobs:
        for lang, texts in TEXTS.items():
            name = f"{kind}-{lang}-{size[0]}x{size[1]}.jpg"
            path = os.path.join(OUT, name)
            cover(os.path.join(PROMO, source), size, texts, t, g, f).save(path, "JPEG", quality=92)
            print("записано:", os.path.relpath(path, ROOT))


if __name__ == "__main__":
    main()
