package nexus.core.hud.elements;

import nexus.api.IConnection;
import nexus.core.Nexus;
import nexus.core.event.Render2DEvent;
import nexus.core.hud.HudElement;
import nexus.core.setting.BooleanSetting;

/** Server address, player count, protocol and brand. */
public final class ServerInfoElement extends HudElement {

    private final BooleanSetting showPlayers;
    private final BooleanSetting showBrand;
    private final BooleanSetting showProtocol;

    public ServerInfoElement() {
        super("serverinfo", "ServerInfo");
        this.showPlayers = new BooleanSetting("Show Players", "Show the player count", true);
        this.showBrand = new BooleanSetting("Show Brand", "Show the server brand", false);
        this.showProtocol = new BooleanSetting("Show Protocol", "Show the protocol version", false);
    }

    private String[] lines() {
        if (!Nexus.instance().client().isInGame()) {
            return new String[]{"Not connected"};
        }
        IConnection connection = Nexus.instance().client().connection();
        java.util.List<String> lines = new java.util.ArrayList<String>();
        lines.add(connection.isSingleplayer() ? "Singleplayer" : connection.serverAddress());
        if (showPlayers.isEnabled() && !connection.isSingleplayer()) {
            lines.add("Players " + connection.playerCount() + "/" + connection.maxPlayers());
        }
        if (showBrand.isEnabled() && connection.serverBrand() != null && !connection.serverBrand().isEmpty()) {
            lines.add("Brand " + connection.serverBrand());
        }
        if (showProtocol.isEnabled()) {
            lines.add("Protocol " + connection.protocol());
        }
        return lines.toArray(new String[0]);
    }

    @Override
    public double width() {
        double max = 0.0;
        for (String line : lines()) {
            max = Math.max(max, textWidth(line, scale()));
        }
        return max;
    }

    @Override
    public double height() {
        return lines().length * lineHeight();
    }

    @Override
    protected void draw(Render2DEvent event, double x, double y) {
        String[] lines = lines();
        for (int i = 0; i < lines.length; i++) {
            event.renderer.drawText(lines[i], x, y + i * lineHeight(), color(System.currentTimeMillis()), shadow(), scale());
        }
    }

    @Override
    public java.util.List<nexus.core.setting.Setting<?>> settings() {
        java.util.List<nexus.core.setting.Setting<?>> list = super.settings();
        list.add(showPlayers);
        list.add(showBrand);
        list.add(showProtocol);
        return list;
    }

    @Override
    public String previewText() {
        return "hypixel.net";
    }
}
