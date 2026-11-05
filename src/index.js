import { loadEnvFile, readConfig } from './config.js';
import { createApp } from './app.js';

loadEnvFile();
const app = createApp(readConfig());
await app.start();
