-- MiniNations3D Config v1.0.1
-- Центральный конфиг игры
local Config = {}

Config.VERSION = "1.0.1 Ultimate"
Config.GAME_NAME = "MINI NATIONS 3D"
Config.SUBTITLE = "WORLD DOMINATION"

Config.YEARS = {1444,1700,1800,1914,1936,1945,1980,1991,2000,2010,2020,2026,2030,2050}
Config.MAPS = {"World","Europe","Asia","America","Africa","Custom"}
Config.DIFFICULTIES = {"Easy","Normal","Hard","Expert"}
Config.AI_BEHAVIORS = {"Peaceful","Economic","Aggressive","Diplomatic","Defensive","Expansionist"}

Config.RESOURCES = {"Oil","Gas","Coal","Iron","Copper","Gold","Uranium","Lithium","Wood","Stone","Food","Water"}
Config.BUILDINGS = {
    Residential = {"House","Apartment","Skyscraper","Complex"},
    Commercial = {"Shop","Mall","Office","Bank","Hotel"},
    Government = {"CityHall","Parliament","Ministry","Police","FireStation"},
    Social = {"School","University","Hospital","Park","Stadium"},
    Energy = {"CoalPlant","GasPlant","OilPlant","Hydro","Nuclear","Solar","Wind","Geothermal"},
    Industry = {"MilitaryFactory","AutoFactory","Metallurgy","Chemical","Electronics","OilRefinery","FoodFactory","Shipyard","AircraftFactory"},
    Infra = {"Road","Highway","Railway","Bridge","Tunnel","Port","Airport","Logistics"},
    Military = {"Barracks","Airbase","NavalBase","MissileBase","Radar","Depot","Fort","Academy"},
}
Config.UNITS = {"Infantry","Motorized","Tank","Artillery","AA","Aviation","Helicopter","Navy","Recon"}

Config.ECONOMY = {
    BaseIncome = 1000,
    TaxRate = 0.22,
    InflationTick = 0.001,
    CrisisChance = 0.02,
}

Config.MAP = {
    ProvinceSize = 48,
    WorldSize = Vector2.new(2400, 1400), -- условные единицы карты
    SeaLevel = 0,
}

Config.COINS_REWARDS = {
    Tutorial = 500,
    WinMatch = 250,
    DailyQuest = 50,
    WeeklyQuest = 300,
    LevelUp = 100,
}

Config.REMOTE_RATE_LIMIT = 8 -- событий в секунду
return Config
