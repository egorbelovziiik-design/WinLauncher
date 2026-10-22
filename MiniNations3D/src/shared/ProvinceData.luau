-- Процедурная генерация ~120 провинций вокруг стран
local CountryData = require(script.Parent.CountryData)
local ProvinceData = {}
local provinces = {}
local idCounter=0
local function add(name:string, owner:string, pop:number, econ:number, pos:Vector3)
    idCounter+=1
    local id = string.format("P-%03d", idCounter)
    if owner=="CAP" then owner = CountryData[1].id end
    provinces[id] = {id=id, name=name, owner=owner, population=pop, economy=econ, infrastructure=math.random(40,90), stability=math.random(60,95), buildings={}, army={}, pos=pos}
    -- также сохраняем для простоты связь с визуальной позицией
end
-- Столицы
for i,c in ipairs(CountryData) do
    if type(c)=="table" and c.id and not c.id:find("byId") then
        add(c.name.." Capital", c.id, math.random(800000,5000000), math.random(800,2500), Vector3.new(math.random(-1100,1100), 0.5, math.random(-600,600)))
    end
end
-- Дополнительные провинции (по 5 на страну примерно)
for i=1,80 do
    local c = CountryData[math.random(1,20)]
    add(c.name.." Region "..i, c.id, math.random(200000,2000000), math.random(300,1500), Vector3.new(math.random(-1100,1100),0.2, math.random(-600,600)))
end
ProvinceData.list = provinces
function ProvinceData.get(id:string) return provinces[id] end
function ProvinceData.all():{{any}} local t={} for _,v in pairs(provinces) do table.insert(t,v) end return t end
return ProvinceData
