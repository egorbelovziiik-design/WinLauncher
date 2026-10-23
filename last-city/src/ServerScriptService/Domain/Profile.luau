--!nonstrict
return function(Config, Items, Weapons, Buildings, Quests, Economy, Util, Inventory)
    local Profile = {}
    local function number(value, default, low, high)
        if not Util.finite(value) then return default end
        return math.clamp(math.floor(value), low, high)
    end
    local function flags(value, allowed)
        local result = {}
        if type(value) == "table" then
            for key in allowed do if value[key] == true then result[key] = true end end
        end
        return result
    end
    function Profile.campaign()
        local c = {
            Started = false, XP = 0, Cash = 200, NextItemId = 0, Inventory = {}, Equipped = "", Armor = "", QuickSlots = table.create(Config.QuickSlots, ""),
            Stockpile = {wood = 60, metal = 30, steel_plate = 0, concrete = 0, glass = 0, wire = 12, electronics = 8,
                fuel = 10, scrap = 8, food = 8, water = 8, ammo = 24, rifle_ammo = 0, shells = 0},
            Quests = {}, Buildings = {}, NextBuildingId = 0, Hunger = 100, Thirst = 100,
            Tutorial = {status = "New", facts = {}}, Reputation = {SURVIVORS = 0, TRADERS = 0},
            Workers = {{name = "Мира", job = ""}, {name = "Лев", job = ""}}, Morale = 80,
            Builder = {name = "Степан", order = ""},
            Squad = {command = "base", resource = "metal", returnAt = 0, expedition = 0, completed = 0},
            Recruits = {}, BossPity = {rainbowArmor = 0, rainbowWeapon = 0},
            Stats = {kills = 0, quests = 0, buildings = 0, seconds = 0},
        }
        assert(Inventory.grant(c, {crowbar = 1, food = 3, water = 3, medkit = 2}))
        local crowbar
        for _, entry in c.Inventory do if entry.id == "crowbar" then crowbar = entry; break end end
        assert(crowbar); c.QuickSlots[1] = crowbar.uid
        -- Start with the tool stowed: keyboard 1 remains an explicit toggle.
        return c
    end
    function Profile.new()
        return {
            SchemaVersion = Config.SchemaVersion, Revision = 0, Coins = 0, TutorialRewarded = false,
            Cosmetics = {}, EquippedSkin = "", Achievements = {},
            Settings = {graphics = "High", uiSound = 0.45, music = 0.32, effects = 0.6, voice = 0.75, sensitivity = 1, scale = 1, difficulty = "Normal", firstPerson = false},
            Stats = {kills = 0, quests = 0, buildings = 0, seconds = 0},
            Campaigns = {coop = Profile.campaign(), solo = Profile.campaign()},
        }
    end
    local function stats(raw)
        raw = type(raw) == "table" and raw or {}
        return {kills = number(raw.kills, 0, 0, 1e9), quests = number(raw.quests, 0, 0, 1e9),
            buildings = number(raw.buildings, 0, 0, 1e9), seconds = number(raw.seconds, 0, 0, 1e10)}
    end
    local function campaign(raw)
        if type(raw) ~= "table" then return Profile.campaign() end
        local c = Profile.campaign()
        c.Started = raw.Started == true; c.XP = number(raw.XP, 0, 0, 1e9); c.Cash = number(raw.Cash, 200, 0, 1e9)
        c.Hunger = number(raw.Hunger, 100, 0, 100); c.Thirst = number(raw.Thirst, 100, 0, 100)
        c.Morale = number(raw.Morale, 80, 0, 100); c.Stats = stats(raw.Stats)
        c.NextItemId = number(raw.NextItemId, 0, 0, 1e12)
        if type(raw.Inventory) == "table" then
            c.Inventory = {}; local seen = {}
            for _, entry in raw.Inventory do
                if #c.Inventory >= Config.InventorySlots then break end
                if type(entry) == "table" and type(entry.uid) == "string" and #entry.uid < 24 and entry.uid:match("^i%d+$") and not seen[entry.uid] and Items[entry.id] then
                    local e = {uid = entry.uid, id = entry.id, count = number(entry.count, 1, 1, Items[entry.id].stack)}
                    if Items[e.id].weapon or Items[e.id].armor then e.durability = number(entry.durability, 100, 0, 100) end
                    if Weapons[e.id] then e.ammo = number(entry.ammo, 0, 0, Weapons[e.id].magazine); e.upgrade = number(entry.upgrade, 0, 0, 10) end
                    table.insert(c.Inventory, e); seen[e.uid] = true
                    c.NextItemId = math.max(c.NextItemId, tonumber(e.uid:sub(2)) or 0)
                end
            end
        end
        c.Equipped = ""; c.Armor = ""
        if type(raw.Equipped) == "string" then Inventory.equip(c, raw.Equipped) end
        if type(raw.Armor) == "string" then
            local e = Inventory.find(c, raw.Armor); if e and Items[e.id].armor then c.Armor = e.uid end
        end
        c.QuickSlots = table.create(Config.QuickSlots, "")
        local slotted = {}
        if type(raw.QuickSlots) == "table" then
            for index = 1, Config.QuickSlots do
                local uid = raw.QuickSlots[index]
                local entry = type(uid) == "string" and Inventory.find(c, uid) or nil
                local def = entry and Items[entry.id]
                if entry and not slotted[uid] and (Weapons[entry.id] or def.armor or def.use) then
                    c.QuickSlots[index] = uid; slotted[uid] = true
                end
            end
        end
        local crowbar
        for _, entry in c.Inventory do if entry.id == "crowbar" then crowbar = entry; break end end
        if crowbar then
            local displaced = c.QuickSlots[1]
            for index = 1, Config.QuickSlots do if c.QuickSlots[index] == crowbar.uid then c.QuickSlots[index] = "" end end
            c.QuickSlots[1] = crowbar.uid
            if displaced ~= "" and displaced ~= crowbar.uid and not table.find(c.QuickSlots, displaced) then
                for index = 2, Config.QuickSlots do if c.QuickSlots[index] == "" then c.QuickSlots[index] = displaced; break end end
            end
        end
        if c.Equipped ~= "" then Inventory.assignQuick(c, c.Equipped) end
        if c.Armor ~= "" then Inventory.assignQuick(c, c.Armor) end
        if type(raw.Stockpile) == "table" then
            c.Stockpile = {}
            for id, def in Items do
                if not def.weapon and not def.armor and not def.blueprint then c.Stockpile[id] = number(raw.Stockpile[id], 0, 0, 9999) end
            end
        end
        if type(raw.Quests) == "table" then
            for id, def in Quests do
                local q = raw.Quests[id]
                if type(q) == "table" and table.find({"Active", "Completed", "RewardClaimed", "Failed"}, q.status) then
                    local entry = {status = q.status, progress = {}, seen = {}, period = number(q.period, 0, 0, 1e9)}
                    for index, objective in def.objectives do entry.progress[index] = number(type(q.progress) == "table" and q.progress[index], 0, 0, objective.count) end
                    if type(q.seen) == "table" then
                        local n = 0
                        for key, value in q.seen do
                            if type(key) == "string" and #key <= 96 and value == true and n < 32 then entry.seen[key] = true; n += 1 end
                        end
                    end
                    c.Quests[id] = entry
                end
            end
        end
        c.NextBuildingId = number(raw.NextBuildingId, 0, 0, 1e12)
        if type(raw.Buildings) == "table" then
            local seen = {}
            for _, b in raw.Buildings do
                if #c.Buildings >= Config.MaxBuildings then break end
                if type(b) == "table" and Buildings[b.id] and type(b.uid) == "string" and b.uid:match("^b%d+$") and #b.uid < 24 and not seen[b.uid] and Util.integer(b.x, -48, 48) and Util.integer(b.z, -48, 48) then
                    local record = {uid = b.uid, id = b.id, x = b.x, z = b.z, r = number(b.r, 0, 0, 3),
                        hp = number(b.hp, Buildings[b.id].health, 1, Buildings[b.id].health), readyAt = number(b.readyAt, 0, 0, 1e11),
                        fuelUntil = number(b.fuelUntil, 0, 0, 1e11), carry = {}}
                    if type(b.carry) == "table" then
                        for item in Buildings[b.id].output or {} do
                            if Util.finite(b.carry[item]) then record.carry[item] = math.clamp(b.carry[item], 0, 0.999999) end
                        end
                    end
                    table.insert(c.Buildings, record); seen[b.uid] = true
                    c.NextBuildingId = math.max(c.NextBuildingId, tonumber(b.uid:sub(2)) or 0)
                end
            end
            if type(raw.Workers) == "table" then
                for index, w in c.Workers do
                    local old = raw.Workers[index]
                    if type(old) == "table" and type(old.job) == "string" and seen[old.job] then w.job = old.job end
                end
            end
        end
        if type(raw.Builder) == "table" and type(raw.Builder.order) == "string" then
            for _, b in c.Buildings do if b.uid == raw.Builder.order then c.Builder.order = b.uid; break end end
        end
        if type(raw.Squad) == "table" then
            if table.find({"base", "follow", "gather"}, raw.Squad.command) then c.Squad.command = raw.Squad.command end
            if table.find({"metal", "mine", "wood"}, raw.Squad.resource) then c.Squad.resource = raw.Squad.resource end
            c.Squad.returnAt = number(raw.Squad.returnAt, 0, 0, 1e11)
            c.Squad.expedition = number(raw.Squad.expedition, 0, 0, 1e9)
            c.Squad.completed = math.min(c.Squad.expedition, number(raw.Squad.completed, 0, 0, 1e9))
            if c.Squad.command == "gather" and (c.Squad.returnAt <= 0 or c.Squad.expedition <= c.Squad.completed) then
                c.Squad.command = "base"
            end
            if c.Squad.command ~= "gather" then c.Squad.returnAt = 0 end
        end
        if type(raw.Recruits) == "table" then
            for _, id in {"builder", "medic", "guard", "scout", "gatherer"} do
                local old = raw.Recruits[id]
                if type(old) == "table" and old.owned == true then
                    local record = {owned = true, command = "base", resource = "metal", returnAt = 0, expedition = 0, completed = 0}
                    if table.find({"base", "follow", "gather"}, old.command) then record.command = old.command end
                    if id ~= "gatherer" and record.command == "gather" then record.command = "base" end
                    if table.find({"metal", "mine", "wood"}, old.resource) then record.resource = old.resource end
                    record.returnAt = number(old.returnAt, 0, 0, 1e11)
                    record.expedition = number(old.expedition, 0, 0, 1e9)
                    record.completed = math.min(record.expedition, number(old.completed, 0, 0, 1e9))
                    if record.command == "gather" and (record.returnAt <= 0 or record.expedition <= record.completed) then record.command = "base" end
                    if record.command ~= "gather" then record.returnAt = 0 end
                    c.Recruits[id] = record
                end
            end
        end
        if type(raw.BossPity) == "table" then
            c.BossPity.rainbowArmor = number(raw.BossPity.rainbowArmor, 0, 0, 3)
            c.BossPity.rainbowWeapon = number(raw.BossPity.rainbowWeapon, 0, 0, 2)
        end
        if type(raw.Tutorial) == "table" then
            local allowed = {}; for _, step in Config.TutorialSteps do allowed[step.key] = true end
            c.Tutorial.facts = flags(raw.Tutorial.facts, allowed)
            if table.find({"New", "Active", "Skipped", "Completed"}, raw.Tutorial.status) then c.Tutorial.status = raw.Tutorial.status end
        end
        if type(raw.Reputation) == "table" then
            for faction in c.Reputation do c.Reputation[faction] = number(raw.Reputation[faction], 0, 0, 100) end
        end
        if type(raw.Craft) == "table" and Economy.Recipes[raw.Craft.id] then
            c.Craft = {id = raw.Craft.id, readyAt = number(raw.Craft.readyAt, 0, 0, 1e11)}
        end
        return c
    end
    function Profile.normalize(raw)
        if raw == nil then return Profile.new() end
        if type(raw) ~= "table" or not Util.integer(raw.SchemaVersion, 1, Config.SchemaVersion) then
            return nil, "Неподдерживаемая версия или повреждённое сохранение"
        end
        local p = Profile.new()
        p.Revision = number(raw.Revision, 0, 0, 1e12); p.Coins = number(raw.Coins, 0, 0, 1e9)
        p.TutorialRewarded = raw.TutorialRewarded == true; p.Stats = stats(raw.Stats)
        p.Cosmetics = flags(raw.Cosmetics, Economy.Cosmetics); p.Achievements = flags(raw.Achievements, Economy.Achievements)
        if p.Cosmetics[raw.EquippedSkin] then p.EquippedSkin = raw.EquippedSkin end
        if type(raw.Settings) == "table" then
            local s = raw.Settings
            if table.find({"Low", "Medium", "High", "Ultra"}, s.graphics) then p.Settings.graphics = s.graphics end
            if table.find({"Relaxed", "Normal", "Hard"}, s.difficulty) then p.Settings.difficulty = s.difficulty end
            for key, bounds in {uiSound = {0, 1}, music = {0, 1}, effects = {0, 1}, voice = {0, 1}, sensitivity = {0.3, 2}, scale = {0.75, 1.15}} do
                if Util.finite(s[key]) then p.Settings[key] = math.clamp(s[key], bounds[1], bounds[2]) end
            end
            if type(s.firstPerson) == "boolean" then p.Settings.firstPerson = s.firstPerson end
        end
        if type(raw.Campaigns) == "table" then
            p.Campaigns.coop = campaign(raw.Campaigns.coop); p.Campaigns.solo = campaign(raw.Campaigns.solo)
        end
        return p
    end
    return Profile
end
