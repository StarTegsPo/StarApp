#!/usr/bin/env python3
"""
Рисует иконки StarTegs для встроенного в Telegram выбора иконки приложения
(Настройки → Темы → Иконка приложения). Запускается вручную при изменении дизайна,
результат лежит в client/res-main и client/res и копируется в исходники при сборке.

    python3 client/patches/make_icons.py
"""
import math
from pathlib import Path

from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parent.parent
MAIN = ROOT / "res-main"      # → TMessagesProj/src/main/res
SA = ROOT / "res"             # → TMessagesProj_AppStandalone/src/main/res

# имя ресурса фона: (цвет1, цвет2). Номера — как в LauncherIconController Telegram.
ICONS = {
    "icon_background_sa":   ("#7B5CFF", "#33A6FF"),  # По умолчанию → Фиолет
    "icon_6_background_sa": ("#E0A526", "#B8741A"),  # Vintage → Золото
    "icon_4_background_sa": ("#1FAE7A", "#0F7F5C"),  # Aqua → Изумруд
    "icon_3_background_sa": ("#FF4FB0", "#B23CFF"),  # Premium → Неон
    "icon_5_background_sa": ("#FF7A45", "#E2405A"),  # Turbo → Закат
    "icon_2_background_sa": ("#1C1F3A", "#3A2F7A"),  # Nox → Ночь (в Telegram это png)
}
# легаси-иконки (Android < 8) для каждого варианта: номер → фон
LEGACY = {"icon_6_launcher_sa": "icon_6_background_sa", "icon_4_launcher_sa": "icon_4_background_sa",
          "icon_3_launcher_sa": "icon_3_background_sa", "icon_5_launcher_sa": "icon_5_background_sa",
          "icon_2_launcher_sa": "icon_2_background_sa"}
FOREGROUNDS = ["icon_foreground_sa", "icon_6_foreground_sa", "icon_3_foreground_sa", "icon_5_foreground_sa"]
DENS = {"mdpi": 1, "hdpi": 1.5, "xhdpi": 2, "xxhdpi": 3, "xxxhdpi": 4}


def rgb(h):
    h = h.lstrip("#")
    return tuple(int(h[i:i + 2], 16) for i in (0, 2, 4))


def star(cx, cy, R, r, n=5):
    return [(cx + (R if i % 2 == 0 else r) * math.cos(math.radians(-90 + i * 180 / n)),
             cy + (R if i % 2 == 0 else r) * math.sin(math.radians(-90 + i * 180 / n))) for i in range(n * 2)]


def gradient(size, c1, c2):
    a, b = rgb(c1), rgb(c2)
    img = Image.new("RGBA", (size, size))
    px = img.load()
    for y in range(size):
        for x in range(size):
            t = (x + (size - y)) / (2 * size)  # 315° — как в xml ниже
            px[x, y] = tuple(round(a[i] + (b[i] - a[i]) * t) for i in range(3)) + (255,)
    return img


def draw_star(img, scale_center=0.5, R=0.24, r=0.10, shadow=True):
    S = img.size[0]
    d = ImageDraw.Draw(img)
    pts = star(S * scale_center, S * (scale_center + 0.02), S * R, S * r)
    if shadow:
        d.polygon([(x, y + S * 0.012) for x, y in pts], fill=(0, 0, 0, 50))
    d.polygon(pts, fill=(255, 255, 255, 255))


def main():
    # 1) фоны: xml-градиенты (кроме Nox, у которого фон — png)
    (MAIN / "drawable").mkdir(parents=True, exist_ok=True)
    for name, (c1, c2) in ICONS.items():
        if name == "icon_2_background_sa":
            continue
        (MAIN / "drawable" / f"{name}.xml").write_text(
            '<?xml version="1.0" encoding="utf-8"?>\n'
            '<shape xmlns:android="http://schemas.android.com/apk/res/android">\n'
            f'    <gradient android:type="linear" android:angle="315" android:startColor="{c1}" android:endColor="{c2}" />\n'
            '</shape>\n', encoding="utf-8")
    for dens, k in DENS.items():
        size = round(108 * k)
        out = MAIN / f"mipmap-{dens}"
        out.mkdir(parents=True, exist_ok=True)
        # фон Nox (png 108dp)
        gradient(size * 2, *ICONS["icon_2_background_sa"]).resize((size, size), Image.LANCZOS).save(out / "icon_2_background_sa.png")
        # 2) передний план: белая звезда в безопасной зоне адаптивной иконки
        fg = Image.new("RGBA", (size * 4, size * 4), (0, 0, 0, 0))
        draw_star(fg)
        fg = fg.resize((size, size), Image.LANCZOS)
        for name in FOREGROUNDS:
            fg.save(out / f"{name}.png")
        # 3) легаси-иконки для Android < 8: круг с градиентом и звездой
        legacy = round(48 * k)
        sa = SA / f"mipmap-{dens}"
        sa.mkdir(parents=True, exist_ok=True)
        for lname, bg in LEGACY.items():
            S = legacy * 4
            img = Image.new("RGBA", (S, S), (0, 0, 0, 0))
            mask = Image.new("L", (S, S), 0)
            ImageDraw.Draw(mask).ellipse((S * 0.04, S * 0.04, S * 0.96, S * 0.96), fill=255)
            img.paste(gradient(S, *ICONS[bg]), (0, 0), mask)
            draw_star(img, R=0.32, r=0.135)
            img.resize((legacy, legacy), Image.LANCZOS).save(sa / f"{lname}.png")
    print("ok")


if __name__ == "__main__":
    main()
