local Util = {}
function Util.clamp(v:number, a:number,b:number) return math.clamp(v,a,b) end
function Util.lerp(a:number,b:number,t:number) return a + (b-a)*t end
function Util.formatMoney(n:number):string
    if n>=1e9 then return string.format("%.1fB", n/1e9)
    elseif n>=1e6 then return string.format("%.1fM", n/1e6)
    elseif n>=1e3 then return string.format("%.1fK", n/1e3)
    else return tostring(math.floor(n)) end
end
function Util.deepCopy(t:any):any
    if type(t)~="table" then return t end
    local c={} for k,v in pairs(t) do c[k]=Util.deepCopy(v) end return c
end
function Util.uuid():string
    return string.format("%08x-%04x", math.random(0,0x7fffffff), math.random(0,0xffff))
end
function Util.colorToHex(c:Color3):string
    return string.format("#%02X%02X%02X", math.floor(c.R*255), math.floor(c.G*255), math.floor(c.B*255))
end
return Util
