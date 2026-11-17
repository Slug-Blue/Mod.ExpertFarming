"""Generates the 16x16 carrot item icons (WR-704) and a contact sheet.
Run from the repo root: python tools/gen_carrot_icons.py
Light from the upper left; roots lean tip to the lower left, leaves at the upper right."""
import math, os
from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(HERE, '..', 'mod', 'src', 'main', 'resources', 'assets', 'wildroots', 'textures', 'item')
SHEET = os.path.join(HERE, '..', 'docs', 'design', 'art', 'carrot_colours_contact_sheet.png')

LG0, LG1, LG2, LG3 = (34, 84, 28), (58, 128, 42), (98, 168, 58), (146, 200, 86)
PAL = {  # D shade, M mid, H light, S gloss, C pale core hint
    'orange': dict(D=(160, 66, 14), M=(218, 106, 20), H=(250, 146, 36), S=(255, 190, 96), C=(255, 214, 150)),
    'yellow': dict(D=(164, 120, 26), M=(214, 170, 40), H=(246, 214, 74), S=(255, 242, 160), C=(255, 250, 215)),
    'purple': dict(D=(48, 14, 54), M=(92, 30, 92), H=(136, 56, 130), S=(184, 104, 174), C=(238, 214, 234)),
    'red':    dict(D=(92, 12, 22), M=(164, 28, 34), H=(214, 64, 56), S=(240, 140, 120), C=(250, 190, 170)),
}


def body(segs, x, y):
    """Signed offset ratio (+ = upper left side) and t along the segment, or None if outside."""
    best = None
    px, py = x + .5, y + .5
    for x0, y0, x1, y1, w0, w1 in segs:
        dx, dy = x1 - x0, y1 - y0
        length = math.hypot(dx, dy)
        t = ((px - x0) * dx + (py - y0) * dy) / (length * length)
        if t < -0.02 or t > 1.0:
            continue
        w = w0 + (w1 - w0) * t
        # perpendicular distance, sign chosen so the upper-left side is positive
        s = ((px - x0) * dy - (py - y0) * dx) / length
        if dx * dy < 0:
            s = -s
        if abs(s) <= w:
            r = s / w
            if best is None or abs(r) < abs(best[0]):
                best = (r, t)
    return best


def render(segs, pal, leaves, gloss=True):
    px = {}
    for y in range(16):
        for x in range(16):
            b = body(segs, x, y)
            if b is None:
                continue
            r, t = b
            if r > 0.55:
                c = 'S' if (gloss and r > 0.8 and 0.12 < t < 0.55) else 'H'
            elif r > -0.2:
                c = 'M'
            else:
                c = 'D'
            px[(x, y)] = pal[c]
    px.update(leaves)
    return px


def grid(rows, key):
    d = {}
    for y, row in enumerate(rows):
        for x, ch in enumerate(row):
            if ch in key:
                d[(x, y)] = key[ch]
    return d


LK = {'1': LG0, '2': LG1, '3': LG2, '4': LG3}
MODERN_LEAVES = grid([
    '................',
    '.........4......',
    '........3.4.2...',
    '.......2.32.3...',
    '........2123....',
    '.........11.....',
    '................',
], LK)
EAST_LEAVES = grid([
    '................',
    '............4...',
    '...........3.2..',
    '............21..',
    '.........4..1...',
    '..........21....',
    '................',
], LK)

icons = {}
# orange: the familiar medium taper
px = render([(11.4, 4.4, 2.4, 14.2, 3.0, 0.3)], PAL['orange'], MODERN_LEAVES)
for p in [(7, 9), (8, 8), (5, 11)]:  # growth rings
    if p in px:
        px[p] = PAL['orange']['D']
icons['carrot'] = px
# yellow: paler, thinner, a little longer
icons['carrot_yellow'] = render([(11.4, 4.2, 1.8, 14.8, 2.1, 0.25)], PAL['yellow'], MODERN_LEAVES)
# purple: dark and thick, pale core hint
icons['carrot_purple'] = render([(11.2, 4.6, 3.0, 13.8, 3.5, 0.4)], PAL['purple'], MODERN_LEAVES)
# red: a deep crimson taper, a little shorter than orange, clear pointed tip, ring pixels
icons['carrot_red'] = render([(11.2, 4.6, 3.4, 13.4, 2.8, 0.2)], PAL['red'], MODERN_LEAVES)
for p in [(8, 8), (7, 9), (6, 10), (9, 7)]:
    if p in icons['carrot_red']:
        icons['carrot_red'][p] = PAL['red']['D']


def eastern(pal):
    segs = [(11.6, 4.8, 6.4, 10.0, 1.9, 1.0),   # shoulder and upper root
            (6.4, 10.0, 1.6, 14.4, 1.0, 0.25),  # left fork
            (6.4, 10.0, 6.2, 15.0, 0.9, 0.2)]   # right fork, short and straight
    px = render(segs, pal, EAST_LEAVES, gloss=False)
    if (8, 8) in px:
        px[(8, 8)] = pal['D']
    return px


for c in ('orange', 'yellow', 'purple', 'red'):
    icons['eastern_carrot_' + c] = eastern(PAL[c])


def core(px, pts, pal):
    for p in pts:
        if p in px:
            px[p] = pal['C']


core(icons['carrot_purple'], [(10, 6), (9, 7), (7, 9), (6, 10), (5, 11)], PAL['purple'])
core(icons['eastern_carrot_purple'], [(9, 7), (7, 9)], PAL['purple'])


def save(name, px):
    im = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
    for (x, y), c in px.items():
        if 0 <= x < 16 and 0 <= y < 16:
            im.putpixel((x, y), c + (255,))
    im.save(os.path.join(OUT, name + '.png'))
    return im


def sheet(ims):
    order = ['carrot', 'carrot_yellow', 'carrot_purple', 'carrot_red', 'wild_carrot_root',
             'eastern_carrot_orange', 'eastern_carrot_yellow', 'eastern_carrot_purple', 'eastern_carrot_red']
    cell, pad, cols, rows = 128, 12, 5, 2
    w = cols * (cell + pad) + pad
    h = rows * (cell + pad) + pad
    out = Image.new('RGBA', (w, h * 2), (0, 0, 0, 255))
    for k, bg in enumerate([(40, 40, 46, 255), (222, 222, 214, 255)]):
        out.paste(Image.new('RGBA', (w, h), bg), (0, k * h))
        for i, n in enumerate(order):
            ox = pad + (i % cols) * (cell + pad)
            oy = k * h + pad + (i // cols) * (cell + pad)
            out.alpha_composite(ims[n].resize((cell, cell), Image.NEAREST), (ox, oy))
    os.makedirs(os.path.dirname(SHEET), exist_ok=True)
    out.save(SHEET)


if __name__ == '__main__':
    ims = {n: save(n, p) for n, p in icons.items()}
    ims['wild_carrot_root'] = Image.open(os.path.join(OUT, 'wild_carrot_root.png')).convert('RGBA')
    sheet(ims)
    print('ok')
