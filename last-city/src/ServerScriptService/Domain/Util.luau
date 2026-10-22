--!strict
local Util = {}
function Util.copy(value: any): any
    if type(value) ~= "table" then return value end
    local result = {}
    for key, entry in value do result[key] = Util.copy(entry) end
    return result
end
function Util.finite(value: any): boolean
    return type(value) == "number" and value == value and math.abs(value) < math.huge
end
function Util.integer(value: any, minimum: number, maximum: number): boolean
    return Util.finite(value) and value % 1 == 0 and value >= minimum and value <= maximum
end
function Util.level(xp: number): number return 1 + math.floor(math.sqrt(math.max(0, xp) / 100)) end
function Util.day(now: number, duration: number): number return math.floor(now / duration) end
return Util
