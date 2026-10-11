#!/usr/bin/env python3
"""Draw the detected 8x8 lattice over a screenshot so it can be eyeballed."""
import sys
from PIL import Image, ImageDraw

GAP = (25, 33, 69)
EMPTY = (38, 46, 85)


def d(p, q):
    return abs(p[0]-q[0]) + abs(p[1]-q[1]) + abs(p[2]-q[2])


def detect(px, w, h, tol=26):
    xs0, ys0, xs1, ys1 = w, h, -1, -1
    for y in range(h):
        for x in range(w):
            if d(px[x, y], GAP) <= tol:
                xs0 = min(xs0, x); xs1 = max(xs1, x)
                ys0 = min(ys0, y); ys1 = max(ys1, y)
    return xs0, ys0, xs1, ys1


path = sys.argv[1]
out = sys.argv[2]
im = Image.open(path).convert("RGB")
w, h = im.size
px = im.load()
left, top, right, bot = detect(px, w, h)
cw, ch = (right-left+1)/8, (bot-top+1)/8
print(f"panel x{left}..{right} y{top}..{bot}  cell {cw:.2f}x{ch:.2f}")

dr = ImageDraw.Draw(im)
for i in range(9):
    dr.line([(left+i*cw, top), (left+i*cw, bot)], fill=(255, 0, 255), width=2)
    dr.line([(left, top+i*ch), (right, top+i*ch)], fill=(255, 0, 255), width=2)
for r in range(8):
    for c in range(8):
        cx, cy = left+(c+.5)*cw, top+(r+.5)*ch
        p = px[int(cx), int(cy)]
        fill = d(p, EMPTY) > 60
        col = (255, 255, 0) if fill else (0, 255, 255)
        dr.rectangle([left+c*cw+3, top+r*ch+3, left+(c+1)*cw-3, top+(r+1)*ch-3],
                     outline=col, width=4)
        dr.text((left+c*cw+8, top+r*ch+6), ("#" if fill else "."),
                fill=col)

# tray slots marked too
im.save(out)
print("wrote", out)

# print clean ground truth
rows = []
for r in range(8):
    row = ""
    for c in range(8):
        p = px[int(left+(c+.5)*cw), int(top+(r+.5)*ch)]
        row += "1" if d(p, EMPTY) > 60 else "0"
    rows.append(row)
print("GROUND TRUTH (1=filled):")
for i, r in enumerate(rows):
    print(f"  row{i}: {r}")