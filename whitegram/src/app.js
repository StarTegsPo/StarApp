import { fileURLToPath } from 'node:url';
import { openDb, migrate, upsertUser } from './db.js';
import { TelegramBot } from './telegram.js';
import { Router, createHttpServer } from './http.js';
import { services, isAdmin, createStarsInvoice } from './services/index.js';
import { readConfig } from './config.js';

const config = readConfig();
const WEBAPP_DIR = fileURLToPath(new URL('../webapp/', import.meta.url));

/** Собирает приложение: БД, бот, HTTP API, все сервисы. Ничего не запускает. */
export function createApp(cfg = config, { bot = new TelegramBot(cfg.botToken), db } = {}) {
  db ??= openDb(cfg.dbPath);
  migrate(db, services);

  const ctx = { config: cfg, db, bot, services };
  const router = new Router();

  // ----- Ядро бота -----
  const webAppButton = (text = '🚀 Открыть WhiteGram') =>
    cfg.publicUrl.startsWith('https://')
      ? { inline_keyboard: [[{ text, web_app: { url: cfg.publicUrl } }]] }
      : undefined;

  // Регистрируем/обновляем пользователя при любом личном взаимодействии
  bot.use((c) => {
    if (!['message', 'callback_query', 'pre_checkout_query', 'my_chat_member'].includes(c.type)) return;
    const from = c.payload?.from;
    if (!from || from.is_bot) return;
    c.isNewUser = !db.prepare('SELECT 1 FROM users WHERE id = ?').get(from.id);
    upsertUser(db, from);
  });

  bot.command('start', async (c) => {
    const { payload, args, isNewUser } = c;
    for (const s of services) await s.onStart?.(ctx, { user: payload.from, args, isNew: !!isNewUser });
    await bot.sendMessage(
      payload.chat.id,
      [
        `Привет, ${payload.from.first_name || 'друг'}! 👋`,
        '',
        '<b>⚪ WhiteGram</b> — ваш личный профиль в Telegram:',
        '',
        '👤 <b>Профиль</b> с балансом и историей',
        '⭐ <b>Пополнение</b> через Telegram Stars',
        '📊 <b>Статистика</b> операций',
        '',
        '➕ Откройте Mini App кнопкой ниже.',
      ].join('\n'),
      { reply_markup: webAppButton() },
    );
  }, 'Запуск и главное меню');

  bot.command('help', ({ payload }) =>
    bot.sendMessage(
      payload.chat.id,
      ['/start — главное меню', '/balance — баланс', '/help — помощь',
        ...(isAdmin(cfg, payload.from.id) ? ['', '<b>Админ:</b> (пока нет админ-команд)'] : []),
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
      for (const s of services) await s.afterPayment?.(ctx, { user: payload.from, payment });
    },
    (m) => !!m.successful_payment,
  );

  // ----- Ядро API -----
  router.get('/api/me', ({ db, user }) => ({
    user: db.prepare('SELECT id, username, first_name, balance FROM users WHERE id = ?').get(user.id),
    isAdmin: isAdmin(cfg, user.id),
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
      await new Promise((r) => server.listen(cfg.port, r));
      console.log(`[http] API и Mini App: http://localhost:${cfg.port}`);
      if (cfg.botToken) {
        bot.me = await bot.call('getMe').catch((e) => { console.error('[bot] getMe', e.message); return null; });
        await bot.publishCommands().catch((e) => console.error('[bot] setMyCommands', e.message));
        if (cfg.publicUrl.startsWith('https://') && bot.me) {
          await bot
            .call('setChatMenuButton', { menu_button: { type: 'web_app', text: 'WhiteGram', web_app: { url: cfg.publicUrl } } })
            .catch((e) => console.error('[bot] setChatMenuButton', e.message));
        }
        bot.startPolling();
      } else {
        console.warn('[bot] BOT_TOKEN не задан — бот не запущен, работает только HTTP');
      }
    },
  };
}