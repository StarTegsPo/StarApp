import { api, esc, date, toast, haptic, tap, empty } from '../app.js';

const STATUS = { open: ['open', '⏳ ждёт ответа'], answered: ['answered', '✅ есть ответ'], closed: ['', 'закрыто'] };
const QUICK = ['Не проходит оплата', 'Как подключить канал?', 'Не вижу статистику', 'Вопрос по заказу'];

export async function render(el, app) {
  const { tickets } = await api('/api/support/tickets');
  el.innerHTML = `
    <section class="hero" style="padding:16px">
      <div style="font-size:19px;font-weight:700">💬 Поддержка</div>
      <div class="hello">Обычно отвечаем в течение часа. Ответ придёт сюда и в чат с ботом.</div>
    </section>
    <section class="card form">
      <b>Новое обращение</b>
      <div class="hscroll" style="margin:0 -16px;padding:2px 16px" id="quick">${QUICK.map((q) => `<button class="btn small secondary">${q}</button>`).join('')}</div>
      <textarea id="text" placeholder="Опишите вопрос — чем подробнее, тем быстрее поможем" maxlength="4000"></textarea>
      <button class="btn" id="send">Отправить</button>
    </section>
    <h3>Мои обращения</h3>
    ${tickets.length ? tickets.map((t) => {
      const [cls, label] = STATUS[t.status] || ['', t.status];
      return `<section class="card">
        <div class="row" style="padding:0 0 8px"><b>Обращение #${t.id}</b><span class="badge ${cls}">${label}</span></div>
        ${t.messages.map((m) => `<div class="msg ${m.from_admin ? 'admin' : 'me'}">${m.from_admin ? '<b>🛟 Поддержка</b>\n' : ''}${esc(m.text)}<div class="meta" style="font-size:11px;margin-top:2px">${date(m.created_at)}</div></div>`).join('')}
        ${t.status !== 'closed' ? `<div class="actions" style="margin-top:10px;flex-wrap:nowrap"><input data-reply="${t.id}" placeholder="Написать…" /><button class="btn" data-send="${t.id}">➤</button></div>` : ''}
      </section>`;
    }).join('') : `<section class="card">${empty('🛟', 'Обращений пока нет. Мы рядом, если что-то понадобится!')}</section>`}`;

  el.querySelector('#quick').onclick = (e) => {
    const b = e.target.closest('button'); if (!b) return;
    tap(); const ta = el.querySelector('#text'); ta.value = b.textContent + '. '; ta.focus();
  };
  el.onclick = async (e) => {
    try {
      if (e.target.id === 'send') {
        const text = el.querySelector('#text').value.trim();
        if (!text) { haptic('warning'); return toast('Напишите сообщение'); }
        e.target.disabled = true;
        const { id } = await api('/api/support/tickets', { method: 'POST', body: { text } });
        haptic(); toast(`Обращение #${id} отправлено 📨`);
        app.rerender(await import('./support.js'));
      }
      const b = e.target.closest('[data-send]');
      if (b) {
        const input = el.querySelector(`[data-reply="${b.dataset.send}"]`);
        if (!input.value.trim()) return;
        await api(`/api/support/tickets/${b.dataset.send}/messages`, { method: 'POST', body: { text: input.value } });
        haptic(); app.rerender(await import('./support.js'));
      }
    } catch (err) { toast(err.message); e.target.disabled = false; }
  };
}
