--!nonstrict
return function(Buildings, Definitions, Inventory, Util)
    local Economy = {}
    function Economy.power(c, now)
        local supply, demand, powered = 0, 0, {}
        for _, b in c.Buildings do
            if b.readyAt <= now and b.hp > 0 then
                local def = Buildings[b.id]
                if def.power and (b.fuelUntil or 0) > now then supply += def.power; powered[b.uid] = true end
                demand += def.demand or 0
            end
        end
        local remaining = supply
        for _, b in c.Buildings do
            local def = Buildings[b.id]
            if b.readyAt <= now and b.hp > 0 then
                if def.demand then
                    powered[b.uid] = remaining >= def.demand
                    if powered[b.uid] then remaining -= def.demand end
                elseif not def.power then powered[b.uid] = true end
            end
        end
        return {supply = supply, demand = demand, spare = remaining, deficit = math.max(0, demand - supply), powered = powered}
    end
    function Economy.tick(c, now, hour)
        -- Deliberately no offline production/catch-up. This prevents timestamp farming.
        c.LastProduction = c.LastProduction or now; c.LastMeal = c.LastMeal or now
        for _, b in c.Buildings do
            local def = Buildings[b.id]
            if def.power and b.readyAt <= now and (b.fuelUntil or 0) <= now and (c.Stockpile.fuel or 0) >= def.fuel then
                c.Stockpile.fuel -= def.fuel; b.fuelUntil = now + Definitions.FuelSeconds
            end
        end
        local power = Economy.power(c, now)
        if now - c.LastMeal >= Definitions.WorkerSeconds then
            c.LastMeal = now
            local cost = #c.Workers
            if (c.Stockpile.food or 0) >= cost and (c.Stockpile.water or 0) >= cost then
                c.Stockpile.food -= cost; c.Stockpile.water -= cost; c.Morale = math.min(100, c.Morale + 3)
            else c.Morale = math.max(0, c.Morale - 8) end
        end
        if now - c.LastProduction < Definitions.ProductionSeconds then return power end
        c.LastProduction = now
        for _, b in c.Buildings do
            local def = Buildings[b.id]
            if not power.powered[b.uid] or not def.output then continue end
            if def.worker then
                local staffed = false
                for _, w in c.Workers do if w.job == b.uid and w.atWork == true then staffed = true end end
                if not staffed or hour < 6 or hour >= 21 then continue end
            end
            local ready = true
            for id, count in def.input or {} do if (c.Stockpile[id] or 0) < count then ready = false end end
            for id, count in def.output do if (c.Stockpile[id] or 0) + count > Definitions.StockCap then ready = false end end
            if not ready then continue end
            for id, count in def.input or {} do c.Stockpile[id] -= count end
            b.carry = b.carry or {}
            for id, count in def.output do
                local exact = count * (def.worker and c.Morale < 30 and 0.5 or 1) + (b.carry[id] or 0)
                local produced = math.floor(exact); b.carry[id] = exact - produced
                c.Stockpile[id] = (c.Stockpile[id] or 0) + produced
            end
        end
        return power
    end
    function Economy.farmIncome(c, now)
        -- Online-only payout: the service calls this at most once per monotonic second.
        -- No timestamps are converted into catch-up income when a save is loaded.
        local payouts = {}
        for _, b in c.Buildings do
            local def = Buildings[b.id]
            if def and def.cashPerSecond and b.readyAt <= now and b.hp > 0 then
                c.Cash += def.cashPerSecond
                table.insert(payouts, {uid = b.uid, amount = def.cashPerSecond})
            end
        end
        return payouts
    end
    function Economy.price(product, stock, reputation)
        local scarcity = 1 + 0.35 * (1 - math.clamp(stock / product.stock, 0, 1))
        return math.max(1, math.ceil(product.price * scarcity * (1 - math.min(0.15, reputation * 0.0015))))
    end
    function Economy.buy(c, product, stock, count)
        if not Util.integer(count, 1, 50) or count > stock then return false, "Нет такого количества в наличии" end
        local total = 0
        for i = 0, count - 1 do total += Economy.price(product, stock - i, c.Reputation.TRADERS) end
        if c.Cash < total then return false, "Недостаточно Survival Cash" end
        local ok, err = Inventory.grant(c, {[product.id] = count})
        if not ok then return false, err end
        c.Cash -= total; return true, stock - count
    end
    function Economy.craft(c, id, now)
        if c.Craft then return false, "Сначала заберите предыдущий заказ" end
        local def = Definitions.Recipes[id]
        if not def then return false, "Рецепт не найден" end
        local workshop = false
        for _, b in c.Buildings do if b.id == "workshop" and b.readyAt <= now then workshop = true end end
        if not workshop then return false, "Нужна построенная мастерская" end
        if not Inventory.pay(c, def.input) then return false, "Недостаточно материалов" end
        c.Craft = {id = id, readyAt = now + def.seconds}; return true
    end
    function Economy.collectCraft(c, now)
        if not c.Craft or c.Craft.readyAt > now then return false, "Заказ ещё не готов" end
        local ok, err = Inventory.grant(c, Definitions.Recipes[c.Craft.id].output)
        if not ok then return false, err end
        c.Craft = nil; return true
    end
    function Economy.cosmetic(p, id)
        local def = Definitions.Cosmetics[id]
        if not def then return false, "Раскраска не найдена" end
        if not p.Cosmetics[id] then
            if p.Coins < def.price then return false, "Недостаточно Coins" end
            p.Coins -= def.price; p.Cosmetics[id] = true
        end
        p.EquippedSkin = id; return true
    end
    return Economy
end
