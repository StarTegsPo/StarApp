# Синтез оригинального бита без внешних сэмплов: python3 music.py meta.json out.wav
import json, sys, wave, numpy as np
SR = 44100
meta = json.load(open(sys.argv[1])); M = meta['music']
dur = meta['dur'] + 0.6; N = int(SR * dur)
bpm = M['bpm']; beat = 60 / bpm; root = M.get('root', 45); prog = M.get('prog', [0, -4, 3, -2])
style = M.get('style', 'house'); drop = M.get('dropAt', 0)
rng = np.random.default_rng(len(meta['hits']) * 7 + bpm)
mix = np.zeros(N); duck = np.ones(N)
hz = lambda n: 440 * 2 ** ((n - 69) / 12)
T = lambda n: np.arange(n) / SR

def lp(x, fc):  # FFT-фильтр нижних частот
    X = np.fft.rfft(x); f = np.fft.rfftfreq(len(x), 1 / SR); X /= np.sqrt(1 + (f / fc) ** 4); return np.fft.irfft(X, len(x))
def hp(x, fc):
    X = np.fft.rfft(x); f = np.fft.rfftfreq(len(x), 1 / SR); X *= 1 / np.sqrt(1 + (fc / np.maximum(f, 1)) ** 4); return np.fft.irfft(X, len(x))
def add(buf, sig, t, g=1.0):
    i = int(t * SR)
    if i >= N or i + len(sig) <= 0: return
    s = sig[:N - i] if i >= 0 else sig[-i:N - i]; i = max(i, 0); buf[i:i + len(s)] += g * s

def kick(big=False):
    n = int(SR * (.6 if big else .35)); t = T(n)
    f = 45 + 110 * np.exp(-t * 28); ph = 2 * np.pi * np.cumsum(f) / SR
    return np.sin(ph) * np.exp(-t * (5 if big else 9)) + .3 * np.exp(-t * 300) * rng.standard_normal(n) * 0.3
noise_bank = rng.standard_normal(SR)
def hat(open_=False):
    n = int(SR * (.18 if open_ else .05)); x = np.diff(np.diff(noise_bank[:n + 2])) / 4
    return x * np.exp(-T(n) * (18 if open_ else 70))
def clap():
    n = int(SR * .22); x = hp(noise_bank[1000:1000 + n], 900); env = np.exp(-T(n) * 22)
    for k in (0.0, .011, .022): env += .6 * (T(n) >= k) * np.exp(-np.maximum(T(n) - k, 0) * 140)
    return x * env * .5
def saw(f, n, det=(0,)):
    t = T(n); return sum(2 * ((t * f * (1 + d)) % 1) - 1 for d in det) / len(det)

bars = int(np.ceil(dur / (beat * 4))) + 1
drums = np.zeros(N); bass = np.zeros(N); pad = np.zeros(N); arp = np.zeros(N); fx = np.zeros(N)
for b in range(bars):
    ch = prog[b % len(prog)]; t0 = b * 4 * beat
    # пэд (аккорд минор)
    n = int(4 * beat * SR); notes = [root + 12 + ch, root + 15 + ch, root + 19 + ch, root + 24 + ch]
    p = sum(saw(hz(x), n, (-.004, 0, .005)) for x in notes) / 4
    env = np.minimum(1, T(n) / .08) * np.minimum(1, (n / SR - T(n)) / .1)
    add(pad, p * env, t0, .5)
    for s in range(16):
        ts = t0 + s * beat / 4; on = ts >= drop
        # арпеджио
        if on and style != 'chill':
            nn = notes[[0, 2, 1, 3, 2, 1, 3, 0][s % 8]] + 12; m = int(SR * beat / 4)
            a = np.sign(np.sin(2 * np.pi * hz(nn) * T(m))) * .5 + np.sin(2 * np.pi * hz(nn) * T(m))
            add(arp, a * np.exp(-T(m) * 14), ts, .22)
        if not on: continue
        if style == 'house':
            if s % 4 == 0: add(drums, kick(), ts, 1.0); add(duck, -.6 * np.exp(-T(int(SR * beat)) * 8), ts)
            if s % 4 == 2: add(drums, hat(True), ts, .35)
            elif s % 2 == 1: add(drums, hat(), ts, .25)
            if s in (4, 12): add(drums, clap(), ts, .9)
            if s % 2 == 1 or s % 4 == 2:
                m = int(SR * beat / 4 * .9); f = hz(root - 12 + ch + (12 if s % 4 == 3 else 0))
                add(bass, (saw(f, m) * .6 + np.sin(2 * np.pi * f * T(m))) * np.exp(-T(m) * 6), ts, .7)
        else:  # трэп: халфтайм, 808, роллы хэтов
            if s in (0, 7, 10) or (b % 2 and s == 14):
                add(drums, kick(True), ts, 1.0)
                m = int(SR * beat * 1.4); f = hz(root - 12 + ch)
                add(bass, np.tanh(2.2 * np.sin(2 * np.pi * f * T(m))) * np.exp(-T(m) * 2.2), ts, .75)
                add(duck, -.5 * np.exp(-T(int(SR * beat)) * 6), ts)
            if s == 8: add(drums, clap(), ts, 1.0)
            if s % 2 == 0: add(drums, hat(), ts, .3)
            if b % 2 == 1 and s >= 12: add(drums, hat(), ts + beat / 8, .22)
pad = lp(pad, 1400); arp = lp(arp, 3500)
pre = np.arange(N) / SR < drop
if drop: pad[pre] *= 1.6; arp *= 1
for h in meta['hits']:
    t = h['t']; big = h['k'] == 'impact'
    if big:  # вжух перед ударом + бум
        n = int(SR * .35); w = hp(rng.standard_normal(n), 1500) * (T(n) / .35) ** 3
        add(fx, w, t - .35, .35 if t > .3 else 0)
        n = int(SR * .9); add(fx, np.sin(2 * np.pi * (38 + 60 * np.exp(-T(n) * 10)) * T(n)) * np.exp(-T(n) * 4), t, .8)
        add(fx, hp(rng.standard_normal(int(SR * .3)), 2500) * np.exp(-T(int(SR * .3)) * 12), t, .25)
    else:  # клик/поп
        n = int(SR * .12); add(fx, np.sin(2 * np.pi * (900 * np.exp(-T(n) * 30) + 300) * T(n)) * np.exp(-T(n) * 40), t, .35)
duck = np.clip(duck, .25, 1)
out = drums * .9 + bass * .8 * duck + pad * .55 * duck + arp * .5 * duck + fx
out = hp(out, 30)
fade = np.minimum(1, (dur - T(N)) / .6); out *= np.minimum(1, T(N) / .01) * fade
out = np.tanh(out / np.max(np.abs(out)) * 1.6) * .93
st = np.stack([out, out], 1)
st[:, 1] = np.roll(out, 9) * .97 + out * .03  # лёгкая ширина
with wave.open(sys.argv[2], 'wb') as w:
    w.setnchannels(2); w.setsampwidth(2); w.setframerate(SR); w.writeframes((st * 32767).astype('<i2').tobytes())
