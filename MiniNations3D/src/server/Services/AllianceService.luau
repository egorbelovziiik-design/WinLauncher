local AllianceService = {}
local alliances: {[string]: any} = {}
function AllianceService.create(name:string, color:Color3, leader:string)
    local id="AL_"..tostring(math.random(10000,99999))
    local al={id=id, name=name, color=color, leader=leader, members={leader}}
    alliances[id]=al
    local rem = game.ReplicatedStorage.Remotes:FindFirstChild("AllianceCreated")
    if rem then rem:FireAllClients(al) end
    return al
end
function AllianceService.invite(allianceId:string, countryId:string)
    local al=alliances[allianceId]; if not al then return false end
    if not table.find(al.members, countryId) then table.insert(al.members, countryId) end
    return true
end
function AllianceService.leave(allianceId:string, countryId:string)
    local al=alliances[allianceId]; if not al then return false end
    for i,v in ipairs(al.members) do if v==countryId then table.remove(al.members,i) break end end
    if #al.members==0 then alliances[allianceId]=nil end
    return true
end
function AllianceService.list() return alliances end
return AllianceService
