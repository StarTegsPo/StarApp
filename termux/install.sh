#!/usr/bin/env bash
# Ставит скрипты запуска в ~/startegs-run. Уже настроенный bots.conf не перезаписывается.
set -e
DEST="$HOME/startegs-run"
RAW="https://raw.githubusercontent.com/StarTegsPo/StarApp/claude/telegram-mod-services-v5haf6/termux"
mkdir -p "$DEST/lib"
for f in common.sh start_all.sh stop_all.sh status.sh lib/supervise.sh lib/check.sh; do
  curl -fsSL "$RAW/$f" -o "$DEST/$f"
done
[ -f "$DEST/bots.conf" ] || curl -fsSL "$RAW/bots.conf" -o "$DEST/bots.conf"
chmod +x "$DEST"/*.sh
echo "Готово: $DEST"
echo "Проверьте пути к ботам 3 и 4 в $DEST/bots.conf (nano $DEST/bots.conf)"
