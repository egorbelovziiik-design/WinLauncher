local ScenarioService = {}
local ProvinceData = require(game.ReplicatedStorage.Shared.ProvinceData)
-- исторические модификаторы по году
local yearMods: {[number]: any} = {
    [1444]={popMul=0.35, econMul=0.4, tech="Feudal"},
    [1914]={popMul=0.8, econMul=0.9, tech="Industrial"},
    [1945]={popMul=0.85, econMul=1.0, tech="WW2"},
    [2026]={popMul=1.0, econMul=1.0, tech="Modern"},
    [2050]={popMul=1.1, econMul=1.3, tech="Future"},
}
function ScenarioService.applyYear(year:number)
    local mod = yearMods[year] or yearMods[2026]
    -- масштабируем провинции
    for _,p in pairs(ProvinceData.list) do
        p.population = math.floor(p.population * mod.popMul)
        p.economy = math.floor(p.economy * mod.econMul)
    end
    print("[Scenario] Applied year "..year.." mod "..mod.tech)
end
return ScenarioService
