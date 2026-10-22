--!nonstrict
-- QuestDefinitions: 38 заданий (10 обязательных + 28 необязательных).
-- kind: collect | interact | talk | reach | survive | lights

local QuestDefinitions = {}

local raw = {
	-- Тёмный лес
	{ id = "forest_m", dream = "forest", kind = "collect", need = 4, title = "Осколки сна", desc = "Соберите 4 светящихся осколка на тропе.", reward = 60, label = "Осколок сна" },
	{ id = "forest_o1", dream = "forest", kind = "interact", need = 1, title = "Разжечь костёр", desc = "Найдите костёр у тропы и разожгите его.", reward = 40, label = "Костёр" },
	{ id = "forest_o2", dream = "forest", kind = "talk", need = 1, title = "Голос леса", desc = "Поговорите со Старым лесником.", reward = 35 },
	{ id = "forest_o3", dream = "forest", kind = "reach", need = 1, title = "Тайная поляна", desc = "Дойдите до поляны на севере леса.", reward = 45 },

	-- Школа
	{ id = "school_m", dream = "school", kind = "lights", need = 3, title = "Аварийный свет", desc = "Включите три аварийные лампы в школе.", reward = 60, label = "Аварийная лампа" },
	{ id = "school_o1", dream = "school", kind = "collect", need = 3, title = "Дневники учеников", desc = "Найдите 3 дневника в классах.", reward = 45, label = "Дневник" },
	{ id = "school_o2", dream = "school", kind = "interact", need = 2, title = "Чужие шкафчики", desc = "Проверьте два шкафчика в коридоре.", reward = 40, label = "Шкафчик" },
	{ id = "school_o3", dream = "school", kind = "lights", need = 3, title = "Дежурный свет", desc = "Включите три лампы в спортзале и коридоре.", reward = 50, label = "Лампа" },

	-- Город
	{ id = "city_m", dream = "city", kind = "interact", need = 3, title = "Питание города", desc = "Запустите три городских щитка.", reward = 70, label = "Щиток" },
	{ id = "city_o1", dream = "city", kind = "collect", need = 5, title = "Батареи", desc = "Соберите 5 батарей на улицах.", reward = 45, label = "Батарея" },
	{ id = "city_o2", dream = "city", kind = "reach", need = 1, title = "Седьмой этаж", desc = "Поднимитесь на крышу дальнего здания.", reward = 50 },
	{ id = "city_o3", dream = "city", kind = "survive", need = 90, title = "Патруль", desc = "Продержитесь 90 секунд, не попадаясь на глаза.", reward = 55 },

	-- Больница
	{ id = "hospital_m", dream = "hospital", kind = "collect", need = 4, title = "Медицинские записи", desc = "Найдите 4 истории болезни.", reward = 65, label = "Запись" },
	{ id = "hospital_o1", dream = "hospital", kind = "interact", need = 1, title = "Резервный генератор", desc = "Запустите генератор в подвале.", reward = 40, label = "Генератор" },
	{ id = "hospital_o2", dream = "hospital", kind = "talk", need = 1, title = "Разговор с медсестрой", desc = "Узнайте у медсестры, где выход.", reward = 35 },
	{ id = "hospital_o3", dream = "hospital", kind = "reach", need = 1, title = "Палата 13", desc = "Найдите палату 13 в западном крыле.", reward = 50 },

	-- Цирк
	{ id = "circus_m", dream = "circus", kind = "interact", need = 1, title = "Клетка", desc = "Откройте клетку с зверем.", reward = 65, label = "Клетка" },
	{ id = "circus_o1", dream = "circus", kind = "collect", need = 3, title = "Билеты", desc = "Соберите 3 билета на представление.", reward = 45, label = "Билет" },
	{ id = "circus_o2", dream = "circus", kind = "reach", need = 1, title = "Колесо обозрения", desc = "Заберитесь на колесо обозрения.", reward = 50 },
	{ id = "circus_o3", dream = "circus", kind = "lights", need = 3, title = "Гирлянды", desc = "Зажгите три гирлянды вокруг арены.", reward = 50, label = "Гирлянда" },

	-- Затопленный город
	{ id = "flood_m", dream = "flood", kind = "interact", need = 1, title = "Маяк", desc = "Зажгите маяк на краю города.", reward = 70, label = "Маяк" },
	{ id = "flood_o1", dream = "flood", kind = "collect", need = 3, title = "Сигнальные ракеты", desc = "Найдите 3 сигнальные ракеты.", reward = 45, label = "Ракета" },
	{ id = "flood_o2", dream = "flood", kind = "interact", need = 1, title = "Затопленный автобус", desc = "Осмотрите автобус в центре воды.", reward = 40, label = "Автобус" },
	{ id = "flood_o3", dream = "flood", kind = "reach", need = 1, title = "Плотина", desc = "Дойдите до старой плотины.", reward = 50 },

	-- Ледяной мир
	{ id = "ice_m", dream = "ice", kind = "interact", need = 1, title = "Огонь среди льда", desc = "Разожгите лагерный костёр.", reward = 65, label = "Костёр" },
	{ id = "ice_o1", dream = "ice", kind = "collect", need = 4, title = "Ледяные кристаллы", desc = "Соберите 4 кристалла.", reward = 45, label = "Кристалл" },
	{ id = "ice_o2", dream = "ice", kind = "talk", need = 1, title = "Замерзший путник", desc = "Поговорите с путником у костра.", reward = 35 },
	{ id = "ice_o3", dream = "ice", kind = "reach", need = 1, title = "Ледяной дворец", desc = "Дойдите до ворот дворца.", reward = 50 },

	-- Фабрика
	{ id = "factory_m", dream = "factory", kind = "interact", need = 3, title = "Остановить конвейер", desc = "Отключите три станка.", reward = 70, label = "Станок" },
	{ id = "factory_o1", dream = "factory", kind = "collect", need = 4, title = "Шестерни", desc = "Соберите 4 шестерни в цеху.", reward = 45, label = "Шестерня" },
	{ id = "factory_o2", dream = "factory", kind = "interact", need = 1, title = "Пульт управления", desc = "Включите главный пульт.", reward = 40, label = "Пульт" },
	{ id = "factory_o3", dream = "factory", kind = "survive", need = 60, title = "Обход", desc = "Переждите обход мастера — 60 секунд.", reward = 45 },

	-- Космическая станция
	{ id = "space_m", dream = "space", kind = "interact", need = 3, title = "Кислород", desc = "Восстановите три модуля подачи кислорода.", reward = 75, label = "Модуль кислорода" },
	{ id = "space_o1", dream = "space", kind = "collect", need = 4, title = "Модули данных", desc = "Соберите 4 модуля данных.", reward = 50, label = "Модуль данных" },
	{ id = "space_o2", dream = "space", kind = "talk", need = 1, title = "ИИ «Орфей»", desc = "Поговорите с ИИ станции.", reward = 40 },
	{ id = "space_o3", dream = "space", kind = "reach", need = 1, title = "Шлюз 4", desc = "Дойдите до четвёртого шлюза.", reward = 55 },

	-- Замок
	{ id = "castle_m", dream = "castle", kind = "interact", need = 2, title = "Снять проклятие", desc = "Активируйте печать и трон.", reward = 75, label = "Печать" },
	{ id = "castle_o1", dream = "castle", kind = "collect", need = 3, title = "Страницы гримуара", desc = "Соберите 3 страницы гримуара.", reward = 50, label = "Страница" },
	{ id = "castle_o2", dream = "castle", kind = "lights", need = 3, title = "Факелы", desc = "Зажгите три факела во дворе.", reward = 45, label = "Факел" },
	{ id = "castle_o3", dream = "castle", kind = "reach", need = 1, title = "Склеп", desc = "Найдите вход в склеп.", reward = 55 },
}

QuestDefinitions.List = {}
QuestDefinitions.ById = {}
QuestDefinitions.ByDream = {}

for _, entry in ipairs(raw) do
	local quest = {
		id = entry.id,
		dream = entry.dream,
		kind = entry.kind,
		need = entry.need or 1,
		title = entry.title,
		desc = entry.desc,
		reward = entry.reward or 40,
		label = entry.label,
	}
	table.insert(QuestDefinitions.List, quest)
	QuestDefinitions.ById[quest.id] = quest
	QuestDefinitions.ByDream[quest.dream] = QuestDefinitions.ByDream[quest.dream] or {}
	table.insert(QuestDefinitions.ByDream[quest.dream], quest)
end

function QuestDefinitions.get(id)
	return QuestDefinitions.ById[id]
end

-- Возвращает обязательный и необязательные квесты сна.
function QuestDefinitions.forDream(dreamId, dreamDef)
	local mandatory = nil
	local optional = {}
	for _, quest in ipairs(QuestDefinitions.ByDream[dreamId] or {}) do
		if dreamDef and quest.id == dreamDef.mandatory then
			mandatory = quest
		elseif quest.id ~= (dreamDef and dreamDef.mandatory) then
			table.insert(optional, quest)
		end
	end
	return mandatory, optional
end

function QuestDefinitions.kindLabel(kind)
	local labels = {
		collect = "Собрать",
		interact = "Активировать",
		talk = "Поговорить",
		reach = "Добраться",
		survive = "Продержаться",
		lights = "Включить",
	}
	return labels[kind] or "Задание"
end

return QuestDefinitions
