#!/usr/bin/env python3
"""Макет игрового поля «АМПЕРГРАД» в стиле Guttercaps: трубы, фон-канализация, ток.

Что делает:
  1. берёт спрайты из art/tiles/ (их нарезает art/tools/cut_pipes.py);
  2. берёт настоящий уровень из packages/engine/vectors/levels.json (маски, шина,
     приёмники, повороты решения) — то есть кадр не постановочный, а собранный по
     правилам игры;
  3. рисует поле: ячейки с трубами, свечение текущей среды по трубам, приёмники
     (открытые концы, из которых течёт) и шину (источник), фон яруса;
  4. добавляет интерфейсную рамку (название квартала, счётчики) и сохраняет кадр
     в art/preview/.

Запуск: python3 art/tools/mock_board.py        (нужен pillow)
"""
import json
import math
import os

from PIL import Image, ImageDraw, ImageFilter, ImageFont

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
REPO = os.path.dirname(ROOT)
TILES = os.path.join(ROOT, "tiles")
OUT = os.path.join(ROOT, "preview")

# Палитра Guttercaps (их дизайн-система) — под неё и рисуем.
ASPHALT = (22, 21, 26)
MAGENTA = (255, 46, 138)
CYAN = (22, 229, 217)
ACID = (182, 255, 60)
ORANGE = (255, 122, 26)
INK = (238, 238, 242)
DIM = (140, 140, 152)

FONT_CANDIDATES = {
    "bold": [
        "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf",
        "/System/Library/Fonts/Supplemental/Arial Bold.ttf",
        "/System/Library/Fonts/Helvetica.ttc",
    ],
    "mono": [
        "/usr/share/fonts/truetype/dejavu/DejaVuSansMono-Bold.ttf",
        "/System/Library/Fonts/Menlo.ttc",
    ],
    "regular": [
        "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf",
        "/System/Library/Fonts/Supplemental/Arial.ttf",
    ],
}

# Кадры, которые собираем: уровень → ярус (фон + материал труб + цвет среды).
# Ярусы идут как в контенте: plateau → mercury → terminal → old-diode → veins.
TIERS = {
    # `metal` — основной материал яруса, `metal2` — второй (есть не у всех):
    # вариант кадра (a/b) берёт либо один, либо пару «основной + второй».
    "plateau": {"bg": ["tier-1-plateau.jpg", "tier-1-plateau-b.jpg"], "metal": "zinc",
                "metal2": "zinc2", "flow": (182, 255, 60)},
    "mercury": {"bg": ["tier-2-mercury.jpg", "tier-2-mercury-b.jpg"], "metal": "chrome",
                "flow": (22, 229, 217)},
    "terminal": {"bg": ["tier-3-terminal.jpg", "tier-3-terminal-b.jpg"], "metal": "rust",
                 "metal2": "rust2", "flow": (255, 122, 26)},
    "old-diode": {"bg": ["tier-4-old-diode.jpg", "tier-4-old-diode-b.jpg"], "metal": "cast",
                  "flow": (255, 176, 64)},
    "veins": {"bg": ["tier-5-veins.jpg", "tier-5-veins-b.jpg"], "metal": "glass",
              "flow": (182, 255, 60)},
}

SHOTS = [
    {"level": 3, "tier": "plateau", "variant": 0, "title": "ЛИЦЕНЗИОННЫЙ СВЕТ", "titleEn": "Licensed Light"},
    {"level": 9, "tier": "plateau", "variant": 1, "title": "СМОТРОВАЯ ПЛОЩАДКА", "titleEn": "The Viewing Deck"},
    {"level": 31, "tier": "terminal", "variant": 1, "title": "ДИСПЕТЧЕРСКАЯ", "titleEn": "Dispatch Room"},
    {"level": 46, "tier": "old-diode", "variant": 0, "title": "СУД НАД СВЕТОМ", "titleEn": "The Trial of Light"},
    {"level": 61, "tier": "veins", "variant": 0, "title": "ЖИЛА № 7", "titleEn": "Vein Number Seven"},
    {"level": 75, "tier": "veins", "variant": 1, "title": "ПОСЛЕДНИЙ КОНТУР", "titleEn": "The Last Circuit"},
]

# Фон чертежа/листа спрайтов.
SHEET_BG = (26, 25, 30)


def font(kind, size):
    for path in FONT_CANDIDATES[kind]:
        try:
            return ImageFont.truetype(path, size)
        except OSError:
            continue
    return ImageFont.load_default()


def rotate_cw_steps(mask, steps):
    """Маска после steps поворотов по часовой — та же формула, что в движке."""
    m = mask & 0xF
    for _ in range(steps % 4):
        m = ((m << 1) | (m >> 3)) & 0xF
    return m


def load_tiles():
    """Спрайты и их маски; из нескольких прямых берём ту, что гуще по краям."""
    with open(os.path.join(TILES, "manifest.json"), encoding="utf-8") as fh:
        manifest = json.load(fh)
    sprites = {}
    for name, info in manifest.items():
        path = os.path.join(REPO, info["file"])
        if not os.path.exists(path):  # manifest пишет путь от корня репозитория
            path = os.path.join(REPO, "art", info["file"])
        sprites[name] = {
            "image": Image.open(path).convert("RGBA"),
            "mask": info["mask"],
        }
    # Заглушки (маска 1) в каталоге нет — собираем её из прямой трубы.
    for name in list(sprites):
        if name.endswith("-straight"):
            sprites[name.replace("-straight", "-stub")] = make_stub(sprites[name])
    return sprites


def make_stub(straight):
    """Заглушка: половина прямой трубы — труба входит с севера и кончается в центре.

    Маска такой детали в движке — 1 (единственный порт), поэтому игровое поле
    доворачивает её так же, как любой другой спрайт.
    """
    art = straight["image"].copy()
    width, height = art.size
    alpha = art.getchannel("A")
    half = alpha.crop((0, 0, width, height // 2))
    alpha.paste(0, (0, height // 2, width, height))
    alpha.paste(half, (0, height // 2, width, height))
    art.putalpha(alpha)
    return {"image": art, "mask": 1}


def sprite_for(mask, sprites, material, variant=0):
    """Какой спрайт рисует ячейку: прямая, отвод, тройник, крест или заглушка.

    Материал выбирается ярусом: цинк в Плато, хром в Ртутном, ржавчина в
    Терминальном, чугун в Старом Диоде, стекло в Жилах. Там, где для яруса есть
    второй материал (`metal2`), вариант кадра отдаёт половину ячеек ему — поле
    одного яруса перестаёт выглядеть клоном поля из соседнего уровня.
    """
    families = {5: ["straight", "straight2"], 3: ["elbow"], 7: ["tee"], 15: ["cross"], 1: ["stub"]}
    materials = [material]
    if isinstance(material, (list, tuple)):
        materials = list(material)
    names = []
    for mat in materials:
        for piece in families[mask]:
            for candidate in (f"{mat}-{piece}", f"base-{piece}"):
                if candidate in sprites:
                    names.append(candidate)
    if not names:
        raise SystemExit(f"нет спрайта для маски {mask} (материал {material})")
    return sprites[names[variant % len(names)]]


def rotation_steps(sprite_mask, target_mask):
    """Сколько поворотов по часовой нужно, чтобы маска спрайта стала целевой."""
    for steps in range(4):
        if rotate_cw_steps(sprite_mask, steps) == target_mask:
            return steps
    raise SystemExit(f"маска спрайта {sprite_mask} не превращается в {target_mask}")


def ports_of(mask):
    return [bit for bit in (1, 2, 4, 8) if mask & bit]


def level_of(number):
    with open(os.path.join(REPO, "packages/engine/vectors/levels.json"), encoding="utf-8") as fh:
        data = json.load(fh)
    for lv in data["levels"]:
        if lv["level"] == number:
            return lv
    raise SystemExit(f"в векторах нет уровня {number}")


def make_background(path, size):
    """Фон яруса: кадр заполняет холст, притемняется, чтобы трубы читались."""
    width, height = size
    bg = Image.open(path).convert("RGB")
    scale = max(width / bg.width, height / bg.height)
    bg = bg.resize((round(bg.width * scale), round(bg.height * scale)), Image.LANCZOS)
    left = (bg.width - width) // 2
    top = (bg.height - height) // 2
    bg = bg.crop((left, top, left + width, top + height))
    dark = Image.new("RGB", (width, height), (0, 0, 0))
    bg = Image.blend(bg, dark, 0.42)
    # Общая цветокоррекция в сторону асфальта: чуть приглушаем и добавляем холода.
    tint = Image.new("RGB", (width, height), ASPHALT)
    bg = Image.blend(bg, tint, 0.18)
    return bg


def glow_line(size, width, color, alpha):
    """Мягкая полоса: рисуем на слое, размываем, потом кладём поверх."""
    layer = Image.new("RGBA", size, (0, 0, 0, 0))
    return layer, ImageDraw.Draw(layer)


def draw_flow(base, cell_box, mask, cell, color, strength=1.0):
    """Среда внутри трубы: узкая полоса от центра ячейки к её портам.

    Светим именно трубой — тёмная кромка по краям полосы оставляет видимой
    металлическую стенку, а лёгкий ореол читается как течение в глубине.
    """
    x0, y0, x1, y1 = cell_box
    cx, cy = (x0 + x1) // 2, (y0 + y1) // 2
    width = max(4, int(cell * 0.19))
    core = Image.new("RGBA", base.size, (0, 0, 0, 0))
    halo = Image.new("RGBA", base.size, (0, 0, 0, 0))
    draw = ImageDraw.Draw(core)
    glow = ImageDraw.Draw(halo)
    ends = {1: (cx, y0), 4: (cx, y1), 2: (x1, cy), 8: (x0, cy)}
    for bit in ports_of(mask):
        ex, ey = ends[bit]
        draw.line([(cx, cy), (ex, ey)], fill=color + (int(210 * strength),), width=int(width * 0.62))
        glow.line([(cx, cy), (ex, ey)], fill=color + (int(60 * strength),), width=width)
    base.alpha_composite(halo.filter(ImageFilter.GaussianBlur(cell * 0.09)))
    base.alpha_composite(core)


def draw_receiver(base, cell_box, cell, sprite, mask, flow=ACID):
    """Приёмник: струя из горловины, поверх неё — открытый конец трубы.

    Открытый конец разворачивается так же, как труба ячейки: горловина смотрит
    наружу (на север при единственном порте сверху и так далее), поэтому струя
    выходит «из глубины» детали, а не поверх неё.
    """
    x0, y0, x1, y1 = cell_box
    cx, cy = (x0 + x1) // 2, (y0 + y1) // 2

    layer = Image.new("RGBA", base.size, (0, 0, 0, 0))
    halo = Image.new("RGBA", base.size, (0, 0, 0, 0))
    draw = ImageDraw.Draw(layer)
    glow = ImageDraw.Draw(halo)
    top = cy + int(cell * 0.12)
    bottom = y1 + int(cell * 0.5)
    w = max(3, int(cell * 0.075))
    draw.line([(cx, top), (cx, bottom)], fill=flow + (185,), width=w)
    glow.line([(cx, top), (cx, bottom)], fill=flow + (55,), width=int(w * 3))
    for k in range(3):
        dy = y1 + int(cell * (0.58 + 0.2 * k))
        rr = max(2, int(cell * (0.038 - 0.009 * k)))
        draw.ellipse([cx - rr, dy - rr, cx + rr, dy + rr], fill=flow + (140 - 26 * k,))
    base.alpha_composite(halo.filter(ImageFilter.GaussianBlur(cell * 0.08)))
    base.alpha_composite(layer)

    if mask & 1:
        art = sprite.copy().rotate(180, expand=False)
    elif mask & 2:
        art = sprite.copy().rotate(-90, expand=False)
    elif mask & 8:
        art = sprite.copy().rotate(90, expand=False)
    else:
        art = sprite.copy()
    side = int(cell * 0.94)
    art.thumbnail((side, side), Image.LANCZOS)
    base.alpha_composite(art, (cx - art.width // 2, cy - art.height // 2))


def draw_source(base, cell_box, cell, sprite_cap, mask, sprite_pipe):
    """Шина (источник): тройник с колпаком и жёстким ореолом — отсюда идёт среда."""
    x0, y0, x1, y1 = cell_box
    cx, cy = (x0 + x1) // 2, (y0 + y1) // 2

    # Короткий патрубок от шины к её порту, чтобы источник «сидел» в сети.
    if sprite_pipe is not None:
        steps = rotation_steps(sprite_pipe["mask"], mask)
        art = sprite_pipe["image"].rotate(-90 * steps, expand=False)
        art = art.resize((cell, cell), Image.LANCZOS)
        base.alpha_composite(art, (x0, y0))

    halo = Image.new("RGBA", base.size, (0, 0, 0, 0))
    draw = ImageDraw.Draw(halo)
    for radius, alpha, color in (
        (cell * 0.44, 34, MAGENTA),
        (cell * 0.32, 58, ORANGE),
    ):
        draw.ellipse([cx - radius, cy - radius, cx + radius, cy + radius], fill=color + (alpha,))
    base.alpha_composite(halo.filter(ImageFilter.GaussianBlur(cell * 0.14)))
    base.alpha_composite(halo.filter(ImageFilter.GaussianBlur(cell * 0.03)))

    cap = sprite_cap.copy()
    side = int(cell * 0.62)
    cap.thumbnail((side, side), Image.LANCZOS)
    base.alpha_composite(cap, (cx - cap.width // 2, cy - cap.height // 2))


def frame_ui(img, shot, level, cell_px):
    """Интерфейсная рамка кадра: заголовок уровня, счётчики, подписи снизу."""
    draw = ImageDraw.Draw(img, "RGBA")
    width, height = img.size

    # Верхняя плашка.
    draw.rectangle([0, 0, width, 190], fill=(12, 12, 14, 205))
    draw.line([(0, 190), (width, 190)], fill=MAGENTA + (170,), width=3)
    draw.text((48, 46), f"УРОВЕНЬ {level['level']}", font=font("mono", 44), fill=CYAN)
    draw.text((48, 104), f"{shot['title']} · {shot['titleEn']}", font=font("bold", 40), fill=INK)
    draw.text((48, 152), f"ХОДЫ 0 / ПЛАН {level['parMoves']}   ·   ВРЕМЯ 0:38", font=font("mono", 30), fill=DIM)

    # Нижняя плашка с подсказкой.
    draw.rectangle([0, height - 210, width, height], fill=(12, 12, 14, 205))
    draw.line([(0, height - 210), (width, height - 210)], fill=CYAN + (150,), width=3)
    hint = "СОБЕРИ СТОК: все ячейки квартала — к шине"
    draw.text((48, height - 178), hint, font=font("bold", 30), fill=INK)
    draw.text(
        (48, height - 128),
        "тап — по часовой · удержание — против часовой · приёмников: "
        f"{len(level['receivers'])} · поле {level['size']}×{level['size']}",
        font=font("regular", 26),
        fill=DIM,
    )
    for i, label in enumerate(("СБРОС", "ПОДСКАЗКА", "МЕНЮ")):
        bx = 48 + i * ((width - 96 - 2 * 24) / 3 + 24)
        bw = (width - 96 - 2 * 24) / 3
        draw.rectangle([bx, height - 92, bx + bw, height - 40], outline=DIM + (140,), width=2)
        text_w = draw.textlength(label, font=font("mono", 26))
        draw.text((bx + (bw - text_w) / 2, height - 78), label, font=font("mono", 26), fill=INK)


def render_level(shot, sprites, size=(1080, 1920)):
    level = level_of(shot["level"])
    tier = TIERS[shot["tier"]]
    # Материал поля: основной берём всегда, второй — если у яруса он есть и кадр
    # идёт вариантом b. Тогда два фона яруса дают ещё и два разных металла.
    if shot["variant"] % 2 == 1 and tier.get("metal2"):
        material = [tier["metal"], tier["metal2"]]
    else:
        material = tier["metal"]
    flow = tier["flow"]
    width, height = size
    bg_file = tier["bg"][shot["variant"] % len(tier["bg"])]
    bg = make_background(os.path.join(ROOT, "backgrounds", bg_file), size).convert("RGBA")
    img = bg.copy()

    # Поле: квадрат по центру, стеклянная плита под сетку.
    board_side = int(width * 0.86)
    bx = (width - board_side) // 2
    by = 260
    grid = level["size"]
    cell = board_side // grid
    cell = int(cell)

    plate = Image.new("RGBA", size, (0, 0, 0, 0))
    pd = ImageDraw.Draw(plate)
    pd.rounded_rectangle(
        [bx - 16, by - 16, bx + board_side + 16, by + board_side + 16],
        radius=28,
        fill=(14, 14, 17, 170),
        outline=(120, 120, 132, 90),
        width=2,
    )
    img.alpha_composite(plate)

    # Сетка квартала.
    grid_layer = Image.new("RGBA", size, (0, 0, 0, 0))
    gd = ImageDraw.Draw(grid_layer)
    for i in range(grid + 1):
        x = bx + i * cell
        y = by + i * cell
        gd.line([(x, by), (x, by + board_side)], fill=(255, 255, 255, 16), width=1)
        gd.line([(bx, y), (bx + board_side, y)], fill=(255, 255, 255, 16), width=1)
    img.alpha_composite(grid_layer)

    bus = level["bus"]
    receivers = set(level["receivers"])

    # Слой труб: сначала все ячейки с трубами, поверх — свечение среды.
    pipes = Image.new("RGBA", size, (0, 0, 0, 0))
    for index in range(grid * grid):
        base_mask = level["base"][index]
        target = rotate_cw_steps(base_mask, level["solutionRot"][index])
        gx, gy = index % grid, index // grid
        box = (round(bx + gx * cell), round(by + gy * cell), round(bx + (gx + 1) * cell), round(by + (gy + 1) * cell))

        sprite = sprite_for(base_mask, sprites, material, variant=(gx + gy + index) % 2)
        if sprite is not None:
            steps = rotation_steps(sprite["mask"], target)
            art = sprite["image"].rotate(-90 * steps, expand=False)
            art = art.resize((int(cell), int(cell)), Image.LANCZOS)
            pipes.alpha_composite(art, (box[0], box[1]))
    img.alpha_composite(pipes)

    flow_layer = Image.new("RGBA", size, (0, 0, 0, 0))
    for index in range(grid * grid):
        base_mask = level["base"][index]
        target = rotate_cw_steps(base_mask, level["solutionRot"][index])
        gx, gy = index % grid, index // grid
        box = (round(bx + gx * cell), round(by + gy * cell), round(bx + (gx + 1) * cell), round(by + (gy + 1) * cell))
        # Все ячейки освещены: решение собрано, среда идёт от шины.
        color = flow if index != bus else (255, 255, 255)
        draw_flow(flow_layer, box, target, cell, color, strength=1.0)
    img.alpha_composite(flow_layer)

    for index in sorted(receivers):
        gx, gy = index % grid, index // grid
        box = (round(bx + gx * cell), round(by + gy * cell), round(bx + (gx + 1) * cell), round(by + (gy + 1) * cell))
        target = rotate_cw_steps(level["base"][index], level["solutionRot"][index])
        draw_receiver(img, box, cell, sprites["mouth"]["image"], target, flow)

    gx, gy = bus % grid, bus // grid
    bus_box = (round(bx + gx * cell), round(by + gy * cell), round(bx + (gx + 1) * cell), round(by + (gy + 1) * cell))
    bus_mask = rotate_cw_steps(level["base"][bus], level["solutionRot"][bus])
    pipe_sprite = sprite_for(level["base"][bus], sprites, material)
    draw_source(img, bus_box, cell, sprites["cap"]["image"], bus_mask, pipe_sprite)

    # Плёночное зерно и виньетка — чтобы кадр не выглядел «наклейкой».
    grain = Image.effect_noise((width, height), 14).convert("L").point(lambda v: 255 if v > 200 else 0)
    grain = grain.filter(ImageFilter.GaussianBlur(0.6)).convert("RGBA")
    grain.putalpha(14)
    img.alpha_composite(grain)

    vignette = Image.new("L", (width, height), 0)
    vd = ImageDraw.Draw(vignette)
    vd.ellipse([-width * 0.35, -height * 0.2, width * 1.35, height * 1.2], fill=255)
    vignette = vignette.filter(ImageFilter.GaussianBlur(120))
    dark = Image.new("RGBA", (width, height), (0, 0, 0, 190))
    img = Image.composite(img, Image.alpha_composite(img, dark), vignette)

    frame_ui(img, shot, level, cell)
    return img.convert("RGB")


def render_tileset(sprites, path, size=None):
    """Лист деталей по материалам: строка — материал, столбец — тип детали.

    Высота считается по числу строк (6 материалов + строка служебных), ширина
    по числу столбцов — ничего не обрезается. Подписи материалов живут в своей
    колонке слева и не заезжают под спрайты.
    """
    materials = [
        ("base", "чугун + цинк · канон"),
        ("zinc", "цинк · Плато"),
        ("zinc2", "цинк с патиной · Плато, кадр b"),
        ("chrome", "хром · Ртутный"),
        ("rust", "ржавый металл · Терминальный"),
        ("rust2", "глубокая ржавчина · Терминальный, кадр b"),
        ("cast", "чугун · Старый Диод"),
        ("glass", "стекло · Жилы"),
    ]
    pieces = ["straight", "elbow", "tee", "cross"]
    cell, gap, label_x, sprite_x0, top = 200, 28, 70, 460, 100
    rows = len(materials) + 1  # + строка служебных деталей
    if size is None:
        size = (max(1500, sprite_x0 + len(pieces) * cell + (len(pieces) - 1) * gap + 60),
                top + rows * (cell + 40) + 40)

    img = Image.new("RGB", size, SHEET_BG)
    draw = ImageDraw.Draw(img)
    for x in range(0, size[0], 40):
        draw.line([(x, 0), (x, size[1])], fill=(32, 31, 37), width=1)
    for y in range(0, size[1], 40):
        draw.line([(0, y), (size[0], y)], fill=(32, 31, 37), width=1)

    draw.text((70, 26), "АМПЕРГРАД × GUTTERCAPS · детали по материалам", font=font("bold", 32), fill=ACID)

    for row, (material, label) in enumerate(materials):
        y = top + row * (cell + 40)
        draw.text((label_x, y + cell // 2 - 10), label, font=font("regular", 20), fill=INK)
        for col, piece in enumerate(pieces):
            name = f"{material}-{piece}"
            x = sprite_x0 + col * (cell + gap)
            art = sprites[name]["image"].copy()
            art.thumbnail((cell - 16, cell - 16), Image.LANCZOS)
            img.paste(art, (x + (cell - art.width) // 2, y + (cell - art.height) // 2), art)
            if row == 0 and col < len(pieces):
                mask = sprites[name]["mask"]
                title = {"straight": f"ПРЯМАЯ · маска {mask}", "elbow": f"ОТВОД · маска {mask}",
                         "tee": f"ТРОЙНИК · маска {mask}", "cross": f"КРЕСТ · маска {mask}"}[piece]
                draw.text((x, y - 26), title, font=font("mono", 19), fill=DIM)

    # Служебные круглые детали — отдельной строкой.
    y = top + len(materials) * (cell + 40)
    draw.text((label_x, y + cell // 2 - 10), "служебные", font=font("regular", 20), fill=INK)
    for col, (name, caption) in enumerate((("mouth", "ПРИЁМНИК"), ("cap", "КОЛПАК · шина"))):
        x = sprite_x0 + col * (cell + gap)
        art = sprites[name]["image"].copy()
        art.thumbnail((cell - 16, cell - 16), Image.LANCZOS)
        img.paste(art, (x + (cell - art.width) // 2, y + (cell - art.height) // 2), art)
        # Подпись по центру ячейки: короткие строки не наезжают друг на друга.
        draw.text((x + cell // 2, y - 16), caption, font=font("mono", 19), fill=DIM, anchor="mm")

    img.save(path, "JPEG", quality=90)
    return path


def render_rewards(path, cell=240, gap=34, margin=70):
    """Лист медальонов наград: что игрок получает за квартал, серию и челлендж."""
    with open(os.path.join(TILES, "rewards.json"), encoding="utf-8") as fh:
        rewards = json.load(fh)
    names = list(rewards)
    columns = min(3, len(names))
    rows = (len(names) + columns - 1) // columns
    size = (margin * 2 + columns * cell + (columns - 1) * gap,
            100 + rows * (cell + gap + 30) + 20)
    img = Image.new("RGB", size, SHEET_BG)
    draw = ImageDraw.Draw(img)
    for x in range(0, size[0], 40):
        draw.line([(x, 0), (x, size[1])], fill=(32, 31, 37), width=1)
    for y in range(0, size[1], 40):
        draw.line([(0, y), (size[0], y)], fill=(32, 31, 37), width=1)
    draw.text((margin, 26), "АМПЕРГРАД · пломбы квартала", font=font("bold", 32), fill=ACID)

    for i, name in enumerate(names):
        col, row = i % columns, i // columns
        x = margin + col * (cell + gap)
        y = 100 + row * (cell + gap + 30)
        art = Image.open(os.path.join(TILES, rewards[name]["file"])).convert("RGBA")
        art.thumbnail((cell - 16, cell - 16), Image.LANCZOS)
        img.paste(art, (x + (cell - art.width) // 2, y + (cell - art.height) // 2), art)
        caption = rewards[name]["caption"]
        draw.text((x + cell // 2, y + cell + 6), caption, font=font("regular", 17),
                  fill=INK, anchor="ma")

    img.save(path, "JPEG", quality=90)
    return path


def render_props(path, cell=230, gap=30, margin=70):
    """Лист служебных деталей: указатели потока и запасные детали поля."""
    with open(os.path.join(TILES, "props.json"), encoding="utf-8") as fh:
        props = json.load(fh)
    groups = [
        ("УКАЗАТЕЛИ ПОТОКА · waypoint", [k for k, v in props.items() if v["kind"] == "waypoint"]),
        ("ЗАПАСНЫЕ ДЕТАЛИ · стыки и декор квартала", [k for k, v in props.items() if v["kind"] == "spare"]),
    ]
    columns = max(len(names) for _, names in groups)
    size = (margin * 2 + columns * cell + (columns - 1) * gap,
            100 + len(groups) * (cell + gap + 34) + 30)
    img = Image.new("RGB", size, SHEET_BG)
    draw = ImageDraw.Draw(img)
    for x in range(0, size[0], 40):
        draw.line([(x, 0), (x, size[1])], fill=(32, 31, 37), width=1)
    for y in range(0, size[1], 40):
        draw.line([(0, y), (size[0], y)], fill=(32, 31, 37), width=1)
    draw.text((margin, 26), "АМПЕРГРАД · служебные детали", font=font("bold", 32), fill=ACID)

    for row, (title, names) in enumerate(groups):
        y = 100 + row * (cell + gap + 34)
        draw.text((margin, y - 24), title, font=font("mono", 19), fill=DIM)
        for col, name in enumerate(names):
            x = margin + col * (cell + gap)
            art = Image.open(os.path.join(TILES, props[name]["file"])).convert("RGBA")
            art.thumbnail((cell - 16, cell - 16), Image.LANCZOS)
            img.paste(art, (x + (cell - art.width) // 2, y + (cell - art.height) // 2), art)
            draw.text((x + cell // 2, y + cell + 6), props[name]["caption"], font=font("regular", 19),
                      fill=INK, anchor="ma")

    img.save(path, "JPEG", quality=90)
    return path


def main():
    os.makedirs(OUT, exist_ok=True)
    sprites = load_tiles()

    for shot in SHOTS:
        img = render_level(shot, sprites)
        path = os.path.join(OUT, f"level-{shot['level']}.jpg")
        img.save(path, "JPEG", quality=88)
        print("записано:", os.path.relpath(path, REPO))

    print("записано:", os.path.relpath(render_tileset(sprites, os.path.join(OUT, "tileset.jpg")), REPO))
    print("записано:", os.path.relpath(render_props(os.path.join(OUT, "props.jpg")), REPO))
    print("записано:", os.path.relpath(render_rewards(os.path.join(OUT, "rewards.jpg")), REPO))


if __name__ == "__main__":
    main()
