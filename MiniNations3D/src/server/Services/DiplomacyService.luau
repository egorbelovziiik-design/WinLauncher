local DiplomacyService = {}
local CountryData = require(game.ReplicatedStorage.Shared.CountryData)
local relations: {[string]: {[string]: number}} = {}
function DiplomacyService.init()
    for _,a in ipairs(CountryData) do if type(a)=="table" and a.id then
        relations[a.id]=relations[a.id] or {}
        for _,b in ipairs(CountryData) do if type(b)=="table" and b.id and a.id~=b.id then relations[a.id][b.id]=math.random(-20,40) end end
    end end
end
function DiplomacyService.get(a:string,b:string) return relations[a] and relations[a][b] or 0 end
function DiplomacyService.change(a:string,b:string, delta:number)
    if not relations[a] then return end
    relations[a][b]=math.clamp((relations[a][b] or 0)+delta, -100,100)
    relations[b][a]=math.clamp((relations[b][a] or 0)+delta*0.8, -100,100)
    local rem=game.ReplicatedStorage.Remotes:FindFirstChild("RelationChanged")
    if rem then rem:FireAllClients(a,b, relations[a][b]) end
end
function DiplomacyService.proposeAlliance(a:string,b:string):boolean
    if DiplomacyService.get(a,b) < 20 then return false end
    DiplomacyService.change(a,b, 10)
    return true
end
function DiplomacyService.sanction(a:string,b:string)
    DiplomacyService.change(a,b, -18)
    local eco = require(script.Parent.EconomyService)
    eco.applySanctions(a,b)
end
function DiplomacyService.all() return relations end
return DiplomacyService
