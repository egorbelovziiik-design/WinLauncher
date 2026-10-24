#!/usr/bin/env bash
# =============================================================================
# ReallyWorld Ultimate — Восстановление сервера из резервной копии
# Использование: ./scripts/restore.sh backups/reallyworld_backup_YYYYMMDD_HHMMSS.tar.gz
# =============================================================================
set -euo pipefail

if [[ $# -lt 1 ]]; then
  echo "Использование: $0 <путь_к_архиву.tar.gz>"
  exit 1
fi

ARCHIVE="$1"
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

if [[ ! -f "${ARCHIVE}" ]]; then
  echo "[ОШИБКА] Архив не найден: ${ARCHIVE}"
  exit 1
fi

if [[ -f "${ARCHIVE}.sha256" ]]; then
  echo "Проверка целостности SHA-256..."
  sha256sum -c "${ARCHIVE}.sha256"
fi

echo "Распаковка резервной копии в ${ROOT_DIR}..."
tar -xzf "${ARCHIVE}" -C "${ROOT_DIR}"
echo "[OK] Сервер успешно восстановлен из резервной копии!"
