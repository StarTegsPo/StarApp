// StarGames Mini App: авторизация, навигация с анимированными переходами, экраны, общие UI-хелперы.
const tg = window.Telegram?.WebApp;
const inTelegram = !!tg?.initData;
const root = document.documentElement;
const view = document.getElementById('view');
const nav = document.getElementById('nav');
const reduced = matchMedia('(prefers-reduced-motion: reduce)').matches;

if (inTelegram) {
  tg.ready();
  tg.expand();
  root.dataset.theme = tg.colorScheme === 'light' ? 'light' : 'dark';
  tg.onEvent?.('themeChanged', () => (root.dataset.theme = tg.colorScheme === 'light' ? 'light' : 'dark'));
  try { tg.setHeaderColor(root.dataset.theme === 'light' ? '#f3f1fb' : '#0b0a16'); tg.setBackgroundColor(root.dataset.theme === 'light' ? '#f3f1fb' : '#0b0a16'); } catch {}
} else if (matchMedia('(prefers-color-scheme: light)').matches) {
  root.dataset.theme = 'light';
}

// ---------- API ----------
export async function api(path, { method = 'GET', body } = {}) {
  const res = await fetch(path, {
    method,
    headers: { 'content-type': 'application/json', authorization: `tma ${tg?.initData || ''}` },
    body: body ? JSON.stringify(body) : undefined,
  });
  const data = await res.json().catch(() => ({}));
  if (!res.ok) throw Object.assign(new Error(data.error || `Ошибка ${res.status}`), { status: res.status });
  return data;
}

// ---------- хелперы ----------
export const esc = (s) => String(s ?? '').replace(/[&<>"']/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' })[c]);
export const plural = (n, one, few, many) => {
  const m10 = n % 10, m100 = n % 100;
  return m10 === 1 && m100 !== 11 ? one : m10 >= 2 && m10 <= 4 && (m100 < 10 || m100 >= 20) ? few : many;
};
export function formatScore(game, v) {
  if (v == null) return '—';
  const n = Number(v);
  if (game.unit === 'ms') return `${n} мс`;
  if (game.unit === 'sec') return `${Math.floor(n / 60)}:${String(n % 60).padStart(2, '0')}`;
  if (game.unit === 'wins') return `${n} ${plural(n, 'победа', 'победы', 'побед')}`;
  return n.toLocaleString('ru-RU');
}
const gameVars = (g) => `--c1:${g.colors[0]};--c2:${g.colors[1]}`;
export const haptic = (type = 'success') => tg?.HapticFeedback?.notificationOccurred(type);
export const tap = (style = 'light') => tg?.HapticFeedback?.impactOccurred(style);

export function toast(text) {
  const el = document.getElementById('toast');
  el.textContent = text;
  el.hidden = false;
  el.style.animation = 'none'; el.offsetHeight; el.style.animation = '';
  clearTimeout(toast.t);
  toast.t = setTimeout(() => (el.hidden = true), 2400);
}

function avatar(name, photo) {
  if (photo) return `<div class="avatar"><img src="${esc(photo)}" alt="" referrerpolicy="no-referrer"></div>`;
  let h = 0;
  for (const c of String(name)) h = (h * 31 + c.charCodeAt(0)) % 360;
  return `<div class="avatar" style="background:linear-gradient(135deg,hsl(${h} 80% 60%),hsl(${(h + 50) % 360} 80% 50%))">${esc(String(name).trim()[0] || '?').toUpperCase()}</div>`;
}

/** Анимированный счётчик чисел */
function countUp(el, to, ms = 900) {
  if (reduced || !Number.isFinite(to)) return void (el.textContent = to.toLocaleString('ru-RU'));
  const start = performance.now();
  const step = (t) => {
    const k = Math.min(1, (t - start) / ms);
    el.textContent = Math.round(to * (1 - Math.pow(1 - k, 3))).toLocaleString('ru-RU');
    if (k < 1) requestAnimationFrame(step);
  };
  requestAnimationFrame(step);
}

// Волна от касания на всех кнопках и карточках
document.addEventListener('pointerdown', (e) => {
  const el = e.target.closest('.btn, .icon-btn, .game-card, .tab-chip, .duel, [data-ripple]');
  if (!el || reduced) return;
  const r = el.getBoundingClientRect();
  const size = Math.max(r.width, r.height) * 2.2;
  const s = document.createElement('span');
  s.className = 'ripple';
  s.style.cssText = `width:${size}px;height:${size}px;left:${e.clientX - r.left - size / 2}px;top:${e.clientY - r.top - size / 2}px`;
  el.appendChild(s);
  setTimeout(() => s.remove(), 650);
});

// ---------- конфетти ----------
export function confetti(colors = ['#8b5cf6', '#ec4899', '#22c55e', '#fbbf24', '#38bdf8']) {
  if (reduced) return;
  const cv = document.getElementById('confetti');
  const ctx = cv.getContext('2d');
  const dpr = devicePixelRatio || 1;
  cv.width = innerWidth * dpr; cv.height = innerHeight * dpr;
  ctx.scale(dpr, dpr);
  const parts = Array.from({ length: 140 }, () => ({
    x: innerWidth / 2 + (Math.random() - 0.5) * 80, y: innerHeight * 0.45,
    vx: (Math.random() - 0.5) * 14, vy: -Math.random() * 15 - 5,
    w: 6 + Math.random() * 6, h: 8 + Math.random() * 8, r: Math.random() * 6, vr: (Math.random() - 0.5) * 0.4,
    c: colors[Math.floor(Math.random() * colors.length)],
  }));
  const t0 = performance.now();
  const frame = (t) => {
    ctx.clearRect(0, 0, innerWidth, innerHeight);
    for (const p of parts) {
      p.vy += 0.38; p.vx *= 0.99; p.x += p.vx; p.y += p.vy; p.r += p.vr;
      ctx.save(); ctx.translate(p.x, p.y); ctx.rotate(p.r); ctx.fillStyle = p.c;
      ctx.fillRect(-p.w / 2, -p.h / 2, p.w, p.h * Math.abs(Math.cos(p.r * 2))); ctx.restore();
    }
    if (t - t0 < 3200) requestAnimationFrame(frame);
    else ctx.clearRect(0, 0, innerWidth, innerHeight);
  };
  requestAnimationFrame(frame);
}

// ---------- состояние ----------
const S = {
  me: null,
  games: [],
  profile: null,
  online: { total: 0, byGame: {} },
  offline: false,
  tab: 'home',
  current: null, // { game, instance }
  lbGame: null,
};
const startParams = new URLSearchParams(location.search);
const startParam = tg?.initDataUnsafe?.start_param || '';
if (/^ttt_[A-Z0-9]{5}$/i.test(startParam)) startParams.set('join', startParam.slice(4));

const localBest = {
  get: (id) => { try { return JSON.parse(localStorage.getItem('sg-best') || '{}')[id] ?? null; } catch { return null; } },
  set: (id, v) => { try { const o = JSON.parse(localStorage.getItem('sg-best') || '{}'); o[id] = v; localStorage.setItem('sg-best', JSON.stringify(o)); } catch {} },
};
const bestOf = (g) => S.profile?.stats?.[g.id]?.best ?? localBest.get(g.id);

// ---------- навигация с переходами ----------
const ORDER = { home: 0, top: 1, profile: 2 };
async function transition(render, dir = 'fade') {
  window.scrollTo({ top: 0 });
  if (document.startViewTransition && !reduced) {
    root.dataset.dir = dir;
    await document.startViewTransition(render).finished.catch(() => {});
  } else {
    render();
    if (!reduced) view.animate([{ opacity: 0, transform: 'translateY(12px)' }, { opacity: 1, transform: 'none' }], { duration: 320, easing: 'cubic-bezier(.22,1,.36,1)' });
  }
}

function setNav(tab) {
  const i = ORDER[tab] ?? 0;
  document.getElementById('navPill').style.transform = `translateX(${i * 100}%)`;
  nav.querySelectorAll('.nav-btn').forEach((b) => b.classList.toggle('active', b.dataset.tab === tab));
}

function goTab(tab, forceDir) {
  if (S.current) closeGame(false);
  const dir = forceDir || (tab === S.tab ? 'fade' : ORDER[tab] > ORDER[S.tab] ? 'forward' : 'back');
  S.tab = tab;
  setNav(tab);
  nav.classList.remove('hidden');
  view.classList.remove('no-nav');
  tg?.BackButton?.hide();
  try { tg?.enableVerticalSwipes?.(); } catch {}
  transition(() => SCREENS[tab](), dir);
  ping();
}
nav.addEventListener('click', (e) => {
  const b = e.target.closest('.nav-btn');
  if (!b || b.dataset.tab === S.tab) return;
  tap('soft');
  goTab(b.dataset.tab);
});

// ---------- онлайн ----------
async function ping() {
  if (S.offline) return;
  try {
    S.online = await api('/api/ping', { method: 'POST', body: { game: S.current?.game.id } });
    document.querySelectorAll('[data-online]').forEach((el) => {
      const g = el.dataset.online;
      el.textContent = g ? (S.online.byGame[g] || 0) : S.online.total;
    });
  } catch {}
}
setInterval(ping, 20_000);
document.addEventListener('visibilitychange', () => document.visibilityState === 'visible' && ping());

// ================= ЭКРАНЫ =================
const SCREENS = {
  home() {
    const p = S.profile;
    const day = Math.floor(Date.now() / 86_400_000);
    const featured = S.games.filter((g) => !g.online)[day % S.games.filter((g) => !g.online).length];
    const online = S.games.find((g) => g.online);
    const name = S.me?.name || tg?.initDataUnsafe?.user?.first_name || 'Игрок';
    view.innerHTML = `
      <header class="head rise">
        ${avatar(name, S.me?.photo || tg?.initDataUnsafe?.user?.photo_url)}
        <div class="head-text">
          <h1>Привет, ${esc(name)}!</h1>
          ${p ? `<p>Уровень ${p.level} · ${p.levelXp}/${p.levelNeed} XP</p><div class="xpbar"><i id="xp"></i></div>` : '<p>Офлайн-режим — рекорды хранятся на устройстве</p>'}
        </div>
        ${S.offline ? '' : `<span class="online-pill"><span class="dot"></span><span data-online>${S.online.total}</span>&nbsp;онлайн</span>`}
      </header>

      <button class="card hero rise" style="--i:1;${gameVars(featured)}" data-play="${featured.id}">
        <span class="tag">⭐ Игра дня</span>
        <span class="big-icon">${featured.icon}</span>
        <div>
          <h2>${esc(featured.title)}</h2>
          <p>${esc(featured.desc)}</p>
          <span class="btn">▶&nbsp; Играть</span>
        </div>
      </button>

      ${online ? `
      <div class="card duel rise" style="--i:2">
        <div class="duel-icon">⚔️</div>
        <div class="duel-text">
          <b>Онлайн-дуэль</b>
          <span><span class="dot"></span><span><span data-online="${online.id}">${S.online.byGame[online.id] || 0}</span> в игре · ${esc(online.title)}</span></span>
        </div>
        <button class="btn online small" data-play="${online.id}" data-mode="find"><span class="dot"></span>Найти</button>
      </div>` : ''}

      <div class="section-title rise" style="--i:3"><h2>Все игры</h2><span>${S.games.length} ${plural(S.games.length, 'игра', 'игры', 'игр')}</span></div>
      <div class="grid">
        ${S.games.map((g, i) => `
          <button class="card game-card rise" style="--i:${i + 4};${gameVars(g)}" data-play="${g.id}">
            ${g.online ? '<span class="badge-online">ОНЛАЙН</span>' : ''}
            <div class="g-icon">${g.icon}</div>
            <h3>${esc(g.title)}</h3>
            <div class="meta">
              <span>${bestOf(g) != null ? `🏆 <span class="best">${formatScore(g, bestOf(g))}</span>` : 'Новая'}</span>
              ${S.offline ? '' : `<span class="mini-online"><span class="dot"></span><span data-online="${g.id}">${S.online.byGame[g.id] || 0}</span></span>`}
            </div>
          </button>`).join('')}
      </div>`;
    if (p) requestAnimationFrame(() => (document.getElementById('xp').style.width = `${(p.levelXp / p.levelNeed) * 100}%`));
    view.querySelectorAll('[data-play]').forEach((b) =>
      b.addEventListener('click', (e) => {
        e.stopPropagation();
        tap('medium');
        openGame(b.dataset.play, { mode: b.dataset.mode });
      }),
    );
  },

  async top() {
    S.lbGame ??= S.games[0].id;
    view.innerHTML = `
      <header class="head rise"><div class="head-text"><h1 style="font-size:24px">🏆 Рейтинг</h1><p>Лучшие игроки StarGames</p></div></header>
      <div class="tabs-row rise" style="--i:1">
        ${S.games.map((g) => `<button class="tab-chip ${g.id === S.lbGame ? 'active' : ''}" style="${gameVars(g)}" data-g="${g.id}">${g.icon} ${esc(g.title)}</button>`).join('')}
      </div>
      <div id="lb"></div>`;
    view.querySelectorAll('.tab-chip').forEach((b) =>
      b.addEventListener('click', () => {
        if (b.dataset.g === S.lbGame) return;
        tap();
        S.lbGame = b.dataset.g;
        view.querySelectorAll('.tab-chip').forEach((x) => x.classList.toggle('active', x === b));
        b.scrollIntoView({ behavior: 'smooth', inline: 'center', block: 'nearest' });
        loadBoard();
      }),
    );
    view.querySelector('.tab-chip.active')?.scrollIntoView({ inline: 'center', block: 'nearest' });
    loadBoard();
  },

  profile() {
    const p = S.profile;
    const name = S.me?.name || 'Игрок';
    const pct = p ? Math.round((p.levelXp / p.levelNeed) * 100) : 0;
    view.innerHTML = `
      <section class="prof rise">
        <div class="ring" id="ring">${avatar(name, S.me?.photo)}<span class="lvl">LVL ${p?.level ?? 1}</span></div>
        <h1>${esc(name)}</h1>
        <p>${p ? `${p.levelXp} / ${p.levelNeed} XP до ${p.level + 1} уровня` : 'Офлайн-режим'}</p>
      </section>
      <div class="stats3">
        <div class="card stat rise" style="--i:1"><b data-count="${p?.plays ?? 0}">0</b><span>${plural(p?.plays ?? 0, 'партия', 'партии', 'партий')}</span></div>
        <div class="card stat rise" style="--i:2"><b data-count="${p?.wins ?? 0}">0</b><span>онлайн-побед</span></div>
        <div class="card stat rise" style="--i:3"><b data-count="${p?.xp ?? 0}">0</b><span>опыта</span></div>
      </div>
      <div class="section-title rise" style="--i:4"><h2>Мои рекорды</h2></div>
      <div class="grow-list">
        ${S.games.map((g, i) => {
          const st = p?.stats?.[g.id] || { best: localBest.get(g.id), plays: 0 };
          const sub = g.online ? `🏆 ${st.wins ?? 0} · 💔 ${st.losses ?? 0} · 🤝 ${st.draws ?? 0}` : `${st.plays} ${plural(st.plays, 'партия', 'партии', 'партий')}`;
          return `
          <button class="card g-row rise" style="--i:${i + 5};${gameVars(g)}" data-play="${g.id}" data-ripple>
            <div class="g-icon">${g.icon}</div>
            <div class="t"><b>${esc(g.title)}</b><span>${sub}</span></div>
            <div class="v">${formatScore(g, st.best)}<small>рекорд</small></div>
          </button>`;
        }).join('')}
      </div>`;
    const ring = document.getElementById('ring');
    ring.style.setProperty('--p', 0);
    // плавное заполнение кольца уровня
    let k = 0;
    const fill = () => { k = Math.min(pct, k + Math.max(1, pct / 30)); ring.style.setProperty('--p', k); if (k < pct) requestAnimationFrame(fill); };
    reduced ? ring.style.setProperty('--p', pct) : requestAnimationFrame(fill);
    view.querySelectorAll('[data-count]').forEach((el) => countUp(el, Number(el.dataset.count)));
    view.querySelectorAll('[data-play]').forEach((b) => b.addEventListener('click', () => { tap('medium'); openGame(b.dataset.play); }));
  },
};

async function loadBoard() {
  const box = document.getElementById('lb');
  const g = S.games.find((x) => x.id === S.lbGame);
  box.style.cssText = gameVars(g);
  if (S.offline) {
    box.innerHTML = `<div class="empty"><div class="e">📡</div>Рейтинг доступен, когда приложение открыто из Telegram</div>`;
    return;
  }
  box.innerHTML = `<div class="skeleton" style="height:210px"></div><div class="skeleton" style="height:58px;margin-top:10px"></div><div class="skeleton" style="height:58px;margin-top:8px"></div>`;
  let data;
  try { data = await api(`/api/leaderboard/${g.id}`); } catch (e) { box.innerHTML = `<div class="empty"><div class="e">⚠️</div>${esc(e.message)}</div>`; return; }
  if (S.lbGame !== g.id) return;
  const myId = S.me?.id;
  const [a, b, c] = data.top;
  const pod = (r, cls, place) => r ? `
    <div class="p ${cls}">${avatar(r.name)}<div class="name">${esc(r.name)}</div><div class="val">${formatScore(g, r.best)}</div><div class="step">${place}</div></div>`
    : `<div class="p ${cls}"><div class="step" style="opacity:.25">${place}</div></div>`;
  box.innerHTML = !data.top.length
    ? `<div class="empty rise"><div class="e">${g.icon}</div>Здесь пока пусто.<br>Сыграйте первым и займите вершину!<br><br><button class="btn game" style="${gameVars(g)}" id="lbPlay">Играть</button></div>`
    : `
      <div class="podium rise">${pod(b, 'second', 2)}${pod(a, 'first', 1)}${pod(c, 'third', 3)}</div>
      <div class="rows">
        ${data.top.slice(3).map((r, i) => `
          <div class="row rise ${r.id === myId ? 'me' : ''}" style="--i:${i}">
            <span class="n">${r.rank}</span>${avatar(r.name)}<span class="who">${esc(r.name)}</span><span class="val">${formatScore(g, r.best)}</span>
          </div>`).join('')}
      </div>
      ${data.me.rank ? `
        <div class="row me my-rank"><span class="n">${data.me.rank}</span>${avatar(S.me?.name || '?', S.me?.photo)}<span class="who">Вы</span><span class="val">${formatScore(g, data.me.best)}</span></div>` : ''}`;
  document.getElementById('lbPlay')?.addEventListener('click', () => openGame(g.id));
}

// ================= ЭКРАН ИГРЫ =================
const loaded = {};
async function openGame(id, params = {}) {
  const game = S.games.find((g) => g.id === id);
  if (!game) return;
  const mod = (loaded[id] ??= await import(`./games/${id}.js`));
  nav.classList.add('hidden');
  view.classList.add('no-nav');
  tg?.BackButton?.show();
  try { tg?.disableVerticalSwipes?.(); } catch {}
  await transition(() => {
    view.innerHTML = `
      <div style="${gameVars(game)}">
        <div class="gbar">
          <button class="icon-btn" id="gBack" aria-label="Назад"><svg viewBox="0 0 24 24"><path d="M15 18l-6-6 6-6"/></svg></button>
          <h1>${game.icon} ${esc(game.title)}</h1>
          <button class="icon-btn" id="gRestart" aria-label="Заново"><svg viewBox="0 0 24 24"><path d="M3 12a9 9 0 1 0 3-6.7L3 8M3 3v5h5"/></svg></button>
        </div>
        <div class="chips">
          <div class="chip accent"><small id="scoreLabel">Счёт</small><b id="score">0</b></div>
          <div class="chip"><small id="infoLabel">Рекорд</small><b id="info">${formatScore(game, bestOf(game))}</b></div>
        </div>
        <div class="stage" id="stage"></div>
      </div>`;
    document.getElementById('gBack').onclick = () => closeGame(true);
    document.getElementById('gRestart').onclick = () => { tap('medium'); mountGame(game, mod, {}); };
    mountGame(game, mod, { ...Object.fromEntries(startParams), ...params });
  }, 'forward');
  startParams.delete('join');
  startParams.delete('game');
  ping();
}

function mountGame(game, mod, params) {
  S.current?.instance?.destroy?.();
  const stage = document.getElementById('stage');
  stage.innerHTML = '';
  const scoreEl = document.getElementById('score');
  const h = {
    game, stage, params, api, tap, haptic, toast, esc, confetti, formatScore,
    me: S.me, online: () => S.online, offline: S.offline,
    setScore(v, label) {
      if (label) document.getElementById('scoreLabel').textContent = label;
      if (scoreEl.textContent === String(v)) return;
      scoreEl.textContent = v;
      scoreEl.classList.remove('bump'); void scoreEl.offsetWidth; scoreEl.classList.add('bump');
    },
    setInfo(v, label) {
      if (label) document.getElementById('infoLabel').textContent = label;
      document.getElementById('info').textContent = v;
    },
    over: (res) => showResult(game, mod, res),
    restart: () => mountGame(game, mod, {}),
  };
  S.current = { game, instance: mod.mount(h) };
}

function closeGame(navigate) {
  S.current?.instance?.destroy?.();
  S.current = null;
  closeSheet();
  if (navigate) { tap(); goTab(S.tab || 'home', 'back'); }
}
tg?.BackButton?.onClick(() => (document.getElementById('sheet').hidden ? closeGame(true) : closeSheet()));

// ---------- результат партии ----------
async function showResult(game, mod, res) {
  const { score = null, submit = true, title = 'Игра окончена', emoji = '🎮', text = '', win = false } = res;
  let info = null;
  if (submit && !S.offline) {
    try {
      info = await api('/api/score', { method: 'POST', body: { game: game.id, score } });
      S.profile = info.profile;
    } catch (e) { toast(e.message); }
  }
  if (submit && score != null) {
    const prev = localBest.get(game.id);
    if (prev == null || (game.order === 'asc' ? score < prev : score > prev)) localBest.set(game.id, score);
  }
  const record = info ? info.isRecord : false;
  const best = info?.best ?? localBest.get(game.id);
  if (best != null && game.unit !== 'wins') document.getElementById('info').textContent = formatScore(game, best);
  if (record || win || info?.first) { confetti(game.colors.concat('#fbbf24', '#ffffff')); haptic('success'); }
  else haptic(win === false && score == null ? 'error' : 'warning');

  const sheet = document.getElementById('sheet');
  sheet.style.cssText = gameVars(game);
  sheet.innerHTML = `
    <div class="sheet" role="dialog" aria-modal="true">
      <div class="grab"></div>
      <div class="emoji">${record ? '🏆' : emoji}</div>
      <h2>${esc(record ? 'Новый рекорд!' : title)}</h2>
      ${score != null && game.unit !== 'wins' ? `<div class="score-big">${formatScore(game, score)}</div>` : ''}
      ${record ? '<div class="record">✨ Лучше, чем когда-либо</div>' : ''}
      <p>${esc(text)}${info?.rank ? `${text ? ' · ' : ''}Место в рейтинге: #${info.rank}` : ''}</p>
      <div class="actions">
        <button class="btn game wide" id="again">${esc(res.againText || 'Играть ещё')}</button>
        <button class="btn ghost" id="toMenu">В меню</button>
        <button class="btn ghost" id="shareRes">Поделиться</button>
      </div>
    </div>`;
  sheet.hidden = false;
  sheet.classList.remove('closing');
  sheet.onclick = (e) => e.target === sheet && closeSheet();
  sheet.querySelector('#again').onclick = () => { tap('medium'); closeSheet(); (res.onAgain || (() => mountGame(game, mod, {})))(); };
  sheet.querySelector('#toMenu').onclick = () => closeGame(true);
  sheet.querySelector('#shareRes').onclick = () => {
    const bot = S.me?.botUsername;
    const url = bot ? `https://t.me/${bot}` : location.origin;
    const what = score != null && game.unit !== 'wins' ? `${formatScore(game, score)} в «${game.title}»` : `партию в «${game.title}»`;
    const msg = `${game.icon} Я сыграл ${what} в StarGames! Сможешь лучше?`;
    const u = `https://t.me/share/url?url=${encodeURIComponent(url)}&text=${encodeURIComponent(msg)}`;
    tg?.openTelegramLink ? tg.openTelegramLink(u) : window.open(u, '_blank');
  };
}
function closeSheet() {
  const sheet = document.getElementById('sheet');
  if (sheet.hidden) return;
  sheet.classList.add('closing');
  setTimeout(() => { sheet.hidden = true; sheet.classList.remove('closing'); }, 240);
}

// ================= СТАРТ =================
async function boot() {
  view.innerHTML = `<div class="skeleton" style="height:56px;margin:4px 0 18px"></div><div class="skeleton" style="height:178px"></div><div class="skeleton" style="height:84px;margin-top:14px"></div>`;
  try {
    const me = await api('/api/me');
    S.me = { ...me.user, botUsername: me.botUsername };
    S.games = me.games;
    S.profile = me.profile;
    S.online = me.online;
  } catch {
    // Открыто в обычном браузере без Telegram — играем офлайн, рекорды в localStorage
    S.offline = true;
    S.games = (await api('/api/games').catch(() => ({ games: [] }))).games;
  }
  if (!S.games.length) { view.innerHTML = '<div class="empty"><div class="e">⚠️</div>Не удалось загрузить игры. Проверьте соединение.</div>'; return; }
  setNav('home');
  SCREENS.home();
  const g = startParams.get('join') ? 'ttt' : startParams.get('game');
  if (g && S.games.some((x) => x.id === g)) openGame(g, startParams.get('join') ? { mode: 'join', join: startParams.get('join') } : {});
  ping();
}
boot();
