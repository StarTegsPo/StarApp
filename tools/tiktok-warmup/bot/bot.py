#!/usr/bin/env python3
"""
Бот «Прогрев TikTok»: раздаёт приложение (APK) и продаёт Premium за Telegram Stars.

Без внешних библиотек — только стандартный Python 3.9+.
Настройки берутся из переменных окружения или файла .env рядом с ботом:

    BOT_TOKEN=123456:ABC...        токен бота от @BotFather
    ADMIN_IDS=111111111            ваш Telegram ID (через запятую, если несколько)
    CODE_SEED=...                  секрет для кодов Premium (выдаётся вместе с ботом, никому не показывать)
    PRICE=50                       цена Premium в звёздах (необязательно)

Запуск:  python bot.py
"""
import hashlib
import hmac
import html
import json
import os
import sys
import time
import traceback
import urllib.error
import urllib.parse
import urllib.request
import uuid

HERE = os.path.dirname(os.path.abspath(__file__))


def load_env():
    path = os.path.join(HERE, ".env")
    if not os.path.exists(path):
        return
    with open(path, encoding="utf-8") as f:
        for line in f:
            line = line.strip()
            if not line or line.startswith("#") or "=" not in line:
                continue
            k, v = line.split("=", 1)
            k = k.strip().removeprefix("export ").strip()
            v = v.strip().strip('"').strip("'")
            os.environ.setdefault(k, v)


load_env()
TOKEN = os.environ.get("BOT_TOKEN", "").strip()
SEED = os.environ.get("CODE_SEED", "").strip()
ADMINS = {int(x) for x in os.environ.get("ADMIN_IDS", "").replace(" ", "").split(",") if x.strip().lstrip("-").isdigit()}
PRICE = int(os.environ.get("PRICE", "50") or 50)              # Premium навсегда
PRICE_MONTH = int(os.environ.get("PRICE_MONTH", "30") or 30)  # Premium на 30 дней
BOT_USERNAME = "Vibe2Scool_bot"   # уточняется через getMe при запуске

if not TOKEN or not SEED:
    sys.exit("Заполните BOT_TOKEN и CODE_SEED в файле .env рядом с bot.py")

API = f"https://api.telegram.org/bot{TOKEN}/"
REPO = "StarTegsPo/StarApp"
RELEASE_API = f"https://api.github.com/repos/{REPO}/releases/tags/warmup-latest"
APK_URL = f"https://github.com/{REPO}/releases/download/warmup-latest/TikTokWarmup.apk"
IPA_URL = f"https://github.com/{REPO}/releases/download/warmup-latest/TikTokWarmup.ipa"
STATE_FILE = os.path.join(HERE, "data.json")
CODES_TOTAL = 5000          # столько кодов знает приложение
CODE_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"


# ======================================================================
# Хранилище
# ======================================================================

def load_state():
    try:
        with open(STATE_FILE, encoding="utf-8") as f:
            s = json.load(f)
    except (OSError, ValueError):
        s = {}
    s.setdefault("users", {})
    s.setdefault("next_code", 0)
    s.setdefault("codes", [])      # {uid, code, kind: paid|gift, stars, charge, date}
    s.setdefault("apk", {})
    s.setdefault("ipa", {})
    s.setdefault("offset", 0)
    s.setdefault("month_next", {})   # yyyymm → сколько месячных кодов выдано
    return s


def save_state():
    tmp = STATE_FILE + ".tmp"
    with open(tmp, "w", encoding="utf-8") as f:
        json.dump(state, f, ensure_ascii=False, indent=1)
    os.replace(tmp, STATE_FILE)


state = load_state()


def code_for(i):
    """Код Premium №i. Приложение хранит только хеши этих кодов."""
    d = hmac.new(SEED.encode(), f"tw-code:{i}".encode(), hashlib.sha256).digest()
    s = "".join(CODE_CHARS[b % 32] for b in d[:12])
    return f"STAR-{s[:4]}-{s[4:8]}-{s[8:12]}"


def issue_code(uid, kind, stars=0, charge=""):
    i = state["next_code"]
    if i >= CODES_TOTAL:
        notify_admins("⚠️ Коды Premium закончились! Нужно обновить приложение с новыми кодами.")
        return None
    state["next_code"] = i + 1
    code = code_for(i)
    state["codes"].append({"uid": uid, "code": code, "kind": kind, "stars": stars,
                           "charge": charge, "date": int(time.time())})
    save_state()
    left = CODES_TOTAL - state["next_code"]
    if left in (500, 100, 20):
        notify_admins(f"⚠️ Осталось кодов Premium: {left}")
    return code


MONTH_CODES = 500   # столько месячных кодов на каждый месяц знает приложение


def month_code_for(ym, i):
    """Месячный код №i для месяца ym (yyyymm). Активируется в этом месяце или следующем, действует 30 дней."""
    d = hmac.new(SEED.encode(), f"tw-month:{ym}:{i}".encode(), hashlib.sha256).digest()
    s = "".join(CODE_CHARS[b % 32] for b in d[:12])
    return f"STRM-{s[:4]}-{s[4:8]}-{s[8:12]}"


def issue_month_code(uid, kind, stars=0, charge=""):
    ym = time.strftime("%Y%m")
    i = state["month_next"].get(ym, 0)
    if i >= MONTH_CODES:
        notify_admins(f"⚠️ Месячные коды на {ym} закончились!")
        return None
    state["month_next"][ym] = i + 1
    code = month_code_for(ym, i)
    state["codes"].append({"uid": uid, "code": code, "kind": kind, "stars": stars,
                           "charge": charge, "date": int(time.time())})
    save_state()
    return code


def user_codes(uid):
    return [c for c in state["codes"] if c["uid"] == uid]


# ======================================================================
# Telegram API
# ======================================================================

class TgError(Exception):
    def __init__(self, code, desc):
        super().__init__(f"{code}: {desc}")
        self.code = code
        self.desc = desc


def api(method, _timeout=30, **params):
    data = json.dumps({k: v for k, v in params.items() if v is not None}).encode()
    req = urllib.request.Request(API + method, data=data, headers={"Content-Type": "application/json"})
    try:
        with urllib.request.urlopen(req, timeout=_timeout) as r:
            res = json.load(r)
    except urllib.error.HTTPError as e:
        try:
            res = json.load(e)
        except ValueError:
            raise TgError(e.code, str(e))
    if not res.get("ok"):
        raise TgError(res.get("error_code"), res.get("description"))
    return res["result"]


def api_upload(method, fields, file_field, filename, content, timeout=180,
               mime="application/vnd.android.package-archive"):
    boundary = uuid.uuid4().hex
    parts = []
    for k, v in fields.items():
        if v is None:
            continue
        if not isinstance(v, str):
            v = json.dumps(v)
        parts.append(f'--{boundary}\r\nContent-Disposition: form-data; name="{k}"\r\n\r\n{v}\r\n'.encode())
    parts.append((f'--{boundary}\r\nContent-Disposition: form-data; name="{file_field}"; filename="{filename}"\r\n'
                  f'Content-Type: {mime}\r\n\r\n').encode() + content + b"\r\n")
    parts.append(f"--{boundary}--\r\n".encode())
    req = urllib.request.Request(API + method, data=b"".join(parts),
                                 headers={"Content-Type": f"multipart/form-data; boundary={boundary}"})
    with urllib.request.urlopen(req, timeout=timeout) as r:
        res = json.load(r)
    if not res.get("ok"):
        raise TgError(res.get("error_code"), res.get("description"))
    return res["result"]


def kb(*rows):
    """Инлайн-клавиатура: kb([("Текст", "callback"), ...], ...). url-кнопка — ("Текст", "https://...")."""
    out = []
    for row in rows:
        r = []
        for text, action in row:
            r.append({"text": text, "url": action} if action.startswith("http") else
                     {"text": text, "callback_data": action})
        out.append(r)
    return {"inline_keyboard": out}


def send(chat, text, markup=None):
    return api("sendMessage", chat_id=chat, text=text, parse_mode="HTML",
               reply_markup=markup, link_preview_options={"is_disabled": True})


def show(chat, msg_id, text, markup=None):
    """Меняет текущее сообщение (навигация как в приложении); если нельзя — шлёт новое."""
    if msg_id:
        try:
            return api("editMessageText", chat_id=chat, message_id=msg_id, text=text, parse_mode="HTML",
                       reply_markup=markup, link_preview_options={"is_disabled": True})
        except TgError as e:
            if "not modified" in (e.desc or ""):
                return None
    return send(chat, text, markup)


def notify_admins(text):
    for a in ADMINS:
        try:
            send(a, text)
        except Exception:
            pass


# ======================================================================
# Экраны
# ======================================================================

def main_text(name):
    hi = f"Привет, <b>{html.escape(name)}</b>! 👋\n\n" if name else ""
    return (hi +
            "🔥 <b>Прогрев TikTok</b>\n"
            "<i>Твой аккаунт прогревается сам — как будто его листает живой человек</i>\n\n"
            "▫️ смотрит ролики случайное время\n"
            "▫️ ставит ❤ через 1–18 роликов\n"
            "▫️ листает фото-посты, добавляет в ★ избранное\n"
            "▫️ пропускает эфиры и рекламу\n"
            "▫️ работает в разделённом экране — телефоном можно пользоваться\n\n"
            "Выбери, что нужно 👇")


def main_kb():
    return kb([("📲 Скачать для Android", "apk")],
              [("🍏 Для iPhone", "ios"), (f"👑 Premium · от {PRICE_MONTH} ⭐", "premium")],
              [("📖 Как установить", "install"), ("❓ Вопросы", "faq")],
              [("🎟 Мои коды", "codes"), ("📊 Отчёты", "reports")],
              [("🎁 Пригласить друга", "invite"), ("💬 Поддержка", "support")])


PREMIUM_TEXT = (
    "👑 <b>Premium</b>\n\n"
    "<b>Бесплатно</b>\n"
    "▫️ 100 постов в день\n"
    "▫️ лайки, избранное, фото-посты\n"
    "▫️ пропуск эфиров, темы оформления\n\n"
    "<b>Premium</b>\n"
    "✅ без дневного лимита\n"
    "✅ авто-пропуск рекламы\n"
    "✅ комментарии: эмодзи и живые фразы\n"
    "✅ репосты с текстом\n"
    "✅ поиск по нише, подписки, расписание — всё без ограничений\n\n"
    "💫 <b>Навсегда — {price} ⭐</b> (один раз, все будущие обновления)\n"
    "📅 <b>Месяц — {price_month} ⭐</b> (30 дней)\n\n"
    "Код активации придёт сюда сразу после оплаты.\n"
    "🎁 Новым пользователям приложение даёт 24 часа Premium бесплатно."
)

INSTALL_TEXT = (
    "📖 <b>Как установить</b>\n\n"
    "<b>1.</b> Нажми «📲 Скачать приложение» и открой файл.\n"
    "<b>2.</b> Телефон спросит про установку из неизвестного источника — разреши.\n"
    "<b>3.</b> Открой «Прогрев TikTok» → «Подключить» → в Спецвозможностях включи «Прогрев TikTok».\n"
    "<b>4.</b> Пишет «Настройка ограничена»? Настройки → Приложения → Прогрев TikTok → ⋮ → "
    "«Разрешить ограниченные настройки», и снова «Подключить».\n"
    "<b>5.</b> Нажми «🛡 Применить лучшие настройки», потом «▶ Начать прогрев».\n\n"
    "Остановить — нажми на плашку «● прогрев» на экране.\n"
    "Нужен Android 7 или новее."
)

FAQ_TEXT = (
    "❓ <b>Вопросы</b>\n\n"
    "<b>Не забанят?</b>\n"
    "TikTok не любит автоматизацию. Чтобы риск был минимальным — жми «Лучшие настройки» "
    "и запускай 2–3 раза в день по 30–40 минут, а не сутками.\n\n"
    "<b>Можно пользоваться телефоном?</b>\n"
    "Да: открой TikTok в разделённом экране или плавающем окне — прогрев идёт в нём.\n\n"
    "<b>Есть на iPhone?</b>\n"
    "Да — жми «🍏 Для iPhone». Там приложение подсказывает, когда лайкать, а листаешь ты сам.\n\n"
    "<b>Потерял код Premium</b>\n"
    "Он всегда есть в «🎟 Мои коды».\n\n"
    "<b>Купил, но код не подходит</b>\n"
    "Обнови приложение (скачай заново здесь) и напиши в «💬 Поддержка»."
)


def back_kb(*extra):
    return kb(*extra, [("◀️ Назад", "home")])


def screen(chat, msg_id, uid, name, action):
    if action == "home":
        show(chat, msg_id, main_text(name), main_kb())
    elif action == "premium":
        show(chat, msg_id, PREMIUM_TEXT.format(price=PRICE, price_month=PRICE_MONTH),
             back_kb([(f"💫 Навсегда — {PRICE} ⭐", "buy_life")], [(f"📅 Месяц — {PRICE_MONTH} ⭐", "buy_month")]))
    elif action == "install":
        show(chat, msg_id, INSTALL_TEXT, back_kb([("📲 Скачать приложение", "apk")]))
    elif action == "faq":
        show(chat, msg_id, FAQ_TEXT, back_kb([("💬 Задать вопрос", "support")]))
    elif action == "codes":
        mine = user_codes(uid)
        if mine:
            icons = {"paid": "👑", "month": "📅", "gift": "🎁", "month_gift": "🎁", "ref": "🎁"}
            lines = "\n".join(f"{icons.get(c['kind'], '🎁')} <code>{c['code']}</code>"
                              + (" — на 30 дней" if c["code"].startswith("STRM") else " — навсегда") for c in mine)
            text = ("🎟 <b>Твои коды Premium</b>\n\n" + lines +
                    "\n\nНажми на код — он скопируется.\n"
                    "Приложение → «Premium» → «У меня есть код».")
            show(chat, msg_id, text, back_kb([("📲 Скачать приложение", "apk")]))
        else:
            show(chat, msg_id, "🎟 <b>Мои коды</b>\n\nПока пусто. Купи Premium — код появится здесь.",
                 back_kb([(f"👑 Premium · от {PRICE_MONTH} ⭐", "premium")]))
    elif action == "support":
        state["users"].setdefault(str(uid), {})["support"] = True
        save_state()
        show(chat, msg_id, "💬 <b>Поддержка</b>\n\nНапиши свой вопрос одним сообщением — "
                           "его получит администратор, ответ придёт сюда.",
             back_kb())
    elif action in ("buy", "buy_life"):
        send_invoice(chat, "life")
    elif action == "buy_month":
        send_invoice(chat, "month")
    elif action == "invite":
        invited = [k for k, v in state["users"].items() if v.get("ref") == uid]
        bought = [k for k in invited if state["users"][k].get("ref_paid")]
        link = f"https://t.me/{BOT_USERNAME}?start=ref_{uid}"
        show(chat, msg_id,
             "🎁 <b>Пригласи друга</b>\n\n"
             "Друг купил Premium по твоей ссылке — тебе <b>Premium на месяц</b> в подарок, за каждого.\n\n"
             f"Твоя ссылка:\n<code>{link}</code>\n<i>нажми — скопируется</i>\n\n"
             f"👥 Пришло: <b>{len(invited)}</b>   👑 Купили: <b>{len(bought)}</b>",
             back_kb([("📤 Поделиться", "https://t.me/share/url?url=" + urllib.parse.quote(link)
                       + "&text=" + urllib.parse.quote("🔥 Прогрев TikTok — аккаунт прогревается сам"))]))
    elif action == "reports":
        reps = state["users"].get(str(uid), {}).get("reports", [])
        if not reps:
            show(chat, msg_id, "📊 <b>Отчёты</b>\n\nПока пусто. После прогрева нажми уведомление "
                               "«✅ Прогрев завершён» или «📤 Отчёт в Telegram» в приложении.", back_kb())
        else:
            week = [r for r in reps if r["t"] > time.time() - 7 * 86400]

            def tot(k, rs):
                return sum(r[k] for r in rs)
            last = "\n".join(f"{time.strftime('%d.%m %H:%M', time.localtime(r['t']))} — 🎬 {r['p']} ❤ {r['l']} "
                             f"★ {r['f']} 💬 {r['c']} ↻ {r['r']} ➕ {r['fo']} · {r['m']} мин" for r in reps[-7:])
            show(chat, msg_id, "📊 <b>Твои отчёты</b>\n\n"
                               f"<b>За 7 дней:</b> 🎬 {tot('p', week)}  ❤ {tot('l', week)}  ★ {tot('f', week)}  "
                               f"💬 {tot('c', week)}  ➕ {tot('fo', week)}  ⏱ {tot('m', week)} мин\n\n"
                               f"<b>Последние сессии</b>\n{last}", back_kb())
    elif action == "apk":
        send_apk(chat)
    elif action == "ios":
        show(chat, msg_id, IOS_TEXT, back_kb([("🍏 Скачать приложение для iPhone", "ipa")]))
    elif action == "ipa":
        send_ipa(chat)


# ======================================================================
# APK
# ======================================================================

def release_info(name="TikTokWarmup.apk"):
    """(updated_at, size) файла последней сборки на GitHub или (None, None)."""
    try:
        req = urllib.request.Request(RELEASE_API, headers={"Accept": "application/vnd.github+json",
                                                           "User-Agent": "tiktok-warmup-bot"})
        with urllib.request.urlopen(req, timeout=15) as r:
            rel = json.load(r)
        for a in rel.get("assets", []):
            if a.get("name") == name:
                return a.get("updated_at"), a.get("size")
    except Exception:
        pass
    return None, None


IOS_TEXT = (
    "🍏 <b>Прогрев TikTok для iPhone</b>\n\n"
    "Те же настройки, темы и Premium, что на Android. Нажимаешь «▶ Начать прогрев» — открывается TikTok, "
    "ты листаешь ленту, а приложение присылает подсказки поверх TikTok: когда лайкнуть, добавить в избранное, "
    "написать комментарий (с готовой фразой), сделать репост или перерыв.\n"
    "<i>Apple не разрешает приложениям самим нажимать в TikTok, поэтому действия делаешь ты — "
    "а приложение следит, чтобы всё было как у живого человека.</i>\n\n"
    "<b>Как поставить IPA</b>\n"
    "1. Скачай файл ниже.\n"
    "2. Установи через <b>Sideloadly</b> или <b>AltStore</b> (с компьютера, бесплатно, нужен Apple ID) "
    "или через ESign / Scarlet.\n"
    "3. Настройки → Основные → VPN и управление устройством → доверять своему Apple ID.\n"
    "4. При первом запуске разреши уведомления — через них приходят подсказки."
)

APK_CAPTION = ("📲 <b>Прогрев TikTok</b> для Android\n\n"
               "Нажми на файл, чтобы установить. Если телефон спросит — разреши установку из этого источника.\n"
               "Обновление ставится поверх старой версии.")

IPA_CAPTION = ("🍏 <b>Прогрев TikTok</b> для iPhone\n\n"
               "Установи через Sideloadly / AltStore / ESign и разреши уведомления — через них приходят подсказки.")


def send_apk(chat):
    send_release_file(chat, "apk", "TikTokWarmup.apk", APK_URL, APK_CAPTION,
                      kb([("📖 Как установить", "install"), ("👑 Premium", "premium")]),
                      "application/vnd.android.package-archive")


def send_ipa(chat):
    send_release_file(chat, "ipa", "TikTokWarmup.ipa", IPA_URL, IPA_CAPTION,
                      kb([("👑 Premium", "premium")]),
                      "application/octet-stream")


def send_release_file(chat, key, name, url, caption, markup, mime):
    """Отправляет файл из релиза; после первой загрузки — мгновенно по file_id."""
    cache = state[key]
    # проверяем новую сборку не чаще раза в 10 минут
    if time.time() - cache.get("checked", 0) > 600 or not cache.get("file_id"):
        updated, _ = release_info(name)
        cache["checked"] = time.time()
        if updated and updated != cache.get("updated"):
            cache.pop("file_id", None)
            cache["updated"] = updated
        save_state()
    if cache.get("file_id"):
        try:
            api("sendDocument", chat_id=chat, document=cache["file_id"], caption=caption,
                parse_mode="HTML", reply_markup=markup)
            return
        except TgError:
            cache.pop("file_id", None)
    try:
        api("sendChatAction", chat_id=chat, action="upload_document")
        req = urllib.request.Request(url, headers={"User-Agent": "tiktok-warmup-bot"})
        with urllib.request.urlopen(req, timeout=120) as r:
            content = r.read()
        res = api_upload("sendDocument", {"chat_id": str(chat), "caption": caption, "parse_mode": "HTML",
                                          "reply_markup": markup}, "document", name, content, mime=mime)
        cache["file_id"] = res["document"]["file_id"]
        save_state()
    except Exception:
        traceback.print_exc()
        send(chat, caption + "\n\nСкачай файл по кнопке ниже 👇", kb([("⬇️ Скачать", url)]))


# ======================================================================
# Оплата Stars
# ======================================================================

def send_invoice(chat, plan="life"):
    month = plan == "month"
    price = PRICE_MONTH if month else PRICE
    api("sendInvoice", chat_id=chat,
        title="📅 Premium на месяц · Прогрев TikTok" if month else "👑 Premium навсегда · Прогрев TikTok",
        description=("30 дней" if month else "Навсегда") + ": без лимита постов, авто-пропуск рекламы, "
                    "комментарии и репосты с текстом. Код активации придёт сразу после оплаты.",
        payload=f"premium:{plan}:{chat}:{int(time.time())}",
        currency="XTR",
        prices=[{"label": "Premium на 30 дней" if month else "Premium навсегда", "amount": price}],
        reply_markup={"inline_keyboard": [[{"text": f"💳 Оплатить {price} ⭐", "pay": True}],
                                          [{"text": "◀️ Назад", "callback_data": "premium"}]]})


def on_pre_checkout(q):
    ok = str(q.get("invoice_payload", "")).startswith("premium:") and q.get("currency") == "XTR"
    api("answerPreCheckoutQuery", pre_checkout_query_id=q["id"], ok=ok,
        error_message=None if ok else "Платёж не распознан, попробуйте ещё раз")


def on_paid(msg):
    pay = msg["successful_payment"]
    u = msg["from"]
    uid = u["id"]
    charge = pay.get("telegram_payment_charge_id", "")
    # защита от повторной выдачи за один и тот же платёж
    for c in state["codes"]:
        if charge and c.get("charge") == charge:
            return
    month = str(pay.get("invoice_payload", "")).startswith("premium:month:")
    stars = pay.get("total_amount", PRICE_MONTH if month else PRICE)
    code = issue_month_code(uid, "month", stars, charge) if month else issue_code(uid, "paid", stars, charge)
    if not code:
        send(uid, "Оплата прошла, но коды временно закончились — администратор уже знает и пришлёт код. 🙏")
        return
    send(uid,
         "🎉 <b>Оплата прошла — спасибо!</b>\n\n" +
         ("Твой код Premium на 30 дней:\n" if month else "Твой код Premium навсегда:\n") +
         f"<code>{code}</code>\n"
         "<i>нажми на код — он скопируется</i>\n\n"
         "<b>Как активировать</b>\n"
         "1. Открой «Прогрев TikTok»\n"
         "2. Карточка «👑 Premium» → «У меня есть код»\n"
         "3. Вставь код → «Активировать»\n\n"
         "Код всегда можно найти в «🎟 Мои коды».",
         kb([("📲 Скачать приложение", "apk")], [("🏠 Меню", "home")]))
    who = f"@{u['username']}" if u.get("username") else html.escape(u.get("first_name", ""))
    notify_admins(f"💰 <b>Продажа Premium {'на месяц' if month else 'навсегда'}</b>\n"
                  f"{who} (<code>{uid}</code>) — {pay.get('total_amount')} ⭐\n"
                  f"Код: <code>{code}</code>\nПлатёж: <code>{charge}</code>")
    reward_referrer(uid)


def reward_referrer(uid):
    """Первая покупка приглашённого — пригласившему Premium на месяц."""
    rec = state["users"].get(str(uid), {})
    ref = rec.get("ref")
    if not ref or rec.get("ref_paid"):
        return
    rec["ref_paid"] = True
    code = issue_month_code(ref, "ref")
    save_state()
    if not code:
        return
    try:
        send(ref, "🎁 <b>Твой друг купил Premium!</b>\n\n"
                  f"Держи Premium на месяц в подарок:\n<code>{code}</code>\n<i>нажми — скопируется</i>\n\n"
                  "Приложение → «👑 Premium» → «У меня есть код».",
             kb([("🎁 Пригласить ещё", "invite")]))
    except Exception:
        pass


# ======================================================================
# Администратор
# ======================================================================

ADMIN_HELP = ("🛠 <b>Админ-панель</b>\n\n"
              "/stats — статистика\n"
              "/code — код Premium навсегда себе, /code month — на месяц\n"
              "/gift ID — подарить код пользователю (или ответом на его сообщение)\n"
              "/broadcast текст — рассылка всем\n"
              "/refund ID charge_id — вернуть звёзды\n\n"
              "Чтобы ответить в поддержку — ответь (reply) на пересланное сообщение.")


def admin_command(msg, text):
    chat = msg["chat"]["id"]
    cmd, _, arg = text.partition(" ")
    cmd = cmd.split("@")[0]
    arg = arg.strip()
    if cmd == "/admin":
        send(chat, ADMIN_HELP)
    elif cmd == "/stats":
        paid = [c for c in state["codes"] if c["kind"] in ("paid", "month")]
        stars = sum(int(c.get("stars") or 0) for c in paid)
        day = time.time() - 86400
        send(chat, "📊 <b>Статистика</b>\n\n"
                   f"👥 Пользователей: <b>{len(state['users'])}</b>\n"
                   f"👑 Продаж: <b>{len(paid)}</b> (навсегда {sum(1 for c in paid if c['kind'] == 'paid')}, "
                   f"на месяц {sum(1 for c in paid if c['kind'] == 'month')}; за сутки: {sum(1 for c in paid if c['date'] > day)})\n"
                   f"🎁 Приглашено: <b>{sum(1 for v in state['users'].values() if v.get('ref'))}</b>\n"
                   f"⭐ Заработано: <b>{stars}</b>\n"
                   f"🎁 Подарено кодов: <b>{len(state['codes']) - len(paid)}</b>\n"
                   f"🎟 Осталось кодов: <b>{CODES_TOTAL - state['next_code']}</b>")
    elif cmd == "/code":
        if arg == "month":
            code = issue_month_code(chat, "month_gift")
            send(chat, f"🎁 Код Premium на 30 дней:\n<code>{code}</code>" if code else "Месячные коды закончились")
        else:
            code = issue_code(chat, "gift")
            send(chat, f"🎁 Код Premium навсегда:\n<code>{code}</code>" if code else "Коды закончились")
    elif cmd == "/gift":
        target = arg
        reply = msg.get("reply_to_message")
        if not target and reply:
            target = support_target(reply) or ""
        if not target.lstrip("-").isdigit():
            send(chat, "Укажи ID: /gift 123456789 (или ответь командой на сообщение пользователя)")
            return
        code = issue_code(int(target), "gift")
        if not code:
            send(chat, "Коды закончились")
            return
        try:
            send(int(target), "🎁 <b>Тебе подарили Premium!</b>\n\n"
                              f"Код: <code>{code}</code>\n<i>нажми — скопируется</i>\n\n"
                              "Приложение → «👑 Premium» → «У меня есть код».",
                 kb([("📲 Скачать приложение", "apk")]))
            send(chat, f"✅ Подарено: <code>{target}</code> — <code>{code}</code>")
        except TgError as e:
            send(chat, f"Код <code>{code}</code> создан, но отправить не вышло: {html.escape(str(e))}")
    elif cmd == "/broadcast":
        if not arg:
            send(chat, "Напиши текст: /broadcast Вышло обновление!")
            return
        ok = fail = 0
        for uid in list(state["users"]):
            try:
                send(int(uid), arg, main_kb())
                ok += 1
            except Exception:
                fail += 1
            time.sleep(0.05)
        send(chat, f"📣 Рассылка: доставлено {ok}, не доставлено {fail}")
    elif cmd == "/refund":
        parts = arg.split()
        if len(parts) != 2 or not parts[0].isdigit():
            send(chat, "Формат: /refund ID charge_id (оба есть в уведомлении о продаже)")
            return
        try:
            api("refundStarPayment", user_id=int(parts[0]), telegram_payment_charge_id=parts[1])
            send(chat, "↩️ Звёзды возвращены")
        except TgError as e:
            send(chat, f"Не получилось: {html.escape(str(e))}")
    else:
        return False
    return True


def support_target(reply):
    """ID пользователя из пересланного админу сообщения поддержки (строка «#id123»)."""
    text = reply.get("text") or reply.get("caption") or ""
    for word in text.split():
        if word.startswith("#id") and word[3:].isdigit():
            return word[3:]
    return None


# ======================================================================
# Обработка обновлений
# ======================================================================

def remember(u):
    rec = state["users"].setdefault(str(u["id"]), {"joined": int(time.time())})
    rec["name"] = u.get("first_name", "")
    if u.get("username"):
        rec["username"] = u["username"]


def on_message(msg):
    u = msg.get("from") or {}
    if not u or u.get("is_bot"):
        return
    chat = msg["chat"]["id"]
    if msg["chat"].get("type") != "private":
        return
    if "successful_payment" in msg:
        on_paid(msg)
        return
    is_new = str(u["id"]) not in state["users"]
    remember(u)
    text = (msg.get("text") or "").strip()
    uid = u["id"]

    if uid in ADMINS:
        reply = msg.get("reply_to_message")
        if text.startswith("/") and admin_command(msg, text):
            save_state()
            return
        target = support_target(reply) if reply else None
        if target and not text.startswith("/"):
            try:
                send(int(target), "💬 <b>Ответ поддержки</b>\n\n" + html.escape(text or "(вложение)"),
                     kb([("🏠 Меню", "home")]))
                send(chat, "✅ Ответ отправлен")
            except TgError as e:
                send(chat, f"Не доставлено: {html.escape(str(e))}")
            return

    cmd = text.split()[0].split("@")[0] if text.startswith("/") else ""
    if cmd == "/start":
        param = text.split(maxsplit=1)[1].strip() if " " in text else ""
        if param.startswith("ref_") and param[4:].isdigit():
            ref = int(param[4:])
            rec = state["users"].setdefault(str(uid), {})
            if is_new and ref != uid and str(ref) in state["users"] and "ref" not in rec:
                rec["ref"] = ref
                try:
                    send(ref, "🎉 По твоей ссылке пришёл новый пользователь! Купит Premium — тебе месяц в подарок.")
                except Exception:
                    pass
            param = ""
        if param.startswith("r_"):
            on_report(chat, uid, param)
            return
        if param in ("premium", "ios", "install", "codes", "invite", "reports"):
            screen(chat, None, uid, u.get("first_name", ""), param)
        else:
            send(chat, main_text(u.get("first_name", "")), main_kb())
    elif cmd in ("/premium", "/buy"):
        screen(chat, None, uid, u.get("first_name", ""), "premium")
    elif cmd in ("/app", "/apk"):
        send_apk(chat)
    elif cmd == "/codes":
        screen(chat, None, uid, u.get("first_name", ""), "codes")
    elif cmd == "/help":
        screen(chat, None, uid, u.get("first_name", ""), "install")
    elif cmd == "/paysupport":
        send(chat, "💬 По вопросам оплаты напиши в «Поддержка» — разберёмся и при необходимости вернём звёзды.",
             kb([("💬 Поддержка", "support")]))
    elif state["users"].get(str(uid), {}).pop("support", False) and (text or msg.get("photo")):
        who = f"@{u['username']}" if u.get("username") else html.escape(u.get("first_name", ""))
        notify_admins(f"💬 <b>Вопрос в поддержку</b> от {who} #id{uid}\n\n{html.escape(text or '(фото)')}\n\n"
                      f"<i>Ответь на это сообщение, чтобы написать пользователю</i>")
        if msg.get("photo"):
            for a in ADMINS:
                try:
                    api("forwardMessage", chat_id=a, from_chat_id=chat, message_id=msg["message_id"])
                except Exception:
                    pass
        send(chat, "✅ Отправлено! Ответ придёт сюда.", kb([("🏠 Меню", "home")]))
    else:
        send(chat, main_text(""), main_kb())
    save_state()


def on_report(chat, uid, param):
    """Отчёт о сессии из приложения: /start r_роликов_лайков_избранное_комменты_репосты_подписки_минут"""
    parts = param.split("_")[1:]
    if len(parts) != 7 or not all(p.isdigit() for p in parts):
        send(chat, main_text(""), main_kb())
        return
    p, l, f, c, r, fo, m = (min(int(x), 100000) for x in parts)
    rec = state["users"].setdefault(str(uid), {})
    reps = rec.setdefault("reports", [])
    reps.append({"t": int(time.time()), "p": p, "l": l, "f": f, "c": c, "r": r, "fo": fo, "m": m})
    del reps[:-60]
    week = [x for x in reps if x["t"] > time.time() - 7 * 86400]
    save_state()
    send(chat, "✅ <b>Прогрев завершён</b>\n\n"
               f"🎬 Роликов: <b>{p}</b>\n❤ Лайков: <b>{l}</b>\n★ В избранное: <b>{f}</b>\n"
               f"💬 Комментариев: <b>{c}</b>\n↻ Репостов: <b>{r}</b>\n➕ Подписок: <b>{fo}</b>\n⏱ Время: <b>{m} мин</b>\n\n"
               f"За 7 дней: 🎬 {sum(x['p'] for x in week)}  ❤ {sum(x['l'] for x in week)}  "
               f"⏱ {sum(x['m'] for x in week)} мин",
         kb([("📊 Все отчёты", "reports")], [("🏠 Меню", "home")]))


def on_callback(q):
    u = q["from"]
    remember(u)
    data = q.get("data", "")
    msg = q.get("message") or {}
    chat = msg.get("chat", {}).get("id", u["id"])
    try:
        api("answerCallbackQuery", callback_query_id=q["id"],
            text="Готовлю файл…" if data == "apk" else None)
    except TgError:
        pass
    # на сообщение с файлом нельзя «перерисовать» текст — отправляем новое
    msg_id = msg.get("message_id") if msg.get("text") else None
    screen(chat, msg_id, u["id"], u.get("first_name", ""), data)
    save_state()


def setup_bot():
    try:
        api("setMyCommands", commands=[
            {"command": "start", "description": "🏠 Главное меню"},
            {"command": "app", "description": "📲 Скачать приложение"},
            {"command": "premium", "description": f"👑 Premium от {PRICE_MONTH} ⭐"},
            {"command": "codes", "description": "🎟 Мои коды"},
            {"command": "help", "description": "📖 Как установить"},
        ])
        api("setMyShortDescription", short_description="🔥 Прогрев TikTok — аккаунт прогревается сам. "
                                                        "Скачать приложение и Premium за ⭐")
        api("setMyDescription", description="🔥 Прогрев TikTok\n\n"
                                            "Приложение для Android, которое само листает ленту TikTok как живой "
                                            "человек: лайки, избранное, фото-посты, пропуск эфиров и рекламы.\n\n"
                                            "Нажми «Старт», чтобы скачать 👇")
    except Exception:
        traceback.print_exc()


def main():
    global BOT_USERNAME
    me = api("getMe")
    BOT_USERNAME = me.get("username") or BOT_USERNAME
    print(f"Бот @{me['username']} запущен. Админы: {sorted(ADMINS) or 'не заданы'}", flush=True)
    setup_bot()
    delay = 1
    while True:
        try:
            updates = api("getUpdates", _timeout=70, timeout=50, offset=state["offset"],
                          allowed_updates=["message", "callback_query", "pre_checkout_query"])
            delay = 1
        except TgError as e:
            if e.code == 409:
                print("Conflict: этот токен уже использует другой запущенный бот. Остановите его.", flush=True)
                time.sleep(30)
            else:
                print("Ошибка Telegram:", e, flush=True)
                time.sleep(delay)
                delay = min(delay * 2, 60)
            continue
        except Exception as e:
            print("Сеть:", e, flush=True)
            time.sleep(delay)
            delay = min(delay * 2, 60)
            continue
        for upd in updates:
            state["offset"] = upd["update_id"] + 1
            try:
                if "pre_checkout_query" in upd:
                    on_pre_checkout(upd["pre_checkout_query"])
                elif "callback_query" in upd:
                    on_callback(upd["callback_query"])
                elif "message" in upd:
                    on_message(upd["message"])
            except Exception:
                traceback.print_exc()
        if updates:
            save_state()


if __name__ == "__main__":
    main()
