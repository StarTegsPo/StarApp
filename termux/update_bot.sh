#!/usr/bin/env bash
# Обновляет бота StarTegs (@StarTegsEcosistem_bot) под приложение StarTegs 4.5:
#   1) находит файл бота (из ~/startegs-run/bots.conf или из первого аргумента);
#   2) делает резервную копию;
#   3) скачивает дополнения v58…v63 и вставляет недостающие перед «def _single_instance():»;
#   4) проверяет, что файл без ошибок (иначе возвращает копию);
#   5) ставит aiohttp и, если получится, anthropic;
#   6) спрашивает ключ нейросети и сохраняет его в .env рядом с ботом;
#   7) перезапускает бота.
# Запуск: bash ~/startegs-run/update_bot.sh            (или с путём: bash update_bot.sh ~/startegs_v56.py)
set -u
RAW="https://raw.githubusercontent.com/StarTegsPo/StarApp/claude/startegs-whitegram-features-cdjfn9"
PY=python
say() { printf '\n\033[1;35m%s\033[0m\n' "$*"; }
die() { printf '\n\033[1;31m❌ %s\033[0m\n' "$*"; exit 1; }

# 1. Файл бота
BOT="${1:-}"
if [ -z "$BOT" ] && [ -f "$HOME/startegs-run/bots.conf" ]; then
  BOT=$(grep -o '"startegs|[^|]*' "$HOME/startegs-run/bots.conf" | head -n1 | cut -d'|' -f2)
  BOT="${BOT//\$HOME/$HOME}"
fi
[ -n "$BOT" ] && [ -f "$BOT" ] || die "Не нашёл файл бота. Запусти так: bash update_bot.sh /путь/к/боту.py"
grep -q '^def _single_instance():' "$BOT" || die "В $BOT нет строки «def _single_instance():» — это точно бот StarTegs?"
DIR=$(dirname "$BOT")
say "🤖 Бот: $BOT"

# 2. Резервная копия
BAK="$BOT.bak-$(date +%Y%m%d-%H%M%S)"
cp "$BOT" "$BAK" || die "Не получилось сделать копию"
echo "Копия: $BAK"

# 3. Дополнения
TMP=$(mktemp -d)
for v in 58 59 60 61 62 63; do
  curl -fsSL "$RAW/client/bot/startegs_v${v}_addon.py" -o "$TMP/v$v.py" || die "Не скачалось дополнение v$v (нет интернета?)"
done
"$PY" - "$BOT" "$TMP" <<'EOF' || { cp "$BAK" "$BOT"; die "Не получилось вставить дополнения — файл бота возвращён как был"; }
import sys
bot, tmp = sys.argv[1], sys.argv[2]
src = open(bot, encoding="utf-8").read()
added = []
for v in range(58, 64):
    mark = "_api_handler_v%d = api_handler" % (v - 1)
    if mark in src:
        print("  v%d — уже есть" % v)
        continue
    add = open("%s/v%d.py" % (tmp, v), encoding="utf-8").read().rstrip() + "\n\n\n"
    i = src.index("\ndef _single_instance():") + 1
    src = src[:i] + add + src[i:]
    added.append(v)
    print("  v%d — добавлено ✅" % v)
open(bot, "w", encoding="utf-8").write(src)
EOF
rm -rf "$TMP"

# 4. Проверка
if ! "$PY" -m py_compile "$BOT"; then
  cp "$BAK" "$BOT"
  die "В файле бота ошибка после вставки — вернул копию. Пришли этот текст в чат с Claude."
fi
say "✅ Файл бота в порядке"

# 5. Пакеты
say "📦 Ставлю пакеты…"
"$PY" -m pip install -q aiohttp || echo "⚠️ aiohttp не поставился — проверь интернет"
if "$PY" -m pip install -q anthropic 2>/dev/null; then
  echo "anthropic установлен"
else
  echo "anthropic не поставился — это нормально для Termux, бот будет обращаться к нейросети напрямую"
fi

# 6. Ключ нейросети
ENV="$DIR/.env"
touch "$ENV" && chmod 600 "$ENV"
if grep -q '^ANTHROPIC_API_KEY=.' "$ENV"; then
  say "🔑 Ключ нейросети уже сохранён в $ENV"
  read -r -p "Заменить его? (y/N) " re
else
  re=y
fi
if [ "${re:-n}" = "y" ] || [ "${re:-n}" = "Y" ]; then
  echo "Вставь ключ (начинается с sk-ant-). Его не будет видно на экране — это нормально. Потом нажми Enter:"
  read -r -s KEY; echo
  KEY="${KEY//[[:space:]]/}"
  case "$KEY" in sk-ant-*) ;; *) die "Это не похоже на ключ Anthropic (должен начинаться с sk-ant-). Запусти скрипт ещё раз." ;; esac
  grep -v '^ANTHROPIC_API_KEY=' "$ENV" > "$ENV.tmp"; mv "$ENV.tmp" "$ENV"
  echo "ANTHROPIC_API_KEY=$KEY" >> "$ENV"
  chmod 600 "$ENV"
  echo "Ключ сохранён в $ENV (виден только этому боту)"
fi
if ! grep -q '^STARTEGS_AI_MODEL=' "$ENV"; then
  echo "Какую модель ставить?  1 — claude-haiku-5-5 (быстро и дёшево, советую)   2 — claude-opus-5-5 (самая умная, дороже)"
  read -r -p "1 или 2: " m
  if [ "${m:-1}" = "2" ]; then echo "STARTEGS_AI_MODEL=claude-opus-5-5" >> "$ENV"; else echo "STARTEGS_AI_MODEL=claude-haiku-5-5" >> "$ENV"; fi
fi

# 7. Перезапуск
if [ -x "$HOME/startegs-run/stop_all.sh" ]; then
  say "🔄 Перезапускаю бота…"
  bash "$HOME/startegs-run/stop_all.sh" startegs
  bash "$HOME/startegs-run/start_all.sh" startegs
  sleep 5
  echo "--- последние строки лога ---"
  tail -n 15 "$HOME/.startegs-run/logs/startegs.log" 2>/dev/null
  if tail -n 40 "$HOME/.startegs-run/logs/startegs.log" 2>/dev/null | grep -q 'Traceback\|Error:'; then
    echo
    echo "⚠️ В логе есть ошибка. Пришли эти строки в чат с Claude. Вернуть бота как было:"
    echo "   cp \"$BAK\" \"$BOT\" && bash ~/startegs-run/stop_all.sh startegs && bash ~/startegs-run/start_all.sh startegs"
  fi
else
  say "Перезапусти бота сам (останови и запусти заново), чтобы изменения заработали"
fi
say "🎉 Готово! Открой StarTegs → ⭐ StarTegs → 🎨 Дизайн → «Звёздочка» и задай вопрос."
