"""Тесты StarTegs Hub без Telegram: подпись initData, API приложения и мини-приложения, разбор времени.

Запуск:  cd hub && python -m unittest test_hub -v
"""
import asyncio
import hashlib
import hmac
import json
import os
import tempfile
import time
import unittest
from urllib.parse import urlencode

TOKEN = "123456:TEST_TOKEN"
os.environ["BOT_TOKEN"] = TOKEN
os.environ["HUB_DB"] = os.path.join(tempfile.mkdtemp(), "t.db")
for k in ("OPENROUTER_API_KEY", "DEEPSEEK_API_KEY", "ANTHROPIC_API_KEY"):
    os.environ.pop(k, None)

from aiohttp.test_utils import TestClient, TestServer  # noqa: E402

import bot  # noqa: E402


def make_init(uid: int, name: str = "Маша", age: int = 0) -> str:
    pairs = {"auth_date": str(int(time.time()) - age), "query_id": "q1",
             "user": json.dumps({"id": uid, "first_name": name, "username": "masha"}, ensure_ascii=False)}
    data = "\n".join(f"{k}={v}" for k, v in sorted(pairs.items()))
    secret = hmac.new(b"WebAppData", TOKEN.encode(), hashlib.sha256).digest()
    pairs["hash"] = hmac.new(secret, data.encode(), hashlib.sha256).hexdigest()
    return urlencode(pairs)


class InitTest(unittest.TestCase):
    def test_valid(self):
        u = bot.check_init(make_init(42))
        self.assertEqual(u["id"], 42)

    def test_tampered(self):
        self.assertIsNone(bot.check_init(make_init(42).replace("42", "43")))

    def test_old(self):
        self.assertIsNone(bot.check_init(make_init(42, age=8 * 86400)))

    def test_empty(self):
        self.assertIsNone(bot.check_init(""))


class WhenTest(unittest.TestCase):
    def test_minutes(self):
        self.assertAlmostEqual(bot.parse_when("30m"), bot.now() + 1800, delta=2)
        self.assertAlmostEqual(bot.parse_when("2ч"), bot.now() + 7200, delta=2)

    def test_clock(self):
        at = bot.parse_when("18:30")
        self.assertGreater(at, bot.now())
        self.assertEqual(time.localtime(at).tm_hour, 18)

    def test_bad(self):
        self.assertIsNone(bot.parse_when("завтра"))


class ApiTest(unittest.TestCase):
    def run_async(self, coro):
        return asyncio.new_event_loop().run_until_complete(coro)

    async def _client(self):
        c = TestClient(TestServer(bot.make_web()))
        await c.start_server()
        return c

    def test_app_protocol(self):
        async def go():
            c = await self._client()
            try:
                a, b = make_init(1001, "Аня"), make_init(1002, "Боря")
                r = await c.post("/api", json={"init": "bad", "a": "ping"})
                self.assertEqual(r.status, 401)
                r = await (await c.post("/api", json={"init": a, "a": "state"})).json()
                self.assertEqual(r["state"]["tier"], "VIP")
                r = await (await c.post("/api", json={"init": a, "a": "cos_equip", "frame": "fr_fire", "effect": "fx_bolt"})).json()
                self.assertEqual(r["frame"], "fr_fire")
                await c.post("/api", json={"init": a, "a": "badge_set", "badge": "bd_crown"})
                await c.post("/api", json={"init": a, "a": "look_set", "name_color": -16711936, "name_style": 3, "cover": 2, "lvl": 7, "bday": "01-01"})
                await c.post("/api", json={"init": a, "a": "prof_set", "mood": "😎 на чиле", "status": "учусь"})
                r = await (await c.post("/api", json={"init": b, "a": "prof_get", "ids": [1001, 999]})).json()
                self.assertEqual(r["frames"]["1001"], "fr_fire")
                self.assertEqual(r["profiles"]["1001"]["mood"], "😎 на чиле")
                self.assertEqual(r["badges"]["1001"], "bd_crown")
                self.assertEqual(r["look"]["1001"]["s"], 3)
                self.assertNotIn("999", r["frames"])
                r = await (await c.post("/api", json={"init": a, "a": "modarch", "items": [{"chat": 5, "mid": 7, "from": 5, "ts": 100, "text": "привет", "name": "Петя"}]})).json()
                self.assertEqual(r["saved"], 1)
                r = await (await c.post("/api", json={"init": a, "a": "ai_start", "prompt": "hi"})).json()
                self.assertEqual(r["err"], "nokey")
                r = await c.post("/api", json={"init": a, "a": "wallet_send"})
                self.assertEqual(r.status, 400)
            finally:
                await c.close()
        self.run_async(go())

    def test_mini(self):
        async def go():
            c = await self._client()
            try:
                a = make_init(2001, "Лиза")
                r = await (await c.post("/mini/api", json={"init": a, "a": "home"})).json()
                self.assertTrue(r["ok"])
                self.assertFalse(r["connected"])
                r = await (await c.post("/mini/api", json={"init": a, "a": "market_add", "title": "AirPods", "price": "5000 ₽", "cat": "📱 Техника"})).json()
                ad = r["id"]
                r = await (await c.post("/mini/api", json={"init": a, "a": "market", "q": "air"})).json()
                self.assertEqual(r["items"][0]["title"], "AirPods")
                self.assertTrue(r["items"][0]["mine"])
                await c.post("/mini/api", json={"init": a, "a": "market_fav", "id": ad})
                r = await (await c.post("/mini/api", json={"init": a, "a": "market", "favs": True})).json()
                self.assertEqual(len(r["items"]), 1)
                await c.post("/mini/api", json={"init": a, "a": "market_del", "id": ad})
                r = await (await c.post("/mini/api", json={"init": a, "a": "market"})).json()
                self.assertEqual(r["items"], [])
                await c.post("/mini/api", json={"init": a, "a": "note_add", "text": "купить молоко"})
                r = await (await c.post("/mini/api", json={"init": a, "a": "notes"})).json()
                self.assertEqual(r["items"][0]["text"], "купить молоко")
                r = await (await c.post("/mini/api", json={"init": a, "a": "reminder_add", "text": "позвонить", "when": "1h"})).json()
                self.assertTrue(r["ok"])
                r = await (await c.post("/mini/api", json={"init": a, "a": "reminder_add", "when": "когда-нибудь"})).json()
                self.assertFalse(r["ok"])
                await c.post("/mini/api", json={"init": a, "a": "settings_set", "key": "notify_edited", "value": False})
                r = await (await c.post("/mini/api", json={"init": a, "a": "home"})).json()
                self.assertEqual(r["notify_edited"], 0)
                r = await (await c.post("/mini/api", json={"init": a, "a": "ai", "text": "привет"})).json()
                self.assertFalse(r["ok"])  # ключа нейросети нет
                r = await c.get("/app/")
                self.assertEqual(r.status, 200)
                self.assertIn("StarTegs", await r.text())
            finally:
                await c.close()
        self.run_async(go())

    def test_deleted_and_stats(self):
        bot.db.x("INSERT INTO msgs(owner, chat, mid, sender, sender_name, chat_name, text, date, deleted, deleted_at) VALUES(3001,5,1,5,'Петя','Петя','секрет',1,1,?)", (bot.now(),))
        self.assertEqual(len(bot.deleted_list(3001)), 1)
        self.assertEqual(len(bot.deleted_list(3001, q="секр")), 1)
        self.assertEqual(len(bot.deleted_list(3001, q="нет")), 0)
        t = bot.now()
        for ts, n in ((t - 8 * 86400, 100), (t - 2 * 86400, 120), (t - 100, 130)):
            bot.db.x("INSERT INTO snaps(chat, ts, members) VALUES(?,?,?)", (-777, ts, n))
        s = bot.channel_stats(-777)
        self.assertEqual(s["members"], 130)
        self.assertEqual(s["d1"], 10)
        self.assertEqual(s["d7"], 30)


if __name__ == "__main__":
    unittest.main()
