import { randomBytes } from 'node:crypto';
import { isAdmin } from '../index.js';
import * as q from './queries.js';

const bad = (msg, status = 400) => Object.assign(new Error(msg), { status });
const parseDays = (query) => Math.min(Math.max(Number(query.get('days')) || 30, 1), 365);
const ADMIN_STATUSES = ['administrator', 'creator'];

async function snapshot(bot, db, chatId) {
  try {
    const members = await bot.call('getChatMemberCount', { chat_id: chatId });
    q.addSnapshot(db, chatId, members);
    return members;
  } catch (e) {
    console.error('[analytics] snapshot', chatId, e.message);
    if ([400, 403].includes(e.code)) db.prepare('UPDATE an_channels SET active = 0 WHERE chat_id = ?').run(chatId);
    return null;
  }
}

export default {
  id: 'analytics',
  title: 'Аналитика',
  icon: '📊',
  order: 10,

  migrations: [
    `CREATE TABLE IF NOT EXISTS an_channels (
       chat_id INTEGER PRIMARY KEY,
       owner_id INTEGER NOT NULL,      -- кто добавил бота админом
       title TEXT, username TEXT, type TEXT,
       active INTEGER NOT NULL DEFAULT 1,
       added_at INTEGER NOT NULL DEFAULT (unixepoch())
     )`,
    `CREATE TABLE IF NOT EXISTS an_channel_snapshots (
       chat_id INTEGER NOT NULL, ts INTEGER NOT NULL, members INTEGER NOT NULL
     )`,
    `CREATE INDEX IF NOT EXISTS an_snap_idx ON an_channel_snapshots (chat_id, ts)`,
    `CREATE TABLE IF NOT EXISTS an_channel_posts (
       chat_id INTEGER NOT NULL, message_id INTEGER NOT NULL, date INTEGER NOT NULL,
       kind TEXT, views INTEGER,
       PRIMARY KEY (chat_id, message_id)
     )`,
    `CREATE TABLE IF NOT EXISTS an_channel_member_events (
       chat_id INTEGER NOT NULL, user_id INTEGER NOT NULL, kind TEXT NOT NULL, date INTEGER NOT NULL
     )`,
    `CREATE INDEX IF NOT EXISTS an_mev_idx ON an_channel_member_events (chat_id, date)`,
    `CREATE TABLE IF NOT EXISTS an_bots (
       id INTEGER PRIMARY KEY AUTOINCREMENT,
       owner_id INTEGER NOT NULL,
       name TEXT NOT NULL,
       api_key TEXT NOT NULL UNIQUE,
       created_at INTEGER NOT NULL DEFAULT (unixepoch())
     )`,
    `CREATE TABLE IF NOT EXISTS an_bot_events (
       id INTEGER PRIMARY KEY AUTOINCREMENT,
       bot_id INTEGER NOT NULL REFERENCES an_bots(id) ON DELETE CASCADE,
       user_id TEXT NOT NULL,
       event TEXT NOT NULL,
       amount INTEGER NOT NULL DEFAULT 0,
       ts INTEGER NOT NULL
     )`,
    `CREATE INDEX IF NOT EXISTS an_bev_idx ON an_bot_events (bot_id, ts)`,
  ],

  setupBot({ bot, db, config }) {
    // Бота добавили/удалили из канала или группы
    bot.on('my_chat_member', async ({ payload: u }) => {
      if (!['channel', 'supergroup', 'group'].includes(u.chat.type)) return;
      const isNowAdmin = ADMIN_STATUSES.includes(u.new_chat_member.status);
      if (isNowAdmin) {
        q.upsertChannel(db, u.chat, u.from.id);
        const members = await snapshot(bot, db, u.chat.id);
        await bot
          .sendMessage(u.from.id, `📊 «${u.chat.title}» подключён к аналитике${members != null ? `: ${members} подписчиков` : ''}. Откройте приложение, чтобы смотреть статистику.`)
          .catch(() => {});
      } else {
        db.prepare('UPDATE an_channels SET active = 0 WHERE chat_id = ?').run(u.chat.id);
      }
    });

    // Посты в каналах
    bot.on('channel_post', ({ payload }) => {
      if (db.prepare('SELECT 1 FROM an_channels WHERE chat_id = ?').get(payload.chat.id)) q.recordPost(db, payload);
    });

    // Подписки/отписки (Telegram присылает только когда бот — админ)
    bot.on('chat_member', ({ payload: u }) => {
      if (!db.prepare('SELECT 1 FROM an_channels WHERE chat_id = ?').get(u.chat.id)) return;
      const was = ['member', 'administrator', 'creator', 'restricted'].includes(u.old_chat_member.status);
      const now = ['member', 'administrator', 'creator', 'restricted'].includes(u.new_chat_member.status);
      if (!was && now) q.recordMemberEvent(db, u.chat.id, u.new_chat_member.user.id, 'join', u.date);
      if (was && !now) q.recordMemberEvent(db, u.chat.id, u.new_chat_member.user.id, 'leave', u.date);
    });

    bot.command('stats', async ({ payload }) => {
      const o = q.overview(db, payload.from.id, { includeShop: isAdmin(config, payload.from.id) });
      const sign = (n) => (n > 0 ? `+${n}` : `${n}`);
      await bot.sendMessage(
        payload.chat.id,
        [
          '📊 <b>Сводка за 30 дней</b>',
          '',
          `📢 Каналы: ${o.channels.count}, подписчиков ${o.channels.members} (${sign(o.channels.growth)}), постов ${o.channels.posts}`,
          `🤖 Боты: ${o.bots.count}, пользователей ${o.bots.users}, DAU ${o.bots.dau}, событий ${o.bots.events}`,
          `💰 Покупки: ${o.purchases.orders} на ${o.purchases.revenue} ⭐, средний чек ${o.purchases.avgCheck} ⭐`,
          '',
          o.channels.count ? '' : 'Чтобы подключить канал — добавьте бота администратором канала.',
        ].join('\n'),
      );
    }, 'Сводка аналитики');
  },

  setupApi(router) {
    const ownBot = (db, userId, id) => {
      const b = db.prepare('SELECT * FROM an_bots WHERE id = ? AND owner_id = ?').get(Number(id), userId);
      if (!b) throw bad('Бот не найден', 404);
      return b;
    };

    router.get('/api/analytics/overview', ({ db, config, user, query }) =>
      q.overview(db, user.id, { days: parseDays(query), includeShop: isAdmin(config, user.id) }));

    router.get('/api/analytics/channels', ({ db, user, query }) => ({ channels: q.channelList(db, user.id, parseDays(query)) }));

    router.get('/api/analytics/channels/:id', ({ db, user, params, query }) => {
      const ch = db.prepare('SELECT * FROM an_channels WHERE chat_id = ? AND owner_id = ?').get(Number(params.id), user.id);
      if (!ch) throw bad('Канал не найден', 404);
      return { channel: ch, ...q.channelReport(db, ch.chat_id, parseDays(query)) };
    });

    router.post('/api/analytics/channels/:id/refresh', async ({ db, bot, user, params }) => {
      const ch = db.prepare('SELECT * FROM an_channels WHERE chat_id = ? AND owner_id = ?').get(Number(params.id), user.id);
      if (!ch) throw bad('Канал не найден', 404);
      return { members: await snapshot(bot, db, ch.chat_id) };
    });

    router.get('/api/analytics/bots', ({ db, user, query }) => ({ bots: q.botList(db, user.id, parseDays(query)) }));

    router.post('/api/analytics/bots', ({ db, user, body }) => {
      const name = String(body.name || '').trim().slice(0, 64);
      if (!name) throw bad('Укажите название бота');
      const apiKey = randomBytes(24).toString('base64url');
      const r = db.prepare('INSERT INTO an_bots (owner_id, name, api_key) VALUES (?, ?, ?)').run(user.id, name, apiKey);
      return { id: Number(r.lastInsertRowid), name, apiKey };
    });

    router.get('/api/analytics/bots/:id', ({ db, user, params, query }) => {
      const b = ownBot(db, user.id, params.id);
      return { bot: b, ...q.botReport(db, b.id, parseDays(query)) };
    });

    router.delete('/api/analytics/bots/:id', ({ db, user, params }) => {
      const b = ownBot(db, user.id, params.id);
      db.prepare('DELETE FROM an_bot_events WHERE bot_id = ?').run(b.id);
      db.prepare('DELETE FROM an_bots WHERE id = ?').run(b.id);
      return { ok: true };
    });

    router.get('/api/analytics/purchases', ({ db, config, user, query }) =>
      q.purchasesReport(db, user.id, { days: parseDays(query), includeShop: isAdmin(config, user.id) }));

    /**
     * Публичный эндпоинт трекинга для ваших ботов.
     * POST /api/track  { "api_key": "...", "user_id": 123, "event": "start" | "purchase" | ..., "amount": 100 }
     * Можно передать массив событий в поле "events".
     */
    router.post(
      '/api/track',
      ({ db, body, req }) => {
        const key = body.api_key || req.headers['x-api-key'];
        const b = key && db.prepare('SELECT id FROM an_bots WHERE api_key = ?').get(String(key));
        if (!b) throw bad('Неверный api_key', 401);
        const events = Array.isArray(body.events) ? body.events : [body];
        if (events.length > 500) throw bad('Не больше 500 событий за запрос');
        const now = Math.floor(Date.now() / 1000);
        const ins = db.prepare('INSERT INTO an_bot_events (bot_id, user_id, event, amount, ts) VALUES (?, ?, ?, ?, ?)');
        let accepted = 0;
        for (const e of events) {
          const event = String(e.event || '').trim().slice(0, 64);
          if (!event || e.user_id == null) continue;
          const ts = Number.isFinite(Number(e.ts)) && Number(e.ts) <= now ? Math.floor(Number(e.ts)) : now;
          ins.run(b.id, String(e.user_id).slice(0, 64), event, Math.floor(Number(e.amount) || 0), ts);
          accepted++;
        }
        return { accepted };
      },
      { auth: false },
    );
  },

  start({ bot, db, config }) {
    if (!config.botToken) return;
    const tick = async () => {
      for (const { chat_id } of db.prepare('SELECT chat_id FROM an_channels WHERE active = 1').all()) {
        await snapshot(bot, db, chat_id);
      }
    };
    const timer = setInterval(tick, Math.max(config.channelSnapshotMinutes, 5) * 60_000);
    timer.unref();
    tick();
  },
};
