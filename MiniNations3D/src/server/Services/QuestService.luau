local QuestService = {}
local CoinService = require(script.Parent.CoinService)
local daily = {
    {id="build5", text="Построй 5 зданий", target=5, reward=50},
    {id="army1", text="Создай одну армию", target=1, reward=50},
    {id="trade1", text="Заключи торговый договор", target=1, reward=70},
    {id="tech1", text="Исследуй технологию", target=1, reward=60},
    {id="match1", text="Сыграй один матч", target=1, reward=80},
}
local weekly = {
    {id="w_build20", text="Построй 20 зданий", target=20, reward=300},
    {id="w_win3", text="Выиграй 3 матча", target=3, reward=400},
}
local progress: {[number]: {[string]: number}} = {}
function QuestService.getDaily(player:Player) return daily end
function QuestService.getProgress(player:Player, questId:string):number
    return progress[player.UserId] and progress[player.UserId][questId] or 0
end
function QuestService.add(player:Player, questId:string, amount:number)
    progress[player.UserId]=progress[player.UserId] or {}
    progress[player.UserId][questId]=(progress[player.UserId][questId] or 0)+amount
    -- check complete
    for _,q in ipairs(daily) do if q.id==questId and progress[player.UserId][questId]>=q.target then CoinService.addCoins(player, q.reward, "Daily") end end
    for _,q in ipairs(weekly) do if q.id==questId and progress[player.UserId][questId]>=q.target then CoinService.addCoins(player, q.reward, "Weekly") end end
end
return QuestService
