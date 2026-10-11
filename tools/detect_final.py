#!/usr/bin/env python3
"""
FINAL panel detection: largest connected component of grid-gap navy pixels.

Why this works (and what it fixes):
  * The panel's interior is dark navy with 8x8 cell wells separated by ~4px
    navy gaps -> all those gaps form ONE connected network.
  * The panel's outer drop-shadow and the "55 FPS" booster badge are dark too,
    but they are NOT 4-connected to the gap network -> they become separate
    components and are ignored.
The bounding box of the largest component is the panel.

Pure-python but only run over candidate pixels; fast enough offline.
"""
import sys
from collections import Counter, deque
from PIL import Image

GAP = (25, 33, 69)
EMPTY = (38, 46, 85)
BG = (58, 82, 146)
TOL = 26


def d(p, q): return abs(p[0]-q[0])+abs(p[1]-q[1])+abs(p[2]-q[2])


def largest_gap_component(px, w, h, tol=TOL):
    cand = bytearray(w*h)
    for y in range(h):
        base = y*w
        for x in range(w):
            if d(px[x, y], GAP) <= tol:
                cand[base+x] = 1
    seen = bytearray(w*h)
    best = None
    for y in range(h):
        for x in range(w):
            i = y*w+x
            if not cand[i] or seen[i]: continue
            q = deque([(x, y)]); seen[i] = 1
            x0=x1=x; y0=y1=y; n=0
            while q:
                cx, cy = q.popleft(); n += 1
                if cx<x0: x0=cx
                if cx>x1: x1=cx
                if cy<y0: y0=cy
                if cy>y1: y1=cy
                for nx, ny in ((cx-1,cy),(cx+1,cy),(cx,cy-1),(cx,cy+1)):
                    if 0<=nx<w and 0<=ny<h:
                        j = ny*w+nx
                        if cand[j] and not seen[j]:
                            seen[j]=1; q.append((nx,ny))
            if best is None or n > best[0]:
                best = (n, x0, y0, x1, y1)
    return best  # (size, left, top, right, bottom)


def detect_tray(px, w, h, y_from, y_to):
    def is_piece(p): return d(p, BG) > 150 and d(p, GAP) > 150
    pts = [(x,y) for y in range(y_from, y_to) for x in range(w) if is_piece(px[x,y])]
    if not pts: return []
    xs = sorted(set(p[0] for p in pts))
    clusters, cur = [], [xs[0]]
    for a,b in zip(xs, xs[1:]):
        if b-a > 20: clusters.append(cur); cur=[]
        cur.append(b)
    clusters.append(cur)
    return [c for c in clusters if len(c) > 3], pts


def main(path):
    im = Image.open(path).convert("RGB"); w,h = im.size; px = im.load()
    n, left, top, right, bot = largest_gap_component(px, w, h)
    pw, ph = right-left+1, bot-top+1
    print("="*76)
    print(f"{path}  {w}x{h}")
    print(f"  PANEL (largest gap component, {n} px)")
    print(f"        x {left}..{right} (w={pw})   y {top}..{bot} (h={ph})   aspect={pw/ph:.4f}")
    cw, ch = pw/8, ph/8
    print(f"  CELL  {cw:.3f} x {ch:.3f} px")

    board=[]; fills=[]; empties=[]
    for r in range(8):
        row=""
        for c in range(8):
            p = px[int(left+(c+.5)*cw), int(top+(r+.5)*ch)]
            f = d(p, EMPTY) > 60
            row += "1" if f else "0"
            (fills if f else empties).append(p)
        board.append(row)
    print(f"  BOARD {sum(r.count('1') for r in board)}/64 filled")
    for r in board: print("     ", r)
    print(f"  filled dist_from_empty: min={min(d(p,EMPTY) for p in fills) if fills else '-'} "
          f"max={max(d(p,EMPTY) for p in fills) if fills else '-'}")
    print(f"  empty  dist_from_empty: max={max(d(p,EMPTY) for p in empties) if empties else '-'}")

    # tray: start below panel + 30px margin, stop at bottom
    res = detect_tray(px, w, h, bot+30, h)
    if not res: print("  (no tray)"); return
    clusters, pts = res
    BLOCK = 35.5
    print(f"  TRAY   {len(clusters)} slots, centres x="
          f"{[round((c[0]+c[-1])/2,1) for c in clusters]}")
    for i,cl in enumerate(clusters):
        c0,c1 = cl[0],cl[-1]
        sub=[p for p in pts if c0<=p[0]<=c1]
        x0,x1 = min(p[0] for p in sub), max(p[0] for p in sub)
        y0,y1 = min(p[1] for p in sub), max(p[1] for p in sub)
        rowsN=int((y1-y0)//BLOCK+1); colsN=int((x1-x0)//BLOCK+1)
        g=[["."]*colsN for _ in range(rowsN)]
        for x,y in sub: g[int((y-y0)//BLOCK)][int((x-x0)//BLOCK)]="#"
        col=Counter(px[x,y] for x,y in sub).most_common(1)[0][0]
        print(f"    slot{i} centre=({(c0+c1)/2:.1f},{(y0+y1)/2:.1f}) size={colsN}x{rowsN} RGB{col}")
        for r in g: print("       ", "".join(r))


for p in (sys.argv[1:] or ["samples/shot3.jpg"]):
    main(p)