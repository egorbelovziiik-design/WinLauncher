#!/usr/bin/env bash
# KINGGRIEF v5.8 — Автозапуск сервера + Оффлайн-заглушка «⚙ Тех работы!»
# Если основной сервер (server.jar / paper.jar) выключен или перезапускается,
# TechWorksStub.jar автоматически держит порт и пишет игрокам при входе: «⚙ Тех работы!»

set -u
JAR_FILE="${SERVER_JARFILE:-server.jar}"
if [ ! -f "$JAR_FILE" ] && [ -f "paper.jar" ]; then
  JAR_FILE="paper.jar"
fi

if [ -f "$JAR_FILE" ]; then
  java -Xms2G -Xmx6G -XX:+UseG1GC -jar "$JAR_FILE" nogui
  echo "[KINGGRIEF] Основной сервер остановлен. Включаем экран «⚙ Тех работы!» (TechWorksStub.jar)..."
fi

exec java -Xmx64M -jar TechWorksStub.jar
