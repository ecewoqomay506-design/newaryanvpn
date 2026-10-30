#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SING_BOX_DIR="${1:-${SING_BOX_DIR:-}}"
PINNED_TAG="v1.9.7"

if [[ -z "$SING_BOX_DIR" || ! -d "$SING_BOX_DIR/.git" ]]; then
  echo "Usage: $0 /path/to/sing-box-v1.9.7" >&2
  echo "Provide an existing local clone at the pinned $PINNED_TAG tag." >&2
  exit 2
fi
if ! command -v make >/dev/null || ! command -v go >/dev/null; then
  echo "Build requirements missing: install make and Go, plus Android NDK and gomobile as required by sing-box." >&2
  exit 2
fi
ACTUAL_TAG="$(git -C "$SING_BOX_DIR" describe --tags --exact-match HEAD 2>/dev/null || true)"
if [[ "$ACTUAL_TAG" != "$PINNED_TAG" ]]; then
  echo "Expected sing-box tag $PINNED_TAG, found '${ACTUAL_TAG:-non-tagged checkout}'. Refusing an unverified libbox API." >&2
  exit 2
fi

make -C "$SING_BOX_DIR" lib_install
make -C "$SING_BOX_DIR" lib_android
AAR="$(find "$SING_BOX_DIR" -type f -name libbox.aar -print -quit)"
if [[ -z "$AAR" ]]; then
  echo "sing-box build completed but no libbox.aar was found; inspect the build output." >&2
  exit 1
fi
mkdir -p "$ROOT/app/libs"
cp "$AAR" "$ROOT/app/libs/libbox.aar"
echo "Installed pinned $PINNED_TAG engine at app/libs/libbox.aar"
