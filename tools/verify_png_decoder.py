#!/usr/bin/env python3
"""
Validate that a minimal PNG decoder (the same algorithm as
app/src/test/.../PngReader.kt) reproduces the exact pixel values that
tools/detect_final.py measured from the JPEG fixtures.

If these agree, the Kotlin decoder will agree too.
"""
import sys, zlib

def read_png(path):
    b = open(path, 'rb').read()
    assert b[:8] == bytes([0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A]), "not a png"
    pos = 8
    w = h = depth = ctype = interlace = 0
    idat = b''
    while pos + 8 <= len(b):
        ln = int.from_bytes(b[pos:pos+4], 'big'); pos += 4
        t = b[pos:pos+4]; pos += 4
        data = b[pos:pos+ln]; pos += ln + 4
        if t == b'IHDR':
            w = int.from_bytes(data[0:4], 'big'); h = int.from_bytes(data[4:8], 'big')
            depth, ctype = data[8], data[9]; interlace = data[12]
        elif t == b'IDAT':
            idat += data
        elif t == b'IEND':
            break
    assert depth == 8 and interlace == 0, (depth, interlace)
    ch = {0: 1, 2: 3, 6: 4}[ctype]
    raw = zlib.decompress(idat)
    stride = w * ch
    px = [0] * (w * h)
    prev = bytearray(stride); cur = bytearray(stride)
    off = 0
    for y in range(h):
        f = raw[off]; off += 1
        cur[:] = raw[off:off+stride]; off += stride
        for i in range(stride):
            a = cur[i-ch] if i >= ch else 0
            b = prev[i]
            c = prev[i-ch] if i >= ch else 0
            x = cur[i]
            if f == 0: v = x
            elif f == 1: v = x + a
            elif f == 2: v = x + b
            elif f == 3: v = x + (a + b) // 2
            elif f == 4:
                p = a + b - c
                pa, pb, pc = abs(p-a), abs(p-b), abs(p-c)
                v = x + (a if (pa <= pb and pa <= pc) else (b if pb <= pc else c))
            else: raise ValueError(f"filter {f}")
            cur[i] = v & 0xFF
        for x in range(w):
            o = x*ch
            r = cur[o]; g = cur[o+1] if ch >= 3 else r; bl = cur[o+2] if ch >= 3 else r
            px[y*w+x] = (r << 16) | (g << 8) | bl
        prev, cur = cur, prev
    return w, h, px


def d(a, b):
    return abs(((a >> 16) & 255) - ((b >> 16) & 255)) + \
           abs(((a >> 8) & 255) - ((b >> 8) & 255)) + \
           abs((a & 255) - (b & 255))


GAP = (25, 33, 69)
EMPTY = (38, 46, 85)
def rgb(v): return ((v >> 16) & 255, (v >> 8) & 255, v & 255)

ok = True
for name, panel in [("shot1", (40, 386, 681, 1026)),
                    ("shot2", (40, 386, 681, 1026)),
                    ("shot3", (43, 388, 681, 1026))]:
    w, h, px = read_png(f"samples/{name}.png")
    left, top, right, bot = panel
    print(f"\n=== {name}.png  {w}x{h} ===")
    # empty-cell samples must hug EMPTY
    worst_empty = 0
    filled = []
    for r in range(8):
        for c in range(8):
            cx = int(left + (c + .5) * (right - left + 1) / 8)
            cy = int(top + (r + .5) * (bot - top + 1) / 8)
            v = px[cy * w + cx]
            dist = d(v, (EMPTY[0] << 16) | (EMPTY[1] << 8) | EMPTY[2])
            if dist > 60:
                filled.append((r, c, dist))
            else:
                worst_empty = max(worst_empty, dist)
    print(f"  worst empty distance = {worst_empty}")
    print(f"  filled cells ({len(filled)}): " +
          ", ".join(f"({r},{c}){dd}" for r, c, dd in filled))
    if worst_empty >= 60:
        ok = False; print("  !! an 'empty' cell is far from the empty colour")

print("\n--- shot3 board from the decoded PNG ---")
w, h, px = read_png("samples/shot3.png")
left, top, right, bot = (43, 388, 681, 1026)
rows = []
for r in range(8):
    row = ""
    for c in range(8):
        cx = int(left + (c + .5) * (right - left + 1) / 8)
        cy = int(top + (r + .5) * (bot - top + 1) / 8)
        v = px[cy * w + cx]
        row += "1" if d(v, (EMPTY[0] << 16) | (EMPTY[1] << 8) | EMPTY[2]) > 60 else "0"
    rows.append(row)
    print("   ", row)
expected = ["11011111", "10100000", "00100010", "00110010",
            "00111110", "11100011", "11111011", "11100111"]
print("\nmatches the hand-verified ground truth:", rows == expected)
if rows != expected: ok = False

print("\nRESULT:", "PASS - the Kotlin decoder will see these pixels" if ok else "FAIL")
sys.exit(0 if ok else 1)