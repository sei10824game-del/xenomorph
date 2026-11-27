"""Godzilla-morph texture + glow: dark blue-black hide, ribbed and muscled; glowing blue-white dorsal fins, tail ridges and tubes."""
import importlib.util, json, os, numpy as np
from PIL import Image
ROOT = os.path.dirname(os.path.abspath(__file__))
spec = importlib.util.spec_from_file_location('rr', os.path.join(ROOT, 'retex_regions.py')); rr = importlib.util.module_from_spec(spec); spec.loader.exec_module(rr)
T = os.path.join(ROOT, '..', 'src/main/resources/assets/xenomorph/textures/entity/')
src = open(os.path.join(ROOT, 'retex_mutants.py')).read(); pre = src[:src.index('for cid,(src,mul,add,kind) in spec.items():')].replace("T='/home/user/xenomorph/src/main/resources/assets/xenomorph/textures/entity/'", f"T='{T}'")
g = {}; exec(pre, g)
im = np.asarray(Image.open(T + 'titan_xeno.png').convert('RGBA'), dtype=float)
keep = im[144:192, :, :3] * np.array((0.5, 0.58, 0.85)) + np.array((0, 3, 14))
rgb = im[..., :3] * np.array((0.5, 0.58, 0.85)) + np.array((0, 3, 14))
rgb = g['overlay']('muscle', rgb); rgb[144:192] = keep
rs = {}
uv = json.load(open(os.path.join(ROOT, 'godzilla_uvmap.json')))['godzillamorph']
for path, rl in uv.items():
    r = rr.region_of(path)
    if r: rs.setdefault(r, []).extend((path, tuple(x)) for x in rl)
rgb = rr.paint('xx', rgb, rs)
Image.fromarray(np.concatenate([np.clip(rgb, 0, 255), im[..., 3:]], axis=2).astype('uint8'), 'RGBA').save(T + 'godzillamorph.png')
rr.GLOW['godzillamorph'] = {'torso': (1.0, (110, 160, 255), (255, 255, 255)), 'tail': (0.95, (110, 160, 255), (240, 250, 255)), 'tube': (0.8, (120, 170, 255), (255, 255, 255)),
                            'head': (0.45, (140, 200, 255), (255, 255, 255)), 'jaw': (0.6, (150, 255, 90), (230, 255, 150)), 'arm': (0.25, (110, 160, 255), (200, 225, 255)), 'leg': (0.25, (110, 160, 255), (200, 225, 255)), 'hand': (0.35, (150, 255, 90), (230, 255, 150))}
Image.fromarray(np.clip(rr.glow('godzillamorph', rs), 0, 255).astype('uint8'), 'RGB').save(T + 'godzillamorph_glow.png')
print('godzillamorph texture written')
