#!/usr/bin/env python3
"""
generate_synth.py
=================

Generative F1 engine sound samples for the F1 Sound Android app.

Outputs a set of mono 22050 Hz 16-bit PCM WAV files suitable for looping.
Each sample represents a different operating regime of a high-revving
V10-style Formula 1 engine.

The sound model is purposely simple but follows well-known principles of
internal-combustion engine noise:

  * Strong fundamental + integer harmonics with -6 dB/oct roll-off
    (typical of impulse-excited mechanical resonance).
  * Band-limited noise (exhaust whoosh / tyre roar) modulated by RPM.
  * Optional "combustion crackle" – a higher-frequency noise layer that is
    pulsed in synchrony with the firing interval.
  * RPM-modulated tremolo to suggest individual cylinder firings.
  * Smooth glissando applied to the fundamental for accel / brake profiles.

The output is normalised to -1 dBFS peak and written as proper RIFF WAV
files using the standard library ``wave`` module (no extra dependencies
beyond NumPy + SciPy).

Run::

    python3 generate_synth.py                 # writes to default OUTPUT_DIR
    OUTPUT_DIR=/some/path python3 ...         # writes elsewhere

Defaults to:
    OUTPUT_DIR = /workspace/f1-sound-app/sounds
"""
from __future__ import annotations

import math
import os
import wave
from dataclasses import dataclass, field
from typing import Callable, List

import numpy as np

# ---------------------------------------------------------------------------
# Configuration
# ---------------------------------------------------------------------------

SAMPLE_RATE = 22_050          # Hz, mono, sufficient for 11 kHz of bandwidth
DURATION_S = 4.0              # default loop length
OUTPUT_DIR = os.environ.get(
    "OUTPUT_DIR",
    "/workspace/f1-sound-app/sounds"
)
os.makedirs(OUTPUT_DIR, exist_ok=True)


# ---------------------------------------------------------------------------
# Helpers
# ---------------------------------------------------------------------------

def _harmonic_amplitudes(n_harm: int, roll_db_per_oct: float = -6.0,
                         odd_boost_db: float = 0.0) -> np.ndarray:
    """Return relative amplitudes for harmonics 1..n_harm."""
    amps = np.zeros(n_harm)
    for i in range(1, n_harm + 1):
        db = (i - 1) * roll_db_per_oct
        if i % 2 == 1:
            db += odd_boost_db
        amps[i - 1] = 10.0 ** (db / 20.0)
    return amps


def _lowpass(x: np.ndarray, cutoff_hz: float, sr: int = SAMPLE_RATE,
             order: int = 4) -> np.ndarray:
    from scipy.signal import butter, sosfiltfilt
    sos = butter(order, cutoff_hz / (sr / 2.0), btype="low", output="sos")
    return sosfiltfilt(sos, x).astype(np.float32)


def _highpass(x: np.ndarray, cutoff_hz: float, sr: int = SAMPLE_RATE,
              order: int = 4) -> np.ndarray:
    from scipy.signal import butter, sosfiltfilt
    sos = butter(order, cutoff_hz / (sr / 2.0), btype="high", output="sos")
    return sosfiltfilt(sos, x).astype(np.float32)


def _normalize(x: np.ndarray, peak_dbfs: float = -1.0) -> np.ndarray:
    peak = float(np.max(np.abs(x)))
    if peak < 1e-9:
        return x
    target = 10.0 ** (peak_dbfs / 20.0)
    return (x / peak * target).astype(np.float32)


def _loop_crossfade(x: np.ndarray, fade_ms: float = 60.0) -> np.ndarray:
    """Make x loop seamlessly.

    A click-free loop requires ``x[N-1] == x[0]`` and ideally a continuous
    first derivative. We achieve this by applying complementary fade-in /
    fade-out envelopes so that the first and last samples both equal
    silence (``y[0] = 0 = y[-1]``). The body of the signal is untouched,
    so the perceived "duck" is limited to ~ ``fade_ms`` at each join.
    """
    n = int(fade_ms * 1e-3 * SAMPLE_RATE)
    if n * 2 >= x.size:
        return x
    t = np.linspace(0.0, 1.0, n, dtype=np.float32)
    fade_in = 0.5 - 0.5 * np.cos(np.pi * t)
    fade_out = 0.5 + 0.5 * np.cos(np.pi * t)
    y = x.copy()
    y[:n] *= fade_in
    y[-n:] *= fade_out
    y[0] = 0.0
    y[-1] = 0.0
    return y


def _firing_tremolo(x: np.ndarray, f0_t: np.ndarray,
                    n_cyl: int = 10, depth: float = 0.10) -> np.ndarray:
    """RPM-modulated amplitude modulation suggesting individual firings."""
    if depth <= 0:
        return x
    t = np.arange(x.size) / SAMPLE_RATE
    rpm = float(np.mean(f0_t) * 60.0 / 2.0)
    f_fire = rpm * (n_cyl / 2.0) / 60.0
    if f_fire < 20.0:
        return x
    mod = 1.0 - depth + depth * np.cos(2.0 * math.pi * f_fire * t)
    return (x * mod).astype(np.float32)


def _write_wav(path: str, samples: np.ndarray, sr: int = SAMPLE_RATE) -> None:
    """Write a 16-bit mono PCM WAV file."""
    samples = np.clip(samples, -1.0, 1.0)
    pcm = (samples * 32767.0).astype(np.int16).tobytes()
    with wave.open(path, "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(sr)
        w.writeframes(pcm)


# ---------------------------------------------------------------------------
# Engine sound model
# ---------------------------------------------------------------------------

@dataclass
class EngineProfile:
    """Describes how RPM evolves over time within one loop."""
    name: str
    duration_s: float = DURATION_S
    # RPM as a function of normalized time in [0, 1]
    rpm_curve: Callable[[np.ndarray], np.ndarray] = field(
        default_factory=lambda: lambda t: np.full_like(t, 4000.0))
    # extra combustion crackle (0..1)
    crackle_level: float = 0.20
    # exhaust noise level (0..1)
    noise_level: float = 0.30
    # harmonic roll-off
    roll_db_per_oct: float = -6.0
    # odd-harmonic emphasis
    odd_boost_db: float = 1.5
    # number of harmonics
    n_harm: int = 28


# ---------------------------------------------------------------------------
# Core synthesis routine
# ---------------------------------------------------------------------------

def synthesize_engine(profile: EngineProfile) -> np.ndarray:
    """Render one engine-loop sample."""
    sr = SAMPLE_RATE
    n = int(profile.duration_s * sr)
    t = np.arange(n, dtype=np.float32) / sr
    tn = t / profile.duration_s

    rpm = profile.rpm_curve(tn)
    f0 = rpm / 60.0 / 2.0

    # 1) Harmonic stack (additive synthesis)
    amps = _harmonic_amplitudes(profile.n_harm, profile.roll_db_per_oct,
                                profile.odd_boost_db)
    harmonic = np.zeros(n, dtype=np.float32)
    for i, a in enumerate(amps, start=1):
        phase = np.random.uniform(0.0, 2.0 * math.pi)
        harmonic += a * np.sin(2.0 * math.pi * i * f0 * t + phase)

    # 2) Exhaust noise: band-limited white noise modulated by RPM
    rng = np.random.default_rng(seed=int(np.mean(rpm)) % (2**31))
    noise = rng.standard_normal(n).astype(np.float32)
    cutoff = float(np.clip(2.0 * f0.mean() * 6.0, 800.0, 6_000.0))
    noise = _lowpass(noise, cutoff)
    f_mod = float(f0.mean()) * 2.0
    noise *= 1.0 - 0.3 * np.cos(2.0 * math.pi * f_mod * t)
    norm = np.max(np.abs(noise)) + 1e-9
    noise = noise / norm * profile.noise_level

    # 3) Crackle: short noise bursts at the firing interval
    crackle = np.zeros(n, dtype=np.float32)
    if profile.crackle_level > 0 and float(f0.mean()) > 30.0:
        n_cyl = 10
        f_fire = float(f0.mean()) * n_cyl
        burst_env = _highpass(rng.standard_normal(n).astype(np.float32), 2_000.0)
        pulse = (np.sin(2.0 * math.pi * f_fire * t) > 0.85).astype(np.float32)
        crackle = profile.crackle_level * burst_env * pulse

    mix = harmonic + noise + crackle
    mix = np.tanh(mix * 1.3) * 0.9
    mix = _firing_tremolo(mix, f0, n_cyl=10, depth=0.12)
    mix = _normalize(mix, peak_dbfs=-1.0)
    mix = _loop_crossfade(mix, fade_ms=60.0)
    return mix


# ---------------------------------------------------------------------------
# Profiles (one per regime)
# ---------------------------------------------------------------------------

def profile_idle() -> EngineProfile:
    return EngineProfile(
        name="idle",
        duration_s=4.0,
        rpm_curve=lambda t: 3000.0 + 60.0 * np.sin(2.0 * np.pi * 1.2 * t),
        crackle_level=0.05,
        noise_level=0.18,
        roll_db_per_oct=-5.0,
        odd_boost_db=2.0,
        n_harm=18,
    )


def profile_cruise_low() -> EngineProfile:
    return EngineProfile(
        name="cruise_low",
        duration_s=4.0,
        rpm_curve=lambda t: np.full_like(t, 6500.0),
        crackle_level=0.08,
        noise_level=0.25,
        roll_db_per_oct=-6.0,
        odd_boost_db=1.5,
        n_harm=22,
    )


def profile_cruise_high() -> EngineProfile:
    return EngineProfile(
        name="cruise_high",
        duration_s=4.0,
        rpm_curve=lambda t: 9500.0 + 200.0 * np.sin(2.0 * np.pi * 0.7 * t),
        crackle_level=0.12,
        noise_level=0.32,
        roll_db_per_oct=-6.5,
        odd_boost_db=1.0,
        n_harm=26,
    )


def profile_accelerating() -> EngineProfile:
    return EngineProfile(
        name="accelerating",
        duration_s=4.0,
        rpm_curve=lambda t: (6500.0 + 5500.0 * (t ** 0.7)) +
                             250.0 * np.sin(2.0 * np.pi * 5.0 * t),
        crackle_level=0.18,
        noise_level=0.40,
        roll_db_per_oct=-7.0,
        odd_boost_db=0.5,
        n_harm=30,
    )


def profile_hard_accel() -> EngineProfile:
    return EngineProfile(
        name="hard_accel",
        duration_s=4.0,
        rpm_curve=lambda t: (11_000.0 + 6500.0 * (t ** 0.55)) +
                             np.where(t > 0.85, 200.0 * np.sin(2 * np.pi * 18 * t), 0.0),
        crackle_level=0.32,
        noise_level=0.50,
        roll_db_per_oct=-7.0,
        odd_boost_db=0.0,
        n_harm=34,
    )


def profile_braking() -> EngineProfile:
    return EngineProfile(
        name="braking",
        duration_s=4.0,
        rpm_curve=lambda t: (12_000.0 - 7000.0 * (t ** 1.1)) +
                             np.where((t > 0.28) & (t < 0.34), 1500.0, 0.0),
        crackle_level=0.15,
        noise_level=0.38,
        roll_db_per_oct=-6.5,
        odd_boost_db=1.0,
        n_harm=28,
    )


def profile_rev_limiter() -> EngineProfile:
    return EngineProfile(
        name="rev_limiter",
        duration_s=4.0,
        rpm_curve=lambda t: 17_000.0 +
                             np.where((np.floor(t * 6.0) % 2.0) > 0.5,
                                      800.0 * np.sin(2 * np.pi * 12 * t),
                                      0.0),
        crackle_level=0.45,
        noise_level=0.55,
        roll_db_per_oct=-7.5,
        odd_boost_db=0.0,
        n_harm=36,
    )


# ---------------------------------------------------------------------------
# Entry point
# ---------------------------------------------------------------------------

PROFILES: List[EngineProfile] = [
    profile_idle(),
    profile_cruise_low(),
    profile_cruise_high(),
    profile_accelerating(),
    profile_hard_accel(),
    profile_braking(),
    profile_rev_limiter(),
]


def main() -> None:
    print(f"Rendering {len(PROFILES)} F1 engine samples "
          f"@ {SAMPLE_RATE} Hz, mono, 16-bit WAV -> {OUTPUT_DIR}")
    for p in PROFILES:
        out_path = os.path.join(OUTPUT_DIR, f"{p.name}.wav")
        sig = synthesize_engine(p)
        _write_wav(out_path, sig, sr=SAMPLE_RATE)
        size = os.path.getsize(out_path)
        rms_db = 20.0 * np.log10(np.sqrt(np.mean(sig.astype(np.float64) ** 2)) + 1e-12)
        peak_db = 20.0 * np.log10(np.max(np.abs(sig)) + 1e-12)
        print(f"  -> {out_path}  ({len(sig)/SAMPLE_RATE:.2f}s, "
              f"{size/1024:.1f} kB, RMS {rms_db:+.1f} dB, "
              f"peak {peak_db:+.1f} dB)")


if __name__ == "__main__":
    main()
