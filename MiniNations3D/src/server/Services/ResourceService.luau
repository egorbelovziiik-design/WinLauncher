local ResourceService = {}
local Config = require(game.ReplicatedStorage.Shared.Config)
local ProvinceData = require(game.ReplicatedStorage.Shared.ProvinceData)
local stocks: {[string]: {[string]: number}} = {} -- country -> resource -> amount

function ResourceService.init()
    for _,p in pairs(ProvinceData.list) do
        if not stocks[p.owner] then stocks[p.owner]={} for _,r in ipairs(Config.RESOURCES) do stocks[p.owner][r]=math.random(200,1200) end end
    end
    task.spawn(function()
        while true do task.wait(7)
            for cid, store in pairs(stocks) do
                store.Oil  += math.random(0,2)
                store.Food += math.random(2,6)
                store.Coal += math.random(0,3)
                -- потребление
                for k,_ in pairs(store) do store[k] = math.max(0, store[k] - math.random(0,1)) end
            end
        end
    end)
end
function ResourceService.get(cid:string) return stocks[cid] end
function ResourceService.trade(from:string, to:string, res:string, amount:number)
    local a=stocks[from]; local b=stocks[to]
    if not a or not b then return false end
    if (a[res] or 0) < amount then return false end
    a[res]-=amount; b[res]=(b[res] or 0)+amount; return true
end
return ResourceService
