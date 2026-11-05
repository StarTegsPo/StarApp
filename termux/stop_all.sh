#!/usr/bin/env bash
# Останавливает всех ботов, или одного: bash stop_all.sh startegsai
source "$(dirname "${BASH_SOURCE[0]}")/common.sh"

for line in "${BOTS[@]}"; do
  parse_bot "$line"
  [ -z "$NAME" ] && continue
  [ -n "$1" ] && [ "$1" != "$NAME" ] && continue
  sup="$(pid_of "$NAME" supervisor.pid)"; bot="$(pid_of "$NAME" pid)"
  if ! alive "$sup" && ! alive "$bot"; then
    echo "${c_dim}  $NAME не запущен${c_off}"
    clear_state "$NAME"; continue
  fi
  touch "$RUN_DIR/$NAME.stop"           # надзиратель больше не перезапустит бота
  alive "$sup" && kill -TERM "$sup" 2>/dev/null
  alive "$bot" && kill -TERM "$bot" 2>/dev/null
  for _ in $(seq 1 10); do alive "$sup" || alive "$bot" || break; sleep 1; done
  if alive "$bot" || alive "$sup"; then
    kill -9 "$bot" "$sup" 2>/dev/null
    warn "$NAME остановлен принудительно"
  else
    ok "$NAME остановлен"
  fi
  clear_state "$NAME"
done

# Если больше ничего не работает — отпускаем wake lock
if [ -z "$1" ] && command -v termux-wake-unlock >/dev/null; then termux-wake-unlock; fi
exit 0
