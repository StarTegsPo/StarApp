/** Кто сейчас онлайн: Mini App шлёт пинг раз в ~20 с, игрок считается онлайн 45 с после пинга. */
export class Presence {
  constructor({ ttlMs = 45_000, now = Date.now } = {}) {
    this.ttl = ttlMs;
    this.now = now;
    this.users = new Map(); // userId -> { at, game }
  }
  ping(userId, game = null) {
    this.users.set(userId, { at: this.now(), game: game || null });
  }
  snapshot() {
    const cutoff = this.now() - this.ttl;
    const byGame = {};
    let total = 0;
    for (const [id, u] of this.users) {
      if (u.at < cutoff) { this.users.delete(id); continue; }
      total++;
      if (u.game) byGame[u.game] = (byGame[u.game] || 0) + 1;
    }
    return { total, byGame };
  }
}
