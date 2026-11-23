"""Synthesises per-family xenomorph voices (ambient / hurt / death) with numpy + ffmpeg into sounds/xeno/voice_<family>_<kind>.ogg."""
import numpy as np, subprocess, os, wave
ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..')
OUT = ROOT + '/src/main/resources/assets/xenomorph/sounds/xeno/'
SR = 22050
rng = np.random.default_rng(11)
def t_(d): return np.arange(int(SR * d)) / SR
def env(n, a=0.02, r=0.3):
    e = np.ones(n); na = max(1, int(SR * a)); nr = max(1, int(n * r)); e[:na] = np.linspace(0, 1, na); e[-nr:] *= np.linspace(1, 0, nr); return e
def lp(x, k):  # crude low-pass by moving average
    k = max(1, int(k)); return np.convolve(x, np.ones(k) / k, 'same')
def bp(x, lo, hi): return lp(x, SR / lo) - lp(x, SR / hi) if hi > lo else x
def noise(n): return rng.standard_normal(n)
def tone(t, f0, f1=None, vib=0.0, vr=6.0, harm=(1.0, 0.5, 0.25)):
    f1 = f0 if f1 is None else f1; f = np.linspace(f0, f1, len(t)) * (1 + vib * np.sin(2 * np.pi * vr * t)); ph = 2 * np.pi * np.cumsum(f) / SR
    return sum(h * np.sin((i + 1) * ph) for i, h in enumerate(harm))
def norm(x): return x / (np.abs(x).max() + 1e-9) * 0.85
# family -> kind -> generator
def deep(kind):
    d = {'amb': 1.6, 'hurt': 0.9, 'death': 2.4}[kind]; t = t_(d); n = len(t)
    f0 = {'amb': 55, 'hurt': 80, 'death': 70}[kind]; f1 = {'amb': 48, 'hurt': 50, 'death': 30}[kind]
    x = tone(t, f0, f1, 0.04, 5, (1, .6, .4, .2)) * env(n, .05, .5) + 0.5 * bp(noise(n), 60, 600) * env(n, .03, .6)
    if kind == 'hurt': x += 0.7 * bp(noise(n), 200, 2500) * np.exp(-t * 7)
    return norm(x)
def shrill(kind):
    d = {'amb': 0.7, 'hurt': 0.45, 'death': 1.1}[kind]; t = t_(d); n = len(t)
    f0 = {'amb': 1800, 'hurt': 2600, 'death': 2400}[kind]; f1 = {'amb': 2600, 'hurt': 1400, 'death': 500}[kind]
    x = tone(t, f0, f1, 0.05, 28, (1, .4, .2)) * env(n, .01, .5) + 0.3 * bp(noise(n), 2000, 6000) * env(n, .01, .5)
    return norm(x)
def rattle(kind):
    d = {'amb': 1.0, 'hurt': 0.6, 'death': 1.6}[kind]; t = t_(d); n = len(t); x = np.zeros(n)
    k = {'amb': 9, 'hurt': 14, 'death': 24}[kind]
    for i in range(k):
        p = int(rng.random() * (n - 800)); l = 500 + int(rng.random() * 300); c = bp(noise(l), 800, 4000) * np.exp(-np.arange(l) / 90.0); x[p:p + l] += c * (0.5 + rng.random())
    return norm(x + 0.2 * tone(t, 220, 120, 0.1, 17) * env(n, .02, .7))
def ether(kind):
    d = {'amb': 2.0, 'hurt': 0.8, 'death': 2.4}[kind]; t = t_(d); n = len(t)
    f0 = {'amb': 380, 'hurt': 900, 'death': 700}[kind]; f1 = {'amb': 520, 'hurt': 300, 'death': 120}[kind]
    x = tone(t, f0, f1, 0.08, 3, (1, .3, .5)) * env(n, .2, .6) + 0.6 * bp(noise(n), 300, 3000) * env(n, .3, .6) * (0.6 + 0.4 * np.sin(2 * np.pi * 2 * t))
    return norm(x)
def fiery(kind):
    d = {'amb': 1.2, 'hurt': 0.6, 'death': 1.8}[kind]; t = t_(d); n = len(t); x = np.zeros(n)
    for i in range({'amb': 30, 'hurt': 50, 'death': 90}[kind]):
        p = int(rng.random() * (n - 300)); l = 80 + int(rng.random() * 200); x[p:p + l] += noise(l) * np.exp(-np.arange(l) / 40.0) * (0.3 + rng.random())
    return norm(x + 0.35 * bp(noise(n), 100, 900) * env(n, .05, .6) + (0.4 * tone(t, 90, 40, 0, 5, (1, .5)) * env(n, .03, .7) if kind == 'death' else 0))
def aqua(kind):
    d = {'amb': 1.0, 'hurt': 0.5, 'death': 1.4}[kind]; t = t_(d); n = len(t)
    x = tone(t, 1500, 3200 if kind != 'death' else 400, 0.03, 9, (1, .2)) * env(n, .03, .5) * (0.5 + 0.5 * (np.sin(2 * np.pi * 14 * t) > 0))   # chirps / clicks
    return norm(x + 0.15 * bp(noise(n), 400, 2500) * env(n, .02, .5))
def reptile(kind):
    d = {'amb': 1.4, 'hurt': 0.7, 'death': 2.0}[kind]; t = t_(d); n = len(t)
    x = 0.8 * bp(noise(n), 2500, 8000) * env(n, .15, .6) + 0.4 * tone(t, 90 if kind != 'death' else 110, 60, 0.12, 11, (1, .5, .3)) * env(n, .1, .6)
    return norm(x)
FAMS = {1: deep, 2: shrill, 3: rattle, 4: ether, 5: fiery, 6: aqua, 7: reptile}
def write(path, x):
    wav = path + '.wav'; w = wave.open(wav, 'wb'); w.setnchannels(1); w.setsampwidth(2); w.setframerate(SR); w.writeframes((x * 32000).astype('<i2').tobytes()); w.close()
    subprocess.run(['ffmpeg', '-y', '-loglevel', 'error', '-i', wav, '-c:a', 'libvorbis', '-q:a', '3', path], check=True); os.remove(wav)
if __name__ == '__main__':
    os.makedirs(OUT, exist_ok=True)
    for fam, fn in FAMS.items():
        for kind in ('amb', 'hurt', 'death'): write(OUT + f'voice_{fam}_{kind}.ogg', fn(kind))
    print('voices written', len(FAMS) * 3)
