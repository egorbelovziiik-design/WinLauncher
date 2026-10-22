--!nonstrict
return function(Config, Economy, Inventory)
    local Progression = {}
    function Progression.fact(c, key)
        if c.Tutorial.status == "Active" then c.Tutorial.facts[key] = true end
    end
    function Progression.stage(c)
        for index, step in Config.TutorialSteps do if not c.Tutorial.facts[step.key] then return index end end
        return #Config.TutorialSteps + 1
    end
    function Progression.tutorial(p, c)
        if c.Tutorial.status ~= "Active" or Progression.stage(c) <= #Config.TutorialSteps then return false, "Сначала выполните этапы обучения" end
        if not p.TutorialRewarded then
            local ok, err = Inventory.grant(c, {food = 3, water = 3, medkit = 1, ammo = 16})
            if not ok then return false, err end
            p.TutorialRewarded = true; p.Coins += 500; c.XP += 100
        end
        c.Tutorial.status = "Completed"; return true
    end
    function Progression.achievements(p)
        local awarded = {}
        for id, def in Economy.Achievements do
            if not p.Achievements[id] and (p.Stats[def.stat] or 0) >= def.count then
                p.Achievements[id] = true; p.Coins += def.coins; table.insert(awarded, def.name)
            end
        end
        return awarded
    end
    return Progression
end
