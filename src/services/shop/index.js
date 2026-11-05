import { tx } from '../../db.js';
import { createStarsInvoice, isAdmin } from '../index.js';

const bad = (msg, status = 400) => Object.assign(new Error(msg), { status });

const DEMO_PRODUCTS = [
  ['PRO-аналитика на месяц', 'Расширенные отчёты по каналам и ботам, экспорт данных', 250],
  ['Аудит канала', 'Разбор канала экспертом: контент, рост, монетизация', 500],
  ['Подключение бота', 'Помощь в подключении вашего бота к трекингу событий', 150],
];

export function addProduct(db, { title, description = '', price }) {
  price = Math.floor(Number(price));
  if (!title || !Number.isFinite(price) || price < 1) throw bad('Нужно название и цена ≥ 1');
  const r = db.prepare('INSERT INTO products (title, description, price) VALUES (?, ?, ?)').run(title, description, price);
  return Number(r.lastInsertRowid);
}

function markPaid(db, orderId, via, chargeId = null) {
  db.prepare("UPDATE orders SET status = 'paid', paid_via = ?, charge_id = ?, paid_at = unixepoch() WHERE id = ?").run(via, chargeId, orderId);
}

export default {
  id: 'shop',
  title: 'Магазин',
  icon: '🛍',
  order: 20,

  migrations: [
    `CREATE TABLE IF NOT EXISTS products (
       id INTEGER PRIMARY KEY AUTOINCREMENT,
       title TEXT NOT NULL,
       description TEXT,
       price INTEGER NOT NULL,
       active INTEGER NOT NULL DEFAULT 1,
       created_at INTEGER NOT NULL DEFAULT (unixepoch())
     )`,
    `CREATE TABLE IF NOT EXISTS orders (
       id INTEGER PRIMARY KEY AUTOINCREMENT,
       user_id INTEGER NOT NULL REFERENCES users(id),
       product_id INTEGER NOT NULL REFERENCES products(id),
       price INTEGER NOT NULL,
       status TEXT NOT NULL DEFAULT 'pending', -- pending | paid
       paid_via TEXT,                           -- stars | balance
       charge_id TEXT,
       created_at INTEGER NOT NULL DEFAULT (unixepoch()),
       paid_at INTEGER
     )`,
  ],

  start({ db }) {
    const { n } = db.prepare('SELECT COUNT(*) AS n FROM products').get();
    if (n === 0) for (const [title, description, price] of DEMO_PRODUCTS) addProduct(db, { title, description, price });
  },

  setupApi(router) {
    router.get('/api/shop/products', ({ db }) => ({
      products: db
        .prepare(
          `SELECT p.id, p.title, p.description, p.price,
             (SELECT COUNT(*) FROM orders o WHERE o.product_id = p.id AND o.status = 'paid') AS sold
           FROM products p WHERE p.active = 1 ORDER BY p.id`,
        )
        .all(),
    }));

    router.get('/api/shop/orders', ({ db, user }) => ({
      orders: db
        .prepare(
          `SELECT o.id, o.price, o.status, o.paid_via, o.created_at, p.title
           FROM orders o JOIN products p ON p.id = o.product_id
           WHERE o.user_id = ? AND o.status = 'paid' ORDER BY o.id DESC LIMIT 50`,
        )
        .all(user.id),
    }));

    router.post('/api/shop/buy', async ({ db, bot, user, body }) => {
      const product = db.prepare('SELECT * FROM products WHERE id = ? AND active = 1').get(Number(body.productId));
      if (!product) throw bad('Товар не найден', 404);
      const createOrder = () =>
        Number(db.prepare('INSERT INTO orders (user_id, product_id, price) VALUES (?, ?, ?)').run(user.id, product.id, product.price).lastInsertRowid);

      if (body.method === 'balance') {
        const orderId = tx(db, () => {
          const { balance } = db.prepare('SELECT balance FROM users WHERE id = ?').get(user.id);
          if (balance < product.price) throw bad('Недостаточно средств на балансе', 402);
          const id = createOrder();
          db.prepare('UPDATE users SET balance = balance - ? WHERE id = ?').run(product.price, user.id);
          db.prepare("INSERT INTO transactions (user_id, amount, kind, description) VALUES (?, ?, 'purchase', ?)").run(
            user.id, -product.price, `Покупка: ${product.title}`,
          );
          markPaid(db, id, 'balance');
          return id;
        });
        return { status: 'paid', orderId };
      }

      const orderId = createOrder();
      const invoiceLink = await createStarsInvoice(bot, 'shop', {
        title: product.title,
        description: product.description,
        payload: `order:${orderId}`,
        amount: product.price,
      });
      return { status: 'pending', orderId, invoiceLink };
    });

    router.post('/api/shop/products', ({ db, config, user, body }) => {
      if (!isAdmin(config, user.id)) throw bad('Только для администраторов', 403);
      return { id: addProduct(db, body) };
    });

    router.delete('/api/shop/products/:id', ({ db, config, user, params }) => {
      if (!isAdmin(config, user.id)) throw bad('Только для администраторов', 403);
      db.prepare('UPDATE products SET active = 0 WHERE id = ?').run(Number(params.id));
      return { ok: true };
    });
  },

  validatePayment({ db }, { user, payload, totalAmount }) {
    const [, id] = payload.split(':');
    const order = db.prepare('SELECT * FROM orders WHERE id = ?').get(Number(id));
    if (!order || order.user_id !== user.id) return 'Заказ не найден';
    if (order.status !== 'pending') return 'Заказ уже оплачен';
    if (order.price !== totalAmount) return 'Сумма не совпадает';
    return null;
  },

  async onPayment({ db, bot }, { user, payload, payment }) {
    const [, id] = payload.split(':');
    const order = db.prepare('SELECT o.*, p.title FROM orders o JOIN products p ON p.id = o.product_id WHERE o.id = ?').get(Number(id));
    markPaid(db, order.id, 'stars', payment.telegram_payment_charge_id);
    db.prepare("INSERT INTO transactions (user_id, amount, kind, description, telegram_charge_id) VALUES (?, 0, 'purchase', ?, ?)").run(
      user.id, `Покупка за ${order.price} ⭐ Stars: ${order.title}`, payment.telegram_payment_charge_id,
    );
    await bot.sendMessage(user.id, `🛍 Заказ #${order.id} оплачен: <b>${order.title}</b>. Спасибо!`);
  },

  setupBot({ bot, db, config }) {
    // /addproduct Название | цена | описание
    bot.command('addproduct', async ({ payload, args }) => {
      if (!isAdmin(config, payload.from.id)) return;
      const [title, price, description] = args.split('|').map((s) => s?.trim());
      try {
        const id = addProduct(db, { title, price, description });
        await bot.sendMessage(payload.chat.id, `✅ Товар #${id} добавлен`);
      } catch (e) {
        await bot.sendMessage(payload.chat.id, `Формат: /addproduct Название | цена | описание\n${e.message}`);
      }
    });
  },
};
