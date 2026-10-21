# WINLAUNCHER · 1.0.1

**Твой мир. Твои правила.** Независимый Minecraft Java Edition Launcher для Windows 10/11 x64: Tauri 2 + Rust + React + TypeScript.

> Это нативное приложение, а не HTML-имитация Minecraft. Веб-предпросмотр интерфейса специально не симулирует вход, каталоги, установки, игровые логи или статистику. Все операции с игрой выполняются Rust-backend.
>
> **Статус:** начальная реализация 1.0.0 с настоящими EXE/NSIS. На Windows прошли **23 Rust-теста, 25 реальных сценариев backend и установка/удаление NSIS**; отдельно прошли 10 frontend- и 13 браузерных тестов. Лицензионный вход, отрисованный игровой сеанс, Discord и keyed CurseForge **NOT TESTED**. Это не заявление о полной production-готовности. Точные результаты и ограничения: [docs/TESTING.md](docs/TESTING.md).

## Обновление 1.0.1: Fabric 26.2 / 1.21.4 / 1.16.5

- [**Скачать WinLauncher.exe 1.0.1**](https://github.com/egorbelovziiik-design/WinLauncher/releases/download/winlauncher-native-v1.0.1-preview.1/WinLauncher.exe)
- [**Скачать установщик WinLauncher-Setup.exe 1.0.1**](https://github.com/egorbelovziiik-design/WinLauncher/releases/download/winlauncher-native-v1.0.1-preview.1/WinLauncher-Setup.exe)
- [Актуальный отчёт проверки](https://github.com/egorbelovziiik-design/WinLauncher/releases/download/winlauncher-native-v1.0.1-preview.1/TESTING.md)

[Windows run 39677604848](https://github.com/egorbelovziiik-design/WinLauncher/actions/runs/39677604848), application source `42acfaba5e2d57a4363c479b623b059d7fe33531`: **PASSED — 30 Rust-тестов, 35 backend-сценариев, 2 NSIS-сценария; 12 frontend- и 13 браузерных тестов.** Fabric 1.21.4, 26.2 и 1.16.5 запускались настоящими JVM и достигли инициализации клиента. Vanilla 1.12.2 и 1.8.9 прошли реальную установку. Отрисованный игровой сеанс и устранение `atio6axx.dll` на конкретной машине пользователя **NOT TESTED**.

Существующий профиль: **Логи → Восстановить Fabric / Java**. Будут проверены файлы, выбран управляемый Temurin и актуальный загрузчик; после подтверждения сбросятся ручной Java executable, дополнительные JVM-аргументы и закрепление загрузчика. Миры, моды и скриншоты не удаляются. Несовместимые моды и видеодрайвер этим не заменяются.

В каталоге и редакторе — поиск по всем версиям и быстрый выбор 26.2, 1.21.4, 1.20.1, 1.19.2, 1.18.2, 1.16.5, 1.12.2, 1.8.9 из настоящего Mojang API. Для разных версий создавайте отдельные профили. [Инструкция 1.0.1](docs/QUICKSTART-1.0.1.ru.txt).

В Windows Java аргументные файлы используют системную кодовую страницу, поэтому управляемые JVM-пути сделаны относительными и ASCII. Файл открывается по относительному ASCII-имени; нестандартные не-ASCII JVM-параметры не записываются в него. Реальная проверка включает загрузку Java-класса из каталога с кириллицей и чтение файла с кириллическим именем/UTF-8-содержимым. Основной класс и официальный FabricMcEmu остаются на командной строке. Токены Microsoft туда, в файл JVM-параметров, не записываются.

Для запуска расширенного CLI acceptance harness сначала скомпилируйте только тестовый probe (`javac --release 8 -encoding UTF-8 -d src-tauri/target/native-probes tests/java/ArgfileProbe.java`). Обычной сборке приложения JDK-компилятор не требуется.

Ниже сохранены описание и результаты предыдущей версии 1.0.0 как история.

## Прямое скачивание — нативная сборка с офлайн-аккаунтом

- [**Скачать WinLauncher.exe**](https://github.com/egorbelovziiik-design/WinLauncher/releases/download/winlauncher-native-v1.0.0-preview.1/WinLauncher.exe)
- [**Скачать WinLauncher-Setup.exe**](https://github.com/egorbelovziiik-design/WinLauncher/releases/download/winlauncher-native-v1.0.0-preview.1/WinLauncher-Setup.exe)
- [Текущий отчёт тестов](https://github.com/egorbelovziiik-design/WinLauncher/releases/download/winlauncher-native-v1.0.0-preview.1/TESTING.md)

Прямые ссылки ведут на отдельные EXE, не ZIP. Публичный prerelease `winlauncher-native-v1.0.0-preview.1`, source `4d3b5421eee487f15c26aebba80f9fe70e2b4836`, [Windows run 39661734616](https://github.com/egorbelovziiik-design/WinLauncher/actions/runs/39661734616). **PASSED: 23 Rust-теста, 25 native backend-сценариев, NSIS install/uninstall; 10 frontend- и 13 браузерных тестов.** Создание/повторное открытие SQLite, локальная launch identity и удаление офлайн-аккаунта проверены на Windows. Отрисованный игровой сеанс по-прежнему NOT TESTED. Сборка неподписанная. Старые релизы другого канала не изменены.

## Локальный офлайн-аккаунт

По явному запросу добавлен второй тип аккаунта: **Аккаунты → Добавить офлайн-аккаунт → ник → Создать**. Microsoft Application ID для него не нужен. Ник: 3–16 латинских букв, цифр или `_`; регистр влияет на UUID. Сохранение, выбор и удаление выполняются нативным backend/SQLite, не браузерным макетом. Старые записи аккаунтов остаются Microsoft-аккаунтами и требуют прежнюю авторизацию.

Это локальная игровая идентичность без проверки владения, не Microsoft-аккаунт и не выданная лицензия. Серверы с Microsoft-авторизацией недоступны. Используйте законно приобретённую копию игры. **Полностью автономный запуск без интернета не гарантируется**: установка/проверка файлов пока использует официальные сетевые метаданные.

Результаты предыдущего лицензионного варианта ниже являются исторической базой; текущие результаты новой сборки публикуются вместе с её EXE. [Описание нативной предварительной сборки](docs/RELEASE-NOTES.native.md).

## Windows EXE: история первоначальной сборки

Проверенная сборка приложения: [`fd718db`](https://github.com/egorbelovziiik-design/WinLauncher/actions/runs/39654021540). [Повторная проверка того же NSIS-пакета](https://github.com/egorbelovziiik-design/WinLauncher/actions/runs/39655709172) подтвердила установку и удаление. Позднейшие изменения CI/документации не меняют код приложения. [Первый запуск и необходимые настройки](docs/QUICKSTART.ru.txt).

Workflow **Windows build & tests** собирает настоящий Windows x64 PE executable и NSIS-установщик на `windows-latest`:

1. Откройте [GitHub Actions](https://github.com/egorbelovziiik-design/WinLauncher/actions/workflows/windows.yml).
2. Выберите **успешный** запуск для нужного commit / ветки.
3. Скачайте artifact `WinLauncher-1.0.0-windows-x64`.
4. Внутри: `WinLauncher.exe`, `WinLauncher-Setup.exe`, `SHA256SUMS.txt`, отчёт проверок.

Для скачивания Actions artifacts может потребоваться вход в GitHub. Если сборка не прошла, готового EXE от этого commit нет. Никакие HTML/ZIP-файлы не переименовываются в EXE.

Важно: acceptance-проверки не блокируют упаковку. Читайте отчёты, а не только зелёный badge. В первоначальном artifact указан устаревший FAILED для сравнения установленного EXE: тест не учитывал штатную метку Tauri `UNK → NSS`. Исправленная полная SHA256-проверка и реальное удаление прошли в отдельном run выше; актуальный результат — `docs/verification/windows-installer.json`. Это не отключение проверки целостности.

- Установщик: для текущего пользователя, WebView2 bootstrapper, меню Пуск, выбор русского/английского/украинского языка.
- Отдельный EXE требует установленного Microsoft Edge WebView2 Runtime.
- Authenticode-сертификат не предоставлен. Windows SmartScreen может предупреждать о неподписанном приложении.
- Не запускайте лаунчер с правами администратора.

## Что реализовано в коде

### Minecraft и Java

- Официальный `version_manifest_v2.json` Mojang; release, snapshot, alpha/beta без выдуманного списка.
- Проверка SHA1 JSON версии, client JAR, библиотек, native-архивов, индекса и объектов assets; legacy virtual/resources mapping.
- Windows x64 Mojang rules, legacy и modern arguments, inheritance загрузчиков, classpath, native extraction, официальная logging configuration.
- Поиск `JAVA_HOME`, `PATH`, стандартных Windows-папок и управляемых runtime; проверка версии/архитектуры реальным `java -version`.
- Автоустановка Eclipse Temurin / Adoptium JRE нужного major (8/17/21/25 и новые), SHA256 архива, безопасная распаковка без администратора.
- Настоящий Java-процесс: `-Xms`, `-Xmx`, JVM/game arguments, рабочая папка профиля, stdout/stderr, PID, exit status, время игры, журнал сессий.
- Fabric / Quilt: официальный profile JSON; Forge / NeoForge: официальный installer JAR с SHA1 и реальным запуском установщика. Headless-несовместимые исторические Forge installers возвращают ошибку, не «успех».

### Аккаунты

- Microsoft OAuth 2.0 Device Code → Xbox Live → XSTS → Minecraft Services.
- Для Microsoft-аккаунтов — проверка владения Minecraft Java Edition перед запуском. Локальные офлайн-аккаунты по нику выделены в отдельный тип, без заявления о подтверждённой лицензии.
- Несколько аккаунтов, UUID/имя/скин, отмена и истечение кода, обновление токенов.
- Refresh/Minecraft tokens — **только Windows Credential Manager** через native keyring. Пароли не запрашиваются лаунчером и не сохраняются. Frontend не получает токены.

### Контент

- Настоящие Modrinth v2 и CurseForge v1 API, поиск/сортировка/фильтры, описания, изображения, совместимые версии.
- Обязательные зависимости, проверка версии Minecraft/loader, SHA512/SHA1, staging и rollback замены файлов, защита изменённых пользователем файлов.
- Изолированные mods/resourcepacks/shaderpacks, включение/отключение переименованием, безопасное удаление в backups, проверка обновлений, избранное.
- `.mrpack` и CurseForge manifest ZIP импортируются в **новый отдельный профиль**; доступные Modrinth/CurseForge модпаки устанавливаются из API.
- `.mrpack` export содержит проверенные ссылки/хеши Modrinth и зависимости, но не миры, токены, пользовательские JAR и приватные config.
- CurseForge `downloadUrl=null` / запрещённая дистрибуция **не обходятся**. Ключ вводится в нативной форме и проверяется API перед сохранением в Credential Manager.

### Приложение

- Собственный тёмный игровой интерфейс, 10 тем биомов, живой предпросмотр, частицы, отключение эффектов и учёт `prefers-reduced-motion`.
- SQLite: профили, настройки, аккаунты без секретов, метаданные контента и сессии.
- Настоящий Discord Desktop Rich Presence через Windows named-pipe RPC IPC с проверкой READY/ACK, nonce и тайм-аутами, application ID, приватность версии/профиля/времени/загрузок, очистка активности.
- Системный трей. Закрытие окна во время игры/установки не обрывает процесс — лаунчер остаётся в трее и отслеживает завершение.
- Системная информация CPU/GPU/RAM/Windows/disk, безопасный перенос data-root с SHA256 и сохранением исходной папки.
- Резервное копирование миров до 4 GB, восстановление **в новую папку**, скриншоты, реальные статистика/логи/уведомления, hotkey запуска в активном окне.
- Проверка публичных релизов WinLauncher; обновление вручную до публикации подписанного канала.
- Русский по умолчанию. English / Українська для навигации и основных действий; часть технической справки/ошибок пока русская.

## Что нужно настроить — никаких вымышленных ключей

### 1. Microsoft Application ID

В Microsoft Entra/Azure зарегистрируйте приложение для **personal Microsoft accounts**. Включите **public client / device code flow**. Используется scope `XboxLive.signin offline_access`; client secret не нужен и нигде не запрашивается.

В современных Minecraft Services доступ нового launcher Application ID может требовать дополнительного одобрения Microsoft. Регистрация Azure сама по себе не гарантирует разрешение Minecraft API. Нельзя использовать чужие Client ID или обходить одобрение.

Вставьте публичный UUID в **Настройки → Интеграции → Microsoft**. Затем **Аккаунты → Войти через Microsoft**. Ввод пароля происходит только на официальном сайте Microsoft. Войдите аккаунтом с лицензией Minecraft Java Edition.

### 2. CurseForge

Получите ключ для своего приложения через [CurseForge for Studios](https://console.curseforge.com/). Введите его **в интерфейсе Windows-приложения**, не в чат/репозиторий. Соблюдайте условия API, ограничения и права авторов. Без ключа показывается настройка подключения, а не фейковый каталог.

### 3. Discord

Создайте приложение в [Discord Developer Portal](https://discord.com/developers/applications), задайте имя `WinLauncher`, загрузите Rich Presence image с ключом `winlauncher`. Скопируйте **Application ID** (не bot token) в настройки. Запустите Discord Desktop и включите Rich Presence.

Название активности задаётся приложением в Discord Developer Portal; launcher меняет details/state/timestamp, но не маскирует своё приложение под другой зарегистрированный application. IP серверов и приватные данные в presence не включаются.

## Сборка из исходников на Windows

Требуются Node.js 22+, Rust stable MSVC x64, Visual Studio Build Tools 2022 с workload **Desktop development with C++**, Windows SDK и Microsoft Edge WebView2.

```powershell
npm ci
npm test
npm run build
cargo test --manifest-path src-tauri/Cargo.toml --lib
# Optional real integration checks; NEW empty test directory only:
# cargo run --manifest-path src-tauri/Cargo.toml --features native-smoke --bin native-smoke -- C:\Temp\WinLauncher-native-new release-artifacts
npm run desktop
# Production EXE + NSIS:
npm run desktop:build -- --target x86_64-pc-windows-msvc
```

Выход:

```text
src-tauri/target/x86_64-pc-windows-msvc/release/winlauncher.exe
src-tauri/target/x86_64-pc-windows-msvc/release/bundle/nsis/*-setup.exe
```

Для UI-разработки (без имитации нативных функций):

```sh
npm ci
npm run dev
# http://localhost:1420, bind 0.0.0.0; preview hosts *.e2b.app разрешены
```

## Данные и безопасность

```text
%APPDATA%/io.winlauncher.desktop/
├── winlauncher.sqlite3       # только не-секретные данные
└── data/
    ├── minecraft/           # versions / libraries / assets
    ├── runtime/             # Temurin, SHA256-verified
    ├── downloads/           # проверенные архивы
    ├── profiles/<UUID>/     # saves / mods / resourcepacks / shaderpacks / screenshots
    ├── backups/             # миры и удалённые из библиотеки файлы
    └── logs/                # редактированные логи по датам
```

Фактический путь показывает UI — Tauri может выбрать платформенный app-data путь. Корневую папку игровых данных можно перенести отдельно от SQLite. Исходные данные при переносе не удаляются. Сторонние symlinks/junctions/reparse points отклоняются.

- HTTPS allowlist + проверка каждого redirect. Неизвестные источники модпака отклоняются, а не скачиваются молча.
- Архивы: проверка Windows path traversal / ADS / device names, запрет symlinks, лимиты размера/количества файлов.
- CSP, без удалённого JS, shell-plugin или неограниченного frontend filesystem API. Параметры JVM передаются аргументами процесса, не командой shell.
- Игровые логи могут содержать чат/IP от самой игры или модов. Токены редактируются; перед отправкой логов проверьте остальные приватные данные.
- Моды — сторонний исполняемый код. SHA подтверждает совпадение с опубликованным файлом, **не отсутствие вредоносного кода**.
- Старые версии Minecraft и модов могут иметь уязвимости. Используется официальный logging config и `log4j2.formatMsgNoLookups`, но это не гарантия безопасности всего стороннего кода.

## Архитектура

```text
src/
  components/    # shell, dialogs, profile editor, accessible controls
  pages/         # Home, Versions, Accounts, Themes, Statistics, Logs, Settings, mods/profiles/worlds
  services/      # typed Tauri IPC, no mock backend
  hooks/         # TanStack Query + real native events
  stores/        # Zustand UI state
  themes/        # theme tokens
  locales/       # extensible RU/EN/UK dictionary
  utils/         # real progress / session calculations
src-tauri/src/
  minecraft/     # metadata, rules, arguments, loaders, Java process lifecycle
  java/          # discovery and verified Temurin installation
  accounts/      # Microsoft/Xbox/Minecraft + Windows vault
  downloads/     # bounded HTTPS, streaming, hashes, retries, cancellation
  modrinth/      # real project/dependency APIs
  curseforge/    # official keyed API, respecting distribution permissions
  discord/       # Desktop Rich Presence IPC
  profiles/      # validated isolated instances
  settings/      # SQLite preferences, hardware, safe paths
  logs/          # export of redacted session logs
  content.rs     # transactional file staging and rollback
  modpacks.rs    # real mrpack / CurseForge import, reference-only export
  worlds.rs      # backups and screenshots
  security.rs    # source allowlist, traversal/reparse safeguards
```

Hero landscape is an AI-generated voxel illustration, used only as decorative artwork. It is not a claimed screenshot of a running Minecraft session.

## Ограничения и проверка

См. [docs/TESTING.md](docs/TESTING.md). Независимая настройка каждой подпапки, автоматическая установка датапаков в выбранный мир, обновление существующего модпака in-place и подписанный auto-updater не объявляются реализованными. Их отсутствие не скрывается неработающими переключателями.

**Not an official Minecraft product. Not approved by or associated with Mojang or Microsoft.** Minecraft is a trademark of Microsoft. Third-party project names, APIs and assets belong to their respective owners.
