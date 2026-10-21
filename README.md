# WinLaucher

Современный лаунчер **Minecraft Java Edition** для Windows 10/11: устанавливает настоящие версии игры,
запускает их через настоящую Java, ставит Fabric / Forge / NeoForge / Quilt, работает с Modrinth и
модпаками `.mrpack`, поддерживает вход через Microsoft и офлайн-профили.

Интерфейс: React + TypeScript, оболочка: Tauri 2 (Rust). Итоговый файл — `WinLaucher.exe` и установщики
`.exe` (NSIS) / `.msi` (WiX).

---

## Скачать

Готовые сборки лежат в разделе **[Releases → v1.0.8](https://github.com/egorbelovziiik-design/WinLauncher/releases/tag/v1.0.8)**:

| Файл | Размер | Что это |
| --- | --- | --- |
| [`WinLaucher_1.0.8_x64-setup.exe`](https://github.com/egorbelovziiik-design/WinLauncher/releases/download/v1.0.8/WinLaucher_1.0.8_x64-setup.exe) | 5.8 МБ | Установщик NSIS (для текущего пользователя, без прав администратора) |
| [`WinLaucher_1.0.8_x64_en-US.msi`](https://github.com/egorbelovziiik-design/WinLauncher/releases/download/v1.0.8/WinLaucher_1.0.8_x64_en-US.msi) | 6.8 МБ | Установщик MSI (Windows Installer) |
| [`WinLaucher-portable.exe`](https://github.com/egorbelovziiik-design/WinLauncher/releases/download/v1.0.8/WinLaucher-portable.exe) | 9.9 МБ | Portable: один файл, без установки |

SHA-256 (для проверки загрузки):

```
2FA692F4C29F46EFFB24D01426CC52A884BDCA948D1936721015BA740631A453  WinLaucher-portable.exe
28B1BA71B62C98376BFC3DD7EC6D7E25572D43761C47BC67F6CE8A41427CB9B9  WinLaucher_1.0.8_x64-setup.exe
B1A56FA182F52D539C5753A9CF9BBD133A47C49F69ACF1B264BC604EA0C3A9D3  WinLaucher_1.0.8_x64_en-US.msi
```

Сборка не подписана сертификатом, поэтому SmartScreen может показать «Неизвестный издатель»:
нажмите «Подробнее» → «Выполнить в любом случае». Требуется Windows 10/11 x64.

## Что умеет

**Версии и установка**
- Список версий из официального манифеста Mojang (`piston-meta.mojang.com`), включая снапшоты и старые версии.
- Установка клиента, библиотек, нативов, ассетов и log4j-конфига с проверкой SHA-1.
- Докачка по HTTP Range (`.part`), повторные попытки, ограничение скорости и параллельных загрузок.
- Удаление версий, проверка целостности файлов профиля.

**Запуск**
- Настоящий запуск `net.minecraft.client.main.Main` (или класс мейна загрузчика) с корректным classpath,
  нативами, аргументами JVM и игры, `-Xms`/`-Xmx` из профиля.
- Вывод игры (`stdout`/`stderr`) попадает в живой лог лаунчера.
- Статусы: `ИГРАТЬ → ПРОВЕРКА → СКАЧИВАНИЕ → УСТАНОВКА → ПОДГОТОВКА JAVA → ЗАПУСК → В ИГРЕ`.
- Остановка игры из интерфейса, возврат окна после выхода.

**Java**
- Поиск установленных JDK/JRE (JAVA_HOME, PATH, Program Files, `.minecraft/runtime`).
- Определение версии и вендора через `java -XshowSettings:properties -version`.
- Автоматическая загрузка нужной версии с Adoptium (Eclipse Temurin) для Java 8/16/17/21 и новее.
- Подбор совместимого рантайма: точное совпадение мажорной версии, иначе ближайшая подходящая.

**Аккаунты**
- Microsoft: OAuth 2.0 Device Code Flow (код показывается в лаунчере, вход выполняется в браузере
  на `microsoft.com/link`). Пароль Microsoft лаунчер никогда не видит и не запрашивает.
- Цепочка Xbox Live → XSTS → Minecraft Services, проверка владения игрой (`entitlements/mcstore`),
  загрузка скина профиля, обновление токена.
- Офлайн-профили (ник + UUID по схеме `OfflinePlayer:<ник>`), честно помечены как офлайн.
- Токены хранятся в Windows Credential Manager (`keyring`), а не в файлах настроек.

**Моды и модпаки**
- Поиск модов Modrinth, фильтры по загрузчику и версии игры, установка конкретной версии файла.
- Список установленных модов, включение/отключение (`.jar` ↔ `.jar.disabled`), удаление, импорт локального `.jar`.
- Модпаки Modrinth: импорт `.mrpack` (файлы + overrides), автоматическая установка загрузчика и создание
  изолированного профиля с отдельной игровой папкой.
- CurseForge: поиск и статус ключа. **Нужен личный API-ключ** — см. «Ограничения».

**Интерфейс**
- Боковое меню: Главная, Версии, Моды, Модпаки, Загрузки, Аккаунты, Настройки.
- 8 тем (включая кастомную), масштаб интерфейса, уровень анимаций.
- 11 языков (RU по умолчанию, автоопределение по языку Windows, переключение без перезапуска).
- Мастер первого запуска: выбор языка → ник → Java → профиль → готово.

---

## Что проверено в CI

Каждый push собирается на `windows-latest` (`.github/workflows/build.yml`):

1. `npm run build` — TypeScript-проверка и сборка фронтенда.
2. `cargo check --all-targets` — проверка всего Rust-кода.
3. `cargo test --release --lib` — юнит-тесты логики запуска: правила версий, распаковка нативов
   LWJGL для 1.19+, maven-координаты Forge, подстановка аргументов, выбор версии Java, лимит `-Xmx`.
4. `npm run tauri build` — реальная сборка `.exe` / `.msi`.
5. End-to-end: `WinLaucher.exe --selftest 1.21.4` устанавливает настоящую версию, скачивает Java 21,
   собирает командную строку и **запускает настоящий клиент Minecraft**; отчёт с выводом игры
   пишется в [`selftest-report.txt`](selftest-report.txt).
6. Smoke-тест: портативный exe запускается на раннере и должен остаться живым 15 секунд
   (то есть окно создаётся, WebView2 инициализируется, паники нет).
   Отчёт с хешами и метаданными PE пишется в [`smoke.txt`](smoke.txt).

## Сборка из исходников

Требования:

- Windows 10/11 x64
- [Node.js](https://nodejs.org/) 20+ (проверено на 22)
- [Rust](https://rustup.rs/) stable с таргетом `x86_64-pc-windows-msvc`
- Visual Studio Build Tools с «Desktop development with C++» (нужно для Rust MSVC и Tauri)
- WebView2 Runtime (в Windows 11 предустановлен, в Windows 10 — обычно уже есть)

```powershell
git clone https://github.com/egorbelovziiik-design/WinLauncher.git
cd WinLauncher
npm ci
npm run tauri build      # соберёт .exe и .msi в src-tauri\target\release\bundle\
```

Режим разработки:

```powershell
npm ci
npm run tauri:dev
```

Только фронтенд (без Rust, браузерный предпросмотр интерфейса): `npm run dev`.

---

## Структура

```
src/                      React-интерфейс
  components/             TitleBar, Sidebar, ProfileForm, LogsDrawer, ui-kit
  pages/                  Главная, Версии, Моды, Модпаки, Загрузки, Аккаунты, Настройки, Onboarding
  lib/                    типы, мост к Tauri, темы, роутер
  i18n/locales/           11 словарей
  store/app.ts            состояние приложения (zustand)
src-tauri/
  src/main.rs             точка входа окна
  src/lib.rs              сборка Tauri, состояние, регистрация команд
  src/commands.rs         все команды IPC
  src/mojang.rs           манифест, version.json, наследование, правила, ассеты
  src/download.rs         движок загрузок (Range, SHA-1, скорость, отмена)
  src/java.rs             поиск/загрузка/выбор Java
  src/accounts.rs         Microsoft device code, Xbox Live, XSTS, Minecraft, офлайн
  src/loaders.rs          Fabric/Quilt (meta) и Forge/NeoForge (официальные установщики)
  src/modpack.rs          установка `.mrpack`
  src/modrinth.rs         Modrinth API и локальные моды
  src/curseforge.rs       CurseForge API (личный ключ)
  src/launch.rs           сборка командной строки и запуск игры
  src/state.rs            состояние, события, логи, хранилище секретов
```

Данные: `%APPDATA%\WinLaucher` (настройки, кэш, Java, логи, игровая папка по умолчанию
`%APPDATA%\WinLaucher\minecraft`). Путь к игровой папке меняется в настройках.

---

## Ограничения (честно)

- **CurseForge.** API требует ключ, который нельзя вшивать в клиент. Вставьте свой ключ в
  «Настройки → CurseForge» (получить: `console.curseforge.com`). Без ключа раздел сообщает, что недоступен.
- **Модпаки CurseForge (`.zip` с `manifest.json`)** не импортируются: для скачивания файлов нужен ключ,
  а формат не гарантирует прямые ссылки. Поддерживается `.mrpack` (Modrinth); для остальных архивов
  выводится понятное сообщение.
- **Прокси.** Используется `reqwest`/rustls: системный прокси Windows напрямую не читается,
  но поддерживаются переменные окружения `HTTPS_PROXY` / `HTTP_PROXY` / `NO_PROXY`.
- **Автообновление лаунчера** не реализовано: новая версия ставится поверх вручную.
- **Подпись кода** отсутствует (см. выше).
- **`Failed to fetch user properties` / `InvalidCredentialsException: Status: 401`.** Так клиент
  реагирует, когда сессия Microsoft отозвана, либо когда офлайн-профиль запущен с типом `msa`.
  Лаунчер проверяет токен перед запуском, обновляет его при отказе и принудительно передаёт
  `--userType legacy` для офлайн-профилей. Для офлайн-аккаунта такие строки в логе безвредны:
  они больше не считаются причиной падения.
- **Краш JVM** (`# A fatal error has been detected by the Java Runtime Environment`). Это сбой
  самой Java, чаще всего из-за драйвера видеокарты. Лаунчер теперь читает `hs_err_pid*.log`,
  показывает модуль, в котором произошёл сбой, и подсказку. Что помогает на практике:
  обновить драйвер GPU с сайта производителя (а не через Windows Update), запустить игру
  повторно (лаунчер пересобирает `natives` и стартует с чистыми JVM-аргументами),
  попробовать Java 17 вместо 21 в «Настройки → Java».
- **Что не проверено вживую:** разработка шла в Linux-песочнице, поэтому сценарии «вход в Microsoft →
  скачивание версии → игра» вручную на Windows не прогонялись. Проверено: успешная компиляция Windows-сборки,
  старт приложения на `windows-latest` (smoke-тест выше) и статические проверки фронтенда.
  Если какой-то сценарий не сработал — приложите лог (`Настройки → Логи → Экспорт`),
  в нём нет токенов: они вырезаются перед записью.

---

## Безопасность

- Никаких секретов в исходниках и в бандле: ключ CurseForge и токены живут в Credential Manager.
- Все пути из сети и из архивов проходят через `safe_join` (защита от path traversal, абсолютных путей и `..`).
- Логи редактируются: `access_token`, `refresh_token`, `authorization`, JWT-подобные строки заменяются на `***`.
- Офлайн-режим не выдаёт себя за лицензию: такие аккаунты помечены, а серверы с проверкой лицензии их не пустят.

## Лицензия

Проект распространяется как есть, без гарантий. Minecraft — торговая марка Mojang Studios;
проект не связан с Mojang и Microsoft.
