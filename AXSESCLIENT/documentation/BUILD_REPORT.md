# AXSESCLIENT v5.0.0 (HYPERION APEX) — Отчёт о сборке и верификации

- **Версия релиза**: `v5.0.0` (`axsesclient-v5.0.0`)
- **Проверенный CI Run**: `42371101710` (GitHub Actions Linux Xvfb + Windows Server x64 + GitHub Release)
- **Синхронизированных модулей**: `120` (`ФастБридж Pro: Ниндзя Игл / ГодБридж 75.6 / Бризли / СейфБридж`, `Неоновый Контур Блока`, `BedWars ESP и Кровати`, `Защита от Фаерболов и ТНТ`, `Авто-Инструмент и Меч`, `Авто-Клатч Блоками`, `Детектор Инвизок и Угроз`, `Неоновый Шлейф и Кибер-Нимб`, `КиллАура v4: SnowGrim / Grim / Matrix / Vulcan / Intave / Polar`, `Перемещение HUD в Чате`, `Музыка и Spotify Плеер`, `Баритон Автопилот`, `Турбо Оптимизация FPS` и др.)
- **Встроенных тем**: `14` (`CELESTIAL VISION`, `EMERALD AURORA`, `HYPERION AURORA`, `AXSES DARK` и др. + пользовательские JSON-темы)
- **Конвейер рендеринга контуров**: `Hyperion SDF Sub-Pixel` (`beginHiDpiSubPixel` / `endHiDpiSubPixel` для физического масштаба `1x1` в OpenGL `PoseStack` / `Matrix3x2fStack`, Евклидово SDF-сглаживание `1.15px` на всех скруглённых рамках, перпендикулярное сглаживание наклонных линий Лянга–Барски и двухслойные 3D неоновые призмы).

## Собранные и проверенные артефакты (`SHA-256`)

| Файл | Размер | SHA-256 |
| --- | --- | --- |
| `AXSESCLIENT-Setup.exe` | 42,022,566 байт | `8983a2fb429c43401797d126d5ff0e021411dfefe9ab9fad0f8f7ff6339560e7` |
| `AXSESCLIENT.exe` | 45,593,350 байт | `de2f9dc45dca6ae5ee181a7258002acfa7702fbf2292d1e8407db47bfc4303ae` |
| `AXSESCLIENT-1.16.5.jar` | 576,098 байт | `fbd26b4c7c232940aed8e5cdc0ed923dec7758350eb533cdb731eddfae29c13c` |
| `AXSESCLIENT-1.21.4.jar` | 583,310 байт | `9ae4839221b595da5c46b2cd6671e85737e43cbeab33f45024ae15bec6f9d4e8` |
| `AXSESCLIENT-1.21.11.jar` | 584,855 байт | `a436fcdc0ca56cd21f44c34606ca5ce9a6b93312c9b6741f979a6ad01dc648a3` |

## Результаты автоматического E2E-тестирования

1. **`client-common` JUnit 5**: PASS — проверены все **120 модулей** и **14 тем**, работа `FastBridge` (авто-слот шерсти, авто-присед на краю блока над бездной, быстрая установка блоков и гарантированный отпуск приседа при выключении), авто-отбитие фаерболов (`fireball_tnt_alert`), `BedWars ESP`, `Block Contour Pro`, сглаженные SDF-контуры, перетаскивание HUD в открытом Чате (`T`), кнопки `Spotify` с авто-стопом музыки Minecraft и профили `KillAura v4`.
2. **Реальный E2E-запуск Minecraft `1.16.5` (Java 8 `1.8.0_512`)**: PASS (`mixinAudit: PASS`, `modulesRegistered: 120`, `themesRegistered: 14`, байткод `Major 52`).
3. **Реальный E2E-запуск Minecraft `1.21.4` (Java 21 `21.0.13`)**: PASS (`mixinAudit: PASS`, `modulesRegistered: 120`, `themesRegistered: 14`, байткод `Major 65`).
4. **Реальный E2E-запуск Minecraft `1.21.11` (Java 21 `21.0.13`)**: PASS (`mixinAudit: PASS`, `modulesRegistered: 120`, `themesRegistered: 14`, байткод `Major 65`).
5. **Windows x64 `AXSESCLIENT.exe` (`--selftest` + Avalonia UI + `FabricModInstallerService` xUnit)**: PASS (`DPAPI=dpapi`, `modules=120`, `themes=14`, отрендерены все **12 страниц** лаунчера).
6. **Windows x64 `AXSESCLIENT-Setup.exe` (Inno Setup 6)**: PASS — проверены тихая установка, запуск установленного лаунчера и безопасное удаление с полным сохранением пользовательских миров (`saves/`).

## Честные ограничения

- Все боевые, мостостроительные (`FastBridge` / `Bridge Clutch`) и визуальные модули работают исключительно на стороне клиента через стандартный ввод/состояние клиента и точную математику углов/хитбоксов (без эксплойтов пакетов, спуфинга протокола или обхода лицензии Mojang).
- Для версии `1.21.11`, если внешний релиз Baritone под конкретный патч ещё не опубликован апстримом, клиент автоматически использует встроенный ИИ-автопилот и 3D-маршрутизатор `AXSES Baritone`.
