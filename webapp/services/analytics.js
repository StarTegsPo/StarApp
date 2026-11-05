import { api, esc, fmt, signed, chart, toast, confirm, count, copy, setBack, skeleton, empty, hue, tap, animateCounts, plural } from '../app.js';

let section = 'overview';
let days = 30;

const stat = (icon, label, value, sub = '') =>
  `<div class="stat"><div class="label">${icon} ${label}</div><div class="value">${value}</div>${sub ? `<div class="sub">${sub}</div>` : ''}</div>`;

export async function render(el, app) {
  if (app.next?.section) { section = app.next.section; app.next = null; }
  const seg = [['overview', 'Сводка'], ['channels', 'Каналы'], ['bots', 'Боты'], ['purchases', 'Продажи']];
  el.innerHTML = `
    <h2>📊 Аналитика</h2>
    <div class="seg" id="sec">${seg.map(([id, t]) => `<button data-id="${id}" class="${id === section ? 'active' : ''}">${t}</button>`).join('')}</div>
    <div class="seg mini" id="days" style="width:60%">${[7, 30, 90].map((d) => `<button data-d="${d}" class="${d === days ? 'active' : ''}">${d} дн</button>`).join('')}</div>
    <div id="body" class="fx">${skeleton(110, 180)}</div>`;
  const body = el.querySelector('#body');
  const reload = async () => {
    setBack(null);
    body.innerHTML = skeleton(110, 180);
    try {
      await ({ overview, channels, bots, purchases })[section](body, app);
      [...body.children].forEach((c, i) => c.style.setProperty('--i', i));
      body.classList.remove('fx'); body.offsetHeight; body.classList.add('fx');
      animateCounts(body);
    } catch (e) {
      body.innerHTML = empty('⚠️', esc(e.message));
    }
  };
  el.querySelector('#sec').onclick = (e) => {
    const b = e.target.closest('button'); if (!b || b.dataset.id === section) return;
    tap(); section = b.dataset.id;
    el.querySelectorAll('#sec button').forEach((x) => x.classList.toggle('active', x === b)); reload();
  };
  el.querySelector('#days').onclick = (e) => {
    const b = e.target.closest('button'); if (!b) return;
    tap(); days = Number(b.dataset.d);
    el.querySelectorAll('#days button').forEach((x) => x.classList.toggle('active', x === b)); reload();
  };
  await reload();
}

const show = async (el, fn) => {
  el.innerHTML = skeleton(110, 180);
  await fn();
  [...el.children].forEach((c, i) => c.style.setProperty('--i', i));
  el.classList.remove('fx'); el.offsetHeight; el.classList.add('fx');
  animateCounts(el);
};

async function overview(el, app) {
  const o = await api(`/api/analytics/overview?days=${days}`);
  const nothing = o.channels.count === 0 && o.bots.count === 0;
  el.innerHTML = `
    <section class="card">
      <div class="row" style="padding:0;border:0"><div class="main"><div class="meta">Выручка за ${days} ${plural(days, 'день', 'дня', 'дней')}</div>
        <div style="font-size:30px;font-weight:750;letter-spacing:-.02em">${count(o.purchases.revenue, ' ⭐')}</div></div>
        <div style="text-align:right"><div class="meta">заказов</div><b style="font-size:18px">${count(o.purchases.orders)}</b></div></div>
      ${chart(o.revenueDaily, { key: 'revenue', unit: ' ⭐' })}
    </section>
    <div class="grid">
      ${stat('📢', 'Подписчики', count(o.channels.members), `${signed(o.channels.growth)} · ${o.channels.count} ${plural(o.channels.count, 'канал', 'канала', 'каналов')}`)}
      ${stat('📝', 'Постов', count(o.channels.posts), `за ${days} дн`)}
      ${stat('🤖', 'Пользователи', count(o.bots.users), `${fmt(o.bots.dau)} сегодня`)}
      ${stat('🧾', 'Средний чек', count(o.purchases.avgCheck, ' ⭐'), `${fmt(o.bots.events)} событий`)}
    </div>
    ${nothing ? `<section class="card">${empty('🚀', 'Подключите канал или бота — и здесь оживут графики')}
      <div class="actions"><button class="btn small" style="flex:1" data-to="channels">📢 Канал</button><button class="btn small secondary" style="flex:1" data-to="bots">🤖 Бот</button></div></section>` : ''}`;
  el.onclick = (e) => { const b = e.target.closest('[data-to]'); if (b) { app.next = { section: b.dataset.to }; app.open('analytics'); } };
}

async function channels(el, app) {
  el.onclick = null;
  const { channels } = await api(`/api/analytics/channels?days=${days}`);
  const bot = app.me.botUsername;
  const addLink = bot ? `https://t.me/${bot}?startchannel&admin=post_messages+invite_users` : null;
  el.innerHTML = `
    ${channels.length ? `<section class="card tight">${channels.map((c) => `
      <div class="row clickable chev" data-id="${c.chat_id}">
        <div class="ic" style="background:${hue(c.title)};color:#fff;font-weight:700">${esc((c.title || '?')[0])}</div>
        <div class="main"><div class="title">${esc(c.title || c.username || c.chat_id)} ${c.active ? '' : '<span class="badge">отключён</span>'}</div>
          <div class="meta">${fmt(c.posts)} постов · <span class="up">+${fmt(c.joins)}</span> / <span class="down">−${fmt(c.leaves)}</span></div></div>
        <div style="text-align:right"><b>${c.members != null ? fmt(c.members) : '—'}</b><div class="meta">${signed(c.growth)}</div></div>
      </div>`).join('')}</section>`
      : `<section class="card">${empty('📢', 'Каналов пока нет. Добавьте бота администратором — статистика начнёт собираться сама.')}</section>`}
    ${addLink ? `<button class="btn block" id="add">➕ Подключить канал</button>` : ''}`;
  el.querySelector('#add')?.addEventListener('click', () => {
    const tg = window.Telegram?.WebApp; tg?.openTelegramLink ? tg.openTelegramLink(addLink) : window.open(addLink, '_blank');
  });
  el.onclick = (e) => { const r = e.target.closest('.row[data-id]'); if (r) { tap(); show(el, () => channelDetail(el, app, r.dataset.id)); } };
}

async function channelDetail(el, app, id) {
  el.onclick = null;
  setBack(() => show(el, () => channels(el, app)));
  const r = await api(`/api/analytics/channels/${id}?days=${days}`);
  const sum = (arr, k) => arr.reduce((s, d) => s + (d[k] || 0), 0);
  const joins = sum(r.flow, 'joins'), leaves = sum(r.flow, 'leaves');
  const last = r.members.at(-1)?.value || 0, first = r.members.find((m) => m.value)?.value || last;
  const total = r.postKinds.reduce((s, k) => s + k.n, 0) || 1;
  const KIND = { text: '📝 Текст', photo: '🖼 Фото', video: '🎬 Видео', animation: '🎞 GIF', document: '📎 Файл', audio: '🎵 Аудио', voice: '🎙 Голос', poll: '📊 Опрос' };
  el.innerHTML = `
    <section class="hero" style="background:${hue(r.channel.title)}">
      <div class="hello">${esc(r.channel.username ? '@' + r.channel.username : 'Канал')}</div>
      <div style="font-size:20px;font-weight:700">${esc(r.channel.title)}</div>
      <div class="big" style="margin-top:10px">${count(last)}</div>
      <div class="hello">подписчиков · ${last - first >= 0 ? '+' : ''}${fmt(last - first)} за ${days} дн</div>
    </section>
    <section class="card"><b>Подписчики</b>${chart(r.members, { type: 'line' })}</section>
    <div class="grid">
      ${stat('🟢', 'Пришло', `+${count(joins)}`)}
      ${stat('🔴', 'Ушло', `−${count(leaves)}`, joins + leaves ? `отток ${Math.round((leaves / (joins + leaves)) * 100)}%` : '')}
    </div>
    <section class="card"><b>Публикации по дням</b>${chart(r.posts, { type: 'bar' })}</section>
    <section class="card"><b>Форматы постов</b>
      ${r.postKinds.length ? r.postKinds.map((k) => `
        <div style="margin:10px 0"><div style="display:flex;justify-content:space-between;font-size:14px"><span>${KIND[k.kind] || esc(k.kind)}</span><b>${fmt(k.n)}</b></div>
        <div class="progress" style="height:6px;margin-top:4px"><i data-w="${Math.round((k.n / total) * 100)}"></i></div></div>`).join('') : '<div class="meta">Нет данных</div>'}
    </section>
    <section class="card"><b>⏰ Когда вы публикуете чаще всего</b>
      <div class="actions" style="margin-top:8px">${r.topHoursUtc.map((h) => `<span class="badge open">${String((h.hour + 3) % 24).padStart(2, '0')}:00 МСК · ${h.n}</span>`).join('') || '<span class="meta">Нет данных</span>'}</div>
    </section>
    <button class="btn block secondary" id="refresh">🔄 Обновить число подписчиков</button>`;
  requestAnimationFrame(() => requestAnimationFrame(() => el.querySelectorAll('[data-w]').forEach((i) => (i.style.width = `${i.dataset.w}%`))));
  el.querySelector('#refresh').onclick = async () => {
    const { members } = await api(`/api/analytics/channels/${id}/refresh`, { method: 'POST' });
    toast(members != null ? `Подписчиков: ${fmt(members)}` : 'Не удалось получить данные');
    show(el, () => channelDetail(el, app, id));
  };
}

async function bots(el, app) {
  el.onclick = null;
  const { bots } = await api(`/api/analytics/bots?days=${days}`);
  el.innerHTML = `
    ${bots.length ? `<section class="card tight">${bots.map((b) => `
      <div class="row clickable chev" data-id="${b.id}">
        <div class="ic" style="background:${hue(b.name)};color:#fff">🤖</div>
        <div class="main"><div class="title">${esc(b.name)}</div><div class="meta">${fmt(b.dau)} сегодня · ${fmt(b.events)} событий</div></div>
        <div style="text-align:right"><b>${fmt(b.users)}</b><div class="meta">${fmt(b.revenue)} ⭐</div></div>
      </div>`).join('')}</section>` : `<section class="card">${empty('🤖', 'Подключите своих ботов, чтобы видеть их аудиторию и продажи')}</section>`}
    <section class="card form">
      <b>➕ Подключить бота</b>
      <input id="name" placeholder="Например, @my_shop_bot" maxlength="64" />
      <button class="btn" id="add">Получить API-ключ</button>
    </section>`;
  el.onclick = (e) => { const r = e.target.closest('.row[data-id]'); if (r) { tap(); show(el, () => botDetail(el, app, r.dataset.id)); } };
  el.querySelector('#add').onclick = async (e) => {
    e.stopPropagation();
    const name = el.querySelector('#name').value.trim();
    if (!name) return toast('Введите название');
    const b = await api('/api/analytics/bots', { method: 'POST', body: { name } });
    toast('Бот подключён 🎉');
    show(el, () => botDetail(el, app, b.id));
  };
}

async function botDetail(el, app, id) {
  el.onclick = null;
  setBack(() => show(el, () => bots(el, app)));
  const r = await api(`/api/analytics/bots/${id}?days=${days}`);
  const trackUrl = `${location.origin}/api/track`;
  const maxEv = Math.max(1, ...r.topEvents.map((t) => t.n));
  el.innerHTML = `
    <section class="hero" style="background:${hue(r.bot.name)}">
      <div class="hello">Бот</div><div style="font-size:20px;font-weight:700">🤖 ${esc(r.bot.name)}</div>
      <div class="grid" style="margin:14px 0 0">
        <div><div class="hello">Покупателей</div><div class="big" style="font-size:26px">${count(r.buyers)}</div></div>
        <div><div class="hello">Конверсия</div><div class="big" style="font-size:26px">${r.conversion}%</div></div>
      </div>
    </section>
    <section class="card"><b>Активные пользователи</b>${chart(r.daily, { key: 'users' })}</section>
    <section class="card"><b>Новые пользователи</b>${chart(r.newUsers, { type: 'bar' })}</section>
    <section class="card"><b>Выручка</b>${chart(r.daily, { key: 'revenue', type: 'bar', unit: ' ⭐' })}</section>
    <section class="card"><b>Топ событий</b>
      ${r.topEvents.length ? r.topEvents.map((t) => `
        <div style="margin:10px 0"><div style="display:flex;justify-content:space-between;font-size:14px"><span>${esc(t.event)}</span><b>${fmt(t.n)}</b></div>
        <div class="progress" style="height:6px;margin-top:4px"><i data-w="${Math.round((t.n / maxEv) * 100)}"></i></div></div>`).join('') : '<div class="meta">Событий ещё не было — отправьте первое по инструкции ниже</div>'}
    </section>
    <section class="card"><b>🔌 Подключение</b>
      <p class="meta">Отправляйте события из своего бота POST-запросом:</p>
      <div class="code">POST ${esc(trackUrl)}
{"api_key": "${esc(r.bot.api_key)}",
 "user_id": 123456789, "event": "start"}

// покупка
{"api_key": "…", "user_id": 123456789,
 "event": "purchase", "amount": 150}</div>
      <div class="actions" style="margin-top:10px"><button class="btn small secondary" id="cp">📋 Копировать ключ</button></div>
    </section>
    <button class="btn block danger" id="del">Удалить бота и его данные</button>`;
  requestAnimationFrame(() => requestAnimationFrame(() => el.querySelectorAll('[data-w]').forEach((i) => (i.style.width = `${i.dataset.w}%`))));
  el.querySelector('#cp').onclick = () => copy(r.bot.api_key);
  el.querySelector('#del').onclick = async () => {
    if (!(await confirm('Удалить бота и всю статистику?'))) return;
    await api(`/api/analytics/bots/${id}`, { method: 'DELETE' });
    show(el, () => bots(el, app));
  };
}

async function purchases(el, app) {
  el.onclick = null;
  const p = await api(`/api/analytics/purchases?days=${days}`);
  const maxP = Math.max(1, ...p.topProducts.map((t) => t.revenue));
  el.innerHTML = `
    <div class="grid">
      ${stat('💰', 'Выручка', count(p.revenue, ' ⭐'))}
      ${stat('🧾', 'Заказов', count(p.orders), `средний чек ${fmt(p.avgCheck)} ⭐`)}
    </div>
    <section class="card"><b>Выручка по дням</b>${chart(p.daily, { key: 'revenue', unit: ' ⭐' })}</section>
    <section class="card"><b>Заказы по дням</b>${chart(p.daily, { key: 'orders', type: 'bar' })}</section>
    ${app.me.isAdmin ? `<section class="card"><b>🏆 Топ товаров магазина</b>
      ${p.topProducts.length ? p.topProducts.map((t, i) => `
        <div style="margin:10px 0"><div style="display:flex;justify-content:space-between;font-size:14px"><span>${['🥇', '🥈', '🥉'][i] || '▫️'} ${esc(t.title)}</span><b>${fmt(t.revenue)} ⭐</b></div>
        <div class="progress" style="height:6px;margin-top:4px"><i data-w="${Math.round((t.revenue / maxP) * 100)}"></i></div></div>`).join('') : '<div class="meta">Продаж пока нет</div>'}
    </section>` : ''}
    <p class="meta" style="text-align:center">Учитываются покупки из подключённых ботов${app.me.isAdmin ? ' и продажи магазина StarApp' : ''}</p>`;
  requestAnimationFrame(() => requestAnimationFrame(() => el.querySelectorAll('[data-w]').forEach((i) => (i.style.width = `${i.dataset.w}%`))));
}
