package nexus.core.module.combat;

import nexus.api.IEntity;
import nexus.core.event.TickEvent;
import nexus.core.module.Module;
import nexus.core.module.ModuleCategory;
import nexus.core.setting.*;
import nexus.util.TimeUtil;

/** Attacks automatically as soon as a target is under the crosshair. */
public final class TriggerBotModule extends Module {

    private final NumberSetting range;
    private final BooleanSetting players;
    private final BooleanSetting mobs;
    private final BooleanSetting requireCooldown;
    private final NumberSetting delay;
    private final TimeUtil.Timer timer = new TimeUtil.Timer();

    public TriggerBotModule() {
        super("triggerbot", "TriggerBot", "Attacks automatically when a target is under the crosshair.",
                ModuleCategory.COMBAT);
        this.range = number("Range", "Maximum attack distance", 3.6, 1.0, 6.0, 0.1);
        this.players = bool("Players", "Trigger on players", true);
        this.mobs = bool("Mobs", "Trigger on mobs", true);
        this.requireCooldown = bool("Wait Cooldown", "Wait for the attack cooldown", true);
        this.delay = number("Delay", "Minimum delay between attacks in ms", 120.0, 0.0, 1000.0, 10.0);
    }

    @Override
    public void onEnable() {
        timer.reset();
    }

    @Override
    public void onTick(TickEvent event) {
        if (!mc().isInGame() || !timer.hasPassed((long) delay.value())) {
            return;
        }
        IEntity target = mc().crosshairTarget();
        if (target == null || !target.isAlive() || target.distanceToPlayer() > range.value()) {
            return;
        }
        if (target.isPlayer() && !players.isEnabled()) {
            return;
        }
        if (!target.isPlayer() && !mobs.isEnabled()) {
            return;
        }
        if (requireCooldown.isEnabled() && player().attackCooldown() < 0.95) {
            return;
        }
        mc().interaction().attackEntity(target);
        player().swingHand();
        timer.reset();
        markActivity();
    }
}
