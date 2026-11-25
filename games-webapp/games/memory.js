// Мемори: 3D-переворот карточек, таймер с первого хода, счёт ходов.
const EMOJI = ['🚀', '🎧', '🍕', '🦄', '🌈', '🔥', '💎', '🎸', '🐙', '🍩', '⚡', '🪐'];

export function mount(h) {
  const { stage } = h;
  const pairs = 8;
  const picked = shuffle(EMOJI.slice()).slice(0, pairs);
  const deck = shuffle([...picked, ...picked]);
  let open = [], matched = 0, moves = 0, t0 = 0, timer, lock = false;

  stage.innerHTML = `
    <div class="mem">
      ${deck.map((e, i) => `
        <button class="mcard" data-i="${i}" style="--i:${i}" aria-label="Карточка">
          <span class="face back"><span>★</span></span>
          <span class="face front">${e}</span>
        </button>`).join('')}
    </div>
    <p class="hint">Время пойдёт с первой карточки</p>`;
  h.setScore('0:00', 'Время');
  h.setInfo('0', 'Ходов');

  const cards = [...stage.querySelectorAll('.mcard')];
  const clock = () => {
    const s = Math.floor((performance.now() - t0) / 1000);
    h.setScore(`${Math.floor(s / 60)}:${String(s % 60).padStart(2, '0')}`);
  };

  stage.querySelector('.mem').addEventListener('click', (e) => {
    const c = e.target.closest('.mcard');
    if (!c || lock || c.classList.contains('flipped')) return;
    if (!t0) { t0 = performance.now(); timer = setInterval(clock, 250); }
    h.tap('light');
    c.classList.add('flipped');
    open.push(c);
    if (open.length < 2) return;
    moves++;
    h.setInfo(String(moves));
    const [a, b] = open;
    open = [];
    if (deck[a.dataset.i] === deck[b.dataset.i]) {
      matched++;
      setTimeout(() => { a.classList.add('matched'); b.classList.add('matched'); h.haptic('success'); }, 250);
      if (matched === pairs) {
        clearInterval(timer);
        const sec = Math.max(5, Math.round((performance.now() - t0) / 1000));
        clock();
        setTimeout(() => h.over({ score: sec, title: 'Все пары найдены!', emoji: '🃏', text: `${moves} ходов`, win: true }), 800);
      }
    } else {
      lock = true;
      setTimeout(() => { a.classList.add('nope'); b.classList.add('nope'); h.haptic('error'); }, 350);
      setTimeout(() => { a.classList.remove('flipped', 'nope'); b.classList.remove('flipped', 'nope'); lock = false; }, 900);
    }
  });

  return { destroy: () => clearInterval(timer), cards };
}

function shuffle(a) {
  for (let i = a.length - 1; i > 0; i--) {
    const j = Math.floor(Math.random() * (i + 1));
    [a[i], a[j]] = [a[j], a[i]];
  }
  return a;
}
