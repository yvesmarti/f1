# F1 Sound — v1.2 (vrais sons)

**Nouveau en v1.2 :** l'app utilise de vrais enregistrements de F1 (McLaren MP4-23 V8 et Williams FW18 V10, Wikimedia Commons). Trois boucles (ralenti, milieu, haut régime) sont calées sur une même hauteur et mélangées en fondu. Les freinages appuyés déclenchent une série de rétrogradages. Sources et mentions : `sounds/SOURCES.md`.

## Corrections de la v1.1

**Corrections apportées :**
- Détection de l'accélération : l'ancienne version lisait l'axe Y brut, qui contient la gravité. Téléphone vertical sur un support, l'app croyait accélérer en permanence. Elle utilise maintenant la gravité et l'accélération linéaire d'Android, et trouve l'avant de la voiture quelle que soit l'inclinaison du support (portrait).
- Seuils recalés pour une vraie voiture (0,04 G à 0,30 G), au lieu de valeurs adaptées au fait de secouer le téléphone.
- Son : les 3 boucles tournent en continu avec un fondu entre elles, au lieu d'être relancées 50 fois par seconde (ce qui produisait un grésillement).
- Boucles audio : suppression du « trou » de volume toutes les 4 secondes.
- Le son s'arrête quand l'app passe en arrière-plan (il restait bloqué auparavant).
- Interface en français.

**Compiler sans rien installer :** le dossier `.github/workflows/` contient une recette qui fait construire l'APK par GitHub à chaque envoi de fichiers (onglet Actions, puis l'artefact `f1-sound-apk`).

---

# F1 Sound — Android App

A minimalist Android app that plays **Formula 1 engine samples**
synchronised with the phone's accelerometer. When you accelerate or
brake, the app switches between idle / cruise / accelerate loops and
modulates the playback pitch in real time. An optional GPS readout
shows your real-world speed.

This is an **MVP** — a single Activity, no MVVM, no third-party
dependencies beyond AndroidX and Material Components.

---

## Features

* **Real F1 samples** (idle, cruise, accelerate — sourced from Wikimedia
  Commons under CC BY-SA 3.0, see `sounds/SOURCES.md`) plus a synthesised
  brake one-shot.
* **Accelerometer-driven audio** with a low-pass filter, virtual RPM
  (0 – 12 000) and a per-stream playback rate that simulates a real
  revving engine.
* **Live UI** showing RPM (with a bar), longitudinal G-force, optional
  GPS speed (km/h) and a flashing "BRAKING" indicator.
* **SoundPool pitch shifting** (0.8× — 1.5×) for realistic revving.
* **GPS toggle** — optional, requires `ACCESS_FINE_LOCATION`.
* **Volume slider**, keep-screen-on while running.

---

## Prerequisites

| Tool                | Version                          |
| ------------------- | -------------------------------- |
| Android Studio      | Hedgehog (2023.1.1) or newer     |
| JDK                 | 17 (bundled with recent AS)      |
| Android SDK         | Platform 34 + Build-Tools 34.x   |
| Gradle              | 8.5 (wrapper provided)           |

If you have never installed the Android SDK, get it via Android Studio's
first-run wizard (`Tools > SDK Manager`) — install **SDK 34**, **Build
Tools 34.0.0** and a recent **Platform Tools**.

> The Gradle wrapper JAR (`gradle/wrapper/gradle-wrapper.jar`) is **not**
> committed — Android Studio will generate it the first time you sync.
> If you prefer the CLI, run `gradle wrapper --gradle-version 8.5`
> once (requires a system Gradle 8.x) and the wrapper script will
> download the JAR.

---

## Building the APK

### Recommended: Android Studio

1. Open Android Studio → **File > Open** → select
   `/workspace/f1-sound-app/` (or the folder you cloned it into).
2. Wait for **Gradle Sync** to finish (downloads AndroidX, Kotlin,
   Material Components — about 1–2 min on a clean machine).
3. **Build > Build Bundle(s) / APK(s) > Build APK(s)**.
4. When the build completes, click **locate** in the toast — the APK
   is at:
   ```
   app/build/outputs/apk/debug/app-debug.apk
   ```

### CLI

Once the wrapper is generated (see Prerequisites):

```bash
cd /workspace/f1-sound-app
./gradlew assembleDebug
```

Output: `app/build/outputs/apk/debug/app-debug.apk`.

### Installing on a device

Enable **USB debugging** on the phone, plug it in, then:

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

The app appears as **F1 Sound** in your launcher.

---

## Using the app

1. Hold the phone **vertically, screen toward you, top edge facing
   the direction of travel** (this matches the assumed coordinate
   frame — see Limitations).
2. Tap **START**. The status text changes to *Listening to motion…*
   and the screen will stay on.
3. Tilt or move the phone forward (accelerate) or backward (brake).
   The audio engine ramps between idle / cruise / accelerate loops
   and the **BRAKING** indicator flashes when you decelerate hard.
4. Toggle the **Use GPS** switch if you want the real-world km/h
   readout. The app will ask for location permission — granting it is
   optional.
5. Tap **STOP** to release audio resources.

The **volume slider** adjusts the playback volume in real time.

---

## Architecture

```
┌─────────────────────┐
│  SensorManager      │  Accelerometer @ SENSOR_DELAY_GAME (~50 Hz)
│  (TYPE_ACCELEROMETER)
└──────────┬──────────┘
           │  raw (m/s²)
           ▼
┌─────────────────────────────────────────────┐
│  MainActivity.kt                            │
│  ─ low-pass filter (α = 0.15)               │
│  ─ convert to G                             │
│  ─ compute virtual RPM (0–12 000)           │
│  ─ pick sample + pitch from RPM zone        │
│  ─ trigger brake one-shot if G < −0.35      │
└──────────┬──────────────────────────────────┘
           │
           ▼
┌─────────────────────┐    ┌─────────────────────┐
│  SoundPool          │    │  TextView / SeekBar │
│  (4 streams, pitch) │    │  UI updates         │
└─────────────────────┘    └─────────────────────┘
```

`LocationManager` runs in parallel and updates the `Speed:` readout
once per second when GPS is enabled.

---

## Project layout

```
f1-sound-app/
├── build.gradle.kts                  ← root build script (plugin versions)
├── settings.gradle.kts               ← module list + repos
├── gradle.properties                 ← AndroidX, JVM args
├── gradle/wrapper/gradle-wrapper.properties
├── README.md                         ← this file
├── sounds/                           ← audio assets + docs
│   ├── idle.wav, cruise.wav, accelerate.wav, brake.wav
│   ├── brake_synth.py                ← regenerates brake.wav
│   ├── real/                         ← bonus: 5 Wikimedia F1 recordings
│   ├── README.md
│   └── SOURCES.md
└── app/
    ├── build.gradle.kts              ← module config
    ├── proguard-rules.pro
    └── src/main/
        ├── AndroidManifest.xml
        ├── java/com/f1sound/app/MainActivity.kt   ← everything in one file
        └── res/
            ├── raw/{idle,cruise,accelerate,brake}.wav
            ├── layout/activity_main.xml
            ├── values/{strings,colors,themes}.xml
            └── mipmap-*/            ← launcher icons (use defaults)
```

---

## Limitations & known issues

* **Coordinate frame** — the app reads `event.values[1]` directly
  (the Y axis). A production version should subtract gravity using a
  fused sensor and project the result into the world frame so the
  detection works no matter how the phone is oriented.
* **Foreground Service** — not implemented. The app runs only while
  it is the active foreground activity; locking the screen stops
  audio. A V2 should promote to a `MediaSessionService` for
  background playback.
* **Sample bank** — only 3 loops + 1 one-shot. Real F1 audio mixing
  benefits from layering multiple sources (intake, exhaust, turbo,
  tyre squeal). The `sounds/real/` folder already contains five
  authentic recordings you can layer on.
* **No rev-limiter / no gearbox** — the virtual RPM is a smooth
  curve driven directly by G. A more interesting model would add
  gear-dependent RPM ramps and a "blip" on downshift.
* **No screen-rotation handling** — the Activity is locked to
  `portrait` in the manifest to keep the audio pipeline simple.

---

## V2 ideas

* Crossfade between samples instead of hard switching.
* Use `ExoPlayer` or a custom `AudioTrack` for higher-fidelity pitch
  shifting (time-stretching without chipmunk effect).
* Promote to a foreground service with media-session controls
  (lock-screen play / pause).
* Allow the user to remap the coordinate frame / calibrate neutral.
* Add more samples — load `sounds/real/` lazily and cross-blend.

---

## Licence

* **App code**: see `LICENSE` (or pick your preferred licence — MIT
  recommended for an MVP).
* **Audio samples**: three of the four shipped WAVs are derived from
  Wikimedia Commons recordings under **CC BY-SA 3.0**. See
  `sounds/SOURCES.md` for the required attribution string.
* **`sounds/real/`**: same CC BY-SA 3.0 recordings, kept as a
  drop-in upgrade bank.
