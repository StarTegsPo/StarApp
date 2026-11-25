#!/usr/bin/env python3
"""Бот аренды товаров.

/start → приветствие → «Выбрать товар» → товар → количество → место встречи →
заявка уходит админу. Админ отвечает реплаем на сообщение в боте — ответ
приходит клиенту, и переписка продолжается в обе стороны.

Без внешних зависимостей: только стандартная библиотека Python 3.8+.
Настройки — в файле .env рядом с этим файлом (или в переменных окружения):
    BOT_TOKEN=123456:ABC...
    ADMIN_ID=123456789      # необязательно: иначе админом станет тот, кто первым отправит /admin
"""
import json
import os
import sys
import time
import urllib.error
import urllib.request

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
DATA_FILE = os.getenv("DATA_FILE", os.path.join(HERE, "rentbot_data.json"))

PRODUCTS = ["Пасик2", "Хрос6", "ХироQ"]
PRICE = 10  # ₽ за 1 шт
MAX_QTY = 1000

WELCOME = "👋 Приветствую в нашем боте!\n\nЗдесь можно взять товар в аренду."


# ---------- хранилище ----------

def load_data():
    try:
        with open(DATA_FILE, encoding="utf-8") as f:
            d = json.load(f)
    except (OSError, ValueError):
        d = {}
    d.setdefault("admin_id", None)
    d.setdefault("users", {})   # user_id -> {"state": ..., "product": ..., "qty": ...}
    d.setdefault("links", {})   # id сообщения у админа -> user_id клиента
    d.setdefault("last_user", None)
    d.setdefault("orders", 0)
    return d


DEFAULT_ADMIN_ID = 7383755594

DATA = load_data()
if os.getenv("ADMIN_ID", "").strip().lstrip("-").isdigit():
    DATA["admin_id"] = int(os.getenv("ADMIN_ID").strip())
else:
    DATA["admin_id"] = DEFAULT_ADMIN_ID


def save():
    tmp = DATA_FILE + ".tmp"
    # храним только последние 5000 связок, чтобы файл не рос бесконечно
    if len(DATA["links"]) > 5000:
        keys = sorted(DATA["links"], key=int)[-5000:]
        DATA["links"] = {k: DATA["links"][k] for k in keys}
    with open(tmp, "w", encoding="utf-8") as f:
        json.dump(DATA, f, ensure_ascii=False)
    os.replace(tmp, DATA_FILE)


def user(uid):
    return DATA["users"].setdefault(str(uid), {"state": None})


# ---------- Telegram API ----------

def api(method, _http_timeout=30, **params):
    url = "https://api.telegram.org/bot%s/%s" % (TOKEN, method)
    body = json.dumps(params).encode()
    req = urllib.request.Request(url, body, {"Content-Type": "application/json"})
    try:
        with urllib.request.urlopen(req, timeout=_http_timeout) as r:
            res = json.load(r)
    except urllib.error.HTTPError as e:
        try:
            res = json.load(e)
        except ValueError:
            res = {"ok": False, "description": str(e)}
    if not res.get("ok"):
        print("API %s: %s" % (method, res.get("description")), file=sys.stderr)
        return None
    return res.get("result")


def kb(rows):
    return {"inline_keyboard": [[{"text": t, "callback_data": d} for t, d in row] for row in rows]}


def send(chat_id, text, markup=None):
    p = {"chat_id": chat_id, "text": text}
    if markup:
        p["reply_markup"] = markup
    return api("sendMessage", **p)


def edit(chat_id, msg_id, text, markup=None):
    p = {"chat_id": chat_id, "message_id": msg_id, "text": text}
    if markup:
        p["reply_markup"] = markup
    return api("editMessageText", **p)


def name_of(u):
    n = (u.get("first_name", "") + " " + u.get("last_name", "")).strip() or "Без имени"
    if u.get("username"):
        n += " (@%s)" % u["username"]
    return n


# ---------- экраны ----------

MAIN_KB = kb([[("🛒 Выбрать товар", "menu")]])


def products_kb():
    rows = [[("📦 " + p, "p:%d" % i)] for i, p in enumerate(PRODUCTS)]
    return kb(rows)


def qty_kb(i):
    nums = [1, 2, 3, 4, 5, 6, 7, 8, 9, 10]
    rows = [[(str(n), "q:%d:%d" % (i, n)) for n in nums[:5]],
            [(str(n), "q:%d:%d" % (i, n)) for n in nums[5:]],
            [("✏️ Другое количество", "qo:%d" % i)],
            [("⬅️ Назад", "menu")]]
    return kb(rows)


def product_text(i):
    return "📦 %s\n💰 Цена: %d₽ за 1 шт\n\nВыберите количество:" % (PRODUCTS[i], PRICE)


def ask_place(chat_id, u, msg_id=None):
    p, q = PRODUCTS[u["product"]], u["qty"]
    text = ("📦 %s × %d шт\n💰 Итого: %d₽\n\n📍 Напишите место встречи:"
            % (p, q, q * PRICE))
    u["state"] = "place"
    save()
    if msg_id:
        edit(chat_id, msg_id, text)
    else:
        send(chat_id, text)


# ---------- связь с админом ----------

def to_admin(from_user, header, msg=None):
    """Отправляет админу заголовок (+ копию сообщения клиента) и запоминает связки."""
    admin = DATA["admin_id"]
    uid = from_user["id"]
    if not admin:
        return False
    sent = send(admin, header)
    if sent:
        DATA["links"][str(sent["message_id"])] = uid
    if msg is not None:
        c = api("copyMessage", chat_id=admin, from_chat_id=msg["chat"]["id"],
                message_id=msg["message_id"])
        if c:
            DATA["links"][str(c["message_id"])] = uid
    DATA["last_user"] = uid
    save()
    return bool(sent)


def handle_admin_message(msg):
    admin = DATA["admin_id"]
    text = msg.get("text", "")
    if text.startswith("/start"):
        send(admin, "Вы админ этого бота.\n\nЗаявки и сообщения клиентов приходят сюда. "
                    "Чтобы ответить клиенту — ответьте (Reply / свайп) на его сообщение.\n"
                    "/to <id> <текст> — написать клиенту по ID.")
        return
    if text.startswith("/to "):
        parts = text.split(maxsplit=2)
        if len(parts) < 3 or not parts[1].lstrip("-").isdigit():
            send(admin, "Формат: /to <id> <текст>")
            return
        r = send(int(parts[1]), "💬 Ответ:\n" + parts[2])
        send(admin, "✅ Отправлено" if r else "❌ Не удалось отправить")
        return
    reply = msg.get("reply_to_message")
    target = DATA["links"].get(str(reply["message_id"])) if reply else None
    if not target:
        send(admin, "Чтобы ответить клиенту, ответьте (Reply) на его сообщение.")
        return
    if text:
        r = send(target, "💬 Ответ:\n" + text)
    else:  # фото, голосовое, геолокация и т.п.
        r = api("copyMessage", chat_id=target, from_chat_id=admin, message_id=msg["message_id"])
    if r:
        DATA["links"][str(msg["message_id"])] = target
        save()
    else:
        send(admin, "❌ Не удалось доставить (возможно, клиент заблокировал бота).")


# ---------- обработчики ----------

def on_message(msg):
    if msg.get("chat", {}).get("type") != "private":
        return
    frm = msg["from"]
    uid = frm["id"]
    text = msg.get("text", "")

    if text.startswith("/admin"):
        if DATA["admin_id"] is None:
            DATA["admin_id"] = uid
            save()
            send(uid, "✅ Вы назначены админом. Заявки будут приходить сюда.")
        elif DATA["admin_id"] == uid:
            send(uid, "Вы уже админ.")
        else:
            send(uid, "Админ уже назначен.")
        return

    if uid == DATA["admin_id"]:
        handle_admin_message(msg)
        return

    u = user(uid)
    if text.startswith("/start"):
        u["state"] = None
        save()
        send(uid, WELCOME, MAIN_KB)
        return

    if u.get("state") == "qty":
        if not text.strip().isdigit() or not (1 <= int(text) <= MAX_QTY):
            send(uid, "Введите количество числом от 1 до %d:" % MAX_QTY)
            return
        u["qty"] = int(text)
        ask_place(uid, u)
        return

    if u.get("state") == "place":
        if not text.strip():
            send(uid, "📍 Напишите место встречи текстом:")
            return
        DATA["orders"] += 1
        n = DATA["orders"]
        p, q = PRODUCTS[u["product"]], u["qty"]
        u["state"] = "chat"
        header = ("🆕 Договор №%d\n👤 %s\n🆔 %d\n\n📦 %s × %d шт\n💰 %d₽\n📍 %s\n\n"
                  "↩️ Ответьте на это сообщение, чтобы написать клиенту."
                  % (n, name_of(frm), uid, p, q, q * PRICE, text.strip()))
        ok = to_admin(frm, header)
        if ok:
            send(uid, "✅ Договор №%d создан!\n\n📦 %s × %d шт — %d₽\n📍 %s\n\n"
                      "Ожидайте ответа. Можете написать сюда, если есть вопросы."
                 % (n, p, q, q * PRICE, text.strip()), MAIN_KB)
        else:
            send(uid, "⚠️ Не удалось создать договор, попробуйте позже.", MAIN_KB)
        return

    # любое другое сообщение — пересылаем админу как переписку
    header = "💬 Сообщение от %s\n🆔 %d" % (name_of(frm), uid)
    if to_admin(frm, header, msg):
        if u.get("state") != "chat":
            send(uid, "✉️ Сообщение отправлено.")
    else:
        send(uid, WELCOME, MAIN_KB)


def on_callback(cb):
    api("answerCallbackQuery", callback_query_id=cb["id"])
    msg = cb.get("message")
    if not msg:
        return
    uid = cb["from"]["id"]
    chat, mid = msg["chat"]["id"], msg["message_id"]
    data = cb.get("data", "")
    u = user(uid)

    if data == "menu":
        u["state"] = None
        save()
        edit(chat, mid, "Выберите товар:", products_kb())
    elif data.startswith("p:"):
        i = int(data[2:])
        if 0 <= i < len(PRODUCTS):
            edit(chat, mid, product_text(i), qty_kb(i))
    elif data.startswith("q:"):
        _, i, q = data.split(":")
        i, q = int(i), int(q)
        if 0 <= i < len(PRODUCTS) and 1 <= q <= MAX_QTY:
            u["product"], u["qty"] = i, q
            ask_place(chat, u, mid)
    elif data.startswith("qo:"):
        i = int(data[3:])
        if 0 <= i < len(PRODUCTS):
            u["product"], u["state"] = i, "qty"
            save()
            edit(chat, mid, "📦 %s — %d₽ за 1 шт\n\n✏️ Введите количество числом:"
                 % (PRODUCTS[i], PRICE))


def handle(update):
    if "message" in update:
        on_message(update["message"])
    elif "callback_query" in update:
        on_callback(update["callback_query"])


def main():
    if not TOKEN:
        sys.exit("Нет BOT_TOKEN: создайте файл .env рядом с main.py со строкой BOT_TOKEN=...")
    me = api("getMe")
    if not me:
        sys.exit("Не удалось подключиться к Telegram — проверьте токен и интернет.")
    api("deleteWebhook")
    print("Бот @%s запущен. Админ: %s" % (me["username"], DATA["admin_id"] or "не назначен (/admin)"))
    offset = 0
    while True:
        try:
            # короткий опрос: мобильная сеть часто рвёт долгие соединения (Errno 103)
            ups = api("getUpdates", _http_timeout=25, offset=offset, timeout=10,
                      allowed_updates=["message", "callback_query"])
        except Exception as e:  # сеть пропала — просто пробуем снова
            print("Сеть моргнула (%s), переподключаюсь..." % e, file=sys.stderr)
            time.sleep(3)
            continue
        if ups is None:
            time.sleep(3)
            continue
        for up in ups:
            offset = up["update_id"] + 1
            try:
                handle(up)
            except Exception as e:
                print("Ошибка обработки:", repr(e), file=sys.stderr)


if __name__ == "__main__":
    main()
