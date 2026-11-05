import { isAdmin } from '../index.js';

const bad = (msg, status = 400) => Object.assign(new Error(msg), { status });
const esc = (s) => String(s).replace(/[&<>]/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;' })[c]);

async function notifyAdmins({ bot, config, db }, ticketId, user, text) {
  const who = user.username ? `@${user.username}` : `${esc(user.first_name || '')} (id ${user.id})`;
  const msg = `🆘 <b>Тикет #${ticketId}</b> от ${who}\n\n${esc(text)}\n\n<i>Ответьте реплаем на это сообщение, чтобы ответить пользователю. /close ${ticketId} — закрыть.</i>`;
  const targets = config.supportChatId ? [config.supportChatId] : config.adminIds;
  for (const chatId of targets) {
    try {
      const sent = await bot.sendMessage(chatId, msg);
      db.prepare('INSERT INTO support_admin_messages (chat_id, message_id, ticket_id) VALUES (?, ?, ?)').run(chatId, sent.message_id, ticketId);
    } catch (e) {
      console.error('[support] не удалось уведомить', chatId, e.message);
    }
  }
}

export function createTicket(db, userId, text) {
  text = String(text || '').trim();
  if (!text) throw bad('Опишите вопрос');
  if (text.length > 4000) throw bad('Слишком длинное сообщение');
  const id = Number(db.prepare('INSERT INTO support_tickets (user_id) VALUES (?)').run(userId).lastInsertRowid);
  db.prepare('INSERT INTO support_messages (ticket_id, from_admin, text) VALUES (?, 0, ?)').run(id, text);
  return id;
}

export default {
  id: 'support',
  title: 'Поддержка',
  icon: '💬',
  order: 40,

  migrations: [
    `CREATE TABLE IF NOT EXISTS support_tickets (
       id INTEGER PRIMARY KEY AUTOINCREMENT,
       user_id INTEGER NOT NULL REFERENCES users(id),
       status TEXT NOT NULL DEFAULT 'open',  -- open | answered | closed
       created_at INTEGER NOT NULL DEFAULT (unixepoch()),
       updated_at INTEGER NOT NULL DEFAULT (unixepoch())
     )`,
    `CREATE TABLE IF NOT EXISTS support_messages (
       id INTEGER PRIMARY KEY AUTOINCREMENT,
       ticket_id INTEGER NOT NULL REFERENCES support_tickets(id),
       from_admin INTEGER NOT NULL DEFAULT 0,
       text TEXT NOT NULL,
       created_at INTEGER NOT NULL DEFAULT (unixepoch())
     )`,
    `CREATE TABLE IF NOT EXISTS support_admin_messages (
       chat_id INTEGER NOT NULL,
       message_id INTEGER NOT NULL,
       ticket_id INTEGER NOT NULL,
       PRIMARY KEY (chat_id, message_id)
     )`,
  ],

  setupApi(router, ctx) {
    router.get('/api/support/tickets', ({ db, user }) => {
      const tickets = db.prepare('SELECT * FROM support_tickets WHERE user_id = ? ORDER BY updated_at DESC LIMIT 30').all(user.id);
      const msgs = db.prepare('SELECT ticket_id, from_admin, text, created_at FROM support_messages WHERE ticket_id = ? ORDER BY id');
      return { tickets: tickets.map((t) => ({ ...t, messages: msgs.all(t.id) })) };
    });

    router.post('/api/support/tickets', async ({ db, user, body }) => {
      const id = createTicket(db, user.id, body.text);
      await notifyAdmins(ctx, id, user, body.text);
      return { id };
    });

    router.post('/api/support/tickets/:id/messages', async ({ db, user, params, body }) => {
      const ticket = db.prepare('SELECT * FROM support_tickets WHERE id = ? AND user_id = ?').get(Number(params.id), user.id);
      if (!ticket) throw bad('Обращение не найдено', 404);
      const text = String(body.text || '').trim();
      if (!text) throw bad('Пустое сообщение');
      db.prepare('INSERT INTO support_messages (ticket_id, from_admin, text) VALUES (?, 0, ?)').run(ticket.id, text);
      db.prepare("UPDATE support_tickets SET status = 'open', updated_at = unixepoch() WHERE id = ?").run(ticket.id);
      await notifyAdmins(ctx, ticket.id, user, text);
      return { ok: true };
    });
  },

  setupBot(ctx) {
    const { bot, db, config } = ctx;

    bot.command('support', async ({ payload, args }) => {
      if (!args) return bot.sendMessage(payload.chat.id, 'Напишите вопрос после команды: <code>/support ваш вопрос</code>');
      const id = createTicket(db, payload.from.id, args);
      await notifyAdmins(ctx, id, payload.from, args);
      await bot.sendMessage(payload.chat.id, `📨 Обращение #${id} создано. Ответ придёт сюда.`);
    }, 'Написать в поддержку');

    bot.command('close', async ({ payload, args }) => {
      if (!isAdmin(config, payload.from.id)) return;
      const id = Number(args);
      const ticket = db.prepare('SELECT * FROM support_tickets WHERE id = ?').get(id);
      if (!ticket) return bot.sendMessage(payload.chat.id, 'Тикет не найден');
      db.prepare("UPDATE support_tickets SET status = 'closed', updated_at = unixepoch() WHERE id = ?").run(id);
      await bot.sendMessage(payload.chat.id, `Тикет #${id} закрыт`);
      await bot.sendMessage(ticket.user_id, `✅ Ваше обращение #${id} закрыто. Если остались вопросы — напишите снова.`);
    });

    // Ответ админа реплаем на уведомление о тикете
    bot.on(
      'message',
      async ({ payload }) => {
        const link = db
          .prepare('SELECT ticket_id FROM support_admin_messages WHERE chat_id = ? AND message_id = ?')
          .get(payload.chat.id, payload.reply_to_message.message_id);
        if (!link) return false;
        const ticket = db.prepare('SELECT * FROM support_tickets WHERE id = ?').get(link.ticket_id);
        db.prepare('INSERT INTO support_messages (ticket_id, from_admin, text) VALUES (?, 1, ?)').run(ticket.id, payload.text);
        db.prepare("UPDATE support_tickets SET status = 'answered', updated_at = unixepoch() WHERE id = ?").run(ticket.id);
        await bot.sendMessage(ticket.user_id, `💬 <b>Ответ поддержки по обращению #${ticket.id}:</b>\n\n${esc(payload.text)}`);
        await bot.sendMessage(payload.chat.id, '✅ Ответ отправлен', { reply_to_message_id: payload.message_id });
      },
      (m) => m.text && m.reply_to_message && isAdmin(config, m.from?.id),
    );
  },
};
