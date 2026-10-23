#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
VERSION=0.743
SHA=be23707d541710a978f2c559c069e22ba9c7c449e07d60472fb4e074914bc91d
mkdir -p "$ROOT/.local/bin" "$ROOT/.local/downloads"
ARCHIVE="$ROOT/.local/downloads/luau-$VERSION.tar.gz"
if [[ ! -f "$ARCHIVE" ]]; then
  curl --fail --location --retry 3 "https://codeload.github.com/luau-lang/luau/tar.gz/refs/tags/$VERSION" --output "$ARCHIVE"
fi
printf '%s  %s\n' "$SHA" "$ARCHIVE" | sha256sum --check -
if [[ ! -d "$ROOT/.local/luau-$VERSION" ]]; then tar -xzf "$ARCHIVE" -C "$ROOT/.local"; fi
printf 'Building Luau %s (one-off, usually 3–5 minutes)…\n' "$VERSION"
make -C "$ROOT/.local/luau-$VERSION" config=release -j2 luau luau-compile luau-analyze > "$ROOT/.local/luau-build.log" 2>&1
for name in luau luau-compile luau-analyze; do install "$ROOT/.local/luau-$VERSION/build/release/$name" "$ROOT/.local/bin/$name"; done
printf 'Installed in %s/.local/bin\n' "$ROOT"
