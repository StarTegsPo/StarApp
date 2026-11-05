import { createServer } from 'node:http';
import { readFile } from 'node:fs/promises';
import { join, normalize, extname } from 'node:path';
import { validateInitData } from './auth.js';
import { upsertUser } from './db.js';

const MIME = {
  '.html': 'text/html; charset=utf-8',
  '.js': 'text/javascript; charset=utf-8',
  '.css': 'text/css; charset=utf-8',
  '.svg': 'image/svg+xml',
  '.png': 'image/png',
  '.json': 'application/json',
};

export class HttpError extends Error {
  constructor(status, message) {
    super(message);
    this.status = status;
  }
}

/** Маленький роутер: router.get('/api/x/:id', handler, { auth: true }) */
export class Router {
  constructor() {
    this.routes = [];
  }
  add(method, path, handler, opts = {}) {
    const keys = [];
    const re = new RegExp(
      '^' + path.replace(/\/:([a-zA-Z_]+)/g, (_, k) => (keys.push(k), '/([^/]+)')) + '/?$',
    );
    this.routes.push({ method, re, keys, handler, auth: opts.auth !== false });
  }
  get(p, h, o) { this.add('GET', p, h, o); }
  post(p, h, o) { this.add('POST', p, h, o); }
  delete(p, h, o) { this.add('DELETE', p, h, o); }
  match(method, pathname) {
    for (const r of this.routes) {
      if (r.method !== method) continue;
      const m = pathname.match(r.re);
      if (m) return { route: r, params: Object.fromEntries(r.keys.map((k, i) => [k, decodeURIComponent(m[i + 1])])) };
    }
    return null;
  }
}

async function readJson(req) {
  const chunks = [];
  let size = 0;
  for await (const c of req) {
    size += c.length;
    if (size > 1_000_000) throw new HttpError(413, 'Слишком большой запрос');
    chunks.push(c);
  }
  if (!chunks.length) return {};
  try {
    return JSON.parse(Buffer.concat(chunks).toString('utf8'));
  } catch {
    throw new HttpError(400, 'Некорректный JSON');
  }
}

/** Достаёт пользователя из заголовка Authorization: tma <initData>. */
export function authenticate(req, { config, db }) {
  const header = req.headers.authorization || '';
  let user = null;
  if (header.startsWith('tma ')) user = validateInitData(header.slice(4), config.botToken);
  if (!user && config.devUserId) user = { id: config.devUserId, first_name: 'Dev' };
  if (!user) return null;
  upsertUser(db, user);
  return user;
}

export function createHttpServer(router, ctx, staticDir) {
  return createServer(async (req, res) => {
    const url = new URL(req.url, 'http://localhost');
    const send = (status, body) => {
      res.writeHead(status, { 'content-type': 'application/json; charset=utf-8' });
      res.end(JSON.stringify(body));
    };
    try {
      if (url.pathname.startsWith('/api/')) {
        const found = router.match(req.method, url.pathname);
        if (!found) throw new HttpError(404, 'Не найдено');
        const user = found.route.auth ? authenticate(req, ctx) : null;
        if (found.route.auth && !user) throw new HttpError(401, 'Нет авторизации Telegram');
        const body = req.method === 'GET' ? {} : await readJson(req);
        const result = await found.route.handler({ ...ctx, req, user, params: found.params, query: url.searchParams, body });
        return send(200, result ?? { ok: true });
      }
      // Статика Mini App
      let rel = normalize(decodeURIComponent(url.pathname)).replace(/^([/\\])+/, '');
      if (rel.includes('..')) throw new HttpError(400, 'Bad path');
      if (!rel) rel = 'index.html';
      let file;
      try {
        file = await readFile(join(staticDir, rel));
      } catch {
        file = await readFile(join(staticDir, 'index.html'));
        rel = 'index.html';
      }
      res.writeHead(200, { 'content-type': MIME[extname(rel)] || 'application/octet-stream' });
      res.end(file);
    } catch (e) {
      const status = e.status || 500;
      if (status === 500) console.error('[http]', e);
      send(status, { error: status === 500 ? 'Внутренняя ошибка' : e.message });
    }
  });
}
