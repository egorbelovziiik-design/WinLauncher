package com.sk89q.worldguard.protection.regions;
import com.sk89q.worldedit.math.BlockVector3;
public final class ProtectedRegion {
    private final BlockVector3 min,max;
    public ProtectedRegion(BlockVector3 min,BlockVector3 max) { this.min=min; this.max=max; }
    public BlockVector3 getMinimumPoint() { return min; }
    public BlockVector3 getMaximumPoint() { return max; }
}
