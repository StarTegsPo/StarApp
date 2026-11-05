# Проверки перед запуском: ищет то, из-за чего боты мешают друг другу.
# Код ботов не меняет, только предупреждает и подсказывает, что поправить.
source "$(dirname "${BASH_SOURCE[0]}")/../common.sh"

mask() { echo "${1%%:*}:…${1: -4}"; }
declare -A TOKEN_OWNER PORT_OWNER DB_OWNER
problems=0
tmp="$RUN_DIR/syntax.err"

for line in "${BOTS[@]}"; do
  parse_bot "$line"
  [ -n "$NAME" ] && [ -f "$FILE" ] || continue
  dir="$(dirname "$FILE")"
  sources=("$FILE"); [ -f "$dir/.env" ] && sources+=("$dir/.env")

  # 1. Синтаксис Python
  if ! "$PYTHON" -c 'import ast,sys; ast.parse(open(sys.argv[1], encoding="utf-8").read())' "$FILE" 2>"$tmp"; then
    err "$NAME: синтаксическая ошибка в $FILE:"; tail -n 4 "$tmp" | sed 's/^/    /'
    problems=$((problems+1))
  fi

  # 2. Токены: один токен у двух ботов = гарантированный Conflict
  tokens=$(grep -ohE '[0-9]{8,10}:[A-Za-z0-9_-]{35}' "${sources[@]}" 2>/dev/null | sort -u)
  [ -z "$tokens" ] && echo "${c_dim}  $NAME: токен в коде не найден (возможно, берётся из переменных окружения)${c_off}"
  for t in $tokens; do
    if [ -n "${TOKEN_OWNER[$t]}" ] && [ "${TOKEN_OWNER[$t]}" != "$NAME" ]; then
      err "Токен $(mask "$t") один и тот же у ${TOKEN_OWNER[$t]} и $NAME — будет Conflict. Выдайте боту свой токен в @BotFather."
      problems=$((problems+1))
    else
      TOKEN_OWNER[$t]="$NAME"
    fi
    # Установленный webhook тоже ломает polling ("Conflict: can't use getUpdates while webhook is active")
    if command -v curl >/dev/null; then
      wh=$(curl -s -m 5 "https://api.telegram.org/bot$t/getWebhookInfo" | grep -o '"url":"[^"]*"' | head -n1)
      if [ -n "$wh" ] && [ "$wh" != '"url":""' ]; then
        warn "$NAME: у токена $(mask "$t") включён webhook $wh. Если бот работает через polling, снимите его:"
        echo "    curl \"https://api.telegram.org/bot<ТОКЕН>/deleteWebhook\""
        problems=$((problems+1))
      fi
    fi
  done

  # 3. Порты веб-серверов (мини-приложение / API)
  ports=$(grep -ohE '(port|PORT)[^0-9]{0,15}[0-9]{4,5}' "${sources[@]}" 2>/dev/null | grep -oE '[0-9]{4,5}$' | sort -u)
  [ -n "$PORT" ] && ports=$(printf '%s\n%s\n' "$ports" "$PORT" | sort -u)
  for p in $ports; do
    if [ -n "${PORT_OWNER[$p]}" ] && [ "${PORT_OWNER[$p]}" != "$NAME" ]; then
      err "Порт $p и у ${PORT_OWNER[$p]}, и у $NAME — второй не поднимет веб-сервер (Address already in use). Поменяйте порт у одного из них."
      problems=$((problems+1))
    else
      PORT_OWNER[$p]="$NAME"
    fi
  done

  # 4. Файлы баз данных (относительные пути считаются от папки бота)
  dbs=$(grep -ohE "[\"'][^\"' ]+\.(db|sqlite|sqlite3)[\"']" "${sources[@]}" 2>/dev/null | tr -d "\"'" | sort -u)
  for d in $dbs; do
    d="${d/#\~/$HOME}"
    case "$d" in /*) abs="$d" ;; *) abs="$dir/$d" ;; esac
    abs="$(readlink -m "$abs")"
    if [ -n "${DB_OWNER[$abs]}" ] && [ "${DB_OWNER[$abs]}" != "$NAME" ]; then
      err "База $abs общая у ${DB_OWNER[$abs]} и $NAME — данные смешаются, возможна ошибка 'database is locked'."
      echo "    Решение: перенесите бота в свою папку или поменяйте имя файла базы в его коде."
      problems=$((problems+1))
    else
      DB_OWNER[$abs]="$NAME"
    fi
  done
done
[ -f "$tmp" ] && rm -f "${RUN_DIR:?}/syntax.err"

[ "$problems" -eq 0 ] && ok "Конфликтов токенов, портов и баз данных не найдено"
exit 0
