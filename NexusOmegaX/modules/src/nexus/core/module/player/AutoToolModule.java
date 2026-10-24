package nexus.core.module.player;

import nexus.api.IItemStack;
import nexus.api.IInventory;
import nexus.core.event.TickEvent;
import nexus.core.module.Module;
import nexus.core.module.ModuleCategory;
import nexus.core.setting.*;

/** Selects the best tool for the block you are breaking. */
public final class AutoToolModule extends Module {

    private final BooleanSetting preferSilkTouch;
    private final BooleanSetting preferFortune;

    public AutoToolModule() {
        super("autotool", "AutoTool", "Selects the best tool for the block you are mining.",
                ModuleCategory.PLAYER);
        this.preferSilkTouch = bool("Prefer Silk Touch", "Prefer silk touch tools when available", false);
        this.preferFortune = bool("Prefer Fortune", "Prefer fortune tools when mining ores", true);
    }

    @Override
    public void onTick(TickEvent event) {
        if (!mc().isInGame() || !mc().input().isMouseDown(0)) {
            return;
        }
        String targetBlock = blockUnderCrosshair();
        if (targetBlock == null || targetBlock.isEmpty() || targetBlock.equals("air")) {
            return;
        }
        int best = bestToolSlot(targetBlock);
        if (best >= 0 && best != player().inventory().selectedSlot()) {
            player().inventory().setSelectedSlot(best);
            markActivity();
        }
    }

    private String blockUnderCrosshair() {
        if (mc().crosshairTarget() != null) {
            return "";
        }
        double yaw = Math.toRadians(player().yaw());
        double pitch = Math.toRadians(player().pitch());
        double reach = 4.0;
        for (double distance = 0.5; distance <= reach; distance += 0.35) {
            double x = player().eyePosition().x - Math.sin(yaw) * Math.cos(pitch) * distance;
            double y = player().eyePosition().y - Math.sin(pitch) * distance;
            double z = player().eyePosition().z + Math.cos(yaw) * Math.cos(pitch) * distance;
            if (mc().world().isSolid((int) Math.floor(x), (int) Math.floor(y), (int) Math.floor(z))) {
                return "solid";
            }
        }
        return "";
    }

    private int bestToolSlot(String block) {
        IInventory inventory = player().inventory();
        int best = -1;
        double bestScore = -1.0;
        for (int slot = 0; slot < 9; slot++) {
            IItemStack stack = inventory.stack(slot);
            if (stack == null || stack.isEmpty() || !stack.isTool()) {
                continue;
            }
            double score = toolScore(stack);
            if (score > bestScore) {
                bestScore = score;
                best = slot;
            }
        }
        return best;
    }

    private double toolScore(IItemStack stack) {
        String id = stack.id();
        double score = 1.0;
        if (id.contains("netherite")) {
            score += 6.0;
        } else if (id.contains("diamond")) {
            score += 5.0;
        } else if (id.contains("iron")) {
            score += 4.0;
        } else if (id.contains("stone")) {
            score += 3.0;
        } else if (id.contains("wooden")) {
            score += 2.0;
        }
        if (id.contains("pickaxe")) {
            score += 3.0;
        } else if (id.contains("axe")) {
            score += 2.0;
        } else if (id.contains("shovel")) {
            score += 1.0;
        }
        if (preferFortune.isEnabled() && stack.isEnchanted()) {
            score += 1.5;
        }
        if (preferSilkTouch.isEnabled() && stack.isEnchanted()) {
            score += 1.0;
        }
        if (stack.isDamageable()) {
            score += stack.durabilityRatio();
        }
        return score;
    }
}
