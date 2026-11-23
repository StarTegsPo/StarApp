"""Озвучка роликов: Piper TTS (офлайн) → дорожка по таймингам → mux в mp4.

Голос: ru-irinia-medium (https://github.com/rhasspy/piper/releases/tag/v0.0.2).
Модель ищется в $PIPER_VOICE_DIR (по умолчанию ./voice рядом с src).

Запуск: python3 src/voice.py [номер ...]
Берёт немые ролики из video/silent/, кладёт озвученные в video/.
"""
import os
import subprocess
import sys
import wave

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), '..'))
VOICE_DIR = os.environ.get('PIPER_VOICE_DIR', os.path.join(ROOT, 'voice'))
MODEL = os.path.join(VOICE_DIR, 'ru-irinia-medium.onnx')
TMP = os.path.join(ROOT, '.voice_tmp')
SPEED = 0.82  # length_scale: меньше — быстрее (темп TikTok)

# (время старта в секундах, текст). «Стар Тэгс» — так модель произносит название правильно.
CTA = 'Это Стар Тэгс. Ссылка в профиле!'
VO = {
    1: [(0.1, 'Тебе удалили сообщение?'), (2.0, 'Она написала… и сразу удалила.'),
        (5.0, 'Но у меня оно осталось!'), (6.5, 'Теперь она в шоке.'), (9.4, CTA)],
    2: [(0.1, 'Ненавидишь голосовые?'), (1.9, 'Голосовое на шесть минут?'), (4.0, 'Зажимаешь, расшифровать…'),
        (5.4, 'И читаешь всё за пять секунд!'), (9.7, CTA)],
    3: [(0.1, 'Этот чат не найдёт никто.'), (1.9, 'Есть секретный чат?'), (3.9, 'Скрываешь — и его нет.'),
        (5.3, 'Тайное слово — и он снова тут!'), (9.3, CTA)],
    4: [(0.1, 'Не читай тысячу сообщений!'), (1.9, 'Семейный чат опять взорвался?'), (3.6, 'Одна кнопка — и нейросеть пересказывает главное.'), (9.5, CTA)],
    5: [(0.1, 'Читай сообщения незаметно.'), (1.9, 'Ты прочитал, а он не знает.'), (3.7, 'Ни «в сети», ни галочек.'),
        (5.2, 'Четыре переключателя — и ты призрак.'), (9.5, CTA)],
    6: [(0.05, 'Пять фишек, которых нет в Телеграме!'), (1.7, 'Удалённые сообщения.'), (3.6, 'Голосовые в текст.'),
        (5.5, 'Скрытые чаты.'), (7.4, 'Режим призрака.'), (9.3, 'Нейросеть в каждом чате.'),
        (11.2, 'Всё это — Стар Тэгс. Ссылка в профиле!')],
}

NAMES = {1: '01_udalennye', 2: '02_golosovye', 3: '03_skrytye_chaty', 4: '04_ii_propustil', 5: '05_nevidimka',
         6: '06_top5'}


def synth(text, path):
    subprocess.run([sys.executable, '-m', 'piper', '-m', MODEL, '-c', MODEL + '.json', '--length-scale', str(SPEED),
                    '--sentence-silence', '0', '-f', path], input=text.encode(), check=True,
                   stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    with wave.open(path) as w:
        return w.getnframes() / w.getframerate()


def duration(path):
    out = subprocess.run(['ffprobe', '-v', 'error', '-show_entries', 'format=duration', '-of', 'csv=p=0', path],
                         capture_output=True, text=True, check=True).stdout
    return float(out)


def build(i):
    name = NAMES[i]
    src = os.path.join(ROOT, 'video', 'silent', name + '.mp4')
    dst = os.path.join(ROOT, 'video', name + '.mp4')
    total = duration(src)
    lines = VO[i]
    inputs, filters, report = [], [], []
    cursor = 0.0  # конец предыдущей реплики: реплики не накладываются
    for k, (planned, text) in enumerate(lines):
        wav = os.path.join(TMP, f'{name}_{k}.wav')
        d = synth(text, wav)
        start = max(planned, cursor + 0.08)
        nxt = max(lines[k + 1][0], start + d + 0.08) if k + 1 < len(lines) else total
        room = nxt - start - 0.05
        tempo = 1.0
        if d > room:  # не влезает — чуть ускоряем, но не больше чем в 1.3 раза
            tempo = min(1.3, d / room)
        cursor = start + d / tempo
        report.append((start, round(d, 2), round(room, 2), round(tempo, 2), text))
        inputs += ['-i', wav]
        ms = int(start * 1000)
        filters.append(f'[{k + 1}:a]atempo={tempo:.3f},aresample=44100,adelay={ms}|{ms}[v{k}]')
    mix = ''.join(f'[v{k}]' for k in range(len(lines)))
    filters.append(f'{mix}amix=inputs={len(lines)}:normalize=0,'
                   f'highpass=f=80,acompressor=threshold=-18dB:ratio=3:attack=5:release=80,'
                   f'loudnorm=I=-14:TP=-1.5:LRA=7,apad,atrim=0:{total:.3f}[a]')
    subprocess.run(['ffmpeg', '-y', '-loglevel', 'error', '-i', src, *inputs, '-filter_complex', ';'.join(filters),
                    '-map', '0:v', '-map', '[a]', '-c:v', 'copy', '-c:a', 'aac', '-b:a', '160k', '-ar', '44100',
                    '-movflags', '+faststart', '-shortest', dst], check=True)
    for r in report:
        flag = f'  ⚠ ускорено x{r[3]}' if r[3] > 1 else ''
        print(f'  {r[0]:5.2f}s  речь {r[1]:4.2f}s / место {r[2]:4.2f}s{flag}  {r[4]}')
    print('ok', dst)


if __name__ == '__main__':
    if not os.path.exists(MODEL):
        sys.exit(f'Нет модели {MODEL}. Скачайте voice-ru-irinia-medium.tar.gz из релиза piper v0.0.2 '
                 f'и распакуйте в {VOICE_DIR} (или задайте PIPER_VOICE_DIR).')
    os.makedirs(TMP, exist_ok=True)
    for i in [int(a) for a in sys.argv[1:]] or list(VO):
        build(i)
