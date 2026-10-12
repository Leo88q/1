#!/usr/bin/env bash
# Открывает (или обновляет) PR механизма «Амперград» в Leo88q/guttercaps.
#
# Что делает: собирает один коммит поверх их main из содержимого
# integrations/guttercaps/pr/ — Git Data API вместо клона (их репозиторий ~1.2 ГБ,
# клонировать его ради документа и пакета незачем). Затем создаёт или обновляет
# ветку и открывает PR.
#
# Запуск:  bash integrations/guttercaps/tools/open-pr.sh
# Требует: gh с доступом на запись в Leo88q/guttercaps.
set -euo pipefail

REPO="${REPO:-Leo88q/guttercaps}"
BRANCH="${BRANCH:-arena/77b06826-ampergrad}"
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PR_ROOT="$(cd "$HERE/.." && pwd)/pr"

TITLE="feat(ampergrad): механизм «Амперград» отдельным контуром — правила, контент, арт-контракт"
BODY_FILE="$HERE/pr-body.md"

echo "→ репозиторий: $REPO, ветка: $BRANCH"
BASE_SHA="$(gh api "repos/$REPO/git/ref/heads/main" --jq .object.sha)"
BASE_TREE="$(gh api "repos/$REPO/git/commits/$BASE_SHA" --jq .tree.sha)"
echo "→ база main: ${BASE_SHA:0:7}, дерево: ${BASE_TREE:0:7}"

# 1. Блобы файлов. Текст уходит как есть, бинарь — base64.
BLOB_ENTRIES=()
while IFS= read -r -d '' file; do
  rel="${file#"$PR_ROOT"/}"
  case "$rel" in
    *.png|*.jpg|*.jpeg|*.webp) enc="base64"; content="$(base64 < "$file" | tr -d '\n')" ;;
    *) enc="utf-8"; content="$(cat "$file")" ;;
  esac
  sha="$(gh api -X POST "repos/$REPO/git/blobs" -f "encoding=$enc" -f "content=$content" --jq .sha)"
  BLOB_ENTRIES+=("$rel" "$sha")
  echo "  блоб ${sha:0:7}  $rel"
done < <(find "$PR_ROOT" -type f -print0 | sort -z)

# 2. Дерево: кладём поверх дерева main.
TREE_JSON="$(mktemp)"
{
  printf '{"tree":['
  first=1
  for ((i = 0; i < ${#BLOB_ENTRIES[@]}; i += 2)); do
    rel="${BLOB_ENTRIES[$i]}"
    sha="${BLOB_ENTRIES[$((i + 1))]}"
    [ $first -eq 1 ] || printf ','
    first=0
    printf '{"path":"%s","mode":"100644","type":"blob","sha":"%s"}' "$rel" "$sha"
  done
  printf '],"base_tree":"%s"}' "$BASE_TREE"
} > "$TREE_JSON"
TREE_SHA="$(gh api -X POST "repos/$REPO/git/trees" --input "$TREE_JSON" --jq .sha)"
rm -f "$TREE_JSON"
echo "→ дерево собрано: ${TREE_SHA:0:7}"

# 3. Коммит поверх main.
COMMIT_JSON="$(mktemp)"
printf '{"message":"%s","tree":"%s","parents":["%s"]}' \
  "feat(ampergrad): механизм «Амперград» отдельным контуром — правила, контент, арт-контракт" \
  "$TREE_SHA" "$BASE_SHA" > "$COMMIT_JSON"
COMMIT_SHA="$(gh api -X POST "repos/$REPO/git/commits" --input "$COMMIT_JSON" --jq .sha)"
rm -f "$COMMIT_JSON"
echo "→ коммит: ${COMMIT_SHA:0:7}"

# 4. Ветка: создаём или сдвигаем на новый коммит.
if gh api "repos/$REPO/git/ref/heads/$BRANCH" >/dev/null 2>&1; then
  gh api -X PATCH "repos/$REPO/git/refs/heads/$BRANCH" -f "sha=$COMMIT_SHA" --jq .object.sha >/dev/null
  echo "→ ветка обновлена: $BRANCH"
else
  gh api -X POST "repos/$REPO/git/refs" -f "ref=refs/heads/$BRANCH" -f "sha=$COMMIT_SHA" --jq .object.sha >/dev/null
  echo "→ ветка создана: $BRANCH"
fi

# 5. PR: открываем или обновляем описание существующего.
if gh pr view "$BRANCH" -R "$REPO" >/dev/null 2>&1; then
  gh pr edit "$BRANCH" -R "$REPO" --title "$TITLE" --body-file "$BODY_FILE"
  echo "→ PR обновлён"
else
  gh pr create -R "$REPO" --base main --head "$BRANCH" --title "$TITLE" --body-file "$BODY_FILE"
fi
gh pr view "$BRANCH" -R "$REPO" --json number,url --jq '"→ PR #\(.number): \(.url)"'
