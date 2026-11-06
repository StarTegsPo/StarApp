# ───────────── v60: витрина профиля, статусы, награды и StarTegs Business ─────────────
# Вставляется после аддона v59, перед строкой «def _single_instance():».
#
# • Витрина и статус: настроение, песня, любимые эмодзи, «чем занят» — видят друзья с модом.
# • Награды: достижения и ежедневные задания мода → монеты ✦, опыт и немного 💎 (каждая — один раз).
# • StarTegs Business: подписка за Stars (payload «biz:<дни>»), статистика продаж в Маркете для графиков.

db.executescript("""
CREATE TABLE IF NOT EXISTS mod_prof(uid INTEGER PRIMARY KEY, mood TEXT DEFAULT '', song TEXT DEFAULT '', emojis TEXT DEFAULT '',
    status TEXT DEFAULT '', status_until INTEGER DEFAULT 0, ts INTEGER DEFAULT 0);
CREATE TABLE IF NOT EXISTS mod_rewards(uid INTEGER, code TEXT, ts INTEGER, PRIMARY KEY(uid, code));
""")
db.commit()

BIZ_PRICE = 299  # ⭐ за 30 дней
BIZ_DAYS = 30

# код достижения → (монеты ✦, опыт, 💎)
MOD_REWARDS = {
    "ach_first_frame": (30, 50, 0), "ach_streak7": (50, 80, 1), "ach_streak30": (150, 200, 3), "ach_streak100": (500, 600, 10),
    "ach_games10": (40, 60, 0), "ach_games50": (120, 150, 2), "ach_win10": (80, 100, 1), "ach_ask10": (40, 60, 0),
    "ach_friends3": (30, 40, 0), "ach_voice_fx": (20, 30, 0), "ach_study7": (60, 80, 1), "ach_sent1000": (100, 150, 2),
    "ach_showcase": (20, 30, 0), "ach_quests7": (100, 120, 2),
}
DAILY_REWARD = (20, 30, 1)
_PROF_LINK = re.compile(r"https?://\S+|t\.me/\S+|@\w{4,}")


def _clean(s, n):
    return _PROF_LINK.sub("", str(s or "")).strip()[:n]


def biz_until(uid: int) -> int:
    return perk_n(uid, "biz")


def biz_on(uid: int) -> bool:
    return uid == ADMIN_ID or biz_until(uid) > time.time()


async def _v60_api(body: dict):
    from aiohttp import web

    def js(d, st=200):
        return web.json_response(d, status=st, headers={**_CORS, "X-Content-Type-Options": "nosniff", "Cache-Control": "no-store"})

    u = tg_init_user(str(body.get("init", ""))[:4000])
    if not u:
        return js({"ok": False, "err": "auth"}, 401)
    uid = int(u["id"])
    if not get_user(uid):
        return js({"ok": False, "err": "start", "msg": "Сначала нажми /start в боте"}, 403)
    if is_banned(uid):
        return js({"ok": False, "err": "ban", "msg": "Доступ ограничен"}, 403)
    a = body.get("a")
    if not _mod_rate(uid, a, 30):
        return js({"ok": False, "err": "rate", "msg": "Слишком часто, подожди минуту"}, 429)
    now = int(time.time())

    if a == "prof_set":
        until = 0
        try:
            until = max(0, min(int(body.get("status_until", 0) or 0), now + 7 * 86400))
        except (TypeError, ValueError):
            pass
        db.execute("INSERT OR REPLACE INTO mod_prof(uid, mood, song, emojis, status, status_until, ts) VALUES(?,?,?,?,?,?,?)",
                   (uid, _clean(body.get("mood"), 40), _clean(body.get("song"), 80), _clean(body.get("emojis"), 24),
                    _clean(body.get("status"), 24), until, now))
        db.commit()
        return js({"ok": True})

    if a == "prof_get":
        ids = body.get("ids")
        if not isinstance(ids, list):
            return js({"ok": False, "err": "bad"}, 400)
        clean = []
        for x in ids[:300]:
            try:
                clean.append(int(x))
            except (TypeError, ValueError):
                pass
        frames, profs = {}, {}
        for i in range(0, len(clean), 100):
            part = clean[i:i + 100]
            q = ",".join("?" * len(part))
            for r in db.execute(f"SELECT uid, frame FROM mod_cos WHERE frame != '' AND uid IN ({q})", part).fetchall():
                frames[str(r[0])] = r[1]
            for r in db.execute(f"SELECT uid, mood, song, emojis, status, status_until FROM mod_prof WHERE uid IN ({q})", part).fetchall():
                st = r[4] if (r[5] == 0 or r[5] > now) else ""
                profs[str(r[0])] = {"mood": r[1], "song": r[2], "emojis": r[3], "status": st}
        return js({"ok": True, "frames": frames, "profiles": profs})

    if a == "reward":
        code = str(body.get("code", ""))[:40]
        today = time.strftime("%Y-%m-%d", time.localtime(now))
        yday = time.strftime("%Y-%m-%d", time.localtime(now - 86400))
        if code.startswith("dq:") and code[3:] in (today, yday):
            prize = DAILY_REWARD
        elif code in MOD_REWARDS:
            prize = MOD_REWARDS[code]
        else:
            return js({"ok": False, "err": "bad", "msg": "Неизвестная награда"}, 400)
        try:
            db.execute("INSERT INTO mod_rewards(uid, code, ts) VALUES(?,?,?)", (uid, code, now))
            db.commit()
        except sqlite3.IntegrityError:
            return js({"ok": False, "err": "dup", "msg": "Награда уже получена"}, 409)
        coins, xp, cr = prize
        add_xp(uid, xp, coins, mult=False, why="StarTegs: " + code)
        if cr:
            cr_add(uid, cr, "StarTegs: награда " + code)
        return js({"ok": True, "coins": coins, "xp": xp, "crystals": cr})

    if a == "biz_state":
        return js({"ok": True, "active": biz_on(uid), "until": biz_until(uid), "price": BIZ_PRICE, "days": BIZ_DAYS})

    if a == "biz_link":
        link = await bot.create_invoice_link(
            title="StarTegs Business", description=f"Бизнес-режим в клиенте StarTegs на {BIZ_DAYS} дней: графики, CRM, неотвеченные, автоответы, ИИ-продавец, контент-план.",
            payload=f"biz:{BIZ_DAYS}", currency="XTR", prices=[LabeledPrice(label=f"Business · {BIZ_DAYS} дней", amount=BIZ_PRICE)])
        return js({"ok": True, "link": link})

    if a == "biz_market":
        if not biz_on(uid):
            return js({"ok": False, "err": "biz", "msg": "Нужна подписка Business"}, 402)
        since = now - 30 * 86400
        rows = db.execute("SELECT o.ts, m.price, m.title, m.id FROM mk_orders o JOIN market m ON m.id = o.lid WHERE m.seller=? AND o.ts>=?", (uid, since)).fetchall()
        days, top = {}, {}
        for ts, price, title, lid in rows:
            d = time.strftime("%Y-%m-%d", time.localtime(ts))
            m = re.search(r"\d[\d\s]*", str(price or ""))
            amount = int(re.sub(r"\s", "", m.group())) if m else 0
            n, s = days.get(d, (0, 0))
            days[d] = (n + 1, s + amount)
            tn, ts_ = top.get(lid, (title, 0))
            top[lid] = (tn, ts_ + 1)
        listings = db.execute("SELECT COUNT(*), COALESCE(SUM(views),0) FROM market WHERE seller=? AND status='active'", (uid,)).fetchone()
        return js({"ok": True, "days": [{"d": d, "n": v[0], "sum": v[1]} for d, v in sorted(days.items())],
                   "top": [{"title": t[0], "n": t[1]} for t in sorted(top.values(), key=lambda x: -x[1])[:5]],
                   "listings": listings[0] if listings else 0, "views": listings[1] if listings else 0})

    return js({"ok": False, "err": "bad"}, 400)


_api_handler_v59 = api_handler
_V60_ACTIONS = {"prof_set", "prof_get", "reward", "biz_state", "biz_link", "biz_market"}


async def api_handler(request):
    try:
        body = await request.json()
    except Exception:
        body = None
    if isinstance(body, dict) and body.get("a") in _V60_ACTIONS:
        try:
            return await _v60_api(body)
        except Exception:
            log.exception("v60 api")
            from aiohttp import web
            return web.json_response({"ok": False, "err": "srv", "msg": "Ошибка на стороне бота"}, status=500, headers=_CORS)
    return await _api_handler_v59(request)


class BizPayMiddleware(BaseMiddleware):
    """Оплата StarTegs Business (payload «biz:30») — до общего обработчика, который считает всё неизвестное подпиской."""

    async def __call__(self, handler, event, data):
        try:
            if isinstance(event, Message) and event.successful_payment and (event.successful_payment.invoice_payload or "").startswith("biz:"):
                p = event.successful_payment
                uid = event.from_user.id
                ensure_user(event.from_user)
                try:
                    days = max(1, min(366, int(p.invoice_payload[4:])))
                except ValueError:
                    days = BIZ_DAYS
                until = perk_extend(uid, "biz", days * 86400)
                db.execute("INSERT INTO pay(uid,charge,ts,amount,kind,meta) VALUES(?,?,?,?,?,?)",
                           (uid, p.telegram_payment_charge_id, int(time.time()), p.total_amount, "biz", days))
                db.commit()
                await event.answer(card("Оплата получена", f"💼 StarTegs Business на <b>{days}</b> дней.", f"📅 До: <b>{when(until)}</b>", "",
                                        "Открой StarTegs → «💼 Бизнес» — графики, клиенты, неотвеченные и автоответы уже ждут."))
                return None
        except Exception:
            log.exception("biz pay")
        return await handler(event, data)


dp.message.outer_middleware(BizPayMiddleware())
