package nexus.api;

import nexus.api.math.Box3;
import nexus.api.math.ScreenPos;
import nexus.api.math.Vec3d;

/**
 * Rendering bridge. Every Nexus visual feature (ClickGUI, HUD, ESP, tracers,
 * waypoints, particles) draws exclusively through this interface, so the very
 * same drawing code runs unmodified on Minecraft 1.16.5, 1.21.4 and 1.21.11.
 * <p>
 * All 2D coordinates are in scaled GUI pixels.
 */
public interface IRenderer {

    /** Called once per frame before any Nexus drawing happens. */
    void begin(float deltaMs);

    /** Called after all Nexus drawing for this frame. */
    void end();

    // ---------------------------------------------------------------- 2D shapes

    void fillRect(double x, double y, double width, double height, int color);

    void fillGradientRect(double x, double y, double width, double height, int startColor, int endColor, boolean vertical);

    void fillGradientRound(double x, double y, double width, double height, double radius, int startColor, int endColor, boolean vertical);

    void fillRoundRect(double x, double y, double width, double height, double radius, int color);

    void outlineRect(double x, double y, double width, double height, double thickness, int color);

    void outlineRoundRect(double x, double y, double width, double height, double radius, double thickness, int color);

    void line(double x1, double y1, double x2, double y2, double thickness, int color);

    void triangle(double x1, double y1, double x2, double y2, double x3, double y3, int color);

    void circle(double x, double y, double radius, int color);

    void circleOutline(double x, double y, double radius, double thickness, int color);

    /** Rounded rectangle with a soft shadow (glass panels, notifications). */
    void shadow(double x, double y, double width, double height, double radius, double blur, int color);

    /** Fast approximate blur of the already rendered backdrop (glassmorphism). */
    void blurRegion(double x, double y, double width, double height, double strength);

    // ------------------------------------------------------------------- text

    void drawText(String text, double x, double y, int color, boolean shadow);

    void drawText(String text, double x, double y, int color, boolean shadow, double scale);

    void drawCenteredText(String text, double x, double y, int color, boolean shadow, double scale);

    double textWidth(String text, double scale);

    double fontHeight(double scale);

    void drawItem(IItemStack stack, double x, double y, double scale);

    void drawItemWithCount(IItemStack stack, double x, double y, double scale);

    void drawTooltip(String[] lines, double x, double y);

    // -------------------------------------------------------------- clipping

    void pushScissor(double x, double y, double width, double height);

    void popScissor();

    // ------------------------------------------------------------------ 3D

    /** Projects a world position to screen space (GUI coordinates). */
    ScreenPos project(Vec3d worldPosition);

    void drawBox(Box3 box, int fillColor, int lineColor, boolean filled);

    void drawCornerBox(Box3 box, int color, double cornerLength, double thickness);

    void drawLine3D(Vec3d from, Vec3d to, int color, double thickness);

    /** Draws a line from the bottom center of the screen to a world position. */
    void drawTracer(Vec3d worldPosition, int color, double thickness);

    void drawNametag(Vec3d worldPosition, String text, int color, double scale, boolean shadow, int backgroundColor);

    void drawEntityBox(IEntity entity, int fillColor, int lineColor, boolean filled);

    void drawSkeleton(IEntity entity, int color, double thickness);

    /** Fills the entity silhouette with a glowing (depth-ignoring) color. */
    void drawGlow(IEntity entity, int color, double scale);

    /** Draws a flat filled quad in world space (chunk borders, light overlay). */
    void drawWorldQuad(Vec3d a, Vec3d b, Vec3d c, Vec3d d, int color);

    /** Draws a small billboard point in world space (used for particles). */
    void drawWorldPoint(Vec3d position, double size, int color);
}
