import { DatabaseSync } from 'node:sqlite';
import { mkdirSync } from 'node:fs';
import { dirname } from 'node:path';

const SCHEMA = `
PRAGMA journal_mode = WAL;
PRAGMA foreign_keys = ON;

CREATE TABLE IF NOT EXISTS users (
  id         INTEGER PRIMARY KEY AUTOINCREMENT,
  username   TEXT NOT NULL UNIQUE COLLATE NOCASE,
  name       TEXT NOT NULL,
  bio        TEXT NOT NULL DEFAULT '',
  color      INTEGER NOT NULL DEFAULT 0,
  avatar     TEXT,
  pass_hash  TEXT NOT NULL,
  created_at INTEGER NOT NULL,
  last_seen  INTEGER NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS sessions (
  token      TEXT PRIMARY KEY,
  user_id    INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  device     TEXT NOT NULL DEFAULT '',
  created_at INTEGER NOT NULL
);

-- type: direct (личный), group (группа), channel (канал), saved (Избранное)
CREATE TABLE IF NOT EXISTS chats (
  id                INTEGER PRIMARY KEY AUTOINCREMENT,
  type              TEXT NOT NULL,
  title             TEXT NOT NULL DEFAULT '',
  description       TEXT NOT NULL DEFAULT '',
  handle            TEXT UNIQUE COLLATE NOCASE,
  avatar            TEXT,
  direct_key        TEXT UNIQUE,
  pinned_message_id INTEGER,
  created_by        INTEGER NOT NULL REFERENCES users(id),
  created_at        INTEGER NOT NULL
);

CREATE TABLE IF NOT EXISTS members (
  chat_id      INTEGER NOT NULL REFERENCES chats(id) ON DELETE CASCADE,
  user_id      INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  role         TEXT NOT NULL DEFAULT 'member',
  last_read_id INTEGER NOT NULL DEFAULT 0,
  pinned       INTEGER NOT NULL DEFAULT 0,
  muted        INTEGER NOT NULL DEFAULT 0,
  joined_at    INTEGER NOT NULL,
  PRIMARY KEY (chat_id, user_id)
);
CREATE INDEX IF NOT EXISTS members_user ON members(user_id);

CREATE TABLE IF NOT EXISTS messages (
  id          INTEGER PRIMARY KEY AUTOINCREMENT,
  chat_id     INTEGER NOT NULL REFERENCES chats(id) ON DELETE CASCADE,
  user_id     INTEGER NOT NULL REFERENCES users(id),
  kind        TEXT NOT NULL DEFAULT 'text',
  text        TEXT NOT NULL DEFAULT '',
  file        TEXT,
  duration    INTEGER,
  reply_to    INTEGER,
  fwd_user_id INTEGER,
  fwd_chat_id INTEGER,
  fwd_name    TEXT,
  created_at  INTEGER NOT NULL,
  edited_at   INTEGER,
  deleted     INTEGER NOT NULL DEFAULT 0
);
CREATE INDEX IF NOT EXISTS messages_chat ON messages(chat_id, id);
CREATE INDEX IF NOT EXISTS messages_file ON messages(file);

CREATE TABLE IF NOT EXISTS reactions (
  message_id INTEGER NOT NULL REFERENCES messages(id) ON DELETE CASCADE,
  user_id    INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  emoji      TEXT NOT NULL,
  created_at INTEGER NOT NULL,
  PRIMARY KEY (message_id, user_id)
);
`;

// Колонки, добавленные после первой версии, — для баз, созданных раньше
const MIGRATIONS = [
  ['users', 'avatar', 'TEXT'],
  ['chats', 'description', "TEXT NOT NULL DEFAULT ''"],
  ['chats', 'handle', 'TEXT'],
  ['chats', 'avatar', 'TEXT'],
  ['chats', 'pinned_message_id', 'INTEGER'],
  ['members', 'pinned', 'INTEGER NOT NULL DEFAULT 0'],
  ['members', 'muted', 'INTEGER NOT NULL DEFAULT 0'],
  ['messages', 'duration', 'INTEGER'],
  ['messages', 'fwd_user_id', 'INTEGER'],
  ['messages', 'fwd_chat_id', 'INTEGER'],
  ['messages', 'fwd_name', 'TEXT'],
];

export function openDb(path) {
  if (path !== ':memory:') mkdirSync(dirname(path), { recursive: true });
  const db = new DatabaseSync(path);
  db.exec(SCHEMA.split('CREATE TABLE IF NOT EXISTS reactions')[0].replace(/CREATE INDEX[^;]*;/g, ''));
  // В первой версии chats.type был ограничен CHECK (direct/group) — пересоздаём таблицу без ограничения
  const chatsSql = db.prepare("SELECT sql FROM sqlite_master WHERE type = 'table' AND name = 'chats'").get()?.sql || '';
  if (chatsSql.includes('CHECK')) {
    db.exec(`PRAGMA foreign_keys = OFF;
      BEGIN;
      CREATE TABLE chats_new (
        id INTEGER PRIMARY KEY AUTOINCREMENT, type TEXT NOT NULL, title TEXT NOT NULL DEFAULT '',
        direct_key TEXT UNIQUE, created_by INTEGER NOT NULL REFERENCES users(id), created_at INTEGER NOT NULL);
      INSERT INTO chats_new SELECT id, type, title, direct_key, created_by, created_at FROM chats;
      DROP TABLE chats;
      ALTER TABLE chats_new RENAME TO chats;
      COMMIT;
      PRAGMA foreign_keys = ON;`);
  }
  for (const [table, col, type] of MIGRATIONS) {
    const cols = db.prepare(`PRAGMA table_info(${table})`).all().map((c) => c.name);
    if (!cols.includes(col)) db.exec(`ALTER TABLE ${table} ADD COLUMN ${col} ${type}`);
  }
  db.exec(SCHEMA);
  db.exec('CREATE UNIQUE INDEX IF NOT EXISTS chats_handle ON chats(handle COLLATE NOCASE) WHERE handle IS NOT NULL');
  return db;
}
