# KingGriefBridge (v1.2.0) — плагин выдачи доната и кейсов

Готовый собранный файл плагина уже лежит здесь:
- Локально в проекте: `kinggrief/minecraft-plugin/KingGriefBridge.jar`
- Прямая ссылка на скачивание с сайта: **`https://kinggrief.vercel.app/KingGriefBridge.jar`** (также кнопка **«Скачать KingGriefBridge.jar»** в админ-панели `/admin/cases` и `/admin/settings`).

## Быстрая установка на `kinggrief.pickme.cloud`

1. Скачайте **`KingGriefBridge.jar`** и загрузите его в папку `/plugins/` вашего сервера (`Paper / Purpur / Spigot 1.16.5 — 1.21.4+`, работает на Java 11 / 16 / 17 / 21+).
2. Перезапустите сервер.
3. **Готово!** Внутри `.jar` уже вшит `config.yml` с адресом `https://kinggrief.vercel.app` и вашим секретом `MINECRAFT_BRIDGE_SECRET` — ничего вручную прописывать не требуется.

## Команды плагина в игре / консоли

- `/kgb status` — проверить связь с `https://kinggrief.vercel.app` и счётчик выданных заказов.
- `/kgb poll` — принудительно опросить очередь выдачи прямо сейчас (без ожидания 5 секунд).
- `/kgb reload` — перечитать `plugins/KingGriefBridge/config.yml`.

## Поддерживаемые кейсы сервера

На сайте `https://kinggrief.vercel.app` и в админ-панели (`/admin/cases`) синхронизированы все 11 серверных кейсов:
- `donate` — Донат-кейс
- `sdonate` — Супер Донат-кейс
- `legendary` — Легендарный кейс
- `riliks` — Кейс с риликами
- `ditems` — Кейс с донат-вещами
- `kits` — Кейс с китами
- `money` — Денежный кейс
- `allorno` — Кейс «Всё или ничего»
- `djamshut` — Кейс Джамшута
- `reallydev` — Кейс ReallyDev
- `summer` — Летний кейс
