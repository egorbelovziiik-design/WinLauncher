--!nonstrict
return function(ctx)
    local Service = {}
    function Service:Use(player, s, uid, aim)
        local c = s.Campaign; local entry = ctx.Inventory.find(c, uid)
        local def = entry and ctx.Items[entry.id]; local h = ctx:Alive(player)
        if not def or not def.use or not h then return false, "Предмет нельзя использовать" end
        if def.grenade then return ctx.Services.CombatService:ThrowGrenade(player, s, uid, aim) end
        local need = def.use == "health" and h.Health < h.MaxHealth or def.use == "hunger" and c.Hunger < 100
            or def.use == "thirst" and c.Thirst < 100 or def.use == "stamina" and s.Stamina < (s.StaminaMax or 100)
        if not need then return false, "Этот показатель уже полон" end
        if not ctx.Inventory.takeUid(c, uid, 1) then return false, "Предмет не найден" end
        if def.use == "health" then
            local armor = ctx.Inventory.find(c, c.Armor)
            local multiplier = armor and ctx.Items[armor.id].healMultiplier or 1
            h.Health = math.min(h.MaxHealth, h.Health + def.amount * multiplier)
        elseif def.use == "hunger" then c.Hunger = math.min(100, c.Hunger + def.amount)
        elseif def.use == "thirst" then c.Thirst = math.min(100, c.Thirst + def.amount)
        else s.Stamina = math.min(s.StaminaMax or 100, s.Stamina + def.amount) end
        return true, nil
    end
    function Service:Quick(player, s, index, aim)
        if not ctx.Util.integer(index, 1, ctx.Config.QuickSlots) then return false, "Неверный быстрый слот" end
        local c = s.Campaign; local uid = c.QuickSlots[index]
        local entry = ctx.Inventory.find(c, uid); local def = entry and ctx.Items[entry.id]
        if not entry or not def then return false, "Быстрый слот пуст" end
        if ctx.Weapons[entry.id] then
            if c.Equipped == uid then c.Equipped = "" else
                local ok, err = ctx.Inventory.equip(c, uid); if not ok then return false, err end
            end
            s.Reloading = nil; ctx.Services.CombatService:SyncTool(player); return true, nil
        elseif def.armor then
            if c.Armor == uid then c.Armor = "" else
                local ok, err = ctx.Inventory.equip(c, uid); if not ok then return false, err end
            end
            ctx.Services.CombatService:SyncTool(player); return true, nil
        elseif def.use then return self:Use(player, s, uid, aim) end
        return false, "Предмет нельзя использовать из быстрого слота"
    end
    function Service:Init()
        ctx.Services.NetworkService:Register("InventoryRemote", true, function(player, s, p)
            local c = s.Campaign
            local ok, err = false, "Недоступное действие"
            if p.action == "toggleCrowbar" then
                local crowbar
                for _, entry in c.Inventory do if entry.id == "crowbar" then crowbar = entry; break end end
                if not crowbar then err = "В рюкзаке нет лома"
                else
                    c.QuickSlots[1] = crowbar.uid
                    for index = 2, ctx.Config.QuickSlots do if c.QuickSlots[index] == crowbar.uid then c.QuickSlots[index] = "" end end
                    if c.Equipped == crowbar.uid then
                        c.Equipped = ""; s.Reloading = nil; ctx.Services.CombatService:SyncTool(player); ok = true
                    else
                        ok, err = ctx.Inventory.equip(c, crowbar.uid)
                        if ok then s.Reloading = nil; ctx.Services.CombatService:SyncTool(player) end
                    end
                end
            elseif p.action == "equip" then
                local slot
                ok, slot = ctx.Inventory.equipToQuick(c, p.uid)
                if ok then
                    err = nil; s.Reloading = nil; ctx.Services.CombatService:SyncTool(player)
                    ctx:Notify(player, "Предмет назначен в быстрый слот " .. slot .. ".", "success")
                else err = slot end
            elseif p.action == "slot" then
                local entry = ctx.Inventory.find(c, p.uid); local def = entry and ctx.Items[entry.id]
                if not entry or not def or entry.id == "crowbar" or not (def.weapon or def.armor or def.use) then err = "Этот предмет нельзя назначить в быстрый слот"
                else
                    local slot
                    ok, slot = ctx.Inventory.assignQuick(c, p.uid)
                    if ok then err = nil; ctx:Notify(player, "Предмет назначен в быстрый слот " .. slot .. ".", "success") else err = slot end
                end
            elseif p.action == "quick" then ok, err = self:Quick(player, s, p.index, p.aim)
            elseif p.action == "quickClear" then ok, err = ctx.Inventory.clearQuick(c, p.index)
            elseif p.action == "unequip" then
                local entry = ctx.Inventory.find(c, p.uid); local item = entry and ctx.Items[entry.id]
                if entry and c.Equipped == entry.uid and ctx.Weapons[entry.id] then
                    c.Equipped = ""; s.Reloading = nil; ctx.Services.CombatService:SyncTool(player); ok = true
                elseif entry and c.Armor == entry.uid and item and item.armor then
                    c.Armor = ""; ctx.Services.CombatService:SyncTool(player); ok = true
                else err = "Предмет уже снят" end
            elseif p.action == "use" then ok, err = self:Use(player, s, p.uid, p.aim)
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
