# Общие функции для start_all.sh / stop_all.sh / status.sh
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
[ "$(basename "$HERE")" = "lib" ] && HERE="$(dirname "$HERE")"
CONF="${BOTS_CONF:-$HERE/bots.conf}"
[ -f "$CONF" ] || { echo "Нет файла настроек: $CONF"; exit 1; }
# shellcheck disable=SC1090
source "$CONF"
PYTHON="${PYTHON:-python}"
RUN_ROOT="${RUN_ROOT:-$HOME/.startegs-run}"
RUN_DIR="$RUN_ROOT/run"
LOG_DIR="$RUN_ROOT/logs"
mkdir -p "$RUN_DIR" "$LOG_DIR"

c_red=$'\e[31m'; c_grn=$'\e[32m'; c_yel=$'\e[33m'; c_dim=$'\e[2m'; c_off=$'\e[0m'
ok()   { echo "${c_grn}✔${c_off} $*"; }
warn() { echo "${c_yel}⚠${c_off} $*"; }
err()  { echo "${c_red}✘${c_off} $*"; }

# Разбирает строку конфига: задаёт NAME, FILE, PORT
parse_bot() {
  IFS='|' read -r NAME FILE PORT <<<"$1"
  NAME="$(echo "$NAME" | tr -cd 'A-Za-z0-9_-')"
  FILE="$(echo "$FILE" | sed 's/^ *//;s/ *$//')"
  PORT="$(echo "$PORT" | tr -cd '0-9')"
}

alive() { [ -n "$1" ] && kill -0 "$1" 2>/dev/null; }
pid_of() { [ -f "$RUN_DIR/$1.$2" ] && cat "$RUN_DIR/$1.$2" 2>/dev/null; }

# Удаляет служебные файлы одного бота (pid, stop). Пустое имя — ошибка, а не удаление.
clear_state() {
  local n="${1:?}"
  rm -f "${RUN_DIR:?}/$n.pid" "${RUN_DIR:?}/$n.supervisor.pid" "${RUN_DIR:?}/$n.stop"
}

# Ищет python-процессы, которые выполняют именно этот файл (в том числе запущенные
# вручную): сравнивает полный путь с учётом рабочей папки процесса. Печатает PID'ы.
find_script_procs() {
  local target base p cmd arg cwd path
  target="$(readlink -f "$1")"
  base="$(basename "$target")"
  for p in /proc/[0-9]*; do
    p="${p#/proc/}"
    [ "$p" = "$$" ] && continue
    cmd="$(tr '\0' '\n' 2>/dev/null < "/proc/$p/cmdline")" || continue
    echo "$cmd" | head -n1 | grep -q 'python' || continue
    cwd="$(readlink "/proc/$p/cwd" 2>/dev/null)"
    while IFS= read -r arg; do
      [ "$(basename -- "$arg" 2>/dev/null)" = "$base" ] || continue
      case "$arg" in /*) path="$arg" ;; *) path="$cwd/$arg" ;; esac
      if [ "$(readlink -f "$path" 2>/dev/null)" = "$target" ]; then echo "$p"; break; fi
    done <<<"$cmd"
  done
}
