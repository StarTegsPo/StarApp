# ───────────── v63: нейросеть в приложении (Claude), стиль для друзей, анимированные рамки, тема недели ─────────────
# Вставляется после аддона v62, перед строкой «def _single_instance():».
#
# Нейросеть (вариант А): мод отправляет вопрос сюда, бот сам обращается к Claude через официальный SDK.
#   pip install anthropic   (если не ставится — не страшно, бот обратится к API напрямую через aiohttp)
#   ANTHROPIC_API_KEY=...            — ключ ТОЛЬКО на сервере, никогда не кладите его в APK
#   STARTEGS_AI_MODEL=claude-opus-5-5 (по умолчанию; можно, например, claude-haiku-5-5 — дешевле)
# Или DeepSeek (работает из России, оплата проще):
#   DEEPSEEK_API_KEY=sk-...          — ключ с platform.deepseek.com, тоже ТОЛЬКО на сервере
#   STARTEGS_DEEPSEEK_MODEL=deepseek-v4-flash (по умолчанию, быстро и дёшево; умнее — deepseek-v4-pro)
#   STARTEGS_AI_PROVIDER=deepseek | claude — кого спрашивать, если заданы оба ключа (иначе — у кого есть ключ)
#   STARTEGS_AI_EFFORT=low           (low/medium/high — глубина размышлений)
#   STARTEGS_AI_FREE=30, STARTEGS_AI_PRO=300 — запросов в день (бесплатно / Premium)
# ai_start {prompt} → {job}; ai_job {id} → {text, done} (ответ набирается по словам).
#
# Стиль для друзей: look_set {name_color, name_style, cover, lvl, bday} / look_get {ids} → цвет и шрифт имени,
# значок уровня, обложка витрины и 🎂 в день рождения — их видят друзья с модом.
# Магазин: анимированные рамки (пульс, вращение, пламя) и «тема недели» на 7 дней.

import asyncio as _asyncio
import os as _os
import uuid as _uuid

try:
    import anthropic as _anthropic
except Exception:  # пакет не установлен — тогда запрос идёт напрямую через aiohttp
    _anthropic = None

db.executescript("""
CREATE TABLE IF NOT EXISTS mod_ai_use(uid INTEGER, day TEXT, n INTEGER DEFAULT 0, PRIMARY KEY(uid, day));
CREATE TABLE IF NOT EXISTS mod_look(uid INTEGER PRIMARY KEY, name_color INTEGER DEFAULT 0, name_style INTEGER DEFAULT -1,
    cover INTEGER DEFAULT -1, lvl INTEGER DEFAULT 0, bday TEXT DEFAULT '', ts INTEGER DEFAULT 0);
""")
db.commit()

AI_MODEL = _os.environ.get("STARTEGS_AI_MODEL", "claude-opus-5-5")
DS_MODEL = _os.environ.get("STARTEGS_DEEPSEEK_MODEL", "deepseek-v4-flash")
DS_URL = _os.environ.get("STARTEGS_DEEPSEEK_URL", "https://api.deepseek.com/chat/completions")


def _ai_provider() -> str:
    """deepseek / claude / '' (нет ключа)."""
    p = _os.environ.get("STARTEGS_AI_PROVIDER", "").strip().lower()
    has_ds = bool(_os.environ.get("DEEPSEEK_API_KEY"))
    has_cl = bool(_os.environ.get("ANTHROPIC_API_KEY")) or (_anthropic is not None and bool(_os.environ.get("ANTHROPIC_AUTH_TOKEN")))
    if p == "deepseek" and has_ds:
        return "deepseek"
    if p == "claude" and has_cl:
        return "claude"
    return "claude" if has_cl else "deepseek" if has_ds else ""
AI_EFFORT = _os.environ.get("STARTEGS_AI_EFFORT", "low")
AI_FREE = int(_os.environ.get("STARTEGS_AI_FREE", "30"))
AI_PRO = int(_os.environ.get("STARTEGS_AI_PRO", "300"))
AI_MAX_PROMPT = 12000
# Серверный запасной вариант при отказе модели — для моделей, которые его поддерживают
_AI_FALLBACK_MODELS = {"claude-opus-5-5", "claude-opus-5", "claude-sonnet-5-5", "claude-fable-5-2", "claude-fable-5-1"}
AI_SYSTEM = (
    "Ты — Звёздочка, дружелюбный ИИ-помощник в мессенджере StarTegs. Пользователи в основном подростки. "
    "Отвечай на языке вопроса (обычно по-русски), коротко и по делу, живо, можно с эмодзи. "
    "Без markdown-разметки (без **, #, таблиц) — текст показывается как обычное сообщение. "
    "Не помогай с тем, что может навредить человеку или другим; на такие просьбы мягко откажи."
)

_ai_client = None
_ai_jobs = {}  # id → {"uid", "text", "done", "err", "ts"}


def _ai_get_client():
    global _ai_client
    if _ai_client is None and _anthropic is not None:
        _ai_client = _anthropic.AsyncAnthropic()  # ключ берётся из ANTHROPIC_API_KEY
    return _ai_client


def _ai_used_today(uid: int) -> int:
    r = db.execute("SELECT n FROM mod_ai_use WHERE uid=? AND day=?", (uid, time.strftime("%Y-%m-%d"))).fetchone()
    return int(r[0]) if r else 0


def _ai_count(uid: int):
    day = time.strftime("%Y-%m-%d")
    db.execute("INSERT INTO mod_ai_use(uid, day, n) VALUES(?,?,1) ON CONFLICT(uid, day) DO UPDATE SET n = n + 1", (uid, day))
    db.commit()


async def _ai_run_http(job: dict, prompt: str):
    """Запасной путь без пакета anthropic (например, если он не ставится в Termux): прямой запрос к API."""
    import aiohttp
    key = _os.environ.get("ANTHROPIC_API_KEY", "")
    if not key:
        job["err"] = "На сервере не задан ключ нейросети (ANTHROPIC_API_KEY)"
        return
    body = {"model": AI_MODEL, "max_tokens": 4000, "system": AI_SYSTEM,
            "output_config": {"effort": AI_EFFORT}, "messages": [{"role": "user", "content": prompt}]}
    headers = {"x-api-key": key, "anthropic-version": "2023-06-01", "content-type": "application/json"}
    async with aiohttp.ClientSession(timeout=aiohttp.ClientTimeout(total=140)) as s:
        async with s.post("https://api.anthropic.com/v1/messages", json=body, headers=headers) as r:
            data = await r.json(content_type=None)
            if r.status == 401:
                job["err"] = "На сервере неверный ключ нейросети"
                return
            if r.status in (429, 529):
                job["err"] = "Нейросеть сейчас перегружена, попробуй через минуту"
                return
            if r.status != 200:
                log.warning("ai http %s: %s", r.status, str(data)[:300])
                job["err"] = "Нейросеть временно недоступна"
                return
    if data.get("stop_reason") == "refusal":
        job["text"] = "Прости, с этим я помочь не могу 🙏"
        return
    job["text"] = "".join(b.get("text", "") for b in data.get("content", []) if b.get("type") == "text")
    if data.get("stop_reason") == "max_tokens":
        job["text"] += "…"


async def _ai_run_deepseek(job: dict, prompt: str):
    """DeepSeek (формат OpenAI): ответ приходит потоком, как у Claude."""
    import aiohttp
    import json as _json
    body = {"model": DS_MODEL, "max_tokens": 4000, "stream": True,
            "messages": [{"role": "system", "content": AI_SYSTEM}, {"role": "user", "content": prompt}]}
    headers = {"Authorization": "Bearer " + _os.environ.get("DEEPSEEK_API_KEY", ""), "Content-Type": "application/json"}
    finish = None
    async with aiohttp.ClientSession(timeout=aiohttp.ClientTimeout(total=140)) as s:
        async with s.post(DS_URL, json=body, headers=headers) as r:
            if r.status != 200:
                txt = (await r.text())[:300]
                log.warning("deepseek http %s: %s", r.status, txt)
                job["err"] = {401: "На сервере неверный ключ DeepSeek",
                              402: "На счёте DeepSeek закончились деньги — пополни баланс",
                              429: "Нейросеть сейчас перегружена, попробуй через минуту"}.get(r.status, "Нейросеть временно недоступна")
                return
            async for raw in r.content:
                line = raw.decode("utf-8", "ignore").strip()
                if not line.startswith("data:"):
                    continue
                data = line[5:].strip()
                if data == "[DONE]":
                    break
                try:
                    ch = _json.loads(data)["choices"][0]
                except Exception:
                    continue
                piece = (ch.get("delta") or {}).get("content")
                if piece:
                    job["text"] += piece
                finish = ch.get("finish_reason") or finish
    if finish == "content_filter":
        job["text"] = "Прости, с этим я помочь не могу 🙏"
    elif finish == "length":
        job["text"] += "…"


async def _ai_run(job_id: str, prompt: str):
    job = _ai_jobs[job_id]
    client = _ai_get_client()
    if _ai_provider() == "deepseek":
        try:
            await _ai_run_deepseek(job, prompt)
        except Exception:
            log.exception("ai job deepseek")
            job["err"] = "Нейросеть временно недоступна"
        finally:
            job["done"] = True
            job["ts"] = time.time()
        return
    if client is None:
        try:
            await _ai_run_http(job, prompt)
        except Exception:
            log.exception("ai job http")
            job["err"] = "Нейросеть временно недоступна"
        finally:
            job["done"] = True
            job["ts"] = time.time()
        return
    try:
        params = dict(
            model=AI_MODEL,
            max_tokens=4000,
            system=AI_SYSTEM,
            output_config={"effort": AI_EFFORT},
            messages=[{"role": "user", "content": prompt}],
        )
        if AI_MODEL in _AI_FALLBACK_MODELS:
            params["betas"] = ["server-side-fallback-2026-07-01"]
            params["fallbacks"] = "default"
            stream_ctx = client.beta.messages.stream(**params)
        else:
            stream_ctx = client.messages.stream(**params)
        async with stream_ctx as stream:
            async for chunk in stream.text_stream:
                job["text"] += chunk
            final = await stream.get_final_message()
        if final.stop_reason == "refusal":
            job["text"] = "Прости, с этим я помочь не могу 🙏"
        elif final.stop_reason == "max_tokens":
            job["text"] += "…"
    except Exception as e:
        log.exception("ai job")
        if _anthropic is not None and isinstance(e, _anthropic.RateLimitError):
            job["err"] = "Нейросеть сейчас перегружена, попробуй через минуту"
        elif _anthropic is not None and isinstance(e, _anthropic.AuthenticationError):
            job["err"] = "На сервере неверный ключ нейросети"
        else:
            job["err"] = "Нейросеть временно недоступна"
    finally:
        job["done"] = True
        job["ts"] = time.time()


def _ai_cleanup():
    now = time.time()
    for k in [k for k, v in _ai_jobs.items() if v["done"] and now - v["ts"] > 600]:
        _ai_jobs.pop(k, None)


# ---------------------------------------------------------------- рамки и тема недели

V63_ITEMS = [
    ("fr_pulse", "frame", "Пульс (анимация)", "💓", 45),
    ("fr_spin", "frame", "Вихрь (анимация)", "🌀", 55),
    ("fr_flame", "frame", "Живое пламя (анимация)", "🔥", 70),
]
for _it in V63_ITEMS:
    if _it[0] not in MOD_ITEMS:
        MOD_CATALOG.append(_it)
        MOD_ITEMS[_it[0]] = _it

# тема недели: id «th_x_<оттенок>_<градиент 0/1><неон 0/1>» — мод собирает её на телефоне
_WEEKLY_THEMES = [
    ("Кислотный лайм", "🧪", 88, 1, 1), ("Розовый закат", "🌅", 330, 1, 0), ("Глубокий океан", "🐋", 205, 1, 1),
    ("Вишнёвая ночь", "🍒", 350, 0, 1), ("Мятный бриз", "🌿", 160, 1, 0), ("Фиолетовый неон", "🔮", 275, 1, 1),
    ("Янтарное солнце", "🌞", 40, 1, 0), ("Синий лёд", "🧊", 195, 0, 1),
]


def _weekly_theme(now: int = None):
    now = now or int(time.time())
    week = int(time.strftime("%G%V", time.localtime(now)))
    name, emoji, hue, g, n = _WEEKLY_THEMES[week % len(_WEEKLY_THEMES)]
    lt = time.localtime(now)
    end = int(time.mktime((lt.tm_year, lt.tm_mon, lt.tm_mday, 0, 0, 0, 0, 0, -1))) + (7 - lt.tm_wday) * 86400 - 1
    return (f"th_x_{hue}_{g}{n}", "theme", f"{name} · тема недели", emoji, 30), end


_mod_state_v62 = _mod_state


def _mod_state(uid: int) -> dict:
    st = _mod_state_v62(uid)
    it, end = _weekly_theme()
    if it[0] not in MOD_ITEMS:
        MOD_CATALOG.append(it)
        MOD_ITEMS[it[0]] = it
    cat = st.get("catalog", [])
    bought = _bought(uid)
    out = []
    for c in cat:
        if c["id"].startswith("th_x_") and c["id"] != it[0] and c["id"] not in bought:
            continue  # прошлые темы недели — только у тех, кто купил
        if c["id"] == it[0] and it[0] not in bought:
            c = dict(c, until=end)
        out.append(c)
    if not any(c["id"] == it[0] for c in out):
        out.append({"id": it[0], "kind": "theme", "title": it[2], "emoji": it[3], "price": it[4], "until": end})
    st["catalog"] = out
    return st


_mod_api_v62 = _mod_api


async def _mod_api(body: dict):
    iid = str(body.get("item", ""))
    if body.get("a") == "cos_buy" and iid.startswith("th_x_"):
        it, end = _weekly_theme()
        if iid != it[0]:
            return _v61_js({"ok": False, "err": "season", "msg": "Эта тема недели уже закончилась"}, 410)
        if it[0] not in MOD_ITEMS:
            MOD_CATALOG.append(it)
            MOD_ITEMS[it[0]] = it
    return await _mod_api_v62(body)


# ---------------------------------------------------------------- API

def _v63_auth(body: dict, per_min: int):
    u = tg_init_user(str(body.get("init", ""))[:4000])
    if not u:
        return None, _v61_js({"ok": False, "err": "auth"}, 401)
    uid = int(u["id"])
    if not get_user(uid):
        return None, _v61_js({"ok": False, "err": "start", "msg": "Сначала нажми /start в боте"}, 403)
    if is_banned(uid):
        return None, _v61_js({"ok": False, "err": "ban", "msg": "Доступ ограничен"}, 403)
    if not _mod_rate(uid, "v63:" + str(body.get("a")), per_min):
        return None, _v61_js({"ok": False, "err": "rate", "msg": "Слишком часто, подожди минуту"}, 429)
    return uid, None


async def _v63_api(body: dict):
    js = _v61_js
    a = body.get("a")
    uid, err = _v63_auth(body, 200 if a == "ai_job" else 20)
    if err is not None:
        return err
    now = int(time.time())

    if a == "ai_start":
        if not _ai_provider():
            return js({"ok": False, "err": "nokey", "msg": "На сервере StarTegs не задан ключ нейросети"}, 503)
        prompt = str(body.get("prompt", "")).strip()
        if not prompt:
            return js({"ok": False, "err": "bad", "msg": "Пустой вопрос"}, 400)
        prompt = prompt[-AI_MAX_PROMPT:]
        limit = AI_PRO if active(uid) else AI_FREE
        if _ai_used_today(uid) >= limit:
            return js({"ok": False, "err": "limit", "msg": f"Дневной лимит нейросети ({limit}) исчерпан" + ("" if active(uid) else " — в Premium до " + str(AI_PRO))}, 429)
        if sum(1 for v in _ai_jobs.values() if v["uid"] == uid and not v["done"]) >= 2:
            return js({"ok": False, "err": "busy", "msg": "Подожди, пока закончится предыдущий ответ"}, 429)
        _ai_cleanup()
        _ai_count(uid)
        job_id = _uuid.uuid4().hex
        _ai_jobs[job_id] = {"uid": uid, "text": "", "done": False, "err": None, "ts": time.time()}
        _asyncio.get_running_loop().create_task(_ai_run(job_id, prompt))
        return js({"ok": True, "job": job_id, "left": limit - _ai_used_today(uid)})

    if a == "ai_job":
        job = _ai_jobs.get(str(body.get("id", "")))
        if not job or job["uid"] != uid:
            return js({"ok": False, "err": "bad", "msg": "Ответ не найден — спроси ещё раз"}, 404)
        if job["err"]:
            return js({"ok": False, "err": "ai", "msg": job["err"]}, 502)
        return js({"ok": True, "text": job["text"], "done": job["done"]})

    if a == "look_set":
        try:
            color = int(body.get("name_color", 0)) & 0xFFFFFFFF
            style = max(-1, min(11, int(body.get("name_style", -1))))
            cover = max(-1, min(9, int(body.get("cover", -1))))
            lvl = max(0, min(99, int(body.get("lvl", 0))))
        except (TypeError, ValueError):
            return js({"ok": False, "err": "bad"}, 400)
        bday = str(body.get("bday", ""))[:5]
        if bday and not re.fullmatch(r"\d{2}-\d{2}", bday):
            bday = ""
        if (color or style >= 0) and not active(uid):
            return js({"ok": False, "err": "pro", "msg": "Цвет и шрифт имени для друзей — в Premium ⭐"}, 402)
        db.execute("INSERT OR REPLACE INTO mod_look(uid, name_color, name_style, cover, lvl, bday, ts) VALUES(?,?,?,?,?,?,?)",
                   (uid, color, style, cover, lvl, bday, now))
        db.commit()
        return js({"ok": True})

    if a == "look_get":
        ids = body.get("ids")
        if not isinstance(ids, list):
            return js({"ok": False, "err": "bad"}, 400)
        clean = []
        for x in ids[:300]:
            try:
                clean.append(int(x))
            except (TypeError, ValueError):
                pass
        today = time.strftime("%m-%d")
        out = {}
        for i in range(0, len(clean), 100):
            part = clean[i:i + 100]
            q = ",".join("?" * len(part))
            for r in db.execute(f"SELECT uid, name_color, name_style, cover, lvl, bday FROM mod_look WHERE uid IN ({q})", part).fetchall():
                pro = bool(active(r[0]))
                out[str(r[0])] = {"c": r[1] if pro else 0, "s": r[2] if pro else -1, "cv": r[3], "l": r[4], "b": r[5] == today}
        return js({"ok": True, "look": out})

    return js({"ok": False, "err": "bad"}, 400)


_api_handler_v62 = api_handler
_V63_ACTIONS = {"ai_start", "ai_job", "look_set", "look_get"}


async def api_handler(request):
    try:
        body = await request.json()
    except Exception:
        body = None
    if isinstance(body, dict) and body.get("a") in _V63_ACTIONS:
        try:
            return await _v63_api(body)
        except Exception:
            log.exception("v63 api")
            return _v61_js({"ok": False, "err": "srv", "msg": "Ошибка на стороне бота"}, 500)
    return await _api_handler_v62(request)
