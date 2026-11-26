/**
 * ИИ-провайдеры хаба. Без зависимостей: прямые HTTP-запросы через fetch (как и весь проект).
 * Ключи берутся из окружения сервера (см. .env.example) — пользователю ничего настраивать не нужно.
 * Провайдер виден в приложении, только если для него задан ключ.
 *
 * Формат сообщений внутри хаба: [{ role: 'user' | 'assistant', content: string }].
 */

const openAiCompatible = (url) => ({
  kind: 'openai',
  url,
});

export const PROVIDERS = [
  { id: 'claude', title: 'Claude', icon: '🧡', vendor: 'Anthropic', env: 'ANTHROPIC_API_KEY', model: 'claude-opus-5-5', kind: 'anthropic' },
  { id: 'chatgpt', title: 'ChatGPT', icon: '🟢', vendor: 'OpenAI', env: 'OPENAI_API_KEY', model: 'gpt-5-mini', ...openAiCompatible('https://api.openai.com/v1/chat/completions') },
  { id: 'gemini', title: 'Gemini', icon: '🔷', vendor: 'Google', env: 'GEMINI_API_KEY', model: 'gemini-2.5-flash', kind: 'gemini' },
  { id: 'deepseek', title: 'DeepSeek', icon: '🐋', vendor: 'DeepSeek', env: 'DEEPSEEK_API_KEY', model: 'deepseek-chat', ...openAiCompatible('https://api.deepseek.com/chat/completions') },
  { id: 'grok', title: 'Grok', icon: '⚫', vendor: 'xAI', env: 'XAI_API_KEY', model: 'grok-4', ...openAiCompatible('https://api.x.ai/v1/chat/completions') },
  { id: 'mistral', title: 'Mistral', icon: '🟠', vendor: 'Mistral AI', env: 'MISTRAL_API_KEY', model: 'mistral-large-latest', ...openAiCompatible('https://api.mistral.ai/v1/chat/completions') },
  { id: 'openrouter', title: 'OpenRouter', icon: '🌐', vendor: 'OpenRouter (сотни моделей)', env: 'OPENROUTER_API_KEY', model: 'openrouter/auto', ...openAiCompatible('https://openrouter.ai/api/v1/chat/completions') },
];

/**
 * Какие провайдеры включены: { id: { key, model } }.
 * Модель можно переопределить переменной <ID>_MODEL, например CLAUDE_MODEL=claude-sonnet-5-5.
 */
export function readAiConfig(env = process.env) {
  const out = {};
  for (const p of PROVIDERS) {
    const key = env[p.env];
    if (key) out[p.id] = { key, model: env[`${p.id.toUpperCase()}_MODEL`] || p.model };
  }
  return out;
}

export class AiError extends Error {
  constructor(message, status = 502) {
    super(message);
    this.status = status;
  }
}

async function postJson(fetchImpl, url, headers, body, timeoutMs) {
  let res;
  try {
    res = await fetchImpl(url, {
      method: 'POST',
      headers: { 'content-type': 'application/json', ...headers },
      body: JSON.stringify(body),
      signal: AbortSignal.timeout(timeoutMs),
    });
  } catch (e) {
    throw new AiError(e.name === 'TimeoutError' ? 'ИИ не ответил вовремя, попробуйте ещё раз' : 'Сервис ИИ недоступен');
  }
  const data = await res.json().catch(() => ({}));
  if (!res.ok) {
    const msg = data?.error?.message || data?.message || `HTTP ${res.status}`;
    if (res.status === 401 || res.status === 403) throw new AiError('Неверный API-ключ провайдера — проверьте настройки сервера');
    if (res.status === 429) throw new AiError('Провайдер ограничил частоту запросов, попробуйте чуть позже', 429);
    throw new AiError(`Ошибка провайдера: ${String(msg).slice(0, 200)}`);
  }
  return data;
}

/**
 * Отправляет диалог провайдеру и возвращает текст ответа.
 * @param {object} opts { provider, cfg: {key, model}, system, messages, fetchImpl, timeoutMs }
 */
export async function complete({ provider, cfg, system, messages, fetchImpl = fetch, timeoutMs = 120_000 }) {
  const p = PROVIDERS.find((x) => x.id === provider);
  if (!p) throw new AiError('Неизвестный ИИ', 400);
  if (!cfg?.key) throw new AiError(`${p.title} не подключён на сервере`, 400);

  if (p.kind === 'anthropic') {
    const data = await postJson(
      fetchImpl,
      'https://api.anthropic.com/v1/messages',
      { 'x-api-key': cfg.key, 'anthropic-version': '2023-06-01', 'anthropic-beta': 'server-side-fallback-2026-07-01' },
      {
        model: cfg.model,
        max_tokens: 16000,
        ...(system ? { system } : {}),
        messages,
        // при отказе по политике безопасности API сам повторит запрос на подходящей модели
        fallbacks: 'default',
      },
      timeoutMs,
    );
    if (data.stop_reason === 'refusal') throw new AiError('Claude отказался отвечать на этот запрос', 422);
    const text = (data.content || []).filter((b) => b.type === 'text').map((b) => b.text).join('').trim();
    if (!text) throw new AiError('Пустой ответ от Claude');
    return text;
  }

  if (p.kind === 'gemini') {
    const url = `https://generativelanguage.googleapis.com/v1beta/models/${encodeURIComponent(cfg.model)}:generateContent`;
    const data = await postJson(
      fetchImpl,
      url,
      { 'x-goog-api-key': cfg.key },
      {
        ...(system ? { systemInstruction: { parts: [{ text: system }] } } : {}),
        contents: messages.map((m) => ({ role: m.role === 'assistant' ? 'model' : 'user', parts: [{ text: m.content }] })),
      },
      timeoutMs,
    );
    const text = (data.candidates?.[0]?.content?.parts || []).map((x) => x.text || '').join('').trim();
    if (!text) throw new AiError('Gemini не вернул ответ (возможно, сработал фильтр безопасности)', 422);
    return text;
  }

  // OpenAI-совместимые: ChatGPT, DeepSeek, Grok, Mistral, OpenRouter
  const data = await postJson(
    fetchImpl,
    p.url,
    { authorization: `Bearer ${cfg.key}` },
    { model: cfg.model, messages: [...(system ? [{ role: 'system', content: system }] : []), ...messages] },
    timeoutMs,
  );
  const text = String(data.choices?.[0]?.message?.content ?? '').trim();
  if (!text) throw new AiError(`Пустой ответ от ${p.title}`);
  return text;
}
