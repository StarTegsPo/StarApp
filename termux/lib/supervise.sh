# Надзиратель одного бота: запускает его и перезапускает при падении.
# Вызывается из start_all.sh: bash lib/supervise.sh <имя> <файл>
source "$(dirname "${BASH_SOURCE[0]}")/../common.sh"
NAME="${1:?}"; FILE="${2:?}"
LOG="$LOG_DIR/$NAME.log"
STOP="$RUN_DIR/$NAME.stop"
MAX_LOG=$((5 * 1024 * 1024))   # 5 МБ, потом старый лог уходит в .log.1

echo $$ > "$RUN_DIR/$NAME.supervisor.pid"
child=""
trap 'touch "$STOP"; alive "$child" && kill -TERM "$child" 2>/dev/null' TERM INT

cd "$(dirname "$FILE")" || exit 1
delay=5
while [ ! -e "$STOP" ]; do
  if [ -f "$LOG" ] && [ "$(wc -c < "$LOG")" -gt "$MAX_LOG" ]; then mv -f "$LOG" "$LOG.1"; fi
  echo "===== $(date '+%F %T') запуск $NAME ($FILE) =====" >> "$LOG"
  started=$(date +%s)
  PYTHONUNBUFFERED=1 "$PYTHON" -u "$FILE" >> "$LOG" 2>&1 &
  child=$!
  echo "$child" > "$RUN_DIR/$NAME.pid"
  wait "$child"; code=$?
  # если wait прервал сигнал, дожидаемся настоящего завершения бота
  while alive "$child"; do wait "$child"; code=$?; done
  [ -e "$STOP" ] && break
  ran=$(( $(date +%s) - started ))
  # Проработал дольше 2 минут — случайное падение, перезапуск через 5 с.
  # Падает сразу — пауза растёт до 5 минут, чтобы не долбить Telegram.
  if [ "$ran" -gt 120 ]; then delay=5; else delay=$(( delay * 2 > 300 ? 300 : delay * 2 )); fi
  echo "===== $(date '+%F %T') $NAME завершился (код $code, работал ${ran} с), перезапуск через ${delay} с =====" >> "$LOG"
  for ((i = 0; i < delay; i++)); do [ -e "$STOP" ] && break; sleep 1; done
done
echo "===== $(date '+%F %T') $NAME остановлен =====" >> "$LOG"
clear_state "$NAME"
