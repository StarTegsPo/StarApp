// Сапёр 9×9: первый ход всегда безопасный, флажки долгим нажатием или переключателем.
const W = 9, H = 9, MINES = 10;

export function mount(h) {
  const { stage } = h;
  let cells, started, over, t0, timer, flagMode = false, opened, flags;

  stage.innerHTML = `
    <div class="mines" id="mf">${'<button class="mc"></button>'.repeat(W * H)}</div>
    <div class="mines-bar">
      <div class="seg" id="seg">
        <span class="seg-pill"></span>
        <button class="active" data-f="0">⛏ Открыть</button>
        <button data-f="1">🚩 Флажок</button>
      </div>
    </div>
    <p class="hint">Долгое нажатие — поставить флажок</p>`;
  const field = stage.querySelector('#mf');
  const els = [...field.children];
  const seg = stage.querySelector('#seg');
  seg.addEventListener('click', (e) => {
    const b = e.target.closest('[data-f]');
    if (!b) return;
    h.tap('soft');
    flagMode = b.dataset.f === '1';
    seg.classList.toggle('right', flagMode);
    seg.querySelectorAll('button').forEach((x) => x.classList.toggle('active', x === b));
  });

  const nb = (i) => {
    const x = i % W, y = Math.floor(i / W), out = [];
    for (let dy = -1; dy <= 1; dy++) for (let dx = -1; dx <= 1; dx++) {
      const nx = x + dx, ny = y + dy;
      if ((dx || dy) && nx >= 0 && ny >= 0 && nx < W && ny < H) out.push(ny * W + nx);
    }
    return out;
  };

  function reset() {
    cells = Array.from({ length: W * H }, () => ({ mine: false, n: 0, open: false, flag: false }));
    started = false; over = false; opened = 0; flags = 0;
    clearInterval(timer);
    els.forEach((el, i) => { el.className = 'mc'; el.textContent = ''; el.style.setProperty('--i', (i % W) + Math.floor(i / W)); });
    h.setScore('0:00', 'Время');
    h.setInfo(`💣 ${MINES}`, 'Мин осталось');
  }

  function plant(safe) {
    const forbidden = new Set([safe, ...nb(safe)]);
    let placed = 0;
    while (placed < MINES) {
      const i = Math.floor(Math.random() * W * H);
      if (forbidden.has(i) || cells[i].mine) continue;
      cells[i].mine = true;
      placed++;
    }
    cells.forEach((c, i) => (c.n = nb(i).filter((j) => cells[j].mine).length));
    started = true;
    t0 = performance.now();
    timer = setInterval(() => {
      const s = Math.floor((performance.now() - t0) / 1000);
      h.setScore(`${Math.floor(s / 60)}:${String(s % 60).padStart(2, '0')}`);
    }, 500);
  }

  function reveal(i, wave = 0) {
    const c = cells[i];
    if (c.open || c.flag) return;
    c.open = true;
    opened++;
    const el = els[i];
    el.style.setProperty('--d', `${wave * 28}ms`);
    el.classList.add('open', `n${c.n}`);
    if (c.n) el.textContent = c.n;
    if (!c.n) for (const j of nb(i)) reveal(j, wave + 1);
  }

  function toggleFlag(i) {
    const c = cells[i];
    if (c.open || over) return;
    c.flag = !c.flag;
    flags += c.flag ? 1 : -1;
    els[i].classList.toggle('flag', c.flag);
    els[i].textContent = c.flag ? '🚩' : '';
    h.tap('rigid');
    h.setInfo(`💣 ${MINES - flags}`);
  }

  function open(i) {
    if (over) return;
    if (!started) plant(i);
    const c = cells[i];
    if (c.flag) return;
    // нажатие на открытую цифру: если флажков вокруг столько же — открыть соседей
    if (c.open) {
      if (!c.n || nb(i).filter((j) => cells[j].flag).length !== c.n) return;
      for (const j of nb(i)) if (!cells[j].flag && !cells[j].open) { if (cells[j].mine) return boom(j); reveal(j); }
    } else {
      if (c.mine) return boom(i);
      h.tap('light');
      reveal(i);
    }
    if (opened === W * H - MINES) win();
  }

  function boom(i) {
    over = true;
    clearInterval(timer);
    h.haptic('error');
    els[i].classList.add('boom');
    els[i].textContent = '💥';
    let k = 0;
    cells.forEach((c, j) => {
      if (j === i) return;
      if (c.mine && !c.flag) setTimeout(() => { els[j].classList.add('mine'); els[j].textContent = '💣'; }, 120 + k++ * 70);
      if (!c.mine && c.flag) els[j].classList.add('wrong');
    });
    field.classList.add('shake');
    setTimeout(() => field.classList.remove('shake'), 500);
    setTimeout(() => h.over({ score: null, submit: false, title: 'Бум! Вы подорвались', emoji: '💥', text: 'Попробуйте ещё раз — первый ход всегда безопасный' }), 1100 + k * 70);
  }

  function win() {
    over = true;
    clearInterval(timer);
    const sec = Math.max(1, Math.round((performance.now() - t0) / 1000));
    cells.forEach((c, j) => c.mine && !c.flag && (els[j].textContent = '🚩', els[j].classList.add('flag')));
    h.setInfo('💣 0');
    setTimeout(() => h.over({ score: sec, title: 'Поле разминировано!', emoji: '🎖️', win: true }), 500);
  }

  // нажатие / долгое нажатие
  let pressT, longFired = false;
  field.addEventListener('pointerdown', (e) => {
    const el = e.target.closest('.mc');
    if (!el) return;
    longFired = false;
    pressT = setTimeout(() => { longFired = true; toggleFlag(els.indexOf(el)); }, 380);
  });
  const cancel = () => clearTimeout(pressT);
  field.addEventListener('pointerup', cancel);
  field.addEventListener('pointerleave', cancel);
  field.addEventListener('contextmenu', (e) => e.preventDefault());
  field.addEventListener('click', (e) => {
    const el = e.target.closest('.mc');
    if (!el || longFired) return;
    const i = els.indexOf(el);
    flagMode && !cells[i].open ? toggleFlag(i) : open(i);
  });

  reset();
  return { destroy: () => { clearInterval(timer); clearTimeout(pressT); } };
}
