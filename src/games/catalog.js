/**
 * Каталог игр StarGames — единый источник для сервера и Mini App.
 *  order: 'desc' — чем больше, тем лучше (очки); 'asc' — чем меньше, тем лучше (время, мс)
 *  unit:  pts | sec | ms | wins — как показывать результат
 *  min/max — границы правдоподобного результата (всё остальное сервер отклоняет)
 */
export const GAMES = [
  {
    id: '2048', title: '2048', icon: '🧩', desc: 'Соединяй плитки и дойди до 2048',
    colors: ['#f59e0b', '#ef4444'], order: 'desc', unit: 'pts', min: 0, max: 5_000_000,
  },
  {
    id: 'snake', title: 'Змейка', icon: '🐍', desc: 'Классика: ешь и расти, не врезайся',
    colors: ['#22c55e', '#0d9488'], order: 'desc', unit: 'pts', min: 0, max: 100_000,
  },
  {
    id: 'ttt', title: 'Крестики-нолики', icon: '❌', desc: 'Онлайн-дуэли, игра с другом или с ботом',
    colors: ['#6366f1', '#06b6d4'], order: 'desc', unit: 'wins', online: true,
  },
  {
    id: 'memory', title: 'Мемори', icon: '🃏', desc: 'Найди все пары карточек как можно быстрее',
    colors: ['#ec4899', '#8b5cf6'], order: 'asc', unit: 'sec', min: 5, max: 3600,
  },
  {
    id: 'mines', title: 'Сапёр', icon: '💣', desc: 'Открой поле и не наступи на мину',
    colors: ['#0ea5e9', '#2563eb'], order: 'asc', unit: 'sec', min: 1, max: 3600,
  },
  {
    id: 'reaction', title: 'Реакция', icon: '⚡', desc: 'Жми, как только экран станет зелёным',
    colors: ['#f43f5e', '#fb923c'], order: 'asc', unit: 'ms', min: 80, max: 5000,
  },
];

export const gameById = (id) => GAMES.find((g) => g.id === id) || null;

/** Человекочитаемый результат: «1 234 очка», «1:05», «231 мс», «7 побед». */
export function formatScore(game, v) {
  if (v == null) return '—';
  const n = Number(v);
  switch (game.unit) {
    case 'ms': return `${n} мс`;
    case 'sec': return `${Math.floor(n / 60)}:${String(n % 60).padStart(2, '0')}`;
    case 'wins': return `${n} ${plural(n, 'победа', 'победы', 'побед')}`;
    default: return n.toLocaleString('ru-RU');
  }
}

export function plural(n, one, few, many) {
  const m10 = n % 10, m100 = n % 100;
  return m10 === 1 && m100 !== 11 ? one : m10 >= 2 && m10 <= 4 && (m100 < 10 || m100 >= 20) ? few : many;
}
