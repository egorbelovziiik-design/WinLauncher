package nexus.core.hud.elements;

import nexus.api.IEntity;
import nexus.core.Nexus;
import nexus.core.event.Render2DEvent;
import nexus.core.hud.HudElement;
import nexus.core.setting.BooleanSetting;
import nexus.core.setting.NumberSetting;
import nexus.util.ColorUtil;

/** Circular minimap radar showing nearby entities around the player. */
public final class RadarElement extends HudElement {

    private final NumberSetting radiusSetting;
    private final NumberSetting radarSize;
    private final BooleanSetting showMobs;
    private final BooleanSetting showPlayers;
    private final BooleanSetting rotate;

    public RadarElement() {
        super("radar", "Radar");
        this.radiusSetting = new NumberSetting("Range", "World range in blocks", 48.0, 8.0, 256.0, 1.0);
        this.radarSize = new NumberSetting("Size", "Radar diameter", 78.0, 40.0, 200.0, 1.0);
        this.showMobs = new BooleanSetting("Show Mobs", "Show mobs on the radar", true);
        this.showPlayers = new BooleanSetting("Show Players", "Show players on the radar", true);
        this.rotate = new BooleanSetting("Rotate", "Rotate with the player view", true);
    }

    @Override
    public double width() {
        return radarSize.value() * scale();
    }

    @Override
    public double height() {
        return radarSize.value() * scale();
    }

    @Override
    protected void draw(Render2DEvent event, double x, double y) {
        if (!Nexus.instance().client().isInGame()) {
            return;
        }
        double size = width();
        double centerX = x + size / 2.0;
        double centerY = y + size / 2.0;
        double radius = size / 2.0;
        int accent = color(System.currentTimeMillis());
        nexus.api.IPlayer player = Nexus.instance().client().player();

        event.renderer.circle(centerX, centerY, radius, ColorUtil.withAlpha(0xFF08080D, 150));
        event.renderer.circleOutline(centerX, centerY, radius, 1.0, ColorUtil.withAlpha(accent, 120));
        event.renderer.circleOutline(centerX, centerY, radius * 0.5, 0.75, ColorUtil.withAlpha(accent, 60));
        event.renderer.line(centerX - radius, centerY, centerX + radius, centerY, 0.75, ColorUtil.withAlpha(accent, 45));
        event.renderer.line(centerX, centerY - radius, centerX, centerY + radius, 0.75, ColorUtil.withAlpha(accent, 45));

        double range = radiusSetting.value();
        float yawRotation = rotate.isEnabled() ? player.yaw() : 0.0f;
        double rad = Math.toRadians(yawRotation);
        double sin = Math.sin(rad);
        double cos = Math.cos(rad);

        for (IEntity entity : Nexus.instance().client().world().entities()) {
            if (entity.isLocalPlayer() || !entity.isAlive()) {
                continue;
            }
            boolean isPlayer = entity.isPlayer();
            if (isPlayer && !showPlayers.isEnabled()) {
                continue;
            }
            if (!isPlayer && !showMobs.isEnabled()) {
                continue;
            }
            double distance = entity.distanceToPlayer();
            if (distance > range) {
                continue;
            }
            double dx = entity.position().x - player.position().x;
            double dz = entity.position().z - player.position().z;
            double rotatedX = dx * cos - dz * sin;
            double rotatedZ = dx * sin + dz * cos;
            double px = centerX + (rotatedX / range) * radius;
            double py = centerY + (rotatedZ / range) * radius;
            if (Math.sqrt(Math.pow(px - centerX, 2) + Math.pow(py - centerY, 2)) > radius) {
                continue;
            }
            int dot = Nexus.instance().friends().isFriend(entity.name())
                    ? 0xFF4ADE80
                    : (Nexus.instance().friends().isEnemy(entity.name()) ? 0xFFF87171
                    : (isPlayer ? 0xFFC084FC : 0xFFFBBF24));
            event.renderer.circle(px - 1.5, py - 1.5, 3.0, dot);
        }
        // player marker
        event.renderer.triangle(centerX - 3.0, centerY - 4.0, centerX + 3.0, centerY - 4.0, centerX, centerY + 4.0, 0xFFF5F3FF);
    }

    @Override
    public java.util.List<nexus.core.setting.Setting<?>> settings() {
        java.util.List<nexus.core.setting.Setting<?>> list = super.settings();
        list.add(radiusSetting);
        list.add(radarSize);
        list.add(showMobs);
        list.add(showPlayers);
        list.add(rotate);
        return list;
    }

    @Override
    public String previewText() {
        return "(radar)";
    }
}
