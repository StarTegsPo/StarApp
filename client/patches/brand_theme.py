#!/usr/bin/env python3
"""
Делает фирменные темы StarTegs из встроенных тем Telegram.

Берёт day.attheme (светлая) и night.attheme (тёмная) из исходников и перекрашивает
синие оттенки (акценты, кнопки, исходящие сообщения, ссылки) в фирменный фиолетово-синий.
Яркость и прозрачность каждого цвета сохраняются, поэтому контраст текста остаётся
таким же, как в оригинальной теме, а цвета, которых нет в файле, Telegram берёт из
своих проверенных значений по умолчанию.

    python3 brand_theme.py <assets Telegram> <куда положить startegs_*.attheme>
"""
import colorsys
import sys
from pathlib import Path

BRAND_HUE = 252 / 360   # #7B5CFF
BLUE_FROM, BLUE_TO = 185 / 360, 235 / 360   # что считается «синим акцентом» Telegram
MIN_SAT = 0.18


def parse(value):
    value = value.strip()
    if value.startswith("#"):
        return int(value[1:], 16) & 0xFFFFFFFF
    return int(value) & 0xFFFFFFFF


def to_signed(argb):
    return argb - (1 << 32) if argb & 0x80000000 else argb


def recolor(argb):
    a = (argb >> 24) & 0xFF
    r, g, b = ((argb >> 16) & 0xFF) / 255, ((argb >> 8) & 0xFF) / 255, (argb & 0xFF) / 255
    h, s, v = colorsys.rgb_to_hsv(r, g, b)
    if s < MIN_SAT or not (BLUE_FROM <= h <= BLUE_TO):
        return argb
    # фиолетовый при той же яркости воспринимается темнее и «кислотнее» синего,
    # поэтому немного снижаем насыщенность и поднимаем яркость
    s2 = s * 0.72
    v2 = min(1.0, v * 1.06)
    r2, g2, b2 = colorsys.hsv_to_rgb(BRAND_HUE, s2, v2)
    return (a << 24) | (round(r2 * 255) << 16) | (round(g2 * 255) << 8) | round(b2 * 255)


def convert(src, dst, extra):
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
        new = recolor(old)
        changed += new != old
        out.append(f"{key}={to_signed(new)}")
    keys = {l.split("=", 1)[0] for l in out if "=" in l}
    for k, v in extra.items():
        line = f"{k}={to_signed(v)}"
        if k in keys:
            out = [line if l.startswith(k + "=") else l for l in out]
        else:
            out.append(line)
    dst.write_text("\n".join(out) + "\n", encoding="utf-8")
    return changed


def main(assets, target):
    assets, target = Path(assets), Path(target)
    target.mkdir(parents=True, exist_ok=True)
    # Немного фирменных акцентов поверх перекраски
    day_extra = {"chats_actionBackground": 0xFF7B5CFF, "chats_actionPressedBackground": 0xFF6A4DF0}
    night_extra = {"chats_actionBackground": 0xFF7B5CFF, "chats_actionPressedBackground": 0xFF6A4DF0}
    n1 = convert(assets / "day.attheme", target / "startegs_day.attheme", day_extra)
    n2 = convert(assets / "night.attheme", target / "startegs_night.attheme", night_extra)
    print(f"  ✓ фирменные темы: светлая ({n1} цветов перекрашено), тёмная ({n2})")


if __name__ == "__main__":
    main(*sys.argv[1:3])
