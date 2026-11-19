// Запуск сервера Компаса: npm run kompas
import { createKompas } from './app.js';

const port = Number(process.env.KOMPAS_PORT || 3100);
const kompas = createKompas({
  dbPath: process.env.KOMPAS_DB_PATH || './data/kompas.db',
  filesDir: process.env.KOMPAS_FILES_DIR || './data/kompas-files',
});
const actual = await kompas.listen(port, process.env.KOMPAS_HOST);
console.log(`🧭 Компас запущен: http://localhost:${actual}`);

for (const sig of ['SIGINT', 'SIGTERM']) {
  process.on(sig, async () => {
    await kompas.close();
    process.exit(0);
  });
}
