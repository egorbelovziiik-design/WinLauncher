package com.sk89q.worldguard.protection.managers;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import java.util.*;
public final class RegionManager {
    private final Map<String,ProtectedRegion> regions=new HashMap<String,ProtectedRegion>();
    public Map<String,ProtectedRegion> getRegions() { return regions; }
}
