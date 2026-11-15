import { DatabaseSync } from 'node:sqlite';
import { readConfig } from './config.js';

let dbInstance = null;

export function openDb(path) {
  if (dbInstance) return dbInstance;
  dbInstance = new DatabaseSync(path);
  return dbInstance;
}

export function migrate(db, services) {
  db.exec(`
    CREATE TABLE IF NOT EXISTS users (
      id INTEGER PRIMARY KEY,
      username TEXT,
      first_name TEXT,
      last_name TEXT,
      balance INTEGER DEFAULT 0,
      created_at INTEGER DEFAULT (strftime('%s', 'now'))
    );
    CREATE TABLE IF NOT EXISTS transactions (
      id INTEGER PRIMARY KEY AUTOINCREMENT,
      user_id INTEGER NOT NULL,
      amount INTEGER NOT NULL,
      kind TEXT NOT NULL,
      description TEXT,
      ref_id TEXT,
      created_at INTEGER DEFAULT (strftime('%s', 'now')),
      FOREIGN KEY(user_id) REFERENCES users(id)
    );
  `);

  for (const s of services) {
    if (s.migrations) {
      for (const sql of s.migrations) db.exec(sql);
    }
  }
}

export function upsertUser(db, from) {
  const now = Math.floor(Date.now() / 1000);
  db.prepare(
    `INSERT INTO users (id, username, first_name, last_name, created_at)
     VALUES (?, ?, ?, ?, ?)
     ON CONFLICT(id) DO UPDATE SET username=excluded.username, first_name=excluded.first_name, last_name=excluded.last_name`
  ).run(from.id, from.username || null, from.first_name || '', from.last_name || '', now);
}

export function changeBalance(db, userId, amount, kind, description, refId = null) {
  const stmt = db.prepare(
    `INSERT INTO transactions (user_id, amount, kind, description, ref_id) VALUES (?, ?, ?, ?, ?)`
  );
  stmt.run(userId, amount, kind, description, refId);
  const row = db.prepare('UPDATE users SET balance = balance + ? WHERE id = ? RETURNING balance').get(amount, userId);
  return row?.balance ?? 0;
}