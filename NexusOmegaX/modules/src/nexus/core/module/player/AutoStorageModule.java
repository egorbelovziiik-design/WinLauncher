package nexus.core.module.player;

import nexus.api.IInventory;
import nexus.core.event.TickEvent;
import nexus.core.module.Module;
import nexus.core.module.ModuleCategory;
import nexus.core.setting.*;
import nexus.util.TimeUtil;

/** Stores everything except a keep list into a nearby container. */
public final class AutoStorageModule extends Module {

    private final MultiSelectSetting keep;
    private final NumberSetting delay;
    private final TimeUtil.Timer timer = new TimeUtil.Timer();

    public AutoStorageModule() {
        super("autostorage", "AutoStorage", "Stores items into an open container, keeping your essentials.",
                ModuleCategory.PLAYER);
        this.keep = multi("Keep", "Items to keep", new String[]{"Tools", "Weapons", "Armor", "Food",
                "Blocks", "Torches", "Pickaxe"}, "Tools", "Weapons", "Armor", "Food");
        this.delay = number("Delay", "Delay between transfers in ms", 180.0, 50.0, 2000.0, 10.0);
    }

    @Override
    public void onTick(TickEvent event) {
        if (!mc().isInGame() || !timer.hasPassed((long) delay.value(), true)) {
            return;
        }
        if (mc().interaction().openSyncId() < 0) {
            return;
        }
        IInventory inventory = player().inventory();
        for (int slot = 9; slot < IInventory.MAIN_SIZE; slot++) {
            nexus.api.IItemStack stack = inventory.stack(slot);
            if (stack == null || stack.isEmpty() || shouldKeep(stack)) {
                continue;
            }
            inventory.clickSlot(slot + 9, 0, IInventory.ClickType.QUICK_MOVE);
            markActivity();
            return;
        }
    }

    private boolean shouldKeep(nexus.api.IItemStack stack) {
        for (String selected : keep.selected()) {
            String needle = selected.toLowerCase();
            if (needle.equals("tools") && stack.isTool()) {
                return true;
            }
            if (needle.equals("weapons") && stack.isWeapon()) {
                return true;
            }
            if (needle.equals("armor") && stack.isArmor()) {
                return true;
            }
            if (needle.equals("food") && stack.isFood()) {
                return true;
            }
            if (needle.equals("blocks") && stack.isBlockItem()) {
                return true;
            }
            if (stack.id().contains(needle)) {
                return true;
            }
        }
        return false;
    }
}
