--!nonstrict
return function(Definitions, Inventory, Util)
    local Quests = {}
    local function current(c, id, now)
        local def, state = Definitions[id], c.Quests[id]
        if state and def.period and state.period ~= Util.day(now, def.period) then c.Quests[id] = nil; return nil end
        return state
    end
    function Quests.accept(c, id, now)
        local def = Definitions[id]
        if not def then return false, "Задание не найдено" end
        if current(c, id, now) then return false, "Задание уже принято" end
        if def.requires and (not c.Quests[def.requires] or c.Quests[def.requires].status ~= "RewardClaimed") then return false, "Сначала завершите предыдущее задание" end
        local q = {status = "Active", progress = {}, seen = {}, period = def.period and Util.day(now, def.period) or 0}
        for index in def.objectives do q.progress[index] = 0 end
        c.Quests[id] = q
        return true
    end
    function Quests.record(c, event, target, amount, now, unique)
        local completed = {}
        if not Util.integer(amount, 1, 999) then return completed end
        for id, def in Definitions do
            local q = current(c, id, now)
            if not q or q.status ~= "Active" then continue end
            local key = unique and (event .. ":" .. unique)
            if key and q.seen[key] then continue end
            local changed = false
            for index, objective in def.objectives do
                if objective.event == event and (objective.target == target or objective.target == "*") then
                    q.progress[index] = math.min(objective.count, q.progress[index] + amount); changed = true
                end
            end
            if key and changed then q.seen[key] = true end
            local complete = true
            for index, objective in def.objectives do if q.progress[index] < objective.count then complete = false end end
            if complete then q.status = "Completed"; table.insert(completed, id) end
        end
        return completed
    end
    function Quests.claim(p, c, id, now)
        if not Definitions[id] then return false, "Задание не найдено" end
        local q = current(c, id, now)
        if not q or q.status ~= "Completed" then return false, "Награда недоступна" end
        local def = Definitions[id]
        for index, objective in def.objectives do
            if q.progress[index] < objective.count then return false, "Цели не выполнены" end
        end
        -- grant is atomic. A full backpack leaves quest, currencies and XP untouched.
        local ok, err = Inventory.grant(c, def.reward.items, def.reward.loadedWeapons)
        if not ok then return false, err end
        q.status = "RewardClaimed"
        p.Coins += def.reward.coins; c.XP += def.reward.xp
        c.Stats.quests += 1; p.Stats.quests += 1
        c.Reputation[def.reputation] = math.min(100, (c.Reputation[def.reputation] or 0) + 5)
        return true
    end
    function Quests.refresh(c, now)
        for id in Definitions do current(c, id, now) end
    end
    return Quests
end
