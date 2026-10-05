#!/usr/bin/env python3
"""Captions the raw emulator captures for the Play listing.

    uv run --with pillow tools/screenshots/caption.py [--font Roboto.ttf]

Reads tools/screenshots/captions.tsv (device, raw capture, output name, headline, subline) and
writes each captioned screenshot into fastlane/metadata/android/en-US/images/<device dir>/,
replacing whatever screenshots were there. The style is the launcher's own: a black canvas,
light Roboto in white with a grey subline, and the capture inset below with the same soft
corners and hairline edge as the home screen's search bar and tip card.

The font is Android's sans-serif, Roboto, which every emulator image ships. Pull it once with
`adb pull /system/fonts/Roboto-Regular.ttf tools/screenshots/Roboto.ttf` (it is a variable
font; the script picks the Light and Regular weights from it). Raw captures come from
prepare-device.sh and capture.sh; see the README's Google Play section.
"""

import argparse
import csv
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

HERE = Path(__file__).resolve().parent
ROOT = HERE.parent.parent
IMAGES = ROOT / "fastlane/metadata/android/en-US/images"
DEVICE_DIRS = {
    "phone": "phoneScreenshots",
    "seven": "sevenInchScreenshots",
    "ten": "tenInchScreenshots",
}

BACKGROUND = (0, 0, 0)
HEADLINE = (255, 255, 255)
SUBLINE = (160, 160, 160)  # 8.6:1 on black
EDGE = (64, 64, 64)


def font(path: Path, size: int, weight: str) -> ImageFont.FreeTypeFont:
    face = ImageFont.truetype(str(path), size)
    try:
        face.set_variation_by_name(weight)
    except (OSError, ValueError):
        pass  # a static font: use it as it is
    return face


def wrap(draw: ImageDraw.ImageDraw, text: str, face: ImageFont.FreeTypeFont, width: int) -> list[str]:
    lines, line = [], ""
    for word in text.split():
        candidate = f"{line} {word}".strip()
        if line and draw.textlength(candidate, font=face) > width:
            lines.append(line)
            line = word
        else:
            line = candidate
    if line:
        lines.append(line)
    return lines


def caption(raw: Path, size: tuple[int, int], headline: str, subline: str, font_path: Path) -> Image.Image:
    width, height = size
    unit = min(width, height)  # type scales with the short side, so landscape matches portrait
    margin = round(unit * 0.075)
    canvas = Image.new("RGB", size, BACKGROUND)
    draw = ImageDraw.Draw(canvas)

    head_face = font(font_path, round(unit * 0.066), "Light")
    sub_face = font(font_path, round(unit * 0.036), "Regular")
    text_width = width - 2 * margin
    y = round(unit * 0.085)
    for line in wrap(draw, headline, head_face, text_width):
        draw.text((margin, y), line, font=head_face, fill=HEADLINE)
        y += round(head_face.size * 1.18)
    if subline:
        y += round(unit * 0.012)
        for line in wrap(draw, subline, sub_face, text_width):
            draw.text((margin, y), line, font=sub_face, fill=SUBLINE)
            y += round(sub_face.size * 1.4)
    top = y + round(unit * 0.05)

    shot = Image.open(raw).convert("RGB")
    room_w, room_h = width - 2 * margin, height - top - margin
    scale = min(room_w / shot.width, room_h / shot.height)
    shot = shot.resize((round(shot.width * scale), round(shot.height * scale)), Image.LANCZOS)
    x = (width - shot.width) // 2
    # Soft corners, but never so round that they clip the status bar in a small landscape inset.
    radius = round(min(unit * 0.04, min(shot.size) * 0.03))
    mask = Image.new("L", shot.size, 0)
    ImageDraw.Draw(mask).rounded_rectangle((0, 0, shot.width - 1, shot.height - 1), radius, fill=255)
    canvas.paste(shot, (x, top), mask)
    edge = max(2, round(unit * 0.002))
    draw.rounded_rectangle(
        (x - edge, top - edge, x + shot.width - 1 + edge, top + shot.height - 1 + edge),
        radius + edge, outline=EDGE, width=edge,
    )
    return canvas


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--font", type=Path, default=HERE / "Roboto.ttf")
    parser.add_argument("--captions", type=Path, default=HERE / "captions.tsv")
    args = parser.parse_args()
    if not args.font.exists():
        raise SystemExit(f"{args.font} not found: adb pull /system/fonts/Roboto-Regular.ttf {args.font}")

    with args.captions.open(newline="") as rows:
        entries = [r for r in csv.DictReader(rows, delimiter="\t") if r["device"] and not r["device"].startswith("#")]

    for device in {e["device"] for e in entries}:
        for old in (IMAGES / DEVICE_DIRS[device]).glob("*.png"):
            old.unlink()
    for e in entries:
        raw = HERE / "raw" / e["device"] / e["raw"]
        with Image.open(raw) as probe:
            size = probe.size  # captions keep the capture's 9:16 or 16:9 size
        out = IMAGES / DEVICE_DIRS[e["device"]] / e["output"]
        out.parent.mkdir(parents=True, exist_ok=True)
        caption(raw, size, e["headline"], e["subline"], args.font).save(out, optimize=True)
        print(f"{out.relative_to(ROOT)}  {size[0]}x{size[1]}")


if __name__ == "__main__":
    main()
