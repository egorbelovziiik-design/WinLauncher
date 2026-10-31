#!/usr/bin/env bash
# KINGGRIEF v7.0 — Paper launcher; optional maintenance stub after a normal stop.
# Set SERVER_JARFILE, JAVA, JAVA_XMS and JAVA_XMX to match your host.
set -u
JAR_FILE="${SERVER_JARFILE:-server.jar}"
if [ ! -f "$JAR_FILE" ] && [ "$JAR_FILE" = "server.jar" ] && [ -f "paper.jar" ]; then
  JAR_FILE="paper.jar"
fi
if [ ! -f "$JAR_FILE" ]; then
  echo "[KINGGRIEF] Нет $JAR_FILE. Установите Paper 1.21.4 отдельно и задайте SERVER_JARFILE." >&2
  exit 1
fi
JAVA_CMD="${JAVA:-java}"
XMS="${JAVA_XMS:-512M}"
XMX="${JAVA_XMX:-2G}"
echo "[KINGGRIEF] Запуск $JAR_FILE: Xms=$XMS, Xmx=$XMX. Оставьте память для системы и плагинов."
"$JAVA_CMD" "-Xms$XMS" "-Xmx$XMX" -XX:+UseG1GC -jar "$JAR_FILE" nogui
status=$?
if [ "$status" -ne 0 ]; then
  echo "[KINGGRIEF] Paper завершился с кодом $status. Проверьте лог и память хостинга." >&2
  # Do not hide an unsuccessful server startup behind a working maintenance stub.
  if [ "${MAINTENANCE_ON_FAILURE:-0}" != "1" ]; then exit "$status"; fi
fi
if [ "${MAINTENANCE_ON_EXIT:-1}" != "1" ]; then exit "$status"; fi
if [ ! -f TechWorksStub.jar ]; then
  echo "[KINGGRIEF] TechWorksStub.jar отсутствует; экран техработ не запущен." >&2
  exit 1
fi
echo "[KINGGRIEF] Сервер остановлен. Включаем экран «⚙ Тех работы!» на порту сервера."
exec "$JAVA_CMD" -Xmx64M -jar TechWorksStub.jar
