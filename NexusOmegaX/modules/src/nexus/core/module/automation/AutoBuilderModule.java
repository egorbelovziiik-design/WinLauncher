package nexus.core.module.automation;

import nexus.core.module.Module;
import nexus.core.module.ModuleCategory;
import nexus.core.setting.*;

/** Places blocks under you to build a bridge or platform automatically. */
public final class AutoBuilderModule extends Module {

    private final ModeSetting pattern;
    private final NumberSetting width;
    private final BooleanSetting useAnyBlock;
    private final BooleanSetting sneakAtEdge;

    public AutoBuilderModule() {
        super("autobuilder", "AutoBuilder", "Places blocks automatically while you move.",
                ModuleCategory.AUTOMATION);
        this.pattern = mode("Pattern", "Building pattern", "Bridge", "Bridge", "Platform", "Wall", "Floor");
        this.width = number("Width", "Width in blocks", 3.0, 1.0, 7.0, 2.0);
        this.useAnyBlock = bool("Any Block", "Use any placeable block", true);
        this.sneakAtEdge = bool("Safe Mode", "Only place when you stand still", false);
    }

    @Override
    public void onTick(nexus.core.event.TickEvent event) {
        if (!mc().isInGame()) {
            return;
        }
        if (sneakAtEdge.isEnabled() && player().horizontalSpeed() > 0.05) {
            return;
        }
        int x = player().blockX();
        int y = player().blockY();
        int z = player().blockZ();
        int half = (int) (width.value() / 2.0);
        for (int dx = -half; dx <= half; dx++) {
            for (int dz = -half; dz <= half; dz++) {
                int targetY = pattern.is("Wall") ? y : y - 1;
                if (pattern.is("Bridge") && Math.abs(dx) > 0 && Math.abs(dz) > 0) {
                    continue;
                }
                int bx = x + dx;
                int bz = z + dz;
                if (!mc().world().isAir(bx, targetY, bz)) {
                    continue;
                }
                if (!selectBlock()) {
                    return;
                }
                mc().interaction().placeBlock(bx, targetY, bz, 1);
                markActivity();
                return;
            }
        }
    }

    private boolean selectBlock() {
        int slot = useAnyBlock.isEnabled()
                ? player().inventory().find(new nexus.api.IInventory.Predicate<nexus.api.IItemStack>() {
                    @Override
                    public boolean test(nexus.api.IItemStack value) {
                        return value.isBlockItem();
                    }
                })
                : player().inventory().findHotbar("");
        if (slot < 0) {
            return false;
        }
        if (slot < 9) {
            player().inventory().setSelectedSlot(slot);
        } else {
            player().inventory().swapWithHotbar(slot, player().inventory().selectedSlot());
        }
        return true;
    }

    @Override
    public String hudInfo() {
        return pattern.get();
    }
}
