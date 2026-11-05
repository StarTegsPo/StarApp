import { changeBalance } from '../../db.js';
import { createStarsInvoice } from '../index.js';

export default {
  id: 'profile',
  title: 'Профиль',
  icon: '👤',
  order: 30,

  setupApi(router, ctx) {
    router.get('/api/profile', ({ db, user }) => {
      const me = db.prepare('SELECT id, username, first_name, last_name, balance, created_at FROM users WHERE id = ?').get(user.id);
      const transactions = db
        .prepare('SELECT id, amount, kind, description, created_at FROM transactions WHERE user_id = ? ORDER BY id DESC LIMIT 50')
        .all(user.id);
      const totals = db
        .prepare(
          `SELECT COALESCE(SUM(CASE WHEN amount > 0 THEN amount END), 0) AS income,
                  COALESCE(-SUM(CASE WHEN amount < 0 THEN amount END), 0) AS spent
           FROM transactions WHERE user_id = ?`,
        )
        .get(user.id);
      return { user: me, totals, transactions };
    });

    router.post('/api/profile/topup', async ({ bot, body, user }) => {
      const amount = Math.floor(Number(body.amount));
      if (!Number.isFinite(amount) || amount < 1 || amount > 100000) {
        throw Object.assign(new Error('Сумма должна быть от 1 до 100000 ⭐'), { status: 400 });
      }
      const link = await createStarsInvoice(bot, 'profile', {
        title: 'Пополнение баланса',
        description: `Пополнение баланса StarApp на ${amount} ⭐`,
        payload: `topup:${user.id}:${amount}`,
        amount,
      });
      return { invoiceLink: link };
    });
  },

  validatePayment(ctx, { user, payload, totalAmount }) {
    const [kind, uid, amount] = payload.split(':');
    if (kind !== 'topup' || Number(uid) !== user.id || Number(amount) !== totalAmount) return 'Некорректный платёж';
    return null;
  },

  async onPayment({ db, bot }, { user, payload, payment }) {
    const [, , amount] = payload.split(':');
    const balance = changeBalance(db, user.id, Number(amount), 'topup', 'Пополнение через Telegram Stars', payment.telegram_payment_charge_id);
    await bot.sendMessage(user.id, `✅ Баланс пополнен на <b>${amount} ⭐</b>. Текущий баланс: <b>${balance} ⭐</b>`);
  },

  setupBot({ bot, db }) {
    bot.command('balance', async ({ payload }) => {
      const row = db.prepare('SELECT balance FROM users WHERE id = ?').get(payload.from.id);
      await bot.sendMessage(payload.chat.id, `💰 Ваш баланс: <b>${row?.balance ?? 0} ⭐</b>`);
    }, 'Мой баланс');
  },
};
