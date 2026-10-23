# Дальнейшие этапы — НЕ готовые функции

Исходное ТЗ значительно больше текущего среза. В 0.7.0 уже есть два города, 9 личных участков, 20 глобальных NPC, Степан с настоящей стройкой, 5 покупаемых специалистов, quest-отряд, двусторонний server-timed вертолёт, 17 зданий, 18 заражённых, пять независимых boss lifecycle, проходимая шахта, Prism Cave, Бронеарсенал, 19 заданий и сохраняемые награды. Ниже перечислены только оставшиеся этапы; это не обещание уже работающих кнопок.

## Ближайший gate — Roblox Studio 0.7.0

1. Пройти раздел 16 `STUDIO_QA.md`: import/F5/Output, три района City 2, 14 домов, пять новых объектов, богатые crates, Ярослав/Михаил/Дарья, Howler/Shocker/Crusher/Ironclad, награды и настоящий цикл Степана.
2. Повторить обязательные регрессии: K/1/O, no-jump, Алекс 25, два рейса, Борис → Рада/Глеб, Rainbow pity, специалисты Лады и mine Glock 10/10 + 48.
3. Выполнить Server & Clients с 2, затем 9 игроками; зафиксировать Output, видео, FPS, memory, Physics, Network и MicroProfiler при cap 34 заражённых и максимальном наборе friendly actors.
4. Выполнить persistent test в отдельной опубликованной test experience: UpdateAsync, specialist commands/expedition, pity, новые inventory items, first-person/voice, reconnect, server handoff и BindToClose.
5. Исправить любые engine-specific дефекты grounding, collision, Motor6D, Pathfinding, TTS/audio availability, camera clipping, Streaming и mobile safe area. Автоматические тесты не дают им PASSED.
6. После исправлений провести accessibility, gamepad/navigation и balance pass; вероятности редких предметов проверять статистически, не одной попыткой.

## Следующее расширение систем

- Группы/роли с server ACL для добровольной общей базы; обычный игрок не получает права на чужой участок.
- Durable ledger для межпрофильного обмена только после crash/retry/replay tests. Текущий owner-bound drop намеренно не является P2P-торговлей.
- Личный сундук, дополнительные рюкзаки, weapon modules и раздельные armor slots.
- Трёхволновая оборона с server event state, читаемым предупреждением и сохранённым результатом.
- Более глубокие worker/recruit schedules, repair roles и инфраструктура без offline resource farming.
- UI для подробного boss/pity journal только после runtime-проверки текущего минимального представления.

## Исследование и сюжет

- Следующие физически доступные районы с настоящими задачами, не facade/teleport menu.
- Документы, секреты и дальнейшие главы после связки Меридиан → Новый Рубеж → Prism Cave.
- Проверенные в Studio фазы/телеграфируемые атаки для пяти существующих boss lifecycle; только затем новые boss classes.
- Репутационные последствия и выборы, сохраняемые сервером.
- Более разнообразные authored interiors только после performance pass текущих 7 232 BasePart.

## Транспорт и город

- Один проверенный свободно управляемый автомобиль с server validation, топливом, ремонтом и гаражом. Текущие автомобили остаются декорациями.
- Вертолёт остаётся фиксированным безопасным маршрутом; свободное пилотирование требует отдельной physics/network architecture.
- Более глубокие production chains, аккумуляторная сеть и восстановление квартала с реальными условиями.
- Полноценный LOD/streaming pass только по данным MicroProfiler и memory с целевого устройства.

## Audio/localization

- Русская синтетическая речь возможна только после появления документированного Roblox voice model или поставки разрешённых authored recordings. Сейчас русский текст сопровождается English engine TTS.
- Улучшение партитуры допустимо только bundled/owned/licensed assets с явным происхождением и реальным playback QA.
- Subtitles/accessibility timing, speech interruption и multiplayer audio mix требуют отдельного наблюдаемого теста.

## Позже

Остальные локации, транспорт, PvP только по согласию, радиосети, фракции, караваны, сезоны и финальная кампания. Пункт переносится в «готово» только после реализации, автоматических regressions и фактического Roblox Studio QA.
