# ───────────── v64: подключение бота к Claude (MCP-коннектор) ─────────────
# Вставляется после аддона v63, перед строкой «def _single_instance():».
#
# Бот становится MCP-сервером: в claude.ai (Настройки → Коннекторы → «Добавить свой коннектор»)
# или в Claude Code (claude mcp add --transport http startegs <адрес>) можно подключить адрес,
# который бот присылает админу по команде /claude. После этого Claude в чате умеет:
#   смотреть статистику, пользователей и логи, читать базу (только чтение),
#   выдавать 💎 и Premium, банить, писать пользователям и делать рассылки (рассылка — только с подтверждением).
# О каждом изменении бот сразу пишет админу в Telegram.
#
# Защита: в адресе секретный ключ (?key=…), он есть только у админа. /claude new — выдать новый ключ
# (старый перестаёт работать), /claude off — выключить коннектор.
# Если у бота нет кнопки мини-приложения с ?api=…, задайте адрес вручную: STARTEGS_PUBLIC_API=https://…/api

import json as _json64
import os as _os64
import re as _re64
import secrets as _secrets64
import sqlite3 as _sqlite64



db.executescript("""
CREATE TABLE IF NOT EXISTS mod_mcp(k TEXT PRIMARY KEY, v TEXT);
CREATE TABLE IF NOT EXISTS mod_gift(uid INTEGER PRIMARY KEY, until INTEGER);
CREATE TABLE IF NOT EXISTS mod_ban(uid INTEGER PRIMARY KEY, reason TEXT, ts INTEGER);
""")
db.commit()

MCP_PROTOCOLS = ("2025-11-25", "2025-06-18", "2025-03-26", "2024-11-05")
_mcp_rl = []


def _mcp_key(new: bool = False) -> str:
    r = db.execute("SELECT v FROM mod_mcp WHERE k='key'").fetchone()
    if r and r[0] and not new:
        return r[0]
    key = _secrets64.token_urlsafe(24)
    db.execute("INSERT OR REPLACE INTO mod_mcp(k, v) VALUES('key', ?)", (key,))
    db.commit()
    return key


def _mcp_off():
    db.execute("DELETE FROM mod_mcp WHERE k='key'")
    db.commit()


def _mcp_enabled_key() -> str:
    r = db.execute("SELECT v FROM mod_mcp WHERE k='key'").fetchone()
    return r[0] if r and r[0] else ""


# --- Premium в подарок и бан (свои таблицы, чтобы не зависеть от устройства основного бота) ---

_active_v63 = active


def active(uid):
    try:
        r = db.execute("SELECT until FROM mod_gift WHERE uid=?", (int(uid),)).fetchone()
        if r and r[0] > time.time():
            return True
    except Exception:
        pass
    return _active_v63(uid)


_is_banned_v63 = is_banned


def is_banned(uid):
    try:
        if db.execute("SELECT 1 FROM mod_ban WHERE uid=?", (int(uid),)).fetchone():
            return True
    except Exception:
        pass
    return _is_banned_v63(uid)


# --- База только для чтения ---

def _db_ro():
    path = ""
    for row in db.execute("PRAGMA database_list").fetchall():
        if row[1] == "main":
            path = row[2]
    if not path:
        raise RuntimeError("база в памяти — чтение недоступно")
    c = _sqlite64.connect("file:" + path + "?mode=ro", uri=True, timeout=5)
    c.execute("PRAGMA query_only=1")
    return c


def _users_table():
    """Ищет таблицу пользователей основного бота: (имя, колонка с id)."""
    c = _db_ro()
    try:
        tables = [r[0] for r in c.execute("SELECT name FROM sqlite_master WHERE type='table'").fetchall()]
        for t in ("users", "user", "members"):
            if t in tables:
                cols = [r[1] for r in c.execute(f'PRAGMA table_info("{t}")').fetchall()]
                for col in ("uid", "id", "user_id", "tg_id"):
                    if col in cols:
                        return t, col
    finally:
        c.close()
    return None, None


def _rows(sql: str, args=(), limit: int = 200):
    c = _db_ro()
    try:
        cur = c.execute(sql, args)
        cols = [d[0] for d in cur.description] if cur.description else []
        out = []
        for r in cur.fetchmany(limit + 1):
            out.append(dict(zip(cols, r)))
        return out[:limit], len(out) > limit
    finally:
        c.close()


async def _mcp_notify(text: str):
    try:
        await bot.send_message(ADMIN_ID, "🤖 <b>Claude</b>: " + text)
    except Exception:
        log.warning("mcp notify failed", exc_info=True)


def _mcp_logfile() -> str:
    cand = [_os64.environ.get("STARTEGS_LOG", ""), _os64.path.expanduser("~/.startegs-run/logs/startegs.log")]
    for p in cand:
        if p and _os64.path.isfile(p):
            return p
    return ""


# --- Инструменты ---

_UID = {"type": "integer", "description": "Telegram ID пользователя"}

MCP_TOOLS = [
    {"name": "overview", "description": "Сводка по боту StarTegs: пользователи, Premium, нейросеть, покупки за 7 дней.",
     "inputSchema": {"type": "object", "properties": {}}, "annotations": {"readOnlyHint": True}},
    {"name": "db_schema", "description": "Список таблиц базы бота и их колонок.",
     "inputSchema": {"type": "object", "properties": {}}, "annotations": {"readOnlyHint": True}},
    {"name": "db_query", "description": "Выполнить SQL-запрос SELECT к базе бота (SQLite, только чтение, до 200 строк).",
     "inputSchema": {"type": "object", "properties": {"sql": {"type": "string"}}, "required": ["sql"]},
     "annotations": {"readOnlyHint": True}},
    {"name": "user_info", "description": "Всё о пользователе: кристаллы, Premium, пробный период, бан, нейросеть сегодня.",
     "inputSchema": {"type": "object", "properties": {"user_id": _UID}, "required": ["user_id"]},
     "annotations": {"readOnlyHint": True}},
    {"name": "bot_logs", "description": "Последние строки лога бота (можно отфильтровать по слову).",
     "inputSchema": {"type": "object", "properties": {"lines": {"type": "integer", "default": 80},
                                                      "contains": {"type": "string"}}},
     "annotations": {"readOnlyHint": True}},
    {"name": "ai_status", "description": "Какая нейросеть подключена к «Звёздочке», модель и лимиты.",
     "inputSchema": {"type": "object", "properties": {}}, "annotations": {"readOnlyHint": True}},
    {"name": "give_crystals", "description": "Начислить (или списать, если отрицательное число) кристаллы 💎 пользователю.",
     "inputSchema": {"type": "object", "properties": {"user_id": _UID, "amount": {"type": "integer"},
                                                      "reason": {"type": "string"}}, "required": ["user_id", "amount"]}},
    {"name": "gift_premium", "description": "Подарить Premium StarTegs на N дней (0 — забрать подарок).",
     "inputSchema": {"type": "object", "properties": {"user_id": _UID, "days": {"type": "integer"}},
                     "required": ["user_id", "days"]}},
    {"name": "ban_user", "description": "Заблокировать пользователя в боте и моде (или разблокировать: unban=true).",
     "inputSchema": {"type": "object", "properties": {"user_id": _UID, "reason": {"type": "string"},
                                                      "unban": {"type": "boolean", "default": False}},
                     "required": ["user_id"]}, "annotations": {"destructiveHint": True}},
    {"name": "send_message", "description": "Отправить сообщение пользователю от имени бота (HTML-разметка Telegram).",
     "inputSchema": {"type": "object", "properties": {"user_id": _UID, "text": {"type": "string"}},
                     "required": ["user_id", "text"]}},
    {"name": "broadcast", "description": "Рассылка всем пользователям бота. Без confirm=true только показывает, "
                                         "сколько человек получат сообщение. Перед отправкой обязательно спроси владельца.",
     "inputSchema": {"type": "object", "properties": {"text": {"type": "string"},
                                                      "confirm": {"type": "boolean", "default": False}},
                     "required": ["text"]}, "annotations": {"destructiveHint": True}},
]


async def _mcp_tool(name: str, a: dict) -> str:
    now = int(time.time())
    if name == "overview":
        out = {}
        t, col = _users_table()
        if t:
            out["users_total"] = _rows(f'SELECT COUNT(*) n FROM "{t}"')[0][0]["n"]
        for key, sql in (("premium_gifts_active", "SELECT COUNT(*) n FROM mod_gift WHERE until > ?"),
                         ("trials_active", "SELECT COUNT(*) n FROM mod_trial WHERE until > ?")):
            try:
                out[key] = _rows(sql, (now,))[0][0]["n"]
            except Exception:
                pass
        try:
            r = _rows("SELECT COUNT(*) users, COALESCE(SUM(n),0) asks FROM mod_ai_use WHERE day=?", (time.strftime("%Y-%m-%d"),))[0][0]
            out["ai_today"] = r
        except Exception:
            pass
        try:
            out["payments_7d"] = _rows("SELECT kind, COUNT(*) n, SUM(amount) stars FROM pay WHERE ts > ? GROUP BY kind",
                                       (now - 7 * 86400,))[0]
        except Exception:
            pass
        try:
            out["banned_by_claude"] = _rows("SELECT COUNT(*) n FROM mod_ban")[0][0]["n"]
        except Exception:
            pass
        return _json64.dumps(out, ensure_ascii=False)

    if name == "db_schema":
        c = _db_ro()
        try:
            res = {}
            for (t,) in c.execute("SELECT name FROM sqlite_master WHERE type='table' ORDER BY name").fetchall():
                res[t] = [f"{r[1]} {r[2]}" for r in c.execute(f'PRAGMA table_info("{t}")').fetchall()]
        finally:
            c.close()
        return _json64.dumps(res, ensure_ascii=False)

    if name == "db_query":
        sql = str(a.get("sql", "")).strip().rstrip(";")
        if not _re64.match(r"(?is)^\s*(select|with)\b", sql) or ";" in sql:
            return "Можно только один запрос SELECT (или WITH … SELECT)."
        rows, more = _rows(sql)
        return _json64.dumps({"rows": rows, "truncated": more}, ensure_ascii=False, default=str)

    if name == "user_info":
        uid = int(a["user_id"])
        u = get_user(uid)
        info = {"exists": bool(u), "crystals": cr_of(uid) if u else 0, "premium": bool(active(uid)),
                "banned": bool(is_banned(uid))}
        try:
            info["trial_until"] = trial_until(uid)
        except Exception:
            pass
        r = db.execute("SELECT until FROM mod_gift WHERE uid=?", (uid,)).fetchone()
        info["premium_gift_until"] = r[0] if r else 0
        try:
            info["ai_today"] = _ai_used_today(uid)
        except Exception:
            pass
        try:
            info["record"] = dict(u) if u is not None and not isinstance(u, (bool, int)) else None
        except Exception:
            info["record"] = str(u)[:500]
        return _json64.dumps(info, ensure_ascii=False, default=str)

    if name == "bot_logs":
        p = _mcp_logfile()
        if not p:
            return "Файл лога не найден (задайте STARTEGS_LOG=/путь/к/логу)."
        n = max(1, min(400, int(a.get("lines") or 80)))
        with open(p, "rb") as f:
            f.seek(0, 2)
            f.seek(max(0, f.tell() - 400_000))
            lines = f.read().decode("utf-8", "ignore").splitlines()
        w = str(a.get("contains") or "")
        if w:
            lines = [ln for ln in lines if w.lower() in ln.lower()]
        txt = "\n".join(lines[-n:])
        return _re64.sub(r"\d{6,}:[A-Za-z0-9_-]{30,}|sk-[A-Za-z0-9_-]{10,}", "[скрыто]", txt) or "(пусто)"

    if name == "ai_status":
        try:
            prov = _ai_provider()
        except Exception:
            prov = "?"
        model = {"claude": globals().get("AI_MODEL"), "deepseek": globals().get("DS_MODEL"),
                 "openrouter": globals().get("OR_MODEL")}.get(prov)
        return _json64.dumps({"provider": prov or "не подключена", "model": model,
                              "limit_free": globals().get("AI_FREE"), "limit_premium": globals().get("AI_PRO")},
                             ensure_ascii=False)

    if name == "give_crystals":
        uid, amount = int(a["user_id"]), int(a["amount"])
        if not get_user(uid):
            return "Такого пользователя нет в боте (он не нажимал /start)."
        if not -100000 <= amount <= 100000 or amount == 0:
            return "Сумма должна быть от -100000 до 100000 и не 0."
        reason = str(a.get("reason") or "подарок от администрации")[:100]
        cr_add(uid, amount, "StarTegs: " + reason)
        await _mcp_notify(f"{'начислил' if amount > 0 else 'списал'} {abs(amount)} 💎 пользователю <code>{uid}</code> ({esc(reason)})")
        return f"Готово. Баланс пользователя: {cr_of(uid)} 💎"

    if name == "gift_premium":
        uid, days = int(a["user_id"]), int(a["days"])
        if not get_user(uid):
            return "Такого пользователя нет в боте (он не нажимал /start)."
        if days <= 0:
            db.execute("DELETE FROM mod_gift WHERE uid=?", (uid,))
            db.commit()
            await _mcp_notify(f"забрал подаренный Premium у <code>{uid}</code>")
            return "Подаренный Premium снят."
        days = min(days, 3650)
        r = db.execute("SELECT until FROM mod_gift WHERE uid=?", (uid,)).fetchone()
        until = max(now, r[0] if r else 0) + days * 86400
        db.execute("INSERT OR REPLACE INTO mod_gift(uid, until) VALUES(?,?)", (uid, until))
        db.commit()
        await _mcp_notify(f"подарил Premium на {days} дн. пользователю <code>{uid}</code>")
        return "Premium подарен до " + time.strftime("%d.%m.%Y", time.localtime(until))

    if name == "ban_user":
        uid = int(a["user_id"])
        if uid == ADMIN_ID:
            return "Админа заблокировать нельзя."
        if a.get("unban"):
            db.execute("DELETE FROM mod_ban WHERE uid=?", (uid,))
            db.commit()
            await _mcp_notify(f"разблокировал <code>{uid}</code>")
            return "Пользователь разблокирован (если его не блокировали другим способом)."
        reason = str(a.get("reason") or "")[:200]
        db.execute("INSERT OR REPLACE INTO mod_ban(uid, reason, ts) VALUES(?,?,?)", (uid, reason, now))
        db.commit()
        await _mcp_notify(f"заблокировал <code>{uid}</code>" + (f": {esc(reason)}" if reason else ""))
        return "Пользователь заблокирован."

    if name == "send_message":
        uid, text = int(a["user_id"]), str(a.get("text", ""))[:4000]
        if not text.strip():
            return "Пустой текст."
        await bot.send_message(uid, text)
        await _mcp_notify(f"написал пользователю <code>{uid}</code>:\n{esc(text[:300])}")
        return "Отправлено."

    if name == "broadcast":
        text = str(a.get("text", ""))[:4000]
        t, col = _users_table()
        if not t:
            return "Не нашёл таблицу пользователей — рассылка недоступна."
        ids = [r[col] for r in _rows(f'SELECT "{col}" FROM "{t}"', limit=1_000_000)[0]]
        if not a.get("confirm"):
            return f"Сообщение получат {len(ids)} чел. Чтобы отправить, вызови broadcast ещё раз с confirm=true (сначала спроси владельца)."
        if not text.strip():
            return "Пустой текст."
        await _mcp_notify(f"начал рассылку на {len(ids)} чел.:\n{esc(text[:300])}")

        async def run():
            ok = bad = 0
            for i in ids:
                if is_banned(i):
                    continue
                try:
                    await bot.send_message(int(i), text)
                    ok += 1
                except Exception:
                    bad += 1
                await _asyncio.sleep(0.05)
            await _mcp_notify(f"рассылка закончена: доставлено {ok}, не доставлено {bad}")

        _asyncio.get_running_loop().create_task(run())
        return f"Рассылка запущена на {len(ids)} чел. Итог бот пришлёт админу в Telegram."

    raise KeyError(name)


# --- Протокол MCP (Streamable HTTP, без сессий, ответы JSON) ---

async def _mcp_handle(request, body):
    from aiohttp import web
    key = _mcp_enabled_key()
    given = request.query.get("key", "") or request.headers.get("Authorization", "").replace("Bearer ", "", 1)
    if not key or not _secrets64.compare_digest(given.encode(), key.encode()):
        return web.json_response({"jsonrpc": "2.0", "id": None, "error": {"code": -32001, "message": "unauthorized"}}, status=401)
    t = time.time()
    _mcp_rl[:] = [x for x in _mcp_rl if t - x < 60]
    if len(_mcp_rl) >= 120:
        return web.json_response({"jsonrpc": "2.0", "id": None, "error": {"code": -32002, "message": "rate limit"}}, status=429)
    _mcp_rl.append(t)

    async def one(msg):
        if not isinstance(msg, dict):
            return {"jsonrpc": "2.0", "id": None, "error": {"code": -32600, "message": "bad request"}}
        mid, method, params = msg.get("id"), msg.get("method"), msg.get("params") or {}
        if mid is None:  # уведомление — ответ не нужен
            return None

        def ok(res):
            return {"jsonrpc": "2.0", "id": mid, "result": res}

        if method == "initialize":
            want = str(params.get("protocolVersion", ""))
            return ok({"protocolVersion": want if want in MCP_PROTOCOLS else MCP_PROTOCOLS[0],
                       "capabilities": {"tools": {"listChanged": False}},
                       "serverInfo": {"name": "startegs-bot", "version": "64"},
                       "instructions": "Управление ботом StarTegs (мод Telegram). Пиши владельцу по-русски. "
                                       "Перед рассылкой, баном и списанием кристаллов спрашивай подтверждение."})
        if method == "ping":
            return ok({})
        if method == "tools/list":
            return ok({"tools": MCP_TOOLS})
        if method == "tools/call":
            name = str(params.get("name", ""))
            try:
                txt = await _mcp_tool(name, params.get("arguments") or {})
                return ok({"content": [{"type": "text", "text": txt}], "isError": False})
            except KeyError as e:
                if name not in {x["name"] for x in MCP_TOOLS}:
                    return {"jsonrpc": "2.0", "id": mid, "error": {"code": -32602, "message": "unknown tool " + name}}
                return ok({"content": [{"type": "text", "text": "Не хватает параметра: " + str(e)}], "isError": True})
            except Exception as e:
                log.exception("mcp tool %s", name)
                return ok({"content": [{"type": "text", "text": "Ошибка: " + str(e)[:300]}], "isError": True})
        return {"jsonrpc": "2.0", "id": mid, "error": {"code": -32601, "message": "method not found"}}

    if isinstance(body, list):
        res = [r for r in [await one(m) for m in body] if r is not None]
    else:
        res = await one(body)
    if not res:
        return web.Response(status=202)
    return web.json_response(res)


_api_handler_v63 = api_handler


async def api_handler(request):
    try:
        body = await request.json()
    except Exception:
        body = None
    if (isinstance(body, dict) and body.get("jsonrpc") == "2.0") or \
            (isinstance(body, list) and body and isinstance(body[0], dict) and body[0].get("jsonrpc") == "2.0"):
        try:
            return await _mcp_handle(request, body)
        except Exception:
            log.exception("mcp")
            from aiohttp import web
            return web.json_response({"jsonrpc": "2.0", "id": None, "error": {"code": -32603, "message": "internal"}}, status=500)
    return await _api_handler_v63(request)


# --- Команда /claude для админа ---

async def _public_api_url() -> str:
    u = _os64.environ.get("STARTEGS_PUBLIC_API", "").strip()
    if u:
        return u.rstrip("/")
    from urllib.parse import parse_qs, urlparse
    for kw in ({"chat_id": ADMIN_ID}, {}):
        try:
            mb = await bot.get_chat_menu_button(**kw)
            wa = getattr(mb, "web_app", None)
            url = getattr(wa, "url", "") if wa else ""
            for part in (urlparse(url).query, urlparse(url).fragment):
                api = parse_qs(part).get("api")
                if api and api[0].startswith("https://"):
                    return api[0].rstrip("/")
        except Exception:
            pass
    return ""


class ClaudeMcpMiddleware(BaseMiddleware):
    async def __call__(self, handler, event, data):
        try:
            if isinstance(event, Message) and event.from_user and event.from_user.id == ADMIN_ID \
                    and (event.text or "").split("@")[0].split(" ")[0] == "/claude" and event.chat.type == "private":
                arg = (event.text or "").split(" ", 1)[1].strip().lower() if " " in (event.text or "") else ""
                if arg == "off":
                    _mcp_off()
                    await event.answer("🔌 Коннектор Claude выключен. Включить снова: /claude")
                    return None
                key = _mcp_key(new=(arg == "new"))
                api = await _public_api_url()
                if not api:
                    await event.answer("Не нашёл публичный адрес бота. Задайте STARTEGS_PUBLIC_API=https://…/api в .env и перезапустите бота.")
                    return None
                url = f"{api}?key={key}"
                await event.answer(
                    "🤖 <b>Подключение бота к Claude</b>\n\n"
                    f"Адрес коннектора (никому не показывайте!):\n<code>{esc(url)}</code>\n\n"
                    "<b>claude.ai / приложение Claude:</b> Настройки → Коннекторы → «Добавить свой коннектор» → "
                    "название <i>StarTegs</i>, вставить адрес → «Добавить».\n\n"
                    "<b>Claude Code:</b>\n<code>claude mcp add --transport http startegs " + esc(url) + "</code>\n\n"
                    "/claude new — новый ключ (старый перестанет работать)\n/claude off — выключить")
                return None
        except Exception:
            log.exception("claude mcp cmd")
        return await handler(event, data)


dp.message.outer_middleware(ClaudeMcpMiddleware())
