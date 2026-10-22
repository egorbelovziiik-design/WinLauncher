--!nonstrict
return function(ctx)
    local Service = {}
    function Service:Record(player, event, target, amount, unique)
        local s = ctx.Sessions[player]; if not s or not s.Active or s.Closing or s.Frozen then return end
        ctx.Quests.record(s.Campaign, event, target, amount, os.time(), unique)
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
