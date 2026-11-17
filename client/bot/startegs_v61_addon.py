# ───────────── v61: пробный Premium, значки у имени, сезонные и ограниченные предметы, темы пользователей ─────────────
# Вставляется после аддона v60, перед строкой «def _single_instance():».
#
# • Пробный Premium: 3 дня один раз на аккаунт (trial_start). Пока идёт — active(uid) = True,
#   так что магазин мода и тариф считают пользователя Premium.
# • Значки у имени (kind «badge»): покупаются за 💎, видны друзьям с модом (badge_set / badge_get).
# • Сезонные предметы: продаются только в свои даты каждый год (Новый год, 14 февраля, 8 марта, Хэллоуин).
# • Товар недели: один предмет со скидкой 40 %, меняется каждый понедельник.
# • Темы пользователей: публикация (theme_pub), витрина (theme_list), покупка (theme_buy) — автору 70 %.

db.executescript("""
CREATE TABLE IF NOT EXISTS mod_trial(uid INTEGER PRIMARY KEY, ts INTEGER, until INTEGER);
CREATE TABLE IF NOT EXISTS mod_badge(uid INTEGER PRIMARY KEY, badge TEXT DEFAULT '', ts INTEGER DEFAULT 0);
CREATE TABLE IF NOT EXISTS mod_themes(id INTEGER PRIMARY KEY AUTOINCREMENT, author INTEGER, name TEXT, hue INTEGER,
    gradient INTEGER DEFAULT 0, neon INTEGER DEFAULT 0, price INTEGER DEFAULT 0, sales INTEGER DEFAULT 0,
    hidden INTEGER DEFAULT 0, ts INTEGER);
CREATE TABLE IF NOT EXISTS mod_theme_own(uid INTEGER, tid INTEGER, ts INTEGER, PRIMARY KEY(uid, tid));
CREATE INDEX IF NOT EXISTS mod_themes_author ON mod_themes(author);
""")
db.commit()

TRIAL_DAYS = 3
THEME_AUTHOR_SHARE = 0.7
THEME_PRICES = (0, 10, 25, 50, 100)
THEMES_PER_USER = 20
DEAL_DISCOUNT = 0.6  # цена товара недели — 60 % от обычной

# id, вид, название, эмодзи, цена в 💎
V61_ITEMS = [
    ("bd_star", "badge", "Звезда", "⭐", 0),
    ("bd_check", "badge", "Галочка", "✅", 40),
    ("bd_crown", "badge", "Корона", "👑", 60),
    ("bd_fire", "badge", "Огонь", "🔥", 20),
    ("bd_bolt", "badge", "Молния", "⚡", 20),
    ("bd_gem", "badge", "Кристалл", "💎", 30),
    ("bd_rocket", "badge", "Ракета", "🚀", 30),
    ("bd_unicorn", "badge", "Единорог", "🦄", 35),
    ("bd_game", "badge", "Геймер", "🎮", 20),
    ("bd_music", "badge", "Меломан", "🎵", 20),
    # сезонные
    ("fr_ny", "frame", "Новогодняя", "🎄", 35),
    ("bd_ny", "badge", "Ёлочка", "🎄", 25),
    ("fr_val", "frame", "Валентинка", "💘", 30),
    ("bd_val", "badge", "Купидон", "💘", 25),
    ("fr_spring", "frame", "Весенняя", "🌷", 25),
    ("bd_spring", "badge", "Тюльпан", "🌷", 20),
    ("fr_hw", "frame", "Хэллоуин", "🎃", 35),
    ("bd_hw", "badge", "Тыква", "🎃", 25),
]
# id → (месяц, день начала), (месяц, день конца) включительно; окно может переходить через Новый год
SEASONS = {
    "fr_ny": ((12, 15), (1, 15)), "bd_ny": ((12, 15), (1, 15)),
    "fr_val": ((2, 7), (2, 16)), "bd_val": ((2, 7), (2, 16)),
    "fr_spring": ((3, 1), (3, 10)), "bd_spring": ((3, 1), (3, 10)),
    "fr_hw": ((10, 20), (11, 3)), "bd_hw": ((10, 20), (11, 3)),
}
for _it in V61_ITEMS:
    if _it[0] not in MOD_ITEMS:
        MOD_CATALOG.append(_it)
        MOD_ITEMS[_it[0]] = _it


def _season_end(item_id: str, now: int = None) -> int:
    """Конец текущего сезона предмета (unix) или 0, если сейчас не сезон."""
    win = SEASONS.get(item_id)
    if not win:
        return 0
    import datetime as _dt
    now = now or int(time.time())
    today = _dt.date.fromtimestamp(now)
    (m1, d1), (m2, d2) = win
    for start_year in (today.year - 1, today.year):
        start = _dt.date(start_year, m1, d1)
        end_year = start_year + (1 if (m2, d2) < (m1, d1) else 0)
        end = _dt.date(end_year, m2, d2)
        if start <= today <= end:
            return int(time.mktime((end + _dt.timedelta(days=1)).timetuple())) - 1
    return 0


def _deal(now: int = None):
    """Товар недели: (id, цена со скидкой, конец недели) — детерминированно по номеру недели."""
    now = now or int(time.time())
    pool = [i for i in MOD_CATALOG if i[4] >= 20 and i[0] not in SEASONS]
    if not pool:
        return None
    week = int(time.strftime("%G%V", time.localtime(now)))
    it = pool[(week * 7919) % len(pool)]
    lt = time.localtime(now)
    days_left = 7 - lt.tm_wday  # до конца воскресенья
    end = int(time.mktime((lt.tm_year, lt.tm_mon, lt.tm_mday, 0, 0, 0, 0, 0, -1))) + days_left * 86400 - 1
    return it[0], max(1, int(it[4] * DEAL_DISCOUNT)), end


def _bought(uid: int):
    rows = db.execute("SELECT item FROM inv WHERE uid=? AND item LIKE 'mod:%'", (uid,)).fetchall()
    return {r[0][4:] for r in rows}


def trial_until(uid: int) -> int:
    r = db.execute("SELECT until FROM mod_trial WHERE uid=?", (uid,)).fetchone()
    return int(r[0]) if r else 0


# Пока идёт пробный период — пользователь считается Premium во всём боте (магазин, тариф мода).
_active_v60 = active


def active(uid):
    try:
        if _active_v60(uid):
            return True
        return trial_until(int(uid)) > time.time()
    except Exception:
        return _active_v60(uid)


_mod_state_v60 = _mod_state


def _mod_state(uid: int) -> dict:
    st = _mod_state_v60(uid)
    now = int(time.time())
    bought = _bought(uid)
    deal = _deal(now)
    cat = []
    for c in st.get("catalog", []):
        iid = c["id"]
        if iid in SEASONS:
            end = _season_end(iid, now)
            if not end and iid not in bought:
                continue  # не сезон и не куплен — скрываем
            if end:
                c = dict(c, until=end)
        if deal and iid == deal[0] and iid not in bought:
            c = dict(c, old=c["price"], price=deal[1], until=deal[2])
        cat.append(c)
    st["catalog"] = cat
    r = db.execute("SELECT badge FROM mod_badge WHERE uid=?", (uid,)).fetchone()
    badge = (r[0] if r else "") or ""
    st["badge"] = badge
    st["badge_emoji"] = MOD_ITEMS[badge][3] if badge in MOD_ITEMS else ""
    st["trial_until"] = trial_until(uid)
    return st


def _v61_js(d, st=200):
    from aiohttp import web
    return web.json_response(d, status=st, headers={**_CORS, "X-Content-Type-Options": "nosniff", "Cache-Control": "no-store"})


def _v61_auth(body: dict):
    u = tg_init_user(str(body.get("init", ""))[:4000])
    if not u:
        return None, _v61_js({"ok": False, "err": "auth"}, 401)
    uid = int(u["id"])
    if not get_user(uid):
        return None, _v61_js({"ok": False, "err": "start", "msg": "Сначала нажми /start в боте"}, 403)
    if is_banned(uid):
        return None, _v61_js({"ok": False, "err": "ban", "msg": "Доступ ограничен"}, 403)
    if not _mod_rate(uid, "v61:" + str(body.get("a")), 30):
        return None, _v61_js({"ok": False, "err": "rate", "msg": "Слишком часто, подожди минуту"}, 429)
    return uid, None


async def _v61_api(body: dict):
    js = _v61_js
    uid, err = _v61_auth(body)
    if err is not None:
        return err
    a = body.get("a")
    now = int(time.time())

    if a == "trial_state":
        return js({"ok": True, "used": trial_until(uid) > 0, "until": trial_until(uid), "days": TRIAL_DAYS})

    if a == "trial_start":
        if trial_until(uid) > 0:
            return js({"ok": False, "err": "dup", "msg": "Пробный Premium уже использован на этом аккаунте"}, 409)
        if _active_v60(uid):
            return js({"ok": False, "err": "have", "msg": "У вас уже есть Premium ⭐"}, 409)
        until = now + TRIAL_DAYS * 86400
        db.execute("INSERT INTO mod_trial(uid, ts, until) VALUES(?,?,?)", (uid, now, until))
        db.commit()
        return js({"ok": True, "until": until})

    if a == "badge_set":
        badge = str(body.get("badge", "") or "")
        if badge:
            it = MOD_ITEMS.get(badge)
            if not it or it[1] != "badge":
                return js({"ok": False, "err": "bad", "msg": "Нет такого значка"}, 400)
            if badge not in _mod_owned(uid) and badge not in _bought(uid):
                return js({"ok": False, "err": "own", "msg": "Сначала купи значок"}, 403)
        db.execute("INSERT OR REPLACE INTO mod_badge(uid, badge, ts) VALUES(?,?,?)", (uid, badge, now))
        db.commit()
        return js({"ok": True, **_mod_state(uid)})

    if a == "badge_get":
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
            for r in db.execute(f"SELECT uid, badge FROM mod_badge WHERE badge != '' AND uid IN ({q})", part).fetchall():
                if r[1] in MOD_ITEMS:
                    out[str(r[0])] = MOD_ITEMS[r[1]][3]
        return js({"ok": True, "badges": out})

    if a == "cos_buy61":  # сезонный предмет или товар недели (цена/доступность по датам)
        iid = str(body.get("item", ""))
        it = MOD_ITEMS.get(iid)
        if not it:
            return js({"ok": False, "err": "bad", "msg": "Нет такого товара"}, 400)
        if iid in _mod_owned(uid) or iid in _bought(uid):
            return js({"ok": True, **_mod_state(uid)})
        price = it[4]
        if iid in SEASONS and not _season_end(iid, now):
            return js({"ok": False, "err": "season", "msg": "Этот предмет сейчас не продаётся — вернётся в свой сезон"}, 410)
        deal = _deal(now)
        if deal and deal[0] == iid:
            price = deal[1]
        if cr_of(uid) < price:
            return js({"ok": False, "err": "money", "msg": f"Нужно {price} 💎 — пополни кристаллы в боте", "need": price}, 402)
        if price:
            cr_add(uid, -price, f"StarTegs: {it[3]} {it[2]}")
        db.execute("INSERT OR IGNORE INTO inv(uid, item) VALUES(?,?)", (uid, "mod:" + iid))
        db.commit()
        return js({"ok": True, **_mod_state(uid)})

    if a == "theme_pub":
        name = _clean(body.get("name"), 32)
        try:
            hue = int(body.get("hue", 270)) % 360
            price = int(body.get("price", 0))
        except (TypeError, ValueError):
            return js({"ok": False, "err": "bad", "msg": "Неверные данные"}, 400)
        if len(name) < 2:
            return js({"ok": False, "err": "bad", "msg": "Название слишком короткое"}, 400)
        if price not in THEME_PRICES:
            return js({"ok": False, "err": "bad", "msg": "Недопустимая цена"}, 400)
        n = db.execute("SELECT COUNT(*) FROM mod_themes WHERE author=? AND hidden=0", (uid,)).fetchone()[0]
        if n >= THEMES_PER_USER:
            return js({"ok": False, "err": "limit", "msg": f"Можно опубликовать до {THEMES_PER_USER} тем"}, 429)
        if not _mod_rate(uid, "theme_pub_hour", 5, 3600):
            return js({"ok": False, "err": "rate", "msg": "Не больше 5 тем в час"}, 429)
        cur = db.execute("INSERT INTO mod_themes(author, name, hue, gradient, neon, price, ts) VALUES(?,?,?,?,?,?,?)",
                         (uid, name, hue, 1 if body.get("gradient") else 0, 1 if body.get("neon") else 0, price, now))
        db.commit()
        return js({"ok": True, "id": cur.lastrowid})

    if a == "theme_list":
        order = "t.sales DESC, t.id DESC" if body.get("sort") != "new" else "t.id DESC"
        rows = db.execute(f"SELECT t.id, t.author, t.name, t.hue, t.gradient, t.neon, t.price, t.sales, "
                          f"EXISTS(SELECT 1 FROM mod_theme_own o WHERE o.uid=? AND o.tid=t.id) "
                          f"FROM mod_themes t WHERE t.hidden=0 ORDER BY {order} LIMIT 60", (uid,)).fetchall()
        prem = bool(active(uid))
        return js({"ok": True, "crystals": cr_of(uid), "themes": [
            {"id": r[0], "name": r[2], "hue": r[3], "gradient": bool(r[4]), "neon": bool(r[5]), "price": r[6], "sales": r[7],
             "mine": r[1] == uid, "owned": bool(r[8]) or prem} for r in rows]})

    if a == "theme_buy":
        try:
            tid = int(body.get("id", 0))
        except (TypeError, ValueError):
            return js({"ok": False, "err": "bad"}, 400)
        t = db.execute("SELECT id, author, name, price FROM mod_themes WHERE id=? AND hidden=0", (tid,)).fetchone()
        if not t:
            return js({"ok": False, "err": "bad", "msg": "Тема не найдена"}, 404)
        if t[1] == uid or db.execute("SELECT 1 FROM mod_theme_own WHERE uid=? AND tid=?", (uid, tid)).fetchone():
            return js({"ok": True, "crystals": cr_of(uid)})
        price = int(t[3] or 0)
        if price and cr_of(uid) < price:
            return js({"ok": False, "err": "money", "msg": f"Нужно {price} 💎 — пополни кристаллы в боте", "need": price}, 402)
        if price:
            cr_add(uid, -price, f"StarTegs: тема «{t[2]}»")
            share = int(price * THEME_AUTHOR_SHARE)
            if share:
                cr_add(t[1], share, f"StarTegs: продажа темы «{t[2]}»")
        db.execute("INSERT OR IGNORE INTO mod_theme_own(uid, tid, ts) VALUES(?,?,?)", (uid, tid, now))
        db.execute("UPDATE mod_themes SET sales = sales + 1 WHERE id=?", (tid,))
        db.commit()
        return js({"ok": True, "crystals": cr_of(uid)})

    return js({"ok": False, "err": "bad"}, 400)


# покупка сезонных предметов и товара недели — со своей ценой и проверкой дат
_mod_api_v60 = _mod_api


async def _mod_api(body: dict):
    if body.get("a") == "cos_buy":
        iid = str(body.get("item", ""))
        d = _deal()
        if iid in SEASONS or (d and d[0] == iid):
            return await _v61_api(dict(body, a="cos_buy61"))
    return await _mod_api_v60(body)


_api_handler_v60 = api_handler
_V61_ACTIONS = {"trial_state", "trial_start", "badge_set", "badge_get", "theme_pub", "theme_list", "theme_buy"}


async def api_handler(request):
    try:
        body = await request.json()
    except Exception:
        body = None
    if isinstance(body, dict) and body.get("a") in _V61_ACTIONS:
        try:
            return await _v61_api(body)
        except Exception:
            log.exception("v61 api")
            return _v61_js({"ok": False, "err": "srv", "msg": "Ошибка на стороне бота"}, 500)
    return await _api_handler_v60(request)
