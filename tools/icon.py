"""Draws the logo (docs/logo.png) the way the launcher icon is drawn: an inked globe on old paper with a compass needle across it."""
import os
from PIL import Image, ImageDraw

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
S = 4  # supersampling
N = 512 * S
PAPER, INK, RED = (239, 227, 200), (43, 33, 24), (158, 43, 32)


def main():
    im = Image.new("RGBA", (N, N), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    u = N / 108  # the vector icon's viewport is 108 units
    d.rounded_rectangle([0, 0, N - 1, N - 1], radius=int(N * 0.22), fill=PAPER)

    def P(x, y):
        return (x * u, y * u)
    c, r = P(54, 54), 24 * u
    d.ellipse([c[0] - r, c[1] - r, c[0] + r, c[1] + r], fill=PAPER, outline=INK, width=int(2.2 * u))
    w = int(1.3 * u)
    d.arc([c[0] - 12 * u, c[1] - r, c[0] + 12 * u, c[1] + r], 90, 270, fill=INK, width=w)
    d.arc([c[0] - 12 * u, c[1] - r, c[0] + 12 * u, c[1] + r], 270, 90, fill=INK, width=w)
    d.line([P(30, 54), P(78, 54)], fill=INK, width=w)
    for dy in (-12, 12):
        half = (24 ** 2 - dy ** 2) ** 0.5
        d.line([P(54 - half, 54 + dy), P(54 + half, 54 + dy)], fill=INK, width=w)
    needle = [P(54, 20), P(59.5, 54), P(54, 88), P(48.5, 54)]
    d.polygon(needle, fill=RED)
    d.polygon([P(54, 54), P(57.6, 54), P(54, 82), P(50.4, 54)], fill=PAPER)
    d.line(needle + [needle[0]], fill=INK, width=int(1.2 * u), joint="curve")
    d.ellipse([c[0] - 2.4 * u, c[1] - 2.4 * u, c[0] + 2.4 * u, c[1] + 2.4 * u], fill=INK)
    out = im.resize((512, 512), Image.LANCZOS)
    os.makedirs(os.path.join(ROOT, "docs"), exist_ok=True)
    out.save(os.path.join(ROOT, "docs", "logo.png"), optimize=True)


if __name__ == "__main__":
    main()
