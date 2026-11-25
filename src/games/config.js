/** Настройки игрового бота StarGames (переменные с префиксом GAMES_, чтобы жить рядом со StarApp). */
export function readGamesConfig(env = process.env) {
  return {
    botToken: env.GAMES_BOT_TOKEN || '',
    publicUrl: (env.GAMES_PUBLIC_URL || '').replace(/\/$/, ''),
    port: Number(env.GAMES_PORT || 3001),
    dbPath: env.GAMES_DB_PATH || './data/stargames.db',
    devUserId: env.NODE_ENV !== 'production' && env.DEV_USER_ID ? Number(env.DEV_USER_ID) : null,
  };
}
