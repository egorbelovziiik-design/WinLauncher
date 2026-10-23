# LAST CITY 3D — проверенный Roblox place

`LAST_CITY_3D.rbxl` — бинарное место **FRONTIER REFORGED 0.7.0**, развиваемый настоящий PvE vertical slice.

[**Скачать LAST_CITY_3D.rbxl 0.7.0**](https://raw.githubusercontent.com/egorbelovziiik-design/WinLauncher/7062ce50d1e4551752fdd02a6eeededaf92021b9/last-city/releases/LAST_CITY_3D.rbxl)

SHA-256:

```text
9311ea07bae623f8f15a8fa0c19c692e6a52a50aeb9cbba1501ded1e65e50cc7  LAST_CITY_3D.rbxl
```

Размер: **365 912 bytes**.

Открыть: Roblox Studio → **File → Open from File** → `LAST_CITY_3D.rbxl` → **Play / F5**. Клавиша **1** достаёт/убирает лом, **2–6** активируют сохранённые быстрые слоты, **K** открывает рюкзак, **O** — настройки, **Z** — первое лицо; **I** не назначена.

## Главное в 0.7.0

- НОВЫЙ РУБЕЖ расширен до трёх кварталов на основании `740 × 1080` studs: 15 дорог, 26 тротуаров, 14 проходимых домов, пять новых учреждений и отдельный Бронеарсенал;
- добавлены движущиеся NPC Ярослав, Михаил и Дарья с тремя заданиями на эксклюзивные меч, электрозащитную броню и заряженную DMR;
- Howler, Shocker, Crusher и Ironclad имеют настоящие server-side support/electric/regen/boss механики;
- 25 новых богатых контейнеров легально выдают материалы, медицину, гранаты и боеприпасы;
- Степан теперь находит доступный маршрут, идёт к footprint и выполняет сохранённую работу только у площадки; опасность, разговор, путешествие и закрытый путь ставят прогресс на паузу, офлайн-прогресса нет;
- незавершённая стройка не может запустить производство через истёкший projected deadline; оплата маршрута и материалов атомарна;
- **O** открывает настройки; исправлены конкретные пересечения кузницы/арены, релея/дома и quest-cache/прессов;
- сохранены Алекс 25, Борис/Рада/Глеб, Prism Cave, специалисты Лады, Glock 10/10 + ровно 48 reserve и Walker 5 HP.

Полное описание: [UPDATE_0.7.0.md](../docs/UPDATE_0.7.0.md).

## Проверка артефакта

Автоматически **PASSED**:

- 57/57 Luau sources;
- 10/10 serialization tests;
- 10/10 road/geometry tests;
- 133/133 domain tests;
- 126/126 mock-adapter tests;
- 270/270 binary assertions;
- две byte-for-byte identical сборки.

Файл содержит **8 143 Instances, 7 232 BaseParts и 57 scripts**.

**Честный gate:** импорт/Play именно 0.7.0, Humanoid/Pathfinding, weld-посадка, физика гранат, TTS/audio playback, Streaming, live DataStore и multiplayer в Roblox Studio пока **NOT TESTED**. Пройдите [STUDIO_QA.md](../docs/STUDIO_QA.md).

История: 0.6.0 загрузила меню, но пользовательский Play застревал на character-loading notice; это **FAILED**, а не PASSED. 0.6.1 и 0.7.0 имеют автоматические lifecycle-регрессии, но их Studio gate остаётся **NOT TESTED** до нового ручного теста.
