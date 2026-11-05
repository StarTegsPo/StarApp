// Оформление Mini App: палитры (CSS-переменные --a1/--a2/--a3/--grad) и сезонные эффекты на canvas.
// Без импортов из app.js, чтобы ядро могло применить тему до первого рендера.

export const PALETTES = [
  { id: 'violet', title: 'Фиолет', a1: '#7B5CFF', a2: '#33A6FF', a3: '#FF5CA8' },
  { id: 'neon', title: 'Неон', a1: '#D12BFF', a2: '#00B4E0', a3: '#2BFF88' },
  { id: 'gold', title: 'Золото', a1: '#E09A0B', a2: '#B4530A', a3: '#FFD34D' },
  { id: 'emerald', title: 'Изумруд', a1: '#10B981', a2: '#0E7490', a3: '#A3E635' },
  { id: 'sunset', title: 'Закат', a1: '#FF7A45', a2: '#E8407F', a3: '#FFC145' },
  { id: 'custom', title: 'Своя' },
];
export const HUES = 12;
/** Цвета палитры «Своя» для оттенка 0..11 (шаг 30°) */
export const customColors = (hue) => {
  const h = (Number(hue) || 0) * 30;
  // умеренная светлота, чтобы белый текст в hero читался на любом оттенке
  return { a1: `hsl(${h} 72% 50%)`, a2: `hsl(${(h + 40) % 360} 78% 42%)`, a3: `hsl(${(h + 300) % 360} 90% 66%)` };
};
export const paletteColors = (s) => (s.palette === 'custom' ? customColors(s.hue) : PALETTES.find((p) => p.id === s.palette) || PALETTES[0]);

export const EFFECTS = [
  { id: 'off', title: 'Выкл', icon: '⭕' },
  { id: 'auto', title: 'Авто', icon: '🗓' },
  { id: 'snow', title: 'Снег', icon: '❄️' },
  { id: 'leaves', title: 'Листопад', icon: '🍂' },
  { id: 'petals', title: 'Лепестки', icon: '🌸' },
  { id: 'fireflies', title: 'Светлячки', icon: '✨' },
];
/** «Авто»: дек–фев снег, мар–май лепестки, июн–авг светлячки, сен–ноя листья */
export function seasonEffect(d = new Date()) {
  const m = d.getMonth();
  return m === 11 || m <= 1 ? 'snow' : m <= 4 ? 'petals' : m <= 7 ? 'fireflies' : 'leaves';
}

export const DEFAULTS = { palette: 'violet', hue: 0, effect: 'off' };
const KEY = 'starapp.theme';
export let current = { ...DEFAULTS };

function normalize(s = {}) {
  return {
    palette: PALETTES.some((p) => p.id === s.palette) ? s.palette : DEFAULTS.palette,
    hue: Number.isInteger(s.hue) && s.hue >= 0 && s.hue < HUES ? s.hue : 0,
    effect: EFFECTS.some((e) => e.id === s.effect) ? s.effect : DEFAULTS.effect,
  };
}

export function loadLocal() {
  try { return normalize(JSON.parse(localStorage.getItem(KEY) || '{}')); } catch { return { ...DEFAULTS }; }
}
function saveLocal(s) {
  try { localStorage.setItem(KEY, JSON.stringify(s)); } catch {}
}

/** Применяет настройки: палитру и эффект. Повторный вызов с теми же значениями ничего не перезапускает. */
export function applyTheme(settings, { persist = true } = {}) {
  const s = normalize({ ...current, ...settings });
  const root = document.documentElement.style;
  if (s.palette === 'violet') {
    ['--a1', '--a2', '--a3', '--grad', '--link'].forEach((v) => root.removeProperty(v));
  } else {
    const c = paletteColors(s);
    root.setProperty('--a1', c.a1);
    root.setProperty('--a2', c.a2);
    root.setProperty('--a3', c.a3);
    root.setProperty('--grad', `linear-gradient(135deg, ${c.a1}, ${c.a2})`);
    root.setProperty('--link', c.a1);
  }
  const effectChanged = s.effect !== current.effect || !fx;
  current = s;
  if (effectChanged) startEffect(s.effect);
  if (persist) saveLocal(s);
  return s;
}

// ---------- Сезонные эффекты ----------
let fx = null; // { canvas, raf, stop }

const reducedMotion = () => window.matchMedia?.('(prefers-reduced-motion: reduce)').matches;

function isDark() {
  const m = getComputedStyle(document.body).backgroundColor.match(/\d+(\.\d+)?/g);
  if (!m) return false;
  const [r, g, b] = m.map(Number);
  return 0.299 * r + 0.587 * g + 0.114 * b < 128;
}

const rnd = (a, b) => a + Math.random() * (b - a);
const pick = (arr) => arr[(Math.random() * arr.length) | 0];

const KINDS = {
  snow: {
    density: 1 / 9000, max: 90,
    make: (w, h, dark) => ({ x: rnd(0, w), y: rnd(-h, h), r: rnd(1.2, 3.6), vy: rnd(0.35, 1.1), sway: rnd(0, 6.28), sw: rnd(0.3, 0.9),
      c: dark ? '255,255,255' : '150,180,235', a: rnd(0.55, 0.95) }),
    step(p, t) { p.y += p.vy; p.x += Math.sin(t / 1400 + p.sway) * p.sw * 0.4; },
    draw(ctx, p) { ctx.globalAlpha = p.a; ctx.fillStyle = `rgb(${p.c})`; ctx.beginPath(); ctx.arc(p.x, p.y, p.r, 0, 6.283); ctx.fill(); },
  },
  leaves: {
    density: 1 / 26000, max: 26,
    make: (w, h) => ({ x: rnd(0, w), y: rnd(-h, h), s: rnd(6, 11), vy: rnd(0.5, 1.2), rot: rnd(0, 6.28), vr: rnd(-0.03, 0.03), sway: rnd(0, 6.28),
      c: pick(['#E8772E', '#D9480F', '#F2A93B', '#B5651D', '#E05A2B']), a: rnd(0.75, 0.95) }),
    step(p, t) { p.y += p.vy; p.x += Math.sin(t / 900 + p.sway) * 0.9; p.rot += p.vr; },
    draw(ctx, p) {
      ctx.globalAlpha = p.a; ctx.save(); ctx.translate(p.x, p.y); ctx.rotate(p.rot); ctx.fillStyle = p.c;
      ctx.beginPath(); ctx.moveTo(0, -p.s); ctx.quadraticCurveTo(p.s * 0.9, 0, 0, p.s); ctx.quadraticCurveTo(-p.s * 0.9, 0, 0, -p.s); ctx.fill();
      ctx.strokeStyle = 'rgba(90,40,10,.35)'; ctx.lineWidth = 0.8; ctx.beginPath(); ctx.moveTo(0, -p.s); ctx.lineTo(0, p.s); ctx.stroke(); ctx.restore();
    },
  },
  petals: {
    density: 1 / 20000, max: 34,
    make: (w, h) => ({ x: rnd(0, w), y: rnd(-h, h), s: rnd(4, 7.5), vy: rnd(0.4, 1), rot: rnd(0, 6.28), vr: rnd(-0.04, 0.04), sway: rnd(0, 6.28),
      c: pick(['#FFB7D0', '#FF9EC4', '#FFC9DC', '#F8A5C2']), a: rnd(0.7, 0.95) }),
    step(p, t) { p.y += p.vy; p.x += Math.sin(t / 1100 + p.sway) * 0.7 + 0.25; p.rot += p.vr; },
    draw(ctx, p) {
      ctx.globalAlpha = p.a; ctx.save(); ctx.translate(p.x, p.y); ctx.rotate(p.rot); ctx.fillStyle = p.c;
      ctx.beginPath(); ctx.ellipse(0, 0, p.s, p.s * 0.55, 0, 0, 6.283); ctx.fill(); ctx.restore();
    },
  },
  fireflies: {
    density: 1 / 22000, max: 30,
    make: (w, h) => ({ x: rnd(0, w), y: rnd(0, h), r: rnd(1.6, 2.8), vx: rnd(-0.3, 0.3), vy: rnd(-0.3, 0.3), ph: rnd(0, 6.28), sp: rnd(0.0015, 0.004) }),
    step(p, t, w, h) {
      p.vx += rnd(-0.03, 0.03); p.vy += rnd(-0.03, 0.03);
      p.vx = Math.max(-0.5, Math.min(0.5, p.vx)); p.vy = Math.max(-0.5, Math.min(0.5, p.vy));
      p.x = (p.x + p.vx + w) % w; p.y = (p.y + p.vy + h) % h;
      p.a = 0.25 + 0.75 * Math.max(0, Math.sin(t * p.sp + p.ph));
    },
    draw(ctx, p, dark) {
      const g = ctx.createRadialGradient(p.x, p.y, 0, p.x, p.y, p.r * 6);
      const c = dark ? '255,230,120' : '240,180,20';
      g.addColorStop(0, `rgba(${c},${p.a})`); g.addColorStop(0.25, `rgba(${c},${p.a * 0.45})`); g.addColorStop(1, `rgba(${c},0)`);
      ctx.globalAlpha = 1; ctx.fillStyle = g; ctx.beginPath(); ctx.arc(p.x, p.y, p.r * 6, 0, 6.283); ctx.fill();
    },
    wrap: true,
  },
};

export function stopEffect() {
  if (!fx) return;
  cancelAnimationFrame(fx.raf);
  removeEventListener('resize', fx.resize);
  fx.canvas.remove();
  fx = null;
}

/** Запускает эффект ('off' | 'auto' | 'snow' | ...). При prefers-reduced-motion не анимирует. */
export function startEffect(effect) {
  stopEffect();
  const kindId = effect === 'auto' ? seasonEffect() : effect;
  const kind = KINDS[kindId];
  if (!kind || reducedMotion()) { fx = { canvas: document.createElement('canvas'), raf: 0, resize: null }; return kindId; }

  const canvas = document.createElement('canvas');
  canvas.id = 'season-fx';
  canvas.setAttribute('aria-hidden', 'true');
  document.body.append(canvas);
  const ctx = canvas.getContext('2d');
  let w = 0, h = 0, ps = [], dark = isDark();

  const resize = () => {
    const dpr = Math.min(devicePixelRatio || 1, 2);
    w = innerWidth; h = innerHeight;
    canvas.width = w * dpr; canvas.height = h * dpr;
    ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
    const n = Math.min(kind.max, Math.max(8, Math.round(w * h * kind.density)));
    while (ps.length < n) ps.push(kind.make(w, h, dark));
    ps.length = n;
  };
  resize();
  addEventListener('resize', resize);

  let lastDarkCheck = 0;
  const frame = (t) => {
    if (t - lastDarkCheck > 2000) { lastDarkCheck = t; dark = isDark(); }
    ctx.clearRect(0, 0, w, h);
    for (const p of ps) {
      kind.step(p, t, w, h);
      if (!kind.wrap && (p.y > h + 20 || p.x < -30 || p.x > w + 30)) Object.assign(p, kind.make(w, h, dark), { y: -15 });
      kind.draw(ctx, p, dark);
    }
    fx.raf = requestAnimationFrame(frame);
  };
  fx = { canvas, raf: requestAnimationFrame(frame), resize };
  return kindId;
}

/** Применить сохранённое локально — сразу при старте, до ответа сервера */
export function initTheme() {
  return applyTheme(loadLocal(), { persist: false });
}
