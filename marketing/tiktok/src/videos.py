"""Сценарии роликов StarTegs для TikTok. Запуск: python3 src/videos.py [номер ...]"""
import os
import sys

sys.path.insert(0, os.path.dirname(__file__))
from engine import *  # noqa

OUT = os.path.join(os.path.dirname(__file__), '..', 'video', 'silent')


# ---------- общие сцены ----------
def hook(lines, size=104, y=None, emoji_big=None):
    def fn(img, t, d):
        n = len(lines)
        lh = int(size * 1.28)
        yy = y if y is not None else (H - n * lh) // 2 - 120
        if emoji_big:
            p = ease_back(prog(t, 0.05, 0.4))
            if p > 0:
                e = emoji_img(emoji_big, max(1, int(220 * p)))
                img.alpha_composite(e, ((W - e.width) // 2, yy - 280 + (220 - e.height) // 2))
        caption_block(img, lines, yy, t, 0, size=size, stagger=0.15)
    return fn


def top_caption(img, t, items):
    """items: [(start, end, [строки])] — подпись над телефоном."""
    for s, e, lines in items:
        if s <= t < e:
            caption_block(img, lines, 190, t, s, size=68, stagger=0.08)


def chat_scene(title, sub, letter, color, msgs, caps=(), toasts=(), taps=(), extra=None):
    """msgs: список dict: t (появление), text, out, meta, а также необязательные
    поля-функции от времени: props(t)->dict с переопределениями."""

    def fn(img, t, d):
        x0, y0 = phone(img, title, sub, color, letter, slide=prog(t, 0, 0.35))
        if t < 0.35:
            return
        y = y0 + 210
        for m in msgs:
            if t < m['t']:
                continue
            kw = dict(text=m['text'], out=m.get('out', False), meta=m.get('meta', ''))
            if 'props' in m:
                kw.update(m['props'](t))
            p = prog(t, m['t'], 0.25)
            x = x0 + PW - 30 if kw['out'] else x0 + 30
            h = bubble(img, x, y + int((1 - ease_out(p)) * 40), alpha=ease_out(p), **kw)
            y += h + 22
        if extra:
            extra(img, t, x0, y0)
        for s, text, yy in toasts:
            toast(img, text, yy, prog(t, s, 0.35))
        for s, tx, ty in taps:
            tap(img, tx, ty, prog(t, s, 0.5))
        top_caption(img, t, caps)
    return fn


def cta(line1='Это StarTegs ⭐', line2='Ссылка в профиле 👆', small='Android-мод Telegram · на iPhone — Mini App'):
    return lambda img, t, d: cta_scene(img, t, d, line1, line2, small)


# ---------- 1. Удалённые сообщения ----------
def v1():
    def deleted(t):
        if t >= 3.2:
            return dict(italic_note='🗑 удалено собеседником', meta='21:05')
        return {}

    chat = chat_scene('Аня', 'в сети', 'А', A3, [
        dict(t=0.5, text='Слушай, давно хотела сказать…', meta='21:04'),
        dict(t=1.4, text='Ты мне нравишься 🙈', meta='21:05', props=deleted),
        dict(t=4.6, text='Аня, я всё видел 😏', out=True, meta='21:06 ✓✓'),
        dict(t=5.6, text='КАК?! 😳 я же удалила', meta='21:06'),
    ], caps=[(0, 3.0, ['Она отправила…']), (3.0, 4.6, ['…и [удалила] 😬']), (4.6, 8, ['Но у меня [StarTegs] 😎'])],
        toasts=[(3.2, '🗑 Сообщение сохранено', 1500)])
    return [(1.7, hook(['Тебе [удалили]', 'сообщение?', 'Смотри 👇'])), (7.5, chat), (2.8, cta())]


# ---------- 2. Расшифровка голосовых ----------
def v2():
    def voice_extra(img, t, x0, y0):
        # голосовое сообщение-«волна»
        y = y0 + 230
        p = ease_out(prog(t, 0.5, 0.3))
        if p <= 0:
            return
        bw, bh = 560, 130
        layer = Image.new('RGBA', (bw, bh), (0, 0, 0, 0))
        d = ImageDraw.Draw(layer)
        d.rounded_rectangle((0, 0, bw - 1, bh - 1), 34, fill=(24, 37, 51))
        d.ellipse((24, 25, 104, 105), fill=A2)
        d.polygon([(54, 45), (54, 85), (86, 65)], fill=WHITE)
        rnd = random.Random(3)
        for i in range(34):
            hgt = rnd.randint(8, 60)
            xx = 130 + i * 11
            d.rounded_rectangle((xx, 65 - hgt // 2, xx + 6, 65 + hgt // 2), 3, fill=(120, 180, 240))
        d.text((bw - 120, 92), '5:47', font=font(26, False), fill=(140, 170, 200))
        layer.putalpha(layer.getchannel('A').point(lambda v: int(v * p)))
        img.alpha_composite(layer, (x0 + 30, y))
        # меню по долгому нажатию
        mp = ease_back(prog(t, 2.4, 0.3))
        if 2.4 <= t < 3.6 and mp > 0:
            menu = Image.new('RGBA', (470, 230), (0, 0, 0, 0))
            md = ImageDraw.Draw(menu)
            md.rounded_rectangle((0, 0, 469, 229), 30, fill=(40, 52, 68))
            draw_text(menu, (30, 26), '↩️ Ответить', 34, bold=False)
            md.rounded_rectangle((12, 88, 457, 150), 18, fill=A1 + (120,))
            draw_text(menu, (30, 94), '🎙 Расшифровать (ИИ)', 34)
            draw_text(menu, (30, 164), '⭐ В избранные', 34, bold=False)
            menu = menu.resize((max(1, int(470 * mp)), max(1, int(230 * mp))))
            img.alpha_composite(menu, (x0 + 200, y + 150))
        # текст расшифровки печатается
        if t >= 3.6:
            full = 'Короче, завтра встречаемся в 7 у метро, возьми паспорт и зарядку. Остальное расскажу на месте 🙌'
            n = int(len(full) * clamp((t - 3.6) / 1.6))
            bubble(img, x0 + 30, y + 160, full[:n] or ' ', meta='🎙 расшифровано за 2 сек' if n == len(full) else '',
                   meta_color=GOLD)

    chat = chat_scene('Макс', 'был(а) недавно', 'М', A1, [], extra=voice_extra,
                      caps=[(0, 2.4, ['Голосовое на [6 минут] 💀']), (2.4, 3.6, ['Зажми → [Расшифровать]']),
                            (3.6, 9, ['Прочитал за [5 секунд] ⚡'])],
                      taps=[(2.1, 560, 760)])
    return [(1.7, hook(['Ненавидишь', '[голосовые]?', '🎙❌'])), (7.8, chat), (2.8, cta())]


# ---------- 3. Скрытые чаты ----------
def v3():
    chats = [('Мама', 'Купи хлеба 🍞', A2), ('Работа 💼', 'Отчёт к пятнице', A1), ('Тайный ❤️', 'Скучаю…', A3),
             ('Брат', 'го в доту', (16, 185, 129)), ('Банк', 'Списание 499 ₽', (224, 154, 11))]

    def lst(img, t, x0, y0):
        hide_p = ease_out(prog(t, 2.2, 0.5))
        y = y0 + 200
        # строка поиска
        d = ImageDraw.Draw(img)
        d.rounded_rectangle((x0 + 30, y, x0 + PW - 30, y + 70), 35, fill=(33, 45, 59))
        typed = ''
        if t >= 3.6:
            word = 'пицца'
            typed = word[:int(len(word) * clamp((t - 3.6) / 0.8))]
        d.text((x0 + 70, y + 16), ('🔍 ' if False else '') + (typed or 'Поиск'), font=font(34, False),
               fill=WHITE if typed else (130, 150, 170))
        y += 100
        show_secret = t >= 4.8
        for i, (name, msg, col) in enumerate(chats):
            secret = name.startswith('Тайный')
            collapse = 1.0
            if secret and not show_secret:
                if hide_p >= 1:
                    continue
                offset = int(hide_p * 120)
                a = 1 - hide_p
                collapse = 1 - hide_p
            else:
                offset, a = 0, 1
            if show_secret and not secret:
                a = 0.25
            row = Image.new('RGBA', (PW - 40, 120), (0, 0, 0, 0))
            rd = ImageDraw.Draw(row)
            if secret and show_secret:
                rd.rounded_rectangle((0, 0, PW - 41, 119), 24, fill=A3 + (70,))
            rd.ellipse((16, 14, 108, 106), fill=col)
            rd.text((62 - font(40).getlength(name[0]) / 2, 34), name[0], font=font(40), fill=WHITE)
            draw_text(row, (130, 16), name, 36)
            draw_text(row, (130, 64), msg, 30, fill=(150, 170, 190), bold=False)
            if a < 1:
                row.putalpha(row.getchannel('A').point(lambda v: int(v * a)))
            if offset:
                row = row.crop((0, 0, row.width - offset, row.height))
            img.alpha_composite(row, (x0 + 20 + offset, int(y)))
            y += int(128 * collapse)

    chat = chat_scene('Чаты', '', '⭐', A1, [], extra=lst,
                      caps=[(0, 2.2, ['Есть [секретный] чат? 🤫']), (2.2, 3.6, ['Скрыл — и его [нет] 👻']),
                            (3.6, 4.8, ['Пишешь тайное слово…']), (4.8, 9, ['…и он [снова тут] 🔓'])],
                      toasts=[(2.3, '🙈 Чат скрыт', 1560), (4.8, '🔓 Открыто по отпечатку', 1560)])
    return [(1.7, hook(['Никто не найдёт', 'этот чат', '[даже если]', 'возьмёт телефон'], size=92)),
            (7.4, chat), (2.8, cta())]


# ---------- 4. ИИ: «Что я пропустил?» ----------
def v4():
    def extra(img, t, x0, y0):
        d = ImageDraw.Draw(img)
        # бейдж непрочитанных
        if t < 2.3:
            n = 99 + int(t * 180)
            s = f'{n} непрочитанных'
            w = font(34).getlength(s) + 60
            d.rounded_rectangle((x0 + (PW - w) / 2, y0 + 240, x0 + (PW + w) / 2, y0 + 310), 35, fill=A3)
            d.text((x0 + (PW - w) / 2 + 30, y0 + 254), s, font=font(34), fill=WHITE)
            rnd = random.Random(int(t * 10))
            for i in range(6):
                txt = rnd.choice(['ахахах', 'кто идёт?', 'скинь фото', '😂😂😂', 'а когда?', '+', 'я за', 'кто взял торт'])
                bubble(img, x0 + 30, y0 + 350 + i * 130, txt, meta='12:0' + str(i), alpha=0.8)
        if 1.8 <= t < 2.5:
            m = ease_back(prog(t, 1.8, 0.25))
            menu = Image.new('RGBA', (460, 160), (0, 0, 0, 0))
            md = ImageDraw.Draw(menu)
            md.rounded_rectangle((0, 0, 459, 159), 28, fill=(40, 52, 68))
            md.rounded_rectangle((10, 10, 449, 80), 18, fill=A1 + (130,))
            draw_text(menu, (26, 20), '🤖 Что я пропустил?', 34)
            draw_text(menu, (26, 96), '🔍 Поиск', 34, bold=False)
            menu = menu.resize((max(1, int(460 * m)), max(1, int(160 * m))))
            img.alpha_composite(menu, (x0 + PW - 490, y0 + 150))
        if t >= 2.5:
            lines = ['🤖 Кратко за день:', '• Встреча в субботу в 15:00 у Лёши', '• Ты скидываешь 500 ₽ на торт',
                     '• Катя спрашивала про тебя 👀', '• Остальное — мемы 😂']
            card = Image.new('RGBA', (PW - 60, 560), (0, 0, 0, 0))
            card.alpha_composite(gradient_rect(PW - 60, 560, (60, 40, 140), (30, 70, 140), 36))
            yy = 30
            for i, l in enumerate(lines):
                if t >= 2.6 + i * 0.45:
                    for wl in wrap(l, 36 if i else 40, PW - 130, bold=(i == 0)):
                        draw_text(card, (34, yy), wl, 36 if i else 40, bold=(i == 0), fill=GOLD if i == 0 else WHITE)
                        yy += 54
                    yy += 18
            p = ease_out(prog(t, 2.5, 0.3))
            img.alpha_composite(card, (x0 + 30, y0 + 230 + int((1 - p) * 300)))

    chat = chat_scene('Семья 🏡', '23 участника', 'С', (16, 185, 129), [], extra=extra,
                      caps=[(0, 1.8, ['[999+] сообщений 💀']), (1.8, 2.5, ['Жмёшь одну кнопку…']),
                            (2.5, 9, ['ИИ пересказал [всё] 🤯'])],
                      taps=[(1.5, 870, 520)])
    return [(1.7, hook(['Не читай', '[999 сообщений]', 'в чате 🙅'])), (7.6, chat), (2.8, cta())]


# ---------- 5. Режим невидимки ----------
def v5():
    def props_read(t):
        return dict(meta='19:40 ✓' if t > 1.0 else '19:40')

    def extra(img, t, x0, y0):
        d = ImageDraw.Draw(img)
        # статус в шапке
        d.rectangle((x0 + 200, y0 + 115, x0 + PW - 20, y0 + 155), fill=(33, 45, 59))
        st = 'был(а) давно 👻' if t > 0.8 else 'в сети'
        draw_text(img, (x0 + 200, y0 + 116), st, 28, fill=(140, 160, 180), bold=False)
        # тумблеры
        if t >= 3.4:
            p = ease_out(prog(t, 3.4, 0.35))
            panel = Image.new('RGBA', (PW - 60, 420), (0, 0, 0, 0))
            pd = ImageDraw.Draw(panel)
            pd.rounded_rectangle((0, 0, PW - 61, 419), 34, fill=(36, 30, 70))
            items = ['Не отправлять «прочитано»', 'Скрыть «в сети»', 'Скрыть «печатает…»', 'Истории тайно']
            for i, it in enumerate(items):
                yy = 30 + i * 96
                draw_text(panel, (30, yy + 12), it, 32, bold=False)
                on = t >= 3.6 + i * 0.3
                pd.rounded_rectangle((PW - 200, yy + 6, PW - 90, yy + 62), 28, fill=A3 if on else (80, 80, 100))
                cx = PW - 118 if on else PW - 172
                pd.ellipse((cx - 22, yy + 12, cx + 22, yy + 56), fill=WHITE)
            panel.putalpha(panel.getchannel('A').point(lambda v: int(v * p)))
            img.alpha_composite(panel, (x0 + 30, y0 + PH - 450 + int((1 - p) * 200)))

    chat = chat_scene('Босс 💼', '', 'Б', (224, 154, 11), [
        dict(t=0.4, text='Ты где? Срочно нужен отчёт', meta='19:40'),
        dict(t=1.2, text='Ау??', meta='19:41'),
        dict(t=1.9, text='Видно же что ты в сети!', meta='19:42'),
        dict(t=2.6, text='А, нет… не видно 🤔', meta='19:43'),
    ], extra=extra,
        caps=[(0, 1.9, ['Прочитал, а он [не знает] 👀']), (1.9, 3.4, ['Ни «в сети», ни [✓✓]']),
              (3.4, 9, ['Ты — [призрак] 👻'])])
    return [(1.7, hook(['Читай сообщения', '[незаметно] 🕶'])), (7.6, chat), (2.8, cta())]


# ---------- 6. Топ-5 фишек (листалка) ----------
def v6():
    items = [('🗑', 'Удалённые сообщения', 'остаются у тебя'), ('🎙', 'Голосовые → текст', 'нейросеть расшифрует'),
             ('🙈', 'Скрытые чаты', 'по отпечатку пальца'), ('🕶', 'Режим призрака', 'без «прочитано» и «в сети»'),
             ('🤖', 'ИИ в каждом чате', 'пересказ, перевод, ответы')]

    def card_scene(i):
        e, title, sub = items[i]

        def fn(img, t, d):
            num = f'#{5 - i}'
            p = ease_back(prog(t, 0, 0.3))
            f = font(int(220 * max(p, 0.01)))
            tw = f.getlength(num)
            ImageDraw.Draw(img).text(((W - tw) / 2, 330), num, font=f, fill=A3 + (230,))
            ep = ease_back(prog(t, 0.1, 0.35))
            if ep > 0:
                em = emoji_img(e, max(1, int(260 * ep)))
                img.alpha_composite(em, ((W - em.width) // 2, 640 + (260 - em.height) // 2))
            caption_block(img, [title], 980, t, 0.2, size=76)
            caption_block(img, [f'[{sub}]'], 1110, t, 0.35, size=54)
        return fn

    sc = [(1.6, hook(['5 фишек Telegram,', 'которых [нет]', 'в Telegram 🤯'], size=96))]
    sc += [(1.9, card_scene(i)) for i in range(5)]
    sc += [(2.8, cta('Всё это — StarTegs ⭐'))]
    return sc


VIDEOS = {1: ('01_udalennye', v1), 2: ('02_golosovye', v2), 3: ('03_skrytye_chaty', v3),
          4: ('04_ii_propustil', v4), 5: ('05_nevidimka', v5), 6: ('06_top5', v6)}

if __name__ == '__main__':
    os.makedirs(OUT, exist_ok=True)
    ids = [int(a) for a in sys.argv[1:]] or list(VIDEOS)
    for i in ids:
        name, f = VIDEOS[i]
        render(os.path.join(OUT, name + '.mp4'), f(), name)
