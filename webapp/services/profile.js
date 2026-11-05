import { api, esc, fmt, date, toast, haptic, openInvoice } from '../app.js';

const KIND = { topup: 'Пополнение', purchase: 'Покупка', refund: 'Возврат', bonus: 'Бонус' };

export async function render(el, app) {
  const { user, totals, transactions } = await api('/api/profile');
  const name = [user.first_name, user.last_name].filter(Boolean).join(' ');
  el.innerHTML = `
    <div class="card">
      <div class="title" style="font-size:18px;font-weight:700">${esc(name || 'Пользователь')}</div>
      <div class="meta">${user.username ? '@' + esc(user.username) + ' · ' : ''}ID ${user.id}${app.me.isAdmin ? ' · <span class="badge open">админ</span>' : ''}</div>
    </div>
    <div class="grid">
      <div class="stat"><div class="label">Баланс</div><div class="value">${fmt(user.balance)} ⭐</div></div>
      <div class="stat"><div class="label">Пополнено / потрачено</div><div class="value" style="font-size:17px">${fmt(totals.income)} / ${fmt(totals.spent)}</div></div>
    </div>
    <div class="card form">
      <b>Пополнить баланс</b>
      <div class="actions">${[50, 100, 250, 500].map((a) => `<button class="btn small secondary" data-a="${a}">${a} ⭐</button>`).join('')}</div>
      <input id="amount" type="number" min="1" placeholder="Своя сумма" />
      <button class="btn" id="topup">Пополнить через Telegram Stars</button>
    </div>
    <h3>История операций</h3>
    <div class="card">${transactions.length ? transactions.map((t) => `
      <div class="row"><div class="main"><div class="title">${esc(t.description || KIND[t.kind] || t.kind)}</div><div class="meta">${date(t.created_at)}</div></div>
      <b class="${t.amount > 0 ? 'up' : t.amount < 0 ? 'down' : ''}">${t.amount > 0 ? '+' : ''}${fmt(t.amount)} ⭐</b></div>`).join('') : '<div class="meta">Операций пока нет</div>'}</div>`;

  el.querySelector('.actions').onclick = (e) => {
    const b = e.target.closest('[data-a]');
    if (b) el.querySelector('#amount').value = b.dataset.a;
  };
  el.querySelector('#topup').onclick = async () => {
    const amount = Number(el.querySelector('#amount').value);
    if (!amount) return toast('Укажите сумму');
    try {
      const { invoiceLink } = await api('/api/profile/topup', { method: 'POST', body: { amount } });
      if ((await openInvoice(invoiceLink)) === 'paid') {
        haptic();
        toast('Баланс пополнен!');
        setTimeout(async () => { await app.refreshMe(); render(el, app); }, 800);
      }
    } catch (err) {
      toast(err.message);
    }
  };
}
