import { fileURLToPath } from 'node:url';
import { openDb, upsertUser } from './db.js';
import { TelegramBot } from './telegram.js';
import { Router, createHttpServer } from './http.js';
import { services, isAdmin } from './services/index.js';

const WEBAPP_DIR = fileURLToPath(new URL('../webapp/', import.meta.url));

/** Собирает приложение: БД, бот, HTTP API, все сервисы. Ничего не запускает. */
export function createApp(config, { bot = new TelegramBot(config.botToken), db } = {}) {
  db ??= openDb(config.dbPath, services);
  const ctx = { config, db, bot, services };
  const router = new Router();

  // ----- Ядро бота -----
  const webAppButton = (text = '🚀 Открыть StarApp') =>
    config.publicUrl.startsWith('https://')
      ? { inline_keyboard: [[{ text, web_app: { url: config.publicUrl } }]] }
      : undefined;

  // Регистрируем/обновляем пользователя при любом личном взаимодействии
  bot.use(({ type, payload }) => {
    if (!['message', 'callback_query', 'pre_checkout_query', 'my_chat_member'].includes(type)) return;
    const from = payload?.from;
    if (from && !from.is_bot) upsertUser(db, from);
  });

  bot.command('start', async ({ payload }) => {
    await bot.sendMessage(
      payload.chat.id,
      [
        `Привет, ${payload.from.first_name || 'друг'}! 👋`,
        '',
        '<b>StarApp</b> — ваш бизнес-помощник в Telegram:',
        '📊 аналитика каналов, ботов и покупок',
        '🛍 магазин услуг с оплатой в Telegram Stars',
        '👤 профиль и баланс',
        '💬 поддержка',
        '',
        'Чтобы подключить канал — добавьте меня администратором канала.',
      ].join('\n'),
      { reply_markup: webAppButton() },
    );
  }, 'Запуск и главное меню');

  bot.command('help', ({ payload }) =>
    bot.sendMessage(
      payload.chat.id,
      ['/start — главное меню', '/stats — сводка аналитики', '/balance — баланс', '/support текст — написать в поддержку',
        ...(isAdmin(config, payload.from.id) ? ['', '<b>Админ:</b>', '/addproduct Название | цена | описание', '/close N — закрыть тикет', 'Реплай на тикет — ответ пользователю'] : []),
      ].join('\n'),
    ), 'Помощь');

  // ----- Платежи Telegram Stars: маршрутизация по префиксу payload "<serviceId>:..." -----
  const routePayment = (payload) => {
    const i = payload.indexOf(':');
    const service = services.find((s) => s.id === payload.slice(0, i));
    return { service, rest: payload.slice(i + 1) };
  };

  bot.on('pre_checkout_query', async ({ payload: pq }) => {
    const { service, rest } = routePayment(pq.invoice_payload);
    let error = service?.onPayment ? null : 'Неизвестный платёж';
    if (!error && service.validatePayment) {
      error = await service.validatePayment(ctx, { user: pq.from, payload: rest, totalAmount: pq.total_amount });
    }
    await bot.call('answerPreCheckoutQuery', error
      ? { pre_checkout_query_id: pq.id, ok: false, error_message: error }
      : { pre_checkout_query_id: pq.id, ok: true });
  });

  bot.on(
    'message',
    async ({ payload }) => {
      const payment = payload.successful_payment;
      const { service, rest } = routePayment(payment.invoice_payload);
      await service.onPayment(ctx, { user: payload.from, payload: rest, payment });
    },
    (m) => !!m.successful_payment,
  );

  // ----- Ядро API -----
  router.get('/api/me', ({ db, user }) => ({
    user: db.prepare('SELECT id, username, first_name, balance FROM users WHERE id = ?').get(user.id),
    isAdmin: isAdmin(config, user.id),
    botUsername: bot.me?.username ?? null,
    services: services.map(({ id, title, icon }) => ({ id, title, icon })),
  }));

  for (const s of services) {
    s.setupBot?.(ctx);
    s.setupApi?.(router, ctx);
  }

  const server = createHttpServer(router, ctx, WEBAPP_DIR);

  return {
    ctx,
    router,
    server,
    async start() {
      for (const s of services) await s.start?.(ctx);
      await new Promise((r) => server.listen(config.port, r));
      console.log(`[http] API и Mini App: http://localhost:${config.port}`);
      if (config.botToken) {
        await bot.publishCommands().catch((e) => console.error('[bot] setMyCommands', e.message));
        if (config.publicUrl.startsWith('https://')) {
          await bot
            .call('setChatMenuButton', { menu_button: { type: 'web_app', text: 'StarApp', web_app: { url: config.publicUrl } } })
            .catch((e) => console.error('[bot] setChatMenuButton', e.message));
        }
        bot.startPolling();
      } else {
        console.warn('[bot] BOT_TOKEN не задан — бот не запущен, работает только HTTP');
      }
    },
  };
}
