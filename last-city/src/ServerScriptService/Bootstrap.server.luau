--!nonstrict
local ctx
workspace:SetAttribute("LastCityServerPhase", "Modules")
workspace:SetAttribute("LastCityServerError", nil)
workspace:SetAttribute("LastCityReady", false)
local function boot()
    -- Composition root: domain modules have no Roblox side effects; services own adapters.
    local ReplicatedStorage = game:GetService("ReplicatedStorage")
    local Shared = assert(ReplicatedStorage:WaitForChild("Shared", 10), "Shared missing")
    local Domain = assert(script.Parent:WaitForChild("Domain", 10), "Domain missing")
    ctx = {
        Sessions = {}, Services = {}, Remotes = assert(ReplicatedStorage:WaitForChild("Remotes", 10), "Remotes missing"),
        World = assert(workspace:WaitForChild("World", 10), "World missing"), Config = require(Shared.GameConfig),
        Items = require(Shared.ItemDefinitions), Weapons = require(Shared.WeaponDefinitions),
        ZombieDefs = require(Shared.ZombieDefinitions), QuestDefs = require(Shared.QuestDefinitions),
        BuildingDefs = require(Shared.BuildingDefinitions), EconomyDefs = require(Shared.EconomyConfig), RecruitDefs = require(Shared.RecruitDefinitions),
        Factory = require(Shared.ModelFactory), Util = require(Domain.Util),
        RateLimiter = require(Domain.RateLimiter), Lease = require(Domain.Lease),
    }
    ctx.Inventory = require(Domain.Inventory)(ctx.Items, ctx.Weapons, ctx.Config, ctx.Util)
    ctx.Profile = require(Domain.Profile)(ctx.Config, ctx.Items, ctx.Weapons, ctx.BuildingDefs, ctx.QuestDefs, ctx.EconomyDefs, ctx.Util, ctx.Inventory)
    ctx.Quests = require(Domain.Quests)(ctx.QuestDefs, ctx.Inventory, ctx.Util)
    ctx.Building = require(Domain.Building)(ctx.BuildingDefs, ctx.Inventory, ctx.Config, ctx.Util)
    ctx.Economy = require(Domain.Economy)(ctx.BuildingDefs, ctx.EconomyDefs, ctx.Inventory, ctx.Util)
    ctx.Progression = require(Domain.Progression)(ctx.Config, ctx.EconomyDefs, ctx.Inventory)
    function ctx:Root(player)
        local c = player.Character; return c and c:FindFirstChild("HumanoidRootPart")
    end
    function ctx:Alive(player)
        local c = player.Character; local h = c and c:FindFirstChildOfClass("Humanoid")
        return h and h.Health > 0 and h or nil
    end
    function ctx:PositionValid(player)
        local s = self.Sessions[player]; local root = self:Root(player)
        if not s or not root then return false end
        if not self.Util.finite(root.Position.X) or not self.Util.finite(root.Position.Y) or not self.Util.finite(root.Position.Z) then return false end
        if not s.SafePosition or os.clock() < (s.SpawnGrace or 0) then return true end
        local elapsed = math.clamp(os.clock() - (s.PositionAt or os.clock()), 0, 1)
        local delta = root.Position - s.SafePosition
        return Vector3.new(delta.X, 0, delta.Z).Magnitude <= math.max(12, elapsed * 32 + 8)
    end
    function ctx:InShelter(player)
        local root = self:Root(player)
        return root and math.abs(root.Position.X) < 74 and root.Position.Z < -30 and root.Position.Z > -164
    end
    function ctx:InPlot(player, margin)
        local s = self.Sessions[player]; local root = self:Root(player)
        if not s or not root or not s.Plot then return false end
        local p = self.Config.Plots[s.Plot]; local half = self.Config.PlotHalfSize + (margin or 0)
        return math.abs(root.Position.X - p.x) <= half and math.abs(root.Position.Z - p.z) <= half and math.abs(root.Position.Y) < 30
    end
    function ctx:LineOfSight(origin, destination, ignore)
        local delta = destination - origin; if delta.Magnitude < 0.01 then return true end
        local params = RaycastParams.new(); params.FilterType = Enum.RaycastFilterType.Exclude; params.FilterDescendantsInstances = ignore
        params.IgnoreWater = true
        return workspace:Raycast(origin, delta, params) == nil
    end
    function ctx:Notify(player, text, kind)
        if player.Parent then self.Remotes.StateRemote:FireClient(player, "notice", {text = text or "Действие отклонено", kind = kind or "info"}) end
    end
    function ctx:Changed(player)
        local s = self.Sessions[player]; if not s or not s.Loaded then return end
        s.Data.Revision += 1
        for _, name in self.Progression.achievements(s.Data) do self:Notify(player, "Достижение: " .. name, "success") end
        if s.SnapshotPending then return end
        s.SnapshotPending = true
        task.delay(0.12, function()
            if self.Sessions[player] == s then s.SnapshotPending = false; self.Services.PlayerDataService:Snapshot(player) end
        end)
    end
    function ctx:FX(kind, data)
        for player, s in self.Sessions do
            local root = self:Root(player)
            if s.Active and root and (root.Position - data.from).Magnitude < 280 then self.Remotes.EffectsRemote:FireClient(player, kind, data) end
        end
    end
    local names = {"NetworkService", "SaveService", "WorldService", "BuildingService", "NPCService", "SquadService", "RecruitService", "TravelService", "QuestService", "InventoryService", "ResourceService", "DoorService", "ShopService", "CombatService", "ZombieService", "EconomyService", "SurvivalService", "PlayerDataService"}
    for _, name in names do workspace:SetAttribute("LastCityServerPhase", "Create: " .. name); ctx.Services[name] = require(script.Parent.Services[name])(ctx) end
    for _, name in names do workspace:SetAttribute("LastCityServerPhase", "Init: " .. name); if ctx.Services[name].Init then ctx.Services[name]:Init() end end
    workspace:SetAttribute("LastCityServerPhase", "Ready")
    workspace:SetAttribute("LastCityReady", true)
    print("[LAST CITY] " .. ctx.Config.Version .. " • server ready; use Studio QA checklist before production.")
end
local ok, err = xpcall(boot, debug.traceback)
if not ok then
    workspace:SetAttribute("LastCityServerError", "Сбой сервера: " .. tostring(workspace:GetAttribute("LastCityServerPhase")))
    warn("[LastCity BOOT] " .. tostring(err))
end
