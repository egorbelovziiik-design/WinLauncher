# Происхождение ресурсов и ограничения — 0.7.0

## Визуальные ресурсы

- Карта, дороги, дома, пещера, здания, автомобили-декорации, растительность, вертолёты, R15-подобные NPC, броня, оружие и UI создаются собственным кодом из стандартных Roblox Instances.
- Нет скачанных Toolbox-моделей, внешних runtime-скриптов, `require(assetId)`, выдуманных MeshId/TextureId или локальных путей, которые Roblox не сможет загрузить.
- Автомобили — **декорации, не управляемый транспорт**. У них нет ложных Drive prompts.
- Две модели вертолёта собраны из native Parts. `TravelService` использует их для фиксированного двустороннего рейса; свободное пилотирование не заявляется.
- Всё оружие, включая двустволку, Призматический клинок, Frontier Sword/Cleaver/DMR/Marshal Pistol, является native-Part Tool с welds. Радужная/Грозовая/элитная броня, armor plates и детали заражённых также не зависят от внешних meshes.
- NPC используют собственные процедурные Motor6D-позы: idle, walk, talk, HeadScratch, Salute, work/build, armed idle/aim, downed, eat, sleep, flee; заражённые — idle, walk/chase, attack и death. Внешние AnimationId не выдаются за поставленные.
- Аватар профиля запрашивается штатным `rbxthumb://type=AvatarHeadShot` для текущего игрока.

## Blender

`bpy` Blender 4.2.23 был скачан в игнорируемую `.local/blender-python` только для оценки offline-authoring. Импорт в sandbox остановился из-за отсутствующих системных X/GL runtime-библиотек (`libXrender`, `libGL` и др.). Небезопасные сторонние бинарники не подставлялись.

Поэтому в 0.7.0 **нет заявленных Blender MeshPart**. Мир использует настоящие native Parts/WedgeParts, а не неработающие ссылки на локальные FBX/GLB.

## Звук

`GameConfig.Audio` использует только файлы из bundled-поставки Roblox через `rbxasset://sounds/...`:

| Файл | Использование |
|---|---|
| `electronicpingshort.wav` | UI и hit/kill confirm |
| `bass.wav` | ноты адаптивной партитуры |
| `wind.mp3` | тихий ambient texture |
| `snap.mp3` | лёгкий выстрел |
| `paintball.wav` | тяжёлый выстрел |
| `swoosh.wav` | blunt/melee swing |
| `swordslash.wav` | клинок/топор |
| `impact_generic.mp3` | попадание по поверхности/цели |
| `electricspark.mp3` | контакт с бронёй |
| `uuhhh.mp3` | hurt и pitch-varied voice заражённых |
| `swordlunge.wav` | атака заражённого |
| `action_footsteps_plastic.mp3` | rotor texture во время рейса |

Произвольных `rbxassetid://<catalog>` ID, сторонней музыки или заявлений о чужой лицензии нет. `AudioController` переиспользует sound channels, меняет pitch/tempo по реальной nearby danger и запускает combat sounds от настоящих server/client combat events. Это собственная процедурная аранжировка bundled-файлов, не сторонний музыкальный трек.

Фактическая доступность, громкость и качество каждого bundled файла в текущем Roblox client остаются **NOT TESTED** до Studio QA.

## Engine-native TTS

- Озвучка не содержит загруженного audio asset и не обращается к внешнему speech API.
- `DialogueVoiceController` использует штатные Roblox `AudioTextToSpeech`, `Wire` и `AudioDeviceOutput`.
- Русская display-реплика имеет отдельно написанный английский speech-текст, выбранный сервером. Текущая официальная voice-линейка документирует английские голоса; русская синтетическая речь не заявляется.
- Один generation-cancelled канал ограничивает текст 300 символами. Если класс/голос/загрузка недоступны в данном Studio channel, `pcall` оставляет работающий текстовый диалог и выводит warning.
- Настройка `voice=0` должна заглушить TTS; `music` и `effects` управляются независимо.
- Реальный `LoadAsync`/playback, голос, latency и поведение двух клиентов остаются **NOT TESTED**.

## Монетизация

Нет Robux-продуктов, Developer Products или MarketplaceService-покупок. «Магазин Coins» — внутриигровая косметическая операция профиля. Специалисты покупаются только за Survival Cash и не используют Coins/Robux.

## Инструменты сборки (не встраиваются в place)

| Зависимость | Использование | Лицензия upstream |
|---|---|---|
| Luau 0.743 | Компиляция, анализ, выполнение тестов | MIT |
| rbxm-parser 1.1.4 | Writer бинарного Roblox DOM | MIT |
| rbx-reader 1.5.10 | Независимая проверка артефакта | AGPL-3.0-or-later |
| Blender Python `bpy` 4.2.23 | Не запущенная из-за native runtime оценка offline-authoring; не входит в `.rbxl` | GPL-3.0-or-later |

Полные зависимости и лицензии указаны в `tools/package-lock.json` и upstream packages. Игровой `.rbxl` не содержит runtime npm/Python-инструментов.
