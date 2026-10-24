package nexus.core.module.misc;

import nexus.core.module.Module;
import nexus.core.module.ModuleCategory;
import nexus.core.setting.*;

/** Keeps your character active so idle kickers do not disconnect you. */
public final class AntiAfkModule extends Module {

    private final ModeSetting action;
    private final NumberSetting interval;
    private final BooleanSetting onlyWhenIdle;
    private final nexus.util.TimeUtil.Timer timer = new nexus.util.TimeUtil.Timer();
    private int step;

    public AntiAfkModule() {
        super("antiafk", "AntiAFK", "Keeps your character active while you are away.",
                ModuleCategory.MISC);
        this.action = mode("Action", "What to do", "Jump", "Jump", "Rotate", "Swing", "Sneak", "Walk");
        this.interval = number("Interval", "Delay between actions in ms", 30000.0, 1000.0, 600000.0, 1000.0);
        this.onlyWhenIdle = bool("Only When Idle", "Only act when you are not moving", true);
    }

    @Override
    public void onEnable() {
        timer.reset();
        step = 0;
    }

    @Override
    public void onTick(nexus.core.event.TickEvent event) {
        if (!mc().isInGame() || !timer.hasPassed((long) interval.value(), true)) {
            return;
        }
        if (onlyWhenIdle.isEnabled() && player().horizontalSpeed() > 0.02) {
            return;
        }
        if (action.is("Jump")) {
            player().jump();
        } else if (action.is("Rotate")) {
            player().setRotation(player().yaw() + 25.0f, player().pitch());
        } else if (action.is("Swing")) {
            player().swingHand();
        } else if (action.is("Sneak")) {
            player().setSneaking(step % 2 == 0);
        } else {
            mc().input().setKeyState(nexus.core.automation.MacroManager.KeyMappingKeys.FORWARD, step % 2 == 0);
        }
        step++;
        markActivity();
    }

    @Override
    public void onDisable() {
        mc().input().setKeyState(nexus.core.automation.MacroManager.KeyMappingKeys.FORWARD, false);
        if (action.is("Sneak")) {
            player().setSneaking(false);
        }
    }

    @Override
    public void cleanup() {
        onDisable();
    }

    @Override
    public String hudInfo() {
        return action.get();
    }
}
