import { test, before, after } from 'node:test';
import assert from 'node:assert/strict';
import { startTestApp } from './helpers.js';
import { changeBalance } from '../src/db.js';

let t;
before(async () => { t = await startTestApp(); });
after(() => t.close());

test('без initData — 401, с ним — профиль и список сервисов', async () => {
  assert.equal((await t.request('/api/me', { user: null })).status, 401);
  const { status, data } = await t.request('/api/me');
  assert.equal(status, 200);
  assert.equal(data.user.id, 2);
  assert.equal(data.isAdmin, false);
  assert.deepEqual(data.services.map((s) => s.id), ['eco', 'home', 'analytics', 'shop', 'profile', 'support']);
});

test('магазин: демо-товары, покупка с баланса и через Stars', async () => {
  const { data } = await t.request('/api/shop/products');
  assert.equal(data.products.length, 3);
  const product = data.products[0];

  let r = await t.request('/api/shop/buy', { method: 'POST', body: { productId: product.id, method: 'balance' } });
  assert.equal(r.status, 402);
  assert.equal(t.db.prepare('SELECT COUNT(*) n FROM orders').get().n, 0, 'неоплаченный заказ не создаётся');

  changeBalance(t.db, 2, 1000, 'bonus', 'тест');
  r = await t.request('/api/shop/buy', { method: 'POST', body: { productId: product.id, method: 'balance' } });
  assert.equal(r.data.status, 'paid');
  assert.equal(t.db.prepare('SELECT balance FROM users WHERE id = 2').get().balance, 1000 - product.price);

  r = await t.request('/api/shop/buy', { method: 'POST', body: { productId: product.id, method: 'stars' } });
  assert.equal(r.data.status, 'pending');
  const inv = t.tg.calls.find((c) => c.method === 'createInvoiceLink');
  assert.equal(inv.params.currency, 'XTR');
  assert.equal(inv.params.payload, `shop:order:${r.data.orderId}`);

  // Telegram присылает pre_checkout_query, затем successful_payment
  const from = { id: 2, first_name: 'Иван' };
  await t.tg.bot.handleUpdate({ update_id: 1, pre_checkout_query: { id: 'pq1', from, currency: 'XTR', total_amount: product.price, invoice_payload: inv.params.payload } });
  const answer = t.tg.calls.find((c) => c.method === 'answerPreCheckoutQuery');
  assert.equal(answer.params.ok, true);
  await t.tg.bot.handleUpdate({ update_id: 2, message: { message_id: 5, from, chat: { id: 2, type: 'private' }, date: 0,
    successful_payment: { currency: 'XTR', total_amount: product.price, invoice_payload: inv.params.payload, telegram_payment_charge_id: 'ch1' } } });
  const orders = (await t.request('/api/shop/orders')).data.orders;
  assert.equal(orders.length, 2);

  // повторная оплата того же заказа отклоняется
  await t.tg.bot.handleUpdate({ update_id: 3, pre_checkout_query: { id: 'pq2', from, currency: 'XTR', total_amount: product.price, invoice_payload: inv.params.payload } });
  assert.equal(t.tg.calls.filter((c) => c.method === 'answerPreCheckoutQuery').at(-1).params.ok, false);
});

test('только админ может добавлять товары', async () => {
  const body = { title: 'Новый', price: 10 };
  assert.equal((await t.request('/api/shop/products', { method: 'POST', body })).status, 403);
  assert.equal((await t.request('/api/shop/products', { method: 'POST', body, user: { id: 1 } })).status, 200);
});

test('пополнение баланса через Stars', async () => {
  const before = t.db.prepare('SELECT balance FROM users WHERE id = 2').get().balance;
  const { data } = await t.request('/api/profile/topup', { method: 'POST', body: { amount: 100 } });
  assert.ok(data.invoiceLink);
  const from = { id: 2, first_name: 'Иван' };
  await t.tg.bot.handleUpdate({ update_id: 10, message: { message_id: 6, from, chat: { id: 2, type: 'private' }, date: 0,
    successful_payment: { currency: 'XTR', total_amount: 100, invoice_payload: 'profile:topup:2:100', telegram_payment_charge_id: 'ch2' } } });
  const p = (await t.request('/api/profile')).data;
  assert.equal(p.user.balance, before + 100);
  assert.equal(p.transactions[0].kind, 'topup');
});

test('поддержка: тикет уходит админу, ответ реплаем возвращается пользователю', async () => {
  const { data } = await t.request('/api/support/tickets', { method: 'POST', body: { text: 'Не работает оплата' } });
  const notify = t.tg.calls.filter((c) => c.method === 'sendMessage' && c.params.chat_id === 1).at(-1);
  assert.match(notify.params.text, new RegExp(`Тикет #${data.id}`));
  const link = t.db.prepare('SELECT message_id FROM support_admin_messages WHERE ticket_id = ?').get(data.id);

  await t.tg.bot.handleUpdate({ update_id: 20, message: { message_id: 900, from: { id: 1, first_name: 'Админ' }, chat: { id: 1, type: 'private' }, date: 0,
    text: 'Уже починили', reply_to_message: { message_id: link.message_id } } });
  const toUser = t.tg.calls.filter((c) => c.method === 'sendMessage' && c.params.chat_id === 2).at(-1);
  assert.match(toUser.params.text, /Уже починили/);
  const ticket = (await t.request('/api/support/tickets')).data.tickets.find((x) => x.id === data.id);
  assert.equal(ticket.status, 'answered');
  assert.equal(ticket.messages.length, 2);
});

test('аналитика ботов: ключ, трекинг событий, отчёт', async () => {
  const { data: b } = await t.request('/api/analytics/bots', { method: 'POST', body: { name: '@shop_bot' } });
  assert.equal((await t.request('/api/track', { user: null, method: 'POST', body: { api_key: 'bad', user_id: 1, event: 'x' } })).status, 401);
  const r = await t.request('/api/track', { user: null, method: 'POST', body: { api_key: b.apiKey, events: [
    { user_id: 11, event: 'start' }, { user_id: 12, event: 'start' }, { user_id: 11, event: 'purchase', amount: 300 },
  ] } });
  assert.equal(r.data.accepted, 3);
  const rep = (await t.request(`/api/analytics/bots/${b.id}`)).data;
  assert.equal(rep.buyers, 1);
  assert.equal(rep.conversion, 50);
  assert.equal(rep.daily.at(-1).revenue, 300);
  // чужой бот недоступен
  assert.equal((await t.request(`/api/analytics/bots/${b.id}`, { user: { id: 99 } })).status, 404);
  const o = (await t.request('/api/analytics/overview')).data;
  assert.equal(o.bots.users, 2);
  assert.equal(o.purchases.revenue, 300);
});

test('аналитика каналов: подключение, посты, подписки', async () => {
  const chat = { id: -1001, type: 'channel', title: 'Мой канал' };
  t.tg.calls.length = 0;
  await t.tg.bot.handleUpdate({ update_id: 30, my_chat_member: { chat, from: { id: 2 }, date: 0,
    old_chat_member: { status: 'left', user: { id: 555 } }, new_chat_member: { status: 'administrator', user: { id: 555 } } } });
  assert.ok(t.tg.calls.some((c) => c.method === 'getChatMemberCount'));
  const now = Math.floor(Date.now() / 1000);
  await t.tg.bot.handleUpdate({ update_id: 31, channel_post: { chat, message_id: 1, date: now, text: 'hi' } });
  await t.tg.bot.handleUpdate({ update_id: 32, channel_post: { chat, message_id: 2, date: now, photo: [{}] } });
  await t.tg.bot.handleUpdate({ update_id: 33, chat_member: { chat, from: { id: 77 }, date: now,
    old_chat_member: { status: 'left', user: { id: 77 } }, new_chat_member: { status: 'member', user: { id: 77 } } } });

  const list = (await t.request('/api/analytics/channels')).data.channels;
  assert.equal(list.length, 1);
  assert.equal(list[0].posts, 2);
  assert.equal(list[0].joins, 1);
  const rep = (await t.request(`/api/analytics/channels/${chat.id}`)).data;
  assert.equal(rep.posts.at(-1).value, 2);
  assert.deepEqual(rep.postKinds.map((k) => k.kind).sort(), ['photo', 'text']);
  // подписчик канала не становится пользователем приложения
  assert.equal(t.db.prepare('SELECT COUNT(*) n FROM users WHERE id = 77').get().n, 0);
});

test('Mini App отдаётся как статика', async () => {
  const res = await fetch(`http://127.0.0.1:${t.app.server.address().port}/`);
  assert.match(await res.text(), /telegram-web-app\.js/);
});
