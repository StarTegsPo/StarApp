// HTTP API + WebSocket + раздача веб-клиента Компаса.
import { createServer } from 'node:http';
import { randomBytes } from 'node:crypto';
import { readFile, writeFile, unlink, mkdir, stat } from 'node:fs/promises';
import { join, extname, normalize, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { openDb } from './db.js';
import { Messenger, HttpError } from './messenger.js';
import { acceptUpgrade } from './ws.js';

const HERE = fileURLToPath(new URL('.', import.meta.url));
export const WEB_ROOT = resolve(HERE, '../app/www');

const MIME = {
  '.html': 'text/html; charset=utf-8',
  '.js': 'text/javascript; charset=utf-8',
  '.css': 'text/css; charset=utf-8',
  '.json': 'application/json; charset=utf-8',
  '.webmanifest': 'application/manifest+json',
  '.svg': 'image/svg+xml',
  '.png': 'image/png',
  '.jpg': 'image/jpeg',
  '.gif': 'image/gif',
  '.webp': 'image/webp',
  '.ico': 'image/x-icon',
};
const IMAGE_TYPES = { 'image/jpeg': 'jpg', 'image/png': 'png', 'image/gif': 'gif', 'image/webp': 'webp' };
const MAX_JSON = 64 * 1024;
const MAX_UPLOAD = 8 * 1024 * 1024;

export function createKompas({
  dbPath = './data/kompas.db',
  filesDir = './data/kompas-files',
  webRoot = WEB_ROOT,
  now,
} = {}) {
  const db = openDb(dbPath);
  const app = new Messenger(db, { now });
  const authLimiter = new RateLimiter(20, 10 * 60 * 1000);

  const routes = [];
  const route = (method, pattern, handler, { auth = true } = {}) => {
    const keys = [];
    const re = new RegExp('^' + pattern.replace(/:(\w+)/g, (_, k) => (keys.push(k), '([^/]+)')) + '$');
    routes.push({ method, re, keys, handler, auth });
  };

  // ---- авторизация ----
  route('POST', '/api/register', ({ body, req }) => {
    authLimiter.hit(ip(req));
    return app.register(body, req.headers['user-agent']);
  }, { auth: false });
  route('POST', '/api/login', ({ body, req }) => {
    authLimiter.hit(ip(req));
    return app.login(body, req.headers['user-agent']);
  }, { auth: false });
  route('POST', '/api/logout', ({ token }) => (app.logout(token), { ok: true }));

  // ---- профиль и пользователи ----
  route('GET', '/api/me', ({ uid }) => app.me(uid));
  route('PATCH', '/api/me', ({ uid, body }) => app.updateMe(uid, body));
  route('POST', '/api/me/password', ({ uid, body }) => (app.changePassword(uid, body), { ok: true }));
  route('GET', '/api/users/search', ({ uid, query }) => app.searchUsers(uid, query.get('q')));
  route('GET', '/api/users/:id', ({ params }) => app.getUser(params.id));

  // ---- чаты ----
  route('GET', '/api/chats', ({ uid }) => app.listChats(uid));
  route('POST', '/api/chats/direct', ({ uid, body }) => app.openDirect(uid, body.userId));
  route('POST', '/api/chats/group', ({ uid, body }) => app.createGroup(uid, body));
  route('GET', '/api/chats/:id', ({ uid, params }) => app.getChat(uid, params.id));
  route('PATCH', '/api/chats/:id', ({ uid, params, body }) => app.renameGroup(uid, params.id, body.title));
  route('POST', '/api/chats/:id/members', ({ uid, params, body }) => app.addMembers(uid, params.id, body.userIds));
  route('POST', '/api/chats/:id/leave', ({ uid, params }) => (app.leaveChat(uid, params.id), { ok: true }));
  route('POST', '/api/chats/:id/read', ({ uid, params, body }) => app.markRead(uid, params.id, body.messageId));

  // ---- сообщения ----
  route('GET', '/api/chats/:id/messages', ({ uid, params, query }) =>
    app.listMessages(uid, params.id, { before: query.get('before'), limit: query.get('limit') }));
  route('POST', '/api/chats/:id/messages', ({ uid, params, body }) => app.sendMessage(uid, params.id, body));
  route('PATCH', '/api/messages/:id', ({ uid, params, body }) => app.editMessage(uid, params.id, body.text));
  route('DELETE', '/api/messages/:id', async ({ uid, params }) => {
    const file = app.deleteMessage(uid, params.id);
    if (file) await unlink(join(filesDir, file)).catch(() => {});
    return { ok: true };
  });

  // ---- файлы ----
  route('POST', '/api/upload', async ({ req }) => {
    const type = String(req.headers['content-type'] || '').split(';')[0].trim();
    const ext = IMAGE_TYPES[type];
    if (!ext) throw new HttpError(415, 'Можно отправлять только изображения (JPEG, PNG, GIF, WebP)');
    const data = await readBody(req, MAX_UPLOAD);
    if (!data.length) throw new HttpError(400, 'Пустой файл');
    const name = `${randomBytes(16).toString('hex')}.${ext}`;
    await mkdir(filesDir, { recursive: true });
    await writeFile(join(filesDir, name), data);
    return { file: name };
  });

  async function handle(req, res) {
    const url = new URL(req.url, 'http://x');
    cors(res);
    if (req.method === 'OPTIONS') return send(res, 204);

    if (url.pathname === '/health') return json(res, 200, { ok: true });

    if (url.pathname.startsWith('/files/')) return serveFile(req, res, url);

    if (url.pathname.startsWith('/api/')) {
      try {
        const r = routes.find((x) => x.method === req.method && x.re.test(url.pathname));
        if (!r) throw new HttpError(404, 'Не найдено');
        const m = url.pathname.match(r.re);
        const params = Object.fromEntries(r.keys.map((k, i) => [k, decodeURIComponent(m[i + 1])]));
        const token = bearer(req);
        const uid = app.userByToken(token);
        if (r.auth && !uid) throw new HttpError(401, 'Требуется вход');
        let body = {};
        if (req.method !== 'GET' && url.pathname !== '/api/upload') {
          const raw = await readBody(req, MAX_JSON);
          if (raw.length) {
            try { body = JSON.parse(raw.toString('utf8')); } catch { throw new HttpError(400, 'Некорректный JSON'); }
            if (!body || typeof body !== 'object') body = {};
          }
        }
        const result = await r.handler({ req, uid, token, params, body, query: url.searchParams });
        return json(res, 200, result);
      } catch (err) {
        if (err instanceof HttpError) return json(res, err.status, { error: err.message });
        console.error(err);
        return json(res, 500, { error: 'Внутренняя ошибка сервера' });
      }
    }

    return serveStatic(res, url.pathname);
  }

  async function serveFile(req, res, url) {
    const name = url.pathname.slice('/files/'.length);
    if (!/^[a-f0-9]{32}\.(jpg|png|gif|webp)$/.test(name)) return json(res, 404, { error: 'Не найдено' });
    const uid = app.userByToken(url.searchParams.get('t') || bearer(req));
    if (!uid || !app.canSeeFile(uid, name)) return json(res, 404, { error: 'Не найдено' });
    try {
      const data = await readFile(join(filesDir, name));
      res.writeHead(200, {
        'Content-Type': MIME[extname(name)],
        'Cache-Control': 'private, max-age=31536000, immutable',
        'X-Content-Type-Options': 'nosniff',
      });
      res.end(data);
    } catch {
      json(res, 404, { error: 'Не найдено' });
    }
  }

  async function serveStatic(res, pathname) {
    let rel = normalize(decodeURIComponent(pathname)).replace(/^([/\\])+/, '');
    if (!rel || rel.endsWith('/')) rel += 'index.html';
    const full = resolve(webRoot, rel);
    if (!full.startsWith(webRoot)) return send(res, 403);
    let path = full;
    try {
      if (!(await stat(path)).isFile()) throw new Error();
    } catch {
      path = join(webRoot, 'index.html'); // SPA-фолбэк
    }
    try {
      const data = await readFile(path);
      const ext = extname(path);
      res.writeHead(200, {
        'Content-Type': MIME[ext] || 'application/octet-stream',
        'Cache-Control': ext === '.html' || path.endsWith('sw.js') ? 'no-cache' : 'public, max-age=3600',
      });
      res.end(data);
    } catch {
      send(res, 404);
    }
  }

  const server = createServer((req, res) => {
    handle(req, res).catch((err) => {
      console.error(err);
      if (!res.headersSent) json(res, 500, { error: 'Внутренняя ошибка сервера' });
    });
  });

  server.on('upgrade', (req, socket) => {
    const url = new URL(req.url, 'http://x');
    if (url.pathname !== '/ws') return socket.destroy();
    const uid = app.userByToken(url.searchParams.get('token'));
    if (!uid) {
      socket.end('HTTP/1.1 401 Unauthorized\r\n\r\n');
      return;
    }
    const conn = acceptUpgrade(req, socket);
    if (!conn) return;
    app.connect(uid, conn);
    conn.send({ type: 'hello', userId: uid });
    conn.on('message', (msg) => {
      if (msg?.type === 'typing') app.typing(uid, Number(msg.chatId));
      else if (msg?.type === 'ping') conn.send({ type: 'pong' });
    });
    conn.on('close', () => app.disconnect(uid, conn));
  });

  // Пинг клиентов: обрывает «зависшие» соединения
  const heartbeat = setInterval(() => {
    for (const set of app.online.values()) {
      for (const conn of set) {
        if (!conn.alive) { conn.close(1001); continue; }
        conn.alive = false;
        conn.ping();
      }
    }
  }, 30_000);
  heartbeat.unref();

  return {
    app,
    db,
    server,
    listen(port = 3100, host) {
      return new Promise((ok) => server.listen(port, host, () => ok(server.address().port)));
    },
    async close() {
      clearInterval(heartbeat);
      for (const set of app.online.values()) for (const c of set) c.close(1001);
      await new Promise((ok) => server.close(ok));
      db.close();
    },
  };
}

class RateLimiter {
  constructor(max, windowMs) {
    this.max = max;
    this.windowMs = windowMs;
    this.hits = new Map();
  }
  hit(key) {
    const t = Date.now();
    const list = (this.hits.get(key) || []).filter((x) => t - x < this.windowMs);
    list.push(t);
    this.hits.set(key, list);
    if (this.hits.size > 10_000) this.hits.clear();
    if (list.length > this.max) throw new HttpError(429, 'Слишком много попыток, попробуйте позже');
  }
}

function ip(req) {
  return req.socket.remoteAddress || '';
}

function bearer(req) {
  const h = String(req.headers.authorization || '');
  return h.startsWith('Bearer ') ? h.slice(7) : null;
}

function cors(res) {
  // Нативные приложения (capacitor://localhost, https://localhost) ходят на сервер с другого origin.
  // Авторизация — по токену в заголовке, cookies не используются, поэтому * безопасен.
  res.setHeader('Access-Control-Allow-Origin', '*');
  res.setHeader('Access-Control-Allow-Headers', 'Authorization, Content-Type');
  res.setHeader('Access-Control-Allow-Methods', 'GET, POST, PATCH, DELETE, OPTIONS');
  res.setHeader('Access-Control-Max-Age', '86400');
}

function send(res, status) {
  res.writeHead(status);
  res.end();
}

function json(res, status, data) {
  const body = JSON.stringify(data);
  res.writeHead(status, { 'Content-Type': 'application/json; charset=utf-8', 'Cache-Control': 'no-store' });
  res.end(body);
}

function readBody(req, limit) {
  return new Promise((ok, fail) => {
    const chunks = [];
    let size = 0;
    req.on('data', (c) => {
      size += c.length;
      if (size > limit) {
        fail(new HttpError(413, 'Слишком большой запрос'));
        req.destroy();
        return;
      }
      chunks.push(c);
    });
    req.on('end', () => ok(Buffer.concat(chunks)));
    req.on('error', fail);
  });
}
