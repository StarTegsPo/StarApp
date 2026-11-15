const defaults = {
  port: 3000,
  dbPath: 'data/whitegram.sqlite',
  adminIds: [],
  botToken: '',
  publicUrl: 'http://localhost:3000',
};

let cached = null;

export function readConfig() {
  if (cached) return cached;
  // Node 22.13+ has process.loadEnvFile()
  if (typeof process.loadEnvFile === 'function') {
    try { process.loadEnvFile(); } catch {}
  }
  cached = {
    port: Number(process.env.PORT) || defaults.port,
    dbPath: process.env.DB_PATH || defaults.dbPath,
    adminIds: (process.env.ADMIN_IDS || '').split(',').map((s) => Number(s.trim())).filter(Boolean),
    botToken: process.env.BOT_TOKEN || defaults.botToken,
    publicUrl: process.env.PUBLIC_URL || defaults.publicUrl,
  };
  return cached;
}