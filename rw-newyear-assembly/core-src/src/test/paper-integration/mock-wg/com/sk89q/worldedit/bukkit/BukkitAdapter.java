package com.sk89q.worldedit.bukkit;
public final class BukkitAdapter {
    public static com.sk89q.worldedit.world.World adapt(org.bukkit.World world) { return new com.sk89q.worldedit.world.World() {}; }
}
