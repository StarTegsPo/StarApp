"""Аватарка профиля TikTok 1080x1080 (TikTok обрезает её кругом)."""
import os
import sys

sys.path.insert(0, os.path.dirname(__file__))
from engine import *  # noqa

S = 1080
x = np.linspace(0, 1, S)[None, :, None]
y = np.linspace(0, 1, S)[:, None, None]
k = (x + y) / 2
c1, c2, c3 = (np.array(c, dtype=float) for c in (A1, A2, A3))
k2 = np.clip(k * 2, 0, 1)
k3 = np.clip(k * 2 - 1, 0, 1)
arr = np.where(k < 0.5, c3 * (1 - k2) + c1 * k2, c1 * (1 - k3) + c2 * k3).astype(np.uint8)
img = Image.fromarray(arr, 'RGB').convert('RGBA')

glow = Image.new('RGBA', (S, S), (0, 0, 0, 0))
ImageDraw.Draw(glow).ellipse((240, 200, 840, 800), fill=(255, 255, 255, 90))
img.alpha_composite(glow.filter(ImageFilter.GaussianBlur(90)))


def star(cx, cy, r_out, r_in, rot=-math.pi / 2):
    pts = []
    for i in range(10):
        r = r_out if i % 2 == 0 else r_in
        a = rot + i * math.pi / 5
        pts.append((cx + r * math.cos(a), cy + r * math.sin(a)))
    return pts


sh = Image.new('RGBA', (S, S), (0, 0, 0, 0))
ImageDraw.Draw(sh).polygon(star(540, 540 + 22, 360, 150), fill=(20, 10, 60, 140))
img.alpha_composite(sh.filter(ImageFilter.GaussianBlur(24)))
ImageDraw.Draw(img).polygon(star(540, 520, 360, 150), fill=WHITE)
# маленькие искры
for cx, cy, r in [(830, 230, 46), (220, 820, 34), (880, 800, 24)]:
    ImageDraw.Draw(img).polygon(star(cx, cy, r, r * 0.4), fill=GOLD)

out = os.path.join(os.path.dirname(__file__), '..', 'avatar.png')
img.convert('RGB').save(out)
print('ok', out)
