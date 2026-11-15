# ⚪ WhiteGram — профиль и баланс в Telegram

Минималистичный бот + Telegram Mini App для управления профилем и балансом с пополнением через Telegram Stars.

## Возможности

| Функция | Описание |
|---|---|
| 👤 **Профиль** | Аватар, имя, юзернейм, дата регистрации |
| ⭐ **Баланс** | Текущий баланс, общая сумма пополнений и трат |
| 💳 **Пополнение** | Через Telegram Stars (пресеты 50/100/250/500/1000 или своя сумма) |
| 📋 **История** | Список всех операций с датами и суммами |
| 🤖 **Бот-команды** | `/start`, `/balance`, `/help` |

## Запуск

```bash
cd whitegram
cp .env.example .env    # заполните BOT_TOKEN, PUBLIC_URL, ADMIN_IDS
npm install
npm start               # бот (long polling) + HTTP-сервер с API и Mini App
npm test
```

### Переменные окружения

| Переменная | Описание |
|---|---|
| `BOT_TOKEN` | Токен бота от @BotFather |
| `PUBLIC_URL` | HTTPS-адрес Mini App (для локальной разработки — ngrok/cloudflared) |
| `ADMIN_IDS` | Ваши Telegram ID через запятую (пока без админ-функций) |
| `PORT` | Порт HTTP-сервера (по умолчанию 3000) |
| `DB_PATH` | Путь к SQLite базе (по умолчанию `data/whitegram.sqlite`) |

## Структура

```
whitegram/
├── src/
│   ├── index.js           # точка входа
│   ├── app.js             # сборка: бот, платежи Stars, API, сервисы
│   ├── config.js          # конфигурация из .env
│   ├── db.js              # SQLite, пользователи, баланс, транзакции
│   ├── auth.js            # проверка подписи initData Mini App
│   ├── http.js            # HTTP-сервер, роутер, статика
│   ├── telegram.js        # минимальный клиент Bot API
│   └── services/
│       ├── index.js       # реестр сервисов
│       └── profile/       # профиль и баланс
├── webapp/
│   ├── index.html         # Mini App входная точка
│   ├── app.js             # ядро Mini App (авторизация, API, навигация, UI)
│   ├── style.css          # стили в фирменном WhiteGram стиле
│   ├── theme.js           # палитры (белый, серебро, графит, жемчуг, иней, пепел) и эффекты
│   └── services/
│       └── profile.js     # экран профиля
└── test/                  # тесты
```

## Стиль WhiteGram

- **Палитры**: Белый (по умолчанию), Серебро, Графит, Жемчуг, Иней, Пепел
- **Сезонные эффекты**: Выкл / Авто / ❄️ Снег / 🍂 Листья / 🌸 Лепестки / ✨ Блики
- **Авто**: по сезону (зима — снег, весна — лепестки, лето — блики, осень — листья)
- Настройки хранятся в `localStorage` и применяются при старте

## API

Все маршруты требуют авторизации через `initData` (заголовок `Authorization: tma <initData>`).

| Метод | Путь | Описание |
|---|---|---|
| GET | `/api/me` | Текущий пользователь, баланс, список сервисов |
| GET | `/api/profile` | Профиль, баланс, итоги, история операций |
| POST | `/api/profile/topup` | Создать инвойс на пополнение (`{ amount }`) |

## Команды бота

- `/start` — главное меню с кнопкой открытия Mini App
- `/balance` — показать баланс в чате
- `/help` — помощь

## Технологии

- Node.js 22.13+ (встроенные `node:sqlite`, `fetch`, `node:test`)
- Telegram Bot API (long polling)
- Telegram Mini App (WebApp API)
- Telegram Stars платежи (XTR)
- Чистый JS в Mini App (без сборщиков)