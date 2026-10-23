--!nonstrict
-- Pure lease policy, shared by SaveService and concurrency tests.
local Lease = {}
function Lease.canAcquire(envelope, token, now)
    if envelope == nil then return true end
    if type(envelope) ~= "table" then return false end
    local held = envelope.Lease
    if held == nil then return true end
    return type(held) == "table" and type(held.Expires) == "number" and (held.Token == token or held.Expires <= now)
end
function Lease.owns(envelope, token, now)
    return type(envelope) == "table" and type(envelope.Lease) == "table" and envelope.Lease.Token == token and envelope.Lease.Expires > now
end
return Lease
