#!/usr/bin/env bash
# Пересобирает patch/ampergrad-mechanism.patch из содержимого pr/.
#
# Патч — тот же коммит, что уехал бы в GUTTERCAPS: один файл, который владелец
# прикладывает к их main через `git am`. Собирается в пустом репозитории, поэтому
# от их истории не зависит: все файлы в патче — новые.
#
# Запуск:  bash integrations/guttercaps/tools/build-patch.sh
set -euo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PR_ROOT="$(cd "$HERE/.." && pwd)/pr"
DIST="$(cd "$HERE/.." && pwd)/patch"
MESSAGE="feat(ampergrad): механизм «Амперград» отдельным контуром — правила, контент, арт-контракт"

TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT

git -C "$TMP" init -q
git -C "$TMP" config user.name "Leo88q"
git -C "$TMP" config user.email "leo88q@users.noreply.github.com"
printf '# guttercaps\n' > "$TMP/README.md"
git -C "$TMP" add README.md
git -C "$TMP" commit -qm "base"

while IFS= read -r -d '' file; do
  rel="${file#"$PR_ROOT"/}"
  mkdir -p "$TMP/$(dirname "$rel")"
  cp "$file" "$TMP/$rel"
done < <(find "$PR_ROOT" -type f -print0 | sort -z)

git -C "$TMP" add -A
git -C "$TMP" commit -qm "$MESSAGE"

mkdir -p "$DIST"
git -C "$TMP" format-patch -1 --stdout HEAD > "$DIST/ampergrad-mechanism.patch"
echo "→ патч пересобран: $(du -h "$DIST/ampergrad-mechanism.patch" | cut -f1)"
node "$HERE/check-patch.mjs"
