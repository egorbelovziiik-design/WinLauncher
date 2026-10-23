--!nonstrict
return function(ctx)
    local Debris = game:GetService("Debris")
    local Service = {}
    function Service:Remove(player)
        local s = ctx.Sessions[player]; if s then s.Reloading = nil end
    end

    function Service:SyncArmor(player, s, char)
        for _, node in char:GetChildren() do if node:GetAttribute("LastCityArmor") then node:Destroy() end end
        local entry = ctx.Inventory.find(s.Campaign, s.Campaign.Armor)
        local item = entry and ctx.Items[entry.id]
        if not entry or not item or not item.armor or (entry.durability or 0) <= 0 then return end
        local torso = char:FindFirstChild("UpperTorso") or char:FindFirstChild("Torso")
        if not torso then return end
        local tint = Color3.fromHex(item.color)
        local function plate(name, body, size, offset, color, material)
            if not body then return end
            local p = Instance.new("Part"); p.Name = name; p.Size = size; p.CFrame = body.CFrame * CFrame.new(offset)
            p.Color = color or tint; p.Material = material or Enum.Material.Fabric; p.CanCollide = false; p.CanTouch = false
            p.CanQuery = false; p.Massless = true; p:SetAttribute("LastCityArmor", true); p.Parent = char
            local weld = Instance.new("WeldConstraint"); weld.Part0 = body; weld.Part1 = p; weld.Parent = p
            return p
        end
        plate("ArmorChest", torso, Vector3.new(2.15, 1.35, 0.27), Vector3.new(0, 0.05, -0.57))
        plate("ArmorBack", torso, Vector3.new(1.85, 1.15, 0.24), Vector3.new(0, 0.02, 0.57), tint)
        plate("ArmorBelt", torso, Vector3.new(2.08, 0.25, 1.08), Vector3.new(0, -0.67, 0), Color3.fromRGB(45, 51, 49), Enum.Material.Fabric)
        if entry.id == "heavy_vest" or entry.id == "riot_armor" or entry.id == "military_vest" or entry.id == "scavenger_armor" or entry.id == "prism_armor"
            or item.armorStyle == "frontier" or item.armorStyle == "bastion" or item.armorStyle == "demolisher" or item.armorStyle == "storm" or item.armorStyle == "ranger_elite" then
            plate("ArmorShoulders", torso, Vector3.new(3, 0.3, 0.9), Vector3.new(0, 0.7, 0), tint, Enum.Material.Metal)
        end
        if entry.id == "hazmat_suit" then
            local head = char:FindFirstChild("Head")
            plate("HazmatMask", head, Vector3.new(1.45, 0.72, 0.14), Vector3.new(0, -0.08, -0.77), Color3.fromRGB(57, 76, 69), Enum.Material.Glass)
            plate("HazmatFilter", head, Vector3.new(0.48, 0.48, 0.3), Vector3.new(0, -0.25, -0.86), Color3.fromRGB(48, 55, 52), Enum.Material.Metal)
        elseif entry.id == "medic_vest" then
            plate("MedicalMark", torso, Vector3.new(0.42, 0.42, 0.08), Vector3.new(-0.55, 0.16, -0.75), Color3.fromRGB(188, 61, 55), Enum.Material.Neon)
        elseif entry.id == "scout_vest" then
            plate("ScoutPack", torso, Vector3.new(1.45, 1.55, 0.48), Vector3.new(0, 0, 0.72), Color3.fromRGB(62, 75, 52), Enum.Material.Fabric)
        elseif entry.id == "prism_armor" then
            plate("PrismBandA", torso, Vector3.new(1.75, 0.18, 0.1), Vector3.new(0, 0.35, -0.76), Color3.fromRGB(76, 229, 255), Enum.Material.Neon)
            plate("PrismBandB", torso, Vector3.new(1.75, 0.18, 0.1), Vector3.new(0, -0.15, -0.76), Color3.fromRGB(255, 208, 76), Enum.Material.Neon)
        elseif item.armorStyle == "courier" then
            plate("CourierStripe", torso, Vector3.new(1.75, 0.16, 0.09), Vector3.new(0, 0.18, -0.76), Color3.fromRGB(255, 188, 73), Enum.Material.Neon)
            plate("CourierPack", torso, Vector3.new(1.35, 1.2, 0.4), Vector3.new(0, -0.02, 0.72), Color3.fromRGB(54, 61, 59), Enum.Material.Fabric)
        elseif item.armorStyle == "engineer" then
            plate("EngineerCollar", torso, Vector3.new(2.35, 0.28, 0.72), Vector3.new(0, 0.67, 0), Color3.fromRGB(76, 92, 92), Enum.Material.Metal)
            plate("EngineerPowerPack", torso, Vector3.new(1.45, 1.55, 0.5), Vector3.new(0, 0, 0.76), Color3.fromRGB(50, 66, 68), Enum.Material.DiamondPlate)
            plate("EngineerCell", torso, Vector3.new(0.32, 0.9, 0.12), Vector3.new(0.48, 0, -0.77), Color3.fromRGB(95, 226, 213), Enum.Material.Neon)
        elseif item.armorStyle == "frontier" then
            local head = char:FindFirstChild("Head")
            plate("FrontierHelmet", head, Vector3.new(1.65, 0.38, 1.55), Vector3.new(0, 0.52, 0), tint, Enum.Material.Metal)
            plate("FrontierVisor", head, Vector3.new(1.35, 0.28, 0.12), Vector3.new(0, 0.18, -0.78), Color3.fromRGB(255, 156, 78), Enum.Material.Glass)
            plate("FrontierMark", torso, Vector3.new(0.25, 0.75, 0.09), Vector3.new(-0.62, 0.1, -0.76), Color3.fromRGB(244, 180, 79), Enum.Material.Neon)
        elseif item.armorStyle == "ranger" then
            local head = char:FindFirstChild("Head")
            plate("RangerHood", head, Vector3.new(1.68, 0.52, 1.55), Vector3.new(0, 0.38, 0.08), Color3.fromRGB(52, 72, 57), Enum.Material.Fabric)
            plate("RangerPack", torso, Vector3.new(1.35, 1.42, 0.48), Vector3.new(0, -0.02, 0.74), Color3.fromRGB(62, 72, 54), Enum.Material.Fabric)
            plate("RangerMark", torso, Vector3.new(0.5, 0.18, 0.09), Vector3.new(-0.5, 0.22, -0.76), Color3.fromRGB(135, 190, 99), Enum.Material.Neon)
        elseif item.armorStyle == "ranger_elite" then
            local head = char:FindFirstChild("Head")
            plate("EliteRangerBeret", head, Vector3.new(1.7, 0.3, 1.52), Vector3.new(0, 0.52, 0), Color3.fromRGB(52, 78, 48), Enum.Material.Fabric)
            plate("EliteRangerVisor", head, Vector3.new(1.22, 0.18, 0.11), Vector3.new(0, 0.12, -0.8), Color3.fromRGB(146, 221, 115), Enum.Material.Neon)
            plate("EliteRangerPack", torso, Vector3.new(1.48, 1.48, 0.5), Vector3.new(0, -0.02, 0.75), Color3.fromRGB(51, 68, 50), Enum.Material.Fabric)
            plate("EliteRangerMark", torso, Vector3.new(0.72, 0.16, 0.09), Vector3.new(-0.4, 0.22, -0.76), Color3.fromRGB(151, 214, 93), Enum.Material.Neon)
        elseif item.armorStyle == "storm" then
            local head = char:FindFirstChild("Head")
            plate("StormHelmet", head, Vector3.new(1.74, 0.55, 1.6), Vector3.new(0, 0.4, 0), tint, Enum.Material.Metal)
            plate("StormVisor", head, Vector3.new(1.34, 0.24, 0.12), Vector3.new(0, 0.12, -0.82), Color3.fromRGB(91, 219, 247), Enum.Material.Neon)
            plate("StormCoilLeft", torso, Vector3.new(0.22, 1.1, 0.18), Vector3.new(-0.72, 0, -0.76), Color3.fromRGB(91, 219, 247), Enum.Material.Neon)
            plate("StormCoilRight", torso, Vector3.new(0.22, 1.1, 0.18), Vector3.new(0.72, 0, -0.76), Color3.fromRGB(91, 219, 247), Enum.Material.Neon)
            plate("StormPowerPack", torso, Vector3.new(1.4, 1.45, 0.52), Vector3.new(0, 0, 0.76), Color3.fromRGB(44, 67, 75), Enum.Material.DiamondPlate)
        elseif item.armorStyle == "demolisher" then
            local head = char:FindFirstChild("Head")
            plate("DemolisherHelmet", head, Vector3.new(1.72, 0.55, 1.58), Vector3.new(0, 0.4, 0), tint, Enum.Material.Metal)
            plate("DemolisherVisor", head, Vector3.new(1.3, 0.27, 0.12), Vector3.new(0, 0.12, -0.8), Color3.fromRGB(242, 181, 80), Enum.Material.Glass)
            plate("DemolisherCollar", torso, Vector3.new(2.45, 0.32, 0.8), Vector3.new(0, 0.65, 0), Color3.fromRGB(83, 67, 49), Enum.Material.Metal)
            plate("DemolisherMark", torso, Vector3.new(0.62, 0.18, 0.09), Vector3.new(0.45, 0.2, -0.76), Color3.fromRGB(255, 170, 55), Enum.Material.Neon)
        elseif item.armorStyle == "bastion" then
            local head = char:FindFirstChild("Head")
            local leftArm = char:FindFirstChild("LeftUpperArm") or char:FindFirstChild("Left Arm")
            local rightArm = char:FindFirstChild("RightUpperArm") or char:FindFirstChild("Right Arm")
            local leftLeg = char:FindFirstChild("LeftUpperLeg") or char:FindFirstChild("Left Leg")
            local rightLeg = char:FindFirstChild("RightUpperLeg") or char:FindFirstChild("Right Leg")
            plate("BastionHelmet", head, Vector3.new(1.78, 0.65, 1.65), Vector3.new(0, 0.35, 0), tint, Enum.Material.DiamondPlate)
            plate("BastionVisor", head, Vector3.new(1.4, 0.25, 0.13), Vector3.new(0, 0.13, -0.84), Color3.fromRGB(103, 212, 222), Enum.Material.Neon)
            plate("BastionLeftArm", leftArm, Vector3.new(0.82, 0.9, 0.38), Vector3.new(0, 0.08, -0.48), tint, Enum.Material.Metal)
            plate("BastionRightArm", rightArm, Vector3.new(0.82, 0.9, 0.38), Vector3.new(0, 0.08, -0.48), tint, Enum.Material.Metal)
            plate("BastionLeftLeg", leftLeg, Vector3.new(0.78, 1.05, 0.34), Vector3.new(0, -0.1, -0.48), tint, Enum.Material.Metal)
            plate("BastionRightLeg", rightLeg, Vector3.new(0.78, 1.05, 0.34), Vector3.new(0, -0.1, -0.48), tint, Enum.Material.Metal)
        end
    end

    function Service:SyncTool(player)
        local s = ctx.Sessions[player]; if not s or not s.Active then return end
        local char = player.Character; local backpack = player:FindFirstChildOfClass("Backpack")
        if not char or not backpack then return end
        self:SyncArmor(player, s, char)
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

    function Service:Hurt(player, damage, damageType)
        local s = ctx.Sessions[player]; local h = ctx:Alive(player)
        if not s or not s.Active or not h then return end
        local raw = damage
        local armor = ctx.Inventory.find(s.Campaign, s.Campaign.Armor)
        if armor and (armor.durability or 0) > 0 then
            local item = ctx.Items[armor.id]
            if damageType == "acid" then damage *= 1 - (item.acidResistance or 0)
            elseif damageType == "grenade" then damage *= 1 - (item.explosiveResistance or 0)
            elseif damageType == "electric" then damage *= 1 - (item.electricResistance or 0) end
            damage *= 1 - item.armor
            local before = armor.durability
            armor.durability = math.max(0, armor.durability - (damageType == "acid" and 0.8 or damageType == "grenade" and 1.2 or damageType == "electric" and 0.7 or 0.5))
            if before > 0 and armor.durability <= 0 then
                ctx:Notify(player, "Броня сломана. Замените или отремонтируйте её.", "error")
                self:SyncArmor(player, s, player.Character)
            end
        end
        h:TakeDamage(damage)
        ctx.Remotes.EffectsRemote:FireClient(player, "player_hurt", {damage = damage, absorbed = math.max(0, raw - damage), damageType = damageType})
    end

    function Service:GrenadePulse(owner, definition, position, damage, stunSeconds)
        local zombies = ctx.Services.ZombieService and ctx.Services.ZombieService.Zombies or {}
        for _, zombie in zombies do
            local root = zombie.model and zombie.model.PrimaryPart
            if zombie.dead or not root or not zombie.model.Parent or zombie.humanoid.Health <= 0 then continue end
            local distance = (root.Position - position).Magnitude
            if distance > definition.radius or not ctx:LineOfSight(position + Vector3.new(0, 0.4, 0), root.Position, {zombie.model}) then continue end
            local falloff = 0.35 + 0.65 * (1 - distance / definition.radius)
            local applied, killed = ctx.Services.ZombieService:Damage(zombie, damage * falloff, owner)
            if stunSeconds and stunSeconds > 0 then zombie.stunnedUntil = math.max(zombie.stunnedUntil or 0, os.clock() + stunSeconds) end
            local session = ctx.Sessions[owner]
            if session and session.Active and type(applied) == "number" then
                ctx.Remotes.EffectsRemote:FireClient(owner, "hit_confirm", {
                    damage = applied, killed = killed == true, critical = false, armored = false, position = root.Position,
                })
            end
        end
    end

    function Service:DetonateGrenade(owner, definition, position)
        self:GrenadePulse(owner, definition, position, definition.damage, definition.stunSeconds)
        local root = ctx:Root(owner)
        if definition.selfDamage > 0 and root then
            local distance = (root.Position - position).Magnitude
            if distance <= definition.radius and ctx:LineOfSight(position + Vector3.new(0, 0.4, 0), root.Position, {owner.Character}) then
                local falloff = 0.35 + 0.65 * (1 - distance / definition.radius)
                self:Hurt(owner, definition.damage * falloff * definition.selfDamage, "grenade")
            end
        end
        ctx:FX("grenade_explosion", {from = position, radius = definition.radius, incendiary = definition.burnDamage ~= nil, concussion = definition.stunSeconds ~= nil})
        if definition.burnDamage and definition.burnTicks then
            local function burn(tick)
                if tick > definition.burnTicks then return end
                task.delay(definition.burnInterval, function()
                    self:GrenadePulse(owner, definition, position, definition.burnDamage, nil)
                    ctx:FX("grenade_burn", {from = position, radius = definition.radius})
                    burn(tick + 1)
                end)
            end
            burn(1)
        end
    end

    function Service:ThrowGrenade(player, s, uid, reportedAim)
        if ctx.Sessions[player] ~= s or not s.Active or s.Closing or s.Frozen or s.Traveling then return false, "Сейчас гранату бросить нельзя" end
        local root = ctx:Root(player); if not root or not ctx:Alive(player) then return false, "Персонаж не готов" end
        local entry = ctx.Inventory.find(s.Campaign, uid); local item = entry and ctx.Items[entry.id]
        local definition = item and item.grenade and ctx.Grenades[item.grenade]
        if not entry or not definition or definition.item ~= entry.id then return false, "Граната не найдена" end
        local now = os.clock(); if now < (s.NextGrenade or 0) then return false, "Граната ещё не готова" end
        local aim = root.CFrame.LookVector
        if typeof(reportedAim) == "Vector3" and ctx.Util.finite(reportedAim.X) and math.abs(reportedAim.X) <= 2
            and ctx.Util.finite(reportedAim.Y) and math.abs(reportedAim.Y) <= 2 and ctx.Util.finite(reportedAim.Z)
            and math.abs(reportedAim.Z) <= 2 and reportedAim.Magnitude > 0.5 then aim = reportedAim.Unit end
        if not ctx.Inventory.takeUid(s.Campaign, uid, 1) then return false, "Граната уже израсходована" end
        s.NextGrenade = now + definition.cooldown
        local projectile = Instance.new("Part"); projectile.Name = "ServerGrenade_" .. item.grenade
        projectile.Shape = Enum.PartType.Ball; projectile.Size = Vector3.new(0.68, 0.68, 0.68); projectile.Color = Color3.fromHex(definition.color)
        projectile.Material = item.grenade == "incendiary" and Enum.Material.Neon or Enum.Material.Metal
        projectile.CanCollide = true; projectile.CanTouch = false; projectile.CFrame = CFrame.new(root.Position + root.CFrame.LookVector * 2.8 + Vector3.new(0, 1.35, 0))
        projectile.Parent = workspace; projectile:SetNetworkOwner(nil)
        projectile.AssemblyLinearVelocity = aim * definition.throwSpeed + Vector3.new(0, definition.lift, 0)
        Debris:AddItem(projectile, definition.fuse + 3)
        ctx:FX("grenade_throw", {from = projectile.Position, to = projectile.Position + aim * 5, grenade = item.grenade})
        task.delay(definition.fuse, function()
            if not projectile.Parent then return end
            local position = projectile.Position; projectile:Destroy()
            self:DetonateGrenade(player, definition, position)
        end)
        return true, nil
    end

    function Service:AimTarget(origin, direction, range, character, radius)
        local best, bestAlong, bestPosition
        for _, zombie in ctx.Services.ZombieService.Zombies do
            if zombie.dead or zombie.humanoid.Health <= 0 or not zombie.model.Parent then continue end
            local targetPart = zombie.model:FindFirstChild("UpperTorso") or zombie.model.PrimaryPart
            local target = targetPart.Position
            local delta = target - origin; local along = delta:Dot(direction)
            if along <= 0 or along > range then continue end
            local miss = (delta - direction * along).Magnitude
            local allowance = (radius or 1.1) * (ctx.ZombieDefs[zombie.kind].size or 1)
            if miss <= allowance and (not bestAlong or along < bestAlong)
                and ctx:LineOfSight(origin, target, {character, zombie.model}) then
                best, bestAlong, bestPosition = zombie, along, target
            end
        end
        return best, bestPosition
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
        local enemy, hitPos, reportedDamage, killed, critical, armored
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
            if enemy then
                local applied, lethal = ctx.Services.ZombieService:Damage(enemy, damage, player)
                reportedDamage = applied or damage; killed = lethal == true
                armored = (ctx.ZombieDefs[enemy.kind].armor or 0) > 0
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
                local assistedPosition
                if not target then target, assistedPosition = self:AimTarget(head.Position, direction, def.range, player.Character, def.hitRadius) end
                if target then
                    enemy = enemy or target
                    local headshot = result and result.Instance and result.Instance.Name == "Head"
                    local amount = damage * (headshot and 1.65 or 1)
                    local record = hits[target] or {damage = 0, critical = false, position = assistedPosition or result and result.Position}
                    record.damage += amount; record.critical = record.critical or headshot
                    record.position = record.position or assistedPosition
                    hits[target] = record
                end
            end
            for target, record in hits do
                local applied, lethal = ctx.Services.ZombieService:Damage(target, record.damage, player)
                reportedDamage = (reportedDamage or 0) + (applied or record.damage)
                killed = killed or lethal == true; critical = critical or record.critical
                armored = armored or (target.kind and (ctx.ZombieDefs[target.kind].armor or 0) > 0)
                hitPos = record.position or hitPos
            end
        end
        if enemy then
            ctx.Remotes.EffectsRemote:FireClient(player, "hit_confirm", {
                damage = reportedDamage or damage, killed = killed == true, critical = critical == true,
                armored = armored == true, position = hitPos or enemy.model and enemy.model.PrimaryPart.Position,
            })
        elseif not def.melee then
            ctx:FX("impact", {from = hitPos or head.Position + aim * def.range})
        end
        ctx:FX("shot", {from = head.Position, to = hitPos or head.Position + aim * 5, melee = def.melee == true, hit = enemy ~= nil, user = player.UserId, weapon = entry.id})
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
                local weapon = entry and ctx.Weapons[entry.id]
                local armor = entry and ctx.Items[entry.id] and ctx.Items[entry.id].armor
                if not entry or (not weapon and not armor) or (p.action == "upgrade" and not weapon) then return end
                local cash, cost
                if p.action == "repair" then
                    if entry.durability >= 100 then return end
                    cash = armor and 25 or 20
                    cost = {metal = math.max(1, math.ceil((100 - entry.durability) / (armor and 20 or 25)))}
                else
                    if entry.upgrade >= 10 then ctx:Notify(player, "Достигнут уровень улучшения 10."); return end
                    cash = 25 * (entry.upgrade + 1); cost = {metal = 2 + entry.upgrade, parts = 1 + math.floor(entry.upgrade / 3)}
                    if entry.upgrade >= 3 then cost.electronics = math.floor(entry.upgrade / 3) end
                end
                if s.Campaign.Cash < cash or not ctx.Inventory.pay(s.Campaign, cost) then ctx:Notify(player, "Недостаточно материалов или Survival Cash.", "error"); return end
                s.Campaign.Cash -= cash
                if p.action == "repair" then
                    entry.durability = 100
                    if armor then self:SyncArmor(player, s, player.Character) end
                else entry.upgrade += 1 end
                ctx:Notify(player, armor and "Броня восстановлена и снова отображается на персонаже." or "Оружие обслужено.", "success"); ctx:Changed(player)
            end
        end)
    end
    return Service
end
