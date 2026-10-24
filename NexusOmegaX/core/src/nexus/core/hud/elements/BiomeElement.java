package nexus.core.hud.elements;

import nexus.core.Nexus;
import nexus.core.event.Render2DEvent;
import nexus.core.hud.HudElement;
import nexus.core.setting.BooleanSetting;

/** Current biome name and light level. */
public final class BiomeElement extends HudElement {

    private final BooleanSetting showLight;

    public BiomeElement() {
        super("biome", "Biome");
        this.showLight = new BooleanSetting("Show Light", "Show the local light level", true);
    }

    private String text() {
        if (!Nexus.instance().client().isInGame()) {
            return "Biome -";
        }
        nexus.api.IPlayer player = Nexus.instance().client().player();
        String biome = Nexus.instance().client().world().biomeAt(player.blockX(), player.blockZ());
        if (!showLight.isEnabled()) {
            return biome;
        }
        int light = Nexus.instance().client().world().lightLevel(player.blockX(), player.blockY(), player.blockZ());
        return biome + " (" + light + " light)";
    }

    @Override
    public double width() {
        return textWidth(text(), scale());
    }

    @Override
    public double height() {
        return fontHeight(scale());
    }

    @Override
    protected void draw(Render2DEvent event, double x, double y) {
        event.renderer.drawText(text(), x, y, color(System.currentTimeMillis()), shadow(), scale());
    }

    @Override
    public java.util.List<nexus.core.setting.Setting<?>> settings() {
        java.util.List<nexus.core.setting.Setting<?>> list = super.settings();
        list.add(showLight);
        return list;
    }

    @Override
    public String previewText() {
        return "Plains (12 light)";
    }
}
