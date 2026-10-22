# Отчёт о проверках — IRON HORIZON 0.5.0

Дата автоматического прогона: **2026-10-23**.

## Честная граница результата

**Полный автоматический pipeline PASSED. Roblox Studio / Roblox Engine в этой среде недоступен.** Ни одного настоящего Play Test 0.5.0, сетевого теста Roblox, воспроизведения звука или обращения к живому DataStore здесь не выполнялось.

`PASSED` ниже относится только к указанному слою. Компиляция, API doubles и чтение бинарного DOM не доказывают правильную Humanoid-физику, Motor6D-картинку, Pathfinding, collision, Streaming, mobile UX, звук, FPS или репликацию двух настоящих клиентов. Эти пункты остаются **NOT TESTED** и перечислены в разделе 13 [STUDIO_QA.md](STUDIO_QA.md).

## Итог автоматического gate

Команда:

```bash
python3 last-city/tools/test.py
```

| Проверка | Статус | Результат |
|---|---|---:|
| Luau compilation, pinned 0.743 | **PASSED** | 52 / 52 sources |
| Standalone analysis | **PASSED** | Нет неожиданных diagnostics; одна известная Roblox-type diagnostic |
| Serialization regressions | **PASSED** | 10 / 10 |
| Road geometry regressions | **PASSED** | 7 / 7 |
| Domain transactions | **PASSED** | 117 / 117 |
| Roblox API adapter doubles | **PASSED** | 89 / 89 |
| Independent binary assertions | **PASSED** | 110 / 110 |
| Две последовательные сборки | **PASSED** | Byte-for-byte identical |
| Roblox Studio import / Play 0.5.0 | **NOT TESTED** | Нужен Studio |
| Живой DataStore / server handoff | **NOT TESTED** | Нужна test experience |
| 2–9 реальных клиентов | **NOT TESTED** | Нужен Studio/опубликованный сервер |
| FPS / MicroProfiler / mobile | **NOT TESTED** | Нужны реальные измерения |

Известная standalone-диагностика:

```text
CombatService.luau: Unknown type 'Vector3'
```

Luau CLI не содержит Roblox userdata type registry; тот же файл компилируется. Любая другая analyzer-диагностика завершает pipeline ошибкой.

## Проверенный артефакт

```text
Файл:      last-city/releases/LAST_CITY_3D.rbxl
Версия:    0.5.0 — IRON HORIZON
SHA-256:   f9d6718bad526fb1e599c616c8546f93b844f3bbd03bca47333117b38f2a6b1c
Размер:    293 152 bytes
Instances: 7 189
BasePart:  6 553
Scripts:   52
```

`rbxm-parser` пишет бинарный Roblox place; независимый `rbx-reader` читает готовый файл. Проверяется настоящий `<roblox!` header, Terrain fixtures, services, references, UTF-8, каждый embedded Source байт-в-байт, world tree и повторный binary roundtrip.

### Структура мира, проверенная после сериализации

- 9 физически разных участков.
- 28 bounded infected markers: 12 городских, 4 общих шахтных, 3 mine-minion, 1 boss и 8 второго города.
- Физические traversable mine/final chamber, отдельные boss/minion markers.
- `11_NewFrontier`, command, clinic, garrison supply, workshop и grounded second-city foundation.
- Две helipad, два arrival marker и две модели вертолёта с сохранённым `PrimaryPart=Body` и перекрёстными роторами.
- 11 уникальных quest-cache targets, из них 3 медицинских во втором городе.
- 6 553 BasePart ниже явного бюджета 7 000.
- 52 embedded Luau Source полностью совпадают с repository.
- Metadata/manifest объявляют 0.5.0 Iron Horizon.

## Что проверяют 117 доменных тестов

Тесты исполняют настоящие модули `Inventory`, `Quests`, `Building`, `Economy`, `Profile`, `Progression`, `Lease`, `RateLimiter`, а не переписанную копию логики.

### Инвентарь и транзакции

- Новый профиль получает один лом, но `Equipped=""`: первое нажатие 1 может достать его.
- Слоты, вес, stack limits, UID, split/move/remove/equip; отрицательные, дробные, NaN и infinite значения.
- Атомарная выдача нескольких предметов и loaded weapon state.
- Совместная цена stockpile + backpack, точное списание и отказ без частичной мутации.
- Склад ↔ рюкзак сохраняет общее количество; переполнение не уничтожает stock.

### Задания

- Accept/prerequisite/status/progress/seen/single claim/daily UTC reset.
- Убийства до accept не считаются; `first_blood` принимает только `walker`.
- `second_city`: после первого сюжетного задания 24 убийства оставляют 24/25, двадцать пятое завершает цель.
- `rescue_fireteam`: недоступно до второго города; требует отдельно 6 `corrosive` и 1 `warden`.
- `harbor_aid`: три разных cache UID, повтор не дублирует progress.
- Mine contract: Glock ammo 10/10 + ровно 48 reserve, 600 Coins, 650 XP; full bag оставляет всю награду/валюту/статус нетронутыми.

### Строительство и производство

- Grid/rotation/bounds/reserved zones/edge contact/overlap/cap/level/blueprint/cost/repair/demolish.
- 17 building definitions имеют bounded цену, здоровье и реальную роль.
- Стальной забор = 900 HP, шипы = 24 damage, военный генератор = 55 кВт.
- Генераторы не работают до `readyAt`, потребляют точное топливо, отключаются без него; brownout не создаёт free power.
- Теплица реально расходует воду и создаёт 4 еды + 1 траву только с работником и питанием.
- Существующие ферма/вода/workshop/morale/night/offline-production regressions сохранены.

### Persistence

- Coins, inventory UID/ammo/durability, quests/seen, buildings/carry/fuel, workers, craft, tutorial, settings и обе кампании проходят `Profile.normalize`.
- `Builder.order`, `Squad.command/resource/returnAt/expedition/completed`, `music` и `effects` сохраняются с allowlist/clamps.
- Неизвестная schema и повреждённые данные fail closed; duplicate UID удаляются; counters двигаются вперёд.
- Lease ownership/expiry/corruption и rate buckets имеют отдельные regressions.

## Что проверяют 89 mock-adapter тестов

Mock исполняет неизменённые игровые services/controllers/views с узким API double. Он не моделирует физику или renderer.

### Клиент и UI

- Полная client composition, menu/state/play/tutorial и construction всех панелей.
- Responsive construction на 390×844 и 844×390.
- K открывает/закрывает inventory и отправляет tutorial fact; старый I binding отсутствует.
- Каждое нажатие 1 отправляет server intent `toggleCrowbar`; клиент не выбирает UID и не зависит от запаздывающего snapshot.
- Camera callbacks menu/survivor/commander и helicopter start/arrive выполняются без Luau error в double.
- Dialogue viewport/branches, map/build previews, settings RPC и equipped firearm HUD.
- Acid/shot audio/effects event binding выполняется; фактический звук/картинка остаются NOT TESTED.

### Server authority

- Quest proximity, reward fields from client ignored, loaded/active/frozen/closing checks.
- Payload rejects nested data, Instances, long strings, NaN; token buckets bound callback count.
- Inventory stock access only on own base; consumables change real health/stamina once.
- Build ownership, model creation/destruction и resident evacuation before Render.
- Степан смешивает реальные backpack/stock ресурсы, создаёт UID/model и не списывает при отказе.
- Combat server damage/ammo/cooldown/aim/Tool validation, delayed reload token, shotgun seven rays, armor durability.

### 0.5 services

- Squad unlock создаёт ровно двух owner-bound бойцов с `MilitaryArmor`, `RifleBody`, no-jump Humanoid.
- Follow/base/gather commands меняют только persistent campaign state; завершённая expedition один раз добавляет ожидаемый stock.
- Helicopter отказывает до unlock, фиксирует персонажа, отправляет start duration=6, server callback завершает оба направления и переносит follow squad.
- Пока `Traveling` установлен, active remotes не выполняются.
- `mine_guard/foreman` не создаются вне шахты; `corrosive/warden` не создаются вне второго города.
- Quartermaster trade требует одновременно city unlock и proximity.
- Full Bootstrap создаёт все 17 services, 16 global NPC, workers, builder и travel prompts.

### Persistence adapter

- Настоящий код `SaveService` с simulated `UpdateAsync`: acquire/save/release, callback replay, 4 retries, lease loss, Studio SessionOnly, live fail-closed, future schema.
- Mine loaded Glock/reserve проходит save/normalize roundtrip.
- Full join/play/quest/loot/snapshot/tutorial/NPC/door composition работает в API doubles.

## Ключевые acceptance contracts 0.5.0

| Контракт | Автоматический статус | Roblox status |
|---|---|---|
| Степан платит реальными ресурсами и строит реальную запись/модель | **PASSED** domain + mock | **NOT TESTED** физический путь/вид |
| Отряд quest-locked, persistent, follow/base/gather | **PASSED** domain + mock | **NOT TESTED** Pathfinding/replication |
| NPC позы + HeadScratch после исправлений | **PASSED** compile/source/state | **NOT TESTED** визуально |
| NPC/заражённые не используют jump links | **PASSED** config/source/mock | **NOT TESTED** engine path result |
| Заражённые атакуют blocking defense | **PASSED** service/source | **NOT TESTED** физический сценарий |
| 17 полезных зданий | **PASSED** definitions/domain/model construction | **NOT TESTED** полный live pass |
| Второй город физически включён | **PASSED** binary geometry | **NOT TESTED** walkthrough/Streaming |
| Алекс требует 25 убийств | **PASSED** domain | **NOT TESTED** live loop |
| Вертолёт 6 с в обе стороны | **PASSED** server/mock/binary | **NOT TESTED** cinematic/replication |
| Authorized bundled audio only | **PASSED** source/binary policy | **NOT TESTED** playback |
| 12 infected, mine minions exclusive | **PASSED** domain/mock/binary markers | **NOT TESTED** population in Engine |
| Walker damage exactly 5 | **PASSED** domain/source | **NOT TESTED** Humanoid hit |
| Armor buy/craft contracts | **PASSED** domain/mock | **NOT TESTED** live shop/craft |
| Glock 10/10 + exactly 48 reserve atomic | **PASSED** domain/mock/save | **NOT TESTED** live boss |
| 1 toggles crowbar; K opens backpack | **PASSED** client + server mock | **NOT TESTED** keyboard in Studio |
| Static facade overlap correction | **PASSED** generator/binary | **NOT TESTED** rendered shimmer |

## История Studio

- **0.1.0 — FAILED import.** Пользовательский screenshot: `unexpected end at offset 2`. Причина: truncated `Terrain.PhysicsGrid`.
- **0.1.1 — PASSED open.** Пользовательские screenshots показали loading GUI/editable scene; gameplay не подтверждён.
- **0.2.0 — PASSED open.** Пользовательские screenshots показали отрендеренное место.
- **0.3.0 — PASSED partial Play с дефектами.** Видимый E dialogue; обнаружены руки назад, неполная крыша и ненадёжный Виктор.
- **0.4.0 — NOT TESTED.** Автоматические исправления были собраны, но нового пользовательского подтверждения нет.
- **0.5.0 — NOT TESTED.** Нужен новый импорт и полный раздел 13 Studio QA; статус предыдущих версий не переносится.

## Текущий итог

- Ошибок в последнем автоматическом pipeline: **0**.
- Исторический импорт 0.1.0: **FAILED** и не переписывается задним числом.
- Roblox Studio 0.5.0: **NOT TESTED**.
- Следующий обязательный gate: импорт release binary, F5, Output, два рейса, builder/squad/no-jump/audio, затем Server & Clients и persistent test experience.

Машиночитаемый результат: [AUTOMATED_RESULTS.json](AUTOMATED_RESULTS.json). Контрольная сумма: `releases/SHA256SUMS`.
