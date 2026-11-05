# Запуск всех ботов StarTegs в Termux

Скрипты запускают несколько Python-ботов одновременно, и боты не мешают друг другу:

- **ровно один экземпляр** каждого бота: повторный `start_all.sh` дублей не создаёт, а копию, запущенную вручную (`python startegs_v56.py`), останавливает, чтобы не было `Conflict`;
- **автоперезапуск** при падении. Если бот падает сразу после старта, пауза растёт 10 → 20 → … → 300 с, чтобы не долбить Telegram;
- **свой лог** у каждого бота: `~/.startegs-run/logs/<имя>.log`, ротация на 5 МБ;
- каждый бот запускается **в папке своего файла**, поэтому относительные пути к базам не пересекаются;
- **проверка перед запуском**: одинаковые токены, одинаковые порты, общий файл базы, синтаксические ошибки и включённый webhook (из-за него polling не работает).

Код ботов скрипты не меняют.

## Установка и запуск

```bash
pkg install -y python curl && curl -fsSL https://raw.githubusercontent.com/StarTegsPo/StarApp/claude/telegram-mod-services-v5haf6/termux/install.sh | bash && bash ~/startegs-run/start_all.sh
```

Пути к ботам задаются в `~/startegs-run/bots.conf`.

**Токены и настройки.** Если бот берёт токен из переменной окружения (`os.getenv("BOT_TOKEN")`), положите рядом с его файлом `.env`:
```
BOT_TOKEN=123456:ABC...
```
Переменные из `.env` получает только бот из этой папки, поэтому у четырёх ботов может быть одинаковое имя переменной `BOT_TOKEN` с разными значениями. Не прописывайте `export BOT_TOKEN=...` в `~/.bashrc`: тогда все боты без своего `.env` получат один токен, и будет Conflict.

| Команда | Что делает |
|---|---|
| `bash ~/startegs-run/start_all.sh` | запустить всех (уже запущенных не трогает) |
| `bash ~/startegs-run/start_all.sh startegsshop` | запустить одного бота отдельно от остальных |
| `bash ~/startegs-run/stop_all.sh` | остановить всех |
| `bash ~/startegs-run/stop_all.sh startegsai` | остановить одного |
| `bash ~/startegs-run/status.sh` | кто работает |
| `tail -f ~/.startegs-run/logs/startegs.log` | смотреть лог |

Перезапуск после правки кода: `bash ~/startegs-run/stop_all.sh startegs && bash ~/startegs-run/start_all.sh`.

## Чтобы Android не убивал ботов

1. Настройки → Приложения → Termux → Батарея → **Без ограничений**.
2. Не смахивайте Termux из списка недавних. `start_all.sh` сам включает `termux-wake-lock`.
3. Android 12+ убивает «лишние» фоновые процессы (phantom process killer). Если боты пропадают сами по себе, отключите его один раз через ADB с компьютера:
   `adb shell "settings put global settings_enable_monitor_phantom_procs false"`
4. Автозапуск после перезагрузки телефона: установите приложение **Termux:Boot** и выполните
   `mkdir -p ~/.termux/boot && printf '#!/data/data/com.termux/files/usr/bin/sh\nbash ~/startegs-run/start_all.sh\n' > ~/.termux/boot/startegs && chmod +x ~/.termux/boot/startegs`
