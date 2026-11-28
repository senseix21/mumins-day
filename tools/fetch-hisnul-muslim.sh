#!/usr/bin/env bash
# Fetches Hisnul Muslim (ADR-27) into content/local/hisnul-muslim/, which git ignores, and checks every file against
# the SHA-256 recorded in content/hisnul-muslim-SOURCES.md. Run by hand only: the build and the app never use the
# network (INV-P1). The text is unreviewed and its permission is the founder's to settle (Q16); never commit it.
# The ids fetched are the ones the record names, so a changed or added dua on the server shows up as a failed check.
set -euo pipefail
cd "$(dirname "$0")/.."
api="https://dua-api.hisnul.workers.dev/api"
record="content/hisnul-muslim-SOURCES.md"
target="content/local/hisnul-muslim"
mkdir -p "$target/duas"

curl --fail --silent --show-error -o "$target/b1-chapters.json" "$api/books/1/chapters"
grep -oE '^sha256 [0-9a-f]{64} duas/[0-9]+\.json$' "$record" | grep -oE '[0-9]+\.json$' | while read -r file; do
  curl --fail --silent --show-error -o "$target/duas/$file" "$api/duas/${file%.json}"
done

# Every recorded line, checked the way the gate checks it.
grep -E '^sha256 [0-9a-f]{64} ' "$record" | sed -E "s#^sha256 ([0-9a-f]{64}) (.*)\$#\\1  $target/\\2#" | shasum -a 256 -c --quiet
echo "fetched and checked: $(find "$target" -type f -name '*.json' | wc -l | tr -d ' ') files in $target"
