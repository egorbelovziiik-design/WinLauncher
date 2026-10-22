local BuildingController = {}
local ghost: Model? = nil
local selectedBuilding:string? = "House"
local RS = game:GetService("ReplicatedStorage")

local function makeGhost(name:string)
    if ghost then ghost:Destroy() end
    local m=Instance.new("Model"); m.Name="Ghost"
    local p=Instance.new("Part"); p.Size=Vector3.new(8,8,8); p.Anchored=true; p.CanCollide=false; p.Transparency=0.5; p.Color=Color3.fromHex("#22C55E"); p.Material=Enum.Material.ForceField; p.Parent=m
    m.PrimaryPart=p; m.Parent=workspace; ghost=m
    -- follow mouse via Raycast
    local UIS=game:GetService("UserInputService")
    game:GetService("RunService").RenderStepped:Connect(function()
        if not ghost or not ghost.Parent then return end
        local mouse = UIS:GetMouseLocation()
        local cam=workspace.CurrentCamera
        local ray = cam:ViewportPointToRay(mouse.X, mouse.Y)
        local params=RaycastParams.new(); params.FilterDescendantsInstances={ghost, workspace:FindFirstChild("Provinces")}; params.FilterType=Enum.RaycastFilterType.Exclude
        local res=workspace:Raycast(ray.Origin, ray.Direction*2000, params)
        if res then
            ghost:PivotTo(CFrame.new(res.Position + Vector3.new(0,4,0)))
            -- проверка можно ли строить (по провинции под курсором)
            local provFolder=workspace:FindFirstChild("Provinces")
            if provFolder then
                -- простейшая проверка расстояния до центра провинции
                local can=false
                for _,part in ipairs(provFolder:GetChildren()) do if (part.Position - res.Position).Magnitude < 28 then can=true break end end
                ghost.PrimaryPart.Color = can and Color3.fromHex("#22C55E") or Color3.fromHex("#EF4444")
            end
        end
    end)
end

function BuildingController.select(name:string) selectedBuilding=name; makeGhost(name) end
function BuildingController.get() return selectedBuilding end
function BuildingController.place()
    if not ghost or not selectedBuilding then return end
    -- найти ближайшую провинцию
    local pos = ghost:GetPivot().Position
    local best, dist=nil, 1e9
    local folder=workspace:FindFirstChild("Provinces"); if not folder then return end
    for _,part in ipairs(folder:GetChildren()) do
        local d=(part.Position - pos).Magnitude
        if d<dist then dist=d; best=part end
    end
    if best and dist < 40 then
        local rem = RS.Remotes:FindFirstChild("BuildRequest")
        if rem then rem:FireServer(best.Name, selectedBuilding) end
        game:GetService("ReplicatedStorage"):WaitForChild("Shared")
        -- локальный эффект
        require(script.Parent.AudioController).playSFX("build")
    end
end
function BuildingController.cancel() if ghost then ghost:Destroy(); ghost=nil end end
return BuildingController
