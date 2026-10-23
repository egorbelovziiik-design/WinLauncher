--!nonstrict
return function(ctx)
    local Service = {Products = {}, Counts = {}}
    function Service:Stock() return self.Counts end
    function Service:Init()
        for _, product in ctx.EconomyDefs.Shop do self.Products[product.id] = product; self.Counts[product.id] = product.stock end
        ctx.Services.NetworkService:Register("ShopRemote", false, function(player, s, p)
            local ok, err = false, "Подойдите к торговцу Максу"
            if p.action == "cosmetic" then
                ok, err = ctx.Economy.cosmetic(s.Data, p.id)
                if ok and s.Active then ctx.Services.CombatService:SyncTool(player) end
            elseif s.Active and ctx:Alive(player) then
                local cityQuest = s.Campaign.Quests.second_city
                local nearMax = ctx.Services.NPCService:Near(player, "max")
                local nearQuartermaster = cityQuest and cityQuest.status == "RewardClaimed" and ctx.Services.NPCService:Near(player, "quartermaster")
                if not nearMax and not nearQuartermaster then ctx:Notify(player, err, "error"); return end
                local product = self.Products[p.id]
                if product and p.action == "buy" then
                    if product.city2 and not nearQuartermaster then
                        ctx:Notify(player, "Этот товар хранится только у квартирмейстера Феликса в НОВОМ РУБЕЖЕ.", "error"); return
                    end
                    local nextStock
                    ok, nextStock = ctx.Economy.buy(s.Campaign, product, self.Counts[p.id], p.count)
                    if ok then self.Counts[p.id] = nextStock else err = nextStock end
                elseif product and p.action == "sell" and not ctx.Items[p.id].weapon and not ctx.Items[p.id].armor then
                    if ctx.Util.integer(p.count, 1, 50) and self.Counts[p.id] + p.count <= product.stock * 2 and ctx.Inventory.remove(s.Campaign, p.id, p.count) then
                        s.Campaign.Cash += math.floor(product.price * 0.35) * p.count
                        self.Counts[p.id] += p.count; ok = true
                    else err = "Нет предметов в рюкзаке или рынок переполнен" end
                end
            end
            ctx:Notify(player, ok and "Сделка завершена" or err, ok and "success" or "error"); ctx:Changed(player)
        end)
        task.spawn(function()
            while task.wait(300) do for id, product in self.Products do self.Counts[id] = product.stock end end
        end)
    end
    return Service
end
