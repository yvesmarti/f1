# F1 Sound App — Audio Assets

This directory contains the WAV files the Android app loads via
`SoundPool`. Four samples sit at the root in the exact names referenced
by `MainActivity.kt` and copied into `app/src/main/res/raw/`:

| File              | Source                              | Used as                          |
| ----------------- | ----------------------------------- | -------------------------------- |
| `idle.wav`        | `generate_synth.py` (idle profile)  | Loop while stationary / coasting |
| `cruise.wav`      | `generate_synth.py` (cruise_low)    | Mid-RPM loop (3 000–7 000 RPM)   |
| `accelerate.wav`  | `generate_synth.py` (hard_accel)    | High-RPM loop (> 7 000 RPM)      |
| `brake.wav`       | `generate_synth.py` (braking)       | One-shot on strong deceleration  |

All four files are:

* **Format**: RIFF WAVE / `pcm_s16le`, **16-bit**, **mono**
* **Sample rate**: 22 050 Hz
* **Duration**: 4.00 s
* **Loop seam**: zero (complementary raised-cosine fade-in/out — see
  `generate_synth.py::_loop_crossfade`)

---

## Bonus content

### `generate_synth.py` — additional sample profiles

The synthesis script also produces three extra samples that ship here
for V2 use (crossfading, layering, richer soundscapes):

| File                  | RPM curve          | Character                                  |
| --------------------- | ------------------ | ------------------------------------------ |
| `cruise_high.wav`     | ~ 9 500            | Bright tone, strong upper harmonics.       |
| `accelerating.wav`    | 6 500 → 12 000     | Smooth RPM glide + wheel-spin wobble.      |
| `rev_limiter.wav`     | ~ 17 000 plateau   | Sharp 6 Hz blips (cutting/claquement).     |

To swap any of them into the app, just rename and update the matching
`SoundPool.load(…)` call in `MainActivity.kt`.

### `real/` — authentic F1 recordings

Five real F1 recordings downloaded from Wikimedia Commons (CC BY-SA 3.0,
contributor **Edvvc**), re-encoded to mono 22 050 Hz 16-bit PCM:

| File                              | Engine                          | Era   |
| --------------------------------- | ------------------------------- | ----- |
| `real/merc_idle.wav`              | Mercedes W196 (straight-8)      | 1954  |
| `real/williams_fw18_v10_reving.wav` | Williams-Renault FW18 (V10)   | 1996  |
| `real/ferrari_f60_v8_reving.wav`  | Ferrari F60 (V8)                | 2009  |
| `real/mclaren_mp4_23_v8_reving.wav` | McLaren-Mercedes MP4-23 (V8)  | 2008  |
| `real/redbull_rb5_v8_reving.wav`  | Red Bull-Renault RB5 (V8)       | 2009  |

These are not loaded by the default `MainActivity.kt` but can replace
any of the four primary files by simple rename + `SoundPool.load`
update. See `SOURCES.md` for the required attribution.

---

## How the app picks a sample

`MainActivity.updateRpmAndAudio()` computes a smoothed virtual RPM
(0…12 000) from the longitudinal accelerometer reading:

| RPM zone          | Sample           | Pitch (`SoundPool.setRate`)  |
| ----------------- | ---------------- | ---------------------------- |
| 0 – 3 000         | `idle.wav`       | 0.8 × — 1.2 ×                |
| 3 000 – 7 000     | `cruise.wav`     | 0.9 × — 1.4 ×                |
| 7 000 – 12 000    | `accelerate.wav` | 1.0 × — 1.5 ×                |
| G < −0.35 (any)   | `brake.wav` one-shot | 0.9 × + |G| × 0.2         |

The brake one-shot is rate-limited to one per 1.2 s.

---

## Reproducing the samples

```bash
cd /workspace/f1-sound-app/sounds
python3 generate_synth.py
```

Requires Python 3.8+, NumPy and SciPy (both already in the sandbox).
The script is deterministic — running it produces byte-identical files
(fixed RNG seed inside).
