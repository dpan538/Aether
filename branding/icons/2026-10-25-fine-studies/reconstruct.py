"""Fit editable grayscale contour paths to supplied references.

The reference pixels are analyzed, never embedded in the SVG. Background paper
and coarse print noise are excluded; connected curves retain their actual shape.
Requires Python, Pillow, NumPy and SciPy. This is reference reconstruction, not
an assertion that the supplied artworks are original Aether brand assets.
"""
import hashlib
import json
from pathlib import Path

import numpy as np
from PIL import Image
from scipy.ndimage import gaussian_filter, gaussian_filter1d, median_filter, map_coordinates, label, binary_fill_holes

ROOT = Path(__file__).resolve().parent
EDGE_PAIRS = {
    1: [(3, 0)], 2: [(0, 1)], 3: [(3, 1)], 4: [(1, 2)],
    5: [(3, 0), (1, 2)], 6: [(0, 2)], 7: [(3, 2)],
    8: [(2, 3)], 9: [(0, 2)], 10: [(0, 1), (2, 3)],
    11: [(1, 2)], 12: [(3, 1)], 13: [(0, 1)], 14: [(0, 3)]
}


def contour_loops(field, level):
    a = np.pad(field, 1, constant_values=0)
    tl, tr, br, bl = a[:-1, :-1], a[:-1, 1:], a[1:, 1:], a[1:, :-1]
    cases = (tl >= level).astype(np.uint8) + 2*(tr >= level) + 4*(br >= level) + 8*(bl >= level)
    ys, xs = np.nonzero((cases != 0) & (cases != 15))
    neighbours, coords = {}, {}
    for y, x in zip(ys, xs):
        values = (a[y, x], a[y, x+1], a[y+1, x+1], a[y+1, x])
        keys = ((0, int(y), int(x)), (1, int(y), int(x+1)),
                (0, int(y+1), int(x)), (1, int(y), int(x)))
        ends = (((x, y), (x+1, y)), ((x+1, y), (x+1, y+1)),
                ((x, y+1), (x+1, y+1)), ((x, y), (x, y+1)))
        edge_values = ((values[0], values[1]), (values[1], values[2]),
                       (values[3], values[2]), (values[0], values[3]))
        pairs = EDGE_PAIRS[int(cases[y, x])]
        if cases[y, x] in (5, 10) and sum(values)/4 >= level:
            pairs = [(0, 1), (2, 3)] if cases[y, x] == 5 else [(3, 0), (1, 2)]
        for first, second in pairs:
            for edge in (first, second):
                key = keys[edge]
                if key not in coords:
                    v0, v1 = edge_values[edge]
                    t = float(np.clip((level-v0)/(v1-v0), 0, 1))
                    p0, p1 = ends[edge]
                    coords[key] = (p0[0]+t*(p1[0]-p0[0])-1, p0[1]+t*(p1[1]-p0[1])-1)
            k1, k2 = keys[first], keys[second]
            neighbours.setdefault(k1, []).append(k2)
            neighbours.setdefault(k2, []).append(k1)
    unseen = set(neighbours)
    loops = []
    while unseen:
        start = min(unseen)
        current, previous, chain = start, None, []
        while True:
            chain.append(coords[current])
            unseen.discard(current)
            choices = neighbours[current]
            nxt = choices[0] if choices[0] != previous else choices[-1]
            previous, current = current, nxt
            if current == start or len(chain) > len(neighbours):
                break
        if current == start and len(chain) >= 12:
            points = np.asarray(chain, dtype=float)
            area = .5*abs(np.sum(points[:, 0]*np.roll(points[:, 1], -1)-np.roll(points[:, 0], -1)*points[:, 1]))
            if area > .65:
                loops.append(points)
    return loops


def simplify(points, tolerance=.14):
    # Split a closed curve at a distant point; ordinary RDP then preserves closure.
    cut = int(np.argmax(np.sum((points-points[0])**2, axis=1)))
    def rdp(p):
        if len(p) <= 2:
            return p
        delta = p[-1]-p[0]
        if np.linalg.norm(delta) < 1e-8:
            return p[[0, -1]]
        distance = abs(delta[0]*(p[:, 1]-p[0, 1])-delta[1]*(p[:, 0]-p[0, 0]))/np.linalg.norm(delta)
        i = int(distance.argmax())
        if distance[i] <= tolerance:
            return p[[0, -1]]
        return np.concatenate([rdp(p[:i+1])[:-1], rdp(p[i:])])
    return np.concatenate([rdp(points[:cut+1])[:-1], rdp(np.concatenate([points[cut:], points[:1]]))[:-1]])


def svg_loop(points, scale):
    points = gaussian_filter1d(points, .55, axis=0, mode='wrap')
    points = simplify(points)*scale
    def p(v):
        return f'{v[0]:.3f} {v[1]:.3f}'
    result = ['M'+p(points[0])]
    for i in range(len(points)):
        p0, p1 = points[i], points[(i+1) % len(points)]
        before, after = points[(i-1) % len(points)], points[(i+2) % len(points)]
        c0, c1 = p0+(p1-before)/9, p1-(after-p0)/9
        result.append(f'C{p(c0)} {p(c1)} {p(p1)}')
    return ''.join(result)+'Z'


def source_field(filename, crop, n, kind):
    path = ROOT/'references'/filename
    original = np.asarray(Image.open(path).convert('L'), dtype=float)
    x, y, size = crop
    axis = (np.arange(n)+.5)*size/n
    xx, yy = np.meshgrid(x+axis, y+axis)
    sampled = map_coordinates(original, [yy, xx], order=1, mode='constant', cval=240 if kind == 'ink' else 0)
    if kind == 'binary':
        sampled = gaussian_filter((250-sampled)/242, .3)
    elif kind == 'signal':
        sampled = gaussian_filter(sampled, .48)/255
    elif kind == 'ink':
        dark = original < 105
        labs, count = label(dark)
        sizes = np.bincount(labs.ravel()); sizes[0] = 0
        silhouette = binary_fill_holes(labs == sizes.argmax())
        silhouette = map_coordinates(silhouette.astype(float), [yy, xx], order=0, mode='constant', cval=0) > .5
        sampled = gaussian_filter(sampled, (1.6, .65))
        sampled += 7*((sampled > 45) & (sampled < 195))
        sampled = (231-sampled)/211
        sampled[~silhouette] = 0
        sampled[sampled < .065] = 0
        sampled = gaussian_filter(sampled, .35)
    elif kind == 'fold':
        sampled = gaussian_filter(median_filter(sampled, size=7), 1.1)
        sampled = (sampled-46)/201
        rr = np.hypot(xx-368, yy-468)
        sampled[rr <= 260] = np.maximum(sampled[rr <= 260], .115)
        sampled[rr > 260] = 0
    else:
        sampled = gaussian_filter(sampled, 1.6)
        sampled = (sampled-34)/202
    return np.clip(sampled, 0, 1), hashlib.sha256(path.read_bytes()).hexdigest()


def reconstruct():
    configs = [
        ('J1','R0-negative-space.png',(136,228,464),900,'binary',1),
        ('J3','R2-axial-light.png',(0,0,736),640,'light',192),
        ('J5','R4-phase-fold.png',(-24,76,784),720,'fold',192)
    ]
    result = {}
    for name, filename, crop, n, kind, steps in configs:
        field, sha = source_field(filename, crop, n, kind)
        paths, count, accent = [], 0, ''
        for i in range(1, steps+1):
            level = i/(steps+1)
            loops = contour_loops(field, level)
            data = ''.join(svg_loop(p, 1024/n) for p in loops)
            if name == 'J1':
                for curve in loops:
                    extent = np.ptp(curve, axis=0)*(1024/n)
                    if 220 < extent[0] < 360 and 140 < extent[1] < 260:
                        accent = svg_loop(curve, 1024/n)
            if not data:
                continue
            value = 8 if kind == 'binary' else round(241*(1-level)) if kind == 'ink' else round(8+237*level)
            tone = f'#{value:02x}{value:02x}{value:02x}'
            paths.append(f'<path data-isovalue="{level:.6f}" d="{data}" fill="{tone}" fill-rule="evenodd"/>')
            count += len(loops)
        lines, line_count = '', 0
        result[name] = {'source':filename,'sourceSha256':sha,'method':'Smoothed reference luminance fitted as closed cubic isocontours; no embedded raster',
                        'kind':kind,'levels':steps,'closedContours':count,'crop':crop,
                        'recoveredLines':line_count,'accentPath':accent,'defs':'','body':'<g data-part="reference-fitted-surfaces">'+''.join(paths)+lines+'</g>'}
        print(f'{name}: fitted {count} closed curves at {steps} luminance levels, {line_count} recovered lines', flush=True)
    (ROOT/'contours.json').write_text(json.dumps(result, ensure_ascii=False, separators=(',',':'))+'\n')


if __name__ == '__main__':
    reconstruct()
