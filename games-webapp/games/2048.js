// 2048: свайпы / стрелки, плавное движение плиток, анимация слияния и появления.
const N = 4;
const GAP = 10;

export function mount(h) {
  const { stage } = h;
  stage.innerHTML = `
    <div class="b2048" id="b2048">
      ${'<div class="cell"></div>'.repeat(N * N)}
    </div>
    <p class="hint">Свайпайте по полю, чтобы сдвигать плитки</p>`;
  const board = stage.querySelector('#b2048');
  let grid, score, nextId, alive, won, cell;

  function layout() {
    const size = board.clientWidth;
    cell = (size - GAP * (N + 1)) / N;
    board.style.setProperty('--cell', `${cell}px`);
    board.querySelectorAll('.cell').forEach((c, i) => place(c, Math.floor(i / N), i % N));
    for (const t of grid) if (t) place(t.el, t.r, t.c);
  }
  const place = (el, r, c) => { el.style.transform = `translate(${GAP + c * (cell + GAP)}px, ${GAP + r * (cell + GAP)}px)`; };

  function addTile() {
    const empty = grid.map((t, i) => (t ? -1 : i)).filter((i) => i >= 0);
    if (!empty.length) return;
    const i = empty[Math.floor(Math.random() * empty.length)];
    const t = { id: nextId++, val: Math.random() < 0.9 ? 2 : 4, r: Math.floor(i / N), c: i % N };
    t.el = document.createElement('div');
    t.el.className = `tile v${t.val}`;
    t.el.innerHTML = `<span class="appear">${t.val}</span>`;
    place(t.el, t.r, t.c);
    board.appendChild(t.el);
    grid[i] = t;
  }

  const idx = (dir, line, k) => {
    const [r, c] = { left: [line, k], right: [line, N - 1 - k], up: [k, line], down: [N - 1 - k, line] }[dir];
    return r * N + c;
  };

  function move(dir) {
    if (!alive) return;
    let moved = false, gained = 0;
    const merges = [];
    for (let line = 0; line < N; line++) {
      const slots = Array.from({ length: N }, (_, k) => idx(dir, line, k));
      const items = slots.map((i) => grid[i]).filter(Boolean);
      const out = [];
      for (const t of items) {
        const last = out[out.length - 1];
        if (last && !last.merged && last.val === t.val) {
          last.merged = true;
          last.val *= 2;
          gained += last.val;
          merges.push({ from: t, into: last });
        } else out.push(t);
      }
      slots.forEach((i, k) => {
        const t = out[k] || null;
        if (grid[i] !== t) moved = true;
        grid[i] = t;
        if (t) { t.r = Math.floor(i / N); t.c = i % N; }
      });
    }
    if (!moved) { board.classList.remove('nudge'); void board.offsetWidth; board.classList.add('nudge'); return; }
    h.tap('light');
    for (const t of grid) if (t) place(t.el, t.r, t.c);
    for (const { from, into } of merges) {
      place(from.el, into.r, into.c);
      from.el.style.zIndex = 0;
      setTimeout(() => from.el.remove(), 130);
    }
    setTimeout(() => {
      for (const t of grid) {
        if (!t?.merged) continue;
        t.merged = false;
        t.el.className = `tile v${Math.min(t.val, 4096)}`;
        t.el.innerHTML = `<span class="merge">${t.val}</span>`;
        if (t.val === 2048 && !won) { won = true; h.confetti(); h.haptic('success'); h.toast('🎉 2048! Продолжайте — рекорд ждёт'); }
      }
      addTile();
      if (!canMove()) {
        alive = false;
        setTimeout(() => h.over({ score, title: 'Ходов больше нет', emoji: '🧩', text: `Максимальная плитка: ${Math.max(...grid.map((t) => t.val))}` }), 450);
      }
    }, 120);
    if (gained) { score += gained; h.setScore(score.toLocaleString('ru-RU')); }
  }

  function canMove() {
    for (let i = 0; i < N * N; i++) {
      const t = grid[i];
      if (!t) return true;
      if (i % N < N - 1 && grid[i + 1]?.val === t.val) return true;
      if (i + N < N * N && grid[i + N]?.val === t.val) return true;
    }
    return false;
  }

  function start() {
    board.querySelectorAll('.tile').forEach((t) => t.remove());
    grid = Array(N * N).fill(null);
    score = 0; nextId = 1; alive = true; won = false;
    h.setScore('0', 'Счёт');
    layout();
    addTile(); addTile();
  }

  // ---- управление ----
  const keys = { ArrowLeft: 'left', ArrowRight: 'right', ArrowUp: 'up', ArrowDown: 'down', a: 'left', d: 'right', w: 'up', s: 'down' };
  const onKey = (e) => { const d = keys[e.key]; if (d) { e.preventDefault(); move(d); } };
  let sx = 0, sy = 0, tracking = false;
  const down = (e) => { tracking = true; sx = e.clientX; sy = e.clientY; };
  const up = (e) => {
    if (!tracking) return;
    tracking = false;
    const dx = e.clientX - sx, dy = e.clientY - sy;
    if (Math.max(Math.abs(dx), Math.abs(dy)) < 24) return;
    move(Math.abs(dx) > Math.abs(dy) ? (dx > 0 ? 'right' : 'left') : dy > 0 ? 'down' : 'up');
  };
  board.addEventListener('pointerdown', down);
  window.addEventListener('pointerup', up);
  window.addEventListener('keydown', onKey);
  const ro = new ResizeObserver(() => grid && layout());
  ro.observe(board);

  start();
  return {
    destroy() {
      window.removeEventListener('pointerup', up);
      window.removeEventListener('keydown', onKey);
      ro.disconnect();
    },
  };
}
