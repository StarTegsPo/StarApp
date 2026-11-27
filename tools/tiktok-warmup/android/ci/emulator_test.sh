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

# ---------- скриншоты: онбординг, главный экран, смена темы (анимации включены) ----------
log "Скриншоты: онбординг и анимации"
adb shell settings put global animator_duration_scale 1
adb shell settings put global transition_animation_scale 1
adb shell settings put global window_animation_scale 1
adb shell am start -W -n $APP/.MainActivity >/dev/null
sleep 2;  adb exec-out screencap -p > "$OUT/ob_1.png"
W=$(adb shell wm size | grep -o '[0-9]*x[0-9]*' | tail -1); SW=${W%x*}; SH=${W#*x}
adb shell input swipe $((SW*8/10)) $((SH/2)) $((SW*2/10)) $((SH/2)) 150
sleep 1.6; adb exec-out screencap -p > "$OUT/ob_2.png"
adb shell input swipe $((SW*8/10)) $((SH/2)) $((SW*2/10)) $((SH/2)) 150
sleep 1.6; adb exec-out screencap -p > "$OUT/ob_3.png"
tapText() {  # нажать на элемент с текстом $1 (до включения спецвозможности uiautomator безопасен)
  adb shell uiautomator dump /sdcard/u.xml >/dev/null 2>&1 && adb pull /sdcard/u.xml "$OUT/u.xml" >/dev/null 2>&1 || return 1
  local xy; xy=$(python3 - "$OUT/u.xml" "$1" <<'PY'
import re, sys
xml = open(sys.argv[1], encoding="utf-8").read()
for m in re.finditer(r'<node [^>]*>', xml):
    n = m.group(0)
    if sys.argv[2] in n:
        a, b, c, d = map(int, re.search(r'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', n).groups())
        print((a + c) // 2, (b + d) // 2); break
PY
)
  [ -n "$xy" ] && adb shell input tap $xy
}
adb shell input swipe $((SW*8/10)) $((SH/2)) $((SW*2/10)) $((SH/2)) 150   # в конце — снова свайп (на 3-м экране ничего)
adb shell input tap $((SW*85/100)) $((SH*8/100))   # «Пропустить»
sleep 0.5; adb exec-out screencap -p > "$OUT/main_entrance.png"
sleep 2;   adb exec-out screencap -p > "$OUT/main.png"
adb shell input tap $((SW*80/100)) $((SH*9/100))   # тема «Тёмно-красная»
sleep 0.55; adb exec-out screencap -p > "$OUT/theme_reveal.png"
sleep 1.5;  adb exec-out screencap -p > "$OUT/theme_wine.png"
adb shell input tap $((SW*50/100)) $((SH*9/100))   # тема «Светлая»
sleep 1.8;  adb exec-out screencap -p > "$OUT/theme_light.png"
adb shell input keyevent KEYCODE_HOME
sleep 1
adb shell am force-stop $APP   # чтобы приложение не перезаписало настройки из памяти
sleep 1
adb shell settings put global animator_duration_scale 0
adb shell settings put global transition_animation_scale 0
adb shell settings put global window_animation_scale 0

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
    <boolean name="onboarded" value="true" />
    <int name="seenVersion" value="99999" />
    <string name="theme">dark</string>
    <string name="niche">авто</string>
    <int name="followChance" value="0" />
</map>
EOF
adb push "$OUT/cfg.xml" /data/local/tmp/cfg.xml >/dev/null
adb shell "cat /data/local/tmp/cfg.xml | run-as $APP sh -c 'mkdir -p shared_prefs && cat > shared_prefs/cfg.xml'"

# после force-stop пакет «остановлен» — открываем его, иначе Android не подключит спецвозможность
adb shell am start -W -n $APP/.MainActivity >/dev/null; sleep 2; adb shell input keyevent KEYCODE_HOME; sleep 1
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
adb logcat -d -s WARMUP:I | grep " niche " | head -12
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
