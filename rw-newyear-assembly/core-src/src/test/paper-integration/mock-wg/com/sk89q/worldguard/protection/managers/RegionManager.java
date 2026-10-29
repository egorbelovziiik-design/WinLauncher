package com.sk89q.worldguard.protection.managers;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import java.util.*;
public final class RegionManager {
    private final Map<String,ProtectedRegion> regions=new HashMap<String,ProtectedRegion>();
    public Map<String,ProtectedRegion> getRegions() { return regions; }
    public com.sk89q.worldguard.protection.ApplicableRegionSet getApplicableRegions(ProtectedRegion query) {
        int n=0;
        com.sk89q.worldedit.math.BlockVector3 a=query.getMinimumPoint(), b=query.getMaximumPoint();
        for (ProtectedRegion r:regions.values()) {
            com.sk89q.worldedit.math.BlockVector3 min=r.getMinimumPoint(), max=r.getMaximumPoint();
            if (b.getBlockX()>=min.getBlockX() && a.getBlockX()<=max.getBlockX()
                    && b.getBlockY()>=min.getBlockY() && a.getBlockY()<=max.getBlockY()
                    && b.getBlockZ()>=min.getBlockZ() && a.getBlockZ()<=max.getBlockZ()) n++;
        }
        final int count=n;
        return new com.sk89q.worldguard.protection.ApplicableRegionSet() { public int size() { return count; } };
    }
}
