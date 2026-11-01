package net.kinggrief.probe;

import net.kinggrief.core.DataStore;
import net.kinggrief.core.KingGriefCore;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

/** Real offline-mode players; 7.5.0 -> 7.5.1, a SIGKILL and restarts. */
public final class TreeAdminDonationProbe extends JavaPlugin implements Listener {
    private static final String ROOT = "winterTree";
    private KingGriefCore core;
    private Player owner, tech, ordinary;
    private boolean started, finished;
    private int checks;

    @Override public void onEnable() {
        saveDefaultConfig();
        core = (KingGriefCore) Bukkit.getPluginManager().getPlugin("KingGriefCore");
        Bukkit.getPluginManager().registerEvents(this, this);
    }
    @EventHandler public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if ("TreeHelper".equals(player.getName())) owner = player;
        if ("TreeFriend".equals(player.getName())) tech = player;
        if ("TreeObserver".equals(player.getName())) ordinary = player;
        if (!started && owner != null && tech != null && ordinary != null) {
            started = true;
            later(40, this::begin);
        }
    }
    private void later(long ticks, Runnable action) {
        Bukkit.getScheduler().runTaskLater(this, () -> {
            try { action.run(); }
            catch (Throwable error) { fail(error); }
        }, ticks);
    }
    private void ok(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
        checks++;
        getLogger().info("KG-TREE-ADMIN PASS " + message);
    }
    private void fail(Throwable error) {
        if (finished) return;
        finished = true;
        getLogger().severe("KG-TREE-ADMIN RESULT FAIL " + error);
        error.printStackTrace();
    }
    private void finish() {
        finished = true;
        getLogger().info("KG-TREE-ADMIN RESULT PASS " + checks + " checks");
    }
    private Object field(Object target, String name) {
        try { Field f = target.getClass().getDeclaredField(name); f.setAccessible(true); return f.get(target); }
        catch (Exception error) { throw new RuntimeException(error); }
    }
    private YamlConfiguration data() { return (YamlConfiguration) field(core.getStore(), "yml"); }
    private DataStore store() { return core.getStore(); }
    private String name(Player player) { return player.getName().toLowerCase(Locale.ROOT); }
    private String donor(String root, Player player) { return root + ".donors." + name(player); }
    private String archive(String id) { return ROOT + ".history." + id; }
    private int total() { return data().getInt(ROOT + ".total"); }
    private int snow(Player player) { return store().getSnowflakes(player.getName()); }
    private int donorTotal(String root, Player player) { return data().getInt(donor(root, player) + ".total"); }
    private int quota(Player player) { return data().getInt(ROOT + ".dailyLedger.used." + name(player)); }
    private int visual() { return ((Number) field(field(core, "winterTree"), "visualStage")).intValue(); }
    private Location marker() { return new Location(Bukkit.getWorld("world"), 160, 100, 160); }
    private void rank(Player player, String id) { store().setSavedRank(player.getName(), id); }
    private void donate(Player player, String amount) { player.performCommand("kgtree donate " + amount); }
    private void withBlockedSave(Runnable action) {
        Path file = core.getDataFolder().toPath().resolve("data.yml");
        Path backup = file.resolveSibling("tree-admin-test-backup.yml");
        try { Files.move(file, backup); Files.createSymbolicLink(file, backup.getFileName()); action.run(); }
        catch (Exception error) { throw new RuntimeException(error); }
        finally {
            try { if (Files.isSymbolicLink(file)) Files.delete(file); if (Files.exists(backup)) Files.move(backup, file); }
            catch (Exception error) { throw new RuntimeException(error); }
        }
    }
    private void unaffected() {
        Location at = marker();
        DataStore.Claim claim = store().getClaimAt(at);
        ok(at.getBlock().getType() == Material.DIAMOND_BLOCK && claim != null
                && claim.owner.equalsIgnoreCase(owner.getName()), "unrelated world block and claim are preserved");
        World spawn = Bukkit.getWorld("spawn_world");
        ok(spawn != null && spawn.getBlockAt(0, 130, 0).getType() == Material.SPRUCE_WOOD,
                "prepared spawn tree remains intact");
        ItemStack item = owner.getInventory().getItem(0);
        ok(item != null && item.getType() == Material.DIAMOND && item.getAmount() == 13,
                "player inventory remains intact");
    }
    private void begin() {
        switch (getConfig().getInt("phase", 1)) {
            case 1: original(); break;
            case 2: upgraded(); break;
            case 3: legacyReader(); break;
            case 4: restart(); break;
            default: throw new AssertionError("unknown test phase");
        }
    }
    private void original() {
        ok("7.5.0".equals(core.getDescription().getVersion()), "fixture really starts with published Core 7.5.0");
        for (Player player : new Player[]{owner, tech, ordinary}) {
            player.setOp(false);
            store().addSnowflakes(player.getName(), 200 - snow(player));
            store().setCoins(player.getName(), 1000);
        }
        rank(owner, "owner"); rank(tech, "techadmin"); rank(ordinary, "default");
        owner.getInventory().setItem(0, new ItemStack(Material.DIAMOND, 13));
        Location loc = marker(); loc.getBlock().setType(Material.DIAMOND_BLOCK);
        store().createClaim(owner.getName(), loc, 7);
        donate(owner, "7"); donate(tech, "9"); donate(ordinary, "5");
        ok(total() == 21 && snow(owner) == 193 && snow(tech) == 191 && snow(ordinary) == 195,
                "published 7.5.0 creates genuine pre-upgrade daily donations and balances");
        donate(owner, "11"); donate(tech, "11"); donate(ordinary, "11");
        ok(total() == 21 && donorTotal(ROOT, owner) == 7 && donorTotal(ROOT, tech) == 9,
                "7.5.0 still enforces the previous per-command limit for both ranks");
        store().save(); unaffected(); finish();
    }
    private void upgraded() {
        ok("7.5.1".equals(core.getDescription().getVersion()), "new Core 7.5.1 is actually loaded");
        ok(total() == 21 && snow(owner) == 193 && snow(tech) == 191 && snow(ordinary) == 195
                && quota(owner) == 7 && quota(tech) == 9 && quota(ordinary) == 5,
                "existing contributions, balances and quota survive the upgrade");
        ok(!owner.isOp() && !tech.isOp() && core.getRanks().isOwnerLevel(owner) && core.getRanks().isOwnerLevel(tech),
                "OWNER and TECH.ADMIN are tested by rank without OP");
        donate(owner, "30"); donate(owner, "13");
        ok(total() == 64 && snow(owner) == 150 && donorTotal(ROOT, owner) == 50,
                "OWNER contributes over ten per command and repeatedly in one day");
        donate(tech, "20"); donate(tech, "31");
        ok(total() == 115 && snow(tech) == 140 && donorTotal(ROOT, tech) == 60,
                "TECH.ADMIN without OP contributes over ten per command and per day");
        ok(quota(owner) == 10 && quota(tech) == 10
                && data().getInt(donor(ROOT, owner) + ".daily") == 10
                && data().getInt(donor(ROOT, tech) + ".daily") == 10,
                "legacy quota fields saturate at ten while seasonal totals stay exact");
        donate(ordinary, "6");
        ok(total() == 115 && snow(ordinary) == 195, "ordinary player cannot exceed remaining daily allowance");
        donate(ordinary, "5");
        ordinary.setOp(true);
        try {
            donate(ordinary, "1"); donate(ordinary, "11");
            ordinary.performCommand("kgtree status");
            ok(total() == 120 && snow(ordinary) == 190 && quota(ordinary) == 10
                    && !core.getRanks().isOwnerLevel(ordinary), "OP without the required rank cannot bypass the quota");
        } finally { ordinary.setOp(false); }
        owner.performCommand("kgtree status"); tech.performCommand("kgtree status");
        donate(owner, "151"); donate(owner, "0"); donate(owner, "-1"); donate(owner, "abc");
        donate(owner, "2147483647"); donate(owner, "99999999999999999999999");
        ok(total() == 120 && snow(owner) == 150 && donorTotal(ROOT, owner) == 50,
                "insufficient balance, invalid and oversized amounts never debit snowflakes");
        withBlockedSave(() -> donate(owner, "12"));
        ok(total() == 120 && snow(owner) == 150 && quota(owner) == 10 && donorTotal(ROOT, owner) == 50,
                "failed durable write rolls back a large admin donation and all quota fields");
        core.getConfig().set("enable-builtin-auth", true);
        store().setAuthenticated(owner, false); donate(owner, "12");
        ok(total() == 120 && snow(owner) == 150, "unauthenticated OWNER cannot donate");
        store().setAuthenticated(owner, true); core.getConfig().set("enable-builtin-auth", false);
        store().toggleQuickFreeze(tech.getUniqueId()); donate(tech, "12");
        ok(total() == 120 && snow(tech) == 140, "frozen TECH.ADMIN cannot donate");
        store().toggleQuickFreeze(tech.getUniqueId());
        donate(tech, "181");
        ok(total() == 120 && snow(tech) == 140, "an admin cannot exceed the remaining seasonal goal");
        donate(owner, "150"); donate(tech, "30");
        ok(total() == 300 && visual() == 3 && snow(owner) == 0 && snow(tech) == 110
                && donorTotal(ROOT, owner) == 200 && donorTotal(ROOT, tech) == 90,
                "large admin donations reach exactly 300 and advance all tree stages");
        donate(tech, "1");
        ok(total() == 300 && snow(tech) == 110, "completed season rejects extra donations without a debit");
        owner.performCommand("kgtree claim"); owner.performCommand("kgtree claim");
        ok(store().getCoins(owner.getName()) == 56000 && store().getCaseKeys(owner.getName(), "legendary") == 1
                && data().getBoolean("players." + name(owner) + ".titles.tree_friend"),
                "milestone rewards, key and title are awarded once after large contributions");
        owner.performCommand("kgtree season start winter_admin confirm");
        ok("winter_admin".equals(data().getString(ROOT + ".season")) && total() == 0
                && data().getInt(archive("legacy") + ".total") == 300 && quota(owner) == 10 && quota(tech) == 10,
                "season rotation archives exact totals and retains same-day quotas");
        store().addSnowflakes(owner.getName(), 20);
        donate(owner, "20"); donate(tech, "25");
        ok(total() == 45 && snow(owner) == 0 && snow(tech) == 85 && quota(owner) == 10 && quota(tech) == 10,
                "both privileged ranks can continue donating after same-day season rotation");
        rank(tech, "default"); donate(tech, "1");
        ok(total() == 45 && snow(tech) == 85, "removing TECH.ADMIN immediately restores the daily limit");
        rank(tech, "techadmin"); donate(tech, "11"); rank(tech, "default"); donate(tech, "1");
        ok(total() == 56 && snow(tech) == 74 && donorTotal(ROOT, tech) == 36 && quota(tech) == 10,
                "promotion lifts the quota and re-demotion does not reset it");
        rank(ordinary, "techadmin"); donate(ordinary, "11"); rank(ordinary, "default"); donate(ordinary, "1");
        ok(total() == 67 && snow(ordinary) == 179 && donorTotal(ROOT, ordinary) == 11 && quota(ordinary) == 10,
                "promoting and demoting an ordinary player cannot bypass the quota");
        store().save(); unaffected();
        // Give the real protocol clients time to receive their status messages before SIGKILL.
        later(20, this::finish);
    }
    private void legacyReader() {
        ok("7.5.0".equals(core.getDescription().getVersion()), "old Core is used to inspect the new persisted tree data");
        ok("winter_admin".equals(data().getString(ROOT + ".season")) && total() == 67 && visual() == 1
                && data().getInt(archive("legacy") + ".total") == 300,
                "old tree reader accepts saturated quota fields after SIGKILL and restart");
        ok(snow(owner) == 0 && snow(tech) == 74 && snow(ordinary) == 179
                && donorTotal(ROOT, tech) == 36 && quota(tech) == 10,
                "exact balances and large per-season donor totals survive restart");
        donate(tech, "1"); donate(ordinary, "1");
        ok(total() == 67 && snow(tech) == 74 && snow(ordinary) == 179,
                "old Core also enforces the demoted players' exhausted quota");
        tech.performCommand("kgtree status"); tech.performCommand("kgtree history");
        ok(data().getInt(archive("legacy") + ".donors." + name(owner) + ".total") == 200
                && data().getBoolean(archive("legacy") + ".donors." + name(owner) + ".claimed.3"),
                "archived contribution and claimed reward are readable by the previous Core");
        unaffected(); finish();
    }
    private void restart() {
        ok("7.5.1".equals(core.getDescription().getVersion()) && total() == 67
                && snow(tech) == 74 && snow(ordinary) == 179,
                "new Core reloads the same tree after another restart");
        ordinary.performCommand("kgtree claim legacy"); ordinary.performCommand("kgtree claim legacy");
        ok(store().getCoins(ordinary.getName()) == 56000 && store().getCaseKeys(ordinary.getName(), "legendary") == 1,
                "previous participant can collect all archived rewards exactly once");
        owner.performCommand("kgtree season start hotfix_round2 confirm");
        ok(total() == 0 && data().getInt(archive("winter_admin") + ".total") == 67
                && data().getInt(ROOT + ".lifetime." + name(tech)) == 126,
                "later rollover keeps exact contribution totals for achievements");
        donate(tech, "1"); donate(ordinary, "1");
        ok(total() == 0 && snow(tech) == 74 && snow(ordinary) == 179,
                "rotation cannot reset the downgraded ranks' same-day quota");
        rank(tech, "techadmin"); donate(tech, "11"); rank(tech, "default"); donate(tech, "1");
        ok(total() == 11 && snow(tech) == 63 && quota(tech) == 10,
                "promoted TECH.ADMIN remains unlimited even after restart and rotation");
        // Simulate the next UTC date in both the cross-season ledger and current donor record.
        data().set(ROOT + ".dailyLedger.day", "2001-01-01");
        data().set(ROOT + ".dailyLedger.used", null);
        data().set(donor(ROOT, tech) + ".day", "2001-01-01");
        store().save(); donate(tech, "10"); donate(tech, "1");
        ok(total() == 21 && snow(tech) == 53 && quota(tech) == 10 && donorTotal(ROOT, tech) == 21,
                "a new UTC day restores exactly ten for an ordinary player");
        data().set(ROOT + ".dailyLedger.day", "2001-01-01");
        data().set(ROOT + ".dailyLedger.used", null);
        data().set(donor(ROOT, tech) + ".day", "2001-01-01");
        store().save();
        rank(tech, "techadmin"); donate(tech, "4"); rank(tech, "default");
        donate(tech, "7"); donate(tech, "6"); donate(tech, "1");
        ok(total() == 31 && snow(tech) == 43 && quota(tech) == 10 && donorTotal(ROOT, tech) == 31,
                "a partial admin donation leaves exactly the remaining allowance after demotion");
        unaffected(); store().save(); finish();
    }
}
