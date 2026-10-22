local AudioController = {}
local assets = {
    menu = 1843523000, -- пример ID (замени на свои из Creator Marketplace)
    peace = 1837240040,
    war = 1837255766,
    economy = 1837761492,
    victory = 12222216,
    defeat = 12222124,
}
local current: Sound? = nil
function AudioController.init()
    local s = Instance.new("Folder"); s.Name="Audio"; s.Parent=workspace
end
function AudioController.play(track:string, volume:number?)
    if current then current:Stop(); current:Destroy() end
    local id = assets[track] or assets.menu
    local sound = Instance.new("Sound")
    sound.SoundId="rbxassetid://"..id
    sound.Volume=volume or 0.4
    sound.Looped = track~="victory" and track~="defeat"
    sound.Parent=workspace
    sound:Play()
    current=sound
end
function AudioController.playSFX(name:string)
    local id = ({click=12222025, build=12221967, war=12222058, coin=12221976, achieve=12222030})[name] or 12222025
    local s=Instance.new("Sound"); s.SoundId="rbxassetid://"..id; s.Volume=0.7; s.Parent=workspace; s:Play(); game.Debris:AddItem(s,3)
end
function AudioController.setVolumes(music:number, sfx:number, ui:number)
    if current then current.Volume=music end
end
return AudioController
