local EconomyService = {}
local ProvinceData = require(game.ReplicatedStorage.Shared.ProvinceData)
local CountryData = require(game.ReplicatedStorage.Shared.CountryData)
local Util = require(game.ReplicatedStorage.Shared.Util)

local state: {[string]: any} = {} -- countryId -> economy

function EconomyService.init()
    for _,c in ipairs(CountryData) do
        if type(c)=="table" and c.id then
            state[c.id] = {
                gdp=math.random(800,9000), budget=5000, income=0, expenses=0,
                taxes=0.22, inflation=math.random(10,40)/10, debt=math.random(0,3000),
                unemployment=math.random(40,120)/10, tradeBalance=0, livingStandard=math.random(50,85)
            }
        end
    end
    task.spawn(function()
        while true do task.wait(5)
            for id,eco in pairs(state) do
                -- доход от провинций + налоги
                local provIncome=0
                for _,p in pairs(ProvinceData.list) do if p.owner==id then provIncome+= p.economy*0.8 + p.population/10000 end end
                eco.income = provIncome + math.random(80,200)
                eco.expenses = 400 + #ecoDebtBuildings(id)*18 + eco.debt*0.02
                local net = eco.income - eco.expenses
                eco.budget += net
                eco.gdp = Util.lerp(eco.gdp, provIncome*1.4, 0.06)
                eco.inflation = Util.clamp(eco.inflation + (math.random()-0.5)*0.05, -1, 12)
                if math.random() < 0.02 then -- кризис
                    eco.budget -= math.random(500,1500)
                    eco.unemployment += math.random(5,15)/10
                end
            end
        end
    end)
end
function ecoDebtBuildings(cid:string)
    local t={} for _,p in pairs(ProvinceData.list) do if p.owner==cid then for _,b in ipairs(p.buildings) do table.insert(t,b) end end end return t
end
function EconomyService.get(id:string) return state[id] end
function EconomyService.all() return state end
function EconomyService.applySanctions(from:string, to:string)
    local e = state[to]; if e then e.income -= 200; e.tradeBalance-=150 end
end
return EconomyService
