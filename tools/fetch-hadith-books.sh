#!/usr/bin/env bash
# Fetches the eight hadith books (ADR-28) from fawazahmed0's hadith-api into a new scratch folder, checks every file
# against the SHA-256 recorded in content/hadith-books-SOURCES.md, and only then copies them into content/local/hadith/,
# which git ignores. Run by hand only: the build and the app never use the network (INV-P1). The text is unreviewed and
# its permission is the founder's to settle (Q17); never commit it. The files fetched are the ones the record names, so
# a changed file on the server shows up as a failed check and nothing is copied.
set -euo pipefail
cd "$(dirname "$0")/.."
api="https://cdn.jsdelivr.net/gh/fawazahmed0/hadith-api@1"
record="content/hadith-books-SOURCES.md"
target="content/local/hadith"
scratch=$(mktemp -d)
trap 'rm -rf "$scratch"' EXIT

grep -oE '^sha256 [0-9a-f]{64} [^ ]+$' "$record" | cut -d' ' -f3 | while read -r path; do
  mkdir -p "$scratch/$(dirname "$path")"
  curl --fail --silent --show-error --location -o "$scratch/$path" "$api/$path"
done

# Every recorded line, checked the way the gate checks it, before anything reaches content/local/.
grep -E '^sha256 [0-9a-f]{64} ' "$record" | sed -E "s#^sha256 ([0-9a-f]{64}) (.*)\$#\\1  $scratch/\\2#" | shasum -a 256 -c --quiet
mkdir -p "$target"
cp -R "$scratch/." "$target/"
echo "fetched and checked: $(find "$target" -type f -name '*.json' | wc -l | tr -d ' ') files in $target"
