package nexus.core.module.misc;

import nexus.core.event.DeathEvent;
import nexus.core.module.Module;
import nexus.core.module.ModuleCategory;
import nexus.core.setting.*;

/** Saves a waypoint at the place where you died. */
public final class DeathCoordinatesModule extends Module {

    private final BooleanSetting waypoint;
    private final BooleanSetting chatMessage;
    private final ColorSetting color;
    private String lastDeath = "-";

    public DeathCoordinatesModule() {
        super("deathcoordinates", "DeathCoordinates", "Saves the coordinates where you died.",
                ModuleCategory.MISC);
        this.waypoint = bool("Waypoint", "Create a waypoint at the death location", true);
        this.chatMessage = bool("Chat", "Print the coordinates in the chat", true);
        this.color = color("Color", "Waypoint color", 0xFFEF4444);
    }

    @Override
    public void onDeath(DeathEvent event) {
        if (!isEnabled()) {
            return;
        }
        int x = player().blockX();
        int y = player().blockY();
        int z = player().blockZ();
        lastDeath = x + ", " + y + ", " + z;
        if (waypoint.isEnabled()) {
            nexus.core.Nexus.instance().waypoints().add("Death " + nexus.util.TimeUtil.formatClock(System.currentTimeMillis()),
                    x, y, z, mc().world().dimensionId(), color.effectiveColor(System.currentTimeMillis()));
        }
        if (chatMessage.isEnabled()) {
            mc().chat().print("You died at " + lastDeath + " (" + event.cause + ")");
        }
        markActivity();
    }

    @Override
    public String hudInfo() {
        return lastDeath;
    }
}
