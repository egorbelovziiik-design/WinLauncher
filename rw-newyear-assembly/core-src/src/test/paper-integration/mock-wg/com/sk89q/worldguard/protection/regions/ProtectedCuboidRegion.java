package com.sk89q.worldguard.protection.regions;
import com.sk89q.worldedit.math.BlockVector3;
public final class ProtectedCuboidRegion extends ProtectedRegion {
    public ProtectedCuboidRegion(String id,BlockVector3 min,BlockVector3 max) { super(min,max); }
}
