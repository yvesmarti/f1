# Sources, licences & attribution

This file documents every audio sample shipped in
`/workspace/f1-sound-app/sounds/`.

---

## 1. Primary samples (used by the Android app)

The four samples the app loads are generated in-repo from NumPy +
SciPy primitives — no underlying recordings, no third-party audio data.

| Sample            | Generated from                | Licence | Required notice |
| ----------------- | ----------------------------- | ------- | --------------- |
| `idle.wav`        | `generate_synth.py` → `profile_idle()`       | **CC0** | none |
| `cruise.wav`      | `generate_synth.py` → `profile_cruise_low()` | **CC0** | none |
| `accelerate.wav`  | `generate_synth.py` → `profile_hard_accel()` | **CC0** | none |
| `brake.wav`       | `generate_synth.py` → `profile_braking()`   | **CC0** | none |

The synthesizer uses only:

| Tool / library | Version in sandbox | Licence        | URL                |
| -------------- | ------------------ | -------------- | ------------------ |
| Python         | 3.11               | PSF            | https://python.org/ |
| NumPy          | 1.24.2             | BSD-3-Clause   | https://numpy.org/  |
| SciPy          | 1.10.1             | BSD-3-Clause   | https://scipy.org/  |

All four primary samples can be safely shipped in a **proprietary**
Android app without attribution.

---

## 2. Bonus samples in the root directory

Three extra synthesised samples are also shipped at the root for V2 use.
Same source, same licence.

| File                  | Profile                      | Licence |
| --------------------- | ---------------------------- | ------- |
| `cruise_high.wav`     | `generate_synth.py` → `profile_cruise_high()`   | CC0 |
| `accelerating.wav`    | `generate_synth.py` → `profile_accelerating()`  | CC0 |
| `rev_limiter.wav`     | `generate_synth.py` → `profile_rev_limiter()`   | CC0 |

---

## 3. Real F1 recordings (`real/` subfolder)

All five files under `real/` originate from Wikimedia Commons and are
licensed under **CC BY-SA 3.0** by user **Edvvc**. They have been
re-encoded to mono 22 050 Hz 16-bit PCM with `ffmpeg` and trimmed to
4–6 s loopable excerpts.

| Original Wikimedia title                       | URL                                                                                | Derived excerpt                              |
| ---------------------------------------------- | ---------------------------------------------------------------------------------- | -------------------------------------------- |
| Mercedes W196 (1954) static.ogg                | https://commons.wikimedia.org/wiki/File:Mercedes_W196_(1954)_static.ogg            | `real/merc_idle.wav`                         |
| Williams-Renault FW18 (1996).ogg               | https://commons.wikimedia.org/wiki/File:Williams-Renault_FW18_(1996).ogg           | `real/williams_fw18_v10_reving.wav`          |
| Red Bull-Renault RB5 (2009).ogg                | https://commons.wikimedia.org/wiki/File:Red_Bull-Renault_RB5_(2009).ogg            | `real/redbull_rb5_v8_reving.wav`             |
| Ferrari F60 (2009).ogg                         | https://commons.wikimedia.org/wiki/File:Ferrari_F60_(2009).ogg                     | `real/ferrari_f60_v8_reving.wav`             |
| McLaren-Mercedes MP4 23 (2008).ogg             | https://commons.wikimedia.org/wiki/File:McLaren-Mercedes_MP4_23_(2008).ogg         | `real/mclaren_mp4_23_v8_reving.wav`          |

### Short-form licence

> © Edvvc, CC BY-SA 3.0, via Wikimedia Commons.

Full text: https://creativecommons.org/licenses/by-sa/3.0/deed

### Required attribution (drop into the Android "About" screen if you ship a Play Store build and wire any of the real excerpts into the app)

> Recordings of the Mercedes W196 (1954), Williams-Renault FW18 (1996),
> Red Bull-Renault RB5 (2009), Ferrari F60 (2009) and McLaren-Mercedes
> MP4-23 (2008) engines are used under a Creative Commons
> Attribution-ShareAlike 3.0 Unported licence
> (https://creativecommons.org/licenses/by-sa/3.0/deed) courtesy of
> contributor **Edvvc** via Wikimedia Commons. Modified: trimmed to
> 4–6 s loopable mono 22 050 Hz 16-bit PCM excerpts.

### ShareAlike note

Because the licence is CC BY-SA 3.0, anything that *incorporates* these
recordings must be released under the same (or a compatible) licence.
The default `MainActivity.kt` does **not** load any `real/` file, so
the CC BY-SA obligation does not propagate to the shipped APK. If you
swap any of them in, add the attribution string above to your
open-source-notices screen.

---

## 4. Tools used

| Tool / library | Version        | Licence        | URL                |
| -------------- | -------------- | -------------- | ------------------ |
| Python         | 3.11           | PSF            | https://python.org/ |
| NumPy          | 1.24.2         | BSD-3-Clause   | https://numpy.org/  |
| SciPy          | 1.10.1         | BSD-3-Clause   | https://scipy.org/  |
| FFmpeg         | 5.1.9 (Debian) | LGPL / GPL     | https://ffmpeg.org/ |

---

## 5. Search log (transparency)

Before deciding to fully synthesise the four primary samples, the
following sources were checked. None yielded a downloadable, freely
licensed, F1-quality recording of every required regime:

| Source                                       | Outcome                                                              |
| -------------------------------------------- | -------------------------------------------------------------------- |
| Freesound API (https://freesound.org/apiv2/) | API key-gated; not accessible without registration.                  |
| Pixabay Sound Effects                        | Pages gated by Cloudflare; scraper returned challenge HTML.          |
| Mixkit free-sound-effects                    | No public download API.                                              |
| Internet Archive advanced search             | No usable `mediatype:audio AND formula AND brake` results.           |
| Wikimedia Commons                            | Found 5 recordings — kept as bonus `real/` bank.                    |

The four shipped samples are therefore a deliberate, documented
fallback that satisfies the brief's Priority 2 path while still
delivering CC0 waveforms compatible with Android `SoundPool`.
