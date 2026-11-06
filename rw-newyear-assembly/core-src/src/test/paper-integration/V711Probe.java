package net.kinggrief.probe;

import net.kinggrief.core.DataStore;
import net.kinggrief.core.ItemFactory;
import net.kinggrief.core.KingGriefCore;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.entity.Zombie;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.UUID;

/** Isolated real Paper/protocol checks, failed writes, external points and crash recovery. */
public final class V711Probe extends JavaPlugin implements Listener {
    private KingGriefCore core;
    private DataStore store;
    private Plugin points;
    private Player a, b;
    private int checks, clicks;
    private boolean started, finished;
    private Path dataFile, savedFile;
    private static final long DAY = 86_400_000L;
    private static final String[] EXTRAS = {"ore_1000", "ore_5000", "gifts_1", "gifts_5", "gifts_10",
            "fish_5", "fish_25", "play_1", "play_24", "play_168"};

    @Override public void onEnable() {
        core = (KingGriefCore) Bukkit.getPluginManager().getPlugin("KingGriefCore");
        store = core.getStore(); points = Bukkit.getPluginManager().getPlugin("PlayerPoints");
        dataFile = core.getDataFolder().toPath().resolve("data.yml");
        savedFile = dataFile.resolveSibling("v711-probe-backup.yml");
        Bukkit.getPluginManager().registerEvents(this, this);
    }
    @EventHandler public void join(PlayerJoinEvent event) {
        if ("WheelOne".equals(event.getPlayer().getName())) a = event.getPlayer();
        if ("WheelTwo".equals(event.getPlayer().getName())) b = event.getPlayer();
        if (!started && a != null && b != null) {
            started = true;
            later(100, () -> { if (getConfig().getInt("phase", 1) == 1) begin(); else restart(); });
        }
    }
    @EventHandler(priority=EventPriority.MONITOR) public void clicked(InventoryClickEvent event) {
        if (a != null && event.getWhoClicked().equals(a)) clicks++;
    }
    private void later(long ticks, Runnable action) {
        Bukkit.getScheduler().runTaskLater(this, () -> { try { action.run(); } catch (Throwable error) { fail(error); } }, ticks);
    }
    private void ok(boolean condition, String text) {
        if (!condition) throw new AssertionError(text);
        checks++; getLogger().info("KG-V711 PASS " + text);
    }
    private void fail(Throwable error) {
        if (finished) return; finished = true; restore();
        getLogger().severe("KG-V711 RESULT FAIL " + error); error.printStackTrace();
    }
    private void finish() {
        if (finished) return; finished = true; restore();
        getLogger().info("KG-V711 RESULT PASS checks=" + checks);
    }
    private void click(int slot, Runnable after) { click(slot, 0, 0, after); }
    private void click(int slot, int button, int mode, Runnable after) {
        int expected = clicks + 1;
        later(8, () -> {
            getLogger().info("KG-V711 REQUEST CLICK " + slot + " " + button + " " + mode);
            later(20, () -> { ok(clicks >= expected, "real protocol action reached server " + slot + "/" + mode); after.run(); });
        });
    }
    private void link() {
        try { Files.move(dataFile, savedFile); Files.createSymbolicLink(dataFile, savedFile.getFileName()); }
        catch (Exception error) { throw new RuntimeException(error); }
    }
    private void restore() {
        try { if (Files.isSymbolicLink(dataFile)) { Files.delete(dataFile); Files.move(savedFile, dataFile); } }
        catch (Exception error) { throw new RuntimeException(error); }
    }
    @SuppressWarnings("unchecked") private <T> T field(Object object, String name) {
        try { Field field = object.getClass().getDeclaredField(name); field.setAccessible(true); return (T) field.get(object); }
        catch (Exception error) { throw new RuntimeException(error); }
    }
    private YamlConfiguration data() { return field(store, "yml"); }
    private String root(Player player) { return "players." + player.getName().toLowerCase(Locale.ROOT); }
    private double coins(Player player) { return store.getCoins(player.getName()); }
    private int relics(Player player) { return store.getRelics(player); }
    private int snow(Player player) { return store.getSnowflakes(player.getName()); }
    private Inventory top() { return a.getOpenInventory().getTopInventory(); }
    private void pointsMode(String mode) {
        try { points.getClass().getMethod("mode", String.class).invoke(points, mode); }
        catch (Exception error) { throw new RuntimeException(error); }
    }
    private int pointsBalance(Player player) {
        try { return (Integer) points.getClass().getMethod("balance", UUID.class).invoke(points, player.getUniqueId()); }
        catch (Exception error) { throw new RuntimeException(error); }
    }
    private void begin() {
        ok(core.getDescription().getVersion().equals("7.14.0"), "new Core version is running");
        a.setGameMode(GameMode.SURVIVAL); b.setGameMode(GameMode.SURVIVAL);
        a.getInventory().clear(); b.getInventory().clear();
        store.setCoins(a.getName(), 1_000_000.0); store.setRelics(a.getName(), 100);
        store.setCoins(b.getName(), 2_000_000.0); store.setRelics(b.getName(), 200);
        data().set(root(a)+".snowflakes", 50); data().set(root(b)+".snowflakes", 60);
        data().set(root(a)+".dailyReady", System.currentTimeMillis()+60_000L);
        data().set(root(a)+".dailyLastClaim", null); data().set(root(a)+".dailyStreak", null);
        data().set(root(a)+".bp", null); data().set(root(b)+".dailyReady", 0L); store.save();
        a.performCommand("daily status"); a.performCommand("daily");
        ok(coins(a)==1_000_000.0 && relics(a)==100 && snow(a)==50,
                "legacy daily cooldown remains in force and status is read-only");
        data().set(root(a)+".dailyReady",0L); store.save();
        link(); a.performCommand("daily"); restore();
        ok(coins(a)==1_000_000.0 && relics(a)==100 && snow(a)==50 && data().getLong(root(a)+".dailyReady")==0L
                && data().getLong(root(a)+".dailyLastClaim")==0L && pointsBalance(a)==100,
                "failed daily save changes neither cooldown, local currencies nor PlayerPoints");
        pointsMode("read-fail"); a.performCommand("daily");
        ok(coins(a)==1_000_000.0 && snow(a)==50 && data().getLong(root(a)+".dailyReady")==0L,
                "unreadable PlayerPoints refuses a daily award before local commit");
        pointsMode("normal"); a.performCommand("daily");
        long ready = data().getLong(root(a)+".dailyReady");
        ok(coins(a)==1_020_000.0 && relics(a)==115 && pointsBalance(a)==115 && snow(a)==55
                && data().getInt(root(a)+".dailyStreak")==1 && ready>=System.currentTimeMillis()+DAY-5_000L,
                "first daily keeps the old base reward and commits all markers and local balances");
        a.performCommand("daily"); a.performCommand("daily status");
        ok(coins(a)==1_020_000.0 && relics(a)==115 && snow(a)==55, "daily replay and status cannot pay twice");
        streak(2, 25_000, 7, "third claim bonus");
        streak(6, 35_000, 10, "seventh claim bonus");
        streak(13, 50_000, 15, "fourteenth claim bonus");
        streak(29, 80_000, 25, "thirtieth claim bonus");
        double before=coins(a); int oldRelics=relics(a),oldSnow=snow(a);
        stageStreak(a, 8, DAY*2+10_000L); a.performCommand("daily");
        ok(data().getInt(root(a)+".dailyStreak")==1 && coins(a)==before+20_000 && relics(a)==oldRelics+15 && snow(a)==oldSnow+5,
                "a gap over 48 hours resets the streak without changing the base reward");
        before=coins(a); data().set(root(a)+".dailyReady",0L);
        data().set(root(a)+".dailyLastClaim",System.currentTimeMillis()+60_000L); store.save(); a.performCommand("daily");
        ok(coins(a)==before && data().getLong(root(a)+".dailyReady")==0L, "backward clock state refuses a reward");
        // Return to a real saved cooldown for the crash-restart check.
        data().set(root(a)+".dailyLastClaim",System.currentTimeMillis());
        data().set(root(a)+".dailyReady",System.currentTimeMillis()+DAY); store.save();
        pointsFailure();
    }
    private void stageStreak(Player player, int streak, long elapsed) {
        long now=System.currentTimeMillis(); data().set(root(player)+".dailyReady",now-1L);
        data().set(root(player)+".dailyLastClaim",now-elapsed); data().set(root(player)+".dailyStreak",streak); store.save();
    }
    private void streak(int previous, int awardCoins, int awardSnow, String label) {
        stageStreak(a, previous, DAY+2_000L);
        double c=coins(a); int r=relics(a),s=snow(a); a.performCommand("daily");
        ok(coins(a)==c+awardCoins && relics(a)==r+15 && pointsBalance(a)==r+15 && snow(a)==s+awardSnow
                && data().getInt(root(a)+".dailyStreak")==previous+1, label+" has exact rewards and persists its streak");
    }
    private void pointsFailure() {
        double c=coins(b); int r=relics(b),s=snow(b); pointsMode("reject"); b.performCommand("daily");
        ok(coins(b)==c+20_000 && relics(b)==r+15 && snow(b)==s+5 && pointsBalance(b)==r
                && data().getLong(root(b)+".dailyReady")>System.currentTimeMillis(),
                "rejected external sync leaves one recorded local daily reward and cooldown");
        b.performCommand("daily");
        ok(coins(b)==c+20_000 && relics(b)==r+15 && snow(b)==s+5,
                "external sync failure cannot replay the local award");
        pointsMode("normal");
        data().set(root(b)+".dailyReady",0L); data().set(root(b)+".dailyLastClaim",0L);
        data().set(root(b)+".dailyStreak",0); data().set(root(b)+".snowflakes",Integer.MAX_VALUE-2); store.save();
        c=coins(b); r=relics(b); b.performCommand("daily");
        ok(coins(b)==c && relics(b)==r && data().getLong(root(b)+".dailyReady")==0L,
                "snowflake overflow rejects the entire daily award");
        data().set(root(b)+".snowflakes",s+5); store.save();
        freebieMenu();
    }
    private void freebieMenu() {
        stageStreak(a,1,DAY+2000L); a.performCommand("free");
        ok(top().getItem(13).getType()==Material.SNOWBALL && top().getItem(13).getItemMeta().getLore().toString().contains("Серия"),
                "freebie menu displays daily status and streak");
        double c=coins(a); int r=relics(a),s=snow(a); Inventory free=top();
        click(13,0,2,() -> {
            ok(coins(a)==c && a.getOpenInventory().getTopInventory()==free && data().getInt(root(a)+".dailyStreak")==1,
                    "number-key click cannot claim the daily gift");
            click(13,() -> {
                ok(coins(a)==c+20_000 && relics(a)==r+15 && snow(a)==s+5 && data().getInt(root(a)+".dailyStreak")==2,
                        "normal real click in freebie menu claims the daily gift exactly once");
                battlePass();
            });
        });
    }
    private void battlePass() {
        data().set(root(a)+".mined",1400); data().set(root(a)+".kills",0);
        data().set(root(a)+".bp",null); data().set(root(a)+".bp.1",true); store.save();
        a.performCommand("bp");
        ok(top().getSize()==54 && top().getItem(38).getType()==Material.HOPPER, "new battle-pass GUI has all-rewards control");
        for(int level=1;level<=15;level++) {
            int slot=level<=7?9+level:level<=14?11+level:28;
            ok(("bp_"+level).equals(ItemFactory.getCustomId(top().getItem(slot))), "battle-pass tier "+level+" is visible");
        }
        ok(top().getItem(10).getType()==Material.MINECART, "legacy level 1 remains claimed");
        double before=coins(a);
        click(10, () -> { ok(coins(a)==before, "old claimed level cannot be rewarded again");
            click(19, () -> { ok(coins(a)==before && !store.hasClaimedBp(a.getName(),8), "locked new level cannot be claimed");
                unusualClicks(before); });
        });
    }
    private void unusualClicks(double before) {
        a.getInventory().setItem(0,new ItemStack(Material.DIAMOND,3));
        click(11,0,2,() -> {
            ok(coins(a)==before && !store.hasClaimedBp(a.getName(),2) && a.getInventory().getItem(0).getAmount()==3,
                    "number-key swap cannot claim or extract a battle-pass icon");
            click(11,0,1,() -> {
                ok(coins(a)==before && !store.hasClaimedBp(a.getName(),2), "shift-click cannot claim a reward");
                click(11,0,4,() -> {
                    ok(coins(a)==before && !store.hasClaimedBp(a.getName(),2) && top().getItem(11)!=null,
                            "drop click cannot extract or claim a reward");
                    core.getConfig().set("enable-builtin-auth",true); store.setAuthenticated(a,false);
                    click(11,() -> {
                        ok(coins(a)==before && !store.hasClaimedBp(a.getName(),2), "an open GUI cannot bypass authentication");
                        core.getConfig().set("enable-builtin-auth",false); store.setAuthenticated(a,true);
                        store.toggleQuickFreeze(a.getUniqueId());
                        click(11,() -> {
                            ok(coins(a)==before && !store.hasClaimedBp(a.getName(),2), "frozen player cannot claim through an open GUI");
                            store.clearQuickFreeze(a.getUniqueId()); blockedBattlePass();
                        });
                    });
                });
            });
        });
    }
    private void blockedBattlePass() {
        double c=coins(a); int r=relics(a),s=snow(a); link();
        click(11,() -> {
            restore();
            ok(coins(a)==c && relics(a)==r && snow(a)==s && pointsBalance(a)==r && !store.hasClaimedBp(a.getName(),2),
                    "failed GUI claim rolls back marker and all balances");
            click(11,() -> {
                ok(coins(a)==c+30_000 && relics(a)==r+30 && pointsBalance(a)==r+30 && snow(a)==s+10
                        && store.hasClaimedBp(a.getName(),2), "normal click grants exactly level 2 once");
                double c2=coins(a); int r2=relics(a),s2=snow(a);
                pointsMode("read-fail"); a.performCommand("bp claim"); pointsMode("normal");
                ok(coins(a)==c2 && !store.hasClaimedBp(a.getName(),3), "unreadable PlayerPoints refuses the batch before any commit");
                link(); click(38,() -> {
                    restore();
                    ok(coins(a)==c2 && relics(a)==r2 && snow(a)==s2 && !store.hasClaimedBp(a.getName(),3)
                            && !store.hasClaimedBp(a.getName(),7), "failed collect-all commits no tier or local currency");
                    click(38,() -> {
                        ok(coins(a)==c2+375_000 && relics(a)==r2+375 && snow(a)==s2+125,
                                "collect-all pays exactly levels 3 through 7 in one local commit");
                        for(int i=1;i<=7;i++)ok(store.hasClaimedBp(a.getName(),i), "legacy or claimed tier "+i+" remains marked");
                        double paid=coins(a); a.performCommand("bp claim"); a.performCommand("bp status");
                        ok(coins(a)==paid && !store.hasClaimedBp(a.getName(),8), "replay skips claimed and unfinished levels");
                        largeCounters();
                    });
                });
            });
        });
    }
    private void largeCounters() {
        data().set(root(a)+".mined",Integer.MAX_VALUE); data().set(root(a)+".kills",Integer.MAX_VALUE);
        data().set(root(a)+".deaths",Integer.MAX_VALUE);
        store.addMined(a.getName()); store.addKill(a.getName()); store.addDeath(a.getName());
        ok(store.getMinedBlocks(a.getName())==Integer.MAX_VALUE && store.getKills(a.getName())==Integer.MAX_VALUE
                && store.getDeaths(a.getName())==Integer.MAX_VALUE, "progress counters saturate rather than wrapping negative");
        a.performCommand("bp");
        String lore=top().getItem(4).getItemMeta().getLore().toString();
        ok(lore.contains("10844792417"), "battle-pass score is computed as a long, not overflowing int");
        double c=coins(a); int r=relics(a),s=snow(a); a.performCommand("bp claim");
        ok(coins(a)==c+1_380_000 && relics(a)==r+1_380 && snow(a)==s+460,
                "all eight new levels pay their exact combined rewards");
        for(int i=8;i<=15;i++)ok(store.hasClaimedBp(a.getName(),i), "new tier "+i+" was persisted");
        c=coins(a); a.performCommand("bp claim"); ok(coins(a)==c, "completed battle pass is idempotent");
        achievements();
    }
    private void achievements() {
        String claim="achievements."+a.getUniqueId()+".claimed.";
        data().set("achievements."+a.getUniqueId()+".claimed",null);
        data().set("utility.mining."+a.getUniqueId()+".total",5000);
        data().set("winterFishing."+a.getUniqueId()+".total",25);
        data().set(root(a)+".playSec",604800L);
        for(int i=0;i<10;i++)data().set(root(a)+".spawnGifts.g"+i,true);
        data().set(root(a)+".titles.boss_slayer",false); data().set(root(a)+".titles.king_achiever",false);
        data().set("parkour.records."+a.getUniqueId()+".all",0L); store.save();
        double c=coins(a); int s=snow(a); a.performCommand("achievements status");
        link(); a.performCommand("achievements claim"); restore();
        ok(!data().getBoolean(claim+"ore_1000") && !data().getBoolean(claim+"gifts_10") && coins(a)==c,
                "failed achievement write rolls back new cosmetic badges");
        a.performCommand("achievements claim");
        for(String id:EXTRAS)ok(data().getBoolean(claim+id), "new achievement "+id+" can be earned from existing progress");
        ok(!store.hasUnlockedTitle(a.getName(),"king_achiever") && coins(a)==c && snow(a)==s,
                "optional badges do not grant the original title or currency");
        data().set(root(a)+".titles.boss_slayer",true); data().set("parkour.records."+a.getUniqueId()+".all",42_000L);
        a.performCommand("achievements claim"); a.performCommand("achievements claim");
        ok(store.hasUnlockedTitle(a.getName(),"king_achiever") && data().getBoolean(claim+"parkour")
                && data().getBoolean(claim+"boss") && coins(a)==c, "original four requirements still unlock the original title once");
        jobs();
    }
    private void jobs() {
        World world=Bukkit.getWorld("world"); world.setPVP(true);
        for(int x=102;x<=110;x++)for(int z=102;z<=108;z++)world.getBlockAt(x,79,z).setType(Material.STONE,false);
        a.closeInventory(); a.teleport(new Location(world,105.5,80,105.5)); b.teleport(new Location(world,107.5,80,105.5));
        a.setGameMode(GameMode.SURVIVAL); b.setGameMode(GameMode.SURVIVAL);
        a.getInventory().clear(); b.getInventory().clear(); store.setJob(a.getName(),"hunter");
        double before=coins(a); Zombie mob=world.spawn(new Location(world,106.5,80,105.5),Zombie.class);
        mob.addScoreboardTag("kg_farm_mob"); mob.setNoDamageTicks(0); mob.damage(1000,a);
        later(10,() -> {
            ok(mob.isDead() && coins(a)==before+70, "hunter job also pays its 30-coin bonus for farm monsters");
            DataStore.Clan clan=store.createClan(a.getName(),"ProbeClan");
            ok(clan!=null && clan.kills==0, "test clan is ready for a real PvP victory");
            data().set(root(a)+".kills",0); data().set(root(a)+".gladiatorReady",0L); store.setJob(a.getName(),"killer"); store.save();
            later(40,() -> {
                double money=coins(a); b.setInvulnerable(false); b.setHealth(20); b.setNoDamageTicks(0); b.setLastDamage(0);
                b.damage(1000,a);
                later(5,() -> {
                    ok(b.isDead(), "real PvP attack killed the second test player");
                    ok(clan.kills==1 && store.getKills(a.getName())==1, "one PvP victory counts exactly once for the clan and player");
                    ok(coins(a)==money+340 && data().getLong(root(a)+".gladiatorReady")>System.currentTimeMillis(),
                            "gladiator receives base 200, level-one clan 40 and durable 100 premium");
                    gladiatorCooldown();
                });
            });
        });
    }
    private void gladiatorCooldown() {
        try {
            Method award=DataStore.class.getDeclaredMethod("awardGladiator",Player.class,long.class); award.setAccessible(true);
            double c=coins(a);
            ok(!(Boolean)award.invoke(store,a,System.currentTimeMillis()) && coins(a)==c,
                    "gladiator premium uses one shared ten-minute cooldown");
            data().set(root(a)+".gladiatorReady",0L); store.save(); link();
            boolean denied=(Boolean)award.invoke(store,a,System.currentTimeMillis()); restore();
            ok(!denied && coins(a)==c && data().getLong(root(a)+".gladiatorReady")==0L,
                    "failed gladiator save cannot pay or start cooldown");
            ok((Boolean)award.invoke(store,a,System.currentTimeMillis()) && coins(a)==c+100,
                    "gladiator premium can commit again after cooldown");
            YamlConfiguration state=new YamlConfiguration();
            state.set("coins",coins(a)); state.set("relics",relics(a)); state.set("snow",snow(a));
            state.set("dailyReady",data().getLong(root(a)+".dailyReady"));
            state.set("gladiatorReady",data().getLong(root(a)+".gladiatorReady"));
            state.save(getDataFolder().toPath().resolve("probe-state.yml").toFile());
            finish();
        } catch(Exception error) { throw new RuntimeException(error); }
    }
    private void restart() {
        YamlConfiguration state=YamlConfiguration.loadConfiguration(getDataFolder().toPath().resolve("probe-state.yml").toFile());
        ok(coins(a)==state.getDouble("coins") && relics(a)==state.getInt("relics") && snow(a)==state.getInt("snow"),
                "local reward balances survive SIGKILL and restart");
        ok(data().getLong(root(a)+".dailyReady")==state.getLong("dailyReady")
                && data().getLong(root(a)+".gladiatorReady")==state.getLong("gladiatorReady"),
                "daily and gladiator cooldowns survive SIGKILL");
        for(int level=1;level<=15;level++)ok(store.hasClaimedBp(a.getName(),level), "battle-pass level "+level+" survives SIGKILL");
        for(String id:EXTRAS)ok(data().getBoolean("achievements."+a.getUniqueId()+".claimed."+id), "achievement "+id+" survives SIGKILL");
        ok(store.hasUnlockedTitle(a.getName(),"king_achiever") && store.getClanOf(a.getName())!=null
                && store.getClanOf(a.getName()).kills==1, "original title and single clan kill survive restart");
        double c=coins(a); int r=relics(a),s=snow(a);
        a.performCommand("daily"); a.performCommand("bp claim"); a.performCommand("achievements claim");
        ok(coins(a)==c && relics(a)==r && snow(a)==s, "replayed claims after crash do not pay a second time");
        a.performCommand("bp"); ok(top().getItem(28).getType()==Material.MINECART, "last claimed tier remains marked in the GUI");
        finish();
    }
}
