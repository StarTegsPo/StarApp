// Крестики-нолики: онлайн-поиск соперника, комната для друга по коду/ссылке, бот двух уровней.
const LINES = [[0, 1, 2], [3, 4, 5], [6, 7, 8], [0, 3, 6], [1, 4, 7], [2, 5, 8], [0, 4, 8], [2, 4, 6]];
const X_SVG = '<svg viewBox="0 0 100 100" class="mk x"><path d="M24 24 76 76"/><path d="M76 24 24 76"/></svg>';
const O_SVG = '<svg viewBox="0 0 100 100" class="mk o"><circle cx="50" cy="50" r="27"/></svg>';

function winnerOf(b) {
  for (const l of LINES) if (b[l[0]] && b[l[0]] === b[l[1]] && b[l[0]] === b[l[2]]) return { mark: b[l[0]], line: l };
  return b.every(Boolean) ? { mark: 'draw', line: null } : null;
}

/** Минимакс: идеальный ход для `me`. */
function bestMove(b, me) {
  const opp = me === 'X' ? 'O' : 'X';
  const score = (board, turn, depth) => {
    const w = winnerOf(board);
    if (w) return w.mark === me ? 10 - depth : w.mark === opp ? depth - 10 : 0;
    let best = turn === me ? -Infinity : Infinity;
    for (let i = 0; i < 9; i++) {
      if (board[i]) continue;
      board[i] = turn;
      const s = score(board, turn === me ? opp : me, depth + 1);
      board[i] = null;
      best = turn === me ? Math.max(best, s) : Math.min(best, s);
    }
    return best;
  };
  let move = -1, best = -Infinity;
  for (let i = 0; i < 9; i++) {
    if (b[i]) continue;
    b[i] = me;
    const s = score(b, opp, 1);
    b[i] = null;
    if (s > best) { best = s; move = i; }
  }
  return move;
}

export function mount(h) {
  const { stage, esc } = h;
  let mode = null; // 'bot' | 'online'
  let level = 'easy';
  let board, drawn, poll, state = null, shownResult = null, wasPrivate = false, busy = false, alive = true;

  h.setScore('—', 'Режим');
  h.setInfo('—', 'Побед онлайн');
  if (h.me) h.api('/api/profile').then((p) => alive && h.setInfo(p.stats.ttt.wins, 'Побед онлайн')).catch(() => {});

  // ---------- лобби ----------
  function lobby() {
    stopPoll();
    mode = null; state = null;
    h.setScore('—', 'Режим');
    const inGame = h.online().byGame.ttt || 0;
    stage.innerHTML = `
      <div class="ttt-lobby">
        <button class="card mode mode-online rise" data-m="find" ${h.offline ? 'disabled' : ''}>
          <span class="m-icon on">🌐</span>
          <span class="m-text"><b>Быстрая игра онлайн</b><span><span class="dot"></span>${inGame} ${inGame === 1 ? 'игрок' : 'игроков'} в игре</span></span>
          <span class="go">›</span>
        </button>
        <button class="card mode rise" style="--i:1" data-m="create" ${h.offline ? 'disabled' : ''}>
          <span class="m-icon">👥</span>
          <span class="m-text"><b>Играть с другом</b><span>Создайте комнату и пришлите приглашение</span></span>
          <span class="go">›</span>
        </button>
        <form class="card mode join rise" style="--i:2" id="joinForm">
          <span class="m-icon">🔑</span>
          <input id="code" maxlength="5" placeholder="Код комнаты" autocomplete="off" autocapitalize="characters" ${h.offline ? 'disabled' : ''}>
          <button class="btn small game" ${h.offline ? 'disabled' : ''}>Войти</button>
        </form>
        <div class="card mode bot rise" style="--i:3">
          <span class="m-icon">🤖</span>
          <span class="m-text"><b>С ботом</b><span>Тренировка без соперника</span></span>
          <div class="bot-btns">
            <button class="btn small ghost" data-level="easy">Лёгкий</button>
            <button class="btn small game" data-level="hard">Непобедимый</button>
          </div>
        </div>
        ${h.offline ? '<p class="hint">Онлайн-режим доступен, когда игра открыта из Telegram</p>' : ''}
      </div>`;
    stage.querySelector('[data-m="find"]').onclick = () => { h.tap('medium'); online('find'); };
    stage.querySelector('[data-m="create"]').onclick = () => { h.tap('medium'); online('create'); };
    stage.querySelector('#joinForm').onsubmit = (e) => {
      e.preventDefault();
      const code = stage.querySelector('#code').value.trim().toUpperCase();
      if (code.length !== 5) return h.toast('Код — 5 символов');
      online('join', { code });
    };
    stage.querySelectorAll('[data-level]').forEach((b) => (b.onclick = () => { h.tap('medium'); startBot(b.dataset.level); }));
  }

  // ---------- игровое поле ----------
  function boardView(me, opp, myMark) {
    const oppMark = myMark === 'X' ? 'O' : 'X';
    stage.innerHTML = `
      <div class="ttt-players">
        <div class="pl" id="plMe"><span class="pm ${myMark.toLowerCase()}">${myMark === 'X' ? X_SVG : O_SVG}</span><b>${esc(me)}</b></div>
        <span class="vs">VS</span>
        <div class="pl" id="plOpp"><span class="pm ${oppMark.toLowerCase()}">${oppMark === 'X' ? X_SVG : O_SVG}</span><b>${esc(opp)}</b></div>
      </div>
      <div class="ttt-status" id="status"></div>
      <div class="ttt-board" id="board">
        ${Array.from({ length: 9 }, (_, i) => `<button class="tc" data-i="${i}" aria-label="Клетка ${i + 1}"></button>`).join('')}
        <svg class="win-line" viewBox="0 0 300 300" id="wl"></svg>
      </div>`;
    drawn = Array(9).fill(null);
    stage.querySelector('#board').addEventListener('click', (e) => {
      const c = e.target.closest('.tc');
      if (c) onCell(Number(c.dataset.i));
    });
  }

  function paint(b, { myTurn, line, status, statusCls = '' }) {
    stage.querySelectorAll('.tc').forEach((c, i) => {
      if (b[i] && drawn[i] !== b[i]) c.innerHTML = b[i] === 'X' ? X_SVG : O_SVG;
      c.disabled = !myTurn || !!b[i];
    });
    drawn = b.slice();
    stage.querySelector('#plMe').classList.toggle('active', !!myTurn);
    stage.querySelector('#plOpp').classList.toggle('active', !myTurn && !line && !statusCls);
    const st = stage.querySelector('#status');
    st.className = `ttt-status ${statusCls}`;
    st.innerHTML = status;
    if (line) {
      const pt = (i) => [50 + (i % 3) * 100, 50 + Math.floor(i / 3) * 100];
      const [a, z] = [pt(line[0]), pt(line[2])];
      stage.querySelector('#wl').innerHTML = `<line x1="${a[0]}" y1="${a[1]}" x2="${z[0]}" y2="${z[1]}"/>`;
      line.forEach((i) => stage.querySelectorAll('.tc')[i].classList.add('win'));
    }
  }

  function onCell(i) {
    if (mode === 'bot') return botTurn(i);
    if (mode === 'online' && state?.yourTurn && !busy) {
      busy = true;
      h.tap('medium');
      // мгновенно рисуем свой ход, сервер подтвердит
      const b = state.board.slice(); b[i] = state.mark;
      paint(b, { myTurn: false, status: 'Соперник думает<span class="dots"><i></i><i></i><i></i></span>' });
      call('move', { cell: i }).finally(() => (busy = false));
    }
  }

  // ---------- бот ----------
  function startBot(lv) {
    stopPoll();
    mode = 'bot'; level = lv; shownResult = null;
    h.setScore(lv === 'hard' ? 'Профи' : 'Лёгкий', 'Бот');
    board = Array(9).fill(null);
    boardView('Вы', lv === 'hard' ? 'Бот-профи' : 'Бот', 'X');
    paint(board, { myTurn: true, status: 'Ваш ход' });
  }

  function botTurn(i) {
    if (board[i] || winnerOf(board)) return;
    h.tap('medium');
    board[i] = 'X';
    if (finishBot()) return;
    paint(board, { myTurn: false, status: 'Бот думает<span class="dots"><i></i><i></i><i></i></span>' });
    setTimeout(() => {
      if (!alive || mode !== 'bot') return;
      const free = board.map((v, k) => (v ? -1 : k)).filter((k) => k >= 0);
      const smart = level === 'hard' || Math.random() < 0.45;
      board[smart ? bestMove(board, 'O') : free[Math.floor(Math.random() * free.length)]] = 'O';
      h.tap('light');
      if (!finishBot()) paint(board, { myTurn: true, status: 'Ваш ход' });
    }, 450 + Math.random() * 350);
  }

  function finishBot() {
    const w = winnerOf(board);
    if (!w) return false;
    const outcome = w.mark === 'draw' ? 'draw' : w.mark === 'X' ? 'win' : 'loss';
    paint(board, { myTurn: false, line: w.line, ...statusFor(outcome) });
    result(outcome, { submit: true, onAgain: () => startBot(level), againText: 'Ещё раунд' });
    return true;
  }

  // ---------- онлайн ----------
  async function call(action, body) {
    try {
      apply(await h.api(`/api/ttt/${action}`, { method: 'POST', body }));
    } catch (e) {
      h.toast(e.message);
      h.haptic('error');
      if (action === 'join') lobby();
    }
  }

  function online(action, body) {
    mode = 'online'; shownResult = null; state = null;
    stage.innerHTML = '<div class="ttt-wait"><div class="spinner"></div></div>';
    call(action, body);
    stopPoll();
    poll = setInterval(async () => {
      if (busy) return;
      try { const st = await h.api('/api/ttt'); if (!busy) apply(st); } catch {}
    }, 1000);
  }

  function apply(st) {
    if (!alive || mode !== 'online') return;
    const prev = state;
    state = st;
    if (st.status === 'idle') return lobby();
    if (st.status === 'searching') {
      if (prev?.status !== 'searching') {
        h.setScore('Поиск', 'Режим');
        stage.innerHTML = `
          <div class="ttt-wait">
            <div class="radar"><span></span><span></span><span></span><b>🌐</b></div>
            <h3>Ищем соперника…</h3>
            <p><span class="dot"></span>&nbsp;${h.online().total} онлайн</p>
            <button class="btn ghost" id="cancel">Отмена</button>
          </div>`;
        stage.querySelector('#cancel').onclick = () => { h.api('/api/ttt/leave', { method: 'POST' }).catch(() => {}); lobby(); };
      }
      return;
    }
    if (st.status === 'waiting') {
      wasPrivate = true;
      if (prev?.status !== 'waiting') {
        h.setScore('Комната', 'Режим');
        stage.innerHTML = `
          <div class="ttt-wait">
            <p>Код комнаты</p>
            <div class="room-code">${[...st.code].map((ch, i) => `<span style="--i:${i}">${ch}</span>`).join('')}</div>
            <p class="waiting-txt">Ждём друга<span class="dots"><i></i><i></i><i></i></span></p>
            <button class="btn online block" id="invite"><span class="dot"></span>Пригласить друга</button>
            <div class="row2">
              <button class="btn ghost" id="copy">Скопировать код</button>
              <button class="btn ghost" id="cancel">Отмена</button>
            </div>
          </div>`;
        stage.querySelector('#invite').onclick = () => invite(st.code);
        stage.querySelector('#copy').onclick = () => {
          navigator.clipboard?.writeText(st.code).then(() => h.toast('Код скопирован'), () => h.toast(`Код: ${st.code}`));
        };
        stage.querySelector('#cancel').onclick = () => { h.api('/api/ttt/leave', { method: 'POST' }).catch(() => {}); lobby(); };
      }
      return;
    }
    // playing / finished
    wasPrivate = st.private;
    if (!prev || !['playing', 'finished'].includes(prev.status) || prev.code !== st.code) {
      h.haptic('success');
      h.setScore('Онлайн', 'Режим');
      boardView('Вы', st.opponent?.name || 'Соперник', st.mark);
    }
    if (st.status === 'playing') {
      if (st.yourTurn && prev?.status === 'playing' && !prev.yourTurn) h.tap('rigid');
      paint(st.board, {
        myTurn: st.yourTurn && !busy,
        status: st.yourTurn ? 'Ваш ход' : 'Соперник думает<span class="dots"><i></i><i></i><i></i></span>',
        statusCls: st.yourTurn ? 'turn' : '',
      });
      return;
    }
    // finished
    stopPoll();
    const key = `${st.code}:${st.moves}`;
    if (shownResult === key) return;
    shownResult = key;
    const o = st.result.outcome;
    paint(st.board, { myTurn: false, line: st.result.line, ...statusFor(o, st.result.reason) });
    h.api('/api/profile').then((p) => alive && h.setInfo(p.stats.ttt.wins, 'Побед онлайн')).catch(() => {});
    result(o, {
      submit: false,
      reason: st.result.reason,
      againText: wasPrivate ? 'Новая комната' : 'Новый соперник',
      onAgain: () => online(wasPrivate ? 'create' : 'find'),
    });
  }

  function invite(code) {
    const bot = h.me?.botUsername;
    const link = bot ? `https://t.me/${bot}?start=ttt_${code}` : `${location.origin}/?join=${code}`;
    const text = `⚔️ Вызываю тебя на дуэль в крестики-нолики! Код комнаты: ${code}`;
    const u = `https://t.me/share/url?url=${encodeURIComponent(link)}&text=${encodeURIComponent(text)}`;
    const tg = window.Telegram?.WebApp;
    tg?.openTelegramLink ? tg.openTelegramLink(u) : window.open(u, '_blank');
  }

  function statusFor(o, reason) {
    if (o === 'win') return { status: reason === 'left' ? '🏆 Соперник сдался — победа!' : '🏆 Победа!', statusCls: 'win' };
    if (o === 'loss') return { status: reason === 'left' ? 'Вы покинули партию' : 'Поражение', statusCls: 'loss' };
    return { status: 'Ничья', statusCls: 'draw' };
  }

  function result(o, { submit, reason, onAgain, againText }) {
    const map = {
      win: { emoji: '🏆', title: 'Победа!', text: reason === 'left' ? 'Соперник покинул игру' : 'Три в ряд — красиво!' },
      loss: { emoji: '😤', title: 'Поражение', text: 'Возьмите реванш!' },
      draw: { emoji: '🤝', title: 'Ничья', text: 'Силы равны' },
    }[o];
    setTimeout(() => alive && h.over({ ...map, score: null, submit, win: o === 'win', onAgain, againText }), 900);
  }

  function stopPoll() { clearInterval(poll); poll = null; }

  // старт: приглашение по ссылке, быстрый поиск с главной или лобби
  if (h.params.mode === 'join' && h.params.join) online('join', { code: h.params.join });
  else if (h.params.mode === 'find' && !h.offline) online('find');
  else lobby();

  return {
    destroy() {
      alive = false;
      stopPoll();
      if (mode === 'online' && state && ['searching', 'waiting', 'playing'].includes(state.status)) {
        h.api('/api/ttt/leave', { method: 'POST' }).catch(() => {});
      }
    },
  };
}
