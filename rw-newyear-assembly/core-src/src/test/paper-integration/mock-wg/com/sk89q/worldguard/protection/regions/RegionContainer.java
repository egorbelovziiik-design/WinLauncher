package com.sk89q.worldguard.protection.regions;
import com.sk89q.worldedit.world.World;
import com.sk89q.worldguard.protection.managers.RegionManager;
public class RegionContainer {
    public final RegionManager manager=new RegionManager();
    public RegionManager get(World world) { return manager; }
}
