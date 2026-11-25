import { test, before, after } from 'node:test';
import assert from 'node:assert/strict';
import { createGamesApp } from '../src/games/app.js';
import { readGamesConfig } from '../src/games/config.js';
import { TttHub, winnerOf } from '../src/games/ttt.js';
import { levelOf } from '../src/games/store.js';
import { openDb } from '../src/db.js';
import { MIGRATIONS } from '../src/games/store.js';
import { signInitData } from '../src/auth.js';
import { fakeTelegram, TOKEN } from './helpers.js';

let t;
before(async () => {
  const config = readGamesConfig({ GAMES_BOT_TOKEN: TOKEN, GAMES_PUBLIC_URL: 'https://games.test', GAMES_PORT: '0' });
  const tg = fakeTelegram({ getMe: () => ({ username: 'StarGamesBot' }) });
  const app = createGamesApp(config, { bot: tg.bot, db: openDb(':memory:', [{ migrations: MIGRATIONS }]) });
  await new Promise((r) => app.server.listen(0, r));
  const base = `http://127.0.0.1:${app.server.address().port}`;
  const auth = (user) => 'tma ' + signInitData({ auth_date: String(Math.floor(Date.now() / 1000)), user: JSON.stringify(user) }, TOKEN);
  const request = async (path, { user = { id: 10, first_name: 'Аня' }, method = 'GET', body } = {}) => {
    const res = await fetch(base + path, {
      method,
      headers: { 'content-type': 'application/json', ...(user ? { authorization: auth(user) } : {}) },
      body: body ? JSON.stringify(body) : undefined,
    });
    return { status: res.status, data: await res.json() };
  };
  t = { app, tg, request };
});
after(() => new Promise((r) => t.app.server.close(r)));

const A = { id: 10, first_name: 'Аня' };
const B = { id: 11, first_name: 'Борис' };
const C = { id: 12, first_name: 'Вика' };

test('каталог открыт без авторизации, остальное — только из Telegram', async () => {
  assert.equal((await t.request('/api/games', { user: null })).data.games.length, 6);
  assert.equal((await t.request('/api/me', { user: null })).status, 401);
  const { data } = await t.request('/api/me');
  assert.equal(data.user.name, 'Аня');
  assert.equal(data.profile.level, 1);
  assert.deepEqual(data.games.map((g) => g.id), ['2048', 'snake', 'ttt', 'memory', 'mines', 'reaction']);
});

test('рекорды: «больше — лучше» и «меньше — лучше», рейтинг и места', async () => {
  let r = await t.request('/api/score', { method: 'POST', body: { game: 'snake', score: 120 } });
  assert.equal(r.data.best, 120);
  assert.equal(r.data.first, true);
  r = await t.request('/api/score', { method: 'POST', body: { game: 'snake', score: 80 } });
  assert.equal(r.data.best, 120);
  assert.equal(r.data.isRecord, false);
  r = await t.request('/api/score', { method: 'POST', body: { game: 'snake', score: 300 } });
  assert.equal(r.data.isRecord, true);

  await t.request('/api/score', { user: B, method: 'POST', body: { game: 'snake', score: 500 } });
  await t.request('/api/score', { method: 'POST', body: { game: 'reaction', score: 300 } });
  r = await t.request('/api/score', { method: 'POST', body: { game: 'reaction', score: 210 } });
  assert.equal(r.data.isRecord, true, 'для реакции меньше — лучше');
  await t.request('/api/score', { user: B, method: 'POST', body: { game: 'reaction', score: 250 } });

  const snake = (await t.request('/api/leaderboard/snake')).data;
  assert.deepEqual(snake.top.map((x) => [x.name, x.best]), [['Борис', 500], ['Аня', 300]]);
  assert.equal(snake.me.rank, 2);
  const reaction = (await t.request('/api/leaderboard/reaction')).data;
  assert.equal(reaction.top[0].name, 'Аня');
  assert.equal(reaction.me.rank, 1);

  assert.equal((await t.request('/api/score', { method: 'POST', body: { game: 'reaction', score: 5 } })).status, 400, 'нереальный результат');
  assert.equal((await t.request('/api/score', { method: 'POST', body: { game: 'nope', score: 5 } })).status, 404);
  const p = (await t.request('/api/profile')).data;
  assert.equal(p.plays, 5);
  assert.equal(p.stats.snake.best, 300);
});

test('онлайн: пинги считаются по играм', async () => {
  await t.request('/api/ping', { method: 'POST', body: { game: 'snake' } });
  const r = await t.request('/api/ping', { user: B, method: 'POST', body: {} });
  assert.equal(r.data.total, 2);
  assert.equal(r.data.byGame.snake, 1);
});

test('крестики-нолики онлайн: поиск, ходы, победа идёт в рейтинг', async () => {
  let a = (await t.request('/api/ttt/find', { user: A, method: 'POST' })).data;
  assert.equal(a.status, 'searching');
  const b = (await t.request('/api/ttt/find', { user: B, method: 'POST' })).data;
  assert.equal(b.status, 'playing');
  a = (await t.request('/api/ttt', { user: A })).data;
  assert.equal(a.code, b.code);
  const [first, second] = a.yourTurn ? [A, B] : [B, A];
  assert.equal((await t.request('/api/ttt/move', { user: second, method: 'POST', body: { cell: 0 } })).status, 409, 'не твой ход');
  for (const [u, cell] of [[first, 0], [second, 3], [first, 1], [second, 4]]) {
    assert.equal((await t.request('/api/ttt/move', { user: u, method: 'POST', body: { cell } })).status, 200);
  }
  const fin = (await t.request('/api/ttt/move', { user: first, method: 'POST', body: { cell: 2 } })).data;
  assert.equal(fin.status, 'finished');
  assert.equal(fin.result.outcome, 'win');
  assert.deepEqual(fin.result.line, [0, 1, 2]);
  assert.equal((await t.request('/api/ttt', { user: second })).data.result.outcome, 'loss');
  const lb = (await t.request('/api/leaderboard/ttt', { user: first })).data;
  assert.equal(lb.top[0].id, first.id);
  assert.equal(lb.top[0].best, 1);
  assert.equal(lb.top.length, 1, 'проигравший без побед в рейтинг не попадает');
});

test('крестики-нолики: приватная комната по коду', async () => {
  const room = (await t.request('/api/ttt/create', { user: C, method: 'POST' })).data;
  assert.equal(room.status, 'waiting');
  assert.match(room.code, /^[A-Z0-9]{5}$/);
  assert.equal((await t.request('/api/ttt/join', { user: A, method: 'POST', body: { code: 'ZZZZZ' } })).status, 404);
  const joined = (await t.request('/api/ttt/join', { user: A, method: 'POST', body: { code: room.code.toLowerCase() } })).data;
  assert.equal(joined.status, 'playing');
  assert.equal(joined.opponent.name, 'Вика');
  assert.equal((await t.request('/api/ttt', { user: C })).data.yourTurn, true, 'первым ходит пригласивший');
  // уход из партии — техническое поражение
  await t.request('/api/ttt/leave', { user: C, method: 'POST' });
  const a = (await t.request('/api/ttt', { user: A })).data;
  assert.equal(a.result.outcome, 'win');
  assert.equal(a.result.reason, 'left');
});

test('TttHub: соперник пропал — победа по тайм-ауту', () => {
  let now = 0;
  const hub = new TttHub({ now: () => now, leaveMs: 1000 });
  hub.find({ id: 1, name: 'a' });
  hub.find({ id: 2, name: 'b' });
  now = 500;
  assert.equal(hub.state(1).status, 'playing');
  now = 1600;
  const s = hub.state(1);
  assert.equal(s.status, 'finished');
  assert.equal(s.result.outcome, 'win');
  assert.equal(hub.state(2).result.outcome, 'loss');
});

test('winnerOf и уровни', () => {
  assert.equal(winnerOf(['X', 'X', 'X', null, null, null, null, null, null]).mark, 'X');
  assert.equal(winnerOf(['X', 'O', 'X', 'X', 'O', 'O', 'O', 'X', 'X']).mark, 'draw');
  assert.equal(winnerOf(Array(9).fill(null)), null);
  assert.deepEqual(levelOf(0), { xp: 0, level: 1, levelXp: 0, levelNeed: 100 });
  assert.deepEqual(levelOf(350), { xp: 350, level: 3, levelXp: 50, levelNeed: 300 });
});

test('бот: /start с кнопками игр и приглашение на дуэль', async () => {
  const send = (text) => t.tg.bot.handleUpdate({ update_id: 1, message: { message_id: 1, text, chat: { id: 10 }, from: A } });
  await send('/start');
  let msg = t.tg.calls.filter((c) => c.method === 'sendMessage').at(-1).params;
  assert.match(msg.text, /StarGames/);
  const buttons = msg.reply_markup.inline_keyboard.flat();
  assert.equal(buttons[0].web_app.url, 'https://games.test/');
  assert.ok(buttons.some((b) => b.web_app?.url === 'https://games.test/?game=snake'));
  assert.ok(buttons.some((b) => b.callback_data === 'top'));

  await send('/start ttt_ABCDE');
  msg = t.tg.calls.filter((c) => c.method === 'sendMessage').at(-1).params;
  assert.equal(msg.reply_markup.inline_keyboard[0][0].web_app.url, 'https://games.test/?join=ABCDE');

  await send('/top');
  msg = t.tg.calls.filter((c) => c.method === 'sendMessage').at(-1).params;
  assert.match(msg.text, /Рейтинг/);
  assert.match(msg.text, /🥇 Борис — 500/);
});
