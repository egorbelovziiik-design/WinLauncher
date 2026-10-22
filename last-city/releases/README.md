# LAST CITY 3D — готовый Roblox place

`LAST_CITY_3D.rbxl` — бинарное место **IRON HORIZON 0.5.0**, развиваемый настоящий PvE vertical slice.

SHA-256:

```text
f9d6718bad526fb1e599c616c8546f93b844f3bbd03bca47333117b38f2a6b1c  LAST_CITY_3D.rbxl
```

Открыть: Roblox Studio → **File → Open from File** → `LAST_CITY_3D.rbxl` → **Play / F5**. Инструкция и управление: [README](../README.md). Рюкзак теперь открывается **K**; клавиша **1** достаёт и повторно убирает лом.

Автоматически PASSED: 52 Luau sources, 10 serialization tests, 7 road tests, 117 domain tests, 89 mock-adapter tests, 110 binary assertions и повторная byte-for-byte сборка. Файл содержит 7 189 Instances, 6 553 BasePart и 52 scripts; размер 293 152 bytes.

**Честный gate:** импорт/Play именно 0.5.0, Motor6D, Humanoid physics, Pathfinding, звук, Streaming, живой DataStore и multiplayer в Roblox Studio пока **NOT TESTED**. Пройдите [STUDIO_QA.md](../docs/STUDIO_QA.md), особенно раздел 13.

История: 0.1.1 исправила некорректный `Terrain.PhysicsGrid`; пользователь подтвердил открытие 0.2.0 и частичный Play 0.3.0. 0.3.0 выявила позы рук, крышу и взаимодействие Виктора; автоматические исправления вошли в 0.4.0. Для 0.4.0 отдельного Studio-подтверждения не было.
