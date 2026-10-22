local ShopService = {}
local catalog = {
    {id="gold_borders", name="Золотые границы", price=400, cat="Country", preview="#FFD700"},
    {id="neon_borders", name="Неоновые границы", price=600, cat="Country", preview="#39FF14"},
    {id="diamond_borders", name="Алмазные границы", price=1200, cat="Country", preview="#00FFFF"},
    {id="modern_city", name="Современный город", price=800, cat="City", preview="#60A5FA"},
    {id="cyber_city", name="Киберпанк город", price=1500, cat="City", preview="#FF00FF"},
    {id="gold_tank", name="Золотые танки", price=700, cat="Army", preview="#FFD700"},
    {id="future_army", name="Футуристическая армия", price=1100, cat="Army", preview="#00FFFF"},
    {id="music_menu2", name="Саундтрек Меню 2", price=300, cat="Music", preview="#A78BFA"},
    {id="music_war", name="Боевой саундтрек", price=500, cat="Music", preview="#EF4444"},
    {id="ui_neon", name="Неоновая тема", price=450, cat="UI", preview="#39FF14"},
    {id="flag_goldframe", name="Золотая рамка флага", price=350, cat="Flag", preview="#FFD700"},
}
local CoinService = require(script.Parent.CoinService)
function ShopService.catalog() return catalog end
function ShopService.buy(player:Player, itemId:string): (boolean,string)
    local item
    for _,it in ipairs(catalog) do if it.id==itemId then item=it break end end
    if not item then return false, "No item" end
    local profile = CoinService.ensure(player)
    if table.find(profile.ownedSkins, itemId) then return false, "Already owned" end
    if not CoinService.spend(player, item.price) then return false, "Not enough coins" end
    table.insert(profile.ownedSkins, itemId)
    local rem = game.ReplicatedStorage.Remotes:FindFirstChild("ShopPurchased")
    if rem then rem:FireClient(player, itemId) end
    return true, "Purchased"
end
return ShopService
