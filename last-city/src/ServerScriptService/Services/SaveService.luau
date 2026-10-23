--!nonstrict
return function(ctx)
    local DSS = game:GetService("DataStoreService")
    local RunService = game:GetService("RunService")
    local HttpService = game:GetService("HttpService")
    local Service = {}
    function Service:GetStore()
        if self.Store then return self.Store end
        local ok, result = pcall(function() return DSS:GetDataStore(ctx.Config.DataStoreName) end)
        if ok then self.Store = result; return result end
        return nil, tostring(result)
    end
    local function retry(callback)
        local last
        for attempt = 1, 4 do
            local ok, result = pcall(callback)
            if ok then return true, result end
            last = result
            if attempt < 4 then task.wait(math.min(4, 0.6 * 2 ^ (attempt - 1)) + math.random() * 0.2) end
        end
        return false, last
    end
    function Service:Load(player)
        local token = (game.JobId ~= "" and game.JobId or "studio") .. ":" .. HttpService:GenerateGUID(false)
        local key = "player_" .. player.UserId
        local function sessionOnly()
            return {Data = ctx.Profile.new(), Key = key, Token = token, LastSave = 0, Expires = math.huge, SaveState = "SessionOnly", Persistent = false}
        end
        if RunService:IsStudio() and (ctx.Config.StudioUseDataStore ~= true or game.PlaceId == 0) then
            return sessionOnly()
        end
        local store, storeError = self:GetStore()
        if not store then
            warn("[LastCity] GetDataStore failed: " .. tostring(storeError))
            if RunService:IsStudio() then return sessionOnly() end
            return nil, "Хранилище недоступно. Вход отменён без сброса вашего сохранения."
        end
        local loaded, reason, locked, ok, result
        -- Handoffs (including solo teleports) can overlap PlayerRemoving by a few seconds.
        -- Retry contention, but never steal a live lease or reset an unreadable record.
        for contentionAttempt = 1, 6 do
            ok, result = retry(function()
                return self.Store:UpdateAsync(key, function(old)
                    loaded = nil; reason = nil; locked = false
                    if old ~= nil and (type(old) ~= "table" or type(old.Data) ~= "table") then
                        reason = "Повреждённая запись профиля. Вход отменён без перезаписи данных."; return nil
                    end
                    if not ctx.Lease.canAcquire(old, token, os.time()) then
                        locked = true; reason = "Сохранение открыто на другом сервере. Подождите до трёх минут."; return nil
                    end
                    local data, err = ctx.Profile.normalize(old and old.Data)
                    if not data then reason = err; return nil end
                    loaded = data
                    return {Data = data, Lease = {Token = token, Expires = os.time() + ctx.Config.LeaseSeconds}}
                end)
            end)
            if not ok or not locked or contentionAttempt == 6 then break end
            task.wait(math.min(4, contentionAttempt))
        end
        if ok and loaded and result then return {Data = loaded, Key = key, Token = token, LastSave = os.time(), Expires = result.Lease.Expires, SaveState = "Saved", Persistent = true} end
        if ok then return nil, reason or "Не удалось получить блокировку сохранения" end
        warn("[LastCity] DataStore load failed: " .. tostring(result))
        if RunService:IsStudio() then
            return sessionOnly()
        end
        return nil, "DataStore недоступен. Вход отменён, чтобы не перезаписать ваш прогресс. Попробуйте позже."
    end
    function Service:Save(player, release)
        local s = ctx.Sessions[player]
        if not s or not s.Loaded then return false end
        if s.Saving then
            if not release then return false end
            local deadline = os.clock() + 12
            while s.Saving and os.clock() < deadline do task.wait(0.1) end
            if s.Saving then return false end
        end
        if not s.Persistent then s.SaveState = "SessionOnly"; return true end
        local store, storeError = self:GetStore()
        if not store then s.SaveState = "Error"; warn("[LastCity] GetDataStore save failed: " .. tostring(storeError)); return false end
        s.Saving = true; s.SaveState = "Saving"
        local snapshot = ctx.Util.copy(s.Data)
        local owned, result
        local ok, err = retry(function()
            result = self.Store:UpdateAsync(s.Key, function(old)
                owned = ctx.Lease.owns(old, s.Token, os.time())
                if not owned then return nil end
                return {Data = snapshot, Lease = not release and {Token = s.Token, Expires = os.time() + ctx.Config.LeaseSeconds} or nil}
            end)
            return result
        end)
        s.Saving = false
        if ok and owned and result then
            s.LastSave = os.time(); s.Expires = release and 0 or result.Lease.Expires; s.SaveState = "Saved"
            return true
        end
        s.SaveState = "Error"
        warn("[LastCity] Save rejected/failed: " .. tostring(err))
        if not owned and ok then
            s.Frozen = true
            if player.Parent then player:Kick("Блокировка сохранения потеряна. Подключитесь заново, чтобы избежать конфликта данных.") end
        elseif player.Parent then ctx:Notify(player, "Сохранение временно недоступно. Выполняется повторная попытка.", "error") end
        return false
    end
    function Service:Init()
        task.spawn(function()
            while task.wait(ctx.Config.AutosaveSeconds) do
                for player, s in ctx.Sessions do
                    if s.Loaded and not s.Closing then task.spawn(function() self:Save(player, false) end) end
                end
            end
        end)
        task.spawn(function()
            while task.wait(5) do
                for player, s in ctx.Sessions do
                    if s.Persistent and s.Loaded and not s.Closing and os.time() >= s.Expires - 10 then
                        s.Frozen = true
                        player:Kick("Сохранение долго недоступно. Сессия остановлена до истечения блокировки. Попробуйте подключиться позже.")
                    end
                end
            end
        end)
        game:BindToClose(function()
            ctx.Closing = true
            local pending = 0
            for player, s in ctx.Sessions do
                if s.Loaded and not s.Closing then
                    pending += 1; s.Closing = true; s.Active = false
                    task.spawn(function() self:Save(player, true); pending -= 1 end)
                end
            end
            local deadline = os.clock() + 25
            while pending > 0 and os.clock() < deadline do task.wait(0.1) end
        end)
    end
    return Service
end
