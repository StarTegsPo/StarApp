// Змейка на canvas: свайпы, стрелки и экранный джойстик; скорость растёт с каждым яблоком.
const COLS = 17;

export function mount(h) {
  const { stage } = h;
  stage.innerHTML = `
    <div class="snake-wrap">
      <canvas class="snake-cv" id="cv"></canvas>
      <div class="overlay" id="ov"><div class="ov-icon">🐍</div><b>Свайпните, чтобы начать</b><span>или нажмите на стрелку</span></div>
    </div>
    <div class="dpad">
      <button class="icon-btn" data-d="up" aria-label="Вверх"><svg viewBox="0 0 24 24"><path d="M6 15l6-6 6 6"/></svg></button>
      <button class="icon-btn" data-d="left" aria-label="Влево"><svg viewBox="0 0 24 24"><path d="M15 18l-6-6 6-6"/></svg></button>
      <button class="icon-btn" data-d="down" aria-label="Вниз"><svg viewBox="0 0 24 24"><path d="M6 9l6 6 6-6"/></svg></button>
      <button class="icon-btn" data-d="right" aria-label="Вправо"><svg viewBox="0 0 24 24"><path d="M9 18l6-6-6-6"/></svg></button>
    </div>`;
  const cv = stage.querySelector('#cv');
  const ov = stage.querySelector('#ov');
  const ctx = cv.getContext('2d');
  const [c1, c2] = h.game.colors;
  const DIRS = { up: [0, -1], down: [0, 1], left: [-1, 0], right: [1, 0] };

  let snake, dir, queue, food, score, speed, timer, running, dead, px, eaten, t0;

  function resize() {
    const w = cv.clientWidth;
    const dpr = devicePixelRatio || 1;
    cv.width = w * dpr; cv.height = w * dpr;
    ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
    px = w / COLS;
    draw(0);
  }

  function reset() {
    const m = Math.floor(COLS / 2);
    snake = [[m, m], [m - 1, m], [m - 2, m]];
    dir = 'right'; queue = []; score = 0; speed = 150; running = false; dead = false; eaten = 0;
    placeFood();
    h.setScore('0', 'Счёт');
    ov.hidden = false;
    ov.classList.remove('dead');
  }

  function placeFood() {
    const free = [];
    for (let x = 0; x < COLS; x++) for (let y = 0; y < COLS; y++) if (!snake.some(([a, b]) => a === x && b === y)) free.push([x, y]);
    food = free[Math.floor(Math.random() * free.length)];
    food.born = performance.now();
  }

  function turn(d) {
    const last = queue.length ? queue[queue.length - 1] : dir;
    const [lx, ly] = DIRS[last], [nx, ny] = DIRS[d];
    // разворот на 180° запрещён
    if (!(lx + nx === 0 && ly + ny === 0) && d !== last && queue.length < 3) queue.push(d);
    if (!running && !dead) begin();
  }

  function begin() {
    running = true;
    ov.hidden = true;
    t0 = performance.now();
    tick();
  }

  function tick() {
    if (!running) return;
    if (queue.length) dir = queue.shift();
    const [dx, dy] = DIRS[dir];
    const head = [snake[0][0] + dx, snake[0][1] + dy];
    const hitWall = head[0] < 0 || head[1] < 0 || head[0] >= COLS || head[1] >= COLS;
    const willEat = head[0] === food[0] && head[1] === food[1];
    const body = willEat ? snake : snake.slice(0, -1);
    if (hitWall || body.some(([a, b]) => a === head[0] && b === head[1])) return die();
    snake.unshift(head);
    if (willEat) {
      eaten++;
      score += 10 + Math.floor(eaten / 5) * 5;
      speed = Math.max(65, speed - 4);
      h.setScore(score);
      h.tap('medium');
      placeFood();
    } else snake.pop();
    timer = setTimeout(tick, speed);
  }

  function die() {
    running = false; dead = true;
    h.haptic('error');
    cv.classList.remove('shake'); void cv.offsetWidth; cv.classList.add('shake');
    const sec = Math.round((performance.now() - t0) / 1000);
    setTimeout(() => h.over({ score, title: 'Змейка разбилась', emoji: '🐍', text: `Длина: ${snake.length} · ${sec} с` }), 600);
  }

  function roundRect(x, y, w, hh, r) {
    ctx.beginPath();
    ctx.moveTo(x + r, y);
    ctx.arcTo(x + w, y, x + w, y + hh, r);
    ctx.arcTo(x + w, y + hh, x, y + hh, r);
    ctx.arcTo(x, y + hh, x, y, r);
    ctx.arcTo(x, y, x + w, y, r);
    ctx.fill();
  }

  function draw(t) {
    const W = px * COLS;
    ctx.clearRect(0, 0, W, W);
    // сетка «шахматкой»
    for (let x = 0; x < COLS; x++) for (let y = 0; y < COLS; y++) {
      ctx.fillStyle = (x + y) % 2 ? 'rgba(255,255,255,0.035)' : 'rgba(255,255,255,0.015)';
      ctx.fillRect(x * px, y * px, px, px);
    }
    // еда с пульсирующим свечением
    if (food) {
      const pulse = 1 + Math.sin(t / 180) * 0.08;
      const born = Math.min(1, (t - (food.born || 0)) / 250);
      const r = px * 0.36 * pulse * born;
      const fx = food[0] * px + px / 2, fy = food[1] * px + px / 2;
      ctx.save();
      ctx.shadowColor = '#f43f5e'; ctx.shadowBlur = 18;
      ctx.fillStyle = '#fb7185';
      ctx.beginPath(); ctx.arc(fx, fy, Math.max(0, r), 0, Math.PI * 2); ctx.fill();
      ctx.restore();
      ctx.fillStyle = 'rgba(255,255,255,.7)';
      ctx.beginPath(); ctx.arc(fx - r * 0.35, fy - r * 0.35, Math.max(0, r * 0.25), 0, Math.PI * 2); ctx.fill();
    }
    // змейка: градиент от головы к хвосту
    const n = snake.length;
    snake.forEach(([x, y], i) => {
      const k = i / Math.max(1, n - 1);
      ctx.fillStyle = mix(c1, c2, k);
      const pad = px * (0.08 + k * 0.08);
      if (i === 0) { ctx.save(); ctx.shadowColor = c1; ctx.shadowBlur = 14; }
      roundRect(x * px + pad, y * px + pad, px - pad * 2, px - pad * 2, px * 0.3);
      if (i === 0) ctx.restore();
    });
    // глаза
    const [hx, hy] = snake[0];
    const [dx, dy] = DIRS[dir];
    ctx.fillStyle = '#fff';
    for (const s of [-1, 1]) {
      const ex = hx * px + px / 2 + dx * px * 0.18 + dy * s * px * 0.18;
      const ey = hy * px + px / 2 + dy * px * 0.18 + dx * s * px * 0.18;
      ctx.beginPath(); ctx.arc(ex, ey, px * 0.11, 0, Math.PI * 2); ctx.fill();
    }
  }

  let raf;
  const loop = (t) => { draw(t); raf = requestAnimationFrame(loop); };

  // ---- управление ----
  const keys = { ArrowLeft: 'left', ArrowRight: 'right', ArrowUp: 'up', ArrowDown: 'down', a: 'left', d: 'right', w: 'up', s: 'down' };
  const onKey = (e) => { const d = keys[e.key]; if (d) { e.preventDefault(); turn(d); } };
  let sx, sy, tracking = false;
  const wrap = stage.querySelector('.snake-wrap');
  wrap.addEventListener('pointerdown', (e) => { tracking = true; sx = e.clientX; sy = e.clientY; });
  wrap.addEventListener('pointermove', (e) => {
    if (!tracking) return;
    const dx = e.clientX - sx, dy = e.clientY - sy;
    if (Math.max(Math.abs(dx), Math.abs(dy)) < 18) return;
    turn(Math.abs(dx) > Math.abs(dy) ? (dx > 0 ? 'right' : 'left') : dy > 0 ? 'down' : 'up');
    sx = e.clientX; sy = e.clientY;
  });
  const stop = () => (tracking = false);
  window.addEventListener('pointerup', stop);
  window.addEventListener('keydown', onKey);
  stage.querySelectorAll('[data-d]').forEach((b) => b.addEventListener('pointerdown', (e) => { e.preventDefault(); h.tap('soft'); turn(b.dataset.d); }));
  const pause = () => { if (document.hidden && running) { running = false; clearTimeout(timer); ov.hidden = false; ov.querySelector('b').textContent = 'Пауза — свайпните, чтобы продолжить'; } };
  document.addEventListener('visibilitychange', pause);
  const ro = new ResizeObserver(resize);

  reset();
  ro.observe(cv);
  raf = requestAnimationFrame(loop);

  return {
    destroy() {
      running = false;
      clearTimeout(timer);
      cancelAnimationFrame(raf);
      ro.disconnect();
      window.removeEventListener('pointerup', stop);
      window.removeEventListener('keydown', onKey);
      document.removeEventListener('visibilitychange', pause);
    },
  };
}

function mix(a, b, k) {
  const pa = parseInt(a.slice(1), 16), pb = parseInt(b.slice(1), 16);
  const ch = (s) => Math.round(((pa >> s) & 255) * (1 - k) + ((pb >> s) & 255) * k);
  return `rgb(${ch(16)},${ch(8)},${ch(0)})`;
}
