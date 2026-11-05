import { api, esc, fmt, signed, chart, toast, confirm } from '../app.js';

let section = 'overview';
let days = 30;

const stat = (label, value, sub = '') => `<div class="stat"><div class="label">${label}</div><div class="value">${value}</div>${sub ? `<div class="sub">${sub}</div>` : ''}</div>`;

export async function render(el, app) {
  const seg = [['overview', 'Сводка'], ['channels', 'Каналы'], ['bots', 'Боты'], ['purchases', 'Покупки']];
  el.innerHTML = `
    <div class="seg" id="sec">${seg.map(([id, t]) => `<button data-id="${id}" class="${id === section ? 'active' : ''}">${t}</button>`).join('')}</div>
    <div class="seg" id="days">${[7, 30, 90].map((d) => `<button data-d="${d}" class="${d === days ? 'active' : ''}">${d} дн.</button>`).join('')}</div>
    <div id="body"><div class="empty">Загрузка…</div></div>`;
  el.querySelector('#sec').onclick = (e) => { const b = e.target.closest('button'); if (b) { section = b.dataset.id; render(el, app); } };
  el.querySelector('#days').onclick = (e) => { const b = e.target.closest('button'); if (b) { days = Number(b.dataset.d); render(el, app); } };
  const body = el.querySelector('#body');
  try {
    await ({ overview, channels, bots, purchases })[section](body, app);
  } catch (e) {
    body.innerHTML = `<div class="empty">⚠️ ${esc(e.message)}</div>`;
  }
}

async function overview(el, app) {
  const o = await api(`/api/analytics/overview?days=${days}`);
  el.innerHTML = `
    <h3>Каналы</h3>
    <div class="grid">
      ${stat('Подписчики', fmt(o.channels.members), `${signed(o.channels.growth)} за ${days} дн.`)}
      ${stat('Постов', fmt(o.channels.posts), `${o.channels.count} канал(ов)`)}
    </div>
    <h3>Боты</h3>
    <div class="grid">
      ${stat('Пользователи', fmt(o.bots.users), `DAU ${fmt(o.bots.dau)}`)}
      ${stat('Событий', fmt(o.bots.events), `${o.bots.count} бот(ов)`)}
    </div>
    <h3>Покупки</h3>
    <div class="grid">
      ${stat('Выручка', `${fmt(o.purchases.revenue)} ⭐`, `${fmt(o.purchases.orders)} заказ(ов)`)}
      ${stat('Средний чек', `${fmt(o.purchases.avgCheck)} ⭐`)}
    </div>
    <div class="card">${chart(o.revenueDaily, { key: 'revenue' })}</div>
    ${o.channels.count === 0 && o.bots.count === 0 ? `<div class="card">
      <b>С чего начать</b>
      <p>📢 Добавьте ${app.me.botUsername ? `@${esc(app.me.botUsername)}` : 'бота'} администратором в свой канал — статистика начнёт собираться автоматически.</p>
      <p>🤖 Подключите свои боты во вкладке «Боты» и отправляйте события через API.</p></div>` : ''}`;
}

async function channels(el, app) {
  const { channels } = await api(`/api/analytics/channels?days=${days}`);
  if (!channels.length) {
    el.innerHTML = `<div class="card empty">Каналов пока нет.<br><br>Добавьте ${app.me.botUsername ? `@${esc(app.me.botUsername)}` : 'бота'} администратором в канал или группу — он появится здесь.</div>`;
    return;
  }
  el.innerHTML = `<div class="card">${channels.map((c) => `
    <div class="row clickable" data-id="${c.chat_id}">
      <div class="main"><div class="title">${esc(c.title || c.username || c.chat_id)}${c.active ? '' : ' <span class="badge">отключён</span>'}</div>
        <div class="meta">${fmt(c.posts)} постов · +${fmt(c.joins)} / −${fmt(c.leaves)}</div></div>
      <div style="text-align:right"><b>${c.members != null ? fmt(c.members) : '—'}</b><div class="meta">${signed(c.growth)}</div></div>
    </div>`).join('')}</div>`;
  el.onclick = (e) => { const r = e.target.closest('.row'); if (r) channelDetail(el, app, r.dataset.id); };
}

async function channelDetail(el, app, id) {
  el.onclick = null;
  const r = await api(`/api/analytics/channels/${id}?days=${days}`);
  const sum = (arr, k) => arr.reduce((s, d) => s + (d[k] || 0), 0);
  el.innerHTML = `
    <span class="back" id="back">‹ Все каналы</span>
    <h2>${esc(r.channel.title || r.channel.username)}</h2>
    <div class="card"><b>Подписчики</b>${chart(r.members, { type: 'line' })}</div>
    <div class="grid">
      ${stat('Пришло', `+${fmt(sum(r.flow, 'joins'))}`)}
      ${stat('Ушло', `−${fmt(sum(r.flow, 'leaves'))}`)}
    </div>
    <div class="card"><b>Посты по дням</b>${chart(r.posts)}</div>
    <div class="card"><b>Форматы постов</b>
      ${r.postKinds.length ? r.postKinds.map((k) => `<div class="row"><span>${esc(k.kind)}</span><b>${fmt(k.n)}</b></div>`).join('') : '<div class="meta">Нет данных</div>'}
    </div>
    <div class="card"><b>Самые активные часы публикаций (UTC)</b>
      <div class="meta">${r.topHoursUtc.map((h) => `${h.hour}:00 — ${h.n} пост.`).join(' · ') || 'Нет данных'}</div>
    </div>
    <button class="btn secondary" id="refresh">Обновить число подписчиков</button>`;
  el.querySelector('#back').onclick = () => channels(el, app);
  el.querySelector('#refresh').onclick = async () => {
    const { members } = await api(`/api/analytics/channels/${id}/refresh`, { method: 'POST' });
    toast(members != null ? `Подписчиков: ${fmt(members)}` : 'Не удалось получить данные');
    channelDetail(el, app, id);
  };
}

async function bots(el, app) {
  const { bots } = await api(`/api/analytics/bots?days=${days}`);
  el.innerHTML = `
    <div class="card">${bots.length ? bots.map((b) => `
      <div class="row clickable" data-id="${b.id}">
        <div class="main"><div class="title">🤖 ${esc(b.name)}</div><div class="meta">DAU ${fmt(b.dau)} · ${fmt(b.events)} событий</div></div>
        <div style="text-align:right"><b>${fmt(b.users)}</b><div class="meta">${fmt(b.revenue)} ⭐</div></div>
      </div>`).join('') : '<div class="empty">Подключённых ботов нет</div>'}</div>
    <div class="card form">
      <b>Подключить бота</b>
      <input id="name" placeholder="Название, например @my_shop_bot" maxlength="64" />
      <button class="btn" id="add">Получить API-ключ</button>
    </div>`;
  el.onclick = (e) => { const r = e.target.closest('.row.clickable'); if (r) botDetail(el, app, r.dataset.id); };
  el.querySelector('#add').onclick = async (e) => {
    e.stopPropagation();
    const name = el.querySelector('#name').value.trim();
    if (!name) return toast('Введите название');
    const b = await api('/api/analytics/bots', { method: 'POST', body: { name } });
    botDetail(el, app, b.id);
  };
}

async function botDetail(el, app, id) {
  el.onclick = null;
  const r = await api(`/api/analytics/bots/${id}?days=${days}`);
  const trackUrl = `${location.origin}/api/track`;
  el.innerHTML = `
    <span class="back" id="back">‹ Все боты</span>
    <h2>🤖 ${esc(r.bot.name)}</h2>
    <div class="grid">
      ${stat('Покупателей', fmt(r.buyers))}
      ${stat('Конверсия в покупку', `${r.conversion}%`)}
    </div>
    <div class="card"><b>Активные пользователи</b>${chart(r.daily, { key: 'users' })}</div>
    <div class="card"><b>Новые пользователи</b>${chart(r.newUsers)}</div>
    <div class="card"><b>Выручка, ⭐</b>${chart(r.daily, { key: 'revenue' })}</div>
    <div class="card"><b>Топ событий</b>
      ${r.topEvents.length ? r.topEvents.map((t) => `<div class="row"><span>${esc(t.event)}</span><b>${fmt(t.n)}</b></div>`).join('') : '<div class="meta">Событий ещё не было</div>'}
    </div>
    <div class="card"><b>Подключение</b>
      <p class="meta">Отправляйте события из своего бота POST-запросом:</p>
      <div class="code">POST ${esc(trackUrl)}
Content-Type: application/json

{"api_key": "${esc(r.bot.api_key)}",
 "user_id": 123456789,
 "event": "start"}

// покупка:
{"api_key": "…", "user_id": 123456789,
 "event": "purchase", "amount": 150}</div>
    </div>
    <button class="btn danger" id="del">Удалить бота и его данные</button>`;
  el.querySelector('#back').onclick = () => bots(el, app);
  el.querySelector('#del').onclick = async () => {
    if (!(await confirm('Удалить бота и всю статистику?'))) return;
    await api(`/api/analytics/bots/${id}`, { method: 'DELETE' });
    bots(el, app);
  };
}

async function purchases(el, app) {
  const p = await api(`/api/analytics/purchases?days=${days}`);
  el.innerHTML = `
    <div class="grid">
      ${stat('Выручка', `${fmt(p.revenue)} ⭐`)}
      ${stat('Заказов', fmt(p.orders), `средний чек ${fmt(p.avgCheck)} ⭐`)}
    </div>
    <div class="card"><b>Выручка по дням</b>${chart(p.daily, { key: 'revenue' })}</div>
    <div class="card"><b>Заказы по дням</b>${chart(p.daily, { key: 'orders' })}</div>
    ${app.me.isAdmin ? `<div class="card"><b>Топ товаров магазина</b>
      ${p.topProducts.length ? p.topProducts.map((t) => `<div class="row"><div class="main"><div class="title">${esc(t.title)}</div><div class="meta">${fmt(t.orders)} заказ(ов)</div></div><b>${fmt(t.revenue)} ⭐</b></div>`).join('') : '<div class="meta">Продаж пока нет</div>'}
    </div>` : ''}
    <p class="meta">Учитываются события <code>purchase</code> из подключённых ботов${app.me.isAdmin ? ' и продажи магазина StarApp' : ''}.</p>`;
}
