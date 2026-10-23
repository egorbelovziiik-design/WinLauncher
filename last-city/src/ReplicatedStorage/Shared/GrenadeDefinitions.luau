--!strict
-- Throwable balance is shared for honest inventory text, but only CombatService
-- applies these values. Clients never choose fuse, radius, damage, burn, or stun.
return {
    frag = {
        item = "frag_grenade", fuse = 2.15, radius = 18, damage = 115,
        throwSpeed = 58, lift = 18, cooldown = 2.5, selfDamage = 0.45,
        color = "6B7A4A",
    },
    incendiary = {
        item = "incendiary_grenade", fuse = 1.8, radius = 14, damage = 28,
        burnDamage = 14, burnTicks = 5, burnInterval = 0.8,
        throwSpeed = 54, lift = 17, cooldown = 2.8, selfDamage = 0.35,
        color = "EA580C",
    },
    concussion = {
        item = "concussion_grenade", fuse = 1.65, radius = 20, damage = 12,
        stunSeconds = 5, throwSpeed = 60, lift = 19, cooldown = 2.4,
        selfDamage = 0, color = "D6D3D1",
    },
}
