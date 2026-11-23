"""Мини-движок вертикальных роликов 1080x1920 для TikTok (PIL + ffmpeg).

Без внешних зависимостей, кроме Pillow, numpy и ffmpeg.
"""
import math
import random
import re
import subprocess
from functools import lru_cache

import numpy as np
from PIL import Image, ImageDraw, ImageFilter, ImageFont

W, H, FPS = 1080, 1920, 30

BOLD = '/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf'
REG = '/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf'
EMOJI = '/usr/share/fonts/truetype/noto/NotoColorEmoji.ttf'

# Фирменная палитра «Фиолет» из webapp/theme.js
A1 = (123, 92, 255)
A2 = (51, 166, 255)
A3 = (255, 92, 168)
GOLD = (255, 211, 77)
BG_TOP = (14, 11, 31)
BG_BOT = (28, 18, 64)
WHITE = (255, 255, 255)

EMOJI_RE = re.compile(
    '([\U0001F000-\U0001FAFF☀-➿⭐⬆↔-⇿⏩-⏺✅❌](?:️)?)'
)


@lru_cache(maxsize=None)
def font(size, bold=True):
    return ImageFont.truetype(BOLD if bold else REG, size)


@lru_cache(maxsize=None)
def emoji_img(ch, size):
    f = ImageFont.truetype(EMOJI, 109)
    im = Image.new('RGBA', (160, 160), (0, 0, 0, 0))
    ImageDraw.Draw(im).text((0, 0), ch, font=f, embedded_color=True)
    bb = im.getbbox() or (0, 0, 1, 1)
    im = im.crop(bb)
    s = size / max(im.size)
    return im.resize((max(1, int(im.width * s)), max(1, int(im.height * s))), Image.LANCZOS)


def segments(text):
    return [p for p in EMOJI_RE.split(text) if p]


def is_emoji(seg):
    return bool(EMOJI_RE.fullmatch(seg))


def text_w(text, size, bold=True):
    w = 0
    for seg in segments(text):
        if is_emoji(seg):
            w += int(size * 1.12)
        else:
            w += font(size, bold).getlength(seg)
    return int(w)


def draw_text(img, xy, text, size, fill=WHITE, bold=True, stroke=0, stroke_fill=(0, 0, 0)):
    """Текст со смешанными эмодзи. xy — левый верх строки."""
    x, y = xy
    d = ImageDraw.Draw(img)
    f = font(size, bold)
    asc = f.getmetrics()[0]
    for seg in segments(text):
        if is_emoji(seg):
            e = emoji_img(seg.replace('️', ''), int(size * 1.0))
            img.alpha_composite(e, (int(x + size * 0.06), int(y + asc - e.height + size * 0.12)))
            x += int(size * 1.12)
        else:
            d.text((x, y), seg, font=f, fill=fill, stroke_width=stroke, stroke_fill=stroke_fill)
            x += f.getlength(seg)


def wrap(text, size, max_w, bold=True):
    lines = []
    for para in text.split('\n'):
        cur = ''
        for word in para.split(' '):
            t = (cur + ' ' + word).strip()
            if text_w(t, size, bold) <= max_w or not cur:
                cur = t
            else:
                lines.append(cur)
                cur = word
        lines.append(cur)
    return lines


# ---------- easing ----------
def clamp(v, a=0.0, b=1.0):
    return max(a, min(b, v))


def ease_out(t):
    t = clamp(t)
    return 1 - (1 - t) ** 3


def ease_back(t):
    t = clamp(t)
    c1, c3 = 1.70158, 2.70158
    return 1 + c3 * (t - 1) ** 3 + c1 * (t - 1) ** 2


def prog(t, start, dur):
    return clamp((t - start) / dur)


# ---------- фон ----------
def _make_bg():
    a = np.linspace(0, 1, H)[:, None, None]
    top = np.array(BG_TOP)[None, None, :]
    bot = np.array(BG_BOT)[None, None, :]
    arr = (top * (1 - a) + bot * a).repeat(W, axis=1).astype(np.uint8)
    bg = Image.fromarray(arr, 'RGB').convert('RGBA')
    glow = Image.new('RGBA', (W, H), (0, 0, 0, 0))
    g = ImageDraw.Draw(glow)
    g.ellipse((-300, -200, 700, 800), fill=A1 + (110,))
    g.ellipse((500, 1200, 1500, 2200), fill=A2 + (80,))
    g.ellipse((600, 300, 1300, 1000), fill=A3 + (45,))
    glow = glow.filter(ImageFilter.GaussianBlur(160))
    bg.alpha_composite(glow)
    return bg


_BG = None
_STARS = None


def background(t):
    global _BG, _STARS
    if _BG is None:
        _BG = _make_bg()
        rnd = random.Random(7)
        _STARS = [(rnd.randrange(W), rnd.randrange(H), rnd.uniform(1.5, 4), rnd.uniform(0, 6.28), rnd.uniform(1, 3))
                  for _ in range(70)]
    img = _BG.copy()
    d = ImageDraw.Draw(img)
    for x, y, r, ph, sp in _STARS:
        a = int(60 + 120 * (0.5 + 0.5 * math.sin(t * sp + ph)))
        yy = (y - t * 18 * sp) % H
        d.ellipse((x - r, yy - r, x + r, yy + r), fill=(255, 255, 255, a))
    return img


def gradient_rect(w, h, c1=A1, c2=A2, radius=0):
    x = np.linspace(0, 1, w)[None, :, None]
    arr = (np.array(c1) * (1 - x) + np.array(c2) * x).repeat(h, axis=0).astype(np.uint8)
    im = Image.fromarray(arr, 'RGB').convert('RGBA')
    if radius:
        m = Image.new('L', (w, h), 0)
        ImageDraw.Draw(m).rounded_rectangle((0, 0, w - 1, h - 1), radius, fill=255)
        im.putalpha(m)
    return im


def shadow(img, box, radius, blur=30, alpha=120, off=14):
    sh = Image.new('RGBA', img.size, (0, 0, 0, 0))
    x0, y0, x1, y1 = box
    ImageDraw.Draw(sh).rounded_rectangle((x0, y0 + off, x1, y1 + off), radius, fill=(0, 0, 0, alpha))
    img.alpha_composite(sh.filter(ImageFilter.GaussianBlur(blur)))


# ---------- крупные подписи-хуки ----------
def caption_block(img, lines, y, t, start=0.0, size=92, hl=GOLD, center=True, stagger=0.12):
    """lines: список строк; слова в [скобках] подсвечиваются плашкой.
    Появляются с «пружиной» по очереди."""
    base = size
    for i, line in enumerate(lines):
        size = base
        while size > 30 and text_w(line.replace('[', '').replace(']', ''), size) > W - 120:
            size -= 4
        lh = int(base * 1.28)
        p = ease_back(prog(t, start + i * stagger, 0.35))
        if p <= 0:
            continue
        layer = Image.new('RGBA', (W, lh + 40), (0, 0, 0, 0))
        dy = (base - size) // 2
        plain = line.replace('[', '').replace(']', '')
        tw = text_w(plain, size)
        x = (W - tw) // 2 if center else 70
        # плашки под выделенными словами
        cx = x
        for part in re.split(r'(\[[^\]]+\])', line):
            if not part:
                continue
            if part.startswith('['):
                word = part[1:-1]
                ww = text_w(word, size)
                ImageDraw.Draw(layer).rounded_rectangle((cx - 14, 6 + dy, cx + ww + 14, int(size * 1.28) + 6 + dy), 18, fill=hl)
                draw_text(layer, (cx, 14 + dy), word, size, fill=(20, 12, 40))
                cx += ww
            else:
                draw_text(layer, (cx, 14 + dy), part, size, stroke=6, stroke_fill=(10, 6, 26))
                cx += text_w(part, size)
        s = 0.6 + 0.4 * p
        nw, nh = int(W * s), int((lh + 40) * s)
        layer = layer.resize((nw, nh), Image.BILINEAR)
        if p < 1:
            a = layer.getchannel('A').point(lambda v: int(v * clamp(p * 1.4)))
            layer.putalpha(a)
        img.alpha_composite(layer, ((W - nw) // 2, int(y + i * lh + (lh - nh) / 2)))


def progress_bar(img, t, total):
    d = ImageDraw.Draw(img)
    d.rounded_rectangle((40, 60, W - 40, 70), 5, fill=(255, 255, 255, 50))
    w = int((W - 80) * clamp(t / total))
    if w > 10:
        img.alpha_composite(gradient_rect(w, 10, A3, GOLD, 5), (40, 60))


def brand_tag(img, alpha=255):
    tag = Image.new('RGBA', (420, 80), (0, 0, 0, 0))
    ImageDraw.Draw(tag).rounded_rectangle((0, 0, 419, 79), 40, fill=(255, 255, 255, 34))
    draw_text(tag, (28, 18), '⭐ StarTegs', 38)
    if alpha < 255:
        tag.putalpha(tag.getchannel('A').point(lambda v: v * alpha // 255))
    img.alpha_composite(tag, ((W - 420) // 2, 110))


# ---------- телефон и чат ----------
PX0, PY0, PW, PH = 150, 430, 780, 1240  # видимая часть «телефона»


def phone(img, title, subtitle='', avatar_color=A2, avatar_letter='А', slide=1.0):
    """Рисует корпус телефона и шапку чата, возвращает слой контента (для обрезки)."""
    dy = int((1 - ease_out(slide)) * 900)
    x0, y0 = PX0, PY0 + dy
    shadow(img, (x0, y0, x0 + PW, y0 + PH), 70, blur=40, alpha=160)
    d = ImageDraw.Draw(img)
    d.rounded_rectangle((x0 - 14, y0 - 14, x0 + PW + 14, y0 + PH + 14), 78, fill=(8, 6, 18))
    d.rounded_rectangle((x0, y0, x0 + PW, y0 + PH), 66, fill=(23, 33, 43))
    # шапка
    d.rounded_rectangle((x0, y0, x0 + PW, y0 + 170), 66, fill=(33, 45, 59))
    d.rectangle((x0, y0 + 100, x0 + PW, y0 + 170), fill=(33, 45, 59))
    d.text((x0 + 40, y0 + 78), '‹', font=font(64), fill=(110, 180, 255))
    d.ellipse((x0 + 100, y0 + 68, x0 + 180, y0 + 148), fill=avatar_color)
    lw = text_w(avatar_letter, 40)
    draw_text(img, (x0 + 140 - lw / 2, y0 + 84), avatar_letter, 40)
    draw_text(img, (x0 + 200, y0 + 70), title, 38)
    if subtitle:
        d.text((x0 + 200, y0 + 118), subtitle, font=font(28, False), fill=(140, 160, 180))
    return x0, y0


def bubble(img, x_right_or_left, y, text, out=False, size=36, max_w=520, alpha=1.0, scale=1.0,
           meta='', meta_color=(140, 170, 200), strike=False, fill=None, text_fill=WHITE, italic_note=None):
    """Пузырь сообщения. Для out=True x — правый край, иначе левый. Возвращает высоту."""
    lines = wrap(text, size, max_w, bold=False)
    lh = int(size * 1.3)
    meta_h = 34 if meta else 0
    note_h = 40 if italic_note else 0
    bw = max(text_w(l, size, False) for l in lines) + 56
    if meta:
        bw = max(bw, text_w(meta, 24, False) + 56)
    if italic_note:
        bw = max(bw, text_w(italic_note, 26, False) + 56)
    bh = len(lines) * lh + 36 + meta_h + note_h
    layer = Image.new('RGBA', (int(bw) + 4, bh + 4), (0, 0, 0, 0))
    d = ImageDraw.Draw(layer)
    col = fill or ((43, 82, 120) if out else (24, 37, 51))
    if out and fill is None:
        layer.alpha_composite(gradient_rect(int(bw), bh, A1, A2, 32))
    else:
        d.rounded_rectangle((0, 0, bw - 1, bh - 1), 32, fill=col)
    ty = 18
    for l in lines:
        draw_text(layer, (28, ty), l, size, fill=text_fill, bold=False)
        if strike:
            d.line((28, ty + lh // 2, 28 + text_w(l, size, False), ty + lh // 2), fill=(255, 120, 140), width=4)
        ty += lh
    if italic_note:
        draw_text(layer, (28, ty + 2), italic_note, 26, fill=(255, 150, 170), bold=True)
        ty += note_h
    if meta:
        mw = text_w(meta, 24, False)
        draw_text(layer, (bw - mw - 24, ty + 2), meta, 24, fill=meta_color, bold=False)
    if scale != 1.0:
        layer = layer.resize((max(1, int(layer.width * scale)), max(1, int(layer.height * scale))), Image.BILINEAR)
    if alpha < 1:
        layer.putalpha(layer.getchannel('A').point(lambda v: int(v * alpha)))
    x = x_right_or_left - layer.width if out else x_right_or_left
    img.alpha_composite(layer, (int(x), int(y)))
    return bh


def toast(img, text, y, p, color=(30, 22, 60)):
    """Всплывающая плашка поверх телефона."""
    if p <= 0:
        return
    size = 40
    tw = text_w(text, size)
    w, h = tw + 80, 100
    layer = Image.new('RGBA', (w, h), (0, 0, 0, 0))
    layer.alpha_composite(gradient_rect(w, h, A3, A1, 50))
    draw_text(layer, (40, 26), text, size)
    s = 0.5 + 0.5 * ease_back(p)
    layer = layer.resize((int(w * s), int(h * s)), Image.BILINEAR)
    img.alpha_composite(layer, ((W - layer.width) // 2, int(y - layer.height / 2)))


def tap(img, x, y, p):
    """Анимация касания пальцем."""
    if p <= 0 or p >= 1:
        return
    r = 30 + 50 * p
    a = int(200 * (1 - p))
    ov = Image.new('RGBA', img.size, (0, 0, 0, 0))
    ImageDraw.Draw(ov).ellipse((x - r, y - r, x + r, y + r), fill=(255, 255, 255, a // 2), outline=(255, 255, 255, a), width=5)
    img.alpha_composite(ov)


# ---------- финальный экран ----------
_LOGO = None


def logo(size):
    global _LOGO
    if _LOGO is None:
        _LOGO = Image.open('/home/user/StarApp/client/res/mipmap-xxxhdpi/ic_launcher_sa.png').convert('RGBA')
    return _LOGO.resize((size, size), Image.LANCZOS)


def cta_scene(img, t, dur, line1='Это StarTegs ⭐', line2='Ссылка в профиле 👆', small='Android-мод Telegram · iPhone — Mini App'):
    p = ease_back(prog(t, 0, 0.5))
    pulse = 1 + 0.04 * math.sin(t * 6)
    sz = max(1, int(300 * (0.5 + 0.5 * p) * pulse))
    glow = Image.new('RGBA', (W, H), (0, 0, 0, 0))
    ImageDraw.Draw(glow).ellipse((W / 2 - 260, 520, W / 2 + 260, 1040), fill=A1 + (150,))
    img.alpha_composite(glow.filter(ImageFilter.GaussianBlur(90)))
    img.alpha_composite(logo(sz), ((W - sz) // 2, 780 - sz // 2))
    caption_block(img, [line1], 1020, t, 0.25, size=84)
    caption_block(img, [line2], 1160, t, 0.5, size=72, hl=GOLD)
    if t > 0.8:
        a = int(255 * ease_out(prog(t, 0.8, 0.4)))
        f = font(34, False)
        tw = f.getlength(small)
        ImageDraw.Draw(img).text(((W - tw) / 2, 1320), small, font=f, fill=(200, 200, 230, a))


# ---------- рендер ----------
def render(path, scenes, title_for_log=''):
    """scenes: список (длительность, fn(img, t, dur)). Между сценами — короткий «рывок» (zoom-punch)."""
    total = sum(d for d, _ in scenes)
    n = int(total * FPS)
    proc = subprocess.Popen(
        ['ffmpeg', '-y', '-loglevel', 'error', '-f', 'rawvideo', '-pix_fmt', 'rgb24', '-s', f'{W}x{H}', '-r', str(FPS),
         '-i', '-', '-f', 'lavfi', '-i', 'anullsrc=r=44100:cl=stereo', '-shortest',
         '-c:v', 'libx264', '-preset', 'medium', '-crf', '20', '-pix_fmt', 'yuv420p', '-movflags', '+faststart',
         '-c:a', 'aac', '-b:a', '64k', path],
        stdin=subprocess.PIPE)
    starts = []
    acc = 0
    for d, _ in scenes:
        starts.append(acc)
        acc += d
    for i in range(n):
        gt = i / FPS
        k = max(j for j, s in enumerate(starts) if s <= gt + 1e-9)
        d, fn = scenes[k]
        lt = gt - starts[k]
        img = background(gt)
        fn(img, lt, d)
        progress_bar(img, gt, total)
        # zoom-punch на стыке сцен
        if k > 0 and lt < 0.15:
            z = 1 + 0.06 * (1 - lt / 0.15)
            big = img.resize((int(W * z), int(H * z)), Image.BILINEAR)
            ox, oy = (big.width - W) // 2, (big.height - H) // 2
            img = big.crop((ox, oy, ox + W, oy + H))
        proc.stdin.write(img.convert('RGB').tobytes())
    proc.stdin.close()
    proc.wait()
    print('ok', path, f'{total:.1f}s', title_for_log)
