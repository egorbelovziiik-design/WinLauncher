package nexus.core.module.automation;

import nexus.core.module.Module;
import nexus.core.module.ModuleCategory;
import nexus.core.setting.*;

/** Mines blocks in front of you automatically when you hold the bind. */
public final class AutoMinerModule extends Module {

    private final ModeSetting shape;
    private final NumberSetting range;
    private final BooleanSetting requirePickaxe;
    private final BooleanSetting rotate;
    private final nexus.util.TimeUtil.Timer timer = new nexus.util.TimeUtil.Timer();

    public AutoMinerModule() {
        super("automine", "AutoMine", "Mines blocks in front of you while you hold the bind.",
                ModuleCategory.AUTOMATION);
        this.shape = mode("Shape", "Mining shape", "Tunnel", "Forward", "Tunnel", "Stairs", "Vein");
        this.range = number("Range", "Mining range in blocks", 4.0, 1.0, 6.0, 1.0);
        this.requirePickaxe = bool("Require Pickaxe", "Only mine when holding a pickaxe", true);
        this.rotate = bool("Rotate", "Look at the block while mining", true);
    }

    @Override
    public void onEnable() {
        timer.reset();
    }

    @Override
    public void onDisable() {
        mc().interaction().stopBreaking();
    }

    @Override
    public void cleanup() {
        mc().interaction().stopBreaking();
    }

    @Override
    public void onTick(nexus.core.event.TickEvent event) {
        if (!mc().isInGame()) {
            return;
        }
        if (requirePickaxe.isEnabled() && !player().inventory().heldItem().isTool()) {
            return;
        }
        if (!mc().input().isKeyDown(keybindSetting().key())) {
            mc().interaction().stopBreaking();
            return;
        }
        int[] target = nextTarget();
        if (target == null) {
            mc().interaction().stopBreaking();
            return;
        }
        if (rotate.isEnabled()) {
            mc().interaction().lookAtBlock(target[0], target[1], target[2]);
        }
        mc().interaction().breakBlock(target[0], target[1], target[2]);
        markActivity();
    }

    private int[] nextTarget() {
        int x = player().blockX();
        int y = player().blockY();
        int z = player().blockZ();
        String facing = player().facing();
        int dirX = facing.contains("west") ? -1 : (facing.contains("east") ? 1 : 0);
        int dirZ = facing.contains("north") ? -1 : (facing.contains("south") ? 1 : 0);
        for (int step = 1; step <= (int) range.value(); step++) {
            int baseY = shape.is("Stairs") ? y + step - 1 : y;
            for (int dy = 0; dy <= (shape.is("Forward") ? 0 : 1); dy++) {
                for (int side = shape.is("Forward") ? 0 : -1; side <= 1; side++) {
                    int bx = x + dirX * step + (dirX == 0 ? side : 0);
                    int bz = z + dirZ * step + (dirZ == 0 ? side : 0);
                    int by = baseY + dy;
                    if (!mc().world().isAir(bx, by, bz)) {
                        return new int[]{bx, by, bz};
                    }
                }
            }
        }
        return null;
    }

    @Override
    public String hudInfo() {
        return shape.get();
    }
}
