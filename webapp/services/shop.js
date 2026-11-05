import { api, esc, fmt, toast, haptic, openInvoice, confirm } from '../app.js';

export async function render(el, app) {
  const [{ products }, { orders }] = await Promise.all([api('/api/shop/products'), api('/api/shop/orders')]);
  el.innerHTML = `
    <h2>🛍 Магазин</h2>
    ${products.length ? products.map((p) => `
      <div class="card">
        <div class="row" style="border:0;padding:0">
          <div class="main"><div class="title">${esc(p.title)}</div><div class="meta">${esc(p.description)}</div></div>
          <b>${fmt(p.price)} ⭐</b>
        </div>
        <div class="actions" style="margin-top:10px">
          <button class="btn small" data-buy="${p.id}" data-method="stars">Оплатить Stars</button>
          <button class="btn small secondary" data-buy="${p.id}" data-method="balance">С баланса</button>
          ${app.me.isAdmin ? `<button class="btn small danger" data-del="${p.id}">Скрыть</button>` : ''}
        </div>
      </div>`).join('') : '<div class="card empty">Товаров пока нет</div>'}
    ${app.me.isAdmin ? `
      <div class="card form">
        <b>Новый товар</b>
        <input id="t" placeholder="Название" />
        <input id="p" type="number" min="1" placeholder="Цена в ⭐" />
        <textarea id="d" placeholder="Описание"></textarea>
        <button class="btn" id="add">Добавить</button>
      </div>` : ''}
    <h3>Мои покупки</h3>
    <div class="card">${orders.length ? orders.map((o) => `
      <div class="row"><div class="main"><div class="title">${esc(o.title)}</div><div class="meta">#${o.id} · ${o.paid_via === 'balance' ? 'с баланса' : 'Stars'}</div></div><b>${fmt(o.price)} ⭐</b></div>`).join('') : '<div class="meta">Покупок пока нет</div>'}</div>`;

  el.onclick = async (e) => {
    const buy = e.target.closest('[data-buy]');
    const del = e.target.closest('[data-del]');
    try {
      if (buy) {
        buy.disabled = true;
        const r = await api('/api/shop/buy', { method: 'POST', body: { productId: Number(buy.dataset.buy), method: buy.dataset.method } });
        const status = r.status === 'paid' ? 'paid' : await openInvoice(r.invoiceLink);
        if (status === 'paid') {
          haptic();
          toast('Оплачено! Спасибо 🎉');
          setTimeout(async () => { await app.refreshMe(); render(el, app); }, 800);
        } else buy.disabled = false;
      }
      if (del && (await confirm('Скрыть товар из каталога?'))) {
        await api(`/api/shop/products/${del.dataset.del}`, { method: 'DELETE' });
        render(el, app);
      }
      if (e.target.id === 'add') {
        await api('/api/shop/products', {
          method: 'POST',
          body: { title: el.querySelector('#t').value, price: el.querySelector('#p').value, description: el.querySelector('#d').value },
        });
        toast('Товар добавлен');
        render(el, app);
      }
    } catch (err) {
      haptic('error');
      toast(err.message);
      if (buy) buy.disabled = false;
    }
  };
}
