package nexus.api.math;

/** Result of projecting a world position onto the screen. */
public final class ScreenPos {

    public final double x;
    public final double y;
    public final double distance;
    public final boolean visible;

    public ScreenPos(double x, double y, double distance, boolean visible) {
        this.x = x;
        this.y = y;
        this.distance = distance;
        this.visible = visible;
    }

    public static ScreenPos offScreen(double distance) {
        return new ScreenPos(-1.0, -1.0, distance, false);
    }

    @Override
    public String toString() {
        return String.format("ScreenPos(%.1f, %.1f, dist %.1f, visible=%b)", x, y, distance, visible);
    }
}
