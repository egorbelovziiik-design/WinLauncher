package net.kinggrief.probe;

import net.kinggrief.core.ItemFactory;
import net.kinggrief.core.KingGriefCore;
import net.kinggrief.core.SpawnEngine;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.UUID;

/** Verifies the actual prepared world with a connected Minecraft protocol player. */
public final class SpawnAvailabilityProbe extends JavaPlugin implements Listener {
    private boolean ran;
    private int checks;
    @Override public void onEnable() { Bukkit.getPluginManager().registerEvents(this, this); }
    @EventHandler public void join(PlayerJoinEvent event) {
        if (ran) return;
        ran = true;
        Player player = event.getPlayer();
        Bukkit.getScheduler().runTaskLater(this, () -> {
            try { check(player); getLogger().info("KG-SPAWN-AVAILABLE RESULT PASS checks=" + checks); }
            catch (Throwable failure) { getLogger().severe("KG-SPAWN-AVAILABLE RESULT FAIL " + failure); failure.printStackTrace(); }
        }, 40L);
    }
    private void ok(boolean value, String description) {
        if (!value) throw new AssertionError(description);
        checks++;
        getLogger().info("KG-SPAWN-AVAILABLE PASS " + description);
    }
    private void check(Player player) throws Exception {
        KingGriefCore core = (KingGriefCore) Bukkit.getPluginManager().getPlugin("KingGriefCore");
        SpawnEngine engine = core.getSpawnEngine();
        Location arrival = engine.getPreparedSpawnArrival();
        String expected = getConfig().getString("expected-world");
        ok(engine.isSpawnActive() && arrival != null, "prepared spawn is active");
        World prepared = arrival.getWorld(), survival = Bukkit.getWorlds().get(0);
        ok(prepared != null && prepared.getName().equals(expected), "prepared world selected: " + expected);
        ok(prepared != survival, "survival remains separate from the prepared spawn");
        ok(player.getWorld().equals(prepared), "real player arrives in the prepared world");
        ok(player.getLocation().distanceSquared(arrival) < 5, "arrival is near the village entrance");
        ok(arrival.clone().add(0, -1, 0).getBlock().getType().isSolid(), "arrival has a solid floor");
        ok(arrival.getBlock().isPassable() && arrival.clone().add(0, 1, 0).getBlock().isPassable(), "arrival has headroom");
        ok(prepared.getBlockAt(2, 121, 0).getType() == Material.OCHRE_FROGLIGHT
                && prepared.getBlockAt(3, 121, 0).getType() == Material.WHITE_CONCRETE
                && prepared.getBlockAt(9, 101, 1).getType() == Material.DARK_OAK_PLANKS
                && prepared.getBlockAt(9, 107, 1).getType() == Material.DARK_OAK_PLANKS
                && prepared.getBlockAt(9, 113, 1).getType() == Material.DARK_OAK_PLANKS,
                "Christmas tree and three figures exist in the actual world");
        engine.enforceStrictSingletonsOnSpawn(prepared);
        int hitboxes = 0, parts = 0;
        for (Entity e : prepared.getEntities()) {
            if (e instanceof Interaction && e.getScoreboardTags().contains("kg_npc_hitbox")) hitboxes++;
            if ((e instanceof BlockDisplay || e instanceof ItemDisplay) && e.getScoreboardTags().contains("kg_npc_model_part")) parts++;
        }
        ok(hitboxes >= 1 && parts >= 12, "a nearby NPC display model and interaction are present");
        ok(engine.isInSpawnWorld(arrival) && !engine.isInSpawnWorld(survival.getSpawnLocation()), "protection follows the selected world");
        for (String warp : new String[]{"mine", "farm", "casino", "wheel", "bafs", "pvp", "train", "kabinki", "parkour", "cases", "top"}) {
            Location l = engine.getWarpLocation(warp);
            ok(l != null && l.getWorld().equals(prepared), "warp " + warp + " uses the prepared world");
        }
        checkAc(player, prepared, survival);
        checkClear(player, arrival, survival);
        Location original = player.getLocation().clone();
        Location custom = survival.getSpawnLocation();
        core.getStore().setCustomSpawnLoc(custom);
        try {
            ok(player.performCommand("kinggriefcore:spawn") && player.getWorld().equals(survival), "/spawn respects the saved spawn");
            ok(player.performCommand("kinggriefcore:kgspawn") && player.getWorld().equals(prepared), "/kgspawn reaches the village despite the saved point");
            ok(core.getStore().getCustomSpawnLoc() != null && core.getStore().getCustomSpawnLoc().getWorld().equals(survival), "custom spawn was not discarded");
        } finally {
            core.getStore().setCustomSpawnLoc(null);
            player.teleport(original);
        }
    }
    private void checkAc(Player player, World prepared, World survival) throws Exception {
        Plugin ac = Bukkit.getPluginManager().getPlugin("KingAC");
        if (ac == null) return;
        ok(ac.isEnabled(), "KingAC is enabled");
        Method getData = ac.getClass().getDeclaredMethod("data", Player.class); getData.setAccessible(true);
        Object data = getData.invoke(ac, player);
        Field stone = data.getClass().getField("stoneMined");
        Method check = ac.getClass().getMethod("onMineXrayCheck", BlockBreakEvent.class);
        org.bukkit.block.Block a = prepared.getBlockAt(50, 99, 50);
        Location spawn = survival.getSpawnLocation();
        org.bukkit.block.Block b = survival.getBlockAt(spawn.getBlockX()+2, spawn.getBlockY()-1, spawn.getBlockZ()+2);
        Material oldA = a.getType(), oldB = b.getType();
        try {
            a.setType(Material.STONE, false); b.setType(Material.STONE, false);
            int before = stone.getInt(data);
            check.invoke(ac, new BlockBreakEvent(a, player));
            ok(stone.getInt(data) == before, "KingAC ignores XRay mining at the active spawn");
            check.invoke(ac, new BlockBreakEvent(b, player));
            ok(stone.getInt(data) == before+1, "KingAC continues XRay checks in survival");
        } finally { a.setType(oldA, false); b.setType(oldB, false); }
    }
    @SuppressWarnings("unchecked")
    private void checkClear(Player player, Location arrival, World survival) throws Exception {
        Plugin clear = Bukkit.getPluginManager().getPlugin("KingClear");
        if (clear == null) return;
        ok(clear.isEnabled(), "KingClear is enabled");
        ItemStack[] original = player.getInventory().getContents().clone();
        Location originalLocation = player.getLocation().clone();
        try {
            player.teleport(arrival);
            player.getInventory().clear();
            player.getInventory().setItem(0, new ItemStack(Material.DIAMOND, 3));
            player.getInventory().setItem(8, ItemFactory.menuStar());
            ok(player.performCommand("kingclear:clear inv"), "KingClear command is registered");
            ok(player.getInventory().getItem(0) == null && "menu_star".equals(ItemFactory.getCustomId(player.getInventory().getItem(8))),
                    "clear removes ordinary items and preserves Core's real menu star at spawn");
            ok(player.performCommand("kingclear:clear undo") && player.getInventory().getItem(0) != null,
                    "clear undo restores ordinary items");
            ok(player.teleport(survival.getSpawnLocation()), "player can return to survival");
            player.getInventory().clear(); player.getInventory().setItem(8, ItemFactory.menuStar());
            Field cooldown = clear.getClass().getDeclaredField("cooldownUntilMs"); cooldown.setAccessible(true);
            ((Map<UUID, Long>) cooldown.get(clear)).remove(player.getUniqueId());
            ok(player.performCommand("kingclear:clear inv") && player.getInventory().getItem(8) == null,
                    "clear can remove the menu star in survival");
        } finally { player.getInventory().setContents(original); player.teleport(originalLocation); }
    }
}
