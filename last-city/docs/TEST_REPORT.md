# Отчёт о проверках — ECHOES OF STEEL 0.6.0

Дата полного автоматического прогона: **2026-10-23**.

## Честная граница результата

**Полный автоматический pipeline PASSED. Roblox Studio / Roblox Engine в этой среде недоступен.** Настоящий Play Test 0.6.0, сетевой тест Roblox, TTS/audio playback и обращение к live DataStore здесь не выполнялись.

`PASSED` ниже относится только к явно названному слою. Compilation, API doubles и разбор binary DOM не доказывают Humanoid grounding/physics, Motor6D-картинку, collision, Pathfinding, first-person presentation, Streaming, mobile UX, звук, FPS или репликацию реальных клиентов. Эти пункты остаются **NOT TESTED** и перечислены в разделе 14 [STUDIO_QA.md](STUDIO_QA.md).

## Итог автоматического gate

Команда:

```bash
python3 last-city/tools/test.py
```

| Проверка | Статус | Результат |
|---|---|---:|
| Luau compilation, pinned 0.743 | **PASSED** | 55 / 55 sources |
| Standalone analysis | **PASSED** | Только три разрешённые Roblox-userdata diagnostics |
| Serialization regressions | **PASSED** | 10 / 10 |
| Road geometry regressions | **PASSED** | 9 / 9 |
| Domain transactions | **PASSED** | 121 / 121 |
| Roblox API adapter doubles | **PASSED** | 95 / 95 |
| Independent binary assertions | **PASSED** | 170 / 170 |
| Две последовательные сборки | **PASSED** | Byte-for-byte identical |
| Roblox Studio import / Play 0.6.0 | **NOT TESTED** | Нужен Studio |
| Engine TTS / bundled audio playback | **NOT TESTED** | Нужен Roblox client |
| Живой DataStore / server handoff | **NOT TESTED** | Нужна test experience |
| 2–9 реальных клиентов | **NOT TESTED** | Нужен Studio/опубликованный сервер |
| FPS / MicroProfiler / mobile | **NOT TESTED** | Нужны реальные измерения |

Standalone Luau CLI не содержит Roblox userdata type registry. Разрешены только:

```text
CombatService.luau: Unknown type 'Vector3'
EffectsController.luau: Unknown type 'Vector3' (2 annotations)
```

Все файлы при этом компилируются. Любая diagnostic другого типа завершает pipeline ошибкой.

## Проверенный артефакт

```text
Файл:      last-city/releases/LAST_CITY_3D.rbxl
Версия:    0.6.0 — ECHOES OF STEEL
SHA-256:   fe53e7ce00faaf19048dc64be992cfa341e06736d6d8005e24567f9b7e00b458
Размер:    328 493 bytes
Instances: 7 568
BasePart:  6 809
Scripts:   55
```

`rbxm-parser` пишет бинарный Roblox place; независимый `rbx-reader` читает готовый файл. Проверяются настоящий `<roblox!` header, pinned Terrain fixture, services, references, exact UTF-8 Source, world tree, property serialization и binary roundtrip.

### Структура мира после сериализации

Проверены, среди прочего:

- девять разных private plots;
- физическая проходимая шахта и отдельные boss/minion markers;
- `11_NewFrontier`, command, clinic, supply, workshop и grounded foundation;
- шесть enterable домов НОВОГО РУБЕЖА и шесть домов West Annex;
- Prism Cave с крышей, стенами, входом и шестью crystal lights;
- три City One и три City Two rotating boss sites плюс Rainbow spawn;
- две helipad, два arrival marker и две pivotable helicopter models с crossed rotors;
- 15 distinct persisted quest targets, включая четыре house surveys и три medical caches;
- пять junction road slabs, одна residential road и десять non-overlapping sidewalk segments;
- 6 809 BasePart ниже явного бюджета 7 000;
- все 55 embedded Source byte-for-byte равны repository;
- manifest/metadata объявляют 0.6.0 Echoes of Steel.

## Что проверяют 9 road tests

1. Crossroads paved once, без coplanar duplicate slabs.
2. Sidewalk не пересекает driving lane.
3. Curb не проходит через intersection asphalt.
4. Settlement road не режет private plot.
5. Все 12 городских перекрёстков имеют непрерывный центр.
6. Rectangle subtraction не создаёт zero/negative geometry.
7. City/settlement joins касаются без overlap.
8. Все roads/sidewalks НОВОГО РУБЕЖА встречаются только footprint-краями.
9. West Annex connector касается lane/sidewalk только краями.

## Что проверяют 121 domain tests

Тесты исполняют настоящие `Inventory`, `Quests`, `Building`, `Economy`, `Profile`, `Progression`, `Lease` и `RateLimiter`.

### Инвентарь и награды

- 24 slots / 180 weight, stack limits, UID, split/move/remove/equip и stock transfer.
- Отрицательные, дробные, NaN/infinite значения отклоняются.
- Multi-item/loaded-weapon reward атомарна; полный backpack не оставляет частичный результат.
- Mine quest сохраняет Glock magazine 10/10 + ровно 48 reserve через normalize roundtrip.
- Weapon/armor definitions, level restrictions, upgrade state и независимые equipped slots.

### Задания и боссы

- Accept/prerequisite/progress/seen/single claim/daily UTC reset.
- Kill до accept не считается; `first_blood` принимает только walker.
- `second_city`: 24/25 не открывает маршрут, 25-е server credit открывает.
- `rescue_fireteam`: требует `second_city`, шесть `corrosive` и одного `warden`; owner — Boris.
- Все 16 definitions существуют; четыре новых City Two quests имеют реальные objectives.
- Rainbow/Reaver/Warden/Foreman имеют правильные zone/boss contracts; walker damage остаётся 5.

### Строительство и производство

- Grid/rotation/bounds/reserved zones/edge contact/overlap/cap/level/blueprint/cost/repair/demolish.
- 17 building definitions имеют bounded стоимость, HP и настоящую роль.
- Генераторы, brownout, fuel, workers, morale, shift и inputs не создают бесплатный output.
- Backpack + stockpile payment точен и откатывается целиком при отказе.

### 0.6 profile contracts

- Ровно пять specialist definitions имеют фиксированные цены 1 250 / 1 000 / 1 500 / 900 / 1 100 Cash.
- Recruit allowlist удаляет invented IDs, non-gatherer не сохраняет `gather`, counters bounded.
- Rainbow armor pity зажимается 0..3, weapon pity 0..2.
- `voice` bounded 0..1; `firstPerson` принимает только boolean.
- Новые equipment stats: Rainbow Armor 0,46 / acid 0,35 / stamina 1,1; Prism Blade 62 / 0,6 / 8,4.
- Shop действительно содержит новые оружие/броню первого города.

## Что проверяют 95 mock-adapter tests

Mock исполняет неизменённые services/controllers/views через узкий Roblox API double. Он не моделирует renderer/physics/audio.

### Клиент и UI

- Client composition, loading/menu/state/play/tutorial и все панели.
- Responsive construction на 390×844 и 844×390.
- K открывает backpack; I не связан; каждое 1 отправляет server `toggleCrowbar` intent.
- Z/touch action меняет реальный CameraMode state и отправляет сохранение настройки.
- TTS controller создаётся или безопасно деградирует, generation отменяется, close dialogue вызывает Stop.
- Hit confirm показывает marker и использует pooled damage number в double.
- Camera menu/survivor/commander/helicopter callbacks выполняются без Luau error.

### Server authority

- Quest proximity, client reward fields ignored, active/frozen/closing checks.
- Payload sanity и token buckets.
- Stock доступен только на своей базе; consumables применяются один раз.
- Building placement создаёт запись/модель и оплачивает реальные ресурсы; resident evacuation идёт до Render.
- Reload token, shotgun rays, ammo, armor durability, Tool UID и server damage.
- Отдельный moving target в пределах bounded near-ray fallback получает 32 server damage и `hit_confirm`; клиент не выбирает цель.

### Специалисты и отряд

- Покупаются все пять ролей; повторная покупка не списывает Cash.
- Аркадий сокращает timer на 35%; Светлана даёт 4 HP; Тимур списывает ammo и наносит 22 damage.
- Scout ping возникает от server nearest target; gatherer получает ожидаемый +25% и один раз завершает expedition.
- Specialist внутри нового footprint получает новый clear home и физически перемещается до Render.
- Follow specialist travel и base-only actor разделены.
- Rada/Gleb unlock, armor/rifle/no-jump, commands, ammo и expedition regressions сохранены.

### Боссы, travel и persistence

- Zone-exclusive spawn запрещает mine/City1/City2 enemy в неправильной области.
- Rainbow pity path выдаёт ordinary resource, 35 Coins, armor и equippable blade.
- Helicopter lock/timer/две стороны/command freeze/follow actors.
- Simulated `UpdateAsync`: acquire/save/release, callback replay, retries, lease loss, Studio SessionOnly, live fail-closed, future schema.
- Full Bootstrap composition, join/restore, quest/loot/tutorial/NPC/door flows.

## 170 binary assertions

Independent verifier проверяет не только наличие строк, но и decoded place:

- exact Source/UTF-8 and source count;
- Terrain opaque payload и roundtrip;
- world/services/remotes/metadata;
- physical houses, cave, facilities, helicopters, roads, sidewalks, caches и boss markers;
- runtime definitions/services for 14 infected, 16 quests, 5 recruits, equipment, TTS, first-person, hit feedback and loot chances;
- authorized bundled-audio policy;
- part budget and deterministic roundtrip.

Source assertions не делают API рабочим в Roblox Engine; они доказывают, что проверенная реализация встроена в release binary, а не осталась только в repository.

## Acceptance matrix 0.6.0

| Контракт | Автоматический статус | Roblox status |
|---|---|---|
| Исправленные City 2 roads без overlap | **PASSED** roads + binary | **NOT TESTED** rendered walkthrough |
| Шесть City 2 домов + 4 survey caches | **PASSED** binary + quest domain | **NOT TESTED** live traversal |
| Богатые City 2 resource crates | **PASSED** binary metadata | **NOT TESTED** live prompts/respawn |
| Prism Cave + Rainbow zone/leash | **PASSED** binary/domain/mock | **NOT TESTED** engine fight |
| 35 Coins + ordinary loot + 22%/30% pity | **PASSED** domain/source/mock | **NOT TESTED** long live sample |
| Rainbow Armor/Blade экипируются | **PASSED** domain/mock/model source | **NOT TESTED** Tool/armor visual |
| Лада продаёт пять ролей за Cash | **PASSED** domain/mock | **NOT TESTED** E/presentation |
| Все пять ролей меняют server state | **PASSED** mock/profile | **NOT TESTED** path/replication |
| 16 quests, больше City 2 content | **PASSED** domain/binary | **NOT TESTED** complete playthrough |
| City 1 equipment ladder | **PASSED** definitions/domain/binary | **NOT TESTED** balance/visual |
| Reliable near-ray + visible feedback | **PASSED** adapter/source/pool | **NOT TESTED** mouse/network feel |
| Saved first-person | **PASSED** adapter/profile/source | **NOT TESTED** real camera/clipping |
| Adaptive sounds/zombie vocals | **PASSED** source policy/event mock | **NOT TESTED** playback/quality |
| English engine TTS for Russian text | **PASSED** payload/controller/source | **NOT TESTED** LoadAsync/voice |
| Boris unlocks Rada/Gleb | **PASSED** domain/mock/source | **NOT TESTED** live quest/path |
| Mine Glock 10/10 + 48 | **PASSED** domain/mock/save | **NOT TESTED** live boss |
| Walker exactly 5 HP | **PASSED** domain/source | **NOT TESTED** Humanoid hit |
| 1/K/I controls | **PASSED** adapter/source | **NOT TESTED** keyboard in Studio |

## История Studio

- **0.1.0 — FAILED import.** Пользовательский screenshot: `unexpected end at offset 2`; причина — truncated `Terrain.PhysicsGrid`.
- **0.1.1 — PASSED open.** Screenshots показали loading GUI/editable scene; gameplay не подтверждён.
- **0.2.0 — PASSED open.** Screenshots показали отрендеренное место.
- **0.3.0 — PASSED partial Play с дефектами.** Видимый E dialogue; обнаружены руки назад, неполная крыша и ненадёжный Виктор.
- **0.4.0 — NOT TESTED.** Автоматические исправления собраны без нового пользовательского подтверждения.
- **0.5.0 — NOT TESTED.** Отдельный импорт/Play не подтверждён.
- **0.6.0 — NOT TESTED.** Статус старых файлов не переносится на новый binary.

## Текущий итог

- Ошибок последнего автоматического pipeline: **0**.
- Deterministic build: **PASSED**.
- Roblox Studio 0.6.0: **NOT TESTED**.
- Следующий обязательный gate: import release binary, F5/Output, дороги и дома, Prism fight/loot, пять ролей, first-person, TTS/audio и hit feedback; затем 2/9 clients и persistent test experience.

Машиночитаемый результат: [AUTOMATED_RESULTS.json](AUTOMATED_RESULTS.json). Контрольная сумма: `../releases/SHA256SUMS`.
