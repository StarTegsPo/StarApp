import { addColumn, changeBalance } from '../../db.js';
import { isAdmin } from '../index.js';
import * as q from '../analytics/queries.js';

/** Главный экран: лента событий, ежедневный бонус, уровни, достижения, рефералы. */

const DAY = 86400;
const today = (now = Date.now() / 1000) => new Date(Math.floor(now) * 1000).toISOString().slice(0, 10);
const dayShift = (day, n) => new Date(Date.parse(day) + n * DAY * 1000).toISOString().slice(0, 10);
const bad = (msg, status = 400) => Object.assign(new Error(msg), { status });

// ---------- Серия и опыт ----------

/** Текущая серия: подряд идущие дни с отметкой, заканчивающиеся сегодня или вчера. */
export function streakOf(db, userId, now = Date.now() / 1000) {
  const days = new Set(db.prepare('SELECT day FROM home_checkins WHERE user_id = ? ORDER BY day DESC LIMIT 400').all(userId).map((r) => r.day));
  const t = today(now);
  const checkedToday = days.has(t);
  let d = checkedToday ? t : dayShift(t, -1);
  let streak = 0;
  while (days.has(d)) { streak++; d = dayShift(d, -1); }
  return { streak, checkedToday, total: days.size };
}

/** Опыт за отметку: 10 за первый день серии, +5 за каждый следующий, максимум 40. */
export const checkinXp = (streakDay) => Math.min(10 + 5 * (streakDay - 1), 40);

/** Уровень: для уровня n нужно 50·(n−1)² опыта. */
export function levelOf(xp) {
  const level = Math.floor(Math.sqrt(xp / 50)) + 1;
  const from = 50 * (level - 1) ** 2;
  const to = 50 * level ** 2;
  return { level, xp, from, to, progress: (xp - from) / (to - from) };
}

export function checkin(db, userId, now = Date.now() / 1000) {
  const s = streakOf(db, userId, now);
  if (s.checkedToday) throw bad('Бонус за сегодня уже получен — возвращайтесь завтра 🌙', 409);
  const streak = s.streak + 1;
  const xp = checkinXp(streak);
  db.prepare('INSERT INTO home_checkins (user_id, day, xp) VALUES (?, ?, ?)').run(userId, today(now), xp);
  db.prepare('UPDATE users SET xp = xp + ? WHERE id = ?').run(xp, userId);
  return { streak, xp };
}

// ---------- Достижения ----------

const ACHIEVEMENTS = [
  { id: 'first_day', icon: '👋', title: 'Добро пожаловать', desc: 'Открыть StarApp', xp: 10, check: () => [1, 1] },
  { id: 'streak3', icon: '🔥', title: 'В ритме', desc: 'Серия 3 дня', xp: 30, check: (d) => [d.streak.streak, 3] },
  { id: 'streak7', icon: '⚡', title: 'Неделя без пропусков', desc: 'Серия 7 дней', xp: 100, check: (d) => [d.streak.streak, 7] },
  { id: 'channel', icon: '📢', title: 'Медиамагнат', desc: 'Подключить канал', xp: 50, check: (d) => [d.channels, 1] },
  { id: 'bot', icon: '🤖', title: 'Бот-мастер', desc: 'Подключить бота', xp: 50, check: (d) => [d.bots, 1] },
  { id: 'k1', icon: '🚀', title: 'Первая тысяча', desc: '1000 подписчиков в каналах', xp: 150, check: (d) => [d.members, 1000] },
  { id: 'buyer', icon: '🛍', title: 'Первая покупка', desc: 'Купить в магазине', xp: 40, check: (d) => [d.orders, 1] },
  { id: 'friends3', icon: '🎁', title: 'Душа компании', desc: 'Пригласить 3 друзей', xp: 120, check: (d) => [d.invited, 3] },
];

function achievementsOf(db, userId, streak) {
  const o = q.overview(db, userId, { days: 30 });
  const data = {
    streak,
    channels: o.channels.count,
    bots: o.bots.count,
    members: o.channels.members,
    orders: db.prepare("SELECT COUNT(*) AS n FROM orders WHERE user_id = ? AND status = 'paid'").get(userId).n,
    invited: db.prepare('SELECT COUNT(*) AS n FROM users WHERE referred_by = ?').get(userId).n,
  };
  return {
    overview: o,
    list: ACHIEVEMENTS.map(({ check, ...a }) => {
      const [value, target] = check(data);
      return { ...a, unlocked: value >= target, value: Math.min(value, target), target };
    }),
  };
}

// ---------- Лента событий ----------

export function feedOf(db, userId, limit = 30) {
  const since = Math.floor(Date.now() / 1000) - 30 * DAY;
  const items = [];
  const push = (ts, icon, title, text, tone = '') => items.push({ ts, icon, title, text, tone });

  for (const t of db.prepare('SELECT amount, kind, description, created_at FROM transactions WHERE user_id = ? AND created_at >= ? ORDER BY id DESC LIMIT 20').all(userId, since)) {
    if (t.kind === 'topup') push(t.created_at, '💰', 'Баланс пополнен', `+${t.amount} ⭐`, 'good');
    else if (t.kind === 'bonus') push(t.created_at, '🎁', 'Бонус', `${t.description} · +${t.amount} ⭐`, 'good');
    else if (t.kind === 'purchase') push(t.created_at, '🛍', 'Покупка', String(t.description || '').replace(/^Покупка:\s*/, ''));
  }
  for (const m of db.prepare(
    `SELECT m.ticket_id, m.text, m.created_at FROM support_messages m JOIN support_tickets t ON t.id = m.ticket_id
     WHERE t.user_id = ? AND m.from_admin = 1 AND m.created_at >= ? ORDER BY m.id DESC LIMIT 10`).all(userId, since)) {
    push(m.created_at, '💬', `Ответ поддержки #${m.ticket_id}`, m.text.slice(0, 80));
  }
  for (const c of db.prepare(
    `SELECT c.title, date(e.date, 'unixepoch') AS day, MAX(e.date) AS ts, SUM(e.kind = 'join') AS joins, SUM(e.kind = 'leave') AS leaves
     FROM an_channel_member_events e JOIN an_channels c ON c.chat_id = e.chat_id
     WHERE c.owner_id = ? AND e.date >= ? GROUP BY c.chat_id, day ORDER BY ts DESC LIMIT 10`).all(userId, since)) {
    const net = c.joins - c.leaves;
    push(c.ts, net >= 0 ? '📈' : '📉', c.title, `${net >= 0 ? '+' : ''}${net} подписчиков за ${c.day.slice(5).split('-').reverse().join('.')} (пришло ${c.joins}, ушло ${c.leaves})`, net >= 0 ? 'good' : 'bad');
  }
  for (const p of db.prepare(
    `SELECT c.title, date(p.date, 'unixepoch') AS day, MAX(p.date) AS ts, COUNT(*) AS n
     FROM an_channel_posts p JOIN an_channels c ON c.chat_id = p.chat_id
     WHERE c.owner_id = ? AND p.date >= ? GROUP BY c.chat_id, day ORDER BY ts DESC LIMIT 5`).all(userId, since)) {
    push(p.ts, '📝', p.title || 'Канал', p.n === 1 ? 'Новый пост' : `${p.n} ${plural(p.n, 'новый пост', 'новых поста', 'новых постов')} за день`);
  }
  for (const e of db.prepare(
    `SELECT b.name, date(e.ts, 'unixepoch') AS day, MAX(e.ts) AS ts, COUNT(*) AS n, SUM(e.amount) AS amount
     FROM an_bot_events e JOIN an_bots b ON b.id = e.bot_id
     WHERE b.owner_id = ? AND e.event = 'purchase' AND e.ts >= ? GROUP BY b.id, day ORDER BY ts DESC LIMIT 7`).all(userId, since)) {
    push(e.ts, '💸', `Продажи в ${e.name}`, `${e.n} ${plural(e.n, 'покупка', 'покупки', 'покупок')} на ${e.amount} ⭐`, 'good');
  }
  for (const u of db.prepare('SELECT first_name, username, created_at FROM users WHERE referred_by = ? AND created_at >= ? ORDER BY created_at DESC LIMIT 10').all(userId, since)) {
    push(u.created_at, '🤝', 'Новый друг', `${u.first_name || u.username || 'Пользователь'} присоединился по вашей ссылке`, 'good');
  }
  for (const c of db.prepare('SELECT day, xp FROM home_checkins WHERE user_id = ? ORDER BY day DESC LIMIT 3').all(userId)) {
    push(Math.floor(Date.parse(c.day) / 1000) + 1, '🔥', 'Ежедневный бонус', `+${c.xp} опыта`);
  }
  return items.sort((a, b) => b.ts - a.ts).slice(0, limit);
}
const plural = (n, one, few, many) => {
  const m10 = n % 10, m100 = n % 100;
  return m10 === 1 && m100 !== 11 ? one : m10 >= 2 && m10 <= 4 && (m100 < 10 || m100 >= 20) ? few : many;
};

// ---------- Сервис ----------

export default {
  id: 'home',
  title: 'Главная',
  icon: '🏠',
  order: 0,

  migrations: [
    `CREATE TABLE IF NOT EXISTS home_checkins (
       user_id INTEGER NOT NULL, day TEXT NOT NULL, xp INTEGER NOT NULL,
       PRIMARY KEY (user_id, day)
     )`,
    (db) => addColumn(db, 'users', 'xp', 'INTEGER NOT NULL DEFAULT 0'),
    (db) => addColumn(db, 'users', 'referred_by', 'INTEGER'),
    (db) => addColumn(db, 'users', 'referral_rewarded', 'INTEGER NOT NULL DEFAULT 0'),
  ],

  /** /start ref_<id> — запоминаем, кто пригласил (только для новых пользователей). */
  async onStart({ db, bot }, { user, args, isNew }) {
    const m = /^ref_(\d+)$/.exec(args || '');
    if (!m || !isNew) return;
    const refId = Number(m[1]);
    if (refId === user.id || !db.prepare('SELECT 1 FROM users WHERE id = ?').get(refId)) return;
    db.prepare('UPDATE users SET referred_by = ? WHERE id = ? AND referred_by IS NULL').run(refId, user.id);
    await bot.sendMessage(refId, `🤝 <b>${(user.first_name || 'Новый пользователь').replace(/[<>&]/g, '')}</b> присоединился по вашей ссылке! Когда друг сделает первую оплату, вы получите бонус.`).catch(() => {});
  },

  /** Первая оплата приглашённого — бонус пригласившему. */
  async afterPayment({ db, bot, config }, { user }) {
    if (!config.referralBonus) return;
    const u = db.prepare('SELECT referred_by, referral_rewarded, first_name FROM users WHERE id = ?').get(user.id);
    if (!u?.referred_by || u.referral_rewarded) return;
    db.prepare('UPDATE users SET referral_rewarded = 1 WHERE id = ?').run(user.id);
    changeBalance(db, u.referred_by, config.referralBonus, 'bonus', `За приглашённого друга ${u.first_name || ''}`.trim());
    await bot.sendMessage(u.referred_by, `🎁 Ваш друг сделал первую покупку — вам начислено <b>${config.referralBonus} ⭐</b>!`).catch(() => {});
  },

  setupApi(router, { bot, config }) {
    router.get('/api/home', ({ db, user }) => {
      const me = db.prepare('SELECT id, first_name, username, balance, xp FROM users WHERE id = ?').get(user.id);
      const streak = streakOf(db, user.id);
      const { overview, list } = achievementsOf(db, user.id, streak);
      const achXp = list.filter((a) => a.unlocked).reduce((s, a) => s + a.xp, 0);
      const week = overview.revenueDaily.slice(-7);
      return {
        user: { ...me, photo_url: user.photo_url ?? null },
        level: levelOf(me.xp + achXp),
        streak: { ...streak, nextXp: checkinXp(streak.streak + 1) },
        stats: {
          members: overview.channels.members,
          growth: overview.channels.growth,
          botUsers: overview.bots.users,
          dau: overview.bots.dau,
          revenue7: week.reduce((s, d) => s + d.revenue, 0),
          revenueSpark: week,
          channels: overview.channels.count,
          bots: overview.bots.count,
        },
        achievements: list,
        referral: {
          link: bot.me?.username ? `https://t.me/${bot.me.username}?start=ref_${user.id}` : null,
          invited: db.prepare('SELECT COUNT(*) AS n FROM users WHERE referred_by = ?').get(user.id).n,
          paid: db.prepare('SELECT COUNT(*) AS n FROM users WHERE referred_by = ? AND referral_rewarded = 1').get(user.id).n,
          bonus: config.referralBonus,
        },
        feed: feedOf(db, user.id),
        isAdmin: isAdmin(config, user.id),
      };
    });

    router.post('/api/home/checkin', ({ db, user }) => checkin(db, user.id));
  },
};
