local CoinService = {}
local profiles: {[number]: any} = {}
local Config = require(game.ReplicatedStorage.Shared.Config)

function CoinService.getProfile(player:Player) return profiles[player.UserId] end
function CoinService.ensure(player:Player)
    if not profiles[player.UserId] then
        profiles[player.UserId] = {coins=500, xp=0, level=1, wins=0, losses=0, playTime=0, ownedSkins={}, achievements={}, tutorialDone=false, settings={music=0.7,sfx=0.8,graphics="Medium"}}
    end
    return profiles[player.UserId]
end
function CoinService.addCoins(player:Player, amount:number, reason:string?)
    local p = CoinService.ensure(player)
    p.coins += amount
    if game.ReplicatedStorage:FindFirstChild("Remotes") then
        local r = game.ReplicatedStorage.Remotes:FindFirstChild("CoinsUpdated")
        if r then r:FireClient(player, p.coins, amount, reason or "") end
    end
    return p.coins
end
function CoinService.spend(player:Player, amount:number):boolean
    local p = CoinService.ensure(player)
    if p.coins < amount then return false end
    p.coins -= amount
    return true
end
function CoinService.addXP(player:Player, xp:number)
    local p = CoinService.ensure(player)
    p.xp += xp
    local need = p.level*500
    if p.xp >= need then p.level+=1; p.xp-=need; CoinService.addCoins(player, Config.COINS_REWARDS.LevelUp, "LevelUp") end
end
return CoinService
