# Отчёт о проверках — Living Meridian 0.3.0

Дата: **2026-10-22 (UTC)**.

## Честная граница результата

**Roblox Studio / Roblox Engine в среде разработки недоступен. Ни одного настоящего Play Test, сетевого теста Roblox или обращения к живому Roblox DataStore здесь не выполнялось.**

`PASSED` ниже относится только к точно указанному слою. Успешная сборка/десериализация **не означает**, что физика, анимация, Pathfinding, touch UX, звук и TeleportService прошли тест в движке.

## Инцидент импорта, сообщённый пользователем

- **0.1.0 — FAILED: открытие места в Roblox Studio.** Скриншот пользователя: `Error while decoding data: unexpected end at offset 2`.
- У поставляемого `.rbxl` `Terrain.PhysicsGrid` имел байты `02 03` (2 байта); пустая сетка из настоящего Studio-файла имеет `02 03` + 12 нулевых байт (14 байт). Это дефект генерации, а не подтверждённая проблема установки Studio или ZIP на компьютере пользователя.
- **0.1.1 — исправлен код сериализации.** `tests/fixtures/empty-terrain.json` содержит байты и immutable-ссылку на Studio fixture. Новый regression test показывает, что общие парсеры принимали старое повреждённое поле, тогда как семантическая проверка его отвергает.
- Старый импорт остаётся FAILED в истории. **Открытие и статический рендер выпущенной 0.2.0 подтверждены скриншотами пользователя**, но Play/физика не подтверждены. Открытие и Play новой 0.3.0 — NOT TESTED. Ни CI, ни приведённый ниже PASSED не заменяют их.

## Выполнено автоматически

| Проверка | Статус | Объём / доказательство |
|---|---|---|
| Компиляция всех игровых Luau-файлов | **PASSED** | 49 исходников, Luau 0.743 |
| Анализ исходников | **PASSED** | Нет TypeError/неожиданных диагностик. Одна известная standalone-диагностика `Unknown type Vector3`: CLI не содержит реестр Roblox userdata |
| Регрессии сериализации | **PASSED** | 10 тестов: отрицательный контроль старой сетки, fixture, NUL bytes, Color3uint8/FontFace/TimeOfDay, запрет несериализуемых свойств |
| Доменные регрессии | **PASSED** | 103 теста `tests/domain.spec.luau` исполняют настоящие модули |
| Обработчики, composition root, UI construction | **PASSED** | 71 тест через `MockRoblox` — API doubles, не движок и не визуальная проверка |
| Бинарный Roblox header / независимое чтение | **PASSED** | 67 проверок `tools/verify.mjs`, writer и reader разных пакетов |
| Исходники внутри `.rbxl` | **PASSED** | Все 49 Source совпадают с файловыми исходниками после строгого UTF-8 decoding |
| Двойная сборка | **PASSED** | Повторная сборка даёт байт-в-байт тот же `.rbxl` |
| StreamingEnabled, сервисы, remotes, геометрия | **PASSED** | 5 592 instances; 5 115 BasePart; 11 RemoteEvent; 6 участков; 12 zombie spawns; 3 supply crates; 8 quest targets |
| Открытие `.rbxl` 0.1.0 в Studio | **FAILED** | Скриншот пользователя; дефект PhysicsGrid найден |
| Открытие `.rbxl` 0.1.1 в Studio | **PASSED** | Пользователь прислал скриншоты сцены и загрузочного экрана; это не подтверждает Play |
| Открытие версии 0.2.0 | **PASSED** | Скриншоты пользователя показывают открытое и отрендеренное место; это не подтверждает Play |
| Открытие / Play версии 0.3.0 | **NOT TESTED** | Новая сборка ещё не проверена пользователем |
| Дорожная геометрия | **PASSED** | 7 тестов: пересечения, бордюры, private plots, общие стыки |
| Roblox Studio Play Test | **NOT TESTED** | Нужен Roblox Studio |
| Настоящий DataStore / solo Teleport | **NOT TESTED** | Нужна опубликованная тестовая experience |
| Реальная репликация 2–6 клиентов | **NOT TESTED** | Нужен Studio Server & Clients и опубликованный сервер |
| FPS, MicroProfiler, мобильный UX | **NOT TESTED** | Нужны реальные устройства/эмулятор Studio |

Между окружениями snapshot проверяется по всем декодированным свойствам/ссылкам/геометрии/Source, а не по представлению LZ4-чанков. Локальная повторяемость остаётся побайтной.

Воспроизведение: `python3 last-city/tools/test.py`. Подробные stdout и JSON формируются в `release-artifacts/last-city/`. Контрольная сумма поставляемого файла — `releases/SHA256SUMS`. Машиночитаемый результат последней проверки — `docs/AUTOMATED_RESULTS.json`.

## Что проверяют доменные тесты

- Отдельные campaign inventories; объём, вес, стопки, UID, перемещение, экипировка и броня.
- Нулевые/отрицательные/дробные/NaN/бесконечные количества; переполнение без частичных изменений.
- Сохранение общего количества при переносе склад ↔ рюкзак.
- Приём заданий, prerequisites, только нужный тип зомби, уникальные ящики, все цели генератора, суточная граница UTC.
- Повторный claim, неполные цели, заполненный рюкзак и точные награды.
- Сетка, поворот, размер участка, пересечения, зарезервированные вход/палатка, стоимость, cap, чертёж, уровень, ремонт и однократный снос.
- Топливо, мощность до/после стройки, brownout, неработающие без питания потребители, назначение работника/ночь, производственная цепочка, мораль, отсутствие offline catch-up.
- Цены/запасы/репутация, отдельные валюты, повторная косметика, таймер/очередь крафта, early/double claim.
- Сохранение tutorial reward-флага между кампаниями и повтором, достижения.
- Нормализация/roundtrip Coins, inventory, quest state, workers, buildings, settings и craft; неизвестная schema.
- Lease policy, просроченный token, чужой сервер, token buckets.
- Последовательность accept → kill events → claim → equip → magazine transfer → build → power → profile roundtrip.

## Что проверяют mock-тесты адаптеров

Исполняется **исходный код** сервисов и клиента с небольшой тестовой заменой Roblox API; логика не переписана в тестах.

- Построение объектов всех UI-панелей, меню/Play RPC, tutorial overlay, dialogue choices, portrait construction, настройки, схемы размеров 390×844 и 844×390.
- Вызовы трёх режимов камеры без Lua-ошибок в тестовом окружении — **не** реальная кинематика/визуальная плавность.
- Серверные проверки proximity, ownership boundary provider, unloaded/closing/frozen/inactive, client-supplied rewards, запрещённых payload и rate limit.
- Создание модели постройки, расход материалов, уничтожение, reset задания рабочего.
- Серверное вычисление урона вместо клиентского `damage`, расход ammo, shot cooldown, проверки направления/Tool, отложенная перезарядка и уход игрока.
- `SaveService` с имитацией UpdateAsync: acquire, callback replay, запись/освобождение, 4 retry, потерянная lease, cloud failure, fail-closed live behavior и явный Studio SessionOnly.
- Полный composition root, загрузка игрока, создание работников, Play, 5 kill events → reward один раз, настоящий обработчик loot prompt и snapshot.
- Личный дроп нельзя получить с чужим UserId или старым session token.
- Запуск/пропуск/повтор tutorial через настоящий SessionRemote-handler; NPC prompt выдаёт dialogue payload.

Mock не вычисляет физику, пути, стриминг, задержки сети, видимость UI или звук. Эти проверки не повышают Studio-статус до PASSED.

## Обязательный список из §54

**Итоговый статус — для настоящей игры в Roblox.** Столбец справа указывает уже выполненную подготовительную проверку.

| № | Проверка | Статус Roblox | Что проверено вне Studio |
|---:|---|---|---|
| 1 | Главное меню открывается | **NOT TESTED** | PASSED: construction и переключение видимости в mock |
| 2 | Кнопка «Играть» работает | **NOT TESTED** | PASSED: RPC, серверный Start, unfreeze и Tool в mock |
| 3 | Обучение запускается | **NOT TESTED** | PASSED: настоящий SessionRemote-handler в mock |
| 4 | Обучение можно пропустить | **NOT TESTED** | PASSED: skip/replay состояния в mock |
| 5 | NPC выдаёт задание | **NOT TESTED** | PASSED: prompt/dialogue, proximity и accept handler в mock |
| 6 | Задание сохраняется | **NOT TESTED** | PASSED: profile roundtrip и mock UpdateAsync |
| 7 | Прогресс задания обновляется | **NOT TESTED** | PASSED: домен и пять kill callbacks в mock |
| 8 | Награда выдаётся только один раз | **NOT TESTED** | PASSED: replay, full backpack, reload roundtrip |
| 9 | Оружие появляется в инвентаре | **NOT TESTED** | PASSED: точная серверная награда/UID в тестах |
| 10 | Оружие наносит урон | **NOT TESTED** | PASSED: damage/raycast-adapter double, ammo/cooldown |
| 11 | Зомби преследует игрока | **NOT TESTED** | Код Pathfinding/MoveTo включён; пути и Humanoid в движке не проверены |
| 12 | Зомби может быть побеждён | **NOT TESTED** | PASSED: mock Humanoid death → однократная награда |
| 13 | Игрок может построить здание | **NOT TESTED** | PASSED: сетка/домен/серверный handler/model в mock |
| 14 | Ресурсы списываются | **NOT TESTED** | PASSED: точные транзакции, отказ без списания |
| 15 | Генератор производит энергию | **NOT TESTED** | PASSED: топливо/готовность/12 кВт/brownout в домене |
| 16 | NPC выполняют работу | **NOT TESTED** | PASSED: производство с `atWork`, смена и мораль; физический путь не проверен |
| 17 | Зомби атакуют базу | **NOT TESTED** | PASSED: damage/removal handler; AI-подход/ночной рейд в движке не проверен |
| 18 | Транспорт управляется | **NOT TESTED** | Не реализовано. Автомобили на карте — декорации |
| 19 | Магазин работает | **NOT TESTED** | PASSED: стоимость, запасы, overflow, косметика и разделение валют |
| 20 | Coins сохраняются | **NOT TESTED** | PASSED: profile roundtrip, mock UpdateAsync; не cloud API |
| 21 | Инвентарь сохраняется | **NOT TESTED** | PASSED: UID/ammo/содержимое в roundtrip; не cloud API |
| 22 | Квесты сохраняются | **NOT TESTED** | PASSED: статусы/прогресс/уникальные ящики в roundtrip |
| 23 | Мультиплеер синхронизируется | **NOT TESTED** | Только структура серверной репликации, без двух настоящих клиентов |
| 24 | Нет критических ошибок в игре | **NOT TESTED** | PASSED: compilation + mock boot. Отсутствие engine-ошибок не гарантируется |

## Исправлено во время проверки

| Найденная проблема | Повторная проверка |
|---|---|
| Пустая непопавшая в Git папка меняла DOM при сборке на CI | **PASSED** — builder пропускает каталоги без Luau; сравнивается весь декодированный DOM |
| Повреждённый existing envelope мог превратиться в новый профиль | **PASSED** — загрузка fail-closed, старые данные не меняются |
| Byte-string writer обрезал Unicode до младшего байта | **PASSED** — явное UTF-8 encoding, независимое полное сравнение Source/вывесок и roundtrip |
| Возможность ставить модель поверх статической палатки | **PASSED** — общие reserved footprints на сервере и в призраке |
| Потенциальная межпрофильная передача дропа без durable ledger | **PASSED** — только тот же владелец и session token, ограниченный TTL |
| Неверные внешние `self` в клиентских callback | **PASSED** — явное замыкание контроллера, mock исполнения |
| Повтор tutorial мог упираться в уже полученную одноразовую награду | **PASSED** — ранее доказанные одноразовые факты переносятся в повтор, награда не дублируется |

**FAILED в текущем автоматическом прогоне: 0; исторический импорт 0.1.0 в Studio: FAILED.** Дальнейший release-gate — ручной Studio QA, а не переименование NOT TESTED в PASSED.

## 0.2.0 — дополнительные проверки

См. [UPDATE_0.2.0.md](UPDATE_0.2.0.md). Добавлены negative controls для `GetDataStore()` до загрузки профиля, неопубликованного Studio place, screen timeout 45 с без фиктивной готовности, дверей, medical/intel prompt, сохранения двух новых заданий и рецепта бинтов. Реалистичность картинки и FPS не оценивались headless-тестами.

## 0.3.0 — дополнительные проверки

См. [UPDATE_0.3.0.md](UPDATE_0.3.0.md). Добавлены проверки полного 15-Motor6D R15-подобного состава, явного `KeyboardKeyCode.E`, настоящего dialogue payload рядом и отказа вдали, разрешённого касания footprint при запрете пересечения, трёх deduplicated улик нового задания, сериализованных Clouds/WedgePart/лагеря/колонны, отсутствия старых `DistantHills` и лимита 6 500 BasePart. Физика rig, визуальная плавность, FPS и Pathfinding остаются NOT TESTED до Studio QA.
