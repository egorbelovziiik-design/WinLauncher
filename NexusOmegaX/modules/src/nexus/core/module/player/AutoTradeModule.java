package nexus.core.module.player;

import nexus.api.IInventory;
import nexus.api.IItemStack;
import nexus.core.event.TickEvent;
import nexus.core.module.Module;
import nexus.core.module.ModuleCategory;
import nexus.core.setting.*;
import nexus.util.TimeUtil;

/** Repeats the first trade of an open villager window. */
public final class AutoTradeModule extends Module {

    private final NumberSetting maxTrades;
    private final BooleanSetting requireEmeralds;
    private final TimeUtil.Timer timer = new TimeUtil.Timer();
    private int trades;

    public AutoTradeModule() {
        super("autotrade", "AutoTrade", "Repeats trades with an open villager interface.",
                ModuleCategory.PLAYER);
        this.maxTrades = number("Max Trades", "Stop after this many trades", 16.0, 1.0, 256.0, 1.0);
        this.requireEmeralds = bool("Require Emeralds", "Stop when you run out of emeralds", true);
    }

    @Override
    public void onEnable() {
        trades = 0;
        timer.reset();
    }

    @Override
    public void onTick(TickEvent event) {
        if (!mc().isInGame() || trades >= (int) maxTrades.value() || !timer.hasPassed(350L, true)) {
            return;
        }
        if (mc().interaction().openSyncId() < 0) {
            return;
        }
        IInventory inventory = player().inventory();
        if (inventory.containerSlots() < 3) {
            return;
        }
        IItemStack first = inventory.containerStack(0);
        if (first == null || first.isEmpty()) {
            return;
        }
        if (requireEmeralds.isEnabled() && inventory.count("emerald") == 0
                && !first.id().contains("emerald")) {
            return;
        }
        inventory.clickSlot(0, 0, IInventory.ClickType.PICKUP);
        inventory.clickSlot(2, 0, IInventory.ClickType.PICKUP);
        trades++;
        markActivity();
    }

    public int trades() {
        return trades;
    }
}
