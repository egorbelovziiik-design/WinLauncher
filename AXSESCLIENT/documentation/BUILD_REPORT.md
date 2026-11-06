# AXSESCLIENT v4.0.0 (APEX ARCHITECT) — Отчёт о сборке и верификации

- **Версия релиза**: `v4.0.0` (`axsesclient-v4.0.0`)
- **Проверенный CI Run**: `42286031798` (GitHub Actions Linux Xvfb + Windows Server x64 + GitHub Release)
- **Синхронизированных модулей**: `112` (`Перемещение HUD в Чате`, `Музыка и Spotify Плеер`, `Баритон Автопилот`, `Турбо Оптимизация FPS`, `КиллАура v4: SnowGrim / Grim / Matrix / Vulcan / Intave / Polar / FunTime / RW / HW`, `ESP Неон-капсула`, `Мини-Радар 2D`, `Счётчик тотемов`, `Геплы и перлы`, `Индикатор урона 3D`, `Таргет HUD` и др.)
- **Встроенных тем**: `14` (`CELESTIAL VISION`, `EMERALD AURORA`, `HYPERION AURORA`, `AXSES DARK` и др. + пользовательские JSON-темы)
- **Каталог модов лаунчера**: `12` проверенных модов (`Baritone`, `Sodium`, `Lithium`, `FerriteCore`, `ImmediatelyFast`, `EntityCulling`, `Iris`, `ModernFix`, `Dynamic FPS`, `Mod Menu`, `Zoomify`, `Fabric API`) с криптографической верификацией SHA-512 / SHA-1.

## Собранные и проверенные артефакты (`SHA-256`)

| Файл | Размер | SHA-256 |
| --- | --- | --- |
| `AXSESCLIENT-Setup.exe` | 41,931,002 байт | `e14f2605533154817c9590b10f3acf5a8cad60232b0ec6abde3f4091191dd119` |
| `AXSESCLIENT.exe` | 45,593,352 байт | `16c55f609aa65449e288e21a755e95acfbffd9d49e9ba4b3be8987b6b0431bdf` |
| `AXSESCLIENT-1.16.5.jar` | 529,405 байт | `feb5ca60137cbccfca31de844fef4eaf516ab07f1b4386c5da7ba5b59d443026` |
| `AXSESCLIENT-1.21.4.jar` | 537,103 байт | `3831fca6ef79848435ee11f0a550b273629be6b6d020d0f6718c53a98020961f` |
| `AXSESCLIENT-1.21.11.jar` | 538,635 байт | `f72961a8dd8b0bbc39b8dc7b9587384d7970d7d8cbfce0997c7a35ffdeced19c` |

## Результаты автоматического E2E-тестирования

1. **`client-common` JUnit 5**: PASS — проверены все **112 модулей** и **14 тем**, перетаскивание HUD в открытом Чате с магнитной сеткой, клики по кнопкам `Стоп` / `Продолжить` на виджете Spotify, авто-остановка ванильной музыки Minecraft (`stopVanillaMusic`), команды `#goto` / `#stop` Baritone, а также профили `KillAura v4` (`SnowGrim`, `Grim`, `Matrix`, `Vulcan`, `Intave`, `Polar`, `FunTime`, `ReallyWorld`, `HolyWorld`).
2. **Реальный E2E-запуск Minecraft `1.16.5` (Java 8 `1.8.0_512`)**: PASS (`mixinAudit: PASS`, `modulesRegistered: 112`, `themesRegistered: 14`, байткод `Major 52`).
3. **Реальный E2E-запуск Minecraft `1.21.4` (Java 21 `21.0.13`)**: PASS (`mixinAudit: PASS`, `modulesRegistered: 112`, `themesRegistered: 14`, байткод `Major 65`).
4. **Реальный E2E-запуск Minecraft `1.21.11` (Java 21 `21.0.13`)**: PASS (`mixinAudit: PASS`, `modulesRegistered: 112`, `themesRegistered: 14`, байткод `Major 65`).
5. **Windows x64 `AXSESCLIENT.exe` (`--selftest` + Avalonia UI + `FabricModInstallerService` xUnit)**: PASS (`DPAPI=dpapi`, `modules=112`, `themes=14`, проверка установки/переключения `.jar`/`.jar.disabled`/удаления модов, отрендерены все **12 страниц** лаунчера включая `launcher-modshub.png`).
6. **Windows x64 `AXSESCLIENT-Setup.exe` (Inno Setup 6)**: PASS — проверены тихая установка, запуск установленного лаунчера и безопасное удаление с полным сохранением пользовательских миров (`saves/`).

## Честные ограничения

- Все боевые, навигационные и визуальные модули работают исключительно на стороне клиента через стандартное состояние и точную математику углов/хитбоксов (без эксплойтов пакетов, спуфинга протокола или обхода лицензии Mojang).
- Для версии `1.21.11`, если внешний релиз Baritone под конкретный патч ещё не опубликован апстримом, клиент автоматически использует встроенный ИИ-автопилот и 3D-маршрутизатор `AXSES Baritone`.
