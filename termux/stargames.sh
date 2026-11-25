#!/usr/bin/env bash
# StarGames (игровой бот + приложение) в Termux одной командой.
#   bash ~/stargames.sh setup <ТОКЕН>   — установить / обновить и прописать токен
#   bash ~/stargames.sh start           — запустить в фоне (бот + HTTPS-туннель для приложения)
#   bash ~/stargames.sh stop | restart | status | logs | update
APP="$HOME/StarGames"
REPO="https://github.com/StarTegsPo/StarApp"
BRANCH="${STARGAMES_BRANCH:-ccr-af53deb1-q7hoyl}"
RUN="$APP/data/run"
LOG="$APP/data/logs"
PORT=3001

c_red=$'\e[31m'; c_grn=$'\e[32m'; c_yel=$'\e[33m'; c_off=$'\e[0m'
ok()   { echo "${c_grn}✔${c_off} $*"; }
warn() { echo "${c_yel}⚠${c_off} $*"; }
die()  { echo "${c_red}✘${c_off} $*"; exit 1; }
alive() { [ -n "$1" ] && kill -0 "$1" 2>/dev/null; }

# Записывает KEY=VALUE в .env (заменяет строку, если ключ уже есть)
set_env() {
  local f="$APP/.env" k="$1" v="$2"
  touch "$f"
  if grep -q "^$k=" "$f"; then
    awk -v k="$k" -v v="$v" -F= '$1==k {print k"="v; next} {print}' "$f" > "$f.tmp" && mv -f "$f.tmp" "$f"
  else
    echo "$k=$v" >> "$f"
  fi
}
get_env() { grep "^$1=" "$APP/.env" 2>/dev/null | head -n1 | cut -d= -f2-; }

tg() { curl -s -m 15 "https://api.telegram.org/bot$(get_env GAMES_BOT_TOKEN)/$1"; }

cmd_setup() {
  local token="${1:-$(get_env GAMES_BOT_TOKEN)}"
  [[ "$token" =~ ^[0-9]{6,12}:[A-Za-z0-9_-]{30,}$ ]] || die "Нужен токен бота вида 123456:ABC...  Пример: bash ~/stargames.sh setup 123456:ABC..."

  echo "Установка пакетов…"
  pkg install -y nodejs git curl procps cloudflared >/dev/null 2>&1 || pkg install -y nodejs git curl procps || die "Не удалось установить пакеты"
  command -v cloudflared >/dev/null || warn "cloudflared не установился — бот будет работать, но без кнопки приложения"

  local major; major=$(node -p 'process.versions.node.split(".")[0]' 2>/dev/null)
  [ "${major:-0}" -ge 22 ] || die "Нужен Node.js 22+, установлен $(node -v 2>/dev/null). Попробуйте: pkg upgrade nodejs"
  node -e "require('node:sqlite')" 2>/dev/null || die "В этой сборке Node.js нет node:sqlite. Обновите: pkg upgrade nodejs"
  ok "Node.js $(node -v)"

  cmd_update quiet

  [ -f "$APP/.env" ] || touch "$APP/.env"
  set_env GAMES_BOT_TOKEN "$token"
  set_env GAMES_PORT "$PORT"
  set_env GAMES_DB_PATH "./data/stargames.db"
  chmod 600 "$APP/.env"

  local me; me=$(tg getMe)
  echo "$me" | grep -q '"ok":true' || die "Telegram не принял токен: $me"
  ok "Токен верный: @$(echo "$me" | grep -o '"username":"[^"]*"' | cut -d'"' -f4)"
  # Бот работает через polling: включённый webhook мешает получать сообщения
  tg "deleteWebhook" >/dev/null && ok "Webhook снят (если был)"
  echo
  ok "Готово. Запуск: bash ~/stargames.sh start"
}

cmd_update() {
  if [ -d "$APP/.git" ]; then
    git -C "$APP" fetch -q origin "$BRANCH" && git -C "$APP" checkout -q "$BRANCH" && git -C "$APP" reset -q --hard "origin/$BRANCH" \
      || die "Не удалось обновить $APP"
    ok "StarGames обновлён"
  else
    [ -e "$APP" ] && die "$APP уже существует и это не git-репозиторий — переименуйте папку и повторите"
    git clone -q -b "$BRANCH" "$REPO" "$APP" || die "Не удалось скачать StarGames"
    ok "StarGames скачан в $APP"
  fi
  cp -f "$APP/termux/stargames.sh" "$HOME/stargames.sh" 2>/dev/null
  if [ "$1" != quiet ] && alive "$(cat "$RUN/supervisor.pid" 2>/dev/null)"; then cmd_stop; cmd_start; fi
}

# Фоновый надзиратель: туннель + бот, перезапуск при падении любого из них
supervise() {
  cd "$APP" || exit 1
  echo $$ > "$RUN/supervisor.pid"
  local node_pid="" cf_pid="" delay=5
  trap 'touch "$RUN/stop"; alive "$node_pid" && kill "$node_pid"; alive "$cf_pid" && kill "$cf_pid"' TERM INT
  while [ ! -e "$RUN/stop" ]; do
    local started; started=$(date +%s)
    if command -v cloudflared >/dev/null; then
      : > "$LOG/tunnel.log"
      cloudflared tunnel --no-autoupdate --url "http://localhost:$PORT" >> "$LOG/tunnel.log" 2>&1 &
      cf_pid=$!
      local url=""
      for _ in $(seq 1 40); do
        url=$(grep -o 'https://[a-z0-9-]*\.trycloudflare\.com' "$LOG/tunnel.log" | head -n1)
        [ -n "$url" ] && break; sleep 1
      done
      if [ -n "$url" ]; then
        set_env GAMES_PUBLIC_URL "$url"; echo "$url" > "$RUN/url"
      else
        echo "$(date '+%F %T') туннель не выдал адрес, запускаю без приложения" >> "$LOG/bot.log"
        set_env GAMES_PUBLIC_URL ""
      fi
    fi
    echo "===== $(date '+%F %T') запуск StarGames =====" >> "$LOG/bot.log"
    node --disable-warning=ExperimentalWarning "$APP/src/games/index.js" >> "$LOG/bot.log" 2>&1 &
    node_pid=$!
    echo "$node_pid" > "$RUN/node.pid"
    # ждём, пока упадёт бот или туннель
    if [ -n "$cf_pid" ]; then wait -n "$node_pid" "$cf_pid"; else wait "$node_pid"; fi
    alive "$node_pid" && kill "$node_pid"; alive "$cf_pid" && kill "$cf_pid"
    wait 2>/dev/null
    rm -f "$RUN/node.pid" "$RUN/url"; cf_pid=""
    [ -e "$RUN/stop" ] && break
    if [ $(( $(date +%s) - started )) -gt 120 ]; then delay=5; else delay=$(( delay * 2 > 300 ? 300 : delay * 2 )); fi
    echo "===== $(date '+%F %T') StarGames или туннель остановился, перезапуск через ${delay} с =====" >> "$LOG/bot.log"
    for ((i = 0; i < delay; i++)); do [ -e "$RUN/stop" ] && break; sleep 1; done
  done
  rm -f "$RUN/supervisor.pid" "$RUN/node.pid" "$RUN/stop" "$RUN/url"
}

cmd_start() {
  [ -f "$APP/.env" ] && [ -n "$(get_env GAMES_BOT_TOKEN)" ] || die "Сначала: bash ~/stargames.sh setup <ТОКЕН>"
  mkdir -p "$RUN" "$LOG"
  if alive "$(cat "$RUN/supervisor.pid" 2>/dev/null)"; then ok "StarGames уже запущен"; cmd_status; return; fi
  # Убираем копии, запущенные вручную через npm start, — иначе будет Conflict
  pkill -f "src/games/index.js" 2>/dev/null && { warn "Остановил ранее запущенный вручную StarGames"; sleep 1; }
  rm -f "$RUN/stop"
  command -v termux-wake-lock >/dev/null && termux-wake-lock
  nohup bash "$0" __supervise >/dev/null 2>&1 &
  echo "Запускаю…"
  for _ in $(seq 1 45); do
    grep -q '\[bot\].*запущен\|polling\|Error' "$LOG/bot.log" 2>/dev/null && [ -n "$(cat "$RUN/node.pid" 2>/dev/null)" ] && break
    sleep 1
  done
  sleep 2
  cmd_status
}

cmd_stop() {
  local sup; sup=$(cat "$RUN/supervisor.pid" 2>/dev/null)
  touch "$RUN/stop" 2>/dev/null
  alive "$sup" && kill "$sup"
  for _ in $(seq 1 10); do alive "$sup" || break; sleep 1; done
  alive "$sup" && kill -9 "$sup"
  pkill -f "src/games/index.js" 2>/dev/null
  pkill -f "cloudflared tunnel --no-autoupdate --url http://localhost:$PORT" 2>/dev/null
  rm -f "$RUN/supervisor.pid" "$RUN/node.pid" "$RUN/url" "$RUN/stop"
  command -v termux-wake-unlock >/dev/null && termux-wake-unlock
  ok "StarGames остановлен"
}

cmd_status() {
  if alive "$(cat "$RUN/node.pid" 2>/dev/null)"; then ok "Бот работает"
  elif alive "$(cat "$RUN/supervisor.pid" 2>/dev/null)"; then warn "Бот перезапускается"
  else warn "Бот остановлен"; fi
  local url; url=$(cat "$RUN/url" 2>/dev/null)
  [ -n "$url" ] && { ok "Приложение: $url"; echo "   Откройте бота в Telegram и нажмите «Играть». В браузере по этой ссылке можно установить приложение на экран."; } || warn "Приложение пока без адреса (нет туннеля)"
  echo "--- последние строки лога ($LOG/bot.log):"
  tail -n 8 "$LOG/bot.log" 2>/dev/null
  if tail -n 20 "$LOG/bot.log" 2>/dev/null | grep -q Conflict; then
    warn "Conflict: этот токен использует ещё какой-то запущенный бот. У StarGames должен быть свой, отдельный токен."
  fi
}

case "$1" in
  setup) shift; cmd_setup "$@" ;;
  update) cmd_update ;;
  start) cmd_start ;;
  stop) cmd_stop ;;
  restart) cmd_stop; cmd_start ;;
  status) cmd_status ;;
  logs) tail -n 50 -f "$LOG/bot.log" ;;
  __supervise) supervise ;;
  *) echo "Использование: bash ~/stargames.sh setup <ТОКЕН> | start | stop | restart | status | logs | update" ;;
esac
