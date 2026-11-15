import { fileURLToPath } from 'node:url';
import { createServer } from 'node:http';
import { readFile } from 'node:fs/promises';
import { join, extname } from 'node:path';
import { authMiddleware } from './auth.js';

export class Router {
  constructor() {
    this.routes = { GET: [], POST: [], PUT: [], DELETE: [], PATCH: [] };
  }

  add(method, path, handler) {
    const keys = [];
    const regexPath = path.replace(/:([^/]+)/g, (_, key) => {
      keys.push(key);
      return '([^/]+)';
    });
    this.routes[method].push({ regex: new RegExp(`^${regexPath}$`), keys, handler });
  }

  get(path, handler) { this.add('GET', path, handler); }
  post(path, handler) { this.add('POST', path, handler); }
  put(path, handler) { this.add('PUT', path, handler); }
  delete(path, handler) { this.add('DELETE', path, handler); }
  patch(path, handler) { this.add('PATCH', path, handler); }

  async handle(req, ctx) {
    const url = new URL(req.url, `http://localhost`);
    const method = req.method;
    const route = this.routes[method]?.find((r) => r.regex.test(url.pathname));
    if (!route) return null;

    const match = url.pathname.match(route.regex);
    const params = {};
    route.keys.forEach((key, i) => { params[key] = match[i + 1]; });

    let body = null;
    if (['POST', 'PUT', 'PATCH'].includes(method)) {
      const text = await new Promise((resolve) => {
        let data = '';
        req.on('data', (c) => (data += c));
        req.on('end', () => resolve(data));
      });
      if (text) body = JSON.parse(text);
    }

    const query = Object.fromEntries(url.searchParams);
    const user = authMiddleware(req);

    return route.handler({ ...ctx, params, query, body, user, req });
  }
}

const MIME = {
  '.html': 'text/html',
  '.js': 'application/javascript',
  '.css': 'text/css',
  '.json': 'application/json',
  '.png': 'image/png',
  '.jpg': 'image/jpeg',
  '.svg': 'image/svg+xml',
  '.ico': 'image/x-icon',
};

export function createHttpServer(router, ctx, webappDir) {
  const server = createServer(async (req, res) => {
    const url = new URL(req.url, `http://localhost`);
    res.setHeader('Access-Control-Allow-Origin', '*');
    res.setHeader('Access-Control-Allow-Methods', 'GET,POST,PUT,DELETE,OPTIONS');
    res.setHeader('Access-Control-Allow-Headers', 'Content-Type,Authorization');
    if (req.method === 'OPTIONS') return void res.writeHead(204).end();

    if (url.pathname.startsWith('/api/')) {
      try {
        const result = await router.handle(req, ctx);
        if (result === null) return void res.writeHead(404).end('Not found');
        res.writeHead(200, { 'content-type': 'application/json' }).end(JSON.stringify(result));
      } catch (e) {
        const status = e.status || 500;
        res.writeHead(status, { 'content-type': 'application/json' }).end(JSON.stringify({ error: e.message }));
      }
      return;
    }

    // Статика
    let filePath = join(webappDir, url.pathname === '/' ? 'index.html' : url.pathname.slice(1));
    try {
      const content = await readFile(filePath);
      res.writeHead(200, { 'content-type': MIME[extname(filePath)] || 'application/octet-stream' }).end(content);
    } catch {
      if (url.pathname !== '/' && !url.pathname.startsWith('/api/')) {
        try {
          const content = await readFile(join(webappDir, 'index.html'));
          res.writeHead(200, { 'content-type': 'text/html' }).end(content);
        } catch {
          res.writeHead(404).end('Not found');
        }
      } else {
        res.writeHead(404).end('Not found');
      }
    }
  });
  return server;
}