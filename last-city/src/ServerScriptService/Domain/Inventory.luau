--!strict
-- Engine-independent; all mutations are synchronous, bounded and transactional.
return function(Items: any, Weapons: any, Config: any, Util: any)
    local Inventory: any = {}
    function Inventory.weight(c: any): number
        local total = 0
        for _, entry in c.Inventory do total += Items[entry.id].weight * entry.count end
        return total
    end
    function Inventory.find(c: any, uid: any): (any, number?)
        for index, entry in c.Inventory do if entry.uid == uid then return entry, index end end
        return nil, nil
    end
    function Inventory.count(c: any, id: any): number
        local count = 0
        for _, entry in c.Inventory do if entry.id == id then count += entry.count end end
        return count
    end
    local function addUnsafe(c: any, id: any, count: any): (boolean, string?)
        local def = Items[id]
        if not def or not Util.integer(count, 1, 9999) then return false, "Неверный предмет" end
        if Inventory.weight(c) + count * def.weight > Config.CarryWeight + 0.001 then return false, "Рюкзак слишком тяжёлый" end
        if not def.weapon and not def.armor then
            for _, entry in c.Inventory do
                if entry.id == id and entry.count < def.stack then
                    local moved = math.min(count, def.stack - entry.count)
                    entry.count += moved; count -= moved
                    if count == 0 then return true, nil end
                end
            end
        end
        while count > 0 do
            if #c.Inventory >= Config.InventorySlots then return false, "В рюкзаке нет свободных ячеек" end
            c.NextItemId += 1
            local entry = {uid = "i" .. c.NextItemId, id = id, count = math.min(count, def.stack)}
            if def.weapon then entry.ammo = 0; entry.durability = 100; entry.upgrade = 0 end
            if def.armor then entry.durability = 100 end
            table.insert(c.Inventory, entry); count -= entry.count
        end
        return true, nil
    end
    function Inventory.grant(c: any, rewards: any, loadedWeapons: any?): (boolean, string?)
        -- Build the complete reward on a draft so ammunition, weapon state and slots
        -- are committed together (or not at all).
        local draft = {Inventory = Util.copy(c.Inventory), NextItemId = c.NextItemId}
        local ids = {}; for id in rewards do table.insert(ids, id) end; table.sort(ids)
        for _, id in ids do
            local ok, err = addUnsafe(draft, id, rewards[id])
            if not ok then return false, err end
        end
        for _, loaded in loadedWeapons or {} do
            local def = Weapons[loaded.id]
            if not def or not Util.integer(loaded.count or 1, 1, 10) or not Util.integer(loaded.ammo, 0, def.magazine) then
                return false, "Неверная награда оружием"
            end
            for _ = 1, (loaded.count or 1) do
                local ok, err = addUnsafe(draft, loaded.id, 1)
                if not ok then return false, err end
                local entry = Inventory.find(draft, "i" .. draft.NextItemId)
                if not entry then return false, "Не удалось подготовить оружие" end
                entry.ammo = loaded.ammo
            end
        end
        c.Inventory = draft.Inventory; c.NextItemId = draft.NextItemId
        return true, nil
    end
    function Inventory.remove(c: any, id: any, count: any): boolean
        if not Util.integer(count, 1, 9999) or Inventory.count(c, id) < count then return false end
        for index = #c.Inventory, 1, -1 do
            local entry = c.Inventory[index]
            if entry.id == id then
                local taken = math.min(count, entry.count); entry.count -= taken; count -= taken
                if entry.count == 0 then
                    if c.Equipped == entry.uid then c.Equipped = "" end
                    if c.Armor == entry.uid then c.Armor = "" end
                    table.remove(c.Inventory, index)
                end
                if count == 0 then return true end
            end
        end
        return true
    end
    function Inventory.takeUid(c: any, uid: any, count: any): boolean
        local entry, index = Inventory.find(c, uid)
        if not entry or not Util.integer(count, 1, entry.count) then return false end
        entry.count -= count
        if entry.count == 0 then
            if c.Equipped == uid then c.Equipped = "" end
            if c.Armor == uid then c.Armor = "" end
            table.remove(c.Inventory, index)
        end
        return true
    end
    function Inventory.split(c: any, uid: any, count: any): (boolean, string?)
        local entry = Inventory.find(c, uid)
        if not entry or not Util.integer(count, 1, entry.count - 1) or #c.Inventory >= Config.InventorySlots then return false, "Невозможно разделить стопку" end
        c.NextItemId += 1; entry.count -= count
        table.insert(c.Inventory, {uid = "i" .. c.NextItemId, id = entry.id, count = count})
        return true, nil
    end
    function Inventory.move(c: any, uid: any, index: any): boolean
        local entry, old = Inventory.find(c, uid)
        if not entry or not Util.integer(index, 1, #c.Inventory) then return false end
        table.remove(c.Inventory, old); table.insert(c.Inventory, index, entry); return true
    end
    function Inventory.equip(c: any, uid: any): (boolean, string?)
        local entry = Inventory.find(c, uid)
        if not entry then return false, "Предмет не найден" end
        if Weapons[entry.id] then
            if Util.level(c.XP) < Weapons[entry.id].level then return false, "Нужен более высокий уровень" end
            c.Equipped = uid; return true, nil
        elseif Items[entry.id].armor then c.Armor = uid; return true, nil end
        return false, "Этот предмет нельзя экипировать"
    end
    function Inventory.afford(c: any, cost: any): boolean
        for id, count in cost do
            if (c.Stockpile[id] or 0) + Inventory.count(c, id) < count then return false end
        end
        return true
    end
    function Inventory.pay(c: any, cost: any): boolean
        if not Inventory.afford(c, cost) then return false end
        for id, count in cost do
            local stock = math.min(count, c.Stockpile[id] or 0)
            c.Stockpile[id] = (c.Stockpile[id] or 0) - stock
            if count > stock then Inventory.remove(c, id, count - stock) end
        end
        return true
    end
    function Inventory.transfer(c: any, id: any, count: any, deposit: any): (boolean, string?)
        if not Items[id] or Items[id].weapon or Items[id].armor or Items[id].blueprint or not Util.integer(count, 1, 999) then return false, "Нельзя переместить" end
        if deposit then
            if (c.Stockpile[id] or 0) + count > 9999 or not Inventory.remove(c, id, count) then return false, "Недостаточно предметов / склад полон" end
            c.Stockpile[id] = (c.Stockpile[id] or 0) + count
        else
            if (c.Stockpile[id] or 0) < count then return false, "На складе не хватает предметов" end
            local ok, err = Inventory.grant(c, {[id] = count})
            if not ok then return false, err end
            c.Stockpile[id] -= count
        end
        return true, nil
    end
    return Inventory
end
