package nexus.core.module.player;

import nexus.core.event.TickEvent;
import nexus.core.module.Module;
import nexus.core.module.ModuleCategory;
import nexus.core.setting.*;
import nexus.util.TimeUtil;

/** Crafts a selected item from the inventory crafting grid automatically. */
public final class AutoCraftModule extends Module {

    private final BooleanSetting useCraftingTable;
    private final NumberSetting delay;
    private final ModeSetting recipe;
    private final TimeUtil.Timer timer = new TimeUtil.Timer();

    public AutoCraftModule() {
        super("autocraft", "AutoCraft", "Crafts common items from your inventory automatically.",
                ModuleCategory.PLAYER);
        this.useCraftingTable = bool("Use Crafting Table", "Prefer a nearby crafting table", true);
        this.delay = number("Delay", "Delay between crafting steps", 500.0, 100.0, 5000.0, 100.0);
        this.recipe = mode("Recipe", "Recipe to craft", "Planks", "Planks", "Sticks", "Torches", "Crafting Table");
    }

    @Override
    public void onTick(TickEvent event) {
        if (!mc().isInGame() || !timer.hasPassed((long) delay.value(), true)) {
            return;
        }
        if (mc().interaction().openSyncId() < 0) {
            mc().screens().openInventoryScreen();
            return;
        }
        nexus.api.IInventory inventory = player().inventory();
        if (recipe.is("Planks")) {
            int logs = inventory.find("log");
            if (logs < 0) {
                return;
            }
            inventory.clickSlot(convertSlot(logs), 0, nexus.api.IInventory.ClickType.PICKUP);
            inventory.clickSlot(1, 0, nexus.api.IInventory.ClickType.PICKUP);
            inventory.clickSlot(0, 0, nexus.api.IInventory.ClickType.PICKUP_ALL);
        } else if (recipe.is("Sticks")) {
            int planks = inventory.find("planks");
            if (planks < 0) {
                return;
            }
            inventory.clickSlot(convertSlot(planks), 0, nexus.api.IInventory.ClickType.PICKUP);
            inventory.clickSlot(1, 0, nexus.api.IInventory.ClickType.PICKUP);
            inventory.clickSlot(4, 0, nexus.api.IInventory.ClickType.PICKUP);
            inventory.clickSlot(0, 0, nexus.api.IInventory.ClickType.PICKUP_ALL);
        } else if (recipe.is("Torches")) {
            int coal = inventory.find("coal");
            int sticks = inventory.find("stick");
            if (coal < 0 || sticks < 0) {
                return;
            }
            inventory.clickSlot(convertSlot(coal), 0, nexus.api.IInventory.ClickType.PICKUP);
            inventory.clickSlot(1, 0, nexus.api.IInventory.ClickType.PICKUP);
            inventory.clickSlot(convertSlot(sticks), 0, nexus.api.IInventory.ClickType.PICKUP);
            inventory.clickSlot(4, 0, nexus.api.IInventory.ClickType.PICKUP);
            inventory.clickSlot(0, 0, nexus.api.IInventory.ClickType.PICKUP_ALL);
        } else {
            int planks = inventory.find("planks");
            if (planks < 0) {
                return;
            }
            fillTwoByTwo(inventory, planks);
        }
        markActivity();
    }

    private void fillTwoByTwo(nexus.api.IInventory inventory, int source) {
        inventory.clickSlot(convertSlot(source), 0, nexus.api.IInventory.ClickType.PICKUP);
        for (int slot = 1; slot <= 4; slot++) {
            inventory.clickSlot(slot, 1, nexus.api.IInventory.ClickType.PICKUP);
        }
        inventory.clickSlot(0, 0, nexus.api.IInventory.ClickType.PICKUP_ALL);
    }

    /** Converts a player inventory slot into an opened inventory container slot. */
    private int convertSlot(int playerSlot) {
        return playerSlot + 9;
    }
}
