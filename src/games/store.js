import { GAMES, gameById } from './catalog.js';
import { HttpError } from '../http.js';

export const MIGRATIONS = [
  `CREATE TABLE IF NOT EXISTS game_stats (
     user_id INTEGER NOT NULL REFERENCES users(id),
     game TEXT NOT NULL,
     best INTEGER,                       -- лучший результат (для онлайн-игр — число побед)
     plays INTEGER NOT NULL DEFAULT 0,
     wins INTEGER NOT NULL DEFAULT 0,
     losses INTEGER NOT NULL DEFAULT 0,
     draws INTEGER NOT NULL DEFAULT 0,
     updated_at INTEGER NOT NULL DEFAULT (unixepoch()),
     PRIMARY KEY (user_id, game)
   )`,
  'CREATE INDEX IF NOT EXISTS game_stats_best ON game_stats (game, best)',
];

const better = (game, a, b) => (b == null ? true : game.order === 'asc' ? a < b : a > b);

function row(db, userId, game) {
  db.prepare('INSERT OR IGNORE INTO game_stats (user_id, game) VALUES (?, ?)').run(userId, game);
  return db.prepare('SELECT * FROM game_stats WHERE user_id = ? AND game = ?').get(userId, game);
}

/** Место игрока в рейтинге игры (1 — первое) или null, если результата нет. */
export function rankOf(db, game, userId) {
  const me = db.prepare('SELECT best, updated_at FROM game_stats WHERE user_id = ? AND game = ?').get(userId, game.id);
  if (me?.best == null) return null;
  const cmp = game.order === 'asc' ? '<' : '>';
  const ahead = db.prepare(
    `SELECT COUNT(*) n FROM game_stats WHERE game = ? AND best IS NOT NULL
       AND (best ${cmp} ? OR (best = ? AND updated_at < ?))`,
  ).get(game.id, me.best, me.best, me.updated_at).n;
  return ahead + 1;
}

/** Сохраняет результат одиночной игры. Для онлайн-игр (unit=wins) только считает партию. */
export function submitScore(db, userId, gameId, score) {
  const game = gameById(gameId);
  if (!game) throw new HttpError(404, 'Нет такой игры');
  const cur = row(db, userId, game.id);
  if (game.unit === 'wins') {
    db.prepare('UPDATE game_stats SET plays = plays + 1 WHERE user_id = ? AND game = ?').run(userId, game.id);
    return { best: cur.best, isRecord: false, rank: rankOf(db, game, userId) };
  }
  const value = Math.round(Number(score));
  if (!Number.isFinite(value) || value < game.min || value > game.max) throw new HttpError(400, 'Некорректный результат');
  const isRecord = better(game, value, cur.best);
  db.prepare(
    `UPDATE game_stats SET plays = plays + 1 ${isRecord ? ', best = ?, updated_at = unixepoch()' : ''}
     WHERE user_id = ? AND game = ?`,
  ).run(...(isRecord ? [value] : []), userId, game.id);
  return { best: isRecord ? value : cur.best, isRecord: isRecord && cur.best != null, first: cur.best == null, rank: rankOf(db, game, userId) };
}

/** Итог онлайн-партии: 'win' | 'loss' | 'draw'. В рейтинг идёт число побед. */
export function recordMatch(db, userId, gameId, result) {
  row(db, userId, gameId);
  const col = { win: 'wins', loss: 'losses', draw: 'draws' }[result];
  db.prepare(
    `UPDATE game_stats SET plays = plays + 1, ${col} = ${col} + 1, best = wins + ${result === 'win' ? 1 : 0},
       updated_at = CASE WHEN ? THEN unixepoch() ELSE updated_at END
     WHERE user_id = ? AND game = ?`,
  ).run(result === 'win' ? 1 : 0, userId, gameId);
}

export function leaderboard(db, gameId, userId, limit = 50) {
  const game = gameById(gameId);
  if (!game) throw new HttpError(404, 'Нет такой игры');
  const top = db.prepare(
    `SELECT s.user_id id, s.best, s.plays, u.first_name, u.username FROM game_stats s JOIN users u ON u.id = s.user_id
     WHERE s.game = ? AND s.best IS NOT NULL AND s.best > ${game.unit === 'wins' ? 0 : -1}
     ORDER BY s.best ${game.order === 'asc' ? 'ASC' : 'DESC'}, s.updated_at ASC LIMIT ?`,
  ).all(game.id, limit).map((r, i) => ({ ...r, rank: i + 1, name: r.first_name || r.username || 'Игрок' }));
  const mine = db.prepare('SELECT best, plays FROM game_stats WHERE user_id = ? AND game = ?').get(userId, game.id);
  return { game: game.id, top, me: { best: mine?.best ?? null, plays: mine?.plays ?? 0, rank: rankOf(db, game, userId) } };
}

/** Профиль: статистика по всем играм, опыт и уровень. */
export function profile(db, userId) {
  const rows = db.prepare('SELECT * FROM game_stats WHERE user_id = ?').all(userId);
  const stats = Object.fromEntries(
    GAMES.map((g) => {
      const r = rows.find((x) => x.game === g.id);
      return [g.id, { best: r?.best ?? null, plays: r?.plays ?? 0, wins: r?.wins ?? 0, losses: r?.losses ?? 0, draws: r?.draws ?? 0 }];
    }),
  );
  const plays = rows.reduce((s, r) => s + r.plays, 0);
  const wins = rows.reduce((s, r) => s + r.wins, 0);
  const xp = plays * 10 + wins * 25;
  return { stats, plays, wins, ...levelOf(xp) };
}

/** Уровень растёт всё медленнее: на n-й уровень нужно 100·n опыта. */
export function levelOf(xp) {
  let level = 1, need = 100, rest = xp;
  while (rest >= need) { rest -= need; level++; need = 100 * level; }
  return { xp, level, levelXp: rest, levelNeed: need };
}
