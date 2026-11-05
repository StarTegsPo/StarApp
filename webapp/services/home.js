import { api, esc, fmt, ago, plural, count, chart, toast, haptic, tap, confetti, share, copy, sheet, animateCounts, empty, tgUser } from '../app.js';

const greeting = () => {
  const h = new Date().getHours();
  return h < 5 ? 'Доброй ночи' : h < 12 ? 'Доброе утро' : h < 18 ? 'Добрый день' : 'Добрый вечер';
};
const WD = ['вс', 'пн', 'вт', 'ср', 'чт', 'пт', 'сб'];
const dayKey = (d) => d.toISOString().slice(0, 10);

function weekDots(streak) {
  const now = new Date();
  const end = new Date(now); if (!streak.checkedToday) end.setUTCDate(end.getUTCDate() - 1);
  const start = new Date(end); start.setUTCDate(start.getUTCDate() - (streak.streak - 1));
  return Array.from({ length: 7 }, (_, k) => {
    const d = new Date(now); d.setUTCDate(d.getUTCDate() - (6 - k));
    const done = streak.streak > 0 && dayKey(d) >= dayKey(start) && dayKey(d) <= dayKey(end);
    const isToday = k === 6;
    return `<div class="d ${done ? 'done' : ''} ${isToday ? 'today' : ''}"><i>${done ? '🔥' : isToday ? '🎁' : ''}</i>${isToday ? 'сегодня' : WD[d.getUTCDay()]}</div>`;
  }).join('');
}

const feedItem = (f, isNew = false) => `
  <div class="item ${f.tone} ${isNew ? 'new' : ''}" data-ts="${f.ts}">
    <div class="ic">${f.icon}</div>
    <div class="main"><div class="t">${esc(f.title)}</div><div class="d">${esc(f.text)}</div></div>
    <div class="when" data-ago="${f.ts}">${ago(f.ts)}</div>
  </div>`;

const FEED_SHOWN = 10;

function feedHtml(feed) {
  if (!feed.length) return empty('✨', 'Здесь появятся события: новые подписчики, продажи, ответы поддержки и бонусы.');
  const head = feed.slice(0, FEED_SHOWN).map((f) => feedItem(f)).join('');
  const rest = feed.slice(FEED_SHOWN);
  return head + (rest.length ? `<div class="more-feed" hidden>${rest.map((f) => feedItem(f)).join('')}</div>
    <button class="btn block secondary" style="margin:6px 0 12px" data-act="more">Показать ещё ${rest.length}</button>` : '');
}

export async function render(el, app) {
  const h = await api('/api/home');
  app.setLevel(h.level.level);
  const name = h.user.first_name || tgUser?.first_name || 'друг';
  const photo = tgUser?.photo_url;
  const lv = h.level;
  const s = h.stats;
  const ref = h.referral;
  const unlocked = h.achievements.filter((a) => a.unlocked).length;

  el.innerHTML = `
    <section class="hero">
      <div class="row" style="justify-content:flex-start;gap:12px">
        <div class="avatar lg">${photo ? `<img src="${esc(photo)}" alt="">` : esc(name[0])}</div>
        <div class="main"><div class="hello">${greeting()},</div><div style="font-size:19px;font-weight:700">${esc(name)} 👋</div></div>
        <span class="badge" style="background:#ffffff2e;color:#fff">LVL ${lv.level}</span>
      </div>
      <div style="margin-top:16px" class="hello">Баланс</div>
      <div class="big">${count(h.user.balance)} ⭐</div>
      <div style="margin:14px 0 6px;display:flex;justify-content:space-between;font-size:12px;opacity:.9">
        <span>Уровень ${lv.level}</span><span>${fmt(lv.xp)} / ${fmt(lv.to)} XP</span>
      </div>
      <div class="progress"><i data-w="${Math.round(lv.progress * 100)}"></i></div>
      <div class="actions" style="margin-top:16px">
        <button class="btn small" data-go="profile">💳 Пополнить</button>
        <button class="btn small ghost" data-act="invite">🎁 Пригласить друга</button>
      </div>
    </section>

    <section class="card" id="checkin">
      <div class="row" style="padding:0;border:0">
        <div class="main"><b style="font-size:17px">🔥 Серия: ${h.streak.streak} ${plural(h.streak.streak, 'день', 'дня', 'дней')}</b>
          <div class="meta">${h.streak.checkedToday ? 'Бонус получен. Завтра будет больше!' : 'Заходите каждый день — опыт растёт с серией'}</div></div>
      </div>
      <div class="week">${weekDots(h.streak)}</div>
      ${h.streak.checkedToday
        ? `<button class="btn block secondary" disabled>✓ Завтра: +${h.streak.nextXp} XP</button>`
        : `<button class="btn block glow" data-act="checkin">Забрать бонус +${h.streak.nextXp} XP</button>`}
    </section>

    <section class="actions-grid">
      <button class="action" data-act="channel"><span class="ic">📢</span>Канал</button>
      <button class="action" data-go="analytics" data-section="bots"><span class="ic">🤖</span>Бот</button>
      <button class="action" data-go="shop"><span class="ic">🛍</span>Магазин</button>
      <button class="action" data-go="support"><span class="ic">💬</span>Помощь</button>
    </section>

    <h3>Ваш бизнес <a data-go="analytics">Аналитика ›</a></h3>
    <section class="hscroll" id="stats">
      <div class="stat" style="width:62%"><div class="label">📢 Подписчики</div><div class="value">${count(s.members)}</div>
        <div class="sub">${s.channels ? `${s.growth >= 0 ? '+' : ''}${fmt(s.growth)} за 30 дней` : 'подключите канал'}</div></div>
      <div class="stat" style="width:62%"><div class="label">🤖 Пользователи ботов</div><div class="value">${count(s.botUsers)}</div>
        <div class="sub">${s.bots ? `${fmt(s.dau)} сегодня` : 'подключите бота'}</div></div>
      <div class="stat" style="width:62%"><div class="label">💰 Выручка, 7 дней</div><div class="value">${count(s.revenue7, ' ⭐')}</div>
        <div class="spark">${chart(s.revenueSpark, { key: 'revenue', mini: true })}</div></div>
    </section>

    <h3>Достижения <span class="muted" style="text-transform:none;letter-spacing:0">${unlocked} из ${h.achievements.length}</span></h3>
    <section class="hscroll">
      ${h.achievements.sort((a, b) => b.unlocked - a.unlocked).map((a) => `
        <div class="ach ${a.unlocked ? '' : 'locked'} clickable" data-ach="${a.id}">
          <span class="e">${a.icon}</span><div class="n">${esc(a.title)}</div>
          ${a.unlocked ? `<div class="p">+${a.xp} XP ✓</div>` : `<div class="progress"><i data-w="${Math.round((a.value / a.target) * 100)}"></i></div><div class="p">${fmt(a.value)} / ${fmt(a.target)}</div>`}
        </div>`).join('')}
    </section>

    ${ref.link ? `
    <section class="card" style="background:linear-gradient(135deg,#ff9a3c22,#ff5ca822)">
      <div class="row" style="padding:0;border:0">
        <div class="ic" style="background:linear-gradient(135deg,#ff9a3c,#ff5ca8);color:#fff">🎁</div>
        <div class="main"><b>Приглашайте друзей</b>
          <div class="meta">${ref.bonus ? `+${ref.bonus} ⭐ за каждого друга после его первой оплаты` : 'Расскажите о StarApp друзьям'}</div></div>
      </div>
      <div class="grid" style="margin:12px 0">
        <div><div class="meta">Приглашено</div><b style="font-size:20px">${count(ref.invited)}</b></div>
        <div><div class="meta">Заработано</div><b style="font-size:20px">${count(ref.paid * ref.bonus, ' ⭐')}</b></div>
      </div>
      <div class="actions"><button class="btn small" data-act="invite" style="flex:1">Поделиться ссылкой</button><button class="btn small secondary" data-act="copy">Копировать</button></div>
    </section>` : ''}

    <h3>Лента <span class="muted" style="text-transform:none;letter-spacing:0;display:flex;align-items:center;gap:6px"><span class="live"></span>в реальном времени</span></h3>
    <section class="card tight feed" id="feed">${feedHtml(h.feed)}</section>`;

  // Прогресс-бары заполняются после появления — так видно анимацию
  requestAnimationFrame(() => requestAnimationFrame(() => el.querySelectorAll('[data-w]').forEach((i) => (i.style.width = `${i.dataset.w}%`))));

  el.onclick = async (e) => {
    const go = e.target.closest('[data-go]');
    if (go) { tap(); if (go.dataset.section) app.next = { section: go.dataset.section }; return app.open(go.dataset.go); }
    const ach = e.target.closest('[data-ach]');
    if (ach) {
      const a = h.achievements.find((x) => x.id === ach.dataset.ach);
      return toast(`${a.icon} ${a.title}: ${a.desc}${a.unlocked ? ' — получено!' : ` (${a.value}/${a.target})`}`);
    }
    const act = e.target.closest('[data-act]')?.dataset.act;
    if (act === 'checkin') {
      const btn = e.target.closest('button'); btn.disabled = true;
      try {
        const r = await api('/api/home/checkin', { method: 'POST' });
        haptic(); confetti();
        toast(`🔥 +${r.xp} XP! Серия ${r.streak} ${plural(r.streak, 'день', 'дня', 'дней')}`);
        await app.rerender(await import('./home.js'));
      } catch (err) { haptic('error'); toast(err.message); btn.disabled = false; }
    }
    if (act === 'invite') {
      if (!ref.link) return toast('Ссылка появится, когда бот будет запущен');
      share(ref.link, '⭐ StarApp — аналитика каналов и ботов прямо в Telegram. Присоединяйся!');
    }
    if (act === 'copy') copy(ref.link);
    if (act === 'more') { tap(); el.querySelector('.more-feed').hidden = false; e.target.closest('button').remove(); }
    if (act === 'channel') {
      const bot = app.me.botUsername;
      const addLink = bot ? `https://t.me/${bot}?startchannel&admin=post_messages+invite_users` : null;
      const { el: sh } = sheet(`
        <h2>📢 Подключить канал</h2>
        <ol class="steps">
          <li><div>Нажмите кнопку ниже и выберите свой канал или группу</div></li>
          <li><div>Подтвердите добавление ${bot ? `<b>@${esc(bot)}</b>` : 'бота'} администратором</div></li>
          <li><div>Готово — подписчики, посты и приток начнут считаться автоматически</div></li>
        </ol>
        ${addLink ? `<button class="btn block" id="add-ch">Выбрать канал</button>` : '<div class="meta">Бот ещё не запущен</div>'}
        <button class="btn block secondary" style="margin-top:8px" data-close>Позже</button>`);
      sh.querySelector('#add-ch')?.addEventListener('click', () => {
        const tg = window.Telegram?.WebApp;
        tg?.openTelegramLink ? tg.openTelegramLink(addLink) : window.open(addLink, '_blank');
      });
    }
  };

  // Живое обновление: каждые 15 секунд подтягиваем новые события и цифры
  let lastTs = h.feed[0]?.ts ?? 0;
  app.timers.push(setInterval(async () => {
    if (document.hidden) return;
    try {
      const fresh = await api('/api/home');
      const feedEl = el.querySelector('#feed');
      const newer = fresh.feed.filter((f) => f.ts > lastTs);
      if (newer.length && feedEl) {
        // перестраиваем ленту целиком: события за день сгруппированы и обновляются, а не дублируются
        const expanded = !feedEl.querySelector('.more-feed[hidden]') && feedEl.querySelector('.more-feed');
        feedEl.innerHTML = feedHtml(fresh.feed);
        if (expanded) { feedEl.querySelector('.more-feed').hidden = false; feedEl.querySelector('[data-act=more]')?.remove(); }
        feedEl.querySelectorAll('.item').forEach((it) => Number(it.dataset.ts) > lastTs && it.classList.add('new'));
        lastTs = fresh.feed[0].ts;
        tap();
      }
      feedEl?.querySelectorAll('[data-ago]').forEach((w) => (w.textContent = ago(Number(w.dataset.ago))));
      const vals = el.querySelectorAll('#stats [data-count]');
      [fresh.stats.members, fresh.stats.botUsers, fresh.stats.revenue7].forEach((v, i) => {
        const c = vals[i];
        if (c && Number(c.dataset.count) !== v) { c.dataset.from = c.dataset.count; c.dataset.count = v; animateCounts(c.parentElement); }
      });
      if (fresh.user.balance !== app.me.user?.balance) app.refreshMe();
    } catch {}
  }, 15000));
}
