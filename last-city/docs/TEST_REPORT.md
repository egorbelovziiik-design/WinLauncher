# Отчёт о проверках — ECHOES OF STEEL 0.6.1

Дата полного автоматического прогона: **2026-10-23**.

## Честная граница результата

**Полный автоматический pipeline PASSED. Roblox Studio / Roblox Engine 0.6.1 в этой среде не запускался.**

`PASSED` ниже относится только к явно указанному слою. Compilation, API doubles и разбор binary DOM не доказывают реальную физику гранат, Humanoid grounding, Motor6D/weld-картинку, Pathfinding, Streaming, mobile UX, TTS/audio playback, FPS, DataStore или репликацию 2–9 клиентов. Эти пункты остаются **NOT TESTED** до выполнения [STUDIO_QA.md](STUDIO_QA.md).

Известная пользовательская проверка 0.6.0 показала, что меню загружалось, но «ИГРАТЬ» повторно возвращало уведомление о загрузке персонажа. Это не считается успешным Play gate. В 0.6.1 путь переписан и покрыт автоматическими lifecycle-регрессиями, однако реальный Studio retest всё равно обязателен.

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
| Road geometry regressions | **PASSED** | 9 / 9 |
| Domain transactions | **PASSED** | 128 / 128 |
| Roblox API adapter doubles | **PASSED** | 114 / 114 |
| Independent binary assertions | **PASSED** | 218 / 218 |
| Две последовательные сборки | **PASSED** | Byte-for-byte identical |
| Roblox Studio import / Play 0.6.1 | **NOT TESTED** | Нужен Studio |
| Projectile physics / armor weld presentation | **NOT TESTED** | Нужен Roblox Engine |
| Engine TTS / bundled audio playback | **NOT TESTED** | Нужен Roblox client |
| Живой DataStore / server handoff | **NOT TESTED** | Нужна test experience |
| 2–9 реальных клиентов | **NOT TESTED** | Нужен Studio/опубликованный сервер |
| FPS / MicroProfiler / mobile | **NOT TESTED** | Нужны реальные измерения |

Standalone Luau CLI не содержит Roblox userdata type registry. Разрешены только пять `Unknown type 'Vector3'` сообщений в `CombatService` и `EffectsController`; все 57 файлов компилируются. Любая diagnostic другого типа завершает pipeline ошибкой.

## Проверенный артефакт

```text
Файл:      last-city/releases/LAST_CITY_3D.rbxl
Версия:    0.6.1 — ECHOES OF STEEL
SHA-256:   476ff32a113cce48bd4d2bdd9aca9afcf85d0d3cf7cfd1a0da2edf0e65b4f4a2
Размер:    345 797 bytes
Instances: 7 580
BasePart:  6 816
Scripts:   57
```

`rbxm-parser` создаёт бинарный Roblox place; независимый `rbx-reader` разбирает готовый файл. Проверены настоящий `<roblox!` header, pinned Terrain fixture, service markers, references, exact UTF-8 Source, world tree, properties и binary roundtrip. Все 57 embedded source byte-for-byte совпадают с repository. Геометрия остаётся ниже явного бюджета 7 000 BaseParts.

## Новые обязательные регрессии 0.6.1

### Вход и lifecycle персонажа

- Повторные Play clicks при отсутствующем Character ставят ровно один recovery.
- Character без `HumanoidRootPart` заменяется и затем входит в игру.
- Мёртвый Humanoid восстанавливается до перехода.
- Исключение `LoadCharacterAsync()` не оставляет вечный lock, сохраняет diagnostic и позволяет повторить действие.
- Composition root явно включает `CharacterAutoLoads` и запускает все services.

### Грядки и склад дерева

- Каждая готовая живая грядка отдельно добавляет ровно 10 Survival Cash; разрушенная не платит.
- Coins не меняются, offline catch-up не создаётся, сервис платит не чаще раза в секунду.
- Owner-only `farm_cash` содержит UID, сумму 10 и позицию грядки; клиент использует pooled зелёный `+10 CASH`.
- E-терминал склада атомарно переносит реальное дерево из backpack в сохранённый stockpile.
- Проверены дистанция, повтор с пустым backpack, stock-driven pile и bounded число визуальных досок.
- Binary gate подтверждает две заземлённые доски, две опоры знака и отсутствие старой пересекающей `RackBack`.

### Быстрые слоты

- Новый профиль имеет шесть слотов и crowbar UID в слоте 1.
- `equipToQuick` назначает первый пустой слот и откатывает equip, если заняты все шесть.
- Ресурс нельзя назначить; consumable/weapon/armor можно.
- Последний израсходованный предмет очищает сохранённую ссылку.
- Normalize удаляет forged UID, resource UID и duplicates, восстанавливая лом в слот 1.
- Adapter проверяет Equip → slot 2, explicit clear, запрет очистки slot 1 и quick consumable.
- Клиент создаёт шесть кнопок и обрабатывает клавиши `1–6`; `1` остаётся отдельным server crowbar toggle.

### Арсенал, боеприпасы и броня

- Четыре новых меча и пять новых firearms имеют предмет, server weapon definition, shop product и workshop recipe.
- Четыре новых ammo ID несовместимы и привязаны к соответствующим firearms.
- Factory создаёт отличимые `SwordBlade`, guard/tip/wrap, extended magazine, cylinder, handguard, scope и bipod.
- Ranger и Demolisher armor продаются/создаются, а все шесть новых комплектов дают отдельные welded visual parts.
- Demolisher armor реально применяет 50% explosive resistance до 36% базовой защиты и теряет durability.

### Гранаты

- Quick-slot frag grenade расходуется ровно один раз до fuse; повтор блокируется server cooldown.
- Переданные клиентом `damage` и `radius` игнорируются.
- Server-owned projectile и server definition определяют fuse, скорость, радиус и урон.
- Frag damage использует distance falloff и line-of-sight.
- Incendiary даёт initial damage и bounded repeated burn.
- Concussion записывает bounded `stunnedUntil`, а zombie AI прекращает движение/атаку на это время.
- Self-damage проходит общий armor pipeline; friendly players не являются целями.
- Клиентский взрыв использует заранее выделенный ограниченный pool.

## Сохранённые критические контракты

- Базовый walker наносит ровно 5 HP до брони.
- Шахта выдаёт атомарно Glock 10/10 и ровно 48 reserve rounds.
- Алекс открывает City 2 после ровно 25 server-credited kills.
- Boris unlock, Rada/Gleb commands, recruiter roles, Rainbow pity/drop и helicopter round trip сохранены.
- Friendly fire, чужая стройка/дроп и клиентские reward/damage поля отвергаются сервером.
- Survival Cash отделён от постоянных Coins.
- DataStore использует normalization, `UpdateAsync`, lease/token, retries, autosave и exit save; Studio default остаётся явным SessionOnly.

## История ручного Studio gate

| Версия | Статус | Свидетельство |
|---|---|---|
| 0.6.1 | **NOT TESTED** | Нужен новый import/Play после hotfix |
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
