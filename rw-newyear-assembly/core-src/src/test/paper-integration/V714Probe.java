package net.kinggrief.probe;

import net.kinggrief.core.CuratorManager;
import net.kinggrief.core.KingGriefCore;
import net.kinggrief.core.RankManager;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.command.BlockCommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerCommandSendEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;

/** Role hierarchy, creative-mode boundaries, and real Paper/Log4j diagnostics. */
public final class V714Probe extends JavaPlugin implements Listener {
    private KingGriefCore core;
    private Player one, two;
    private boolean started, finished, throwEvent;
    private int checks;

    public static final class ProbePlayerError extends RuntimeException {
        ProbePlayerError(String s) { super(s); }
    }
    public static final class ProbeGlobalError extends RuntimeException {
        ProbeGlobalError(String s) { super(s); }
    }
    public static final class ProbeWarning extends RuntimeException {
        ProbeWarning(String s) { super(s); }
    }

    @Override public void onEnable() {
        core = (KingGriefCore) Bukkit.getPluginManager().getPlugin("KingGriefCore");
        Bukkit.getPluginManager().registerEvents(this, this);
    }
    @EventHandler public void join(PlayerJoinEvent e) {
        if ("V714One".equals(e.getPlayer().getName())) one = e.getPlayer();
        if ("V714Two".equals(e.getPlayer().getName())) two = e.getPlayer();
        if (!started && one != null && two != null) { started = true; later(70, this::run); }
    }
    @EventHandler(priority = EventPriority.HIGHEST)
    public void brokenInteraction(PlayerInteractEvent e) {
        if (throwEvent && e.getPlayer().equals(two) && e.getAction() == Action.PHYSICAL) {
            throwEvent = false;
            throw new ProbePlayerError("secret=SECRET_PLAYER_DO_NOT_SEND token=TOKEN_PLAYER cookie=COOKIE_PLAYER /login PASSWORD_PLAYER 192.0.2.22");
        }
    }
    private void later(long delay, Runnable action) {
        Bukkit.getScheduler().runTaskLater(this, () -> { try { action.run(); } catch (Throwable error) { fail(error); } }, delay);
    }
    private void ok(boolean condition, String name) {
        if (!condition) throw new AssertionError(name);
        checks++; getLogger().info("KG-V714 PASS " + name);
    }
    private void fail(Throwable error) {
        if (finished) return;
        finished = true; getLogger().severe("KG-V714 RESULT FAIL " + error); error.printStackTrace();
    }
    private void finish() { finished = true; getLogger().info("KG-V714 RESULT PASS " + checks + " checks"); }
    private Object field(Object object, String name) {
        try { Field f = object.getClass().getDeclaredField(name); f.setAccessible(true); return f.get(object); }
        catch (Exception error) { throw new RuntimeException(error); }
    }
    private void command(Player p, String command) { Bukkit.dispatchCommand(p, command); }
    private Set<String> visible(Player p) {
        Set<String> commands = new HashSet<String>(Arrays.asList("gm", "gms", "gmc", "gamemode", "tp", "log", "curator", "staffchat", "sc"));
        Bukkit.getPluginManager().callEvent(new PlayerCommandSendEvent(p, commands));
        return commands;
    }

    private void run() {
        RankManager r = core.getRanks();
        String[] order = {"owner", "techadmin", "curator_support", "deputy_curator_support", "curator_discord",
                "deputy_curator_discord", "curator_kp", "deputy_curator_kp", "curator_media", "deputy_curator_media",
                "curator_dkp", "deputy_curator_dkp", "curator", "deputy_curator", "gladmin", "stadmin", "admin",
                "mladmin", "glmoder", "stmoder", "moder", "mlmoder", "sthelper", "helper", "kp", "techsupport",
                "downer", "opka", "dadmin", "dmladmin", "dglmoder", "dstmoder", "dmoder", "dkp", "dhelper",
                "partner", "mediaplus", "media", "yt", "twitch", "tiktok", "sponsor", "king", "imperator", "god"};
        for (int i = 1; i < order.length; i++) {
            ok(r.exists(order[i]) && r.byId(order[i-1]).weight > r.byId(order[i]).weight, "rank order " + order[i]);
        }
        String[][] pairs = {{"curator", "deputy_curator"}, {"curator_dkp", "deputy_curator_dkp"},
                {"curator_media", "deputy_curator_media"}, {"curator_kp", "deputy_curator_kp"},
                {"curator_discord", "deputy_curator_discord"}, {"curator_support", "deputy_curator_support"}};
        for (String[] pair : pairs) {
            ok(r.isCuratorRank(pair[0]) && r.isCuratorRank(pair[1]), "both curator roles recognized " + pair[0]);
            r.setRank(one.getName(), pair[0], false);
            r.setRank("V714Subject", "default", false);
            core.getCurator().executeRankChange(one, "V714Subject", pair[1], null);
            ok(pair[1].equals(r.getRankByName("V714Subject").id), "curator can appoint own deputy " + pair[1]);
            core.getCurator().executeRankChange(one, "V714Subject", pair[0], null);
            ok(pair[1].equals(r.getRankByName("V714Subject").id), "curator cannot promote equal " + pair[0]);
            r.setRank(one.getName(), pair[1], false);
            r.setRank("V714Subject", "default", false);
            core.getCurator().executeRankChange(one, "V714Subject", pair[0], null);
            ok("default".equals(r.getRankByName("V714Subject").id), "deputy cannot promote own curator " + pair[1]);
            ok(visible(one).contains("curator"), "deputy sees curator command " + pair[1]);
        }
        for (String id : new String[]{"curator_support", "deputy_curator_support", "deputy_curator_discord",
                "deputy_curator_kp", "deputy_curator_media", "deputy_curator_dkp", "deputy_curator", "techsupport", "partner"}) {
            RankManager.RankDef def = r.byId(id);
            ok(def.priceRub == 0 && def.glyphIndex < 0 && r.getAnimatedBadge(def, 3).contains("["), "free text badge " + id);
        }
        ok(CuratorManager.MEDIA_RANKS.contains("partner") && !CuratorManager.DONATE_RANKS.contains("partner"), "partner is assignable media, not donate");
        r.setRank(one.getName(), "owner", false);
        ok(visible(one).contains("curator"), "OWNER can see curator command");
        r.setRank(one.getName(), "techadmin", false);
        ok(visible(one).contains("curator"), "TECH.ADMIN can see curator command");
        r.setRank(one.getName(), "owner", false);
        core.getCurator().openCategoryGui(one, "kp", "V714Subject");
        Inventory first = one.getOpenInventory().getTopInventory();
        ok(first.getItem(53) != null && first.getItem(53).getType() == Material.ARROW, "curator role menu has second page");
        Bukkit.getPluginManager().callEvent(new InventoryClickEvent(one.getOpenInventory(), InventoryType.SlotType.CONTAINER,
                53, ClickType.LEFT, InventoryAction.PICKUP_ALL));
        CuratorManager.CuratorHolder holder = (CuratorManager.CuratorHolder) one.getOpenInventory().getTopInventory().getHolder();
        ok(holder.pageIndex == 1 && one.getOpenInventory().getTopInventory().getItem(10) != null, "curator second page opens and displays roles");
        one.closeInventory();
        core.getGuis().openDonateMenu(one);
        String contents = Arrays.toString(one.getOpenInventory().getTopInventory().getContents()).toLowerCase();
        ok(!contents.contains("partner") && !contents.contains("партнёр"), "partner absent from in-game donate shop");
        one.closeInventory();

        r.setRank(one.getName(), "god", false); one.setGameMode(GameMode.SURVIVAL);
        command(one, "gm 1"); ok(one.getGameMode() == GameMode.SURVIVAL && !visible(one).contains("gm"), "GOD cannot use or see creative command");
        r.setRank(one.getName(), "imperator", false); two.setGameMode(GameMode.SURVIVAL);
        List<String> modes = core.onTabComplete(one, core.getCommand("gm"), "gm", new String[]{""});
        ok(modes.contains("0") && modes.contains("1") && !modes.contains("2") && !modes.contains("3"), "new rank completion lists only own modes");
        List<String> targets = core.onTabComplete(one, core.getCommand("gm"), "gm", new String[]{"1", ""});
        ok(targets.contains("V714One") && !targets.contains("V714Two"), "new rank completion does not suggest other targets");
        command(one, "gm 1 V714Two"); ok(two.getGameMode() == GameMode.SURVIVAL, "IMPERATOR cannot change another player");
        command(one, "gm 1 V714One"); ok(one.getGameMode() == GameMode.CREATIVE, "explicit own name uses self rules");
        command(one, "gm 0"); ok(one.getGameMode() == GameMode.SURVIVAL, "IMPERATOR can return to survival");
        command(one, "gm 2"); ok(one.getGameMode() == GameMode.SURVIVAL, "new self entitlement does not grant adventure");
        command(one, "gm 3"); ok(one.getGameMode() == GameMode.SURVIVAL, "new self entitlement does not grant spectator");
        command(one, "gm bogus"); ok(one.getGameMode() == GameMode.SURVIVAL, "invalid mode does not enable creative");
        command(one, "gmsp"); ok(one.getGameMode() == GameMode.SURVIVAL, "spectator alias does not grant creative or spectator");
        command(one, "gmc"); ok(one.getGameMode() == GameMode.CREATIVE, "creative alias enables own creative");
        command(one, "gms"); ok(one.getGameMode() == GameMode.SURVIVAL, "survival alias reverts own creative");
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "gm bogus V714One");
        ok(one.getGameMode() == GameMode.SURVIVAL, "console invalid mode does not enable creative");
        for (RankManager.RankDef def : r.allRanks()) {
            if (def.weight < 75) continue;
            r.setRank(one.getName(), def.id, false); one.setGameMode(GameMode.SURVIVAL);
            command(one, "gm 1");
            ok(one.getGameMode() == GameMode.CREATIVE && visible(one).contains("gm"), "creative from IMPERATOR " + def.id);
            command(one, "gm 0"); ok(one.getGameMode() == GameMode.SURVIVAL, "survival from creative " + def.id);
        }
        r.setRank(one.getName(), "techsupport", false);
        ok(!core.getModeration().isStaff(one) && !core.getCurator().hasCuratorAccess(one), "tech support has no moderation or curator access");
        ok(visible(one).contains("log") && visible(one).contains("staffchat") && !visible(one).contains("tp"), "support sees log and staff chat, not moderator tp");
        ok(one.hasPermission("kinggrief.errorlog"), "support permission node attached");
        r.setRank(one.getName(), "partner", false);
        ok(!visible(one).contains("log") && !visible(one).contains("curator"), "partner has no diagnostic or curator access");

        // A real JUL SEVERE and real Paper exception from another plugin listener.
        // A nickname in free-form log text is deliberately NOT attribution.
        getLogger().log(Level.SEVERE, "V714Two password=SECRET_GLOBAL_DO_NOT_SEND /login PASSWORD_GLOBAL 192.0.2.99", new ProbeGlobalError("TOKEN_GLOBAL cookie=COOKIE_GLOBAL"));
        getLogger().log(Level.WARNING, "must not appear", new ProbeWarning("warn"));
        throwEvent = true;
        Bukkit.getPluginManager().callEvent(new PlayerInteractEvent(two, Action.PHYSICAL, null, null, null, EquipmentSlot.HAND));
        later(15, this::checkLogs);
    }

    @SuppressWarnings("unchecked")
    private List<Object> entries() {
        Object manager = field(core, "errorLogs");
        Collection<Object> queue = (Collection<Object>) field(manager, "entries");
        synchronized (queue) { return new ArrayList<Object>(queue); }
    }
    private int find(String exceptionSuffix, String exactPlayer) {
        int count = 0;
        for (Object entry : entries()) {
            String exception = (String) field(entry, "exception"), player = (String) field(entry, "player");
            if (exception.endsWith(exceptionSuffix) && (exactPlayer == null ? player == null : exactPlayer.equals(player))) count++;
        }
        return count;
    }
    private void checkLogs() {
        ok(find("ProbeGlobalError", null) >= 1, "real SEVERE captured without text-based attribution");
        ok(find("ProbeGlobalError", "V714Two") == 0, "name in arbitrary message is not attribution");
        ok(find("ProbeWarning", null) == 0, "WARNING is excluded");
        ok(find("ProbePlayerError", "V714Two") == 1, "real Paper event exception attributed exactly once to its player");
        ok(find("ProbePlayerError", "V714One") == 0, "other player does not receive subject error");
        RankManager ranks = core.getRanks();
        ranks.setRank(one.getName(), "techsupport", false);
        one.sendMessage("KG714_BEGIN_PLAYER"); command(one, "log V714Two"); one.sendMessage("KG714_END_PLAYER");
        one.sendMessage("KG714_BEGIN_SERVER"); command(one, "log"); one.sendMessage("KG714_END_SERVER");
        one.sendMessage("KG714_BEGIN_WRONG"); command(one, "log V714Tw"); one.sendMessage("KG714_END_WRONG");
        ranks.setRank(one.getName(), "partner", false);
        one.sendMessage("KG714_BEGIN_DENIED"); command(one, "log"); one.sendMessage("KG714_END_DENIED");
        ranks.setRank(one.getName(), "curator_support", false); ok(visible(one).contains("log"), "support curator can see log");
        ranks.setRank(one.getName(), "deputy_curator_support", false); ok(visible(one).contains("log"), "support deputy can see log");
        ranks.setRank(one.getName(), "curator_kp", false); ok(!visible(one).contains("log"), "unrelated curator has no automatic error access");
        ranks.setRank(one.getName(), "techsupport", false);
        core.getConfig().set("enable-builtin-auth", true); core.getStore().setAuthenticated(one, false);
        one.sendMessage("KG714_BEGIN_UNAUTH"); command(one, "log"); one.sendMessage("KG714_END_UNAUTH");
        core.getStore().setAuthenticated(one, true); core.getConfig().set("enable-builtin-auth", false);
        core.getStore().toggleQuickFreeze(one.getUniqueId());
        one.sendMessage("KG714_BEGIN_FROZEN"); command(one, "log"); one.sendMessage("KG714_END_FROZEN");
        core.getStore().toggleQuickFreeze(one.getUniqueId());
        List<String> blocked = new ArrayList<String>();
        BlockCommandSender block = (BlockCommandSender) Proxy.newProxyInstance(getClassLoader(), new Class[]{BlockCommandSender.class}, (proxy, method, args) -> {
            if (method.getName().equals("sendMessage") && args != null) for (Object arg : args) if (arg instanceof String) blocked.add((String) arg);
            if (method.getReturnType() == boolean.class) return false;
            if (method.getReturnType() == int.class) return 0;
            return null;
        });
        core.getCommand("log").execute(block, "log", new String[0]);
        ok(!blocked.isEmpty(), "command block refused error access");
        later(30, this::capacity);
    }
    private void cleanup() {
        try {
            Object manager = field(core, "errorLogs");
            org.apache.logging.log4j.core.Appender appender = (org.apache.logging.log4j.core.Appender) field(manager, "appender");
            org.apache.logging.log4j.core.config.Configuration configuration =
                    (org.apache.logging.log4j.core.config.Configuration) field(manager, "attachedConfiguration");
            ok(configuration.getRootLogger().getAppenders().containsKey(appender.getName()), "appender attached to root logger");
            Method shutdown = manager.getClass().getDeclaredMethod("shutdown"); shutdown.setAccessible(true); shutdown.invoke(manager);
            ok(!configuration.getRootLogger().getAppenders().containsKey(appender.getName())
                    && !configuration.getAppenders().containsKey(appender.getName()), "appender removed from root and configuration on shutdown");
            ok(entries().isEmpty(), "shutdown clears in-memory errors");
            getLogger().log(Level.SEVERE, "after shutdown", new ProbeGlobalError("test"));
            ok(entries().isEmpty(), "disabled journal no longer captures new logs");
            later(15, this::finish);
        } catch (Exception error) { throw new RuntimeException(error); }
    }

    private void capacity() {
        try {
            Object manager = field(core, "errorLogs");
            Class<?> entryClass = Class.forName("net.kinggrief.core.ErrorLogManager$Entry", true, core.getClass().getClassLoader());
            Constructor<?> ctor = entryClass.getDeclaredConstructor(long.class, String.class, String.class, String.class, Throwable.class);
            ctor.setAccessible(true);
            Method add = manager.getClass().getDeclaredMethod("add", entryClass); add.setAccessible(true);
            for (int i = 0; i < 520; i++) add.invoke(manager, ctor.newInstance(System.currentTimeMillis(), "ERROR", "probe", "probe", null));
            ok(entries().size() == 500, "error ring stays bounded at 500 entries");
            one.sendMessage("KG714_BEGIN_PAGE"); command(one, "log -p 2"); one.sendMessage("KG714_END_PAGE");
            later(25, this::cleanup);
        } catch (Exception error) { throw new RuntimeException(error); }
    }
}
