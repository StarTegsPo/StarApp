#!/usr/bin/env bash
# Проверка APK на эмуляторе: работает ли прогрев, когда приложение «Прогрев TikTok» свёрнуто,
# и встаёт ли на паузу, когда свёрнут сам TikTok. Вместо TikTok — тестовая лента faketok.
set -u
cd "$(dirname "$0")/.."
OUT=ci/out
mkdir -p "$OUT"
APP=com.startegs.warmup
FAIL=0
log() { echo "=== $*"; }
count() { adb logcat -d -s WARMUP_TEST:I | grep -c " $1" || true; }
svc() { adb logcat -d -s WARMUP:* | grep -c "$1" || true; }
fg() { adb shell dumpsys activity activities | grep -m1 -E "mResumedActivity|topResumedActivity" | sed 's/^ *//'; }
check() { if eval "$2"; then echo "✅ $1"; else echo "❌ $1"; FAIL=1; fi; }

adb install -r faketok/build/outputs/apk/debug/faketok-debug.apk
adb install -r app/build/outputs/apk/debug/app-debug.apk

# быстрые настройки, чтобы за минуту было видно много действий
cat > "$OUT/cfg.xml" <<'EOF'
<?xml version='1.0' encoding='utf-8' standalone='yes' ?>
<map>
    <int name="videos" value="0" />
    <int name="likeMin" value="1" />
    <int name="likeMax" value="2" />
    <int name="watchMin" value="2" />
    <int name="watchMax" value="3" />
    <int name="skipChance" value="0" />
    <int name="longChance" value="0" />
    <int name="favChance" value="0" />
    <boolean name="premium" value="true" />
    <string name="niche">авто</string>
    <int name="followChance" value="0" />
</map>
EOF
adb push "$OUT/cfg.xml" /data/local/tmp/cfg.xml >/dev/null
adb shell "cat /data/local/tmp/cfg.xml | run-as $APP sh -c 'mkdir -p shared_prefs && cat > shared_prefs/cfg.xml'"

log "Включаю спецвозможность «Прогрев TikTok»"
adb shell settings put secure enabled_accessibility_services $APP/$APP.WarmupService
adb shell settings put secure accessibility_enabled 1
sleep 4
BOUND=$(adb shell dumpsys accessibility | grep -c "$APP" || true)
echo "служба в dumpsys accessibility: $BOUND"
check "спецвозможность подключена" '[ "$BOUND" -ge 1 ]'

log "Открываю приложение и жму «Начать прогрев»"
adb shell am start -W -n $APP/.MainActivity >/dev/null
sleep 4
adb exec-out screencap -p > "$OUT/1_app.png"
adb shell uiautomator dump /sdcard/ui.xml >/dev/null && adb pull /sdcard/ui.xml "$OUT/ui.xml" >/dev/null
XY=$(python3 - "$OUT/ui.xml" <<'PY'
import re, sys
xml = open(sys.argv[1], encoding="utf-8").read()
for m in re.finditer(r'<node [^>]*>', xml):
    n = m.group(0)
    if "Начать прогрев" in n:
        a, b, c, d = map(int, re.search(r'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', n).groups())
        print((a + c) // 2, (b + d) // 2)
        break
PY
)
check "кнопка «Начать прогрев» видна на экране" '[ -n "$XY" ]'
[ -n "$XY" ] || { echo "UI:"; head -c 3000 "$OUT/ui.xml"; exit 1; }
adb logcat -c
adb shell input tap $XY

log "Фаза 1: приложение свёрнуто, TikTok на экране — 60 с"
sleep 8
FG1=$(fg); echo "на экране: $FG1"
check "«Прогрев TikTok» свёрнут, на экране TikTok" 'echo "$FG1" | grep -q com.zhiliaoapp.musically'
sleep 52
adb exec-out screencap -p > "$OUT/2_running.png"
S1=$(count SWIPE); L1=$(count LIKE)
SOK=$(svc "swipe ok"); SC=$(svc "swipe cancelled"); SN=$(svc "swipe not sent")
echo "свайпов: $S1, лайков: $L1 | приложение: свайпов выполнено $SOK, отменено $SC, не отправлено $SN"
POSTS=$(svc "post ")
echo "постов просмотрено (по приложению): $POSTS"
check "каждый выполненный свайп дошёл до TikTok" '[ "$S1" -ge "$SOK" ]'
check "на каждый просмотренный пост был свайп (не засиживается)" '[ $((S1 + 1)) -ge "$POSTS" ]' 
check "свайпы идут, пока приложение свёрнуто (>=5)" '[ "$S1" -ge 5 ]'
check "лайки идут, пока приложение свёрнуто (>=2)" '[ "$L1" -ge 2 ]'
NICHE_BAD=$(adb logcat -d -s WARMUP_TEST:I | grep " LIKE " | awk '{print $NF}' | awk '$1 % 2 == 1' | wc -l)
NICHE_OK=$(adb logcat -d -s WARMUP_TEST:I | grep " LIKE " | awk '{print $NF}' | awk '$1 % 2 == 0' | wc -l)
echo "ниша «авто»: лайков нишевым $NICHE_OK, остальным $NICHE_BAD"
check "лайки только роликам из ниши" '[ "$NICHE_BAD" -eq 0 ] && [ "$NICHE_OK" -ge 1 ]'

log "Фаза 2: сворачиваю сам TikTok (Домой) — 25 с"
adb shell input keyevent KEYCODE_HOME
sleep 3
adb logcat -c
sleep 22
adb exec-out screencap -p > "$OUT/3_tiktok_minimized.png"
S2=$(count SWIPE); L2=$(count LIKE)
echo "свайпов: $S2, лайков: $L2"
check "когда TikTok свёрнут — пауза, ничего не нажимается" '[ "$S2" -eq 0 ] && [ "$L2" -eq 0 ]'

log "Фаза 3: снова открываю TikTok — 30 с"
adb logcat -c
adb shell am start -n com.zhiliaoapp.musically/.FeedActivity >/dev/null
sleep 30
S3=$(count SWIPE)
echo "свайпов: $S3"
check "после возврата в TikTok прогрев продолжается" '[ "$S3" -ge 2 ]'
adb exec-out screencap -p > "$OUT/4_resumed.png"

log "Фаза 4: останавливаю кнопкой в приложении — сессия должна попасть в историю"
# uiautomator во время сессии не запускаем: на Android 11 он временно отключает все спецвозможности.
# Главная кнопка та же («Начать» → «Остановить»), её координаты известны из первого снимка экрана.
adb shell am start -W -n $APP/.MainActivity >/dev/null
sleep 4
adb exec-out screencap -p > "$OUT/5_before_stop.png"
RUNNING_BEFORE=$(adb shell "run-as $APP cat shared_prefs/cfg.xml" | grep -c '&quot;p&quot;' || true)
check "до нажатия «Остановить» сессия ещё идёт (в истории пусто)" '[ "$RUNNING_BEFORE" -eq 0 ]'
adb shell input tap $XY
sleep 3
adb exec-out screencap -p > "$OUT/6_stopped.png"
HIST=$(adb shell "run-as $APP cat shared_prefs/cfg.xml" | grep -o '&quot;p&quot;:[0-9]*' | head -1)
echo "история: $HIST"
check "сессия сохранена в истории" '[ -n "$HIST" ]'


adb logcat -d > "$OUT/logcat.txt"
echo
[ $FAIL -eq 0 ] && echo "ИТОГ: всё работает ✅" || echo "ИТОГ: есть проблемы ❌"
exit $FAIL
