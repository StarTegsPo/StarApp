"""Вторая серия: 10 роликов (07–16). Запуск: python3 src/videos2.py [номер ...]"""
import os
import sys

sys.path.insert(0, os.path.dirname(__file__))
from engine import *  # noqa
from videos import OUT, chat_scene, cta, hook, top_caption  # noqa

GREEN = (16, 185, 129)
ORANGE = (224, 154, 11)


def msg(img, t, start, x0, y, text, out=False, **kw):
    """Пузырь с плавным появлением; возвращает занятую высоту (0, если ещё не появился)."""
    if t < start:
        return 0
    p = ease_out(prog(t, start, 0.25))
    a = kw.pop('alpha', 1.0) * p
    x = x0 + PW - 30 if out else x0 + 30
    h = bubble(img, x, y + int((1 - p) * 40), text, out=out, alpha=a, **kw)
    return h + 22


def pill(img, cx, cy, text, size=34, c1=A3, c2=A1, alpha=1.0, scale=1.0):
    tw = text_w(text, size)
    w, h = int(tw + 64), int(size * 2)
    layer = gradient_rect(w, h, c1, c2, h // 2)
    draw_text(layer, (32, int(size * 0.42)), text, size)
    if scale != 1.0:
        layer = layer.resize((max(1, int(w * scale)), max(1, int(h * scale))), Image.BILINEAR)
    if alpha < 1:
        layer.putalpha(layer.getchannel('A').point(lambda v: int(v * alpha)))
    img.alpha_composite(layer, (int(cx - layer.width / 2), int(cy - layer.height / 2)))
    return w, h


def panel(w, h, color=(36, 30, 70), radius=34):
    p = Image.new('RGBA', (w, h), (0, 0, 0, 0))
    ImageDraw.Draw(p).rounded_rectangle((0, 0, w - 1, h - 1), radius, fill=color)
    return p


def fade(layer, a):
    if a < 1:
        layer.putalpha(layer.getchannel('A').point(lambda v: int(v * a)))
    return layer


def chat_row(name, sub, col, w=PW - 40):
    row = Image.new('RGBA', (w, 120), (0, 0, 0, 0))
    rd = ImageDraw.Draw(row)
    rd.ellipse((16, 14, 108, 106), fill=col)
    letter = name[0]
    draw_text(row, (62 - text_w(letter, 40) / 2, 34), letter, 40)
    draw_text(row, (130, 16), name, 36)
    draw_text(row, (130, 64), sub, 30, fill=(150, 170, 190), bold=False)
    return row


# ---------- 07. Отмена отправки ----------
def v7():
    def extra(img, t, x0, y0):
        y = y0 + 210
        y += msg(img, t, 0.4, x0, y, 'Сдай отчёт до вечера', meta='18:02')
        if t >= 1.0:
            gone = ease_out(prog(t, 2.9, 0.35))
            if gone < 1:
                typed = 'Он такой душнила 🙄'
                bubble(img, x0 + PW - 30, y, typed, out=True, meta='отправка…', alpha=1 - gone)
                left = max(0, 5 - int((t - 1.0) * 1.6))
                pill(img, x0 + PW - 200, y + 175, f'↩️ Отменить · {left}', 30, alpha=1 - gone)
        y += 0 if t < 4.4 else 0
        if t >= 4.4:
            y += msg(img, t, 4.4, x0, y, 'Ты что-то писал? 🤨', meta='18:03')
            msg(img, t, 5.4, x0, y, 'Нет, показалось 😇', out=True, meta='18:03 ✓✓')
        tap(img, x0 + PW - 200, y0 + 210 + 130 + 175, prog(t, 2.6, 0.5))

    chat = chat_scene('Чат отдела 💼', '12 участников', 'Ч', ORANGE, [], extra=extra,
                      caps=[(0, 2.6, ['Отправил [не тому] 😱']), (2.6, 4.4, ['Жмёшь [Отменить]']),
                            (4.4, 9, ['Никто [не увидел] 😌'])],
                      toasts=[(3.0, '↩️ Отправка отменена', 1560)])
    return [(1.7, hook(['Отправил', 'сообщение', '[не тому]? 😱'])), (7.6, chat), (2.8, cta())]


# ---------- 08. Фальшивый код-пароль ----------
def v8():
    real = [('Тайный ❤️', 'Скучаю…', A3), ('Крипта 💰', '+340% 🚀', ORANGE), ('Мама', 'Купи хлеба 🍞', A2),
            ('Работа 💼', 'Отчёт к пятнице', A1), ('Друзья 🍻', 'В пятницу в бар?', GREEN)]
    clean = [('Мама', 'Купи хлеба 🍞', A2), ('Работа 💼', 'Отчёт к пятнице', A1)]

    def code_screen(img, t, x0, y0, code, start):
        d = ImageDraw.Draw(img)
        d.rounded_rectangle((x0, y0, x0 + PW, y0 + PH), 66, fill=(23, 33, 43))
        e = emoji_img('🔒', 120)
        img.alpha_composite(e, (x0 + (PW - e.width) // 2, y0 + 180))
        s = 'Введите код-пароль'
        draw_text(img, (x0 + (PW - text_w(s, 40)) / 2, y0 + 340), s, 40)
        n = clamp(int((t - start) / 0.25), 0, 4)
        for i in range(4):
            cx = x0 + PW / 2 + (i - 1.5) * 90
            fill = WHITE if i < n else None
            d.ellipse((cx - 22, y0 + 460, cx + 22, y0 + 504), outline=WHITE, width=4, fill=fill)
        keys = '123456789'
        for i, k in enumerate(keys):
            cx = x0 + PW / 2 + (i % 3 - 1) * 200
            cy = y0 + 640 + (i // 3) * 170
            pressed = k in code and n > code.index(k) and (t - start - code.index(k) * 0.25) < 0.2
            d.ellipse((cx - 70, cy - 70, cx + 70, cy + 70), fill=(80, 90, 120) if pressed else (40, 52, 68))
            d.text((cx - font(56).getlength(k) / 2, cy - 36), k, font=font(56), fill=WHITE)
        return n == 4

    def list_screen(img, t, x0, y0, rows, start):
        d = ImageDraw.Draw(img)
        d.rounded_rectangle((x0, y0, x0 + PW, y0 + PH), 66, fill=(23, 33, 43))
        draw_text(img, (x0 + 50, y0 + 70), 'Чаты', 48)
        for i, (n, s, c) in enumerate(rows):
            p = ease_out(prog(t, start + i * 0.08, 0.25))
            img.alpha_composite(fade(chat_row(n, s, c), p), (x0 + 20, y0 + 180 + i * 128 + int((1 - p) * 30)))

    def fn(img, t, d):
        x0, y0 = PX0, PY0
        shadow(img, (x0, y0, x0 + PW, y0 + PH), 70, blur=40, alpha=160)
        ImageDraw.Draw(img).rounded_rectangle((x0 - 14, y0 - 14, x0 + PW + 14, y0 + PH + 14), 78, fill=(8, 6, 18))
        if t < 1.4:
            code_screen(img, t, x0, y0, '1793', 0.2)
        elif t < 3.4:
            list_screen(img, t, x0, y0, real, 1.4)
        elif t < 4.9:
            code_screen(img, t, x0, y0, '2580', 3.6)
        else:
            list_screen(img, t, x0, y0, clean, 4.9)
        top_caption(img, t, [(0, 3.4, ['Свой код — [всё] на месте']), (3.4, 4.9, ['Просят телефон? Вводишь [второй]']),
                             (4.9, 9, ['Чисто, [ничего лишнего] 😇'])])
    return [(1.7, hook(['Пароль, который', 'открывает', '[пустой] Telegram 🎭'], size=96)), (7.6, fn), (2.8, cta())]


# ---------- 09. Статистика переписки ----------
def v9():
    def extra(img, t, x0, y0):
        d = ImageDraw.Draw(img)
        y = y0 + 220
        draw_text(img, (x0 + 40, y), '📊 Статистика переписки', 38)
        y += 90
        # доля сообщений
        p = ease_out(prog(t, 0.5, 1.0))
        for i, (who, val, col) in enumerate([('Ты', 0.67, A3), ('Аня', 0.33, A2)]):
            yy = y + i * 110
            draw_text(img, (x0 + 40, yy), who, 34)
            d.rounded_rectangle((x0 + 170, yy + 4, x0 + PW - 40, yy + 52), 24, fill=(40, 52, 68))
            w = int((PW - 210) * val * p)
            if w > 30:
                img.alpha_composite(gradient_rect(w, 48, col, A1, 24), (x0 + 170, yy + 4))
            s = f'{int(val * 100 * p)}%'
            draw_text(img, (x0 + 190, yy + 8), s, 30)
        y += 250
        # активность по часам
        draw_text(img, (x0 + 40, y), 'Активность по часам', 30, fill=(150, 170, 190), bold=False)
        y += 60
        vals = [2, 1, 1, 0, 0, 0, 1, 2, 3, 4, 3, 4, 5, 4, 3, 4, 5, 6, 7, 8, 9, 10, 12, 14]
        hp = ease_out(prog(t, 1.4, 1.0))
        for i, v in enumerate(vals):
            bh = int(v * 14 * hp)
            xx = x0 + 50 + i * 28
            c = A3 if i >= 22 else A2
            d.rounded_rectangle((xx, y + 200 - bh, xx + 18, y + 200), 6, fill=c)
        y += 240
        if t >= 3.0:
            draw_text(img, (x0 + 40, y), 'Частые слова', 30, fill=(150, 170, 190), bold=False)
            for i, wrd in enumerate(['люблю', 'ахахах', 'спишь?', 'кушать']):
                if t >= 3.2 + i * 0.25:
                    pill(img, x0 + 150 + (i % 2) * 330, y + 90 + (i // 2) * 90, wrd, 30)

    chat = chat_scene('Аня ❤️', 'статистика', 'А', A3, [], extra=extra,
                      caps=[(0, 1.4, ['Кто пишет [больше]?']), (1.4, 3.0, ['Ты пишешь [в 2 раза] больше 💀']),
                            (3.0, 9, ['И даже [о чём] 👀'])])
    return [(1.7, hook(['Кто из вас', 'пишет [больше]? 📊'])), (7.6, chat), (2.8, cta())]


# ---------- 10. Проверка тона ----------
def v10():
    def extra(img, t, x0, y0):
        y = y0 + 210
        y += msg(img, t, 0.4, x0, y, 'Я не видела твоё сообщение', meta='20:11')
        # поле ввода
        d = ImageDraw.Draw(img)
        fy = y0 + PH - 130
        d.rounded_rectangle((x0 + 24, fy, x0 + PW - 24, fy + 100), 50, fill=(33, 45, 59))
        rude = 'Ты вообще читаешь, что я пишу??'
        soft = 'Кажется, моё сообщение потерялось 🙂'
        if t < 1.0:
            cur = ''
        elif t < 3.6:
            cur = rude[:int(len(rude) * clamp((t - 1.0) / 1.0))]
        else:
            cur = soft
        if t < 4.4:
            col = (255, 140, 160) if 2.0 <= t < 3.6 else WHITE
            draw_text(img, (x0 + 60, fy + 30), cur if text_w(cur, 30, False) < PW - 140 else cur[:28] + '…', 30,
                      fill=col, bold=False)
        if 2.0 <= t < 3.6:
            p = ease_back(prog(t, 2.0, 0.3))
            pn = panel(PW - 60, 230, (60, 30, 50))
            draw_text(pn, (30, 26), '😬 Звучит резковато', 36)
            draw_text(pn, (30, 80), 'Сказать мягче?', 30, fill=(220, 200, 210), bold=False)
            pn.alpha_composite(gradient_rect(260, 64, A3, A1, 32), (30, 140))
            draw_text(pn, (60, 152), '✨ Смягчить', 32)
            pn = pn.resize((max(1, int(pn.width * (0.6 + 0.4 * p))), max(1, int(pn.height * (0.6 + 0.4 * p)))))
            img.alpha_composite(fade(pn, clamp(p)), (x0 + 30, fy - 260))
        tap(img, x0 + 190, fy - 260 + 172, prog(t, 3.3, 0.5))
        if t >= 4.4:
            y += msg(img, t, 4.4, x0, y, soft, out=True, meta='20:12 ✓✓')
            msg(img, t, 5.4, x0, y, 'Ой, прости! Сейчас прочитаю 🙏', meta='20:12')

    chat = chat_scene('Лена', 'в сети', 'Л', GREEN, [], extra=extra,
                      caps=[(0, 2.0, ['Пишешь [на эмоциях] 😤']), (2.0, 3.6, ['ИИ: [«звучит резко»]']),
                            (3.6, 9, ['Ссоры [не случилось] 🕊'])])
    return [(1.7, hook(['Нейросеть', 'не даст тебе', '[нагрубить] 😬'])), (7.6, chat), (2.8, cta())]


# ---------- 11. 10 аккаунтов ----------
def v11():
    names = ['Основной', 'Работа', 'Магазин', 'Канал', 'Бизнес', 'Учёба', 'Игры', 'Крипта', 'Блог', 'Тайный']
    cols = [A1, ORANGE, A3, A2, GREEN, (14, 116, 144), (209, 43, 255), (255, 122, 69), (232, 64, 127), (100, 100, 130)]

    def extra(img, t, x0, y0):
        d = ImageDraw.Draw(img)
        n = 3 if t < 2.0 else min(10, 3 + int((t - 2.0) / 0.3) + 1)
        for i in range(n):
            col, row = i % 2, i // 2
            cx = x0 + 60 + col * 360
            cy = y0 + 210 + row * 172
            p = ease_back(prog(t, 0 if i < 3 else 2.0 + (i - 3) * 0.3, 0.3))
            card = panel(320, 160, (36, 46, 62), 28)
            cd = ImageDraw.Draw(card)
            cd.ellipse((20, 36, 108, 124), fill=cols[i])
            draw_text(card, (64 - text_w(names[i][0], 40) / 2, 52), names[i][0], 40)
            draw_text(card, (124, 60), names[i], 30)
            s = 0.5 + 0.5 * p
            card = card.resize((max(1, int(320 * s)), max(1, int(160 * s))))
            img.alpha_composite(fade(card, clamp(p)), (int(cx + (320 - card.width) / 2), int(cy + (160 - card.height) / 2)))
        big = f'{n}/10'
        d.text((x0 + PW / 2 - font(64).getlength(big) / 2, y0 + PH - 105), big, font=font(64),
               fill=A3 if n == 10 else WHITE)

    chat = chat_scene('Аккаунты', '', '👥', A1, [], extra=extra,
                      caps=[(0, 2.0, ['В обычном — [3] 😐']), (2.0, 9, ['В StarTegs — [10] 🔥'])])
    return [(1.7, hook(['[10 аккаунтов]', 'в Telegram', 'без Premium 👥'])), (7.6, chat), (2.8, cta())]


# ---------- 12. Итоги года ----------
def v12():
    cards = [('✍️', 'Ты написал', '48 213', 'сообщений'), ('❤️', 'Главный собеседник', 'Аня', '9 412 сообщений'),
             ('😂', 'Эмодзи года', '😂', '3 108 раз'), ('🦉', 'Любимое время', '01:47', 'ночная сова')]
    palettes = [(A1, A2), (A3, A1), ((255, 122, 69), (232, 64, 127)), ((16, 185, 129), (14, 116, 144))]

    def card(i):
        e, title, big, sub = cards[i]

        def fn(img, t, d):
            p = ease_back(prog(t, 0, 0.35))
            w, h = 820, 1120
            c = gradient_rect(w, h, *palettes[i], radius=60)
            em = emoji_img(e, 150)
            c.alpha_composite(em, ((w - em.width) // 2, 110))
            draw_text(c, ((w - text_w(title, 50)) / 2, 330), title, 50)
            bs = 150 if not is_emoji(big) else 170
            if is_emoji(big):
                eb = emoji_img(big, 220)
                c.alpha_composite(eb, ((w - eb.width) // 2, 460))
            else:
                bw = text_w(big, bs)
                while bw > w - 80:
                    bs -= 10
                    bw = text_w(big, bs)
                draw_text(c, ((w - bw) / 2, 480), big, bs)
            draw_text(c, ((w - text_w(sub, 46, False)) / 2, 760), sub, 46, bold=False)
            draw_text(c, ((w - text_w('⭐ StarTegs · 2026', 34)) / 2, 1010), '⭐ StarTegs · 2026', 34,
                      fill=(255, 255, 255))
            s = 0.7 + 0.3 * p
            c = c.resize((max(1, int(w * s)), max(1, int(h * s))))
            shadow(img, ((W - c.width) // 2, 420, (W + c.width) // 2, 420 + c.height), 60, alpha=150)
            img.alpha_composite(fade(c, clamp(p * 1.5)), ((W - c.width) // 2, 420))
            caption_block(img, [f'Итоги года [{i + 1}/4]'], 190, t, 0, size=64)
        return fn

    return [(1.7, hook(['Твой Telegram', 'за [2026] год 🎁']))] + [(1.9, card(i)) for i in range(4)] + [(2.8, cta())]


# ---------- 13. Автоответ ИИ ----------
def v13():
    chat = chat_scene('Дима', 'в сети', 'Д', A2, [
        dict(t=0.5, text='Привет! Ты завтра свободен?', meta='02:14'),
        dict(t=2.0, text='Привет! Сейчас не могу ответить, напишу утром 🙌', out=True, meta='🤖 автоответ ИИ · 02:14'),
        dict(t=4.0, text='Ок, договорились 👍', meta='02:15'),
    ], caps=[(0, 2.0, ['Тебе пишут [ночью] 🌙']), (2.0, 4.0, ['ИИ отвечает [сам] 🤖']), (4.0, 9, ['А ты [спишь] 😴'])],
        toasts=[(5.0, '😴 Автоответ включён', 1560)])
    return [(1.7, hook(['ИИ отвечает', '[за тебя] 🤖'])), (7.6, chat), (2.8, cta())]


# ---------- 14. Перевод ----------
def v14():
    es = '¡Hola! ¿Mañana vamos a la playa? 🏖'
    ru = 'Привет! Завтра идём на пляж? 🏖'

    def extra(img, t, x0, y0):
        y = y0 + 210
        txt = es if t < 2.6 else ru
        h = msg(img, t, 0.4, x0, y, txt, meta='🌍 переведено ИИ' if t >= 2.6 else '15:20',
                meta_color=GOLD if t >= 2.6 else (140, 170, 200))
        if 1.4 <= t < 2.6:
            pill(img, x0 + 230, y + h + 40, '🌍 Перевести', 32, scale=0.7 + 0.3 * ease_back(prog(t, 1.4, 0.3)))
        tap(img, x0 + 230, y + 150 + 40, prog(t, 2.2, 0.5))
        y += h + 30 if h else 0
        if t >= 4.0:
            y += msg(img, t, 4.0, x0, y, 'Да! Во сколько?', out=True, meta='15:21 ✓✓')
            msg(img, t, 4.8, x0, y, '¡Sí! ¿A qué hora? 🙌', out=True, meta='→ отправлено на испанском',
                meta_color=GOLD)

    chat = chat_scene('Lucía', 'в сети', 'L', ORANGE, [], extra=extra,
                      caps=[(0, 2.6, ['Пишут [на испанском]?']), (2.6, 4.0, ['Одно нажатие — [по-русски]']),
                            (4.0, 9, ['И ответ [туда же] 🌍'])])
    return [(1.7, hook(['Друг пишет', 'на [другом языке]? 🌍'])), (7.6, chat), (2.8, cta())]


# ---------- 15. Напоминания из переписки ----------
def v15():
    def extra(img, t, x0, y0):
        y = y0 + 210
        y += msg(img, t, 0.4, x0, y, 'Давай завтра в 7 у кино? 🎬', meta='13:05')
        y += msg(img, t, 1.2, x0, y, 'Давай! 🔥', out=True, meta='13:05 ✓✓')
        if t >= 2.2:
            s = 0.7 + 0.3 * ease_back(prog(t, 2.2, 0.3))
            done = t >= 3.6
            pill(img, x0 + PW / 2, y + 50, '✅ Напомню завтра в 18:30' if done else '⏰ Напомнить в 18:30', 32,
                 c1=GREEN if done else A3, c2=(14, 116, 144) if done else A1, scale=s)
        tap(img, x0 + PW / 2, y0 + 210 + 300 + 50, prog(t, 3.2, 0.5))
        if t >= 5.0:
            p = ease_back(prog(t, 5.0, 0.35))
            n = panel(PW - 40, 150, (40, 52, 68), 30)
            draw_text(n, (30, 24), '⏰ Завтра, 18:30', 34)
            draw_text(n, (30, 80), 'Кино с Катей через 30 минут', 30, fill=(200, 210, 220), bold=False)
            img.alpha_composite(fade(n, clamp(p)), (x0 + 20, y0 + 20 - int((1 - p) * 100) if p < 1 else y0 + 20))

    chat = chat_scene('Катя', 'в сети', 'К', A3, [], extra=extra,
                      caps=[(0, 2.2, ['Договорились [в переписке]']), (2.2, 5.0, ['Мод [сам] предлагает напомнить']),
                            (5.0, 9, ['И ты [не забудешь] ⏰'])])
    return [(1.7, hook(['Вечно [забываешь],', 'о чём', 'договорился? 🤦'])), (7.6, chat), (2.8, cta())]


# ---------- 16. Обычный Telegram vs StarTegs ----------
def v16():
    rows = [('Удалили сообщение', 'пропало', 'осталось'), ('Голосовое 6 минут', 'слушай', 'читай текстом'),
            ('Аккаунты', 'до 3', 'до 10'), ('Перевод чатов', 'Premium', 'бесплатно ИИ'),
            ('«Прочитано»', 'всегда', 'по желанию')]

    def fn(img, t, d):
        y = 420
        hp = ease_out(prog(t, 0, 0.3))
        hdr = Image.new('RGBA', (W, 100), (0, 0, 0, 0))
        draw_text(hdr, (300 - text_w('Telegram', 44) / 2, 20), 'Telegram', 44, fill=(170, 180, 200))
        draw_text(hdr, (780 - text_w('StarTegs ⭐', 44) / 2, 20), 'StarTegs ⭐', 44, fill=GOLD)
        img.alpha_composite(fade(hdr, hp), (0, y))
        y += 120
        for i, (what, bad, good) in enumerate(rows):
            p = ease_back(prog(t, 0.6 + i * 1.3, 0.35))
            if p <= 0:
                continue
            row = Image.new('RGBA', (W - 80, 200), (0, 0, 0, 0))
            draw_text(row, ((W - 80 - text_w(what, 38)) / 2, 0), what, 38, fill=(220, 220, 240))
            l = panel(440, 110, (60, 40, 60), 28)
            draw_text(l, ((440 - text_w('❌ ' + bad, 34)) / 2, 34), '❌ ' + bad, 34)
            r = Image.new('RGBA', (440, 110), (0, 0, 0, 0))
            r.alpha_composite(gradient_rect(440, 110, A1, A3, 28))
            draw_text(r, ((440 - text_w('✅ ' + good, 34)) / 2, 34), '✅ ' + good, 34)
            row.alpha_composite(l, (20, 60))
            row.alpha_composite(r, (500, 60))
            s = 0.7 + 0.3 * p
            row = row.resize((max(1, int(row.width * s)), max(1, int(row.height * s))))
            img.alpha_composite(fade(row, clamp(p)), ((W - row.width) // 2, y + i * 200))
        caption_block(img, ['Сравним? 👀'], 190, t, 0, size=72)
    return [(1.7, hook(['Обычный Telegram', 'vs [StarTegs] ⭐'], size=96)), (7.6, fn), (2.8, cta())]


VIDEOS2 = {7: ('07_otmena', v7), 8: ('08_falshivyi_parol', v8), 9: ('09_statistika', v9), 10: ('10_ton', v10),
           11: ('11_10_akkauntov', v11), 12: ('12_itogi_goda', v12), 13: ('13_avtootvet', v13),
           14: ('14_perevod', v14), 15: ('15_napominanie', v15), 16: ('16_vs', v16)}

if __name__ == '__main__':
    os.makedirs(OUT, exist_ok=True)
    for i in [int(a) for a in sys.argv[1:]] or list(VIDEOS2):
        name, f = VIDEOS2[i]
        render(os.path.join(OUT, name + '.mp4'), f(), name)
