package nexus.api.math;

/** Immutable 3D double vector shared by the whole client. */
public final class Vec3d {

    public static final Vec3d ZERO = new Vec3d(0.0, 0.0, 0.0);

    public final double x;
    public final double y;
    public final double z;

    public Vec3d(double x, double y, double z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public Vec3d add(Vec3d other) {
        return new Vec3d(x + other.x, y + other.y, z + other.z);
    }

    public Vec3d add(double dx, double dy, double dz) {
        return new Vec3d(x + dx, y + dy, z + dz);
    }

    public Vec3d subtract(Vec3d other) {
        return new Vec3d(x - other.x, y - other.y, z - other.z);
    }

    public Vec3d multiply(double scale) {
        return new Vec3d(x * scale, y * scale, z * scale);
    }

    public double length() {
        return Math.sqrt(x * x + y * y + z * z);
    }

    public double distanceTo(Vec3d other) {
        double dx = other.x - x;
        double dy = other.y - y;
        double dz = other.z - z;
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    public Vec3d lerp(Vec3d target, double t) {
        return new Vec3d(x + (target.x - x) * t, y + (target.y - y) * t, z + (target.z - z) * t);
    }

    public double yawTo(Vec3d target) {
        double dx = target.x - x;
        double dz = target.z - z;
        return Math.toDegrees(Math.atan2(dz, dx)) - 90.0;
    }

    public double pitchTo(Vec3d target) {
        double dx = target.x - x;
        double dy = target.y - y;
        double dz = target.z - z;
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        return -Math.toDegrees(Math.atan2(dy, horizontal));
    }

    @Override
    public String toString() {
        return String.format("(%.2f, %.2f, %.2f)", x, y, z);
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof Vec3d)) {
            return false;
        }
        Vec3d v = (Vec3d) other;
        return x == v.x && y == v.y && z == v.z;
    }

    @Override
    public int hashCode() {
        long bits = Double.doubleToLongBits(x);
        bits = 31L * bits + Double.doubleToLongBits(y);
        bits = 31L * bits + Double.doubleToLongBits(z);
        return (int) (bits ^ (bits >>> 32));
    }
}
