#!/usr/bin/env python3
"""The store listing's feature graphic: metadata/en-US/images/featureGraphic.png, 1024 x 500.

    python3 scripts/listing/feature_graphic.py                # writes the PNG
    python3 scripts/listing/feature_graphic.py --out x.png    # somewhere else, to look at it first

A felt table seen from above on the app's black ground, a gold line round the felt, the two cards of
the app icon, the app's name in gold and one plain line. The colours are read from PokerColors.kt (the
app's design tokens), the font is Barlow Condensed (bundled with the app, OFL; DejaVu Sans as the
fallback, which ubuntu-latest has), and the cards are cut out of the listing's icon.png. Nothing is
fetched from the web, and nothing random goes in, so a rerun with the same inputs and Pillow writes
the same picture. Needs Pillow (python3-pil).
"""

import argparse
import os
import re
import sys

try:
    from PIL import Image, ImageChops, ImageDraw, ImageFont
except ImportError:
    sys.exit("feature_graphic.py needs Pillow: sudo apt-get install python3-pil (or pip install Pillow)")

REPO = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
COLORS_KT = os.path.join(REPO, "core/src/main/java/com/huntercoles/pokerpayout/core/design/PokerColors.kt")
FONT_DIR = os.path.join(REPO, "core/src/main/res/font")
ICON = os.path.join(REPO, "metadata/en-US/images/icon.png")
OUT = os.path.join(REPO, "metadata/en-US/images/featureGraphic.png")

WIDTH, HEIGHT = 1024, 500
TITLE = "Poker Payout"
# The owner's line (PP-040), set as two rows so it reads at phone size
LINES = ("Tournament clock, payouts and bank.", "Free, no ads, offline.")

SUPERSAMPLE = 2  # drawn at twice the size, then scaled down, for smooth edges


def poker_colors():
    """Every `val Name = Color(0xAARRGGBB)` in PokerColors.kt, as (r, g, b)."""
    with open(COLORS_KT, encoding="utf-8") as f:
        source = f.read()
    colors = {}
    for name, argb in re.findall(r"val (\w+) = Color\(0x([0-9A-Fa-f]{8})\)", source):
        value = int(argb, 16)
        colors[name] = ((value >> 16) & 0xFF, (value >> 8) & 0xFF, value & 0xFF)
    needed = ("PokerBlack", "FeltDeep", "DarkGreen", "PokerGold", "DarkGold", "CardWhite")
    missing = [n for n in needed if n not in colors]
    if missing:
        sys.exit("PokerColors.kt has no %s" % ", ".join(missing))
    return colors


def font(weight, size):
    """Barlow Condensed from the app's resources, else DejaVu Sans (bold for the title)."""
    bundled = os.path.join(FONT_DIR, "barlow_condensed_%s.ttf" % weight)
    fallback = "/usr/share/fonts/truetype/dejavu/DejaVuSans%s.ttf" % ("-Bold" if weight == "bold" else "")
    for path in (bundled, fallback):
        if os.path.exists(path):
            return ImageFont.truetype(path, size)
    sys.exit("no font: neither %s nor %s" % (bundled, fallback))


def lerp(a, b, t):
    return tuple(round(x + (y - x) * t) for x, y in zip(a, b))


def felt(size, centre, edge):
    """A radial wash from `centre` (under the lamp) to `edge`, as an RGB image of `size`."""
    w, h = size
    # A small radial gradient, stretched: smooth, and cheap to make
    small = Image.new("RGB", (64, 32))
    px = small.load()
    for y in range(32):
        for x in range(64):
            dx, dy = (x + 0.5) / 32 - 1, (y + 0.5) / 16 - 1
            t = min(1.0, (dx * dx + dy * dy) ** 0.5 / 1.15)
            px[x, y] = lerp(centre, edge, t * t)
    return small.resize((w, h), Image.BICUBIC)


def cards(height, color):
    """The icon's two cards in `color`, on transparency, `height` px tall. The icon is white cards on
    one flat green, so how far each pixel is from that green to white is how much card it is."""
    icon = Image.open(ICON).convert("RGB")
    bg = icon.getpixel((0, 0))[0]
    red = icon.getchannel("R")
    alpha = red.point(lambda v: max(0, min(255, round((v - bg) * 255 / max(1, 254 - bg)))))
    box = alpha.getbbox()
    alpha = alpha.crop(box)
    layer = Image.new("RGBA", alpha.size, color + (255,))
    layer.putalpha(alpha)
    scale = height / layer.height
    return layer.resize((round(layer.width * scale), height), Image.LANCZOS)


def draw(colors):
    s = SUPERSAMPLE
    w, h = WIDTH * s, HEIGHT * s
    image = Image.new("RGB", (w, h), colors["PokerBlack"])

    # The table: a stadium of felt on the black ground, lit from the middle
    inset = 22 * s
    table = (inset, inset, w - inset, h - inset)
    radius = (table[3] - table[1]) // 2
    mask = Image.new("L", (w, h), 0)
    ImageDraw.Draw(mask).rounded_rectangle(table, radius=radius, fill=255)
    image.paste(felt((w, h), colors["DarkGreen"], colors["FeltDeep"]), (0, 0), mask)

    # The gold line round the felt, as on a real table
    line_inset = inset + 26 * s
    ImageDraw.Draw(image).rounded_rectangle(
        (line_inset, line_inset, w - line_inset, h - line_inset),
        radius=radius - 26 * s, outline=colors["DarkGold"], width=3 * s,
    )

    # The two cards of the app icon beside the name in gold and the line in white, the group centred
    d = ImageDraw.Draw(image)
    card = cards(214 * s, colors["CardWhite"])
    title_font = font("bold", 118 * s)
    line_font = font("medium", 42 * s)
    title_box = d.textbbox((0, 0), TITLE, font=title_font)
    line_boxes = [d.textbbox((0, 0), line, font=line_font) for line in LINES]
    text_width = max([title_box[2] - title_box[0]] + [b[2] - b[0] for b in line_boxes])
    gap = 56 * s
    left = (w - (card.width + gap + text_width)) // 2
    if left < line_inset + 40 * s:
        sys.exit("the picture is too wide for the table (%d px of %d): shorten the text" %
                 ((card.width + gap + text_width) // s, WIDTH))

    cy = (h - card.height) // 2
    shadow = Image.new("RGBA", card.size, (0, 0, 0, 0))
    shadow.putalpha(ImageChops.multiply(card.getchannel("A"), Image.new("L", card.size, 110)))
    image.paste(Image.new("RGB", card.size, colors["PokerBlack"]), (left + 8 * s, cy + 10 * s), shadow)
    image.paste(card, (left, cy), card)

    x = left + card.width + gap
    gap_title, gap_line = 24 * s, 12 * s
    block = (title_box[3] - title_box[1]) + gap_title + sum(b[3] - b[1] for b in line_boxes) + gap_line * (len(LINES) - 1)
    y = (h - block) // 2
    d.text((x - title_box[0], y - title_box[1]), TITLE, font=title_font, fill=colors["PokerGold"])
    y += title_box[3] - title_box[1] + gap_title
    for line, box in zip(LINES, line_boxes):
        d.text((x - box[0], y - box[1]), line, font=line_font, fill=colors["CardWhite"])
        y += box[3] - box[1] + gap_line
    return image.resize((WIDTH, HEIGHT), Image.LANCZOS)


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--out", default=OUT, help="where to write the PNG (default: the listing's featureGraphic.png)")
    a = ap.parse_args()
    image = draw(poker_colors())
    # No metadata chunks, so the file only changes when the picture does
    image.save(a.out, format="PNG", optimize=True)
    print("wrote %s (%dx%d, %d KB)" % (os.path.relpath(a.out, REPO), WIDTH, HEIGHT, os.path.getsize(a.out) // 1024))


if __name__ == "__main__":
    main()
