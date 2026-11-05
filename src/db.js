import { DatabaseSync } from 'node:sqlite';
import { mkdirSync } from 'node:fs';
import { dirname } from 'node:path';

const CORE_MIGRATIONS = [
  `CREATE TABLE IF NOT EXISTS users (
     id INTEGER PRIMARY KEY,           -- Telegram user id
     username TEXT,
     first_name TEXT,
     last_name TEXT,
     language_code TEXT,
     balance INTEGER NOT NULL DEFAULT 0, -- баланс во внутренних звёздах
     created_at INTEGER NOT NULL DEFAULT (unixepoch()),
     last_seen_at INTEGER NOT NULL DEFAULT (unixepoch())
   )`,
  `CREATE TABLE IF NOT EXISTS transactions (
     id INTEGER PRIMARY KEY AUTOINCREMENT,
     user_id INTEGER NOT NULL REFERENCES users(id),
     amount INTEGER NOT NULL,           -- >0 пополнение, <0 списание
     kind TEXT NOT NULL,                -- topup | purchase | refund | bonus
     description TEXT,
     telegram_charge_id TEXT,
     created_at INTEGER NOT NULL DEFAULT (unixepoch())
   )`,
];

/** Открывает БД и применяет миграции ядра и сервисов (все идемпотентны). */
export function openDb(path, services = []) {
  if (path !== ':memory:') mkdirSync(dirname(path), { recursive: true });
  const db = new DatabaseSync(path);
  db.exec('PRAGMA journal_mode = WAL; PRAGMA foreign_keys = ON;');
  for (const sql of CORE_MIGRATIONS) db.exec(sql);
  for (const s of services) for (const sql of s.migrations || []) db.exec(sql);
  return db;
}

/** Выполняет fn в транзакции. */
export function tx(db, fn) {
  db.exec('BEGIN');
  try {
    const r = fn();
    db.exec('COMMIT');
    return r;
  } catch (e) {
    db.exec('ROLLBACK');
    throw e;
  }
}

export function upsertUser(db, u) {
  db.prepare(
    `INSERT INTO users (id, username, first_name, last_name, language_code)
     VALUES (?, ?, ?, ?, ?)
     ON CONFLICT(id) DO UPDATE SET username = excluded.username, first_name = excluded.first_name,
       last_name = excluded.last_name, language_code = excluded.language_code, last_seen_at = unixepoch()`,
  ).run(u.id, u.username ?? null, u.first_name ?? null, u.last_name ?? null, u.language_code ?? null);
}

/** Изменяет баланс и пишет транзакцию. Бросает ошибку, если средств недостаточно. */
export function changeBalance(db, userId, amount, kind, description, chargeId = null) {
  return tx(db, () => {
    const row = db.prepare('SELECT balance FROM users WHERE id = ?').get(userId);
    if (!row) throw new Error('Пользователь не найден');
    if (row.balance + amount < 0) throw Object.assign(new Error('Недостаточно средств'), { status: 402 });
    db.prepare('UPDATE users SET balance = balance + ? WHERE id = ?').run(amount, userId);
    db.prepare(
      'INSERT INTO transactions (user_id, amount, kind, description, telegram_charge_id) VALUES (?, ?, ?, ?, ?)',
    ).run(userId, amount, kind, description, chargeId);
    return row.balance + amount;
  });
}
