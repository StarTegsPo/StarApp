"""Озвучка (Piper TTS), синтез звуковых эффектов и фоновой музыки, сведение."""
import os, subprocess, hashlib, wave
import numpy as np
from scipy import signal

SR = 48000
VOICE_MODEL = os.environ.get('VOICE_MODEL', 'voices/ru-irinia-medium.onnx')
CACHE = os.environ.get('TTS_CACHE', 'cache')


def read_wav(path):
    with wave.open(path) as w:
        n, ch, sr = w.getnframes(), w.getnchannels(), w.getframerate()
        a = np.frombuffer(w.readframes(n), np.int16).astype(np.float32) / 32768
    if ch > 1:
        a = a.reshape(-1, ch).mean(1)
    assert sr == SR, sr
    return a


def write_wav(path, a):
    a = np.clip(a, -1, 1)
    if a.ndim == 1:
        a = np.stack([a, a], 1)
    with wave.open(path, 'wb') as w:
        w.setnchannels(2); w.setsampwidth(2); w.setframerate(SR)
        w.writeframes((a * 32767).astype(np.int16).tobytes())


def tts(text):
    """Нейросетевой голос + обработка: 48 кГц, срез низа, присутствие, компрессия, обрезка тишины."""
    os.makedirs(CACHE, exist_ok=True)
    key = hashlib.md5(('v3' + text).encode()).hexdigest()[:12]
    raw, out = f'{CACHE}/{key}_raw.wav', f'{CACHE}/{key}.wav'
    if not os.path.exists(out):
        subprocess.run(['piper', '-m', VOICE_MODEL, '-f', raw, '--length-scale', '0.9',
                        '--noise-scale', '0.62', '--noise-w-scale', '0.75', '--sentence-silence', '0.12'],
                       input=text.encode(), check=True, capture_output=True)
        af = ('aresample=48000:resampler=soxr,highpass=f=85,lowpass=f=11000,'
              'equalizer=f=180:t=q:w=1:g=2,equalizer=f=3200:t=q:w=1.2:g=3.5,equalizer=f=7500:t=q:w=1:g=2,'
              'acompressor=threshold=-20dB:ratio=3:attack=5:release=80:makeup=4,'
              'silenceremove=start_periods=1:start_threshold=-45dB,areverse,'
              'silenceremove=start_periods=1:start_threshold=-45dB,areverse,'
              'alimiter=limit=0.9')
        subprocess.run(['ffmpeg', '-y', '-loglevel', 'error', '-i', raw, '-af', af, '-ac', '1', out], check=True)
    return read_wav(out)


# ---------------------------------------------------------------- SFX
rng = np.random.default_rng(7)


def t_(d):
    return np.arange(int(d * SR)) / SR


def env(n, a, d, curve=4.0):
    t = np.arange(n) / SR
    e = np.minimum(1, t / max(a, 1e-4)) * np.exp(-np.maximum(0, t - a) * curve / max(d, 1e-4))
    return e


def bp(x, lo, hi, order=2):
    sos = signal.butter(order, [lo, hi], 'bandpass', fs=SR, output='sos')
    return signal.sosfilt(sos, x)


def lp(x, f, order=2):
    return signal.sosfilt(signal.butter(order, f, 'lowpass', fs=SR, output='sos'), x)


def hp(x, f, order=2):
    return signal.sosfilt(signal.butter(order, f, 'highpass', fs=SR, output='sos'), x)


def sweep_filter(x, f0, f1, q=2.0, blocks=64):
    """Полосовой фильтр с плавающей частотой (по блокам с перекрытием)."""
    n = len(x); out = np.zeros(n); bl = n // blocks + 1
    win = np.hanning(bl * 2)
    for i in range(blocks):
        s = max(0, i * bl - bl // 2); e = min(n, s + bl * 2)
        f = f0 * (f1 / f0) ** (i / (blocks - 1))
        seg = bp(x[max(0, s - 2000):e], f / (1 + 1 / q), min(f * (1 + 1 / q), SR / 2 - 100))[min(s, 2000):]
        out[s:e] += seg[:e - s] * win[:e - s]
    return out / 1.0


def norm(x, p=1.0):
    m = np.max(np.abs(x)) + 1e-9
    return x / m * p


def sfx_whoosh(d=0.45, f0=300, f1=4000):
    n = int(d * SR); x = rng.standard_normal(n)
    y = sweep_filter(x, f0, f1, q=1.5)
    t = np.linspace(0, 1, n)
    e = np.sin(np.pi * t ** 0.7) ** 2
    return norm(y * e, 0.8)


def sfx_pop(f=520, d=0.12):
    t = t_(d)
    fr = f * (1 + 1.2 * np.exp(-t * 60))
    y = np.sin(2 * np.pi * np.cumsum(fr) / SR) * np.exp(-t * 32)
    click = hp(rng.standard_normal(len(t)), 3000) * np.exp(-t * 400) * 0.3
    return norm(y + click, 0.8)


def sfx_send():
    t = t_(0.22)
    fr = 400 + 1400 * (t / 0.22) ** 1.5
    y = np.sin(2 * np.pi * np.cumsum(fr) / SR) * np.sin(np.pi * t / 0.22) ** 2
    return norm(y + 0.4 * sfx_whoosh(0.22, 1200, 6000), 0.6)


def sfx_click():
    t = t_(0.05)
    y = hp(rng.standard_normal(len(t)), 2000) * np.exp(-t * 180)
    y += np.sin(2 * np.pi * 1800 * t) * np.exp(-t * 120) * 0.5
    return norm(y, 0.7)


def sfx_tick():
    t = t_(0.025)
    y = bp(rng.standard_normal(len(t)), 2500, 7000) * np.exp(-t * 300)
    return norm(y, 0.35)


def sfx_ding(f=1318.5, d=0.9):
    t = t_(d)
    y = (np.sin(2 * np.pi * f * t) + 0.5 * np.sin(2 * np.pi * f * 2.01 * t) * np.exp(-t * 6)
         + 0.25 * np.sin(2 * np.pi * f * 3.0 * t) * np.exp(-t * 10)) * np.exp(-t * 5)
    y *= np.minimum(1, t / 0.003)
    return norm(y, 0.55)


def sfx_notify():
    a = sfx_ding(1046.5, 0.5); b = sfx_ding(1568, 0.9)
    y = np.zeros(int(1.0 * SR)); y[:len(a)] += a; s = int(0.09 * SR); y[s:s + len(b)] += b[:len(y) - s]
    return norm(y, 0.6)


def sfx_alert():
    t = t_(0.5)
    y = np.zeros(len(t))
    for i, f in enumerate([880, 660]):
        s = int(i * 0.14 * SR); tt = t[:len(t) - s]
        y[s:] += signal.square(2 * np.pi * f * tt, 0.5) * np.exp(-tt * 14)
    return norm(lp(y, 3500), 0.5)


def sfx_glitch(d=0.35):
    n = int(d * SR); y = np.zeros(n); pos = 0
    while pos < n:
        L = int(rng.uniform(0.01, 0.04) * SR)
        kind = rng.integers(0, 3); tt = np.arange(L) / SR
        if kind == 0:
            seg = signal.square(2 * np.pi * rng.uniform(80, 900) * tt)
        elif kind == 1:
            seg = rng.standard_normal(L)
            seg = np.round(seg * 3) / 3
        else:
            seg = np.sin(2 * np.pi * rng.uniform(1500, 4000) * tt)
        seg *= rng.uniform(0.3, 1)
        y[pos:pos + L] = seg[:n - pos]; pos += L + int(rng.uniform(0, 0.015) * SR)
    y = np.round(y * 6) / 6
    return norm(lp(y, 6000) * np.linspace(1, 0.3, n), 0.55)


def sfx_impact(d=1.6):
    t = t_(d)
    fr = 40 + 120 * np.exp(-t * 18)
    sub = np.sin(2 * np.pi * np.cumsum(fr) / SR) * np.exp(-t * 3.2)
    noise = lp(rng.standard_normal(len(t)), 2500) * np.exp(-t * 14) * 0.6
    tail = bp(rng.standard_normal(len(t)), 200, 3000) * np.exp(-t * 3) * 0.12
    y = np.tanh((sub * 1.4 + noise + tail) * 1.5)
    return norm(y, 0.95)


def sfx_riser(d=1.4):
    t = t_(d); n = len(t)
    fr = 200 * (12 ** (t / d))
    saw = signal.sawtooth(2 * np.pi * np.cumsum(fr) / SR) + signal.sawtooth(2 * np.pi * np.cumsum(fr * 1.01) / SR)
    y = lp(saw, 5000) * 0.25 + sweep_filter(rng.standard_normal(n), 400, 9000, q=1.2)
    y *= (t / d) ** 2
    return norm(y, 0.7)


def sfx_sparkle(d=1.0):
    y = np.zeros(int(d * SR))
    for i in range(10):
        f = rng.choice([1760, 2093, 2349, 2637, 3136, 3520])
        s = int(rng.uniform(0, d * 0.6) * SR); b = sfx_ding(f, 0.4) * rng.uniform(0.3, 0.7)
        e = min(len(y), s + len(b)); y[s:e] += b[:e - s]
    return norm(y, 0.45)


def sfx_unlock():
    a = sfx_click(); b = sfx_ding(1975, 0.7)
    y = np.zeros(int(0.8 * SR)); y[:len(a)] += a; s = int(0.06 * SR); y[s:s + len(b)] += b[:len(y) - s] * 0.8
    return y


SFX = dict(whoosh=sfx_whoosh, pop=lambda: sfx_pop(560), pop_in=lambda: sfx_pop(430), send=sfx_send, click=sfx_click,
           tick=sfx_tick, ding=sfx_ding, notify=sfx_notify, alert=sfx_alert, glitch=sfx_glitch, impact=sfx_impact,
           riser=sfx_riser, sparkle=sfx_sparkle, unlock=sfx_unlock, whoosh_small=lambda: sfx_whoosh(0.3, 800, 6000))


def reverb_ir(d=1.2, decay=4.0):
    t = t_(d)
    ir = rng.standard_normal((len(t), 2)) * np.exp(-t * decay)[:, None]
    ir[:, 0] = lp(ir[:, 0], 6000); ir[:, 1] = lp(ir[:, 1], 6000)
    return ir / np.sqrt((ir ** 2).sum(0))


# ---------------------------------------------------------------- музыка
def midi(n):
    return 440 * 2 ** ((n - 69) / 12)


def music(total, bpm=120, drop_at=None, seed=3):
    """Синтвейв/тех-бит: бочка, клэп, хэты, бас, пэд, арпеджио. drop_at — момент брейкдауна перед финалом."""
    r = np.random.default_rng(seed)
    n = int(total * SR) + SR; beat = 60 / bpm; L = np.zeros((n, 2))
    chords = [(57, [57, 60, 64]), (53, [53, 57, 60]), (48, [55, 60, 64]), (55, [55, 59, 62])]  # Am F C G
    bar = beat * 4
    kick_env = np.zeros(n)

    def put(buf, s, x, pan=0.0):
        i = int(s * SR)
        if i >= n: return
        e = min(n, i + len(x)); x = x[:e - i]
        buf[i:e, 0] += x * (1 - max(0, pan)); buf[i:e, 1] += x * (1 + min(0, pan))

    tk = t_(0.45)
    kick = np.sin(2 * np.pi * np.cumsum(48 + 110 * np.exp(-tk * 30)) / SR) * np.exp(-tk * 7)
    kick = np.tanh(kick * 2) * 0.9
    tc = t_(0.25)
    clap = bp(r.standard_normal(len(tc)), 900, 5000) * (np.exp(-tc * 25) + 0.4 * np.exp(-np.maximum(0, tc - 0.012) * 40) * (tc > 0.012))
    clap = norm(clap, 0.5)
    th = t_(0.06); hat = norm(hp(r.standard_normal(len(th)), 7000) * np.exp(-th * 70), 0.22)
    to = t_(0.25); ohat = norm(hp(r.standard_normal(len(to)), 6000) * np.exp(-to * 14), 0.16)

    nb = int(total / beat) + 2
    for b in range(nb):
        s = b * beat
        breakdown = drop_at is not None and drop_at - bar <= s < drop_at
        intro = s < bar  # первый такт без бочки — мягкий вход
        if not breakdown and not intro:
            put(L, s, kick); i0 = int(s * SR)
            if i0 < n: kick_env[i0:i0 + int(0.3 * SR)] = np.linspace(1, 0, min(int(0.3 * SR), n - i0))
            if b % 2 == 1: put(L, s, clap, 0.0)
        if not breakdown:
            put(L, s + beat / 2, ohat, 0.3)
            for k in range(4):
                put(L, s + k * beat / 4, hat * (0.6 if k % 2 else 0.35), -0.3)

    # бас и пэд
    pad = np.zeros(n); bass = np.zeros(n); arp = np.zeros(n)
    nbar = int(total / bar) + 2
    for i in range(nbar):
        root, notes = chords[i % 4]; s = i * bar
        tb = t_(bar + 0.3)
        p = sum(signal.sawtooth(2 * np.pi * midi(m) * (1 + d) * tb) for m in notes for d in (-0.004, 0, 0.004))
        p *= np.minimum(1, tb / 0.4) * np.minimum(1, np.maximum(0, (bar + 0.3 - tb)) / 0.3)
        i0 = int(s * SR)
        if i0 >= n: break
        e = min(n, i0 + len(p)); pad[i0:e] += p[:e - i0] * 0.05
        for k in range(8):
            t8 = t_(beat / 2)
            f = midi(root - 24) * (2 if k in (3, 7) else 1)
            x = (signal.sawtooth(2 * np.pi * f * t8) * 0.6 + np.sin(2 * np.pi * f * t8)) * np.exp(-t8 * 3) * np.minimum(1, t8 / 0.005)
            j = int((s + k * beat / 2) * SR)
            if j >= n: continue
            e = min(n, j + len(x)); bass[j:e] += x[:e - j] * 0.32
        seq = notes + [notes[0] + 12, notes[1] + 12, notes[2] + 12, notes[1] + 12, notes[0] + 12]
        for k in range(16):
            t16 = t_(beat / 4 * 1.5)
            f = midi(seq[k % len(seq)] + 12)
            x = (signal.square(2 * np.pi * f * t16, 0.3) * 0.5 + signal.sawtooth(2 * np.pi * f * 1.003 * t16) * 0.5) * np.exp(-t16 * 16)
            j = int((s + k * beat / 4) * SR)
            if j >= n: continue
            e = min(n, j + len(x)); arp[j:e] += x[:e - j] * 0.09
    pad = lp(pad, 1400, 4); bass = lp(bass, 500, 4); arp = lp(arp, 3800, 2)
    # эхо на арпеджио
    dl = int(beat * 0.75 * SR); arp2 = arp.copy(); arp2[dl:] += arp[:-dl] * 0.35; arp2[2 * dl:] += arp[:-2 * dl] * 0.15
    pump = 1 - 0.6 * lp(kick_env, 30, 1).clip(0, 1)
    L[:, 0] += (pad * pump * 1.0 + bass * pump + arp2 * 0.9)
    L[:, 1] += (pad * pump * 1.0 + bass * pump + np.roll(arp2, int(0.012 * SR)) * 0.9)
    if drop_at is not None:  # фильтр-свип в брейкдауне
        a, b2 = int((drop_at - bar) * SR), int(drop_at * SR)
        if 0 <= a < b2 <= n:
            seg = L[a:b2].copy()
            for c in range(2):
                seg[:, c] = sweep_lp(seg[:, c], 400, 8000)
            L[a:b2] = seg
    return L[:int(total * SR)]


def sweep_lp(x, f0, f1, blocks=48):
    n = len(x); out = np.zeros(n); bl = n // blocks + 1; win = np.hanning(bl * 2)
    for i in range(blocks):
        s = max(0, i * bl - bl // 2); e = min(n, s + bl * 2)
        f = f0 * (f1 / f0) ** (i / (blocks - 1))
        pre = min(s, 2000)
        seg = lp(x[s - pre:e], f)[pre:]
        out[s:e] += seg[:e - s] * win[:e - s]
    return out


def mix(total, voices, events, drop_at, out_path):
    """voices: [(t, wav)], events: [(t, name, gain)]"""
    n = int(total * SR)
    vb = np.zeros(n)
    for t, w in voices:
        i = int(t * SR); e = min(n, i + len(w)); vb[i:e] += w[:e - i]
    vb = norm(vb, 0.9)
    # лёгкая комната на голосе
    ir = reverb_ir(0.6, 9)
    vrev = signal.fftconvolve(vb, ir[:, 0])[:n] * 0.06
    voice = np.stack([vb + vrev, vb + signal.fftconvolve(vb, ir[:, 1])[:n] * 0.06], 1)

    fx = np.zeros((n, 2)); cache = {}
    for t, name, g in events:
        if name not in cache:
            cache[name] = SFX[name]()
        x = cache[name] if name not in ('glitch', 'sparkle', 'whoosh') else SFX[name]()
        i = int(max(0, t) * SR)
        if i >= n: continue
        e = min(n, i + len(x)); pan = rng.uniform(-0.25, 0.25)
        fx[i:e, 0] += x[:e - i] * g * (1 - max(0, pan)); fx[i:e, 1] += x[:e - i] * g * (1 + min(0, pan))
    ir2 = reverb_ir(1.4, 3.5)
    fx = fx + np.stack([signal.fftconvolve(fx[:, 0], ir2[:, 0])[:n], signal.fftconvolve(fx[:, 1], ir2[:, 1])[:n]], 1) * 0.18

    mus = music(total, drop_at=drop_at)
    # дакинг музыки под голос
    envv = np.abs(vb)
    envv = lp(envv, 6, 1)
    envv = np.clip(envv / (np.percentile(envv[envv > 1e-3], 60) + 1e-9), 0, 1) if np.any(envv > 1e-3) else envv
    duck = 1 - 0.55 * envv
    mus *= (0.30 * duck)[:, None]
    # плавный выход
    fade = np.ones(n); fl = int(1.2 * SR); fade[-fl:] = np.linspace(1, 0, fl)
    out = (voice * 1.0 + fx * 0.42 + mus) * fade[:, None]
    out = np.tanh(out * 0.95) * 0.98
    write_wav(out_path, out)
