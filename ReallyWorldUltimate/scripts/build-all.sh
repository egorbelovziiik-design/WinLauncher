#!/usr/bin/env bash
# =============================================================================
# ReallyWorld Ultimate — Полная пересборка исходников ReallyCore и ресурспака
# Поддерживает Gradle 8.8+ и Maven 3.9+ на Java 21
# =============================================================================
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SRC_DIR="${ROOT_DIR}/source/ReallyCore"

echo "=== Сборка плагина ReallyCore (Java 21) ==="
cd "${SRC_DIR}"

if command -v gradle >/dev/null 2>&1; then
  gradle clean test jar
  cp -f build/libs/ReallyCore.jar "${ROOT_DIR}/server/plugins/ReallyCore.jar"
elif command -v mvn >/dev/null 2>&1; then
  mvn clean test package
  cp -f target/ReallyCore.jar "${ROOT_DIR}/server/plugins/ReallyCore.jar"
else
  echo "[ОШИБКА] Установите Gradle 8.8+ или Maven 3.9+ для пересборки из исходников."
  exit 1
fi

echo "=== Пересборка ресурспака 1.21.4–1.21.11 ==="
cd "${ROOT_DIR}/resourcepack/pack"
zip -qr -FS "../ReallyWorld-ResourcePack-1.21.4-1.21.11.zip" .
sha1sum "../ReallyWorld-ResourcePack-1.21.4-1.21.11.zip" | awk '{print $1}' > "../SHA1.txt"

echo "[OK] Сборка завершена: ${ROOT_DIR}/server/plugins/ReallyCore.jar"
