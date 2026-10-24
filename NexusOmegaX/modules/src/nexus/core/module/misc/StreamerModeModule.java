package nexus.core.module.misc;

import nexus.core.module.Module;
import nexus.core.module.ModuleCategory;
import nexus.core.setting.*;

/** Hides private information for screenshots, streams and videos. */
public final class StreamerModeModule extends Module {

    private final BooleanSetting hideName;
    private final BooleanSetting hideIp;
    private final BooleanSetting hideWaypoints;
    private final BooleanSetting hideCoords;

    public StreamerModeModule() {
        super("streamermode", "StreamerMode", "Hides private information while you stream.",
                ModuleCategory.MISC);
        this.hideName = bool("Hide Name", "Hide your player name", true);
        this.hideIp = bool("Hide Server", "Hide the server address", true);
        this.hideWaypoints = bool("Hide Waypoints", "Hide waypoint names", true);
        this.hideCoords = bool("Hide Coordinates", "Hide your coordinates", false);
    }

    public boolean hideName() {
        return isEnabled() && hideName.isEnabled();
    }

    public boolean hideServer() {
        return isEnabled() && hideIp.isEnabled();
    }

    public boolean hideWaypointText() {
        return isEnabled() && hideWaypoints.isEnabled();
    }

    public boolean hideCoordinates() {
        return isEnabled() && hideCoords.isEnabled();
    }

    public String mask(String text) {
        if (!isEnabled() || text == null) {
            return text;
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            sb.append('*');
        }
        return sb.toString();
    }
}
