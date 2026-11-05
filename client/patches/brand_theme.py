#!/usr/bin/env python3
"""
Делает фирменные темы StarTegs из встроенных тем Telegram.

Берёт day.attheme (светлая) и night.attheme (тёмная) из исходников и перекрашивает
синие оттенки (акценты, кнопки, исходящие сообщения, ссылки) в цвет палитры.
Прозрачность сохраняется, а яркость поправляется под оттенок: жёлтый и зелёный
при той же яркости кажутся светлее синего, поэтому их немного затемняем, чтобы
белый текст на кнопках оставался читаемым. Цвета, которых нет в файле, Telegram
берёт из своих значений по умолчанию.

    python3 brand_theme.py <assets Telegram> <куда положить startegs_*.attheme>
"""
import colorsys
import sys
from pathlib import Path

# id: (оттенок в градусах, множитель насыщенности, множитель яркости)
PALETTES = {
    "violet":  (252, 0.72, 1.06),   # фирменная, #7B5CFF
    "neon":    (318, 0.95, 1.04),   # розово-малиновый неон
    "gold":    (40,  0.88, 0.80),   # золото
    "emerald": (155, 0.82, 0.78),   # изумруд
    "sunset":  (14,  0.86, 0.92),   # закат, коралловый
}

BLUE_FROM, BLUE_TO = 185 / 360, 235 / 360   # что считается «синим акцентом» Telegram
MIN_SAT = 0.18


def parse(value):
    value = value.strip()
    if value.startswith("#"):
        return int(value[1:], 16) & 0xFFFFFFFF
    return int(value) & 0xFFFFFFFF


def to_signed(argb):
    return argb - (1 << 32) if argb & 0x80000000 else argb


def recolor(argb, palette):
    hue, ks, kv = palette
    a = (argb >> 24) & 0xFF
    r, g, b = ((argb >> 16) & 0xFF) / 255, ((argb >> 8) & 0xFF) / 255, (argb & 0xFF) / 255
    h, s, v = colorsys.rgb_to_hsv(r, g, b)
    if s < MIN_SAT or not (BLUE_FROM <= h <= BLUE_TO):
        return argb
    r2, g2, b2 = colorsys.hsv_to_rgb(hue / 360, min(1.0, s * ks), min(1.0, v * kv))
    return (a << 24) | (round(r2 * 255) << 16) | (round(g2 * 255) << 8) | round(b2 * 255)


def convert(src, dst, palette):
    out, changed = [], 0
    for line in src.read_text(encoding="utf-8").splitlines():
        if "=" not in line or line.startswith("#"):
            out.append(line)
            continue
        key, value = line.split("=", 1)
        try:
            old = parse(value)
        except ValueError:
            out.append(line)
            continue
        new = recolor(old, palette)
        changed += new != old
        out.append(f"{key}={to_signed(new)}")
    dst.write_text("\n".join(out) + "\n", encoding="utf-8")
    return changed


def main(assets, target):
    assets, target = Path(assets), Path(target)
    target.mkdir(parents=True, exist_ok=True)
    for pid, palette in PALETTES.items():
        n1 = convert(assets / "day.attheme", target / f"startegs_{pid}_day.attheme", palette)
        n2 = convert(assets / "night.attheme", target / f"startegs_{pid}_night.attheme", palette)
        print(f"  ✓ тема {pid}: светлая ({n1} цветов), тёмная ({n2})")


if __name__ == "__main__":
    main(*sys.argv[1:3])
