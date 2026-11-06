#!/usr/bin/env python3
"""Генерирует звуки уведомлений StarTegs (без чужих файлов — синтез на месте) в client/res-main/raw/.

    python3 client/patches/make_sounds.py
"""
import math
import struct
import wave
from pathlib import Path

RATE = 22050
OUT = Path(__file__).resolve().parent.parent / "res-main" / "raw"


def env(t, attack, decay):
    return min(1.0, t / attack) * math.exp(-t / decay) if attack > 0 else math.exp(-t / decay)


def tone(freq, dur, wave_fn="sine", attack=0.005, decay=0.25, harm=(1.0,), sweep=None):
    out = []
    n = int(dur * RATE)
    phase = 0.0
    for i in range(n):
        t = i / RATE
        f = freq if sweep is None else freq + (sweep - freq) * (t / dur)
        phase += 2 * math.pi * f / RATE
        v = 0.0
        for k, a in enumerate(harm, 1):
            if wave_fn == "square":
                v += a * (1.0 if math.sin(phase * k) >= 0 else -1.0) * 0.35
            else:
                v += a * math.sin(phase * k)
        out.append(v * env(t, attack, decay))
    return out


def silence(dur):
    return [0.0] * int(dur * RATE)


def mix(*parts):
    n = max(len(p) for p in parts)
    return [sum(p[i] if i < len(p) else 0.0 for p in parts) for i in range(n)]


def seq(*parts):
    out = []
    for p in parts:
        out += p
    return out


def save(name, samples, gain=0.6):
    peak = max(1e-6, max(abs(s) for s in samples))
    OUT.mkdir(parents=True, exist_ok=True)
    with wave.open(str(OUT / f"{name}.wav"), "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(RATE)
        w.writeframes(b"".join(struct.pack("<h", int(max(-1, min(1, s / peak * gain)) * 32767)) for s in samples))


def main():
    bell = (1.0, 0.45, 0.25, 0.12)
    save("startegs_snd_crystal", seq(tone(1318.5, 0.32, harm=bell, decay=0.18), tone(1975.5, 0.6, harm=bell, decay=0.3)))
    save("startegs_snd_bubble", seq(tone(320, 0.12, sweep=980, decay=0.08), silence(0.04), tone(420, 0.10, sweep=1200, decay=0.06)))
    save("startegs_snd_drop", tone(1250, 0.35, sweep=520, decay=0.12, harm=(1.0, 0.2)))
    save("startegs_snd_coin", seq(tone(987.8, 0.09, "square", decay=0.5), tone(1318.5, 0.42, "square", decay=0.18)))
    save("startegs_snd_laser", tone(1600, 0.45, "square", sweep=180, decay=0.3))
    save("startegs_snd_heart", seq(tone(62, 0.16, decay=0.07, harm=(1.0, 0.5)), silence(0.07), tone(58, 0.22, decay=0.09, harm=(1.0, 0.5)), silence(0.2)), gain=0.9)
    arp = [523.3, 659.3, 784.0, 1046.5]
    save("startegs_snd_magic", mix(seq(*[tone(f, 0.13, harm=bell, decay=0.2) for f in arp], tone(1568, 0.5, harm=bell, decay=0.35)),
                                   seq(silence(0.05), *[tone(f * 2.01, 0.13, decay=0.15) for f in arp])))
    save("startegs_snd_retro", seq(*[tone(f, 0.075, "square", decay=1.0) for f in (659.3, 784.0, 1046.5, 1318.5, 1568.0)], tone(2093, 0.25, "square", decay=0.15)))
    print("  ✓ звуки:", ", ".join(sorted(p.stem for p in OUT.glob("*.wav"))))


if __name__ == "__main__":
    main()
