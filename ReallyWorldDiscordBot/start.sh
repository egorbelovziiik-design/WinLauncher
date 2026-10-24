#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"

echo "===================================================================="
echo "  Запуск Discord-бота ReallyWorld КП"
echo "  Главный владелец: robik0277 (ID: 1381255368650526861)"
echo "===================================================================="

if [ ! -d ".venv" ]; then
  echo "[1/3] Создаю виртуальное окружение Python..."
  python3 -m venv .venv
fi

echo "[2/3] Проверяю зависимости..."
./.venv/bin/pip install -q -r requirements.txt

if [ ! -f ".env" ]; then
  cp .env.example .env
  echo "[!] Создан файл .env. Впишите ваш DISCORD_TOKEN в файл .env и запустите ./start.sh повторно!"
  exit 0
fi

echo "[3/3] Запускаю бота..."
exec ./.venv/bin/python main.py
