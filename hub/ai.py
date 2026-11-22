"""StarTegs Hub — нейросеть «Звёздочка». Провайдер выбирается переменными окружения:

  OPENROUTER_API_KEY=sk-or-...   бесплатные модели OpenRouter (около 50 запросов в день на ключ)
  DEEPSEEK_API_KEY=sk-...        DeepSeek (недорого), модель DEEPSEEK_MODEL (deepseek-v4-flash)
  ANTHROPIC_API_KEY=sk-ant-...   Claude, модель CLAUDE_MODEL (claude-haiku-5-5)
  AI_PROVIDER=openrouter|deepseek|claude — если задано несколько ключей

Ответ отдаётся потоком (async-генератор кусочков текста).
"""
import json
import os
import time

import aiohttp

SYSTEM = ("Ты — Звёздочка, дружелюбный ИИ-помощник StarTegs в Telegram. Пользователи в основном подростки. "
          "Отвечай по-русски (или на языке вопроса), коротко, тепло и по делу. Можно эмодзи, но без markdown-заголовков "
          "и таблиц. Не помогай с опасным, незаконным и вредным; на такие просьбы мягко отказывай.")

_free = {"ids": [], "ts": 0}


def provider() -> str:
    p = os.environ.get("AI_PROVIDER", "").strip().lower()
    keys = {"claude": "ANTHROPIC_API_KEY", "deepseek": "DEEPSEEK_API_KEY", "openrouter": "OPENROUTER_API_KEY"}
    if p in keys and os.environ.get(keys[p]):
        return p
    for name in ("claude", "deepseek", "openrouter"):
        if os.environ.get(keys[name]):
            return name
    return ""


def title() -> str:
    return {"claude": "Claude", "deepseek": "DeepSeek", "openrouter": "OpenRouter (бесплатно)"}.get(provider(), "не подключена")


async def _free_models(s: aiohttp.ClientSession) -> list:
    if _free["ids"] and time.time() - _free["ts"] < 6 * 3600:
        return _free["ids"]
    try:
        async with s.get("https://openrouter.ai/api/v1/models", timeout=aiohttp.ClientTimeout(total=20)) as r:
            data = await r.json(content_type=None)
        ids = [m["id"] for m in data.get("data", []) if str(m.get("id", "")).endswith(":free")]
        pref = ("deepseek", "qwen", "llama", "gemma", "mistral")
        ids.sort(key=lambda i: next((n for n, k in enumerate(pref) if k in i), len(pref)))
        if ids:
            _free.update(ids=ids, ts=time.time())
    except Exception:
        pass
    return _free["ids"]


class AIError(Exception):
    pass


async def stream(messages: list, max_tokens: int = 1500):
    """messages: [{"role": "user"/"assistant", "content": str}, ...] без системного."""
    p = provider()
    if not p:
        raise AIError("Нейросеть не подключена: админу нужно задать ключ (OPENROUTER_API_KEY — бесплатно)")
    timeout = aiohttp.ClientTimeout(total=150)
    async with aiohttp.ClientSession(timeout=timeout) as s:
        if p == "claude":
            body = {"model": os.environ.get("CLAUDE_MODEL", "claude-haiku-5-5"), "max_tokens": max_tokens,
                    "system": SYSTEM, "messages": messages, "stream": True}
            headers = {"x-api-key": os.environ["ANTHROPIC_API_KEY"], "anthropic-version": "2023-06-01",
                       "content-type": "application/json"}
            async with s.post("https://api.anthropic.com/v1/messages", json=body, headers=headers) as r:
                if r.status != 200:
                    raise AIError(_err(r.status))
                async for raw in r.content:
                    line = raw.decode("utf-8", "ignore").strip()
                    if not line.startswith("data:"):
                        continue
                    try:
                        ev = json.loads(line[5:])
                    except Exception:
                        continue
                    if ev.get("type") == "content_block_delta" and ev.get("delta", {}).get("type") == "text_delta":
                        yield ev["delta"]["text"]
                    elif ev.get("type") == "message_delta" and ev.get("delta", {}).get("stop_reason") == "refusal":
                        yield "\nПрости, с этим я помочь не могу 🙏"
            return
        if p == "deepseek":
            url, key = "https://api.deepseek.com/chat/completions", os.environ["DEEPSEEK_API_KEY"]
            pick = {"model": os.environ.get("DEEPSEEK_MODEL", "deepseek-v4-flash")}
        else:
            url, key = "https://openrouter.ai/api/v1/chat/completions", os.environ["OPENROUTER_API_KEY"]
            model = os.environ.get("OPENROUTER_MODEL", "auto")
            if model == "auto":
                ids = await _free_models(s)
                if not ids:
                    raise AIError("Не нашёл бесплатную нейросеть, попробуй позже")
                pick = {"model": ids[0], "models": ids[:3]}
            else:
                pick = {"model": model}
        body = {**pick, "stream": True, "max_tokens": max_tokens,
                "messages": [{"role": "system", "content": SYSTEM}] + messages}
        headers = {"Authorization": "Bearer " + key, "Content-Type": "application/json", "X-Title": "StarTegs Hub"}
        async with s.post(url, json=body, headers=headers) as r:
            if r.status != 200:
                raise AIError(_err(r.status))
            async for raw in r.content:
                line = raw.decode("utf-8", "ignore").strip()
                if not line.startswith("data:"):
                    continue
                data = line[5:].strip()
                if data == "[DONE]":
                    break
                try:
                    ch = json.loads(data)["choices"][0]
                except Exception:
                    continue
                piece = (ch.get("delta") or {}).get("content")
                if piece:
                    yield piece


def _err(code: int) -> str:
    return {401: "Неверный ключ нейросети", 402: "На счёте нейросети закончились деньги или бесплатный лимит",
            429: "Нейросеть перегружена или лимит на сегодня закончился — попробуй позже"}.get(code, f"Нейросеть ответила ошибкой {code}")


async def ask(messages: list, max_tokens: int = 1500) -> str:
    out = []
    async for piece in stream(messages, max_tokens):
        out.append(piece)
    text = "".join(out).strip()
    return text or "…"
