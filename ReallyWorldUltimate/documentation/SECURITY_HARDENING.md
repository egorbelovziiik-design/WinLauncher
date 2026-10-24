# Информационная Безопасность и Защита от Эксплойтов (SECURITY HARDENING)

## 1. Аутентификация и Сетевой Периметр
- **`online-mode=true`** и **`enforce-secure-profile=true`** включены по умолчанию в `server/server.properties` для криптографической проверки лицензий Mojang и подписей пакетов чата 1.21.
- Если сервер ставится за прокси **Velocity**, включите `proxies.velocity.enabled: true` и `proxies.velocity.online-mode: true` в `config/paper-global.yml` с уникальным секретом `forwarding.secret` (никогда не оставляйте открытый порт бэкенд-сервера во внешнюю сеть!).
- `rcon` и `enable-command-block` отключены по умолчанию.

---

## 2. Защита от Дюпов, Крашей и Подделки Предметов (`SecurityModule`)
1. **Криптоподпись предметов `HMAC-SHA256`:** все кастомные предметы несут подпись `rw_hmac` в `PersistentDataContainer`, вычисляемую с серверной солью `RW_ITEM_SALT` (переменная окружения `.env`).
2. **Анти-дюп GUI:** при смерти, телепортации, входе в портал или выходе игрока все открытые окна `ReallyGuiHolder` принудительно закрываются на приоритете `LOWEST`; заблокированы `SWAP_OFFHAND`, `NUMBER_KEY`, `DOUBLE_CLICK` и перетаскивание (`InventoryDragEvent`).
3. **Анти-дюп аукциона и кейсов:** списание ключей и выкуп лотов `/ah` защищены условными атомарными SQL-запросами (`WHERE status = 'ACTIVE'`, `WHERE amount >= 1`) внутри транзакций БД.
4. **Защита от портального дюпа и Book-Ban:** запрещён проход вьючных животных с сундуками (`ChestedHorse`) через порталы; размер книг ограничен 50 страницами и 16 КБ UTF-8 как в `SecurityModule`, так и в `paper-global.yml`.
5. **Временные блоки Трапок и Пластов:** координаты всех временных клеток занесены в `temporaryStructureBlocks`, их добыча киркой отменена (`BlockBreakEvent`), поэтому невозможно фармить бесконечный обсидиан!

---

## 3. Скрытие Команд, Античит GrimAC и Аудит
- Игрокам без `reallycore.admin` заблокированы и вырезаны из дерева автодополнения 1.21 (`PlayerCommandSendEvent`) команды `/pl`, `/plugins`, `/ver`, `/version`, `/about`, `/?` и любые команды с двоеточием (`/bukkit:*`, `/minecraft:*`).
- Конфигурация **GrimAC** (`server/plugins/GrimAC/config.yml`) настроена под симуляцию физики 1.21.4–1.21.11 (`Reach 3.003`, `TimerA`, `Simulation`, `NoFall`, `Knockback`).
- Журналы `economy_transactions` и `security_audit_log` обеспечивают полную трассируемость действий игроков и администраторов.
