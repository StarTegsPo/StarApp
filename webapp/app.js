// Ядро Mini App: авторизация, API, навигация по сервисам, общие UI-хелперы.
const tg = window.Telegram?.WebApp;
if (tg?.initData) {
  document.documentElement.classList.add('tg');
  tg.ready();
  tg.expand();
}

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

export const esc = (s) => String(s ?? '').replace(/[&<>"']/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' })[c]);
export const fmt = (n) => Number(n || 0).toLocaleString('ru-RU');
export const date = (ts) => new Date(ts * 1000).toLocaleString('ru-RU', { day: '2-digit', month: 'short', hour: '2-digit', minute: '2-digit' });
export const signed = (n) => (n == null ? '—' : `<span class="${n > 0 ? 'up' : n < 0 ? 'down' : ''}">${n > 0 ? '+' : ''}${fmt(n)}</span>`);

export function toast(text) {
  const el = document.getElementById('toast');
  el.textContent = text;
  el.hidden = false;
  clearTimeout(toast.t);
  toast.t = setTimeout(() => (el.hidden = true), 2500);
}

export const haptic = (type = 'success') => tg?.HapticFeedback?.notificationOccurred(type);

/** Открывает счёт Telegram Stars; резолвится статусом: paid | cancelled | failed | pending */
export function openInvoice(link) {
  return new Promise((resolve) => {
    if (!tg?.openInvoice) {
      window.open(link, '_blank');
      return resolve('pending');
    }
    tg.openInvoice(link, resolve);
  });
}

export function confirm(text) {
  return new Promise((resolve) => (tg?.showConfirm ? tg.showConfirm(text, resolve) : resolve(window.confirm(text))));
}

/** Мини-график без зависимостей. type: 'bar' | 'line' */
export function chart(points, { type = 'bar', key = 'value', height = 120 } = {}) {
  const w = 320, h = height, pad = 14;
  const vals = points.map((p) => Number(p[key]) || 0);
  const max = Math.max(1, ...vals);
  const min = type === 'line' ? Math.min(...vals) : 0;
  const span = Math.max(1, max - min);
  const step = (w - 2) / Math.max(1, vals.length);
  const y = (v) => h - pad - ((v - min) / span) * (h - pad * 2);
  let body = '';
  if (type === 'bar') {
    body = vals.map((v, i) => `<rect class="bar" rx="2" x="${1 + i * step + step * 0.15}" y="${y(v)}" width="${step * 0.7}" height="${h - pad - y(v)}"><title>${points[i].day}: ${v}</title></rect>`).join('');
  } else {
    const pts = vals.map((v, i) => `${1 + i * step + step / 2},${y(v)}`);
    body = `<polygon class="area" points="${pts[0]?.split(',')[0]},${h - pad} ${pts.join(' ')} ${pts.at(-1)?.split(',')[0]},${h - pad}"/><polyline class="line" points="${pts.join(' ')}"/>`;
  }
  const first = points[0]?.day?.slice(5) ?? '', last = points.at(-1)?.day?.slice(5) ?? '';
  return `<svg class="chart" viewBox="0 0 ${w} ${h}" preserveAspectRatio="none">${body}
    <text x="2" y="${h - 2}">${first}</text><text x="${w - 2}" y="${h - 2}" text-anchor="end">${last}</text>
    <text x="${w - 2}" y="10" text-anchor="end">${fmt(max)}</text></svg>`;
}

// ---------- Навигация ----------
const view = document.getElementById('view');
const tabs = document.getElementById('tabs');
export const app = { me: null, api, refreshMe };

async function refreshMe() {
  app.me = await api('/api/me');
  document.getElementById('balance').textContent = `${fmt(app.me.user?.balance)} ⭐`;
  return app.me;
}

let current = null;
async function open(id) {
  current = id;
  if (location.hash.slice(1) !== id) location.hash = id;
  tabs.querySelectorAll('button').forEach((b) => b.classList.toggle('active', b.dataset.id === id));
  tg?.BackButton?.hide();
  view.innerHTML = '<div class="empty">Загрузка…</div>';
  try {
    const mod = await import(`./services/${id}.js`);
    await mod.render(view, app);
  } catch (e) {
    view.innerHTML = `<div class="empty">⚠️ ${esc(e.message)}</div>`;
  }
}
app.open = open;

try {
  await refreshMe();
  tabs.innerHTML = app.me.services
    .map((s) => `<button data-id="${s.id}"><span class="ico">${s.icon}</span>${esc(s.title)}</button>`)
    .join('');
  tabs.addEventListener('click', (e) => {
    const b = e.target.closest('button');
    if (b) open(b.dataset.id);
  });
  window.addEventListener('hashchange', () => {
    const id = location.hash.slice(1);
    if (id !== current && app.me.services.some((s) => s.id === id)) open(id);
  });
  const initial = location.hash.slice(1);
  open(app.me.services.some((s) => s.id === initial) ? initial : app.me.services[0].id);
} catch (e) {
  view.innerHTML = `<div class="empty">⚠️ ${esc(e.message)}<br><br>Откройте приложение из бота в Telegram.</div>`;
}
