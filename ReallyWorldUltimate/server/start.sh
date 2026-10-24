#!/usr/bin/env bash
# =============================================================================
# ReallyWorld Ultimate — Скрипт запуска сервера (Java 21 + Aikar's G1GC Flags)
# =============================================================================
set -euo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")"

MIN_RAM="${MIN_RAM:-4G}"
MAX_RAM="${MAX_RAM:-8G}"
JAR_FILE="${JAR_FILE:-server.jar}"
JAVA_BIN="${JAVA_BIN:-java}"

if ! command -v "${JAVA_BIN}" >/dev/null 2>&1; then
  echo "[ОШИБКА] Java 21+ не найдена в PATH! Установите Eclipse Temurin JDK 21."
  exit 1
fi

if [[ ! -f "${JAR_FILE}" ]]; then
  echo "[!] Файл ядра ${JAR_FILE} ещё не скачан."
  echo "    Запустите автоматический установщик: ../install.sh"
  exit 1
fi

echo "=== Запуск ReallyWorld Ultimate (RAM: ${MIN_RAM}..${MAX_RAM}) ==="
exec "${JAVA_BIN}" \
  -Xms"${MIN_RAM}" -Xmx"${MAX_RAM}" \
  -Dfile.encoding=UTF-8 \
  -XX:+UseG1GC \
  -XX:+ParallelRefProcEnabled \
  -XX:MaxGCPauseMillis=200 \
  -XX:+UnlockExperimentalVMOptions \
  -XX:+DisableExplicitGC \
  -XX:+AlwaysPreTouch \
  -XX:G1NewSizePercent=30 \
  -XX:G1MaxNewSizePercent=40 \
  -XX:G1HeapRegionSize=8M \
  -XX:G1ReservePercent=20 \
  -XX:G1HeapWastePercent=5 \
  -XX:G1MixedGCCountTarget=4 \
  -XX:InitiatingHeapOccupancyPercent=15 \
  -XX:G1MixedGCLiveThresholdPercent=90 \
  -XX:G1RSetUpdatingPauseTimePercent=5 \
  -XX:SurvivorRatio=32 \
  -XX:+PerfDisableSharedMem \
  -XX:MaxTenuringThreshold=1 \
  -Dusing.aikars.flags=https://mcflags.emc.gs \
  -Daikars.new.flags=true \
  -jar "${JAR_FILE}" --nogui
