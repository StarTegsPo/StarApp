// Компас — клиент мессенджера. Один и тот же код работает в браузере (PWA)
// и внутри нативных приложений iOS/Android (Capacitor).

// ================= утилиты =================
const $ = (sel, root = document) => root.querySelector(sel);
const LS = {
  get(k, d = null) { try { const v = localStorage.getItem('kompas.' + k); return v === null ? d : JSON.parse(v); } catch { return d; } },
  set(k, v) { try { v === null ? localStorage.removeItem('kompas.' + k) : localStorage.setItem('kompas.' + k, JSON.stringify(v)); } catch { /* приватный режим */ } },
};

/** h('div.cls#id', {attrs}, ...children) — DOM без innerHTML для пользовательских данных (защита от XSS). */
function h(tag, attrs, ...kids) {
  const [base, id] = tag.split('#');
  const [name, ...cls] = base.split('.');
  const el = document.createElement(name || 'div');
  if (id) el.id = id;
  if (cls.length) el.className = cls.join(' ');
  if (attrs && (typeof attrs !== 'object' || attrs instanceof Node || Array.isArray(attrs))) { kids.unshift(attrs); attrs = null; }
  for (const [k, v] of Object.entries(attrs || {})) {
    if (v === undefined || v === null || v === false) continue;
    if (k.startsWith('on')) el.addEventListener(k.slice(2), v);
    else if (k === 'class') el.className += (el.className ? ' ' : '') + v;
    else if (k === 'html') el.innerHTML = v; // только для своих SVG-иконок
    else if (k === 'value') el.value = v;
    else el.setAttribute(k, v === true ? '' : v);
  }
  for (const kid of kids.flat(Infinity)) {
    if (kid === null || kid === undefined || kid === false) continue;
    el.append(kid instanceof Node ? kid : document.createTextNode(String(kid)));
  }
  return el;
}

const svg = (body, extra = '') => `<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" ${extra}>${body}</svg>`;
const I = {
  menu: svg('<path d="M4 6h16M4 12h16M4 18h16"/>'),
  back: svg('<path d="M19 12H5M11 18l-6-6 6-6"/>'),
  search: svg('<circle cx="11" cy="11" r="7"/><path d="m20 20-3.5-3.5"/>'),
  pencil: svg('<path d="M4 20h4L19 9a2.8 2.8 0 0 0-4-4L4 16v4Z"/><path d="m13.5 6.5 4 4"/>'),
  send: '<svg viewBox="0 0 24 24" fill="currentColor"><path d="M3.4 20.4 21 12 3.4 3.6 3.4 10l12.6 2-12.6 2z"/></svg>',
  mic: svg('<rect x="9" y="3" width="6" height="11" rx="3"/><path d="M5 11a7 7 0 0 0 14 0M12 18v3"/>'),
  clip: svg('<path d="m21.4 11.1-9.2 9.2a6 6 0 0 1-8.5-8.5l9.2-9.2a4 4 0 0 1 5.7 5.7l-9.2 9.2a2 2 0 0 1-2.8-2.8l8.5-8.5"/>'),
  smile: svg('<circle cx="12" cy="12" r="9"/><path d="M8.5 14.5a4.5 4.5 0 0 0 7 0"/><path d="M9 9.5h.01M15 9.5h.01" stroke-width="3"/>'),
  close: svg('<path d="M18 6 6 18M6 6l12 12"/>'),
  check: svg('<path d="m5 12.5 4.5 4.5L19 7.5"/>'),
  checks: svg('<path d="m2 12.5 4.5 4.5L16 7.5"/><path d="m11.5 16 1 1L22 7.5"/>'),
  clock: svg('<circle cx="12" cy="12" r="8"/><path d="M12 8v4l2.5 2"/>'),
  group: svg('<path d="M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2"/><circle cx="9" cy="7" r="4"/><path d="M22 21v-2a4 4 0 0 0-3-3.9M16 3.1a4 4 0 0 1 0 7.8"/>'),
  channel: svg('<path d="M3 10v4a1 1 0 0 0 1 1h3l6 4V5L7 9H4a1 1 0 0 0-1 1Z"/><path d="M17 8.5a5 5 0 0 1 0 7M19.5 6a8.5 8.5 0 0 1 0 12"/>'),
  bookmark: svg('<path d="M6 3h12v18l-6-4.5L6 21z"/>'),
  bookmarkFill: '<svg viewBox="0 0 24 24" fill="currentColor"><path d="M6 3h12v18l-6-4.5L6 21z"/></svg>',
  settings: svg('<circle cx="12" cy="12" r="3"/><path d="M19.4 15a1.7 1.7 0 0 0 .3 1.8l.1.1a2 2 0 1 1-2.8 2.8l-.1-.1a1.7 1.7 0 0 0-1.8-.3 1.7 1.7 0 0 0-1 1.5V21a2 2 0 1 1-4 0v-.1a1.7 1.7 0 0 0-1.1-1.5 1.7 1.7 0 0 0-1.8.3l-.1.1a2 2 0 1 1-2.8-2.8l.1-.1a1.7 1.7 0 0 0 .3-1.8 1.7 1.7 0 0 0-1.5-1H3a2 2 0 1 1 0-4h.1a1.7 1.7 0 0 0 1.5-1.1 1.7 1.7 0 0 0-.3-1.8l-.1-.1a2 2 0 1 1 2.8-2.8l.1.1a1.7 1.7 0 0 0 1.8.3H9a1.7 1.7 0 0 0 1-1.5V3a2 2 0 1 1 4 0v.1a1.7 1.7 0 0 0 1 1.5 1.7 1.7 0 0 0 1.8-.3l.1-.1a2 2 0 1 1 2.8 2.8l-.1.1a1.7 1.7 0 0 0-.3 1.8V9a1.7 1.7 0 0 0 1.5 1H21a2 2 0 1 1 0 4h-.1a1.7 1.7 0 0 0-1.5 1Z"/>'),
  moon: svg('<path d="M21 12.8A9 9 0 1 1 11.2 3a7 7 0 0 0 9.8 9.8Z"/>'),
  sun: svg('<circle cx="12" cy="12" r="4"/><path d="M12 2v2M12 20v2M4.9 4.9l1.4 1.4M17.7 17.7l1.4 1.4M2 12h2M20 12h2M4.9 19.1l1.4-1.4M17.7 6.3l1.4-1.4"/>'),
  dots: '<svg viewBox="0 0 24 24" fill="currentColor"><circle cx="12" cy="5" r="2"/><circle cx="12" cy="12" r="2"/><circle cx="12" cy="19" r="2"/></svg>',
  reply: svg('<path d="M9 17 4 12l5-5"/><path d="M20 18v-2a4 4 0 0 0-4-4H4"/>'),
  forward: svg('<path d="m15 17 5-5-5-5"/><path d="M4 18v-2a4 4 0 0 1 4-4h12"/>'),
  copy: svg('<rect x="9" y="9" width="12" height="12" rx="2"/><path d="M5 15H4a1 1 0 0 1-1-1V4a1 1 0 0 1 1-1h10a1 1 0 0 1 1 1v1"/>'),
  pin: svg('<path d="M12 17v5M9 3h6l-1 6 4 4H6l4-4z"/>'),
  unpin: svg('<path d="M12 17v5M9 3h6l-1 6 4 4H6l4-4zM3 3l18 18"/>'),
  trash: svg('<path d="M3 6h18M8 6V4h8v2M19 6l-1 14H6L5 6"/>'),
  edit: svg('<path d="M12 20h9"/><path d="M16.5 3.5a2.1 2.1 0 0 1 3 3L7 19l-4 1 1-4Z"/>'),
  mute: svg('<path d="M11 5 6 9H2v6h4l5 4V5zM23 9l-6 6M17 9l6 6"/>'),
  unmute: svg('<path d="M11 5 6 9H2v6h4l5 4V5zM15.5 8.5a5 5 0 0 1 0 7M19 5a10 10 0 0 1 0 14"/>'),
  mutedSmall: '<svg viewBox="0 0 24 24" fill="currentColor"><path d="M11 5 6 9H2v6h4l5 4zM16.3 9.3l1.4-1.4 2.3 2.3 2.3-2.3 1.4 1.4-2.3 2.3 2.3 2.3-1.4 1.4-2.3-2.3-2.3 2.3-1.4-1.4 2.3-2.3z"/></svg>',
  pinSmall: '<svg viewBox="0 0 24 24" fill="currentColor"><path d="M15 3H9l1 6-4 4v2h5v6l1 1 1-1v-6h5v-2l-4-4z"/></svg>',
  user: svg('<circle cx="12" cy="8" r="4"/><path d="M4 21a8 8 0 0 1 16 0"/>'),
  at: svg('<circle cx="12" cy="12" r="4"/><path d="M16 8v5a3 3 0 0 0 6 0v-1a10 10 0 1 0-4 8"/>'),
  info: svg('<circle cx="12" cy="12" r="9"/><path d="M12 16v-5M12 8h.01"/>'),
  leave: svg('<path d="M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4M16 17l5-5-5-5M21 12H9"/>'),
  addUser: svg('<path d="M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2"/><circle cx="9" cy="7" r="4"/><path d="M19 8v6M22 11h-6"/>'),
  lock: svg('<rect x="4" y="11" width="16" height="10" rx="2"/><path d="M8 11V7a4 4 0 0 1 8 0v4"/>'),
  down: svg('<path d="m6 9 6 6 6-6"/>'),
  play: '<svg viewBox="0 0 24 24" fill="currentColor"><path d="M8 5.5v13a1 1 0 0 0 1.5.9l10-6.5a1 1 0 0 0 0-1.7l-10-6.6A1 1 0 0 0 8 5.5z"/></svg>',
  pause: '<svg viewBox="0 0 24 24" fill="currentColor"><rect x="6" y="5" width="4" height="14" rx="1"/><rect x="14" y="5" width="4" height="14" rx="1"/></svg>',
  logout: svg('<path d="M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4M16 17l5-5-5-5M21 12H9"/>'),
  bell: svg('<path d="M6 8a6 6 0 0 1 12 0c0 7 3 9 3 9H3s3-2 3-9M10.3 21a1.9 1.9 0 0 0 3.4 0"/>'),
  palette: svg('<circle cx="13.5" cy="6.5" r="1.5"/><circle cx="17.5" cy="10.5" r="1.5"/><circle cx="8.5" cy="7.5" r="1.5"/><circle cx="6.5" cy="12.5" r="1.5"/><path d="M12 2a10 10 0 0 0 0 20c.9 0 1.7-.8 1.7-1.7 0-.4-.2-.8-.4-1.1-.3-.3-.4-.7-.4-1.1 0-.9.8-1.7 1.7-1.7H17a5 5 0 0 0 5-5C22 6 17.5 2 12 2z"/>'),
};
const icon = (name) => h('span', { html: I[name], style: 'display:contents' });

// Палитра аватарок (градиенты) и цвета имён в группах
const AV = [['#ff885e', '#ff516a'], ['#ffcd6a', '#ffa85c'], ['#82b1ff', '#665fff'], ['#a0de7e', '#54cb68'],
  ['#53edd6', '#28c9b7'], ['#72d5fd', '#2a9ef1'], ['#e0a2f3', '#d669ed'], ['#ffb3b3', '#f07575']];
const NAME_COLORS = ['#e17076', '#eda86c', '#a695e7', '#7bc862', '#6ec9cb', '#65aadd', '#ee7aae', '#e7826a'];

const REACTIONS = ['👍', '❤️', '🔥', '😂', '😮', '😢', '🙏', '👏', '🎉', '🤔', '👎', '💯'];
const EMOJI = ('😀 😃 😄 😁 😆 😅 😂 🤣 🙂 😉 😊 😇 🥰 😍 🤩 😘 😋 😛 😜 🤪 😎 🤓 🧐 🤔 🤗 🤭 🤫 😐 😑 😶 😏 😒 🙄 😬 😌 😔 😪 😴 😷 🤒 🤕 🤢 🥵 🥶 😵 🤯 🤠 🥳 😕 😟 🙁 😮 😯 😲 😳 🥺 😦 😧 😨 😰 😥 😢 😭 😱 😖 😣 😞 😓 😩 😫 😤 😡 😠 🤬 😈 💀 💩 🤡 👻 👽 🤖 '
  + '👍 👎 👌 ✌️ 🤞 🤟 🤘 🤙 👈 👉 👆 👇 ☝️ ✋ 👋 👏 🙌 🙏 💪 🤝 ❤️ 🧡 💛 💚 💙 💜 🖤 🤍 💔 💯 💥 🔥 ✨ ⭐ 🌟 🎉 🎊 🎁 🏆 🥇 ⚽ 🏀 🎮 🎵 🎶 '
  + '☀️ 🌙 ⛅ 🌧 ❄️ 🌈 🌊 🌸 🌹 🌻 🍀 🌲 🏔 🧭 🗺 ✈️ 🚀 🚗 🏠 ☕ 🍵 🍺 🍷 🍕 🍔 🍟 🍣 🍰 🍫 🍎 🍓 🍉 🐶 🐱 🦊 🐻 🐼 🐨 🦁 🐸 🐵 🦄 🐝 🦋 💡 📌 📎 ✅ ❌ ❓ ❗ 💬 👀 🎯 💰').split(' ');

function toast(text) {
  const t = $('#toast');
  t.textContent = text;
  t.classList.add('show');
  clearTimeout(toast.timer);
  toast.timer = setTimeout(() => t.classList.remove('show'), 2400);
}

function plural(n, [one, few, many]) {
  const a = Math.abs(n) % 100; const b = a % 10;
  if (a > 10 && a < 20) return many;
  if (b > 1 && b < 5) return few;
  if (b === 1) return one;
  return many;
}
const pad = (n) => String(n).padStart(2, '0');
const hm = (ts) => { const d = new Date(ts); return `${pad(d.getHours())}:${pad(d.getMinutes())}`; };
const mmss = (s) => `${Math.floor(s / 60)}:${pad(Math.floor(s % 60))}`;
const MONTHS = ['января', 'февраля', 'марта', 'апреля', 'мая', 'июня', 'июля', 'августа', 'сентября', 'октября', 'ноября', 'декабря'];
function dayLabel(ts) {
  const d = new Date(ts); const now = new Date();
  if (d.toDateString() === now.toDateString()) return 'Сегодня';
  const y = new Date(now); y.setDate(now.getDate() - 1);
  if (d.toDateString() === y.toDateString()) return 'Вчера';
  return `${d.getDate()} ${MONTHS[d.getMonth()]}${d.getFullYear() !== now.getFullYear() ? ' ' + d.getFullYear() : ''}`;
}
function shortTime(ts) {
  const d = new Date(ts); const now = new Date();
  if (d.toDateString() === now.toDateString()) return hm(ts);
  if (now - d < 6 * 864e5) return ['вс', 'пн', 'вт', 'ср', 'чт', 'пт', 'сб'][d.getDay()];
  return `${pad(d.getDate())}.${pad(d.getMonth() + 1)}${d.getFullYear() !== now.getFullYear() ? '.' + String(d.getFullYear()).slice(2) : ''}`;
}
function lastSeen(u) {
  if (!u) return '';
  if (u.online) return 'в сети';
  if (!u.lastSeen) return 'был(а) недавно';
  const diff = Date.now() - u.lastSeen;
  if (diff < 60e3) return 'был(а) только что';
  if (diff < 3600e3) { const m = Math.floor(diff / 60e3); return `был(а) ${m} ${plural(m, ['минуту', 'минуты', 'минут'])} назад`; }
  if (new Date(u.lastSeen).toDateString() === new Date().toDateString()) return `был(а) сегодня в ${hm(u.lastSeen)}`;
  if (diff < 7 * 864e5) return `был(а) ${dayLabel(u.lastSeen).toLowerCase()} в ${hm(u.lastSeen)}`;
  return 'был(а) давно';
}
const initials = (name) => (name || '?').trim().split(/\s+/).slice(0, 2).map((w) => [...w][0] || '').join('').toUpperCase();
const onlyEmoji = (t) => t && t.length <= 12 && /^(\p{Extended_Pictographic}|\p{Emoji_Modifier}|‍|️|\s)+$/u.test(t);

function linkify(text) {
  const out = [];
  const re = /\bhttps?:\/\/[^\s<>"']+[^\s<>"'.,;:!?)\]]|(?<![\w@])@[a-zA-Z][a-zA-Z0-9_]{2,31}\b/gi;
  let last = 0; let m;
  while ((m = re.exec(text))) {
    if (m.index > last) out.push(text.slice(last, m.index));
    if (m[0].startsWith('@')) {
      const handle = m[0].slice(1);
      out.push(h('a', { href: '#', onclick: (e) => { e.preventDefault(); e.stopPropagation(); openHandle(handle); } }, m[0]));
    } else out.push(h('a', { href: m[0], target: '_blank', rel: 'noopener noreferrer' }, m[0]));
    last = m.index + m[0].length;
  }
  if (last < text.length) out.push(text.slice(last));
  return out;
}

// ================= состояние =================
const isNative = !!window.Capacitor?.isNativePlatform?.();
const coarse = () => matchMedia('(pointer: coarse)').matches;
const S = {
  token: LS.get('token'),
  me: LS.get('me'),
  chats: new Map(),
  users: new Map(),
  messages: new Map(), // chatId -> [msg]
  hasMore: new Map(),
  unreadMark: new Map(), // chatId -> lastReadId на момент открытия (для «Непрочитанные сообщения»)
  current: null,
  reply: null,
  editing: null,
  typing: new Map(),
  filter: '',
  folder: LS.get('folder', 'all'),
  online: true,
  recording: null,
  audio: null, // { id, el }
};

function serverBase() {
  const saved = LS.get('server');
  if (saved) return saved;
  if (window.KOMPAS_SERVER) return window.KOMPAS_SERVER.replace(/\/$/, '');
  if (!isNative && /^https?:$/.test(location.protocol)) return location.origin + location.pathname.replace(/\/[^/]*$/, '');
  return '';
}

class ApiError extends Error {
  constructor(status, message) { super(message); this.status = status; }
}

async function api(method, path, body, { raw } = {}) {
  const base = serverBase();
  if (!base) throw new ApiError(0, 'Укажите адрес сервера');
  const headers = {};
  if (S.token) headers.Authorization = 'Bearer ' + S.token;
  let payload;
  if (raw) { payload = raw; headers['Content-Type'] = raw.type || 'application/octet-stream'; }
  else if (body !== undefined) { payload = JSON.stringify(body); headers['Content-Type'] = 'application/json'; }
  let res;
  try {
    res = await fetch(base + path, { method, headers, body: payload });
  } catch {
    throw new ApiError(0, 'Нет соединения с сервером');
  }
  const data = await res.json().catch(() => ({}));
  if (!res.ok) {
    if (res.status === 401 && S.token && !path.startsWith('/api/login')) logout(false);
    throw new ApiError(res.status, data.error || `Ошибка ${res.status}`);
  }
  return data;
}

const fileUrl = (name) => `${serverBase()}/files/${name}?t=${encodeURIComponent(S.token)}`;
const upload = async (blob) => (await api('POST', '/api/upload', undefined, { raw: blob })).file;

function remember(u) {
  if (!u) return u;
  const next = { ...(S.users.get(u.id) || {}), ...u };
  delete next.role;
  S.users.set(u.id, next);
  if (S.me && u.id === S.me.id) { S.me = { ...S.me, ...next }; LS.set('me', S.me); }
  return next;
}
const user = (id) => S.users.get(id) || { id, name: 'Пользователь', username: '', color: 0 };

let fetchingUsers = new Set();
let usersTimer;
/** Подгружает неизвестных пользователей (например, вышедших из группы) и перерисовывает чат. */
function ensureUsers(ids) {
  const missing = ids.filter((id) => id && !S.users.has(id) && !fetchingUsers.has(id));
  if (!missing.length) return;
  missing.forEach((id) => fetchingUsers.add(id));
  clearTimeout(usersTimer);
  usersTimer = setTimeout(async () => {
    const list = [...fetchingUsers];
    await Promise.all(list.map((id) => api('GET', `/api/users/${id}`).then(remember).catch(() => S.users.set(id, { id, name: 'Удалённый аккаунт', color: 0 }))));
    fetchingUsers = new Set();
    renderMessages({ keepBottom: true });
    renderList();
  }, 50);
}

function putChat(chat) {
  (chat.members || []).forEach(remember);
  S.chats.set(chat.id, chat);
  return chat;
}
function peerOf(chat) {
  if (chat?.type !== 'direct') return null;
  const m = chat.members.find((x) => x.id !== S.me.id);
  return m ? user(m.id) : null;
}
function chatTitle(chat) {
  if (chat.type === 'saved') return 'Избранное';
  if (chat.type === 'direct') return peerOf(chat)?.name || 'Чат';
  return chat.title;
}

/** Аватарка: фото, градиент с инициалами или значок «Избранного». */
function avatarEl(o, cls = '') {
  const el = h('div.avatar' + (cls ? '.' + cls.split(' ').join('.') : ''));
  if (o.saved) { el.style.cssText = '--c1:#72d5fd;--c2:#2a9ef1'; el.innerHTML = I.bookmarkFill; return el; }
  const [c1, c2] = AV[Math.abs(o.color ?? 0) % AV.length];
  el.style.cssText = `--c1:${c1};--c2:${c2}`;
  if (o.avatar) el.append(h('img', { src: fileUrl(o.avatar), alt: '', loading: 'lazy', draggable: 'false' }));
  else el.append(initials(o.name));
  if (o.online) el.append(h('span.dot'));
  return el;
}
const userAv = (u, cls, { dot = false } = {}) => avatarEl({ name: u.name, color: u.color, avatar: u.avatar, online: dot && u.online && u.id !== S.me?.id }, cls);
function chatAv(chat, cls, opts = {}) {
  if (chat.type === 'saved') return avatarEl({ saved: true }, cls);
  const peer = peerOf(chat);
  if (peer) return userAv(peer, cls, opts);
  return avatarEl({ name: chat.title, color: chat.id, avatar: chat.avatar }, cls);
}

// ================= обои и мелкие картинки =================
function setupArt() {
  // Хвостик пузыря
  const tail = 'data:image/svg+xml,' + encodeURIComponent('<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 9 17"><path d="M0 0v17h6.5c1.5 0 2.4-1.6 1.6-2.9C5 9.5 1.6 6.3 0 0z"/></svg>');
  // Узор обоев: компасы, звёзды, горы, облака и метки на карте (своя графика)
  const P = [
    '<g transform="translate(40 40)"><circle r="18"/><path d="M0-12 4 0 0 12-4 0z"/></g>',
    '<path d="M120 30l4 9 10 1-7 7 2 10-9-5-9 5 2-10-7-7 10-1z"/>',
    '<path d="M190 70l18-28 12 18 8-10 16 20z"/>',
    '<path d="M60 120a10 10 0 0 1 18-6 8 8 0 0 1 14 6 7 7 0 0 1-2 14H64a8 8 0 0 1-4-14z"/>',
    '<path d="M160 120c0-10 16-10 16 0 0 7-8 14-8 14s-8-7-8-14z"/><circle cx="168" cy="120" r="3"/>',
    '<g transform="translate(250 130)"><circle r="12"/><path d="M0-20v8M0 12v8M-20 0h8M12 0h8"/></g>',
    '<path d="M30 200q10-10 20 0t20 0 20 0"/>',
    '<g transform="translate(120 200)"><circle r="16"/><path d="M0-10 3 0 0 10-3 0z"/><path d="M-10 0h-4M10 0h4M0-14v-3M0 14v3"/></g>',
    '<path d="M200 190l4 9 10 1-7 7 2 10-9-5-9 5 2-10-7-7 10-1z"/>',
    '<path d="M240 260l12-18 9 13 6-7 12 12z"/>',
    '<path d="M60 260c0-10 16-10 16 0 0 7-8 14-8 14s-8-7-8-14z"/>',
    '<circle cx="160" cy="270" r="4"/><circle cx="175" cy="262" r="2"/><circle cx="148" cy="258" r="2"/>',
    '<path d="M270 40q6 8 0 16t0 16"/>',
  ].join('');
  const pattern = 'url("data:image/svg+xml,' + encodeURIComponent(`<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 300 300" fill="none" stroke="#000" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">${P}</svg>`) + '")';
  const camera = 'url("data:image/svg+xml,' + encodeURIComponent('<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="#fff" stroke-width="2"><path d="M23 19a2 2 0 0 1-2 2H3a2 2 0 0 1-2-2V8a2 2 0 0 1 2-2h4l2-3h6l2 3h4a2 2 0 0 1 2 2z"/><circle cx="12" cy="13" r="4"/></svg>') + '")';
  const r = document.documentElement.style;
  r.setProperty('--tail', `url("${tail}")`);
  r.setProperty('--pattern', pattern);
  r.setProperty('--camera', camera);
}

// ================= авторизация =================
function renderAuth(mode = 'login', error = '') {
  const needServer = !serverBase();
  let busy = false;
  const errEl = h('div.err', error);
  const btn = h('button.btn', { type: 'submit' }, mode === 'register' ? 'Создать аккаунт' : 'Далее');
  const field = (label, input) => h('label.field', input, h('span', label));
  const form = h('form', {
    onsubmit: async (e) => {
      e.preventDefault();
      if (busy) return;
      const fd = new FormData(form);
      if (fd.get('server') !== null) {
        const s = normalizeServer(fd.get('server'));
        if (!s) { errEl.textContent = 'Укажите адрес сервера, например https://kompas.example.ru'; return; }
        LS.set('server', s);
      }
      busy = true; btn.disabled = true;
      try {
        const body = { username: fd.get('username'), password: fd.get('password') };
        if (mode === 'register') body.name = fd.get('name');
        onLogin(await api('POST', mode === 'register' ? '/api/register' : '/api/login', body));
      } catch (err) {
        errEl.textContent = err.message;
      } finally { busy = false; btn.disabled = false; }
    },
  }, ...[
    needServer ? field('Адрес сервера', h('input', { name: 'server', type: 'url', inputmode: 'url', placeholder: 'https://kompas.example.ru', autocapitalize: 'off', required: true })) : null,
    mode === 'register' ? field('Ваше имя', h('input', { name: 'name', autocomplete: 'name', maxlength: 64, placeholder: ' ' })) : null,
    field('Имя пользователя', h('input', { name: 'username', autocomplete: 'username', autocapitalize: 'off', autocorrect: 'off', spellcheck: 'false', required: true, minlength: 3, maxlength: 32 })),
    field('Пароль', h('input', { name: 'password', type: 'password', autocomplete: mode === 'register' ? 'new-password' : 'current-password', required: true, minlength: 6, maxlength: 128 })),
    mode === 'register' ? h('div.hint', 'Имя пользователя — латиница, цифры и _. По нему вас найдут друзья.') : null,
    errEl, btn,
  ].filter(Boolean));

  $('#app').replaceChildren(h('div.auth', h('div.auth-card',
    h('img.auth-logo', { src: 'icons/icon.svg', alt: '' }),
    h('h1', mode === 'register' ? 'Регистрация' : 'Компас'),
    h('p.sub', mode === 'register' ? 'Придумайте имя пользователя и пароль' : 'Войдите, чтобы продолжить общение'),
    form,
    h('button.link-btn', { type: 'button', onclick: () => renderAuth(mode === 'login' ? 'register' : 'login') },
      mode === 'login' ? 'Нет аккаунта? Зарегистрироваться' : 'Уже есть аккаунт? Войти'),
    h('br'),
    !needServer && (isNative || LS.get('server')) ? h('button.link-btn', { type: 'button', style: 'color:var(--muted)', onclick: changeServer },
      `Сервер: ${serverBase().replace(/^https?:\/\//, '')}`) : null,
  )));
}

function normalizeServer(s) {
  s = String(s || '').trim().replace(/\/+$/, '');
  if (!s) return '';
  if (!/^https?:\/\//i.test(s)) s = 'https://' + s;
  try { return new URL(s).toString().replace(/\/+$/, ''); } catch { return ''; }
}

function changeServer() {
  const input = h('input', { type: 'url', value: serverBase(), placeholder: ' ', autocapitalize: 'off' });
  sheet('Адрес сервера', h('div', h('label.field', input, h('span', 'Сервер Компаса')),
    h('div.hint', 'Все собеседники должны быть на одном сервере.')), {
    action: 'Сохранить',
    onAction: (close) => {
      const s = normalizeServer(input.value);
      if (!s) return toast('Некорректный адрес');
      LS.set('server', s); close(); renderAuth();
    },
  });
}

function onLogin({ token, user: me }) {
  S.token = token; S.me = me;
  LS.set('token', token); LS.set('me', me);
  start();
}

async function logout(callApi = true) {
  if (callApi) await api('POST', '/api/logout').catch(() => {});
  S.token = null; S.me = null;
  LS.set('token', null); LS.set('me', null);
  S.chats.clear(); S.messages.clear(); S.current = null;
  ws.stop();
  document.querySelectorAll('.overlay, .drawer, .drawer-bg').forEach((e) => e.remove());
  renderAuth();
}

// ================= WebSocket =================
const ws = {
  sock: null, retry: 0, timer: null, stopped: true,
  start() { this.stopped = false; this.open(); },
  stop() { this.stopped = true; clearTimeout(this.timer); const s = this.sock; this.sock = null; s?.close(); },
  open() {
    if (this.stopped || !S.token) return;
    const sock = new WebSocket(serverBase().replace(/^http/, 'ws') + '/ws?token=' + encodeURIComponent(S.token));
    this.sock = sock;
    sock.onopen = () => {
      const reconnect = this.retry > 0;
      this.retry = 0; setOnline(true);
      if (reconnect) syncAfterReconnect();
    };
    sock.onmessage = (e) => { try { onEvent(JSON.parse(e.data)); } catch (err) { console.error(err); } };
    sock.onclose = () => {
      if (this.sock !== sock) return;
      setOnline(false);
      if (this.stopped) return;
      this.timer = setTimeout(() => this.open(), Math.min(15000, 800 * 2 ** this.retry++));
    };
  },
  send(obj) { if (this.sock?.readyState === 1) this.sock.send(JSON.stringify(obj)); },
};

let offlineTimer;
function setOnline(v) {
  clearTimeout(offlineTimer);
  // Не пугаем «Соединением…» при мгновенном переподключении
  if (!v) offlineTimer = setTimeout(() => { S.online = false; renderSideTitle(); }, 1500);
  else { S.online = true; renderSideTitle(); }
}

async function syncAfterReconnect() {
  await loadChats();
  if (S.current) {
    const msgs = await api('GET', `/api/chats/${S.current}/messages`).catch(() => null);
    if (msgs) { S.messages.set(S.current, msgs); renderMessages({ keepBottom: true }); }
  }
}

function onEvent(ev) {
  switch (ev.type) {
    case 'message': return onNewMessage(ev.message);
    case 'message_edit':
    case 'message_delete': {
      const chatId = ev.message?.chatId ?? ev.chatId;
      const id = ev.message?.id ?? ev.messageId;
      const list = S.messages.get(chatId);
      const i = list ? list.findIndex((x) => x.id === id) : -1;
      if (i >= 0) list[i] = ev.message || { ...list[i], deleted: true, text: '', file: null, reactions: [] };
      const chat = S.chats.get(chatId);
      if (chat?.lastMessage?.id === id) {
        if (ev.message) chat.lastMessage = ev.message;
        else refreshChat(chatId);
      }
      if (chat?.pinnedMessage?.id === id && ev.message) chat.pinnedMessage = ev.message;
      renderList();
      if (S.current === chatId) { renderMessages({ keepBottom: true }); renderPinned(); }
      return;
    }
    case 'reactions': {
      const m = S.messages.get(ev.chatId)?.find((x) => x.id === ev.messageId);
      if (m) m.reactions = ev.reactions;
      if (S.current === ev.chatId) renderMessages({ keepBottom: true });
      return;
    }
    case 'chat':
      putChat(ev.chat);
      renderList();
      if (S.current === ev.chat.id) { renderHeader(); renderPinned(); renderComposerMode(); }
      return;
    case 'chat_meta': {
      const chat = S.chats.get(ev.chatId);
      if (!chat) return;
      Object.assign(chat, { title: ev.title, description: ev.description, handle: ev.handle, avatar: ev.avatar, pinnedMessage: ev.pinnedMessage });
      renderList();
      if (S.current === ev.chatId) { renderHeader(); renderPinned(); }
      return;
    }
    case 'chat_removed':
      S.chats.delete(ev.chatId); S.messages.delete(ev.chatId);
      if (S.current === ev.chatId) closeChat();
      renderList();
      return;
    case 'read': {
      const chat = S.chats.get(ev.chatId);
      if (!chat) return;
      if (ev.userId === S.me.id) {
        chat.lastReadId = Math.max(chat.lastReadId, ev.messageId);
        recountUnread(chat);
      } else chat.readUpTo = Math.max(chat.readUpTo, ev.messageId);
      renderList();
      if (S.current === ev.chatId) renderMessages({ keepBottom: true });
      return;
    }
    case 'presence':
      remember({ id: ev.userId, online: ev.online, lastSeen: ev.lastSeen ?? user(ev.userId).lastSeen });
      renderList(); renderHeader();
      return;
    case 'user':
      remember(ev.user);
      renderList(); renderHeader();
      if (S.current) renderMessages({ keepBottom: true });
      return;
    case 'typing': {
      let map = S.typing.get(ev.chatId);
      if (!map) S.typing.set(ev.chatId, (map = new Map()));
      clearTimeout(map.get(ev.userId));
      map.set(ev.userId, setTimeout(() => clearTyping(ev.chatId, ev.userId), 5000));
      renderHeader(); renderList();
      return;
    }
  }
}

function onNewMessage(m) {
  const chat = S.chats.get(m.chatId);
  if (!chat || !chat.joined) { refreshChat(m.chatId); return; }
  const list = S.messages.get(m.chatId);
  if (list && !list.some((x) => x.id === m.id)) {
    // Своё сообщение может прийти раньше ответа на POST — заменяем «часики»
    const temp = m.userId === S.me.id ? list.findIndex((x) => x.pending && x.text === m.text && x.kind === m.kind) : -1;
    if (temp >= 0) list.splice(temp, 1, m); else list.push(m);
  }
  chat.lastMessage = m;
  if (m.userId !== S.me.id) {
    clearTyping(m.chatId, m.userId);
    if (m.kind !== 'system') {
      if (S.current === m.chatId && isVisible() && nearBottom()) markRead(m.chatId, m.id);
      else { chat.unread++; notify(chat, m); }
    }
  } else {
    chat.lastReadId = Math.max(chat.lastReadId, m.id);
    S.unreadMark.delete(m.chatId);
  }
  if (m.kind === 'system') refreshChat(m.chatId);
  renderList();
  if (S.current === m.chatId) renderMessages({ keepBottom: m.userId !== S.me.id, toBottom: m.userId === S.me.id });
}

async function refreshChat(id) {
  try {
    const c = putChat(await api('GET', `/api/chats/${id}`));
    renderList();
    if (S.current === c.id) { renderHeader(); renderPinned(); renderComposerMode(); }
  } catch { /* чат недоступен */ }
}

function clearTyping(chatId, userId) {
  const map = S.typing.get(chatId);
  if (!map?.has(userId)) return;
  clearTimeout(map.get(userId));
  map.delete(userId);
  renderHeader(); renderList();
}

function typingText(chatId) {
  const ids = [...(S.typing.get(chatId)?.keys() || [])];
  if (!ids.length) return '';
  if (S.chats.get(chatId)?.type === 'direct') return 'печатает…';
  return ids.length === 1 ? `${user(ids[0]).name.split(' ')[0]} печатает…` : `${ids.length} ${plural(ids.length, ['человек', 'человека', 'человек'])} печатают…`;
}

function recountUnread(chat) {
  const list = S.messages.get(chat.id);
  if (list) chat.unread = list.filter((m) => m.id > chat.lastReadId && m.userId !== S.me.id && !m.deleted && m.kind !== 'system').length;
  else if (chat.lastMessage && chat.lastMessage.id <= chat.lastReadId) chat.unread = 0;
}

const isVisible = () => document.visibilityState === 'visible';

// ================= уведомления =================
const nativeNotifications = isNative ? {
  call: (method, options = {}) => window.Capacitor.nativePromise('LocalNotifications', method, options),
  on: (event, cb) => window.Capacitor.addListener('LocalNotifications', event, cb),
} : null;

async function notify(chat, m) {
  if (LS.get('notify', true) === false || chat.muted) return;
  const title = chatTitle(chat);
  const body = (chat.type === 'group' ? user(m.userId).name + ': ' : '') + preview(m);
  if (nativeNotifications) {
    try { await nativeNotifications.call('schedule', { notifications: [{ id: m.id % 2147483647, title, body, extra: { chatId: chat.id } }] }); } catch { /* нет разрешения */ }
    return;
  }
  if (!('Notification' in window) || Notification.permission !== 'granted' || isVisible()) return;
  try {
    const reg = await navigator.serviceWorker?.getRegistration();
    if (reg) reg.showNotification(title, { body, tag: 'chat-' + chat.id, icon: 'icons/icon-192.png', data: { chatId: chat.id } });
    else new Notification(title, { body, tag: 'chat-' + chat.id });
  } catch { /* не поддерживается */ }
}

async function askNotifications() {
  if (nativeNotifications) {
    try {
      await nativeNotifications.call('requestPermissions');
      nativeNotifications.on('localNotificationActionPerformed', (a) => { const id = a.notification?.extra?.chatId; if (id) openChat(id); });
    } catch { /* ignore */ }
    return;
  }
  if ('Notification' in window && Notification.permission === 'default') {
    const ask = () => { Notification.requestPermission().catch(() => {}); document.removeEventListener('click', ask); };
    document.addEventListener('click', ask);
  }
}

// ================= основной экран =================
function start() {
  remember(S.me);
  $('#app').replaceChildren(h('div.main#main',
    h('aside.side',
      h('div.topbar',
        h('button.icon-btn', { title: 'Меню', onclick: openDrawer }, icon('menu')),
        h('h2#side-title', 'Компас')),
      h('div.search', h('input#search', {
        type: 'search', placeholder: 'Поиск', autocomplete: 'off', enterkeyhint: 'search',
        oninput: (e) => { S.filter = e.target.value.trim(); renderList(); searchGlobal(); },
      }), h('span', { html: I.search, style: 'display:contents' })),
      h('div.folders#folders'),
      h('div.list#chats'),
      h('button.fab', { title: 'Новое сообщение', onclick: newChat }, icon('pencil'))),
    h('section.pane#pane'),
  ));
  renderSideTitle();
  renderPane();
  renderList();
  loadChats();
  ws.start();
  askNotifications();
}

function renderSideTitle() {
  const t = $('#side-title');
  if (t) t.replaceChildren(S.online ? 'Компас' : h('span.conn', 'Соединение…'));
  if (S.current) renderHeader();
}

async function loadChats() {
  try {
    const [me, chats] = await Promise.all([api('GET', '/api/me'), api('GET', '/api/chats')]);
    remember(me);
    const keepPreview = S.current && S.chats.get(S.current)?.joined === false ? S.chats.get(S.current) : null;
    S.chats.clear();
    chats.forEach(putChat);
    if (keepPreview) S.chats.set(keepPreview.id, keepPreview);
    renderList();
    if (S.current && !S.chats.has(S.current)) closeChat();
    else if (S.current) { renderHeader(); renderPinned(); renderComposerMode(); }
  } catch (err) {
    if (err.status !== 401) toast(err.message);
  }
}

function preview(m) {
  if (!m) return '';
  if (m.deleted) return 'Сообщение удалено';
  if (m.kind === 'system') return systemText(m);
  if (m.kind === 'image') return '🖼 ' + (m.text || 'Фотография');
  if (m.kind === 'voice') return '🎤 Голосовое сообщение';
  return m.text;
}

function systemText(m) {
  const mine = m.userId === S.me.id;
  const who = mine ? 'Вы' : user(m.userId).name;
  const v = (me, other) => (mine ? me : other);
  const i = m.text.indexOf(':');
  const kind = i < 0 ? m.text : m.text.slice(0, i);
  const arg = i < 0 ? '' : m.text.slice(i + 1);
  const chat = S.chats.get(m.chatId);
  switch (kind) {
    case 'created': return `${who} ${v('создали', 'создал(а)')} группу «${chat?.title || ''}»`;
    case 'channel_created': return 'Канал создан';
    case 'renamed': return chat?.type === 'channel' ? `Название канала изменено на «${arg}»` : `${who} ${v('изменили', 'изменил(а)')} название группы на «${arg}»`;
    case 'added': {
      const id = Number(arg);
      ensureUsers([id]);
      if (id === m.userId) return `${who} ${v('вступили', 'вступил(а)')} в группу`;
      return `${who} ${v('добавили', 'добавил(а)')} ${id === S.me.id ? 'вас' : user(id).name}`;
    }
    case 'left': return `${who} ${v('покинули', 'покинул(а)')} группу`;
    case 'pinned': {
      const p = S.messages.get(m.chatId)?.find((x) => x.id === Number(arg));
      const t = p ? preview(p) : 'сообщение';
      return `${who} ${v('закрепили', 'закрепил(а)')} «${t.length > 30 ? t.slice(0, 30) + '…' : t}»`;
    }
    default: return m.text;
  }
}

function sortedChats() {
  return [...S.chats.values()].filter((c) => c.joined)
    .sort((a, b) => ((b.pinned ? 1 : 0) - (a.pinned ? 1 : 0)) || ((b.lastMessage?.createdAt ?? b.createdAt) - (a.lastMessage?.createdAt ?? a.createdAt)));
}

const FOLDERS = [
  ['all', 'Все чаты', () => true],
  ['personal', 'Личные', (c) => c.type === 'direct' || c.type === 'saved'],
  ['groups', 'Группы', (c) => c.type === 'group'],
  ['channels', 'Каналы', (c) => c.type === 'channel'],
];

function renderFolders() {
  const box = $('#folders');
  if (!box) return;
  const all = sortedChats();
  const used = FOLDERS.filter(([k, , f]) => k === 'all' || all.some(f));
  if (!used.some(([k]) => k === S.folder)) S.folder = 'all';
  box.classList.toggle('hidden', used.length < 2 || !!S.filter);
  box.replaceChildren(...used.map(([k, label, f]) => {
    const n = all.filter((c) => f(c) && c.unread && !c.muted).length;
    return h('button', { class: S.folder === k ? 'on' : '', onclick: () => { S.folder = k; LS.set('folder', k); renderList(); } },
      label, n ? h('span.count', n) : null);
  }));
}

let globalResults = { users: [], channels: [] };
let searchTimer;
function searchGlobal() {
  clearTimeout(searchTimer);
  const q = S.filter;
  if (q.replace(/^@/, '').length < 2) { globalResults = { users: [], channels: [] }; renderList(); return; }
  searchTimer = setTimeout(async () => {
    try {
      const res = await api('GET', '/api/search?q=' + encodeURIComponent(q));
      if (q !== S.filter) return;
      res.users.forEach(remember);
      globalResults = res;
      renderList();
    } catch { /* офлайн */ }
  }, 250);
}

function chatRow(c, { onclick, sub } = {}) {
  const m = c.lastMessage;
  const typing = typingText(c.id);
  const mineLast = m && m.userId === S.me.id && m.kind !== 'system' && !m.deleted && c.type !== 'channel';
  const title = [
    c.type === 'group' ? h('span.ic', { html: I.group }) : null,
    c.type === 'channel' ? h('span.ic', { html: I.channel }) : null,
    h('span.name', chatTitle(c)),
    c.muted ? h('span.ic', { html: I.mutedSmall }) : null,
    h('span.fill'),
    mineLast ? h('span.ic.ok', { html: c.readUpTo >= m.id ? I.checks : I.check }) : null,
    h('span.time', m ? shortTime(m.createdAt) : ''),
  ];
  let body;
  if (sub) body = h('span.preview', sub);
  else if (typing) body = h('span.preview', h('span.typing-text', typing));
  else {
    const author = m && m.kind !== 'system' && !m.deleted && (c.type === 'group' || (mineLast && c.type !== 'saved'))
      ? h('b', (m.userId === S.me.id ? 'Вы' : user(m.userId).name.split(' ')[0]) + ': ') : null;
    body = h('span.preview', author, m ? preview(m) : (c.type === 'saved' ? 'Пересылайте сюда сообщения и заметки' : 'Нет сообщений'));
  }
  const row = h('button.row', { class: c.id === S.current && !coarse() ? 'on' : '', onclick: onclick || (() => openChat(c.id)) },
    chatAv(c, '', { dot: true }),
    h('div.body', h('div.top', title), h('div.bottom', body,
      !sub && c.unread ? h('span.badge', { class: c.muted ? 'muted' : '' }, c.unread > 999 ? '999+' : c.unread)
        : !sub && c.pinned ? h('span.ic', { html: I.pinSmall }) : null)));
  if (!onclick) {
    row.addEventListener('contextmenu', (e) => { e.preventDefault(); chatMenu(c, e.clientX, e.clientY); });
    longPress(row, (x, y) => chatMenu(c, x, y));
  }
  return row;
}

function renderList() {
  const box = $('#chats');
  if (!box) return;
  renderFolders();
  const q = S.filter.toLowerCase().replace(/^@/, '');
  const folder = FOLDERS.find(([k]) => k === S.folder)?.[2] || (() => true);
  const chats = sortedChats().filter((c) => (q ? (chatTitle(c).toLowerCase().includes(q) || (peerOf(c)?.username || c.handle || '').toLowerCase().includes(q)) : folder(c)));
  const rows = chats.map((c) => chatRow(c));
  if (q) {
    const knownPeers = new Set(chats.map((c) => peerOf(c)?.id).filter(Boolean));
    const knownChats = new Set(chats.map((c) => c.id));
    const people = globalResults.users.filter((u) => !knownPeers.has(u.id));
    const channels = globalResults.channels.filter((c) => !knownChats.has(c.id));
    if (people.length || channels.length) rows.push(h('div.section-title', 'Глобальный поиск'));
    rows.push(...channels.map((c) => chatRow(c, { sub: `@${c.handle}, ${c.memberCount} ${plural(c.memberCount, ['подписчик', 'подписчика', 'подписчиков'])}`, onclick: () => openPreview(c) })));
    rows.push(...people.map((u) => h('button.row', { onclick: () => startDirect(u.id) },
      userAv(u, '', { dot: true }),
      h('div.body', h('div.top', h('span.name', u.name)), h('div.bottom', h('span.preview', '@' + u.username))))));
  }
  if (!rows.length) {
    rows.push(h('div.empty', h('div.big', q ? '🔍' : '🧭'),
      q ? 'Ничего не найдено' : h('div', 'Здесь пока пусто.', h('br'), 'Нажмите ✏️, чтобы найти собеседника по имени пользователя.')));
  }
  box.replaceChildren(...rows);
  const total = sortedChats().filter((c) => !c.muted).reduce((s, c) => s + c.unread, 0);
  document.title = total ? `(${total}) Компас` : 'Компас';
}

function chatMenu(c, x, y) {
  const items = [
    [c.pinned ? 'unpin' : 'pin', c.pinned ? 'Открепить' : 'Закрепить', () => chatSettings(c, { pinned: !c.pinned })],
    c.type !== 'saved' ? [c.muted ? 'unmute' : 'mute', c.muted ? 'Включить уведомления' : 'Без звука', () => chatSettings(c, { muted: !c.muted })] : null,
    c.unread ? ['checks', 'Отметить прочитанным', () => markRead(c.id, c.lastMessage?.id || 0, true)] : null,
    c.type === 'group' ? ['leave', 'Покинуть группу', () => leave(c), 'danger'] : null,
    c.type === 'channel' && c.role !== 'owner' ? ['leave', 'Покинуть канал', () => leave(c), 'danger'] : null,
  ];
  popupMenu(items.filter(Boolean), x, y);
}

async function chatSettings(c, body) {
  try { putChat(await api('POST', `/api/chats/${c.id}/settings`, body)); renderList(); renderHeader(); } catch (err) { toast(err.message); }
}

async function leave(c) {
  const what = c.type === 'channel' ? 'канал' : 'группу';
  if (!confirm(`Покинуть ${what} «${c.title}»?`)) return;
  try {
    await api('POST', `/api/chats/${c.id}/leave`);
    S.chats.delete(c.id);
    if (S.current === c.id) closeChat();
    renderList();
  } catch (err) { toast(err.message); }
}

// ================= боковое меню =================
function openDrawer() {
  const me = user(S.me.id);
  const close = () => { bg.remove(); dr.remove(); };
  const dark = document.documentElement.dataset.theme === 'dark' || (!document.documentElement.dataset.theme && matchMedia('(prefers-color-scheme: dark)').matches);
  const item = (ic, label, fn) => h('button.drawer-item', { onclick: () => { close(); fn(); } }, h('span', { html: I[ic], style: 'display:contents' }), label);
  const bg = h('div.drawer-bg', { onclick: close });
  const dr = h('nav.drawer',
    h('div.drawer-head',
      h('button.icon-btn.night', { title: dark ? 'Дневной режим' : 'Ночной режим', onclick: () => { LS.set('theme', dark ? 'light' : 'dark'); applyTheme(); close(); } }, icon(dark ? 'sun' : 'moon')),
      userAv(me), h('div.n', me.name), h('div.u', '@' + me.username)),
    item('group', 'Создать группу', newGroup),
    item('channel', 'Создать канал', newChannel),
    item('bookmark', 'Избранное', openSaved),
    item('settings', 'Настройки', openSettings),
    h('div.drawer-foot', 'Компас — мессенджер', h('br'), serverBase().replace(/^https?:\/\//, '')));
  document.body.append(bg, dr);
}

async function openSaved() {
  try { const c = putChat(await api('POST', '/api/chats/saved')); renderList(); openChat(c.id); } catch (err) { toast(err.message); }
}

// ================= окно чата =================
function renderPane() {
  const pane = $('#pane');
  if (!pane) return;
  $('#main').classList.toggle('in-chat', !!S.current);
  if (!S.current) {
    pane.replaceChildren(h('div.wall'), h('div.pane-empty', h('span', 'Выберите, кому хотели бы написать')));
    return;
  }
  const input = h('textarea#input', {
    rows: 1, placeholder: 'Сообщение', maxlength: 4000, enterkeyhint: 'enter',
    oninput: () => { autosize(input); sendTyping(); saveDraft(); updateSendButton(); },
    onkeydown: (e) => {
      if (e.key === 'Enter' && !e.shiftKey && !e.isComposing && !coarse()) { e.preventDefault(); submit(); }
      if (e.key === 'Escape') { cancelReplyEdit(); }
      if (e.key === 'ArrowUp' && !input.value) { const last = [...(S.messages.get(S.current) || [])].reverse().find((x) => x.userId === S.me.id && x.kind === 'text' && !x.deleted && !x.forward); if (last) { e.preventDefault(); startEdit(last); } }
    },
    onpaste: (e) => { const f = [...(e.clipboardData?.files || [])].find((x) => x.type.startsWith('image/')); if (f) { e.preventDefault(); sendImage(f); } },
  });
  const file = h('input', { type: 'file', accept: 'image/*', class: 'hidden', onchange: (e) => { const f = e.target.files[0]; e.target.value = ''; if (f) sendImage(f); } });
  const messages = h('div.messages#messages', { onscroll: onMessagesScroll });
  messages.addEventListener('dragover', (e) => e.preventDefault());
  messages.addEventListener('drop', (e) => { e.preventDefault(); const f = [...e.dataTransfer.files].find((x) => x.type.startsWith('image/')); if (f) sendImage(f); });

  pane.replaceChildren(
    h('div.wall'),
    h('div.topbar',
      h('button.icon-btn.back', { title: 'Назад', onclick: () => history.state?.chat ? history.back() : closeChat() }, icon('back')),
      h('button.chat-head#chat-head', { onclick: openChatInfo }),
      h('button.icon-btn', { title: 'Ещё', onclick: (e) => headerMenu(e) }, icon('dots'))),
    h('div#pinned-bar'),
    messages,
    h('div.composer-wrap',
      h('button.scroll-down.gone#scroll-down', { title: 'Вниз', onclick: scrollToBottom }, icon('down')),
      h('div.composer-col#composer-normal',
        h('div.composer-box',
          h('div.composer-bar#composer-bar'),
          h('div.composer#composer-input',
            h('button.icon-btn', { title: 'Эмодзи', onclick: toggleEmoji }, icon('smile')),
            input,
            h('button.icon-btn#attach', { title: 'Прикрепить фото', onclick: () => file.click() }, icon('clip')), file),
          h('div.recording.hidden#recording',
            h('span.dot'), h('span.tm#rec-time', '0:00'),
            h('button.cancel', { onclick: () => stopRecording(false) }, 'Отмена'))),
        h('button.round-btn#send-btn', { onclick: onSendButton })),
      h('div.composer-col.hidden#composer-alt')),
  );
  input.value = LS.get('draft.' + S.current, '') || '';
  autosize(input);
  renderHeader();
  renderPinned();
  renderComposerBar();
  renderComposerMode();
  updateSendButton();
  renderMessages({ toBottom: true });
}

function renderHeader() {
  const head = $('#chat-head');
  const chat = S.chats.get(S.current);
  if (!head || !chat) return;
  const peer = peerOf(chat);
  const typing = typingText(chat.id);
  let status;
  if (!S.online) status = h('div.s', 'соединение…');
  else if (typing) status = h('div.s.on', typing);
  else if (chat.type === 'saved') status = null;
  else if (peer) status = h('div.s', { class: peer.online ? 'on' : '' }, lastSeen(peer));
  else if (chat.type === 'channel') status = h('div.s', `${chat.memberCount} ${plural(chat.memberCount, ['подписчик', 'подписчика', 'подписчиков'])}`);
  else {
    const n = chat.members.length;
    const online = chat.members.filter((m) => m.id === S.me.id || user(m.id).online).length;
    status = h('div.s', `${n} ${plural(n, ['участник', 'участника', 'участников'])}${online > 1 ? `, ${online} в сети` : ''}`);
  }
  head.replaceChildren(chatAv(chat, 'sm'),
    h('div.t', h('div.n', chatTitle(chat), chat.muted ? h('span', { html: I.mutedSmall, style: 'display:contents' }) : null), status));
}

function renderPinned() {
  const box = $('#pinned-bar');
  const chat = S.chats.get(S.current);
  if (!box || !chat) return;
  const p = chat.pinnedMessage;
  if (!p) return box.replaceChildren();
  const canUnpin = chat.joined && (chat.type !== 'channel' || chat.role === 'owner' || chat.role === 'admin');
  box.replaceChildren(h('div.pinned-bar', { onclick: () => jumpTo(p.id) },
    h('span.ln'),
    h('div.t', h('b', 'Закреплённое сообщение'), h('span', preview(p))),
    canUnpin ? h('button.icon-btn', { title: 'Открепить', onclick: (e) => { e.stopPropagation(); pinMessage(null); } }, icon('close')) : null));
}

/** Нижняя панель: поле ввода, «Подписаться» или «Без звука» (для читателей канала). */
function renderComposerMode() {
  const chat = S.chats.get(S.current);
  const normal = $('#composer-normal'); const alt = $('#composer-alt');
  if (!chat || !normal) return;
  if (chat.canPost) { normal.classList.remove('hidden'); alt.classList.add('hidden'); return; }
  normal.classList.add('hidden'); alt.classList.remove('hidden');
  if (!chat.joined) alt.replaceChildren(h('button.wide-btn', { onclick: () => joinChannel(chat) }, 'Подписаться'));
  else alt.replaceChildren(h('button.wide-btn', { onclick: () => chatSettings(chat, { muted: !chat.muted }) }, chat.muted ? 'Включить звук' : 'Выключить звук'));
}

function headerMenu(e) {
  const chat = S.chats.get(S.current);
  if (!chat) return;
  const r = e.currentTarget.getBoundingClientRect();
  const admin = chat.role === 'owner' || chat.role === 'admin';
  const items = [
    ['info', chat.type === 'direct' ? 'Профиль' : chat.type === 'saved' ? 'Об Избранном' : 'Информация', openChatInfo],
    chat.joined && chat.type !== 'saved' ? [chat.muted ? 'unmute' : 'mute', chat.muted ? 'Включить уведомления' : 'Без звука', () => chatSettings(chat, { muted: !chat.muted })] : null,
    chat.joined ? [chat.pinned ? 'unpin' : 'pin', chat.pinned ? 'Открепить чат' : 'Закрепить чат', () => chatSettings(chat, { pinned: !chat.pinned })] : null,
    chat.type === 'group' ? ['addUser', 'Добавить участников', () => addMembers(chat)] : null,
    (chat.type === 'group' || (chat.type === 'channel' && admin)) ? ['edit', 'Изменить', () => editChat(chat)] : null,
    chat.type === 'group' ? ['leave', 'Покинуть группу', () => leave(chat), 'danger'] : null,
    chat.type === 'channel' && chat.joined && chat.role !== 'owner' ? ['leave', 'Покинуть канал', () => leave(chat), 'danger'] : null,
  ];
  popupMenu(items.filter(Boolean), r.right - 220, r.bottom + 4);
}

async function openChat(id) {
  id = Number(id);
  if (!S.chats.has(id)) {
    try { putChat(await api('GET', `/api/chats/${id}`)); } catch (err) { return toast(err.message); }
  }
  const was = S.current;
  stopRecording(false);
  S.current = id; S.reply = null; S.editing = null;
  const chat = S.chats.get(id);
  if (chat.unread && chat.joined) S.unreadMark.set(id, chat.lastReadId); else S.unreadMark.delete(id);
  if (!S.messages.has(id)) S.messages.set(id, []);
  closeEmoji();
  renderPane();
  renderList();
  if (!was || !history.state?.chat) history.pushState({ chat: id }, '');
  else history.replaceState({ chat: id }, '');
  try {
    const msgs = await api('GET', `/api/chats/${id}/messages`);
    if (S.current !== id) return;
    S.messages.set(id, msgs);
    S.hasMore.set(id, msgs.length >= 50);
    renderMessages({ toUnread: true });
    if (nearBottom()) { const last = msgs.at(-1); if (last) markRead(id, last.id); }
  } catch (err) { toast(err.message); }
  if (!coarse()) $('#input')?.focus();
}

async function openPreview(c) {
  S.chats.set(c.id, c.joined ? putChat(c) : c);
  openChat(c.id);
  $('#search').value = ''; S.filter = ''; globalResults = { users: [], channels: [] };
  renderList();
}

async function openHandle(handle) {
  try {
    const res = await api('GET', '/api/search?q=' + encodeURIComponent(handle));
    const u = res.users.find((x) => x.username.toLowerCase() === handle.toLowerCase());
    if (u) return openUserProfile(remember(u));
    if (handle.toLowerCase() === S.me.username.toLowerCase()) return openSettings();
    const c = res.channels.find((x) => x.handle?.toLowerCase() === handle.toLowerCase());
    if (c) return openPreview(c);
    toast('Пользователь не найден');
  } catch (err) { toast(err.message); }
}

async function joinChannel(chat) {
  try {
    putChat(await api('POST', `/api/chats/${chat.id}/join`));
    renderList(); renderHeader(); renderComposerMode();
    toast('Вы подписались на канал');
  } catch (err) { toast(err.message); }
}

function closeChat() {
  stopRecording(false);
  const chat = S.chats.get(S.current);
  if (chat && !chat.joined) S.chats.delete(chat.id);
  S.current = null; S.reply = null; S.editing = null;
  closeEmoji();
  renderPane(); renderList();
}

window.addEventListener('popstate', () => {
  const open = document.querySelector('.overlay, .drawer');
  if (open) { document.querySelectorAll('.overlay, .drawer, .drawer-bg').forEach((e) => e.remove()); if (S.current) history.pushState({ chat: S.current }, ''); return; }
  if (S.current) closeChat();
});

let readTimer;
function markRead(chatId, messageId, force = false) {
  const chat = S.chats.get(chatId);
  if (!chat?.joined || (!force && messageId <= chat.lastReadId)) return;
  chat.lastReadId = Math.max(chat.lastReadId, messageId);
  chat.unread = 0;
  recountUnread(chat);
  renderList();
  renderScrollDown();
  clearTimeout(readTimer);
  readTimer = setTimeout(() => api('POST', `/api/chats/${chatId}/read`, { messageId: messageId || undefined }).catch(() => {}), 250);
}

function markVisibleRead() {
  if (!S.current || !isVisible() || !nearBottom()) return;
  const last = S.messages.get(S.current)?.filter((m) => m.id > 0).at(-1);
  if (last) markRead(S.current, last.id);
}
document.addEventListener('visibilitychange', markVisibleRead);
window.addEventListener('focus', markVisibleRead);

function nearBottom() {
  const box = $('#messages');
  return !box || box.scrollHeight - box.scrollTop - box.clientHeight < 120;
}

function scrollToBottom() {
  const box = $('#messages');
  if (box) box.scrollTo({ top: box.scrollHeight, behavior: 'smooth' });
}

function renderScrollDown() {
  const btn = $('#scroll-down');
  if (!btn) return;
  const chat = S.chats.get(S.current);
  btn.classList.toggle('gone', nearBottom());
  btn.replaceChildren(icon('down'), chat?.unread ? h('span.badge', chat.unread) : null);
}

let loadingOlder = false;
async function onMessagesScroll(e) {
  renderScrollDown();
  markVisibleRead();
  const box = e.target;
  if (box.scrollTop > 200 || loadingOlder || !S.hasMore.get(S.current)) return;
  const id = S.current;
  const list = S.messages.get(id);
  if (!list?.length) return;
  loadingOlder = true;
  try {
    const older = await api('GET', `/api/chats/${id}/messages?before=${list[0].id}`);
    if (S.current !== id) return;
    S.hasMore.set(id, older.length >= 50);
    const prevHeight = box.scrollHeight;
    const prevTop = box.scrollTop;
    S.messages.set(id, [...older, ...list]);
    renderMessages();
    box.scrollTop = box.scrollHeight - prevHeight + prevTop;
  } catch { /* офлайн */ } finally { loadingOlder = false; }
}

function renderMessages({ toBottom = false, keepBottom = false, toUnread = false } = {}) {
  const box = $('#messages');
  const chat = S.chats.get(S.current);
  if (!box || !chat) return;
  const atBottom = nearBottom();
  const prevTop = box.scrollTop;
  const list = S.messages.get(S.current) || [];
  const mark = S.unreadMark.get(S.current);
  const col = h('div.col');
  let lastDay = '';
  let sep = null;
  const unknown = [];
  list.forEach((m, i) => {
    const day = new Date(m.createdAt).toDateString();
    if (day !== lastDay) { col.append(h('div.day', dayLabel(m.createdAt))); lastDay = day; }
    if (mark !== undefined && !sep && m.id > mark && m.userId !== S.me.id && m.kind !== 'system') {
      sep = h('div.unread-sep', 'Непрочитанные сообщения');
      col.append(sep);
    }
    if (!S.users.has(m.userId)) unknown.push(m.userId);
    if (m.kind === 'system') { col.append(h('div.sys', systemText(m))); return; }
    const same = (x) => x && x.kind !== 'system' && x.userId === m.userId && new Date(x.createdAt).toDateString() === day;
    const next = list[i + 1]; const prev = list[i - 1];
    const tail = !same(next) || next.createdAt - m.createdAt > 10 * 60e3;
    const head = !same(prev) || m.createdAt - prev.createdAt > 10 * 60e3;
    col.append(messageEl(m, chat, { tail, head }));
  });
  if (!list.length) {
    col.append(h('div.sys', { style: 'margin-top:40vh' }, chat.type === 'saved' ? 'Сохраняйте здесь заметки, ссылки и пересланные сообщения' : 'Сообщений пока нет. Напишите первым!'));
  }
  box.replaceChildren(h('div.spacer'), col);
  if (unknown.length) ensureUsers([...new Set(unknown)]);
  if (toUnread && sep) sep.scrollIntoView({ block: 'center' });
  else if (toBottom || toUnread || (keepBottom && atBottom)) box.scrollTop = box.scrollHeight;
  else box.scrollTop = prevTop;
  renderScrollDown();
}

function messageEl(m, chat, { tail, head }) {
  const isChannel = chat.type === 'channel';
  const mine = m.userId === S.me.id && !isChannel;
  const u = user(m.userId);
  const big = !m.deleted && m.kind === 'text' && !m.replyTo && !m.forward && onlyEmoji(m.text);
  const media = !m.deleted && m.kind === 'image';
  const bubble = h('div.bubble', { class: [m.deleted ? 'deleted' : '', big ? 'big-emoji' : '', media ? 'media' : '', media && !m.text && !m.reactions?.length ? 'only' : ''].join(' ') });

  if (isChannel && head) bubble.append(h('div.author', { style: `color:${NAME_COLORS[chat.id % NAME_COLORS.length]}` }, chat.title));
  else if (chat.type === 'group' && !mine && head) {
    bubble.append(h('div.author', { style: `color:${NAME_COLORS[(u.color ?? 0) % NAME_COLORS.length]}`, onclick: () => openUserProfile(u) }, u.name));
  }
  if (m.forward) {
    bubble.append(h('div.fwd', { onclick: () => openForwardSource(m.forward) }, 'Переслано от', h('b', m.forward.name)));
  }
  if (m.replyTo) bubble.append(quoteEl(m.replyTo, () => jumpTo(m.replyTo.id)));

  if (media) {
    const img = h('img.photo', { src: fileUrl(m.file), alt: 'Фото', loading: 'lazy', onclick: (e) => { e.stopPropagation(); viewImage(fileUrl(m.file)); } });
    img.addEventListener('load', () => { const box = $('#messages'); if (box && box.scrollHeight - box.scrollTop - box.clientHeight < img.height + 160) box.scrollTop = box.scrollHeight; }, { once: true });
    bubble.append(img);
  }
  if (m.kind === 'voice' && !m.deleted) bubble.append(voiceEl(m));

  const read = mine && (chat.readUpTo >= m.id);
  const meta = h('span.meta',
    m.editedAt && !m.deleted ? 'изм. ' : '',
    hm(m.createdAt),
    mine && !m.deleted ? h('span', { style: 'display:flex', html: m.pending ? I.clock : read ? I.checks : I.check }) : null);
  if (m.kind !== 'voice' || m.deleted) bubble.append(h('div.text', m.deleted ? 'Сообщение удалено' : linkify(m.text), meta));
  else bubble.append(meta);

  if (m.reactions?.length) {
    bubble.append(h('div.reactions', m.reactions.map((r) => {
      const minePick = r.userIds.includes(S.me.id);
      const showAvatars = r.userIds.length <= 3 && chat.type !== 'channel';
      return h('button.reaction', { class: minePick ? 'mine' : '', onclick: (e) => { e.stopPropagation(); react(m, r.emoji); } },
        h('span.e', r.emoji),
        showAvatars ? r.userIds.map((id) => userAv(user(id), 'xs')) : r.userIds.length);
    })));
  }

  const el = h('div.msg', { class: [mine ? 'mine' : '', tail ? 'tail' : '', head ? 'head' : ''].join(' '), 'data-id': m.id },
    chat.type === 'group' && !mine ? (() => { const a = userAv(u, 'sm'); a.style.cursor = 'pointer'; a.onclick = () => openUserProfile(u); return a; })() : null,
    bubble, h('span.reply-hint', { html: I.reply }));
  if (!m.deleted && !m.pending) attachGestures(el, m);
  return el;
}

function attachGestures(el, m) {
  el.addEventListener('contextmenu', (e) => { e.preventDefault(); messageMenu(m, e.clientX, e.clientY); });
  // Долгое нажатие — меню, свайп влево — ответить, двойное касание — 👍
  let timer; let sx = 0; let sy = 0; let dx = 0; let swiping = false;
  const hint = el.querySelector('.reply-hint');
  el.addEventListener('touchstart', (e) => {
    const t = e.touches[0]; sx = t.clientX; sy = t.clientY; dx = 0; swiping = false;
    timer = setTimeout(() => { navigator.vibrate?.(12); messageMenu(m, sx, sy); }, 450);
  }, { passive: true });
  el.addEventListener('touchmove', (e) => {
    const t = e.touches[0];
    const mx = t.clientX - sx; const my = t.clientY - sy;
    if (Math.abs(mx) > 8 || Math.abs(my) > 8) clearTimeout(timer);
    if (!swiping && mx < -12 && Math.abs(mx) > Math.abs(my) * 1.5) swiping = true;
    if (swiping && S.chats.get(m.chatId)?.canPost) {
      dx = Math.max(-80, Math.min(0, mx));
      el.style.transform = `translateX(${dx}px)`; el.style.transition = 'none';
      hint.style.opacity = Math.min(1, -dx / 60);
    }
  }, { passive: true });
  const end = () => {
    clearTimeout(timer);
    if (swiping) {
      el.style.transition = ''; el.style.transform = ''; hint.style.opacity = 0;
      if (dx <= -60) { navigator.vibrate?.(8); setReply(m); }
    }
    swiping = false;
  };
  el.addEventListener('touchend', end, { passive: true });
  el.addEventListener('touchcancel', end, { passive: true });
  let lastTap = 0;
  el.addEventListener('click', (e) => {
    if (e.target.closest('a,img,button,.wave')) return;
    if (Date.now() - lastTap < 320) { lastTap = 0; window.getSelection()?.removeAllRanges(); react(m, '👍'); } else lastTap = Date.now();
  });
}

// ---------- голосовые ----------
function waveform(id, n = 34) {
  let x = id * 9301 + 49297;
  return Array.from({ length: n }, (_, i) => { x = (x * 9301 + 49297) % 233280; const r = x / 233280; return 0.25 + 0.75 * Math.abs(Math.sin(i * 0.55 + r * 2)) * (0.5 + r / 2); });
}

function voiceEl(m) {
  const playing = S.audio?.id === m.id;
  const progress = playing && S.audio.el.duration ? S.audio.el.currentTime / S.audio.el.duration : 0;
  const bars = waveform(m.id).map((v, i, arr) => h('i', { class: i / arr.length < progress ? 'p' : '', style: `height:${Math.round(v * 24)}px` }));
  const wave = h('div.wave', {
    onclick: (e) => {
      const r = e.currentTarget.getBoundingClientRect();
      playVoice(m, (e.clientX - r.left) / r.width);
    },
  }, bars);
  const cur = playing ? S.audio.el.currentTime : m.duration || 0;
  return h('div.voice', { 'data-voice': m.id },
    h('button.play', { title: playing && !S.audio.el.paused ? 'Пауза' : 'Слушать', onclick: (e) => { e.stopPropagation(); playVoice(m); }, html: playing && !S.audio.el.paused ? I.pause : I.play }),
    h('div', wave, h('div.dur', mmss(cur))));
}

function playVoice(m, seek) {
  if (S.audio?.id === m.id) {
    const a = S.audio.el;
    if (seek !== undefined && a.duration) { a.currentTime = seek * a.duration; if (a.paused) a.play(); }
    else if (a.paused) a.play(); else a.pause();
    return;
  }
  S.audio?.el.pause();
  const a = new Audio(fileUrl(m.file));
  S.audio = { id: m.id, el: a };
  const update = () => {
    const box = document.querySelector(`[data-voice="${m.id}"]`);
    if (!box) return;
    const chat = S.chats.get(S.current);
    if (chat) box.replaceWith(voiceEl(m));
  };
  a.addEventListener('timeupdate', update);
  a.addEventListener('play', update);
  a.addEventListener('pause', update);
  a.addEventListener('loadedmetadata', () => { if (seek !== undefined && isFinite(a.duration)) a.currentTime = seek * a.duration; });
  a.addEventListener('ended', () => {
    S.audio = null; update();
    // Как в популярных мессенджерах — следующее голосовое играет автоматически
    const list = S.messages.get(m.chatId) || [];
    const next = list.find((x) => x.id > m.id && x.kind === 'voice' && !x.deleted);
    if (next && S.current === m.chatId) playVoice(next);
  });
  a.play().catch(() => toast('Не удалось воспроизвести'));
}

// ---------- запись голоса ----------
async function startRecording() {
  if (!navigator.mediaDevices?.getUserMedia || !window.MediaRecorder) return toast('Запись голоса не поддерживается на этом устройстве');
  let stream;
  try { stream = await navigator.mediaDevices.getUserMedia({ audio: true }); } catch { return toast('Нет доступа к микрофону'); }
  const type = ['audio/webm;codecs=opus', 'audio/mp4', 'audio/ogg;codecs=opus', 'audio/webm'].find((t) => MediaRecorder.isTypeSupported?.(t));
  const rec = new MediaRecorder(stream, type ? { mimeType: type } : undefined);
  const chunks = [];
  rec.ondataavailable = (e) => { if (e.data.size) chunks.push(e.data); };
  rec.start(250);
  const chatId = S.current;
  S.recording = { rec, stream, chunks, chatId, started: Date.now(), timer: setInterval(() => { const t = $('#rec-time'); if (t) t.textContent = mmss((Date.now() - S.recording.started) / 1000); }, 200) };
  $('#composer-input').classList.add('hidden');
  $('#composer-bar').classList.add('hidden');
  $('#recording').classList.remove('hidden');
  updateSendButton();
}

function stopRecording(send) {
  const r = S.recording;
  if (!r) return;
  S.recording = null;
  clearInterval(r.timer);
  $('#composer-input')?.classList.remove('hidden');
  $('#composer-bar')?.classList.remove('hidden');
  $('#recording')?.classList.add('hidden');
  updateSendButton();
  const duration = (Date.now() - r.started) / 1000;
  r.rec.onstop = async () => {
    r.stream.getTracks().forEach((t) => t.stop());
    if (!send) return;
    if (duration < 0.8) return toast('Удерживайте запись подольше');
    const blob = new Blob(r.chunks, { type: (r.rec.mimeType || 'audio/webm').split(';')[0] });
    const replyTo = S.reply?.id;
    S.reply = null; renderComposerBar();
    try {
      const file = await upload(blob);
      await api('POST', `/api/chats/${r.chatId}/messages`, { file, duration, replyTo });
    } catch (err) { toast(err.message); }
  };
  try { r.rec.stop(); } catch { r.stream.getTracks().forEach((t) => t.stop()); }
}

// ---------- ответ, правка, меню сообщения ----------
function quoteEl(r, onclick) {
  const t = r.deleted ? 'Сообщение удалено' : r.kind === 'image' ? '🖼 ' + (r.text || 'Фотография') : r.kind === 'voice' ? '🎤 Голосовое сообщение' : r.text;
  const chat = S.chats.get(S.current);
  const name = chat?.type === 'channel' ? chat.title : r.userId === S.me.id ? 'Вы' : user(r.userId).name;
  return h('button.quote', { type: 'button', onclick: (e) => { e.stopPropagation(); onclick?.(); } }, h('div.qn', name), h('div.qt', t));
}

function jumpTo(id) {
  const el = $(`.msg[data-id="${id}"]`);
  if (!el) return toast('Сообщение выше в истории');
  el.scrollIntoView({ block: 'center', behavior: 'smooth' });
  el.classList.remove('flash'); void el.offsetWidth; el.classList.add('flash');
}

function messageMenu(m, x, y) {
  const chat = S.chats.get(m.chatId);
  const mine = m.userId === S.me.id;
  const admin = chat.role === 'owner' || chat.role === 'admin';
  const canPin = chat.joined && (chat.type !== 'channel' || admin);
  const isPinned = chat.pinnedMessage?.id === m.id;
  const items = [
    chat.canPost ? ['reply', 'Ответить', () => setReply(m)] : null,
    m.text ? ['copy', 'Копировать текст', () => copyText(m.text)] : null,
    ['forward', 'Переслать', () => forwardDialog(m)],
    canPin ? [isPinned ? 'unpin' : 'pin', isPinned ? 'Открепить' : 'Закрепить', () => pinMessage(isPinned ? null : m.id)] : null,
    mine && m.kind !== 'voice' && !m.forward ? ['edit', 'Изменить', () => startEdit(m)] : null,
    mine || ((chat.type === 'group' || chat.type === 'channel') && admin) ? ['trash', 'Удалить', () => deleteMessage(m), 'danger'] : null,
  ].filter(Boolean);
  let strip = null;
  if (chat.joined) {
    const myReaction = m.reactions?.find((r) => r.userIds.includes(S.me.id))?.emoji;
    strip = h('div.react-strip', REACTIONS.map((e) => h('button', { class: e === myReaction ? 'on' : '', onclick: () => { closeMenu(); react(m, e); } }, e)));
  }
  popupMenu(items, x, y, strip);
}

let closeMenu = () => {};
function popupMenu(items, x, y, top = null) {
  closeMenu();
  const close = () => { bg.remove(); menu.remove(); closeMenu = () => {}; };
  const bg = h('div.menu-bg', { onclick: close, oncontextmenu: (e) => { e.preventDefault(); close(); } });
  const menu = h('div.menu', top, items.map(([ic, label, fn, cls]) => h('button', { class: cls || '', onclick: () => { close(); fn(); } },
    h('span', { html: I[ic] || '', style: 'display:contents' }), label)));
  document.body.append(bg, menu);
  closeMenu = close;
  const r = menu.getBoundingClientRect();
  menu.style.left = Math.max(8, Math.min(x, innerWidth - r.width - 8)) + 'px';
  menu.style.top = Math.max(8, Math.min(y, innerHeight - r.height - 8)) + 'px';
}

function longPress(el, fn) {
  let timer; let sx; let sy;
  el.addEventListener('touchstart', (e) => { const t = e.touches[0]; sx = t.clientX; sy = t.clientY; timer = setTimeout(() => { navigator.vibrate?.(12); fn(sx, sy); }, 500); }, { passive: true });
  el.addEventListener('touchmove', (e) => { const t = e.touches[0]; if (Math.hypot(t.clientX - sx, t.clientY - sy) > 10) clearTimeout(timer); }, { passive: true });
  for (const ev of ['touchend', 'touchcancel']) el.addEventListener(ev, () => clearTimeout(timer), { passive: true });
}

async function react(m, emoji) {
  const chat = S.chats.get(m.chatId);
  if (!chat?.joined || m.id < 0) return;
  // Оптимистично: одна реакция на человека, повторное нажатие снимает
  const before = m.reactions || [];
  const had = before.find((r) => r.userIds.includes(S.me.id))?.emoji;
  let next = before.map((r) => ({ ...r, userIds: r.userIds.filter((id) => id !== S.me.id) })).filter((r) => r.userIds.length);
  if (had !== emoji) {
    const r = next.find((x) => x.emoji === emoji);
    if (r) r.userIds.push(S.me.id); else next.push({ emoji, userIds: [S.me.id] });
  }
  m.reactions = next;
  renderMessages({ keepBottom: true });
  try { m.reactions = (await api('POST', `/api/messages/${m.id}/react`, { emoji })).reactions; } catch (err) { m.reactions = before; toast(err.message); }
  renderMessages({ keepBottom: true });
}

async function pinMessage(messageId) {
  try {
    const c = putChat(await api('POST', `/api/chats/${S.current}/pin`, { messageId }));
    renderPinned();
    if (messageId) toast('Сообщение закреплено');
    return c;
  } catch (err) { toast(err.message); }
}

async function copyText(t) {
  try { await navigator.clipboard.writeText(t); toast('Текст скопирован'); } catch { toast('Не удалось скопировать'); }
}

function setReply(m) {
  if (!S.chats.get(m.chatId)?.canPost) return;
  S.editing = null; S.reply = m;
  renderComposerBar();
  $('#input')?.focus();
}

function startEdit(m) {
  S.reply = null; S.editing = m;
  const input = $('#input');
  input.value = m.text;
  autosize(input);
  renderComposerBar(); updateSendButton();
  input.focus();
}

function cancelReplyEdit() {
  if (S.editing) { $('#input').value = LS.get('draft.' + S.current, '') || ''; autosize($('#input')); }
  S.reply = null; S.editing = null; renderComposerBar(); updateSendButton();
}

function renderComposerBar() {
  const bar = $('#composer-bar');
  if (!bar) return;
  const cancel = h('button.icon-btn', { title: 'Отмена', onclick: cancelReplyEdit }, icon('close'));
  if (S.reply) bar.replaceChildren(h('div.reply-bar', h('span.ic', { html: I.reply }), quoteEl(S.reply, () => jumpTo(S.reply.id)), cancel));
  else if (S.editing) bar.replaceChildren(h('div.reply-bar', h('span.ic', { html: I.edit }), h('div.quote', h('div.qn', 'Редактирование'), h('div.qt', S.editing.text)), cancel));
  else bar.replaceChildren();
}

/** Кнопка справа: микрофон, когда поле пустое; «отправить», когда есть текст или идёт запись. */
function updateSendButton() {
  const btn = $('#send-btn');
  if (!btn) return;
  const hasText = !!$('#input')?.value.trim();
  const mode = S.recording ? 'rec' : hasText || S.editing ? 'send' : 'mic';
  btn.className = 'round-btn ' + (mode === 'mic' ? '' : mode === 'rec' ? 'rec' : 'send');
  btn.innerHTML = mode === 'mic' ? I.mic : mode === 'send' && S.editing ? I.check : I.send;
  btn.title = mode === 'mic' ? 'Записать голосовое' : 'Отправить';
}

function onSendButton() {
  if (S.recording) return stopRecording(true);
  if ($('#input').value.trim() || S.editing) return submit();
  startRecording();
}

async function deleteMessage(m) {
  if (!confirm('Удалить сообщение у всех?')) return;
  try { await api('DELETE', `/api/messages/${m.id}`); } catch (err) { toast(err.message); }
}

function autosize(el) {
  el.style.height = 'auto';
  el.style.height = Math.min(el.scrollHeight, 180) + 'px';
}

let typingSent = 0;
function sendTyping() {
  if (Date.now() - typingSent < 3000 || !$('#input').value) return;
  typingSent = Date.now();
  ws.send({ type: 'typing', chatId: S.current });
}

function saveDraft() {
  if (!S.editing) LS.set('draft.' + S.current, $('#input').value || null);
}

// ---------- эмодзи ----------
function toggleEmoji(e) {
  e?.stopPropagation();
  if ($('.emoji-panel')) return closeEmoji();
  const input = $('#input');
  const panel = h('div.emoji-panel', EMOJI.filter(Boolean).map((em) => h('button', {
    type: 'button',
    onclick: () => {
      const s = input.selectionStart ?? input.value.length; const en = input.selectionEnd ?? s;
      input.value = input.value.slice(0, s) + em + input.value.slice(en);
      input.selectionStart = input.selectionEnd = s + em.length;
      autosize(input); saveDraft(); updateSendButton();
      if (!coarse()) input.focus();
    },
  }, em)));
  $('.composer-wrap').append(panel);
  setTimeout(() => document.addEventListener('click', outsideEmoji));
}
function outsideEmoji(e) { if (!e.target.closest('.emoji-panel')) closeEmoji(); }
function closeEmoji() { $('.emoji-panel')?.remove(); document.removeEventListener('click', outsideEmoji); }

// ---------- отправка ----------
let tempId = -1;
async function submit() {
  const input = $('#input');
  const text = input.value.trim();
  const chatId = S.current;
  closeEmoji();
  if (S.editing) {
    const m = S.editing;
    S.editing = null; input.value = LS.get('draft.' + chatId, '') || ''; autosize(input); renderComposerBar(); updateSendButton();
    if (text && text !== m.text) api('PATCH', `/api/messages/${m.id}`, { text }).catch((err) => toast(err.message));
    return;
  }
  if (!text) return;
  const replyTo = S.reply;
  S.reply = null; input.value = ''; autosize(input); renderComposerBar(); updateSendButton();
  LS.set('draft.' + chatId, null);
  typingSent = 0;
  // Оптимистичное сообщение с «часиками», пока сервер не подтвердит
  const temp = { id: tempId--, chatId, userId: S.me.id, kind: 'text', text, reactions: [],
    replyTo: replyTo ? { id: replyTo.id, userId: replyTo.userId, kind: replyTo.kind, text: replyTo.text } : null, createdAt: Date.now(), pending: true };
  S.messages.get(chatId)?.push(temp);
  S.unreadMark.delete(chatId);
  renderMessages({ toBottom: true });
  try {
    replaceTemp(chatId, temp.id, await api('POST', `/api/chats/${chatId}/messages`, { text, replyTo: replyTo?.id }));
  } catch (err) {
    replaceTemp(chatId, temp.id, null);
    if (S.current === chatId && !$('#input').value) { $('#input').value = text; autosize($('#input')); updateSendButton(); }
    toast(err.message);
  }
}

function replaceTemp(chatId, id, msg) {
  const list = S.messages.get(chatId);
  if (!list) return;
  const i = list.findIndex((x) => x.id === id);
  if (i < 0) return;
  if (!msg || list.some((x) => x.id === msg.id)) list.splice(i, 1);
  else list[i] = msg;
  if (S.current === chatId) renderMessages({ keepBottom: true });
}

async function sendImage(file) {
  const chatId = S.current;
  if (!S.chats.get(chatId)?.canPost) return;
  const replyTo = S.reply?.id;
  const caption = $('#input').value.trim();
  try {
    toast('Отправка фото…');
    const name = await upload(await compressImage(file));
    await api('POST', `/api/chats/${chatId}/messages`, { file: name, text: caption, replyTo });
    if (caption && S.current === chatId) { $('#input').value = ''; autosize($('#input')); LS.set('draft.' + chatId, null); updateSendButton(); }
    S.reply = null; renderComposerBar();
  } catch (err) { toast(err.message); }
}

async function compressImage(file, { max = 1920, square = false } = {}) {
  if (file.type === 'image/gif' && file.size < 8e6 && !square) return file;
  try {
    const bmp = await createImageBitmap(file, { imageOrientation: 'from-image' });
    let sx = 0; let sy = 0; let sw = bmp.width; let sh = bmp.height;
    if (square) { const s = Math.min(sw, sh); sx = (sw - s) / 2; sy = (sh - s) / 2; sw = sh = s; }
    const k = Math.min(1, max / Math.max(sw, sh));
    const c = document.createElement('canvas');
    c.width = Math.round(sw * k); c.height = Math.round(sh * k);
    const ctx = c.getContext('2d');
    ctx.fillStyle = '#fff'; // у JPEG нет прозрачности
    ctx.fillRect(0, 0, c.width, c.height);
    ctx.drawImage(bmp, sx, sy, sw, sh, 0, 0, c.width, c.height);
    return await new Promise((ok, fail) => c.toBlob((b) => (b ? ok(b) : fail(new Error('Не удалось обработать фото'))), 'image/jpeg', 0.86));
  } catch {
    if (['image/jpeg', 'image/png', 'image/webp'].includes(file.type) && !square) return file;
    throw new Error('Этот формат изображения не поддерживается');
  }
}

function pickImage() {
  return new Promise((ok) => {
    const input = h('input', { type: 'file', accept: 'image/*', onchange: () => ok(input.files[0] || null) });
    input.click();
  });
}

/** Выбор, квадратная обрезка и загрузка аватарки. Возвращает { name, url } (url — локальное превью). */
async function uploadAvatar() {
  const f = await pickImage();
  if (!f) return null;
  toast('Загрузка фото…');
  const blob = await compressImage(f, { max: 640, square: true });
  return { name: await upload(blob), url: URL.createObjectURL(blob) };
}

function viewImage(src) {
  const v = h('div.viewer', { onclick: () => v.remove() }, h('img', { src, alt: '' }));
  document.body.append(v);
}

// ================= окна =================
function sheet(title, content, { action, onAction, left } = {}) {
  const close = () => ov.remove();
  const btn = action ? h('button.btn', { onclick: () => onAction(close, btn) }, action) : null;
  const ov = h('div.overlay', { onclick: (e) => { if (e.target === ov) close(); } },
    h('div.sheet', { role: 'dialog', 'aria-label': title },
      h('div.topbar', h('button.icon-btn', { title: 'Закрыть', onclick: close }, icon('close')), h('h2', title), left || null),
      h('div.content', content),
      btn ? h('div.foot', btn) : null));
  document.body.append(ov);
  return { close, el: ov };
}

function peoplePicker({ multi, exclude = new Set(), onPick }) {
  const selected = new Map();
  const chips = h('div.chips');
  const results = h('div');
  const input = h('input', { type: 'search', placeholder: ' ', autocomplete: 'off', autocapitalize: 'off' });
  let timer; let last = [];
  const show = (list) => {
    last = list;
    const rows = list.filter((u) => !exclude.has(u.id) && u.id !== S.me.id).map((u) => h('button.row', {
      class: selected.has(u.id) ? 'sel' : '',
      onclick: () => {
        if (!multi) return onPick(u);
        selected.has(u.id) ? selected.delete(u.id) : selected.set(u.id, u);
        renderChips(); show(last);
      },
    }, userAv(u, 'sm', { dot: true }),
    h('div.body', h('div.top', h('span.name', u.name)), h('div.bottom', h('span.preview', u.online ? 'в сети' : '@' + u.username))),
    multi ? h('span.check', { html: selected.has(u.id) ? I.check : '' }) : null));
    results.replaceChildren(...(rows.length ? rows : [h('div.empty', { style: 'padding:20px' }, input.value.trim().length < 2 ? 'Введите имя или @username' : 'Никого не нашли')]));
  };
  const renderChips = () => chips.replaceChildren(...[...selected.values()].map((u) => h('span.chip', userAv(u, 'xs'), u.name.split(' ')[0])));
  input.addEventListener('input', () => {
    clearTimeout(timer);
    const q = input.value.trim();
    if (q.replace(/^@/, '').length < 2) return show([...S.users.values()].filter((u) => [...S.chats.values()].some((c) => c.type === 'direct' && peerOf(c)?.id === u.id)));
    timer = setTimeout(async () => {
      try { show((await api('GET', '/api/users/search?q=' + encodeURIComponent(q))).map(remember)); } catch (err) { toast(err.message); }
    }, 250);
  });
  setTimeout(() => { input.dispatchEvent(new Event('input')); if (!coarse()) input.focus(); });
  return { el: h('div', chips, h('label.field', input, h('span', 'Кого добавить?')), multi ? null : h('div.section-title', 'Контакты'), results), selected };
}

function newChat() {
  let s;
  const picker = peoplePicker({ multi: false, onPick: (u) => { s.close(); startDirect(u.id); } });
  const action = (ic, label, fn) => h('button.info-row', { style: 'color:var(--accent)', onclick: () => { s.close(); fn(); } }, h('span', { html: I[ic], style: 'display:contents' }), label);
  s = sheet('Новое сообщение', h('div', action('group', 'Создать группу', newGroup), action('channel', 'Создать канал', newChannel), action('bookmark', 'Избранное', openSaved), h('div.sep'), picker.el));
}

async function startDirect(userId) {
  try {
    const chat = putChat(await api('POST', '/api/chats/direct', { userId }));
    $('#search').value = ''; S.filter = ''; globalResults = { users: [], channels: [] };
    renderList();
    openChat(chat.id);
  } catch (err) { toast(err.message); }
}

function newGroup() {
  const picker = peoplePicker({ multi: true });
  let s = sheet('Добавить участников', picker.el, {
    action: 'Далее',
    onAction: (close) => {
      close();
      const title = h('input', { maxlength: 64, placeholder: ' ' });
      let avatar = null;
      const av = h('div.avatar.lg.editable', { style: '--c1:#72d5fd;--c2:#2a9ef1', html: I.group, onclick: async () => {
        try { const a = await uploadAvatar(); if (a) { avatar = a.name; av.replaceChildren(h('img', { src: a.url, alt: '' })); } } catch (err) { toast(err.message); }
      } });
      sheet('Новая группа', h('div', h('div.profile', av), h('label.field', title, h('span', 'Название группы')),
        h('div.section-title', `${picker.selected.size + 1} ${plural(picker.selected.size + 1, ['участник', 'участника', 'участников'])}`),
        [S.me, ...picker.selected.values()].map((u) => h('div.row', userAv(user(u.id), 'sm'), h('div.body', h('span.name', user(u.id).name))))), {
        action: 'Создать',
        onAction: async (close2, btn) => {
          if (!title.value.trim()) return toast('Введите название группы');
          btn.disabled = true;
          try {
            const chat = putChat(await api('POST', '/api/chats/group', { title: title.value, members: [...picker.selected.keys()], avatar }));
            close2(); renderList(); openChat(chat.id);
          } catch (err) { toast(err.message); btn.disabled = false; }
        },
      });
      setTimeout(() => title.focus());
    },
  });
  return s;
}

function newChannel() {
  const title = h('input', { maxlength: 64, placeholder: ' ' });
  const desc = h('textarea', { rows: 2, maxlength: 255, placeholder: ' ' });
  const handle = h('input', { maxlength: 32, placeholder: ' ', autocapitalize: 'off', autocorrect: 'off', spellcheck: 'false' });
  sheet('Новый канал', h('div',
    h('div.profile', h('div.avatar.lg', { style: '--c1:#72d5fd;--c2:#2a9ef1', html: I.channel })),
    h('label.field', title, h('span', 'Название канала')),
    h('label.field', desc, h('span', 'Описание (необязательно)')),
    h('label.field', h('span.prefix', '@'), handle, h('span', 'Публичная ссылка')),
    h('div.hint', 'По ссылке канал можно найти в поиске. Без ссылки канал будет частным — в него можно только пересылать посты.')), {
    action: 'Создать',
    onAction: async (close, btn) => {
      if (!title.value.trim()) return toast('Введите название канала');
      btn.disabled = true;
      try {
        const chat = putChat(await api('POST', '/api/chats/channel', { title: title.value, description: desc.value, handle: handle.value }));
        close(); renderList(); openChat(chat.id);
      } catch (err) { toast(err.message); btn.disabled = false; }
    },
  });
  setTimeout(() => title.focus());
}

function forwardDialog(m) {
  const input = h('input', { type: 'search', placeholder: ' ', autocomplete: 'off' });
  const list = h('div');
  let s;
  const render = () => {
    const q = input.value.trim().toLowerCase();
    const chats = sortedChats().filter((c) => c.canPost && (!q || chatTitle(c).toLowerCase().includes(q)))
      .sort((a, b) => (b.type === 'saved') - (a.type === 'saved'));
    list.replaceChildren(...chats.map((c) => h('button.row', {
      onclick: async () => {
        s.close();
        try {
          await api('POST', '/api/messages/forward', { messageIds: [m.id], chatIds: [c.id] });
          toast(c.type === 'saved' ? 'Сообщение сохранено в Избранном' : `Сообщение переслано: ${chatTitle(c)}`);
        } catch (err) { toast(err.message); }
      },
    }, chatAv(c, 'sm'), h('div.body', h('div.top', h('span.name', chatTitle(c)))))));
  };
  input.addEventListener('input', render);
  render();
  s = sheet('Переслать', h('div', h('label.field', input, h('span', 'Поиск')), list));
  if (!coarse()) setTimeout(() => input.focus());
}

function openForwardSource(f) {
  if (f.chatId) {
    const c = S.chats.get(f.chatId);
    if (c) return openChat(c.id);
    return api('GET', `/api/chats/${f.chatId}`).then(openPreview).catch(() => toast('Канал недоступен'));
  }
  if (f.userId) return api('GET', `/api/users/${f.userId}`).then((u) => openUserProfile(remember(u))).catch(() => toast('Пользователь скрыт'));
}

// ---------- профили ----------
function infoRow(ic, value, key, onclick) {
  return h(onclick ? 'button.info-row' : 'div.info-row', { onclick }, h('span', { html: I[ic], style: 'display:contents' }), h('div', h('div.v', value), key ? h('div.k', key) : null));
}

function openUserProfile(u) {
  if (u.id === S.me.id) return openSettings();
  u = user(u.id);
  const direct = [...S.chats.values()].find((c) => c.type === 'direct' && peerOf(c)?.id === u.id);
  let s;
  s = sheet('Информация', h('div',
    h('div.profile', userAv(u, 'lg'), h('h3', u.name), h('div.s', { class: u.online ? 'on' : '' }, lastSeen(u))),
    u.bio ? infoRow('info', u.bio, 'О себе') : null,
    infoRow('at', '@' + u.username, 'Имя пользователя', () => copyText('@' + u.username)),
    h('div.sep'),
    S.current !== direct?.id ? h('button.info-row', { style: 'color:var(--accent)', onclick: () => { s.close(); startDirect(u.id); } }, h('span', { html: I.send, style: 'display:contents;color:var(--accent)' }), 'Отправить сообщение') : null,
    direct ? h('button.switch-row', { onclick: (e) => { chatSettings(direct, { muted: !direct.muted }); e.currentTarget.querySelector('.switch').classList.toggle('on'); } },
      h('span', 'Уведомления'), h('span.switch', { class: direct.muted ? '' : 'on' })) : null));
}

function openChatInfo() {
  const chat = S.chats.get(S.current);
  if (!chat) return;
  const peer = peerOf(chat);
  if (peer) return openUserProfile(peer);
  if (chat.type === 'saved') {
    return sheet('Избранное', h('div', h('div.profile', avatarEl({ saved: true }, 'lg'), h('h3', 'Избранное')),
      h('p', { style: 'color:var(--muted);text-align:center' }, 'Облачное хранилище для заметок, ссылок и пересланных сообщений. Доступно на всех ваших устройствах.')));
  }
  const admin = chat.role === 'owner' || chat.role === 'admin';
  const canEdit = chat.type === 'group' || admin;
  const isChannel = chat.type === 'channel';
  let s;
  const av = chatAv(chat, canEdit ? 'lg editable' : 'lg');
  if (canEdit) {
    av.onclick = async () => {
      try { const a = await uploadAvatar(); if (a) { putChat(await api('PATCH', `/api/chats/${chat.id}`, { avatar: a.name })); s.close(); renderHeader(); renderList(); openChatInfo(); } } catch (err) { toast(err.message); }
    };
  }
  const count = isChannel ? `${chat.memberCount} ${plural(chat.memberCount, ['подписчик', 'подписчика', 'подписчиков'])}`
    : `${chat.members.length} ${plural(chat.members.length, ['участник', 'участника', 'участников'])}`;
  const members = isChannel ? [] : chat.members.map((m) => {
    const u = user(m.id);
    return h('button.row', { onclick: () => { s.close(); openUserProfile(u); } },
      userAv(u, 'sm', { dot: true }),
      h('div.body', h('div.top', h('span.name', u.id === S.me.id ? `${u.name}` : u.name), h('span.fill'), m.role === 'owner' ? h('span.time', 'владелец') : null),
        h('div.bottom', h('span.preview', { style: u.online || u.id === S.me.id ? 'color:var(--accent)' : '' }, u.id === S.me.id ? 'в сети' : lastSeen(u)))));
  });
  s = sheet(isChannel ? 'Канал' : 'Группа', h('div',
    h('div.profile', av, h('h3', chat.title), h('div.s', count)),
    chat.description ? infoRow('info', chat.description, 'Описание') : null,
    chat.handle ? infoRow('at', '@' + chat.handle, 'Ссылка', () => copyText('@' + chat.handle)) : null,
    chat.joined ? h('button.switch-row', { onclick: (e) => { chatSettings(chat, { muted: !chat.muted }); e.currentTarget.querySelector('.switch').classList.toggle('on'); } },
      h('span', 'Уведомления'), h('span.switch', { class: chat.muted ? '' : 'on' })) : null,
    canEdit ? h('button.info-row', { onclick: () => { s.close(); editChat(chat); } }, h('span', { html: I.edit, style: 'display:contents' }), 'Изменить') : null,
    h('div.sep'),
    !isChannel ? h('div.section-title', count) : null,
    !isChannel ? h('button.info-row', { style: 'color:var(--accent)', onclick: () => { s.close(); addMembers(chat); } }, h('span', { html: I.addUser, style: 'display:contents' }), 'Добавить участников') : null,
    members,
    !chat.joined && isChannel ? h('button.btn', { style: 'margin-top:12px', onclick: () => { s.close(); joinChannel(chat); } }, 'Подписаться') : null,
    chat.joined && !(isChannel && chat.role === 'owner') ? h('button.info-row.danger', { onclick: () => { s.close(); leave(chat); } }, h('span', { html: I.leave, style: 'display:contents' }), isChannel ? 'Покинуть канал' : 'Покинуть группу') : null));
}

function editChat(chat) {
  const title = h('input', { value: chat.title, maxlength: 64, placeholder: ' ' });
  const desc = h('textarea', { rows: 2, maxlength: 255, placeholder: ' ' });
  desc.value = chat.description || '';
  const handle = h('input', { value: chat.handle || '', maxlength: 32, placeholder: ' ', autocapitalize: 'off' });
  sheet(chat.type === 'channel' ? 'Изменить канал' : 'Изменить группу', h('div',
    h('label.field', title, h('span', 'Название')),
    h('label.field', desc, h('span', 'Описание')),
    chat.type === 'channel' ? h('label.field', h('span.prefix', '@'), handle, h('span', 'Публичная ссылка')) : null), {
    action: 'Сохранить',
    onAction: async (close) => {
      const body = { title: title.value, description: desc.value };
      if (chat.type === 'channel') body.handle = handle.value;
      try { putChat(await api('PATCH', `/api/chats/${chat.id}`, body)); close(); renderList(); renderHeader(); } catch (err) { toast(err.message); }
    },
  });
}

function addMembers(chat) {
  const picker = peoplePicker({ multi: true, exclude: new Set(chat.members.map((m) => m.id)) });
  sheet('Добавить участников', picker.el, {
    action: 'Добавить',
    onAction: async (close) => {
      if (!picker.selected.size) return toast('Выберите участников');
      try { putChat(await api('POST', `/api/chats/${chat.id}/members`, { userIds: [...picker.selected.keys()] })); close(); renderHeader(); } catch (err) { toast(err.message); }
    },
  });
}

function openSettings() {
  const me = user(S.me.id);
  const name = h('input', { value: me.name, maxlength: 64, placeholder: ' ' });
  const username = h('input', { value: me.username, maxlength: 32, placeholder: ' ', autocapitalize: 'off', autocorrect: 'off', spellcheck: 'false' });
  const bio = h('textarea', { rows: 2, maxlength: 200, placeholder: ' ' });
  bio.value = me.bio || '';
  let s;
  const av = userAv(me, 'lg editable');
  av.onclick = async () => {
    try { const a = await uploadAvatar(); if (a) { remember(await api('PATCH', '/api/me', { avatar: a.name })); s.close(); openSettings(); renderList(); } } catch (err) { toast(err.message); }
  };
  const theme = LS.get('theme', 'auto');
  const seg = h('div.seg', [['auto', 'Как в системе'], ['light', 'Дневная'], ['dark', 'Ночная']].map(([v, label]) =>
    h('button', { class: v === theme ? 'on' : '', onclick: (e) => {
      LS.set('theme', v); applyTheme();
      [...seg.children].forEach((b) => b.classList.toggle('on', b === e.currentTarget));
    } }, label)));
  const notifySwitch = h('span.switch', { class: LS.get('notify', true) ? 'on' : '' });

  s = sheet('Настройки', h('div',
    h('div.profile', av, h('h3', me.name), h('div.s.on', 'в сети')),
    h('label.field', name, h('span', 'Имя')),
    h('label.field', h('span.prefix', '@'), username, h('span', 'Имя пользователя')),
    h('label.field', bio, h('span', 'О себе')),
    h('button.btn', { onclick: async (e) => {
      const b = e.currentTarget; b.disabled = true;
      try { remember(await api('PATCH', '/api/me', { name: name.value, bio: bio.value, username: username.value })); toast('Профиль сохранён'); renderList(); } catch (err) { toast(err.message); }
      b.disabled = false;
    } }, 'Сохранить'),
    h('div.sep'),
    h('div.section-title', 'Оформление'), seg,
    h('div.section-title', 'Уведомления'),
    h('button.switch-row', { onclick: () => {
      const v = !LS.get('notify', true); LS.set('notify', v); notifySwitch.classList.toggle('on', v);
      if (v && 'Notification' in window && !isNative) Notification.requestPermission().catch(() => {});
    } }, h('span', 'Уведомления о сообщениях'), notifySwitch),
    h('div.sep'),
    h('button.info-row', { onclick: changePassword }, h('span', { html: I.lock, style: 'display:contents' }), 'Сменить пароль'),
    h('button.info-row.danger', { onclick: () => { if (confirm('Выйти из аккаунта?')) logout(); } }, h('span', { html: I.logout, style: 'display:contents' }), 'Выйти'),
    h('p', { style: 'text-align:center;color:var(--muted);font-size:13px;margin-top:18px' }, `Компас · ${serverBase().replace(/^https?:\/\//, '')}`)));
}

function changePassword() {
  const oldP = h('input', { type: 'password', autocomplete: 'current-password', placeholder: ' ' });
  const newP = h('input', { type: 'password', autocomplete: 'new-password', minlength: 6, placeholder: ' ' });
  sheet('Смена пароля', h('div', h('label.field', oldP, h('span', 'Текущий пароль')), h('label.field', newP, h('span', 'Новый пароль'))), {
    action: 'Сменить',
    onAction: async (close) => {
      try { await api('POST', '/api/me/password', { oldPassword: oldP.value, newPassword: newP.value }); close(); toast('Пароль изменён'); } catch (err) { toast(err.message); }
    },
  });
}

function applyTheme() {
  const t = LS.get('theme', 'auto');
  if (t === 'auto') delete document.documentElement.dataset.theme;
  else document.documentElement.dataset.theme = t;
  const dark = t === 'dark' || (t === 'auto' && matchMedia('(prefers-color-scheme: dark)').matches);
  document.querySelector('meta[name="theme-color"]')?.setAttribute('content', dark ? '#17212b' : '#ffffff');
}

// ================= запуск =================
setupArt();
applyTheme();
if (!isNative && 'serviceWorker' in navigator && (location.protocol === 'https:' || location.hostname === 'localhost')) {
  navigator.serviceWorker.register('sw.js').catch(() => {});
  navigator.serviceWorker.addEventListener('message', (e) => { if (e.data?.type === 'open-chat' && S.token) openChat(e.data.chatId); });
}
if (S.token && S.me) start();
else renderAuth();
