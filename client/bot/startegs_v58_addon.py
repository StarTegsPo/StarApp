
# ───────────── v58: облачный архив из клиента StarTegs ─────────────
# Клиент StarTegs (мод Telegram) присылает удалённые сообщения, которые он сохранил
# на телефоне. Они попадают в тот же архив, что и сообщения Gram (таблицы msg и ev),
# поэтому ищутся обычным поиском бота, с пометкой «🗑 удалено».
# Авторизация — та же подпись Telegram (initData), что и у мини-приложения.

_api_handler_v57 = api_handler
_modarch_rl = {}
MODARCH_MAX = 200


async def _modarch(body: dict):
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
    now = time.time()
    hist = [t for t in _modarch_rl.get(uid, []) if now - t < 60]
    if len(hist) >= 6:
        return js({"ok": False, "err": "rate", "msg": "Слишком часто, подожди минуту"}, 429)
    hist.append(now)
    _modarch_rl[uid] = hist
    items = body.get("items")
    if not isinstance(items, list):
        return js({"ok": False, "err": "bad"}, 400)
    conn = f"mod:{uid}"
    saved = 0
    for it in items[:MODARCH_MAX]:
        if not isinstance(it, dict):
            continue
        try:
            chat, mid = int(it.get("chat", 0)), int(it.get("mid", 0))
            frm, ts = int(it.get("from", 0) or 0), int(it.get("ts", 0) or 0)
        except (TypeError, ValueError):
            continue
        if not chat or not mid:
            continue
        text = str(it.get("text", "") or "")[:4096]
        name = str(it.get("name", "") or "")[:128] or "?"
        kind = "media" if it.get("media") else None
        if not text and not kind:
            continue
        db.execute(
            "INSERT OR REPLACE INTO msg(conn,chat,mid,uid,name,text,kind,file_id,ts,owner) VALUES(?,?,?,?,?,?,?,?,?,?)",
            (conn, chat, mid, frm, name, text, kind, None, ts or int(now), uid),
        )
        if not db.execute("SELECT 1 FROM ev WHERE owner=? AND chat=? AND mid=? AND kind='del'", (uid, chat, mid)).fetchone():
            db.execute("INSERT INTO ev(chat,mid,kind,ts,owner) VALUES(?,?,?,?,?)", (chat, mid, "del", int(now), uid))
        try:
            fts_index(conn, chat, mid)
        except Exception:
            pass
        saved += 1
    db.commit()
    return js({"ok": True, "saved": saved})


async def api_handler(request):
    try:
        body = await request.json()
    except Exception:
        body = None
    if isinstance(body, dict) and body.get("a") == "modarch":
        try:
            return await _modarch(body)
        except Exception:
            log.exception("modarch")
            from aiohttp import web
            return web.json_response({"ok": False, "err": "srv", "msg": "Ошибка на стороне бота"}, status=500, headers=_CORS)
    return await _api_handler_v57(request)

