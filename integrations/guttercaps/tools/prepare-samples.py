#!/usr/bin/env python3
"""Готовит образцы арта для PR в GUTTERCAPS: уменьшенные копии спрайтов и контракты.

Прод-спрайты живут в этом репозитории (512×512, все шесть материалов). В чужой
монорепо кладём только образцы — чтобы ревьюер мог собрать поле локально, не таща
13 МБ арта. Поэтому копии уменьшаются вдвое, а в манифесте остаётся пометка, что
в продакшене размер другой.

Запуск: python3 integrations/guttercaps/tools/prepare-samples.py   (нужен pillow)
"""
import json
import os

from PIL import Image

HERE = os.path.abspath(__file__)
ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.dirname(HERE))))  # корень репозитория
ART = os.path.join(ROOT, "art")
PR_ART = os.path.join(ROOT, "integrations", "guttercaps", "pr", "packages", "ampergrad", "art")

TILE_SAMPLE = 256
BG_SAMPLE = (512, 917)  # 9:16 при высоте 917 — как в приложении (768×1376, пополам)

# Что кладём в образцы: канонические детали поля, круглая пара (шина и приёмник),
# все служебные пропсы и один фон яруса.
TILES = ["base-straight", "base-straight2", "base-elbow", "base-tee", "base-cross", "mouth", "cap"]
BACKGROUNDS = ["tier-1-plateau.jpg"]


def write_manifest(props=None, tiles=None):
    """Контракт образцов: пути внутри пакета, а не внутри репозитория арта."""
    with open(os.path.join(ART, "tiles", "manifest.json"), encoding="utf-8") as fh:
        manifest = json.load(fh)
    sample = {}
    for name in TILES:
        entry = dict(manifest[name])
        entry["file"] = f"art/tiles/{name}.png"
        sample[name] = entry
    sample["_note"] = (
        "Образцы уменьшены до 256×256 для ревью. Прод-спрайты — 512×512 и лежат в "
        "репозитории арта Amperograd (шесть материалов поля × четыре детали плюс "
        "служебные). Поворот спрайта равен повороту маски: preRotation — доворот "
        "спрайта до его mask в каноне движка."
    )
    with open(os.path.join(PR_ART, "tiles", "manifest.json"), "w", encoding="utf-8") as fh:
        json.dump(sample, fh, ensure_ascii=False, indent=2)
        fh.write("\n")


def main():
    os.makedirs(os.path.join(PR_ART, "tiles"), exist_ok=True)
    os.makedirs(os.path.join(PR_ART, "backgrounds"), exist_ok=True)

    for name in TILES:
        src = os.path.join(ART, "tiles", f"{name}.png")
        img = Image.open(src)
        img.thumbnail((TILE_SAMPLE, TILE_SAMPLE), Image.LANCZOS)
        img.save(os.path.join(PR_ART, "tiles", f"{name}.png"))
        print("образец:", f"tiles/{name}.png", img.size)

    with open(os.path.join(ART, "tiles", "props.json"), encoding="utf-8") as fh:
        props = json.load(fh)
    for name in props:
        src = os.path.join(ART, "tiles", props[name]["file"])
        img = Image.open(src)
        img.thumbnail((TILE_SAMPLE, TILE_SAMPLE), Image.LANCZOS)
        img.save(os.path.join(PR_ART, "tiles", props[name]["file"]))
    with open(os.path.join(PR_ART, "tiles", "props.json"), "w", encoding="utf-8") as fh:
        json.dump(props, fh, ensure_ascii=False, indent=2)
        fh.write("\n")
    print("образцы пропсов:", len(props))

    write_manifest()

    for name in BACKGROUNDS:
        img = Image.open(os.path.join(ART, "backgrounds", name))
        img.thumbnail(BG_SAMPLE, Image.LANCZOS)
        img.save(os.path.join(PR_ART, "backgrounds", name), "JPEG", quality=86)
        print("образец фона:", name, img.size)


if __name__ == "__main__":
    main()
