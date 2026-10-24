package nexus.core.module.misc;

import nexus.api.IEntity;
import nexus.core.event.Render2DEvent;
import nexus.core.module.Module;
import nexus.core.module.ModuleCategory;
import nexus.core.setting.*;

import java.util.ArrayList;
import java.util.List;

/** Highlights players that are likely staff members (vanished or special names). */
public final class StaffDetectorModule extends Module {

    private final MultiSelectSetting clues;
    private final BooleanSetting notify;
    private final List<String> detected = new ArrayList<String>();

    public StaffDetectorModule() {
        super("staffdetector", "StaffDetector", "Highlights players that look like staff members.",
                ModuleCategory.MISC);
        this.clues = multi("Clues", "What counts as a clue", new String[]{"Vanished", "Spectator",
                "No Items", "Name Prefix"}, "Vanished", "Spectator");
        this.notify = bool("Notify", "Show a notification when someone is detected", true);
    }

    @Override
    public void onRender2D(Render2DEvent event) {
        if (!mc().isInGame()) {
            return;
        }
        for (IEntity entity : mc().world().players()) {
            if (entity.isLocalPlayer() || !entity.isAlive()) {
                continue;
            }
            boolean vanished = clues.isSelected("Vanished") && entity.isInvisible();
            boolean spectator = clues.isSelected("Spectator") && entity.isSpectator();
            boolean noItems = clues.isSelected("No Items") && entity.heldItem() != null
                    && entity.heldItem().isEmpty();
            boolean prefix = clues.isSelected("Name Prefix") && entity.name() != null
                    && (entity.name().startsWith("[") || entity.name().startsWith("<"));
            if (!vanished && !spectator && !noItems && !prefix) {
                continue;
            }
            if (!detected.contains(entity.name())) {
                detected.add(entity.name());
                if (notify.isEnabled()) {
                    nexus.core.Nexus.instance().notifications().warn("StaffDetector",
                            "Possible staff: " + entity.name());
                }
            }
        }
    }

    public List<String> detected() {
        return detected;
    }

    @Override
    public void onDisable() {
        detected.clear();
    }

    @Override
    public String hudInfo() {
        return detected.size() + " found";
    }
}
