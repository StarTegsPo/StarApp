import { test, before, after } from 'node:test';
import assert from 'node:assert/strict';
import { startTestApp } from './helpers.js';
import { oembedUrl, isDue, localNow } from '../src/services/hub/index.js';
import { readAiConfig, complete } from '../src/services/hub/providers.js';

const ALICE = { id: 8001, first_name: 'Алиса' };
const BOB = { id: 8002, first_name: 'Боб' };

/** Фейковые провайдеры ИИ и oEmbed: записывают запросы, отвечают заготовками. */
function fakeAi() {
  const calls = [];
  const json = (status, body) => ({ ok: status < 400, status, json: async () => body });
  const impl = async (url, opts = {}) => {
    const body = opts.body ? JSON.parse(opts.body) : null;
    calls.push({ url, headers: opts.headers, body });
    if (url.startsWith('https://api.anthropic.com/')) {
      return json(200, { stop_reason: 'end_turn', content: [{ type: 'thinking', thinking: '' }, { type: 'text', text: `claude:${body.messages.at(-1).content}` }] });
    }
    if (url.startsWith('https://api.openai.com/')) return json(200, { choices: [{ message: { content: `gpt:${body.messages.at(-1).content}` } }] });
    if (url.startsWith('https://generativelanguage.googleapis.com/')) return json(500, { error: { message: 'boom' } });
    if (url.startsWith('https://www.tiktok.com/oembed')) return json(200, { title: 'Котик', author_name: 'cat', thumbnail_url: 'https://p16.tiktokcdn.com/x.jpg' });
    throw new Error('unexpected ' + url);
  };
  return { calls, impl };
}

let t, ai;
before(async () => {
  t = await startTestApp({ ANTHROPIC_API_KEY: 'sk-a', OPENAI_API_KEY: 'sk-o', GEMINI_API_KEY: 'g', HUB_DAILY_LIMIT: '5' });
  ai = fakeAi();
  t.app.ctx.hubFetch = ai.impl;
});
after(() => t.close());

test('hub: маршруты требуют авторизации', async () => {
  for (const [method, path] of [['GET', '/api/hub'], ['POST', '/api/hub/ask'], ['POST', '/api/hub/compare'], ['GET', '/api/hub/chats'], ['GET', '/api/hub/tasks'], ['POST', '/api/hub/tasks'], ['GET', '/api/hub/preview']]) {
    const r = await t.request(path, { user: null, method, body: method === 'GET' ? undefined : {} });
    assert.equal(r.status, 401, `${method} ${path}`);
  }
});

test('hub: провайдеры видны по ключам, вкладка вторая', async () => {
  const me = (await t.request('/api/me', { user: ALICE })).data;
  assert.equal(me.services[1].id, 'hub');
  const { data } = await t.request('/api/hub', { user: ALICE });
  assert.deepEqual(data.providers.filter((p) => p.enabled).map((p) => p.id), ['claude', 'chatgpt', 'gemini']);
  assert.ok(data.social.some((s) => s.id === 'tiktok'));
  assert.deepEqual(data.usage, { used: 0, limit: 5 });
  assert.ok(!JSON.stringify(data).includes('sk-a'), 'ключи не уходят клиенту');
});

test('hub: диалог с историей и переключением ИИ, чужой чат недоступен', async () => {
  const r1 = await t.request('/api/hub/ask', { user: ALICE, method: 'POST', body: { text: 'привет' } });
  assert.equal(r1.status, 200);
  assert.equal(r1.data.reply, 'claude:привет');
  const claudeCall = ai.calls.at(-1);
  assert.equal(claudeCall.headers['x-api-key'], 'sk-a');
  assert.equal(claudeCall.body.model, 'claude-opus-5-5');
  assert.equal(claudeCall.body.fallbacks, 'default');

  const r2 = await t.request('/api/hub/ask', { user: ALICE, method: 'POST', body: { text: 'ещё', provider: 'chatgpt', chatId: r1.data.chatId } });
  assert.equal(r2.data.reply, 'gpt:ещё');
  assert.equal(r2.data.chatId, r1.data.chatId);
  const sent = ai.calls.at(-1).body.messages;
  assert.deepEqual(sent.map((m) => m.role), ['system', 'user', 'assistant', 'user'], 'ИИ видит всю переписку');

  const chat = (await t.request(`/api/hub/chats/${r1.data.chatId}`, { user: ALICE })).data;
  assert.equal(chat.messages.length, 4);
  assert.equal((await t.request(`/api/hub/chats/${r1.data.chatId}`, { user: BOB })).status, 404);
  assert.equal((await t.request('/api/hub/ask', { user: BOB, method: 'POST', body: { text: 'x', chatId: r1.data.chatId } })).status, 404);
  assert.equal((await t.request(`/api/hub/chats/${r1.data.chatId}`, { user: BOB, method: 'DELETE' })).status, 404);

  assert.equal((await t.request('/api/hub/ask', { user: ALICE, method: 'POST', body: { text: 'x', provider: 'grok' } })).status, 400);
  assert.equal((await t.request('/api/hub/ask', { user: ALICE, method: 'POST', body: { text: '  ' } })).status, 400);
});

test('hub: сравнение — ошибка одного ИИ не ломает остальные; дневной лимит', async () => {
  const user = { id: 8003, first_name: 'Ваня' };
  const r = await t.request('/api/hub/compare', { user, method: 'POST', body: { text: 'вопрос' } });
  assert.equal(r.status, 200);
  assert.deepEqual(r.data.answers.map((a) => a.provider), ['claude', 'chatgpt', 'gemini']);
  assert.equal(r.data.answers[1].reply, 'gpt:вопрос');
  assert.match(r.data.answers[2].error, /boom/);
  assert.equal(r.data.usage.used, 3);
  await t.request('/api/hub/ask', { user, method: 'POST', body: { text: '4' } });
  await t.request('/api/hub/ask', { user, method: 'POST', body: { text: '5' } });
  const over = await t.request('/api/hub/ask', { user, method: 'POST', body: { text: '6' } });
  assert.equal(over.status, 429);
  // админ без лимита
  const admin = (await t.request('/api/hub', { user: { id: 1, first_name: 'A' } })).data;
  assert.equal(admin.usage.limit, null);
});

test('hub: превью только для TikTok/YouTube', async () => {
  assert.equal(oembedUrl('http://169.254.169.254/latest'), null);
  assert.equal(oembedUrl('javascript:alert(1)'), null);
  assert.equal(oembedUrl('https://youtu.be/abc').platform, 'youtube');
  const bad = await t.request('/api/hub/preview?url=' + encodeURIComponent('https://evil.example/x'), { user: ALICE });
  assert.equal(bad.status, 400);
  const ok = await t.request('/api/hub/preview?url=' + encodeURIComponent('https://www.tiktok.com/@cat/video/1'), { user: ALICE });
  assert.equal(ok.status, 200);
  assert.equal(ok.data.title, 'Котик');
  assert.ok(ai.calls.at(-1).url.startsWith('https://www.tiktok.com/oembed?url='));
});

test('hub: автоматизации — создание, права, запуск по расписанию', async () => {
  const bad = await t.request('/api/hub/tasks', { user: ALICE, method: 'POST', body: { title: 'x', kind: 'ai', prompt: 'p', time: '25:00' } });
  assert.equal(bad.status, 400);
  const { data: task } = await t.request('/api/hub/tasks', {
    user: ALICE, method: 'POST', body: { title: 'Идеи', kind: 'ai', prompt: 'идеи', time: '09:30', days: 127, tzOffset: 180, provider: 'chatgpt' },
  });
  assert.equal(task.time, '09:30');
  assert.equal(task.provider, 'chatgpt');
  assert.equal((await t.request(`/api/hub/tasks/${task.id}`, { user: BOB, method: 'POST', body: { enabled: false } })).status, 404);

  // ручной запуск — сообщение уходит пользователю в Telegram
  const run = await t.request(`/api/hub/tasks/${task.id}/run`, { user: ALICE, method: 'POST' });
  assert.equal(run.status, 200);
  const msg = t.tg.calls.filter((c) => c.method === 'sendMessage').at(-1);
  assert.equal(msg.params.chat_id, ALICE.id);
  assert.match(msg.params.text, /Идеи[\s\S]*gpt:идеи/);

  // планировщик: делаем задачу «просроченной» и проверяем, что запустится ровно один раз
  t.db.prepare('UPDATE hub_tasks SET hour = 0, minute = 0, last_run_date = NULL WHERE id = ?').run(task.id);
  const before = t.tg.calls.length;
  await t.app.ctx.hubTick();
  await new Promise((r) => setTimeout(r, 50));
  await t.app.ctx.hubTick();
  await new Promise((r) => setTimeout(r, 50));
  assert.equal(t.tg.calls.filter((c, i) => i >= before && c.method === 'sendMessage').length, 1);

  const off = await t.request(`/api/hub/tasks/${task.id}`, { user: ALICE, method: 'POST', body: { enabled: false } });
  assert.equal(off.data.enabled, 0);
  assert.equal((await t.request(`/api/hub/tasks/${task.id}`, { user: ALICE, method: 'DELETE' })).status, 200);
});

test('hub: isDue учитывает часовой пояс и дни недели', () => {
  // 2026-11-26 — четверг; 06:30 UTC = 09:30 по Москве (+180)
  const now = Date.UTC(2026, 10, 26, 6, 30);
  assert.deepEqual(localNow(180, now), { date: '2026-11-26', minutes: 570, weekday: 3 });
  const base = { enabled: 1, hour: 9, minute: 30, tz_offset: 180, days: 127, last_run_date: null };
  assert.equal(isDue(base, now), true);
  assert.equal(isDue({ ...base, last_run_date: '2026-11-26' }, now), false);
  assert.equal(isDue({ ...base, minute: 31 }, now), false);
  assert.equal(isDue({ ...base, days: 1 }, now), false, 'только по понедельникам');
  assert.equal(isDue({ ...base, enabled: 0 }, now), false);
});

test('hub: провайдеры — модели из окружения, отказ Claude', async () => {
  const cfg = readAiConfig({ ANTHROPIC_API_KEY: 'k', CLAUDE_MODEL: 'claude-sonnet-5-5' });
  assert.deepEqual(cfg, { claude: { key: 'k', model: 'claude-sonnet-5-5' } });
  const refusal = async () => ({ ok: true, status: 200, json: async () => ({ stop_reason: 'refusal', content: [] }) });
  await assert.rejects(complete({ provider: 'claude', cfg: cfg.claude, messages: [{ role: 'user', content: 'x' }], fetchImpl: refusal }), /отказался/);
  const unauthorized = async () => ({ ok: false, status: 401, json: async () => ({}) });
  await assert.rejects(complete({ provider: 'claude', cfg: cfg.claude, messages: [], fetchImpl: unauthorized }), /API-ключ/);
});

test('hub: без ключей ИИ — понятная ошибка, напоминания работают', async () => {
  const t2 = await startTestApp();
  try {
    const r = await t2.request('/api/hub/ask', { method: 'POST', body: { text: 'hi' } });
    assert.equal(r.status, 503);
    const task = await t2.request('/api/hub/tasks', { method: 'POST', body: { title: 'Видео', kind: 'remind', prompt: 'Выложи ролик', time: '19:00' } });
    assert.equal(task.status, 200);
    assert.equal(task.data.provider, null);
  } finally { await t2.close(); }
});
