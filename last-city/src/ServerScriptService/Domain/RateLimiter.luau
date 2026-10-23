--!nonstrict
local RateLimiter = {}
RateLimiter.__index = RateLimiter
function RateLimiter.new(capacity, refill)
    return setmetatable({capacity = capacity, refill = refill, buckets = {}}, RateLimiter)
end
function RateLimiter:allow(key, now, cost)
    cost = cost or 1
    local bucket = self.buckets[key] or {tokens = self.capacity, at = now}
    bucket.tokens = math.min(self.capacity, bucket.tokens + math.max(0, now - bucket.at) * self.refill)
    bucket.at = now; self.buckets[key] = bucket
    if bucket.tokens < cost then return false end
    bucket.tokens -= cost; return true
end
function RateLimiter:remove(key) self.buckets[key] = nil end
return RateLimiter
