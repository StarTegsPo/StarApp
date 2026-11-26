#!/data/data/com.termux/files/usr/bin/bash
# Прогрев TikTok прямо на Android-телефоне через Termux + ADB по Wi-Fi (без root и без ПК).
# Листает ленту в приложении TikTok и ставит лайк через случайное число роликов (1–18).
#
#   bash warmup.sh                       — запуск с настройками по умолчанию
#   bash warmup.sh --videos=200          — свои настройки (список ниже)
#   bash warmup.sh --check               — только проверить, что кнопка лайка находится
set -u

VIDEOS=120        # сколько роликов пролистать за запуск
LIKE_MIN=1        # лайк через случайное число роликов
LIKE_MAX=18       #   в диапазоне [LIKE_MIN, LIKE_MAX]
WATCH_MIN=5       # секунд просмотра ролика (мин)
WATCH_MAX=25      # секунд просмотра ролика (макс)
BREAK_EVERY=40    # перерыв примерно каждые N роликов
BREAK_MIN=30      # длительность перерыва, сек
BREAK_MAX=120
LIKE_X=""         # координаты сердечка вручную (если автопоиск не работает)
LIKE_Y=""
CHECK_ONLY=0

for a in "$@"; do
  case "$a" in
    --videos=*)      VIDEOS=${a#*=} ;;
    --like-min=*)    LIKE_MIN=${a#*=} ;;
    --like-max=*)    LIKE_MAX=${a#*=} ;;
    --watch-min=*)   WATCH_MIN=${a#*=} ;;
    --watch-max=*)   WATCH_MAX=${a#*=} ;;
    --break-every=*) BREAK_EVERY=${a#*=} ;;
    --break-min=*)   BREAK_MIN=${a#*=} ;;
    --break-max=*)   BREAK_MAX=${a#*=} ;;
    --like-x=*)      LIKE_X=${a#*=} ;;
    --like-y=*)      LIKE_Y=${a#*=} ;;
    --check)         CHECK_ONLY=1 ;;
    *) echo "Неизвестный параметр: $a"; exit 1 ;;
  esac
done

rnd() { echo $(( $1 + RANDOM % ($2 - $1 + 1) )); }          # целое из [a, b]
log() { echo "$(date +%H:%M:%S) $*"; }
ash() { adb shell "$@" 2>/dev/null | tr -d '\r'; }

# ---------- проверки ----------
if ! command -v adb >/dev/null; then
  echo "❌ Нет adb. Выполните: pkg install android-tools"; exit 1
fi
if [ "$(adb get-state 2>/dev/null)" != "device" ]; then
  echo "❌ Телефон не подключён к adb."
  echo "   Включите «Отладку по Wi-Fi» и выполните adb pair / adb connect (см. README.md)."
  exit 1
fi

PKG=""
for p in com.zhiliaoapp.musically com.ss.android.ugc.trill com.ss.android.ugc.aweme; do
  if ash pm list packages "$p" | grep -q "package:$p\$"; then PKG=$p; break; fi
done
[ -z "$PKG" ] && { echo "❌ Приложение TikTok не найдено."; exit 1; }

SIZE=$(ash wm size | tail -1 | grep -o '[0-9]*x[0-9]*')
W=${SIZE%x*}; H=${SIZE#*x}
[ -z "$W" ] || [ -z "$H" ] && { echo "❌ Не удалось узнать размер экрана."; exit 1; }
log "📱 экран ${W}x${H}, приложение $PKG"

# ---------- действия ----------
next_video() {
  local x=$(( W / 2 + RANDOM % 121 - 60 ))
  local y1=$(( H * (70 + RANDOM % 10) / 100 ))
  local y2=$(( H * (20 + RANDOM % 10) / 100 ))
  ash input swipe "$x" "$y1" "$(( x + RANDOM % 41 - 20 ))" "$y2" "$(rnd 180 380)"
}

# Ищет сердечко на экране через uiautomator. Печатает "x y liked" или ничего.
find_like() {
  timeout 15 adb shell uiautomator dump /sdcard/.wu.xml >/dev/null 2>&1 || return 1
  local line
  line=$(ash cat /sdcard/.wu.xml | tr '>' '\n' \
    | grep -iE '(content-desc|text)="[^"]*(like|лайк|нрав)' \
    | grep -viE 'comment|коммент|share|подел|repost|репост' | head -1)
  [ -z "$line" ] && return 1
  local b; b=$(echo "$line" | grep -o 'bounds="[^"]*"' | grep -o '[0-9]\+' | tr '\n' ' ')
  set -- $b
  [ $# -eq 4 ] || return 1
  local liked=0
  echo "$line" | grep -qE 'selected="true"|checked="true"' && liked=1
  echo "$line" | grep -qiE 'unlike|убрать|удалить лайк' && liked=1
  echo "$(( ($1 + $3) / 2 )) $(( ($2 + $4) / 2 )) $liked"
}

do_like() {
  local res x y liked
  if [ -n "$LIKE_X" ] && [ -n "$LIKE_Y" ]; then
    ash input tap "$LIKE_X" "$LIKE_Y"; log "❤️  лайк (ручные координаты)"; return
  fi
  if res=$(find_like) && [ -n "$res" ]; then
    read -r x y liked <<< "$res"
    if [ "$liked" = 1 ]; then log "💗 уже лайкнут — пропускаю"; return; fi
    sleep "0.$(rnd 2 8)"
    ash input tap "$(( x + RANDOM % 9 - 4 ))" "$(( y + RANDOM % 9 - 4 ))"
    log "❤️  лайк"
  else
    log "⚠️  сердечко не найдено — пропускаю (см. --like-x/--like-y в README)"
  fi
}

cleanup() {
  ash svc power stayon false >/dev/null
  command -v termux-wake-unlock >/dev/null && termux-wake-unlock
  echo; log "⏹  остановлено. Роликов: ${done_videos:-0}, лайков: ${likes:-0}"
  exit 0
}
trap cleanup INT TERM

# ---------- старт ----------
command -v termux-wake-lock >/dev/null && termux-wake-lock
ash svc power stayon true >/dev/null
ash monkey -p "$PKG" -c android.intent.category.LAUNCHER 1 >/dev/null
log "🚀 открываю TikTok… через 10 секунд начну (лента «Рекомендации» должна быть открыта)"
sleep 10

if [ "$CHECK_ONLY" = 1 ]; then
  if res=$(find_like) && [ -n "$res" ]; then
    read -r x y liked <<< "$res"
    log "✅ сердечко найдено: x=$x y=$y, уже лайкнут: $liked"
  else
    log "❌ сердечко не найдено. Задайте координаты вручную: --like-x=… --like-y=…"
  fi
  cleanup
fi

until_like=$(rnd "$LIKE_MIN" "$LIKE_MAX")
until_break=$(rnd $(( BREAK_EVERY > 15 ? BREAK_EVERY - 10 : 5 )) $(( BREAK_EVERY + 10 )))
likes=0
log "▶️  старт: $VIDEOS роликов, первый лайк через $until_like"

for (( i = 1; i <= VIDEOS; i++ )); do
  watch="$(rnd "$WATCH_MIN" "$WATCH_MAX").$(( RANDOM % 10 ))"
  log "🎬 ролик $i/$VIDEOS, смотрю ${watch} c"
  sleep "$watch"

  until_like=$(( until_like - 1 ))
  if [ "$until_like" -le 0 ]; then
    do_like; likes=$(( likes + 1 ))
    sleep "$(rnd 1 3)"
    until_like=$(rnd "$LIKE_MIN" "$LIKE_MAX")
    log "   следующий лайк через $until_like"
  fi

  until_break=$(( until_break - 1 ))
  if [ "$until_break" -le 0 ]; then
    pause=$(rnd "$BREAK_MIN" "$BREAK_MAX")
    log "☕ перерыв $pause c"; sleep "$pause"
    until_break=$(rnd $(( BREAK_EVERY > 15 ? BREAK_EVERY - 10 : 5 )) $(( BREAK_EVERY + 10 )))
  fi

  done_videos=$i
  [ "$i" -lt "$VIDEOS" ] && next_video
done

log "✅ готово"
cleanup
