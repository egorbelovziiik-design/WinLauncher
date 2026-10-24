package nexus.core.module.world;

import nexus.core.event.TickEvent;
import nexus.core.module.Module;
import nexus.core.module.ModuleCategory;
import nexus.core.setting.*;

import java.util.LinkedHashMap;
import java.util.Map;

/** Counts the blocks in your inventory and caches the total value. */
public final class ResourceTrackerModule extends Module {

    private final NumberSetting interval;
    private final Map<String, Integer> totals = new LinkedHashMap<String, Integer>();
    private final nexus.util.TimeUtil.Timer timer = new nexus.util.TimeUtil.Timer();

    public ResourceTrackerModule() {
        super("resourcetracker", "ResourceTracker", "Counts the resources in your inventory.",
                ModuleCategory.WORLD);
        this.interval = number("Interval", "Update interval in ms", 500.0, 100.0, 10000.0, 100.0);
    }

    @Override
    public void onEnable() {
        totals.clear();
        timer.reset();
    }

    @Override
    public void onDisable() {
        totals.clear();
    }

    @Override
    public void onTick(TickEvent event) {
        if (!mc().isInGame() || !timer.hasPassed((long) interval.value(), true)) {
            return;
        }
        totals.clear();
        nexus.api.IInventory inventory = player().inventory();
        for (int slot = 0; slot < inventory.mainSize(); slot++) {
            nexus.api.IItemStack stack = inventory.stack(slot);
            if (stack.isEmpty()) {
                continue;
            }
            String id = shortId(stack.id());
            Integer previous = totals.get(id);
            totals.put(id, Integer.valueOf((previous == null ? 0 : previous.intValue()) + stack.count()));
        }
    }

    private String shortId(String id) {
        int index = id.indexOf(':');
        return index >= 0 && index + 1 < id.length() ? id.substring(index + 1) : id;
    }

    public Map<String, Integer> totals() {
        return totals;
    }

    public String topResource() {
        String best = "-";
        int bestCount = -1;
        for (Map.Entry<String, Integer> entry : totals.entrySet()) {
            if (entry.getValue().intValue() > bestCount) {
                bestCount = entry.getValue().intValue();
                best = entry.getKey();
            }
        }
        return best + " x" + Math.max(0, bestCount);
    }

    @Override
    public String hudInfo() {
        return topResource();
    }
}
