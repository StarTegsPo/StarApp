/**
 * Минимальный клиент Telegram Bot API без зависимостей:
 * вызов методов, long polling и простой роутер обработчиков.
 */
export class TelegramBot {
  constructor(token, { apiBase = 'https://api.telegram.org', fetchImpl = fetch } = {}) {
    this.token = token;
    this.apiBase = apiBase;
    this.fetch = fetchImpl;
    this.commands = new Map(); // команда -> { handler, description }
    this.handlers = []; // { type, filter, handler }
    this.middlewares = []; // выполняются для каждого апдейта до маршрутизации
    this.offset = 0;
    this.running = false;
    this.me = null;
  }

  async call(method, params = {}) {
    const res = await this.fetch(`${this.apiBase}/bot${this.token}/${method}`, {
      method: 'POST',
      headers: { 'content-type': 'application/json' },
      body: JSON.stringify(params),
    });
    const data = await res.json();
    if (!data.ok) {
      const err = new Error(`Telegram ${method}: ${data.description}`);
      err.code = data.error_code;
      throw err;
    }
    return data.result;
  }

  sendMessage(chatId, text, extra = {}) {
    return this.call('sendMessage', { chat_id: chatId, text, parse_mode: 'HTML', ...extra });
  }

  /** Регистрирует /команду. description попадает в меню команд бота. */
  command(name, handler, description) {
    this.commands.set(name, { handler, description });
  }

  use(fn) {
    this.middlewares.push(fn);
  }

  /** Обработчик любого типа апдейта: 'message', 'callback_query', 'channel_post', ... */
  on(type, handler, filter = () => true) {
    this.handlers.push({ type, filter, handler });
  }

  get allowedUpdates() {
    const types = new Set(['message', ...this.handlers.map((h) => h.type)]);
    return [...types];
  }

  async handleUpdate(update) {
    const type = Object.keys(update).find((k) => k !== 'update_id');
    const payload = update[type];
    const ctx = { bot: this, update, type, payload };
    for (const mw of this.middlewares) await mw(ctx);

    if (type === 'message' && payload.text?.startsWith('/')) {
      const [raw, ...rest] = payload.text.split(' ');
      const [name, mention] = raw.slice(1).split('@');
      if (!mention || !this.me || mention.toLowerCase() === this.me.username?.toLowerCase()) {
        const cmd = this.commands.get(name.toLowerCase());
        if (cmd) {
          ctx.args = rest.join(' ').trim();
          return cmd.handler(ctx);
        }
      }
    }
    for (const h of this.handlers) {
      if (h.type === type && h.filter(payload, ctx)) {
        const r = await h.handler(ctx);
        if (r !== false) return; // false = передать следующему обработчику
      }
    }
  }

  async publishCommands() {
    const commands = [...this.commands]
      .filter(([, c]) => c.description)
      .map(([command, c]) => ({ command, description: c.description }));
    await this.call('setMyCommands', { commands });
  }

  async startPolling() {
    this.running = true;
    // Нет интернета при старте (частое дело на телефоне) — ждём и пробуем снова, а не падаем
    while (this.running && !this.me) {
      try {
        this.me = await this.call('getMe');
      } catch (e) {
        if (e.code === 401) console.error('[bot] Telegram отклонил токен (401 Unauthorized) — проверьте BOT_TOKEN в .env');
        else console.error('[bot] нет связи с Telegram:', e.message, '— повтор через 5 с');
        await new Promise((r) => setTimeout(r, 5000));
      }
    }
    console.log(`[bot] @${this.me.username} запущен (long polling)`);
    while (this.running) {
      try {
        const updates = await this.call('getUpdates', {
          offset: this.offset,
          timeout: 30,
          allowed_updates: this.allowedUpdates,
        });
        for (const u of updates) {
          this.offset = u.update_id + 1;
          this.handleUpdate(u).catch((e) => console.error('[bot] ошибка обработки', e));
        }
      } catch (e) {
        console.error('[bot] polling:', e.message);
        await new Promise((r) => setTimeout(r, 3000));
      }
    }
  }

  stop() {
    this.running = false;
  }
}
