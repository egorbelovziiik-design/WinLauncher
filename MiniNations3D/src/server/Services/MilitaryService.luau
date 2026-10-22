local MilitaryService = {}
local ProvinceData = require(game.ReplicatedStorage.Shared.ProvinceData)
local Config = require(game.ReplicatedStorage.Shared.Config)
local unitCost = {Infantry=120, Motorized=250, Tank=600, Artillery=500, AA=450, Aviation=900, Helicopter=850, Navy=1200, Recon=200}

local armies: {[string]: any} = {} -- id -> army
local byProvince: {[string]: {string}} = {}

function MilitaryService.create(player:Player, provinceId:string, unitType:string, countryId:string?)
    local prov = ProvinceData.get(provinceId)
    if not prov then return false, "Bad province" end
    if not table.find(Config.UNITS, unitType) then return false, "Bad unit" end
    local cost = unitCost[unitType] or 300
    -- budget check skipped for demo, but would call EconomyService
    local id = "ARMY_"..tostring(math.random(100000,999999))
    local army = {id=id, type=unitType, owner=countryId or prov.owner, province=provinceId, hp=100, morale=80, pos=prov.pos}
    armies[id]=army
    byProvince[provinceId]=byProvince[provinceId] or {}; table.insert(byProvince[provinceId], id)
    table.insert(prov.army, id)
    local rem = game.ReplicatedStorage.Remotes:FindFirstChild("ArmyCreated")
    if rem then rem:FireAllClients(army) end
    -- создать 3D модель в Workspace
    MilitaryService.spawnModel(army)
    return true, id
end

function MilitaryService.move(armyId:string, toProvinceId:string)
    local army = armies[armyId]; if not army then return false end
    local toProv = ProvinceData.get(toProvinceId); if not toProv then return false end
    local fromProv = ProvinceData.get(army.province)
    if fromProv then
        -- удалить из старой
        for i,v in ipairs(fromProv.army) do if v==armyId then table.remove(fromProv.army,i) break end end
    end
    army.province = toProvinceId; army.pos = toProv.pos
    table.insert(toProv.army, armyId)
    local rem = game.ReplicatedStorage.Remotes:FindFirstChild("ArmyMoved")
    if rem then rem:FireAllClients(armyId, toProvinceId, toProv.pos) end
    -- tween модели
    local mdl = workspace:FindFirstChild("Armies") and workspace.Armies:FindFirstChild(armyId)
    if mdl then
        game:GetService("TweenService"):Create(mdl.PrimaryPart, TweenInfo.new(1.2, Enum.EasingStyle.Quad), {Position = toProv.pos + Vector3.new(0,2,0)}):Play()
    end
    return true
end

function MilitaryService.spawnModel(army:any)
    local folder = workspace:FindFirstChild("Armies")
    if not folder then folder=Instance.new("Folder"); folder.Name="Armies"; folder.Parent=workspace end
    local part = Instance.new("Part")
    part.Name=army.id; part.Size=Vector3.new(4,2.5,6); part.Anchored=true; part.CanCollide=false
    part.Position = army.pos + Vector3.new(math.random(-6,6),2, math.random(-6,6))
    part.Color = Color3.fromHex("#FACC15"); part.Material=Enum.Material.SmoothPlastic
    part.Parent=folder
    local hum = Instance.new("BillboardGui"); hum.Size=UDim2.fromOffset(80,18); hum.StudsOffset=Vector3.new(0,3,0); hum.Parent=part
    local tl = Instance.new("TextLabel"); tl.Size=UDim2.fromScale(1,1); tl.BackgroundTransparency=0.3; tl.BackgroundColor3=Color3.fromHex("#0F172A"); tl.TextColor3=Color3.new(1,1,1); tl.TextScaled=true; tl.Text=army.type; tl.Parent=hum
end

function MilitaryService.get(armyId:string) return armies[armyId] end
function MilitaryService.all() return armies end
function MilitaryService.inProvince(pid:string) return byProvince[pid] or {} end
return MilitaryService
