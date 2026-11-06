# AXSESCLIENT v3.5.0 (CELESTIAL VISION) — Отчёт о сборке и верификации

- **Версия релиза**: `v3.5.0` (`axsesclient-v3.5.0`)
- **Проверенный CI Run**: `42254382726` (GitHub Actions Linux Xvfb + Windows Server x64 + GitHub Release)
- **Синхронизированных модулей**: `108` (`КиллАура v3.5 + 3D Сферы душ`, `ESP Неон-капсула`, `Мини-Радар 2D`, `Счётчик тотемов`, `Геплы и перлы`, `Индикатор урона 3D`, `Тревога низкого ХП`, `Страж смерти`, `3D Маяки меток`, `Таргет HUD v3.5` и др.)
- **Встроенных тем**: `14` (`CELESTIAL VISION`, `EMERALD AURORA`, `HYPERION AURORA`, `AXSES DARK` и др. + пользовательские JSON-темы)

## Собранные и проверенные артефакты (`SHA-256`)

| Файл | Размер | SHA-256 |
| --- | --- | --- |
| `AXSESCLIENT-Setup.exe` | 41,826,880 байт | `41719940988b2abea1b693dada287c96152cd1402ecece40629940d6deaec8ab` |
| `AXSESCLIENT.exe` | 45,576,969 байт | `c1e9b04be51e381202b7287bb906ae53fd793dfc7387987617900e778ceefe00` |
| `AXSESCLIENT-1.16.5.jar` | 481,493 байт | `c46eb612c6ec5cb9babadce780e5ef942fa20b6f088dda0710b5153e33f76463` |
| `AXSESCLIENT-1.21.4.jar` | 488,733 байт | `88e6242f22d45bdd652173f8287a6efd4a21509034e0f165044405238ac8448e` |
| `AXSESCLIENT-1.21.11.jar` | 490,191 байт | `e04c9cb66694ef709ab3e122791c3ce3908aa3d8ddead46a4cc84849ab0ff777` |

## Результаты автоматического E2E-тестирования

1. **`client-common` JUnit 5**: PASS — проверены все **108 модулей** и **14 тем**, круговая геометрия `drawRoundedOutline` / `fillRoundedHorizontalGradient`, скруглённое ClickGUI (`Right Shift`), `KillAura v3.5` (`FunTime` / `ReallyWorld` / `HolyWorld` + `3D Soul Orbs`), `Vision Capsule ESP`, `2D PvP Radar`, `Totem Counter`, `Gapple Tracker`, `3D Damage Indicators`, плавный скролл и русский поиск.
2. **Реальный E2E-запуск Minecraft `1.16.5` (Java 8 `1.8.0_512`)**: PASS (`mixinAudit: PASS`, `modulesRegistered: 108`, `themesRegistered: 14`, байткод `Major 52`).
3. **Реальный E2E-запуск Minecraft `1.21.4` (Java 21 `21.0.13`)**: PASS (`mixinAudit: PASS`, `modulesRegistered: 108`, `themesRegistered: 14`, байткод `Major 65`).
4. **Реальный E2E-запуск Minecraft `1.21.11` (Java 21 `21.0.13`)**: PASS (`mixinAudit: PASS`, `modulesRegistered: 108`, `themesRegistered: 14`, байткод `Major 65`).
5. **Windows x64 `AXSESCLIENT.exe` (`--selftest` + Avalonia UI)**: PASS (`DPAPI=dpapi`, `modules=108`, `themes=14`, синхронизация `options.txt -> lang:ru_ru`, отрендерены все 11 страниц интерфейса).
6. **Windows x64 `AXSESCLIENT-Setup.exe` (Inno Setup 6)**: PASS — проверены тихая установка, запуск установленного лаунчера и безопасное удаление с полным сохранением пользовательских миров (`saves/`).

## Честные ограничения

- Все боевые, визуальные и игровые модули работают исключительно на стороне клиента через стандартное состояние и точную математику углов/хитбоксов (без эксплойтов пакетов, спуфинга протокола или обхода лицензии Mojang). На внешних серверах используйте легитные профили (`Legit Ghost` / `FunTime`) в соответствии с правилами площадки.
