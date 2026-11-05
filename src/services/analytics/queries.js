/** SQL-отчёты аналитики. Все функции принимают db и возвращают простые объекты. */

const DAY = 86400;
export const since = (days, now = Date.now() / 1000) => Math.floor(now) - days * DAY;

/** Заполняет пропущенные дни нулями, чтобы графики были непрерывными. */
export function fillDays(rows, days, now = Date.now() / 1000, keys = ['value']) {
  const map = new Map(rows.map((r) => [r.day, r]));
  const out = [];
  for (let i = days - 1; i >= 0; i--) {
    const day = new Date((Math.floor(now) - i * DAY) * 1000).toISOString().slice(0, 10);
    const r = map.get(day) || {};
    out.push({ day, ...Object.fromEntries(keys.map((k) => [k, r[k] ?? 0])) });
  }
  return out;
}

// ---------- Каналы ----------

export function upsertChannel(db, chat, ownerId) {
  db.prepare(
    `INSERT INTO an_channels (chat_id, owner_id, title, username, type, active)
     VALUES (?, ?, ?, ?, ?, 1)
     ON CONFLICT(chat_id) DO UPDATE SET owner_id = excluded.owner_id, title = excluded.title,
       username = excluded.username, type = excluded.type, active = 1`,
  ).run(chat.id, ownerId, chat.title ?? null, chat.username ?? null, chat.type);
}

export function addSnapshot(db, chatId, members, ts = Math.floor(Date.now() / 1000)) {
  db.prepare('INSERT INTO an_channel_snapshots (chat_id, ts, members) VALUES (?, ?, ?)').run(chatId, ts, members);
}

export function recordPost(db, post) {
  db.prepare(
    `INSERT OR IGNORE INTO an_channel_posts (chat_id, message_id, date, kind, views)
     VALUES (?, ?, ?, ?, ?)`,
  ).run(post.chat.id, post.message_id, post.date, postKind(post), post.views ?? null);
}

export function postKind(p) {
  for (const k of ['photo', 'video', 'animation', 'document', 'audio', 'voice', 'poll']) if (p[k]) return k;
  return 'text';
}

export function recordMemberEvent(db, chatId, userId, kind, date) {
  db.prepare('INSERT INTO an_channel_member_events (chat_id, user_id, kind, date) VALUES (?, ?, ?, ?)').run(chatId, userId, kind, date);
}

export function channelList(db, ownerId, days = 30, now = Date.now() / 1000) {
  const from = since(days, now);
  return db
    .prepare(
      `SELECT c.chat_id, c.title, c.username, c.type, c.active,
         (SELECT members FROM an_channel_snapshots s WHERE s.chat_id = c.chat_id ORDER BY ts DESC LIMIT 1) AS members,
         (SELECT members FROM an_channel_snapshots s WHERE s.chat_id = c.chat_id AND ts >= ? ORDER BY ts ASC LIMIT 1) AS members_start,
         (SELECT COUNT(*) FROM an_channel_posts p WHERE p.chat_id = c.chat_id AND p.date >= ?) AS posts,
         (SELECT COUNT(*) FROM an_channel_member_events e WHERE e.chat_id = c.chat_id AND e.kind = 'join' AND e.date >= ?) AS joins,
         (SELECT COUNT(*) FROM an_channel_member_events e WHERE e.chat_id = c.chat_id AND e.kind = 'leave' AND e.date >= ?) AS leaves
       FROM an_channels c WHERE c.owner_id = ? ORDER BY c.title`,
    )
    .all(from, from, from, from, ownerId)
    .map((c) => ({ ...c, growth: c.members != null && c.members_start != null ? c.members - c.members_start : null }));
}

export function channelReport(db, chatId, days = 30, now = Date.now() / 1000) {
  const from = since(days, now);
  const members = db
    .prepare(
      `SELECT date(ts, 'unixepoch') AS day, MAX(members) AS value
       FROM an_channel_snapshots WHERE chat_id = ? AND ts >= ? GROUP BY day ORDER BY day`,
    )
    .all(chatId, from);
  const posts = db
    .prepare(
      `SELECT date(date, 'unixepoch') AS day, COUNT(*) AS value
       FROM an_channel_posts WHERE chat_id = ? AND date >= ? GROUP BY day`,
    )
    .all(chatId, from);
  const flow = db
    .prepare(
      `SELECT date(date, 'unixepoch') AS day,
         SUM(kind = 'join') AS joins, SUM(kind = 'leave') AS leaves
       FROM an_channel_member_events WHERE chat_id = ? AND date >= ? GROUP BY day`,
    )
    .all(chatId, from);
  const kinds = db
    .prepare('SELECT kind, COUNT(*) AS n FROM an_channel_posts WHERE chat_id = ? AND date >= ? GROUP BY kind ORDER BY n DESC')
    .all(chatId, from);
  const hours = db
    .prepare(
      `SELECT CAST(strftime('%H', date, 'unixepoch') AS INTEGER) AS hour, COUNT(*) AS n
       FROM an_channel_posts WHERE chat_id = ? AND date >= ? GROUP BY hour ORDER BY n DESC LIMIT 3`,
    )
    .all(chatId, from);
  // Для подписчиков переносим последнее известное значение на дни без снимков
  const filledMembers = fillDays(members, days, now);
  let last = null;
  for (const d of filledMembers) {
    if (d.value) last = d.value;
    else if (last != null) d.value = last;
  }
  return {
    members: filledMembers,
    posts: fillDays(posts, days, now),
    flow: fillDays(flow, days, now, ['joins', 'leaves']),
    postKinds: kinds,
    topHoursUtc: hours,
  };
}

// ---------- Боты (трекинг событий через API-ключ) ----------

export function botList(db, ownerId, days = 30, now = Date.now() / 1000) {
  const from = since(days, now);
  const dayAgo = since(1, now);
  return db
    .prepare(
      `SELECT b.id, b.name, b.api_key, b.created_at,
         (SELECT COUNT(DISTINCT user_id) FROM an_bot_events e WHERE e.bot_id = b.id AND e.ts >= ?) AS users,
         (SELECT COUNT(DISTINCT user_id) FROM an_bot_events e WHERE e.bot_id = b.id AND e.ts >= ?) AS dau,
         (SELECT COUNT(*) FROM an_bot_events e WHERE e.bot_id = b.id AND e.ts >= ?) AS events,
         (SELECT COALESCE(SUM(amount), 0) FROM an_bot_events e WHERE e.bot_id = b.id AND e.event = 'purchase' AND e.ts >= ?) AS revenue
       FROM an_bots b WHERE b.owner_id = ? ORDER BY b.id`,
    )
    .all(from, dayAgo, from, from, ownerId);
}

export function botReport(db, botId, days = 30, now = Date.now() / 1000) {
  const from = since(days, now);
  const daily = db
    .prepare(
      `SELECT date(ts, 'unixepoch') AS day, COUNT(DISTINCT user_id) AS users, COUNT(*) AS events,
         COALESCE(SUM(CASE WHEN event = 'purchase' THEN amount END), 0) AS revenue
       FROM an_bot_events WHERE bot_id = ? AND ts >= ? GROUP BY day`,
    )
    .all(botId, from);
  const newUsers = db
    .prepare(
      `SELECT date(first_ts, 'unixepoch') AS day, COUNT(*) AS value FROM (
         SELECT user_id, MIN(ts) AS first_ts FROM an_bot_events WHERE bot_id = ? GROUP BY user_id
       ) WHERE first_ts >= ? GROUP BY day`,
    )
    .all(botId, from);
  const topEvents = db
    .prepare('SELECT event, COUNT(*) AS n FROM an_bot_events WHERE bot_id = ? AND ts >= ? GROUP BY event ORDER BY n DESC LIMIT 10')
    .all(botId, from);
  const funnel = db
    .prepare(
      `SELECT
         COUNT(DISTINCT user_id) AS users,
         COUNT(DISTINCT CASE WHEN event = 'purchase' THEN user_id END) AS buyers
       FROM an_bot_events WHERE bot_id = ? AND ts >= ?`,
    )
    .get(botId, from);
  return {
    daily: fillDays(daily, days, now, ['users', 'events', 'revenue']),
    newUsers: fillDays(newUsers, days, now),
    topEvents,
    conversion: funnel.users ? +(funnel.buyers / funnel.users * 100).toFixed(1) : 0,
    buyers: funnel.buyers,
  };
}

// ---------- Покупки ----------

/** Выручка: покупки из подключённых ботов владельца + (для админа) продажи собственного магазина. */
export function purchasesReport(db, ownerId, { includeShop = false, days = 30, now = Date.now() / 1000 } = {}) {
  const from = since(days, now);
  const bots = db
    .prepare(
      `SELECT date(e.ts, 'unixepoch') AS day, SUM(e.amount) AS value, COUNT(*) AS n
       FROM an_bot_events e JOIN an_bots b ON b.id = e.bot_id
       WHERE b.owner_id = ? AND e.event = 'purchase' AND e.ts >= ? GROUP BY day`,
    )
    .all(ownerId, from);
  const shop = includeShop
    ? db
        .prepare(
          `SELECT date(paid_at, 'unixepoch') AS day, SUM(price) AS value, COUNT(*) AS n
           FROM orders WHERE status = 'paid' AND paid_at >= ? GROUP BY day`,
        )
        .all(from)
    : [];
  const merged = new Map();
  for (const r of [...bots, ...shop]) {
    const m = merged.get(r.day) || { day: r.day, revenue: 0, orders: 0 };
    m.revenue += r.value;
    m.orders += r.n;
    merged.set(r.day, m);
  }
  const daily = fillDays([...merged.values()], days, now, ['revenue', 'orders']);
  const revenue = daily.reduce((s, d) => s + d.revenue, 0);
  const orders = daily.reduce((s, d) => s + d.orders, 0);
  const topProducts = includeShop
    ? db
        .prepare(
          `SELECT p.title, COUNT(*) AS orders, SUM(o.price) AS revenue FROM orders o JOIN products p ON p.id = o.product_id
           WHERE o.status = 'paid' AND o.paid_at >= ? GROUP BY p.id ORDER BY revenue DESC LIMIT 5`,
        )
        .all(from)
    : [];
  return { daily, revenue, orders, avgCheck: orders ? Math.round(revenue / orders) : 0, topProducts };
}

export function overview(db, ownerId, opts = {}) {
  const channels = channelList(db, ownerId, opts.days, opts.now);
  const bots = botList(db, ownerId, opts.days, opts.now);
  const purchases = purchasesReport(db, ownerId, opts);
  return {
    channels: {
      count: channels.length,
      members: channels.reduce((s, c) => s + (c.members || 0), 0),
      growth: channels.reduce((s, c) => s + (c.growth || 0), 0),
      posts: channels.reduce((s, c) => s + c.posts, 0),
    },
    bots: {
      count: bots.length,
      users: bots.reduce((s, b) => s + b.users, 0),
      dau: bots.reduce((s, b) => s + b.dau, 0),
      events: bots.reduce((s, b) => s + b.events, 0),
    },
    purchases: { revenue: purchases.revenue, orders: purchases.orders, avgCheck: purchases.avgCheck },
    revenueDaily: purchases.daily,
  };
}
