import { api, esc, fmt, toast, haptic, tap, openInvoice, confirm, sheet, confetti, hue, empty, date } from '../app.js';

const EMOJI = [[/аналит|отч[её]т|pro/i, '📊'], [/аудит|разбор|эксперт/i, '🔍'], [/бот/i, '🤖'], [/реклам|продвиж/i, '📣'], [/дизайн|оформ/i, '🎨'], [/канал/i, '📢'], [/курс|обуч/i, '🎓']];
const emojiFor = (p) => EMOJI.find(([re]) => re.test(p.title + ' ' + p.description))?.[1] || '📦';

export async function render(el, app) {
  const [{ products }, { orders }] = await Promise.all([api('/api/shop/products'), api('/api/shop/orders')]);
  const topSold = Math.max(0, ...products.map((p) => p.sold));
  el.innerHTML = `
    <h2>🛍 Магазин</h2>
    ${products.length ? products.map((p) => `
      <section class="card product">
        <div class="cover" style="background:${hue(p.title)}">
          ${topSold > 0 && p.sold === topSold ? '<span class="badge hot">🔥 Хит продаж</span>' : ''}
          <span>${emojiFor(p)}</span>
        </div>
        <div class="body">
          <b style="font-size:16px">${esc(p.title)}</b>
          <div class="meta" style="margin:4px 0 12px">${esc(p.description)}</div>
          <div class="row" style="padding:0;border:0">
            <div class="main"><span class="price">${fmt(p.price)} ⭐</span>${p.sold ? `<span class="meta"> · купили ${fmt(p.sold)}</span>` : ''}</div>
            <button class="btn" data-buy="${p.id}">Купить</button>
          </div>
          ${app.me.isAdmin ? `<button class="btn small danger" style="margin-top:10px" data-del="${p.id}">Скрыть из каталога</button>` : ''}
        </div>
      </section>`).join('') : `<section class="card">${empty('🛍', 'Товаров пока нет')}</section>`}
    ${app.me.isAdmin ? `
      <section class="card form">
        <b>➕ Новый товар</b>
        <input id="t" placeholder="Название" />
        <input id="p" type="number" min="1" placeholder="Цена в ⭐" />
        <textarea id="d" placeholder="Описание"></textarea>
        <button class="btn" id="add">Добавить</button>
      </section>` : ''}
    <h3>Мои покупки</h3>
    <section class="card tight">${orders.length ? orders.map((o) => `
      <div class="row"><div class="ic">✅</div><div class="main"><div class="title">${esc(o.title)}</div><div class="meta">${date(o.created_at)} · ${o.paid_via === 'balance' ? 'с баланса' : 'Telegram Stars'}</div></div><b>${fmt(o.price)} ⭐</b></div>`).join('') : empty('🧾', 'Покупок пока нет')}</section>`;

  const buy = async (product, method, close) => {
    try {
      const r = await api('/api/shop/buy', { method: 'POST', body: { productId: product.id, method } });
      close();
      const status = r.status === 'paid' ? 'paid' : await openInvoice(r.invoiceLink);
      if (status === 'paid') {
        haptic(); confetti(); toast('Оплачено! Спасибо 🎉');
        setTimeout(async () => { await app.refreshMe(); app.rerender(await import('./shop.js')); }, 900);
      }
    } catch (err) { haptic('error'); toast(err.message); }
  };

  el.onclick = async (e) => {
    const b = e.target.closest('[data-buy]');
    if (b) {
      tap();
      const p = products.find((x) => x.id === Number(b.dataset.buy));
      const balance = app.me.user?.balance ?? 0;
      const { el: sh, close } = sheet(`
        <div style="text-align:center"><div style="font-size:52px;animation:pop .5s both">${emojiFor(p)}</div>
          <h2 style="margin:6px 0 2px">${esc(p.title)}</h2><div class="price" style="margin-bottom:16px">${fmt(p.price)} ⭐</div></div>
        <button class="btn block" data-m="stars">⭐ Оплатить через Telegram Stars</button>
        <button class="btn block secondary" style="margin-top:8px" data-m="balance" ${balance < p.price ? 'disabled' : ''}>
          💳 С баланса (${fmt(balance)} ⭐)</button>
        ${balance < p.price ? `<div class="meta" style="text-align:center;margin-top:8px">На балансе не хватает ${fmt(p.price - balance)} ⭐</div>` : ''}`);
      sh.onclick = (ev) => { const m = ev.target.closest('[data-m]'); if (m) { m.disabled = true; buy(p, m.dataset.m, close); } };
      return;
    }
    const del = e.target.closest('[data-del]');
    if (del && (await confirm('Скрыть товар из каталога?'))) {
      await api(`/api/shop/products/${del.dataset.del}`, { method: 'DELETE' });
      app.rerender(await import('./shop.js'));
    }
    if (e.target.id === 'add') {
      try {
        await api('/api/shop/products', { method: 'POST', body: { title: el.querySelector('#t').value, price: el.querySelector('#p').value, description: el.querySelector('#d').value } });
        toast('Товар добавлен ✓');
        app.rerender(await import('./shop.js'));
      } catch (err) { toast(err.message); }
    }
  };
}
