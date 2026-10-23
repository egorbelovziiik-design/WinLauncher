--!nonstrict
-- Roblox engine-native English TTS. No catalog audio ID or external service is used.
-- Requests are generation-cancelled so rapidly switching NPCs cannot play stale lines.
return function(app)
    local SoundService = game:GetService("SoundService")
    local Voice = {generation = 0, Available = false}
    local ok, bundle = pcall(function()
        local speech = Instance.new("AudioTextToSpeech"); speech.Name = "LastCityDialogueVoice"
        speech.AutoLocalize = false; speech.VoiceId = "3"; speech.Volume = 0; speech.Parent = SoundService
        local output = Instance.new("AudioDeviceOutput"); output.Name = "LastCityDialogueOutput"; output.Player = app.Player; output.Parent = SoundService
        local wire = Instance.new("Wire"); wire.Name = "LastCityDialogueWire"; wire.SourceInstance = speech; wire.TargetInstance = output; wire.Parent = SoundService
        return {speech = speech, output = output, wire = wire}
    end)
    if not ok then
        -- Some Studio/client channels can expose the game without the beta audio class.
        -- Dialogue remains readable instead of taking down the entire client boot.
        function Voice:Stop() self.generation += 1 end
        function Voice:Speak() end
        warn("[LastCity TTS] Engine speech is unavailable; using text dialogue only.")
        return Voice
    end
    Voice.Available = true
    local speech = bundle.speech
    function Voice:Stop()
        self.generation += 1
        pcall(function() speech:Pause() end)
        pcall(function() speech:Unload() end)
    end
    function Voice:Speak(text, voiceId)
        if type(text) ~= "string" or text == "" then return end
        self:Stop(); local generation = self.generation
        -- Official AudioTextToSpeech requests are capped at 300 characters.
        text = text:sub(1, 300)
        task.spawn(function()
            speech.Text = text; speech.VoiceId = tostring(voiceId or "3")
            speech.Volume = math.clamp(app.State and app.State.profile.Settings.voice or 0.75, 0, 1) * 0.8
            local loaded = pcall(function() speech:LoadAsync() end)
            if not loaded or generation ~= self.generation or not app.Dialogue then return end
            pcall(function() speech:Play() end)
        end)
    end
    return Voice
end
