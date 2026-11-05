import { readFileSync, existsSync } from 'node:fs';

/** Простейший загрузчик .env (без зависимостей). Переменные окружения имеют приоритет. */
export function loadEnvFile(path = '.env') {
  if (!existsSync(path)) return;
  for (const line of readFileSync(path, 'utf8').split(/\r?\n/)) {
    const m = line.match(/^\s*([A-Za-z_][A-Za-z0-9_]*)\s*=\s*(.*)\s*$/);
    if (!m) continue;
    let value = m[2];
    if (/^(['"]).*\1$/.test(value)) value = value.slice(1, -1);
    if (process.env[m[1]] === undefined) process.env[m[1]] = value;
  }
}

const list = (v) => (v || '').split(',').map((s) => s.trim()).filter(Boolean).map(Number);

export function readConfig(env = process.env) {
  return {
    botToken: env.BOT_TOKEN || '',
    publicUrl: (env.PUBLIC_URL || '').replace(/\/$/, ''),
    port: Number(env.PORT || 3000),
    dbPath: env.DB_PATH || './data/starapp.db',
    adminIds: list(env.ADMIN_IDS),
    supportChatId: env.SUPPORT_CHAT_ID ? Number(env.SUPPORT_CHAT_ID) : null,
    channelSnapshotMinutes: Number(env.CHANNEL_SNAPSHOT_MINUTES || 60),
    devUserId: env.NODE_ENV !== 'production' && env.DEV_USER_ID ? Number(env.DEV_USER_ID) : null,
  };
}
