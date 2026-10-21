# WinLaucher ⛏️
Настоящий лаунчер Minecraft Java Edition: Tauri 2 + React + TypeScript + Tailwind + Rust.

## Возможности (реальные, не моки)
- Версии из официального `piston-meta.mojang.com` manifest v2 (release/snapshot/alpha/beta), поиск/фильтры
- Установка: version JSON → client JAR → libraries (с OS/arch rules) → assets index+objects, SHA1-проверка, retry x4, Range-докачка (.part), пропуск целых файлов
- Запуск: classpath, natives extraction (.dll/.so/.dylib), `arguments.jvm/game` и legacy `minecraftArguments`, подстановка ${auth_*}, ${natives_directory}, ${classpath} и т.д., -Xms/-Xmx из профиля, отслеживание процесса, стрим логов игры
- Java: определение major по `javaVersion` версии, поиск системной/скачанной, авто-скачивание Adoptium Temurin (8/16/17/21+) ZIP с распаковкой
- Аккаунты: Offline (UUIDv3 MD5 OfflinePlayer:name) + Microsoft Device-Code (MS → Xbox Live → XSTS → Minecraft Services, проверка entitlements, пароль не запрашивается)
- Загрузчики: Fabric/Quilt (meta API, profile JSON + библиотеки, inheritsFrom merge), Forge/NeoForge (Maven installer.jar --installClient через Java)
- Modrinth: поиск, совместимая версия по game_version+loader, скачивание JAR, SHA512-проверка, вкл/выкл/удаление модов
- CurseForge: официальный API, ключ задаёт пользователь в Настройках (в код не вшит), без ключа — понятная ошибка
- Модпаки .mrpack: modrinth.index.json, files по хешам/URL, overrides (zip-slip защита), отдельный профиль/папка
- Профили с изолированными папками, темы (8 + custom), 11 языков (i18next, без перезапуска), менеджер загрузок с реальными событиями прогресса/скорости, логи без токенов

## Запуск в разработку
```bash
npm install
npm run tauri dev
```
Нужны Rust (rustup) и, на Windows, MSVC Build Tools + WebView2.

## Сборка Windows (.exe + .msi)
Локально на Windows: `npm run tauri build` → `src-tauri/target/release/bundle/nsis/WinLaucher_1.0.0_x64-setup.exe` и `.../msi/`.
Или GitHub Actions: workflow **Build Windows** (`.github/workflows/build-windows.yml`) собирается на `windows-latest` и кладёт установщики в Artifacts.

## Структура
`src/` — UI (App, i18n 11 языков, темы CSS-variables), `src-tauri/src/lib.rs` — системная логика Rust (installer/launcher/java/auth/loaders/modrinth/modpacks), разбита на команды Tauri по доменам.
