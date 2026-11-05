import { test, before, after } from 'node:test';
import assert from 'node:assert/strict';
import { startTestApp } from './helpers.js';
import { parseEcoBots, DEFAULT_ECO_BOTS, ANDROID_URL, LIMITS } from '../src/services/eco/index.js';

let t;
before(async () => { t = await startTestApp(); });
after(() => t.close());

const ALICE = { id: 7001, first_name: 'Алиса' };
const BOB = { id: 7002, first_name: 'Боб' };

test('eco: все маршруты требуют авторизации', async () => {
  for (const [method, path] of [
    ['GET', '/api/eco'], ['GET', '/api/eco/settings'], ['POST', '/api/eco/settings'],
    ['GET', '/api/eco/templates'], ['POST', '/api/eco/templates'], ['DELETE', '/api/eco/templates/1'],
    ['GET', '/api/eco/notes'], ['POST', '/api/eco/notes'], ['GET', '/api/eco/notes/1'], ['POST', '/api/eco/notes/1'], ['DELETE', '/api/eco/notes/1'],
  ]) {
    const r = await t.request(path, { user: null, method, body: method === 'GET' ? undefined : {} });
    assert.equal(r.status, 401, `${method} ${path}`);
  }
});

test('eco: первая вкладка, боты экосистемы и ссылка на Android', async () => {
  const me = (await t.request('/api/me', { user: ALICE })).data;
  assert.equal(me.services[0].id, 'eco');
  const { status, data } = await t.request('/api/eco', { user: ALICE });
  assert.equal(status, 200);
  assert.equal(data.android, ANDROID_URL);
  assert.deepEqual(data.bots.map((b) => b.bot || `tab:${b.tab}`), ['StarTegsEcosistem_bot', 'StarTegsShop_bot', 'StarTegsAi_bot', 'tab:analytics']);
  assert.deepEqual(data.settings, { palette: 'violet', hue: 0, effect: 'off' });
  assert.deepEqual(data.counts, { templates: 0, notes: 0 });
});

test('eco: ECO_BOTS из окружения и защита от мусора', async () => {
  const custom = parseEcoBots(JSON.stringify([{ title: 'Мой бот', bot: '@my_cool_bot', icon: '🚀' }, { title: 'Плохой', bot: 'x y' }]));
  assert.equal(custom.length, 1);
  assert.equal(custom[0].bot, 'my_cool_bot');
  const orig = console.error; console.error = () => {};
  try {
    assert.equal(parseEcoBots('не json'), DEFAULT_ECO_BOTS);
    assert.equal(parseEcoBots('[]'), DEFAULT_ECO_BOTS);
  } finally { console.error = orig; }

  const t2 = await startTestApp({ ECO_BOTS: JSON.stringify([{ title: 'Тест', bot: 'Test_echo_bot' }]) });
  try {
    const { data } = await t2.request('/api/eco');
    assert.deepEqual(data.bots.map((b) => b.bot), ['Test_echo_bot']);
  } finally { await t2.close(); }
});

test('eco: настройки оформления сохраняются, частичное обновление, валидация', async () => {
  let r = await t.request('/api/eco/settings', { user: ALICE, method: 'POST', body: { palette: 'neon', effect: 'snow' } });
  assert.equal(r.status, 200);
  assert.deepEqual(r.data, { palette: 'neon', hue: 0, effect: 'snow' });

  r = await t.request('/api/eco/settings', { user: ALICE, method: 'POST', body: { palette: 'custom', hue: 11 } });
  assert.deepEqual(r.data, { palette: 'custom', hue: 11, effect: 'snow' });
  assert.deepEqual((await t.request('/api/eco/settings', { user: ALICE })).data, { palette: 'custom', hue: 11, effect: 'snow' });

  for (const body of [{ palette: 'pink' }, { effect: 'rain' }, { hue: 12 }, { hue: -1 }, { hue: 1.5 }, { hue: '3' }, { palette: ['violet'] }]) {
    const bad = await t.request('/api/eco/settings', { user: ALICE, method: 'POST', body });
    assert.equal(bad.status, 400, JSON.stringify(body));
    assert.ok(bad.data.error);
  }
  // неудачные запросы ничего не испортили
  assert.deepEqual((await t.request('/api/eco/settings', { user: ALICE })).data, { palette: 'custom', hue: 11, effect: 'snow' });
  // у другого пользователя — свои настройки
  assert.deepEqual((await t.request('/api/eco/settings', { user: BOB })).data, { palette: 'violet', hue: 0, effect: 'off' });
});

test('eco: шаблоны — создание, проверки, удаление', async () => {
  let r = await t.request('/api/eco/templates', { user: ALICE, method: 'POST', body: { title: '  Приветствие ', text: 'Здравствуйте! Чем помочь?' } });
  assert.equal(r.status, 200);
  assert.equal(r.data.title, 'Приветствие');
  const id = r.data.id;

  for (const body of [{ title: '', text: 'x' }, { title: 'x', text: '   ' }, { title: 'x' }, { title: 5, text: 'x' },
    { title: 'x', text: 'a'.repeat(LIMITS.templateText + 1) }, { title: 'a'.repeat(LIMITS.templateTitle + 1), text: 'x' }]) {
    const bad = await t.request('/api/eco/templates', { user: ALICE, method: 'POST', body });
    assert.equal(bad.status, 400, JSON.stringify(body).slice(0, 60));
  }
  assert.equal((await t.request('/api/eco/templates', { user: ALICE, method: 'POST', body: { title: 'Длинный', text: 'a'.repeat(LIMITS.templateText) } })).status, 200);

  const list = (await t.request('/api/eco/templates', { user: ALICE })).data;
  assert.equal(list.templates.length, 2);
  assert.equal(list.limit, 50);

  // чужой шаблон не видно и нельзя удалить
  assert.equal((await t.request('/api/eco/templates', { user: BOB })).data.templates.length, 0);
  assert.equal((await t.request(`/api/eco/templates/${id}`, { user: BOB, method: 'DELETE' })).status, 404);
  assert.equal((await t.request('/api/eco/templates/abc', { user: ALICE, method: 'DELETE' })).status, 400);

  assert.equal((await t.request(`/api/eco/templates/${id}`, { user: ALICE, method: 'DELETE' })).status, 200);
  assert.equal((await t.request(`/api/eco/templates/${id}`, { user: ALICE, method: 'DELETE' })).status, 404);
  assert.equal((await t.request('/api/eco/templates', { user: ALICE })).data.templates.length, 1);
});

test('eco: не больше 50 шаблонов', async () => {
  const u = { id: 7003, first_name: 'Лимит' };
  for (let i = 0; i < 50; i++) {
    const r = await t.request('/api/eco/templates', { user: u, method: 'POST', body: { title: `Т${i}`, text: 'текст' } });
    assert.equal(r.status, 200);
  }
  const r = await t.request('/api/eco/templates', { user: u, method: 'POST', body: { title: 'лишний', text: 'текст' } });
  assert.equal(r.status, 409);
  assert.match(r.data.error, /50/);
  assert.equal((await t.request('/api/eco', { user: u })).data.counts.templates, 50);
});

test('eco: заметки — CRUD и изоляция пользователей', async () => {
  let r = await t.request('/api/eco/notes', { user: ALICE, method: 'POST', body: { title: 'Идеи', text: 'Запустить рассылку' } });
  assert.equal(r.status, 200);
  const id = r.data.id;
  assert.equal(r.data.title, 'Идеи');

  // только текст — можно, пустая — нельзя
  assert.equal((await t.request('/api/eco/notes', { user: ALICE, method: 'POST', body: { text: 'без заголовка' } })).status, 200);
  assert.equal((await t.request('/api/eco/notes', { user: ALICE, method: 'POST', body: { title: ' ', text: '' } })).status, 400);
  assert.equal((await t.request('/api/eco/notes', { user: ALICE, method: 'POST', body: { title: 'x', text: 'a'.repeat(LIMITS.noteText + 1) } })).status, 400);
  assert.equal((await t.request('/api/eco/notes', { user: ALICE, method: 'POST', body: { title: { a: 1 } } })).status, 400);

  r = await t.request(`/api/eco/notes/${id}`, { user: ALICE, method: 'POST', body: { title: 'Идеи на неделю', text: 'Рассылка в пятницу' } });
  assert.equal(r.status, 200);
  assert.equal(r.data.text, 'Рассылка в пятницу');
  assert.equal((await t.request(`/api/eco/notes/${id}`, { user: ALICE })).data.title, 'Идеи на неделю');

  // Боб не видит, не меняет и не удаляет заметку Алисы
  assert.equal((await t.request('/api/eco/notes', { user: BOB })).data.notes.length, 0);
  assert.equal((await t.request(`/api/eco/notes/${id}`, { user: BOB })).status, 404);
  assert.equal((await t.request(`/api/eco/notes/${id}`, { user: BOB, method: 'POST', body: { title: 'взлом' } })).status, 404);
  assert.equal((await t.request(`/api/eco/notes/${id}`, { user: BOB, method: 'DELETE' })).status, 404);
  assert.equal((await t.request(`/api/eco/notes/${id}`, { user: ALICE })).data.title, 'Идеи на неделю');

  const { notes } = (await t.request('/api/eco/notes', { user: ALICE })).data;
  assert.equal(notes.length, 2);
  assert.equal((await t.request(`/api/eco/notes/${id}`, { user: ALICE, method: 'DELETE' })).status, 200);
  assert.equal((await t.request(`/api/eco/notes/${id}`, { user: ALICE })).status, 404);
  assert.equal((await t.request('/api/eco', { user: ALICE })).data.counts.notes, 1);
});
