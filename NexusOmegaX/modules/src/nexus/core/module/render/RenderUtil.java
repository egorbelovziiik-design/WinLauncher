package nexus.core.module.render;

import nexus.api.IEntity;
import nexus.api.IWorld;
import nexus.core.event.Render3DEvent;
import nexus.core.setting.ColorSetting;
import nexus.util.ColorUtil;

/**
 * Shared rendering helpers used by every ESP / tracer / nametag module.
 * <p>
 * Keeping the drawing code in one place guarantees that all ESP modules behave
 * identically and keeps the per module code small and easy to review.
 */
public final class RenderUtil {

    /** Entity filter implemented by each module. */
    public interface EntityFilter {
        boolean accept(IEntity entity);
    }

    private RenderUtil() {
    }

    public static String[] espModes() {
        return new String[]{"2D", "3D", "BOX", "CORNER", "OUTLINE", "GLOW", "FILLED", "SKELETON"};
    }

    /** Renders every accepted entity with the requested ESP style. */
    public static void entities(Render3DEvent event, IWorld world, EntityFilter filter, String style,
                                ColorSetting color, double range, boolean tracers, double thickness,
                                boolean rainbow) {
        long now = System.currentTimeMillis();
        for (IEntity entity : world.entities()) {
            if (entity == null || !entity.isAlive() || entity.isLocalPlayer()) {
                continue;
            }
            if (!filter.accept(entity)) {
                continue;
            }
            if (entity.distanceToPlayer() > range) {
                continue;
            }
            int value = color.effectiveColor(now, entity.position().y / 40.0);
            draw(event, entity, style, value, thickness);
            if (tracers) {
                event.renderer.drawTracer(entity.position().add(0.0, entity.height() / 2.0, 0.0), value, thickness * 0.7);
            }
        }
    }

    public static void draw(Render3DEvent event, IEntity entity, String style, int color, double thickness) {
        if (style.equalsIgnoreCase("2D")) {
            nexus.api.math.ScreenPos position = event.renderer.project(entity.position().add(0.0, entity.height(), 0.0));
            if (position.visible) {
                double halfWidth = entity.width() * 6.0;
                double height = entity.height() * 12.0;
                event.renderer.outlineRect(position.x - halfWidth, position.y - height, halfWidth * 2.0, height,
                        thickness, color);
            }
        } else if (style.equalsIgnoreCase("FILLED")) {
            event.renderer.drawEntityBox(entity, ColorUtil.withAlpha(color, 55), color, true);
        } else if (style.equalsIgnoreCase("GLOW")) {
            event.renderer.drawGlow(entity, color, 1.05);
        } else if (style.equalsIgnoreCase("CORNER")) {
            event.renderer.drawCornerBox(entity.boundingBox(), color, 0.3, thickness);
        } else if (style.equalsIgnoreCase("OUTLINE")) {
            event.renderer.drawEntityBox(entity, 0x00000000, color, false);
        } else if (style.equalsIgnoreCase("SKELETON")) {
            event.renderer.drawSkeleton(entity, color, thickness);
        } else if (style.equalsIgnoreCase("3D")) {
            event.renderer.drawEntityBox(entity, ColorUtil.withAlpha(color, 28),
                    ColorUtil.withAlpha(color, 220), false);
        } else {
            event.renderer.drawEntityBox(entity, ColorUtil.withAlpha(color, 30), color, false);
        }
    }

    /** Renders blocks matching a filter inside a radius. */
    public static void blocks(Render3DEvent event, IWorld world, String blockPart, int color,
                              int radius, boolean filled, double thickness) {
        int playerX = (int) Math.floor(nexus.core.Nexus.instance().client().player().position().x);
        int playerY = (int) Math.floor(nexus.core.Nexus.instance().client().player().position().y);
        int playerZ = (int) Math.floor(nexus.core.Nexus.instance().client().player().position().z);
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                for (int dy = -radius; dy <= radius; dy++) {
                    int x = playerX + dx;
                    int y = playerY + dy;
                    int z = playerZ + dz;
                    if (y < world.minHeight() || y > world.maxHeight()) {
                        continue;
                    }
                    nexus.api.IBlockRef block = world.blockAt(x, y, z);
                    if (block == null || block.isAir() || !block.id().contains(blockPart)) {
                        continue;
                    }
                    nexus.api.math.Box3 box = nexus.api.math.Box3.block(x, y, z);
                    event.renderer.drawBox(box, filled ? ColorUtil.withAlpha(color, 45) : 0x00000000,
                            color, filled);
                }
            }
        }
    }
}
