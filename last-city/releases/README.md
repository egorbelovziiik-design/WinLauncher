# LAST CITY 3D — проверенный Roblox place

`LAST_CITY_3D.rbxl` — бинарное место **ECHOES OF STEEL 0.6.1**, развиваемый настоящий PvE vertical slice.

[**Скачать текущий проверенный файл**](LAST_CITY_3D.rbxl)

SHA-256:

```text
476ff32a113cce48bd4d2bdd9aca9afcf85d0d3cf7cfd1a0da2edf0e65b4f4a2  LAST_CITY_3D.rbxl
```

Размер: **345 797 bytes**.

Открыть: Roblox Studio → **File → Open from File** → `LAST_CITY_3D.rbxl` → **Play / F5**. Клавиша **1** достаёт/убирает лом, **2–6** активируют сохранённые быстрые слоты, **K** открывает рюкзак, **Z** переключает первое лицо; **I** не назначена.

## Главное в 0.6.1

- guarded server recovery исправляет тупиковый вход по «ИГРАТЬ»;
- каждая готовая целая грядка платит ровно +10 Survival Cash каждую онлайн-секунду;
- две бывшие парящие доски заземлены как поддоны E-склада, а сохранённая куча растёт от настоящего дерева;
- шесть сохраняемых quick slots с atomic Equip → first empty;
- четыре новых меча, пять firearms и четыре раздельных ammo ID;
- осколочная, зажигательная и светошумовая гранаты с server-owned projectile, falloff/burn/stun;
- шесть новых видимых комплектов брони, включая следопыта и сапёра с реальной explosive resistance;
- сохранены City 2, Prism Cave/Rainbow Guardian, recruiter roles, Алекс 25, Борис/Рада/Глеб, Glock 10/10 + 48 reserve и walker 5 HP.

Полное описание: [UPDATE_0.6.1.md](../docs/UPDATE_0.6.1.md).

## Проверка артефакта

Автоматически **PASSED**:

- 57/57 Luau sources;
- 10/10 serialization tests;
- 9/9 road tests;
- 128/128 domain tests;
- 114/114 mock-adapter tests;
- 218/218 binary assertions;
- две byte-for-byte identical сборки.

Файл содержит **7 580 Instances, 6 816 BaseParts и 57 scripts**.

**Честный gate:** импорт/Play именно 0.6.1, Humanoid/Pathfinding, weld-посадка, физика гранат, TTS/audio playback, Streaming, live DataStore и multiplayer в Roblox Studio пока **NOT TESTED**. Пройдите [STUDIO_QA.md](../docs/STUDIO_QA.md).

История: 0.6.0 загрузила меню, но пользовательский Play застревал на character-loading notice; поэтому она отмечена FAILED, а не PASSED. Автотесты 0.6.1 покрывают recovery, но статус станет PASSED только после нового ручного Studio-теста.
