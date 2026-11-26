// 🚀 Хаб: все ИИ, соцсети, контент-студия и автоматизации в одном экране.
import { api, esc, ago, toast, haptic, tap, empty, copy, confirm, sheet } from '../app.js';

const tg = window.Telegram?.WebApp;
const DAYS = ['Пн', 'Вт', 'Ср', 'Чт', 'Пт', 'Сб', 'Вс'];
const TEMPLATES = [
  { icon: '💡', title: 'Идеи для TikTok каждое утро', kind: 'ai', time: '09:00', days: 127, prompt: 'Придумай 5 свежих идей для коротких видео в TikTok на сегодня. Для каждой — хук на первые 2 секунды и формат ролика.' },
  { icon: '📈', title: 'Тренды недели', kind: 'ai', time: '10:00', days: 1, prompt: 'Какие форматы и приёмы коротких вертикальных видео сейчас лучше всего работают? Дай 7 конкретных советов, которые можно применить на этой неделе.' },
  { icon: '🎬', title: 'Напоминание: выложить видео', kind: 'remind', time: '19:00', days: 127, prompt: 'Пора выложить ролик в TikTok / Reels / Shorts 🎬 Лучшее время для охватов — сейчас!' },
  { icon: '💬', title: 'Ответить на комментарии', kind: 'remind', time: '21:00', days: 127, prompt: 'Загляни в комментарии и директ — ответы в первый час поднимают ролик в рекомендациях.' },
];

// состояние экрана сохраняется между перерисовками
const st = { tab: 'ai', provider: null, chatId: null, messages: [], compare: false, studio: null, preview: null };

/** Открыть ссылку во встроенном браузере Telegram (не выходя из приложения). */
const openLink = (url) => (tg?.openLink ? tg.openLink(url) : window.open(url, '_blank'));

/** Минимальная разметка ответа ИИ: жирный, курсив, код, заголовки. Сначала экранируем. */
function md(text) {
  return esc(text)
    .replace(/```[a-z]*\n?([\s\S]*?)```/g, '<code class="hub-pre">$1</code>')
    .replace(/`([^`\n]+)`/g, '<code>$1</code>')
    .replace(/^#{1,4}\s*(.+)$/gm, '<b>$1</b>')
    .replace(/\*\*([^*\n]+)\*\*/g, '<b>$1</b>')
    .replace(/(^|[\s(])\*([^*\n]+)\*(?=[\s).,!?:]|$)/g, '$1<i>$2</i>');
}

const usageText = (u) => (u.limit == null ? 'без лимита' : `${u.used} / ${u.limit} сегодня`);

export async function render(el, app) {
  const hub = await api('/api/hub');
  const enabled = hub.providers.filter((p) => p.enabled);
  if (!st.provider || !enabled.some((p) => p.id === st.provider)) st.provider = enabled[0]?.id ?? null;
  const prov = (id) => hub.providers.find((p) => p.id === id) || { icon: '🤖', title: id };
  const rerender = async () => app.rerender(await import('./hub.js'));

  const tabs = [['ai', '💬 ИИ'], ['studio', '🎬 Студия'], ['social', '📱 Соцсети'], ['auto', '⚡ Авто']];

  // ---------- вкладки ----------
  async function aiTab() {
    if (!enabled.length) {
      return `<section class="card">${empty('🔌', 'ИИ ещё не подключены.<br>Администратору: добавьте ключи в <code>.env</code> — например, <code>ANTHROPIC_API_KEY</code>, <code>OPENAI_API_KEY</code>, <code>GEMINI_API_KEY</code>.')}</section>
        ${webAi()}`;
    }
    const { chats } = await api('/api/hub/chats');
    const chips = hub.providers.map((p) => `<button class="hub-prov ${p.enabled ? '' : 'off'} ${!st.compare && p.id === st.provider ? 'active' : ''}" data-prov="${p.id}" ${p.enabled ? '' : 'disabled'}>
        <span>${p.icon}</span>${esc(p.title)}${p.enabled ? '' : '<small>нет ключа</small>'}</button>`).join('');
    const msgs = st.messages.map((m) => m.role === 'user'
      ? `<div class="msg me">${esc(m.content)}</div>`
      : m.pending
        ? `<div class="msg admin hub-typing"><b>${prov(m.provider).icon} ${esc(prov(m.provider).title)}</b><span><i></i><i></i><i></i></span></div>`
        : `<div class="msg admin ${m.error ? 'hub-err' : ''}"><div class="hub-msg-head"><b>${prov(m.provider).icon} ${esc(prov(m.provider).title)}</b>${m.error ? '' : `<button class="hub-copy" data-copy="${esc(m.content)}">⧉</button>`}</div>${m.error ? esc(m.error) : md(m.content)}</div>`).join('');
    return `
      <div class="hscroll hub-provs">${chips}<button class="hub-prov ${st.compare ? 'active' : ''}" id="compare"><span>⚖️</span>Сравнить</button></div>
      <section class="card hub-chat">
        <div class="row" style="padding:0 0 8px"><b>${st.compare ? '⚖️ Один вопрос — все ИИ сразу' : st.chatId ? 'Диалог' : 'Новый диалог'}</b>
          ${st.chatId || st.messages.length ? '<button class="btn small secondary" id="new">＋ Новый</button>' : `<span class="badge">${usageText(hub.usage)}</span>`}</div>
        <div id="msgs">${msgs || `<div class="hub-hint">${st.compare ? 'Задайте вопрос — ответят до 4 подключённых ИИ, а вы выберете лучший ответ.' : `Спросите что угодно у ${esc(prov(st.provider).title)}. ИИ можно переключать прямо посреди диалога — он увидит всю переписку.`}</div>`}</div>
        <div class="hub-input"><textarea id="q" rows="1" maxlength="${hub.limits.text}" placeholder="Сообщение…"></textarea><button class="btn" id="send">➤</button></div>
      </section>
      ${chats.length ? `<h3>История <span class="badge">${chats.length}</span></h3><section class="card tight">${chats.map((c) => `
        <div class="row clickable" data-chat="${c.id}"><div class="ic">${prov(c.provider).icon}</div>
          <div class="main"><div class="title">${esc(c.title)}</div><div class="hub-sub">${esc(c.last)}</div></div>
          <span class="hub-sub">${ago(c.updated_at)}</span><button class="hub-del" data-delchat="${c.id}">✕</button></div>`).join('')}</section>` : ''}
      ${webAi()}`;
  }

  function webAi() {
    return `<h3>Сайты ИИ-сервисов</h3><div class="hub-tiles">${hub.aiWeb.map((s) => `<button class="hub-tile" data-open="${esc(s.url)}"><span>${s.icon}</span>${esc(s.title)}</button>`).join('')}</div>`;
  }

  function studioTab() {
    const cur = hub.presets.find((p) => p.id === st.studio?.preset);
    return `
      <section class="hero" style="padding:16px"><div style="font-size:19px;font-weight:700">🎬 Контент-студия</div>
        <div class="hello">Идеи, сценарии, описания и хэштеги для TikTok, Reels и Shorts за секунды.</div></section>
      <div class="hub-tiles">${hub.presets.map((p) => `<button class="hub-tile ${cur?.id === p.id ? 'active' : ''}" data-preset="${p.id}"><span>${p.icon}</span>${esc(p.title)}</button>`).join('')}</div>
      ${cur ? `<section class="card form">
        <b>${cur.icon} ${esc(cur.title)}</b>
        <textarea id="sq" placeholder="${esc(cur.hint)}" maxlength="${hub.limits.text}">${esc(st.studio.input || '')}</textarea>
        ${enabled.length ? `<select id="sprov">${enabled.map((p) => `<option value="${p.id}" ${p.id === st.provider ? 'selected' : ''}>${p.icon} ${esc(p.title)}</option>`).join('')}</select>
        <button class="btn" id="gen">✨ Сгенерировать</button>` : '<div class="hub-hint">Сначала подключите хотя бы один ИИ (ключ в .env).</div>'}
      </section>` : ''}
      ${st.studio?.result ? `<section class="card"><div class="hub-msg-head"><b>${prov(st.studio.provider).icon} Результат</b><button class="hub-copy" data-copy="${esc(st.studio.result)}">⧉</button></div>
        <div class="hub-result">${md(st.studio.result)}</div>
        <div class="actions" style="margin-top:12px"><button class="btn small secondary" data-copy="${esc(st.studio.result)}">Копировать</button>
        <button class="btn small secondary" id="tochat">Доработать в чате</button><button class="btn small secondary" data-open="https://www.tiktok.com/tiktokstudio/upload">🎵 В TikTok</button></div></section>` : ''}`;
  }

  function socialTab() {
    const p = st.preview;
    return `
      <section class="card form"><b>🔗 Ссылка на TikTok или YouTube</b>
        <div class="hub-input"><input id="link" placeholder="https://www.tiktok.com/@user/video/…" value="${esc(p?.url || '')}"/><button class="btn" id="look">👁</button></div>
        ${p ? `<div class="hub-preview">${p.thumbnail ? `<img src="${esc(p.thumbnail)}" alt="" referrerpolicy="no-referrer">` : ''}
          <div><b>${esc(p.title || 'Без названия')}</b><div class="hub-sub">${p.platform === 'tiktok' ? '🎵 TikTok' : '▶️ YouTube'} · ${esc(p.author || '')}</div>
          <div class="actions" style="margin-top:8px"><button class="btn small" data-open="${esc(p.url)}">Открыть</button>
          <button class="btn small secondary" id="remix">💡 Идеи по мотивам</button><button class="btn small secondary" id="caption">✍️ Описание</button></div></div></div>` : '<div class="hub-hint">Вставьте ссылку на ролик — покажем превью, а ИИ придумает похожие идеи или описание.</div>'}
      </section>
      <h3>Соцсети</h3>
      ${hub.social.map((s) => `<section class="card hub-social">
        <div class="row" style="padding:0"><div class="ic">${s.icon}</div><div class="main"><div class="title">${esc(s.title)}</div></div><button class="btn small" data-open="${esc(s.url)}">Открыть</button></div>
        ${s.actions.length ? `<div class="hub-acts">${s.actions.map((a) => `<button class="btn small secondary" data-open="${esc(a.url)}">${esc(a.title)}</button>`).join('')}</div>` : ''}
      </section>`).join('')}
      <div class="hub-hint" style="margin:4px">Ссылки открываются во встроенном браузере Telegram — вход в аккаунты сохраняется, приложение не закрывается.</div>`;
  }

  async function autoTab() {
    const { tasks, limit } = await api('/api/hub/tasks');
    st.tasks = tasks;
    const daysText = (m) => (m === 127 ? 'каждый день' : m === 31 ? 'по будням' : DAYS.filter((_, i) => m & (1 << i)).join(', '));
    return `
      <section class="hero" style="padding:16px"><div style="font-size:19px;font-weight:700">⚡ Автоматизации</div>
        <div class="hello">ИИ сам выполняет задания по расписанию и присылает результат в чат с ботом. Плюс напоминания, чтобы не пропустить публикацию.</div></section>
      <h3>Быстрый старт</h3>
      <div class="hscroll">${TEMPLATES.map((t, i) => `<button class="hub-tpl" data-tpl="${i}"><span>${t.icon}</span><b>${esc(t.title)}</b><small>${t.time} · ${daysText(t.days)}</small></button>`).join('')}</div>
      <h3>Мои автоматизации <span class="badge">${tasks.length} / ${limit}</span></h3>
      ${tasks.length ? tasks.map((t) => `<section class="card hub-task ${t.enabled ? '' : 'paused'}">
        <div class="row" style="padding:0"><div class="ic">${t.kind === 'ai' ? prov(t.provider).icon : '⏰'}</div>
          <div class="main"><div class="title">${esc(t.title)}</div><div class="hub-sub">${t.time} · ${daysText(t.days)}${t.kind === 'ai' ? ` · ${esc(prov(t.provider).title)}` : ' · напоминание'}</div></div>
          <label class="hub-switch"><input type="checkbox" data-toggle="${t.id}" ${t.enabled ? 'checked' : ''}><i></i></label></div>
        ${t.last_error ? `<div class="hub-sub hub-err" style="margin-top:8px">⚠️ ${esc(t.last_error)}</div>` : t.last_run_at ? `<div class="hub-sub" style="margin-top:8px">Последний запуск: ${ago(t.last_run_at)}</div>` : ''}
        <div class="actions" style="margin-top:10px"><button class="btn small secondary" data-run="${t.id}">▶ Запустить сейчас</button><button class="btn small secondary" data-edit="${t.id}">Изменить</button><button class="btn small danger" data-deltask="${t.id}">Удалить</button></div>
      </section>`).join('') : `<section class="card">${empty('⚡', 'Пока пусто. Выберите шаблон выше или создайте свою автоматизацию.')}</section>`}
      <button class="btn block" id="addtask">＋ Новая автоматизация</button>`;
  }

  // ---------- форма автоматизации (шторка) ----------
  function taskForm(t = {}) {
    let kind = t.kind || (enabled.length ? 'ai' : 'remind');
    let days = t.days ?? 127;
    const s = sheet(`
      <b style="font-size:17px">${t.id ? 'Изменить' : 'Новая'} автоматизация</b>
      <div class="form" style="margin-top:12px">
        <input id="tt" placeholder="Название" maxlength="${hub.limits.taskTitle}" value="${esc(t.title || '')}">
        <div class="seg mini" id="tk"><button data-k="ai" ${enabled.length ? '' : 'disabled'}>🤖 Задание ИИ</button><button data-k="remind">⏰ Напоминание</button></div>
        <textarea id="tp" maxlength="${hub.limits.taskPrompt}" placeholder="">${esc(t.prompt || '')}</textarea>
        <select id="tpr">${enabled.map((p) => `<option value="${p.id}" ${p.id === (t.provider || st.provider) ? 'selected' : ''}>${p.icon} ${esc(p.title)}</option>`).join('')}</select>
        <input id="ttime" type="time" value="${t.time || '09:00'}">
        <div class="hub-days" id="td">${DAYS.map((d, i) => `<button data-d="${i}">${d}</button>`).join('')}</div>
        <button class="btn" id="tsave">Сохранить</button>
      </div>`);
    const paint = () => {
      s.el.querySelectorAll('#tk button').forEach((b) => b.classList.toggle('active', b.dataset.k === kind));
      s.el.querySelector('#tpr').hidden = kind !== 'ai';
      s.el.querySelector('#tp').placeholder = kind === 'ai' ? 'Что должен сделать ИИ? Например: «5 идей для TikTok про фитнес»' : 'Текст напоминания';
      s.el.querySelectorAll('#td button').forEach((b) => b.classList.toggle('active', !!(days & (1 << b.dataset.d))));
    };
    paint();
    s.el.querySelector('#tk').onclick = (e) => { const b = e.target.closest('[data-k]'); if (b) { kind = b.dataset.k; tap(); paint(); } };
    s.el.querySelector('#td').onclick = (e) => { const b = e.target.closest('[data-d]'); if (b) { days ^= 1 << b.dataset.d; tap(); paint(); } };
    s.el.querySelector('#tsave').onclick = async (e) => {
      e.target.disabled = true;
      try {
        const body = { title: s.el.querySelector('#tt').value, kind, prompt: s.el.querySelector('#tp').value, time: s.el.querySelector('#ttime').value, days,
          tzOffset: -new Date().getTimezoneOffset(), ...(kind === 'ai' ? { provider: s.el.querySelector('#tpr').value } : {}) };
        await api(t.id ? `/api/hub/tasks/${t.id}` : '/api/hub/tasks', { method: 'POST', body });
        haptic(); toast(t.id ? 'Сохранено ✓' : 'Автоматизация создана ⚡'); s.close(); rerender();
      } catch (err) { toast(err.message); e.target.disabled = false; }
    };
  }

  // ---------- отрисовка ----------
  const body = st.tab === 'ai' ? await aiTab() : st.tab === 'studio' ? studioTab() : st.tab === 'social' ? socialTab() : await autoTab();
  el.innerHTML = `
    <section class="hero hub-hero"><div class="hub-hero-title">🚀 Хаб</div>
      <div class="hello">${enabled.length} ИИ · ${hub.social.length} соцсетей · ${hub.counts.tasks} автоматизаций — всё не выходя из Telegram</div></section>
    <div class="seg" id="hubtabs">${tabs.map(([id, t]) => `<button data-tab="${id}" class="${st.tab === id ? 'active' : ''}">${t}</button>`).join('')}</div>
    <div id="hubbody">${body}</div>`;

  const msgsEl = el.querySelector('#msgs');
  if (msgsEl) msgsEl.lastElementChild?.scrollIntoView?.({ block: 'nearest' });
  const q = el.querySelector('#q');
  if (q) {
    const grow = () => { q.style.height = 'auto'; q.style.height = `${Math.min(q.scrollHeight, 160)}px`; };
    q.oninput = grow;
    q.onkeydown = (e) => { if (e.key === 'Enter' && !e.shiftKey && !('ontouchstart' in window)) { e.preventDefault(); el.querySelector('#send').click(); } };
  }

  // ---------- действия ----------
  async function send() {
    const text = q.value.trim();
    if (!text) return haptic('warning');
    q.value = '';
    st.messages.push({ role: 'user', content: text });
    if (st.compare) {
      const ids = enabled.slice(0, hub.limits.compare).map((p) => p.id);
      ids.forEach((id) => st.messages.push({ role: 'assistant', provider: id, pending: true }));
      await rerender();
      try {
        const { answers } = await api('/api/hub/compare', { method: 'POST', body: { text, providers: ids } });
        st.messages = st.messages.filter((m) => !m.pending);
        answers.forEach((a) => st.messages.push({ role: 'assistant', provider: a.provider, content: a.reply || '', error: a.error }));
        haptic();
      } catch (err) {
        st.messages = st.messages.filter((m) => !m.pending);
        st.messages.push({ role: 'assistant', provider: ids[0], error: err.message });
        haptic('error');
      }
      return rerender();
    }
    const provider = st.provider;
    st.messages.push({ role: 'assistant', provider, pending: true });
    await rerender();
    try {
      const r = await api('/api/hub/ask', { method: 'POST', body: { text, provider, chatId: st.chatId } });
      st.chatId = r.chatId;
      st.messages.pop();
      st.messages.push({ role: 'assistant', provider: r.provider, content: r.reply });
      haptic();
    } catch (err) {
      st.messages.pop();
      st.messages.push({ role: 'assistant', provider, error: err.message });
      haptic('error');
    }
    rerender();
  }

  async function generate(preset, input, provider) {
    st.studio = { preset, input, loading: true };
    const btn = el.querySelector('#gen');
    if (btn) { btn.disabled = true; btn.textContent = '✨ Генерирую…'; }
    try {
      const r = await api('/api/hub/ask', { method: 'POST', body: { text: input, preset, provider } });
      st.studio = { preset, input, result: r.reply, provider: r.provider, chatId: r.chatId };
      haptic();
    } catch (err) { toast(err.message); haptic('error'); st.studio.loading = false; }
    rerender();
  }

  el.onclick = async (e) => {
    const t = e.target;
    const d = (k) => t.closest(`[data-${k}]`)?.dataset[k];
    try {
      if (d('tab')) { tap(); st.tab = d('tab'); return rerender(); }
      if (d('open')) { tap(); return openLink(d('open')); }
      if (d('copy') !== undefined) return copy(d('copy'));
      if (d('prov')) { tap(); st.compare = false; st.provider = d('prov'); return rerender(); }
      if (t.closest('#compare')) {
        tap(); st.compare = !st.compare;
        if (st.compare) { st.chatId = null; st.messages = []; }
        return rerender();
      }
      if (t.closest('#new')) { tap(); st.chatId = null; st.messages = []; return rerender(); }
      if (t.closest('#send')) return send();
      if (d('delchat')) {
        e.stopPropagation();
        if (!(await confirm('Удалить этот диалог?'))) return;
        await api(`/api/hub/chats/${d('delchat')}`, { method: 'DELETE' });
        if (String(st.chatId) === d('delchat')) { st.chatId = null; st.messages = []; }
        haptic(); return rerender();
      }
      if (d('chat')) {
        tap();
        const c = await api(`/api/hub/chats/${d('chat')}`);
        st.compare = false; st.chatId = c.id; st.provider = enabled.some((p) => p.id === c.provider) ? c.provider : st.provider;
        st.messages = c.messages;
        window.scrollTo({ top: 0, behavior: 'smooth' });
        return rerender();
      }
      // студия
      if (d('preset')) { tap(); st.studio = { preset: d('preset'), input: st.studio?.input || '' }; return rerender(); }
      if (t.closest('#gen')) {
        const input = el.querySelector('#sq').value.trim();
        if (!input) { haptic('warning'); return toast('Опишите задачу'); }
        return generate(st.studio.preset, input, el.querySelector('#sprov').value);
      }
      if (t.closest('#tochat')) {
        const c = await api(`/api/hub/chats/${st.studio.chatId}`);
        st.tab = 'ai'; st.compare = false; st.chatId = c.id; st.provider = c.provider; st.messages = c.messages;
        return rerender();
      }
      // соцсети
      if (t.closest('#look')) {
        const url = el.querySelector('#link').value.trim();
        if (!url) return toast('Вставьте ссылку');
        t.disabled = true;
        st.preview = await api(`/api/hub/preview?url=${encodeURIComponent(url)}`);
        haptic(); return rerender();
      }
      if (t.closest('#remix') || t.closest('#caption')) {
        const p = st.preview;
        const preset = t.closest('#remix') ? 'ideas' : 'caption';
        const input = `${preset === 'ideas' ? 'Придумай идеи по мотивам этого ролика' : 'Сделай описание для похожего ролика'}: «${p.title || ''}» (автор ${p.author || 'неизвестен'}, ${p.platform === 'tiktok' ? 'TikTok' : 'YouTube'}).`;
        st.tab = 'studio';
        if (!enabled.length) { st.studio = { preset, input }; return rerender(); }
        st.studio = { preset, input };
        await rerender();
        return generate(preset, input, st.provider);
      }
      // автоматизации
      if (d('tpl') !== undefined) { tap(); const tpl = TEMPLATES[d('tpl')]; return taskForm({ ...tpl, kind: tpl.kind === 'ai' && !enabled.length ? 'remind' : tpl.kind }); }
      if (t.closest('#addtask')) { tap(); return taskForm(); }
      if (d('edit')) { tap(); return taskForm((st.tasks || []).find((x) => String(x.id) === d('edit'))); }
      if (d('deltask')) {
        if (!(await confirm('Удалить автоматизацию?'))) return;
        await api(`/api/hub/tasks/${d('deltask')}`, { method: 'DELETE' });
        haptic(); return rerender();
      }
      if (d('run')) {
        t.disabled = true; t.textContent = '⏳ Выполняется…';
        await api(`/api/hub/tasks/${d('run')}/run`, { method: 'POST' });
        haptic(); toast('Готово — результат отправлен в чат с ботом 📨');
        return rerender();
      }
    } catch (err) { toast(err.message); haptic('error'); if (t.disabled !== undefined) t.disabled = false; }
  };

  el.onchange = async (e) => {
    const id = e.target.dataset?.toggle;
    if (!id) return;
    try {
      await api(`/api/hub/tasks/${id}`, { method: 'POST', body: { enabled: e.target.checked } });
      haptic(); toast(e.target.checked ? 'Включено ⚡' : 'На паузе');
      e.target.closest('.hub-task')?.classList.toggle('paused', !e.target.checked);
    } catch (err) { toast(err.message); e.target.checked = !e.target.checked; }
  };
}
