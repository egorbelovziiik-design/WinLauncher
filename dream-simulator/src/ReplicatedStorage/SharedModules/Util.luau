--!nonstrict
-- Util: общие вспомогательные функции (работают и на сервере, и на клиенте).

local Util = {}

function Util.clamp(value, minValue, maxValue)
	if value ~= value then
		return minValue
	end
	if value < minValue then
		return minValue
	elseif value > maxValue then
		return maxValue
	end
	return value
end

function Util.lerp(a, b, t)
	return a + (b - a) * t
end

function Util.round(value, decimals)
	local mult = 10 ^ (decimals or 0)
	return math.floor(value * mult + 0.5) / mult
end

function Util.deepCopy(source)
	if type(source) ~= "table" then
		return source
	end
	local copy = {}
	for key, value in pairs(source) do
		copy[key] = Util.deepCopy(value)
	end
	return copy
end

function Util.copy(source)
	local copy = {}
	for key, value in pairs(source) do
		copy[key] = value
	end
	return copy
end

function Util.shuffle(list, rng)
	local random = rng or Random.new()
	for i = #list, 2, -1 do
		local j = random:NextInteger(1, i)
		list[i], list[j] = list[j], list[i]
	end
	return list
end

function Util.pick(list, rng)
	if #list == 0 then
		return nil
	end
	if rng then
		return list[rng:NextInteger(1, #list)]
	end
	return list[math.random(1, #list)]
end

function Util.pickMany(list, count, rng)
	local pool = Util.copy(list)
	Util.shuffle(pool, rng)
	local out = {}
	for i = 1, math.min(count, #pool) do
		out[i] = pool[i]
	end
	return out
end

function Util.weightedPick(entries, rng)
	local total = 0
	for _, entry in ipairs(entries) do
		total = total + (entry.weight or 1)
	end
	if total <= 0 then
		return nil
	end
	local roll = rng and rng:NextNumber() * total or math.random() * total
	local acc = 0
	for _, entry in ipairs(entries) do
		acc = acc + (entry.weight or 1)
		if roll <= acc then
			return entry
		end
	end
	return entries[#entries]
end

function Util.formatTime(seconds)
	local s = math.max(0, math.floor(seconds))
	return string.format("%02d:%02d", math.floor(s / 60), s % 60)
end

function Util.formatLongTime(seconds)
	local s = math.max(0, math.floor(seconds))
	local hours = math.floor(s / 3600)
	local minutes = math.floor((s % 3600) / 60)
	if hours > 0 then
		return string.format("%d ч %02d мин", hours, minutes)
	end
	return string.format("%d мин", minutes)
end

function Util.count(tbl)
	local total = 0
	for _ in pairs(tbl or {}) do
		total = total + 1
	end
	return total
end

function Util.find(list, predicate)
	for index, value in ipairs(list) do
		if predicate(value, index) then
			return value, index
		end
	end
	return nil, nil
end

function Util.contains(list, value)
	for _, entry in ipairs(list) do
		if entry == value then
			return true
		end
	end
	return false
end

function Util.distance2D(a, b)
	local dx = a.X - b.X
	local dz = a.Z - b.Z
	return math.sqrt(dx * dx + dz * dz)
end

function Util.isFiniteNumber(value)
	return type(value) == "number" and value == value and value ~= math.huge and value ~= -math.huge
end

function Util.sanitizeNumber(value, fallback, minValue, maxValue)
	if not Util.isFiniteNumber(value) then
		value = fallback
	end
	if minValue and value < minValue then
		value = minValue
	end
	if maxValue and value > maxValue then
		value = maxValue
	end
	return value
end

function Util.newRigPart(name, size, color, material)
	local part = Instance.new("Part")
	part.Name = name
	part.Size = size
	part.Color = color
	part.Material = material or Enum.Material.SmoothPlastic
	part.Anchored = false
	part.CanCollide = false
	part.TopSurface = Enum.SurfaceType.Smooth
	part.BottomSurface = Enum.SurfaceType.Smooth
	part.Massless = true
	return part
end

function Util.weld(parentPart, childPart, offset)
	local weld = Instance.new("WeldConstraint")
	weld.Part0 = parentPart
	weld.Part1 = childPart
	weld.Parent = childPart
	if offset then
		childPart.CFrame = parentPart.CFrame * offset
	end
	return weld
end

function Util.tween(instance, info, goals)
	local TweenService = game:GetService("TweenService")
	local tween = TweenService:Create(instance, info, goals)
	tween:Play()
	return tween
end

function Util.fadeIn(guiObject, duration, targetTransparency)
	if not guiObject then
		return
	end
	guiObject.Visible = true
	local TweenService = game:GetService("TweenService")
	TweenService:Create(guiObject, TweenInfo.new(duration or 0.4), {
		BackgroundTransparency = targetTransparency or 0,
	}):Play()
end

function Util.getPlayerFromCharacter(character)
	local Players = game:GetService("Players")
	for _, player in ipairs(Players:GetPlayers()) do
		if player.Character == character then
			return player
		end
	end
	return nil
end

function Util.taggedParts(tagName)
	local CollectionService = game:GetService("CollectionService")
	return CollectionService:GetTagged(tagName)
end

function Util.shallowEqual(a, b)
	if type(a) ~= "table" or type(b) ~= "table" then
		return a == b
	end
	for key, value in pairs(a) do
		if b[key] ~= value then
			return false
		end
	end
	for key in pairs(b) do
		if a[key] == nil then
			return false
		end
	end
	return true
end

return Util
