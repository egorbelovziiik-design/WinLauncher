# Архитектура Frontier Reforged 0.7.0

## Граница доверия

Клиент передаёт **намерения**, а не результаты: ID предмета/рецепта/задания/специалиста, UID своей вещи, направление прицеливания, координаты сетки, четверть оборота и выбранную команду из allowlist. Он не передаёт подтверждённый урон, target Instance, валюту, цену, награду, loot roll, output экспедиции, heal amount, количество убийств или конечную координату перелёта.

`NetworkService`:

- принимает не более восьми верхнеуровневых полей; отклоняет вложенные таблицы, Instances, бесконечности, NaN и длинные строки;
- использует общий token bucket и отдельные лимиты каналов: combat burst 12/refill 9 с⁻¹, остальные burst 8/refill 3 с⁻¹;
- проверяет loaded/active/health, closing/frozen/lost lease и допустимость движения;
- пока установлен server token `Traveling`, все active-команды отклоняются;
- важные операции повторно проверяют ownership, unlock, proximity/line-of-sight, стоимость, cooldown и state внутри сервиса;
- mutation handlers не выполняют DataStore/Pathfinding yield внутри транзакции.

`StateRemote` и `EffectsRemote` работают только server → client для snapshots, диалогов, презентации и подтверждений. Сервер не обрабатывает отправленные клиентом сообщения этих каналов.

**Это базовая защита, не обещание полной exploit-proof архитектуры.** Стандартный character network ownership Roblox остаётся частью модели. Перед публичным запуском нужен adversarial Play Test.

## Домен и composition root

| Модуль | Обязанность |
|---|---|
| `Inventory` | Слоты, вес, UID, ammo/durability, атомарная выдача, split/move/equip, рюкзак ↔ stockpile |
| `Quests` | Prerequisite, server events, unique `seen`, UTC periods, single claim, loaded weapons |
| `Building` | Сетка, rotation, bounds/reserved zones, footprint edge contact, цена, repair/demolish |
| `Economy` | Power/fuel, production, workers, prices, craft, cosmetics |
| `Profile` | Schema v1, defaults, allowlists, clamps, duplicate UID cleanup, campaign isolation |
| `Progression` | Tutorial facts, one-time profile reward, achievements |
| `Lease`, `RateLimiter` | Чистые политики lease ownership и token buckets |

`Bootstrap.server.luau` собирает определения, домены и 18 сервисов через dependency context. Нет циклических server `require`; Shared не выдаёт клиенту функции начисления наград.

Крупные дополнительные зависимости 0.6–0.7:

- `RecruitDefinitions` содержит пять фиксированных контрактов без клиентских цен;
- `RecruitService` владеет покупкой, runtime actors, AI-ролями и expedition idempotency;
- `ZombieService.Bosses` содержит отдельное состояние `alive/respawnAt/lastPoint` для Foreman, Reaver, Warden, Rainbow и Ironclad;
- `BuildingService:BuilderAccess` выполняет bounded grid flood-fill до оплаты, а `BuilderWork` валидирует server position Степана и списывает сохранённый `remaining`;
- `NPCService:UpdateBuilder` владеет travel/work/pause состояниями и no-jump Pathfinding Степана;
- `DialogueVoiceController` — client-only presentation над server-selected authored speech payload.

## Транзакции и idempotency

- Выдача пакета предметов сначала моделируется на копии inventory. Переполнение не оставляет частичную quest-награду.
- `RewardClaimed`, предметы, Coins, XP и quest stats меняются синхронно в одном профиле.
- Reload завершает transfer только для того же session, UID, equipped state и operation token; callback replay не выдаёт ammo снова.
- Builder auto-order проверяет ту же grid/footprint/price, что ручное строительство, плюс доступную сторону и bounded flood-fill от защищённого входа. Только после этого запись, точное списание и модель появляются вместе.
- `Builder.remaining` — единственный источник объёма работы Степана. Внутренний `BuilderWork` принимает фактическую server position его модели, повторно проверяет proximity и уменьшает остаток только в состоянии work.
- `Building.complete` и Economy дополнительно блокируют функции активного builder-order, поэтому projected `readyAt` не может сам включить здание.
- Hired builder Аркадий меняет только исходный server volume уже валидной новой записи на ×0,65; client не сообщает multiplier/time.
- Squad и gatherer используют монотонные пары `expedition/completed`; один номер рейса может увеличить stockpile один раз.
- Rainbow loot roll выполняется один раз на credited participant во время server death. Два pity-счётчика независимы и сохраняются.
- Обычный Rainbow bundle сначала атомарно пробует backpack, затем целиком поступает в stockpile. Rare grant при переполнении превращается в owner/session-bound pickup.
- Helicopter destination выбирается по authored противоположному marker; клиент получает только presentation data.
- Нет offline production. Ручной build/craft deadline и expedition absolute `returnAt` могут завершиться после reconnect, но не повторяют output. Заказ Степана хранит `remaining`, перебазирует только projected deadline и **никогда не выполняет работу офлайн**.
- Quest caches используют server part metadata и `seen`; покупка у магазина не генерирует world-collect event.

## Бой

1. Клиент строит центральный camera ray и отправляет только unit aim от головы.
2. Сервер проверяет active state, cooldown, stamina/ammo, equipped inventory UID и реальный Tool UID.
3. Основной hit — `workspace:Raycast` с исключением character.
4. Только при отсутствии zombie hit вызывается `AimTarget`: ближайшая живая server zombie должна лежать в малом weapon-specific радиусе луча, впереди, внутри range и иметь чистую server line-of-sight.
5. Headshot существует только для фактического RaycastResult по `Head`; assisted target не получает fabricated critical.
6. `ZombieService:Damage` применяет armor, записывает credit и возвращает applied/lethal.
7. Только стрелку отправляется `hit_confirm` с server damage и world position; глобальные nearby-клиенты получают tracer/impact/zombie presentation.

Клиентские damage numbers, marker, sparks и recoil не наносят урон. Friendly fire по Player не входит в target resolution.

## Rainbow loot

Для каждого валидного credited player:

```text
base: 210 Survival Cash + 260 XP
profile: 35 Coins
ordinary: один server-selected resource bundle
armor: 22%; pity counter 0..3, гарантия на четвёртой победе
blade: 30%; pity counter 0..2, гарантия на третьей победе
```

Credit требует недавнего damage, активной незакрытой сессии, живого root и расстояния до смерти не более 240 studs. Клиент не отправляет chance/roll/pity/reward.

## Специалисты и coexistence с базой

- Purchase требует `second_city=RewardClaimed`, server proximity к Ладе, отсутствие владения и достаточный Survival Cash.
- `builder`, `medic`, `guard`, `scout`, `gatherer` имеют fixed health/speed/price и owner-bound runtime model.
- Follow/base/gather разрешены только на своей базе, рядом со своим actor или рядом с Ладой. Gather доступен только добытчику и трём resource IDs.
- Guard выбирает server zombie, проверяет line-of-sight и списывает `rifle_ammo` до 22 damage.
- Medic лечит только владельца; scout snapshot не раскрывает ничего кроме kind/position/distance ближайшей server zombie.
- Все actors используют `AgentCanJump=false`, shared NPC path budget и server network ownership.
- `home` выбирается только на свободном footprint. Перед Render нового здания `NPCService:Evacuate` перемещает workers, Степана, squad и recruits; recruit сохраняет новый runtime home, а squad пересчитывает свободный guard point.
- Во время helicopter transfer следуют только actors с command `follow`; base/gather actors остаются в своём состоянии.

## DataStore

Ключ `player_<UserId>` в `LastCity_Profile_v1`:

```text
{
  Data = {
    SchemaVersion, Revision, Coins, Cosmetics, EquippedSkin,
    Achievements, Settings, Stats, TutorialRewarded,
    Campaigns = { coop = Campaign, solo = Campaign }
  },
  Lease = { Token = <JobId + GUID>, Expires = <UTC epoch> }
}
```

Campaign содержит allowlisted `Recruits`, bounded `BossPity` и `Builder={name,order,remaining,working}`; при normalization `working` всегда сбрасывается. Settings содержит bounded `voice` и boolean `firstPerson`.

- Acquire/renew/release — `UpdateAsync` с повторной проверкой token внутри callback.
- Callback не начисляет игровые награды и безопасен при replay.
- Lease TTL 180 секунд; autosave 55 секунд; за 10 секунд до непродлённого expiry session замораживается.
- Четыре I/O retry с backoff; live foreign lease не отбирается.
- Future schema и повреждённый envelope fail closed, а не заменяются default profile.
- `PlayerRemoving`/`BindToClose`: прекращение mutations, ожидание текущей записи, final snapshot и release в пределах 25-секундного deadline.
- Studio по умолчанию — явный `SessionOnly`, без вызова DataStore. Cloud QA требует опубликованную отдельную experience и opt-in.

## Производительность и лимиты

- Проверенная сборка: **7 232 BasePart**, ниже явно проверяемого binary budget 7 400; 8 143 Instances и 57 scripts.
- StreamingEnabled, MinRadius 128, TargetRadius 512. Большой статический мир генерируется offline, а не одним runtime script.
- Global maximum 34 заражённых; normal desired population ≤26 оставляет резерв боссам/рейдам. AI работает 4 Гц.
- Не более двух одновременно вычисляемых zombie paths. Мирные workers, Степан, squad и recruits делят отдельный лимит двух path computations и минимум 2,5 с до пересчёта actor.
- Bosses имеют zone restrictions, leash и один actor каждого kind. Mine minions bounded тремя authored markers.
- Client pools: 12 tracers, 8 impact sparks, 5 blast spheres, `MaxBuildings+8` popup anchors, 10 positional sound channels и 28 rain streaks. Попадание/стройка/выплата грядки не создают новый Part/GUI/Sound.
- `ActorAnimationController` обновляет tagged Motor6D до 30 Гц без Instance allocation per frame. HUD/map обновляются 10 Гц; state snapshots — около 1 Гц с coalescing.
- Максимум friendly runtime actors теперь выше 0.5: 20 global NPC плюс фиксированный per-player набор workers/builder/squad/recruits. Поэтому 9-player path/physics/FPS тест обязателен; статический part count не доказывает runtime performance.
- Production MicroProfiler, 9-client soak и измеренный mobile FPS остаются NOT TESTED.

## Звук, речь и камера

- Все звуки используют Roblox-bundled `rbxasset://sounds/...`; произвольные numeric catalog ID отсутствуют.
- `AudioController` имеет ambient loop, пять score voices и 10-channel positional pool. Danger score зависит от реально tagged zombies поблизости.
- Dialogue server payload содержит русскую display-строку, заранее написанную English speech-строку и allowlisted voice ID.
- Client строит `AudioTextToSpeech → Wire → AudioDeviceOutput(Player)` в `pcall`; generation token отменяет stale speech. Unsupported engine channel деградирует в text-only, не ломая client boot.
- `CameraController:SetFirstPerson` меняет `CameraMode` и zoom bounds. Выбор сохраняется; cinematic/dialogue/Commander остаются отдельными режимами.

## Бинарная сборка

`tools/world/{primitives,shelter,city,landscape,architecture,locations,district,mine,secondCity,encounters,roads}.mjs` создают Parts, Models, Terrain payload, Signs, Lights, quest metadata, roads, boss sites, cave, transports и spawn markers.

`build.mjs` встраивает точный Source всех Luau-файлов в бинарный Roblox DOM. `rbxm-parser` пишет место, а независимый `rbx-reader` проверяет header, hierarchy, references, geometry, UTF-8 и каждый Source byte-for-byte. Сборка повторяется дважды в одном окружении и должна дать идентичные bytes; CI дополнительно сравнивает полный декодированный DOM между jobs.

`Terrain.PhysicsGrid` остаётся opaque BinaryString: writer исправляет upstream default по pinned Studio fixture и сохраняет trailing NUL bytes. Каждый offline object защищён от expando fields; генератор использует сериализуемые имена свойств (`Color3uint8`, `TimeOfDay`, `FontFace`).

Это проверяет структуру/содержимое `.rbxl`, но **не запускает Roblox Engine**.

## Дороги и мир

- Общая сеть Меридиана вычитает перекрёстки, чтобы coplanar asphalt не накладывался.
- `secondCity.mjs` экспортирует точные constants 15 road rectangles и 26 sidewalk segments; tests и verifier используют те же authored footprints и попарно проверяют объекты/асфальт.
- `encounters.mjs` строит компактные arena с cover, boundary rails, light и скрытым spawn marker — без fake interaction.
- `architecture.mjs` создаёт enterable compact homes с раздельными стенами/проёмом.
- Cars остаются декорациями. Вертолёты — authored models для server-timed маршрута, не свободно пилотируемый транспорт.

## Runtime rigs

`ModelFactory.rig` создаёт R15-подобную сборку из 15 видимых частей, Motor6D и RigAttachment. Server services задают path/state/health; клиенты локально анимируют только tagged `LastCityActor` через `Motor6D.Transform`. Состояния включают idle/walk/talk/head scratch/salute/work/build/armed/aim/downed и infected idle/chase/attack/death.

Клиент не выбирает AI state, путь, цель, урон или награду. Визуальная корректность Motor6D и репликация двух клиентов требуют Studio QA.
