#!/usr/bin/env bash
# The store build (phase 8). Run on the machine that holds the upload key, from the repository root.
#
#   export MUMINS_UPLOAD_STORE_FILE=/path/outside/the/repo/upload.jks
#   export MUMINS_UPLOAD_STORE_PASSWORD=...   MUMINS_UPLOAD_KEY_ALIAS=...   MUMINS_UPLOAD_KEY_PASSWORD=...
#   tools/release.sh
#
# It refuses to make a store build unless every gate passes with both content files required: no unreviewed deed
# (INV-C1) and the real district list (Q4). Until the reviewer delivers content/deeds.json, it fails on purpose.
set -euo pipefail
cd "$(dirname "$0")/.."

for name in MUMINS_UPLOAD_STORE_FILE MUMINS_UPLOAD_STORE_PASSWORD MUMINS_UPLOAD_KEY_ALIAS MUMINS_UPLOAD_KEY_PASSWORD; do
  if [ -z "${!name:-}" ]; then echo "release: $name is not set; a store build must be signed with the upload key." >&2; exit 1; fi
done
case "$MUMINS_UPLOAD_STORE_FILE" in
  "$PWD"/*) echo "release: the keystore must live outside the repository." >&2; exit 1 ;;
esac
if [ -n "$(git status --porcelain)" ]; then echo "release: the working tree has uncommitted changes; build from a commit." >&2; exit 1; fi

./gradlew clean build bundleRelease -PdeedsRequired=true -PdistrictsRequired=true --console=plain

echo "release: built from commit $(git rev-parse HEAD)"
ls -l app/build/outputs/bundle/release/*.aab
