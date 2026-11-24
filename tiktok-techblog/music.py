"""Генерирует спокойный lo-fi фон (без авторских прав): music.wav, ~64 с."""
import numpy as np, wave, os
SR = 44100; BPM = 90; beat = 60 / BPM; bar = beat * 4
def note(m): return 440 * 2 ** ((m - 69) / 12)
prog = [[57, 60, 64, 67], [53, 57, 60, 64], [48, 52, 55, 59], [55, 59, 62, 66]]  # Am7 Fmaj7 Cmaj7 G
bars = 24; N = int(SR * bar * bars); out = np.zeros(N)
t_all = np.arange(N) / SR
rng = np.random.default_rng(7)
for b in range(bars):
    ch = prog[b % 4]; s = int(b * bar * SR); n = int(bar * SR); t = np.arange(n) / SR
    env = np.minimum(1, t / 0.4) * np.minimum(1, (bar - t) / 0.4)
    pad = sum(np.sin(2 * np.pi * note(m) * t + 0.3 * np.sin(2 * np.pi * 0.2 * t)) for m in ch) / len(ch)
    out[s:s + n] += 0.18 * pad * env
    bass = np.sin(2 * np.pi * note(ch[0] - 12) * t) * np.exp(-t * 1.2)
    out[s:s + n] += 0.22 * bass
    for k in range(8):  # мягкое арпеджио
        st = s + int(k * beat / 2 * SR); L = int(beat * SR)
        tt = np.arange(L) / SR; m = ch[[0, 2, 1, 3, 2, 1, 3, 2][k]] + 12
        tone = (np.sin(2 * np.pi * note(m) * tt) + 0.3 * np.sin(4 * np.pi * note(m) * tt)) * np.exp(-tt * 6)
        e = min(N, st + L); out[st:e] += 0.09 * tone[:e - st]
    for k in range(4):  # бочка и хэт
        st = s + int(k * beat * SR); L = int(0.25 * SR); tt = np.arange(L) / SR
        if k in (0, 2): out[st:st + L] += 0.35 * np.sin(2 * np.pi * (50 + 60 * np.exp(-tt * 30)) * tt) * np.exp(-tt * 14)
        hs = st + int(beat / 2 * SR); Lh = int(0.05 * SR)
        out[hs:hs + Lh] += 0.025 * rng.standard_normal(Lh) * np.exp(-np.arange(Lh) / SR * 80)
out = np.tanh(out * 1.2) / 1.2
out += 0.004 * rng.standard_normal(N)  # винил-шум
out /= np.abs(out).max() * 1.1
st = np.stack([out, np.roll(out, 300)], 1)
with wave.open(os.path.join(os.path.dirname(os.path.abspath(__file__)), "_build", "music.wav"), "wb") as w:
    w.setnchannels(2); w.setsampwidth(2); w.setframerate(SR); w.writeframes((st * 32767).astype(np.int16).tobytes())
