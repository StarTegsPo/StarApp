// Компас — клиент мессенджера. Один и тот же код работает в браузере (PWA)
// и внутри нативных приложений iOS/Android (Capacitor).

// ============ утилиты ============
const $ = (sel, root = document) => root.querySelector(sel);
const LS = {
  get(k, d = null) { try { const v = localStorage.getItem('kompas.' + k); return v === null ? d : JSON.parse(v); } catch { return d; } },
  set(k, v) { try { v === null ? localStorage.removeItem('kompas.' + k) : localStorage.setItem('kompas.' + k, JSON.stringify(v)); } catch { /* приватный режим */ } },
};

/** h('div.cls', {attrs}, ...children) — создание DOM без innerHTML (защита от XSS). */
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

const I = {
  edit: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M12 20h9"/><path d="M16.5 3.5a2.1 2.1 0 0 1 3 3L7 19l-4 1 1-4Z"/></svg>',
  menu: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"><path d="M4 6h16M4 12h16M4 18h16"/></svg>',
  back: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round"><path d="m15 18-6-6 6-6"/></svg>',
  send: '<svg viewBox="0 0 24 24" fill="currentColor"><path d="M3.4 20.4 21 12 3.4 3.6 3.4 10l12.6 2-12.6 2z"/></svg>',
  clip: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="m21.4 11.1-9.2 9.2a6 6 0 0 1-8.5-8.5l9.2-9.2a4 4 0 0 1 5.7 5.7l-9.2 9.2a2 2 0 0 1-2.8-2.8l8.5-8.5"/></svg>',
  close: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round"><path d="M18 6 6 18M6 6l12 12"/></svg>',
  check: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.4" stroke-linecap="round" stroke-linejoin="round"><path d="m5 12 5 5L20 7"/></svg>',
  checks: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round"><path d="m2 12 5 5L18 6"/><path d="m11 16 1 1L23 6"/></svg>',
  clock: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"><circle cx="12" cy="12" r="9"/><path d="M12 7v5l3 2"/></svg>',
  group: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2"/><circle cx="9" cy="7" r="4"/><path d="M22 21v-2a4 4 0 0 0-3-3.9M16 3.1a4 4 0 0 1 0 7.8"/></svg>',
  user: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"><circle cx="12" cy="8" r="4"/><path d="M4 21a8 8 0 0 1 16 0"/></svg>',
};
const icon = (name) => h('span', { html: I[name], style: 'display:contents' });

const COLORS = ['#0f766e', '#2563eb', '#7c3aed', '#db2777', '#ea580c', '#ca8a04', '#16a34a', '#0891b2'];

function toast(text) {
  const t = $('#toast');
  t.textContent = text;
  t.classList.add('show');
  clearTimeout(toast.timer);
  toast.timer = setTimeout(() => t.classList.remove('show'), 2600);
}

const pad = (n) => String(n).padStart(2, '0');
const hm = (ts) => { const d = new Date(ts); return `${pad(d.getHours())}:${pad(d.getMinutes())}`; };
const MONTHS = ['января', 'февраля', 'марта', 'апреля', 'мая', 'июня', 'июля', 'августа', 'сентября', 'октября', 'ноября', 'декабря'];
function dayLabel(ts) {
  const d = new Date(ts); const now = new Date();
  const same = (a, b) => a.toDateString() === b.toDateString();
  if (same(d, now)) return 'Сегодня';
  const y = new Date(now); y.setDate(now.getDate() - 1);
  if (same(d, y)) return 'Вчера';
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
  if (diff < 3600e3) return `был(а) ${Math.floor(diff / 60e3)} мин назад`;
  const d = new Date(u.lastSeen);
  if (d.toDateString() === new Date().toDateString()) return `был(а) в ${hm(u.lastSeen)}`;
  return `был(а) ${dayLabel(u.lastSeen).toLowerCase()} в ${hm(u.lastSeen)}`;
}
const initials = (name) => (name || '?').trim().split(/\s+/).slice(0, 2).map((w) => w[0]).join('').toUpperCase();

function linkify(text) {
  const out = [];
  const re = /\bhttps?:\/\/[^\s<>"']+[^\s<>"'.,;:!?)\]]/gi;
  let last = 0; let m;
  while ((m = re.exec(text))) {
    if (m.index > last) out.push(text.slice(last, m.index));
    out.push(h('a', { href: m[0], target: '_blank', rel: 'noopener noreferrer' }, m[0]));
    last = m.index + m[0].length;
  }
  if (last < text.length) out.push(text.slice(last));
  return out;
}

// ============ состояние ============
const isNative = !!window.Capacitor?.isNativePlatform?.();
const S = {
  token: LS.get('token'),
  me: LS.get('me'),
  chats: new Map(),
  users: new Map(),
  messages: new Map(), // chatId -> [msg]
  hasMore: new Map(),
  current: null,
  reply: null,
  editing: null,
  typing: new Map(), // chatId -> Map(userId -> timeout)
  filter: '',
  online: false,
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
  if (raw) { payload = raw; headers['Content-Type'] = raw.type; }
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

function remember(u) {
  if (!u) return u;
  const prev = S.users.get(u.id) || {};
  const next = { ...prev, ...u };
  if ('role' in next) delete next.role;
  S.users.set(u.id, next);
  if (S.me && u.id === S.me.id) { S.me = { ...S.me, ...next }; LS.set('me', S.me); }
  return next;
}
const user = (id) => S.users.get(id) || { id, name: 'Пользователь', username: '', color: 0 };

function putChat(chat) {
  chat.members.forEach(remember);
  S.chats.set(chat.id, chat);
}
function peerOf(chat) {
  if (chat.type !== 'direct') return null;
  const m = chat.members.find((x) => x.id !== S.me.id);
  return m ? user(m.id) : null;
}
function chatTitle(chat) {
  if (chat.type === 'group') return chat.title;
  return peerOf(chat)?.name || 'Чат';
}

function avatar(entity, cls = '', { dot = false } = {}) {
  const isGroup = entity && entity.type === 'group';
  const name = isGroup ? entity.title : entity?.name;
  const color = COLORS[(isGroup ? entity.id : entity?.color ?? 0) % COLORS.length];
  const el = h('div.avatar' + (cls ? '.' + cls : ''), { style: `--c:${color}` }, initials(name));
  if (dot && !isGroup && entity?.online) el.append(h('span.dot'));
  return el;
}

// ============ авторизация ============
function renderAuth(mode = 'login', error = '') {
  const app = $('#app');
  const needServer = !serverBase();
  let busy = false;

  const form = h('form', {
    onsubmit: async (e) => {
      e.preventDefault();
      if (busy) return;
      const fd = new FormData(form);
      if (fd.get('server') !== null) {
        const s = normalizeServer(fd.get('server'));
        if (!s) return showErr('Укажите адрес сервера, например https://kompas.example.ru');
        LS.set('server', s);
      }
      busy = true; btn.disabled = true;
      try {
        const body = { username: fd.get('username'), password: fd.get('password') };
        if (mode === 'register') body.name = fd.get('name');
        const res = await api('POST', mode === 'register' ? '/api/register' : '/api/login', body);
        onLogin(res);
      } catch (err) {
        showErr(err.message);
      } finally {
        busy = false; btn.disabled = false;
      }
    },
  });
  const errEl = h('div.err', error);
  const showErr = (t) => { errEl.textContent = t; };
  const btn = h('button.btn', { type: 'submit' }, mode === 'register' ? 'Создать аккаунт' : 'Войти');

  form.append(...[
    needServer ? h('label.field', h('span', 'Адрес сервера'),
      h('input', { name: 'server', type: 'url', inputmode: 'url', placeholder: 'https://kompas.example.ru', autocapitalize: 'off', required: true })) : null,
    mode === 'register' ? h('label.field', h('span', 'Ваше имя'),
      h('input', { name: 'name', autocomplete: 'name', maxlength: 64, placeholder: 'Как вас называть' })) : null,
    h('label.field', h('span', 'Имя пользователя'),
      h('input', { name: 'username', autocomplete: 'username', autocapitalize: 'off', autocorrect: 'off', spellcheck: 'false', required: true, minlength: 3, maxlength: 32, placeholder: 'например, ivan_petrov' })),
    h('label.field', h('span', 'Пароль'),
      h('input', { name: 'password', type: 'password', autocomplete: mode === 'register' ? 'new-password' : 'current-password', required: true, minlength: 6, maxlength: 128 })),
    errEl,
    btn,
  ].filter(Boolean));

  app.replaceChildren(h('div.auth', h('div.auth-card',
    h('img.auth-logo', { src: 'icons/icon.svg', alt: '' }),
    h('h1', 'Компас'),
    h('p.sub', 'Мессенджер, который всегда указывает на своих'),
    h('div.tabs',
      h('button', { type: 'button', class: mode === 'login' ? 'on' : '', onclick: () => renderAuth('login') }, 'Вход'),
      h('button', { type: 'button', class: mode === 'register' ? 'on' : '', onclick: () => renderAuth('register') }, 'Регистрация')),
    form,
    !needServer && (isNative || LS.get('server')) ? h('button.server-link', { type: 'button', onclick: changeServer }, `Сервер: ${serverBase().replace(/^https?:\/\//, '')} · изменить`) : null,
  )));
}

function normalizeServer(s) {
  s = String(s || '').trim().replace(/\/+$/, '');
  if (!s) return '';
  if (!/^https?:\/\//i.test(s)) s = 'https://' + s;
  try { return new URL(s).toString().replace(/\/+$/, ''); } catch { return ''; }
}

function changeServer() {
  const input = h('input', { type: 'url', value: serverBase(), placeholder: 'https://kompas.example.ru', autocapitalize: 'off' });
  sheet('Адрес сервера', h('div',
    h('label.field', h('span', 'Сервер Компаса'), input),
    h('p', { style: 'color:var(--muted);font-size:14px' }, 'Все участники переписки должны быть на одном сервере.'),
  ), {
    action: 'Сохранить',
    onAction: (close) => {
      const s = normalizeServer(input.value);
      if (!s) return toast('Некорректный адрес');
      LS.set('server', s);
      close();
      renderAuth();
    },
  });
}

function onLogin({ token, user: me }) {
  S.token = token; S.me = me;
  LS.set('token', token); LS.set('me', me);
  remember(me);
  start();
}

async function logout(callApi = true) {
  if (callApi) await api('POST', '/api/logout').catch(() => {});
  S.token = null; S.me = null;
  LS.set('token', null); LS.set('me', null);
  S.chats.clear(); S.messages.clear(); S.current = null;
  ws.stop();
  renderAuth();
}

// ============ WebSocket ============
const ws = {
  sock: null, retry: 0, timer: null, stopped: true,
  start() { this.stopped = false; this.open(); },
  stop() { this.stopped = true; clearTimeout(this.timer); this.sock?.close(); this.sock = null; },
  open() {
    if (this.stopped || !S.token) return;
    const url = serverBase().replace(/^http/, 'ws') + '/ws?token=' + encodeURIComponent(S.token);
    const sock = new WebSocket(url);
    this.sock = sock;
    sock.onopen = () => {
      const reconnect = this.retry > 0 || S.online === false;
      this.retry = 0; setOnline(true);
      if (reconnect) syncAfterReconnect();
    };
    sock.onmessage = (e) => { try { onEvent(JSON.parse(e.data)); } catch (err) { console.error(err); } };
    sock.onclose = () => {
      if (this.sock !== sock) return;
      setOnline(false);
      if (this.stopped) return;
      const delay = Math.min(30000, 1000 * 2 ** this.retry++);
      this.timer = setTimeout(() => this.open(), delay);
    };
  },
  send(obj) { if (this.sock?.readyState === 1) this.sock.send(JSON.stringify(obj)); },
};

function setOnline(v) {
  S.online = v;
  const el = $('#conn');
  if (el) el.classList.toggle('hidden', v);
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
    case 'message': {
      const m = ev.message;
      const chat = S.chats.get(m.chatId);
      if (!chat) { api('GET', `/api/chats/${m.chatId}`).then((c) => { putChat(c); renderList(); }).catch(() => {}); return; }
      const list = S.messages.get(m.chatId);
      if (list && !list.some((x) => x.id === m.id)) list.push(m);
      chat.lastMessage = m;
      if (m.userId !== S.me.id) {
        clearTyping(m.chatId, m.userId);
        if (S.current === m.chatId && isVisible()) markRead(m.chatId, m.id);
        else if (m.kind !== 'system') { chat.unread++; notify(chat, m); }
      } else {
        chat.lastReadId = Math.max(chat.lastReadId, m.id);
      }
      if (m.kind === 'system') api('GET', `/api/chats/${m.chatId}`).then((c) => { putChat(c); renderList(); renderHeader(); }).catch(() => {});
      renderList();
      if (S.current === m.chatId) renderMessages({ keepBottom: m.userId !== S.me.id, toBottom: m.userId === S.me.id });
      break;
    }
    case 'message_edit':
    case 'message_delete': {
      const chatId = ev.message?.chatId ?? ev.chatId;
      const id = ev.message?.id ?? ev.messageId;
      const list = S.messages.get(chatId);
      if (list) {
        const i = list.findIndex((x) => x.id === id);
        if (i >= 0) list[i] = ev.message || { ...list[i], deleted: true, text: '', file: null };
      }
      const chat = S.chats.get(chatId);
      if (chat?.lastMessage?.id === id) {
        if (ev.type === 'message_edit') chat.lastMessage = ev.message;
        else api('GET', `/api/chats/${chatId}`).then((c) => { putChat(c); renderList(); }).catch(() => {});
      }
      renderList();
      if (S.current === chatId) renderMessages({ keepBottom: true });
      break;
    }
    case 'chat':
      putChat(ev.chat);
      renderList();
      if (S.current === ev.chat.id) renderHeader();
      break;
    case 'chat_removed':
      S.chats.delete(ev.chatId); S.messages.delete(ev.chatId);
      if (S.current === ev.chatId) closeChat();
      renderList();
      break;
    case 'read': {
      const chat = S.chats.get(ev.chatId);
      if (!chat) return;
      if (ev.userId === S.me.id) { chat.lastReadId = Math.max(chat.lastReadId, ev.messageId); recountUnread(chat); }
      else chat.readUpTo = Math.max(chat.readUpTo, ev.messageId);
      renderList();
      if (S.current === ev.chatId) renderMessages({ keepBottom: true });
      break;
    }
    case 'presence': {
      remember({ id: ev.userId, online: ev.online, lastSeen: ev.lastSeen ?? user(ev.userId).lastSeen });
      renderList();
      renderHeader();
      break;
    }
    case 'user':
      remember(ev.user);
      renderList(); renderHeader();
      break;
    case 'typing': {
      let map = S.typing.get(ev.chatId);
      if (!map) S.typing.set(ev.chatId, (map = new Map()));
      clearTimeout(map.get(ev.userId));
      map.set(ev.userId, setTimeout(() => clearTyping(ev.chatId, ev.userId), 5000));
      renderTyping();
      renderList();
      break;
    }
  }
}

function clearTyping(chatId, userId) {
  const map = S.typing.get(chatId);
  if (!map?.has(userId)) return;
  clearTimeout(map.get(userId));
  map.delete(userId);
  renderTyping(); renderList();
}

function typingText(chatId) {
  const ids = [...(S.typing.get(chatId)?.keys() || [])];
  if (!ids.length) return '';
  const chat = S.chats.get(chatId);
  if (chat?.type === 'direct') return 'печатает…';
  return ids.length === 1 ? `${user(ids[0]).name} печатает…` : `${ids.length} человека печатают…`;
}

function recountUnread(chat) {
  const list = S.messages.get(chat.id);
  if (list) chat.unread = list.filter((m) => m.id > chat.lastReadId && m.userId !== S.me.id && !m.deleted && m.kind !== 'system').length;
}

const isVisible = () => document.visibilityState === 'visible' && document.hasFocus?.() !== false;

// ============ уведомления ============
// Вызов нативного плагина напрямую через мост Capacitor (без сборщика JS-модулей).
const nativeNotifications = isNative ? {
  call: (method, options = {}) => window.Capacitor.nativePromise('LocalNotifications', method, options),
  on: (event, cb) => window.Capacitor.addListener('LocalNotifications', event, cb),
} : null;

async function notify(chat, m) {
  if (LS.get('notify', true) === false) return;
  const title = chatTitle(chat);
  const body = (chat.type === 'group' ? user(m.userId).name + ': ' : '') + preview(m);
  if (nativeNotifications) {
    if (isVisible() && S.current === chat.id) return;
    try {
      await nativeNotifications.call('schedule', { notifications: [{ id: m.id % 2147483647, title, body, extra: { chatId: chat.id } }] });
    } catch { /* нет разрешения */ }
    return;
  }
  if (!('Notification' in window) || Notification.permission !== 'granted' || isVisible()) return;
  try {
    const reg = await navigator.serviceWorker?.getRegistration();
    if (reg) reg.showNotification(title, { body, tag: 'chat-' + chat.id, icon: 'icons/icon-192.png', data: { chatId: chat.id } });
    else new Notification(title, { body, tag: 'chat-' + chat.id });
  } catch { /* браузер не поддерживает */ }
}

async function askNotifications() {
  if (nativeNotifications) {
    try {
      await nativeNotifications.call('requestPermissions');
      nativeNotifications.on('localNotificationActionPerformed', (a) => {
        const id = a.notification?.extra?.chatId;
        if (id) openChat(id);
      });
    } catch { /* ignore */ }
    return;
  }
  if ('Notification' in window && Notification.permission === 'default') {
    const ask = () => { Notification.requestPermission().catch(() => {}); document.removeEventListener('click', ask); };
    document.addEventListener('click', ask);
  }
}

// ============ основной экран ============
function start() {
  remember(S.me);
  $('#app').replaceChildren(h('div.main#main',
    h('aside.side',
      h('div.topbar',
        h('button.icon-btn', { title: 'Профиль и настройки', onclick: openSettings }, icon('menu')),
        h('h2', 'Компас'),
        h('button.icon-btn', { title: 'Новая группа', onclick: newGroup }, icon('group'))),
      h('div.search', h('input#search', {
        type: 'search', placeholder: 'Поиск чатов и людей', autocomplete: 'off',
        oninput: (e) => { S.filter = e.target.value.trim(); renderList(); searchPeople(); },
      })),
      h('div.list#chats'),
      h('button.fab', { title: 'Новый чат', onclick: newChat }, icon('edit'))),
    h('section.pane#pane'),
  ));
  renderPane();
  loadChats();
  ws.start();
  askNotifications();
}

async function loadChats() {
  try {
    const [me, chats] = await Promise.all([api('GET', '/api/me'), api('GET', '/api/chats')]);
    remember(me);
    S.chats.clear();
    chats.forEach(putChat);
    renderList();
    if (S.current && !S.chats.has(S.current)) closeChat();
    else renderHeader();
  } catch (err) {
    if (err.status !== 401) toast(err.message);
  }
}

function preview(m) {
  if (!m) return '';
  if (m.deleted) return 'Сообщение удалено';
  if (m.kind === 'system') return systemText(m);
  if (m.kind === 'image') return '📷 ' + (m.text || 'Фото');
  return m.text;
}

function systemText(m) {
  const who = m.userId === S.me.id ? 'Вы' : user(m.userId).name;
  const [kind, arg] = [m.text.split(':')[0], m.text.slice(m.text.indexOf(':') + 1)];
  switch (kind) {
    case 'created': return `${who} ${who === 'Вы' ? 'создали' : 'создал(а)'} группу`;
    case 'renamed': return `${who} ${who === 'Вы' ? 'переименовали' : 'переименовал(а)'} группу в «${arg}»`;
    case 'added': {
      const target = Number(arg) === S.me.id ? 'вас' : user(Number(arg)).name;
      return `${who} ${who === 'Вы' ? 'добавили' : 'добавил(а)'} ${target}`;
    }
    case 'left': return `${who} ${who === 'Вы' ? 'покинули' : 'покинул(а)'} группу`;
    default: return m.text;
  }
}

function sortedChats() {
  return [...S.chats.values()].sort((a, b) => (b.lastMessage?.createdAt ?? b.createdAt) - (a.lastMessage?.createdAt ?? a.createdAt));
}

let peopleResults = [];
let searchTimer;
function searchPeople() {
  clearTimeout(searchTimer);
  const q = S.filter;
  if (q.length < 2) { peopleResults = []; renderList(); return; }
  searchTimer = setTimeout(async () => {
    try {
      const res = await api('GET', '/api/users/search?q=' + encodeURIComponent(q));
      if (q !== S.filter) return;
      peopleResults = res.map(remember);
      renderList();
    } catch { /* офлайн */ }
  }, 250);
}

function renderList() {
  const box = $('#chats');
  if (!box) return;
  const q = S.filter.toLowerCase();
  const chats = sortedChats().filter((c) => !q || chatTitle(c).toLowerCase().includes(q)
    || (peerOf(c)?.username || '').toLowerCase().includes(q.replace(/^@/, '')));
  const rows = chats.map((c) => {
    const peer = peerOf(c);
    const m = c.lastMessage;
    const typing = typingText(c.id);
    const mineLast = m && m.userId === S.me.id && m.kind !== 'system';
    return h('button.row', { class: c.id === S.current ? 'on' : '', onclick: () => openChat(c.id) },
      avatar(peer || c, '', { dot: true }),
      h('div.body',
        h('div.top', h('span.name', chatTitle(c)),
          mineLast && !m.deleted ? h('span', { style: 'color:var(--accent);display:flex', html: c.readUpTo >= m.id ? I.checks : I.check }) : null,
          h('span.time', m ? shortTime(m.createdAt) : '')),
        h('div.bottom',
          typing ? h('span.preview', { style: 'color:var(--accent)' }, typing)
            : h('span.preview',
              m && c.type === 'group' && m.kind !== 'system' ? h('b', (m.userId === S.me.id ? 'Вы' : user(m.userId).name) + ': ') : null,
              m ? preview(m) : (c.type === 'group' ? 'Группа создана' : 'Начните переписку')),
          c.unread ? h('span.badge', c.unread > 99 ? '99+' : c.unread) : null)));
  });

  const knownPeers = new Set(chats.map((c) => peerOf(c)?.id).filter(Boolean));
  const people = q ? peopleResults.filter((u) => !knownPeers.has(u.id)) : [];
  if (people.length) {
    rows.push(h('div.section-title', { style: 'margin-left:16px' }, 'Люди'));
    rows.push(...people.map((u) => h('button.row', { onclick: () => startDirect(u.id) },
      avatar(u, '', { dot: true }),
      h('div.body', h('div.top', h('span.name', u.name)), h('div.bottom', h('span.preview', '@' + u.username))))));
  }
  if (!rows.length) {
    rows.push(h('div.empty',
      h('div.big', '🧭'),
      q ? 'Ничего не найдено' : h('div', 'Чатов пока нет.', h('br'), 'Нажмите ✏️, чтобы найти собеседника по имени пользователя.')));
  }
  box.replaceChildren(...rows);
  document.title = totalUnread() ? `(${totalUnread()}) Компас` : 'Компас';
}
const totalUnread = () => [...S.chats.values()].reduce((s, c) => s + c.unread, 0);

// ============ окно чата ============
function renderPane() {
  const pane = $('#pane');
  if (!pane) return;
  $('#main').classList.toggle('in-chat', !!S.current);
  if (!S.current) {
    pane.replaceChildren(h('div.pane-empty', h('div',
      h('img', { src: 'icons/icon.svg', alt: '' }),
      h('p', 'Выберите чат или начните новый'))));
    return;
  }
  const input = h('textarea#input', {
    rows: 1, placeholder: 'Сообщение', maxlength: 4000,
    oninput: () => { autosize(input); sendTyping(); saveDraft(); },
    onkeydown: (e) => {
      if (e.key === 'Enter' && !e.shiftKey && !e.isComposing && !matchMedia('(pointer: coarse)').matches) { e.preventDefault(); submit(); }
      if (e.key === 'Escape') { S.reply = null; S.editing = null; renderComposerBar(); }
    },
  });
  const file = h('input', { type: 'file', accept: 'image/*', class: 'hidden', onchange: (e) => { const f = e.target.files[0]; e.target.value = ''; if (f) sendImage(f); } });
  pane.replaceChildren(
    h('div.topbar',
      h('button.icon-btn.back', { title: 'Назад', onclick: closeChat }, icon('back')),
      h('button.chat-head#chat-head', { onclick: openChatInfo })),
    h('div.conn#conn', { class: S.online ? 'hidden' : '' }, 'Подключение…'),
    h('div.messages#messages', { onscroll: onMessagesScroll }),
    h('div.typing#typing'),
    h('div.composer-wrap',
      h('div#composer-bar'),
      h('div.composer',
        h('button.icon-btn', { title: 'Отправить фото', onclick: () => file.click() }, icon('clip')),
        file, input,
        h('button.icon-btn.send', { title: 'Отправить', onclick: submit }, icon('send')))),
  );
  input.value = LS.get('draft.' + S.current, '') || '';
  autosize(input);
  renderHeader();
  renderComposerBar();
  renderMessages({ toBottom: true });
  renderTyping();
}

function renderHeader() {
  const head = $('#chat-head');
  const chat = S.chats.get(S.current);
  if (!head || !chat) return;
  const peer = peerOf(chat);
  const typing = typingText(chat.id);
  let status;
  if (typing) status = h('div.s.on', typing);
  else if (peer) status = h('div.s', { class: peer.online ? 'on' : '' }, lastSeen(peer));
  else {
    const online = chat.members.filter((m) => user(m.id).online).length;
    status = h('div.s', `${chat.members.length} участн.${online > 1 ? `, ${online} в сети` : ''}`);
  }
  head.replaceChildren(avatar(peer || chat, 'sm', { dot: true }), h('div.t', h('div.n', chatTitle(chat)), status));
}

function renderTyping() {
  if (!S.current) return;
  renderHeader();
}

async function openChat(id) {
  id = Number(id);
  if (!S.chats.has(id)) {
    try { putChat(await api('GET', `/api/chats/${id}`)); } catch (err) { return toast(err.message); }
  }
  S.current = id; S.reply = null; S.editing = null;
  if (!S.messages.has(id)) S.messages.set(id, []);
  renderPane();
  renderList();
  try {
    const msgs = await api('GET', `/api/chats/${id}/messages`);
    if (S.current !== id) return;
    S.messages.set(id, msgs);
    S.hasMore.set(id, msgs.length >= 50);
    renderMessages({ toBottom: true });
    const last = msgs[msgs.length - 1];
    if (last) markRead(id, last.id);
  } catch (err) { toast(err.message); }
  if (!matchMedia('(pointer: coarse)').matches) $('#input')?.focus();
  history.pushState({ chat: id }, '');
}

function closeChat() {
  S.current = null; S.reply = null; S.editing = null;
  renderPane(); renderList();
}

window.addEventListener('popstate', () => { if (S.current) closeChat(); });

let readTimer;
function markRead(chatId, messageId) {
  const chat = S.chats.get(chatId);
  if (!chat || messageId <= chat.lastReadId) return;
  chat.lastReadId = messageId;
  chat.unread = 0;
  recountUnread(chat);
  renderList();
  clearTimeout(readTimer);
  readTimer = setTimeout(() => api('POST', `/api/chats/${chatId}/read`, { messageId }).catch(() => {}), 300);
}

document.addEventListener('visibilitychange', () => {
  if (document.visibilityState === 'visible' && S.current) {
    const last = S.messages.get(S.current)?.at(-1);
    if (last) markRead(S.current, last.id);
  }
});

let loadingOlder = false;
async function onMessagesScroll(e) {
  const box = e.target;
  if (box.scrollTop > 120 || loadingOlder || !S.hasMore.get(S.current)) return;
  const id = S.current;
  const list = S.messages.get(id);
  if (!list?.length) return;
  loadingOlder = true;
  try {
    const older = await api('GET', `/api/chats/${id}/messages?before=${list[0].id}`);
    if (S.current !== id) return;
    S.hasMore.set(id, older.length >= 50);
    const prevHeight = box.scrollHeight;
    S.messages.set(id, [...older, ...list]);
    renderMessages();
    box.scrollTop = box.scrollHeight - prevHeight + box.scrollTop;
  } catch { /* офлайн */ } finally { loadingOlder = false; }
}

function renderMessages({ toBottom = false, keepBottom = false } = {}) {
  const box = $('#messages');
  const chat = S.chats.get(S.current);
  if (!box || !chat) return;
  const atBottom = box.scrollHeight - box.scrollTop - box.clientHeight < 80;
  const prevTop = box.scrollTop;
  const list = S.messages.get(S.current) || [];
  const out = [];
  let lastDay = '';
  list.forEach((m, i) => {
    const day = new Date(m.createdAt).toDateString();
    if (day !== lastDay) { out.push(h('div.day', dayLabel(m.createdAt))); lastDay = day; }
    if (m.kind === 'system') { out.push(h('div.sys', systemText(m))); return; }
    const next = list[i + 1];
    const tail = !next || next.userId !== m.userId || next.kind === 'system'
      || new Date(next.createdAt).toDateString() !== day || next.createdAt - m.createdAt > 5 * 60e3;
    const prev = list[i - 1];
    const head = !prev || prev.userId !== m.userId || prev.kind === 'system' || new Date(prev.createdAt).toDateString() !== day;
    out.push(messageEl(m, chat, { tail, head }));
  });
  if (!list.length) out.push(h('div.empty', h('div.big', '👋'), 'Сообщений пока нет. Напишите первым!'));
  box.replaceChildren(...out);
  if (toBottom || (keepBottom && atBottom)) box.scrollTop = box.scrollHeight;
  else box.scrollTop = prevTop;
}

function messageEl(m, chat, { tail, head }) {
  const mine = m.userId === S.me.id;
  const u = user(m.userId);
  const bubble = h('div.bubble', { class: m.deleted ? 'deleted' : '' });
  if (chat.type === 'group' && !mine && head) {
    bubble.append(h('div.author', { style: `color:${COLORS[u.color % COLORS.length]}` }, u.name));
  }
  if (m.replyTo) bubble.append(quoteEl(m.replyTo, () => jumpTo(m.replyTo.id)));
  if (m.file && !m.deleted) {
    const img = h('img.photo', { src: fileUrl(m.file), alt: 'Фото', loading: 'lazy', onclick: (e) => { e.stopPropagation(); viewImage(fileUrl(m.file)); } });
    img.addEventListener('load', () => {
      const box = $('#messages');
      if (box && box.scrollHeight - box.scrollTop - box.clientHeight < img.height + 120) box.scrollTop = box.scrollHeight;
    }, { once: true });
    bubble.append(img);
  }
  const meta = h('span.meta',
    m.editedAt && !m.deleted ? 'изм. ' : '',
    hm(m.createdAt),
    mine && !m.deleted ? h('span', { class: chat.readUpTo >= m.id ? 'read' : '', style: 'display:flex', html: m.pending ? I.clock : chat.readUpTo >= m.id ? I.checks : I.check }) : null);
  bubble.append(h('div.text', m.deleted ? 'Сообщение удалено' : linkify(m.text), meta));

  const el = h('div.msg', { class: (mine ? 'mine ' : '') + (tail ? 'tail' : ''), 'data-id': m.id },
    chat.type === 'group' && !mine ? avatar(u, 'sm') : null, bubble);
  if (!m.deleted && !m.pending) {
    el.addEventListener('contextmenu', (e) => { e.preventDefault(); messageMenu(m, e.clientX, e.clientY); });
    let timer;
    el.addEventListener('touchstart', (e) => {
      const t = e.touches[0];
      timer = setTimeout(() => { navigator.vibrate?.(10); messageMenu(m, t.clientX, t.clientY); }, 450);
    }, { passive: true });
    for (const ev of ['touchend', 'touchmove', 'touchcancel']) el.addEventListener(ev, () => clearTimeout(timer), { passive: true });
    let lastTap = 0;
    el.addEventListener('click', (e) => {
      if (e.target.closest('a,img')) return;
      if (Date.now() - lastTap < 300) { setReply(m); lastTap = 0; } else lastTap = Date.now();
    });
  }
  return el;
}

function quoteEl(r, onclick) {
  return h('div.quote', { onclick },
    h('div.qn', r.userId === S.me.id ? 'Вы' : user(r.userId).name),
    h('div.qt', r.deleted ? 'Сообщение удалено' : r.kind === 'image' ? '📷 ' + (r.text || 'Фото') : r.text));
}

function jumpTo(id) {
  const el = $(`.msg[data-id="${id}"]`);
  if (!el) return toast('Сообщение выше в истории');
  el.scrollIntoView({ block: 'center', behavior: 'smooth' });
  el.classList.remove('flash'); void el.offsetWidth; el.classList.add('flash');
}

function messageMenu(m, x, y) {
  const mine = m.userId === S.me.id;
  const items = [
    ['↩️ Ответить', () => setReply(m)],
    m.text ? ['📋 Копировать', () => copyText(m.text)] : null,
    mine && m.kind !== 'system' ? ['✏️ Изменить', () => startEdit(m)] : null,
    mine ? ['🗑 Удалить', () => deleteMessage(m), 'danger'] : null,
  ].filter(Boolean);
  popupMenu(items, x, y);
}

function popupMenu(items, x, y) {
  const close = () => { bg.remove(); menu.remove(); };
  const bg = h('div.menu-bg', { onclick: close, oncontextmenu: (e) => { e.preventDefault(); close(); } });
  const menu = h('div.menu', items.map(([label, fn, cls]) => h('button', { class: cls || '', onclick: () => { close(); fn(); } }, label)));
  document.body.append(bg, menu);
  const r = menu.getBoundingClientRect();
  menu.style.left = Math.max(8, Math.min(x, innerWidth - r.width - 8)) + 'px';
  menu.style.top = Math.max(8, Math.min(y, innerHeight - r.height - 8)) + 'px';
}

async function copyText(t) {
  try { await navigator.clipboard.writeText(t); toast('Скопировано'); } catch { toast('Не удалось скопировать'); }
}

function setReply(m) {
  S.editing = null; S.reply = m;
  renderComposerBar();
  $('#input')?.focus();
}

function startEdit(m) {
  S.reply = null; S.editing = m;
  const input = $('#input');
  input.value = m.text;
  autosize(input);
  renderComposerBar();
  input.focus();
}

function renderComposerBar() {
  const bar = $('#composer-bar');
  if (!bar) return;
  const cancel = h('button.icon-btn', { title: 'Отмена', onclick: () => {
    if (S.editing) { $('#input').value = ''; autosize($('#input')); }
    S.reply = null; S.editing = null; renderComposerBar();
  } }, icon('close'));
  if (S.reply) bar.replaceChildren(h('div.reply-bar', quoteEl(S.reply, () => jumpTo(S.reply.id)), cancel));
  else if (S.editing) bar.replaceChildren(h('div.reply-bar', h('div.quote', h('div.qn', 'Редактирование'), h('div.qt', S.editing.text)), cancel));
  else bar.replaceChildren();
}

async function deleteMessage(m) {
  if (!confirm('Удалить сообщение у всех?')) return;
  try { await api('DELETE', `/api/messages/${m.id}`); } catch (err) { toast(err.message); }
}

function autosize(el) {
  el.style.height = 'auto';
  el.style.height = Math.min(el.scrollHeight, 140) + 'px';
}

let typingSent = 0;
function sendTyping() {
  if (Date.now() - typingSent < 3000 || !$('#input').value) return;
  typingSent = Date.now();
  ws.send({ type: 'typing', chatId: S.current });
}

function saveDraft() {
  if (S.editing) return;
  LS.set('draft.' + S.current, $('#input').value || null);
}

let tempId = -1;
async function submit() {
  const input = $('#input');
  const text = input.value.trim();
  if (!text) return;
  const chatId = S.current;
  if (S.editing) {
    const m = S.editing;
    S.editing = null; input.value = ''; autosize(input); renderComposerBar();
    if (text !== m.text) api('PATCH', `/api/messages/${m.id}`, { text }).catch((err) => toast(err.message));
    return;
  }
  const replyTo = S.reply;
  S.reply = null; input.value = ''; autosize(input); renderComposerBar();
  LS.set('draft.' + chatId, null);
  typingSent = 0;
  // Оптимистичное сообщение с «часиками», пока сервер не подтвердит
  const temp = { id: tempId--, chatId, userId: S.me.id, kind: 'text', text, replyTo: replyTo ? { id: replyTo.id, userId: replyTo.userId, kind: replyTo.kind, text: replyTo.text } : null, createdAt: Date.now(), pending: true };
  S.messages.get(chatId)?.push(temp);
  renderMessages({ toBottom: true });
  try {
    const msg = await api('POST', `/api/chats/${chatId}/messages`, { text, replyTo: replyTo?.id });
    replaceTemp(chatId, temp.id, msg);
  } catch (err) {
    replaceTemp(chatId, temp.id, null);
    if (S.current === chatId && !$('#input').value) { $('#input').value = text; autosize($('#input')); }
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
  const replyTo = S.reply?.id;
  const caption = $('#input').value.trim();
  try {
    toast('Отправка фото…');
    const blob = await compressImage(file);
    const { file: name } = await api('POST', '/api/upload', undefined, { raw: blob });
    await api('POST', `/api/chats/${chatId}/messages`, { file: name, text: caption, replyTo });
    if (caption && S.current === chatId) { $('#input').value = ''; autosize($('#input')); LS.set('draft.' + chatId, null); }
    S.reply = null; renderComposerBar();
    toast('Фото отправлено');
  } catch (err) { toast(err.message); }
}

async function compressImage(file) {
  if (file.type === 'image/gif' && file.size < 8e6) return file;
  const MAX = 1920;
  try {
    const bmp = await createImageBitmap(file, { imageOrientation: 'from-image' });
    const k = Math.min(1, MAX / Math.max(bmp.width, bmp.height));
    const c = document.createElement('canvas');
    c.width = Math.round(bmp.width * k); c.height = Math.round(bmp.height * k);
    const ctx = c.getContext('2d');
    ctx.fillStyle = '#fff'; // у JPEG нет прозрачности — иначе прозрачные области станут чёрными
    ctx.fillRect(0, 0, c.width, c.height);
    ctx.drawImage(bmp, 0, 0, c.width, c.height);
    return await new Promise((ok, fail) => c.toBlob((b) => (b ? ok(b) : fail(new Error('Не удалось обработать фото'))), 'image/jpeg', 0.85));
  } catch {
    if (['image/jpeg', 'image/png', 'image/webp'].includes(file.type)) return file;
    throw new Error('Этот формат изображения не поддерживается');
  }
}

function viewImage(src) {
  const v = h('div.viewer', { onclick: () => v.remove() }, h('img', { src, alt: '' }));
  document.body.append(v);
}

// ============ окна ============
function sheet(title, content, { action, onAction, danger } = {}) {
  const close = () => ov.remove();
  const btn = action ? h('button.btn', { class: danger ? 'danger' : '', onclick: () => onAction(close, btn) }, action) : null;
  const ov = h('div.overlay', { onclick: (e) => { if (e.target === ov) close(); } },
    h('div.sheet', { role: 'dialog', 'aria-label': title },
      h('div.topbar', h('h2', title), h('button.icon-btn', { title: 'Закрыть', onclick: close }, icon('close'))),
      h('div.content', content),
      btn ? h('div.foot', btn) : null));
  document.body.append(ov);
  return { close, el: ov };
}

function peoplePicker({ multi, exclude = new Set(), onPick }) {
  const selected = new Map();
  const chips = h('div.chips');
  const results = h('div');
  const input = h('input', { type: 'search', placeholder: 'Имя или @username', autocomplete: 'off', autocapitalize: 'off' });
  let timer;
  const show = (list) => {
    const rows = list.filter((u) => !exclude.has(u.id)).map((u) => h('button.row', {
      class: selected.has(u.id) ? 'sel' : '', style: 'padding:8px 4px',
      onclick: () => {
        if (!multi) return onPick(u);
        selected.has(u.id) ? selected.delete(u.id) : selected.set(u.id, u);
        renderChips(); show(list);
      },
    }, avatar(u, 'sm', { dot: true }),
    h('div.body', h('div.top', h('span.name', u.name)), h('div.bottom', h('span.preview', '@' + u.username))),
    multi ? h('span.check', { html: selected.has(u.id) ? I.check : '' }) : null));
    results.replaceChildren(...(rows.length ? rows : [h('div.empty', { style: 'padding:20px' }, input.value.trim().length < 2 ? 'Введите хотя бы 2 символа' : 'Никого не нашли')]));
  };
  const renderChips = () => chips.replaceChildren(...[...selected.values()].map((u) => h('span.chip', u.name)));
  input.addEventListener('input', () => {
    clearTimeout(timer);
    const q = input.value.trim();
    if (q.length < 2) {
      const known = [...S.users.values()].filter((u) => u.id !== S.me.id);
      return show(known);
    }
    timer = setTimeout(async () => {
      try { show((await api('GET', '/api/users/search?q=' + encodeURIComponent(q))).map(remember)); } catch (err) { toast(err.message); }
    }, 250);
  });
  setTimeout(() => { input.dispatchEvent(new Event('input')); if (!matchMedia('(pointer: coarse)').matches) input.focus(); });
  return { el: h('div', h('label.field', input), chips, results), selected };
}

function newChat() {
  let s;
  const picker = peoplePicker({ multi: false, onPick: (u) => { s.close(); startDirect(u.id); } });
  s = sheet('Новый чат', h('div',
    h('button.row', { style: 'padding:8px 4px;color:var(--accent)', onclick: () => { s.close(); newGroup(); } },
      h('div.avatar.sm', { style: '--c:#0f766e', html: I.group }), h('div.body', h('span.name', 'Создать группу'))),
    h('div.section-title', 'Найти человека'),
    picker.el));
}

async function startDirect(userId) {
  try {
    const chat = await api('POST', '/api/chats/direct', { userId });
    putChat(chat);
    $('#search').value = ''; S.filter = ''; peopleResults = [];
    renderList();
    openChat(chat.id);
  } catch (err) { toast(err.message); }
}

function newGroup() {
  const title = h('input', { placeholder: 'Название группы', maxlength: 64 });
  const picker = peoplePicker({ multi: true });
  sheet('Новая группа', h('div', h('label.field', h('span', 'Название'), title), h('div.section-title', 'Участники'), picker.el), {
    action: 'Создать группу',
    onAction: async (close, btn) => {
      if (!title.value.trim()) return toast('Введите название группы');
      btn.disabled = true;
      try {
        const chat = await api('POST', '/api/chats/group', { title: title.value, members: [...picker.selected.keys()] });
        putChat(chat); close(); renderList(); openChat(chat.id);
      } catch (err) { toast(err.message); btn.disabled = false; }
    },
  });
}

function openChatInfo() {
  const chat = S.chats.get(S.current);
  if (!chat) return;
  const peer = peerOf(chat);
  if (peer) {
    sheet('Профиль', h('div.profile',
      avatar(peer, 'lg'),
      h('h3', peer.name),
      h('div.u', '@' + peer.username),
      h('p', { style: 'color:var(--muted)' }, lastSeen(peer)),
      peer.bio ? h('p', peer.bio) : null));
    return;
  }
  let s;
  const members = chat.members.map((m) => {
    const u = user(m.id);
    return h('button.row', { style: 'padding:8px 4px', onclick: () => { if (u.id !== S.me.id) { s.close(); startDirect(u.id); } } },
      avatar(u, 'sm', { dot: true }),
      h('div.body', h('div.top', h('span.name', u.id === S.me.id ? `${u.name} (вы)` : u.name), m.role === 'owner' ? h('span.time', 'создатель') : null),
        h('div.bottom', h('span.preview', lastSeen(u)))));
  });
  s = sheet('Группа', h('div',
    h('div.profile', avatar(chat, 'lg'), h('h3', chat.title), h('div.u', `${chat.members.length} участников`)),
    h('button.btn.ghost', { style: 'margin-bottom:8px', onclick: () => { s.close(); renameGroup(chat); } }, 'Переименовать'),
    h('button.btn.ghost', { onclick: () => { s.close(); addMembers(chat); } }, 'Добавить участников'),
    h('div.section-title', 'Участники'),
    members,
    h('button.btn.danger', { style: 'margin-top:16px', onclick: async () => {
      if (!confirm(`Выйти из группы «${chat.title}»?`)) return;
      try { await api('POST', `/api/chats/${chat.id}/leave`); s.close(); } catch (err) { toast(err.message); }
    } }, 'Выйти из группы')));
}

function renameGroup(chat) {
  const input = h('input', { value: chat.title, maxlength: 64 });
  sheet('Название группы', h('label.field', input), {
    action: 'Сохранить',
    onAction: async (close) => {
      try { putChat(await api('PATCH', `/api/chats/${chat.id}`, { title: input.value })); close(); renderList(); renderHeader(); } catch (err) { toast(err.message); }
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
  const name = h('input', { value: me.name, maxlength: 64 });
  const bio = h('textarea', { rows: 2, maxlength: 200, placeholder: 'Пара слов о себе' });
  bio.value = me.bio || '';
  const theme = LS.get('theme', 'auto');
  const seg = h('div.seg', [['auto', 'Авто'], ['light', 'Светлая'], ['dark', 'Тёмная']].map(([v, label]) =>
    h('button', { class: v === theme ? 'on' : '', onclick: (e) => {
      LS.set('theme', v); applyTheme();
      [...seg.children].forEach((b) => b.classList.toggle('on', b === e.currentTarget));
    } }, label)));
  const notifyOn = LS.get('notify', true);
  const nseg = h('div.seg', [[true, 'Вкл'], [false, 'Выкл']].map(([v, label]) =>
    h('button', { class: v === notifyOn ? 'on' : '', onclick: (e) => {
      LS.set('notify', v);
      if (v && 'Notification' in window && !isNative) Notification.requestPermission().catch(() => {});
      [...nseg.children].forEach((b) => b.classList.toggle('on', b === e.currentTarget));
    } }, label)));

  sheet('Профиль и настройки', h('div',
    h('div.profile', avatar(me, 'lg'), h('h3', me.name), h('div.u', '@' + me.username)),
    h('label.field', h('span', 'Имя'), name),
    h('label.field', h('span', 'О себе'), bio),
    h('button.btn', { onclick: async (e) => {
      e.currentTarget.disabled = true;
      try { remember(await api('PATCH', '/api/me', { name: name.value, bio: bio.value })); toast('Сохранено'); renderList(); } catch (err) { toast(err.message); }
      e.currentTarget.disabled = false;
    } }, 'Сохранить профиль'),
    h('div.section-title', 'Тема'), seg,
    h('div.section-title', 'Уведомления'), nseg,
    h('div.section-title', 'Аккаунт'),
    h('button.btn.ghost', { style: 'margin-bottom:8px', onclick: changePassword }, 'Сменить пароль'),
    h('button.btn.danger', { onclick: () => { if (confirm('Выйти из аккаунта?')) { document.querySelector('.overlay')?.remove(); logout(); } } }, 'Выйти'),
    h('p', { style: 'text-align:center;color:var(--muted);font-size:13px;margin-top:18px' },
      `Компас · сервер ${serverBase().replace(/^https?:\/\//, '')}`),
  ));
}

function changePassword() {
  const oldP = h('input', { type: 'password', autocomplete: 'current-password' });
  const newP = h('input', { type: 'password', autocomplete: 'new-password', minlength: 6 });
  sheet('Смена пароля', h('div', h('label.field', h('span', 'Текущий пароль'), oldP), h('label.field', h('span', 'Новый пароль'), newP)), {
    action: 'Сменить',
    onAction: async (close) => {
      try { await api('POST', '/api/me/password', { oldPassword: oldP.value, newPassword: newP.value }); close(); toast('Пароль изменён'); } catch (err) { toast(err.message); }
    },
  });
}

function applyTheme() {
  const t = LS.get('theme', 'auto');
  if (t === 'auto') document.documentElement.removeAttribute('data-theme');
  else document.documentElement.setAttribute('data-theme', t);
}

// ============ запуск ============
applyTheme();
if (!isNative && 'serviceWorker' in navigator && (location.protocol === 'https:' || location.hostname === 'localhost')) {
  navigator.serviceWorker?.register('sw.js').catch(() => {});
  navigator.serviceWorker?.addEventListener('message', (e) => { if (e.data?.type === 'open-chat' && S.token) openChat(e.data.chatId); });
}
window.addEventListener('focus', () => {
  if (S.current) { const last = S.messages.get(S.current)?.at(-1); if (last) markRead(S.current, last.id); }
});
if (S.token && S.me) start();
else renderAuth();
