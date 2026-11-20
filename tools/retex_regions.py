"""Region-aware detailing for the mutant lineage textures (run after retex_mutants.py).
Uses tools/mutant_uvmap.json (UV rectangles per model part, dumped from the baked models) so every effect lands on the right body part:
bone nodules on joints, rib shadows on the chest, lava seams on the core, wear and acid burns on hands/feet, and a region-weighted glow gradient."""
import json, numpy as np, os, sys
from PIL import Image, ImageFilter
ROOT = os.path.dirname(os.path.abspath(__file__))
T = os.path.join(ROOT, '..', 'src/main/resources/assets/xenomorph/textures/entity/')
UV = json.load(open(os.path.join(ROOT, 'mutant_uvmap.json')))
H, W = 256, 512

def region_of(path):
    p = path.split('/')
    last = p[-1]
    if 'dome' in p: return None                       # cranium strip keeps its sheen
    if last in ('jaw', 'inner', 'upperTeeth', 'ridges'): return 'jaw'
    if 'head' in p[:-1] or last == 'head' or last in ('fan', 'horns', 'dreads', 'bulb'): return 'head'
    if last == 'neck': return 'torso'
    if last.startswith('tube'): return 'tube'
    if last.startswith('tail'): return 'tail'
    if last in ('foot',): return 'foot'
    if last in ('thighL', 'thighR', 'knee', 'shin'): return 'leg'
    if last == 'hand' or (len(p) > 1 and (p[-1].startswith('f') and p[-1][1:].isdigit() or last == 'd')): return 'hand'
    if last == 'fore': return 'fore'
    if last.startswith('arm') or last in ('elbow', 'shoulder'): return 'arm'
    return 'torso'

def rects(cid):
    out = {}
    for path, rs in UV[cid].items():
        r = region_of(path)
        if r is None: continue
        out.setdefault(r, []).extend((path, tuple(x)) for x in rs)
    return out

def noise(scale, seed):
    r = np.random.default_rng(seed).random((H, W))
    a = np.asarray(Image.fromarray((r * 255).astype('uint8')).filter(ImageFilter.GaussianBlur(scale)), dtype=float)
    return (a - a.min()) / (a.max() - a.min() + 1e-9)

def slices(rs, regions):
    for reg in regions:
        for path, (x0, y0, x1, y1) in rs.get(reg, []):
            x0, y0 = max(0, x0), max(0, y0); x1, y1 = min(W, x1), min(H, y1)
            if x1 - x0 > 1 and y1 - y0 > 1: yield reg, path, slice(y0, y1), slice(x0, x1)

def local(sy, sx):
    h, w = sy.stop - sy.start, sx.stop - sx.start
    ly, lx = np.mgrid[0:h, 0:w]
    return ly, lx, ly / max(1, h - 1), lx / max(1, w - 1)

def mul(img, sy, sx, f): img[sy, sx] = np.clip(img[sy, sx] * f, 0, 255)
def add(img, sy, sx, c): img[sy, sx] = np.clip(img[sy, sx] + c, 0, 255)

# ---------------- per-lineage recipes: (regions, effect, colour/strength)
def paint(cid, img, rs):
    n = noise(2, hash(cid) % 1000)
    for reg, path, sy, sx in slices(rs, rs.keys()):
        ly, lx, fy, fx = local(sy, sx)
        nn = n[sy, sx][..., None]
        # --- universal wear: joints darker, hands/feet scuffed and acid-burned, feet dirt gradient
        if reg in ('hand', 'foot'):
            mul(img, sy, sx, (0.8 + 0.25 * nn))
            burn = (n[sy, sx] > 0.82)[..., None] * np.array([-30., 20., -20.]); add(img, sy, sx, burn)
        if reg == 'foot': mul(img, sy, sx, (0.75 + 0.25 * (1 - fy))[..., None])
        if path.endswith(('elbow', 'knee', 'shoulder')): mul(img, sy, sx, 0.8)
        # --- lineage-specific
        if cid == 'bone_xeno':
            if reg == 'torso': mul(img, sy, sx, (0.55 + 0.45 * (np.sin(ly * 1.1) > -0.2))[..., None])                 # rib shadows
            if reg in ('arm', 'fore', 'leg', 'hand') : add(img, sy, sx, ((fy < 0.15) | (fy > 0.85))[..., None] * 26.0)   # pale joint nodules
            if reg == 'tail': mul(img, sy, sx, (0.7 + 0.3 * (lx % 6 > 1))[..., None])                                   # vertebra rings
            if reg == 'head': mul(img, sy, sx, (0.8 + 0.3 * (np.abs(fx - 0.5) > 0.02))[..., None])                     # suture
        elif cid == 'hulk_xeno':
            if reg in ('torso', 'arm', 'fore'): mul(img, sy, sx, (0.8 + 0.35 * np.abs(np.sin(lx * 0.8 + ly * 0.15)))[..., None]); add(img, sy, sx, np.array([10., 4., -6.]))
            if reg == 'hand': add(img, sy, sx, np.array([36., -8., -8.]) * (fy > 0.6)[..., None])                     # bruised knuckles
        elif cid == 'blast_xeno':
            if reg == 'torso':
                core = np.exp(-(((fx - 0.5) * 3) ** 2 + ((fy - 0.5) * 2.5) ** 2)); add(img, sy, sx, core[..., None] * np.array([20., 90., 20.]))
            if reg == 'tail': mul(img, sy, sx, (0.75 + 0.4 * np.abs(np.sin(lx * 0.7)))[..., None])
        elif cid == 'void_xeno':
            if reg in ('arm', 'fore', 'hand'): add(img, sy, sx, ((np.random.default_rng(lx.size + ly.size).random(ly.shape) > 0.9)[..., None]) * np.array([80., 30., 140.]))
            if reg == 'torso': add(img, sy, sx, (np.exp(-((fx - 0.5) * 6) ** 2)[..., None]) * np.array([50., 10., 110.]) * 0.6)
        elif cid in ('frost_xeno', 'frozen_xeno'):
            if reg in ('hand', 'foot', 'tail'): add(img, sy, sx, (fy[..., None]) * np.array([60., 80., 90.]))              # frost creeping to the extremities
            if reg == 'torso' and cid == 'frost_xeno': add(img, sy, sx, (np.abs(np.sin((lx + ly) * 0.4)) > 0.9)[..., None] * 70.0)
        elif cid == 'wither_xeno':
            if reg in ('arm', 'fore', 'tail', 'leg'): add(img, sy, sx, ((lx % 7) < 2)[..., None] * 34.0)                    # bone plate seams
            if reg == 'torso': add(img, sy, sx, ((np.sin(ly * 1.0) > 0.7)[..., None]) * np.array([70., 10., 20.]))        # glowing red rib cracks
        elif cid == 'blaze_xeno':
            if reg == 'torso':
                core = np.exp(-(((fx - 0.5) * 3) ** 2 + ((fy - 0.5) * 2) ** 2)); add(img, sy, sx, core[..., None] * np.array([140., 60., 0.]))
            if reg in ('arm', 'leg', 'fore'): mul(img, sy, sx, (0.65 + 0.35 * (1 - np.abs(fy - 0.5) * 2))[..., None]); add(img, sy, sx, (fy < 0.12)[..., None] * np.array([110., 40., 0.]))
        elif cid == 'shulker_xeno':
            if reg == 'torso': mul(img, sy, sx, (0.6 + 0.6 * (np.abs(np.sin(lx * 0.35)) * np.abs(np.sin(ly * 0.35))))[..., None]); add(img, sy, sx, np.array([18., 0., 24.]))
            if reg in ('arm', 'leg'): mul(img, sy, sx, 0.75)
        elif cid == 'husk_xeno':
            mul(img, sy, sx, (0.9 + 0.2 * (np.sin(lx * 0.5 + ly * 0.4) > 0.7))[..., None]) if reg in ('torso', 'arm', 'fore') else None
            if reg == 'hand': mul(img, sy, sx, 0.8)
        elif cid == 'hog_xeno':
            if reg in ('head', 'jaw'): mul(img, sy, sx, (0.7 + 0.4 * (lx % 3 == 0))[..., None]); add(img, sy, sx, np.array([6., 0., 0.]))
            if reg == 'torso': mul(img, sy, sx, (0.75 + 0.35 * ((lx + ly) % 4 < 2))[..., None])
        elif cid == 'jungle_xeno':
            if reg in ('arm', 'fore', 'leg', 'tail'):
                v = ((lx + ly) % 8) < 2; img[sy, sx] = np.where(v[..., None], img[sy, sx] * 0.4 + np.array([20., 90., 20.]), img[sy, sx])
            if reg in ('head', 'torso'): add(img, sy, sx, (n[sy, sx] > 0.7)[..., None] * np.array([10., 50., 5.]))      # moss patches
        elif cid == 'chimera_queen':                                                                                         # one lineage per body part
            style = {'head': 'void', 'jaw': 'void', 'torso': 'hulk', 'arm': 'wither', 'fore': 'wither', 'hand': 'bone', 'leg': 'frost', 'foot': 'frost', 'tail': 'jungle', 'tube': 'blaze'}.get(reg)
            tint = {'void': (0.8, 0.5, 1.5), 'hulk': (0.7, 1.15, 0.65), 'wither': (0.75, 0.75, 0.85), 'bone': (1.5, 1.4, 1.2), 'frost': (0.8, 1.2, 1.5), 'jungle': (0.6, 1.2, 0.55), 'blaze': (1.5, 0.9, 0.45)}
            if style: mul(img, sy, sx, np.array(tint[style])[None, None, :])
    return img

# ---------------- glow: region-weighted colour gradients (A -> B) instead of random speckle
GLOW = {  # id: {region: (weight, colourA, colourB)}
 'hulk_xeno': {'torso': (0.25, (60, 200, 60), (160, 255, 120)), 'hand': (0.5, (200, 255, 120), (255, 255, 200))},
 'bone_xeno': {'head': (0.35, (255, 250, 220), (255, 255, 255)), 'torso': (0.2, (255, 240, 200), (255, 255, 240))},
 'blast_xeno': {'torso': (0.8, (90, 255, 60), (230, 255, 120)), 'head': (0.4, (120, 255, 80), (255, 255, 160)), 'tail': (0.4, (90, 255, 60), (200, 255, 100))},
 'void_xeno': {'head': (0.7, (200, 80, 255), (120, 255, 255)), 'torso': (0.45, (170, 60, 255), (220, 140, 255)), 'hand': (0.7, (200, 80, 255), (255, 255, 255)), 'fore': (0.3, (170, 60, 255), (200, 120, 255))},
 'frost_xeno': {'torso': (0.35, (140, 230, 255), (255, 255, 255)), 'tail': (0.5, (140, 230, 255), (255, 255, 255)), 'hand': (0.5, (160, 240, 255), (255, 255, 255))},
 'wither_xeno': {'torso': (0.6, (120, 120, 150), (255, 60, 80)), 'head': (0.5, (255, 60, 80), (255, 140, 140))},
 'blaze_xeno': {'torso': (0.9, (255, 120, 20), (255, 230, 120)), 'arm': (0.4, (255, 100, 10), (255, 200, 60)), 'leg': (0.3, (255, 90, 10), (255, 180, 50)), 'head': (0.5, (255, 150, 30), (255, 240, 140))},
 'shulker_xeno': {'torso': (0.5, (240, 150, 255), (255, 230, 255)), 'head': (0.5, (255, 200, 255), (255, 255, 255))},
 'husk_xeno': {'torso': (0.15, (255, 210, 110), (255, 240, 170)), 'hand': (0.3, (255, 220, 120), (255, 250, 190))},
 'hog_xeno': {'head': (0.6, (255, 120, 80), (255, 200, 120)), 'torso': (0.25, (255, 100, 60), (255, 160, 90))},
 'frozen_xeno': {'torso': (0.4, (150, 240, 255), (255, 255, 255)), 'hand': (0.55, (170, 245, 255), (255, 255, 255)), 'tail': (0.4, (150, 240, 255), (255, 255, 255))},
 'jungle_xeno': {'head': (0.5, (160, 255, 100), (240, 255, 180)), 'arm': (0.35, (120, 230, 70), (200, 255, 130)), 'fore': (0.35, (120, 230, 70), (200, 255, 130))},
 'chimera_queen': {'head': (0.7, (200, 80, 255), (120, 255, 255)), 'torso': (0.55, (255, 220, 120), (255, 255, 255)), 'arm': (0.4, (255, 60, 80), (255, 160, 160)), 'tail': (0.4, (160, 255, 100), (240, 255, 180)), 'leg': (0.4, (140, 230, 255), (255, 255, 255))},
}
def glow(cid, rs):
    g = np.zeros((H, W, 3)); n = noise(1.2, 77)
    for reg, path, sy, sx in slices(rs, rs.keys()):
        spec = GLOW.get(cid, {}).get(reg)
        if not spec: continue
        w, ca, cb = spec
        ly, lx, fy, fx = local(sy, sx)
        t = (fy * 0.6 + n[sy, sx] * 0.4)[..., None]                                   # gradient along the part with a little noise
        col = np.array(ca) * (1 - t) + np.array(cb) * t
        veins = (np.abs(n[sy, sx] - 0.5) < 0.05 + 0.1 * w)[..., None]                   # glowing seams sized by weight
        g[sy, sx] = np.maximum(g[sy, sx], col * veins * min(1.0, 0.45 + w))
    return g

if __name__ == '__main__':
    for cid in UV:
        rs = rects(cid)
        im = np.asarray(Image.open(T + cid + '.png').convert('RGBA'), dtype=float)
        rgb = paint(cid, im[..., :3].copy(), rs)
        Image.fromarray(np.concatenate([np.clip(rgb, 0, 255), im[..., 3:]], axis=2).astype('uint8'), 'RGBA').save(T + cid + '.png')
        Image.fromarray(np.clip(glow(cid, rs), 0, 255).astype('uint8'), 'RGB').save(T + cid + '_glow.png')
    print('regions painted:', len(UV))
