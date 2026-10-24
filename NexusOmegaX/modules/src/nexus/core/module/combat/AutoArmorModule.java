package nexus.core.module.combat;

import nexus.api.IInventory;
import nexus.api.IItemStack;
import nexus.core.event.TickEvent;
import nexus.core.module.Module;
import nexus.core.module.ModuleCategory;
import nexus.core.setting.*;
import nexus.util.TimeUtil;

/** Equips the best available armor pieces automatically. */
public final class AutoArmorModule extends Module {

    private static final int[] ARMOR_SLOTS = new int[]{IInventory.BOOTS_SLOT, IInventory.LEGGINGS_SLOT,
            IInventory.CHESTPLATE_SLOT, IInventory.HELMET_SLOT};

    private final NumberSetting delay;
    private final BooleanSetting keepEnchanted;
    private final BooleanSetting preferDurability;
    private final TimeUtil.Timer timer = new TimeUtil.Timer();

    public AutoArmorModule() {
        super("autoarmor", "AutoArmor", "Equips the best armor pieces available in the inventory.",
                ModuleCategory.COMBAT);
        this.delay = number("Delay", "Delay between swaps in ms", 350.0, 0.0, 3000.0, 50.0);
        this.keepEnchanted = bool("Prefer Enchanted", "Prefer enchanted armor pieces", true);
        this.preferDurability = bool("Prefer Durability", "Prefer the least damaged piece", true);
    }

    @Override
    public void onTick(TickEvent event) {
        if (!mc().isInGame() || !timer.hasPassed((long) delay.value(), true)) {
            return;
        }
        IInventory inventory = player().inventory();
        for (int armorSlot : ARMOR_SLOTS) {
            IItemStack current = inventory.stack(armorSlot);
            int best = bestPiece(inventory, armorSlot);
            if (best < 0) {
                continue;
            }
            if (current != null && !current.isEmpty() && score(current) >= score(inventory.stack(best))) {
                continue;
            }
            inventory.clickSlot(best, 0, IInventory.ClickType.QUICK_MOVE);
            markActivity();
            return;
        }
    }

    private int bestPiece(IInventory inventory, int armorSlot) {
        int best = -1;
        double bestScore = -1.0;
        for (int slot = 0; slot < IInventory.MAIN_SIZE; slot++) {
            IItemStack stack = inventory.stack(slot);
            if (stack == null || stack.isEmpty() || !stack.isArmor()) {
                continue;
            }
            if (!fitsSlot(stack, armorSlot)) {
                continue;
            }
            double value = score(stack);
            if (value > bestScore) {
                bestScore = value;
                best = slot;
            }
        }
        return best;
    }

    private boolean fitsSlot(IItemStack stack, int armorSlot) {
        String id = stack.id();
        switch (armorSlot) {
            case IInventory.HELMET_SLOT:
                return id.contains("helmet");
            case IInventory.CHESTPLATE_SLOT:
                return id.contains("chestplate");
            case IInventory.LEGGINGS_SLOT:
                return id.contains("leggings");
            case IInventory.BOOTS_SLOT:
                return id.contains("boots");
            default:
                return false;
        }
    }

    private double score(IItemStack stack) {
        double value = stack.isInTag("minecraft:armor") ? 10.0 : 5.0;
        if (id(stack).contains("netherite")) {
            value += 40.0;
        } else if (id(stack).contains("diamond")) {
            value += 30.0;
        } else if (id(stack).contains("iron")) {
            value += 20.0;
        } else if (id(stack).contains("golden")) {
            value += 15.0;
        } else if (id(stack).contains("chainmail")) {
            value += 10.0;
        } else if (id(stack).contains("leather")) {
            value += 5.0;
        }
        if (keepEnchanted.isEnabled() && stack.isEnchanted()) {
            value += 12.0;
        }
        if (preferDurability.isEnabled() && stack.isDamageable()) {
            value += stack.durabilityRatio() * 5.0;
        }
        return value;
    }

    private String id(IItemStack stack) {
        return stack.id() == null ? "" : stack.id().toLowerCase();
    }
}
