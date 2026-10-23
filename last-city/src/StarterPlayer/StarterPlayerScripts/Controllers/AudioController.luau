--!nonstrict
-- Asset-safe adaptive score and positional combat mix. Every SoundId is a Roblox-
-- bundled file from GameConfig; there are no arbitrary catalog or third-party IDs.
return function(app)
    local SoundService = game:GetService("SoundService")
    local RunService = game:GetService("RunService")
    local CollectionService = game:GetService("CollectionService")
    local Audio = {pulse = 0, beat = 0, hurtAt = 0, scan = 0, danger = 0}
    local ambient = Instance.new("Sound"); ambient.Name = "LastCityWindLayer"; ambient.SoundId = app.Config.Audio.MusicTexture
    ambient.Looped = true; ambient.PlaybackSpeed = 0.72; ambient.Volume = 0; ambient.Parent = SoundService; ambient:Play()
    local rotor = Instance.new("Sound"); rotor.Name = "LastCityHelicopter"; rotor.SoundId = app.Config.Audio.Rotor
    rotor.Looped = true; rotor.PlaybackSpeed = 0.42; rotor.Volume = 0; rotor.Parent = SoundService
    local score = {}
    for index = 1, 5 do
        local sound = Instance.new("Sound"); sound.Name = "LastCityScoreVoice" .. index; sound.SoundId = app.Config.Audio.MusicPulse; sound.Parent = SoundService
        score[index] = sound
    end
    local scoreIndex = 1
    local anchors, effects = {}, {}
    local folder = Instance.new("Folder"); folder.Name = "LastCityAudioAnchors"; folder.Parent = workspace
    for index = 1, 10 do
        local anchor = Instance.new("Part"); anchor.Name = "AudioAnchor" .. index; anchor.Size = Vector3.new(0.1, 0.1, 0.1)
        anchor.Anchored = true; anchor.Transparency = 1; anchor.CanCollide = false; anchor.CanTouch = false; anchor.CanQuery = false; anchor.Parent = folder
        local sound = Instance.new("Sound"); sound.Name = "Effect"; sound.RollOffMinDistance = 8; sound.RollOffMaxDistance = 95; sound.Parent = anchor
        anchors[index] = anchor; effects[index] = sound
    end
    local nextEffect = 1
    local function volume(key, base)
        local settings = app.State and app.State.profile.Settings
        return (settings and settings[key] or 0) * base
    end
    local function play(id, speed, base, position)
        local index = nextEffect; nextEffect = nextEffect % #effects + 1
        local sound, anchor = effects[index], anchors[index]
        if position then anchor.CFrame = CFrame.new(position); sound.Parent = anchor else sound.Parent = SoundService end
        sound.SoundId = id; sound.PlaybackSpeed = speed or 1; sound.Volume = volume("effects", base or 0.35)
        sound.TimePosition = 0; sound:Play()
    end
    function Audio:Weapon(id)
        local def = app.Weapons[id]; if not def then return end
        if def.melee then
            local blade = table.find({"machete", "hatchet", "prism_blade", "short_sword", "saber", "longsword", "katana", "frontier_sword", "foundry_cleaver"}, id) ~= nil
            play(blade and app.Config.Audio.Blade or app.Config.Audio.Melee, id == "prism_blade" and 1.3 or id == "katana" and 1.16 or blade and 0.92 or 0.8, 0.34)
        else
            local heavy = table.find({"shotgun", "double_barrel", "revolver", "hunting_rifle", "magnum", "sniper_rifle", "frontier_dmr", "marshal_pistol", "lmg"}, id) ~= nil
            play(heavy and app.Config.Audio.HeavyShot or app.Config.Audio.Shot, heavy and 0.78 or (id == "smg" or id == "machine_pistol") and 1.3 or 1.06, heavy and 0.5 or 0.3)
        end
    end
    app.Remotes.EffectsRemote.OnClientEvent:Connect(function(kind, data)
        data = data or {}
        if kind == "acid" then play(app.Config.Audio.Melee, 0.55, 0.22, data.from)
        elseif kind == "electric" then play(app.Config.Audio.Impact, 1.6, 0.3, data.from)
        elseif kind == "shot" then
            if data.user ~= app.Player.UserId then
                local heavy = table.find({"shotgun", "double_barrel", "revolver", "hunting_rifle", "magnum", "sniper_rifle", "frontier_dmr", "marshal_pistol", "lmg"}, data.weapon) ~= nil
                local id = data.melee and app.Config.Audio.Melee or heavy and app.Config.Audio.HeavyShot or app.Config.Audio.Shot
                play(id, data.melee and 0.82 or heavy and 0.78 or 1.08, data.melee and 0.3 or heavy and 0.46 or 0.25, data.from)
            end
        elseif kind == "impact" then play(app.Config.Audio.Impact, 0.78 + math.random() * 0.3, 0.25, data.from)
        elseif kind == "build_spark" then play(app.Config.Audio.Impact, 0.52 + math.random() * 0.12, 0.16, data.from)
        elseif kind == "grenade_throw" then play(app.Config.Audio.Melee, 1.25, 0.18, data.from)
        elseif kind == "grenade_explosion" then play(app.Config.Audio.HeavyShot, data.concussion and 1.35 or 0.52, data.concussion and 0.45 or 0.75, data.from)
        elseif kind == "grenade_burn" then play(app.Config.Audio.Impact, 0.58, 0.12, data.from)
        elseif kind == "zombie_voice" then play(app.Config.Audio.ZombieVoice, (data.tone or 1) * 0.82, data.boss and 0.48 or 0.3, data.from)
        elseif kind == "zombie_howl" then play(app.Config.Audio.ZombieVoice, (data.tone or 1) * 0.48, 0.58, data.from)
        elseif kind == "zombie_hurt" then play(app.Config.Audio.ZombieVoice, (data.tone or 1) * 1.08, 0.2, data.from)
        elseif kind == "zombie_death" then play(app.Config.Audio.ZombieVoice, (data.tone or 1) * 0.62, data.boss and 0.58 or 0.34, data.from)
        elseif kind == "zombie_attack" then play(app.Config.Audio.ZombieAttack, data.ranged and 1.22 or 0.76, 0.23, data.from)
        elseif kind == "hit_confirm" then
            play(data.armored and app.Config.Audio.ArmorImpact or app.Config.Audio.Impact, data.critical and 1.3 or 1.02, 0.22)
            if data.killed then play(app.Config.Audio.Confirm, 0.68, 0.28) elseif data.critical then play(app.Config.Audio.Confirm, 1.35, 0.2) end
        elseif kind == "player_hurt" then
            Audio.hurtAt = os.clock() + 0.55; play(data.absorbed and data.absorbed > 0 and app.Config.Audio.ArmorImpact or app.Config.Audio.Hurt, 0.9, 0.28)
        elseif kind == "medic_heal" then play(app.Config.Audio.Confirm, 1.55, 0.18, data.from) end
    end)
    local healthConnection
    local function character(character)
        if healthConnection then healthConnection:Disconnect() end
        local humanoid = character:WaitForChild("Humanoid", 10); local previous = humanoid and humanoid.Health or 100
        if humanoid then healthConnection = humanoid.HealthChanged:Connect(function(value)
            if value < previous and os.clock() >= Audio.hurtAt then Audio.hurtAt = os.clock() + 0.6; play(app.Config.Audio.Hurt, 1, 0.22) end
            previous = value
        end) end
    end
    app.Player.CharacterAdded:Connect(character); if app.Player.Character then task.spawn(function() character(app.Player.Character) end) end
    function Audio:SetTravel(active)
        rotor.Volume = active and volume("effects", 0.32) or 0
        if active then if not rotor.IsPlaying then rotor:Play() end else rotor:Stop() end
    end
    local calm = {0.5, 0.595, 0.667, 0.75, 0.667, 0.595, 0.445, 0.5}
    local danger = {0.5, 0.63, 0.75, 0.56, 0.84, 0.63, 0.94, 0.75}
    RunService.Heartbeat:Connect(function(dt)
        if not app.State or not app.State.active then ambient.Volume = 0; return end
        Audio.scan += dt
        if Audio.scan >= 1 then
            Audio.scan = 0; Audio.danger = 0
            local root = app.Player.Character and app.Player.Character:FindFirstChild("HumanoidRootPart")
            if root then
                for _, model in CollectionService:GetTagged("Zombie") do
                    if model.Parent and model.PrimaryPart and (model.PrimaryPart.Position - root.Position).Magnitude <= 85 then Audio.danger += 1 end
                end
            end
        end
        local travel = app.Camera and app.Camera.Travel ~= nil
        ambient.Volume = volume("music", travel and 0.025 or Audio.danger > 0 and 0.045 or 0.075)
        Audio.pulse += dt
        local interval = travel and 2.4 or Audio.danger >= 3 and 0.95 or Audio.danger > 0 and 1.65 or 4.4
        if Audio.pulse >= interval then
            Audio.pulse = 0; Audio.beat += 1
            local pattern = Audio.danger > 0 and danger or calm
            local note = pattern[(Audio.beat - 1) % #pattern + 1]
            local sound = score[scoreIndex]; scoreIndex = scoreIndex % #score + 1
            sound.PlaybackSpeed = note; sound.Volume = volume("music", travel and 0.055 or Audio.danger > 0 and 0.2 or 0.13)
            sound.TimePosition = 0; sound:Play()
        end
        if rotor.IsPlaying then rotor.Volume = volume("effects", 0.32) end
    end)
    return Audio
end
