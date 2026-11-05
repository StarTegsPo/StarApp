import { createApp } from '../src/app.js';
import { readConfig } from '../src/config.js';
import { openDb } from '../src/db.js';
import { services } from '../src/services/index.js';
import { TelegramBot } from '../src/telegram.js';
import { signInitData } from '../src/auth.js';

export const TOKEN = '123456:TEST';

/** Фейковый Bot API: записывает вызовы и возвращает заготовленные ответы. */
export function fakeTelegram(responses = {}) {
  const calls = [];
  let msgId = 100;
  const fetchImpl = async (url, { body }) => {
    const method = url.split('/').pop();
    const params = JSON.parse(body);
    calls.push({ method, params });
    const result =
      method in responses ? responses[method](params)
      : method === 'sendMessage' ? { message_id: ++msgId, chat: { id: params.chat_id } }
      : method === 'createInvoiceLink' ? `https://t.me/$invoice-${params.payload}`
      : true;
    return { json: async () => ({ ok: true, result }) };
  };
  return { calls, bot: new TelegramBot(TOKEN, { fetchImpl }) };
}

export async function startTestApp(env = {}, responses) {
  const config = readConfig({ BOT_TOKEN: TOKEN, ADMIN_IDS: '1', PORT: '0', ...env });
  const tg = fakeTelegram(responses);
  const db = openDb(':memory:', services);
  const app = createApp(config, { bot: tg.bot, db });
  for (const s of services) await s.start?.(app.ctx);
  await new Promise((r) => app.server.listen(0, r));
  const base = `http://127.0.0.1:${app.server.address().port}`;

  const authFor = (user) =>
    'tma ' + signInitData({ auth_date: String(Math.floor(Date.now() / 1000)), user: JSON.stringify(user), query_id: 'q' }, TOKEN);

  const request = async (path, { user = { id: 2, first_name: 'Иван' }, method = 'GET', body, headers = {} } = {}) => {
    const res = await fetch(base + path, {
      method,
      headers: { 'content-type': 'application/json', ...(user ? { authorization: authFor(user) } : {}), ...headers },
      body: body ? JSON.stringify(body) : undefined,
    });
    return { status: res.status, data: await res.json() };
  };

  return { app, db, tg, request, close: () => new Promise((r) => app.server.close(r)) };
}
