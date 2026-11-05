import { api, esc, date, toast, haptic } from '../app.js';

const STATUS = { open: 'ожидает ответа', answered: 'есть ответ', closed: 'закрыто' };

export async function render(el, app) {
  const { tickets } = await api('/api/support/tickets');
  el.innerHTML = `
    <h2>💬 Поддержка</h2>
    <div class="card form">
      <b>Новое обращение</b>
      <textarea id="text" placeholder="Опишите вопрос или проблему" maxlength="4000"></textarea>
      <button class="btn" id="send">Отправить</button>
    </div>
    <h3>Мои обращения</h3>
    ${tickets.length ? tickets.map((t) => `
      <div class="card">
        <div class="row" style="padding-top:0"><b>#${t.id}</b><span class="badge ${t.status}">${STATUS[t.status] || t.status}</span></div>
        ${t.messages.map((m) => `<div class="msg ${m.from_admin ? 'admin' : 'me'}">${m.from_admin ? '<b>Поддержка:</b> ' : ''}${esc(m.text)}<div class="meta">${date(m.created_at)}</div></div>`).join('')}
        ${t.status !== 'closed' ? `<div class="actions" style="margin-top:8px"><input data-reply="${t.id}" placeholder="Ответить…" style="flex:1" /><button class="btn small" data-send="${t.id}">➤</button></div>` : ''}
      </div>`).join('') : '<div class="card empty">Обращений пока нет</div>'}`;

  el.onclick = async (e) => {
    try {
      if (e.target.id === 'send') {
        const text = el.querySelector('#text').value.trim();
        if (!text) return toast('Напишите сообщение');
        const { id } = await api('/api/support/tickets', { method: 'POST', body: { text } });
        haptic();
        toast(`Обращение #${id} отправлено`);
        render(el, app);
      }
      const b = e.target.closest('[data-send]');
      if (b) {
        const input = el.querySelector(`[data-reply="${b.dataset.send}"]`);
        if (!input.value.trim()) return;
        await api(`/api/support/tickets/${b.dataset.send}/messages`, { method: 'POST', body: { text: input.value } });
        render(el, app);
      }
    } catch (err) {
      toast(err.message);
    }
  };
}
