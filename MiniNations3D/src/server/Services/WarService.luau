local WarService = {}
local CountryService = require(script.Parent.CountryService)
local ProvinceData = require(game.ReplicatedStorage.Shared.ProvinceData)
local wars: {[string]: any} = {}

function WarService.declare(att:string, def:string)
    local id = att.."vs"..def.."_"..os.time()
    wars[id]={id=id, attackers={att}, defenders={def}, startTime=os.time(), battles=0}
    local rem = game.ReplicatedStorage.Remotes:FindFirstChild("WarDeclared")
    if rem then rem:FireAllClients(id, att, def) end
    return id
end
function WarService.battle(warId:string, attacker:string, defender:string, provinceId:string)
    local war = wars[warId]; if not war then return false end
    war.battles+=1
    -- очень упрощённый бросок
    local atkPower = math.random(60,140) + #CountryService.provincesOf(attacker)*2
    local defPower = math.random(60,140) + #CountryService.provincesOf(defender)*2
    local attackerWins = atkPower > defPower
    if attackerWins then
        local p = ProvinceData.get(provinceId)
        if p and p.owner==defender then
            CountryService.transferProvince(provinceId, attacker)
        end
    end
    local rem = game.ReplicatedStorage.Remotes:FindFirstChild("BattleResult")
    if rem then rem:FireAllClients(warId, provinceId, attackerWins, atkPower, defPower) end
    return attackerWins
end
function WarService.peace(warId:string)
    local w=wars[warId]; if not w then return false end
    wars[warId]=nil
    local rem = game.ReplicatedStorage.Remotes:FindFirstChild("PeaceSigned")
    if rem then rem:FireAllClients(warId) end
    return true
end
function WarService.list() return wars end
return WarService
