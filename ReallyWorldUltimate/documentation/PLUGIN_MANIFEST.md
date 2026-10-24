# PLUGIN_MANIFEST.md — Реестр серверного ядра и плагинов

## 1. Принцип отбора плагинов

В соответствии с архитектурными требованиями проекта:
- Мы **не перегружаем** сервер десятками конфликтующих сторонних плагинов (`EssentialsX`, `DeluxeMenus`, `ExcellentCrates`, `CombatLogX`, `BetterRTP`, `PlayerPoints`, `Citizens` **намеренно исключены**, так как все их функции нативно, быстрее, без конфликтов БД и в едином стиле реализованы внутри единого модульного плагина **`ReallyCore.jar`**).
- Все внешние плагины выбраны исключительно из проверенных официальных открытых источников (**PaperMC / PurpurMC / Modrinth / GitHub**) с прозрачными открытыми лицензиями.
- Ни один пиратский, слитый или фейковый JAR-файл в сборку не включён.
- Автоматический установщик `install.sh` / `install.bat` скачивает внешние бинарные релизы напрямую по официальным API (`api.purpurmc.org`, `api.modrinth.com`) и сверяет криптографическую контрольную сумму **SHA-256 / SHA-512**, возвращаемую официальным реестром, записывая итоговый файл `server/checksums.sha256`.

---

## 2. Включённые собственные плагины (поставляются в виде готового JAR + полных исходников)

| Плагин | Файл | Версия | Лицензия | Назначение | Статус в архиве |
|---|---|---|---|---|---|
| **ReallyCore** | `server/plugins/ReallyCore.jar` | `1.0.0` | MIT (авторский код проекта) | Единое модульное ядро ReallyWorld Ultimate: Анархия, Экономика (Монеты/Риллики/Ромашки), Магазин и Скупщик, Аукцион `/ah`, Кланы `/clan`, PvP и Combat Tag, 15 Сфер, 12 Талисманов, 9 Артефактов, Оружие и Броня, Зелья и Расходники (Трапки, Пласты, Божья аура), 8 Кейсов, Киты, 18 GUI, Scoreboard/TAB, 10 Ивентов + 5 Боссов, Квесты, Боевой пропуск, Награды, Статистика, NPC/Голограммы/Спавн, Антидюп и Безопасность, интеграции LuckPerms/Vault/PAPI. | **Включён скомпилированный рабочий JAR** (`SHA-256: 7b365ff58ad2610816317e1ba77d425df07351e4e81ba1d81089a1fcc70d4930`, исходники в `source/ReallyCore/`) |

> **Критическое преимущество архитектуры:** Сервер способен полноценно работать даже в минимальной конфигурации **`Purpur/Paper 1.21.11 + ReallyCore.jar`**, так как для прав, экономики, скорборда, таба, регионов спавна, голограмм и NPC в `ReallyCore` предусмотрены собственные самодостаточные реализации, которые автоматически делегируют часть функций внешним плагинам (`LuckPerms`, `Vault`, `PlaceholderAPI`, `WorldGuard`, `TAB`), если они установлены!

---

## 3. Рекомендуемые официальные внешние компоненты (автоматически устанавливаются `install.sh` / `install.bat`)

| Компонент | Рекомендуемая версия | Лицензия | Официальный источник / API загрузки | Роль в сборке и готовая конфигурация |
|---|---|---|---|---|
| **Purpur Server** *(основное ядро)* | `1.21.11` build `#2568` *(или `1.21.4` build `#2416`)* | MIT | `https://api.purpurmc.org/v2/purpur/1.21.11/2568/download` | Высокопроизводительный форк Paper с тонкими настройками TPS/сущностей. Конфигурации `purpur.yml`, `config/paper-global.yml`, `config/paper-world-defaults.yml` уже включены в `server/`. |
| **Paper Server** *(альтернативное ядро)* | `1.21.11` build `#116` | GPL-3.0 / MIT patches | `https://fill.papermc.io` / `https://papermc.io/downloads/paper` | Эталонное серверное ядро, против API которого собран `ReallyCore.jar`. |
| **ViaVersion** | `5.12.1+` | GPL-3.0 | Modrinth (`P1OZGk5p`) / `https://github.com/ViaVersion/ViaVersion` | Сетевой протокольный мост для мультиверсионного подключения `1.21.4–1.21.11`. Готовый `server/plugins/ViaVersion/config.yml` включён. |
| **ViaBackwards** | `5.12.1+` | GPL-3.0 | Modrinth (`NpvuJQoq`) / `https://github.com/ViaVersion/ViaBackwards` | Поддержка входа клиентов `1.21.4–1.21.10` на сервер `1.21.11`. Готовый `server/plugins/ViaBackwards/config.yml` включён. |
| **LuckPerms** | `5.5.x` (Bukkit) | MIT | Modrinth (`Vebnzrzj`) / `https://luckperms.net` | Управление группами привилегий (`HERO`..`GOD`, `HELPER`, `ADMIN`). Все 13 групп преднастроены в `server/plugins/LuckPerms/yaml-storage/groups/`. |
| **VaultUnlocked / Vault** | `2.x` / `1.7.3` | LGPL-3.0 | Modrinth (`vaultunlocked` / GitHub `MilkBowl/Vault`) | Универсальный мост экономики (`ReallyCore` автоматически регистрируется как провайдер `Vault Economy`). |
| **PlaceholderAPI** | `2.11.6+` | GPL-3.0 | Modrinth (`lKEzGugV`) / `https://github.com/PlaceholderAPI/PlaceholderAPI` | Обмен плейсхолдерами `%reallycore_*%` между плагинами. |
| **WorldEdit** | `7.3.x+` (1.21.4–1.21.11) | GPL-3.0 | Modrinth (`1u6JkXh5`) / EngineHub | Зависимость для WorldGuard и инструмент администрирования карт. |
| **WorldGuard** | `7.0.13+` (1.21.4–1.21.11) | LGPL-3.0 | Modrinth (`DKY9btbd`) / EngineHub | Защита центральной зоны спавна и арены (`server/plugins/WorldGuard/worlds/world/regions.yml` преднастроен). |
| **TAB** | `5.0.x+` | Unlicense / MIT (NEZNAMY) | Modrinth (`tab-was-taken`) / `https://github.com/NEZNAMY/TAB` | Расширенная сортировка таба (опционально — `ReallyCore` также имеет встроенный TAB/Scoreboard движок). Конфиг в `server/plugins/TAB/` преднастроен. |
| **GrimAC** | `2.3.70+` | GPL-3.0 | Modrinth (`LJNGWSvH`) / `https://github.com/GrimAnticheat/Grim` | Пакетный симуляционный античит для 1.21. Конфигурации снижения ложных срабатываний в `server/plugins/GrimAC/` преднастроены. |
| **CoreProtect** | `22.4+` / `23.x` | Artistic-2.0 | Modrinth (`Lu3KuzdV`) | Логирование блоков и откат рейдов для модерации. Конфиг в `server/plugins/CoreProtect/` преднастроен. |
| **spark** | Встроен в Paper/Purpur `1.21+` | GPL-3.0 | Входит в состав Paper/Purpur `1.21.4–1.21.11` из коробки! | Профилирование TPS, MSPT, GC и потоков (отдельная установка JAR на Paper/Purpur 1.21+ не требуется!). |

---

## 4. Рассмотренные и обоснованно отклонённые кандидаты

| Кандидат | Причина отказа в пользу `ReallyCore` |
|---|---|
| `EssentialsX` | Пересекается с кастомной экономикой, китами, домами, РТП и локализацией ReallyWorld; увеличивает время старта и дублирует БД. |
| `DeluxeMenus` | Требует громоздких скриптов кликов и подвержен ошибкам синхронизации условий; в `ReallyCore` все 15 меню строго типизированы и защищены от дюпа. |
| `ExcellentCrates` | В `ReallyCore` реализована нативная система из 8 кейсов с атомарными ключами в единой транзакционной БД SQLite/SQL. |
| `CombatLogX` / `BetterRTP` / `PlayerPoints` | Заменены нативными модулями `CombatModule`, `PlayerModule` и мультивалютным `EconomyModule` внутри `ReallyCore`, что устраняет рассинхронизацию Combat Tag с кастомными предметами, трапками, флай-режимом и захватами ивентов. |
| `Citizens` / `DecentHolograms` | На версиях `1.21.4–1.21.11` часто ломаются при минорных обновлениях NMS; `ReallyCore` использует официальный стабильный Paper API (`TextDisplay` + защищённые интерактивные сущности с `PersistentDataContainer`). |
