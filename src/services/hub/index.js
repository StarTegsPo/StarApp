/**
 * «Хаб» — всё в одном окне, не выходя из Telegram:
 *  - чат с любым ИИ (Claude, ChatGPT, Gemini, DeepSeek, Grok, Mistral, OpenRouter) и сравнение ответов;
 *  - соцсети (TikTok, YouTube, Instagram…): быстрый запуск во встроенном браузере Telegram и превью ссылок;
 *  - контент-студия: готовые сценарии для TikTok/Reels/Shorts, описания, хэштеги, ответы на комментарии;
 *  - автоматизации: ИИ-задачи и напоминания по расписанию, результат приходит сообщением от бота.
 */
import { PROVIDERS, complete, AiError } from './providers.js';

const bad = (msg, status = 400) => Object.assign(new Error(msg), { status });

export const LIMITS = { text: 8000, history: 20, chats: 100, tasks: 20, taskTitle: 80, taskPrompt: 4000, compare: 4 };

/** Соцсети и сервисы: открываются во встроенном браузере Telegram (Telegram.WebApp.openLink). */
export const SOCIAL = [
  { id: 'tiktok', icon: '🎵', title: 'TikTok', url: 'https://www.tiktok.com/', actions: [
    { title: 'Загрузить видео', url: 'https://www.tiktok.com/tiktokstudio/upload' },
    { title: 'Статистика', url: 'https://www.tiktok.com/tiktokstudio/analytics' },
    { title: 'Сообщения', url: 'https://www.tiktok.com/messages' },
  ] },
  { id: 'youtube', icon: '▶️', title: 'YouTube', url: 'https://www.youtube.com/', actions: [
    { title: 'YouTube Studio', url: 'https://studio.youtube.com/' },
    { title: 'Shorts', url: 'https://www.youtube.com/shorts' },
  ] },
  { id: 'instagram', icon: '📸', title: 'Instagram', url: 'https://www.instagram.com/', actions: [
    { title: 'Директ', url: 'https://www.instagram.com/direct/inbox/' },
    { title: 'Reels', url: 'https://www.instagram.com/reels/' },
  ] },
  { id: 'x', icon: '✖️', title: 'X (Twitter)', url: 'https://x.com/', actions: [{ title: 'Новый пост', url: 'https://x.com/compose/post' }] },
  { id: 'vk', icon: '🔵', title: 'ВКонтакте', url: 'https://vk.com/', actions: [{ title: 'Клипы', url: 'https://vk.com/clips' }, { title: 'Сообщения', url: 'https://vk.com/im' }] },
  { id: 'threads', icon: '🧵', title: 'Threads', url: 'https://www.threads.net/', actions: [] },
  { id: 'twitch', icon: '🟣', title: 'Twitch', url: 'https://www.twitch.tv/', actions: [{ title: 'Панель автора', url: 'https://dashboard.twitch.tv/' }] },
  { id: 'pinterest', icon: '📌', title: 'Pinterest', url: 'https://www.pinterest.com/', actions: [] },
];

/** ИИ-сервисы в браузере — на случай, если нужен сайт сервиса (генерация картинок, видео и т.п.). */
export const AI_WEB = [
  { icon: '🧡', title: 'Claude', url: 'https://claude.ai/' },
  { icon: '🟢', title: 'ChatGPT', url: 'https://chatgpt.com/' },
  { icon: '🔷', title: 'Gemini', url: 'https://gemini.google.com/' },
  { icon: '🐋', title: 'DeepSeek', url: 'https://chat.deepseek.com/' },
  { icon: '🔎', title: 'Perplexity', url: 'https://www.perplexity.ai/' },
  { icon: '⚫', title: 'Grok', url: 'https://grok.com/' },
  { icon: '🎨', title: 'Midjourney', url: 'https://www.midjourney.com/' },
  { icon: '🎬', title: 'CapCut', url: 'https://www.capcut.com/' },
  { icon: '🎙', title: 'ElevenLabs', url: 'https://elevenlabs.io/' },
  { icon: '🖌', title: 'Canva', url: 'https://www.canva.com/' },
];

/** Пресеты контент-студии: системная инструкция + подсказка для поля ввода. */
export const PRESETS = [
  { id: 'ideas', icon: '💡', title: 'Идеи для TikTok', hint: 'Тема или ниша: например, «кофейня у дома»',
    system: 'Ты продюсер коротких вертикальных видео (TikTok, Reels, Shorts). Дай 10 идей роликов по теме пользователя. Для каждой: цепляющий хук в первые 2 секунды, суть ролика в 1–2 предложениях и формат (тренд, туториал, POV, до/после и т.п.). Пиши по-русски, нумерованным списком, без вступлений.' },
  { id: 'script', icon: '🎬', title: 'Сценарий ролика', hint: 'О чём ролик и сколько секунд',
    system: 'Ты сценарист коротких видео. Напиши сценарий вертикального ролика по раскадровке: время (0–3 с, 3–8 с…), что в кадре, текст на экране, закадровый текст. В конце — призыв к действию и идея для музыки. По-русски, кратко и по делу.' },
  { id: 'caption', icon: '✍️', title: 'Описание + хэштеги', hint: 'О чём видео',
    system: 'Ты SMM-специалист. Напиши 3 варианта описания к короткому видео (до 150 символов каждое, с эмодзи), затем блок из 10–15 релевантных хэштегов: смесь популярных и нишевых. По-русски.' },
  { id: 'reply', icon: '💬', title: 'Ответ на комментарий', hint: 'Вставьте комментарий подписчика',
    system: 'Ты ведёшь аккаунт автора в соцсетях. Предложи 3 варианта ответа на комментарий: дружелюбный, с юмором и деловой. Коротко, живым языком, на языке комментария. Если комментарий токсичный — ответь спокойно и без агрессии.' },
  { id: 'post', icon: '📣', title: 'Пост для Telegram', hint: 'Тема поста или черновик',
    system: 'Ты редактор Telegram-канала. Напиши пост: цепкий первый абзац, польза по делу, короткие абзацы, умеренно эмодзи, призыв к действию в конце. Длина до 1200 символов. По-русски.' },
  { id: 'plan', icon: '🗓', title: 'Контент-план', hint: 'Ниша, соцсети и сколько постов в неделю',
    system: 'Ты контент-стратег. Составь контент-план на 7 дней для указанных соцсетей: день, площадка, формат, тема, хук. Таблицей в виде списка. В конце — 3 совета по росту охватов. По-русски.' },
  { id: 'translate', icon: '🌍', title: 'Перевод', hint: 'Текст и на какой язык',
    system: 'Ты профессиональный переводчик. Переведи текст на указанный язык (если не указан — на английский, а английский — на русский), сохрани стиль, эмодзи и хэштеги. Выведи только перевод.' },
];

const DEFAULT_SYSTEM = 'Ты полезный ассистент внутри приложения StarApp в Telegram. Отвечай по делу, на языке пользователя. Используй простое форматирование без Markdown-таблиц.';

// ---------- помощники ----------

function str(value, name, max, required = true) {
  if (value == null) value = '';
  if (typeof value !== 'string') throw bad(`${name}: ожидается текст`);
  value = value.trim();
  if (required && !value) throw bad(`${name}: заполните поле`);
  if (value.length > max) throw bad(`${name}: не длиннее ${max} символов`);
  return value;
}

const idOf = (v) => {
  const id = Number(v);
  if (!Number.isSafeInteger(id) || id < 1) throw bad('Некорректный id');
  return id;
};

const fetchOf = (ctx) => ctx.hubFetch || fetch;
const enabledProviders = (config) => PROVIDERS.filter((p) => config.ai?.[p.id]);

function pickProvider(config, id) {
  const enabled = enabledProviders(config);
  if (!enabled.length) throw bad('Ни один ИИ не подключён. Администратору: добавьте API-ключ в .env (например, ANTHROPIC_API_KEY)', 503);
  if (id == null || id === '') return enabled[0].id;
  if (!enabled.some((p) => p.id === id)) throw bad('Этот ИИ не подключён на сервере');
  return id;
}

const today = () => new Date().toISOString().slice(0, 10);

export function usage(db, config, userId) {
  const row = db.prepare('SELECT n FROM hub_usage WHERE user_id = ? AND day = ?').get(userId, today());
  const limit = config.adminIds.includes(Number(userId)) ? null : config.hubDailyLimit;
  return { used: row?.n ?? 0, limit };
}

/** Списывает n запросов из дневного лимита или бросает 429. */
function spend(db, config, userId, n = 1) {
  const { used, limit } = usage(db, config, userId);
  if (limit != null && used + n > limit) throw bad(`Дневной лимит ИИ-запросов исчерпан (${limit}). Возвращайтесь завтра!`, 429);
  db.prepare(
    `INSERT INTO hub_usage (user_id, day, n) VALUES (?, ?, ?)
     ON CONFLICT(user_id, day) DO UPDATE SET n = n + excluded.n`,
  ).run(userId, today(), n);
}

const ask = (ctx, provider, system, messages) =>
  complete({ provider, cfg: ctx.config.ai[provider], system, messages, fetchImpl: fetchOf(ctx) });

const presetSystem = (id) => {
  if (id == null || id === '') return DEFAULT_SYSTEM;
  const p = PRESETS.find((x) => x.id === id);
  if (!p) throw bad('Неизвестный пресет');
  return p.system;
};

// ---------- превью ссылок (TikTok / YouTube) ----------

/** Только фиксированные oEmbed-эндпоинты — сервер не ходит по произвольным адресам. */
export function oembedUrl(raw) {
  let u;
  try { u = new URL(String(raw || '').trim()); } catch { return null; }
  if (u.protocol !== 'https:' && u.protocol !== 'http:') return null;
  const host = u.hostname.replace(/^www\.|^m\./, '');
  if (['tiktok.com', 'vm.tiktok.com', 'vt.tiktok.com'].includes(host)) {
    return { platform: 'tiktok', url: `https://www.tiktok.com/oembed?url=${encodeURIComponent(u.href)}` };
  }
  if (['youtube.com', 'youtu.be', 'music.youtube.com'].includes(host)) {
    return { platform: 'youtube', url: `https://www.youtube.com/oembed?format=json&url=${encodeURIComponent(u.href)}` };
  }
  return null;
}

// ---------- автоматизации ----------

/** Локальная дата/время пользователя по смещению часового пояса (минуты от UTC, как -Date.getTimezoneOffset()). */
export function localNow(tzOffset, now = Date.now()) {
  const d = new Date(now + tzOffset * 60_000);
  return { date: d.toISOString().slice(0, 10), minutes: d.getUTCHours() * 60 + d.getUTCMinutes(), weekday: (d.getUTCDay() + 6) % 7 };
}

/** Пора ли запускать задачу: нужный день недели, время наступило, сегодня ещё не запускалась. */
export function isDue(task, now = Date.now()) {
  if (!task.enabled) return false;
  const l = localNow(task.tz_offset, now);
  return (task.days & (1 << l.weekday)) !== 0 && l.minutes >= task.hour * 60 + task.minute && task.last_run_date !== l.date;
}

function taskFields(body, config, partial = null) {
  const t = partial ? { ...partial } : {};
  if (!partial || body.title !== undefined) t.title = str(body.title, 'Название', LIMITS.taskTitle);
  if (!partial || body.kind !== undefined) {
    if (!['ai', 'remind'].includes(body.kind)) throw bad('Тип задачи: ai или remind');
    t.kind = body.kind;
  }
  if (!partial || body.prompt !== undefined) t.prompt = str(body.prompt, t.kind === 'remind' ? 'Текст напоминания' : 'Задание для ИИ', LIMITS.taskPrompt);
  if (!partial || body.provider !== undefined || body.kind !== undefined) {
    t.provider = t.kind === 'ai' ? pickProvider(config, body.provider ?? t.provider) : null;
  }
  if (!partial || body.time !== undefined) {
    const m = /^([01]?\d|2[0-3]):([0-5]\d)$/.exec(String(body.time ?? ''));
    if (!m) throw bad('Время в формате ЧЧ:ММ');
    t.hour = Number(m[1]); t.minute = Number(m[2]);
  }
  if (!partial || body.days !== undefined) {
    const days = body.days ?? 127;
    if (!Number.isInteger(days) || days < 1 || days > 127) throw bad('Выберите хотя бы один день недели');
    t.days = days;
  }
  if (!partial || body.tzOffset !== undefined) {
    const tz = body.tzOffset ?? 180;
    if (!Number.isInteger(tz) || tz < -720 || tz > 840) throw bad('Некорректный часовой пояс');
    t.tz_offset = tz;
  }
  if (body.enabled !== undefined) t.enabled = body.enabled ? 1 : 0;
  return t;
}

const TASK_COLS = 'id, user_id, title, kind, prompt, provider, hour, minute, days, tz_offset, enabled, last_run_date, last_run_at, last_result, last_error, created_at';
const publicTask = ({ user_id, ...t }) => ({ ...t, time: `${String(t.hour).padStart(2, '0')}:${String(t.minute).padStart(2, '0')}` });

const tgEsc = (s) => String(s).replace(/[&<>]/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;' })[c]);

/** Отправляет длинный текст частями (лимит Telegram — 4096 символов). */
async function sendLong(bot, chatId, header, text) {
  const body = tgEsc(text);
  const first = 3800 - header.length;
  const parts = [body.slice(0, first)];
  for (let i = first; i < body.length; i += 3800) parts.push(body.slice(i, i + 3800));
  for (let i = 0; i < parts.length; i++) await bot.sendMessage(chatId, i === 0 ? `${header}\n\n${parts[i]}` : parts[i]);
}

/** Выполняет задачу: ИИ-запрос или напоминание, результат — сообщением в Telegram. */
export async function runTask(ctx, task, { manual = false } = {}) {
  const { db, bot, config } = ctx;
  const l = localNow(task.tz_offset);
  // помечаем запуск заранее, чтобы планировщик не запустил задачу повторно, пока ждём ответ ИИ
  if (!manual) db.prepare('UPDATE hub_tasks SET last_run_date = ? WHERE id = ?').run(l.date, task.id);
  let result = null, error = null;
  try {
    if (task.kind === 'ai') {
      spend(db, config, task.user_id);
      result = await ask(ctx, pickProvider(config, task.provider), DEFAULT_SYSTEM, [{ role: 'user', content: task.prompt }]);
      const p = PROVIDERS.find((x) => x.id === task.provider);
      await sendLong(bot, task.user_id, `🤖 <b>${tgEsc(task.title)}</b> · ${p?.icon ?? ''} ${tgEsc(p?.title ?? '')}`, result);
    } else {
      result = task.prompt;
      await sendLong(bot, task.user_id, `⏰ <b>${tgEsc(task.title)}</b>`, result);
    }
  } catch (e) {
    error = e.message;
    if (!manual) await bot.sendMessage(task.user_id, `⚠️ Автоматизация «${tgEsc(task.title)}» не выполнилась: ${tgEsc(e.message)}`).catch(() => {});
  }
  db.prepare('UPDATE hub_tasks SET last_run_at = unixepoch(), last_result = ?, last_error = ? WHERE id = ?')
    .run(result ? result.slice(0, 4000) : null, error, task.id);
  return { result, error };
}

// ---------- сервис ----------

export default {
  id: 'hub',
  title: 'Хаб',
  icon: '🚀',
  order: -0.5,

  migrations: [
    `CREATE TABLE IF NOT EXISTS hub_chats (
       id INTEGER PRIMARY KEY AUTOINCREMENT,
       user_id INTEGER NOT NULL REFERENCES users(id),
       title TEXT NOT NULL,
       provider TEXT NOT NULL,
       preset TEXT,
       created_at INTEGER NOT NULL DEFAULT (unixepoch()),
       updated_at INTEGER NOT NULL DEFAULT (unixepoch())
     )`,
    'CREATE INDEX IF NOT EXISTS hub_chats_user ON hub_chats(user_id, updated_at)',
    `CREATE TABLE IF NOT EXISTS hub_messages (
       id INTEGER PRIMARY KEY AUTOINCREMENT,
       chat_id INTEGER NOT NULL REFERENCES hub_chats(id) ON DELETE CASCADE,
       role TEXT NOT NULL,                 -- user | assistant
       content TEXT NOT NULL,
       provider TEXT,
       created_at INTEGER NOT NULL DEFAULT (unixepoch())
     )`,
    'CREATE INDEX IF NOT EXISTS hub_messages_chat ON hub_messages(chat_id, id)',
    `CREATE TABLE IF NOT EXISTS hub_usage (
       user_id INTEGER NOT NULL,
       day TEXT NOT NULL,
       n INTEGER NOT NULL DEFAULT 0,
       PRIMARY KEY (user_id, day)
     )`,
    `CREATE TABLE IF NOT EXISTS hub_tasks (
       id INTEGER PRIMARY KEY AUTOINCREMENT,
       user_id INTEGER NOT NULL REFERENCES users(id),
       title TEXT NOT NULL,
       kind TEXT NOT NULL,                 -- ai | remind
       prompt TEXT NOT NULL,
       provider TEXT,
       hour INTEGER NOT NULL,
       minute INTEGER NOT NULL,
       days INTEGER NOT NULL DEFAULT 127,  -- битовая маска: пн=1, вт=2 … вс=64
       tz_offset INTEGER NOT NULL DEFAULT 180,
       enabled INTEGER NOT NULL DEFAULT 1,
       last_run_date TEXT,
       last_run_at INTEGER,
       last_result TEXT,
       last_error TEXT,
       created_at INTEGER NOT NULL DEFAULT (unixepoch())
     )`,
    'CREATE INDEX IF NOT EXISTS hub_tasks_user ON hub_tasks(user_id)',
  ],

  setupBot(ctx) {
    const { bot, db, config } = ctx;
    bot.command('ai', async ({ payload, args }) => {
      const text = (args || '').trim();
      if (!text) {
        return bot.sendMessage(payload.chat.id, 'Напишите вопрос после команды, например:\n<code>/ai придумай 5 идей для TikTok про кофе</code>\n\nВсе ИИ, соцсети и автоматизации — во вкладке 🚀 Хаб в приложении.');
      }
      try {
        const provider = pickProvider(config);
        spend(db, config, payload.from.id);
        await bot.call('sendChatAction', { chat_id: payload.chat.id, action: 'typing' }).catch(() => {});
        const reply = await ask(ctx, provider, DEFAULT_SYSTEM, [{ role: 'user', content: text.slice(0, LIMITS.text) }]);
        const p = PROVIDERS.find((x) => x.id === provider);
        await sendLong(bot, payload.chat.id, `${p.icon} <b>${p.title}</b>`, reply);
      } catch (e) {
        await bot.sendMessage(payload.chat.id, `⚠️ ${tgEsc(e.message)}`);
      }
    }, 'Спросить ИИ');
  },

  setupApi(router) {
    router.get('/api/hub', ({ db, user, config }) => ({
      providers: PROVIDERS.map(({ id, title, icon, vendor }) => ({ id, title, icon, vendor, enabled: !!config.ai?.[id], model: config.ai?.[id]?.model ?? null })),
      social: SOCIAL,
      aiWeb: AI_WEB,
      presets: PRESETS.map(({ id, icon, title, hint }) => ({ id, icon, title, hint })),
      usage: usage(db, config, user.id),
      counts: {
        chats: db.prepare('SELECT COUNT(*) AS n FROM hub_chats WHERE user_id = ?').get(user.id).n,
        tasks: db.prepare('SELECT COUNT(*) AS n FROM hub_tasks WHERE user_id = ?').get(user.id).n,
      },
      limits: LIMITS,
    }));

    // ----- чаты -----
    router.get('/api/hub/chats', ({ db, user }) => ({
      chats: db.prepare(
        `SELECT c.id, c.title, c.provider, c.preset, c.updated_at,
                (SELECT content FROM hub_messages WHERE chat_id = c.id ORDER BY id DESC LIMIT 1) AS last
         FROM hub_chats c WHERE c.user_id = ? ORDER BY c.updated_at DESC, c.id DESC LIMIT ?`,
      ).all(user.id, LIMITS.chats).map((c) => ({ ...c, last: c.last ? c.last.slice(0, 140) : '' })),
    }));

    const ownChat = (db, userId, id) => {
      const chat = db.prepare('SELECT id, title, provider, preset, updated_at FROM hub_chats WHERE id = ? AND user_id = ?').get(idOf(id), userId);
      if (!chat) throw bad('Чат не найден', 404);
      return chat;
    };

    router.get('/api/hub/chats/:id', ({ db, user, params }) => {
      const chat = ownChat(db, user.id, params.id);
      return { ...chat, messages: db.prepare('SELECT id, role, content, provider, created_at FROM hub_messages WHERE chat_id = ? ORDER BY id').all(chat.id) };
    });

    router.delete('/api/hub/chats/:id', ({ db, user, params }) => {
      const chat = ownChat(db, user.id, params.id);
      db.prepare('DELETE FROM hub_messages WHERE chat_id = ?').run(chat.id);
      db.prepare('DELETE FROM hub_chats WHERE id = ?').run(chat.id);
      return { ok: true };
    });

    /** Сообщение в чат: { text, provider?, chatId?, preset? } → { chatId, reply, provider } */
    router.post('/api/hub/ask', async (ctx) => {
      const { db, user, body, config } = ctx;
      const text = str(body.text, 'Сообщение', LIMITS.text);
      const provider = pickProvider(config, body.provider);
      let chat = body.chatId ? ownChat(db, user.id, body.chatId) : null;
      const system = presetSystem(chat ? chat.preset : body.preset);
      const history = chat
        ? db.prepare('SELECT role, content FROM (SELECT id, role, content FROM hub_messages WHERE chat_id = ? ORDER BY id DESC LIMIT ?) ORDER BY id').all(chat.id, LIMITS.history)
        : [];
      spend(db, config, user.id);
      const reply = await ask(ctx, provider, system, [...history.map(({ role, content }) => ({ role, content })), { role: 'user', content: text }]);

      if (!chat) {
        const id = Number(db.prepare('INSERT INTO hub_chats (user_id, title, provider, preset) VALUES (?, ?, ?, ?)')
          .run(user.id, text.replace(/\s+/g, ' ').slice(0, 60), provider, body.preset || null).lastInsertRowid);
        chat = { id };
      }
      const add = db.prepare('INSERT INTO hub_messages (chat_id, role, content, provider) VALUES (?, ?, ?, ?)');
      add.run(chat.id, 'user', text, null);
      add.run(chat.id, 'assistant', reply, provider);
      db.prepare('UPDATE hub_chats SET updated_at = unixepoch(), provider = ? WHERE id = ?').run(provider, chat.id);
      return { chatId: chat.id, reply, provider, usage: usage(db, config, user.id) };
    });

    /** Один вопрос — сразу нескольким ИИ: { text, providers?: [] } → { answers: [{ provider, reply | error }] } */
    router.post('/api/hub/compare', async (ctx) => {
      const { db, user, body, config } = ctx;
      const text = str(body.text, 'Вопрос', LIMITS.text);
      let ids = Array.isArray(body.providers) && body.providers.length ? [...new Set(body.providers)] : enabledProviders(config).map((p) => p.id);
      ids = ids.slice(0, LIMITS.compare).map((id) => pickProvider(config, id));
      const system = presetSystem(body.preset);
      spend(db, config, user.id, ids.length);
      const answers = await Promise.all(ids.map(async (provider) => {
        try {
          return { provider, reply: await ask(ctx, provider, system, [{ role: 'user', content: text }]) };
        } catch (e) {
          return { provider, error: e instanceof AiError || e.status ? e.message : 'Ошибка' };
        }
      }));
      return { answers, usage: usage(db, config, user.id) };
    });

    // ----- соцсети: превью ссылки -----
    router.get('/api/hub/preview', async (ctx) => {
      const target = oembedUrl(ctx.query.get('url'));
      if (!target) throw bad('Поддерживаются ссылки на TikTok и YouTube');
      let data;
      try {
        const res = await fetchOf(ctx)(target.url, { signal: AbortSignal.timeout(10_000) });
        if (!res.ok) throw new Error();
        data = await res.json();
      } catch {
        throw bad('Не удалось получить превью — проверьте ссылку', 502);
      }
      const s = (v, n = 300) => (typeof v === 'string' ? v.slice(0, n) : null);
      return {
        platform: target.platform,
        title: s(data.title),
        author: s(data.author_name, 120),
        authorUrl: s(data.author_url),
        thumbnail: s(data.thumbnail_url, 1000),
        url: ctx.query.get('url'),
      };
    });

    // ----- автоматизации -----
    const ownTask = (db, userId, id) => {
      const task = db.prepare(`SELECT ${TASK_COLS} FROM hub_tasks WHERE id = ? AND user_id = ?`).get(idOf(id), userId);
      if (!task) throw bad('Автоматизация не найдена', 404);
      return task;
    };

    router.get('/api/hub/tasks', ({ db, user }) => ({
      tasks: db.prepare(`SELECT ${TASK_COLS} FROM hub_tasks WHERE user_id = ? ORDER BY hour, minute, id`).all(user.id).map(publicTask),
      limit: LIMITS.tasks,
    }));

    router.post('/api/hub/tasks', ({ db, user, body, config }) => {
      const t = taskFields(body, config);
      const { n } = db.prepare('SELECT COUNT(*) AS n FROM hub_tasks WHERE user_id = ?').get(user.id);
      if (n >= LIMITS.tasks) throw bad(`Можно создать не больше ${LIMITS.tasks} автоматизаций`, 409);
      // если время на сегодня уже прошло — первый запуск завтра, а не сразу
      const l = localNow(t.tz_offset);
      const lastRun = l.minutes >= t.hour * 60 + t.minute ? l.date : null;
      const id = Number(db.prepare(
        `INSERT INTO hub_tasks (user_id, title, kind, prompt, provider, hour, minute, days, tz_offset, enabled, last_run_date)
         VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)`,
      ).run(user.id, t.title, t.kind, t.prompt, t.provider, t.hour, t.minute, t.days, t.tz_offset, t.enabled ?? 1, lastRun).lastInsertRowid);
      return publicTask(ownTask(db, user.id, id));
    });

    router.post('/api/hub/tasks/:id', ({ db, user, params, body, config }) => {
      const cur = ownTask(db, user.id, params.id);
      const t = taskFields(body, config, cur);
      db.prepare(
        `UPDATE hub_tasks SET title = ?, kind = ?, prompt = ?, provider = ?, hour = ?, minute = ?, days = ?, tz_offset = ?, enabled = ? WHERE id = ?`,
      ).run(t.title, t.kind, t.prompt, t.provider, t.hour, t.minute, t.days, t.tz_offset, t.enabled, cur.id);
      return publicTask(ownTask(db, user.id, cur.id));
    });

    router.delete('/api/hub/tasks/:id', ({ db, user, params }) => {
      const task = ownTask(db, user.id, params.id);
      db.prepare('DELETE FROM hub_tasks WHERE id = ?').run(task.id);
      return { ok: true };
    });

    router.post('/api/hub/tasks/:id/run', async (ctx) => {
      const task = ownTask(ctx.db, ctx.user.id, ctx.params.id);
      const r = await runTask(ctx, task, { manual: true });
      if (r.error) throw bad(r.error, 502);
      return { ok: true, result: r.result };
    });
  },

  /** Планировщик: раз в 30 секунд ищет задачи, которым пора выполниться. */
  start(ctx) {
    const running = new Set();
    const tick = async () => {
      const tasks = ctx.db.prepare(`SELECT ${TASK_COLS} FROM hub_tasks WHERE enabled = 1`).all();
      for (const task of tasks) {
        if (!isDue(task) || running.has(task.id)) continue;
        running.add(task.id);
        runTask(ctx, task).catch((e) => console.error('[hub] задача', task.id, e.message)).finally(() => running.delete(task.id));
      }
    };
    ctx.hubTick = tick; // для тестов
    if (ctx.config.botToken) {
      const timer = setInterval(() => tick().catch((e) => console.error('[hub]', e.message)), 30_000);
      timer.unref?.();
    }
  },
};
