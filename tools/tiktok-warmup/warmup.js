// Прогрев TikTok-аккаунта: листает ленту «Для вас» в настоящем окне Chromium
// и ставит лайк через случайное количество роликов (по умолчанию 1–18).
// Вход в аккаунт делаете вы сами в открывшемся окне — сессия сохраняется в ./profile.
import { chromium } from 'playwright';
import readline from 'node:readline/promises';
import { stdin, stdout } from 'node:process';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const DIR = path.dirname(fileURLToPath(import.meta.url));

// ---------- настройки (меняются флагами: --videos=150 --like-max=18 ...) ----------
const args = Object.fromEntries(
  process.argv.slice(2).map((a) => a.replace(/^--/, '').split('=')).map(([k, v]) => [k, v ?? 'true'])
);
const num = (k, d) => (args[k] !== undefined ? Number(args[k]) : d);
const CFG = {
  videos: num('videos', 120),         // сколько роликов пролистать за запуск
  likeMin: num('like-min', 1),        // лайк через случайное число роликов
  likeMax: num('like-max', 18),       //   в диапазоне [likeMin, likeMax]
  watchMin: num('watch-min', 5),      // секунд просмотра одного ролика (мин)
  watchMax: num('watch-max', 25),     // секунд просмотра одного ролика (макс)
  breakEvery: num('break-every', 40), // пауза примерно каждые N роликов
  breakMin: num('break-min', 30),
  breakMax: num('break-max', 120),
};

const rand = (a, b) => a + Math.random() * (b - a);
const randInt = (a, b) => Math.floor(rand(a, b + 1));
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
const log = (...m) => console.log(new Date().toLocaleTimeString(), ...m);

// Кнопка лайка того ролика, который сейчас ближе всего к центру экрана
async function findActiveLike(page) {
  const icons = await page.$$('[data-e2e="like-icon"], [data-e2e="browse-like-icon"]');
  const vh = await page.evaluate(() => window.innerHeight);
  let best = null;
  let bestDist = Infinity;
  for (const el of icons) {
    const box = await el.boundingBox();
    if (!box || box.y < 0 || box.y > vh) continue;
    const dist = Math.abs(box.y + box.height / 2 - vh / 2);
    if (dist < bestDist) { best = el; bestDist = dist; }
  }
  if (!best) return null;
  return (await best.evaluateHandle((el) => el.closest('button') || el)).asElement();
}

async function isLiked(btn) {
  return btn.evaluate((el) => {
    if (el.getAttribute('aria-pressed') === 'true') return true;
    const svg = el.querySelector('svg');
    if (!svg) return false;
    const m = getComputedStyle(svg).color.match(/\d+/g);
    if (!m) return false;
    const [r, g, b] = m.map(Number);
    return r > 200 && g < 100 && b < 120; // красное сердце = уже лайкнут
  });
}

async function like(page) {
  const btn = await findActiveLike(page);
  if (!btn) return log('⚠️  кнопка лайка не найдена — пропускаю');
  if (await isLiked(btn)) return log('💗 уже лайкнут, не трогаю');
  await btn.hover();
  await sleep(rand(200, 700));
  await btn.click();
  log('❤️  лайк');
}

async function nextVideo(page) {
  // Стрелка вниз листает ленту на tiktok.com
  await page.keyboard.press('ArrowDown');
  await sleep(rand(800, 1500));
}

async function main() {
  const ctx = await chromium.launchPersistentContext(path.join(DIR, 'profile'), {
    headless: false,
    viewport: null,
    args: ['--start-maximized'],
  });
  const page = ctx.pages()[0] ?? (await ctx.newPage());
  await page.goto('https://www.tiktok.com/foryou', { waitUntil: 'domcontentloaded' });

  const rl = readline.createInterface({ input: stdin, output: stdout });
  await rl.question(
    '\n👉 Войдите в свой аккаунт в открывшемся окне (если ещё не вошли),\n' +
    '   откройте ленту «Для вас», закройте всплывающие окна и нажмите Enter здесь...'
  );
  rl.close();

  let untilLike = randInt(CFG.likeMin, CFG.likeMax);
  let untilBreak = randInt(Math.max(5, CFG.breakEvery - 10), CFG.breakEvery + 10);
  let likes = 0;
  log(`▶️  старт: ${CFG.videos} роликов, первый лайк через ${untilLike}`);

  for (let i = 1; i <= CFG.videos; i++) {
    const watch = rand(CFG.watchMin, CFG.watchMax);
    log(`🎬 ролик ${i}/${CFG.videos}, смотрю ${watch.toFixed(1)} c`);
    await sleep(watch * 1000);

    if (--untilLike <= 0) {
      await like(page).then(() => likes++).catch((e) => log('⚠️  ошибка лайка:', e.message));
      await sleep(rand(1000, 3000));
      untilLike = randInt(CFG.likeMin, CFG.likeMax);
      log(`   следующий лайк через ${untilLike}`);
    }

    if (--untilBreak <= 0) {
      const pause = rand(CFG.breakMin, CFG.breakMax);
      log(`☕ перерыв ${pause.toFixed(0)} c`);
      await sleep(pause * 1000);
      untilBreak = randInt(Math.max(5, CFG.breakEvery - 10), CFG.breakEvery + 10);
    }

    if (i < CFG.videos) await nextVideo(page);
  }

  log(`✅ готово: ${CFG.videos} роликов, лайков ~${likes}`);
  await ctx.close();
}

main().catch((e) => { console.error(e); process.exit(1); });
