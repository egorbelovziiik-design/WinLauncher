# LAST CITY 3D — проверенный Roblox place

`LAST_CITY_3D.rbxl` — бинарное место **ECHOES OF STEEL 0.6.0**, развиваемый настоящий PvE vertical slice.

[**Неизменяемая прямая загрузка с GitHub**](https://github.com/egorbelovziiik-design/WinLauncher/raw/ecc9a63469b42200373c0059ea15d65cedd36f78/last-city/releases/LAST_CITY_3D.rbxl)

SHA-256:

```text
fe53e7ce00faaf19048dc64be992cfa341e06736d6d8005e24567f9b7e00b458  LAST_CITY_3D.rbxl
```

Открыть: Roblox Studio → **File → Open from File** → `LAST_CITY_3D.rbxl` → **Play / F5**. Инструкция: [README](../README.md). Клавиша **1** достаёт/убирает лом, **K** открывает рюкзак, **Z** переключает первое лицо; **I** не назначена.

## Что включено

- исправленные дороги/тротуары НОВОГО РУБЕЖА, шесть домов, богатые ящики и четыре новых задания второго города;
- физическая Prism Cave и Радужный Страж с обычными ресурсами, 35 Coins, независимыми шансами/pity для Радужной брони и Призматического клинка;
- вербовщик Лада и пять сохраняемых специалистов с настоящими server roles;
- расширенная лестница оружия/брони Меридиана, вращающиеся боссы, server-assisted hit registration и pooled visual feedback;
- сохраняемое первое лицо, адаптивный bundled audio mix и guarded English engine TTS для русских диалогов;
- сохранённые контракты Алекса 25, Бориса/Рады/Глеба, mine Glock 10/10 + 48 reserve, walker 5 HP, K/1 controls и двусторонний вертолёт.

## Проверка артефакта

Автоматически **PASSED**:

- 55/55 Luau sources;
- 10/10 serialization tests;
- 9/9 road tests;
- 121/121 domain tests;
- 95/95 mock-adapter tests;
- 170/170 binary assertions;
- две byte-for-byte identical сборки.

Файл содержит 7 568 Instances, 6 809 BasePart и 55 scripts; размер 328 493 bytes.

**Честный gate:** импорт/Play именно 0.6.0, Motor6D, Humanoid physics, collision, Pathfinding, первое лицо, TTS/audio playback, Streaming, live DataStore и multiplayer в Roblox Studio пока **NOT TESTED**. Пройдите [STUDIO_QA.md](../docs/STUDIO_QA.md), особенно раздел 14.

История: 0.1.0 не открылась из-за truncated `Terrain.PhysicsGrid`; 0.1.1 исправила бинарный payload. Пользователь подтвердил открытие 0.2.0 и partial Play 0.3.0. Для 0.4.0, 0.5.0 и 0.6.0 отдельного Studio-подтверждения нет; статус старой версии автоматически не переносится.
