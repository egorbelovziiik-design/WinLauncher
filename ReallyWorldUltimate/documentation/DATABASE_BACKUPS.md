# Базы Данных, Миграции и Резервное Копирование (DATABASE & BACKUPS)

## 1. Поддерживаемые СУБД (`StorageModule`)
Плагин `ReallyCore` поддерживает 3 промышленных SQL-движка с автоматическим применением миграций при старте:
1. **SQLite 3 в режиме `WAL` (по умолчанию, Zero-Config):**
   - База уже инициализирована по пути `server/plugins/ReallyCore/data/reallycore.db`.
   - Автоматически включаются `PRAGMA journal_mode = WAL`, `PRAGMA synchronous = NORMAL`, `PRAGMA foreign_keys = ON`, `PRAGMA busy_timeout = 5000` и честная блокировка записи `ReentrantLock(true)`.
2. **MariaDB 10.11+ / 11.4+ (для высоких нагрузок и кластеров):**
   - Скрипт схемы: `database/migrations/V1__initial_schema_mariadb.sql`.
   - Переключение в `server/plugins/ReallyCore/config.yml`: `storage.type: "MARIADB"`, пароль передаётся через переменную окружения `${RW_DB_PASSWORD}`.
3. **PostgreSQL 15+ / 16+:**
   - Скрипт схемы: `database/migrations/V1__initial_schema_postgresql.sql`.
   - Переключение: `storage.type: "POSTGRESQL"`.

---

## 2. Таблицы схемы V1 (16 таблиц)
- `schema_migrations` — контроль версий схемы БД
- `players` — профили, 3 целочисленные валюты (`coins`, `rilliks`, `tokens`), ранги, статистика и настройки
- `economy_transactions` — неизменяемый аудит всех операций с валютами
- `player_homes`, `server_warps`, `player_cooldowns`, `player_ignores` — телепортация и персистентные кулдауны
- `auction_listings` — лоты аукциона `/ah` с индексами по статусу, категории и сроку истечения
- `clans`, `clan_members`, `clan_relations`, `clan_progress` — клановая система
- `player_case_keys`, `case_history` — ключи кейсов и история дропа
- `player_quests`, `player_claimed_rewards` — прогресс квестов и защита от повторного получения наград
- `security_audit_log` — журнал событий безопасности

---

## 3. Резервное копирование и восстановление
- **Создание бэкапа (Linux/macOS):** `./scripts/backup.sh` — упаковывает миры, конфиги и `reallycore.db` в `backups/reallyworld_backup_YYYYMMDD_HHMMSS.tar.gz`, записывает контрольную сумму `.sha256` и хранит последние 7 копий.
- **Создание бэкапа (Windows):** `scripts\backup.bat`.
- **Восстановление с проверкой SHA-256:**
  ```bash
  ./scripts/restore.sh backups/reallyworld_backup_20261024_164500.tar.gz
  ```
- Рекомендуемое расписание `cron` (каждые 6 часов):
  ```cron
  0 */6 * * * /opt/reallyworld/ReallyWorldUltimate/scripts/backup.sh >> /var/log/rw-backup.log 2>&1
  ```
