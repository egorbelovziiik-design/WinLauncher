--!nonstrict
-- Asset-safe soundscape using Roblox-bundled audio only. A slow pitched bass sequence
-- forms the exploration score; combat and flight layers respond to real game events.
return function(app)
    local SoundService = game:GetService("SoundService")
    local RunService = game:GetService("RunService")
    local Audio = {pulse = 0, note = 0, hurtAt = 0}
    local music = Instance.new("Sound"); music.Name = "LastCityMusicPulse"; music.SoundId = app.Config.Audio.MusicPulse
    music.Volume = 0; music.Parent = SoundService
    local rotor = Instance.new("Sound"); rotor.Name = "LastCityHelicopter"; rotor.SoundId = app.Config.Audio.Rotor
    rotor.Looped = true; rotor.PlaybackSpeed = 0.42; rotor.Volume = 0; rotor.Parent = SoundService
    local effects = {}
    for index = 1, 6 do
        local sound = Instance.new("Sound"); sound.Name = "LastCityEffect" .. index; sound.Parent = SoundService
        effects[index] = sound
    end
    local nextEffect = 1
    local function volume(key, base)
        local settings = app.State and app.State.profile.Settings
        return (settings and settings[key] or 0) * base
    end
    local function play(id, speed, base)
        local sound = effects[nextEffect]; nextEffect = nextEffect % #effects + 1
        sound.SoundId = id; sound.PlaybackSpeed = speed or 1; sound.Volume = volume("effects", base or 0.35)
        sound.TimePosition = 0; sound:Play()
    end
    app.Remotes.EffectsRemote.OnClientEvent:Connect(function(kind, data)
        if kind == "acid" then play(app.Config.Audio.Melee, 0.55, 0.22)
        elseif kind == "shot" then play(data.melee and app.Config.Audio.Melee or app.Config.Audio.Shot, data.melee and 0.82 or 1.12, data.melee and 0.3 or 0.22) end
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
    local notes = {0.5, 0.595, 0.667, 0.75, 0.667, 0.595}
    RunService.Heartbeat:Connect(function(dt)
        if not app.State or not app.State.active then music.Volume = 0; return end
        Audio.pulse += dt
        if Audio.pulse >= 5.2 then
            Audio.pulse = 0; Audio.note = Audio.note % #notes + 1
            music.PlaybackSpeed = notes[Audio.note]
            music.Volume = volume("music", app.Camera and app.Camera.Travel and 0.08 or 0.18)
            music.TimePosition = 0; music:Play()
        end
        if rotor.IsPlaying then rotor.Volume = volume("effects", 0.32) end
    end)
    return Audio
end
