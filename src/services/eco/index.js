/**
 * «StarTegs для iPhone»: экосистема ботов StarTegs, оформление Mini App (палитры и сезонные эффекты),
 * шаблоны ответов и личные заметки. На iPhone нельзя поставить Android-мод клиента, поэтому
 * похожие возможности собраны здесь.
 */

const bad = (msg, status = 400) => Object.assign(new Error(msg), { status });

/** Сервисы экосистемы по умолчанию. Переопределяется переменной окружения ECO_BOTS (JSON-массив). */
export const DEFAULT_ECO_BOTS = [
  { id: 'startegs', icon: '⭐', title: 'StarTegs', bot: 'StarTegsEcosistem_bot', desc: 'Удалённые сообщения, архив, Premium' },
  { id: 'market', icon: '🛍', title: 'Маркет', bot: 'StarTegsShop_bot', desc: 'Цифровые товары и услуги за Telegram Stars' },
  { id: 'ai', icon: '🤖', title: 'ИИ-ассистент', bot: 'StarTegsAi_bot', desc: 'Ответы, тексты и идеи за секунды' },
  { id: 'analytics', icon: '📊', title: 'Бизнес-аналитика', tab: 'analytics', desc: 'Каналы, боты и продажи — прямо в этом приложении' },
];

export const ANDROID_URL = 'https://github.com/StarTegsPo/StarApp/releases/tag/client-latest';

export const PALETTES = ['violet', 'neon', 'gold', 'emerald', 'sunset', 'custom'];
export const EFFECTS = ['off', 'auto', 'snow', 'leaves', 'petals', 'fireflies'];
export const HUES = 12;
export const DEFAULT_SETTINGS = { palette: 'violet', hue: 0, effect: 'off' };

export const LIMITS = { templates: 50, templateTitle: 100, templateText: 2000, notes: 200, noteTitle: 120, noteText: 10000 };

const USERNAME_RE = /^[A-Za-z][A-Za-z0-9_]{3,31}$/;

/** Разбирает ECO_BOTS; при ошибке возвращает список по умолчанию. */
export function parseEcoBots(raw) {
  if (!raw) return DEFAULT_ECO_BOTS;
  try {
    const list = JSON.parse(raw);
    if (!Array.isArray(list)) throw new Error('не массив');
    const bots = list
      .map((b, i) => ({
        id: String(b.id || `bot${i}`),
        icon: String(b.icon || '🤖'),
        title: String(b.title || b.bot || ''),
        desc: String(b.desc || ''),
        ...(b.tab ? { tab: String(b.tab) } : { bot: String(b.bot || '').replace(/^@/, '') }),
      }))
      .filter((b) => b.title && (b.tab || USERNAME_RE.test(b.bot)));
    if (!bots.length) throw new Error('пустой список');
    return bots;
  } catch (e) {
    console.error('[eco] ECO_BOTS не разобран, используется список по умолчанию:', e.message);
    return DEFAULT_ECO_BOTS;
  }
}

/** Строка из тела запроса: обрезает пробелы, проверяет тип и длину. */
function str(value, name, max, required = true) {
  if (value == null) value = '';
  if (typeof value !== 'string') throw bad(`${name}: ожидается текст`);
  value = value.trim();
  if (required && !value) throw bad(`${name}: заполните поле`);
  if (value.length > max) throw bad(`${name}: не длиннее ${max} символов`);
  return value;
}

const idOf = (params) => {
  const id = Number(params.id);
  if (!Number.isSafeInteger(id) || id < 1) throw bad('Некорректный id');
  return id;
};

// ---------- Настройки ----------

export function getSettings(db, userId) {
  const row = db.prepare('SELECT palette, hue, effect FROM eco_settings WHERE user_id = ?').get(userId);
  return row ? { ...row } : { ...DEFAULT_SETTINGS };
}

export function saveSettings(db, userId, body = {}) {
  const s = getSettings(db, userId);
  if (body.palette !== undefined) {
    if (!PALETTES.includes(body.palette)) throw bad('Неизвестная палитра');
    s.palette = body.palette;
  }
  if (body.hue !== undefined) {
    if (!Number.isInteger(body.hue) || body.hue < 0 || body.hue >= HUES) throw bad(`Оттенок — число от 0 до ${HUES - 1}`);
    s.hue = body.hue;
  }
  if (body.effect !== undefined) {
    if (!EFFECTS.includes(body.effect)) throw bad('Неизвестный эффект');
    s.effect = body.effect;
  }
  db.prepare(
    `INSERT INTO eco_settings (user_id, palette, hue, effect) VALUES (?, ?, ?, ?)
     ON CONFLICT(user_id) DO UPDATE SET palette = excluded.palette, hue = excluded.hue, effect = excluded.effect, updated_at = unixepoch()`,
  ).run(userId, s.palette, s.hue, s.effect);
  return s;
}

// ---------- Шаблоны ----------

export function listTemplates(db, userId) {
  return db.prepare('SELECT id, title, text, created_at FROM eco_templates WHERE user_id = ? ORDER BY id DESC').all(userId);
}

export function addTemplate(db, userId, body = {}) {
  const title = str(body.title, 'Название', LIMITS.templateTitle);
  const text = str(body.text, 'Текст', LIMITS.templateText);
  const { n } = db.prepare('SELECT COUNT(*) AS n FROM eco_templates WHERE user_id = ?').get(userId);
  if (n >= LIMITS.templates) throw bad(`Можно сохранить не больше ${LIMITS.templates} шаблонов`, 409);
  const id = Number(db.prepare('INSERT INTO eco_templates (user_id, title, text) VALUES (?, ?, ?)').run(userId, title, text).lastInsertRowid);
  return db.prepare('SELECT id, title, text, created_at FROM eco_templates WHERE id = ?').get(id);
}

// ---------- Заметки ----------

function noteFields(body = {}) {
  const title = str(body.title, 'Заголовок', LIMITS.noteTitle, false);
  const text = str(body.text, 'Текст', LIMITS.noteText, false);
  if (!title && !text) throw bad('Заметка пустая');
  return { title, text };
}

const NOTE_COLS = 'id, title, text, created_at, updated_at';

export default {
  id: 'eco',
  title: 'StarTegs',
  icon: '🌐',
  order: -1,

  migrations: [
    `CREATE TABLE IF NOT EXISTS eco_settings (
       user_id INTEGER PRIMARY KEY REFERENCES users(id),
       palette TEXT NOT NULL DEFAULT 'violet',
       hue INTEGER NOT NULL DEFAULT 0,          -- оттенок палитры «Своя», 0..11
       effect TEXT NOT NULL DEFAULT 'off',      -- off | auto | snow | leaves | petals | fireflies
       updated_at INTEGER NOT NULL DEFAULT (unixepoch())
     )`,
    `CREATE TABLE IF NOT EXISTS eco_templates (
       id INTEGER PRIMARY KEY AUTOINCREMENT,
       user_id INTEGER NOT NULL REFERENCES users(id),
       title TEXT NOT NULL,
       text TEXT NOT NULL,
       created_at INTEGER NOT NULL DEFAULT (unixepoch())
     )`,
    'CREATE INDEX IF NOT EXISTS eco_templates_user ON eco_templates(user_id)',
    `CREATE TABLE IF NOT EXISTS eco_notes (
       id INTEGER PRIMARY KEY AUTOINCREMENT,
       user_id INTEGER NOT NULL REFERENCES users(id),
       title TEXT NOT NULL DEFAULT '',
       text TEXT NOT NULL DEFAULT '',
       created_at INTEGER NOT NULL DEFAULT (unixepoch()),
       updated_at INTEGER NOT NULL DEFAULT (unixepoch())
     )`,
    'CREATE INDEX IF NOT EXISTS eco_notes_user ON eco_notes(user_id)',
  ],

  setupApi(router, { config }) {
    const bots = parseEcoBots(config.ecoBots);

    router.get('/api/eco', ({ db, user }) => ({
      bots,
      android: ANDROID_URL,
      settings: getSettings(db, user.id),
      counts: {
        templates: db.prepare('SELECT COUNT(*) AS n FROM eco_templates WHERE user_id = ?').get(user.id).n,
        notes: db.prepare('SELECT COUNT(*) AS n FROM eco_notes WHERE user_id = ?').get(user.id).n,
      },
      limits: LIMITS,
    }));

    router.get('/api/eco/settings', ({ db, user }) => getSettings(db, user.id));
    router.post('/api/eco/settings', ({ db, user, body }) => saveSettings(db, user.id, body));

    router.get('/api/eco/templates', ({ db, user }) => ({ templates: listTemplates(db, user.id), limit: LIMITS.templates }));
    router.post('/api/eco/templates', ({ db, user, body }) => addTemplate(db, user.id, body));
    router.delete('/api/eco/templates/:id', ({ db, user, params }) => {
      const r = db.prepare('DELETE FROM eco_templates WHERE id = ? AND user_id = ?').run(idOf(params), user.id);
      if (!r.changes) throw bad('Шаблон не найден', 404);
      return { ok: true };
    });

    router.get('/api/eco/notes', ({ db, user }) => ({
      notes: db.prepare(`SELECT ${NOTE_COLS} FROM eco_notes WHERE user_id = ? ORDER BY updated_at DESC, id DESC`).all(user.id),
    }));
    router.get('/api/eco/notes/:id', ({ db, user, params }) => {
      const note = db.prepare(`SELECT ${NOTE_COLS} FROM eco_notes WHERE id = ? AND user_id = ?`).get(idOf(params), user.id);
      if (!note) throw bad('Заметка не найдена', 404);
      return note;
    });
    router.post('/api/eco/notes', ({ db, user, body }) => {
      const { title, text } = noteFields(body);
      const { n } = db.prepare('SELECT COUNT(*) AS n FROM eco_notes WHERE user_id = ?').get(user.id);
      if (n >= LIMITS.notes) throw bad(`Можно сохранить не больше ${LIMITS.notes} заметок`, 409);
      const id = Number(db.prepare('INSERT INTO eco_notes (user_id, title, text) VALUES (?, ?, ?)').run(user.id, title, text).lastInsertRowid);
      return db.prepare(`SELECT ${NOTE_COLS} FROM eco_notes WHERE id = ?`).get(id);
    });
    // Обновление заметки (роутер поддерживает GET/POST/DELETE)
    router.post('/api/eco/notes/:id', ({ db, user, params, body }) => {
      const id = idOf(params);
      const { title, text } = noteFields(body);
      const r = db.prepare('UPDATE eco_notes SET title = ?, text = ?, updated_at = unixepoch() WHERE id = ? AND user_id = ?').run(title, text, id, user.id);
      if (!r.changes) throw bad('Заметка не найдена', 404);
      return db.prepare(`SELECT ${NOTE_COLS} FROM eco_notes WHERE id = ?`).get(id);
    });
    router.delete('/api/eco/notes/:id', ({ db, user, params }) => {
      const r = db.prepare('DELETE FROM eco_notes WHERE id = ? AND user_id = ?').run(idOf(params), user.id);
      if (!r.changes) throw bad('Заметка не найдена', 404);
      return { ok: true };
    });
  },
};
