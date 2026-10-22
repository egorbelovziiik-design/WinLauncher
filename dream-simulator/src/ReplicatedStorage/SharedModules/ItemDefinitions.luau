--!nonstrict
-- ItemDefinitions: магазин (10 предметов 150–1200) + расходники.

local ItemDefinitions = {}

ItemDefinitions.List = {
	{
		id = "flashlight",
		name = "Фонарик",
		description = "Освещает путь и отпугивает тени. F — включить/выключить.",
		price = 150,
		icon = "🔦",
		color = Color3.fromRGB(230, 235, 180),
		rarity = "Обычный",
		stackable = false,
		category = "Инструмент",
		slot = "flashlight",
		lightRange = 30,
		lightColor = Color3.fromRGB(255, 245, 205),
		bonus = { sanityMul = 0.98 },
	},
	{
		id = "lantern",
		name = "Лампа сновидца",
		description = "Тёплый свет шире и дальше фонарика.",
		price = 300,
		icon = "🏮",
		color = Color3.fromRGB(255, 180, 110),
		rarity = "Редкий",
		stackable = false,
		category = "Инструмент",
		slot = "flashlight",
		lightRange = 42,
		lightColor = Color3.fromRGB(255, 210, 150),
		bonus = { sanityMul = 0.95 },
	},
	{
		id = "amulet",
		name = "Амулет сна",
		description = "Бирюзовый камень: рассудок тратится на 20% медленнее.",
		price = 450,
		icon = "🔮",
		color = Color3.fromRGB(110, 230, 205),
		rarity = "Редкий",
		stackable = false,
		category = "Амулет",
		slot = "amulet",
		bonus = { sanityMul = 0.8 },
	},
	{
		id = "trail",
		name = "Шлейф звёзд",
		description = "За спиной остаётся звёздная пыль. Чистая эстетика.",
		price = 600,
		icon = "✨",
		color = Color3.fromRGB(170, 180, 255),
		rarity = "Редкий",
		stackable = false,
		category = "Эффект",
		slot = "trail",
		bonus = {},
	},
	{
		id = "mask",
		name = "Маска кошмара",
		description = "Фарфоровая маска с трещинами. Прыжки-скримеры пугают меньше.",
		price = 750,
		icon = "🎭",
		color = Color3.fromRGB(240, 232, 240),
		rarity = "Эпический",
		stackable = false,
		category = "Аксессуар",
		slot = "mask",
		bonus = { scareMul = 0.8 },
	},
	{
		id = "chronometer",
		name = "Хронометр",
		description = "Добавляет 60 секунд ко каждому сну.",
		price = 900,
		icon = "⏳",
		color = Color3.fromRGB(255, 205, 120),
		rarity = "Эпический",
		stackable = false,
		category = "Амулет",
		slot = "amulet",
		bonus = { timeBonus = 60 },
	},
	{
		id = "cloak",
		name = "Плащ пустоты",
		description = "Ткань без единой звезды. Пустота смотрит в ответ.",
		price = 1000,
		icon = "🕳",
		color = Color3.fromRGB(60, 45, 90),
		rarity = "Эпический",
		stackable = false,
		category = "Одежда",
		slot = "cloak",
		bonus = { sanityMul = 0.88 },
	},
	{
		id = "eye",
		name = "Око тьмы",
		description = "Видит даже сквозь стены сна. Свет дальше и ярче.",
		price = 1100,
		icon = "👁",
		color = Color3.fromRGB(255, 130, 150),
		rarity = "Легендарный",
		stackable = false,
		category = "Инструмент",
		slot = "flashlight",
		lightRange = 56,
		lightColor = Color3.fromRGB(255, 150, 170),
		bonus = { sanityMul = 0.9 },
	},
	{
		id = "crown",
		name = "Корона кошмаров",
		description = "Корона из чёрного стекла. Кошмары признают своего.",
		price = 1200,
		icon = "👑",
		color = Color3.fromRGB(150, 100, 230),
		rarity = "Легендарный",
		stackable = false,
		category = "Аксессуар",
		slot = "mask",
		bonus = { scareMul = 0.65, sanityMul = 0.85 },
	},
	{
		id = "wings",
		name = "Крылья ночи",
		description = "Крылья из тьмы за спиной. Немного ускоряют бег.",
		price = 1200,
		icon = "🦇",
		color = Color3.fromRGB(95, 70, 150),
		rarity = "Легендарный",
		stackable = false,
		category = "Эффект",
		slot = "wings",
		bonus = { speedBonus = 2 },
	},

	-- Расходники
	{
		id = "sanity_potion",
		name = "Эликсир разума",
		description = "Восстанавливает 45 рассудка. Q — использовать во сне.",
		price = 80,
		icon = "🧪",
		color = Color3.fromRGB(150, 240, 190),
		rarity = "Расходник",
		stackable = true,
		category = "Расходник",
		consumable = { sanity = 45 },
	},
	{
		id = "medkit",
		name = "Аптечка",
		description = "Восстанавливает 45 здоровья.",
		price = 120,
		icon = "🩹",
		color = Color3.fromRGB(255, 150, 150),
		rarity = "Расходник",
		stackable = true,
		category = "Расходник",
		consumable = { health = 45 },
	},
	{
		id = "battery",
		name = "Батарейка",
		description = "Возвращает 60 выносливости и включает фонарик.",
		price = 60,
		icon = "🔋",
		color = Color3.fromRGB(255, 225, 130),
		rarity = "Расходник",
		stackable = true,
		category = "Расходник",
		consumable = { stamina = 60, flashlight = true },
	},
	{
		id = "speed_potion",
		name = "Эликсир скорости",
		description = "На 20 секунд ускоряет бег.",
		price = 100,
		icon = "⚡",
		color = Color3.fromRGB(255, 175, 100),
		rarity = "Расходник",
		stackable = true,
		category = "Расходник",
		consumable = { speed = 20 },
	},
}

ItemDefinitions.ById = {}
ItemDefinitions.Order = {}

for _, item in ipairs(ItemDefinitions.List) do
	ItemDefinitions.ById[item.id] = item
	table.insert(ItemDefinitions.Order, item.id)
end

function ItemDefinitions.get(id)
	return ItemDefinitions.ById[id]
end

function ItemDefinitions.price(id)
	local item = ItemDefinitions.ById[id]
	return item and item.price or math.huge
end

return ItemDefinitions
