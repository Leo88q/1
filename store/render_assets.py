#!/usr/bin/env python3
"""Рисует ассеты витрины из настоящих данных движка (store/data/scenes.json).

Запуск:  npm run store:assets        (из корня репозитория)
Требует: pillow, шрифты DejaVu (есть в ubuntu-latest и в большинстве систем).

Скриншоты рисуются по кадрам игры (маски фрагментов, ток, ярусы), поэтому
показывают именно то, что видит игрок, а не художественную постановку.
"""
import json
import math
import os
from PIL import Image, ImageDraw, ImageFont

ROOT = os.path.dirname(os.path.abspath(__file__))
DATA = os.path.join(ROOT, "data", "scenes.json")
# Каталог косметики — тот же файл, что уходит в assets приложения.
COSMETICS = os.path.join(os.path.dirname(ROOT), "packages", "engine", "content", "cosmetics.json")

# Наборы для витрины: русский (основной рынок) и английский (листинг dApp Store).
TEXTS = {
    "ru": {
        "out": os.path.join(ROOT, "assets"),
        "moves": "ХОДЫ",
        "time": "ВРЕМЯ",
        "hint": "Подай ток на приёмники: все ячейки квартала должны быть подключены к шине",
        "buttons": ("СБРОС", "ПОДСКАЗКА", "МЕНЮ"),
        "footer": "АМПЕРГРАД · игра без кошелька и без токена",
        "stats": lambda moves, par, time: f"ходов {moves} · план {par} · {time}",
        "registry": "РЕЕСТР",
        "wardrobe": "ГАРДЕРОБ",
        "coins_word": "монет",
        "wardrobe_note": "Монеты зарабатываются в игре: 20 за собранный квартал плюс 10 за "
                         "каждую звезду. Косметика меняет только вид поля и ничего не даёт в игре.",
        "title": lambda scene: scene["title"],
        "subtitle": lambda scene: scene["subtitle"],
        "feature": ("АМПЕРГРАД", "ВЕРНИ ГОРОДУ ТОК", "1–3 минуты на квартал",
                    "без кошелька и без токена", "награды — сжатые NFT (cNFT)"),
    },
    "en": {
        "out": os.path.join(ROOT, "assets-en"),
        "moves": "MOVES",
        "time": "TIME",
        "hint": "Power the receivers: every cell of the district must reach the bus",
        "buttons": ("RESET", "HINT", "MENU"),
        "footer": "AMPERGRAD · no wallet, no token required",
        "stats": lambda moves, par, time: f"moves {moves} · par {par} · {time}",
        "registry": "REGISTRY",
        "registry": "REGISTRY",
        "wardrobe": "WARDROBE",
        "coins_word": "coins",
        "wardrobe_note": "Coins are earned by playing: 20 per cleared district plus 10 per star. "
                         "Cosmetics change the look of the field only and give no advantage.",
        "title": lambda scene: scene["titleEn"],
        "subtitle": lambda scene: scene["subtitleEn"],
        "feature": ("AMPERGRAD", "BRING THE CURRENT BACK", "1–3 minutes per district",
                    "no wallet, no token", "rewards are compressed NFTs (cNFTs)"),
    },
}

LANG = "ru"

BG = (6, 8, 14)
PANEL = (10, 16, 24)
LINE = (34, 48, 62)
MINT = (0, 229, 199)
GOLD = (255, 194, 75)
DANGER = (255, 61, 129)
INK = (234, 255, 249)
DIM = (107, 122, 143)

# Канонический вид поля: совпадает с BoardSkin.Default в приложении.
DEFAULT_SKIN = {
    "current": MINT,
    "idle": LINE,
    "hub": (22, 32, 47),
    "source": GOLD,
    "source_ring": GOLD,
    "grid": LINE,
    "field": (7, 11, 19),
}

FONT_DIR = "/usr/share/fonts/truetype/dejavu"
MONO = os.path.join(FONT_DIR, "DejaVuSansMono-Bold.ttf")
MONO_R = os.path.join(FONT_DIR, "DejaVuSansMono.ttf")
SANS = os.path.join(FONT_DIR, "DejaVuSans.ttf")


def font(path, size):
    try:
        return ImageFont.truetype(path, size)
    except OSError:
        return ImageFont.load_default()


def text(draw, xy, s, f, fill, anchor="la"):
    draw.text(xy, s, font=f, fill=fill, anchor=anchor)


def wrap(draw, s, f, max_width):
    words, lines, line = s.split(), [], ""
    for w in words:
        probe = (line + " " + w).strip()
        if draw.textlength(probe, font=f) <= max_width:
            line = probe
        else:
            if line:
                lines.append(line)
            line = w
    if line:
        lines.append(line)
    return lines


def hex_rgb(value):
    """Строка «#RRGGBB» в каноне → тройка (r, g, b)."""
    v = value.lstrip("#")
    return tuple(int(v[i:i + 2], 16) for i in (0, 2, 4))


def blend(a, b, part):
    """Смешивает два цвета: часть = 0 — первый, часть = 1 — второй."""
    t = max(0.0, min(1.0, part))
    return tuple(round(x + (y - x) * t) for x, y in zip(a, b))


def load_cosmetics():
    """Каталог магазина из канона (packages/engine/content/cosmetics.json)."""
    try:
        with open(COSMETICS, encoding="utf-8") as fh:
            return {item["id"]: item for item in json.load(fh)["cosmetics"]}
    except OSError:
        return {}


def wardrobe_skin(items, palette, theme, source):
    """Повторяет маппинг гардероба приложения (content/Wardrobe.kt):
    тема → фон и сетка; палитра → ток, подложка и ядро; источник → шина и ореол.
    Порядок важен: подложка палитры считается от фона, который задала тема."""
    skin = dict(DEFAULT_SKIN)
    th = items.get(theme, {}).get("colors", [])
    if th:
        skin["field"] = hex_rgb(th[2]) if len(th) >= 3 else DEFAULT_SKIN["field"]
        skin["grid"] = blend(skin["field"], hex_rgb(th[0]), 0.28)
    pal = items.get(palette, {}).get("colors", [])
    if pal:
        skin["current"] = hex_rgb(pal[0])
        skin["idle"] = blend(skin["field"], skin["current"], 0.30)
        skin["hub"] = blend(skin["idle"], skin["field"], 0.5)
    src = items.get(source, {}).get("colors", [])
    if src:
        skin["source"] = hex_rgb(src[0])
        skin["source_ring"] = hex_rgb(src[1]) if len(src) >= 2 else hex_rgb(src[0])
    return skin


def draw_board(draw, scene, x0, y0, side, skin=None):
    skin = skin or DEFAULT_SKIN
    size = scene["size"]
    cell = side / size
    draw.rounded_rectangle([x0, y0, x0 + side, y0 + side], radius=28, fill=skin["field"],
                           outline=skin["grid"], width=3)
    cx0, cy0 = x0 + cell / 2, y0 + cell / 2
    stroke = max(6, int(cell * 0.2))

    for c in scene["cells"]:
        cx, cy = cx0 + c["x"] * cell, cy0 + c["y"] * cell
        color = skin["current"] if c["powered"] else skin["idle"]
        for bit, (dx, dy) in ((1, (0, -1)), (2, (1, 0)), (4, (0, 1)), (8, (-1, 0))):
            if c["mask"] & bit:
                ex, ey = cx + dx * cell * 0.5, cy + dy * cell * 0.5
                draw.line([cx, cy, ex, ey], fill=color, width=stroke)
        r = stroke * 0.45
        draw.ellipse([cx - r, cy - r, cx + r, cy + r], fill=color)

    # Шина (источник тока) и приёмники — как в игре.
    bus = next((c for c in scene["cells"] if c["role"] == "bus"), None)
    if bus:
        bx, by = cx0 + bus["x"] * cell, cy0 + bus["y"] * cell
        rr = cell * 0.28
        draw.ellipse([bx - rr * 1.6, by - rr * 1.6, bx + rr * 1.6, by + rr * 1.6],
                     outline=skin["source_ring"], width=4)
        draw.ellipse([bx - rr, by - rr, bx + rr, by + rr], fill=skin["source"])
    for c in scene["cells"]:
        if c["role"] != "receiver":
            continue
        rx, ry = cx0 + c["x"] * cell, cy0 + c["y"] * cell
        rr = cell * 0.3
        draw.ellipse([rx - rr, ry - rr, rx + rr, ry + rr], outline=DANGER, width=max(4, int(cell * 0.08)))


def header(draw, scene, width, txt, top=70):
    f_title = font(MONO, 46)
    f_sub = font(MONO_R, 26)
    f_stat = font(MONO, 28)
    text(draw, (60, top), txt["subtitle"](scene), f_sub, MINT)
    text(draw, (60, top + 38), txt["title"](scene), f_title, INK)
    stats = txt["stats"](scene['stats']['moves'], scene['stats']['par'], scene['stats']['time'])
    text(draw, (60, top + 104), stats, f_stat, DIM)


def buttons(draw, width, y, labels):
    f = font(MONO, 28)
    gap, w = 30, (width - 120 - 60) / 3
    for i, label in enumerate(labels):
        x = 60 + i * (w + gap)
        draw.rounded_rectangle([x, y, x + w, y + 110], radius=16, outline=LINE, width=3)
        text(draw, (x + w / 2, y + 55), label, f, INK, anchor="mm")


def screenshot(scene, path, txt):
    width, height = 1080, 1920
    img = Image.new("RGB", (width, height), BG)
    draw = ImageDraw.Draw(img)

    header(draw, scene, width, txt)
    side = 900
    draw_board(draw, scene, (width - side) / 2, 260, side)

    if scene.get("stars"):
        f_star = font(MONO, 92)
        stars = "★" * scene["stars"] + "☆" * (3 - scene["stars"])
        text(draw, (width / 2, 1290), stars, f_star, GOLD, anchor="mm")
        entry = scene.get("registry") if LANG == "ru" else scene.get("registryEn")
        if entry:
            f_label = font(MONO, 24)
            f_txt = font(SANS, 30)
            f_title = font(MONO, 32)
            box = [80, 1360, width - 80, 1720]
            draw.rounded_rectangle(box, radius=20, fill=PANEL, outline=LINE, width=2)
            text(draw, (110, 1394), entry["label"], f_label, MINT)
            text(draw, (110, 1432), entry["title"], f_title, INK)
            y = 1486
            for line in wrap(draw, f"«{entry['text']}»", f_txt, width - 220)[:3]:
                text(draw, (110, y), line, f_txt, INK)
                y += 40
    else:
        f_hint = font(SANS, 30)
        lines = wrap(draw, txt["hint"], f_hint, width - 160)
        y = 1250
        for line in lines:
            text(draw, (width / 2, y), line, f_hint, DIM, anchor="ma")
            y += 42
        f_progress = font(MONO, 32)
        note = "долгое удержание — поворот против часовой" if LANG == "ru" else "press and hold to turn counter-clockwise"
        text(draw, (width / 2, y + 30), note, f_hint, (75, 90, 108), anchor="ma")

    buttons(draw, width, 1740, txt["buttons"])
    text(draw, (width / 2, 1878), txt["footer"], font(SANS, 26), DIM, anchor="mm")
    img.save(path, "PNG")
    return path


def icon(path, size=512):
    """Знак игры: перекрёсток квартала (ток + память + пломба).
    Тот же знак лежит в Android как vector drawable (adaptive icon)."""
    img = Image.new("RGB", (size, size), BG)
    draw = ImageDraw.Draw(img)
    step = size / 8
    for i in range(9):
        draw.line([(i * step, 0), (i * step, size)], fill=(10, 18, 26), width=2)
        draw.line([(0, i * step), (size, i * step)], fill=(10, 18, 26), width=2)

    m = size * 0.125
    draw.rounded_rectangle([m, m, size - m, size - m], radius=size * 0.215, fill=(7, 11, 19),
                           outline=MINT, width=max(8, size // 28))

    c = size / 2
    stroke = max(14, size // 8)
    span = size * 0.26
    r = stroke // 2
    for x1, y1, x2, y2, color in (
        (c - span, c, c + span, c, MINT),
        (c, c - span, c, c + span, GOLD),
    ):
        draw.line([(x1, y1), (x2, y2)], fill=color, width=stroke)
        for x, y in ((x1, y1), (x2, y2)):
            draw.ellipse([x - r, y - r, x + r, y + r], fill=color)

    ring = size * 0.09
    draw.ellipse([c - ring, c - ring, c + ring, c + ring], fill=(7, 11, 19), outline=INK,
                 width=max(6, size // 36))
    img.save(path, "PNG")
    return path


def circuit_fragment(pieces, bus, receivers):
    """Ток считаем обходом от шины: ячейка под током, если соединена с источником
    (порты соседей совпадают). Так кадр витрины остаётся честным."""
    by_cell = {(x, y): mask for x, y, mask in pieces}
    seen, queue = {bus}, [bus]
    while queue:
        x, y = queue.pop()
        for bit, (dx, dy) in ((1, (0, -1)), (2, (1, 0)), (4, (0, 1)), (8, (-1, 0))):
            if not by_cell[(x, y)] & bit:
                continue
            nx, ny = x + dx, y + dy
            back = {1: 4, 2: 8, 4: 1, 8: 2}[bit]
            if (nx, ny) in by_cell and by_cell[(nx, ny)] & back and (nx, ny) not in seen:
                seen.add((nx, ny))
                queue.append((nx, ny))
    return seen


def feature_graphic(path, txt):
    width, height = 1024, 500
    img = Image.new("RGB", (width, height), BG)
    draw = ImageDraw.Draw(img)
    for i in range(0, width, 44):
        draw.line([(i, 0), (i, height)], fill=(9, 14, 21), width=2)
    for i in range(0, height, 44):
        draw.line([(0, i), (width, i)], fill=(9, 14, 21), width=2)

    f_title = font(MONO, 72)
    f_tag = font(MONO_R, 30)
    f_small = font(SANS, 25)
    title, tag, line1, line2, line3 = txt["feature"]
    text(draw, (56, 118), title, f_title, INK)
    text(draw, (58, 216), tag, f_tag, MINT)
    text(draw, (58, 286), line1, f_small, DIM)
    text(draw, (58, 322), line2, f_small, DIM)
    text(draw, (58, 358), line3, f_small, DIM)

    # Квартал: настоящий контур (кольцо шина → приёмник) и обесточенные фрагменты рядом.
    x0, y0, cell = 636, 96, 76
    draw.rounded_rectangle([x0 - 26, y0 - 26, x0 + cell * 4 + 26, y0 + cell * 4 + 26], radius=24,
                           fill=(8, 12, 20), outline=LINE, width=3)
    pieces = [
        (0, 1, 6), (1, 1, 10), (2, 1, 12), (2, 2, 5), (2, 3, 9), (1, 3, 10), (0, 3, 3), (0, 2, 5),
        (3, 0, 9), (3, 2, 5), (1, 2, 4), (3, 3, 7),
    ]
    bus, receivers = (1, 3), {(0, 2)}
    powered = circuit_fragment(pieces, bus, receivers)

    for gx, gy, mask in pieces:
        cx, cy = x0 + gx * cell + cell / 2, y0 + gy * cell + cell / 2
        color = MINT if (gx, gy) in powered else (34, 48, 62)
        if (gx, gy) in receivers:
            color = MINT
        for bit, (dx, dy) in ((1, (0, -1)), (2, (1, 0)), (4, (0, 1)), (8, (-1, 0))):
            if mask & bit:
                draw.line([cx, cy, cx + dx * cell / 2, cy + dy * cell / 2], fill=color, width=15)
        draw.ellipse([cx - 8, cy - 8, cx + 8, cy + 8], fill=color)
    bx, by = x0 + bus[0] * cell + cell / 2, y0 + bus[1] * cell + cell / 2
    draw.ellipse([bx - 22, by - 22, bx + 22, by + 22], outline=GOLD, width=5)
    draw.ellipse([bx - 13, by - 13, bx + 13, by + 13], fill=GOLD)
    for gx, gy in receivers:
        rx, ry = x0 + gx * cell + cell / 2, y0 + gy * cell + cell / 2
        draw.ellipse([rx - 20, ry - 20, rx + 20, ry + 20], outline=DANGER, width=6)
    img.save(path, "PNG")
    return path


def swatch(draw, box, colors):
    """Образец предмета: основной цвет, окантовка — второй."""
    fill = hex_rgb(colors[0])
    edge = hex_rgb(colors[1]) if len(colors) > 1 else fill
    draw.rounded_rectangle(box, radius=16, fill=fill, outline=edge, width=4)


def wardrobe_shot(scene, path, txt, skin, worn, lang):
    """Кадр «гардероб»: поле с надетой косметикой, купленной за монеты.

    Поле рисуется теми же правилами, что в приложении (тот же кадр, та же
    раскладка), а строки берутся из каталога: название предмета и цена в монетах.
    """
    width, height = 1080, 1920
    img = Image.new("RGB", (width, height), BG)
    draw = ImageDraw.Draw(img)

    header(draw, scene, width, txt)
    side = 900
    draw_board(draw, scene, (width - side) / 2, 260, side, skin)

    f_label = font(MONO, 30)
    f_name = font(SANS, 34)
    f_price = font(MONO, 26)
    f_note = font(SANS, 28)
    text(draw, (80, 1230), txt["wardrobe"], f_label, MINT)

    y = 1290
    for item in worn:
        swatch(draw, [80, y, 176, y + 96], item.get("colors", ["#00E5C7"]))
        name = item["name"] if lang == "ru" else (item.get("nameEn") or item["name"])
        text(draw, (202, y + 8), name, f_name, INK)
        price = item.get("priceCoins")
        if price:
            text(draw, (202, y + 56), f"{price} {txt['coins_word']}", f_price, GOLD)
        y += 124

    note_y = y + 16
    for line in wrap(draw, txt["wardrobe_note"], f_note, width - 160):
        text(draw, (80, note_y), line, f_note, DIM)
        note_y += 38

    text(draw, (width / 2, 1878), txt["footer"], font(SANS, 26), DIM, anchor="mm")
    img.save(path, "PNG")
    return path


def main():
    global LANG
    import sys

    if "--lang" in sys.argv:
        LANG = sys.argv[sys.argv.index("--lang") + 1]
    txt = TEXTS[LANG]
    out = txt["out"]
    os.makedirs(out, exist_ok=True)

    with open(DATA, encoding="utf-8") as fh:
        data = json.load(fh)

    for scene in data["scenes"]:
        path = os.path.join(out, f"{scene['id']}.png")
        screenshot(scene, path, txt)
        print("записано:", path)

    # Кадр гардероба: поле с надетой косметикой, купленной за монеты.
    items = load_cosmetics()
    worn = [items[i] for i in ("palette-mercury", "theme-plateau", "source-gold") if i in items]
    if worn:
        skin = wardrobe_skin(items, "palette-mercury", "theme-plateau", "source-gold")
        scene = next((s for s in data["scenes"] if not s.get("stars")), data["scenes"][0])
        path = os.path.join(out, "screenshot-4.png")
        print("записано:", wardrobe_shot(scene, path, txt, skin, worn, LANG))
    print("записано:", icon(os.path.join(out, "icon-512.png")))
    print("записано:", feature_graphic(os.path.join(out, "feature-graphic-1024x500.png"), txt))


if __name__ == "__main__":
    main()
