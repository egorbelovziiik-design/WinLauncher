# Скачать STEAL AN EGG ULTIMATE HUB 1.0.0

## ⬇️ Прямая ссылка

**[Скачать StealAnEggUltimate.exe (62.9 МБ)](https://github.com/egorbelovziiik-design/WinLauncher/raw/saeu-latest/StealAnEggUltimate/dist/StealAnEggUltimate.exe)**

Резервные варианты той же сборки:

| Способ | Ссылка |
| --- | --- |
| Тег `saeu-latest` (стабильная, не меняется) | https://github.com/egorbelovziiik-design/WinLauncher/raw/saeu-latest/StealAnEggUltimate/dist/StealAnEggUltimate.exe |
| CDN (то же самое) | https://raw.githubusercontent.com/egorbelovziiik-design/WinLauncher/saeu-latest/StealAnEggUltimate/dist/StealAnEggUltimate.exe |
| Ветка `arena/95195420-winlauncher` (всегда свежий билд) | https://github.com/egorbelovziiik-design/WinLauncher/raw/arena/95195420-winlauncher/StealAnEggUltimate/dist/StealAnEggUltimate.exe |
| Страница файла на GitHub | https://github.com/egorbelovziiik-design/WinLauncher/blob/saeu-latest/StealAnEggUltimate/dist/StealAnEggUltimate.exe |
| Артефакт CI (нужен вход в GitHub) | https://github.com/egorbelovziiik-design/WinLauncher/actions/runs/39962140897 |

## Файл

| | |
| --- | --- |
| Имя | `StealAnEggUltimate.exe` |
| Размер | 65 979 139 байт (62.9 МБ) |
| SHA256 | `9FD94E62B40057A821451B4032861DF0B08777015EEBF57B34FD9723181B2354` |
| Платформа | Windows 10/11 x64, self-contained single-file — .NET устанавливать не нужно |

```powershell
# проверка целостности после скачивания
Get-FileHash .\StealAnEggUltimate.exe -Algorithm SHA256
# ожидается 9FD94E62B40057A821451B4032861DF0B08777015EEBF57B34FD9723181B2354
```

## Запуск

```powershell
.\StealAnEggUltimate.exe              # обычный запуск (демо-режим без Roblox)
.\StealAnEggUltimate.exe --selftest   # 36 автотестов → %AppData%\StealAnEggUltimate\selftest-report.txt
.\StealAnEggUltimate.exe --smoke      # старт окна + оверлея с авто-закрытием
```

## Статус сборки

- CI (windows-latest, run 39962140897): build → publish → verify EXE → self-test → smoke — все шаги **success**.
- Self-test: `RESULT: PASS` · `SUMMARY: 36 passed, 0 failed, 1 skipped`.
- Smoke: `uiExceptions=0`, exit code 0.
- `1 skipped` — аттач оверлея и меню по RIGHT SHIFT: требует реального клиента Roblox, интерактивной
  desktop-сессии и установленного тестового плейса. Автоматически не проверялось.
- Ограничения: нет DLL-инъекций, нет доступа к памяти Roblox, нет обхода античитов; для сторонних
  плейсов функции помечены в интерфейсе как `TEST PLACE`. Подробно — в [README.md](README.md) и [Docs/RUN.md](Docs/RUN.md).
