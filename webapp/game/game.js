// «Тоннели Роя» — оригинальная хоррор-игра про выживание в тёмных тоннелях.
// Чистый JS + Canvas, без зависимостей. Работает в браузере, в Telegram Mini App
// и как устанавливаемое приложение (PWA, см. manifest.json и sw.js).

const T = 40; // размер клетки в мировых пикселях
const $ = (id) => document.getElementById(id);
const canvas = $('c');
const ctx = canvas.getContext('2d');
const fog = document.createElement('canvas');
const fctx = fog.getContext('2d');

const store = {
  get(k, d) { try { const v = localStorage.getItem(k); return v == null ? d : JSON.parse(v); } catch { return d; } },
  set(k, v) { try { localStorage.setItem(k, JSON.stringify(v)); } catch {} },
};

// ---------- экран ----------
let W = 0, H = 0, DPR = 1, scale = 1;
function resize() {
  DPR = Math.min(window.devicePixelRatio || 1, 2);
  W = window.innerWidth; H = window.innerHeight;
  canvas.width = fog.width = Math.round(W * DPR);
  canvas.height = fog.height = Math.round(H * DPR);
  scale = Math.min(W, H) / (T * 8.5);
}
window.addEventListener('resize', resize);
resize();

// ---------- утилиты ----------
const rnd = (a, b) => a + Math.random() * (b - a);
const ri = (n) => Math.floor(Math.random() * n);
const clamp = (v, a, b) => Math.max(a, Math.min(b, v));
const shuffle = (a) => { for (let i = a.length - 1; i > 0; i--) { const j = ri(i + 1); [a[i], a[j]] = [a[j], a[i]]; } return a; };
const DIRS = [[1, 0], [-1, 0], [0, 1], [0, -1]];

// ---------- генерация лабиринта ----------
function genMaze(cw, ch) {
  const GW = cw * 2 + 1, GH = ch * 2 + 1;
  const g = new Uint8Array(GW * GH).fill(1);
  const seen = new Uint8Array(cw * ch);
  const stack = [[0, 0]];
  seen[0] = 1; g[GW + 1] = 0;
  while (stack.length) {
    const [cx, cy] = stack[stack.length - 1];
    const opts = shuffle(DIRS.slice()).filter(([dx, dy]) => {
      const nx = cx + dx, ny = cy + dy;
      return nx >= 0 && ny >= 0 && nx < cw && ny < ch && !seen[ny * cw + nx];
    });
    if (!opts.length) { stack.pop(); continue; }
    const [dx, dy] = opts[0];
    const nx = cx + dx, ny = cy + dy;
    seen[ny * cw + nx] = 1;
    g[(cy * 2 + 1 + dy) * GW + cx * 2 + 1 + dx] = 0;
    g[(ny * 2 + 1) * GW + nx * 2 + 1] = 0;
    stack.push([nx, ny]);
  }
  // петли — чтобы от роя можно было уйти обходным путём
  let loops = Math.floor(cw * ch * 0.14);
  for (let k = 0; k < 5000 && loops > 0; k++) {
    const x = 1 + ri(GW - 2), y = 1 + ri(GH - 2);
    if (!g[y * GW + x]) continue;
    const horiz = !g[y * GW + x - 1] && !g[y * GW + x + 1] && g[(y - 1) * GW + x] && g[(y + 1) * GW + x];
    const vert = !g[(y - 1) * GW + x] && !g[(y + 1) * GW + x] && g[y * GW + x - 1] && g[y * GW + x + 1];
    if (horiz || vert) { g[y * GW + x] = 0; loops--; }
  }
  // несколько залов 3×3
  const rooms = 1 + Math.floor(cw * ch / 50);
  for (let r = 0; r < rooms; r++) {
    const x = 2 + ri(GW - 4), y = 2 + ri(GH - 4);
    for (let j = -1; j <= 1; j++) for (let i = -1; i <= 1; i++) g[(y + j) * GW + x + i] = 0;
  }
  return { g, GW, GH };
}

function bfs(map, sx, sy) {
  const { g, GW, GH } = map;
  const d = new Int16Array(GW * GH).fill(-1);
  const q = new Int32Array(GW * GH);
  let h = 0, t = 0;
  d[sy * GW + sx] = 0; q[t++] = sy * GW + sx;
  while (h < t) {
    const c = q[h++], x = c % GW, y = (c / GW) | 0;
    for (const [dx, dy] of DIRS) {
      const nx = x + dx, ny = y + dy;
      if (nx < 0 || ny < 0 || nx >= GW || ny >= GH) continue;
      const n = ny * GW + nx;
      if (g[n] || d[n] >= 0) continue;
      d[n] = d[c] + 1; q[t++] = n;
    }
  }
  return d;
}

// ---------- звук (WebAudio, без файлов) ----------
const audio = {
  ctx: null, buzzGain: null,
  init() {
    if (this.ctx) return;
    try {
      const a = this.ctx = new (window.AudioContext || window.webkitAudioContext)();
      const filter = a.createBiquadFilter(); filter.type = 'lowpass'; filter.frequency.value = 700;
      this.buzzGain = a.createGain(); this.buzzGain.gain.value = 0;
      for (const f of [118, 121.5, 236]) {
        const o = a.createOscillator(); o.type = 'sawtooth'; o.frequency.value = f;
        const lfo = a.createOscillator(); lfo.frequency.value = rnd(5, 9);
        const lg = a.createGain(); lg.gain.value = 6; lfo.connect(lg).connect(o.frequency);
        o.connect(filter); o.start(); lfo.start();
      }
      filter.connect(this.buzzGain).connect(a.destination);
    } catch { this.ctx = null; }
  },
  buzz(v) { if (this.buzzGain) this.buzzGain.gain.setTargetAtTime(v, this.ctx.currentTime, 0.1); },
  beep(freq, dur = 0.12, type = 'sine', vol = 0.12) {
    if (!this.ctx) return;
    const a = this.ctx, o = a.createOscillator(), gn = a.createGain();
    o.type = type; o.frequency.value = freq;
    gn.gain.setValueAtTime(vol, a.currentTime);
    gn.gain.exponentialRampToValueAtTime(0.001, a.currentTime + dur);
    o.connect(gn).connect(a.destination); o.start(); o.stop(a.currentTime + dur);
  },
};
const vibrate = (ms) => { try { navigator.vibrate?.(ms); } catch {} };

// ---------- состояние игры ----------
let S = null; // текущий забег
let running = false, paused = false;

function newRun() {
  S = { level: 0, score: 0, hp: 100, flares: 1, totalCells: 0 };
  startLevel();
}

function startLevel() {
  S.level++;
  const size = Math.min(7 + S.level * 2, 21);
  const map = genMaze(size, size);
  const { g, GW } = map;
  const floor = [];
  for (let i = 0; i < g.length; i++) if (!g[i]) floor.push(i);
  const start = GW + 1;
  const dStart = bfs(map, 1, 1);
  const byDist = floor.slice().sort((a, b) => dStart[b] - dStart[a]);
  const taken = new Set([start]);
  const take = (pred) => {
    const pool = shuffle(floor.filter((i) => !taken.has(i) && pred(i)));
    const i = pool[0] ?? floor.find((k) => !taken.has(k));
    taken.add(i);
    return i;
  };
  const exit = byDist[0]; taken.add(exit);
  const maxD = dStart[exit];
  const items = [];
  const cellsNeeded = 2 + S.level;
  for (let k = 0; k < cellsNeeded; k++) items.push({ kind: 'cell', i: take((i) => dStart[i] > maxD * 0.25) });
  for (let k = 0; k < 1 + (S.level > 2); k++) items.push({ kind: 'med', i: take((i) => dStart[i] > 6) });
  for (let k = 0; k < 1 + (S.level > 3); k++) items.push({ kind: 'flare', i: take((i) => dStart[i] > 6) });
  for (const it of items) { it.x = (it.i % GW + 0.5) * T; it.y = (((it.i / GW) | 0) + 0.5) * T; it.ph = Math.random() * 6; }

  const swarms = [];
  const swarmCount = 1 + Math.ceil(S.level * 0.8);
  for (let k = 0; k < swarmCount; k++) {
    const i = take((i) => dStart[i] > Math.max(10, maxD * 0.4));
    const tx = i % GW, ty = (i / GW) | 0;
    swarms.push({
      tx, ty, nx: tx, ny: ty, x: (tx + 0.5) * T, y: (ty + 0.5) * T, dir: ri(4), hunt: false, cool: 0,
      parts: Array.from({ length: 26 }, () => ({ a: Math.random() * 6.28, r: rnd(3, 17), s: rnd(2, 6), o: Math.random() * 6 })),
    });
  }

  Object.assign(S, {
    map, items, swarms, exit, cellsNeeded, cells: 0,
    exitX: (exit % GW + 0.5) * T, exitY: (((exit / GW) | 0) + 0.5) * T,
    px: 1.5 * T, py: 1.5 * T, face: 0, stam: 100, tired: false,
    flare: null, flow: null, flowTile: -1, grace: 2.5, hurt: 0, time: 0, msgT: 0,
  });
  showMsg(`Уровень ${S.level} · найди ${cellsNeeded} ⚡`, 2.5);
  updateHud();
}

// ---------- ввод ----------
const keys = new Set();
const input = { mx: 0, my: 0, run: false };
window.addEventListener('keydown', (e) => {
  keys.add(e.code);
  if (running && (e.code === 'Space')) { e.preventDefault(); useFlare(); }
  if (running && (e.code === 'Escape' || e.code === 'KeyP')) togglePause();
});
window.addEventListener('keyup', (e) => keys.delete(e.code));
window.addEventListener('blur', () => { keys.clear(); if (running && !paused) togglePause(); });
document.addEventListener('visibilitychange', () => { if (document.hidden && running && !paused) togglePause(); });

const isTouch = matchMedia('(pointer: coarse)').matches || 'ontouchstart' in window;
const stick = $('stick'), knob = $('knob');
let stickId = null;
function stickMove(e) {
  const r = stick.getBoundingClientRect();
  let dx = e.clientX - (r.left + r.width / 2), dy = e.clientY - (r.top + r.height / 2);
  const max = r.width / 2, len = Math.hypot(dx, dy);
  if (len > max) { dx *= max / len; dy *= max / len; }
  knob.style.transform = `translate(${dx}px, ${dy}px)`;
  input.mx = dx / max; input.my = dy / max;
}
stick.addEventListener('pointerdown', (e) => { stickId = e.pointerId; stick.setPointerCapture(e.pointerId); stickMove(e); });
stick.addEventListener('pointermove', (e) => { if (e.pointerId === stickId) stickMove(e); });
const stickEnd = (e) => { if (e.pointerId !== stickId) return; stickId = null; input.mx = input.my = 0; knob.style.transform = ''; };
stick.addEventListener('pointerup', stickEnd);
stick.addEventListener('pointercancel', stickEnd);
const btnRun = $('btnRun');
btnRun.addEventListener('pointerdown', (e) => { e.preventDefault(); input.run = !input.run; btnRun.classList.toggle('on', input.run); });
$('btnFlare').addEventListener('pointerdown', (e) => { e.preventDefault(); useFlare(); });

function useFlare() {
  if (!S || paused) return;
  if (S.flare) return showMsg('Факел уже горит', 1);
  if (S.flares <= 0) return showMsg('Нет факелов', 1);
  S.flares--;
  S.flare = { x: S.px, y: S.py, t: 7 };
  audio.beep(220, 0.4, 'triangle', 0.15);
  vibrate(30);
  updateHud();
}

// ---------- физика ----------
function solidAt(x, y) {
  const { g, GW, GH } = S.map;
  const tx = Math.floor(x / T), ty = Math.floor(y / T);
  if (tx < 0 || ty < 0 || tx >= GW || ty >= GH) return true;
  return g[ty * GW + tx] === 1;
}
function blocked(x, y, r) {
  return solidAt(x - r, y - r) || solidAt(x + r, y - r) || solidAt(x - r, y + r) || solidAt(x + r, y + r);
}
const isFloor = (tx, ty) => tx >= 0 && ty >= 0 && tx < S.map.GW && ty < S.map.GH && !S.map.g[ty * S.map.GW + tx];

function update(dt) {
  S.time += dt;
  if (S.grace > 0) S.grace -= dt;
  if (S.msgT > 0 && (S.msgT -= dt) <= 0) $('msg').hidden = true;

  // --- игрок
  let mx = input.mx, my = input.my;
  if (keys.has('KeyW') || keys.has('ArrowUp')) my -= 1;
  if (keys.has('KeyS') || keys.has('ArrowDown')) my += 1;
  if (keys.has('KeyA') || keys.has('ArrowLeft')) mx -= 1;
  if (keys.has('KeyD') || keys.has('ArrowRight')) mx += 1;
  const len = Math.hypot(mx, my);
  if (len > 1) { mx /= len; my /= len; }
  const moving = len > 0.1;
  const wantRun = (keys.has('ShiftLeft') || keys.has('ShiftRight') || input.run) && moving;
  if (S.stam < 3) S.tired = true;
  if (S.tired && S.stam > 30) S.tired = false;
  const runningNow = wantRun && !S.tired;
  S.stam = clamp(S.stam + (runningNow ? -32 : 16) * dt, 0, 100);
  const slow = S.hurt > 0 ? 0.8 : 1;
  const speed = (runningNow ? 165 : 105) * slow;
  if (moving) {
    S.face = Math.atan2(my, mx);
    const r = 11, nx = S.px + mx * speed * dt, ny = S.py + my * speed * dt;
    if (!blocked(nx, S.py, r)) S.px = nx;
    if (!blocked(S.px, ny, r)) S.py = ny;
  }
  const { GW } = S.map;
  const ptx = Math.floor(S.px / T), pty = Math.floor(S.py / T), pt = pty * GW + ptx;
  if (pt !== S.flowTile) { S.flow = bfs(S.map, ptx, pty); S.flowTile = pt; }

  // --- предметы
  for (let k = S.items.length - 1; k >= 0; k--) {
    const it = S.items[k];
    if (Math.hypot(it.x - S.px, it.y - S.py) > 20) continue;
    S.items.splice(k, 1);
    if (it.kind === 'cell') {
      S.cells++; S.totalCells++; S.score += 100;
      audio.beep(880, 0.1); setTimeout(() => audio.beep(1320, 0.12), 90);
      if (S.cells >= S.cellsNeeded) { showMsg('🚪 Шлюз открыт! Беги к выходу', 2.5); audio.beep(660, 0.5, 'square', 0.08); }
      else showMsg(`⚡ ${S.cells}/${S.cellsNeeded}`, 1);
    } else if (it.kind === 'med') {
      S.hp = Math.min(100, S.hp + 40); showMsg('➕ +40 здоровья', 1.2); audio.beep(520, 0.2);
    } else {
      S.flares++; showMsg('🔥 +1 факел', 1.2); audio.beep(440, 0.2);
    }
    vibrate(20);
    updateHud();
  }

  // --- выход
  if (S.cells >= S.cellsNeeded && Math.hypot(S.exitX - S.px, S.exitY - S.py) < 22) {
    S.score += 1000 + Math.round(S.hp * 5);
    S.hp = Math.min(100, S.hp + 25);
    audio.beep(523, 0.15); setTimeout(() => audio.beep(659, 0.15), 140); setTimeout(() => audio.beep(784, 0.3), 280);
    startLevel();
    return;
  }

  // --- факел
  if (S.flare && (S.flare.t -= dt) <= 0) S.flare = null;

  // --- рой
  const hear = runningNow ? 13 : 7;
  const hunt = Math.min(70 + S.level * 9, 150), wander = 48;
  let nearest = Infinity;
  for (const sw of S.swarms) {
    const d = S.flow[sw.ty * GW + sw.tx];
    const eu = Math.hypot(sw.x - S.px, sw.y - S.py);
    nearest = Math.min(nearest, eu);
    if (d >= 0 && (d <= hear || eu < T * 3)) sw.hunt = true;
    else if (d < 0 || d > hear + 7) sw.hunt = false;
    const fleeing = S.flare && Math.hypot(sw.x - S.flare.x, sw.y - S.flare.y) < T * 5.5;
    if (S.grace > 0) continue;

    const v = fleeing ? 120 : sw.hunt ? hunt : wander;
    const tx = (sw.nx + 0.5) * T, ty = (sw.ny + 0.5) * T;
    const dx = tx - sw.x, dy = ty - sw.y, dist = Math.hypot(dx, dy);
    const step = v * dt;
    // в прямом контакте рой летит прямо на игрока
    if (sw.hunt && !fleeing && eu < T * 0.9) {
      sw.x += (S.px - sw.x) / eu * Math.min(step, eu);
      sw.y += (S.py - sw.y) / eu * Math.min(step, eu);
    } else if (dist > step) {
      sw.x += dx / dist * step; sw.y += dy / dist * step;
    } else {
      sw.x = tx; sw.y = ty; sw.tx = sw.nx; sw.ty = sw.ny;
      const opts = DIRS.map((dd, i) => ({ i, x: sw.tx + dd[0], y: sw.ty + dd[1] })).filter((o) => isFloor(o.x, o.y));
      let pick;
      if (fleeing) {
        pick = opts.reduce((a, b) => (Math.hypot((b.x + .5) * T - S.flare.x, (b.y + .5) * T - S.flare.y) > Math.hypot((a.x + .5) * T - S.flare.x, (a.y + .5) * T - S.flare.y) ? b : a));
      } else if (sw.hunt) {
        pick = opts.reduce((a, b) => (S.flow[b.y * GW + b.x] < S.flow[a.y * GW + a.x] ? b : a));
      } else {
        const back = sw.dir ^ 1; // DIRS попарно противоположны
        const fwd = opts.filter((o) => o.i !== back);
        pick = (fwd.length ? fwd : opts)[ri(fwd.length || opts.length)];
      }
      if (pick) { sw.nx = pick.x; sw.ny = pick.y; sw.dir = pick.i; }
    }
    if (eu < 22 && !fleeing) {
      S.hp -= 34 * dt; S.hurt = 0.35;
      if (Math.random() < dt * 6) vibrate(15);
    }
  }
  if (S.hurt > 0) S.hurt -= dt;
  audio.buzz(clamp(1 - nearest / (T * 8), 0, 1) ** 2 * 0.13);
  if (S.hp <= 0) return gameOver();
  setBar();
}

// ---------- свет и видимость ----------
const RAYS = 160;
function castRays(ox, oy, R) {
  const out = new Float32Array(RAYS);
  for (let k = 0; k < RAYS; k++) {
    const a = k / RAYS * Math.PI * 2, c = Math.cos(a), s = Math.sin(a);
    let d = 0;
    while (d < R) { d += 5; if (solidAt(ox + c * d, oy + s * d)) { d += 6; break; } }
    out[k] = Math.min(d, R);
  }
  return out;
}
function lightPoly(c, ox, oy, rays, R, from = 0, to = Math.PI * 2, inner = 0, color = null) {
  c.beginPath();
  const full = to - from >= Math.PI * 2 - 1e-3;
  if (!full) c.moveTo(ox, oy);
  const steps = Math.ceil((to - from) / (Math.PI * 2) * RAYS);
  for (let k = 0; k <= steps; k++) {
    const a = from + (to - from) * k / steps;
    const idx = ((Math.round(a / (Math.PI * 2) * RAYS) % RAYS) + RAYS) % RAYS;
    const d = Math.min(rays[idx], R);
    c.lineTo(ox + Math.cos(a) * d, oy + Math.sin(a) * d);
  }
  c.closePath();
  const gr = c.createRadialGradient(ox, oy, inner, ox, oy, R);
  gr.addColorStop(0, color || 'rgba(0,0,0,1)');
  gr.addColorStop(0.55, color ? color.replace(/[\d.]+\)$/, '0.5)') : 'rgba(0,0,0,.75)');
  gr.addColorStop(1, 'rgba(0,0,0,0)');
  c.fillStyle = gr;
  c.fill();
}

// ---------- отрисовка ----------
function worldTransform(c) {
  c.setTransform(DPR * scale, 0, 0, DPR * scale, DPR * (W / 2 - S.px * scale), DPR * (H / 2 - S.py * scale));
}

function draw() {
  const { g, GW, GH } = S.map;
  ctx.setTransform(1, 0, 0, 1, 0, 0);
  ctx.fillStyle = '#07060c';
  ctx.fillRect(0, 0, canvas.width, canvas.height);
  worldTransform(ctx);

  const vw = W / scale / 2 + T, vh = H / scale / 2 + T;
  const x0 = Math.max(0, Math.floor((S.px - vw) / T)), x1 = Math.min(GW - 1, Math.ceil((S.px + vw) / T));
  const y0 = Math.max(0, Math.floor((S.py - vh) / T)), y1 = Math.min(GH - 1, Math.ceil((S.py + vh) / T));

  // пол
  for (let y = y0; y <= y1; y++) for (let x = x0; x <= x1; x++) {
    if (g[y * GW + x]) continue;
    ctx.fillStyle = (x + y) & 1 ? '#2a2433' : '#262030';
    ctx.fillRect(x * T, y * T, T, T);
    ctx.fillStyle = '#ffffff08';
    ctx.fillRect(x * T + 2, y * T + 2, T - 4, 2);
  }
  // стены
  for (let y = y0; y <= y1; y++) for (let x = x0; x <= x1; x++) {
    if (!g[y * GW + x]) continue;
    ctx.fillStyle = '#4b4058';
    ctx.fillRect(x * T, y * T, T, T);
    ctx.fillStyle = '#3a3146';
    ctx.fillRect(x * T, y * T + T - 8, T, 8);
    if ((x * 7 + y * 13) % 5 === 0) { ctx.fillStyle = '#5a4d6a'; ctx.fillRect(x * T + 6, y * T + 8, 10, 3); }
  }

  // выход
  const open = S.cells >= S.cellsNeeded;
  ctx.fillStyle = open ? '#16a34a' : '#7f1d1d';
  ctx.fillRect(S.exitX - 15, S.exitY - 15, 30, 30);
  ctx.fillStyle = open ? '#86efac' : '#f87171';
  ctx.font = '18px sans-serif'; ctx.textAlign = 'center'; ctx.textBaseline = 'middle';
  ctx.fillText(open ? '🚪' : '🔒', S.exitX, S.exitY + 1);

  // предметы
  for (const it of S.items) {
    const bob = Math.sin(S.time * 3 + it.ph) * 2.5;
    ctx.fillText(it.kind === 'cell' ? '⚡' : it.kind === 'med' ? '➕' : '🔥', it.x, it.y + bob);
  }

  // факел на полу
  if (S.flare) {
    ctx.fillStyle = '#fb923c';
    ctx.beginPath(); ctx.arc(S.flare.x, S.flare.y, 4 + Math.random() * 2, 0, 7); ctx.fill();
  }

  // рой
  for (const sw of S.swarms) drawSwarm(sw);

  // игрок
  ctx.save();
  ctx.translate(S.px, S.py);
  ctx.rotate(S.face);
  ctx.fillStyle = '#facc15';
  ctx.beginPath(); ctx.moveTo(14, 0); ctx.lineTo(8, -4); ctx.lineTo(8, 4); ctx.fill();
  ctx.fillStyle = S.hurt > 0 ? '#fca5a5' : '#e0d7ff';
  ctx.beginPath(); ctx.arc(0, 0, 10, 0, 7); ctx.fill();
  ctx.fillStyle = '#6d28d9';
  ctx.beginPath(); ctx.arc(-2, 0, 6, 0, 7); ctx.fill();
  ctx.restore();

  // темнота
  fctx.globalCompositeOperation = 'source-over';
  fctx.setTransform(1, 0, 0, 1, 0, 0);
  fctx.fillStyle = 'rgba(3,2,7,0.975)';
  fctx.fillRect(0, 0, fog.width, fog.height);
  worldTransform(fctx);
  fctx.globalCompositeOperation = 'destination-out';
  const R = T * 7.5;
  const rays = castRays(S.px, S.py, R);
  const flick = 1 + Math.sin(S.time * 23) * 0.015 + (Math.random() - 0.5) * 0.02;
  lightPoly(fctx, S.px, S.py, rays, T * 2.6 * flick);
  lightPoly(fctx, S.px, S.py, rays, R * flick, S.face - 0.5, S.face + 0.5, T * 0.5);
  if (S.flare) {
    const fr = T * (4.5 + Math.random() * 0.3) * Math.min(1, S.flare.t);
    lightPoly(fctx, S.flare.x, S.flare.y, castRays(S.flare.x, S.flare.y, fr), fr);
  }
  ctx.setTransform(1, 0, 0, 1, 0, 0);
  ctx.drawImage(fog, 0, 0);

  // то, что видно и в темноте: глаза роя, свечение ячеек, открытый шлюз
  worldTransform(ctx);
  ctx.globalCompositeOperation = 'lighter';
  if (S.flare) glow(S.flare.x, S.flare.y, T * 4, 'rgba(251,146,60,0.18)');
  for (const it of S.items) if (it.kind === 'cell' && Math.hypot(it.x - S.px, it.y - S.py) < T * 9) glow(it.x, it.y, 16, 'rgba(56,189,248,0.35)');
  if (open) glow(S.exitX, S.exitY, 30, 'rgba(34,197,94,0.4)');
  ctx.globalCompositeOperation = 'source-over';
  for (const sw of S.swarms) {
    const d = Math.hypot(sw.x - S.px, sw.y - S.py);
    if (d > T * 10) continue;
    ctx.globalAlpha = clamp(1 - d / (T * 10), 0, 1) * (sw.hunt ? 1 : 0.55);
    ctx.fillStyle = '#ef4444';
    for (let k = 0; k < 5; k++) {
      const p = sw.parts[k * 4];
      const ex = sw.x + Math.cos(p.a + S.time * p.s * 0.3) * p.r * 0.8, ey = sw.y + Math.sin(p.a + S.time * p.s * 0.3) * p.r * 0.8;
      ctx.fillRect(ex - 2, ey, 1.6, 1.6); ctx.fillRect(ex + 1, ey, 1.6, 1.6);
    }
    ctx.globalAlpha = 1;
  }

  // экранные эффекты
  ctx.setTransform(DPR, 0, 0, DPR, 0, 0);
  if (open) drawExitArrow();
  if (S.hurt > 0 || S.hp < 30) {
    const a = S.hurt > 0 ? 0.35 : 0.12 + Math.sin(S.time * 5) * 0.06;
    const gr = ctx.createRadialGradient(W / 2, H / 2, Math.min(W, H) * 0.3, W / 2, H / 2, Math.max(W, H) * 0.7);
    gr.addColorStop(0, 'rgba(220,38,38,0)'); gr.addColorStop(1, `rgba(220,38,38,${a})`);
    ctx.fillStyle = gr; ctx.fillRect(0, 0, W, H);
  }
}

function glow(x, y, r, color) {
  const gr = ctx.createRadialGradient(x, y, 0, x, y, r);
  gr.addColorStop(0, color); gr.addColorStop(1, 'rgba(0,0,0,0)');
  ctx.fillStyle = gr; ctx.fillRect(x - r, y - r, r * 2, r * 2);
}

function drawSwarm(sw) {
  const t = S.time;
  for (const p of sw.parts) {
    const a = p.a + t * p.s * (sw.hunt ? 0.5 : 0.25);
    const r = p.r + Math.sin(t * 7 + p.o) * 3;
    const x = sw.x + Math.cos(a) * r + (Math.random() - 0.5) * 2;
    const y = sw.y + Math.sin(a * 1.3) * r * 0.8 + (Math.random() - 0.5) * 2;
    ctx.fillStyle = '#3f4d1c';
    ctx.beginPath(); ctx.ellipse(x, y, 3.2, 1.7, a, 0, 7); ctx.fill();
    ctx.fillStyle = '#a3b84a55';
    ctx.fillRect(x - 3, y - 2.5, 2, 1); ctx.fillRect(x + 1, y - 2.5, 2, 1);
  }
}

function drawExitArrow() {
  const dx = S.exitX - S.px, dy = S.exitY - S.py;
  const sx = dx * scale, sy = dy * scale;
  if (Math.abs(sx) < W / 2 - 30 && Math.abs(sy) < H / 2 - 30) return;
  const a = Math.atan2(dy, dx);
  const m = Math.min((W / 2 - 34) / Math.abs(Math.cos(a) || 1e-6), (H / 2 - 34) / Math.abs(Math.sin(a) || 1e-6));
  ctx.save();
  ctx.translate(W / 2 + Math.cos(a) * m, H / 2 + Math.sin(a) * m);
  ctx.rotate(a);
  ctx.fillStyle = `rgba(74,222,128,${0.6 + Math.sin(S.time * 6) * 0.3})`;
  ctx.beginPath(); ctx.moveTo(14, 0); ctx.lineTo(-8, -10); ctx.lineTo(-3, 0); ctx.lineTo(-8, 10); ctx.fill();
  ctx.restore();
}

// ---------- HUD и экраны ----------
function setBar() {
  $('hpbar').style.width = `${clamp(S.hp, 0, 100)}%`;
  $('stambar').style.width = `${S.stam}%`;
  $('stambar').style.background = S.tired ? '#64748b' : '#38bdf8';
}
function updateHud() {
  $('lvl').textContent = `Ур. ${S.level}`;
  $('cells').textContent = `⚡ ${S.cells}/${S.cellsNeeded}`;
  $('flares').textContent = `🔥 ${S.flares}`;
  setBar();
}
function showMsg(text, sec) {
  const m = $('msg'); m.textContent = text; m.hidden = false; S.msgT = sec;
}
function showScreen(id) {
  for (const s of ['menu', 'over', 'pause']) $(s).hidden = s !== id;
  const playing = id === null;
  $('hud').hidden = !playing;
  $('touch').hidden = !playing || !isTouch;
}
function renderBest() {
  const b = store.get('swarm.best', null);
  $('best').textContent = b ? `Рекорд: ${b.score} очков · уровень ${b.level}` : '';
}

function play() {
  audio.init();
  audio.ctx?.resume?.();
  newRun();
  showScreen(null);
  running = true; paused = false;
  last = performance.now();
}
function togglePause() {
  paused = !paused;
  showScreen(paused ? 'pause' : null);
  audio.buzz(0);
  last = performance.now();
}
function gameOver() {
  running = false;
  audio.buzz(0);
  audio.beep(110, 0.8, 'sawtooth', 0.15);
  vibrate([80, 60, 160]);
  const best = store.get('swarm.best', null);
  const record = !best || S.score > best.score;
  if (record) store.set('swarm.best', { score: S.score, level: S.level });
  $('overTitle').textContent = 'Рой настиг тебя';
  $('overText').innerHTML = `Уровень ${S.level} · ⚡ ${S.totalCells} ячеек · <b>${S.score}</b> очков${record ? '<br>🏆 Новый рекорд!' : ''}`;
  showScreen('over');
  $('msg').hidden = true;
}

$('play').onclick = play;
$('again').onclick = play;
$('resume').onclick = togglePause;
$('toMenu').onclick = () => { renderBest(); showScreen('menu'); };
$('quit').onclick = () => { running = false; paused = false; audio.buzz(0); renderBest(); showScreen('menu'); $('msg').hidden = true; };
$('back').onclick = () => { location.href = '/'; };

let last = performance.now();
function frame(now) {
  const dt = Math.min(0.05, (now - last) / 1000);
  last = now;
  if (running && !paused) {
    update(dt);
    if (running) draw();
  }
  requestAnimationFrame(frame);
}
renderBest();
showScreen('menu');
requestAnimationFrame(frame);

if ('serviceWorker' in navigator && location.protocol === 'https:') {
  navigator.serviceWorker.register('/game/sw.js', { scope: '/game/' }).catch(() => {});
}
