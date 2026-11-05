# ⭐ StarApp — бизнес-помощник в Telegram

Бот + Telegram Mini App, внутри которого собраны все сервисы:

| Сервис | Что делает |
|---|---|
| 📊 **Аналитика** | Каналы (подписчики, рост, приход/отток, посты, форматы, активные часы), боты (DAU, новые пользователи, события, конверсия в покупку), покупки (выручка, заказы, средний чек, топ товаров) |
| 🛍 **Магазин** | Каталог услуг, оплата в Telegram Stars или с внутреннего баланса, история покупок, управление товарами для админов |
| 👤 **Профиль** | Баланс, пополнение через Stars, история операций |
| 💬 **Поддержка** | Тикеты из приложения или `/support`, уведомления админам, ответ реплаем прямо в Telegram |

Внешних зависимостей нет: Node.js 22.13+ (встроенные `node:sqlite`, `fetch`, `node:test`), чистый JS в Mini App.

## Запуск

```bash
cp .env.example .env    # заполните BOT_TOKEN, PUBLIC_URL, ADMIN_IDS
npm start               # бот (long polling) + HTTP-сервер с API и Mini App
npm test
```

1. Создайте бота в [@BotFather](https://t.me/BotFather) и вставьте токен в `BOT_TOKEN`.
2. Mini App должен быть доступен по **HTTPS**: укажите адрес в `PUBLIC_URL`. Для локальной разработки подойдёт туннель (ngrok, cloudflared) на `PORT`.
   При старте бот сам ставит кнопку меню «StarApp» и команды.
3. `ADMIN_IDS` — ваши Telegram ID (видят продажи магазина, добавляют товары, отвечают в поддержке).
   `SUPPORT_CHAT_ID` — группа, куда падают тикеты (иначе — в личку админам).
4. Чтобы открыть Mini App в обычном браузере без Telegram, задайте `DEV_USER_ID` (не работает при `NODE_ENV=production`).

## Как собирается аналитика

- **Каналы и группы.** Добавьте бота администратором. Он запоминает, кто его добавил (это владелец статистики), раз в `CHANNEL_SNAPSHOT_MINUTES` снимает число подписчиков, записывает посты (`channel_post`) и подписки/отписки (`chat_member`).
- **Ваши боты.** В приложении: Аналитика → Боты → «Получить API-ключ». Затем отправляйте события из бота:
  ```bash
  curl -X POST https://your-domain/api/track -H 'content-type: application/json' \
    -d '{"api_key":"KEY","user_id":123,"event":"start"}'
  # покупка: "event":"purchase","amount":150
  # пакетом: {"api_key":"KEY","events":[{...},{...}]}
  ```
- **Покупки.** События `purchase` ваших ботов плюс (для админов) продажи магазина StarApp.

## Команды бота

`/start`, `/stats`, `/balance`, `/support текст`, `/help`. Для админов: `/addproduct Название | цена | описание`, `/close N`, а также ответ реплаем на тикет.

## Структура

```
src/
  index.js            точка входа
  app.js              сборка: бот, платежи Stars, API, сервисы
  telegram.js         минимальный клиент Bot API (long polling, команды, роутинг апдейтов)
  http.js             HTTP-сервер, роутер, авторизация по initData, статика
  auth.js             проверка подписи initData Mini App
  db.js               SQLite, пользователи, баланс, транзакции
  services/
    index.js          реестр сервисов и контракт
    analytics/        аналитика каналов, ботов, покупок (queries.js — SQL-отчёты)
    shop/  profile/  support/
webapp/
  index.html, app.js, style.css    ядро Mini App (тема Telegram, навигация, графики)
  services/<id>.js                  экран каждого сервиса
test/                               тесты API, платежей, поддержки, аналитики
```

### Как добавить новый сервис

1. `src/services/<id>/index.js`: объект `{ id, title, icon, order, migrations, setupBot, setupApi, onPayment, validatePayment, start }`, подробности в `src/services/index.js`.
2. Добавьте его в массив `services` в `src/services/index.js`.
3. `webapp/services/<id>.js` с `export async function render(el, app)`.

Вкладка появится в Mini App автоматически. Платежи маршрутизируются по префиксу payload `"<id>:..."`.
