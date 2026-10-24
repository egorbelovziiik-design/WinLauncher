# ReallyWorld Ultimate [Minecraft Java Edition 1.21.4 — 1.21.11]

Полностью готовая к продакшену, модульная, русскоязычная серверная сборка анархо-гриферского выживания в духе **ReallyWorld** на базе **Purpur / Paper** с собственным скомпилированным центральным плагином **`ReallyCore.jar` (Java 21)**, кросс-версионным ресурспаком **`1.21.4–1.21.11`**, авто-архитектором Спавна, защитой от дюпов и полным комплектом документации и автотестов.

---

## Быстрый запуск (Linux / macOS / Windows / Docker)

### Linux / macOS
```bash
chmod +x install.sh scripts/*.sh server/start.sh
./install.sh          # Скачивает официальный Purpur/Paper 1.21.11 и внешние плагины с проверкой SHA-512
cd server && ./start.sh
```

### Windows 10 / 11 / Server
1. Установите **OpenJDK 21 LTS** ([Adoptium Temurin 21](https://adoptium.net/)) и **Python 3.10+**.
2. Запустите **`install.bat`** в корне проекта.
3. Запустите **`server\start.bat`**.

### Docker Compose
```bash
cp .env.example .env
docker compose up -d --build
```

---

## Состав проекта
- **`server/`** — настроенный сервер (`online-mode=true`), `plugins/ReallyCore.jar` + 25 русских YAML-конфигураций, инициализированная БД `reallycore.db` (SQLite WAL), 13 групп `LuckPerms`, регионы `WorldGuard`, конфиги `ViaVersion`, `ViaBackwards`, `TAB`, `GrimAC`, `CoreProtect`.
- **`source/ReallyCore/`** — полный чистый исходный код `ReallyCore` на Java 21 (Gradle Kotlin DSL + Maven) и модульно-интеграционный набор тестов `ReallyCoreSuiteTest` (JUnit 4 + SQLite).
- **`resourcepack/`** — готовый ресурспак `ReallyWorld-ResourcePack-1.21.4-1.21.11.zip` (57 моделей и пиксель-арт текстур Сфер, Талисманов, Артефактов, Оружия и Расходников, `pack_format 46..75`).
- **`database/migrations/`** — SQL-схемы V1 для `SQLite`, `MariaDB` и `PostgreSQL`.
- **`scripts/`** — `install.sh/.bat`, `build-all.sh/.bat`, `backup.sh/.bat`, `restore.sh`.
- **`documentation/`** — 18 подробных руководств на русском языке (исследование 50 механик, совместимость `1.21.4–1.21.11`, гайды игрока и администратора, безопасность, отчёт о тестировании).
