#!/usr/bin/env bash
# =============================================================================
# ReallyWorld Ultimate — Резервное копирование БД, миров и конфигураций
# =============================================================================
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
BACKUP_DIR="${ROOT_DIR}/backups"
KEEP_COUNT="${KEEP_COUNT:-7}"
STAMP="$(date -u +%Y%m%d_%H%M%S)"
ARCHIVE="${BACKUP_DIR}/reallyworld_backup_${STAMP}.tar.gz"

mkdir -p "${BACKUP_DIR}"
echo "=== Создание резервной копии: ${ARCHIVE} ==="

tar -czf "${ARCHIVE}" \
  -C "${ROOT_DIR}" \
  server/server.properties \
  server/bukkit.yml \
  server/spigot.yml \
  server/purpur.yml \
  server/config \
  server/plugins/ReallyCore \
  server/plugins/LuckPerms \
  server/plugins/WorldGuard \
  server/plugins/TAB \
  server/plugins/GrimAC \
  server/world \
  server/world_nether \
  server/world_the_end

sha256sum "${ARCHIVE}" > "${ARCHIVE}.sha256"

# Ротация старых архивов (оставляем последние KEEP_COUNT)
ls -1t "${BACKUP_DIR}"/reallyworld_backup_*.tar.gz 2>/dev/null | tail -n +$((KEEP_COUNT + 1)) | xargs -r rm -f

echo "[OK] Резервная копия успешно сохранена и проверена!"
