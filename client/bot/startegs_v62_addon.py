# ───────────── v62: кошелёк 💎, чаевые авторам тем, колесо бонусов, значок «Поддержал StarTegs» ─────────────
# Вставляется после аддона v61, перед строкой «def _single_instance():».
#
# • wallet_send {to, amount}: перевод кристаллов другу (до 500 💎 в сутки, получатель должен был нажать /start).
# • theme_tip {id, amount}: чаевые автору темы (до 100 💎 за раз).
# • wheel_spin: раз в 20 часов — случайный косметический предмет из магазина (если всё уже есть — 3 💎).
# • donate_link {stars}: счёт в Stars, payload «donate:<n>» → значок «🫶 Поддержал StarTegs» навсегда.

db.executescript("""
CREATE TABLE IF NOT EXISTS mod_wallet_log(id INTEGER PRIMARY KEY AUTOINCREMENT, uid INTEGER, to_uid INTEGER, amount INTEGER, kind TEXT, ts INTEGER);
CREATE TABLE IF NOT EXISTS mod_wheel(uid INTEGER PRIMARY KEY, ts INTEGER);
CREATE INDEX IF NOT EXISTS mod_wallet_log_uid ON mod_wallet_log(uid, ts);
""")
db.commit()

WALLET_DAY_LIMIT = 500
TIP_MAX = 100
WHEEL_COOLDOWN = 20 * 3600
DONATE_OPTIONS = (50, 100, 250, 500)

if "bd_supporter" not in MOD_ITEMS:
    _sup = ("bd_supporter", "badge", "Поддержал StarTegs", "🫶", 0)
    MOD_CATALOG.append(_sup)
    MOD_ITEMS["bd_supporter"] = _sup
SPECIAL_ITEMS = {"bd_supporter"}  # не продаются и не выпадают в колесе


def _sent_today(uid: int) -> int:
    r = db.execute("SELECT COALESCE(SUM(amount),0) FROM mod_wallet_log WHERE uid=? AND ts>=?", (uid, int(time.time()) - 86400)).fetchone()
    return int(r[0] or 0)


_mod_state_v61 = _mod_state


def _mod_state(uid: int) -> dict:
    st = _mod_state_v61(uid)
    bought = _bought(uid)
    st["catalog"] = [c for c in st.get("catalog", []) if c["id"] not in SPECIAL_ITEMS or c["id"] in bought]
    if "bd_supporter" in bought and "bd_supporter" not in st.get("owned", []):
        st["owned"] = list(st.get("owned", [])) + ["bd_supporter"]
    return st


_mod_owned_v61 = _mod_owned


def _mod_owned(uid: int):
    owned = [i for i in _mod_owned_v61(uid) if i not in SPECIAL_ITEMS]  # Premium не открывает значок поддержки
    if "bd_supporter" in _bought(uid):
        owned.append("bd_supporter")
    return owned


async def _v62_api(body: dict):
    js = _v61_js
    uid, err = _v61_auth(body)
    if err is not None:
        return err
    a = body.get("a")
    now = int(time.time())

    if a == "wallet_send":
        try:
            to = int(body.get("to", 0))
            amount = int(body.get("amount", 0))
        except (TypeError, ValueError):
            return js({"ok": False, "err": "bad", "msg": "Неверные данные"}, 400)
        if to == uid or amount <= 0:
            return js({"ok": False, "err": "bad", "msg": "Неверная сумма или получатель"}, 400)
        if not get_user(to):
            return js({"ok": False, "err": "nouser", "msg": "Получатель ещё не запускал бота — попросите его нажать /start"}, 404)
        if _sent_today(uid) + amount > WALLET_DAY_LIMIT:
            return js({"ok": False, "err": "limit", "msg": f"Можно переводить до {WALLET_DAY_LIMIT} 💎 в сутки"}, 429)
        if cr_of(uid) < amount:
            return js({"ok": False, "err": "money", "msg": f"Не хватает: на балансе {cr_of(uid)} 💎"}, 402)
        cr_add(uid, -amount, f"Перевод другу ({to})")
        cr_add(to, amount, f"Перевод от друга ({uid})")
        db.execute("INSERT INTO mod_wallet_log(uid, to_uid, amount, kind, ts) VALUES(?,?,?,?,?)", (uid, to, amount, "send", now))
        db.commit()
        try:
            await bot.send_message(to, f"💎 Вам перевели <b>{amount}</b> кристаллов через StarTegs!")
        except Exception:
            pass
        return js({"ok": True, "crystals": cr_of(uid)})

    if a == "theme_tip":
        try:
            tid = int(body.get("id", 0))
            amount = int(body.get("amount", 0))
        except (TypeError, ValueError):
            return js({"ok": False, "err": "bad"}, 400)
        if amount <= 0 or amount > TIP_MAX:
            return js({"ok": False, "err": "bad", "msg": f"Чаевые — от 1 до {TIP_MAX} 💎"}, 400)
        t = db.execute("SELECT author, name FROM mod_themes WHERE id=? AND hidden=0", (tid,)).fetchone()
        if not t:
            return js({"ok": False, "err": "bad", "msg": "Тема не найдена"}, 404)
        if t[0] == uid:
            return js({"ok": False, "err": "self", "msg": "Это ваша тема 🙂"}, 400)
        if _sent_today(uid) + amount > WALLET_DAY_LIMIT:
            return js({"ok": False, "err": "limit", "msg": f"Можно отдавать до {WALLET_DAY_LIMIT} 💎 в сутки"}, 429)
        if cr_of(uid) < amount:
            return js({"ok": False, "err": "money", "msg": f"Не хватает: на балансе {cr_of(uid)} 💎"}, 402)
        cr_add(uid, -amount, f"Чаевые за тему «{t[1]}»")
        cr_add(t[0], amount, f"Чаевые за вашу тему «{t[1]}»")
        db.execute("INSERT INTO mod_wallet_log(uid, to_uid, amount, kind, ts) VALUES(?,?,?,?,?)", (uid, t[0], amount, "tip", now))
        db.commit()
        return js({"ok": True, "crystals": cr_of(uid)})

    if a == "wheel_spin":
        r = db.execute("SELECT ts FROM mod_wheel WHERE uid=?", (uid,)).fetchone()
        if r and now - r[0] < WHEEL_COOLDOWN:
            left = WHEEL_COOLDOWN - (now - r[0])
            return js({"ok": False, "err": "wait", "msg": f"Колесо отдыхает ещё {left // 3600} ч {left % 3600 // 60} мин"}, 429)
        owned = set(_mod_owned(uid)) | _bought(uid)
        pool = [i for i in MOD_CATALOG if i[0] not in owned and i[0] not in SPECIAL_ITEMS and i[0] not in SEASONS
                and i[1] in ("frame", "effect", "badge") and 0 < i[4] <= 30]
        db.execute("INSERT OR REPLACE INTO mod_wheel(uid, ts) VALUES(?,?)", (uid, now))
        if pool:
            it = secrets.choice(pool)
            db.execute("INSERT OR IGNORE INTO inv(uid, item) VALUES(?,?)", (uid, "mod:" + it[0]))
            db.commit()
            return js({"ok": True, "prize": f"{it[3]} {it[2]}", "item": it[0]})
        db.commit()
        cr_add(uid, 3, "StarTegs: колесо бонусов")
        return js({"ok": True, "prize": "💎 3 кристалла (вся косметика уже ваша!)"})

    if a == "donate_link":
        try:
            stars = int(body.get("stars", 0))
        except (TypeError, ValueError):
            stars = 0
        if stars not in DONATE_OPTIONS:
            return js({"ok": False, "err": "bad", "msg": "Недопустимая сумма"}, 400)
        link = await bot.create_invoice_link(
            title="Поддержать StarTegs", description="Спасибо за поддержку! Значок «🫶 Поддержал StarTegs» у имени — навсегда.",
            payload=f"donate:{stars}", currency="XTR", prices=[LabeledPrice(label="Поддержка StarTegs", amount=stars)])
        return js({"ok": True, "link": link})

    return js({"ok": False, "err": "bad"}, 400)


# значок поддержки нельзя купить за кристаллы
_mod_api_v61 = _mod_api


async def _mod_api(body: dict):
    if body.get("a") == "cos_buy" and str(body.get("item", "")) in SPECIAL_ITEMS:
        return _v61_js({"ok": False, "err": "special", "msg": "Этот значок выдаётся только за поддержку StarTegs"}, 403)
    return await _mod_api_v61(body)


_api_handler_v61 = api_handler
_V62_ACTIONS = {"wallet_send", "theme_tip", "wheel_spin", "donate_link"}


async def api_handler(request):
    try:
        body = await request.json()
    except Exception:
        body = None
    if isinstance(body, dict) and body.get("a") in _V62_ACTIONS:
        try:
            return await _v62_api(body)
        except Exception:
            log.exception("v62 api")
            return _v61_js({"ok": False, "err": "srv", "msg": "Ошибка на стороне бота"}, 500)
    return await _api_handler_v61(request)


class DonatePayMiddleware(BaseMiddleware):
    """Донат (payload «donate:<n>») — значок «Поддержал StarTegs»."""

    async def __call__(self, handler, event, data):
        try:
            if isinstance(event, Message) and event.successful_payment and (event.successful_payment.invoice_payload or "").startswith("donate:"):
                p = event.successful_payment
                uid = event.from_user.id
                ensure_user(event.from_user)
                db.execute("INSERT OR IGNORE INTO inv(uid, item) VALUES(?,?)", (uid, "mod:bd_supporter"))
                db.execute("INSERT INTO pay(uid,charge,ts,amount,kind,meta) VALUES(?,?,?,?,?,?)",
                           (uid, p.telegram_payment_charge_id, int(time.time()), p.total_amount, "donate", 0))
                db.commit()
                await event.answer("🫶 Спасибо за поддержку StarTegs!\n\nЗначок «Поддержал StarTegs» уже ваш: StarTegs → Оформление → Магазин → Значки.")
                return None
        except Exception:
            log.exception("donate pay")
        return await handler(event, data)


dp.message.outer_middleware(DonatePayMiddleware())
