"""Озвучка роликов (офлайн) → дорожка по таймингам → mux в mp4.

Голоса:
  ролики 01–06 — женский, Piper ru-irinia-medium (https://github.com/rhasspy/piper/releases/tag/v0.0.2),
                модель ищется в $PIPER_VOICE_DIR (по умолчанию ./voice рядом с src);
  ролики 07–16 — мужской, RHVoice «Александр HQ» (apt install rhvoice rhvoice-russian).
Сменить голос для любого ролика: VOICE=rhvoice:artemiy python3 src/voice.py 3

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

CTA_M = 'Это Стар Тэгс. Ссылка в профиле!'
VO.update({
    7: [(0.1, 'Отправил сообщение не тому?'), (1.9, 'Пишешь в рабочий чат… и понимаешь, что ошибся.'),
        (4.3, 'Жмёшь «отменить»!'), (6.2, 'И никто ничего не увидел.'), (9.4, CTA_M)],
    8: [(0.1, 'Пароль, который открывает пустой Телеграм.'), (2.0, 'Вводишь свой код — всё на месте.'),
        (5.1, 'Просят телефон? Вводишь второй.'), (6.8, 'И там ничего лишнего.'), (9.4, CTA_M)],
    9: [(0.1, 'Кто из вас пишет больше?'), (1.9, 'Открываешь статистику переписки…'),
        (3.3, 'Ты пишешь в два раза больше!'), (4.9, 'И даже видно, о чём.'), (9.4, CTA_M)],
    10: [(0.1, 'Нейросеть не даст тебе нагрубить.'), (1.9, 'Пишешь на эмоциях…'),
         (3.7, 'А ИИ подсказывает: звучит резко.'), (5.4, 'Одно нажатие — и ссоры не случилось.'), (9.4, CTA_M)],
    11: [(0.1, 'Десять аккаунтов в Телеграме без премиума.'), (2.0, 'В обычном Телеграме — всего три.'),
         (3.8, 'А в Стар Тэгс — целых десять!'), (9.4, CTA_M)],
    12: [(0.1, 'Твой Телеграм за год.'), (1.8, 'Сорок восемь тысяч сообщений.'), (3.7, 'Главный собеседник — Аня.'),
         (5.6, 'Эмодзи года — смех до слёз.'), (7.5, 'А любимое время — почти два ночи.'),
         (9.4, 'Итоги года — в Стар Тэгс. Ссылка в профиле!')],
    13: [(0.1, 'ИИ отвечает за тебя.'), (1.9, 'Тебе пишут в два часа ночи?'), (3.7, 'Нейросеть ответит сама.'),
         (5.7, 'А ты спокойно спишь.'), (9.4, CTA_M)],
    14: [(0.1, 'Друг пишет на другом языке?'), (1.9, 'Сообщение на испанском?'),
         (4.3, 'Одно нажатие — и всё по-русски.'), (5.9, 'А ответ уйдёт на испанском.'), (9.4, CTA_M)],
    15: [(0.1, 'Вечно забываешь, о чём договорился?'), (2.0, 'Договорились в переписке на завтра.'),
         (3.9, 'Мод сам предлагает напомнить.'), (6.7, 'И ты точно не забудешь.'), (9.4, CTA_M)],
    16: [(0.1, 'Телеграм против Стар Тэгс.'), (2.2, 'Удалённые — остаются.'),
         (3.6, 'Голосовые — текстом.'), (4.9, 'Десять аккаунтов.'), (6.2, 'Перевод — бесплатно.'),
         (7.5, '«Прочитано» — по желанию.'), (9.4, 'Выбор очевиден. Ссылка в профиле!')],
})

MALE = ('rhvoice', 'aleksandr-hq')
FEMALE = ('piper', 'irinia')
VOICES = {i: (FEMALE if i <= 6 else MALE) for i in VO}
RH_RATE = 130  # темп RHVoice, % от обычного

NAMES = {1: '01_udalennye', 2: '02_golosovye', 3: '03_skrytye_chaty', 4: '04_ii_propustil', 5: '05_nevidimka',
         6: '06_top5', 7: '07_otmena', 8: '08_falshivyi_parol', 9: '09_statistika', 10: '10_ton',
         11: '11_10_akkauntov', 12: '12_itogi_goda', 13: '13_avtootvet', 14: '14_perevod', 15: '15_napominanie',
         16: '16_vs'}


def synth(text, path, voice=FEMALE):
    engine, name = voice
    if engine == 'rhvoice':
        raw = path + '.raw.wav'
        subprocess.run(['RHVoice-test', '-p', name, '-r', str(RH_RATE), '-o', raw], input=text.encode(), check=True,
                       stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
        # обрезаем тишину по краям, чтобы тайминги были точными
        subprocess.run(['ffmpeg', '-y', '-loglevel', 'error', '-i', raw, '-af',
                        'silenceremove=start_periods=1:start_threshold=-45dB,areverse,'
                        'silenceremove=start_periods=1:start_threshold=-45dB,areverse', path], check=True)
        with wave.open(path) as w:
            return w.getnframes() / w.getframerate()
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
    voice = VOICES[i]
    if os.environ.get('VOICE'):
        voice = tuple(os.environ['VOICE'].split(':', 1))
    eq = 'equalizer=f=140:t=q:w=1:g=3,' if voice[0] == 'rhvoice' else ''
    inputs, filters, report = [], [], []
    cursor = 0.0  # конец предыдущей реплики: реплики не накладываются
    for k, (planned, text) in enumerate(lines):
        wav = os.path.join(TMP, f'{name}_{k}.wav')
        d = synth(text, wav, voice)
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
                   f'highpass=f=80,{eq}acompressor=threshold=-18dB:ratio=3:attack=5:release=80,'
                   f'loudnorm=I=-14:TP=-1.5:LRA=7,apad,atrim=0:{total:.3f}[a]')
    subprocess.run(['ffmpeg', '-y', '-loglevel', 'error', '-i', src, *inputs, '-filter_complex', ';'.join(filters),
                    '-map', '0:v', '-map', '[a]', '-c:v', 'copy', '-c:a', 'aac', '-b:a', '160k', '-ar', '44100',
                    '-movflags', '+faststart', '-shortest', dst], check=True)
    for r in report:
        flag = f'  ⚠ ускорено x{r[3]}' if r[3] > 1 else ''
        print(f'  {r[0]:5.2f}s  речь {r[1]:4.2f}s / место {r[2]:4.2f}s{flag}  {r[4]}')
    print('ok', dst)


if __name__ == '__main__':
    ids = [int(a) for a in sys.argv[1:]] or list(VO)
    if any(VOICES[i][0] == 'piper' for i in ids) and not os.path.exists(MODEL) and not os.environ.get('VOICE'):
        sys.exit(f'Нет модели {MODEL}. Скачайте voice-ru-irinia-medium.tar.gz из релиза piper v0.0.2 '
                 f'и распакуйте в {VOICE_DIR} (или задайте PIPER_VOICE_DIR).')
    os.makedirs(TMP, exist_ok=True)
    for i in ids:
        build(i)
