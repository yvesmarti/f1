"""Fabrique les boucles de l'app à partir des vrais enregistrements de sounds/real/.

- ralenti  : McLaren MP4-23, 0,05 s -> 1,15 s (~133 Hz)
- milieu   : Williams FW18, 1,95 s -> 2,55 s (montée 275 -> 320 Hz, aplatie à hauteur constante)
- haut     : McLaren MP4-23, 2,85 s -> 3,95 s (~609 Hz)
- blip     : McLaren MP4-23, 2,95 s -> 3,35 s, avec attaque/extinction (rétrogradage)

Usage : python3 build_real_loops.py  (écrit dans ../app/src/main/res/raw/)
"""
import wave
import numpy as np

SR = 22050
OUT = "../app/src/main/res/raw/"


def load(name):
    w = wave.open("real/" + name)
    x = np.frombuffer(w.readframes(w.getnframes()), dtype=np.int16).astype(float) / 32768
    return x


def cut(x, a, b):
    return x[int(a * SR):int(b * SR)].copy()


def highpass(x, fc=45.0):
    a = np.exp(-2 * np.pi * fc / SR)
    y = np.zeros_like(x)
    prev_x = prev_y = 0.0
    for i, v in enumerate(x):
        prev_y = a * (prev_y + v - prev_x)
        prev_x = v
        y[i] = prev_y
    return y


def flatten_level(x, win_s=0.08):
    """Supprime les variations de volume (évite l'effet de pompage en boucle)."""
    n = int(win_s * SR)
    env = np.sqrt(np.convolve(x ** 2, np.ones(n) / n, mode="same"))
    env = np.convolve(env, np.ones(n) / n, mode="same")
    return x / np.maximum(env, 1e-4)


def flatten_pitch(x, p_start, p_end):
    """Rééchantillonne une montée de régime pour obtenir une hauteur constante."""
    dur = len(x) / SR
    p_mean = (p_start + p_end) / 2
    out, pos = [], 0.0
    while pos < len(x) - 2:
        t = pos / len(x) * dur
        p = p_start + (p_end - p_start) * (t / dur)
        i = int(pos)
        frac = pos - i
        out.append(x[i] * (1 - frac) + x[i + 1] * frac)
        pos += p_mean / p
    return np.array(out)


def make_loop(x, fade_s=0.08):
    """Fondu enchaîné fin -> début : la boucle ne s'entend plus."""
    L = int(fade_s * SR)
    t = np.linspace(0, np.pi / 2, L)
    y = x[:-L].copy()
    y[:L] = x[:L] * np.sin(t) + x[-L:] * np.cos(t)
    return y


def normalize(x, rms):
    x = x * (rms / np.sqrt(np.mean(x ** 2)))
    return np.tanh(x * 1.2) / 1.2  # limite douce, sans écrêtage dur


def save(name, x):
    w = wave.open(OUT + name, "wb")
    w.setnchannels(1)
    w.setsampwidth(2)
    w.setframerate(SR)
    w.writeframes((np.clip(x, -1, 1) * 32767).astype(np.int16).tobytes())
    w.close()


mc = load("mclaren_mp4_23_v8_reving.wav")
wi = load("williams_fw18_v10_reving.wav")

idle = make_loop(normalize(flatten_level(highpass(cut(mc, 0.05, 1.15))), 0.22))
mid = make_loop(normalize(flatten_level(flatten_pitch(highpass(cut(wi, 1.95, 2.55)), 275, 320)), 0.28))
high = make_loop(normalize(flatten_level(highpass(cut(mc, 2.85, 3.95))), 0.28))

blip = normalize(flatten_level(highpass(cut(mc, 2.95, 3.35))), 0.30)
tb = np.arange(len(blip)) / SR
blip *= np.minimum(1, tb / 0.008) * np.exp(-tb / 0.12)

for n, s in [("idle.wav", idle), ("mid.wav", mid), ("high.wav", high), ("blip.wav", blip)]:
    save(n, s)
    print(n, f"{len(s) / SR:.2f} s")
