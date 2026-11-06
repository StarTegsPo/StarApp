# ───────────── v59: косметика клиента StarTegs и анонимные вопросы ─────────────
# Вставляется в startegs_v58.py перед строкой «def _single_instance():» (после аддона v58).
#
# 1) Косметика мода: рамки на аватарку, эффекты при отправке, темы, звуки.
#    Покупаются за 💎 кристаллы бота (их покупают за Stars в «Кристаллах»), Premium/VIP открывает всё.
#    Друзья с модом видят вашу рамку: клиент спрашивает cos_get по списку id.
# 2) Анонимные вопросы: ссылка t.me/<бот>?start=ask_<код>. Кто перешёл — пишет вопрос,
#    бот пересылает его владельцу без имени. Владелец может заблокировать отправителя, не узнав, кто это.
#
# Авторизация — та же подпись Telegram (initData), что и у мини-приложения.

db.executescript("""
CREATE TABLE IF NOT EXISTS mod_cos(uid INTEGER PRIMARY KEY, frame TEXT DEFAULT '', effect TEXT DEFAULT '', ts INTEGER DEFAULT 0);
CREATE TABLE IF NOT EXISTS ask_code(uid INTEGER PRIMARY KEY, code TEXT UNIQUE);
CREATE TABLE IF NOT EXISTS ask_q(id INTEGER PRIMARY KEY AUTOINCREMENT, owner INTEGER, sender INTEGER, text TEXT, ts INTEGER, seen INTEGER DEFAULT 0);
CREATE TABLE IF NOT EXISTS ask_block(owner INTEGER, sender INTEGER, PRIMARY KEY(owner, sender));
CREATE TABLE IF NOT EXISTS ask_wait(sender INTEGER PRIMARY KEY, owner INTEGER, ts INTEGER);
""")
db.commit()

# id, вид, название, эмодзи, цена в 💎 (0 — бесплатно)
MOD_CATALOG = [
    ("fr_star", "frame", "Звёздная", "⭐", 0),
    ("fr_fire", "frame", "Огненная", "🔥", 30),
    ("fr_neon", "frame", "Неон", "💜", 30),
    ("fr_snow", "frame", "Снежная", "❄️", 20),
    ("fr_hearts", "frame", "Сердечки", "💖", 25),
    ("fr_rainbow", "frame", "Радуга", "🌈", 40),
    ("fr_gold", "frame", "Королевская", "👑", 50),
    ("fr_galaxy", "frame", "Галактика", "🌌", 60),
    ("fx_hearts", "effect", "Сердечки", "❤️", 0),
    ("fx_confetti", "effect", "Конфетти", "🎉", 0),
    ("fx_fire", "effect", "Огонь", "🔥", 15),
    ("fx_bolt", "effect", "Молния", "⚡", 15),
    ("fx_stars", "effect", "Звездопад", "⭐", 15),
    ("fx_snow", "effect", "Снег", "❄️", 10),
    ("fx_money", "effect", "Деньги", "💸", 20),
    ("th_sakura", "theme", "Сакура", "🌸", 25),
    ("th_cyber", "theme", "Киберпанк", "🌃", 35),
    ("th_ocean", "theme", "Океан", "🌊", 25),
    ("th_matcha", "theme", "Матча", "🍵", 25),
    ("th_lava", "theme", "Лава", "🌋", 35),
    ("snd_pack", "sound", "Звуки для друзей", "🔔", 25),
]
MOD_ITEMS = {i[0]: i for i in MOD_CATALOG}
_mod_rl = {}


def _mod_rate(uid: int, key: str, n: int, per: int = 60) -> bool:
    now = time.time()
    k = (uid, key)
    hist = [t for t in _mod_rl.get(k, []) if now - t < per]
    if len(hist) >= n:
        _mod_rl[k] = hist
        return False
    hist.append(now)
    _mod_rl[k] = hist
    return True


def _mod_owned(uid: int):
    if active(uid):
        return [i[0] for i in MOD_CATALOG]  # Premium/VIP — всё открыто
    rows = db.execute("SELECT item FROM inv WHERE uid=? AND item LIKE 'mod:%'", (uid,)).fetchall()
    have = {r[0][4:] for r in rows}
    return [i[0] for i in MOD_CATALOG if i[4] == 0 or i[0] in have]


def _mod_state(uid: int) -> dict:
    r = db.execute("SELECT frame, effect FROM mod_cos WHERE uid=?", (uid,)).fetchone()
    return {
        "crystals": cr_of(uid),
        "premium": bool(active(uid)),
        "owned": _mod_owned(uid),
        "frame": (r[0] if r else "") or "",
        "effect": (r[1] if r else "") or "",
        "catalog": [{"id": i[0], "kind": i[1], "title": i[2], "emoji": i[3], "price": i[4]} for i in MOD_CATALOG],
    }


async def _ask_code(uid: int) -> str:
    r = db.execute("SELECT code FROM ask_code WHERE uid=?", (uid,)).fetchone()
    if r:
        return r[0]
    code = secrets.token_urlsafe(6).replace("-", "x").replace("_", "y")
    db.execute("INSERT OR REPLACE INTO ask_code(uid, code) VALUES(?,?)", (uid, code))
    db.commit()
    return code


async def _mod_api(body: dict):
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

    if a == "cos_state":
        return js({"ok": True, **_mod_state(uid)})

    if a == "cos_buy":
        it = MOD_ITEMS.get(str(body.get("item", "")))
        if not it:
            return js({"ok": False, "err": "bad", "msg": "Нет такого товара"}, 400)
        if it[0] in _mod_owned(uid):
            return js({"ok": True, **_mod_state(uid)})
        if cr_of(uid) < it[4]:
            return js({"ok": False, "err": "money", "msg": f"Нужно {it[4]} 💎 — пополни кристаллы в боте", "need": it[4]}, 402)
        cr_add(uid, -it[4], f"StarTegs: {it[3]} {it[2]}")
        db.execute("INSERT OR IGNORE INTO inv(uid, item) VALUES(?,?)", (uid, "mod:" + it[0]))
        db.commit()
        return js({"ok": True, **_mod_state(uid)})

    if a == "cos_equip":
        frame = str(body.get("frame", "") or "")
        effect = str(body.get("effect", "") or "")
        owned = set(_mod_owned(uid))
        if frame and (frame not in owned or MOD_ITEMS[frame][1] != "frame"):
            return js({"ok": False, "err": "own", "msg": "Сначала купи рамку"}, 403)
        if effect and (effect not in owned or MOD_ITEMS[effect][1] != "effect"):
            return js({"ok": False, "err": "own", "msg": "Сначала купи эффект"}, 403)
        db.execute("INSERT OR REPLACE INTO mod_cos(uid, frame, effect, ts) VALUES(?,?,?,?)", (uid, frame, effect, int(time.time())))
        db.commit()
        return js({"ok": True, **_mod_state(uid)})

    if a == "cos_get":
        ids = body.get("ids")
        if not isinstance(ids, list):
            return js({"ok": False, "err": "bad"}, 400)
        clean = []
        for x in ids[:300]:
            try:
                clean.append(int(x))
            except (TypeError, ValueError):
                pass
        out = {}
        for i in range(0, len(clean), 100):
            part = clean[i:i + 100]
            q = ",".join("?" * len(part))
            for r in db.execute(f"SELECT uid, frame FROM mod_cos WHERE frame != '' AND uid IN ({q})", part).fetchall():
                out[str(r[0])] = r[1]
        return js({"ok": True, "frames": out})

    if a == "ask_link":
        code = await _ask_code(uid)
        return js({"ok": True, "link": f"https://t.me/{await bot_username()}?start=ask_{code}"})

    if a == "ask_list":
        rows = db.execute("SELECT id, text, ts, seen FROM ask_q WHERE owner=? ORDER BY id DESC LIMIT 100", (uid,)).fetchall()
        db.execute("UPDATE ask_q SET seen=1 WHERE owner=?", (uid,))
        db.commit()
        return js({"ok": True, "items": [{"id": r[0], "text": r[1], "ts": r[2], "new": not r[3]} for r in rows]})

    if a in ("ask_del", "ask_block"):
        try:
            qid = int(body.get("id", 0))
        except (TypeError, ValueError):
            qid = 0
        r = db.execute("SELECT sender FROM ask_q WHERE id=? AND owner=?", (qid, uid)).fetchone()
        if not r:
            return js({"ok": False, "err": "bad"}, 400)
        if a == "ask_block":
            db.execute("INSERT OR IGNORE INTO ask_block(owner, sender) VALUES(?,?)", (uid, r[0]))
            db.execute("DELETE FROM ask_q WHERE owner=? AND sender=?", (uid, r[0]))
        else:
            db.execute("DELETE FROM ask_q WHERE id=? AND owner=?", (qid, uid))
        db.commit()
        return js({"ok": True})

    return js({"ok": False, "err": "bad"}, 400)


_api_handler_v58 = api_handler
_MOD_ACTIONS = {"cos_state", "cos_buy", "cos_equip", "cos_get", "ask_link", "ask_list", "ask_del", "ask_block"}


async def api_handler(request):
    try:
        body = await request.json()
    except Exception:
        body = None
    if isinstance(body, dict) and body.get("a") in _MOD_ACTIONS:
        try:
            return await _mod_api(body)
        except Exception:
            log.exception("mod api")
            from aiohttp import web
            return web.json_response({"ok": False, "err": "srv", "msg": "Ошибка на стороне бота"}, status=500, headers=_CORS)
    return await _api_handler_v58(request)


class AskMiddleware(BaseMiddleware):
    """/start ask_<код> → следующий текст пользователя уходит владельцу ссылки анонимно."""

    async def __call__(self, handler, event, data):
        try:
            if isinstance(event, Message) and event.chat.type == "private" and event.from_user and not event.from_user.is_bot:
                text = event.text or ""
                uid = event.from_user.id
                if text.startswith("/start ask_"):
                    code = text.split("ask_", 1)[1].strip()[:20]
                    r = db.execute("SELECT uid FROM ask_code WHERE code=?", (code,)).fetchone()
                    if not r:
                        await event.answer("Ссылка для вопросов не найдена 🤔")
                        return None
                    owner = r[0]
                    if owner == uid:
                        await event.answer("Это твоя ссылка для анонимных вопросов — отправь её друзьям или поставь в био 😉")
                        return None
                    ensure_user(event.from_user)
                    db.execute("INSERT OR REPLACE INTO ask_wait(sender, owner, ts) VALUES(?,?,?)", (uid, owner, int(time.time())))
                    db.commit()
                    await event.answer("🤫 <b>Анонимный вопрос</b>\n\nНапиши вопрос одним сообщением — получатель не узнает, кто ты.\n"
                                       "Будь добрым: за оскорбления можно получить блок.\n\n/cancel — передумал")
                    return None
                w = db.execute("SELECT owner, ts FROM ask_wait WHERE sender=?", (uid,)).fetchone()
                if w and time.time() - w[1] < 1800:
                    if text.startswith("/"):
                        db.execute("DELETE FROM ask_wait WHERE sender=?", (uid,))
                        db.commit()
                        if text.startswith("/cancel"):
                            await event.answer("Ок, вопрос не отправлен.")
                            return None
                        return await handler(event, data)
                    owner = w[0]
                    db.execute("DELETE FROM ask_wait WHERE sender=?", (uid,))
                    db.commit()
                    if not text.strip():
                        await event.answer("Можно отправить только текст 🙂")
                        return None
                    if db.execute("SELECT 1 FROM ask_block WHERE owner=? AND sender=?", (owner, uid)).fetchone():
                        await event.answer("Этот человек не принимает от тебя вопросы.")
                        return None
                    if not _mod_rate(uid, "ask_send", 5, 3600):
                        await event.answer("Слишком много вопросов за час, попробуй позже.")
                        return None
                    q = re.sub(r"https?://\S+|t\.me/\S+|@\w{4,}", "[ссылка скрыта]", text.strip())[:500]
                    db.execute("INSERT INTO ask_q(owner, sender, text, ts) VALUES(?,?,?,?)", (owner, uid, q, int(time.time())))
                    db.commit()
                    await event.answer("✅ Вопрос отправлен анонимно!\n\nХочешь получать вопросы сам? Поставь StarTegs и возьми свою ссылку 😉")
                    try:
                        await bot.send_message(owner, f"📨 <b>Новый анонимный вопрос</b>\n\n{esc(q)}\n\n<i>Ответить красиво — в StarTegs → «Анонимные вопросы»</i>")
                    except Exception:
                        pass
                    return None
        except Exception:
            log.exception("ask middleware")
        return await handler(event, data)


dp.message.outer_middleware(AskMiddleware())
