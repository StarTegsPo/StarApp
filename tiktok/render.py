"""Рендер вертикальных роликов 1080x1920 для TikTok: анимированный фон, кинетический текст,
макет телефона с анимациями интерфейса StarTegs, субтитры по словам, музыка и SFX."""
import os, sys, math, functools, subprocess, re
import numpy as np
from PIL import Image, ImageDraw, ImageFont, ImageFilter
from multiprocessing import Pool

from scenes import VIDEOS
import audio

W, H, FPS = 1080, 1920, 30
VOFF = 0.30   # пауза перед голосом в сцене
TAIL = 0.45   # хвост после голоса
FD = '/usr/share/fonts/opentype/inter/'
FW = {'r': 'Inter-Regular', 'm': 'Inter-Medium', 'sb': 'Inter-SemiBold', 'b': 'Inter-Bold',
      'eb': 'InterDisplay-ExtraBold', 'k': 'InterDisplay-Black'}
EMOJI_FONT = '/usr/share/fonts/truetype/noto/NotoColorEmoji.ttf'
SYM_FONT = '/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf'

BG = (9, 6, 20)
P1 = (139, 92, 246)   # фиолет
P2 = (236, 72, 153)   # розовый
P3 = (34, 211, 238)   # циан
YEL = (253, 224, 71)
WHITE = (255, 255, 255)
GRAY = (148, 140, 170)


@functools.lru_cache(None)
def F(w, s):
    return ImageFont.truetype(FD + FW[w] + '.otf', s)


@functools.lru_cache(None)
def SYM(s):
    return ImageFont.truetype(SYM_FONT, s)


# ------------------------------------------------------------------ утилиты
def clamp(x, a=0.0, b=1.0):
    return a if x < a else b if x > b else x


def prog(t, t0, d):
    return clamp((t - t0) / d) if d > 0 else float(t >= t0)


def eo(x):
    return 1 - (1 - x) ** 3


def eio(x):
    return 4 * x ** 3 if x < .5 else 1 - (-2 * x + 2) ** 3 / 2


def eob(x, s=1.9):
    x -= 1
    return 1 + (s + 1) * x ** 3 + s * x ** 2


def lerp(a, b, x):
    return a + (b - a) * x


def lerpc(c1, c2, x):
    return tuple(int(lerp(a, b, x)) for a, b in zip(c1, c2))


def with_alpha(im, a):
    if a >= 0.999:
        return im
    im = im.copy()
    al = np.asarray(im.getchannel('A'), np.float32) * a
    im.putalpha(Image.fromarray(al.astype(np.uint8)))
    return im


def comp(base, im, x, y, alpha=1.0):
    if alpha <= 0.003 or im is None:
        return
    x, y = int(round(x)), int(round(y))
    l, t = max(0, -x), max(0, -y)
    r, b = min(im.width, base.width - x), min(im.height, base.height - y)
    if r <= l or b <= t:
        return
    src = im if alpha >= 0.999 else with_alpha(im, alpha)
    base.alpha_composite(src, (x + l, y + t), (l, t, r, b))


def comp_c(base, im, cx, cy, scale=1.0, alpha=1.0):
    if scale <= 0.01 or alpha <= 0.003:
        return
    if abs(scale - 1) > 0.004:
        im = im.resize((max(1, int(im.width * scale)), max(1, int(im.height * scale))), Image.BILINEAR)
    comp(base, im, cx - im.width / 2, cy - im.height / 2, alpha)


@functools.lru_cache(4096)
def emoji(ch, size):
    f = ImageFont.truetype(EMOJI_FONT, 109)
    im = Image.new('RGBA', (180 * max(1, len(ch)), 160))
    ImageDraw.Draw(im).text((6, 6), ch, font=f, embedded_color=True)
    bb = im.getbbox()
    if not bb:
        return Image.new('RGBA', (size, size))
    im = im.crop(bb)
    s = size / max(im.size)
    return im.resize((max(1, int(im.width * s)), max(1, int(im.height * s))), Image.LANCZOS)


@functools.lru_cache(4096)
def timg(text, w, s, fill=WHITE, stroke=0, sfill=(0, 0, 0), font=None):
    f = font or F(w, s)
    bb = f.getbbox(text, stroke_width=stroke)
    asc, desc = f.getmetrics()
    im = Image.new('RGBA', (max(1, bb[2] - bb[0] + 4), asc + desc + stroke * 2 + 4))
    ImageDraw.Draw(im).text((2 - bb[0], 2 + stroke), text, font=f, fill=fill, stroke_width=stroke, stroke_fill=sfill)
    return im


def grad_img(w, h, cols, angle=0.0):
    x = np.linspace(0, 1, w)[None, :]; y = np.linspace(0, 1, h)[:, None]
    u = np.clip(x * math.cos(angle) + y * math.sin(angle), 0, 1) if angle else np.repeat(x, h, 0)
    u = u / max(u.max(), 1e-6)
    seg = len(cols) - 1
    out = np.zeros((h, w, 3), np.float32)
    for i in range(seg):
        a = np.clip(u * seg - i, 0, 1)
        m = (u * seg >= i) & ((u * seg <= i + 1) if i < seg - 1 else True)
        c = np.array(cols[i], np.float32) * (1 - a[..., None]) + np.array(cols[i + 1], np.float32) * a[..., None]
        out = np.where(m[..., None], c, out)
    return out


@functools.lru_cache(512)
def tgrad(text, w, s, cols=(P3, P1, P2), stroke=0, glow=True):
    base = timg(text, w, s, WHITE, stroke, (255, 255, 255))
    a = np.asarray(base.getchannel('A'), np.float32)
    g = grad_img(base.width, base.height, list(cols))
    im = Image.fromarray(np.dstack([g, a]).astype(np.uint8), 'RGBA')
    if glow:
        pad = 40
        big = Image.new('RGBA', (im.width + pad * 2, im.height + pad * 2))
        big.alpha_composite(im, (pad, pad))
        gl = big.filter(ImageFilter.GaussianBlur(18))
        gl = with_alpha(gl, 0.85)
        gl.alpha_composite(big)
        return gl
    return im


def wrap(text, f, maxw):
    words, lines, cur = text.split(), [], ''
    for w in words:
        t = (cur + ' ' + w).strip()
        if f.getlength(t) <= maxw or not cur:
            cur = t
        else:
            lines.append(cur); cur = w
    if cur:
        lines.append(cur)
    return lines


def rich_line(draw_im, x, y, text, f, fill, esize=None):
    """Текст с эмодзи внутри (эмодзи рисуются цветным шрифтом)."""
    esize = esize or int(f.size * 1.05)
    d = ImageDraw.Draw(draw_im)
    parts = re.split(r'([\U0001F000-\U0001FAFF☀-➿⭐⏰⌨️]+)', text)
    for p in parts:
        if not p:
            continue
        if re.match(r'[\U0001F000-\U0001FAFF☀-➿⭐⏰⌨]', p):
            core = p.replace('️', '')
            for ch in core:
                e = emoji(ch, esize)
                draw_im.alpha_composite(e, (int(x), int(y + (f.size * 1.2 - esize) / 2)))
                x += esize + 4
        else:
            d.text((x, y), p, font=f, fill=fill)
            x += f.getlength(p)
    return x


def rich_len(text, f):
    esize = int(f.size * 1.05)
    parts = re.split(r'([\U0001F000-\U0001FAFF☀-➿⭐⏰⌨️]+)', text)
    L = 0
    for p in parts:
        if re.match(r'[\U0001F000-\U0001FAFF☀-➿⭐⏰⌨]', p or ' '):
            L += (esize + 4) * len(p.replace('️', ''))
        else:
            L += f.getlength(p)
    return L


def star_poly(cx, cy, R, r, rot=0.0, n=5):
    pts = []
    for i in range(n * 2):
        a = rot - math.pi / 2 + i * math.pi / n
        rad = R if i % 2 == 0 else r
        pts.append((cx + rad * math.cos(a), cy + rad * math.sin(a)))
    return pts


# ------------------------------------------------------------------ фон
LW, LH = 135, 240
_gx, _gy = np.meshgrid(np.linspace(0, 1, LW), np.linspace(0, 1.777, LH))
_base_bg = np.array(BG, np.float32)[None, None, :] + np.linspace(0, 1, LH)[:, None, None] * np.array([6, 2, 14], np.float32) + np.zeros((LH, LW, 1), np.float32)
_noise = [np.random.default_rng(i).integers(-3, 4, (H, W, 1)).astype(np.int16) for i in range(4)]
_grid = None
_stars = np.random.default_rng(5).random((90, 5))


def grid_layer():
    global _grid
    if _grid is None:
        g = Image.new('RGBA', (W, H + 120)); d = ImageDraw.Draw(g)
        for x in range(0, W + 1, 120):
            d.line([(x, 0), (x, H + 120)], fill=(160, 130, 255, 14), width=2)
        for y in range(0, H + 120, 120):
            d.line([(0, y), (W, y)], fill=(160, 130, 255, 14), width=2)
        # маска: сетка сильнее внизу
        m = np.linspace(0.25, 1.0, H + 120)[:, None] * np.ones((1, W))
        a = np.asarray(g.getchannel('A'), np.float32) * m
        g.putalpha(Image.fromarray(a.astype(np.uint8)))
        _grid = g
    return _grid


def background(t, hue_shift=0.0, pulse=0.0):
    img = _base_bg.copy()
    glows = [
        (0.2 + 0.15 * math.sin(t * 0.35), 0.35 + 0.12 * math.cos(t * 0.27), 0.55, P1, 0.55),
        (0.85 + 0.1 * math.cos(t * 0.31), 1.0 + 0.2 * math.sin(t * 0.23), 0.6, P2, 0.38),
        (0.5 + 0.3 * math.sin(t * 0.19 + 1), 1.55 + 0.1 * math.cos(t * 0.4), 0.5, P3, 0.25),
    ]
    for cx, cy, r, col, inten in glows:
        d2 = (_gx - cx) ** 2 + (_gy - cy) ** 2
        img += np.exp(-d2 / (r * r * 0.35))[..., None] * np.array(col, np.float32)[None, None, :] * (inten * (1 + pulse))
    small = Image.fromarray(np.clip(img, 0, 255).astype(np.uint8), 'RGB')
    big = np.asarray(small.resize((W, H), Image.BICUBIC), np.int16)
    big = np.clip(big + _noise[int(t * FPS) % 4], 0, 255).astype(np.uint8)
    im = Image.fromarray(big, 'RGB').convert('RGBA')
    off = int(t * 40) % 120
    im.alpha_composite(grid_layer().crop((0, off, W, off + H)))
    # звёздочки
    fx = Image.new('RGBA', (W, H)); d = ImageDraw.Draw(fx)
    for x0, y0, sp, sz, ph in _stars:
        x = x0 * W + 20 * math.sin(t * 0.5 + ph * 6)
        y = (y0 * H - t * (15 + sp * 50)) % H
        tw = 0.45 + 0.55 * (0.5 + 0.5 * math.sin(t * (2 + sp * 3) + ph * 10))
        r = 1.2 + sz * 2.6
        a = int(200 * tw * (0.4 + 0.6 * sz))
        d.ellipse([x - r, y - r, x + r, y + r], fill=(220, 210, 255, a))
        if sz > 0.82:
            L = r * 5 * tw
            d.line([(x - L, y), (x + L, y)], fill=(220, 210, 255, a // 2), width=2)
            d.line([(x, y - L), (x, y + L)], fill=(220, 210, 255, a // 2), width=2)
    im.alpha_composite(fx)
    return im


# ------------------------------------------------------------------ телефон (рисуется в 2x и уменьшается)
K = 2
PW, PH = 720, 840


def rr(d, box, r, **kw):
    x0, y0, x1, y1 = box
    d.rounded_rectangle([x0 * K, y0 * K, x1 * K, y1 * K], r * K, **kw)


def txt(im, x, y, s, w, size, fill, anchor='la'):
    d = ImageDraw.Draw(im)
    d.text((x * K, y * K), s, font=F(w, size * K), fill=fill, anchor=anchor)


def rtxt(im, x, y, s, w, size, fill):
    return rich_line(im, x * K, y * K, s, F(w, size * K), fill) / K


def avatar(im, cx, cy, r, color, letter):
    d = ImageDraw.Draw(im)
    g = grad_img(int(r * 2 * K), int(r * 2 * K), [lerpc(color, WHITE, 0.25), color, lerpc(color, (0, 0, 0), 0.25)], 0.8)
    gi = Image.fromarray(g.astype(np.uint8)).convert('RGBA')
    m = Image.new('L', gi.size, 0); ImageDraw.Draw(m).ellipse([0, 0, gi.width - 1, gi.height - 1], fill=255)
    gi.putalpha(m)
    im.alpha_composite(gi, (int((cx - r) * K), int((cy - r) * K)))
    d.text((cx * K, cy * K), letter, font=F('b', int(r * (0.85 if len(letter) == 1 else 0.7) * K)), fill=WHITE, anchor='mm')


HEADER_H = 108
INPUT_H = 92
SCREEN = (14, 10, 26)
IN_BUB = (36, 28, 58)
OUT_BUB = (124, 58, 237)
AI_BUB = (17, 40, 56)


def bubble_layout(it):
    f = F('r', 30)
    maxw = 470
    lines = []
    if it.get('voice'):
        w = 440; h = 92
        tl = []
        if it.get('trans') is not None:
            tl = wrap(it['text'], f, maxw - 40)
            h += 18 + len(tl) * 40
        return w, h, tl
    lines = wrap(it['text'], f, maxw - 44)
    w = max(F('r', 30).getlength(l) for l in lines) + 44
    if it['side'] == 'out':
        w = max(w, F('r', 30).getlength(lines[-1]) + 44 + 110)
    top = 0
    if it.get('who'):
        top = 34; w = max(w, F('sb', 24).getlength(it['who']) + 44)
    if it['side'] == 'ai':
        top = 40; w = max(w, 300)
    h = top + len(lines) * 40 + 30 + (24 if it['side'] in ('in', 'out', 'ai') else 0)
    return min(w, maxw + 10), h, lines


class Phone:
    def __init__(self, sc):
        self.sc = sc
        self.rects = {}

    def frame(self, st):
        sc = self.sc
        im = Image.new('RGBA', (PW * K, PH * K))
        d = ImageDraw.Draw(im)
        # корпус
        rr(d, (0, 0, PW, PH), 64, fill=(30, 22, 52))
        rr(d, (8, 8, PW - 8, PH - 8), 58, fill=SCREEN)
        # шапка
        scr = sc['screen']
        hd = sc['header']
        rr(d, (8, 8, PW - 8, HEADER_H + 8), 58, fill=(22, 16, 40))
        d.rectangle([8 * K, 60 * K, (PW - 8) * K, (HEADER_H + 8) * K], fill=(22, 16, 40))
        d.line([8 * K, (HEADER_H + 8) * K, (PW - 8) * K, (HEADER_H + 8) * K], fill=(50, 40, 80), width=2 * K)
        if scr == 'chat':
            txt(im, 40, 62, '‹', 'r', 56, (180, 160, 255), 'lm')
            avatar(im, 112, 62, 32, hd['color'], hd['letter'])
            txt(im, 160, 44, hd['name'], 'sb', 30, WHITE, 'lm')
            status, col = hd['status'][0][1], hd['status'][0][2]
            for tt, s, c in hd['status']:
                if st >= self.T(tt):
                    status, col = s, c
            txt(im, 160, 82, status, 'r', 23, P3 if col == 'c' else GRAY, 'lm')
            for i in range(3):
                d.ellipse([(PW - 56) * K - 5 * K, (44 + i * 14) * K - 5 * K, (PW - 56) * K + 5 * K, (44 + i * 14) * K + 5 * K], fill=(170, 160, 200))
        else:
            txt(im, PW / 2, 62, hd['title'], 'b', 32, WHITE, 'mm')
            if scr == 'list':
                txt(im, 44, 62, 'Изм.', 'm', 26, (180, 160, 255), 'lm')
                # звёздочка StarTegs
                d.polygon([(x * K, y * K) for x, y in star_poly(PW - 60, 62, 20, 9)], fill=YEL)
        self.rects = {}
        items = sc['items']
        if scr == 'chat':
            self.draw_chat(im, st, items)
        elif scr == 'list':
            self.draw_list(im, st, items)
        elif scr == 'settings':
            self.draw_settings(im, st, items)
        for it in items:
            k = it['k']
            if k == 'menu':
                self.draw_menu(im, st, it)
            elif k == 'card':
                self.draw_card(im, st, it)
            elif k == 'pin':
                self.draw_pin(im, st, it)
            elif k == 'toast':
                self.draw_toast(im, st, it)
        # тапы
        for idx, it in enumerate(items):
            if 'tap' in it and it['k'] != 'card':
                r = self.rects.get(idx)
                if r:
                    self.draw_tap(im, st, self.T(it['tap']), (r[0] + r[2]) / 2, (r[1] + r[3]) / 2)
        # маска закругления экрана
        mask = Image.new('L', im.size, 0)
        ImageDraw.Draw(mask).rounded_rectangle([0, 0, im.width - 1, im.height - 1], 64 * K, fill=255)
        a = np.minimum(np.asarray(im.getchannel('A')), np.asarray(mask))
        im.putalpha(Image.fromarray(a))
        # рамка-свечение
        d = ImageDraw.Draw(im)
        rr(d, (2, 2, PW - 2, PH - 2), 62, outline=(120, 90, 220), width=3)
        return im.resize((PW, PH), Image.LANCZOS)

    T = None  # подставляется сценой

    # ---------------- чат
    def draw_chat(self, im, st, items):
        d = ImageDraw.Draw(im)
        # поле ввода
        inp = next((it for it in items if it['k'] == 'input'), None)
        y0 = PH - 8 - INPUT_H
        d.rectangle([8 * K, y0 * K, (PW - 8) * K, (PH - 8) * K], fill=(22, 16, 40))
        rr(d, (30, y0 + 16, PW - 110, y0 + 76), 30, fill=(36, 28, 58))
        shown, ph = '', True
        if inp:
            t0, t1 = self.T(inp['t']), self.T(inp['t1'])
            if st >= t0:
                n = int(len(inp['text']) * prog(st, t0, t1 - t0))
                shown = inp['text'][:n]; ph = False
            if 'rep' in inp and st >= self.T(inp['rep']):
                shown = inp['rep_text']
            if 'send' in inp and st >= self.T(inp['send']):
                shown = ''; ph = True
        f = F('r', 26 * K)
        if ph:
            txt(im, 56, y0 + 46, 'Сообщение', 'r', 26, (120, 110, 150), 'lm')
        else:
            s = shown
            while f.getlength(s) > (PW - 190) * K and len(s) > 1:
                s = s[1:]
            col = WHITE
            if inp and 'rep' in inp and st >= self.T(inp['rep']):
                col = lerpc((134, 239, 172), WHITE, prog(st, self.T(inp['rep']) + 0.3, 0.6))
            txt(im, 56, y0 + 46, s, 'r', 26, col, 'lm')
            if (st * 2) % 1 < 0.6 and shown != '' and (inp is None or 'rep' not in inp or st < self.T(inp['rep'])):
                cx = 56 + f.getlength(s) / K + 3
                d.rectangle([cx * K, (y0 + 30) * K, (cx + 3) * K, (y0 + 62) * K], fill=P3)
        # кнопка
        d.ellipse([(PW - 92) * K, (y0 + 14) * K, (PW - 30) * K, (y0 + 76) * K], fill=OUT_BUB)
        if ph:
            # микрофон
            cx, cy = PW - 61, y0 + 45
            rr(d, (cx - 8, cy - 17, cx + 8, cy + 5), 8, fill=WHITE)
            d.arc([(cx - 14) * K, (cy - 10) * K, (cx + 14) * K, (cy + 14) * K], 0, 180, fill=WHITE, width=3 * K)
        else:
            cx, cy = PW - 61, y0 + 45
            d.polygon([((cx - 12) * K, (cy - 14) * K), ((cx + 16) * K, cy * K), ((cx - 12) * K, (cy + 14) * K), ((cx - 6) * K, cy * K)], fill=WHITE)

        # пузыри снизу вверх
        bottom = y0 - 18
        bubs = [(i, it) for i, it in enumerate(items) if it['k'] == 'bubble']
        y = bottom
        for idx, it in reversed(bubs):
            t0 = self.T(it['t'])
            if st < t0:
                continue
            p = eo(prog(st, t0, 0.32))
            w, h, lines = bubble_layout(it)
            if it.get('voice') and it.get('trans') is not None:
                tp = eo(prog(st, self.T(it['trans']), 0.35))
                h = 92 + (h - 92) * tp
            extra = 0
            if it.get('btn') and st >= self.T(it['btn_t']):
                extra = 76 * eo(prog(st, self.T(it['btn_t']), 0.3))
            hh = (h + 16 + extra) * p
            y -= hh
            if y + h < HEADER_H:
                break
            bub = self.bubble_img(it, st, w, h, lines)
            side = it['side']
            x = 30 if side in ('in', 'ai') else PW - 30 - w
            sc = 0.75 + 0.25 * eob(prog(st, t0, 0.35))
            alpha = p
            jit = 0
            if it.get('deleted') is not None:
                td = self.T(it['deleted'])
                if td <= st < td + 0.3:
                    jit = (np.random.default_rng(int(st * 100)).random() - 0.5) * 24
            bw, bh = bub.width / K, bub.height / K
            ax = x if side != 'out' else x + w
            nx = ax - (bw * sc if side == 'out' else 0) + jit
            ny = y + (h - bh * sc)
            if abs(sc - 1) > 0.01:
                bub = bub.resize((max(1, int(bub.width * sc)), max(1, int(bub.height * sc))), Image.BILINEAR)
            comp(im, bub, nx * K, ny * K, alpha)
            self.rects[idx] = (x, y, x + w, y + h)
            if extra > 0:
                bp = eob(prog(st, self.T(it['btn_t']), 0.4))
                emo, label = it['btn']
                pulse = 1 + 0.04 * math.sin((st - self.T(it['btn_t'])) * 8)
                pressed = 'tap' in it and st >= self.T(it['tap'])
                bwid = 290
                bx, by = x, y + h + 12
                pill = Image.new('RGBA', (bwid * K, 60 * K))
                pd = ImageDraw.Draw(pill)
                col = (34, 197, 94) if pressed else P1
                pd.rounded_rectangle([0, 0, pill.width - 1, pill.height - 1], 30 * K, fill=col)
                rich_line(pill, 26 * K, 10 * K, emo + ' ' + ('Напомню ✓' if pressed else label), F('sb', 28 * K), WHITE)
                comp_c(im, pill, (bx + bwid / 2) * K, (by + 30) * K, bp * pulse, min(1, bp))
                self.rects[('btn', idx)] = (bx, by, bx + bwid, by + 60)
                if 'tap' in it:
                    self.rects[idx] = (bx, by, bx + bwid, by + 60)

    def bubble_img(self, it, st, w, h, lines):
        side = it['side']
        im = Image.new('RGBA', (int(w * K), int(h * K)))
        d = ImageDraw.Draw(im)
        deleted = it.get('deleted') is not None and st >= self.T(it['deleted'])
        col = {'in': IN_BUB, 'out': OUT_BUB, 'ai': AI_BUB}[side]
        if deleted:
            col = (70, 22, 40)
        if side == 'out' and not deleted:
            g = grad_img(im.width, im.height, [(147, 51, 234), (124, 58, 237), (219, 39, 119)], 0.6).astype(np.uint8)
            gi = Image.fromarray(g).convert('RGBA')
            m = Image.new('L', im.size, 0)
            ImageDraw.Draw(m).rounded_rectangle([0, 0, im.width - 1, im.height - 1], 26 * K, fill=255)
            gi.putalpha(m); im.alpha_composite(gi)
        else:
            d.rounded_rectangle([0, 0, im.width - 1, im.height - 1], 26 * K, fill=col,
                                outline=(239, 68, 68) if deleted else ((34, 211, 238) if side == 'ai' else None), width=2 * K)
        y = 16
        if it.get('who'):
            txt(im, 22, 12, it['who'], 'sb', 24, [P3, YEL, (134, 239, 172), P2][hash(it['who']) % 4]); y = 46
        if side == 'ai':
            rich_line(im, 22 * K, 12 * K, '✨ StarTegs AI', F('sb', 24 * K), P3); y = 52
        if it.get('voice'):
            # кнопка play + волна
            d.ellipse([18 * K, 16 * K, 78 * K, 76 * K], fill=P1)
            d.polygon([(40 * K, 32 * K), (62 * K, 46 * K), (40 * K, 60 * K)], fill=WHITE)
            rng = np.random.default_rng(3)
            hs = rng.random(34)
            for i, hv in enumerate(hs):
                bx = 96 + i * 8.6
                bh = 6 + hv * 34
                d.rounded_rectangle([bx * K, (40 - bh / 2) * K, (bx + 4.5) * K, (40 + bh / 2) * K], 2 * K, fill=(190, 170, 255) if i < 6 else (110, 95, 150))
            txt(im, 96, 66, it['voice'], 'm', 20, GRAY)
            if it.get('trans') is not None and st >= self.T(it['trans']):
                t0 = self.T(it['trans']); t1 = self.T(it.get('typing', 0.9))
                full = ' '.join(lines)
                n = int(len(full) * prog(st, t0 + 0.2, t1 - t0 - 0.2))
                d.line([20 * K, 100 * K, (w - 20) * K, 100 * K], fill=(80, 70, 110), width=K)
                c = 0
                for i, l in enumerate(lines):
                    part = l[:max(0, n - c)]; c += len(l) + 1
                    if part:
                        txt(im, 22, 114 + i * 40, part, 'r', 28, (230, 225, 255))
                txt(im, w - 22, 70, '✨ ИИ', 'sb', 20, P3, 'ra')
            return im
        n = None
        if it.get('typing') is not None:
            t0 = self.T(it['t']) + 0.25; t1 = self.T(it['typing'])
            n = int(len(' '.join(lines)) * prog(st, t0, t1 - t0))
        c = 0
        for i, l in enumerate(lines):
            part = l if n is None else l[:max(0, n - c)]
            c += len(l) + 1
            if part:
                txt(im, 22, y + i * 40, part, 'r', 30, (255, 225, 230) if deleted else WHITE)
        if n is not None and n < len(' '.join(lines)) and (st * 3) % 1 < 0.6:
            # курсор
            li = 0; c = 0
            for i, l in enumerate(lines):
                if n <= c + len(l):
                    li = i; break
                c += len(l) + 1
            part = lines[li][:max(0, n - c)]
            cx = 22 + F('r', 30 * K).getlength(part) / K + 2
            d.rectangle([cx * K, (y + li * 40 + 4) * K, (cx + 3) * K, (y + li * 40 + 36) * K], fill=P3)
        by = h - 30
        if deleted:
            rich_line(im, 22 * K, (by - 2) * K, '🗑 удалено', F('sb', 21 * K), (252, 165, 165))
        if side == 'out':
            txt(im, w - 62, by, '12:41', 'r', 20, (230, 210, 255), 'ra')
            d.text(((w - 22) * K, by * K), '✓✓', font=SYM(20 * K), fill=(230, 210, 255), anchor='ra')
        elif side == 'in':
            txt(im, w - 22, by, '12:40', 'r', 20, GRAY, 'ra')
        return im

    # ---------------- список чатов
    def draw_list(self, im, st, items):
        d = ImageDraw.Draw(im)
        y = HEADER_H + 20
        # поиск
        rr(d, (28, y, PW - 28, y + 54), 27, fill=(30, 24, 50))
        txt(im, 60, y + 27, 'Поиск', 'r', 25, (120, 110, 150), 'lm')
        y += 74
        rows = [(i, it) for i, it in enumerate(items) if it['k'] == 'row']
        for idx, it in rows:
            t0 = self.T(it['t'])
            if st < t0:
                continue
            p = eo(prog(st, t0, 0.3)) if it['t'] else 1.0
            rh = 104
            out_p = 0
            if it.get('out') is not None:
                out_p = eio(prog(st, self.T(it['out']), 0.45))
            hh = rh * p * (1 - out_p)
            if hh < 1:
                continue
            x_off = out_p * PW * 1.1
            row = Image.new('RGBA', (PW * K, rh * K))
            rd = ImageDraw.Draw(row)
            hl = 'tap' in it and self.T(it['tap']) <= st
            if hl:
                rd.rectangle([0, 0, row.width, row.height], fill=(60, 40, 100))
            avatar(row, 70, rh / 2, 36, it['color'], it['name'][0])
            txt(row, 126, 26, it['name'], 'sb', 29, WHITE)
            rich_line(row, 126 * K, 62 * K, it['prev'], F('r', 24 * K), GRAY)
            txt(row, PW - 40, 28, '12:3' + str(idx % 10), 'r', 20, GRAY, 'ra')
            if it.get('badge'):
                b = str(it['badge']); bw = max(38, F('b', 20).getlength(b) + 22)
                rd.rounded_rectangle([(PW - 40 - bw) * K, 58 * K, (PW - 40) * K, 92 * K], 17 * K, fill=P1)
                rd.text(((PW - 40 - bw / 2) * K, 75 * K), b, font=F('b', 20 * K), fill=WHITE, anchor='mm')
            rd.line([126 * K, (rh - 1) * K, PW * K, (rh - 1) * K], fill=(40, 32, 62), width=K)
            if out_p > 0:
                row = with_alpha(row, 1 - out_p * 0.6)
                # значок скрытия
                lk = emoji('🙈', 60 * K)
                comp(im, lk, (40 + x_off * 0.0) * K, (y + rh / 2 - 30) * K, min(1, out_p * 3) * (1 - out_p))
            crop_h = int(hh * K)
            comp(im, row.crop((0, int((rh * K - crop_h) / 2), row.width, int((rh * K + crop_h) / 2))), x_off * K, y * K, min(1, p))
            self.rects[idx] = (0, y, PW, y + hh)
            y += hh

    # ---------------- настройки (переключатели)
    def draw_settings(self, im, st, items):
        d = ImageDraw.Draw(im)
        y = HEADER_H + 40
        ghost = emoji('👻', 120 * K)
        bob = 8 * math.sin(st * 2.5)
        comp(im, ghost, (PW / 2 - 60) * K, (y + bob) * K, 0.95)
        y += 150
        txt(im, PW / 2, y, 'Режим призрака', 'b', 30, WHITE, 'ma')
        y += 60
        for idx, it in enumerate(items):
            if it['k'] != 'toggle':
                continue
            rh = 100
            rr(d, (28, y, PW - 28, y + rh - 14), 24, fill=(28, 21, 48))
            rich_line(im, 50 * K, (y + 24) * K, it['emo'], F('r', 30 * K), WHITE)
            txt(im, 104, y + (rh - 14) / 2, it['label'], 'm', 27, WHITE, 'lm')
            on = prog(st, self.T(it['t']), 0.22)
            on = eio(on)
            tx0, ty0 = PW - 140, y + (rh - 14) / 2 - 22
            col = lerpc((70, 62, 96), (34, 197, 94), on)
            rr(d, (tx0, ty0, tx0 + 84, ty0 + 44), 22, fill=col)
            kx = tx0 + 22 + on * 40
            d.ellipse([(kx - 18) * K, (ty0 + 4) * K, (kx + 18) * K, (ty0 + 40) * K], fill=WHITE)
            if 0 < st - self.T(it['t']) < 0.6:
                gp = (st - self.T(it['t'])) / 0.6
                rr(d, (28 - 6 * gp, y - 6 * gp, PW - 28 + 6 * gp, y + rh - 14 + 6 * gp), 26, outline=(34, 197, 94, int(255 * (1 - gp))), width=3 * K)
            y += rh

    # ---------------- контекстное меню
    def draw_menu(self, im, st, it):
        t0, t1 = self.T(it['t']), self.T(it['out'])
        if st < t0 or st > t1 + 0.25:
            return
        p = eob(prog(st, t0, 0.3)); a = min(1, prog(st, t0, 0.15)) * (1 - prog(st, t1, 0.25))
        dim = Image.new('RGBA', im.size, (0, 0, 0, int(140 * a)))
        im.alpha_composite(dim)
        mw, rh = 470, 84
        mh = rh * len(it['opts']) + 20
        m = Image.new('RGBA', (mw * K, mh * K)); md = ImageDraw.Draw(m)
        md.rounded_rectangle([0, 0, m.width - 1, m.height - 1], 28 * K, fill=(34, 27, 58), outline=(90, 70, 150), width=2 * K)
        sel_on = st >= self.T(it['sel_t'])
        for i, (e, label) in enumerate(it['opts']):
            yy = 10 + i * rh
            if sel_on and i == it['sel']:
                md.rounded_rectangle([10 * K, yy * K, (mw - 10) * K, (yy + rh) * K], 20 * K, fill=(90, 60, 170))
            rich_line(m, 34 * K, (yy + 22) * K, e, F('r', 30 * K), WHITE)
            txt(m, 92, yy + rh / 2, label, 'sb' if i == it['sel'] else 'm', 28, WHITE, 'lm')
        cx, cy = PW / 2, PH / 2 + 40
        comp_c(im, m, cx * K, cy * K, 0.6 + 0.4 * p, a)
        sy = cy - mh / 2 + 10 + it['sel'] * rh + rh / 2
        self.draw_tap(im, st, self.T(it['sel_t']) - 0.05, cx + 120, sy)

    def draw_tap(self, im, st, t0, x, y):
        dt = st - t0
        if dt < -0.18 or dt > 0.7:
            return
        d = ImageDraw.Draw(im)
        if dt < 0:
            a = 1 - (-dt / 0.18)
            r = 34
            d.ellipse([(x - r) * K, (y - r) * K, (x + r) * K, (y + r) * K], fill=(255, 255, 255, int(70 * a)), outline=(255, 255, 255, int(200 * a)), width=3 * K)
            return
        p = dt / 0.7
        r = 30 + 70 * eo(p)
        layer = Image.new('RGBA', im.size)
        ld = ImageDraw.Draw(layer)
        ld.ellipse([(x - r) * K, (y - r) * K, (x + r) * K, (y + r) * K], outline=(255, 255, 255, int(220 * (1 - p))), width=4 * K)
        r2 = 26 * (1 - p * 0.5)
        ld.ellipse([(x - r2) * K, (y - r2) * K, (x + r2) * K, (y + r2) * K], fill=(255, 255, 255, int(150 * (1 - p))))
        im.alpha_composite(layer)

    # ---------------- карточка
    def draw_card(self, im, st, it):
        t0 = self.T(it['t'])
        t1 = self.T(it['out']) if it.get('out') is not None else 1e9
        if st < t0 or st > t1 + 0.3:
            return
        p = eob(prog(st, t0, 0.38)); a = min(1, prog(st, t0, 0.18)) * (1 - prog(st, t1, 0.28))
        dim = Image.new('RGBA', im.size, (0, 0, 0, int(120 * a)))
        im.alpha_composite(dim)
        cw = 620
        lines = it['lines']
        ch = 130 + len(lines) * 50 + (100 if it.get('btns') else 20)
        c = Image.new('RGBA', (cw * K, ch * K)); cd = ImageDraw.Draw(c)
        warn = it['tone'] == 'warn'
        cd.rounded_rectangle([0, 0, c.width - 1, c.height - 1], 32 * K, fill=(40, 26, 30) if warn else (16, 32, 48),
                             outline=(251, 146, 60) if warn else P3, width=3 * K)
        rich_line(c, 30 * K, 30 * K, it['emo'], F('b', 40 * K), WHITE)
        txt(c, 96, 58, it['title'], 'b', 36, (253, 186, 116) if warn else WHITE, 'lm')
        typ = it.get('typing')
        full = sum(len(l) for l in lines)
        n = None
        if typ is not None:
            n = int(full * prog(st, t0 + 0.35, self.T(typ) - t0 - 0.35))
        cnt = 0
        for i, l in enumerate(lines):
            part = l if n is None else l[:max(0, n - cnt)]
            cnt += len(l)
            if part:
                rich_line(c, 32 * K, (112 + i * 50) * K, part, F('m', 29 * K), (235, 230, 250))
        if it.get('btns'):
            by = ch - 88
            bw = (cw - 30 * 3) / 2
            tap_i, tap_t = it.get('tap', (None, None))
            for i, b in enumerate(it['btns']):
                bx = 30 + i * (bw + 30)
                hi = i == 1
                pressed = tap_i == i and st >= self.T(tap_t)
                fill = (34, 197, 94) if pressed else (P1 if hi else (60, 50, 80))
                cd.rounded_rectangle([bx * K, by * K, (bx + bw) * K, (by + 62) * K], 31 * K, fill=fill)
                L = rich_len(b, F('sb', 26 * K)) / K
                rich_line(c, (bx + bw / 2 - L / 2) * K, (by + 15) * K, b, F('sb', 26 * K), WHITE)
        cx, cy = PW / 2, PH / 2 + 30
        comp_c(im, c, cx * K, cy * K, 0.7 + 0.3 * p, a)
        if it.get('tap'):
            i, tt = it['tap']
            bw = (cw - 90) / 2
            bx = cx - cw / 2 + 30 + i * (bw + 30) + bw / 2
            by = cy - ch / 2 + ch - 88 + 31
            self.draw_tap(im, st, self.T(tt), bx, by)

    # ---------------- PIN-экран
    def draw_pin(self, im, st, it):
        t0, t1 = self.T(it['t']), self.T(it['end'])
        if st < t0 - 0.01:
            return
        out = prog(st, t1 + (0.55 if it.get('ok') else 0.0), 0.35)
        if out >= 1 and not it.get('ok'):
            return
        a = min(1, prog(st, t0, 0.2) if it['t'] else 1)
        lay = Image.new('RGBA', im.size)
        ld = ImageDraw.Draw(lay)
        ld.rounded_rectangle([8 * K, 8 * K, (PW - 8) * K, (PH - 8) * K], 58 * K, fill=(16, 11, 30, 250))
        ok = it.get('ok') and st >= t1
        lk = emoji('🔓' if ok else '🔒', 84 * K)
        comp(lay, lk, (PW / 2 - 42) * K, 90 * K)
        txt(lay, PW / 2, 210, it['title'], 'b', 32, WHITE, 'ma')
        nd = 4
        fill_n = min(nd, int(nd * prog(st, t0 + 0.25, (t1 - t0 - 0.35)) + 0.0001))
        shake = 0
        for i in range(nd):
            cx = PW / 2 + (i - 1.5) * 52 + shake
            filled = i < fill_n
            col = (34, 197, 94) if ok else (P1 if filled else (70, 60, 100))
            r = 13 if filled else 11
            ld.ellipse([(cx - r) * K, (290 - r) * K, (cx + r) * K, (290 + r) * K], fill=col if filled or ok else None,
                       outline=col, width=3 * K)
        if it.get('keypad') or not it.get('fp'):
            keys = '123456789 0⌫'
            pressed = None
            if fill_n > 0 and not ok:
                step = (t1 - t0 - 0.35) / nd
                last = t0 + 0.25 + (fill_n - 0) * step - step
                if st - (t0 + 0.25 + (fill_n) * step - step) < 0.18:
                    pressed = [6, 2, 8, 0][min(fill_n - 1, 3)]
            for i, kch in enumerate(keys):
                if kch == ' ':
                    continue
                kx = PW / 2 + (i % 3 - 1) * 150
                ky = 400 + (i // 3) * 104
                kp = pressed is not None and kch == str(pressed if pressed else 0) and str(pressed) == kch
                ld.ellipse([(kx - 44) * K, (ky - 44) * K, (kx + 44) * K, (ky + 44) * K], fill=(90, 60, 170) if kp else (34, 27, 58))
                ld.text((kx * K, ky * K), kch, font=F('m', 38 * K) if kch != '⌫' else SYM(34 * K), fill=WHITE, anchor='mm')
        else:
            # отпечаток
            fp_p = prog(st, t0 + 0.2, t1 - t0 - 0.2)
            fe = emoji('🫆', 10)  # может отсутствовать в шрифте — рисуем сами
            cx, cy = PW / 2, 520
            for i in range(7):
                r = 22 + i * 18
                col = lerpc((70, 60, 110), (34, 197, 94) if ok else P3, clamp(fp_p * 7 - i))
                ld.arc([(cx - r) * K, (cy - r * 1.15) * K, (cx + r) * K, (cy + r * 1.15) * K], 200 + i * 6, 340 - i * 4, fill=col, width=5 * K)
                ld.arc([(cx - r) * K, (cy - r * 1.15) * K, (cx + r) * K, (cy + r * 1.15) * K], 10 + i * 5, 160 - i * 6, fill=col, width=5 * K)
            txt(lay, PW / 2, 690, 'Приложите палец' if not ok else '', 'm', 26, GRAY, 'ma')
            if ok:
                okp = eob(prog(st, t1, 0.4))
                pill = Image.new('RGBA', (300 * K, 70 * K)); pd = ImageDraw.Draw(pill)
                pd.rounded_rectangle([0, 0, pill.width - 1, pill.height - 1], 35 * K, fill=(34, 197, 94))
                pd.text((150 * K, 35 * K), '✓ ' + it['ok'], font=F('b', 30 * K), fill=WHITE, anchor='mm')
                comp_c(lay, pill, PW / 2 * K, 700 * K, okp, 1)
        if it.get('ok') and st >= t1 + 0.55:
            # показываем скрытые чаты
            hp_ = eo(prog(st, t1 + 0.55, 0.4))
            ld.rounded_rectangle([8 * K, 8 * K, (PW - 8) * K, (PH - 8) * K], 58 * K, fill=(14, 10, 26, int(255 * hp_)))
            y = 140
            txt(lay, PW / 2, 70, '🙈 Скрытые чаты'.replace('🙈 ', ''), 'b', 32, (255, 255, 255, int(255 * hp_)), 'mm')
            for j, (nm, pv, col) in enumerate([('Лиза', 'Только никому 🤫', (168, 85, 247)), ('Банк', 'Код: 4417', (34, 197, 94))]):
                pj = eo(prog(st, t1 + 0.7 + j * 0.12, 0.3))
                row = Image.new('RGBA', (PW * K, 104 * K))
                avatar(row, 70, 52, 36, col, nm[0])
                txt(row, 126, 26, nm, 'sb', 29, WHITE)
                rich_line(row, 126 * K, 62 * K, pv, F('r', 24 * K), GRAY)
                comp(lay, row, (1 - pj) * 80 * K, (y + j * 104) * K, pj)
            out = 0
        comp(im, lay, 0, -out * PH * K, a * (1 - out))

    def draw_toast(self, im, st, it):
        t0 = self.T(it['t'])
        if st < t0:
            return
        p = eob(prog(st, t0, 0.4))
        tw = 560
        t = Image.new('RGBA', (tw * K, 92 * K)); td = ImageDraw.Draw(t)
        td.rounded_rectangle([0, 0, t.width - 1, t.height - 1], 30 * K, fill=(22, 60, 44), outline=(34, 197, 94), width=3 * K)
        rich_line(t, 28 * K, 24 * K, it['emo'], F('b', 34 * K), WHITE)
        txt(t, 90, 46, it['text'], 'sb', 29, WHITE, 'lm')
        comp_c(im, t, PW / 2 * K, (HEADER_H + 70 - (1 - p) * 120) * K, 1, min(1, p * 1.5))


# ------------------------------------------------------------------ таймлайн
def words_of(cap):
    raw = cap.split()
    ws = []
    for w in raw:
        if w in ('—', '-', '–') and ws:
            ws[-1] += ' ' + w
        else:
            ws.append(w)
    return ws


def word_times(ws, dur):
    wt = []
    for w in ws:
        x = len(re.sub(r'[^\wа-яА-ЯёЁ0-9]', '', w)) + 2.2
        if re.search(r'[.!?…]["»]?$', w):
            x += 5
        elif re.search(r'[,:—–]["»]?$', w) or w.endswith(' —'):
            x += 2.5
        wt.append(x)
    tot = sum(wt)
    t, out = 0, []
    for w, x in zip(ws, wt):
        out.append((t / tot * dur, (t + x) / tot * dur))
        t += x
    return out


def build(vid):
    V = VIDEOS[vid]
    t = 0.0
    voices, events = [], []
    for i, sc in enumerate(V['scenes']):
        w = audio.tts(sc['voice'])
        sc['vdur'] = len(w) / audio.SR
        sc['start'] = t
        sc['dur'] = VOFF + sc['vdur'] + TAIL
        voices.append((t + VOFF, w))
        sc['words'] = words_of(sc['cap'])
        sc['wt'] = word_times(sc['words'], sc['vdur'] * 0.96)
        t += sc['dur']
    total = t
    V['total_t'] = total
    # события SFX
    cta_start = None
    for i, sc in enumerate(V['scenes']):
        S = sc['start']
        T = make_T(sc)
        if sc['kind'] == 'cta':
            cta_start = S
            events += [(S - 1.35, 'riser', 0.8), (S, 'impact', 1.0), (S + 0.05, 'sparkle', 0.8), (S + T(sc['btn_t']), 'pop', 0.8),
                       (S + T(sc['btn_t']) + 0.25, 'pop', 0.8)]
            continue
        if i > 0:
            events.append((S - 0.12, 'whoosh', 0.9))
        if sc['kind'] == 'hook':
            seen = set()
            for ln in sc['lines']:
                tt = S + T(ln['t'])
                if ln['ph'] not in seen:
                    seen.add(ln['ph']); events.append((tt, 'impact', 0.9 if ln['ph'] == 0 else 0.65))
                    if ln['ph'] > 0:
                        events.append((tt - 0.25, 'whoosh_small', 0.7))
                else:
                    events.append((tt, 'pop', 0.7))
            continue
        events.append((S + 0.35, 'pop', 0.5))
        for it in sc['items']:
            k = it['k']
            if k == 'bubble':
                events.append((S + T(it['t']), 'send' if it['side'] == 'out' else 'pop_in', 0.7))
                if it.get('deleted') is not None:
                    events.append((S + T(it['deleted']), 'glitch', 0.75))
                if it.get('tap') is not None:
                    events.append((S + T(it['tap']), 'click', 0.8))
                if it.get('btn'):
                    events.append((S + T(it['btn_t']), 'pop', 0.8))
                typing_from = None
                if it.get('trans') is not None:
                    events.append((S + T(it['trans']), 'ding', 0.5)); typing_from = T(it['trans']) + 0.2
                elif it.get('typing') is not None:
                    typing_from = T(it['t']) + 0.25
                if typing_from is not None:
                    tt = typing_from
                    while tt < T(it['typing']):
                        events.append((S + tt, 'tick', 0.35)); tt += 0.075
            elif k == 'toggle':
                events += [(S + T(it['t']), 'click', 0.9), (S + T(it['t']) + 0.05, 'ding', 0.3)]
            elif k == 'row':
                if it.get('out') is not None:
                    events.append((S + T(it['out']), 'whoosh_small', 0.8))
                if it.get('tap') is not None:
                    events.append((S + T(it['tap']), 'click', 0.8))
                if it['t'] and isinstance(it['t'], float) and it['t'] > 0:
                    events.append((S + T(it['t']), 'pop_in', 0.4))
            elif k == 'pin':
                t0, t1 = T(it['t']), T(it['end'])
                for j in range(4):
                    events.append((S + t0 + 0.25 + (t1 - t0 - 0.35) / 4 * j, 'click', 0.8))
                events.append((S + t1, 'unlock', 0.9))
            elif k == 'menu':
                events += [(S + T(it['t']), 'pop', 0.6), (S + T(it['sel_t']) - 0.05, 'click', 0.9)]
            elif k == 'card':
                events.append((S + T(it['t']), 'alert' if it['tone'] == 'warn' else 'notify', 0.8))
                if it.get('tap'):
                    events.append((S + T(it['tap'][1]), 'click', 0.9))
                if it.get('typing') is not None:
                    tt = T(it['t']) + 0.35
                    while tt < T(it['typing']):
                        events.append((S + tt, 'tick', 0.3)); tt += 0.08
            elif k == 'toast':
                events.append((S + T(it['t']), 'notify', 0.8))
            elif k == 'input':
                tt = T(it['t'])
                while tt < T(it['t1']):
                    events.append((S + tt, 'tick', 0.45)); tt += 0.07
                if it.get('rep') is not None:
                    events.append((S + T(it['rep']), 'sparkle', 0.6))
    return V, voices, events, total, cta_start


def make_T(sc):
    def T(x):
        if isinstance(x, bool):
            x = float(x)
        if isinstance(x, int):
            return VOFF + sc['wt'][min(x, len(sc['wt']) - 1)][0]
        return VOFF + x * sc['vdur']
    return T


# ------------------------------------------------------------------ сцены
def draw_brand(cv, t):
    chip = Image.new('RGBA', (300, 64)); d = ImageDraw.Draw(chip)
    d.rounded_rectangle([0, 0, 299, 63], 32, fill=(255, 255, 255, 22), outline=(255, 255, 255, 40), width=2)
    d.polygon(star_poly(36, 32, 17, 7.5, t * 0.6), fill=YEL)
    d.text((64, 32), 'StarTegs', font=F('b', 28), fill=WHITE, anchor='lm')
    d.text((196, 33), 'техно', font=F('m', 22), fill=(200, 190, 240), anchor='lm')
    comp(cv, chip, 60, 168)


def draw_captions(cv, sc, st):
    T = make_T(sc)
    ws, wt = sc['words'], sc['wt']
    vt = st - VOFF
    if vt < -0.05 or vt > sc['vdur'] + 0.3:
        return
    # группы по 3 слова / до 20 символов, разрыв после пунктуации
    groups, cur = [], []
    for i, w in enumerate(ws):
        cur.append(i)
        if len(cur) >= 3 or len(' '.join(ws[j] for j in cur)) > 17 or re.search(r'[.!?,…:]["»]?$', w):
            groups.append(cur); cur = []
    if cur:
        groups.append(cur)
    gi = 0
    for k, g in enumerate(groups):
        if wt[g[0]][0] <= vt:
            gi = k
    g = groups[gi]
    cur_w = max([i for i in g if wt[i][0] <= vt] or [g[0]])
    gstart = wt[g[0]][0]
    size = 64
    f = F('k', size)
    parts = [ws[i].upper() for i in g]
    total_w = sum(f.getlength(p) for p in parts) + 22 * (len(parts) - 1)
    while total_w > 940 and size > 40:
        size -= 4; f = F('k', size)
        total_w = sum(f.getlength(p) for p in parts) + 22 * (len(parts) - 1)
    line = Image.new('RGBA', (int(total_w + 60), size + 50))
    x = 30
    for i, p in zip(g, parts):
        col = YEL if i == cur_w else WHITE
        im = timg(p, 'k', size, col, 7, (12, 6, 24))
        sc_ = 1.0
        line.alpha_composite(im, (int(x), 14))
        x += f.getlength(p) + 22
    pop = eob(prog(vt, gstart, 0.22), 2.2)
    comp_c(cv, line, W / 2, 1470, 0.82 + 0.18 * pop, min(1, prog(vt, gstart, 0.08) * 1.0 + 0.3))


def draw_hook(cv, sc, st):
    T = make_T(sc)
    lines = sc['lines']
    ph = 0
    for ln in lines:
        if st >= T(ln['t']) - 0.05:
            ph = max(ph, ln['ph'])
    cur = [ln for ln in lines if ln['ph'] == ph]
    # размеры
    imgs = []
    for ln in cur:
        s = ln['st']
        if s == 'w':
            im = timg(ln['text'], 'k', 112, WHITE, 0)
        elif s == 'g':
            im = tgrad(ln['text'], 'k', 128)
        elif s == 'G':
            im = tgrad(ln['text'], 'k', 190)
        elif s == 'e':
            im = emoji(ln['text'], 190)
        else:
            im = timg(ln['text'], 'sb', 50, (215, 205, 245))
        if im.width > 980:
            k = 980 / im.width
            im = im.resize((int(im.width * k), int(im.height * k)), Image.LANCZOS)
        imgs.append(im)
    tot = sum(im.height for im in imgs) + 10 * (len(imgs) - 1)
    y = 820 - tot / 2
    ph_t = min(T(ln['t']) for ln in cur)
    for ln, im in zip(cur, imgs):
        t0 = T(ln['t'])
        p = prog(st, t0 - 0.04, 0.32)
        if p <= 0:
            y += im.height + 10; continue
        s = eob(p, 2.4)
        s0 = 0.4 if ln['st'] in ('G', 'e') else 0.7
        scale = s0 + (1 - s0) * s
        bob = 0
        if ln['st'] == 'e':
            bob = 12 * math.sin(st * 3)
        rot_im = im
        comp_c(cv, rot_im, W / 2, y + im.height / 2 + bob, scale, min(1, p * 2.5))
        y += im.height + 10


def draw_feature(cv, sc, st, V, phone_cache):
    T = make_T(sc)
    a_in = eo(prog(st, 0.0, 0.35))
    # номер
    chip = Image.new('RGBA', (260, 70)); d = ImageDraw.Draw(chip)
    g = grad_img(260, 70, [P1, P2]).astype(np.uint8)
    gi = Image.fromarray(g).convert('RGBA'); m = Image.new('L', (260, 70)); ImageDraw.Draw(m).rounded_rectangle([0, 0, 259, 69], 35, fill=255)
    gi.putalpha(m); chip.alpha_composite(gi)
    e = emoji(sc['emo'].replace('️', '')[0], 42)
    chip.alpha_composite(e, (22, 14))
    d.text((80, 35), f"{sc['num']:02d} / {V['total']:02d}", font=F('b', 30), fill=WHITE, anchor='lm')
    comp_c(cv, chip, W / 2, 285 - (1 - a_in) * 40, 0.8 + 0.2 * eob(prog(st, 0, 0.4)), a_in)
    # заголовок
    title = sc['title']
    tim = tgrad(title, 'k', 86, (WHITE, (230, 220, 255), (200, 180, 255)), glow=False)
    if tim.width > 980:
        k = 980 / tim.width; tim = tim.resize((int(tim.width * k), int(tim.height * k)), Image.LANCZOS)
    p = prog(st, 0.08, 0.4)
    comp_c(cv, tim, W / 2 + (1 - eo(p)) * 120, 382, 1, eo(p))
    sub = timg(sc['sub'], 'sb', 40, (196, 181, 253))
    p2 = prog(st, 0.2, 0.4)
    comp_c(cv, sub, W / 2 + (1 - eo(p2)) * 120, 458, 1, eo(p2))
    # телефон
    ph = phone_cache(st)
    pp = eo(prog(st, 0.05, 0.55))
    bob = 6 * math.sin((sc['start'] + st) * 1.6)
    # тень/свечение
    glow = glow_for_phone()
    comp(cv, glow, (W - glow.width) / 2, 540 + (1 - pp) * 260 - 50 + bob, 0.85 * pp)
    comp(cv, ph, (W - PW) / 2, 548 + (1 - pp) * 260 + bob, pp)


@functools.lru_cache(1)
def glow_for_phone():
    g = Image.new('RGBA', (PW + 200, PH + 200))
    d = ImageDraw.Draw(g)
    d.rounded_rectangle([100, 100, 100 + PW, 100 + PH], 64, fill=(139, 92, 246, 150))
    return g.filter(ImageFilter.GaussianBlur(45))


def draw_cta(cv, sc, st):
    T = make_T(sc)
    p = eob(prog(st, 0.0, 0.6), 1.6)
    # лучи
    lay = Image.new('RGBA', (W, H)); d = ImageDraw.Draw(lay)
    cx, cy = W / 2, 640
    for i in range(16):
        a = st * 0.25 + i * math.pi / 8
        L = 900
        w = 0.07
        d.polygon([(cx, cy), (cx + L * math.cos(a - w), cy + L * math.sin(a - w)), (cx + L * math.cos(a + w), cy + L * math.sin(a + w))],
                  fill=(167, 139, 250, int(26 * min(1, p))))
    cv.alpha_composite(lay)
    # звезда
    R = 170 * p
    if R > 2:
        st_im = Image.new('RGBA', (600, 600)); sd = ImageDraw.Draw(st_im)
        pts = star_poly(300, 300, R * 1.0, R * 0.45, math.sin(st * 1.2) * 0.12)
        sd.polygon(pts, fill=(253, 224, 71, 255))
        glow = st_im.filter(ImageFilter.GaussianBlur(28))
        glow = with_alpha(glow, 0.9)
        comp(cv, glow, cx - 300, cy - 300)
        g = grad_img(600, 600, [(254, 240, 138), (250, 204, 21), (245, 158, 11)], 0.9).astype(np.uint8)
        gi = Image.fromarray(g).convert('RGBA'); gi.putalpha(st_im.getchannel('A'))
        comp(cv, gi, cx - 300, cy - 300)
    big = tgrad(sc['big'], 'k', 150)
    p1 = prog(st, 0.25, 0.45)
    comp_c(cv, big, W / 2, 940, 0.6 + 0.4 * eob(p1), min(1, p1 * 2))
    sub = timg(sc['sub'], 'sb', 46, (221, 214, 254))
    p2 = prog(st, 0.45, 0.4)
    comp_c(cv, sub, W / 2, 1050 + (1 - eo(p2)) * 40, 1, eo(p2))
    bt = T(sc['btn_t'])
    for i, (e, label) in enumerate(sc['btns']):
        pb = prog(st, bt + i * 0.25, 0.4)
        if pb <= 0:
            continue
        bw, bh = 400, 104
        b = Image.new('RGBA', (bw, bh)); bd = ImageDraw.Draw(b)
        if i == 1:
            g = grad_img(bw, bh, [P2, P1]).astype(np.uint8)
            gi = Image.fromarray(g).convert('RGBA'); m = Image.new('L', (bw, bh)); ImageDraw.Draw(m).rounded_rectangle([0, 0, bw - 1, bh - 1], 52, fill=255)
            gi.putalpha(m); b.alpha_composite(gi)
        else:
            bd.rounded_rectangle([0, 0, bw - 1, bh - 1], 52, fill=(255, 255, 255, 30), outline=(255, 255, 255, 120), width=3)
        L = rich_len(e + ' ' + label, F('b', 40))
        rich_line(b, (bw - L) / 2, 26, e + ' ' + label, F('b', 40), WHITE)
        pulse = 1 + 0.035 * math.sin((st - bt) * 7 + i * 1.5)
        comp_c(cv, b, W / 2 + (i - 0.5) * 440, 1230, (0.5 + 0.5 * eob(pb)) * pulse, min(1, pb * 2))


# ------------------------------------------------------------------ кадр
_V = None
_meta = None


def init_worker(vid):
    global _V, _meta
    _V, voices, events, total, cta = build(vid)
    _meta = dict(total=total, events=events)


_phone_objs = {}


def render_frame(fi):
    t = fi / FPS
    V = _V
    sc = None
    for s in V['scenes']:
        if s['start'] <= t < s['start'] + s['dur']:
            sc = s
    if sc is None:
        sc = V['scenes'][-1]
    st = t - sc['start']
    # импульс фона на ударах
    pulse = 0.0
    for et, name, g in _meta['events']:
        if name == 'impact' and 0 <= t - et < 0.6:
            pulse = max(pulse, (1 - (t - et) / 0.6) * 0.8)
    cv = background(t, pulse=pulse)
    if sc['kind'] == 'hook':
        draw_hook(cv, sc, st)
    elif sc['kind'] == 'feature':
        key = id(sc)
        if key not in _phone_objs:
            ph = Phone(sc); ph.T = make_T(sc); _phone_objs[key] = ph
        draw_feature(cv, sc, st, V, _phone_objs[key].frame)
    else:
        draw_cta(cv, sc, st)
    draw_brand(cv, t)
    draw_captions(cv, sc, st)
    # прогресс
    d = ImageDraw.Draw(cv)
    pw = int(W * t / _meta['total'])
    g = grad_img(max(2, pw), 8, [P3, P1, P2]).astype(np.uint8)
    cv.alpha_composite(Image.fromarray(np.dstack([g, np.full((8, max(2, pw)), 230, np.uint8)]), 'RGBA'), (0, 0))
    # переход: вспышка + лёгкий зум
    out = cv.convert('RGB')
    ft = st
    if sc is not V['scenes'][0] and ft < 0.22:
        a = (1 - ft / 0.22) * 0.55
        out = Image.blend(out, Image.new('RGB', (W, H), (235, 225, 255)), a)
    if pulse > 0.2:
        sh = int(pulse * 14)
        rng = np.random.default_rng(fi)
        dx, dy = rng.integers(-sh, sh + 1, 2)
        z = 1 + pulse * 0.025
        zz = out.resize((int(W * z), int(H * z)), Image.BILINEAR)
        ox = (zz.width - W) // 2 + dx; oy = (zz.height - H) // 2 + dy
        out = zz.crop((ox, oy, ox + W, oy + H))
    return out.tobytes()


def main(vid, outdir):
    os.makedirs(outdir, exist_ok=True)
    V, voices, events, total, cta = build(vid)
    wav = os.path.join(audio.CACHE, vid + '_mix.wav')
    audio.mix(total, voices, events, cta, wav)
    nframes = int(total * FPS)
    out = os.path.join(outdir, vid + '.mp4')
    ff = subprocess.Popen(['ffmpeg', '-y', '-loglevel', 'error', '-f', 'rawvideo', '-pix_fmt', 'rgb24', '-s', f'{W}x{H}', '-r', str(FPS), '-i', '-',
                           '-i', wav, '-af', 'loudnorm=I=-14:TP=-1.0:LRA=9', '-ar', '48000',
                           '-c:v', 'libx264', '-preset', 'slow', '-crf', '17', '-profile:v', 'high', '-pix_fmt', 'yuv420p',
                           '-c:a', 'aac', '-b:a', '256k', '-movflags', '+faststart', '-shortest', out], stdin=subprocess.PIPE)
    only = os.environ.get('FRAMES')
    with Pool(int(os.environ.get('JOBS', 4)), initializer=init_worker, initargs=(vid,)) as pool:
        for i, fr in enumerate(pool.imap(render_frame, range(nframes), chunksize=8)):
            ff.stdin.write(fr)
            if i % 150 == 0:
                print(f'{vid}: {i}/{nframes}', flush=True)
    ff.stdin.close(); ff.wait()
    print('готово:', out, f'{total:.1f}s')


if __name__ == '__main__':
    if sys.argv[1] == 'still':
        vid = sys.argv[2]
        init_worker(vid)
        for tt in sys.argv[3:]:
            fr = render_frame(int(float(tt) * FPS))
            Image.frombytes('RGB', (W, H), fr).save(os.path.join(audio.CACHE, f'still_{vid}_{tt}.png'))
    else:
        main(sys.argv[1], sys.argv[2] if len(sys.argv) > 2 else 'out')
