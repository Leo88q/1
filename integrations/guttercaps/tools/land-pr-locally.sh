#!/usr/bin/env bash
# Укладывает механизм «Амперград» в клон GUTTERCAPS и открывает PR — с вашей машины.
#
# Нужен, когда у сессии Arena нет прав на запись в Leo88q/guttercaps (токен сессии
# видит только репозиторий «Амперграда»). Локально их клон пушится вашей подписью,
# поэтому доступ есть всегда.
#
# Запуск:
#   bash integrations/guttercaps/tools/land-pr-locally.sh ~/Desktop/guttercaps
set -euo pipefail

REPO_DIR="${1:?путь к клону guttercaps, например ~/Desktop/guttercaps}"
BRANCH="arena/77b06826-ampergrad"
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PATCH="$HERE/../patch/ampergrad-mechanism.patch"
TITLE="feat(ampergrad): механизм «Амперград» отдельным контуром — правила, контент, арт-контракт"
BODY="$HERE/pr-body.md"

cd "$REPO_DIR"
test -f "$PATCH" || { echo "нет патча: $PATCH"; exit 1; }

BASE="$(git remote show origin | sed -n 's/.*HEAD branch: //p')"
echo "→ клон: $REPO_DIR (основная ветка: ${BASE:-main})"
git fetch --prune origin
git checkout "${BASE:-main}"
git pull --ff-only

if git show-ref --verify --quiet "refs/heads/$BRANCH"; then
  git checkout "$BRANCH"
  git am "$PATCH" || { git am --abort; echo "патч не лёг — ветка $BRANCH уже содержит другие правки"; exit 1; }
else
  git checkout -b "$BRANCH"
  git am "$PATCH"
fi

git push -u origin "$BRANCH"
gh pr create --base "${BASE:-main}" --head "$BRANCH" --title "$TITLE" --body-file "$BODY" \
  || gh pr view "$BRANCH" --json number,url --jq '"PR уже открыт: #\(.number) \(.url)"'
