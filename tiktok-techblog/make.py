"""Генератор аватарки и коротких видео для TikTok-техноблога «ТехноБит».
Требует: Pillow, ffmpeg, RHVoice (apt install rhvoice rhvoice-russian)."""
import os, subprocess, json, math, random
from PIL import Image, ImageDraw, ImageFont, ImageFilter

OUT = os.path.dirname(os.path.abspath(__file__))
TMP = os.path.join(OUT, "_build"); os.makedirs(TMP, exist_ok=True)
FB = "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf"
FR = "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf"
FM = "/usr/share/fonts/truetype/dejavu/DejaVuSansMono-Bold.ttf"
VOICE = os.environ.get("VOICE", "artemiy")
# обработка голоса: теплота, разборчивость, компрессия, лёгкая «комната», громкость под TikTok
VOICE_FX = ("highpass=f=70,equalizer=f=180:t=q:w=1:g=3,equalizer=f=3500:t=q:w=1.5:g=2,deesser,"
            "acompressor=threshold=-20dB:ratio=3:attack=5:release=80,aecho=0.8:0.6:25:0.12,loudnorm=I=-16:TP=-1.5")
CYAN, PINK, BG1, BG2 = (0, 229, 255), (255, 46, 136), (10, 12, 28), (32, 10, 60)

def gradient(w, h):
    img = Image.new("RGB", (w, h)); d = ImageDraw.Draw(img)
    for y in range(h):
        t = y / h
        d.line([(0, y), (w, y)], fill=tuple(int(BG1[i] + (BG2[i] - BG1[i]) * t) for i in range(3)))
    return img

def grid(img, step=60, alpha=28):
    ov = Image.new("RGBA", img.size, (0, 0, 0, 0)); d = ImageDraw.Draw(ov)
    for x in range(0, img.width, step): d.line([(x, 0), (x, img.height)], fill=CYAN + (alpha,))
    for y in range(0, img.height, step): d.line([(0, y), (img.width, y)], fill=CYAN + (alpha,))
    return Image.alpha_composite(img.convert("RGBA"), ov)

def glow_text(img, xy, text, font, color, anchor="mm", r=18):
    g = Image.new("RGBA", img.size, (0, 0, 0, 0))
    ImageDraw.Draw(g).text(xy, text, font=font, fill=color + (255,), anchor=anchor)
    img = Image.alpha_composite(img, g.filter(ImageFilter.GaussianBlur(r)))
    ImageDraw.Draw(img).text(xy, text, font=font, fill=(255, 255, 255, 255), anchor=anchor)
    return img

def chip(img, cx, cy, s, color=CYAN, width=10):
    """Иконка микрочипа."""
    g = Image.new("RGBA", img.size, (0, 0, 0, 0)); d = ImageDraw.Draw(g)
    h = s // 2
    d.rounded_rectangle([cx - h, cy - h, cx + h, cy + h], radius=s // 8, outline=color + (255,), width=width)
    for i in range(4):
        o = -h + (i + 0.5) * s / 4
        for a, b in [((cx + o, cy - h), (cx + o, cy - h - s // 6)), ((cx + o, cy + h), (cx + o, cy + h + s // 6)),
                     ((cx - h, cy + o), (cx - h - s // 6, cy + o)), ((cx + h, cy + o), (cx + h + s // 6, cy + o))]:
            d.line([a, b], fill=color + (255,), width=width)
    blur = g.filter(ImageFilter.GaussianBlur(14))
    img = Image.alpha_composite(img, blur); return Image.alpha_composite(img, g)

# ---------- Аватарка ----------
def avatar():
    S = 1080
    img = grid(gradient(S, S), 54, 22)
    # TikTok обрезает аватар в круг — держим всё в центре
    img = chip(img, S // 2, S // 2, 500, CYAN, 16)
    img = glow_text(img, (S // 2, S // 2), "TB", ImageFont.truetype(FB, 240), PINK, r=22)
    img.convert("RGB").save(os.path.join(OUT, "avatar.png"))

# ---------- Видео ----------
W, H = 1080, 1920

def wrap(text, font, maxw, d):
    words, lines, cur = text.split(), [], ""
    for w in words:
        t = (cur + " " + w).strip()
        if d.textlength(t, font=font) <= maxw: cur = t
        else: lines.append(cur); cur = w
    lines.append(cur); return lines

def frame(title, big, sub, idx, total, path, emoji=None):
    img = grid(gradient(W, H), 72, 20)
    d = ImageDraw.Draw(img)
    # шапка
    img = chip(img, 130, 150, 90, CYAN, 6)
    d = ImageDraw.Draw(img)
    d.text((200, 150), "ТЕХНОБИТ", font=ImageFont.truetype(FB, 54), fill=(255, 255, 255), anchor="lm")
    d.text((W - 70, 150), f"{idx}/{total}", font=ImageFont.truetype(FM, 46), fill=CYAN, anchor="rm")
    # тема
    tf = ImageFont.truetype(FB, 58)
    y = 330
    for ln in wrap(title, tf, W - 160, d):
        img = glow_text(img, (W // 2, y), ln, tf, PINK, r=10); y += 76
    d = ImageDraw.Draw(img)
    # карточка
    top, bot = 560, 1460
    card = Image.new("RGBA", img.size, (0, 0, 0, 0))
    ImageDraw.Draw(card).rounded_rectangle([60, top, W - 60, bot], radius=48, fill=(255, 255, 255, 18), outline=CYAN + (200,), width=4)
    img = Image.alpha_composite(img, card); d = ImageDraw.Draw(img)
    bf = ImageFont.truetype(FB, 84)
    lines = wrap(big, bf, W - 220, d)
    while len(lines) > 5:
        bf = ImageFont.truetype(FB, bf.size - 8); lines = wrap(big, bf, W - 220, d)
    sf = ImageFont.truetype(FR, 46)
    slines = wrap(sub, sf, W - 220, d) if sub else []
    total_h = len(lines) * (bf.size + 18) + (40 + len(slines) * 62 if slines else 0)
    y = (top + bot) // 2 - total_h // 2 + bf.size // 2
    for ln in lines:
        img = glow_text(img, (W // 2, y), ln, bf, CYAN, r=12); y += bf.size + 18
    d = ImageDraw.Draw(img); y += 30
    for ln in slines:
        d.text((W // 2, y), ln, font=sf, fill=(220, 220, 240), anchor="mm"); y += 62
    # прогресс
    d.rounded_rectangle([60, 1560, W - 60, 1576], radius=8, fill=(60, 55, 95))
    d.rounded_rectangle([60, 1560, 60 + (W - 120) * idx / total, 1576], radius=8, fill=PINK)
    d.text((W // 2, 1680), "Подписывайся → @technobit", font=ImageFont.truetype(FB, 48), fill=(255, 255, 255), anchor="mm")
    img.convert("RGB").save(path)

def tts(text, path):
    raw = path + ".raw.wav"
    subprocess.run(["RHVoice-test", "-q", "max", "-p", VOICE, "-r", "105", "-o", raw], input=text.encode(), check=True)
    subprocess.run(["ffmpeg", "-y", "-loglevel", "error", "-i", raw, "-af", VOICE_FX, "-ar", "44100", path], check=True)
    dur = float(subprocess.check_output(["ffprobe", "-v", "error", "-show_entries", "format=duration", "-of", "csv=p=0", path]))
    return dur

def video(name, title, slides):
    parts = []
    for i, (big, sub, speech) in enumerate(slides, 1):
        png = os.path.join(TMP, f"{name}_{i}.png"); wav = os.path.join(TMP, f"{name}_{i}.wav")
        mp4 = os.path.join(TMP, f"{name}_{i}.mp4")
        frame(title, big, sub, i, len(slides), png)
        dur = tts(speech, wav) + 0.35
        # лёгкий зум + плавные переходы
        subprocess.run(["ffmpeg", "-y", "-loglevel", "error", "-loop", "1", "-i", png, "-i", wav,
            "-filter_complex",
            f"[0:v]scale=1188:2112,zoompan=z='min(zoom+0.0006,1.06)':x='iw/2-(iw/zoom/2)':y='ih/2-(ih/zoom/2)':d=1:s={W}x{H}:fps=30,"
            f"fade=t=in:st=0:d=0.25,fade=t=out:st={dur-0.25:.2f}:d=0.25,format=yuv420p[v];[1:a]apad[a]",
            "-map", "[v]", "-map", "[a]", "-t", f"{dur:.2f}", "-c:v", "libx264", "-preset", "medium", "-crf", "20",
            "-c:a", "aac", "-b:a", "160k", "-ar", "44100", "-ac", "2", mp4], check=True)
        parts.append(mp4)
    lst = os.path.join(TMP, f"{name}.txt")
    open(lst, "w").write("".join(f"file '{p}'\n" for p in parts))
    joined = os.path.join(TMP, f"{name}_joined.mp4")
    subprocess.run(["ffmpeg", "-y", "-loglevel", "error", "-f", "concat", "-safe", "0", "-i", lst, "-c", "copy", joined], check=True)
    # фоновая музыка тише голоса и приглушается, когда он звучит (ducking)
    music = os.path.join(TMP, "music.wav")
    subprocess.run(["ffmpeg", "-y", "-loglevel", "error", "-i", joined, "-stream_loop", "-1", "-i", music, "-filter_complex",
        "[1:a]volume=0.22[m];[0:a]asplit[v][sc];[m][sc]sidechaincompress=threshold=0.05:ratio=4:attack=20:release=300[md];"
        "[v][md]amix=inputs=2:duration=first:normalize=0,alimiter=limit=0.95[a]",
        "-map", "0:v", "-map", "[a]", "-c:v", "copy", "-c:a", "aac", "-b:a", "192k", "-shortest",
        "-movflags", "+faststart", os.path.join(OUT, f"{name}.mp4")], check=True)

if __name__ == "__main__":
    avatar()
    subprocess.run(["python3", os.path.join(OUT, "music.py")], check=True)
    data = json.load(open(os.path.join(OUT, "videos.json"), encoding="utf-8"))
    for v in data:
        video(v["file"], v["title"], v["slides"])
    print("готово")
