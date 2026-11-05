#!/usr/bin/env bash
# Запускает все боты из bots.conf в фоне. У каждого свой надзиратель:
# отдельный лог, ровно один экземпляр, автоперезапуск при падении.
source "$(dirname "${BASH_SOURCE[0]}")/common.sh"

command -v "$PYTHON" >/dev/null || { err "Не найден $PYTHON. Установите: pkg install python"; exit 1; }
# Не даём Android усыпить Termux, иначе боты замирают при выключенном экране
command -v termux-wake-lock >/dev/null && termux-wake-lock

echo "Проверка…"
bash "$HERE/lib/check.sh"
echo

for line in "${BOTS[@]}"; do
  parse_bot "$line"
  [ -z "$NAME" ] && continue
  if [ ! -f "$FILE" ]; then
    err "$NAME: файл не найден: $FILE — исправьте путь в $CONF"
    continue
  fi
  sup="$(pid_of "$NAME" supervisor.pid)"
  if alive "$sup"; then
    ok "$NAME уже запущен (PID надзирателя $sup), второй экземпляр не создаю"
    continue
  fi
  # Копия, запущенная вручную (python startegs_v56.py), даст Conflict — останавливаем её
  strays=$(find_script_procs "$FILE")
  if [ -n "$strays" ]; then
    warn "$NAME: уже работает процесс, запущенный вручную (PID $(echo $strays)) — останавливаю, чтобы не было Conflict"
    kill $strays 2>/dev/null; sleep 2; kill -9 $strays 2>/dev/null
  fi
  clear_state "$NAME"
  nohup bash "$HERE/lib/supervise.sh" "$NAME" "$FILE" >/dev/null 2>&1 &
  ok "$NAME запущен → лог: $LOG_DIR/$NAME.log"
done

sleep 3
echo
bash "$HERE/status.sh"
echo
echo "Логи:      tail -f $LOG_DIR/<имя>.log"
echo "Статус:    bash $HERE/status.sh"
echo "Остановка: bash $HERE/stop_all.sh   (один бот: bash $HERE/stop_all.sh <имя>)"
