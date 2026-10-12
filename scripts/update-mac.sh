#!/usr/bin/env bash
# Обновляет локальную копию «Амперграда» на Mac и открывает арт для просмотра.
#
# Что делает:
#   1. переключается на рабочую ветку и подтягивает свежие коммиты (только fast-forward);
#   2. проверяет, что рабочее дерево не грязное — если есть свои правки, останавливается
#      и ничего не трогает;
#   3. открывает в Finder папку с превью арта и лист-обзор;
#   4. по флагу --rebuild пересобирает арт-производные (нужен python3 + pillow);
#   5. по флагу --play ставит зависимости, поднимает игру и открывает браузер.
#
# Запуск:  bash scripts/update-mac.sh            # обновить и открыть превью
#          bash scripts/update-mac.sh --rebuild  # ещё и пересобрать макеты из спрайтов
#          bash scripts/update-mac.sh --play     # обновить и сразу запустить игру
set -euo pipefail

BRANCH="arena/77b06826-1"
PORT="${PORT:-5173}"
REBUILD=0
PLAY=0
for arg in "$@"; do
  case "$arg" in
    --rebuild) REBUILD=1 ;;
    --play) PLAY=1 ;;
  esac
done

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT="$(cd "$HERE/.." && pwd)"
cd "$ROOT"

echo "→ репозиторий: $ROOT"

# 1. Чистота дерева: свои правки не затираем никогда.
DIRTY="$(git status --porcelain)"
if [ -n "$DIRTY" ]; then
  echo "⚠ в дереве есть незакоммиченные изменения — обновление остановлено, чтобы ничего не потерять:"
  echo "$DIRTY" | head -20
  echo
  echo "  отложить их:  git stash push -u -m 'локальные правки'"
  echo "  посмотреть:   git status"
  exit 1
fi

# 2. Ветка и коммиты.
CURRENT="$(git rev-parse --abbrev-ref HEAD)"
if [ "$CURRENT" != "$BRANCH" ]; then
  echo "→ переключаюсь с ветки $CURRENT на $BRANCH"
  git fetch origin "$BRANCH"
  git checkout "$BRANCH"
fi
BEFORE="$(git rev-parse --short HEAD)"
git fetch origin "$BRANCH"
git merge --ff-only "origin/$BRANCH"
AFTER="$(git rev-parse --short HEAD)"

if [ "$BEFORE" = "$AFTER" ]; then
  echo "→ обновлений не было: $AFTER"
else
  echo "→ обновлено: $BEFORE → $AFTER"
  git --no-pager log --oneline "$BEFORE..$AFTER" | head -10
fi

# 3. Пересборка арта (по желанию): спрайты и макеты из исходных листов.
PYTHON="${PYTHON:-python3}"
if [ "$REBUILD" = "1" ]; then
  if ! "$PYTHON" -c "import PIL" >/dev/null 2>&1; then
    echo "⚠ pillow не найден ($PYTHON) — пересборка пропущена."
    echo "  поставить:  $PYTHON -m pip install --user pillow"
  else
    echo "→ пересобираю арт-производные"
    "$PYTHON" art/tools/cut_pipes.py
    "$PYTHON" art/tools/cut_props.py
    "$PYTHON" art/tools/cut_rewards.py
    "$PYTHON" art/tools/mock_board.py
    "$PYTHON" art/tools/make_promo.py
    "$PYTHON" art/tools/make_index.py
    if [ -n "$(git status --porcelain art)" ]; then
      echo "⚠ картинки отличаются от закоммиченных (другая версия pillow) — это нормально."
      echo "  вернуть как в репозитории:  git checkout -- art"
    else
      echo "→ пересборка совпала с репозиторием, дерево чистое"
    fi
  fi
fi

# 4. Запуск игры (по желанию): зависимости, dev-сервер, браузер.
if [ "$PLAY" = "1" ]; then
  if [ ! -d "$ROOT/node_modules" ]; then
    echo "→ ставлю зависимости (один раз)"
    npm install
  fi
  echo "→ поднимаю игру на http://localhost:$PORT"
  npm run dev -- --port "$PORT" &
  DEV_PID=$!
  trap 'echo; echo "→ останавливаю игру"; kill "$DEV_PID" 2>/dev/null || true' EXIT INT TERM

  # Ждём, пока сервер начнёт отвечать: иначе браузер откроет «не удалось открыть».
  for _ in $(seq 1 40); do
    if curl -sf -o /dev/null "http://localhost:$PORT/"; then break; fi
    sleep 0.5
  done
  URL="http://localhost:$PORT/"
  if [ "$(uname)" = "Darwin" ]; then open "$URL" 2>/dev/null || true; fi
  echo
  echo "игра открыта: $URL"
  echo "  вид поля (трубы из арта ↔ схема) — кнопка «Вид» внизу или клавиша A"
  echo "  остановить — Ctrl+C в этом окне"
  echo
  wait "$DEV_PID"
  exit 0
fi

# 5. Открыть превью (только на macOS).
PREVIEW="$ROOT/art/preview"
if [ "$(uname)" = "Darwin" ]; then
  open "$PREVIEW" 2>/dev/null || true
  [ -f "$PREVIEW/index.jpg" ] && open "$PREVIEW/index.jpg" 2>/dev/null || true
  echo "→ открыл Finder: art/preview"
else
  echo "→ превью лежит здесь: $PREVIEW"
fi

echo
echo "что смотреть:"
echo "  art/preview/index.jpg    — весь арт одним листом"
echo "  art/preview/level-*.jpg  — поля на уровнях 3, 9, 31, 46, 61, 75"
echo "  art/preview/tileset.jpg  — 8 наборов деталей с масками"
echo "  art/preview/rewards.jpg  — шесть пломб квартала"
echo "  art/promo/listing/       — готовые кадры витрины (1200×630, 1024×500, 1080×1080)"
echo "  art/README.md            — что где лежит и как пересобрать"
echo
echo "играть: bash scripts/update-mac.sh --play    (откроет http://localhost:5173)"
