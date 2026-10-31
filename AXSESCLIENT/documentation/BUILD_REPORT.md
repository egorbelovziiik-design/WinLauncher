# AXSESCLIENT v2.0.0 (OBSIDIAN AURORA) — Отчёт о сборке и проверке

## Прямые ссылки на скачивание (GitHub Release `axsesclient-v2.0.0`)

- **Страница релиза v2.0.0**: https://github.com/egorbelovziiik-design/WinLauncher/releases/tag/axsesclient-v2.0.0
- **`AXSESCLIENT-Setup.exe` (Установщик Windows 10/11 x64 — рекомендуется)**: https://github.com/egorbelovziiik-design/WinLauncher/releases/download/axsesclient-v2.0.0/AXSESCLIENT-Setup.exe
- **`AXSESCLIENT.exe` (Портативный лаунчер Windows x64)**: https://github.com/egorbelovziiik-design/WinLauncher/releases/download/axsesclient-v2.0.0/AXSESCLIENT.exe
- **`AXSESCLIENT-1.16.5.jar` (Fabric 1.16.5 • Java 8)**: https://github.com/egorbelovziiik-design/WinLauncher/releases/download/axsesclient-v2.0.0/AXSESCLIENT-1.16.5.jar
- **`AXSESCLIENT-1.21.4.jar` (Fabric 1.21.4 • Java 21)**: https://github.com/egorbelovziiik-design/WinLauncher/releases/download/axsesclient-v2.0.0/AXSESCLIENT-1.21.4.jar
- **`AXSESCLIENT-1.21.11.jar` (Fabric 1.21.11 • Java 21)**: https://github.com/egorbelovziiik-design/WinLauncher/releases/download/axsesclient-v2.0.0/AXSESCLIENT-1.21.11.jar
- **`AXSESCLIENT-v2.0.0-Full-Bundle.zip` (Полный архив: лаунчер + моды + документация)**: https://github.com/egorbelovziiik-design/WinLauncher/releases/download/axsesclient-v2.0.0/AXSESCLIENT-v2.0.0-Full-Bundle.zip

---

## Контрольные суммы SHA-256

| Файл | Размер | SHA-256 |
|---|---|---|
| `AXSESCLIENT.exe` | 45,572,873 байт | `99a9c1891980befa00ebbcdfe6223a74486b6e8b3c9986017442bd0d7f3bb1b6` |
| `AXSESCLIENT-Setup.exe` | 41,632,560 байт | `c6258a23c81ec163a0d879a8d6451045d7de9d11ca9e21f23b632f5907c93d9d` |
| `AXSESCLIENT-1.16.5.jar` | 382,460 байт | `f39708180d24b20bcda9501e37b3c5154fc6ca8e2fdc75dd12551a33bc4dd1db` |
| `AXSESCLIENT-1.21.4.jar` | 388,658 байт | `7021cb14883d1f9f5884277a27eede7867682461f926e53f35d4b640b610a614` |
| `AXSESCLIENT-1.21.11.jar` | 389,926 байт | `35c0380aff80c2ca2a4e0b6958bac0440aca4c0908ea3aa6ab81ebcfa2059a5e` |

---

## Проведённая верификация (CI Run `41147547005` — ALL GREEN)

1. **`client-common` JUnit 5**: PASS — проверены все **93 модуля**, атака цели через `KillAura`, автоматическая передача цели в `Target HUD` и двуязычный поиск (`RU` / `EN`).
2. **Реальный E2E-запуск Minecraft `1.16.5` (Java 8 `1.8.0_504`)**: PASS (`mixinAudit: PASS`, `modulesRegistered: 93`).
3. **Реальный E2E-запуск Minecraft `1.21.4` (Java 21 `21.0.13`)**: PASS (`mixinAudit: PASS`, `modulesRegistered: 93`).
4. **Реальный E2E-запуск Minecraft `1.21.11` (Java 21 `21.0.13`)**: PASS (`mixinAudit: PASS`, `modulesRegistered: 93`).
5. **Windows x64 `AXSESCLIENT.exe` + Avalonia Skia UI**: PASS — отрендерены все 11 страниц в стиле **OBSIDIAN AURORA**, проверены Win32 `GlobalMemoryStatusEx`, сохранение ОЗУ, DPAPI-шифрование и авто-определение Java 8 + Java 21.
6. **Windows x64 `AXSESCLIENT-Setup.exe` (Inno Setup 6)**: PASS — проверены тихая установка, запуск установленного лаунчера и безопасное удаление без затрагивания сохранений игрока (`saves/level.dat`).
