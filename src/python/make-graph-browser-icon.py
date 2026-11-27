"""Make the small Graph Browser link icon from the Lomikel logo.

The Fink Portal shows this icon (at about 18 px, on a dark background) in
front of its "Explore in Graph Browser" link. It is derived from
src/LomikelOverlap.png:

- the class labels are removed (unreadable at icon size),
- the black lines become light (#f7f7f7) so they show on a dark background,
- bold arcs keep full strength, thin orbits are faded,
- the coloured stars keep their colours.

Usage (from the repository root, needs numpy and Pillow):

    python3 src/python/make-graph-browser-icon.py \
        [src/LomikelOverlap.png] [src/webservice/images/LomikelGraphBrowser.png] [--size 64]
"""
import argparse

import numpy as np
from PIL import Image, ImageFilter

LINE_COLOUR = 247  # #f7f7f7, the Portal link text colour


def make_icon(source, size):
    """Return the RGBA icon (size x size) made from the logo at `source`."""
    a = np.asarray(Image.open(source).convert("RGBA")).astype(np.int16)
    r, g, b, al = a[..., 0], a[..., 1], a[..., 2], a[..., 3]
    gray = (abs(r - g) < 8) & (abs(g - b) < 8) & (al > 0)

    # 1. Remove the class labels (#343434 text and its anti-aliased edges),
    #    keeping the black orbit lines that cross them.
    text = gray & (abs(r - 52) <= 6)
    near = np.asarray(Image.fromarray((text * 255).astype(np.uint8)).filter(ImageFilter.MaxFilter(7))) > 0
    al = np.where(near & gray & (r >= 30), 0, al)

    # 2. Lines: coverage from darkness. Bold arcs (they survive a
    #    morphological opening) stay at full strength and get a little
    #    thicker; thin orbits are thickened but faded.
    line = gray & (al > 0)
    cimg = Image.fromarray(np.where(line, (255 - r).clip(0, 255), 0).astype(np.uint8))
    bold = cimg.filter(ImageFilter.MinFilter(9)).filter(ImageFilter.MaxFilter(9))
    bold = np.asarray(bold.filter(ImageFilter.MaxFilter(15))).astype(np.int16)
    thin = np.asarray(cimg.filter(ImageFilter.MaxFilter(11))).astype(np.int16)
    cov = np.maximum(bold, (thin * 0.45).astype(np.int16)).clip(0, 255).astype(np.uint8)

    # 3. Colour layer: stars and dots keep their colours, slightly enlarged.
    colour = (~gray) & (al > 0)
    crgb = np.asarray(
        Image.fromarray(np.where(colour[..., None], a[..., :3], 0).astype(np.uint8)).filter(ImageFilter.MaxFilter(9))
    )
    cmask = np.asarray(Image.fromarray((colour * 255).astype(np.uint8)).filter(ImageFilter.MaxFilter(9))) > 0
    rgba = a.copy()
    rgba[..., :3] = np.where(cmask[..., None], crgb, a[..., :3])

    out = np.zeros(a.shape, np.uint8)
    out[..., :3] = LINE_COLOUR
    out[..., 3] = cov
    out[cmask] = rgba[cmask].astype(np.uint8)

    # 4. Crop, centre on a transparent square and downscale (premultiplied,
    #    to avoid dark fringes).
    im = Image.fromarray(out, "RGBA")
    im = im.crop(im.getbbox())
    w, h = im.size
    side = max(w, h)
    square = Image.new("RGBA", (side, side), (0, 0, 0, 0))
    square.paste(im, ((side - w) // 2, (side - h) // 2))
    return square.convert("RGBa").resize((size, size), Image.Resampling.LANCZOS).convert("RGBA")


def main():
    parser = argparse.ArgumentParser(description="Make the Graph Browser link icon from the Lomikel logo.")
    parser.add_argument("source", nargs="?", default="src/LomikelOverlap.png")
    parser.add_argument("output", nargs="?", default="src/webservice/images/LomikelGraphBrowser.png")
    parser.add_argument("--size", type=int, default=64)
    args = parser.parse_args()
    make_icon(args.source, args.size).save(args.output, optimize=True)


if __name__ == "__main__":
    main()
