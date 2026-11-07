# AXSESCLIENT v6.0.0 (CELESTIAL HYPERION ULTRA) — Отчёт о сборке и верификации

- **Версия релиза**: `v6.0.0` (`axsesclient-v6.0.0`, коммит `2385dfd`)
- **Проверенный CI Run**: `42403624458` (GitHub Actions Linux Xvfb + Windows Server x64 + GitHub Release)
- **Синхронизированных модулей**: `124` (`Кастомный Хотбар и Щит`, `Анимации Рук и Меча 3D`, `Атмосфера и Светлячки 3D`, `Таргет Стрейф 3D`, `ФастБридж Pro: Ниндзя Игл / ГодБридж 75.6 / Бризли / СейфБридж`, `Неоновый Контур Блока`, `BedWars ESP и Кровати`, `Защита от Фаерболов и ТНТ`, `Авто-Инструмент и Меч`, `Авто-Клатч Блоками`, `Детектор Инвизок и Угроз`, `Неоновый Шлейф и Кибер-Нимб`, `КиллАура v4: SnowGrim / Grim / Matrix / Vulcan / Intave / Polar`, `Перемещение HUD в Чате`, `Музыка и Spotify Плеер`, `Баритон Автопилот`, `Турбо Оптимизация FPS` и др.)
- **Встроенных тем**: `14` (`CELESTIAL VISION`, `EMERALD AURORA`, `HYPERION AURORA`, `AXSES DARK` и др. + пользовательские JSON-темы)
- **Не-ванильный графический конвейер `Celestial Frosted Glass + 3D Volumetric`**:
  - Трёхслойное неоновое свечение (`drawFrostedGlassPanel`), обсидиановое матовое стекло с верхним зеркальным бликом, двухцветные градиентные SDF-рамки `1.15px` и кастомные векторные иконки во всех HUD-капсулах (никаких плоских прямоугольников).
  - Полигональная 3D-заливка `fillTriangle` / `fillQuad` / `draw3dVolumetricPrism` во всех трёх платформах (`1.16.5`, `1.21.4`, `1.21.11`) для объёмных 3D Chams игроков, 38-гранного конуса `ChinaHat`, сонарного луча 2D-радара и 3D-призм кроватей/блоков BedWars.
  - Полная замена ванильного хотбара и сердечек на `glass_hotbar_dock` с перехватом `GuiMixin` (`1.16.5`, `1.21.4`, `1.21.11`).

## Собранные и проверенные артефакты (`SHA-256`)

| Файл | Размер | SHA-256 |
| --- | --- | --- |
| `AXSESCLIENT-Setup.exe` | 42,068,265 байт | `9f0520093dbcb89d9360ba500bfa470702aa2855ec2791ca45c7bdda2e38f09d` |
| `AXSESCLIENT.exe` | 45,593,348 байт | `77fb091c21c5fa79a21920385f05f1f599844ccf517fea1b3a02adedfeae0318` |
| `AXSESCLIENT-1.16.5.jar` | 603,585 байт | `b7883f99924cacd7dc4b7745237f57c6a9fc32cd9188a67df2a869d97d829f2b` |
| `AXSESCLIENT-1.21.4.jar` | 610,778 байт | `84dbf76a2e28f6f796d297d50913b094d3e7ab93d4b6c15eb26a00265f600e85` |
| `AXSESCLIENT-1.21.11.jar` | 612,325 байт | `a8e53dcfaa6f82d8443d9fd20e6c49736fed929b4858f7bae3d60ca3ab9a744c` |

## Результаты автоматического E2E-тестирования

1. **`client-common` JUnit 5**: PASS — проверены все **124 модуля** и **14 тем**, полигональный 3D-рендер `fillTriangle`/`fillQuad`/`drawFrostedGlassPanel`/`draw3dVolumetricPrism`, работа `glass_hotbar_dock`, `viewmodel_animations`, `world_ambience`, `target_strafe` (с защитой от падения в бездну), `FastBridge`, `BedWars ESP`, `Block Contour Pro`, перетаскивание HUD в открытом Чате (`T`), кнопки `Spotify` с авто-стопом музыки Minecraft и профили `KillAura v4`.
2. **Реальный E2E-запуск Minecraft `1.16.5` (Java 8 `1.8.0_512`)**: PASS (`mixinAudit: PASS`, `modulesRegistered: 124`, `themesRegistered: 14`, байткод `Major 52`).
3. **Реальный E2E-запуск Minecraft `1.21.4` (Java 21 `21.0.13`)**: PASS (`mixinAudit: PASS`, `modulesRegistered: 124`, `themesRegistered: 14`, байткод `Major 65`).
4. **Реальный E2E-запуск Minecraft `1.21.11` (Java 21 `21.0.13`)**: PASS (`mixinAudit: PASS`, `modulesRegistered: 124`, `themesRegistered: 14`, байткод `Major 65`).
5. **Windows x64 `AXSESCLIENT.exe` (`--selftest` + Avalonia UI + `FabricModInstallerService` xUnit)**: PASS (`DPAPI=dpapi`, `modules=124`, `themes=14`, отрендерены все **12 страниц** лаунчера).
6. **Windows x64 `AXSESCLIENT-Setup.exe` (Inno Setup 6)**: PASS — проверены тихая установка, запуск установленного лаунчера и безопасное удаление с полным сохранением пользовательских миров (`saves/`).

## Честные ограничения

- Все боевые, мостостроительные (`FastBridge` / `Bridge Clutch` / `TargetStrafe 3D`) и визуальные модули работают исключительно на стороне клиента через стандартный ввод/состояние клиента и точную математику углов/хитбоксов (без эксплойтов пакетов, спуфинга протокола или обхода лицензии Mojang).
- Для версии `1.21.11`, если внешний релиз Baritone под конкретный патч ещё не опубликован апстримом, клиент автоматически использует встроенный ИИ-автопилот и 3D-маршрутизатор `AXSES Baritone`.
