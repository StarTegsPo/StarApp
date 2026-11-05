#!/usr/bin/env bash
# Показывает, какие боты работают
source "$(dirname "${BASH_SOURCE[0]}")/common.sh"
echo "БОТ             СОСТОЯНИЕ   PID     ПОСЛЕДНЯЯ СТРОКА ЛОГА"
for line in "${BOTS[@]}"; do
  parse_bot "$line"
  [ -z "$NAME" ] && continue
  sup="$(pid_of "$NAME" supervisor.pid)"; bot="$(pid_of "$NAME" pid)"
  if alive "$bot"; then st="${c_grn}работает   ${c_off}"
  elif alive "$sup"; then st="${c_yel}перезапуск ${c_off}"; bot="-"
  else st="${c_red}остановлен ${c_off}"; bot="-"
  fi
  last=$(tail -n 1 "$LOG_DIR/$NAME.log" 2>/dev/null | cut -c1-70)
  printf '%-15s %s %-7s %s\n' "$NAME" "$st" "$bot" "$last"
done
