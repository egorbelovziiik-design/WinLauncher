--!nonstrict
return function(ctx)
    local Service = {}
    function Service:Remove(player)
        local s = ctx.Sessions[player]; if s then s.Reloading = nil end
    end
    function Service:SyncTool(player)
        local s = ctx.Sessions[player]; if not s or not s.Active then return end
        local char = player.Character; local backpack = player:FindFirstChildOfClass("Backpack")
        if not char or not backpack then return end
        for _, container in {char, backpack} do
            for _, tool in container:GetChildren() do if tool:IsA("Tool") and tool:GetAttribute("LastCityTool") then tool:Destroy() end end
        end
        local e = ctx.Inventory.find(s.Campaign, s.Campaign.Equipped)
        if not e or not ctx.Weapons[e.id] then return end
        local color
        local skin = ctx.EconomyDefs.Cosmetics[s.Data.EquippedSkin]
        if skin then color = Color3.fromRGB(table.unpack(skin.color)) end
        local tool = ctx.Factory.weapon(e.id, color)
        tool.Name = ctx.Items[e.id].name; tool:SetAttribute("LastCityTool", true); tool:SetAttribute("UID", e.uid); tool:SetAttribute("ItemId", e.id)
        tool.ToolTip = "ЛКМ / атака • R / перезарядка"; tool.Parent = backpack
        local h = char:FindFirstChildOfClass("Humanoid"); if h and h.Health > 0 then h:EquipTool(tool) end
    end
    function Service:Hurt(player, damage)
        local s = ctx.Sessions[player]; local h = ctx:Alive(player)
        if not s or not s.Active or not h then return end
        local armor = ctx.Inventory.find(s.Campaign, s.Campaign.Armor)
        if armor and (armor.durability or 0) > 0 then
            damage *= 1 - ctx.Items[armor.id].armor
            armor.durability = math.max(0, armor.durability - 0.5)
        end
        h:TakeDamage(damage)
    end
    function Service:Attack(player, s, aim)
        if typeof(aim) ~= "Vector3" or not ctx.Util.finite(aim.Magnitude) or aim.Magnitude < 0.5 or aim.Magnitude > 2 or s.Commander then return end
        if s.Reloading or (s.NextShot or 0) > os.clock() then return end
        local root = ctx:Root(player); local head = player.Character and player.Character:FindFirstChild("Head")
        if not root or not head then return end
        local entry = ctx.Inventory.find(s.Campaign, s.Campaign.Equipped)
        local def = entry and ctx.Weapons[entry.id]
        if not def then return end
        local tool = player.Character:FindFirstChildOfClass("Tool")
        if not tool or tool:GetAttribute("UID") ~= entry.uid then return end
        if entry.durability <= 0 then ctx:Notify(player, "Оружие сломано. Отремонтируйте его у Виктора.", "error"); return end
        if not def.melee and entry.ammo <= 0 then ctx:Notify(player, "Нет патронов в магазине. Нажмите R."); return end
        if def.melee and s.Stamina < 8 then return end
        s.NextShot = os.clock() + def.interval
        entry.durability = math.max(0, entry.durability - (def.melee and 0.12 or 0.06))
        if def.melee then s.Stamina -= 8 else entry.ammo -= 1 end
        ctx.Progression.fact(s.Campaign, "attack")
        aim = aim.Unit
        local damage = def.damage * (1 + (entry.upgrade or 0) * 0.06)
        local enemy, hitPos, damageHandled
        if def.melee then
            local best = def.range
            for _, zombie in ctx.Services.ZombieService.Zombies do
                if zombie.dead or zombie.humanoid.Health <= 0 then continue end
                local delta = zombie.model.PrimaryPart.Position - root.Position
                local flat = Vector3.new(delta.X, 0, delta.Z)
                local facing = Vector3.new(aim.X, 0, aim.Z)
                if delta.Magnitude < best and facing.Magnitude > 0.1 and (flat.Magnitude < 0.1 or flat.Unit:Dot(facing.Unit) > 0.2)
                    and ctx:LineOfSight(head.Position, zombie.model.PrimaryPart.Position, {player.Character, zombie.model}) then
                    best = delta.Magnitude; enemy = zombie; hitPos = zombie.model.PrimaryPart.Position
                end
            end
        else
            local rng = Random.new(); local hits = {}
            local spread = (1 - def.accuracy) * (1 - (entry.upgrade or 0) * 0.03)
            local params = RaycastParams.new(); params.FilterType = Enum.RaycastFilterType.Exclude; params.FilterDescendantsInstances = {player.Character}
            for pellet = 1, (def.pellets or 1) do
                local direction = (aim + Vector3.new(rng:NextNumber(-spread, spread), rng:NextNumber(-spread, spread), rng:NextNumber(-spread, spread))).Unit
                local result = workspace:Raycast(head.Position, direction * def.range, params)
                if pellet == 1 then hitPos = result and result.Position or head.Position + direction * def.range end
                local target = result and ctx.Services.ZombieService:FromPart(result.Instance)
                if target then enemy = enemy or target; hits[target] = (hits[target] or 0) + damage end
            end
            for target, total in hits do ctx.Services.ZombieService:Damage(target, total, player) end
            damageHandled = true
        end
        if enemy and not damageHandled then ctx.Services.ZombieService:Damage(enemy, damage, player) end
        ctx:FX("shot", {from = head.Position, to = hitPos or head.Position + aim * 5, melee = def.melee == true, hit = enemy ~= nil, user = player.UserId})
        ctx:Changed(player)
    end
    function Service:Reload(player, s)
        local c = s.Campaign; local entry = ctx.Inventory.find(c, c.Equipped); local def = entry and ctx.Weapons[entry.id]
        if not def or def.melee or s.Reloading or entry.ammo >= def.magazine then return end
        local ammoId = def.ammo or "ammo"
        if ctx.Inventory.count(c, ammoId) < 1 then ctx:Notify(player, "Нужны боеприпасы в рюкзаке."); return end
        local token = {}; local uid = entry.uid; s.Reloading = token
        ctx.Remotes.StateRemote:FireClient(player, "reload", def.reload)
        task.delay(def.reload, function()
            if ctx.Sessions[player] ~= s or s.Closing or s.Frozen or not s.Active or not ctx:Alive(player) or s.Reloading ~= token or c.Equipped ~= uid then return end
            s.Reloading = nil
            local current = ctx.Inventory.find(c, uid); if not current then return end
            local count = math.min(def.magazine - current.ammo, ctx.Inventory.count(c, ammoId))
            if count > 0 and ctx.Inventory.remove(c, ammoId, count) then current.ammo += count end
            ctx:Changed(player)
        end)
    end
    function Service:Init()
        ctx.Services.NetworkService:Register("CombatRemote", true, function(player, s, p)
            if p.action == "attack" then self:Attack(player, s, p.aim)
            elseif p.action == "reload" then self:Reload(player, s)
            elseif (p.action == "repair" or p.action == "upgrade") and ctx.Services.NPCService:Near(player, "victor") then
                local entry = ctx.Inventory.find(s.Campaign, p.uid)
                if not entry or not ctx.Weapons[entry.id] then return end
                local cash, cost
                if p.action == "repair" then
                    if entry.durability >= 100 then return end
                    cash = 20; cost = {metal = math.max(1, math.ceil((100 - entry.durability) / 25))}
                else
                    if entry.upgrade >= 10 then ctx:Notify(player, "Достигнут уровень улучшения 10."); return end
                    cash = 25 * (entry.upgrade + 1); cost = {metal = 2 + entry.upgrade, parts = 1 + math.floor(entry.upgrade / 3)}
                    if entry.upgrade >= 3 then cost.electronics = math.floor(entry.upgrade / 3) end
                end
                if s.Campaign.Cash < cash or not ctx.Inventory.pay(s.Campaign, cost) then ctx:Notify(player, "Недостаточно материалов или Survival Cash.", "error"); return end
                s.Campaign.Cash -= cash
                if p.action == "repair" then entry.durability = 100 else entry.upgrade += 1 end
                ctx:Notify(player, "Оружие обслужено.", "success"); ctx:Changed(player)
            end
        end)
    end
    return Service
end
