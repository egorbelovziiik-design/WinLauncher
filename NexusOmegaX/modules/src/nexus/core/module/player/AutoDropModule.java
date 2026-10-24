package nexus.core.module.player;

import nexus.api.IInventory;
import nexus.api.IItemStack;
import nexus.core.event.TickEvent;
import nexus.core.module.Module;
import nexus.core.module.ModuleCategory;
import nexus.core.setting.*;
import nexus.util.TimeUtil;

/** Drops unwanted items automatically. */
public final class AutoDropModule extends Module {

    private final MultiSelectSetting blacklist;
    private final BooleanSetting keepTools;
    private final NumberSetting delay;
    private final TimeUtil.Timer timer = new TimeUtil.Timer();

    public AutoDropModule() {
        super("autodrop", "AutoDrop", "Drops unwanted items automatically.", ModuleCategory.PLAYER);
        this.blacklist = multi("Drop", "Items to drop", new String[]{"Cobblestone", "Dirt", "Gravel",
                "Rotten Flesh", "Seeds", "Flint", "String"}, "Cobblestone", "Dirt", "Gravel");
        this.keepTools = bool("Keep Tools", "Never drop tools and armor", true);
        this.delay = number("Delay", "Delay between drops in ms", 250.0, 0.0, 2000.0, 50.0);
    }

    @Override
    public void onTick(TickEvent event) {
        if (!mc().isInGame() || !timer.hasPassed((long) delay.value(), true)) {
            return;
        }
        IInventory inventory = player().inventory();
        for (int slot = 0; slot < IInventory.MAIN_SIZE; slot++) {
            IItemStack stack = inventory.stack(slot);
            if (stack == null || stack.isEmpty()) {
                continue;
            }
            if (keepTools.isEnabled() && (stack.isTool() || stack.isArmor() || stack.isWeapon())) {
                continue;
            }
            if (matches(stack)) {
                inventory.drop(slot, true);
                markActivity();
                return;
            }
        }
    }

    private boolean matches(IItemStack stack) {
        String id = stack.id().toLowerCase();
        for (String selected : blacklist.selected()) {
            String needle = selected.toLowerCase().replace(" ", "_");
            if (id.contains(needle)) {
                return true;
            }
        }
        return false;
    }
}
