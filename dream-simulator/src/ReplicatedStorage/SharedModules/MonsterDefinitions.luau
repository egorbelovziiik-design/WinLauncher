--!nonstrict
-- MonsterDefinitions: 14 типов монстров (5 базовых архетипов + тематические).

local MonsterDefinitions = {}

MonsterDefinitions.Archetypes = {
	shadow = {
		name = "Тень",
		behavior = "stalker",
		speed = 12,
		detectRange = 70,
		attackRange = 6,
		damage = 22,
		sanityDrain = 8,
		height = 7.4,
		bodyColor = Color3.fromRGB(14, 12, 22),
		eyeColor = Color3.fromRGB(255, 60, 70),
		transparency = 0.25,
		scareSound = "scare",
		desc = "Скользит по стенам и не отбрасывает тени.",
	},
	runner = {
		name = "Бегун",
		behavior = "chaser",
		speed = 19,
		detectRange = 95,
		attackRange = 5,
		damage = 16,
		sanityDrain = 6,
		height = 6.2,
		bodyColor = Color3.fromRGB(120, 26, 30),
		eyeColor = Color3.fromRGB(255, 210, 120),
		transparency = 0,
		scareSound = "scare",
		desc = "Слышит каждый шаг. Никогда не устаёт.",
	},
	watcher = {
		name = "Наблюдатель",
		behavior = "watcher",
		speed = 6,
		detectRange = 120,
		attackRange = 40,
		damage = 9,
		sanityDrain = 12,
		height = 8.6,
		bodyColor = Color3.fromRGB(200, 200, 210),
		eyeColor = Color3.fromRGB(255, 30, 30),
		transparency = 0.45,
		scareSound = "scare_big",
		desc = "Стоит неподвижно и смотрит. Смотреть в ответ нельзя.",
	},
	trickster = {
		name = "Обманщик",
		behavior = "mimic",
		speed = 14,
		detectRange = 80,
		attackRange = 6,
		damage = 20,
		sanityDrain = 10,
		height = 6.8,
		bodyColor = Color3.fromRGB(70, 120, 110),
		eyeColor = Color3.fromRGB(160, 255, 220),
		transparency = 0,
		scareSound = "scare",
		desc = "Притворяется другом. Голос звучит знакомо.",
	},
	nightmare = {
		name = "Кошмар",
		behavior = "hunter",
		speed = 16,
		detectRange = 110,
		attackRange = 8,
		damage = 34,
		sanityDrain = 16,
		height = 11,
		bodyColor = Color3.fromRGB(30, 8, 40),
		eyeColor = Color3.fromRGB(255, 120, 0),
		transparency = 0.1,
		scareSound = "scare_big",
		desc = "Хозяин сна. Появляется, когда разум на исходе.",
		boss = true,
	},
}

-- Алиасы архетипов: поведение "chaser"/"hunter" описывается отдельными наборами.
MonsterDefinitions.Archetypes.chaser = {
	name = "Преследователь",
	behavior = "chaser",
	speed = 15,
	detectRange = 90,
	attackRange = 6,
	damage = 20,
	sanityDrain = 7,
	height = 7.2,
	bodyColor = Color3.fromRGB(90, 40, 40),
	eyeColor = Color3.fromRGB(255, 180, 80),
	transparency = 0,
	scareSound = "scare",
	desc = "Идёт по следу, пока не найдёт.",
}

MonsterDefinitions.Archetypes.hunter = {
	name = "Охотник",
	behavior = "hunter",
	speed = 15,
	detectRange = 105,
	attackRange = 7,
	damage = 28,
	sanityDrain = 12,
	height = 9.2,
	bodyColor = Color3.fromRGB(40, 20, 40),
	eyeColor = Color3.fromRGB(255, 110, 30),
	transparency = 0.05,
	scareSound = "scare_big",
	desc = "Не отступает. Умеет ждать.",
}

MonsterDefinitions.Types = {
	{
		id = "shadow",
		archetype = "shadow",
		name = "Тень",
		dreams = { "forest", "school", "hospital", "factory" },
	},
	{
		id = "runner",
		archetype = "runner",
		name = "Бегун",
		dreams = { "city", "flood", "school", "factory" },
	},
	{
		id = "watcher",
		archetype = "watcher",
		name = "Наблюдатель",
		dreams = { "forest", "ice", "castle", "space" },
	},
	{
		id = "trickster",
		archetype = "trickster",
		name = "Обманщик",
		dreams = { "circus", "city" },
	},
	{
		id = "nightmare",
		archetype = "nightmare",
		name = "Кошмар",
		dreams = { "castle", "circus", "space" },
	},
	{
		id = "pierrot",
		archetype = "trickster",
		name = "Пьеро",
		dreams = { "circus" },
		color = Color3.fromRGB(235, 235, 245),
		eyeColor = Color3.fromRGB(255, 40, 80),
		speed = 15,
	},
	{
		id = "nurse",
		archetype = "stalker",
		name = "Медсестра",
		dreams = { "hospital" },
		color = Color3.fromRGB(225, 230, 230),
		eyeColor = Color3.fromRGB(210, 30, 40),
		speed = 13,
	},
	{
		id = "doll",
		archetype = "watcher",
		name = "Кукла",
		dreams = { "circus", "school" },
		color = Color3.fromRGB(220, 200, 190),
		eyeColor = Color3.fromRGB(30, 30, 30),
		speed = 5,
		scale = 0.75,
	},
	{
		id = "drowned",
		archetype = "chaser",
		name = "Утопленник",
		dreams = { "flood" },
		color = Color3.fromRGB(60, 100, 100),
		eyeColor = Color3.fromRGB(200, 255, 240),
		speed = 13,
	},
	{
		id = "frost",
		archetype = "watcher",
		name = "Мороз",
		dreams = { "ice" },
		color = Color3.fromRGB(190, 225, 245),
		eyeColor = Color3.fromRGB(90, 200, 255),
		speed = 8,
	},
	{
		id = "machine",
		archetype = "chaser",
		name = "Механизм",
		dreams = { "factory", "space" },
		color = Color3.fromRGB(120, 110, 90),
		eyeColor = Color3.fromRGB(255, 170, 40),
		speed = 11,
		scale = 1.15,
	},
	{
		id = "alien",
		archetype = "hunter",
		name = "Чужой",
		dreams = { "space" },
		color = Color3.fromRGB(60, 80, 70),
		eyeColor = Color3.fromRGB(120, 255, 180),
		speed = 15,
	},
	{
		id = "knight",
		archetype = "hunter",
		name = "Тёмный рыцарь",
		dreams = { "castle" },
		color = Color3.fromRGB(70, 68, 80),
		eyeColor = Color3.fromRGB(255, 90, 40),
		speed = 12,
		scale = 1.1,
	},
	{
		id = "librarian",
		archetype = "stalker",
		name = "Библиотекарь",
		dreams = { "school", "castle" },
		color = Color3.fromRGB(80, 70, 110),
		eyeColor = Color3.fromRGB(230, 220, 160),
		speed = 9,
	},
}

MonsterDefinitions.ByArchetype = {}
for _, monster in ipairs(MonsterDefinitions.Types) do
	local base = MonsterDefinitions.Archetypes[monster.archetype] or MonsterDefinitions.Archetypes.shadow
	local merged = {}
	for key, value in pairs(base) do
		merged[key] = value
	end
	for key, value in pairs(monster) do
		merged[key] = value
	end
	merged.speed = monster.speed or base.speed
	merged.color = monster.color or base.bodyColor
	merged.eyeColor = monster.eyeColor or base.eyeColor
	merged.scale = monster.scale or 1
	MonsterDefinitions.ByArchetype[monster.id] = merged
end

function MonsterDefinitions.get(id)
	return MonsterDefinitions.ByArchetype[id] or MonsterDefinitions.ByArchetype.shadow
end

function MonsterDefinitions.forDream(dreamId)
	local out = {}
	for _, monster in ipairs(MonsterDefinitions.Types) do
		for _, dream in ipairs(monster.dreams) do
			if dream == dreamId then
				table.insert(out, monster.id)
				break
			end
		end
	end
	if #out == 0 then
		out = { "shadow", "runner" }
	end
	return out
end

return MonsterDefinitions
