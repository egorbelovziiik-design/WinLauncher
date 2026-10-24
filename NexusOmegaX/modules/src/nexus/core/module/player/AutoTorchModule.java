package nexus.core.module.player;

import nexus.core.event.TickEvent;
import nexus.core.module.Module;
import nexus.core.module.ModuleCategory;
import nexus.core.setting.*;
import nexus.util.TimeUtil;

/** Places torches in dark places while you move. */
public final class AutoTorchModule extends Module {

    private final NumberSetting lightLevel;
    private final NumberSetting distance;
    private final BooleanSetting preventMobSpawns;
    private final TimeUtil.Timer timer = new TimeUtil.Timer();

    public AutoTorchModule() {
        super("autotorch", "AutoTorch", "Places torches automatically in dark areas.",
                ModuleCategory.PLAYER);
        this.lightLevel = number("Light Level", "Place torches below this light level", 7.0, 0.0, 15.0, 1.0);
        this.distance = number("Distance", "Place a torch every N blocks", 6.0, 2.0, 16.0, 1.0);
        this.preventMobSpawns = bool("Spawn Proof", "Prioritize places where mobs could spawn", true);
    }

    @Override
    public void onTick(TickEvent event) {
        if (!mc().isInGame() || !timer.hasPassed(400L, true)) {
            return;
        }
        int torches = player().inventory().findHotbar("torch");
        if (torches < 0) {
            return;
        }
        int x = player().blockX();
        int y = player().blockY();
        int z = player().blockZ();
        if (mc().world().lightLevel(x, y, z) > lightLevel.value()) {
            return;
        }
        if (!mc().world().isAir(x, y, z) || !mc().world().isSolid(x, y - 1, z)) {
            return;
        }
        if (lastTorchX != 0 && Math.abs(x - lastTorchX) < distance.value()
                && Math.abs(z - lastTorchZ) < distance.value()) {
            return;
        }
        int previous = player().inventory().selectedSlot();
        player().inventory().setSelectedSlot(torches);
        mc().interaction().lookAtBlock(x, y - 1, z);
        mc().interaction().placeBlock(x, y - 1, z, 1);
        player().inventory().setSelectedSlot(previous);
        lastTorchX = x;
        lastTorchZ = z;
        markActivity();
    }

    private int lastTorchX;
    private int lastTorchZ;

    @Override
    public void onEnable() {
        lastTorchX = 0;
        lastTorchZ = 0;
        timer.reset();
    }
}
