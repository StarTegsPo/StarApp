import { readConfig } from './config.js';

const config = readConfig();

export class TelegramBot {
  constructor(token = config.botToken) {
    this.token = token;
    this.handlers = [];
    this.commands = [];
    this.me = null;
    this.polling = false;
  }

  apiUrl(method) {
    return `https://api.telegram.org/bot${this.token}/${method}`;
  }

  async call(method, params = {}) {
    const res = await fetch(this.apiUrl(method), {
      method: 'POST',
      headers: { 'content-type': 'application/json' },
      body: JSON.stringify(params),
    });
    const data = await res.json();
    if (!data.ok) throw new Error(data.description || `Telegram API error: ${method}`);
    return data.result;
  }

  use(fn) {
    this.handlers.push({ type: 'middleware', fn });
  }

  command(name, fn, description) {
    this.commands.push({ command: name, description: description || name });
    this.handlers.push({
      type: 'command',
      name,
      fn: async (c) => {
        c.args = c.payload.text?.slice(name.length + 1).trim() || '';
        await fn(c);
      },
    });
  }

  on(type, fn, filter) {
    this.handlers.push({ type: 'update', updateType: type, fn, filter });
  }

  async publishCommands() {
    if (!this.commands.length) return;
    await this.call('setMyCommands', { commands: this.commands });
  }

  async startPolling() {
    this.polling = true;
    let offset = 0;
    while (this.polling) {
      try {
        const updates = await this.call('getUpdates', { offset, timeout: 30, allowed_updates: ['message', 'callback_query', 'pre_checkout_query', 'my_chat_member'] });
        for (const u of updates) {
          offset = u.update_id + 1;
          await this.processUpdate(u);
        }
      } catch (e) {
        console.error('[bot] polling error:', e.message);
        await new Promise((r) => setTimeout(r, 3000));
      }
    }
  }

  stopPolling() {
    this.polling = false;
  }

  async processUpdate(u) {
    const ctx = { bot: this, type: 'update', payload: u };
    for (const h of this.handlers) {
      if (h.type === 'middleware') {
        await h.fn(ctx);
        continue;
      }
      if (h.type === 'command') {
        const msg = u.message;
        if (msg?.text?.startsWith(`/${h.name}`) && (!h.filter || h.filter(msg))) {
          ctx.type = 'command';
          ctx.payload = msg;
          await h.fn(ctx);
          return;
        }
        continue;
      }
      if (h.type === 'update' && u[h.updateType]) {
        if (!h.filter || h.filter(u[h.updateType])) {
          ctx.type = h.updateType;
          ctx.payload = u[h.updateType];
          await h.fn(ctx);
        }
      }
    }
  }

  async sendMessage(chatId, text, opts = {}) {
    return this.call('sendMessage', { chat_id: chatId, text, parse_mode: 'HTML', ...opts });
  }
}