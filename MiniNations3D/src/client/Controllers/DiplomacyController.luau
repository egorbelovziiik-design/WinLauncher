local DiplomacyController = {}
local RS = game:GetService("ReplicatedStorage")
function DiplomacyController.declareWar(from:string, to:string)
    local r=RS.Remotes:FindFirstChild("DeclareWar"); if r then r:FireServer(from,to) end
end
function DiplomacyController.makePeace(warId:string)
    local r=RS.Remotes:FindFirstChild("MakePeace"); if r then r:FireServer(warId) end
end
function DiplomacyController.createAlliance(name:string, color:Color3, leader:string)
    local r=RS.Remotes:FindFirstChild("CreateAlliance"); if r then r:FireServer(name,color,leader) end
end
return DiplomacyController
