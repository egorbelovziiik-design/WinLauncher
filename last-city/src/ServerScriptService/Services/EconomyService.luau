--!nonstrict
return function(ctx)
    local Service = {TurretAt = {}}
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
                    if not s.Active or not s.Power or s.Closing or s.Frozen then continue end
                    for _, b in s.Campaign.Buildings do
                        if b.id == "turret" and s.Power.powered[b.uid] and b.readyAt <= os.time() and (s.Campaign.Stockpile.ammo or 0) > 0 then
                            local origin = ctx.Services.BuildingService:Position(player, b) + Vector3.new(0, 7, 0)
                            local target = ctx.Services.ZombieService:Nearest(origin, 65)
                            local model = ctx.Services.BuildingService.Models[player][b.uid]
                            if target and ctx:LineOfSight(origin, target.model.PrimaryPart.Position, {model, target.model}) then
                                s.Campaign.Stockpile.ammo -= 1
                                ctx.Services.ZombieService:Damage(target, 22, player)
                                ctx:FX("shot", {from = origin, to = target.model.PrimaryPart.Position, melee = false, hit = true, user = player.UserId})
                            end
                        end
                    end
                end
            end
        end)
    end
    return Service
end
