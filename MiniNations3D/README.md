# 🌍 MINI NATIONS 3D — WORLD DOMINATION: ULTIMATE EDITION

**Premium Low Poly 3D Grand Strategy для Roblox**

> Полноценная 3D-стратегия: строительство, экономика, дипломатия, войны, мультиплеер, редактор карт.

## 📦 Что внутри

```
/MiniNations3D
  ├── MiniNations3D.rbxl          ← ГОТОВЫЙ PLACE ФАЙЛ (открыть в Roblox Studio)
  ├── default.project.json        ← Rojo проект
  ├── src/
  │   ├── shared/                 ← Общие конфиги, типы, данные стран/провинций
  │   ├── server/Services/        ← 25+ серверных сервисов (авторитетная логика)
  │   ├── client/Controllers/     ← Клиентские контроллеры + UI
  │   └── workspace/              ← Процедурная карта
  └── docs/
```

## 🚀 Быстрый старт

### Вариант A — Открыть готовый файл
1. Открой `MiniNations3D.rbxl` в **Roblox Studio**
2. Нажми **Play (F5)** — увидишь главное меню с 3D фоном
3. Нажми **ОДИНОЧНАЯ ИГРА → НОВАЯ ИГРА → выбери год/страну → НАЧАТЬ**
4. Строй города, управляй экономикой, объявляй войны!

### Вариант B — Rojo workflow
```bash
npm i -g rojo
rojo serve
# подключи плагин Rojo в Studio → Sync
```

## 🎮 Управление

| Действие | Клавиша/Жест |
|---|---|
| Движение камеры | WASD / свайп по краю (мобилки) |
| Зум | Колесо / pinch |
| Вращение | ПКМ + мышь |
| Выбор провинции | ЛКМ |
| Панорамирование | Средняя кнопка |
| Фокус на столице | F / двойной тап |

## 🧠 Архитектура (24 сервиса)

GameService, MenuService, LobbyService, MatchService, ScenarioService, CountryService, ProvinceService, CityService, BuildingService, PowerService, EconomyService, ResourceService, MilitaryService, WarService, DiplomacyService, AllianceService, TechnologyService, AIService, ShopService, CoinService, QuestService, AchievementService, MapEditorService, SaveService, AudioService

Вся важная логика — на сервере. Проверка RemoteEvents, rate limit, валидация.

## 🗺️ Редактор карт
Главное меню → 🗺️ РЕДАКТОР КАРТ → кисти (земля/вода/горы/лес/границы), страны, провинции, ресурсы → СОХРАНИТЬ → карта появляется в одиночной игре и мультиплеере.

## 💾 Сохранения
DataStoreService: профиль, коины, XP, покупки, достижения, обучение, кастомные карты, кампании. Отдельные ключи для мультиплеера с защитой от конфликтов.

## 🎨 Стиль
Premium Low Poly, miniature world, тёплые палитры, мягкие тени, стеклянные панели (glassmorphism), градиенты, анимации Tween.

## ⚠️ Примечания
- Музыка — только из Roblox Creator Marketplace (ID указаны в AudioService)
- Для мультиплеера используются MemoryStoreService + TeleportService
- Оптимизировано под StreamingEnabled, LOD и пакетную отрисовку провинций

Сделано как вертикальный слайс: Меню → Выбор страны → Карта → Строительство → Экономика → Армия → Война → Победа,
с полной заготовкой для расширения до всех 40 пунктов ТЗ.

— Senior Roblox / Luau Stack, 2026
