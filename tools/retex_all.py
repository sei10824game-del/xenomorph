"""Region-aware detailing for every non-mutant caste. Always starts from the pristine textures of BASE (a git commit), so re-running never compounds.
Needs tools/all_uvmap.json (UvDump). Usage: python3 -I tools/retex_all.py"""
import json, os, subprocess, io, numpy as np
from PIL import Image
import importlib.util
ROOT = os.path.dirname(os.path.abspath(__file__))
spec = importlib.util.spec_from_file_location('rr', os.path.join(ROOT, 'retex_regions.py')); rr = importlib.util.module_from_spec(spec); spec.loader.exec_module(rr)
BASE = 'd66f71cde591ded2dc4c6eb1304311b535bf16c9'
UV = json.load(open(os.path.join(ROOT, 'all_uvmap.json')))
MUTANTS = set(json.load(open(os.path.join(ROOT, 'mutant_uvmap.json'))).keys())
H, W = 256, 512
REL = 'src/main/resources/assets/xenomorph/textures/entity/'
GIANT = {'titan_xeno', 'true_king', 'queen_mother', 'empress', 'matriarch', 'king', 'red_queen_mother', 'white_king', 'k_fatalis', 'berserker', 'ultramorph', 'symbiotic_brute', 'brute', 'crusher', 'predalien_queen', 'dog_queen', 'queen'}
ANIMAL = {'panther_alien', 'goat_alien', 'bat_alien', 'winterbeast', 'bull_alien', 'tusked_xeno', 'arachnid'}
PALE = {'neomorph', 'bloodburster', 'protomorph', 'newborn', 'infectoid', 'deacon', 'bellyburster', 'white_hybrid', 'white_king', 'seven_drone', 'siren'}
def family(i):
    if i.startswith('red_'): return 'red'
    if i.startswith('k_'): return 'k'
    if i.startswith('clone_') or i == 'clone_xenomorph': return 'clone'
    if i.startswith('pathogen') or i in ('harbinger', 'thrasher', 'goreburster'): return 'pathogen'
    if i in PALE: return 'pale'
    if i in ANIMAL: return 'animal'
    return 'classic'
def base_png(i):
    out = subprocess.run(['git', 'show', f'{BASE}:{REL}{i}.png'], cwd=os.path.join(ROOT, '..'), capture_output=True)
    return np.asarray(Image.open(io.BytesIO(out.stdout)).convert('RGBA'), dtype=float)

def paint(i, fam, img, rs):
    n = rr.noise(2, abs(hash(i)) % 997)
    for reg, path, sy, sx in rr.slices(rs, rs.keys()):
        ly, lx, fy, fx = rr.local(sy, sx)
        nn = n[sy, sx][..., None]
        # ---- common: wear, joints, ribs, tail rings
        if reg in ('hand', 'foot'):
            rr.mul(img, sy, sx, 0.82 + 0.22 * nn); rr.add(img, sy, sx, (n[sy, sx] > 0.84)[..., None] * np.array([-26., 18., -18.]))      # scuffs + acid burns
        if reg == 'foot': rr.mul(img, sy, sx, (0.78 + 0.22 * (1 - fy))[..., None])
        if path.endswith(('elbow', 'knee', 'shoulder')): rr.mul(img, sy, sx, 0.84)
        if reg == 'torso': rr.mul(img, sy, sx, (0.8 + 0.2 * (np.sin(ly * 1.05) > -0.1))[..., None])                                       # rib shadows
        if reg == 'tail': rr.mul(img, sy, sx, (0.82 + 0.18 * (lx % 7 > 1))[..., None])                                                # tail segment rings
        if reg in ('fore', 'arm', 'leg'): rr.add(img, sy, sx, ((fx > 0.4) & (fx < 0.55))[..., None] * 9.0)                              # bone ridge highlight
        if reg == 'hand': rr.add(img, sy, sx, (fy > 0.8)[..., None] * 18.0)                                                              # sharper claw tips
        if reg in ('head', 'jaw'): rr.add(img, sy, sx, ((lx % 5) == 0)[..., None] * 6.0)
        # ---- family looks
        if fam == 'red':
            if reg in ('fore', 'hand'): rr.add(img, sy, sx, (fy[..., None]) * np.array([22., 22., 24.]))                                  # metallic blade sheen
            if reg in ('fore', 'arm', 'leg', 'torso'): rr.add(img, sy, sx, ((np.sin(lx * 0.8) > 0.93) & (fy > 0.3))[..., None] * np.array([-10., -20., -20.]))   # blood streaks
        elif fam == 'k':
            if reg in ('torso', 'arm', 'fore', 'leg'): rr.mul(img, sy, sx, (0.78 + 0.28 * ((lx % 9 > 0) & (ly % 9 > 0)))[..., None])            # armour frame seams
        elif fam == 'clone':
            rr.mul(img, sy, sx, (0.86 + 0.28 * (np.floor(lx / 11 + np.floor(ly / 9)) % 2))[..., None]); rr.mul(img, sy, sx, (0.9 + 0.2 * nn))   # patchwork / suture blotches
        elif fam == 'pathogen':
            rr.mul(img, sy, sx, 0.88); rr.add(img, sy, sx, ((np.sin(lx * 0.6 + n[sy, sx] * 6) > 0.9)[..., None]) * 38.0)                    # wet black streaks
            if reg == 'hand': rr.add(img, sy, sx, (fy > 0.6)[..., None] * 40.0)                                                           # clouded white claws
        elif fam == 'pale':
            rr.add(img, sy, sx, ((np.abs(n[sy, sx] - 0.5) < 0.018)[..., None]) * np.array([-36., -48., -22.]))                            # dark vein network under translucent skin
            rr.add(img, sy, sx, ((np.abs(n[sy, sx] - 0.35) < 0.012)[..., None]) * np.array([-10., -26., -44.]) + 0)
        elif fam == 'animal':
            rr.mul(img, sy, sx, (0.78 + 0.34 * (np.sin(lx * 1.4 + n[sy, sx] * 7) > 0.2))[..., None])                                       # fur / hide striping
        elif fam == 'classic':
            if reg in ('head', 'torso') and i in ('queen', 'matriarch', 'empress', 'queen_mother', 'flying_queen', 'vampiric_queen'):
                rr.add(img, sy, sx, ((np.abs(n[sy, sx] - 0.5) < 0.02)[..., None]) * np.array([20., 8., 12.]))                              # royal vein network
        if i in GIANT:
            rr.add(img, sy, sx, (n[sy, sx] > 0.78)[..., None] * np.array([-12., 14., -14.]))                                               # moss / weathering
            rr.mul(img, sy, sx, (1 - 0.35 * (np.abs(n[sy, sx] - 0.52) < 0.012))[..., None])                                                # deep cracks
    return img

def main():
    done = 0
    for i, parts in UV.items():
        if i in MUTANTS: continue
        im = base_png(i)
        if im.size == 0: continue
        # reuse the rect machinery with this caste's parts
        rs = {}
        for path, rl in parts.items():
            r = rr.region_of(path)
            if r is None: continue
            rs.setdefault(r, []).extend((path, tuple(x)) for x in rl)
        rgb = paint(i, family(i), im[..., :3].copy(), rs)
        if i == 'deacon':   # tongue colour lives in the lower half of the teeth strip (x0..70, y232..256), clear of the teeth themselves
            rng = np.random.default_rng(3); rgb[232:256, 0:70] = np.array([205, 110, 125]) * (0.85 + 0.3 * rng.random((24, 70, 1)))
        Image.fromarray(np.concatenate([np.clip(rgb, 0, 255), im[..., 3:]], axis=2).astype('uint8'), 'RGBA').save(os.path.join(ROOT, '..', REL, i + '.png'))
        done += 1
    print('painted', done)
if __name__ == '__main__': main()
