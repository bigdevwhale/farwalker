"""Builds the Google Play graphics in docs/google-play/graphics from the rendered screens.

./gradlew testDebugUnitTest -Prender --tests '*RenderArtScreens*'
python tools/play.py

Takes app/build/art/screens-<lang>/*.png (1233x2673 renders of the demo journey) and writes the 512 px icon,
the 1024x500 feature graphics and six 1080x1920 captioned screenshots per language.
Play wants screenshots no longer than twice their width, so each screen is cut short under its caption.
"""
from pathlib import Path

from PIL import Image, ImageDraw, ImageFilter, ImageFont

ROOT = Path(__file__).resolve().parent.parent
OUT = ROOT / "docs/google-play/graphics"
SHOTS = ROOT / "app/build/art"
FONT = ROOT / "app/src/main/res/font"

# The app's own palette (ui/theme/Theme.kt).
PAPER = (0xEF, 0xE3, 0xC8)
PAPER2 = (0xE5, 0xD5, 0xB0)
DEEP = (0xD8, 0xC4, 0x9A)
INK = (0x2B, 0x21, 0x18)
RED = (0x9E, 0x2B, 0x20)

# The order they appear in the store, with the caption over each: an ink line and a red one.
PAGES = [
    ("1-atlas", ("Your steps walk", "the real map of the world"), ("Ваши шаги ведут путника", "по настоящей карте мира")),
    ("2-atlas-far", ("195 countries", "and 22 ends of the earth"), ("195 стран", "и 22 края земли")),
    ("4-passport", ("A stamp", "for every border"), ("Штамп в паспорте", "за каждую границу")),
    ("6-goal", ("Any town, any country,", "any cape"), ("Любой город, страна", "или мыс")),
    ("5-journal", ("Every night", "in the journal"), ("Каждая ночь —", "в дневнике")),
    ("3-atlas-near", ("A coin a kilometre:", "trains and ships"), ("Километр — монета:", "поезда и корабли")),
]


def font(name, size):
    return ImageFont.truetype(str(FONT / name), size)


def fit(name, text, size, width):
    while font(name, size).getlength(text) > width:
        size -= 2
    return size


def chart(w, h):
    """Old paper with the faint graticule of an engraved atlas and a ruled border."""
    g = Image.new("RGB", (w, h), PAPER)
    d = ImageDraw.Draw(g)
    for x in range(-w, w * 2, 90):
        d.line((x, 0, x + h // 6, h), fill=PAPER2, width=2)
    for y in range(40, h, 90):
        d.line((0, y, w, y), fill=PAPER2, width=2)
    d.rectangle((14, 14, w - 15, h - 15), outline=INK, width=3)
    d.rectangle((24, 24, w - 25, h - 25), outline=DEEP, width=1)
    return g.convert("RGBA")


def lay(g, card, x, y):
    """A screen on the chart: a soft shadow and a thin ink frame round it."""
    sh = Image.new("RGBA", (card.width + 80, card.height + 80), (0, 0, 0, 0))
    ImageDraw.Draw(sh).rectangle((40, 50, 40 + card.width, 50 + card.height), fill=(43, 33, 24, 110))
    g.alpha_composite(sh.filter(ImageFilter.GaussianBlur(18)), (x - 40, y - 40))
    g.alpha_composite(card.convert("RGBA"), (x, y))
    ImageDraw.Draw(g).rectangle((x - 3, y - 3, x + card.width + 2, y + card.height + 2), outline=INK, width=3)


def screenshot(src, caption):
    W, H = 1080, 1920
    g = chart(W, H)
    d = ImageDraw.Draw(g)
    size = min(fit("cormorant_bold.ttf", line, 92, W - 140) for line in caption)
    d.text((W / 2, 135), caption[0], font=font("cormorant_bold.ttf", size), fill=INK, anchor="mm")
    d.text((W / 2, 235), caption[1], font=font("cormorant_italic.ttf", size), fill=RED, anchor="mm")
    im = Image.open(src).convert("RGB")
    w = 840
    im = im.resize((w, round(im.height * w / im.width)), Image.LANCZOS)
    top = 330
    # The screen runs off the bottom edge, like a road that goes on.
    lay(g, im.crop((0, 0, w, H - top + 20)), (W - w) // 2, top)
    return g.convert("RGB")


def feature(lang):
    W, H = 1024, 500
    g = chart(W, H)
    d = ImageDraw.Draw(g)
    # The traveller's road across Europe, from the atlas.
    atlas = Image.open(SHOTS / f"screens-{lang}" / "2-atlas-far.png").convert("RGB").crop((0, 430, 1233, 1430))
    atlas = atlas.resize((round(atlas.width * 400 / atlas.height), 400), Image.LANCZOS)
    lay(g, atlas, W - atlas.width - 50, 50)
    logo = Image.open(ROOT / "docs/logo.png").convert("RGBA").resize((110, 110), Image.LANCZOS)
    g.alpha_composite(logo, (56, 56))
    name, lines = ("Farwalker", ["A pedometer that walks", "you round the real world"]) if lang == "en" else \
        ("Кругосветка", ["Шагомер, который ведёт", "вокруг настоящего мира"])
    d.text((56, 190), name, font=font("cormorant_bold.ttf", fit("cormorant_bold.ttf", name, 86, 400)), fill=INK)
    d.line((58, 300, 420, 300), fill=RED, width=3)
    sub = min(fit("pt_serif_italic.ttf", l, 32, 400) for l in lines)
    for i, l in enumerate(lines):
        d.text((56, 322 + i * 46), l, font=font("pt_serif_italic.ttf", sub), fill=RED if i == 1 else INK)
    g.convert("RGB").save(OUT / f"feature-graphic-{lang}.png", optimize=True)


def icon():
    """The logo on a full square of paper, so Play's own mask rounds the corners."""
    logo = Image.open(ROOT / "docs/logo.png").convert("RGBA")
    sq = Image.new("RGBA", logo.size, PAPER)
    sq.alpha_composite(logo)
    sq.convert("RGB").resize((512, 512), Image.LANCZOS).save(OUT / "icon-512.png", optimize=True)


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    icon()
    for lang in ("en", "ru"):
        feature(lang)
        folder = OUT / f"screenshots-{lang}"
        folder.mkdir(exist_ok=True)
        for i, (name, en, ru) in enumerate(PAGES, 1):
            screenshot(SHOTS / f"screens-{lang}" / f"{name}.png", en if lang == "en" else ru).save(folder / f"{i}.png", optimize=True)
    print("done:", OUT)


if __name__ == "__main__":
    main()
