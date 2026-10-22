--!nonstrict
return function(Definitions, Inventory, Config, Util)
    local Building = {}
    function Building.extents(id, r)
        local s = Definitions[id].size
        if r % 2 == 0 then return s[1] / 2, s[3] / 2 end
        return s[3] / 2, s[1] / 2
    end
    function Building.valid(c, id, x, z, r)
        local def = Definitions[id]
        if not def or not Util.integer(x, -48, 48) or not Util.integer(z, -48, 48) or not Util.integer(r, 0, 3) then return false, "Неверная позиция" end
        if x % Config.Grid ~= 0 or z % Config.Grid ~= 0 then return false, "Нужна привязка к сетке" end
        if #c.Buildings >= Config.MaxBuildings then return false, "Лимит: " .. Config.MaxBuildings .. " построек" end
        local hx, hz = Building.extents(id, r)
        if math.abs(x) + hx > Config.PlotHalfSize or math.abs(z) + hz > Config.PlotHalfSize then return false, "За пределами вашего участка" end
        -- Keep a spawn/terminal clearance in the front centre of every plot.
        for _, zone in Config.PlotReserved do
            if math.abs(x - zone.x) < hx + zone.hx and math.abs(z - zone.z) < hz + zone.hz then return false, zone.reason end
        end
        for _, other in c.Buildings do
            local ox, oz = Building.extents(other.id, other.r)
            -- Positive interior intersection is blocked; exact edge contact is intentionally valid.
            if math.abs(x - other.x) < hx + ox and math.abs(z - other.z) < hz + oz then return false, "Постройки пересекаются" end
        end
        if def.level and Util.level(c.XP) < def.level then return false, "Недостаточный уровень" end
        if def.blueprint and Inventory.count(c, def.blueprint) < 1 then return false, "Нужен чертёж инженера" end
        return true
    end
    function Building.place(c, id, x, z, r, now)
        local ok, err = Building.valid(c, id, x, z, r)
        if not ok then return false, err end
        local def = Definitions[id]
        if c.Cash < def.cash or not Inventory.afford(c, def.cost) then return false, "Недостаточно ресурсов / Survival Cash" end
        Inventory.pay(c, def.cost); c.Cash -= def.cash; c.NextBuildingId += 1
        local b = {uid = "b" .. c.NextBuildingId, id = id, x = x, z = z, r = r, hp = def.health,
            readyAt = now + def.seconds, carry = {}}
        table.insert(c.Buildings, b); c.Stats.buildings += 1
        return true, b
    end
    function Building.find(c, uid)
        for index, b in c.Buildings do if b.uid == uid then return b, index end end
        return nil
    end
    function Building.remove(c, uid, refund)
        local b, index = Building.find(c, uid)
        if not b then return false, "Постройка не найдена" end
        if refund then for id, count in Definitions[b.id].cost do c.Stockpile[id] = math.min(9999, (c.Stockpile[id] or 0) + math.floor(count * 0.25)) end end
        for _, w in c.Workers do if w.job == uid then w.job = "" end end
        if c.Builder and c.Builder.order == uid then c.Builder.order = "" end
        table.remove(c.Buildings, index); return true
    end
    function Building.repair(c, uid)
        local b = Building.find(c, uid)
        if not b then return false, "Постройка не найдена" end
        local def = Definitions[b.id]
        if b.hp >= def.health then return false, "Ремонт не нужен" end
        local cost = math.max(1, math.ceil((def.health - b.hp) / 50))
        if not Inventory.pay(c, {metal = cost}) then return false, "Нужно металла: " .. cost end
        b.hp = def.health; return true
    end
    return Building
end
