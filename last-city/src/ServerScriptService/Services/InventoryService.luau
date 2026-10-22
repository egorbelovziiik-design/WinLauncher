--!nonstrict
return function(ctx)
    local Service = {}
    function Service:Init()
        ctx.Services.NetworkService:Register("InventoryRemote", true, function(player, s, p)
            local c = s.Campaign
            local ok, err = false, "Недоступное действие"
            if p.action == "toggleCrowbar" then
                local crowbar
                for _, entry in c.Inventory do if entry.id == "crowbar" then crowbar = entry; break end end
                if not crowbar then err = "В рюкзаке нет лома"
                elseif c.Equipped == crowbar.uid then
                    c.Equipped = ""; s.Reloading = nil; ctx.Services.CombatService:SyncTool(player); ok = true
                else
                    ok, err = ctx.Inventory.equip(c, crowbar.uid)
                    if ok then s.Reloading = nil; ctx.Services.CombatService:SyncTool(player) end
                end
            elseif p.action == "equip" then
                ok, err = ctx.Inventory.equip(c, p.uid)
                if ok then s.Reloading = nil; ctx.Services.CombatService:SyncTool(player) end
            elseif p.action == "unequip" then
                local entry = ctx.Inventory.find(c, p.uid)
                if entry and c.Equipped == entry.uid and ctx.Weapons[entry.id] then
                    c.Equipped = ""; s.Reloading = nil; ctx.Services.CombatService:SyncTool(player); ok = true
                else err = "Оружие уже убрано" end
            elseif p.action == "use" then
                local entry = ctx.Inventory.find(c, p.uid)
                local def = entry and ctx.Items[entry.id]
                local h = ctx:Alive(player)
                if def and def.use and h then
                    local need = def.use == "health" and h.Health < h.MaxHealth or def.use == "hunger" and c.Hunger < 100
                        or def.use == "thirst" and c.Thirst < 100 or def.use == "stamina" and s.Stamina < 100
                    if not need then err = "Этот показатель уже полон"
                    elseif ctx.Inventory.takeUid(c, p.uid, 1) then
                        if def.use == "health" then h.Health = math.min(h.MaxHealth, h.Health + def.amount)
                        elseif def.use == "hunger" then c.Hunger = math.min(100, c.Hunger + def.amount)
                        elseif def.use == "thirst" then c.Thirst = math.min(100, c.Thirst + def.amount)
                        else s.Stamina = math.min(100, s.Stamina + def.amount) end
                        ok = true
                    end
                end
            elseif p.action == "split" then ok, err = ctx.Inventory.split(c, p.uid, p.count)
            elseif p.action == "move" then ok = ctx.Inventory.move(c, p.uid, p.index)
            elseif p.action == "sort" then
                table.sort(c.Inventory, function(a, b) return a.id == b.id and a.uid < b.uid or a.id < b.id end); ok = true
            elseif p.action == "drop" then
                local entry = ctx.Inventory.find(c, p.uid)
                if entry and not ctx.Items[entry.id].weapon and not ctx.Items[entry.id].armor and not ctx.Items[entry.id].blueprint and ctx.Util.integer(p.count, 1, entry.count) then
                    local root = ctx:Root(player)
                    if ctx.Services.ResourceService:CanDrop(player) then
                        local id = entry.id
                        ctx.Inventory.takeUid(c, p.uid, p.count)
                        ctx.Services.ResourceService:Drop(player, id, p.count, root.Position + root.CFrame.LookVector * 4)
                        ok = true
                    else err = "Слишком много предметов на земле" end
                else err = "Оружие, броню и чертежи выбрасывать нельзя" end
            elseif p.action == "deposit" or p.action == "withdraw" then
                if not ctx:InPlot(player, 12) then err = "Подойдите к своему участку"
                else ok, err = ctx.Inventory.transfer(c, p.id, p.count, p.action == "deposit") end
            end
            if not ok then ctx:Notify(player, err or "Действие отклонено", "error") end
            ctx:Changed(player)
        end)
    end
    return Service
end
