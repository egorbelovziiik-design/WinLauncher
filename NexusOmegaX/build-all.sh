#!/usr/bin/env bash
# Builds every NEXUS CLIENT OMEGA X artefact.
#
#   ./build-all.sh              jars + launcher + source zip
#   ./build-all.sh --zip-only   only the source archive
#
#   ./build-all.sh --install    also copy the jars into ~/.minecraft/mods
set -e
cd "$(dirname "$0")"
ROOT="$(pwd)"
mkdir -p dist
install_mods=0
for arg in "$@"; do [ "$arg" = "--install" ] && install_mods=1; done

zip_only=0
[ "${1:-}" = "--zip-only" ] && zip_only=1

# ---------------------------------------------------------------- 1. mod jars
if [ "$zip_only" -eq 0 ]; then
  echo "== building the Fabric adapters (needs internet for Loom) =="
  for adapter in nexus1165 nexus1214 nexus12111; do
    if command -v gradle >/dev/null 2>&1; then
      ( cd "adapters/$adapter" && gradle build --quiet --console=plain ) \
        && cp -f "adapters/$adapter/build/libs/NexusOmegaX-"*.jar "$ROOT/dist/" 2>/dev/null \
        && echo "   OK $adapter" \
        || echo "   SKIP $adapter (gradle unavailable or offline - see BUILD_INSTRUCTIONS.md)"
    else
      echo "   SKIP $adapter (gradle is not installed)"
    fi
  done
fi

# ------------------------------------------------------------- 2. the launcher
if [ "$zip_only" -eq 0 ]; then
  echo "== building NexusInjector.exe =="
  ( cd launcher && ./build.sh ) && cp -f launcher/build/NexusInjector.exe dist/NexusInjector.exe \
    && echo "   OK dist/NexusInjector.exe"
fi

# ------------------------------------------------------------ 3. source archive
echo "== packing NexusOmegaX-Source.zip =="
rm -f NexusOmegaX-Source.zip
zip -q -r NexusOmegaX-Source.zip \
    api core modules adapters launcher test \
    README.md BUILD_INSTRUCTIONS.md TEST_REPORT.md \
    build-all.sh build.bat install-mods.bat \
    -x '*/build/*' '*/build/libs/*' '*/.gradle/*' '*/dist/*'
echo "   OK NexusOmegaX-Source.zip ($(du -h NexusOmegaX-Source.zip | cut -f1))"
echo
if [ "$install_mods" -eq 1 ]; then
  MODS="${HOME}/.minecraft/mods"
  if [ ! -d "${HOME}/.minecraft" ]; then
    echo "   ~/.minecraft not found - start the official launcher once"
  else
    mkdir -p "$MODS" && cp -f dist/NexusOmegaX-*.jar "$MODS"/ 2>/dev/null       && echo "   installed into $MODS"
  fi
fi

echo "artefacts in $(pwd)/dist:"
ls -1 dist 2>/dev/null || echo "  (none - build the jars with gradle, see BUILD_INSTRUCTIONS.md)"
