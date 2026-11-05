// Ядро Mini App: авторизация, API, навигация, общие UI-хелперы и анимации.
import { initTheme, applyTheme } from './theme.js';

// Палитра и сезонный эффект из localStorage — сразу, чтобы не было «мигания» цветов
initTheme();
const tg = window.Telegram?.WebApp;
if (tg?.initData) {
  document.documentElement.classList.add('tg');
  tg.ready();
  tg.expand();
  try { tg.setHeaderColor('secondary_bg_color'); tg.setBackgroundColor('secondary_bg_color'); } catch {}
}
export const tgUser = tg?.initDataUnsafe?.user || null;

export async function api(path, { method = 'GET', body } = {}) {
  const res = await fetch(path, {
    method,
    headers: { 'content-type': 'application/json', authorization: `tma ${tg?.initData || ''}` },
    body: body ? JSON.stringify(body) : undefined,
  });
  const data = await res.json().catch(() => ({}));
  if (!res.ok) throw new Error(data.error || `Ошибка ${res.status}`);
  return data;
}

// ---------- форматирование ----------
export const esc = (s) => String(s ?? '').replace(/[&<>"']/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' })[c]);
export const fmt = (n) => Number(n || 0).toLocaleString('ru-RU');
export const date = (ts) => new Date(ts * 1000).toLocaleString('ru-RU', { day: '2-digit', month: 'short', hour: '2-digit', minute: '2-digit' });
export const signed = (n) => (n == null ? '—' : `<span class="${n > 0 ? 'up' : n < 0 ? 'down' : ''}">${n > 0 ? '+' : ''}${fmt(n)}</span>`);
export function ago(ts) {
  const s = Math.max(0, Date.now() / 1000 - ts);
  if (s < 60) return 'только что';
  if (s < 3600) return `${Math.floor(s / 60)} мин`;
  if (s < 86400) return `${Math.floor(s / 3600)} ч`;
  if (s < 86400 * 7) return `${Math.floor(s / 86400)} дн`;
  return new Date(ts * 1000).toLocaleDateString('ru-RU', { day: 'numeric', month: 'short' });
}
export const plural = (n, one, few, many) => {
  const m10 = n % 10, m100 = n % 100;
  return m10 === 1 && m100 !== 11 ? one : m10 >= 2 && m10 <= 4 && (m100 < 10 || m100 >= 20) ? few : many;
};
/** Цвет обложки по строке — у каждого товара/канала свой стабильный градиент */
export function hue(str) {
  let h = 0;
  for (const c of String(str)) h = (h * 31 + c.charCodeAt(0)) % 360;
  // только насыщенные оттенки (синие → фиолетовые → розовые → красные), на которых читается белый текст
  const a = 190 + (h % 180), b = a + 35;
  return `linear-gradient(135deg, hsl(${a % 360} 78% 56%), hsl(${b % 360} 80% 48%))`;
}

// ---------- обратная связь ----------
export function toast(text) {
  const el = document.getElementById('toast');
  el.textContent = text;
  el.hidden = false;
  el.style.animation = 'none'; el.offsetHeight; el.style.animation = '';
  clearTimeout(toast.t);
  toast.t = setTimeout(() => (el.hidden = true), 2600);
}
export const haptic = (type = 'success') => tg?.HapticFeedback?.notificationOccurred(type);
export const tap = () => tg?.HapticFeedback?.impactOccurred('light');

export function openInvoice(link) {
  return new Promise((resolve) => {
    if (!tg?.openInvoice) { window.open(link, '_blank'); return resolve('pending'); }
    tg.openInvoice(link, resolve);
  });
}
export function confirm(text) {
  return new Promise((resolve) => (tg?.showConfirm ? tg.showConfirm(text, resolve) : resolve(window.confirm(text))));
}
export function share(url, text) {
  const u = `https://t.me/share/url?url=${encodeURIComponent(url)}&text=${encodeURIComponent(text)}`;
  tg?.openTelegramLink ? tg.openTelegramLink(u) : window.open(u, '_blank');
}
/** Копирование в буфер: Clipboard API, иначе запасной путь через execCommand, иначе показать текст */
export async function copy(text) {
  try {
    await navigator.clipboard.writeText(text);
  } catch {
    const ta = document.createElement('textarea');
    ta.value = text; ta.setAttribute('readonly', ''); ta.style.cssText = 'position:fixed;top:0;left:0;opacity:0;pointer-events:none';
    document.body.append(ta); ta.select(); ta.setSelectionRange(0, text.length);
    let ok = false;
    try { ok = document.execCommand('copy'); } catch {}
    ta.remove();
    if (!ok) return toast(text.length > 200 ? 'Не удалось скопировать — выделите текст вручную' : text);
  }
  toast('Скопировано ✓'); tap();
}

/** Кнопка «Назад» Telegram (в браузере — без неё). fn = null убирает кнопку. */
let backFn = null;
export function setBack(fn) {
  if (backFn) tg?.BackButton?.offClick(backFn);
  backFn = fn;
  if (fn) { tg?.BackButton?.onClick(fn); tg?.BackButton?.show(); } else tg?.BackButton?.hide();
}

/** Нижняя шторка */
export function sheet(html) {
  const bg = document.createElement('div'); bg.className = 'sheet-bg';
  const el = document.createElement('div'); el.className = 'sheet fx'; el.innerHTML = html;
  const close = () => { bg.remove(); el.remove(); };
  bg.onclick = close;
  el.addEventListener('click', (e) => e.target.closest('[data-close]') && close());
  document.body.append(bg, el);
  return { el, close };
}

// ---------- анимации ----------
/** Плавно «накручивает» числа у элементов с data-count */
export function animateCounts(root) {
  root.querySelectorAll('[data-count]').forEach((el) => {
    const to = Number(el.dataset.count) || 0;
    const from = Number(el.dataset.from ?? 0);
    const suffix = el.dataset.suffix || '';
    const t0 = performance.now(), dur = 900;
    const step = (t) => {
      const k = Math.min(1, (t - t0) / dur), e = 1 - (1 - k) ** 3;
      el.textContent = fmt(Math.round(from + (to - from) * e)) + suffix;
      if (k < 1) requestAnimationFrame(step);
    };
    requestAnimationFrame(step);
  });
}
export const count = (n, suffix = '', from = 0) => `<span data-count="${Number(n) || 0}" data-from="${from}" data-suffix="${esc(suffix)}">${fmt(from)}${esc(suffix)}</span>`;

/** Конфетти без библиотек */
export function confetti() {
  const c = document.createElement('canvas'); c.id = 'confetti';
  const dpr = devicePixelRatio || 1;
  c.width = innerWidth * dpr; c.height = innerHeight * dpr;
  document.body.append(c);
  const ctx = c.getContext('2d'); ctx.scale(dpr, dpr);
  const colors = ['#7b5cff', '#33a6ff', '#ff5ca8', '#ffb020', '#22c55e'];
  const ps = Array.from({ length: 120 }, () => ({
    x: innerWidth / 2, y: innerHeight * 0.35, vx: (Math.random() - 0.5) * 12, vy: -Math.random() * 12 - 4,
    s: 4 + Math.random() * 5, r: Math.random() * 6, vr: (Math.random() - 0.5) * 0.3, c: colors[(Math.random() * colors.length) | 0],
  }));
  const t0 = performance.now();
  (function frame(t) {
    ctx.clearRect(0, 0, innerWidth, innerHeight);
    for (const p of ps) {
      p.vy += 0.35; p.vx *= 0.99; p.x += p.vx; p.y += p.vy; p.r += p.vr;
      ctx.save(); ctx.translate(p.x, p.y); ctx.rotate(p.r); ctx.fillStyle = p.c;
      ctx.globalAlpha = Math.max(0, 1 - (t - t0) / 2200); ctx.fillRect(-p.s / 2, -p.s / 4, p.s, p.s / 2); ctx.restore();
    }
    if (t - t0 < 2200) requestAnimationFrame(frame); else c.remove();
  })(t0);
}

export const skeleton = (...heights) => `<div>${heights.map((h) => `<div class="sk" style="height:${h}px"></div>`).join('')}</div>`;
export const empty = (icon, text) => `<div class="empty"><span class="big-ico">${icon}</span>${text}</div>`;

// ---------- графики ----------
let chartId = 0;
/**
 * График без зависимостей. type: 'line' | 'bar'. Касание/наведение показывает значение дня.
 * opts.mini — компактная спарклайн-версия без подписей.
 */
export function chart(points, { type = 'line', key = 'value', height = 130, mini = false, unit = '' } = {}) {
  const id = ++chartId;
  const w = 320, h = mini ? 36 : height, padB = mini ? 2 : 18, padT = mini ? 4 : 14;
  const vals = points.map((p) => Number(p[key]) || 0);
  const max = Math.max(1, ...vals);
  const min = type === 'line' && !mini ? Math.min(...vals) * 0.9 : 0;
  const span = Math.max(1, max - min);
  const n = Math.max(1, vals.length);
  const x = (i) => (type === 'bar' ? (i + 0.5) * (w / n) : n === 1 ? w / 2 : (i / (n - 1)) * w);
  const y = (v) => h - padB - ((v - min) / span) * (h - padB - padT);
  const defs = `<defs>
    <linearGradient id="g-line" x1="0" x2="1"><stop offset="0" style="stop-color:var(--a1)"/><stop offset="1" style="stop-color:var(--a2)"/></linearGradient>
    <linearGradient id="g-area${id}" x1="0" x2="0" y1="0" y2="1"><stop offset="0" style="stop-color:var(--a1)" stop-opacity=".28"/><stop offset="1" style="stop-color:var(--a2)" stop-opacity="0"/></linearGradient>
    <linearGradient id="g-bar${id}" x1="0" x2="0" y1="0" y2="1"><stop offset="0" style="stop-color:var(--a1)"/><stop offset="1" style="stop-color:var(--a2)"/></linearGradient></defs>`;
  let body = '';
  if (type === 'bar') {
    const bw = (w / n) * 0.62;
    body = vals.map((v, i) => `<rect class="bar" style="animation-delay:${i * 15}ms" rx="${Math.min(4, bw / 2)}" x="${x(i) - bw / 2}" y="${y(v)}" width="${bw}" height="${Math.max(0, h - padB - y(v))}" fill="url(#g-bar${id})"/>`).join('');
  } else {
    // сглаженная линия (Catmull-Rom → кривые Безье)
    const P = vals.map((v, i) => [x(i), y(v)]);
    let d = `M${P[0]?.[0] ?? 0},${P[0]?.[1] ?? h}`;
    for (let i = 0; i < P.length - 1; i++) {
      const p0 = P[i - 1] || P[i], p1 = P[i], p2 = P[i + 1], p3 = P[i + 2] || p2;
      d += ` C${p1[0] + (p2[0] - p0[0]) / 6},${p1[1] + (p2[1] - p0[1]) / 6} ${p2[0] - (p3[0] - p1[0]) / 6},${p2[1] - (p3[1] - p1[1]) / 6} ${p2[0]},${p2[1]}`;
    }
    body = `<path d="${d} L${w},${h - padB} L0,${h - padB} Z" fill="url(#g-area${id})"/><path class="line" d="${d}"/>`;
  }
  const grid = mini ? '' : [0.5, 1].map((k) => `<line class="grid-line" x1="0" x2="${w}" y1="${y(min + span * k)}" y2="${y(min + span * k)}"/>`).join('');
  const labels = mini ? '' : `<text x="0" y="${h - 2}">${points[0]?.day?.slice(5).split('-').reverse().join('.') ?? ''}</text>
    <text x="${w}" y="${h - 2}" text-anchor="end">${points.at(-1)?.day?.slice(5).split('-').reverse().join('.') ?? ''}</text>
    <text x="${w}" y="${padT - 4}" text-anchor="end">${fmt(max)}</text>`;
  const data = esc(JSON.stringify(points.map((p) => [p.day, Number(p[key]) || 0])));
  return `<div class="chart-wrap" data-chart='${data}' data-type="${type}" data-unit="${esc(unit)}" data-h="${h}" data-pb="${padB}" data-pt="${padT}" data-min="${min}" data-span="${span}">
    <svg class="chart" style="height:${h}px" viewBox="0 0 ${w} ${h}" preserveAspectRatio="none">${defs}${grid}${body}${labels}<g class="hover"></g></svg></div>`;
}

// Общий обработчик подсказок для всех графиков
function chartHover(e) {
  const wrap = e.target.closest?.('.chart-wrap');
  document.querySelectorAll('.chart-wrap .tip').forEach((t) => t.closest('.chart-wrap') !== wrap && t.remove());
  document.querySelectorAll('.chart-wrap .hover').forEach((g) => g.closest('.chart-wrap') !== wrap && (g.innerHTML = ''));
  if (!wrap || wrap.querySelector('.chart').style.height === '36px') return;
  const pts = JSON.parse(wrap.dataset.chart);
  const rect = wrap.getBoundingClientRect();
  const cx = ((e.touches?.[0] || e).clientX - rect.left) / rect.width;
  const n = pts.length, bar = wrap.dataset.type === 'bar';
  const i = Math.max(0, Math.min(n - 1, bar ? Math.floor(cx * n) : Math.round(cx * (n - 1))));
  const fx = bar ? (i + 0.5) / n : n === 1 ? 0.5 : i / (n - 1);
  const h = +wrap.dataset.h, pb = +wrap.dataset.pb, pt = +wrap.dataset.pt, min = +wrap.dataset.min, span = +wrap.dataset.span;
  const yy = h - pb - ((pts[i][1] - min) / span) * (h - pb - pt);
  wrap.querySelector('.hover').innerHTML = `<line class="cursor" x1="${fx * 320}" x2="${fx * 320}" y1="${pt}" y2="${h - pb}"/>${bar ? '' : `<circle class="dot" cx="${fx * 320}" cy="${yy}" r="4.5" vector-effect="non-scaling-stroke"/>`}`;
  let tip = wrap.querySelector('.tip');
  if (!tip) { tip = document.createElement('div'); tip.className = 'tip'; wrap.append(tip); }
  tip.textContent = `${pts[i][0].slice(5).split('-').reverse().join('.')} · ${fmt(pts[i][1])}${wrap.dataset.unit}`;
  tip.style.left = `${Math.min(Math.max(fx * 100, 12), 88)}%`;
  if (wrap.dataset.last !== String(i)) { wrap.dataset.last = i; tg?.HapticFeedback?.selectionChanged(); }
}
document.addEventListener('pointermove', chartHover);
document.addEventListener('pointerdown', chartHover);
document.addEventListener('touchmove', chartHover, { passive: true });

// ---------- навигация ----------
const view = document.getElementById('view');
const tabs = document.getElementById('tabs');
export const app = { me: null, api, refreshMe, timers: [] };

function renderTop() {
  const u = app.me.user || {};
  const name = u.first_name || tgUser?.first_name || 'Гость';
  const photo = tgUser?.photo_url;
  document.getElementById('top').innerHTML = `
    <div class="avatar">${photo ? `<img src="${esc(photo)}" alt="">` : esc(name[0] || '⭐')}</div>
    <div class="who"><div class="name">${esc(name)}</div><div class="lvl" id="top-lvl">⭐ StarApp</div></div>
    <div class="chip clickable" id="top-bal"><span class="live"></span><span id="bal">${fmt(u.balance)}</span> ⭐</div>`;
  document.getElementById('top-bal').onclick = () => open('profile');
}

async function refreshMe() {
  const prev = app.me?.user?.balance;
  app.me = await api('/api/me');
  if (document.getElementById('bal')) {
    const el = document.getElementById('bal');
    el.dataset.count = app.me.user?.balance ?? 0;
    el.dataset.from = prev ?? 0;
    animateCounts(el.parentElement);
  } else renderTop();
  return app.me;
}
app.setLevel = (lvl) => { const el = document.getElementById('top-lvl'); if (el) el.textContent = `Уровень ${lvl}`; };

let current = null;
async function open(id) {
  current = id;
  if (location.hash.slice(1) !== id) location.hash = id;
  tabs.querySelectorAll('button').forEach((b) => b.classList.toggle('active', b.dataset.id === id));
  app.timers.forEach(clearInterval); app.timers = [];
  setBack(null);
  window.scrollTo({ top: 0 });
  view.innerHTML = skeleton(150, 90, 220);
  try {
    const mod = await import(`./services/${id}.js`);
    await mod.render(view, app);
    view.classList.remove('fx'); view.offsetHeight; view.classList.add('fx');
    [...view.children].forEach((c, i) => c.style.setProperty('--i', i));
    animateCounts(view);
  } catch (e) {
    view.innerHTML = empty('⚠️', esc(e.message));
  }
}
app.open = open;
/** Перерисовать текущий экран после действия (без мигания скелетоном) */
app.rerender = async (mod) => { await mod.render(view, app); animateCounts(view); };

try {
  await refreshMe();
  renderTop();
  // Оформление с сервера (синхронизация между устройствами), если сервис eco подключён
  if (app.me.services.some((s) => s.id === 'eco')) api('/api/eco/settings').then((s) => applyTheme(s)).catch(() => {});
  tabs.innerHTML = app.me.services.map((s) => `<button data-id="${s.id}"><span class="ico">${s.icon}</span>${esc(s.title)}</button>`).join('');
  tabs.addEventListener('click', (e) => { const b = e.target.closest('button'); if (b && b.dataset.id !== current) { tap(); open(b.dataset.id); } });
  window.addEventListener('hashchange', () => {
    const id = location.hash.slice(1);
    if (id !== current && app.me.services.some((s) => s.id === id)) open(id);
  });
  const initial = location.hash.slice(1);
  open(app.me.services.some((s) => s.id === initial) ? initial : app.me.services[0].id);
} catch (e) {
  view.innerHTML = empty('🔒', `${esc(e.message)}<br><br>Откройте приложение из бота в Telegram.`);
}
