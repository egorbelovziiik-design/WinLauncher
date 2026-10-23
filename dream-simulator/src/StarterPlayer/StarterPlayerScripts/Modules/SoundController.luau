--!nocheck
--[[
	SoundController.luau
	Музыка меню, эмбиент сна, сердцебиение, шёпот, скримеры, звуки покупок и побед.
	Громкость управляется настройками игрока.
]]

local SoundService = game:GetService("SoundService")
local ReplicatedStorage = game:GetService("ReplicatedStorage")
local ContentProvider = game:GetService("ContentProvider")

local Config = require(ReplicatedStorage.SharedModules.Config)
local ClientState = require(script.Parent.ClientState)

local SoundController = {}

local groups = {}
local sounds = {}

local function createGroup(name, volume)
	local group = Instance.new("SoundGroup")
	group.Name = name
	group.Volume = volume
	group.Parent = SoundService
	groups[name] = group
	return group
end

local function newSound(name, soundId, groupName, looped, volume)
	local sound = Instance.new("Sound")
	sound.Name = name
	sound.SoundId = soundId
	sound.Looped = looped == true
	sound.Volume = volume or 0.6
	sound.SoundGroup = groups[groupName] or groups.SFX
	-- Запасной встроенный звук: срабатывает, если основной ассет недоступен
	-- (чужие аудио в Roblox часто закрыты правами).
	sound:SetAttribute("FallbackId", Config.SoundFallbacks[name])
	sound:SetAttribute("FallbackPitch", Config.SoundPitch[name] or 1)
	sound:SetAttribute("PrimaryId", soundId)
	sound.Parent = SoundService
	sounds[name] = sound
	return sound
end

local function playWithFallback(sound)
	pcall(function()
		sound:Play()
	end)
	local fallback = sound:GetAttribute("FallbackId")
	if not fallback or sound:GetAttribute("UsingFallback") then
		return
	end
	task.delay(0.3, function()
		if not sound.Parent then
			return
		end
		-- Основной ассет не загрузился — переключаемся на встроенный.
		local loaded = true
		pcall(function()
			loaded = sound.IsLoaded
		end)
		if sound.IsPlaying and (loaded or sound.TimeLength > 0) then
			return
		end
		if loaded and sound.TimeLength > 0 then
			return
		end
		sound:SetAttribute("UsingFallback", true)
		sound.SoundId = fallback
		sound.PlaybackSpeed = sound:GetAttribute("FallbackPitch") or 1
		pcall(function()
			sound:Play()
		end)
	end)
end

function SoundController.play(name, volumeMultiplier)
	local sound = sounds[name]
	if not sound then
		return
	end
	if sound.IsPlaying and not sound.Looped then
		sound:Stop()
	end
	sound.Volume = (sound:GetAttribute("BaseVolume") or 0.6) * (volumeMultiplier or 1)
	playWithFallback(sound)
end

function SoundController.stop(name, fadeOut)
	local sound = sounds[name]
	if not sound then
		return
	end
	if fadeOut then
		ClientState.tween(sound, fadeOut, { Volume = 0 })
		task.delay(fadeOut, function()
			sound:Stop()
		end)
	else
		sound:Stop()
	end
end

function SoundController.applyVolumes()
	local music = ClientState.settings.musicVolume or 0.5
	local sfx = ClientState.settings.sfxVolume or 0.75
	if groups.Music then
		groups.Music.Volume = music
	end
	if groups.Ambient then
		groups.Ambient.Volume = sfx * 0.8
	end
	if groups.SFX then
		groups.SFX.Volume = sfx
	end
	if groups.Scares then
		groups.Scares.Volume = sfx
	end
end

function SoundController.playMusic(track)
	SoundController.stop("MenuMusic", 0.6)
	SoundController.stop("DreamMusic", 0.6)
	local sound = sounds[track]
	if not sound then
		return
	end
	sound.Volume = 0
	playWithFallback(sound)
	ClientState.tween(sound, 1.6, { Volume = 0.5 })
end

function SoundController.setAmbient(dreamId)
	local ambient = sounds.DreamAmbient
	if not ambient then
		return
	end
	if ambient.IsPlaying then
		ambient:Stop()
	end
	if dreamId then
		playWithFallback(ambient)
	end
end

function SoundController.heartbeat(enabled)
	local sound = sounds.Heartbeat
	if not sound then
		return
	end
	if enabled and not sound.IsPlaying then
		sound.Volume = 0
		playWithFallback(sound)
		ClientState.tween(sound, 0.8, { Volume = 0.5 })
	elseif not enabled and sound.IsPlaying then
		ClientState.tween(sound, 0.8, { Volume = 0 })
		task.delay(0.85, function()
			sound:Stop()
		end)
	end
end

function SoundController.start()
	createGroup("Music", 0.5)
	createGroup("Ambient", 0.5)
	createGroup("SFX", 0.75)
	createGroup("Scares", 0.9)

	local music = newSound("MenuMusic", Config.Sounds.MenuMusic, "Music", true, 0.5)
	music:SetAttribute("BaseVolume", 0.5)
	local dreamMusic = newSound("DreamMusic", Config.Sounds.DreamAmbient, "Music", true, 0.35)
	dreamMusic:SetAttribute("BaseVolume", 0.35)
	local heartbeat = newSound("Heartbeat", Config.Sounds.Heartbeat, "Scares", true, 0.5)
	heartbeat:SetAttribute("BaseVolume", 0.5)
	local whisper = newSound("Whisper", Config.Sounds.Whisper, "Scares", false, 0.55)
	whisper:SetAttribute("BaseVolume", 0.55)
	local scare = newSound("Scare", Config.Sounds.Scare, "Scares", false, 0.9)
	scare:SetAttribute("BaseVolume", 0.9)
	local growl = newSound("MonsterGrowl", Config.Sounds.MonsterGrowl, "Scares", false, 0.7)
	growl:SetAttribute("BaseVolume", 0.7)
	local click = newSound("Click", Config.Sounds.Click, "SFX", false, 0.5)
	click:SetAttribute("BaseVolume", 0.5)
	local purchase = newSound("Purchase", Config.Sounds.Purchase, "SFX", false, 0.7)
	purchase:SetAttribute("BaseVolume", 0.7)
	local win = newSound("Win", Config.Sounds.Win, "Music", false, 0.8)
	win:SetAttribute("BaseVolume", 0.8)
	local lose = newSound("Lose", Config.Sounds.Lose, "Scares", false, 0.8)
	lose:SetAttribute("BaseVolume", 0.8)
	local door = newSound("DoorCreak", Config.Sounds.DoorCreak, "SFX", false, 0.6)
	door:SetAttribute("BaseVolume", 0.6)
	local pickup = newSound("Pickup", Config.Sounds.Pickup, "SFX", false, 0.55)
	pickup:SetAttribute("BaseVolume", 0.55)
	local portal = newSound("Portal", Config.Sounds.Portal, "Scares", false, 0.7)
	portal:SetAttribute("BaseVolume", 0.7)
	local growlBig = newSound("BigScare", Config.Sounds.BigScare, "Scares", false, 1)
	growlBig:SetAttribute("BaseVolume", 1)
	local whisperLoop = newSound("WhisperLoop", Config.Sounds.Whisper, "Ambient", true, 0)
	whisperLoop:SetAttribute("BaseVolume", 0.35)

	SoundController.applyVolumes()
	ClientState.on("Sound", function(name)
		SoundController.play(name)
	end)
	ClientState.on("Profile", function()
		SoundController.applyVolumes()
	end)
end

function SoundController.setMenu()
	SoundController.applyVolumes()
	SoundController.heartbeat(false)
	SoundController.setAmbient(nil)
	SoundController.playMusic("MenuMusic")
end

function SoundController.setDream()
	SoundController.applyVolumes()
	SoundController.playMusic("DreamMusic")
	SoundController.setAmbient(true)
end

-- Шёпот на фоне при низком рассудке (плавно, без щелчков)
function SoundController.whispers(enabled, intensity)
	local sound = sounds.WhisperLoop
	if not sound then
		return
	end
	local level = math.clamp(intensity or 0.5, 0, 1) * 0.45
	if enabled then
		if not sound.IsPlaying then
			sound.Volume = 0
			playWithFallback(sound)
		end
		ClientState.tween(sound, 1.2, { Volume = level })
	elseif sound.IsPlaying then
		ClientState.tween(sound, 1.2, { Volume = 0 })
		task.delay(1.3, function()
			if sound and sound.IsPlaying and sound.Volume <= 0.02 then
				sound:Stop()
			end
		end)
	end
end

function SoundController.setWaking(won)
	SoundController.heartbeat(false)
	SoundController.setAmbient(nil)
	SoundController.stop("DreamMusic", 1)
	SoundController.play(won and "Win" or "Lose")
end

return SoundController
