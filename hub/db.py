"""StarTegs Hub — база данных (SQLite, один файл рядом с ботом)."""
import json
import os
import sqlite3
import time

DB_PATH = os.environ.get("HUB_DB", os.path.join(os.path.dirname(os.path.abspath(__file__)), "hub.db"))

SCHEMA = """
CREATE TABLE IF NOT EXISTS users(
    id INTEGER PRIMARY KEY, name TEXT DEFAULT '', username TEXT DEFAULT '', lang TEXT DEFAULT 'ru',
    joined INTEGER, last_seen INTEGER, banned INTEGER DEFAULT 0,
    notify_deleted INTEGER DEFAULT 1, notify_edited INTEGER DEFAULT 1, ai_used_day TEXT DEFAULT '', ai_used INTEGER DEFAULT 0
);
-- Telegram Business: подключения и сообщения из личных чатов владельца
CREATE TABLE IF NOT EXISTS biz(conn_id TEXT PRIMARY KEY, owner INTEGER, enabled INTEGER, ts INTEGER);
CREATE TABLE IF NOT EXISTS msgs(
    owner INTEGER, chat INTEGER, mid INTEGER, sender INTEGER, sender_name TEXT, chat_name TEXT,
    text TEXT, media_type TEXT, file_id TEXT, date INTEGER, out INTEGER DEFAULT 0,
    deleted INTEGER DEFAULT 0, deleted_at INTEGER DEFAULT 0, edited INTEGER DEFAULT 0,
    PRIMARY KEY(owner, chat, mid)
);
CREATE INDEX IF NOT EXISTS msgs_owner_del ON msgs(owner, deleted, deleted_at);
CREATE TABLE IF NOT EXISTS edits(owner INTEGER, chat INTEGER, mid INTEGER, old TEXT, new TEXT, ts INTEGER);
CREATE INDEX IF NOT EXISTS edits_idx ON edits(owner, chat, mid);
-- ИИ: история диалога
CREATE TABLE IF NOT EXISTS ai_hist(uid INTEGER, role TEXT, content TEXT, ts INTEGER);
CREATE INDEX IF NOT EXISTS ai_hist_uid ON ai_hist(uid, ts);
-- маркет
CREATE TABLE IF NOT EXISTS ads(
    id INTEGER PRIMARY KEY AUTOINCREMENT, uid INTEGER, kind TEXT, cat TEXT, title TEXT, descr TEXT,
    price TEXT, photo TEXT DEFAULT '', city TEXT DEFAULT '', ts INTEGER, active INTEGER DEFAULT 1, views INTEGER DEFAULT 0
);
CREATE INDEX IF NOT EXISTS ads_active ON ads(active, ts);
CREATE TABLE IF NOT EXISTS favs(uid INTEGER, ad INTEGER, PRIMARY KEY(uid, ad));
-- аналитика каналов и групп
CREATE TABLE IF NOT EXISTS channels(id INTEGER PRIMARY KEY, owner INTEGER, title TEXT, username TEXT, kind TEXT, added INTEGER, active INTEGER DEFAULT 1);
CREATE TABLE IF NOT EXISTS snaps(chat INTEGER, ts INTEGER, members INTEGER);
CREATE INDEX IF NOT EXISTS snaps_idx ON snaps(chat, ts);
CREATE TABLE IF NOT EXISTS joins(chat INTEGER, ts INTEGER, delta INTEGER);
CREATE INDEX IF NOT EXISTS joins_idx ON joins(chat, ts);
CREATE TABLE IF NOT EXISTS posts(chat INTEGER, mid INTEGER, ts INTEGER, kind TEXT, PRIMARY KEY(chat, mid));
-- полезное: напоминания и заметки
CREATE TABLE IF NOT EXISTS reminders(id INTEGER PRIMARY KEY AUTOINCREMENT, uid INTEGER, at INTEGER, text TEXT, done INTEGER DEFAULT 0);
CREATE INDEX IF NOT EXISTS rem_at ON reminders(done, at);
CREATE TABLE IF NOT EXISTS notes(id INTEGER PRIMARY KEY AUTOINCREMENT, uid INTEGER, text TEXT, ts INTEGER);
-- связь с приложением StarTegs: стиль для друзей, витрина, облачный архив
CREATE TABLE IF NOT EXISTS looks(uid INTEGER PRIMARY KEY, data TEXT, ts INTEGER);
CREATE TABLE IF NOT EXISTS kv(k TEXT PRIMARY KEY, v TEXT);
-- StarTegs Gold: оплаты звёздами Telegram
-- анонимные вопросы
CREATE TABLE IF NOT EXISTS qa(id INTEGER PRIMARY KEY AUTOINCREMENT, owner INTEGER, asker INTEGER, text TEXT, answer TEXT DEFAULT '', ts INTEGER);
CREATE INDEX IF NOT EXISTS qa_owner ON qa(owner, ts);
CREATE TABLE IF NOT EXISTS qa_block(owner INTEGER, asker INTEGER, PRIMARY KEY(owner, asker));
CREATE TABLE IF NOT EXISTS payments(charge TEXT PRIMARY KEY, uid INTEGER, amount INTEGER, days INTEGER, ts INTEGER, refunded INTEGER DEFAULT 0);
"""
MIGRATIONS = ["ALTER TABLE users ADD COLUMN gold_until INTEGER DEFAULT 0",
              "ALTER TABLE users ADD COLUMN trial_used INTEGER DEFAULT 0",
              "ALTER TABLE users ADD COLUMN ref_by INTEGER DEFAULT 0",
              "ALTER TABLE users ADD COLUMN refs INTEGER DEFAULT 0",
              "ALTER TABLE users ADD COLUMN digest INTEGER DEFAULT 1"]


class DB:
    def __init__(self, path: str = DB_PATH):
        self.c = sqlite3.connect(path, check_same_thread=False)
        self.c.row_factory = sqlite3.Row
        self.c.execute("PRAGMA journal_mode=WAL")
        self.c.executescript(SCHEMA)
        for m in MIGRATIONS:
            try:
                self.c.execute(m)
            except sqlite3.OperationalError:
                pass  # уже применено
        self.c.commit()

    def q(self, sql, args=()):
        return self.c.execute(sql, args).fetchall()

    def one(self, sql, args=()):
        return self.c.execute(sql, args).fetchone()

    def x(self, sql, args=()):
        cur = self.c.execute(sql, args)
        self.c.commit()
        return cur

    # ------------------------------------------------------------ пользователи
    def touch_user(self, u) -> None:
        now = int(time.time())
        name = " ".join(x for x in [getattr(u, "first_name", "") or "", getattr(u, "last_name", "") or ""] if x).strip()
        self.x("INSERT INTO users(id,name,username,joined,last_seen) VALUES(?,?,?,?,?) "
               "ON CONFLICT(id) DO UPDATE SET name=excluded.name, username=excluded.username, last_seen=excluded.last_seen",
               (u.id, name, getattr(u, "username", "") or "", now, now))

    def user(self, uid: int):
        return self.one("SELECT * FROM users WHERE id=?", (uid,))

    # ------------------------------------------------------------ StarTegs Gold
    def gold_until(self, uid: int) -> int:
        r = self.one("SELECT gold_until FROM users WHERE id=?", (uid,))
        return int(r["gold_until"] or 0) if r else 0

    def gold(self, uid: int) -> bool:
        return self.gold_until(uid) > int(time.time())

    def add_gold(self, uid: int, days: int) -> int:
        until = max(self.gold_until(uid), int(time.time())) + days * 86400
        self.x("UPDATE users SET gold_until=? WHERE id=?", (until, uid))
        return until

    def trial(self, uid: int, days: int):
        """Пробный Gold один раз на аккаунт Telegram. Возвращает дату окончания или None."""
        r = self.user(uid)
        if not r or r["trial_used"]:
            return None
        self.x("UPDATE users SET trial_used=1 WHERE id=?", (uid,))
        return self.add_gold(uid, days)

    def kv_get(self, k, default=None):
        r = self.one("SELECT v FROM kv WHERE k=?", (k,))
        return json.loads(r["v"]) if r else default

    def kv_set(self, k, v):
        self.x("INSERT OR REPLACE INTO kv(k,v) VALUES(?,?)", (k, json.dumps(v, ensure_ascii=False)))
