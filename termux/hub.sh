#!/usr/bin/env bash
# StarTegs Hub в Termux одной командой — один бот вместо четырёх.
#   bash ~/hub.sh setup <ТОКЕН> <ВАШ_ID>   — установить / обновить и прописать настройки
#   bash ~/hub.sh key                       — подключить нейросеть (OpenRouter бесплатно, DeepSeek, Claude)
#   bash ~/hub.sh start | stop | restart | status | logs | update
APP="$HOME/StarTegsHub"
REPO="https://github.com/StarTegsPo/StarApp"
BRANCH="claude/startegs-whitegram-features-cdjfn9"
HUB="$APP/hub"
RUN="$HUB/data/run"
LOG="$HUB/data/logs"
PORT=8080
PY=python

c_red=$'\e[31m'; c_grn=$'\e[32m'; c_yel=$'\e[33m'; c_off=$'\e[0m'
ok()   { echo "${c_grn}✔${c_off} $*"; }
warn() { echo "${c_yel}⚠${c_off} $*"; }
die()  { echo "${c_red}✘${c_off} $*"; exit 1; }
alive() { [ -n "$1" ] && kill -0 "$1" 2>/dev/null; }

set_env() {
  local f="$HUB/.env" k="$1" v="$2"
  touch "$f"; chmod 600 "$f"
  if grep -q "^$k=" "$f"; then
    awk -v k="$k" -v v="$v" -F= '$1==k {print k"="v; next} {print}' "$f" > "$f.tmp" && mv -f "$f.tmp" "$f"
  else
    echo "$k=$v" >> "$f"
  fi
}
get_env() { grep "^$1=" "$HUB/.env" 2>/dev/null | head -n1 | cut -d= -f2-; }
tg() { curl -s -m 15 "https://api.telegram.org/bot$(get_env BOT_TOKEN)/$1"; }

fetch() {
  if [ -d "$APP/.git" ]; then
    git -C "$APP" fetch -q --depth 1 origin "$BRANCH" && git -C "$APP" checkout -q "$BRANCH" 2>/dev/null; git -C "$APP" reset -q --hard "origin/$BRANCH" \
      || die "Не удалось обновить $APP"
    ok "StarTegs Hub обновлён"
  else
    [ -e "$APP" ] && die "$APP уже есть и это не git-папка — переименуйте её и повторите"
    git clone -q --depth 1 -b "$BRANCH" "$REPO" "$APP" || die "Не удалось скачать StarTegs Hub"
    ok "StarTegs Hub скачан в $APP"
  fi
}

cmd_setup() {
  local token="$1" admin="$2"
  [[ "$token" =~ ^[0-9]{6,12}:[A-Za-z0-9_-]{30,}$ ]] || die "Первым аргументом нужен токен бота вида 123456:ABC... (от @BotFather)"
  [[ "$admin" =~ ^[0-9]{3,15}$ ]] || die "Вторым аргументом нужен ваш числовой Telegram ID (узнать: @userinfobot)"
  echo "Установка пакетов…"
  pkg install -y python git curl procps cloudflared >/dev/null 2>&1 || pkg install -y python git curl procps || die "Не удалось установить пакеты"
  command -v cloudflared >/dev/null || warn "cloudflared не установился — бот будет работать, но без мини-приложения и связи с приложением"
  fetch
  $PY -m pip install -q -r "$HUB/requirements.txt" || die "Не удалось установить aiogram/aiohttp"
  ok "Python-библиотеки на месте"
  mkdir -p "$RUN" "$LOG"
  set_env BOT_TOKEN "$token"
  set_env ADMIN_ID "$admin"
  set_env PORT "$PORT"
  local me; me=$(tg getMe)
  echo "$me" | grep -q '"ok":true' || die "Telegram не принял токен: $me"
  ok "Токен верный: @$(echo "$me" | grep -o '"username":"[^"]*"' | cut -d'"' -f4)"
  echo
  warn "Если этот токен раньше использовал старый бот StarTegs — остановите его: bash ~/startegs-run/stop_all.sh startegs"
  ok "Готово. Нейросеть: bash ~/hub.sh key   ·   Запуск: bash ~/hub.sh start"
}

cmd_key() {
  [ -f "$HUB/.env" ] || die "Сначала: bash ~/hub.sh setup <ТОКЕН> <ВАШ_ID>"
  echo "Какую нейросеть подключить?"
  echo "  1 — OpenRouter   БЕСПЛАТНО (около 50 вопросов в день, ключ на openrouter.ai → Keys)"
  echo "  2 — DeepSeek     недорого (platform.deepseek.com)"
  echo "  3 — Claude       (console.anthropic.com)"
  read -r -p "1, 2 или 3: " p
  echo "Вставь ключ (его не будет видно) и нажми Enter:"
  read -r -s KEY; echo; KEY="${KEY//[[:space:]]/}"
  [ -n "$KEY" ] || die "Пустой ключ"
  case "${p:-1}" in
    2) set_env DEEPSEEK_API_KEY "$KEY"; set_env AI_PROVIDER deepseek ;;
    3) set_env ANTHROPIC_API_KEY "$KEY"; set_env AI_PROVIDER claude ;;
    *) set_env OPENROUTER_API_KEY "$KEY"; set_env AI_PROVIDER openrouter ;;
  esac
  ok "Ключ сохранён в $HUB/.env (виден только боту). Перезапуск: bash ~/hub.sh restart"
}

load_env() {
  while IFS= read -r l || [ -n "$l" ]; do
    [[ "$l" =~ ^([A-Za-z_][A-Za-z0-9_]*)=(.*)$ ]] || continue
    export "${BASH_REMATCH[1]}=${BASH_REMATCH[2]}"
  done < "$HUB/.env"
}

supervise() {
  cd "$HUB" || exit 1
  echo $$ > "$RUN/supervisor.pid"
  local py_pid="" cf_pid="" delay=5
  trap 'touch "$RUN/stop"; alive "$py_pid" && kill "$py_pid"; alive "$cf_pid" && kill "$cf_pid"' TERM INT
  while [ ! -e "$RUN/stop" ]; do
    local started; started=$(date +%s)
    set_env PUBLIC_URL ""
    if command -v cloudflared >/dev/null; then
      : > "$LOG/tunnel.log"
      cloudflared tunnel --no-autoupdate --url "http://localhost:$PORT" >> "$LOG/tunnel.log" 2>&1 &
      cf_pid=$!
      local url=""
      for _ in $(seq 1 40); do
        url=$(grep -o 'https://[a-z0-9-]*\.trycloudflare\.com' "$LOG/tunnel.log" | head -n1)
        [ -n "$url" ] && break; sleep 1
      done
      [ -n "$url" ] && { set_env PUBLIC_URL "$url"; echo "$url" > "$RUN/url"; } || echo "$(date '+%F %T') туннель не выдал адрес" >> "$LOG/bot.log"
    fi
    load_env
    echo "===== $(date '+%F %T') запуск StarTegs Hub =====" >> "$LOG/bot.log"
    $PY "$HUB/bot.py" >> "$LOG/bot.log" 2>&1 &
    py_pid=$!
    echo "$py_pid" > "$RUN/bot.pid"
    if [ -n "$cf_pid" ]; then wait -n "$py_pid" "$cf_pid"; else wait "$py_pid"; fi
    alive "$py_pid" && kill "$py_pid"; alive "$cf_pid" && kill "$cf_pid"
    wait 2>/dev/null
    rm -f "$RUN/bot.pid" "$RUN/url"; cf_pid=""
    [ -e "$RUN/stop" ] && break
    if [ $(( $(date +%s) - started )) -gt 120 ]; then delay=5; else delay=$(( delay * 2 > 300 ? 300 : delay * 2 )); fi
    echo "===== $(date '+%F %T') бот или туннель остановился, перезапуск через ${delay} с =====" >> "$LOG/bot.log"
    for ((i = 0; i < delay; i++)); do [ -e "$RUN/stop" ] && break; sleep 1; done
  done
  rm -f "$RUN/supervisor.pid" "$RUN/bot.pid" "$RUN/stop" "$RUN/url"
}

cmd_start() {
  [ -f "$HUB/.env" ] && [ -n "$(get_env BOT_TOKEN)" ] || die "Сначала: bash ~/hub.sh setup <ТОКЕН> <ВАШ_ID>"
  mkdir -p "$RUN" "$LOG"
  if alive "$(cat "$RUN/supervisor.pid" 2>/dev/null)"; then ok "StarTegs Hub уже запущен"; cmd_status; return; fi
  pkill -f "$HUB/bot.py" 2>/dev/null && sleep 1
  rm -f "$RUN/stop"
  command -v termux-wake-lock >/dev/null && termux-wake-lock
  nohup bash "$0" __supervise >/dev/null 2>&1 &
  echo "Запускаю (до минуты — пока поднимается туннель)…"
  for _ in $(seq 1 60); do grep -q "Start polling\|веб-сервер" "$LOG/bot.log" 2>/dev/null && break; sleep 1; done
  sleep 3
  cmd_status
}

cmd_stop() {
  local sup; sup=$(cat "$RUN/supervisor.pid" 2>/dev/null)
  touch "$RUN/stop" 2>/dev/null
  alive "$sup" && kill "$sup"
  for _ in $(seq 1 10); do alive "$sup" || break; sleep 1; done
  alive "$sup" && kill -9 "$sup"
  pkill -f "$HUB/bot.py" 2>/dev/null
  pkill -f "cloudflared tunnel --no-autoupdate --url http://localhost:$PORT" 2>/dev/null
  rm -f "$RUN/supervisor.pid" "$RUN/bot.pid" "$RUN/url" "$RUN/stop"
  ok "StarTegs Hub остановлен"
}

cmd_status() {
  if alive "$(cat "$RUN/bot.pid" 2>/dev/null)"; then ok "Бот работает"
  elif alive "$(cat "$RUN/supervisor.pid" 2>/dev/null)"; then warn "Бот перезапускается"
  else warn "Бот остановлен"; fi
  local url; url=$(cat "$RUN/url" 2>/dev/null)
  [ -n "$url" ] && ok "Мини-приложение и связь с приложением: $url" || warn "Нет туннеля — мини-приложение и связь с приложением недоступны"
  echo "--- последние строки лога ($LOG/bot.log):"
  tail -n 8 "$LOG/bot.log" 2>/dev/null
  tail -n 30 "$LOG/bot.log" 2>/dev/null | grep -q "Conflict" && warn "Conflict: этот токен использует ещё один запущенный бот — остановите его"
}

case "$1" in
  setup) shift; cmd_setup "$@" ;;
  key) cmd_key ;;
  start) cmd_start ;;
  stop) cmd_stop ;;
  restart) cmd_stop; cmd_start ;;
  status) cmd_status ;;
  logs) tail -n 60 -f "$LOG/bot.log" ;;
  update) cmd_stop; fetch; $PY -m pip install -q -r "$HUB/requirements.txt"; cmd_start ;;
  __supervise) supervise ;;
  *) echo "Использование: bash ~/hub.sh setup <ТОКЕН> <ВАШ_ID> | key | start | stop | restart | status | logs | update" ;;
esac
