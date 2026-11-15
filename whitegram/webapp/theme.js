// WhiteGram Theme: палитры и сезонные эффекты
const PALETTES = {
  white: { a1: '#ffffff', a2: '#e8e8e8', a3: '#d0d0d0', grad: 'linear-gradient(135deg, #ffffff 0%, #e8e8e8 50%, #d0d0d0 100%)', name: 'Белый' },
  silver: { a1: '#c0c0c0', a2: '#a8a8a8', a3: '#909090', grad: 'linear-gradient(135deg, #c0c0c0 0%, #a8a8a8 50%, #909090 100%)', name: 'Серебро' },
  graphite: { a1: '#4a4a4a', a2: '#383838', a3: '#262626', grad: 'linear-gradient(135deg, #4a4a4a 0%, #383838 50%, #262626 100%)', name: 'Графит' },
  pearl: { a1: '#fafafa', a2: '#f0f0f0', a3: '#e6e6e6', grad: 'linear-gradient(135deg, #fafafa 0%, #f0f0f0 50%, #e6e6e6 100%)', name: 'Жемчуг' },
  frost: { a1: '#e0f0ff', a2: '#b8d8ff', a3: '#90c0ff', grad: 'linear-gradient(135deg, #e0f0ff 0%, #b8d8ff 50%, #90c0ff 100%)', name: 'Иней' },
  ash: { a1: '#b8b8b8', a2: '#a0a0a0', a3: '#888888', grad: 'linear-gradient(135deg, #b8b8b8 0%, #a0a0a0 50%, #888888 100%)', name: 'Пепел' },
};

const EFFECTS = {
  none: { name: 'Выкл', render: null },
  auto: { name: 'Авто', render: null },
  snow: { name: '❄️ Снег', render: createSnow },
  leaves: { name: '🍂 Листья', render: createLeaves },
  petals: { name: '🌸 Лепестки', render: createPetals },
  sparkles: { name: '✨ Блики', render: createSparkles },
};

let effectCanvas = null;
let effectAnim = null;
let currentEffect = 'none';

function createCanvas() {
  if (effectCanvas) return effectCanvas;
  effectCanvas = document.createElement('canvas');
  effectCanvas.id = 'wg-effect';
  effectCanvas.style.cssText = 'position:fixed;top:0;left:0;width:100%;height:100%;pointer-events:none;z-index:9999';
  document.body.append(effectCanvas);
  return effectCanvas;
}

function resizeCanvas() {
  if (!effectCanvas) return;
  const dpr = devicePixelRatio || 1;
  effectCanvas.width = innerWidth * dpr;
  effectCanvas.height = innerHeight * dpr;
  effectCanvas.style.width = innerWidth + 'px';
  effectCanvas.style.height = innerHeight + 'px';
}

function runEffect(renderFn) {
  if (!renderFn) return stopEffect();
  const canvas = createCanvas();
  const ctx = canvas.getContext('2d');
  const dpr = devicePixelRatio || 1;
  ctx.scale(dpr, dpr);
  const particles = renderFn(canvas);
  let t0 = performance.now();

  function frame(t) {
    ctx.clearRect(0, 0, canvas.width / dpr, canvas.height / dpr);
    for (const p of particles) p.update(t - t0);
    particles.forEach((p) => p.draw(ctx));
    if (particles.some((p) => p.alive)) effectAnim = requestAnimationFrame(frame);
    else stopEffect();
  }
  effectAnim = requestAnimationFrame(frame);
}

function stopEffect() {
  if (effectAnim) cancelAnimationFrame(effectAnim);
  effectAnim = null;
  if (effectCanvas) { effectCanvas.remove(); effectCanvas = null; }
}

function createSnow(canvas) {
  const w = canvas.width / (devicePixelRatio || 1), h = canvas.height / (devicePixelRatio || 1);
  return Array.from({ length: 80 }, () => ({
    x: Math.random() * w, y: Math.random() * h,
    vx: (Math.random() - 0.5) * 0.5, vy: Math.random() * 1.5 + 0.5,
    r: Math.random() * 3 + 1, alive: true,
    update(dt) { this.y += this.vy * dt / 16; this.x += this.vx * dt / 16; if (this.y > h) { this.y = -10; this.x = Math.random() * w; } },
    draw(ctx) { ctx.fillStyle = 'rgba(255,255,255,0.9)'; ctx.beginPath(); ctx.arc(this.x, this.y, this.r, 0, Math.PI * 2); ctx.fill(); },
  }));
}

function createLeaves(canvas) {
  const w = canvas.width / (devicePixelRatio || 1), h = canvas.height / (devicePixelRatio || 1);
  return Array.from({ length: 40 }, () => ({
    x: Math.random() * w, y: Math.random() * h,
    vx: (Math.random() - 0.5) * 1, vy: Math.random() * 1 + 0.3,
    r: Math.random() * 8 + 6, rot: Math.random() * Math.PI * 2, vr: (Math.random() - 0.5) * 0.02, alive: true,
    update(dt) { this.y += this.vy * dt / 16; this.x += this.vx * dt / 16; this.rot += this.vr * dt / 16; if (this.y > h) { this.y = -20; this.x = Math.random() * w; } },
    draw(ctx) { ctx.save(); ctx.translate(this.x, this.y); ctx.rotate(this.rot); ctx.fillStyle = '#8b6f47'; ctx.beginPath(); ctx.ellipse(0, 0, this.r, this.r * 0.6, 0, 0, Math.PI * 2); ctx.fill(); ctx.restore(); },
  }));
}

function createPetals(canvas) {
  const w = canvas.width / (devicePixelRatio || 1), h = canvas.height / (devicePixelRatio || 1);
  return Array.from({ length: 50 }, () => ({
    x: Math.random() * w, y: Math.random() * h,
    vx: (Math.random() - 0.5) * 0.8, vy: Math.random() * 0.8 + 0.2,
    r: Math.random() * 6 + 4, rot: Math.random() * Math.PI * 2, vr: (Math.random() - 0.5) * 0.03, alive: true,
    update(dt) { this.y += this.vy * dt / 16; this.x += this.vx * dt / 16; this.rot += this.vr * dt / 16; if (this.y > h) { this.y = -15; this.x = Math.random() * w; } },
    draw(ctx) { ctx.save(); ctx.translate(this.x, this.y); ctx.rotate(this.rot); ctx.fillStyle = 'rgba(255,182,193,0.8)'; ctx.beginPath(); ctx.ellipse(0, 0, this.r, this.r * 0.5, 0, 0, Math.PI * 2); ctx.fill(); ctx.restore(); },
  }));
}

function createSparkles(canvas) {
  const w = canvas.width / (devicePixelRatio || 1), h = canvas.height / (devicePixelRatio || 1);
  return Array.from({ length: 60 }, () => ({
    x: Math.random() * w, y: Math.random() * h,
    vx: (Math.random() - 0.5) * 0.3, vy: (Math.random() - 0.5) * 0.3,
    r: Math.random() * 2 + 1, life: Math.random() * 2000, maxLife: 2000, alive: true,
    update(dt) { this.life -= dt; this.x += this.vx * dt / 16; this.y += this.vy * dt / 16; if (this.life <= 0) { this.x = Math.random() * w; this.y = Math.random() * h; this.life = this.maxLife; } },
    draw(ctx) { const a = this.life / this.maxLife; ctx.fillStyle = `rgba(255,255,255,${a * 0.8})`; ctx.beginPath(); ctx.arc(this.x, this.y, this.r, 0, Math.PI * 2); ctx.fill(); },
  }));
}

function getAutoEffect() {
  const m = new Date().getMonth();
  if (m >= 11 || m <= 1) return 'snow';
  if (m >= 2 && m <= 4) return 'petals';
  if (m >= 5 && m <= 7) return 'sparkles';
  return 'leaves';
}

export function initTheme() {
  const saved = localStorage.getItem('wg-theme');
  if (saved) applyTheme(JSON.parse(saved));
}

export function applyTheme(settings) {
  if (!settings) return;
  const { palette = 'white', effect = 'none' } = settings;
  const p = PALETTES[palette] || PALETTES.white;
  const root = document.documentElement;
  root.style.setProperty('--a1', p.a1);
  root.style.setProperty('--a2', p.a2);
  root.style.setProperty('--a3', p.a3);
  root.style.setProperty('--grad', p.grad);

  let eff = effect;
  if (effect === 'auto') eff = getAutoEffect();
  if (eff !== currentEffect) {
    currentEffect = eff;
    runEffect(EFFECTS[eff]?.render || null);
  }
}

window.addEventListener('resize', resizeCanvas);

const media = window.matchMedia('(prefers-reduced-motion: reduce)');
media.addEventListener('change', () => {
  if (media.matches) stopEffect();
  else if (currentEffect !== 'none') runEffect(EFFECTS[currentEffect]?.render || null);
});

export { PALETTES, EFFECTS };