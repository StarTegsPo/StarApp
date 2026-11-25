// Реакция: 5 попыток, жмите, как только экран станет зелёным. Результат — среднее время.
const ROUNDS = 5;

export function mount(h) {
  const { stage } = h;
  stage.innerHTML = `
    <button class="react idle" id="pad">
      <span class="r-icon">⚡</span>
      <b id="rTitle">Нажмите, чтобы начать</b>
      <span id="rSub">Когда экран станет зелёным — жмите как можно быстрее</span>
    </button>
    <div class="r-dots" id="dots">${'<i></i>'.repeat(ROUNDS)}</div>`;
  const pad = stage.querySelector('#pad');
  const title = stage.querySelector('#rTitle');
  const sub = stage.querySelector('#rSub');
  const icon = stage.querySelector('.r-icon');
  const dots = [...stage.querySelectorAll('#dots i')];
  let phase = 'idle', times = [], timer, goAt = 0;

  h.setScore('—', 'Среднее');
  h.setInfo(`0 / ${ROUNDS}`, 'Попытка');

  const set = (cls, ic, t, s) => {
    pad.className = `react ${cls}`;
    icon.textContent = ic;
    title.textContent = t;
    sub.textContent = s;
  };

  function arm() {
    phase = 'wait';
    set('wait', '✋', 'Ждите зелёного…', 'Не торопитесь');
    timer = setTimeout(() => {
      phase = 'go';
      set('go', '👆', 'ЖМИТЕ!', '');
      goAt = performance.now();
      h.tap('heavy');
    }, 1200 + Math.random() * 2300);
  }

  pad.addEventListener('pointerdown', (e) => {
    e.preventDefault();
    if (phase === 'idle' || phase === 'result') return arm();
    if (phase === 'wait') {
      clearTimeout(timer);
      phase = 'early';
      h.haptic('error');
      set('early', '😬', 'Слишком рано!', 'Нажмите, чтобы повторить попытку');
      return;
    }
    if (phase === 'early') return arm();
    if (phase === 'go') {
      const ms = Math.round(performance.now() - goAt);
      times.push(ms);
      dots[times.length - 1].classList.add('done');
      dots[times.length - 1].style.setProperty('--q', Math.max(0, Math.min(1, (ms - 150) / 350)));
      h.setInfo(`${times.length} / ${ROUNDS}`);
      const avg = Math.round(times.reduce((a, b) => a + b, 0) / times.length);
      h.setScore(`${avg} мс`);
      h.tap('light');
      if (times.length >= ROUNDS) {
        phase = 'done';
        set('result', rate(avg).e, `${avg} мс в среднем`, rate(avg).t);
        setTimeout(() => h.over({ score: Math.max(80, avg), title: rate(avg).t, emoji: rate(avg).e, text: `Попытки: ${times.join(' · ')} мс` }), 700);
      } else {
        phase = 'result';
        set('result', ms < 250 ? '🔥' : '👍', `${ms} мс`, 'Нажмите для следующей попытки');
      }
    }
  });

  return { destroy: () => clearTimeout(timer) };
}

function rate(ms) {
  if (ms < 200) return { e: '🚀', t: 'Молниеносно!' };
  if (ms < 260) return { e: '🔥', t: 'Отличная реакция' };
  if (ms < 330) return { e: '👍', t: 'Хорошо' };
  return { e: '🐢', t: 'Можно быстрее' };
}
