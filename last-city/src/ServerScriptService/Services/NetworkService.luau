--!nonstrict
return function(ctx)
    local Service = {connections = {}}
    local limiter = ctx.RateLimiter.new(35, 14)
    local channels = {}
    local function sane(payload)
        if type(payload) ~= "table" then return false end
        local n = 0
        for key, value in payload do
            n += 1
            if n > 8 or type(key) ~= "string" or #key > 24 then return false end
            local t = typeof(value)
            if t == "number" then if not ctx.Util.finite(value) then return false end
            elseif t == "string" then if #value > 80 then return false end
            elseif t == "Vector3" then
                if not ctx.Util.finite(value.X) or not ctx.Util.finite(value.Y) or not ctx.Util.finite(value.Z) then return false end
            elseif t ~= "boolean" then return false end
        end
        return type(payload.action) == "string" and #payload.action <= 24
    end
    function Service:Permit(player, channel, active)
        local s = ctx.Sessions[player]
        if not s or not s.Loaded or s.Closing or s.Frozen then return nil end
        if active and (not s.Active or not ctx:Alive(player)) then return nil end
        if active and ctx.PositionValid and not ctx:PositionValid(player) then return nil end
        if not limiter:allow(player, os.clock()) then return nil end
        local bucket = channels[channel]
        if not bucket then bucket = ctx.RateLimiter.new(channel == "CombatRemote" and 12 or 8, channel == "CombatRemote" and 9 or 3); channels[channel] = bucket end
        if not bucket:allow(player, os.clock()) then return nil end
        return s
    end
    function Service:Register(name, active, handler)
        local remote = ctx.Remotes:WaitForChild(name)
        table.insert(self.connections, remote.OnServerEvent:Connect(function(player, payload)
            if not sane(payload) then return end
            local session = self:Permit(player, name, active)
            if not session then return end
            local ok, err = xpcall(function() handler(player, session, payload) end, debug.traceback)
            if not ok then warn("[LastCity] " .. name .. ": " .. tostring(err)); ctx:Notify(player, "Ошибка действия. Ресурсы не отправляйте повторно; проверьте состояние.", "error") end
        end))
    end
    function Service:Remove(player)
        limiter:remove(player)
        for _, bucket in channels do bucket:remove(player) end
    end
    return Service
end
