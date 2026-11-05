import { api, esc, ago, plural, toast, haptic, tap, copy, sheet, confirm, empty, hue, tgUser } from '../app.js';
import { PALETTES, HUES, EFFECTS, customColors, seasonEffect, applyTheme, current } from '../theme.js';

const greeting = () => {
  const h = new Date().getHours();
  return h < 5 ? 'Доброй ночи' : h < 12 ? 'Доброе утро' : h < 18 ? 'Добрый день' : 'Добрый вечер';
};

const SECTIONS = [
  { id: 'services', title: 'Сервисы', icon: '🌐' },
  { id: 'style', title: 'Стиль', icon: '🎨' },
  { id: 'templates', title: 'Шаблоны', icon: '💬' },
  { id: 'notes', title: 'Заметки', icon: '📝' },
];
let section = 'services';

const tg = () => window.Telegram?.WebApp;
const inTelegram = () => !!tg()?.initData;
function openTg(url) { inTelegram() && tg().openTelegramLink ? tg().openTelegramLink(url) : window.open(url, '_blank'); }
function openExternal(url) { inTelegram() && tg().openLink ? tg().openLink(url) : window.open(url, '_blank', 'noopener'); }

const reducedMotion = () => window.matchMedia?.('(prefers-reduced-motion: reduce)').matches;
const effectInfo = (id) => EFFECTS.find((e) => e.id === id);

export async function render(el, app) {
  if (app.next?.section && SECTIONS.some((s) => s.id === app.next.section)) { section = app.next.section; app.next = null; }
  const data = await api('/api/eco');
  const { limits } = data;
  applyTheme(data.settings);
  const name = app.me?.user?.first_name || tgUser?.first_name || 'друг';

  el.innerHTML = `
    <section class="hero eco-hero">
      <div class="hello">${greeting()}, ${esc(name)} 👋</div>
      <div class="eco-title">⭐ StarTegs для iPhone</div>
      <div class="hello" style="margin-top:6px">Боты экосистемы, темы, сезонные эффекты, шаблоны ответов и заметки — всё в Telegram, без установки мода.</div>
      <div class="eco-chips" id="eco-chips"></div>
    </section>
    <div class="seg eco-seg" id="eco-seg">${SECTIONS.map((s) => `<button data-sec="${s.id}"><span>${s.icon}</span>${s.title}</button>`).join('')}</div>
    <div id="eco-body"></div>`;

  const body = el.querySelector('#eco-body');
  const counts = data.counts;

  const chips = () => {
    const p = PALETTES.find((x) => x.id === current.palette);
    const e = effectInfo(current.effect);
    el.querySelector('#eco-chips').innerHTML = `
      <button class="eco-chip" data-sec="templates">💬 ${counts.templates} ${plural(counts.templates, 'шаблон', 'шаблона', 'шаблонов')}</button>
      <button class="eco-chip" data-sec="notes">📝 ${counts.notes} ${plural(counts.notes, 'заметка', 'заметки', 'заметок')}</button>
      <button class="eco-chip" data-sec="style">🎨 ${esc(p.title)}${current.effect !== 'off' ? ` · ${e.icon}` : ''}</button>`;
  };

  // ---------- Сервисы ----------
  const servicesHtml = () => `
    <h3>Экосистема StarTegs</h3>
    ${data.bots.map((b, i) => `
      <section class="card eco-bot">
        <div class="ic" style="background:${hue(b.title)}">${esc(b.icon)}</div>
        <div class="main">
          <div class="title">${esc(b.title)}</div>
          <div class="meta">${esc(b.desc)}</div>
          <div class="eco-handle">${b.tab ? 'вкладка в этом приложении' : `@${esc(b.bot)}`}</div>
        </div>
        <button class="btn small" data-bot="${i}">Открыть</button>
      </section>`).join('')}
    <h3>Android-версия</h3>
    <section class="card eco-android">
      <div class="row" style="padding:0;border:0;align-items:flex-start">
        <div class="ic">🤖</div>
        <div class="main">
          <b>Полный мод клиента StarTegs</b>
          <div class="meta" style="margin-top:2px">На Android есть полноценный мод Telegram: удалённые сообщения, архив и Premium-функции прямо в клиенте. На iPhone поставить его нельзя — поэтому похожие возможности собраны здесь.</div>
        </div>
      </div>
      <button class="btn block" style="margin-top:12px" data-act="android">⬇️ Скачать для Android</button>
      <div class="meta eco-url">GitHub · релиз client-latest</div>
    </section>`;

  // ---------- Оформление ----------
  const styleHtml = () => {
    const s = current;
    const auto = effectInfo(seasonEffect());
    return `
    <h3>Палитра</h3>
    <section class="card">
      <div class="eco-palettes">${PALETTES.map((p) => {
        const c = p.id === 'custom' ? null : p;
        const bg = c ? `linear-gradient(135deg, ${c.a1}, ${c.a2})` : 'conic-gradient(from 0deg, #ff5a5a, #ffcc33, #4cd964, #33c7ff, #7b5cff, #ff5ca8, #ff5a5a)';
        return `<button class="eco-swatch ${s.palette === p.id ? 'active' : ''}" data-palette="${p.id}"><i style="background:${bg}"></i>${esc(p.title)}</button>`;
      }).join('')}</div>
      ${s.palette === 'custom' ? `
        <div class="meta" style="margin:14px 0 8px">Оттенок</div>
        <div class="eco-hues">${Array.from({ length: HUES }, (_, k) => {
          const c = customColors(k);
          return `<button class="eco-hue ${s.hue === k ? 'active' : ''}" data-hue="${k}" aria-label="Оттенок ${k + 1}" style="background:linear-gradient(135deg, ${c.a1}, ${c.a2})"></button>`;
        }).join('')}</div>` : ''}
      <div class="eco-preview">
        <span class="btn small">Кнопка</span><span class="btn small secondary">Вторичная</span><span class="badge open">Метка</span>
      </div>
    </section>
    <h3>Сезонные эффекты</h3>
    <section class="card">
      <div class="eco-effects">${EFFECTS.map((e) => `<button class="eco-effect ${s.effect === e.id ? 'active' : ''}" data-effect="${e.id}"><span>${e.icon}</span>${esc(e.title)}</button>`).join('')}</div>
      <div class="meta" style="margin-top:12px">«Авто» меняет эффект по времени года — сейчас ${auto.icon} ${esc(auto.title.toLowerCase())}.
        ${reducedMotion() ? '<br>В системе включено «Уменьшение движения» — эффекты не анимируются.' : ''}</div>
    </section>
    <div class="meta" style="text-align:center;margin:4px 0 12px">Оформление сохраняется в аккаунте и применяется на всех устройствах.</div>`;
  };

  // ---------- Шаблоны ----------
  let templates = null;
  const templatesHtml = () => `
    <section class="card form">
      <div class="row" style="padding:0;border:0"><b>Новый шаблон</b><span class="badge">${templates.length} из ${limits.templates}</span></div>
      <input id="tpl-title" placeholder="Название, например «Приветствие»" maxlength="${limits.templateTitle}" />
      <textarea id="tpl-text" placeholder="Текст ответа — его можно будет скопировать одним нажатием" maxlength="${limits.templateText}"></textarea>
      <div class="row" style="padding:0;border:0">
        <span class="meta" id="tpl-count">0 / ${limits.templateText}</span>
        <button class="btn" data-act="tpl-add" ${templates.length >= limits.templates ? 'disabled' : ''}>Сохранить</button>
      </div>
    </section>
    ${templates.length ? `<h3>Мои шаблоны</h3>` + templates.map((t) => `
      <section class="card eco-tpl">
        <b>${esc(t.title)}</b>
        <div class="eco-text">${esc(t.text)}</div>
        <div class="actions" style="margin-top:10px">
          <button class="btn small" data-copy="${t.id}">📋 Копировать</button>
          <button class="btn small danger" data-tpl-del="${t.id}">Удалить</button>
        </div>
      </section>`).join('') : `<section class="card">${empty('💬', 'Шаблонов пока нет. Сохраните частые ответы клиентам — и отправляйте их за секунду.')}</section>`}`;

  // ---------- Заметки ----------
  let notes = null;
  const noteTitle = (n) => n.title || n.text.split('\n')[0].slice(0, 60) || 'Без названия';
  const notesHtml = () => `
    <button class="btn block" data-act="note-new" style="margin:6px 0 4px">＋ Новая заметка</button>
    ${notes.length ? `<h3>Мои заметки <span class="muted" style="text-transform:none;letter-spacing:0">${notes.length}</span></h3>` + notes.map((n) => `
      <section class="card eco-note clickable" data-note="${n.id}">
        <div class="row" style="padding:0;border:0"><b class="eco-note-title">${esc(noteTitle(n))}</b><span class="meta" style="white-space:nowrap">${ago(n.updated_at)}</span></div>
        ${n.text && n.title ? `<div class="eco-text clamp2">${esc(n.text)}</div>` : ''}
      </section>`).join('') : `<section class="card">${empty('📝', 'Здесь будут ваши заметки: идеи, планы, черновики постов.')}</section>`}`;

  async function show(id, { quiet = false } = {}) {
    section = id;
    el.querySelectorAll('#eco-seg button').forEach((b) => b.classList.toggle('active', b.dataset.sec === id));
    if (id === 'templates' && !templates) templates = (await api('/api/eco/templates')).templates;
    if (id === 'notes' && !notes) notes = (await api('/api/eco/notes')).notes;
    if (section !== id) return; // пока грузили, переключились на другую секцию
    body.innerHTML = { services: servicesHtml, style: styleHtml, templates: templatesHtml, notes: notesHtml }[id]();
    if (!quiet) { body.classList.remove('fx'); body.offsetHeight; body.classList.add('fx'); [...body.children].forEach((c, i) => c.style.setProperty('--i', i)); }
    chips();
  }

  async function saveSettings(patch) {
    const prev = { ...current };
    applyTheme(patch);
    tap();
    show('style', { quiet: true });
    try {
      applyTheme(await api('/api/eco/settings', { method: 'POST', body: patch }));
    } catch (err) {
      applyTheme(prev); show('style', { quiet: true }); haptic('error'); toast(err.message);
    }
  }

  function noteEditor(note) {
    const { el: sh, close } = sheet(`
      <h2>${note ? '📝 Заметка' : '📝 Новая заметка'}</h2>
      <div class="form">
        <input id="n-title" placeholder="Заголовок" maxlength="${limits.noteTitle}" value="${esc(note?.title || '')}" />
        <textarea id="n-text" placeholder="Текст заметки" maxlength="${limits.noteText}" style="min-height:160px">${esc(note?.text || '')}</textarea>
        <button class="btn block" id="n-save">Сохранить</button>
        ${note ? `<div class="actions"><button class="btn small secondary" id="n-copy" style="flex:1">📋 Копировать</button><button class="btn small danger" id="n-del" style="flex:1">Удалить</button></div>` : ''}
        <button class="btn block secondary" data-close>Закрыть</button>
      </div>`);
    if (!note) setTimeout(() => sh.querySelector('#n-title').focus(), 300);
    sh.querySelector('#n-save').onclick = async (e) => {
      const title = sh.querySelector('#n-title').value.trim();
      const text = sh.querySelector('#n-text').value.trim();
      if (!title && !text) { haptic('warning'); return toast('Заметка пустая'); }
      e.target.disabled = true;
      try {
        const saved = await api(note ? `/api/eco/notes/${note.id}` : '/api/eco/notes', { method: 'POST', body: { title, text } });
        notes = [saved, ...notes.filter((n) => n.id !== saved.id)];
        if (!note) counts.notes++;
        haptic(); toast(note ? 'Заметка сохранена ✓' : 'Заметка создана ✓'); close(); show('notes');
      } catch (err) { haptic('error'); toast(err.message); e.target.disabled = false; }
    };
    sh.querySelector('#n-copy')?.addEventListener('click', () => copy([note.title, note.text].filter(Boolean).join('\n\n')));
    sh.querySelector('#n-del')?.addEventListener('click', async () => {
      if (!(await confirm('Удалить заметку?'))) return;
      try {
        await api(`/api/eco/notes/${note.id}`, { method: 'DELETE' });
        notes = notes.filter((n) => n.id !== note.id); counts.notes--;
        haptic(); toast('Заметка удалена'); close(); show('notes');
      } catch (err) { toast(err.message); }
    });
  }

  el.oninput = (e) => {
    if (e.target.id === 'tpl-text') el.querySelector('#tpl-count').textContent = `${e.target.value.length} / ${limits.templateText}`;
  };

  el.onclick = async (e) => {
    const sec = e.target.closest('[data-sec]');
    if (sec) { if (sec.dataset.sec !== section) { tap(); show(sec.dataset.sec); } return; }

    const botBtn = e.target.closest('[data-bot]');
    if (botBtn) {
      const b = data.bots[Number(botBtn.dataset.bot)];
      tap();
      return b.tab ? app.open(b.tab) : openTg(`https://t.me/${b.bot}?startapp`);
    }
    const pal = e.target.closest('[data-palette]');
    if (pal) return pal.dataset.palette !== current.palette && saveSettings({ palette: pal.dataset.palette });
    const hueBtn = e.target.closest('[data-hue]');
    if (hueBtn) return saveSettings({ hue: Number(hueBtn.dataset.hue) });
    const eff = e.target.closest('[data-effect]');
    if (eff) return eff.dataset.effect !== current.effect && saveSettings({ effect: eff.dataset.effect });

    const cp = e.target.closest('[data-copy]');
    if (cp) return copy(templates.find((t) => t.id === Number(cp.dataset.copy))?.text || '');
    const del = e.target.closest('[data-tpl-del]');
    if (del) {
      const t = templates.find((x) => x.id === Number(del.dataset.tplDel));
      if (!(await confirm(`Удалить шаблон «${t.title}»?`))) return;
      try {
        await api(`/api/eco/templates/${t.id}`, { method: 'DELETE' });
        templates = templates.filter((x) => x.id !== t.id); counts.templates--;
        haptic(); toast('Шаблон удалён'); show('templates', { quiet: true });
      } catch (err) { toast(err.message); }
      return;
    }
    const note = e.target.closest('[data-note]');
    if (note) { tap(); return noteEditor(notes.find((n) => n.id === Number(note.dataset.note))); }

    const act = e.target.closest('[data-act]')?.dataset.act;
    if (act === 'android') { tap(); openExternal(data.android); }
    if (act === 'note-new') { tap(); noteEditor(null); }
    if (act === 'tpl-add') {
      const title = el.querySelector('#tpl-title').value.trim();
      const text = el.querySelector('#tpl-text').value.trim();
      if (!title) { haptic('warning'); return toast('Введите название шаблона'); }
      if (!text) { haptic('warning'); return toast('Введите текст шаблона'); }
      const btn = e.target.closest('button'); btn.disabled = true;
      try {
        const t = await api('/api/eco/templates', { method: 'POST', body: { title, text } });
        templates = [t, ...templates]; counts.templates++;
        haptic(); toast('Шаблон сохранён ✓'); show('templates', { quiet: true });
      } catch (err) { haptic('error'); toast(err.message); btn.disabled = false; }
    }
  };

  await show(section, { quiet: true });
}

