local ShopController = {}
local RS=game:GetService("ReplicatedStorage")
function ShopController.buy(itemId:string)
    local r=RS.Remotes:FindFirstChild("BuyItem"); if r then r:FireServer(itemId) end
end
return ShopController
