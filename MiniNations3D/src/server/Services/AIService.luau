local AIService = {}
local CountryData = require(game.ReplicatedStorage.Shared.CountryData)
local ProvinceData = require(game.ReplicatedStorage.Shared.ProvinceData)
local EconomyService = require(script.Parent.EconomyService)
local MilitaryService = require(script.Parent.MilitaryService)
local DiplomacyService = require(script.Parent.DiplomacyService)
local BuildingService = require(script.Parent.BuildingService)

local running=false
local difficulty="Normal"
local aggression=0.6

function AIService.configure(opts:{difficulty:string?, aggression:number?})
    if opts.difficulty then difficulty=opts.difficulty end
    if opts.aggression then aggression=opts.aggression end
end

function AIService.start()
    if running then return end; running=true
    task.spawn(function()
        while running do task.wait(8)
            for _,c in ipairs(CountryData) do if type(c)=="table" and c.id then
                local role = c.id==CountryData[2].id and "Aggressive" or (math.random()<0.5 and "Economic" or "Defensive")
                -- экономика: строить случайное здание
                if math.random() < 0.45 then
                    local provs={} for _,p in pairs(ProvinceData.list) do if p.owner==c.id then table.insert(provs,p) end end
                    if #provs>0 then
                        local p = provs[math.random(1,#provs)]
                        local picks={"House","Shop","CoalPlant","MilitaryFactory","Barracks","Road"}
                        local b = picks[math.random(1,#picks)]
                        local fakePl = {UserId=0} :: any
                        pcall(function() BuildingService.build(fakePl :: any, p.id, b) end)
                    end
                end
                -- военный найм
                if math.random() < 0.25 + aggression*0.2 then
                    local provs={} for _,p in pairs(ProvinceData.list) do if p.owner==c.id then table.insert(provs,p) end end
                    if #provs>0 then
                        local p = provs[math.random(1,#provs)]
                        local units={"Infantry","Tank","Artillery","Recon"}
                        pcall(function() MilitaryService.create({UserId=0}::any, p.id, units[math.random(1,#units)], c.id) end)
                    end
                end
                -- дипломатия
                if math.random() < 0.15 then
                    local other = CountryData[math.random(1,20)]
                    if type(other)=="table" and other.id~=c.id then
                        DiplomacyService.change(c.id, other.id, math.random(-6,10))
                    end
                end
            end end
        end
    end)
    -- войны AI vs AI иногда
    task.spawn(function()
        while running do task.wait(22)
            if math.random() < aggression*0.35 then
                local a = CountryData[math.random(1,20)]; local d=CountryData[math.random(1,20)]
                if type(a)=="table" and type(d)=="table" and a.id~=d.id then
                    local WarService = require(script.Parent.WarService)
                    local id = WarService.declare(a.id, d.id)
                    -- через 10 сек попытка битвы
                    task.delay(10, function()
                        local provs={} for _,p in pairs(ProvinceData.list) do if p.owner==d.id then table.insert(provs,p) end end
                        if #provs>0 and warsStillExist(id) then
                            WarService.battle(id, a.id, d.id, provs[math.random(1,#provs)].id)
                        end
                    end)
                end
            end
        end
    end)
end
function warsStillExist(id:string)
    local WarService = require(script.Parent.WarService)
    return WarService.list()[id] ~= nil
end
function AIService.stop() running=false end
return AIService
