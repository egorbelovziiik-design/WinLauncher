--!nonstrict
return function(ctx)
    local Service = {}
    function Service:Record(player, event, target, amount, unique)
        local s = ctx.Sessions[player]; if not s or not s.Active or s.Closing or s.Frozen then return end
        local completed = ctx.Quests.record(s.Campaign, event, target, amount, os.time(), unique)
        for _, id in completed do
            local def = ctx.QuestDefs[id]
            if def and def.autoClaim then
                local ok, err = ctx.Quests.claim(s.Data, s.Campaign, id, os.time())
                if ok then
                    ctx.Progression.fact(s.Campaign, "reward")
                    ctx.Services.CombatService:SyncTool(player)
                    ctx:Notify(player, "Босс повержен! Glock 10/10 и 48 патронов добавлены в рюкзак.", "success")
                else
                    -- Atomic rewards remain claimable at the quest resident if the backpack is full.
                    ctx:Notify(player, "Освободите место и заберите награду у Иры: " .. tostring(err), "error")
                end
            end
        end
        ctx:Changed(player)
    end
    function Service:Init()
        ctx.Services.NetworkService:Register("QuestRemote", true, function(player, s, p)
            local def = ctx.QuestDefs[p.id]
            if not def or not ctx.Services.NPCService:Near(player, def.npc) then ctx:Notify(player, "Вернитесь к нужному NPC.", "error"); return end
            local ok, err
            if p.action == "accept" then
                ok, err = ctx.Quests.accept(s.Campaign, p.id, os.time())
                if ok then ctx.Progression.fact(s.Campaign, "quest") end
            elseif p.action == "claim" then
                ok, err = ctx.Quests.claim(s.Data, s.Campaign, p.id, os.time())
                if ok then ctx.Progression.fact(s.Campaign, "reward"); ctx.Services.CombatService:SyncTool(player) end
            else return end
            ctx:Notify(player, ok and (p.action == "claim" and "Награда получена. Откройте рюкзак: I." or "Задание принято. Журнал: J.") or err, ok and "success" or "error")
            ctx:Changed(player)
        end)
    end
    return Service
end
