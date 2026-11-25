import { fileURLToPath } from 'node:url';
import { openDb, upsertUser } from '../db.js';
import { TelegramBot } from '../telegram.js';
import { Router, createHttpServer, HttpError } from '../http.js';
import { GAMES, gameById, formatScore } from './catalog.js';
import { MIGRATIONS, submitScore, recordMatch, leaderboard, profile } from './store.js';
import { Presence } from './presence.js';
import { TttHub } from './ttt.js';

const WEBAPP_DIR = fileURLToPath(new URL('../../games-webapp/', import.meta.url));
const MEDALS = ['🥇', '🥈', '🥉'];
const esc = (s) => String(s ?? '').replace(/[&<>]/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;' })[c]);

/** Собирает игрового бота StarGames: БД, бот, API и Mini App. Ничего не запускает. */
export function createGamesApp(config, { bot = new TelegramBot(config.botToken), db, now } = {}) {
  db ??= openDb(config.dbPath, [{ migrations: MIGRATIONS }]);
  const presence = new Presence({ now });
  const ttt = new TttHub({
    now,
    onFinish(room) {
      if (room.players.length < 2) return;
      room.players.forEach((p, i) => {
        const w = room.result.winner;
        recordMatch(db, p.id, 'ttt', w == null ? 'draw' : w === i ? 'win' : 'loss');
      });
    },
  });
  const ctx = { config, db, bot };
  const router = new Router();
  const nameOf = (u) => u.first_name || u.username || 'Игрок';

  // ----- Бот -----
  const https = config.publicUrl.startsWith('https://');
  const appUrl = (params = {}) => {
    const q = new URLSearchParams(params).toString();
    return config.publicUrl + '/' + (q ? `?${q}` : '');
  };
  const webApp = (text, params) => (https ? { text, web_app: { url: appUrl(params) } } : null);

  const mainKeyboard = () => {
    const rows = [];
    const play = webApp('🎮 Играть');
    if (play) rows.push([play]);
    const games = GAMES.map((g) => webApp(`${g.icon} ${g.title}`, { game: g.id })).filter(Boolean);
    for (let i = 0; i < games.length; i += 2) rows.push(games.slice(i, i + 2));
    rows.push([{ text: '🏆 Рейтинг', callback_data: 'top' }, { text: '🎲 Бросить кубик', callback_data: 'dice' }]);
    return { inline_keyboard: rows };
  };

  bot.use((c) => {
    const from = c.payload?.from;
    if (from && !from.is_bot && ['message', 'callback_query'].includes(c.type)) upsertUser(db, from);
  });

  bot.command('start', async ({ payload, args }) => {
    const chat = payload.chat.id;
    const code = /^ttt_([A-Z0-9]{5})$/i.exec(args || '')?.[1]?.toUpperCase();
    if (code) {
      const btn = webApp('⚔️ Принять вызов', { join: code });
      return bot.sendMessage(
        chat,
        `⚔️ Вас вызвали на дуэль в <b>крестики-нолики</b>!\nКод комнаты: <code>${code}</code>`,
        btn ? { reply_markup: { inline_keyboard: [[btn]] } } : {},
      );
    }
    await bot.sendMessage(
      chat,
      [
        `Привет, ${esc(nameOf(payload.from))}! 👾`,
        '',
        '<b>🎮 StarGames</b> — игровой зал прямо в Telegram:',
        '',
        ...GAMES.map((g) => `${g.icon} <b>${g.title}</b> — ${g.desc}`),
        '',
        '🌐 Онлайн-дуэли с живыми соперниками, 🏆 рейтинги и уровни.',
        'Жми «Играть»!',
      ].join('\n'),
      { reply_markup: mainKeyboard() },
    );
  }, 'Игровой зал');

  bot.command('games', ({ payload }) =>
    bot.sendMessage(payload.chat.id, '🎮 Выберите игру:', { reply_markup: mainKeyboard() }), 'Список игр');

  const topText = () => {
    const parts = ['<b>🏆 Рейтинг StarGames</b>'];
    for (const g of GAMES) {
      const { top } = leaderboard(db, g.id, 0, 3);
      parts.push('', `${g.icon} <b>${g.title}</b>`);
      if (!top.length) parts.push('<i>пока никого — станьте первым!</i>');
      for (const r of top) parts.push(`${MEDALS[r.rank - 1]} ${esc(r.name)} — ${formatScore(g, r.best)}`);
    }
    return parts.join('\n');
  };
  bot.command('top', ({ payload }) => bot.sendMessage(payload.chat.id, topText()), 'Лучшие игроки');

  const dice = (chat) => bot.call('sendDice', { chat_id: chat, emoji: ['🎲', '🎯', '🏀', '⚽', '🎳', '🎰'][Math.floor(Math.random() * 6)] });
  bot.command('dice', ({ payload }) => dice(payload.chat.id), 'Бросить кубик');

  bot.command('help', ({ payload }) =>
    bot.sendMessage(payload.chat.id, '/start — игровой зал\n/games — список игр\n/top — рейтинг\n/dice — бросить кубик'), 'Помощь');

  bot.on('callback_query', async ({ payload: q }) => {
    const chat = q.message?.chat?.id;
    await bot.call('answerCallbackQuery', { callback_query_id: q.id }).catch(() => {});
    if (!chat) return;
    if (q.data === 'top') await bot.sendMessage(chat, topText());
    if (q.data === 'dice') await dice(chat);
  });

  // ----- API -----
  router.get('/api/me', ({ user }) => ({
    user: { id: user.id, name: nameOf(user), username: user.username ?? null, photo: user.photo_url ?? null },
    botUsername: bot.me?.username ?? null,
    games: GAMES,
    profile: profile(db, user.id),
    online: presence.snapshot(),
  }));

  router.get('/api/games', () => ({ games: GAMES }), { auth: false });

  router.post('/api/ping', ({ user, body }) => {
    presence.ping(user.id, gameById(body.game)?.id);
    return presence.snapshot();
  });

  router.post('/api/score', ({ user, body }) => {
    const res = submitScore(db, user.id, body.game, body.score);
    return { ...res, profile: profile(db, user.id) };
  });

  router.get('/api/leaderboard/:game', ({ user, params }) => leaderboard(db, params.game, user.id));
  router.get('/api/profile', ({ user }) => profile(db, user.id));

  const tttUser = (u) => ({ id: u.id, name: nameOf(u) });
  const wrap = (fn) => {
    try { return fn(); } catch (e) { throw new HttpError(e.status || 500, e.message); }
  };
  router.get('/api/ttt', ({ user }) => ttt.state(user.id));
  router.post('/api/ttt/find', ({ user }) => ttt.find(tttUser(user)));
  router.post('/api/ttt/create', ({ user }) => ttt.create(tttUser(user)));
  router.post('/api/ttt/join', ({ user, body }) => wrap(() => ttt.join(tttUser(user), body.code)));
  router.post('/api/ttt/move', ({ user, body }) => wrap(() => ttt.move(user.id, body.cell)));
  router.post('/api/ttt/leave', ({ user }) => ttt.leave(user.id));

  const server = createHttpServer(router, ctx, WEBAPP_DIR);

  return {
    ctx, router, server, presence, ttt,
    async start() {
      await new Promise((r) => server.listen(config.port, r));
      console.log(`[games] API и Mini App: http://localhost:${config.port}`);
      if (!config.botToken) return console.warn('[games] GAMES_BOT_TOKEN не задан — бот не запущен, работает только HTTP');
      await bot.publishCommands().catch((e) => console.error('[games] setMyCommands', e.message));
      if (https) {
        await bot.call('setChatMenuButton', { menu_button: { type: 'web_app', text: 'Играть', web_app: { url: appUrl() } } })
          .catch((e) => console.error('[games] setChatMenuButton', e.message));
      }
      bot.startPolling();
    },
  };
}
