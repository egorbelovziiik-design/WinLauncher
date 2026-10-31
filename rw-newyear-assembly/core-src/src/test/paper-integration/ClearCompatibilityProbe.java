package net.kinggrief.probe;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

/** Tests standalone and incompatible-Core fail-safe behavior with a real player. */
public final class ClearCompatibilityProbe extends JavaPlugin implements Listener {
    private static final NamespacedKey ID = new NamespacedKey("kinggrief", "custom_id");
    private boolean ran;
    @Override public void onEnable() { Bukkit.getPluginManager().registerEvents(this, this); }
    @EventHandler public void onJoin(PlayerJoinEvent event) {
        if (ran) return;
        ran = true;
        Player player = event.getPlayer();
        Bukkit.getScheduler().runTaskLater(this, () -> {
            try {
                boolean expected = getConfig().getBoolean("expect-protection");
                boolean broken = getConfig().getBoolean("broken-core");
                if (Bukkit.getPluginManager().getPlugin("KingClear") == null) throw new AssertionError("KingClear did not load");
                org.bukkit.plugin.Plugin core = Bukkit.getPluginManager().getPlugin("KingGriefCore");
                if ((core != null) != broken) throw new AssertionError("Core fixture did not match the scenario");
                if (getConfig().getBoolean("disabled-core")) Bukkit.getPluginManager().disablePlugin(core);
                checkKingAc(player, getConfig().getBoolean("expect-xray"));
                player.getInventory().clear();
                ItemStack star = new ItemStack(Material.NETHER_STAR);
                ItemMeta meta = star.getItemMeta();
                meta.getPersistentDataContainer().set(ID, PersistentDataType.STRING, "menu_star");
                star.setItemMeta(meta);
                player.getInventory().setItem(0, new ItemStack(Material.DIAMOND, 3));
                player.getInventory().setItem(8, star);
                if (!player.performCommand("kingclear:clear inv")) throw new AssertionError("command not registered");
                if (player.getInventory().getItem(0) != null) throw new AssertionError("ordinary item not removed");
                ItemStack after = player.getInventory().getItem(8);
                if ((after != null && after.getType() == Material.NETHER_STAR) != expected)
                    throw new AssertionError("menu star protection did not match the expected world/fail-safe");
                if (!player.performCommand("kingclear:clear undo") || player.getInventory().getItem(0) == null)
                    throw new AssertionError("clear undo did not restore the ordinary item");
                int count = 0;
                for (ItemStack item : player.getInventory().getContents())
                    if (item != null && item.getType() == Material.NETHER_STAR) count += item.getAmount();
                if (count != 1) throw new AssertionError("undo lost or duplicated the menu star: " + count);
                getLogger().info("KG-CLEAR-COMPAT RESULT PASS mode=" + getConfig().getString("mode")
                        + " star-protected=" + expected + " undo-count=" + count);
            } catch (Throwable failure) {
                getLogger().severe("KG-CLEAR-COMPAT RESULT FAIL " + failure);
                failure.printStackTrace();
            }
        }, 20L);
    }
    private void checkKingAc(Player player, boolean expectedXray) throws Exception {
        org.bukkit.plugin.Plugin ac = Bukkit.getPluginManager().getPlugin("KingAC");
        if (ac == null || !ac.isEnabled()) throw new AssertionError("KingAC did not load");
        java.lang.reflect.Method getData = ac.getClass().getDeclaredMethod("data", Player.class); getData.setAccessible(true);
        Object data = getData.invoke(ac, player);
        java.lang.reflect.Field stone = data.getClass().getField("stoneMined");
        org.bukkit.block.Block block = player.getLocation().clone().add(2, -1, 2).getBlock();
        Material old = block.getType();
        try {
            block.setType(Material.STONE, false);
            int before = stone.getInt(data);
            ac.getClass().getMethod("onMineXrayCheck", org.bukkit.event.block.BlockBreakEvent.class)
                    .invoke(ac, new org.bukkit.event.block.BlockBreakEvent(block, player));
            if (stone.getInt(data) != before + (expectedXray ? 1 : 0))
                throw new AssertionError("KingAC XRay did not use the expected standalone/fail-safe world");
            getLogger().info("KG-CLEAR-COMPAT PASS KingAC xray=" + expectedXray);
        } finally { block.setType(old, false); }
    }
}
