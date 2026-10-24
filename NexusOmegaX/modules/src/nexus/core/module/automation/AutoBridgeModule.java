package nexus.core.module.automation;

import nexus.core.module.Module;
import nexus.core.module.ModuleCategory;
import nexus.core.setting.*;

/** Places blocks in front of your feet so you can walk over the void. */
public final class AutoBridgeModule extends Module {

    private final BooleanSetting onlyOverAir;
    private final BooleanSetting flatOnly;
    private final NumberSetting lookAhead;

    public AutoBridgeModule() {
        super("autobridge", "AutoBridge", "Places blocks in front of your feet.", ModuleCategory.AUTOMATION);
        this.onlyOverAir = bool("Only Over Air", "Only place over empty space", true);
        this.flatOnly = bool("Flat Only", "Only bridge on a flat level", false);
        this.lookAhead = number("Look Ahead", "Blocks ahead to place", 1.0, 1.0, 3.0, 1.0);
    }

    @Override
    public void onTick(nexus.core.event.TickEvent event) {
        if (!mc().isInGame()) {
            return;
        }
        if (!player().isOnGround() && flatOnly.isEnabled()) {
            return;
        }
        String facing = player().facing();
        int dirX = facing.contains("west") ? -1 : (facing.contains("east") ? 1 : 0);
        int dirZ = facing.contains("north") ? -1 : (facing.contains("south") ? 1 : 0);
        int x = player().blockX();
        int y = player().blockY();
        int z = player().blockZ();
        for (int step = 1; step <= (int) lookAhead.value(); step++) {
            int bx = x + dirX * step;
            int bz = z + dirZ * step;
            int by = y - 1;
            if (!mc().world().isAir(bx, by, bz) && onlyOverAir.isEnabled()) {
                continue;
            }
            int slot = player().inventory().findHotbar(new nexus.api.IInventory.Predicate<nexus.api.IItemStack>() {
                @Override
                public boolean test(nexus.api.IItemStack value) {
                    return value.isBlockItem();
                }
            });
            if (slot < 0) {
                return;
            }
            player().inventory().setSelectedSlot(slot);
            mc().interaction().placeBlock(bx, by, bz, 1);
            markActivity();
            return;
        }
    }
}
