import { test, before, after } from 'node:test';
import assert from 'node:assert/strict';
import { startTestApp } from './helpers.js';
import { levelOf, checkinXp, streakOf, checkin } from '../src/services/home/index.js';
import { upsertUser } from '../src/db.js';

let t;
before(async () => { t = await startTestApp({ REFERRAL_BONUS: '10' }); t.tg.bot.me = { username: 'star_test_bot' }; });
after(() => t.close());

test('уровни и опыт за серию', () => {
  assert.deepEqual([levelOf(0).level, levelOf(49).level, levelOf(50).level, levelOf(200).level], [1, 1, 2, 3]);
  assert.deepEqual([1, 2, 7, 20].map(checkinXp), [10, 15, 40, 40]);
});

test('серия считается по дням подряд и прерывается при пропуске', () => {
  upsertUser(t.db, { id: 500 });
  const d = (n) => Date.UTC(2026, 0, n, 12) / 1000;
  checkin(t.db, 500, d(1)); checkin(t.db, 500, d(2)); checkin(t.db, 500, d(3));
  assert.equal(streakOf(t.db, 500, d(3)).streak, 3);
  assert.equal(streakOf(t.db, 500, d(4)).streak, 3, 'вчера отмечался — серия ещё жива');
  assert.equal(streakOf(t.db, 500, d(5)).streak, 0, 'пропуск — серия сброшена');
  assert.throws(() => checkin(t.db, 500, d(3)), /уже получен/);
});

test('ежедневный бонус через API, повтор — 409', async () => {
  const r = await t.request('/api/home/checkin', { method: 'POST' });
  assert.equal(r.status, 200);
  assert.equal(r.data.streak, 1);
  assert.equal((await t.request('/api/home/checkin', { method: 'POST' })).status, 409);
  const h = (await t.request('/api/home')).data;
  assert.equal(h.streak.checkedToday, true);
  assert.ok(h.level.xp >= 10);
  assert.equal(h.feed[0].title, 'Ежедневный бонус');
  assert.ok(h.achievements.find((a) => a.id === 'first_day').unlocked);
});

test('реферал: /start ref_<id>, бонус после первой оплаты, один раз', async () => {
  await t.request('/api/home'); // пригласивший (id 2) существует
  const friend = { id: 3001, first_name: 'Петя' };
  await t.tg.bot.handleUpdate({ update_id: 1, message: { message_id: 1, from: friend, chat: { id: 3001, type: 'private' }, date: 0, text: '/start ref_2' } });
  assert.equal(t.db.prepare('SELECT referred_by FROM users WHERE id = 3001').get().referred_by, 2);
  // повторный /start с другой ссылкой не перезаписывает пригласившего
  await t.tg.bot.handleUpdate({ update_id: 2, message: { message_id: 2, from: friend, chat: { id: 3001, type: 'private' }, date: 0, text: '/start ref_1' } });
  assert.equal(t.db.prepare('SELECT referred_by FROM users WHERE id = 3001').get().referred_by, 2);

  const before = t.db.prepare('SELECT balance FROM users WHERE id = 2').get().balance;
  const pay = (id) => t.tg.bot.handleUpdate({ update_id: id, message: { message_id: id, from: friend, chat: { id: 3001, type: 'private' }, date: 0,
    successful_payment: { currency: 'XTR', total_amount: 50, invoice_payload: 'profile:topup:3001:50', telegram_payment_charge_id: `c${id}` } } });
  await pay(3); await pay(4);
  assert.equal(t.db.prepare('SELECT balance FROM users WHERE id = 2').get().balance, before + 10);

  const h = (await t.request('/api/home')).data;
  assert.equal(h.referral.invited, 1);
  assert.equal(h.referral.paid, 1);
  assert.equal(h.referral.link, 'https://t.me/star_test_bot?start=ref_2');
  assert.ok(h.feed.some((f) => f.title === 'Новый друг'));
});

test('нельзя пригласить самого себя', async () => {
  const u = { id: 4001, first_name: 'Я' };
  await t.tg.bot.handleUpdate({ update_id: 9, message: { message_id: 9, from: u, chat: { id: 4001, type: 'private' }, date: 0, text: '/start ref_4001' } });
  assert.equal(t.db.prepare('SELECT referred_by FROM users WHERE id = 4001').get().referred_by, null);
});
