import { randomInt } from 'node:crypto';

export const LINES = [[0, 1, 2], [3, 4, 5], [6, 7, 8], [0, 3, 6], [1, 4, 7], [2, 5, 8], [0, 4, 8], [2, 4, 6]];

/** Победитель на доске: { mark: 'X'|'O', line } | { mark: 'draw' } | null */
export function winnerOf(board) {
  for (const line of LINES) {
    const [a, b, c] = line;
    if (board[a] && board[a] === board[b] && board[a] === board[c]) return { mark: board[a], line };
  }
  return board.every(Boolean) ? { mark: 'draw', line: null } : null;
}

const CODE_ALPHABET = 'ABCDEFGHJKLMNPQRSTUVWXYZ23456789';

/**
 * Онлайн-крестики-нолики: быстрый поиск соперника, приватные комнаты по коду, ходы, тайм-ауты.
 * Хранится в памяти процесса: партия короткая, при перезапуске просто начинается новая.
 * Клиент опрашивает state() раз в секунду — это заодно и «я ещё здесь».
 */
export class TttHub {
  constructor({ now = Date.now, leaveMs = 25_000, onFinish = () => {} } = {}) {
    this.now = now;
    this.leaveMs = leaveMs; // сколько ждать молчащего соперника, прежде чем засчитать победу
    this.onFinish = onFinish;
    this.rooms = new Map(); // id -> room
    this.byCode = new Map(); // code -> id
    this.byUser = new Map(); // userId -> roomId
    this.queue = null; // { user, at } — ждёт быстрого соперника
    this.seq = 0;
  }

  #code() {
    let code;
    do code = Array.from({ length: 5 }, () => CODE_ALPHABET[randomInt(CODE_ALPHABET.length)]).join('');
    while (this.byCode.has(code));
    return code;
  }

  #newRoom(players, { isPrivate = false } = {}) {
    const room = {
      id: ++this.seq,
      code: this.#code(),
      private: isPrivate,
      players, // [{ id, name }] — первый ходит крестиками
      seen: players.map(() => this.now()),
      board: Array(9).fill(null),
      turn: 0,
      moves: 0,
      status: players.length === 2 ? 'playing' : 'waiting',
      result: null, // { winner: 0|1|null, line, reason: 'line'|'draw'|'left' }
      createdAt: this.now(),
    };
    this.rooms.set(room.id, room);
    this.byCode.set(room.code, room.id);
    for (const p of players) this.byUser.set(p.id, room.id);
    return room;
  }

  #roomOf(userId) {
    const room = this.rooms.get(this.byUser.get(userId));
    if (!room) this.byUser.delete(userId);
    return room || null;
  }

  #finish(room, winner, line, reason) {
    room.status = 'finished';
    room.result = { winner, line, reason };
    room.finishedAt = this.now();
    this.byCode.delete(room.code);
    try { this.onFinish(room); } catch (e) { console.error('[ttt] onFinish', e); }
  }

  #gc() {
    const now = this.now();
    for (const [id, r] of this.rooms) {
      const old = r.status === 'finished' ? now - r.finishedAt > 120_000 : now - Math.max(...r.seen) > 10 * 60_000;
      if (!old) continue;
      this.rooms.delete(id);
      this.byCode.delete(r.code);
      for (const p of r.players) if (this.byUser.get(p.id) === id) this.byUser.delete(p.id);
    }
    if (this.queue && now - this.queue.at > this.leaveMs) this.queue = null;
  }

  /** Выйти из текущей комнаты/очереди. Уход из идущей партии — поражение. */
  leave(userId) {
    if (this.queue?.user.id === userId) this.queue = null;
    const room = this.#roomOf(userId);
    if (!room) return this.state(userId);
    const me = room.players.findIndex((p) => p.id === userId);
    if (room.status === 'playing') this.#finish(room, 1 - me, null, 'left');
    else if (room.status === 'waiting') { this.rooms.delete(room.id); this.byCode.delete(room.code); }
    this.byUser.delete(userId);
    return this.state(userId);
  }

  /** Быстрый поиск: если кто-то ждёт — сразу партия, иначе встаём в очередь. */
  find(user) {
    this.#gc();
    this.leave(user.id);
    const q = this.queue;
    if (q && q.user.id !== user.id) {
      this.queue = null;
      // кто ходит первым — случайно
      const players = randomInt(2) ? [q.user, user] : [user, q.user];
      this.#newRoom(players.map(({ id, name }) => ({ id, name })));
    } else {
      this.queue = { user: { id: user.id, name: user.name }, at: this.now() };
    }
    return this.state(user.id);
  }

  /** Приватная комната: друг заходит по коду или по ссылке-приглашению. */
  create(user) {
    this.#gc();
    this.leave(user.id);
    this.#newRoom([{ id: user.id, name: user.name }], { isPrivate: true });
    return this.state(user.id);
  }

  join(user, code) {
    this.#gc();
    const room = this.rooms.get(this.byCode.get(String(code || '').trim().toUpperCase()));
    if (!room || room.status !== 'waiting') throw Object.assign(new Error('Комната не найдена или уже занята'), { status: 404 });
    if (room.players[0].id === user.id) return this.state(user.id);
    this.leave(user.id);
    room.players.push({ id: user.id, name: user.name });
    room.seen.push(this.now());
    room.status = 'playing';
    // в приватной комнате первым ходит пригласивший — так привычнее
    this.byUser.set(user.id, room.id);
    return this.state(user.id);
  }

  move(userId, cell) {
    const room = this.#roomOf(userId);
    const i = Number(cell);
    if (!room || room.status !== 'playing') throw Object.assign(new Error('Партия не идёт'), { status: 409 });
    const me = room.players.findIndex((p) => p.id === userId);
    if (room.turn !== me) throw Object.assign(new Error('Сейчас ход соперника'), { status: 409 });
    if (!Number.isInteger(i) || i < 0 || i > 8 || room.board[i]) throw Object.assign(new Error('Клетка занята'), { status: 400 });
    room.board[i] = me === 0 ? 'X' : 'O';
    room.moves++;
    room.seen[me] = this.now();
    const w = winnerOf(room.board);
    if (w) this.#finish(room, w.mark === 'draw' ? null : me, w.line, w.mark === 'draw' ? 'draw' : 'line');
    else room.turn = 1 - me;
    return this.state(userId);
  }

  /** Состояние для игрока. Заодно отмечает, что он на связи, и проверяет, не ушёл ли соперник. */
  state(userId) {
    const now = this.now();
    if (this.queue?.user.id === userId) {
      this.queue.at = now;
      return { status: 'searching' };
    }
    const room = this.#roomOf(userId);
    if (!room) return { status: 'idle' };
    const me = room.players.findIndex((p) => p.id === userId);
    room.seen[me] = now;
    if (room.status === 'playing' && now - room.seen[1 - me] > this.leaveMs) this.#finish(room, me, null, 'left');

    const opp = room.players[1 - me];
    const r = room.result;
    return {
      status: room.status,
      code: room.code,
      private: room.private,
      board: room.board,
      mark: me === 0 ? 'X' : 'O',
      yourTurn: room.status === 'playing' && room.turn === me,
      opponent: opp ? { name: opp.name } : null,
      moves: room.moves,
      result: r && {
        outcome: r.winner == null ? 'draw' : r.winner === me ? 'win' : 'loss',
        line: r.line,
        reason: r.reason,
      },
    };
  }
}
