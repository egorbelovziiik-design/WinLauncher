# Отчёт о проверках — FRONTIER REFORGED 0.7.0

Дата полного автоматического прогона: **2026-10-23**.

## Честная граница результата

**Полный автоматический pipeline PASSED. Roblox Studio / Roblox Engine 0.7.0 в этой среде не запускался.**

`PASSED` ниже относится только к явно указанному слою. Compilation, API doubles и разбор binary DOM не доказывают реальную физику гранат, Humanoid grounding, Motor6D/weld-картинку, Pathfinding, Streaming, mobile UX, TTS/audio playback, FPS, DataStore или репликацию 2–9 клиентов. Эти пункты остаются **NOT TESTED** до выполнения [STUDIO_QA.md](STUDIO_QA.md).

Известная пользовательская проверка 0.6.0 показала, что меню загружалось, но «ИГРАТЬ» повторно возвращало уведомление о загрузке персонажа. Это не считается успешным Play gate. В 0.6.1 путь входа был переписан и покрыт lifecycle-регрессиями; 0.7.0 сохраняет исправление, но ручной Studio retest всё равно обязателен.

## Финальный автоматический gate

Команда:

```bash
python3 last-city/tools/test.py
```

| Проверка | Статус | Результат |
|---|---|---:|
| Luau compilation, pinned 0.743 | **PASSED** | 57 / 57 sources |
| Standalone analysis | **PASSED** | Только разрешённые Roblox `Vector3` diagnostics |
| Serialization regressions | **PASSED** | 10 / 10 |
| Road/structure geometry regressions | **PASSED** | 10 / 10 |
| Domain transactions | **PASSED** | 133 / 133 |
| Roblox API adapter doubles | **PASSED** | 126 / 126 |
| Independent binary assertions | **PASSED** | 270 / 270 |
| Две последовательные сборки | **PASSED** | Byte-for-byte identical |
| Roblox Studio import / Play 0.7.0 | **NOT TESTED** | Нужен Studio |
| Humanoid / Pathfinding / Motor6D | **NOT TESTED** | Нужен Roblox Engine |
| Projectile physics / armor weld presentation | **NOT TESTED** | Нужен Roblox Engine |
| Engine TTS / bundled audio playback | **NOT TESTED** | Нужен Roblox client |
| Живой DataStore / server handoff | **NOT TESTED** | Нужна test experience |
| 2–9 реальных клиентов | **NOT TESTED** | Нужен Studio/опубликованный сервер |
| FPS / MicroProfiler / mobile | **NOT TESTED** | Нужны реальные измерения |

Standalone Luau CLI не содержит Roblox userdata type registry. Разрешены только шесть `Unknown type 'Vector3'` сообщений в `BuildingService`, `CombatService` и `EffectsController`; все 57 файлов компилируются. Любая diagnostic другого типа завершает pipeline ошибкой.

## Проверенный артефакт

```text
Файл:      last-city/releases/LAST_CITY_3D.rbxl
URL:       https://raw.githubusercontent.com/egorbelovziiik-design/WinLauncher/7062ce50d1e4551752fdd02a6eeededaf92021b9/last-city/releases/LAST_CITY_3D.rbxl
Версия:    0.7.0 — FRONTIER REFORGED
SHA-256:   9311ea07bae623f8f15a8fa0c19c692e6a52a50aeb9cbba1501ded1e65e50cc7
Размер:    365 912 bytes
Instances: 8 143
BasePart:  7 232
Scripts:   57
```

`rbxm-parser` создаёт бинарный Roblox place; независимый `rbx-reader` разбирает готовый файл. Проверены настоящий `<roblox!` header, pinned Terrain fixture, service markers, references, exact UTF-8 Source, world tree, properties и binary roundtrip. Все 57 embedded source byte-for-byte совпадают с repository. Статическая геометрия остаётся ниже явно проверяемого бюджета 7 400 BaseParts.

## Обязательные регрессии 0.7.0

### Расширение НОВОГО РУБЕЖА

- Основание района ровно одно, заземлено и имеет размер `740 × 1080` studs.
- 15 асфальтовых прямоугольников соединяются краями без coplanar overlap.
- 26 тротуаров не пересекают driving lanes; lane paint не создаёт второй слой дороги.
- Пять новых учреждений, 14 домов и четыре boss arena footprint не пересекаются друг с другом или асфальтом.
- Отдельные регрессии фиксируют найденные дефекты кузница/арена и южный релей/дом №12.
- Три storm-cache вынесены из гидравлических прессов; binary gate проверяет все 22 уникальные quest targets.
- Binary gate подтверждает 50 bounded enemy markers, 96 loot markers, богатые City 2 crate IDs и отдельные Rainbow/Ironclad spawns.

### Задания, снаряжение и gating

- Три цепочки Ярослава, Михаила и Дарьи требуют реальные уникальные события/цели и платят награду только один раз.
- Frontier Sword, Foundry Cleaver, Frontier DMR и Marshal Pistol имеют item, weapon, model и server-authoritative combat параметры.
- Storm Armor и Ranger Elite Armor имеют отдельную welded-геометрию и реальную electric resistance.
- Frontier DMR выдаётся с сохранённым магазином 10/10 и отдельным резервом.
- Три advanced-рецепта остаются недоступны до `second_city=RewardClaimed`; отказ не расходует ни stockpile, ни backpack.
- Quartermaster inventory проверяет City 2 и близость Феликса на сервере.

### Новые заражённые

- Howler применяет bounded временное усиление к ближайшей стае.
- Shocker использует отдельный electric pipeline и истощает выносливость только с защитой целой брони; durability `0` больше не оставляет скрытое сопротивление stamina drain.
- Crusher регенерирует только после интервала без урона.
- Ironclad имеет отдельный Arsenal lifecycle и не блокирует другие boss spawns.
- Mine-only, city1-only и city2-only правила сохраняются; `AgentCanJump=false` остаётся у заражённых и союзных NPC.

### Настоящая стройка Степана

- Заказ атомарно списывает смешанные backpack/stockpile материалы и Cash только после поиска валидного footprint с доступной стороной.
- Детерминированный flood-fill отклоняет изолированный маршрут до оплаты.
- Adapter напрямую вызывает реальный `NPCService:UpdateBuilder`: Степан у площадки уменьшает `remaining`, а дальняя позиция — нет.
- Диалог, заражённый рядом, travel/frozen session и три ошибки пути не уменьшают работу; blocked path переключает сторону.
- Прогресс хранится как `Builder.remaining`, `working` не доверяется сохранению, а normalization перебазирует projected deadline без offline work; регрессия отдельно сохраняет дробный остаток `0.25` с и не допускает преждевременного завершения.
- Истёкший `readyAt` активного заказа не включает farm Cash, power, production, workshop, turret, spikes, watchtower или command-post bonus.
- Реальное завершение делает модель непрозрачной, очищает заказ и уведомляет владельца.
- Снос и уничтожение очищают `order`, `remaining` и `working`.
- Клиент обрабатывает pooled `build_spark`; UI и E-диалог показывают секунды настоящей работы.

### Сохранённые регрессии 0.6.1

- Guarded character recovery не оставляет вечный Play-lock.
- Каждая готовая живая грядка платит ровно +10 Survival Cash раз в online second; Coins и offline catch-up не меняются.
- E-склад дерева атомарно переносит реальный backpack wood и управляет bounded pile.
- Шесть quick slots нормализуют forged/duplicate UID; slot 1 остаётся crowbar toggle.
- Гранаты расходуются до server-owned projectile; клиентские damage/radius игнорируются.
- Раздельные ammo, видимая броня, explosive/acid/electric resistance и authoritative reload остаются покрыты.

## Сохранённые критические контракты

- Базовый Walker наносит ровно 5 HP до брони.
- Шахта выдаёт атомарно Glock 10/10 и ровно 48 reserve rounds.
- Алекс открывает City 2 после ровно 25 server-credited kills.
- Boris unlock, Rada/Gleb commands, recruiter roles, Rainbow pity/drop и helicopter round trip сохранены.
- Friendly fire, чужая стройка/дроп и клиентские reward/damage поля отвергаются сервером.
- Survival Cash отделён от постоянных Coins.
- DataStore использует normalization, `UpdateAsync`, lease/token, retries, autosave и exit save; Studio default остаётся явным SessionOnly.

## История ручного Studio gate

| Версия | Статус | Свидетельство |
|---|---|---|
| 0.7.0 | **NOT TESTED** | Нужен новый import/Play и builder/City 2 QA |
| 0.6.1 | **NOT TESTED** | Не получено подтверждение import/Play после hotfix |
| 0.6.0 | **FAILED** | Меню загружалось, Play повторно показывал character-loading notice |
| 0.5.0 | **NOT TESTED** | Нет подтверждения |
| 0.4.0 | **NOT TESTED** | Нет подтверждения |
| 0.3.0 | **PASSED с найденными дефектами** | Скриншоты Play и E-диалога; выявлены visual/runtime проблемы |
| 0.2.0 | **PASSED** | Скриншоты открытого и отрисованного place |
| 0.1.1 | **PASSED частично** | Loading GUI и редактируемая сцена; gameplay startup не подтверждён |
| 0.1.0 | **FAILED** | `unexpected end at offset 2` |

## Воспроизводимость

Pinned toolchain:

- Luau `0.743`;
- `rbxm-parser 1.1.4`;
- `rbx-reader 1.5.10`.

После первого build/verify pipeline повторно строит place и сравнивает все байты. Финальный результат `PASSED` означает, что две последовательные сборки дали один SHA-256. Сырые машинные результаты находятся в `docs/AUTOMATED_RESULTS.json`; Roblox Studio статус там остаётся `NOT TESTED`.
