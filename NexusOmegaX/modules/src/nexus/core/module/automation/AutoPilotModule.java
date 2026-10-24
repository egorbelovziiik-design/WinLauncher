package nexus.core.module.automation;

import nexus.core.module.Module;
import nexus.core.module.ModuleCategory;
import nexus.core.setting.*;
import nexus.core.automation.MacroManager;

/** Walks in a straight line or towards a waypoint without holding a key. */
public final class AutoPilotModule extends Module {

    private final ModeSetting target;
    private final StringSetting waypoint;
    private final BooleanSetting sprint;
    private final BooleanSetting jump;

    public AutoPilotModule() {
        super("autopilot", "AutoPilot", "Walks in a direction or towards a waypoint.",
                ModuleCategory.AUTOMATION);
        this.target = mode("Target", "Where to walk", "Forward", "Forward", "Waypoint", "Spawn");
        this.waypoint = text("Waypoint", "Waypoint name to walk to", "base");
        this.sprint = bool("Sprint", "Sprint while walking", true);
        this.jump = bool("Jump", "Jump over obstacles", true);
    }

    @Override
    public void onEnable() {
        mc().input().setKeyState(MacroManager.KeyMappingKeys.FORWARD, true);
        if (sprint.isEnabled()) {
            player().setSprinting(true);
        }
    }

    @Override
    public void onDisable() {
        mc().input().setKeyState(MacroManager.KeyMappingKeys.FORWARD, false);
        mc().input().setKeyState(MacroManager.KeyMappingKeys.JUMP, false);
        player().setSprinting(false);
    }

    @Override
    public void cleanup() {
        onDisable();
    }

    @Override
    public void onTick(nexus.core.event.TickEvent event) {
        if (!mc().isInGame()) {
            return;
        }
        nexus.api.math.Vec3d destination = destination();
        if (destination != null) {
            player().lookAt(destination);
            if (player().position().distanceTo(destination) < 2.0) {
                nexus.core.Nexus.instance().notifications().success("AutoPilot", "Destination reached");
                setEnabled(false, true);
                return;
            }
        }
        if (jump.isEnabled() && player().isOnGround()
                && mc().world().isSolid(player().blockX() + (int) Math.round(Math.sin(Math.toRadians(player().yaw())) * -1),
                        player().blockY(), player().blockZ() + (int) Math.round(Math.cos(Math.toRadians(player().yaw()))))) {
            player().jump();
        }
        markActivity();
    }

    private nexus.api.math.Vec3d destination() {
        if (target.is("Spawn")) {
            return new nexus.api.math.Vec3d(mc().world().spawnX(), player().blockY(), mc().world().spawnZ());
        }
        if (target.is("Waypoint")) {
            nexus.core.world.WaypointManager.Waypoint point =
                    nexus.core.Nexus.instance().waypoints().byName(waypoint.rawValue());
            if (point != null) {
                return new nexus.api.math.Vec3d(point.x(), point.y(), point.z());
            }
        }
        return null;
    }

    @Override
    public String hudInfo() {
        return target.get();
    }
}
