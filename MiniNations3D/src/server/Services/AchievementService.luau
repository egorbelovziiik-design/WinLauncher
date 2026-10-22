local AchievementService = {}
local CoinService = require(script.Parent.CoinService)
local defs = {
    first_win={name="Первая победа", desc="Выиграй свой первый матч", reward=200},
    first_war={name="Первая война", desc="Объяви войну", reward=100},
    first_alliance={name="Первый альянс", desc="Создай альянс", reward=150},
    first_power={name="Первая электростанция", desc="Построй электростанцию", reward=120},
    first_city={name="Первый город", desc="Оснуй поселение", reward=100},
    superpower={name="Экономическая сверхдержава", desc="Достигни ВВП 10k", reward=500},
    domination={name="Мировое господство", desc="Захвати 60 провинций", reward=1000},
    diplomat={name="Великий дипломат", desc="5 альянсов", reward=400},
    builder={name="Великий строитель", desc="100 зданий", reward=350},
    techleader={name="Технологический лидер", desc="Открой 10 технологий", reward=300},
}
function AchievementService.unlock(player:Player, id:string)
    local def = defs[id]; if not def then return end
    local p = CoinService.ensure(player)
    if table.find(p.achievements, id) then return end
    table.insert(p.achievements, id)
    CoinService.addCoins(player, def.reward, "Achievement:"..id)
    CoinService.addXP(player, 100)
    local rem = game.ReplicatedStorage.Remotes:FindFirstChild("AchievementUnlocked")
    if rem then rem:FireClient(player, id, def) end
end
function AchievementService.list() return defs end
return AchievementService
