package com.sk89q.worldguard;
import com.sk89q.worldguard.internal.platform.WorldGuardPlatform;
import com.sk89q.worldguard.protection.regions.RegionContainer;
public final class WorldGuard {
    private static final WorldGuard INSTANCE=new WorldGuard();
    private final RegionContainer container=new RegionContainer();
    private final WorldGuardPlatform platform=new WorldGuardPlatform() { public RegionContainer getRegionContainer() { return container; } };
    public static WorldGuard getInstance() { return INSTANCE; }
    public WorldGuardPlatform getPlatform() { return platform; }
}
