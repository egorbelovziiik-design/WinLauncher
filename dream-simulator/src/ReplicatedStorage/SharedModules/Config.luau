--!nonstrict
-- Config: единые настройки игры (сервер и клиент).

local Config = {}

Config.GAME_NAME = "🌙 DREAM SIMULATOR: NIGHTMARE WORLDS"
Config.VERSION = "1.0.0"

-- Основные тайминги (клиент тоже использует эти имена)
Config.DreamDuration = 600
Config.SleepDuration = 9.5
Config.WakeDuration = 6.0
Config.SaveInterval = 120
Config.ProfileVersion = 3

-- Персонаж
Config.BaseWalkSpeed = 16
Config.SprintSpeed = 26
Config.JumpPower = 50
Config.MaxHealth = 100
Config.MaxSanity = 100
Config.MaxStamina = 100
Config.InteractRange = 13
Config.SprintDrain = 16
Config.StaminaRegen = 11

-- Экономика / сохранение
Config.DataStoreName = "DreamSimulator_v1"
Config.DataStoreKey = "player_"
Config.SavedCoins = 0

-- Сложности (ключи используются в именах карточек: EasyCard, NormalCard, ...)
Config.Difficulties = {
	Easy = {
		key = "Easy",
		icon = "🌿",
		name = "Лёгкий",
		description = "Спокойный сон: монстры медлительны, разум крепок.",
		reward = 100,
		time = 600,
		monsterCount = 2,
		monsterSpeed = 0.78,
		monsterDamage = 0.6,
		sanityDrain = 0.6,
		eventInterval = 46,
		color = Color3.fromRGB(130, 225, 165),
	},
	Normal = {
		key = "Normal",
		icon = "🌙",
		name = "Обычный",
		description = "Стандартный кошмар. Держись подальше от тени.",
		reward = 250,
		time = 600,
		monsterCount = 3,
		monsterSpeed = 1.0,
		monsterDamage = 1.0,
		sanityDrain = 1.0,
		eventInterval = 32,
		color = Color3.fromRGB(150, 165, 255),
	},
	Hard = {
		key = "Hard",
		icon = "🔥",
		name = "Сложный",
		description = "Сон не отпускает: монстры быстрее, разум слабеет.",
		reward = 500,
		time = 600,
		monsterCount = 4,
		monsterSpeed = 1.18,
		monsterDamage = 1.35,
		sanityDrain = 1.35,
		eventInterval = 24,
		color = Color3.fromRGB(255, 175, 95),
	},
	Extreme = {
		key = "Extreme",
		icon = "💀",
		name = "Экстрим",
		description = "Кошмар без пощады. Один удар — и ты не проснёшься.",
		reward = 1000,
		time = 600,
		monsterCount = 5,
		monsterSpeed = 1.34,
		monsterDamage = 1.9,
		sanityDrain = 1.7,
		eventInterval = 17,
		color = Color3.fromRGB(255, 85, 105),
	},
}

Config.DifficultyOrder = { "Easy", "Normal", "Hard", "Extreme" }

Config.Settings = {
	musicVolume = 0.55,
	sfxVolume = 0.8,
	quality = "Высокое",
	sensitivity = 0.5,
	scareIntensity = 1,
	hints = true,
	cameraShake = true,
}

Config.QualityLevels = { "Низкое", "Средний", "Высокое" }

Config.Sanity = {
	DrainDark = 0.9,
	DrainMonster = 4.2,
	RegenLight = 0.55,
	Low = 55,
	Critical = 25,
}

Config.Sounds = {
	MenuMusic = "rbxassetid://1847861148",
	DreamAmbient = "rbxassetid://9046893493",
	Heartbeat = "rbxassetid://1837761438",
	Whisper = "rbxassetid://5665737137",
	Scare = "rbxassetid://5153734608",
	BigScare = "rbxassetid://5801257793",
	MonsterGrowl = "rbxassetid://5801257793",
	Click = "rbxassetid://6073536806",
	Purchase = "rbxassetid://6023527123",
	Win = "rbxassetid://5654042073",
	Lose = "rbxassetid://5153642694",
	DoorCreak = "rbxassetid://5674194248",
	Pickup = "rbxassetid://5674227256",
	Portal = "rbxassetid://1837736541",
}

Config.Colors = {
	bg = Color3.fromRGB(10, 10, 18),
	panel = Color3.fromRGB(20, 18, 34),
	accent = Color3.fromRGB(150, 120, 255),
	accent2 = Color3.fromRGB(90, 200, 230),
	good = Color3.fromRGB(120, 230, 160),
	bad = Color3.fromRGB(255, 95, 115),
	warn = Color3.fromRGB(255, 195, 95),
	text = Color3.fromRGB(238, 236, 250),
	dim = Color3.fromRGB(150, 148, 172),
	gold = Color3.fromRGB(255, 205, 100),
}

function Config.getDifficulty(key)
	return Config.Difficulties[key] or Config.Difficulties.Normal
end

function Config.validDifficulty(key)
	return type(key) == "string" and Config.Difficulties[key] ~= nil
end

function Config.defaultSettings()
	local copy = {}
	for key, value in pairs(Config.Settings) do
		copy[key] = value
	end
	return copy
end

return Config
