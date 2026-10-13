#!/usr/bin/env python3
"""Captions the raw emulator captures for the Play listing.

    uv run --with pillow tools/screenshots/caption.py [--font Roboto.ttf]

Reads tools/screenshots/captions.tsv (device, raw capture, output name, headline, subline) and
writes each captioned screenshot into fastlane/metadata/android/en-US/images/<device dir>/,
replacing whatever screenshots were there. The style is the launcher's own, made to sell: a
black canvas with a soft glow in one of the drawer's category colours, the spaced-out app name
over a short accent bar, a bold white Roboto headline and a grey subline, centred, and the
capture inset below with the same soft corners and hairline edge as the home screen's search
bar and tip card.

The font is Android's sans-serif, Roboto, as a variable font (the script picks the Medium, Bold
and Regular weights from it). Fetch it once from Google Fonts:
`curl -L -o tools/screenshots/Roboto.ttf 'https://github.com/google/fonts/raw/main/ofl/roboto/Roboto%5Bwdth,wght%5D.ttf'`. Raw captures come from
prepare-device.sh and capture.sh; see the README's Google Play section.
"""

import argparse
import csv
from pathlib import Path

from PIL import Image, ImageDraw, ImageFilter, ImageFont

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
SUBLINE = (176, 176, 182)  # 9.9:1 on black
BADGE = (150, 150, 156)
EDGE = (64, 64, 64)
# The drawer's own category colours (AppCategory.kt), one per shot in turn:
# People, Focus, Media, Shopping, Sleep, Agents.
ACCENTS = [(0x56, 0x9C, 0xD6), (0x4E, 0xC9, 0xB0), (0xC5, 0x86, 0xC0),
           (0xDC, 0xB4, 0x5F), (0x8C, 0x9E, 0xFF), (0xA7, 0x8B, 0xFA)]


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


def glow(size: tuple[int, int], centre: tuple[int, int], radius: int, accent: tuple[int, int, int]) -> Image.Image:
    """A soft pool of the accent colour on black, brightest at the centre."""
    small = (max(1, size[0] // 8), max(1, size[1] // 8))
    mask = Image.new("L", small, 0)
    cx, cy, r = centre[0] // 8, centre[1] // 8, radius // 8
    ImageDraw.Draw(mask).ellipse((cx - r, cy - r, cx + r, cy + r), fill=110)
    mask = mask.filter(ImageFilter.GaussianBlur(max(1, r // 2))).resize(size, Image.BICUBIC)
    return Image.composite(Image.new("RGB", size, accent), Image.new("RGB", size, BACKGROUND), mask)


def caption(raw: Path, size: tuple[int, int], headline: str, subline: str, font_path: Path,
            accent: tuple[int, int, int]) -> Image.Image:
    width, height = size
    unit = min(width, height)  # type scales with the short side, so landscape matches portrait
    margin = round(unit * 0.07)
    # A pool of the shot's category colour behind the screen, black everywhere else.
    canvas = glow(size, (width // 2, round(height * 0.62)), round(max(width, height) * 0.42), accent)
    draw = ImageDraw.Draw(canvas)

    badge_face = font(font_path, round(unit * 0.03), "Medium")
    head_face = font(font_path, round(unit * 0.082), "Bold")
    sub_face = font(font_path, round(unit * 0.038), "Regular")
    text_width = width - 2 * margin

    def centred(line: str, y: int, face: ImageFont.FreeTypeFont, fill: tuple[int, int, int]) -> None:
        draw.text(((width - draw.textlength(line, font=face)) / 2, y), line, font=face, fill=fill)

    y = round(unit * 0.07)
    # The app name, spaced out, with a short bar of the accent under it.
    centred("B L A U N C H E R", y, badge_face, BADGE)
    y += round(badge_face.size * 1.5)
    bar_w, bar_h = round(unit * 0.07), max(3, round(unit * 0.006))
    draw.rounded_rectangle(((width - bar_w) // 2, y, (width + bar_w) // 2, y + bar_h), bar_h, fill=accent)
    y += bar_h + round(unit * 0.035)
    for line in wrap(draw, headline, head_face, text_width):
        centred(line, y, head_face, HEADLINE)
        y += round(head_face.size * 1.12)
    if subline:
        y += round(unit * 0.014)
        for line in wrap(draw, subline, sub_face, round(text_width * 0.92)):
            centred(line, y, sub_face, SUBLINE)
            y += round(sub_face.size * 1.38)
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
    edge = max(2, round(unit * 0.003))
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
        raise SystemExit(f"{args.font} not found: see the curl line at the top of this script")

    with args.captions.open(newline="") as rows:
        entries = [r for r in csv.DictReader(rows, delimiter="\t") if r["device"] and not r["device"].startswith("#")]

    for device in {e["device"] for e in entries}:
        for old in (IMAGES / DEVICE_DIRS[device]).glob("*.png"):
            old.unlink()
    shown = {device: 0 for device in DEVICE_DIRS}
    for e in entries:
        accent = ACCENTS[shown[e["device"]] % len(ACCENTS)]
        shown[e["device"]] += 1
        raw = HERE / "raw" / e["device"] / e["raw"]
        with Image.open(raw) as probe:
            size = probe.size  # captions keep the capture's 9:16 or 16:9 size
        out = IMAGES / DEVICE_DIRS[e["device"]] / e["output"]
        out.parent.mkdir(parents=True, exist_ok=True)
        caption(raw, size, e["headline"], e["subline"], args.font, accent).save(out, optimize=True)
        print(f"{out.relative_to(ROOT)}  {size[0]}x{size[1]}")


if __name__ == "__main__":
    main()
