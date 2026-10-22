local PowerService = {}
local ProvinceData = require(game.ReplicatedStorage.Shared.ProvinceData)
local power: {[string]: {produced:number, consumed:number}} = {}

function PowerService.tick()
    task.spawn(function()
        while true do task.wait(6)
            for cid,_ in pairs(power) do power[cid]={produced=0, consumed=0} end
            for _,p in pairs(ProvinceData.list) do
                local pw = power[p.owner]; if not pw then pw={produced=0,consumed=0}; power[p.owner]=pw end
                for _,b in ipairs(p.buildings) do
                    if b=="CoalPlant" then pw.produced+=120 elseif b=="Nuclear" then pw.produced+=400 elseif b=="Solar" then pw.produced+=60 elseif b=="Wind" then pw.produced+=50
                    else pw.consumed+=8 end
                end
            end
        end
    end)
end
function PowerService.get(owner:string) return power[owner] or {produced=0,consumed=0} end
function PowerService.balance(owner:string) local g=PowerService.get(owner) return g.produced - g.consumed end
return PowerService
