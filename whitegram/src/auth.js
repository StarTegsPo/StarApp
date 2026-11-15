import { createHmac } from 'node:crypto';
import { readConfig } from './config.js';

const config = readConfig();

export function verifyInitData(initData) {
  if (!initData) return null;
  const params = new URLSearchParams(initData);
  const hash = params.get('hash');
  params.delete('hash');
  const dataCheckString = [...params.entries()].sort(([a], [b]) => a.localeCompare(b)).map(([k, v]) => `${k}=${v}`).join('\n');
  const secret = createHmac('sha256', 'WebAppData').update(config.botToken).digest();
  const calculatedHash = createHmac('sha256', secret).update(dataCheckString).digest('hex');
  if (calculatedHash !== hash) return null;
  const authDate = Number(params.get('auth_date'));
  if (Date.now() / 1000 - authDate > 86400) return null;
  const user = params.get('user');
  return user ? JSON.parse(user) : null;
}

export function authMiddleware(req) {
  const authHeader = req.headers['authorization'] || req.headers['Authorization'] || '';
  const initData = authHeader.startsWith('tma ') ? authHeader.slice(4) : '';
  const user = verifyInitData(initData);
  if (!user) throw Object.assign(new Error('Unauthorized'), { status: 401 });
  return user;
}