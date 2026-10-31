# AXSESCLIENT v2.5.0 (OBSIDIAN AURORA) — Отчёт о сборке и верификации

- **Версия релиза**: `v2.5.0` (`axsesclient-v2.5.0`)
- **Проверенный CI Run**: `41192490115` (GitHub Actions Linux Xvfb + Windows Server x64)
- **Синхронизированных модулей**: `96` (`КиллАура v2.5`, `ESP Подсветка`, `ESP Предметов`, `Трейсеры`, `Таргет HUD`, `ТриггерБот` и др.)
- **Встроенных тем**: `8` (`OBSIDIAN AURORA` палитры + пользовательские темы)

## Собранные и проверенные артефакты (`SHA-256`)

| Файл | Размер | SHA-256 |
| --- | --- | --- |
| `AXSESCLIENT-Setup.exe` | 41,703,068 байт | `fe247da24478868439586dda22a29d18b0084317662869d58c3bf7bc2593e474` |
| `AXSESCLIENT.exe` | 45,572,876 байт | `4b3cac5207539c56695a3f55fb7c8aee1769743c1e55e5afe0e0fc9b7fa60f7f` |
| `AXSESCLIENT-1.16.5.jar` | 416,496 байт | `7e349fa924ffec5fa5f743dc734e18313ef391f5008d6f733be4778741b73d22` |
| `AXSESCLIENT-1.21.4.jar` | 423,249 байт | `09d4aa3d0ac2f0c0016bda3bceabe5716bc73a223d04e7544195d35ea1470dee` |
| `AXSESCLIENT-1.21.11.jar` | 424,706 байт | `12e4f1f1b9a6698e7a1ae7e8b96bcc77b0e8971457c048f188b6e807b17a50a4` |

## Результаты автоматического E2E-тестирования

1. **`client-common` JUnit 5**: PASS — проверены все **96 модулей**, профили `KillAura` (`FunTime` / `ReallyWorld` / `HolyWorld`), формула `Mouse GCD Fix`, пересечение луча с хитбоксом (`Ray-AABB`), пауза при еде, проекция `Entity ESP` и `Tracers`, плавная прокрутка `mouseScrolled` без сброса дельты `beginFrame()`, а также асинхронное сохранение конфигов и русский перевод настроек.
2. **Реальный E2E-запуск Minecraft `1.16.5` (Java 8 `1.8.0_504`)**: PASS (`mixinAudit: PASS`, `modulesRegistered: 96`, байткод `Major 52`).
3. **Реальный E2E-запуск Minecraft `1.21.4` (Java 21 `21.0.13`)**: PASS (`mixinAudit: PASS`, `modulesRegistered: 96`, байткод `Major 65`).
4. **Реальный E2E-запуск Minecraft `1.21.11` (Java 21 `21.0.13`)**: PASS (`mixinAudit: PASS`, `modulesRegistered: 96`, байткод `Major 65`).
5. **Windows x64 `AXSESCLIENT.exe` (`--selftest` + Avalonia UI)**: PASS (`DPAPI=dpapi`, `modules=96`, `themes=8`, синхронизация `options.txt -> lang:ru_ru`, отрендерены все 11 страниц интерфейса).
6. **Windows x64 `AXSESCLIENT-Setup.exe` (Inno Setup 6)**: PASS — проверены тихая установка, запуск установленного лаунчера и безопасное удаление с полным сохранением пользовательских миров (`saves/`).
