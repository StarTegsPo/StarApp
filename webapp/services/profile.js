import { api, esc, fmt, date, toast, haptic, tap, openInvoice, confetti, count, empty, tgUser } from '../app.js';

const KIND = { topup: ['💰', 'Пополнение'], purchase: ['🛍', 'Покупка'], refund: ['↩️', 'Возврат'], bonus: ['🎁', 'Бонус'] };
const PRESETS = [50, 100, 250, 500, 1000];

export async function render(el, app) {
  const { user, totals, transactions } = await api('/api/profile');
  const name = [user.first_name, user.last_name].filter(Boolean).join(' ') || 'Пользователь';
  const photo = tgUser?.photo_url;
  const since = new Date(user.created_at * 1000).toLocaleDateString('ru-RU', { month: 'long', year: 'numeric' });
  el.innerHTML = `
    <section class="hero">
      <div class="row" style="justify-content:flex-start;gap:12px">
        <div class="avatar lg">${photo ? `<img src="${esc(photo)}" alt="">` : esc(name[0])}</div>
        <div class="main"><div style="font-size:19px;font-weight:700">${esc(name)}</div>
          <div class="hello">${user.username ? '@' + esc(user.username) + ' · ' : ''}с нами с ${since}</div></div>
        ${app.me.isAdmin ? '<span class="badge" style="background:#ffffff2e;color:#fff">👑 админ</span>' : ''}
      </div>
      <div class="hello" style="margin-top:16px">Баланс</div>
      <div class="big">${count(user.balance)} ⭐</div>
      <div class="grid" style="margin:12px 0 0">
        <div><div class="hello">Пополнено</div><b>${count(totals.income)} ⭐</b></div>
        <div><div class="hello">Потрачено</div><b>${count(totals.spent)} ⭐</b></div>
      </div>
    </section>

    <section class="card form">
      <b>Пополнить баланс</b>
      <div class="hscroll" style="margin:0 -16px;padding:4px 16px" id="presets">${PRESETS.map((a) => `<button class="btn small secondary" data-a="${a}">${a} ⭐</button>`).join('')}</div>
      <input id="amount" type="number" min="1" inputmode="numeric" placeholder="Своя сумма" />
      <button class="btn" id="topup">⭐ Пополнить через Telegram Stars</button>
    </section>

    <h3>История операций</h3>
    <section class="card tight">${transactions.length ? transactions.map((t) => {
      const [ic, label] = KIND[t.kind] || ['•', t.kind];
      return `<div class="row"><div class="ic">${ic}</div><div class="main"><div class="title">${esc(t.description || label)}</div><div class="meta">${date(t.created_at)}</div></div>
        <b class="${t.amount > 0 ? 'up' : t.amount < 0 ? 'down' : ''}">${t.amount > 0 ? '+' : ''}${t.amount ? fmt(t.amount) + ' ⭐' : ''}</b></div>`;
    }).join('') : empty('🧾', 'Операций пока нет')}</section>`;

  const input = el.querySelector('#amount');
  el.querySelector('#presets').onclick = (e) => {
    const b = e.target.closest('[data-a]'); if (!b) return;
    tap(); input.value = b.dataset.a;
    el.querySelectorAll('#presets button').forEach((x) => (x.className = `btn small ${x === b ? '' : 'secondary'}`));
  };
  el.querySelector('#topup').onclick = async () => {
    const amount = Number(input.value);
    if (!amount) { haptic('warning'); return toast('Выберите или введите сумму'); }
    try {
      const { invoiceLink } = await api('/api/profile/topup', { method: 'POST', body: { amount } });
      if ((await openInvoice(invoiceLink)) === 'paid') {
        haptic(); confetti(); toast(`Баланс пополнен на ${fmt(amount)} ⭐`);
        setTimeout(async () => { await app.refreshMe(); app.rerender(await import('./profile.js')); }, 900);
      }
    } catch (err) { toast(err.message); }
  };
}
