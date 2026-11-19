# 🧭 Компас — мессенджер для iOS и Android

Отдельный мессенджер со своим сервером. Один и тот же клиент работает как:

- **приложение для Android** (`Kompas.apk`);
- **приложение для iPhone** (`Kompas-unsigned.ipa` или через App Store/TestFlight);
- **веб-приложение (PWA)**: открывается в любом браузере и ставится на экран «Домой» на iPhone и Android без магазинов.

## Возможности

- регистрация и вход по имени пользователя и паролю (пароли хранятся как scrypt-хэши);
- поиск людей по `@username` и имени;
- личные чаты и группы (до 200 участников): создание, переименование, добавление участников, выход;
- сообщения в реальном времени (WebSocket): ответы (reply), редактирование, удаление у всех;
- фотографии (сжимаются на телефоне до 1920 px, доступны только участникам чата);
- статусы «в сети» / «был(а) …», «печатает…», ✓ доставлено и ✓✓ прочитано, счётчики непрочитанных;
- уведомления о новых сообщениях, черновики, светлая/тёмная тема, работа на телефоне и на компьютере.

## Структура

| Путь | Что это |
|---|---|
| `kompas/server/` | сервер: HTTP API, WebSocket, SQLite (`node:sqlite`). Внешних зависимостей нет |
| `kompas/app/www/` | клиент (HTML/CSS/JS без сборщика) — его же раздаёт сервер как PWA |
| `kompas/app/` | обёртка Capacitor для нативных приложений iOS/Android |
| `kompas/test/` | тесты сервера (`npm test` в корне репозитория) |
| `.github/workflows/kompas-apps.yml` | автосборка APK и IPA, публикация в релизе `kompas-latest` |

## 1. Запуск сервера

Нужен Node.js 22.13+.

```bash
npm run kompas          # http://localhost:3100
```

Переменные окружения:

| Переменная | По умолчанию | |
|---|---|---|
| `KOMPAS_PORT` | `3100` | порт |
| `KOMPAS_HOST` | все интерфейсы | адрес для прослушивания |
| `KOMPAS_DB_PATH` | `./data/kompas.db` | база SQLite |
| `KOMPAS_FILES_DIR` | `./data/kompas-files` | загруженные фото |

Для телефонов сервер должен быть доступен из интернета **по HTTPS** (iOS не пускает приложения на http).
Проще всего — VPS + [Caddy](https://caddyserver.com), который сам получит сертификат:

```
kompas.example.ru {
    reverse_proxy localhost:3100
}
```

WebSocket (`/ws`) Caddy проксирует автоматически; для nginx добавьте заголовки `Upgrade`/`Connection`.

## 2. Как скачать приложение

После пуша в `kompas/app/**` (или вручную: Actions → «Сборка мессенджера Компас» → Run workflow)
GitHub соберёт приложения и выложит их в релиз **kompas-latest**:
`https://github.com/<владелец>/<репозиторий>/releases/tag/kompas-latest`.

Чтобы приложение сразу знало адрес вашего сервера, задайте переменную репозитория
`KOMPAS_SERVER_URL` (Settings → Secrets and variables → Actions → Variables), например `https://kompas.example.ru`.
Без неё приложение спросит адрес сервера на экране входа (его можно поменять там же).

### Android

Скачайте `Kompas.apk` на телефон и установите (разрешите установку из неизвестных источников).

Чтобы обновления ставились поверх старой версии, APK должен всегда подписываться одним ключом.
Workflow использует секреты `KOMPAS_KEYSTORE_BASE64` и `KOMPAS_KEYSTORE_PASSWORD`
(если их нет — ключ StarTegs `KEYSTORE_BASE64`/`KEYSTORE_PASSWORD`, а если нет и его — отладочный ключ). Создать ключ:

```bash
keytool -genkeypair -v -keystore kompas.keystore -alias kompas -keyalg RSA -keysize 2048 -validity 10000
base64 -w0 kompas.keystore   # → секрет KOMPAS_KEYSTORE_BASE64
```

Для Google Play соберите AAB (`./gradlew bundleRelease`) тем же ключом.

### iPhone

Apple не разрешает ставить приложения в обход App Store без подписи, поэтому есть три пути:

1. **PWA (бесплатно, сразу):** откройте адрес сервера в Safari → «Поделиться» → «На экран Домой». Компас появится как обычное приложение со своей иконкой.
2. **Sideload:** `Kompas-unsigned.ipa` из релиза устанавливается через AltStore или Sideloadly — они подписывают его вашим Apple ID (бесплатный аккаунт — на 7 дней).
3. **App Store / TestFlight:** нужен Apple Developer Program (99 $/год) и Mac:
   ```bash
   cd kompas/app
   npm ci && npx cap add ios && npm run assets && npx cap sync ios
   npx cap open ios   # в Xcode: Signing & Capabilities → ваша команда → Product → Archive
   ```

## 3. Разработка

```bash
cd kompas/app
npm ci
npx cap add android && npx cap add ios   # папки android/ и ios/ генерируются, в git не хранятся
npm run assets                           # иконки и заставка из resources/
npx cap sync
npx cap open android                     # Android Studio
npx cap open ios                         # Xcode (только macOS)
```

Веб-клиент можно править и смотреть без пересборки: `npm run kompas` и откройте http://localhost:3100.

## API

Все запросы, кроме регистрации и входа, — с заголовком `Authorization: Bearer <token>`.

| Метод | Путь | |
|---|---|---|
| POST | `/api/register` | `{ username, name, password }` → `{ token, user }` |
| POST | `/api/login` | `{ username, password }` → `{ token, user }` |
| POST | `/api/logout` | завершить сессию |
| GET / PATCH | `/api/me` | профиль / изменить `{ name, bio }` |
| POST | `/api/me/password` | `{ oldPassword, newPassword }` |
| GET | `/api/users/search?q=` | поиск людей |
| GET | `/api/chats` | список чатов с последним сообщением и непрочитанными |
| POST | `/api/chats/direct` | `{ userId }` — открыть личный чат |
| POST | `/api/chats/group` | `{ title, members: [id] }` |
| GET / PATCH | `/api/chats/:id` | чат / переименовать группу `{ title }` |
| POST | `/api/chats/:id/members` | `{ userIds: [id] }` |
| POST | `/api/chats/:id/leave` | выйти из группы |
| POST | `/api/chats/:id/read` | `{ messageId }` — отметить прочитанным |
| GET | `/api/chats/:id/messages?before=&limit=` | история (по 50) |
| POST | `/api/chats/:id/messages` | `{ text, replyTo, file }` |
| PATCH / DELETE | `/api/messages/:id` | изменить `{ text }` / удалить |
| POST | `/api/upload` | тело — изображение, `Content-Type: image/*` → `{ file }` |
| GET | `/files/:name?t=<token>` | фото (только участникам чата) |
| WS | `/ws?token=` | события `message`, `message_edit`, `message_delete`, `chat`, `chat_removed`, `read`, `typing`, `presence`, `user` |

## Ограничения текущей версии

- Уведомления приходят, пока приложение запущено (в том числе свёрнуто, пока система его не выгрузила).
  Push-уведомления при закрытом приложении требуют Firebase (Android) и APNs (iOS) — это следующий шаг.
- Сообщения шифруются при передаче (HTTPS/WSS), но хранятся на сервере в открытом виде — сквозного шифрования пока нет.
