local MapController = {}
local RS = game:GetService("ReplicatedStorage")
local ProvinceData = require(RS.Shared.ProvinceData)
local selectedCountry:string? = nil
local selectedProvince:string? = nil
local hoverProv:string? = nil

function MapController.init()
    -- генерировать 3D карту из провинций
    local folder = workspace:FindFirstChild("Provinces")
    if not folder then folder=Instance.new("Folder"); folder.Name="Provinces"; folder.Parent=workspace end
    folder:ClearAllChildren()
    for id,prov in pairs(ProvinceData.list) do
        local part = Instance.new("Part")
        part.Name=id
        part.Anchored=true; part.CanCollide=false; part.TopSurface=Enum.SurfaceType.Smooth
        part.Size=Vector3.new(38, math.random(2,9), 38)
        part.Position = prov.pos + Vector3.new(0, part.Size.Y/2, 0)
        -- цвет по владельцу
        local country = require(RS.Shared.CountryData).byId[prov.owner]
        part.Color = country and country.color or Color3.fromHex("#334155")
        part.Material=Enum.Material.Slate
        part.Parent=folder
        -- ClickDetector для выбора
        local cd = Instance.new("ClickDetector"); cd.MaxActivationDistance=800; cd.Parent=part
        cd.MouseClick:Connect(function(pl) MapController.selectProvince(id) end)
        cd.MouseHoverEnter:Connect(function() hoverProv=id; part.Material=Enum.Material.Neon end)
        cd.MouseHoverLeave:Connect(function() if hoverProv==id then hoverProv=nil end; part.Material=Enum.Material.Slate end)
        -- Биом: горные провинции выше
        if id:find("3$") then part.Size=Vector3.new(38,12,38); part.Color=Color3.fromHex("#78716C") end
        -- Леса — маленькие цилиндры
        if math.random()<0.3 then
            local tree=Instance.new("Part"); tree.Size=Vector3.new(2,6,2); tree.Position=part.Position+Vector3.new(math.random(-12,12),4, math.random(-12,12)); tree.Color=Color3.fromHex("#15803D"); tree.Anchored=true; tree.Parent=part
        end
    end
    -- границы: подсветка через SelectionBox
    -- Океан
    local ocean = Instance.new("Part")
    ocean.Name="Ocean"; ocean.Size=Vector3.new(2600, 2, 1600); ocean.Position=Vector3.new(0,-4,0); ocean.Anchored=true; ocean.Color=Color3.fromHex("#1E3A8A"); ocean.Material=Enum.Material.SmoothPlastic; ocean.Transparency=0.25; ocean.Parent=workspace

    -- ремоуты обновления
    local remotes = RS:FindFirstChild("Remotes")
    if remotes then
        remotes:FindFirstChild("ProvinceOwnerChanged").OnClientEvent:Connect(function(pid,newOwner)
            local p = folder:FindFirstChild(pid)
            if p then
                local c=require(RS.Shared.CountryData).byId[newOwner]
                if c then game:GetService("TweenService"):Create(p, TweenInfo.new(0.6), {Color=c.color}):Play() end
            end
        end)
        remotes:FindFirstChild("BuildingPlaced").OnClientEvent:Connect(function(pid, building, pos)
            local mdl=Instance.new("Part"); mdl.Size=Vector3.new(6,10,6); mdl.Position=pos+Vector3.new(math.random(-10,10),6, math.random(-10,10)); mdl.Anchored=true; mdl.Color=Color3.fromHex("#FBBF24"); mdl.Material=Enum.Material.Concrete; mdl.Parent=folder
        end)
    end
end

function MapController.selectProvince(id:string)
    selectedProvince=id
    local prov = ProvinceData.get(id)
    if prov then
        -- показать UI провинции
        local HUD = require(script.Parent.Parent.UI.HUD)
        HUD.showProvince(prov)
        -- обновить выбранную страну
        selectedCountry = prov.owner
    end
end
function MapController.getSelectedCountry() return selectedCountry end
function MapController.getSelectedCountryCapitalPos(): Vector3?
    if not selectedCountry then return nil end
    local list={}
    for _,p in pairs(ProvinceData.list) do if p.owner==selectedCountry then table.insert(list,p) end end
    if #list>0 then return list[1].pos end
    return nil
end
function MapController.setMapMode(mode:string)
    local folder=workspace:FindFirstChild("Provinces"); if not folder then return end
    for _,part in ipairs(folder:GetChildren()) do
        if part:IsA("BasePart") then
            local prov = ProvinceData.get(part.Name)
            if not prov then continue end
            if mode=="Political" then
                local c=require(RS.Shared.CountryData).byId[prov.owner]; part.Color=c and c.color or part.Color
            elseif mode=="Economic" then
                local t = prov.economy/2000; part.Color=Color3.fromHSV(0.33*t,0.8,0.9)
            elseif mode=="Population" then
                local t=prov.population/4000000; part.Color=Color3.fromHSV(0.05+t*0.15,0.85,0.9)
            end
        end
    end
end
return MapController
