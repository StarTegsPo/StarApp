import { loadEnvFile } from '../config.js';
import { readGamesConfig } from './config.js';
import { createGamesApp } from './app.js';

loadEnvFile();
await createGamesApp(readGamesConfig()).start();
