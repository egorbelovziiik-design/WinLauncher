--!nonstrict
return function(ctx)
    local Service = {TurretAt = {}, TrapAt = {}}
    function Service:PayFarmIncome(player, s, now, clock)
        if not s.Loaded or not s.Active or s.Closing or s.Frozen then return 0 end
        clock = clock or os.clock()
        if (s.NextFarmCash or 0) > clock then return 0 end
        s.NextFarmCash = clock + 1
        local payouts = ctx.Economy.farmIncome(s.Campaign, now or os.time())
        local total = 0
        for _, payout in payouts do
            local building = ctx.Building.find(s.Campaign, payout.uid)
            if building then
                total += payout.amount
                ctx.Remotes.EffectsRemote:FireClient(player, "farm_cash", {
                    position = ctx.Services.BuildingService:Position(player, building) + Vector3.new(0, 1.2, 0),
                    amount = payout.amount, uid = payout.uid,
                })
            end
        end
        -- PlayerDataService already publishes one bounded state snapshot per second. Mark the
        -- save dirty without scheduling a duplicate full campaign snapshot for this fast loop.
        if total > 0 then s.Data.Revision += 1 end
        return total
    end
    function Service:Init()
        ctx.Services.NetworkService:Register("CraftingRemote", true, function(player, s, p)
            if not ctx:InPlot(player, 12) then ctx:Notify(player, "Крафт доступен на своём участке.", "error"); return end
            local ok, err
            if p.action == "start" then ok, err = ctx.Economy.craft(s.Campaign, p.id, os.time())
            elseif p.action == "collect" then ok, err = ctx.Economy.collectCraft(s.Campaign, os.time())
            else return end
            ctx:Notify(player, ok and (p.action == "start" and "Заказ поставлен в работу" or "Заказ в рюкзаке") or err, ok and "success" or "error")
            ctx:Changed(player)
        end)
        task.spawn(function()
            while task.wait(5) do
                for player, s in ctx.Sessions do
                    if not s.Loaded or not s.Active or s.Closing or s.Frozen then continue end
                    s.Power = ctx.Economy.tick(s.Campaign, os.time(), game:GetService("Lighting").ClockTime)
                    for _, b in s.Campaign.Buildings do ctx.Services.BuildingService:Render(player, b) end
                    ctx.Quests.refresh(s.Campaign, os.time()); ctx:Changed(player)
                end
            end
        end)
        task.spawn(function()
            while task.wait(1) do
                for player, s in ctx.Sessions do
                    if not s.Loaded or not s.Active or s.Closing or s.Frozen then continue end
                    self:PayFarmIncome(player, s, os.time(), os.clock())
                    for _, b in s.Campaign.Buildings do
                        if b.id == "turret" and s.Power and s.Power.powered[b.uid] and ctx.Building.complete(s.Campaign, b, os.time()) and (s.Campaign.Stockpile.ammo or 0) > 0 then
                            local origin = ctx.Services.BuildingService:Position(player, b) + Vector3.new(0, 7, 0)
                            local target = ctx.Services.ZombieService:Nearest(origin, 65)
                            local model = ctx.Services.BuildingService.Models[player][b.uid]
                            if target and ctx:LineOfSight(origin, target.model.PrimaryPart.Position, {model, target.model}) then
                                s.Campaign.Stockpile.ammo -= 1
                                ctx.Services.ZombieService:Damage(target, 22, player)
                                ctx:FX("shot", {from = origin, to = target.model.PrimaryPart.Position, melee = false, hit = true, user = player.UserId})
                            end
                        elseif b.id == "spikes" and ctx.Building.complete(s.Campaign, b, os.time()) then
                            local key = tostring(player.UserId) .. ":" .. b.uid
                            if (self.TrapAt[key] or 0) <= os.clock() then
                                local origin = ctx.Services.BuildingService:Position(player, b) + Vector3.new(0, 1, 0)
                                local target = ctx.Services.ZombieService:Nearest(origin, 6.5)
                                if target then
                                    self.TrapAt[key] = os.clock() + 1.5
                                    ctx.Services.ZombieService:Damage(target, ctx.BuildingDefs.spikes.trapDamage, player)
                                    ctx.Services.BuildingService:Damage(player, b.uid, 1)
                                    ctx:FX("shot", {from = origin, to = target.model.PrimaryPart.Position, melee = true, hit = true, user = 0})
                                end
                            end
                        end
                    end
                end
            end
        end)
    end
    return Service
end
