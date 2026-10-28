"""Synthesises AGAPE's church bell (own work, CC0): an additive model of a tuned bell, hum to nominal, two strikes.

Usage: python tools/build_bell.py app/src/main/res/raw/church_bell.wav  (needs numpy)
"""
import numpy as np, wave, sys
sr = 22050
dur = 6.0
t = np.arange(int(sr * dur)) / sr
prime = 311.1  # E-flat 4, a mid-sized tower bell
# (ratio to prime, amplitude, decay seconds); a bell's partials are not harmonic.
partials = [(0.5, 0.55, 4.5), (1.0, 0.60, 3.2), (1.183, 0.42, 2.6), (1.506, 0.22, 1.8), (2.0, 0.50, 2.0),
            (2.514, 0.20, 1.2), (2.662, 0.18, 1.1), (3.011, 0.12, 0.8), (4.166, 0.06, 0.5), (5.433, 0.04, 0.35)]
def strike(start, gain):
    out = np.zeros_like(t)
    tt = t - start
    on = tt >= 0
    x = tt[on]
    for r, a, d in partials:
        f = prime * r
        # Each partial is a slightly split doublet, which gives the slow beating of a real bell.
        s = np.sin(2 * np.pi * f * x) + 0.6 * np.sin(2 * np.pi * (f + 0.7 + r * 0.15) * x + 0.4)
        out[on] += a * s * np.exp(-x / d)
    # The clapper: a very short, soft knock.
    rng = np.random.default_rng(7)
    k = rng.standard_normal(on.sum()) * np.exp(-x / 0.008) * 0.25
    out[on] += np.convolve(k, np.ones(6) / 6, mode="same")
    attack = np.clip(x / 0.004, 0, 1)
    out[on] *= attack
    return out * gain
y = strike(0.0, 1.0) + strike(1.6, 0.85)
fade = np.clip((dur - t) / 0.8, 0, 1)
y *= fade
y /= np.abs(y).max() * 1.12
pcm = (y * 32767).astype(np.int16)
with wave.open(sys.argv[1], "wb") as w:
    w.setnchannels(1); w.setsampwidth(2); w.setframerate(sr); w.writeframes(pcm.tobytes())
