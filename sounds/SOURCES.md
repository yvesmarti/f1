# Sources, licences & attribution

## Sons utilisés par l'app (v1.2)

Les 4 sons de `app/src/main/res/raw/` sont des extraits retravaillés
(boucles, volume et hauteur stabilisés) de vrais enregistrements Wikimedia Commons.
Ils sont recréés par `build_real_loops.py`.

| Fichier app  | Enregistrement d'origine                 | Extrait        |
| ------------ | ---------------------------------------- | -------------- |
| `idle.wav`   | McLaren-Mercedes MP4 23 (2008).ogg       | 0,05 → 1,15 s  |
| `mid.wav`    | Williams-Renault FW18 (1996).ogg         | 1,95 → 2,55 s  |
| `high.wav`   | McLaren-Mercedes MP4 23 (2008).ogg       | 2,85 → 3,95 s  |
| `blip.wav`   | McLaren-Mercedes MP4 23 (2008).ogg       | 2,95 → 3,35 s  |

**À vérifier avant diffusion :** l'auteur et la licence ci-dessous proviennent de la
version précédente du projet. Contrôle-les sur la page Wikimedia de chacun des deux
fichiers (McLaren et Williams) et reprends la mention exacte indiquée.

Les anciens sons synthétiques (`sounds/*.wav`) ne sont plus utilisés.

---



## Enregistrements d'origine (`real/`)



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
L'app utilise désormais des extraits de ces enregistrements : la mention ci-dessus
doit figurer dans l'app (écran « À propos ») ou sa page de présentation si tu la diffuses.

---

## 4. Tools used

| Tool / library | Version        | Licence        | URL                |
| -------------- | -------------- | -------------- | ------------------ |
| Python         | 3.11           | PSF            | https://python.org/ |
| NumPy          | 1.24.2         | BSD-3-Clause   | https://numpy.org/  |
| SciPy          | 1.10.1         | BSD-3-Clause   | https://scipy.org/  |
| FFmpeg         | 5.1.9 (Debian) | LGPL / GPL     | https://ffmpeg.org/ |
