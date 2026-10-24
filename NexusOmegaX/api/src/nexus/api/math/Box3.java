package nexus.api.math;

/** Axis aligned bounding box in world space. */
public final class Box3 {

    public final double minX;
    public final double minY;
    public final double minZ;
    public final double maxX;
    public final double maxY;
    public final double maxZ;

    public Box3(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
        this.minX = minX;
        this.minY = minY;
        this.minZ = minZ;
        this.maxX = maxX;
        this.maxY = maxY;
        this.maxZ = maxZ;
    }

    public static Box3 ofSize(Vec3d center, double width, double height) {
        double half = width / 2.0;
        return new Box3(center.x - half, center.y, center.z - half,
                center.x + half, center.y + height, center.z + half);
    }

    public static Box3 block(int x, int y, int z) {
        return new Box3(x, y, z, x + 1.0, y + 1.0, z + 1.0);
    }

    public Vec3d center() {
        return new Vec3d((minX + maxX) / 2.0, (minY + maxY) / 2.0, (minZ + maxZ) / 2.0);
    }

    public double sizeX() {
        return maxX - minX;
    }

    public double sizeY() {
        return maxY - minY;
    }

    public double sizeZ() {
        return maxZ - minZ;
    }

    public Box3 expand(double amount) {
        return new Box3(minX - amount, minY - amount, minZ - amount,
                maxX + amount, maxY + amount, maxZ + amount);
    }

    public Box3 offset(double dx, double dy, double dz) {
        return new Box3(minX + dx, minY + dy, minZ + dz, maxX + dx, maxY + dy, maxZ + dz);
    }

    public boolean contains(Vec3d point) {
        return point.x >= minX && point.x <= maxX
                && point.y >= minY && point.y <= maxY
                && point.z >= minZ && point.z <= maxZ;
    }
}
