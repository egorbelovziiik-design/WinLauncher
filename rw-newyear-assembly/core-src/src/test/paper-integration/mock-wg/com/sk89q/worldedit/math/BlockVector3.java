package com.sk89q.worldedit.math;
public final class BlockVector3 {
    private final int x,y,z;
    public BlockVector3(int x,int y,int z) { this.x=x; this.y=y; this.z=z; }
    public static BlockVector3 at(int x,int y,int z) { return new BlockVector3(x,y,z); }
    public int getBlockX() { return x; } public int getBlockY() { return y; } public int getBlockZ() { return z; }
}
