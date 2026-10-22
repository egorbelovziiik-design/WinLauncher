--!nonstrict
-- AchievementDefinitions: 16 достижений.

local AchievementDefinitions = {}

AchievementDefinitions.List = {
	{ id = "first_dream", name = "Первый сон", description = "Проживите первый сон.", icon = "🌙", reward = 50 },
	{ id = "first_win", name = "Пробуждение", description = "Впервые выберитесь из сна.", icon = "☀️", reward = 100 },
	{ id = "dreams_10", name = "Сновидец", description = "Проживите 10 снов.", icon = "💤", reward = 150 },
	{ id = "dreams_25", name = "Хронический сон", description = "Проживите 25 снов.", icon = "🛌", reward = 300 },
	{ id = "wins_5", name = "Не боится тьмы", description = "Победите в 5 снах.", icon = "🛡", reward = 250 },
	{ id = "wins_20", name = "Повелитель снов", description = "Победите в 20 снах.", icon = "👑", reward = 600 },
	{ id = "quests_10", name = "Исполнитель", description = "Выполните 10 заданий.", icon = "📜", reward = 150 },
	{ id = "quests_30", name = "Герой снов", description = "Выполните 30 заданий.", icon = "🏅", reward = 400 },
	{ id = "coins_1000", name = "Богач", description = "Заработайте 1000 монет.", icon = "🪙", reward = 100 },
	{ id = "coins_5000", name = "Сокровищница", description = "Заработайте 5000 монет.", icon = "💰", reward = 300 },
	{ id = "shop_5", name = "Модник", description = "Купите 5 предметов.", icon = "🛍", reward = 200 },
	{ id = "all_dreams", name = "Коллекционер снов", description = "Побывайте во всех 10 снах.", icon = "🗺", reward = 500 },
	{ id = "extreme_win", name = "Экстрим пройден", description = "Победите на сложности «Экстрим».", icon = "🔥", reward = 700 },
	{ id = "speedrun", name = "Скоростной сон", description = "Победите быстрее, чем за 5 минут.", icon = "⏱", reward = 350 },
	{ id = "survivor", name = "Несломленный", description = "Выберитесь из сна с 1 здоровьем.", icon = "❤️", reward = 300 },
	{ id = "death", name = "Кошмар победил", description = "Проиграйте сон впервые.", icon = "💀", reward = 25 },
}

AchievementDefinitions.ById = {}
for _, entry in ipairs(AchievementDefinitions.List) do
	AchievementDefinitions.ById[entry.id] = entry
end

function AchievementDefinitions.get(id)
	return AchievementDefinitions.ById[id]
end

return AchievementDefinitions
