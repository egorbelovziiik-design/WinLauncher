-- Main.server — инициализация всех сервисов и RemoteEvents
print("🌍 Mini Nations 3D — Server starting...")

-- Создать папку Remotes
local RS = game:GetService("ReplicatedStorage")
local remotesFolder = RS:FindFirstChild("Remotes") or Instance.new("Folder")
remotesFolder.Name="Remotes"; remotesFolder.Parent=RS

local function remote(name:string, isFunc:boolean?)
    if remotesFolder:FindFirstChild(name) then return remotesFolder:FindFirstChild(name) end
    local r = isFunc and Instance.new("RemoteFunction") or Instance.new("RemoteEvent")
    r.Name=name; r.Parent=remotesFolder; return r
end

-- Все ремоуты
local events = {
    "RequestProfile","CoinsUpdated","ProvinceOwnerChanged","BuildingPlaced","ArmyCreated","ArmyMoved",
    "WarDeclared","BattleResult","PeaceSigned","RelationChanged","AllianceCreated","TechUnlocked",
    "ShopPurchased","AchievementUnlocked","MapSaved","CityFounded","MatchStarted","CoinsUpdated",
    "BuyItem","BuildRequest","MoveArmy","DeclareWar","MakePeace","CreateAlliance","ResearchTech",
    "JoinMatch","CreateMatch","StartMatch","SaveMap","LoadMap","SetReady","ToggleTutorial",
    "GetEconomy","GetResources","GetProvinces"
}
for _,e in ipairs(events) do remote(e,false) end
remote("GetProfile", true)

-- Rate limit
local rl: {[number]: {number}} = {}
local LIMIT = 8
game:GetService("RunService").Heartbeat:Connect(function()
    -- очистка окна
end)

local function checkLimit(pl:Player):boolean
    local now = tick()
    rl[pl.UserId]=rl[pl.UserId] or {}
    table.insert(rl[pl.UserId], now)
    -- оставить только последнюю секунду
    local t={} for _,v in ipairs(rl[pl.UserId]) do if now - v < 1 then table.insert(t,v) end end
    rl[pl.UserId]=t
    return #t <= LIMIT
end

-- Загрузить сервисы
local CoinService = require(script.Services.CoinService)
local SaveService = require(script.Services.SaveService)
local EconomyService = require(script.Services.EconomyService)
local ResourceService = require(script.Services.ResourceService)
local DiplomacyService = require(script.Services.DiplomacyService)
local PowerService = require(script.Services.PowerService)
local BuildingService = require(script.Services.BuildingService)
local MilitaryService = require(script.Services.MilitaryService)
local WarService = require(script.Services.WarService)
local AllianceService = require(script.Services.AllianceService)
local TechnologyService = require(script.Services.TechnologyService)
local ShopService = require(script.Services.ShopService)
local QuestService = require(script.Services.QuestService)
local AchievementService = require(script.Services.AchievementService)
local MapEditorService = require(script.Services.MapEditorService)
local GameService = require(script.Services.GameService)

EconomyService.init()
ResourceService.init()
DiplomacyService.init()
PowerService.tick()
SaveService.startAutosave()

-- Подключения
game.Players.PlayerAdded:Connect(function(pl)
    SaveService.load(pl)
    CoinService.ensure(pl)
    -- отдать профиль
    task.wait(1)
    local prof = CoinService.getProfile(pl)
    local rem = remotesFolder:FindFirstChild("RequestProfile")
    if rem then rem:FireClient(pl, prof) end
end)

-- RemoteFunction GetProfile
remotesFolder.GetProfile.OnServerInvoke = function(pl:Player)
    return CoinService.ensure(pl)
end

-- Build
remotesFolder.BuildRequest.OnServerEvent:Connect(function(pl:Player, provinceId:string, building:string)
    if not checkLimit(pl) then return end
    local ok,msg = BuildingService.build(pl, provinceId, building)
    if ok then
        QuestService.add(pl, "build5", 1)
        AchievementService.unlock(pl, "first_city")
    end
end)

remotesFolder.BuyItem.OnServerEvent:Connect(function(pl:Player, itemId:string)
    if not checkLimit(pl) then return end
    ShopService.buy(pl, itemId)
end)

remotesFolder.MoveArmy.OnServerEvent:Connect(function(pl:Player, armyId:string, toProv:string)
    if not checkLimit(pl) then return end
    MilitaryService.move(armyId, toProv)
end)

remotesFolder.DeclareWar.OnServerEvent:Connect(function(pl:Player, from:string, to:string)
    if not checkLimit(pl) then return end
    -- валидация: from должен принадлежать игроку (упрощено)
    WarService.declare(from,to)
    AchievementService.unlock(pl, "first_war")
    QuestService.add(pl,"match1",1)
end)

remotesFolder.MakePeace.OnServerEvent:Connect(function(pl:Player, warId:string)
    WarService.peace(warId)
end)

remotesFolder.CreateAlliance.OnServerEvent:Connect(function(pl:Player, name:string, color:Color3, leader:string)
    AllianceService.create(name,color,leader)
    AchievementService.unlock(pl,"first_alliance")
end)

remotesFolder.ResearchTech.OnServerEvent:Connect(function(pl:Player, countryId:string, branch:string, tech:string)
    TechnologyService.research(countryId, branch, tech)
    QuestService.add(pl,"tech1",1)
end)

-- Economy polling
remotesFolder.GetEconomy.OnServerEvent:Connect(function(pl:Player, countryId:string)
    local eco = EconomyService.get(countryId)
    remotesFolder.GetEconomy:FireClient(pl, eco)
end)

remotesFolder.CreateMatch.OnServerEvent:Connect(function(pl:Player, cfg:any)
    local m = GameService.createMatch(pl, cfg)
    remotesFolder.CreateMatch:FireClient(pl, m)
end)
remotesFolder.JoinMatch.OnServerEvent:Connect(function(pl:Player, matchId:string)
    GameService.joinMatch(pl, matchId)
end)
remotesFolder.StartMatch.OnServerEvent:Connect(function(pl:Player, matchId:string)
    GameService.start(matchId)
end)
remotesFolder.SaveMap.OnServerEvent:Connect(function(pl:Player, name:string, data:any)
    MapEditorService.save(pl,name,data)
end)

print("✅ Mini Nations 3D Server ready — "..#events.." remotes")
