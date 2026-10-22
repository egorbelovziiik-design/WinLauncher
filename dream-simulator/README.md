# 🌙 DREAM SIMULATOR: NIGHTMARE WORLDS

Хоррор-игра для Roblox на Luau. Готовое место: `DreamSimulator.rbxl`.

## Как открыть
1. Скачай `DreamSimulator.rbxl`.
2. Roblox Studio → **File → Open from File…** → выбери файл.
3. Нажми **Play** (F5). В лобби появится меню, сверху слева — кнопка «☰ МЕНЮ».

## Управление
| Клавиша | Действие |
|---|---|
| WASD | движение |
| SHIFT | бег (тратит выносливость и привлекает монстров) |
| E | взаимодействие (NPC, предметы, портал) |
| F | фонарик (батарея тратится, пока он включён) |
| TAB | задания |
| M / ☰ МЕНЮ | меню и пауза |

## Фонарик и батарея
- Фонарик есть всегда: базовый луч (44 м) работает без покупок.
- «Фонарик» в магазине (150) — 62 м, «Лампа сновидца» (300) — широкий луч 76 м.
- Заряд 100%, тратится ~0,38–0,78 %/с в зависимости от предмета, медленно
  восстанавливается, когда свет выключен (0,12 %/с).
- Батарейки: зелёные блоки по сну (+35–55 %), NPC за 55 монет (+80 %),
  расходник «Батарейка» в инвентаре (Q) +75 %.
- Свет помогает: монстры теряют игрока из виду на 45 % позже, но включённый
  фонарик слышно и видно — иногда ближайший монстр сразу узнаёт, где ты.
- Низкий заряд или низкий рассудок → луч мерцает.

## Что внутри
- Лобби-спальня (`Workspace.MenuBedroom`), меню, магазин, инвентарь,
  достижения, статистика, настройки, обучение.
- 10 снов в `ServerStorage.DreamMaps`: лес, школа, город, больница, цирк,
  затопленный город, ледяной мир, фабрика, станция, замок.
- 14 монстров (5 архетипов + тематические), PathfindingService, скримеры.
- 38 заданий, NPC с диалогами (задания, лечение, торговля, батарейки, слухи),
  портал пробуждения, 600-секундный таймер.
- Батарейки и предметы разбросаны по карте, 16 случайных событий,
  рассудок с искажениями и галлюцинациями.
- Сохранение профиля в DataStoreService.

## Сборка из исходников
```bash
npm install
node tools/luaucheck.mjs     # проверка синтаксиса всех .luau
node tools/checkmaps.mjs     # проверка маркеров карт (пол/перекрытия)
node tools/build.mjs         # → build/DreamSimulator.rbxl(.rbxlx)
node tools/verify.mjs build/DreamSimulator
```
Сборщик: `tools/rbxkit.mjs` + `rbx-dom`; карты — `tools/maps/*.mjs`,
интерфейс — `tools/gui.mjs`, скрипты — `src/**`.

## Структура скриптов
```
ReplicatedStorage/SharedModules  Config, Util, Remotes, ItemDefinitions,
                                 DreamDefinitions, MonsterDefinitions,
                                 QuestDefinitions, AchievementDefinitions
ServerScriptService              MapService, DataManager, EconomyManager,
                                 AchievementManager, QuestManager, NPCManager,
                                 MonsterManager, DreamManager, Main
StarterPlayerScripts             BootMenu (страховка UI), ClientMain + Modules/
```
