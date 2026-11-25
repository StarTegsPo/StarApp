#!/usr/bin/env python3
"""Бот продажи курса «Нейросети на практике».

/start → обложка и описание → «Купить» → реквизиты карты → «Я оплатил» →
клиент присылает скриншот чека → админу приходит чек с кнопками
«Подтвердить / Отклонить» → после подтверждения бот сам отправляет курс (PDF).
Плюс переписка: клиент пишет вопрос — админ отвечает реплаем.

Без внешних зависимостей: только стандартная библиотека Python 3.8+.
Рядом с файлом должны лежать course.pdf и cover.jpg.
Настройки — в .env рядом с этим файлом:
    BOT_TOKEN=123456:ABC...
    ADMIN_ID=7383755594       # необязательно, по умолчанию — значение ниже
    CARD=2200700529968603     # необязательно
    PRICE=149                 # необязательно
"""
import html
import json
import os
import sys
import time
import urllib.error
import urllib.request
import uuid

HERE = os.path.dirname(os.path.abspath(__file__))


def load_env(path):
    if not os.path.exists(path):
        return
    with open(path, encoding="utf-8") as f:
        for line in f:
            line = line.strip()
            if not line or line.startswith("#") or "=" not in line:
                continue
            k, v = line.split("=", 1)
            os.environ.setdefault(k.strip(), v.strip().strip('"').strip("'"))


load_env(os.path.join(HERE, ".env"))

TOKEN = os.getenv("BOT_TOKEN", "").strip()
ADMIN_ID = int(os.getenv("ADMIN_ID", "") or 7383755594)
CARD = "".join(ch for ch in os.getenv("CARD", "2200700529968603") if ch.isdigit())
PRICE = int(os.getenv("PRICE", "") or 149)
DATA_FILE = os.getenv("DATA_FILE", os.path.join(HERE, "coursebot_data.json"))
COURSE_FILE = os.path.join(HERE, "course.pdf")
COURSE_NAME = "Нейросети_на_практике.pdf"
COVER_FILE = os.path.join(HERE, "cover.jpg")
API_URL = os.getenv("API_URL", "https://api.telegram.org").rstrip("/")
PROXY = os.getenv("PROXY", "").strip()

CARD_PRETTY = " ".join(CARD[i:i + 4] for i in range(0, len(CARD), 4))
e = html.escape

# ---------- тексты ----------

WELCOME = (
    "👋 Привет, <b>{name}</b>!\n\n"
    "🧠 <b>Нейросети на практике</b> — курс, после которого нейросети станут "
    "твоим ежедневным инструментом: в учёбе, работе и заработке.\n\n"
    "📘 <b>Что ты получишь:</b>\n"
    "• 8 модулей без воды и сложных терминов\n"
    "• Формулу промпта, который работает с первого раза\n"
    "• 40+ готовых промптов — просто копируй\n"
    "• Картинки, голос и видео с помощью ИИ\n"
    "• 6 услуг, на которых можно зарабатывать\n"
    "• План практики на 7 дней и чек-лист\n\n"
    "💰 Цена: <b>{price}₽</b>\n"
    "📥 Курс приходит сюда в PDF сразу после подтверждения оплаты."
)

PROGRAM = (
    "📖 <b>Программа курса</b>\n\n"
    "<b>01 · Как устроены нейросети</b>\nЧто они умеют, а что — нет. Виды нейросетей.\n\n"
    "<b>02 · Инструменты</b>\nКакие нейросети выбрать, в том числе доступные в России.\n\n"
    "<b>03 · Промпт-формула</b>\n6 элементов запроса: роль, контекст, задача, формат, ограничения, пример.\n\n"
    "<b>04 · Продвинутые приёмы</b>\nУточняющие вопросы, варианты, самопроверка, итерации.\n\n"
    "<b>05 · Библиотека промптов</b>\n40+ шаблонов: учёба, работа, тексты, соцсети, бизнес.\n\n"
    "<b>06 · Картинки, голос и видео</b>\nФормула промпта для изображений.\n\n"
    "<b>07 · Заработок на нейросетях</b>\nУслуги, цены и где искать первых клиентов.\n\n"
    "<b>08 · Безопасность и ошибки</b>\nГаллюцинации, личные данные, топ-7 ошибок.\n\n"
    "⭐ <b>Бонус:</b> план практики на 7 дней и чек-лист.\n\n"
    "📄 14 страниц PDF · навсегда у тебя"
)

PAY = (
    "💳 <b>Оплата курса</b>\n\n"
    "Сумма: <b>{price}₽</b>\n"
    "Карта: <code>{card}</code>\n"
    "<i>(нажми на номер, чтобы скопировать)</i>\n\n"
    "1️⃣ Переведи <b>{price}₽</b> на карту\n"
    "2️⃣ Нажми «✅ Я оплатил»\n"
    "3️⃣ Пришли скриншот чека\n\n"
    "После проверки курс придёт сюда автоматически ⚡"
)

# ---------- хранилище ----------


def load_data():
    try:
        with open(DATA_FILE, encoding="utf-8") as f:
            d = json.load(f)
    except (OSError, ValueError):
        d = {}
    d.setdefault("users", {})    # user_id -> {"state", "paid", "name"}
    d.setdefault("links", {})    # id сообщения у админа -> user_id
    d.setdefault("orders", {})   # номер -> {"uid", "status", "header"}
    d.setdefault("n", 0)
    d.setdefault("file_ids", {})
    return d


DATA = load_data()


def save():
    if len(DATA["links"]) > 5000:
        keys = sorted(DATA["links"], key=int)[-5000:]
        DATA["links"] = {k: DATA["links"][k] for k in keys}
    tmp = DATA_FILE + ".tmp"
    with open(tmp, "w", encoding="utf-8") as f:
        json.dump(DATA, f, ensure_ascii=False)
    os.replace(tmp, DATA_FILE)


def user(uid):
    return DATA["users"].setdefault(str(uid), {"state": None, "paid": False})


# ---------- сеть ----------

# На мобильном интернете IPv6 до Telegram часто рвётся (Errno 103) — ходим только по IPv4.
if os.getenv("FORCE_IPV4", "1") != "0":
    import socket
    _orig_gai = socket.getaddrinfo

    def _gai_v4(host, *a, **kw):
        res = _orig_gai(host, *a, **kw)
        return [r for r in res if r[0] == socket.AF_INET] or res
    socket.getaddrinfo = _gai_v4

_opener = urllib.request.build_opener(
    *([urllib.request.ProxyHandler({"http": PROXY, "https": PROXY})] if PROXY else []))


class NetError(Exception):
    pass


def _request(method, body, ctype, http_timeout=30, tries=3):
    url = "%s/bot%s/%s" % (API_URL, TOKEN, method)
    last = None
    for attempt in range(tries):
        req = urllib.request.Request(url, body, {"Content-Type": ctype})
        try:
            with _opener.open(req, timeout=http_timeout) as r:
                res = json.load(r)
            break
        except urllib.error.HTTPError as err:
            try:
                res = json.load(err)
            except ValueError:
                res = {"ok": False, "description": str(err)}
            break
        except (urllib.error.URLError, OSError, ValueError) as err:
            last = err
            time.sleep(1 + attempt)
    else:
        raise NetError(str(getattr(last, "reason", last)))
    if not res.get("ok"):
        print("API %s: %s" % (method, res.get("description")), file=sys.stderr)
        return None
    return res.get("result")


def api(method, _http_timeout=30, _tries=3, **params):
    return _request(method, json.dumps(params).encode(), "application/json",
                    _http_timeout, _tries)


def upload(method, field, path, filename, **params):
    """Отправка файла (multipart/form-data)."""
    b = uuid.uuid4().hex
    parts = []
    for k, v in params.items():
        if isinstance(v, (dict, list)):
            v = json.dumps(v)
        parts.append(('--%s\r\nContent-Disposition: form-data; name="%s"\r\n\r\n%s\r\n'
                      % (b, k, v)).encode())
    with open(path, "rb") as f:
        data = f.read()
    parts.append(('--%s\r\nContent-Disposition: form-data; name="%s"; filename="%s"\r\n'
                  'Content-Type: application/octet-stream\r\n\r\n' % (b, field, filename)).encode()
                 + data + b"\r\n")
    parts.append(("--%s--\r\n" % b).encode())
    return _request(method, b"".join(parts), "multipart/form-data; boundary=" + b, 120, 2)


def send_file(kind, chat_id, key, path, filename, **params):
    """sendPhoto/sendDocument: один раз загружает файл, дальше шлёт по file_id."""
    method = "send" + kind.capitalize()
    fid = DATA["file_ids"].get(key)
    if fid:
        r = api(method, chat_id=chat_id, **{kind: fid}, **params)
        if r:
            return r
    if not os.path.exists(path):
        print("Нет файла %s" % path, file=sys.stderr)
        return None
    r = upload(method, kind, path, filename, chat_id=chat_id, **params)
    if r:
        obj = r.get(kind)
        if isinstance(obj, list):
            obj = obj[-1]
        if obj and obj.get("file_id"):
            DATA["file_ids"][key] = obj["file_id"]
            save()
    return r


def kb(rows):
    return {"inline_keyboard": [[{"text": t, "callback_data": d} for t, d in row] for row in rows]}


def send(chat_id, text, markup=None):
    p = {"chat_id": chat_id, "text": text, "parse_mode": "HTML",
         "link_preview_options": {"is_disabled": True}}
    if markup:
        p["reply_markup"] = markup
    return api("sendMessage", **p)


def name_of(u):
    n = (u.get("first_name", "") + " " + u.get("last_name", "")).strip() or "Без имени"
    if u.get("username"):
        n += " (@%s)" % u["username"]
    return n


# ---------- экраны ----------

def menu_kb(uid):
    if user(uid).get("paid"):
        return kb([[("📥 Скачать курс", "get")],
                   [("📖 Программа", "program"), ("💬 Вопрос", "ask")]])
    return kb([[("💳 Купить за %d₽" % PRICE, "buy")],
               [("📖 Программа", "program"), ("💬 Вопрос", "ask")]])


def show_menu(frm):
    uid = frm["id"]
    text = WELCOME.format(name=e(frm.get("first_name") or "друг"), price=PRICE)
    if user(uid).get("paid"):
        text += "\n\n✅ <b>Курс уже твой!</b> Нажми «Скачать курс», чтобы получить файл."
    r = send_file("photo", uid, "cover", COVER_FILE, "cover.jpg",
                  caption=text, parse_mode="HTML", reply_markup=menu_kb(uid))
    if not r:  # нет картинки — просто текст
        send(uid, text, menu_kb(uid))


def give_course(uid):
    return send_file("document", uid, "course", COURSE_FILE, COURSE_NAME,
                     caption="📘 <b>Нейросети на практике</b>\n\n"
                             "Открой рядом любую нейросеть и повторяй примеры — так навык "
                             "появится быстрее всего. Удачи! 🚀",
                     parse_mode="HTML")


# ---------- админ ----------

def to_admin(frm, header, msg=None, markup=None):
    """Пересылает админу сообщение клиента и запоминает, кому отвечать."""
    uid = frm["id"]
    if msg is not None and (msg.get("photo") or msg.get("document")) and markup:
        r = api("copyMessage", chat_id=ADMIN_ID, from_chat_id=uid, message_id=msg["message_id"],
                caption=header, parse_mode="HTML", reply_markup=markup)
        if r:
            DATA["links"][str(r["message_id"])] = uid
            save()
            return r
    r = send(ADMIN_ID, header, markup)
    if r:
        DATA["links"][str(r["message_id"])] = uid
    if msg is not None:
        c = api("copyMessage", chat_id=ADMIN_ID, from_chat_id=uid, message_id=msg["message_id"])
        if c:
            DATA["links"][str(c["message_id"])] = uid
    save()
    return r


def stats_text():
    paid = [o for o in DATA["orders"].values() if o["status"] == "ok"]
    wait = [o for o in DATA["orders"].values() if o["status"] == "wait"]
    return ("📊 <b>Статистика</b>\n\n👥 Пользователей: %d\n✅ Продаж: %d\n💰 Выручка: %d₽\n⏳ Ждут проверки: %d"
            % (len(DATA["users"]), len(paid), len(paid) * PRICE, len(wait)))


ADMIN_HELP = (
    "👑 <b>Панель админа</b>\n\n"
    "• Чеки об оплате приходят сюда с кнопками <b>Подтвердить / Отклонить</b>. "
    "После подтверждения курс уходит клиенту сам.\n"
    "• Чтобы ответить клиенту — ответь (Reply / свайп) на его сообщение.\n\n"
    "Команды:\n"
    "/stats — статистика\n"
    "/give &lt;id&gt; — выдать курс вручную\n"
    "/to &lt;id&gt; &lt;текст&gt; — написать клиенту\n"
    "/test — посмотреть бота глазами клиента"
)


def handle_admin(msg):
    text = msg.get("text", "")
    if text.startswith("/start"):
        send(ADMIN_ID, ADMIN_HELP + "\n\n" + stats_text())
        return
    if text.startswith("/stats"):
        send(ADMIN_ID, stats_text())
        return
    if text.startswith("/test"):
        show_menu(msg["from"])
        return
    if text.startswith("/give"):
        parts = text.split()
        if len(parts) < 2 or not parts[1].isdigit():
            send(ADMIN_ID, "Формат: /give &lt;id&gt;")
            return
        uid = int(parts[1])
        if give_course(uid):
            user(uid)["paid"] = True
            save()
            send(ADMIN_ID, "✅ Курс отправлен")
        else:
            send(ADMIN_ID, "❌ Не удалось отправить (клиент не запускал бота?)")
        return
    if text.startswith("/to "):
        parts = text.split(maxsplit=2)
        if len(parts) < 3 or not parts[1].isdigit():
            send(ADMIN_ID, "Формат: /to &lt;id&gt; &lt;текст&gt;")
            return
        r = send(int(parts[1]), "💬 <b>Ответ поддержки:</b>\n" + e(parts[2]))
        send(ADMIN_ID, "✅ Отправлено" if r else "❌ Не удалось отправить")
        return
    reply = msg.get("reply_to_message")
    target = DATA["links"].get(str(reply["message_id"])) if reply else None
    if not target:
        send(ADMIN_ID, "Чтобы ответить клиенту — ответь (Reply) на его сообщение.\n/start — панель админа")
        return
    if text:
        r = send(target, "💬 <b>Ответ поддержки:</b>\n" + e(text))
    else:
        r = api("copyMessage", chat_id=target, from_chat_id=ADMIN_ID, message_id=msg["message_id"])
    if r:
        DATA["links"][str(msg["message_id"])] = target
        save()
    else:
        send(ADMIN_ID, "❌ Не доставлено (клиент заблокировал бота?)")


def decide(cb, n, approve):
    o = DATA["orders"].get(n)
    msg = cb["message"]
    if not o:
        api("answerCallbackQuery", callback_query_id=cb["id"], text="Заказ не найден")
        return
    if o["status"] != "wait":
        api("answerCallbackQuery", callback_query_id=cb["id"], text="Уже обработан")
        return
    uid = o["uid"]
    if approve:
        o["status"] = "ok"
        user(uid)["paid"] = True
        user(uid)["state"] = None
        save()
        send(uid, "🎉 <b>Оплата подтверждена!</b>\n\nСпасибо за покупку ❤️ Лови курс 👇")
        ok = give_course(uid)
        mark = "✅ Подтверждено, курс отправлен" if ok else "⚠️ Подтверждено, но файл не ушёл — /give %d" % uid
        api("answerCallbackQuery", callback_query_id=cb["id"], text="Курс отправлен ✅")
    else:
        o["status"] = "no"
        user(uid)["state"] = None
        save()
        send(uid, "❌ <b>Оплата не найдена.</b>\n\nПроверь сумму (<b>%d₽</b>) и номер карты, затем "
                  "пришли чек ещё раз. Если уверен, что всё верно — напиши нам через «💬 Вопрос»."
             % PRICE, kb([[("💳 Оплатить ещё раз", "buy")], [("💬 Вопрос", "ask")]]))
        mark = "❌ Отклонено"
        api("answerCallbackQuery", callback_query_id=cb["id"], text="Отклонено")
    new = o["header"] + "\n\n<b>" + mark + "</b>"
    if msg.get("caption") is not None or msg.get("photo") or msg.get("document"):
        api("editMessageCaption", chat_id=ADMIN_ID, message_id=msg["message_id"],
            caption=new, parse_mode="HTML")
    else:
        api("editMessageText", chat_id=ADMIN_ID, message_id=msg["message_id"],
            text=new, parse_mode="HTML")


# ---------- клиент ----------

def on_message(msg):
    if msg.get("chat", {}).get("type") != "private":
        return
    frm = msg["from"]
    uid = frm["id"]
    text = msg.get("text", "")

    if uid == ADMIN_ID:
        handle_admin(msg)
        return

    u = user(uid)
    u["name"] = name_of(frm)
    if text.startswith("/start"):
        u["state"] = None
        save()
        show_menu(frm)
        return

    if u.get("state") == "receipt":
        if not (msg.get("photo") or msg.get("document")):
            send(uid, "📸 Пришли, пожалуйста, <b>скриншот чека</b> (картинкой или файлом).",
                 kb([[("⬅️ Отмена", "menu")]]))
            return
        DATA["n"] += 1
        n = str(DATA["n"])
        header = ("💰 <b>Оплата №%s</b>\n👤 %s\n🆔 <code>%d</code>\n💵 %d₽\n\n"
                  "Проверь поступление и нажми кнопку 👇" % (n, e(name_of(frm)), uid, PRICE))
        DATA["orders"][n] = {"uid": uid, "status": "wait", "header": header}
        u["state"] = "wait"
        save()
        r = to_admin(frm, header, msg, kb([[("✅ Подтвердить", "ok:" + n), ("❌ Отклонить", "no:" + n)]]))
        if r:
            send(uid, "⏳ <b>Чек отправлен на проверку!</b>\n\nОбычно это занимает несколько минут. "
                      "Курс придёт сюда автоматически — никуда не уходи 😉")
        else:
            send(uid, "⚠️ Не удалось отправить чек, попробуй ещё раз чуть позже.")
        return

    if u.get("state") == "wait" and (msg.get("photo") or msg.get("document")):
        send(uid, "⏳ Твой чек уже на проверке. Как только подтвердим — курс придёт сюда.")
        return

    # любое другое сообщение — вопрос в поддержку
    header = "💬 <b>Сообщение</b> от %s\n🆔 <code>%d</code>%s\n\n↩️ Ответь реплаем, чтобы написать клиенту." % (
        e(name_of(frm)), uid, " · ✅ купил" if u.get("paid") else "")
    if to_admin(frm, header, msg):
        if u.get("state") == "ask":
            send(uid, "✅ Вопрос отправлен! Ответ придёт сюда.", kb([[("🏠 Меню", "menu")]]))
            u["state"] = None
            save()
    else:
        send(uid, "⚠️ Не удалось отправить сообщение, попробуй позже.")


def on_callback(cb):
    msg = cb.get("message")
    data = cb.get("data", "")
    uid = cb["from"]["id"]
    if not msg:
        api("answerCallbackQuery", callback_query_id=cb["id"])
        return
    if uid == ADMIN_ID and data[:3] in ("ok:", "no:"):
        decide(cb, data[3:], data.startswith("ok:"))
        return
    api("answerCallbackQuery", callback_query_id=cb["id"])
    u = user(uid)

    if data == "menu":
        u["state"] = None
        save()
        show_menu(cb["from"])
    elif data == "program":
        send(uid, PROGRAM, kb([[("💳 Купить за %d₽" % PRICE, "buy")] if not u.get("paid") else
                               [("📥 Скачать курс", "get")], [("🏠 Меню", "menu")]]))
    elif data == "buy":
        if u.get("paid"):
            send(uid, "✅ Курс уже куплен!", kb([[("📥 Скачать курс", "get")]]))
            return
        send(uid, PAY.format(price=PRICE, card=CARD_PRETTY),
             kb([[("✅ Я оплатил", "paid")], [("🏠 Меню", "menu")]]))
    elif data == "paid":
        if u.get("state") == "wait":
            send(uid, "⏳ Твой чек уже на проверке. Как только подтвердим — курс придёт сюда.")
            return
        u["state"] = "receipt"
        save()
        send(uid, "📸 Отлично! Теперь пришли <b>скриншот чека</b> об оплате одним сообщением.",
             kb([[("⬅️ Отмена", "menu")]]))
    elif data == "get":
        if u.get("paid"):
            give_course(uid)
        else:
            send(uid, "Курс станет доступен после оплаты 💳", kb([[("💳 Купить за %d₽" % PRICE, "buy")]]))
    elif data == "ask":
        u["state"] = "ask"
        save()
        send(uid, "💬 Напиши свой вопрос одним сообщением — мы ответим сюда же.",
             kb([[("⬅️ Отмена", "menu")]]))


def handle(update):
    if "message" in update:
        on_message(update["message"])
    elif "callback_query" in update:
        on_callback(update["callback_query"])


VPN_HINT = ("\n⚠️ Telegram недоступен из этой сети (провайдер обрывает соединение).\n"
            "   Включите VPN на телефоне и перезапустите бота,\n"
            "   или укажите прокси в .env: PROXY=http://адрес:порт\n")


def main():
    if not TOKEN:
        sys.exit("Нет BOT_TOKEN: создайте файл .env рядом с main.py со строкой BOT_TOKEN=...")
    for f in (COURSE_FILE, COVER_FILE):
        if not os.path.exists(f):
            print("⚠️ Нет файла %s — скачайте его рядом с main.py" % os.path.basename(f), file=sys.stderr)
    print("Подключаюсь к Telegram...")
    while True:
        try:
            me = api("getMe")
            break
        except NetError as err:
            print("Нет связи с Telegram: %s%s" % (err, VPN_HINT), file=sys.stderr)
            time.sleep(10)
    if not me:
        sys.exit("Telegram не принял токен — проверьте BOT_TOKEN в .env.")
    try:
        api("deleteWebhook")
        api("setMyCommands", commands=[{"command": "start", "description": "Главное меню"}])
    except NetError:
        pass
    print("✅ Бот @%s запущен. Админ: %s. Цена: %d₽" % (me["username"], ADMIN_ID, PRICE))
    offset, fails = 0, 0
    while True:
        try:
            ups = api("getUpdates", _http_timeout=25, _tries=1, offset=offset, timeout=10,
                      allowed_updates=["message", "callback_query"])
        except NetError as err:
            fails += 1
            if fails in (1, 5) or fails % 30 == 0:
                print("Сеть: %s — переподключаюсь (%d)%s" % (err, fails, VPN_HINT if fails >= 5 else ""),
                      file=sys.stderr)
            time.sleep(min(3 * fails, 15))
            continue
        if fails:
            print("✅ Связь восстановлена")
            fails = 0
        if ups is None:
            time.sleep(3)
            continue
        for up in ups:
            offset = up["update_id"] + 1
            try:
                handle(up)
            except NetError as err:
                print("Не удалось отправить ответ (сеть: %s)" % err, file=sys.stderr)
            except Exception as err:
                print("Ошибка обработки:", repr(err), file=sys.stderr)


if __name__ == "__main__":
    main()
