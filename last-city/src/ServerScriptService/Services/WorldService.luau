--!nonstrict
return function(ctx)
    local Lighting = game:GetService("Lighting")
    local Service = {Weather = "Ясно"}
    function Service:Init()
        local start = os.clock(); local previous = 15.5; local weatherAt = os.clock() + 180
        local atmosphere = Lighting:FindFirstChildOfClass("Atmosphere")
        local night = nil
        task.spawn(function()
            while task.wait(1) do
                local hour = (15.5 + (os.clock() - start) / ctx.Config.DayLength * 24) % 24
                Lighting.ClockTime = hour
                local isNight = hour >= 20 or hour < 6
                if isNight ~= night then
                    night = isNight
                    for _, light in ctx.World:GetDescendants() do
                        if light:IsA("Light") and not light:IsDescendantOf(ctx.World.Buildings) and light.Parent.Name ~= "InteriorStrip" then light.Enabled = isNight end
                    end
                end
                if previous < 19 and hour >= 19 then
                    for player, s in ctx.Sessions do if s.Active then ctx:Notify(player, "Скоро ночь. Проверьте топливо, стены и боеприпасы на складе.", "info") end end
                end
                if previous < 21 and hour >= 21 then ctx.Services.ZombieService:NightRaid() end
                previous = hour
                if os.clock() >= weatherAt then
                    weatherAt = os.clock() + 180
                    Service.Weather = ({"Ясно", "Туман", "Дождь"})[math.random(1, 3)]
                end
                if atmosphere then atmosphere.Density = Service.Weather == "Туман" and 0.38 or Service.Weather == "Дождь" and 0.26 or isNight and 0.24 or 0.17 end
                Lighting.Brightness = isNight and 1.1 or Service.Weather == "Дождь" and 1.8 or 2.6
                -- A small bounded set of street lights flickers, never every world object each frame.
                local flicker = ctx.World:FindFirstChild("FlickerLamp", true)
                if flicker then local l = flicker:FindFirstChildOfClass("PointLight"); if l then l.Enabled = isNight and math.random() > 0.18 end end
            end
        end)
    end
    return Service
end
