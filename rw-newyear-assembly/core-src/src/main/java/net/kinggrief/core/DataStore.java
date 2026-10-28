package net.kinggrief.core;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.permissions.PermissionAttachment;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class DataStore {

    public static final class Clan {
        public String id;
        public String tag;
        public String displayName = "";
        public String color = "&#B388FF";
        public String leader;
        public int level = 1;
        public final Set<String> members = new HashSet<String>();
        public final Map<String, Integer> roles = new HashMap<String, Integer>(); // 0=Новичок, 1=Проверенный, 2=Модератор, 3=Зам.Лидера, 4=Лидер
        public final Set<String> allies = new HashSet<String>();
        public double bank = 0.0;
        public int kills = 0;
        public boolean friendlyFire = false;
        public Location home = null;
        public org.bukkit.inventory.Inventory liveStorage = null;

        public int getRole(String playerName) {
            if (playerName == null) return 0;
            if (leader != null && leader.equalsIgnoreCase(playerName)) return 4;
            Integer r = roles.get(playerName.toLowerCase(Locale.ROOT));
            return r != null ? r.intValue() : 0;
        }

        public String getRoleName(String playerName) {
            int r = getRole(playerName);
            if (r >= 4) return "&#FFD740👑 Лидер";
            if (r == 3) return "&#FF5252⚔ Зам.Лидера";
            if (r == 2) return "&#40C4FF🛡 Модератор";
            if (r == 1) return "&#69F0AE✦ Проверенный";
            return "&7Участник";
        }

        public int getMaxMembers() {
            return Math.min(35, 10 + (level - 1) * 3);
        }

        public int getStorageSize() {
            return level >= 5 ? 54 : (level >= 3 ? 36 : 27);
        }
    }

    public static final class Claim {
        public String id;
        public String owner;
        public String world;
        public int cx, cy, cz, radius;
        public final Set<String> members = new HashSet<String>();

        public boolean contains(Location loc) {
            if (loc == null || loc.getWorld() == null) return false;
            if (!loc.getWorld().getName().equalsIgnoreCase(world)) return false;
            int bx = loc.getBlockX(), bz = loc.getBlockZ();
            return Math.abs(bx - cx) <= radius && Math.abs(bz - cz) <= radius;
        }

        public boolean isCenter(Location loc) {
            if (loc == null || loc.getWorld() == null) return false;
            return loc.getWorld().getName().equalsIgnoreCase(world)
                    && loc.getBlockX() == cx && loc.getBlockY() == cy && loc.getBlockZ() == cz;
        }

        public boolean canAccess(String playerName) {
            if (playerName == null) return false;
            return owner.equalsIgnoreCase(playerName) || members.contains(playerName.toLowerCase(Locale.ROOT));
        }
    }

    public static final class AuctionLot {
        public int id;
        public String seller;
        public double price;
        public ItemStack item;
        public long createdAt;
    }

    public static final class CoinflipRoom {
        public int id;
        public String creator;
        public double bet;
        public long createdAt;
    }

    public static final class DuelRequest {
        public UUID challenger;
        public UUID target;
        public double bet;
        public long expiresAt;
    }

    private final KingGriefCore plugin;
    private final File file;
    private YamlConfiguration yml;

    // v6.3.2: читается из асинхронного потока чата/пре-логина -> потокобезопасное множество!
    private final Set<UUID> loggedIn = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private final Map<String, Clan> clans = new LinkedHashMap<String, Clan>();
    private final Map<String, String> playerClan = new java.util.concurrent.ConcurrentHashMap<String, String>();
    private final Map<String, Set<String>> clanInvites = new HashMap<String, Set<String>>();
    private final List<Claim> claims = new ArrayList<Claim>();
    private final List<AuctionLot> auction = new ArrayList<AuctionLot>();
    private int nextLotId = 1;

    private final List<CoinflipRoom> coinflips = new ArrayList<CoinflipRoom>();
    private int nextCfId = 1;
    private final Map<UUID, DuelRequest> duelRequests = new HashMap<UUID, DuelRequest>();
    private final Map<UUID, Location> parkourCheckpoints = new HashMap<UUID, Location>();
    private final Map<UUID, PermissionAttachment> attachments = new HashMap<UUID, PermissionAttachment>();

    private final Map<UUID, Location> backLocs = new HashMap<UUID, Location>();
    private final Map<UUID, UUID> tpaFrom = new HashMap<UUID, UUID>();
    private final Map<UUID, Boolean> tpaHere = new HashMap<UUID, Boolean>();
    private final Map<UUID, Long> tpaTime = new HashMap<UUID, Long>();
    private final Set<UUID> sbDisabled = new HashSet<UUID>();
    private final Set<UUID> vanished = new HashSet<UUID>();

    public DataStore(KingGriefCore plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "data.yml");
        load();
    }

    public synchronized void load() {
        if (!plugin.getDataFolder().exists()) {
            plugin.getDataFolder().mkdirs();
        }
        this.yml = file.exists() ? YamlConfiguration.loadConfiguration(file) : new YamlConfiguration();
        clans.clear();
        playerClan.clear();
        ConfigurationSection cs = yml.getConfigurationSection("clans");
        if (cs != null) {
            for (String cid : cs.getKeys(false)) {
                Clan c = new Clan();
                c.id = cid.toLowerCase(Locale.ROOT);
                c.tag = cs.getString(cid + ".tag", cid.toUpperCase(Locale.ROOT));
                c.displayName = cs.getString(cid + ".name", c.tag);
                c.color = cs.getString(cid + ".color", "&#B388FF");
                c.leader = cs.getString(cid + ".leader", "");
                c.level = Math.max(1, cs.getInt(cid + ".level", 1));
                c.bank = cs.getDouble(cid + ".bank", 0.0);
                c.kills = cs.getInt(cid + ".kills", 0);
                c.friendlyFire = cs.getBoolean(cid + ".ff", false);
                c.home = readLoc(cs.getString(cid + ".home", null));
                for (String al : cs.getStringList(cid + ".allies")) {
                    c.allies.add(al.toLowerCase(Locale.ROOT));
                }
                ConfigurationSection roleSec = cs.getConfigurationSection(cid + ".roles");
                if (roleSec != null) {
                    for (String rk : roleSec.getKeys(false)) {
                        c.roles.put(rk.toLowerCase(Locale.ROOT), Integer.valueOf(roleSec.getInt(rk, 0)));
                    }
                }
                for (String m : cs.getStringList(cid + ".members")) {
                    String low = m.toLowerCase(Locale.ROOT);
                    c.members.add(low);
                    playerClan.put(low, c.id);
                }
                int stSize = c.getStorageSize();
                c.liveStorage = Bukkit.createInventory(null, stSize, ColorUtil.c("&0📦 Хранилище Клана [" + c.tag + "]"));
                ConfigurationSection stSec = cs.getConfigurationSection(cid + ".storage");
                if (stSec != null) {
                    for (String sk : stSec.getKeys(false)) {
                        try {
                            int slot = Integer.parseInt(sk);
                            if (slot >= 0 && slot < stSize) {
                                ItemStack it = stSec.getItemStack(sk);
                                if (it != null) c.liveStorage.setItem(slot, it);
                            }
                        } catch (Throwable ignored) {}
                    }
                }
                clans.put(c.id, c);
            }
        }
        claims.clear();
        ConfigurationSection rs = yml.getConfigurationSection("claims");
        if (rs != null) {
            for (String rid : rs.getKeys(false)) {
                Claim cl = new Claim();
                cl.id = rid;
                cl.owner = rs.getString(rid + ".owner", "");
                cl.world = rs.getString(rid + ".world", "world");
                cl.cx = rs.getInt(rid + ".x");
                cl.cy = rs.getInt(rid + ".y");
                cl.cz = rs.getInt(rid + ".z");
                cl.radius = rs.getInt(rid + ".r", 10);
                for (String m : rs.getStringList(rid + ".members")) cl.members.add(m.toLowerCase(Locale.ROOT));
                claims.add(cl);
            }
        }
        auction.clear();
        nextLotId = yml.getInt("auctionNextId", 1);
        ConfigurationSection as = yml.getConfigurationSection("auction");
        if (as != null) {
            for (String k : as.getKeys(false)) {
                ItemStack it = as.getItemStack(k + ".item");
                if (it == null) continue;
                AuctionLot lot = new AuctionLot();
                lot.id = Integer.parseInt(k);
                lot.seller = as.getString(k + ".seller", "Server");
                lot.price = as.getDouble(k + ".price", 1000.0);
                lot.createdAt = as.getLong(k + ".time", System.currentTimeMillis());
                lot.item = it;
                auction.add(lot);
            }
        }
    }

    public synchronized void save() {
        yml.set("clans", null);
        for (Clan c : clans.values()) {
            String p = "clans." + c.id;
            yml.set(p + ".tag", c.tag);
            yml.set(p + ".name", c.displayName);
            yml.set(p + ".color", c.color);
            yml.set(p + ".leader", c.leader);
            yml.set(p + ".level", c.level);
            yml.set(p + ".bank", c.bank);
            yml.set(p + ".kills", c.kills);
            yml.set(p + ".ff", c.friendlyFire);
            yml.set(p + ".home", writeLoc(c.home));
            yml.set(p + ".allies", new ArrayList<String>(c.allies));
            yml.set(p + ".members", new ArrayList<String>(c.members));
            for (Map.Entry<String, Integer> re : c.roles.entrySet()) {
                yml.set(p + ".roles." + re.getKey(), re.getValue());
            }
            if (c.liveStorage != null) {
                for (int s = 0; s < c.liveStorage.getSize(); s++) {
                    ItemStack stIt = c.liveStorage.getItem(s);
                    if (stIt != null && stIt.getType() != org.bukkit.Material.AIR) {
                        yml.set(p + ".storage." + s, stIt);
                    }
                }
            }
        }
        yml.set("claims", null);
        for (Claim cl : claims) {
            String p = "claims." + cl.id;
            yml.set(p + ".owner", cl.owner);
            yml.set(p + ".world", cl.world);
            yml.set(p + ".x", cl.cx);
            yml.set(p + ".y", cl.cy);
            yml.set(p + ".z", cl.cz);
            yml.set(p + ".r", cl.radius);
            yml.set(p + ".members", new ArrayList<String>(cl.members));
        }
        yml.set("auctionNextId", nextLotId);
        yml.set("auction", null);
        for (AuctionLot lot : auction) {
            String p = "auction." + lot.id;
            yml.set(p + ".seller", lot.seller);
            yml.set(p + ".price", lot.price);
            yml.set(p + ".time", lot.createdAt);
            yml.set(p + ".item", lot.item);
        }
        try {
            yml.save(file);
        } catch (IOException ignored) {
        }
    }

    private String key(String name) {
        return name == null ? "unknown" : name.toLowerCase(Locale.ROOT);
    }

    // ================= AUTH =================
    public boolean isExternalAuthActive() {
        return Bukkit.getPluginManager().isPluginEnabled("AuthMe")
                || !plugin.getConfig().getBoolean("enable-builtin-auth", true);
    }

    public boolean isRegistered(String name) {
        String h = yml.getString("players." + key(name) + ".pass", "");
        return h != null && !h.isEmpty();
    }

    public boolean isAuthenticated(Player p) {
        if (isExternalAuthActive()) return true;
        return loggedIn.contains(p.getUniqueId());
    }

    public void setAuthenticated(Player p, boolean auth) {
        if (auth) {
            loggedIn.add(p.getUniqueId());
            String ip = getIp(p);
            yml.set("players." + key(p.getName()) + ".lastIp", ip);
            yml.set("players." + key(p.getName()) + ".sessionUntil", System.currentTimeMillis() + 6L * 3600_000L);
        } else {
            loggedIn.remove(p.getUniqueId());
            yml.set("players." + key(p.getName()) + ".sessionUntil", 0L);
        }
    }

    public boolean trySessionAutoLogin(Player p) {
        if (!isRegistered(p.getName())) return false;
        String savedIp = yml.getString("players." + key(p.getName()) + ".lastIp", "");
        long until = yml.getLong("players." + key(p.getName()) + ".sessionUntil", 0L);
        if (!savedIp.isEmpty() && savedIp.equals(getIp(p)) && System.currentTimeMillis() < until) {
            loggedIn.add(p.getUniqueId());
            return true;
        }
        return false;
    }

    public void registerPassword(Player p, String rawPass) {
        String salt = UUID.randomUUID().toString().substring(0, 8);
        String hash = salt + "$" + ColorUtil.sha256(salt + ":" + rawPass);
        String k = "players." + key(p.getName());
        yml.set(k + ".pass", hash);
        if (!yml.contains(k + ".initialized")) {
            yml.set(k + ".initialized", true);
            yml.set(k + ".coins", plugin.getConfig().getDouble("starting-coins", 2500.0));
            yml.set(k + ".relics", plugin.getConfig().getInt("starting-relics", 50));
            addCaseKeys(p.getName(), "summer", 1);
        }
        setAuthenticated(p, true);
        save();
    }

    public boolean checkPassword(String name, String rawPass) {
        String stored = yml.getString("players." + key(name) + ".pass", "");
        if (stored == null || !stored.contains("$")) return false;
        String[] parts = stored.split("\\$", 2);
        return parts[1].equals(ColorUtil.sha256(parts[0] + ":" + rawPass));
    }

    public void changePassword(String name, String rawPass) {
        String salt = UUID.randomUUID().toString().substring(0, 8);
        String hash = salt + "$" + ColorUtil.sha256(salt + ":" + rawPass);
        yml.set("players." + key(name) + ".pass", hash);
        save();
    }

    public String getPlayerIp(Player p) {
        return getIp(p);
    }

    private String getIp(Player p) {
        try {
            if (p != null && p.getAddress() != null && p.getAddress().getAddress() != null) {
                return p.getAddress().getAddress().getHostAddress();
            }
        } catch (Throwable ignored) {
        }
        return "";
    }

    public String ipKey(String rawIp) {
        if (rawIp == null) return "";
        return rawIp.trim().toLowerCase(Locale.ROOT).replace('.', '_').replace(':', '_');
    }

    public static boolean looksLikeIp(String s) {
        if (s == null || s.isEmpty()) return false;
        String t = s.trim();
        if (t.contains(":")) return true;
        String[] parts = t.split("\\.");
        if (parts.length != 4) return false;
        for (String p : parts) {
            if (p.isEmpty() || p.length() > 3) return false;
            for (int i = 0; i < p.length(); i++) {
                if (!Character.isDigit(p.charAt(i))) return false;
            }
        }
        return true;
    }

    public void recordPlayerIp(String playerName, String ip) {
        if (playerName == null || playerName.isEmpty() || ip == null || ip.isEmpty()) return;
        String k = "players." + key(playerName);
        yml.set(k + ".joinIp", ip);
        yml.set(k + ".lastIp", ip);
        yml.set(k + ".displayName", playerName);
        String ik = "ipHistory." + ipKey(ip);
        List<String> accs = new ArrayList<String>(yml.getStringList(ik));
        boolean found = false;
        for (String ex : accs) {
            if (ex.equalsIgnoreCase(playerName)) { found = true; break; }
        }
        if (!found) {
            accs.add(playerName);
            yml.set(ik, accs);
        }
    }

    public String resolveIpForTarget(String nickOrIp) {
        if (nickOrIp == null || nickOrIp.trim().isEmpty()) return "";
        String t = nickOrIp.trim();
        if (looksLikeIp(t)) return t;
        Player on = Bukkit.getPlayerExact(t);
        if (on != null) {
            String live = getIp(on);
            if (!live.isEmpty()) return live;
        }
        String jIp = yml.getString("players." + key(t) + ".joinIp", "");
        if (jIp != null && !jIp.isEmpty()) return jIp;
        String lIp = yml.getString("players." + key(t) + ".lastIp", "");
        return lIp == null ? "" : lIp;
    }

    public List<String> getAccountsByIp(String ip) {
        List<String> out = new ArrayList<String>();
        if (ip == null || ip.isEmpty()) return out;
        for (String n : yml.getStringList("ipHistory." + ipKey(ip))) {
            boolean dup = false;
            for (String ex : out) if (ex.equalsIgnoreCase(n)) { dup = true; break; }
            if (!dup) out.add(n);
        }
        ConfigurationSection ps = yml.getConfigurationSection("players");
        if (ps != null) {
            for (String pk : ps.getKeys(false)) {
                String j = ps.getString(pk + ".joinIp", ps.getString(pk + ".lastIp", ""));
                if (ip.equalsIgnoreCase(j)) {
                    String disp = ps.getString(pk + ".displayName", pk);
                    boolean dup = false;
                    for (String ex : out) if (ex.equalsIgnoreCase(disp)) { dup = true; break; }
                    if (!dup) out.add(disp);
                }
            }
        }
        return out;
    }

    public void ensureProfileInitialized(Player p) {
        String k = "players." + key(p.getName());
        String ip = getIp(p);
        if (!ip.isEmpty()) {
            recordPlayerIp(p.getName(), ip);
        }
        if (!yml.contains(k + ".initialized")) {
            yml.set(k + ".initialized", true);
            yml.set(k + ".coins", plugin.getConfig().getDouble("starting-coins", 2500.0));
            yml.set(k + ".relics", plugin.getConfig().getInt("starting-relics", 50));
            addCaseKeys(p.getName(), "summer", 1);
        }
    }

    // ================= LUCKPERMS BUILT-IN ENGINE & ATTACHMENTS =================
    public Map<String, Map<String, Object>> getLpGroupOverrides() {
        Map<String, Map<String, Object>> out = new LinkedHashMap<String, Map<String, Object>>();
        ConfigurationSection gs = yml.getConfigurationSection("lp.groups");
        if (gs != null) {
            for (String g : gs.getKeys(false)) {
                Map<String, Object> m = new HashMap<String, Object>();
                if (gs.contains(g + ".weight")) m.put("weight", Integer.valueOf(gs.getInt(g + ".weight")));
                if (gs.contains(g + ".prefix")) m.put("prefix", gs.getString(g + ".prefix"));
                out.put(g.toLowerCase(Locale.ROOT), m);
            }
        }
        return out;
    }

    public void saveLpGroupMeta(String group, int weight, String prefix) {
        String g = group.toLowerCase(Locale.ROOT);
        yml.set("lp.groups." + g + ".weight", Integer.valueOf(weight));
        yml.set("lp.groups." + g + ".prefix", prefix);
        save();
    }

    public void deleteLpGroup(String group) {
        yml.set("lp.groups." + group.toLowerCase(Locale.ROOT), null);
        save();
    }

    public Map<String, Boolean> getGroupPermissions(String group) {
        Map<String, Boolean> out = new LinkedHashMap<String, Boolean>();
        List<String> list = yml.getStringList("lp.groups." + group.toLowerCase(Locale.ROOT) + ".perms");
        for (String line : list) {
            if (line.startsWith("-")) out.put(line.substring(1), Boolean.FALSE);
            else out.put(line, Boolean.TRUE);
        }
        return out;
    }

    public void setGroupPermission(String group, String node, boolean value) {
        String g = group.toLowerCase(Locale.ROOT);
        Map<String, Boolean> map = getGroupPermissions(g);
        map.put(node.toLowerCase(Locale.ROOT), Boolean.valueOf(value));
        savePermMap("lp.groups." + g + ".perms", map);
    }

    public void unsetGroupPermission(String group, String node) {
        String g = group.toLowerCase(Locale.ROOT);
        Map<String, Boolean> map = getGroupPermissions(g);
        map.remove(node.toLowerCase(Locale.ROOT));
        savePermMap("lp.groups." + g + ".perms", map);
    }

    public int clearGroupPermissions(String group) {
        String g = group.toLowerCase(Locale.ROOT);
        int count = getGroupPermissions(g).size();
        yml.set("lp.groups." + g + ".perms", null);
        save();
        return count;
    }

    public Map<String, Boolean> getUserPermissions(String playerName) {
        Map<String, Boolean> out = new LinkedHashMap<String, Boolean>();
        List<String> list = yml.getStringList("players." + key(playerName) + ".lpPerms");
        for (String line : list) {
            if (line.startsWith("-")) out.put(line.substring(1), Boolean.FALSE);
            else out.put(line, Boolean.TRUE);
        }
        return out;
    }

    public void setUserPermission(String playerName, String node, boolean value) {
        Map<String, Boolean> map = getUserPermissions(playerName);
        map.put(node.toLowerCase(Locale.ROOT), Boolean.valueOf(value));
        savePermMap("players." + key(playerName) + ".lpPerms", map);
    }

    public void unsetUserPermission(String playerName, String node) {
        Map<String, Boolean> map = getUserPermissions(playerName);
        map.remove(node.toLowerCase(Locale.ROOT));
        savePermMap("players." + key(playerName) + ".lpPerms", map);
    }

    public int clearUserPermissions(String playerName) {
        int count = getUserPermissions(playerName).size();
        yml.set("players." + key(playerName) + ".lpPerms", null);
        save();
        return count;
    }

    private static boolean isOwnerOnlyPermNode(String node) {
        if (node == null) return false;
        String n = node.toLowerCase(Locale.ROOT);
        return n.startsWith("luckperms") || "kinggrief.lp".equals(n) || "kinggrief.owner".equals(n)
                || "kinggrief.spawn.edit".equals(n)
                || "minecraft.command.op".equals(n) || "minecraft.command.deop".equals(n)
                || "bukkit.command.op".equals(n) || "bukkit.command.deop".equals(n)
                || "minecraft.command.stop".equals(n) || "bukkit.command.reload".equals(n)
                || n.startsWith("commandpanels.admin");
    }

    private void savePermMap(String path, Map<String, Boolean> map) {
        List<String> list = new ArrayList<String>();
        for (Map.Entry<String, Boolean> e : map.entrySet()) {
            list.add(e.getValue().booleanValue() ? e.getKey() : ("-" + e.getKey()));
        }
        yml.set(path, list);
        save();
    }

    public String getUserCustomPrefix(String name) {
        return yml.getString("players." + key(name) + ".lpPrefix", "");
    }

    public void setUserCustomPrefix(String name, String prefix) {
        yml.set("players." + key(name) + ".lpPrefix", prefix == null ? null : prefix);
        save();
    }

    public void refreshPermissions(Player p, RankManager ranks) {
        if (p == null || !p.isOnline()) return;
        PermissionAttachment old = attachments.remove(p.getUniqueId());
        if (old != null) {
            try { p.removeAttachment(old); } catch (Throwable ignored) {}
        }
        PermissionAttachment att = p.addAttachment(plugin);
        attachments.put(p.getUniqueId(), att);

        RankManager.RankDef rank = ranks.getRank(p);
        boolean isOwner = "owner".equalsIgnoreCase(rank.id);

        // STRICT OWNER-ONLY OP GUARD: nobody except OWNER can ever hold vanilla OP ("звезду" оператора)!
        if (!isOwner && p.isOp()) {
            try { p.setOp(false); } catch (Throwable ignored) {}
        }

        // Built-in tier permissions
        if (rank.weight >= 35) att.setPermission("kinggrief.workbench", true);
        if (rank.weight >= 50) att.setPermission("kinggrief.feed", true);
        if (rank.weight >= 65) {
            att.setPermission("kinggrief.heal", true);
            att.setPermission("kinggrief.enderchest", true);
        }
        if (rank.weight >= 90) {
            att.setPermission("kinggrief.fly", true);
            att.setPermission("kinggrief.repair", true);
        }
        if (rank.tier >= 4) {
            att.setPermission("kinggrief.staff", true);
        }
        if ("techadmin".equalsIgnoreCase(rank.id)) {
            att.setPermission("kinggrief.admin", true);
            // v6.1.2: тех.админу выдан полный LuckPerms (/lp)!
            att.setPermission("luckperms.*", true);
            att.setPermission("kinggrief.lp", true);
        }
        // v6.2.1: TECH.ADMIN получает ПОЛНЫЙ набор пермишенов как OWNER!
        if (isOwner || "techadmin".equalsIgnoreCase(rank.id)) {
            att.setPermission("*", true);
            att.setPermission("luckperms.*", true);
            att.setPermission("kinggrief.admin", true);
            att.setPermission("kinggrief.lp", true);
            att.setPermission("kinggrief.owner", true);
            att.setPermission("kinggrief.spawn.edit", true);
        }

        // Apply group custom permissions (filtering out LP/OP nodes for non-OWNER!)
        for (Map.Entry<String, Boolean> gp : getGroupPermissions("default").entrySet()) {
            if (!isOwner && ("*".equals(gp.getKey()) || isOwnerOnlyPermNode(gp.getKey()))) continue;
            att.setPermission(gp.getKey(), gp.getValue().booleanValue());
        }
        for (Map.Entry<String, Boolean> gp : getGroupPermissions(rank.id).entrySet()) {
            if (!isOwner && ("*".equals(gp.getKey()) || isOwnerOnlyPermNode(gp.getKey()))) continue;
            att.setPermission(gp.getKey(), gp.getValue().booleanValue());
        }
        // Apply user custom permissions (OWNER can grant `*`, and clear it via `/lp user <nick> permission clear`!)
        for (Map.Entry<String, Boolean> up : getUserPermissions(p.getName()).entrySet()) {
            if (!isOwner && isOwnerOnlyPermNode(up.getKey())) continue;
            att.setPermission(up.getKey(), up.getValue().booleanValue());
        }

        // Even if a non-owner player was granted `*`, NEVER allow them to touch LP or /op!
        if (!isOwner && !"techadmin".equalsIgnoreCase(rank.id)) {
            att.setPermission("luckperms.*", false);
            att.setPermission("kinggrief.lp", false);
            att.setPermission("kinggrief.owner", false);
            att.setPermission("minecraft.command.op", false);
            att.setPermission("minecraft.command.deop", false);
            att.setPermission("bukkit.command.op", false);
            att.setPermission("bukkit.command.deop", false);
        }

        try { p.recalculatePermissions(); } catch (Throwable ignored) {}
        try { p.getClass().getMethod("updateCommands").invoke(p); } catch (Throwable ignored) {}
    }

    public void removeAttachment(Player p) {
        PermissionAttachment old = attachments.remove(p.getUniqueId());
        if (old != null) {
            try { p.removeAttachment(old); } catch (Throwable ignored) {}
        }
    }

    // ================= TITLES & PLAYTIME & SNOWFLAKES =================
    public String getActiveTitleDisplay(String name) {
        return yml.getString("players." + key(name) + ".activeTitle", "");
    }

    public void setActiveTitleDisplay(String name, String display) {
        yml.set("players." + key(name) + ".activeTitle", display == null ? "" : display);
        save();
    }

    public boolean hasUnlockedTitle(String name, String titleId) {
        return yml.getBoolean("players." + key(name) + ".titles." + titleId.toLowerCase(Locale.ROOT), false);
    }

    public void unlockTitle(String name, String titleId) {
        yml.set("players." + key(name) + ".titles." + titleId.toLowerCase(Locale.ROOT), Boolean.TRUE);
        save();
    }

    public long getPlaySeconds(String name) {
        return yml.getLong("players." + key(name) + ".playSec", 0L);
    }

    public void addPlaySeconds(String name, long delta) {
        yml.set("players." + key(name) + ".playSec", getPlaySeconds(name) + delta);
    }

    public boolean hasClaimedPlayReward(String name, String rewardId) {
        return yml.getBoolean("players." + key(name) + ".playRewards." + rewardId, false);
    }

    public void setClaimedPlayReward(String name, String rewardId) {
        yml.set("players." + key(name) + ".playRewards." + rewardId, Boolean.TRUE);
        save();
    }

    public int getSnowflakes(String name) {
        return yml.getInt("players." + key(name) + ".snowflakes", 0);
    }

    public void addSnowflakes(String name, int delta) {
        yml.set("players." + key(name) + ".snowflakes", Math.max(0, getSnowflakes(name) + delta));
    }

    public boolean takeSnowflakes(String name, int amt) {
        int cur = getSnowflakes(name);
        if (cur < amt) return false;
        yml.set("players." + key(name) + ".snowflakes", cur - amt);
        return true;
    }

    // ================= PARKOUR & CASINO COINFLIP & DUELS =================
    public void setParkourCp(Player p, Location loc) {
        if (p != null) {
            if (loc == null) parkourCheckpoints.remove(p.getUniqueId());
            else parkourCheckpoints.put(p.getUniqueId(), loc.clone());
        }
    }

    public Location getParkourCp(Player p) {
        return p == null ? null : parkourCheckpoints.get(p.getUniqueId());
    }

    public long getParkourRewardReadyAt(String name) {
        return yml.getLong("players." + key(name) + ".pkReady", 0L);
    }

    public void setParkourRewardReadyAt(String name, long ts) {
        yml.set("players." + key(name) + ".pkReady", ts);
    }

    public synchronized List<CoinflipRoom> getCoinflips() {
        return new ArrayList<CoinflipRoom>(coinflips);
    }

    public synchronized CoinflipRoom createCoinflip(String creator, double bet) {
        CoinflipRoom r = new CoinflipRoom();
        r.id = nextCfId++;
        r.creator = creator;
        r.bet = bet;
        r.createdAt = System.currentTimeMillis();
        coinflips.add(0, r);
        return r;
    }

    public synchronized CoinflipRoom takeCoinflip(int id) {
        for (int i = 0; i < coinflips.size(); i++) {
            if (coinflips.get(i).id == id) {
                return coinflips.remove(i);
            }
        }
        return null;
    }

    public void putDuelRequest(Player challenger, Player target, double bet) {
        DuelRequest dr = new DuelRequest();
        dr.challenger = challenger.getUniqueId();
        dr.target = target.getUniqueId();
        dr.bet = bet;
        dr.expiresAt = System.currentTimeMillis() + 60_000L;
        duelRequests.put(target.getUniqueId(), dr);
    }

    public DuelRequest popDuelRequest(Player target) {
        DuelRequest dr = duelRequests.remove(target.getUniqueId());
        if (dr == null || System.currentTimeMillis() > dr.expiresAt) return null;
        return dr;
    }

    // ================= RANK, ECONOMY & RELICS =================
    public String getSavedRank(String name) {
        return yml.getString("players." + key(name) + ".rank", "default");
    }

    public void setSavedRank(String name, String rank) {
        yml.set("players." + key(name) + ".rank", rank.toLowerCase(Locale.ROOT));
    }

    public Map<String, String> getAllSavedPlayerRanks() {
        Map<String, String> out = new java.util.LinkedHashMap<String, String>();
        org.bukkit.configuration.ConfigurationSection sec = yml.getConfigurationSection("players");
        if (sec != null) {
            for (String k : sec.getKeys(false)) {
                String r = sec.getString(k + ".rank", "default");
                if (r != null && !"default".equalsIgnoreCase(r)) {
                    out.put(k, r.toLowerCase(Locale.ROOT));
                }
            }
        }
        return out;
    }

    public List<String> getCuratorAuditLog() {
        List<String> list = yml.getStringList("curator-log");
        return list != null ? new ArrayList<String>(list) : new ArrayList<String>();
    }

    public void appendCuratorAuditLog(String entry) {
        List<String> list = getCuratorAuditLog();
        list.add(0, entry);
        while (list.size() > 40) list.remove(list.size() - 1);
        yml.set("curator-log", list);
        save();
    }

    public double getCoins(String name) {
        return yml.getDouble("players." + key(name) + ".coins", plugin.getConfig().getDouble("starting-coins", 2500.0));
    }

    public void setCoins(String name, double val) {
        yml.set("players." + key(name) + ".coins", Math.max(0.0, val));
    }

    public void addCoins(String name, double delta) {
        setCoins(name, getCoins(name) + delta);
    }

    public boolean takeCoins(String name, double amount) {
        double cur = getCoins(name);
        if (cur < amount) return false;
        setCoins(name, cur - amount);
        return true;
    }

    public List<Map.Entry<String, Double>> getBalTop(int limit) {
        List<Map.Entry<String, Double>> list = new ArrayList<Map.Entry<String, Double>>();
        ConfigurationSection ps = yml.getConfigurationSection("players");
        if (ps != null) {
            for (String u : ps.getKeys(false)) {
                double c = ps.getDouble(u + ".coins", 0.0);
                list.add(new HashMap.SimpleEntry<String, Double>(u, Double.valueOf(c)));
            }
        }
        Collections.sort(list, new Comparator<Map.Entry<String, Double>>() {
            @Override
            public int compare(Map.Entry<String, Double> a, Map.Entry<String, Double> b) {
                return Double.compare(b.getValue().doubleValue(), a.getValue().doubleValue());
            }
        });
        return list.size() > limit ? list.subList(0, limit) : list;
    }

    public int getRelics(Player p) {
        int pp = readPlayerPoints(p.getUniqueId());
        int local = yml.getInt("players." + key(p.getName()) + ".relics", 50);
        if (pp >= 0) {
            return Math.max(pp, local);
        }
        return local;
    }

    public int getRelics(String name) {
        Player p = org.bukkit.Bukkit.getPlayerExact(name);
        if (p != null) return getRelics(p);
        return yml.getInt("players." + key(name) + ".relics", 50);
    }

    public int getRelicsByName(String name) {
        Player on = Bukkit.getPlayerExact(name);
        if (on != null) return getRelics(on);
        return yml.getInt("players." + key(name) + ".relics", 50);
    }

    public void addRelics(String name, int amount) {
        int cur = getRelicsByName(name);
        int next = Math.max(0, cur + amount);
        yml.set("players." + key(name) + ".relics", next);
        Player on = Bukkit.getPlayerExact(name);
        if (on != null) writePlayerPoints(on.getUniqueId(), next);
    }

    public void setRelics(String name, int amount) {
        int next = Math.max(0, amount);
        yml.set("players." + key(name) + ".relics", next);
        Player on = Bukkit.getPlayerExact(name);
        if (on != null) writePlayerPoints(on.getUniqueId(), next);
    }

    public boolean takeRelics(Player p, int amount) {
        int cur = getRelics(p);
        if (cur < amount) return false;
        int next = cur - amount;
        yml.set("players." + key(p.getName()) + ".relics", next);
        writePlayerPoints(p.getUniqueId(), next);
        return true;
    }

    private int readPlayerPoints(UUID uuid) {
        try {
            Object pl = Bukkit.getPluginManager().getPlugin("PlayerPoints");
            if (pl == null) return -1;
            Object api = pl.getClass().getMethod("getAPI").invoke(pl);
            Object res = api.getClass().getMethod("look", UUID.class).invoke(api, uuid);
            return res instanceof Number ? ((Number) res).intValue() : -1;
        } catch (Throwable ignored) {
            return -1;
        }
    }

    private void writePlayerPoints(UUID uuid, int amount) {
        try {
            Object pl = Bukkit.getPluginManager().getPlugin("PlayerPoints");
            if (pl == null) return;
            Object api = pl.getClass().getMethod("getAPI").invoke(pl);
            Method setM = api.getClass().getMethod("set", UUID.class, int.class);
            setM.invoke(api, uuid, Integer.valueOf(amount));
        } catch (Throwable ignored) {
        }
    }

    // ================= STATS, JOBS, PROMOS, BP & KITS =================
    public int getKills(String name) { return yml.getInt("players." + key(name) + ".kills", 0); }
    public int getDeaths(String name) { return yml.getInt("players." + key(name) + ".deaths", 0); }
    public int getMinedBlocks(String name) { return yml.getInt("players." + key(name) + ".mined", 0); }
    public void addKill(String name) {
        yml.set("players." + key(name) + ".kills", getKills(name) + 1);
        Clan c = getClanOf(name);
        if (c != null) c.kills++;
    }
    public void addDeath(String name) { yml.set("players." + key(name) + ".deaths", getDeaths(name) + 1); }
    public void addMined(String name) { yml.set("players." + key(name) + ".mined", getMinedBlocks(name) + 1); }

    public String getJob(String name) { return yml.getString("players." + key(name) + ".job", "none"); }
    public void setJob(String name, String job) { yml.set("players." + key(name) + ".job", job); }

    public boolean hasClaimedBp(String name, int level) {
        return yml.getBoolean("players." + key(name) + ".bp." + level, false);
    }
    public void setClaimedBp(String name, int level) {
        yml.set("players." + key(name) + ".bp." + level, true);
    }

    public boolean hasUsedPromo(String name, String code) {
        return yml.getBoolean("players." + key(name) + ".promos." + code.toLowerCase(Locale.ROOT), false);
    }
    public void markUsedPromo(String name, String code) {
        yml.set("players." + key(name) + ".promos." + code.toLowerCase(Locale.ROOT), true);
    }

    public long getKitReadyAt(String name, String kit) {
        return yml.getLong("players." + key(name) + ".kits." + kit.toLowerCase(Locale.ROOT), 0L);
    }
    public void setKitReadyAt(String name, String kit, long ts) {
        yml.set("players." + key(name) + ".kits." + kit.toLowerCase(Locale.ROOT), ts);
    }

    public long getDailyReadyAt(String name) {
        return yml.getLong("players." + key(name) + ".dailyReady", 0L);
    }
    public void setDailyReadyAt(String name, long ts) {
        yml.set("players." + key(name) + ".dailyReady", ts);
    }

    // ================= CASE KEYS =================
    public int getCaseKeys(String name, String caseId) {
        return yml.getInt("players." + key(name) + ".cases." + caseId.toLowerCase(Locale.ROOT), 0);
    }

    public void addCaseKeys(String name, String caseId, int delta) {
        String c = caseId.toLowerCase(Locale.ROOT);
        int next = Math.max(0, getCaseKeys(name, c) + delta);
        yml.set("players." + key(name) + ".cases." + c, next);
    }

    // ================= CLANS =================
    public Clan getClanOf(String playerName) {
        String cid = playerClan.get(key(playerName));
        return cid != null ? clans.get(cid) : null;
    }

    public Clan getClanById(String id) {
        return id == null ? null : clans.get(id.toLowerCase(Locale.ROOT));
    }

    public Collection<Clan> getAllClans() {
        return clans.values();
    }

    public Clan createClan(String leaderName, String rawTag) {
        String cid = ChatColorStrip(rawTag).toLowerCase(Locale.ROOT);
        if (cid.isEmpty() || clans.containsKey(cid)) return null;
        Clan c = new Clan();
        c.id = cid;
        c.tag = rawTag;
        c.displayName = rawTag;
        c.leader = leaderName;
        c.members.add(key(leaderName));
        c.roles.put(key(leaderName), Integer.valueOf(4));
        c.liveStorage = Bukkit.createInventory(null, 27, ColorUtil.c("&0📦 Хранилище Клана [" + c.tag + "]"));
        clans.put(cid, c);
        playerClan.put(key(leaderName), cid);
        save();
        return c;
    }

    public org.bukkit.inventory.Inventory getOrCreateClanStorage(Clan c) {
        if (c == null) return null;
        int want = c.getStorageSize();
        if (c.liveStorage == null) {
            c.liveStorage = Bukkit.createInventory(null, want, ColorUtil.c("&0📦 Хранилище Клана [" + c.tag + "]"));
        } else if (c.liveStorage.getSize() < want) {
            org.bukkit.inventory.Inventory next = Bukkit.createInventory(null, want, ColorUtil.c("&0📦 Хранилище Клана [" + c.tag + "]"));
            for (int i = 0; i < c.liveStorage.getSize(); i++) {
                next.setItem(i, c.liveStorage.getItem(i));
            }
            c.liveStorage = next;
        }
        return c.liveStorage;
    }

    public void disbandClan(Clan c) {
        if (c == null) return;
        for (String m : c.members) playerClan.remove(m);
        for (Clan other : clans.values()) other.allies.remove(c.id);
        clans.remove(c.id);
        save();
    }

    public void inviteToClan(String cid, String targetName) {
        Set<String> set = clanInvites.get(key(targetName));
        if (set == null) {
            set = new LinkedHashSetCompat();
            clanInvites.put(key(targetName), set);
        }
        set.remove(cid.toLowerCase(Locale.ROOT));
        set.add(cid.toLowerCase(Locale.ROOT));
    }

    private static final class LinkedHashSetCompat extends java.util.LinkedHashSet<String> {}

    public boolean hasClanInvite(String targetName, String cid) {
        Set<String> set = clanInvites.get(key(targetName));
        return set != null && set.contains(cid.toLowerCase(Locale.ROOT));
    }

    public String getLatestClanInvite(String targetName) {
        Set<String> set = clanInvites.get(key(targetName));
        if (set == null || set.isEmpty()) return null;
        String last = null;
        for (String s : set) last = s;
        return last;
    }

    public boolean denyClanInvite(String targetName, String cid) {
        Set<String> set = clanInvites.get(key(targetName));
        if (set == null || set.isEmpty()) return false;
        if (cid == null || cid.isEmpty()) {
            String last = getLatestClanInvite(targetName);
            if (last != null) {
                set.remove(last);
                return true;
            }
            return false;
        }
        return set.remove(cid.toLowerCase(Locale.ROOT));
    }

    public void joinClan(String playerName, Clan c) {
        c.members.add(key(playerName));
        c.roles.put(key(playerName), Integer.valueOf(0));
        playerClan.put(key(playerName), c.id);
        Set<String> set = clanInvites.get(key(playerName));
        if (set != null) set.remove(c.id);
        save();
    }

    public void leaveClan(String playerName, Clan c) {
        c.members.remove(key(playerName));
        c.roles.remove(key(playerName));
        playerClan.remove(key(playerName));
        save();
    }

    public boolean hasClaimedFreeDragon(String name) {
        return yml.getBoolean("players." + key(name) + ".freeDragon", false);
    }

    public void markClaimedFreeDragon(String name) {
        yml.set("players." + key(name) + ".freeDragon", Boolean.TRUE);
        save();
    }

    private String ChatColorStrip(String s) {
        return s == null ? "" : s.replaceAll("(?i)&[0-9A-FK-ORX]", "").replaceAll("[^A-Za-zА-Яа-я0-9_]", "");
    }

    // ================= HOMES & TPA & BACK =================
    public Map<String, Location> getHomes(String name) {
        Map<String, Location> out = new LinkedHashMap<String, Location>();
        ConfigurationSection hs = yml.getConfigurationSection("players." + key(name) + ".homes");
        if (hs != null) {
            for (String h : hs.getKeys(false)) {
                Location l = readLoc(hs.getString(h));
                if (l != null) out.put(h, l);
            }
        }
        return out;
    }

    public void setHome(String name, String homeName, Location loc) {
        yml.set("players." + key(name) + ".homes." + homeName.toLowerCase(Locale.ROOT), writeLoc(loc));
        save();
    }

    public boolean delHome(String name, String homeName) {
        String p = "players." + key(name) + ".homes." + homeName.toLowerCase(Locale.ROOT);
        if (!yml.contains(p)) return false;
        yml.set(p, null);
        save();
        return true;
    }

    public void setBack(Player p, Location loc) { if (p != null && loc != null) backLocs.put(p.getUniqueId(), loc.clone()); }
    public Location getBack(Player p) { return backLocs.get(p.getUniqueId()); }

    public void sendTpa(Player sender, Player target, boolean here) {
        tpaFrom.put(target.getUniqueId(), sender.getUniqueId());
        tpaHere.put(target.getUniqueId(), Boolean.valueOf(here));
        tpaTime.put(target.getUniqueId(), Long.valueOf(System.currentTimeMillis() + 60_000L));
    }

    public static final class CustomWarp {
        public final String name;
        public final String creator;
        public final Location loc;
        public final long createdAt;

        public CustomWarp(String name, String creator, Location loc, long createdAt) {
            this.name = name;
            this.creator = creator;
            this.loc = loc;
            this.createdAt = createdAt;
        }
    }

    private final Set<UUID> godModePlayers = new HashSet<UUID>();
    private final Set<UUID> socialSpyPlayers = new HashSet<UUID>();
    private final Map<UUID, UUID> lastReplyPartner = new HashMap<UUID, UUID>();

    public boolean toggleGodMode(Player p) {
        if (godModePlayers.contains(p.getUniqueId())) {
            godModePlayers.remove(p.getUniqueId());
            return false;
        }
        godModePlayers.add(p.getUniqueId());
        return true;
    }

    public boolean isGodMode(Player p) {
        return p != null && godModePlayers.contains(p.getUniqueId());
    }

    public void disableGodMode(Player p) {
        if (p != null) godModePlayers.remove(p.getUniqueId());
    }

    public boolean toggleSocialSpy(Player p) {
        if (socialSpyPlayers.contains(p.getUniqueId())) {
            socialSpyPlayers.remove(p.getUniqueId());
            return false;
        }
        socialSpyPlayers.add(p.getUniqueId());
        return true;
    }

    public boolean isSocialSpy(Player p) {
        return p != null && socialSpyPlayers.contains(p.getUniqueId());
    }

    public void setLastReplyPartner(UUID a, UUID b) {
        if (a != null && b != null) {
            lastReplyPartner.put(a, b);
            lastReplyPartner.put(b, a);
        }
    }

    public UUID getLastReplyPartner(UUID a) {
        return a == null ? null : lastReplyPartner.get(a);
    }

    public boolean isTpDisabled(String name) {
        return yml.getBoolean("players." + key(name) + ".tpDisabled", false);
    }

    public boolean toggleTpDisabled(String name) {
        boolean next = !isTpDisabled(name);
        yml.set("players." + key(name) + ".tpDisabled", Boolean.valueOf(next));
        save();
        return next;
    }

    public boolean isMsgDisabled(String name) {
        return yml.getBoolean("players." + key(name) + ".msgDisabled", false);
    }

    public boolean toggleMsgDisabled(String name) {
        boolean next = !isMsgDisabled(name);
        yml.set("players." + key(name) + ".msgDisabled", Boolean.valueOf(next));
        save();
        return next;
    }

    public boolean isIgnoring(String viewerName, String senderName) {
        List<String> list = yml.getStringList("players." + key(viewerName) + ".ignoreList");
        return list != null && list.contains(key(senderName));
    }

    public boolean toggleIgnore(String viewerName, String targetName) {
        String p = "players." + key(viewerName) + ".ignoreList";
        List<String> list = new ArrayList<String>(yml.getStringList(p));
        String tk = key(targetName);
        boolean nowIgnoring;
        if (list.contains(tk)) {
            list.remove(tk);
            nowIgnoring = false;
        } else {
            list.add(tk);
            nowIgnoring = true;
        }
        yml.set(p, list);
        save();
        return nowIgnoring;
    }

    public void setCustomSpawnLoc(Location loc) {
        yml.set("customSpawnArrival", loc == null ? null : writeLoc(loc));
        save();
    }

    public Location getCustomSpawnLoc() {
        String raw = yml.getString("customSpawnArrival", null);
        return raw == null ? null : readLoc(raw);
    }

    // ================= CUSTOM SERVER & PLAYER WARPS (/warp set, /setwarp, /delwarp) =================
    public void setCustomWarp(String warpName, String creator, Location loc) {
        String k = warpName.toLowerCase(Locale.ROOT);
        String base = "customWarps." + k;
        yml.set(base + ".creator", creator);
        yml.set(base + ".loc", writeLoc(loc));
        yml.set(base + ".createdAt", Long.valueOf(System.currentTimeMillis()));
        save();
    }

    public boolean delCustomWarp(String warpName) {
        String base = "customWarps." + warpName.toLowerCase(Locale.ROOT);
        if (!yml.contains(base)) return false;
        yml.set(base, null);
        save();
        return true;
    }

    public CustomWarp getCustomWarp(String warpName) {
        if (warpName == null) return null;
        String k = warpName.toLowerCase(Locale.ROOT);
        String base = "customWarps." + k;
        if (!yml.contains(base + ".loc")) return null;
        Location loc = readLoc(yml.getString(base + ".loc"));
        if (loc == null) return null;
        String creator = yml.getString(base + ".creator", "Сервер");
        long ts = yml.getLong(base + ".createdAt", 0L);
        return new CustomWarp(k, creator, loc, ts);
    }

    public Location getCustomWarpLocation(String warpName) {
        CustomWarp cw = getCustomWarp(warpName);
        return cw == null ? null : cw.loc;
    }

    public Map<String, CustomWarp> getAllCustomWarps() {
        Map<String, CustomWarp> out = new LinkedHashMap<String, CustomWarp>();
        ConfigurationSection sec = yml.getConfigurationSection("customWarps");
        if (sec != null) {
            for (String k : sec.getKeys(false)) {
                CustomWarp cw = getCustomWarp(k);
                if (cw != null) out.put(k, cw);
            }
        }
        return out;
    }

    public int countCustomWarpsOf(String creatorName) {
        int n = 0;
        for (CustomWarp cw : getAllCustomWarps().values()) {
            if (cw.creator != null && cw.creator.equalsIgnoreCase(creatorName)) n++;
        }
        return n;
    }

    public UUID denyTpaSender(Player target) {
        UUID s = tpaFrom.remove(target.getUniqueId());
        tpaHere.remove(target.getUniqueId());
        tpaTime.remove(target.getUniqueId());
        return s;
    }

    public UUID popTpaSender(Player target, boolean[] isHereOut) {
        Long exp = tpaTime.get(target.getUniqueId());
        if (exp == null || System.currentTimeMillis() > exp.longValue()) return null;
        UUID s = tpaFrom.remove(target.getUniqueId());
        Boolean h = tpaHere.remove(target.getUniqueId());
        tpaTime.remove(target.getUniqueId());
        if (isHereOut != null && isHereOut.length > 0) isHereOut[0] = h != null && h.booleanValue();
        return s;
    }

    // ================= ORE PROTECTION CLAIMS =================
    public Claim getClaimAt(Location loc) {
        for (Claim c : claims) {
            if (c.contains(loc)) return c;
        }
        return null;
    }

    public List<Claim> getClaimsOf(String owner) {
        List<Claim> out = new ArrayList<Claim>();
        for (Claim c : claims) {
            if (c.owner.equalsIgnoreCase(owner)) out.add(c);
        }
        return out;
    }

    public boolean overlapsAnyClaim(String world, int cx, int cz, int radius) {
        for (Claim c : claims) {
            if (!c.world.equalsIgnoreCase(world)) continue;
            if (Math.abs(c.cx - cx) <= (c.radius + radius) && Math.abs(c.cz - cz) <= (c.radius + radius)) {
                return true;
            }
        }
        return false;
    }

    public Claim createClaim(String owner, Location loc, int radius) {
        Claim cl = new Claim();
        cl.id = "rg_" + System.currentTimeMillis() + "_" + (claims.size() + 1);
        cl.owner = owner;
        cl.world = loc.getWorld().getName();
        cl.cx = loc.getBlockX();
        cl.cy = loc.getBlockY();
        cl.cz = loc.getBlockZ();
        cl.radius = radius;
        claims.add(cl);
        save();
        return cl;
    }

    public void removeClaim(Claim cl) {
        claims.remove(cl);
        save();
    }

    // ================= AUCTION HOUSE =================
    public synchronized List<AuctionLot> getAuctionLots() {
        return new ArrayList<AuctionLot>(auction);
    }

    public synchronized AuctionLot addAuctionLot(String seller, ItemStack item, double price) {
        AuctionLot lot = new AuctionLot();
        lot.id = nextLotId++;
        lot.seller = seller;
        lot.price = price;
        lot.item = item.clone();
        lot.createdAt = System.currentTimeMillis();
        auction.add(0, lot);
        save();
        return lot;
    }

    public synchronized AuctionLot removeAuctionLot(int id) {
        for (int i = 0; i < auction.size(); i++) {
            if (auction.get(i).id == id) {
                AuctionLot r = auction.remove(i);
                save();
                return r;
            }
        }
        return null;
    }

    // ================= MODERATION & STAFFWORK (/sw) FLAGS =================
    public void mute(String name, long durationMs, String reason) {
        mute(name, durationMs, reason, "Консоль");
    }
    public void mute(String name, long durationMs, String reason, String staff) {
        long until = durationMs <= 0 ? Long.MAX_VALUE : System.currentTimeMillis() + durationMs;
        yml.set("mutes." + key(name) + ".until", until);
        yml.set("mutes." + key(name) + ".reason", reason);
        yml.set("mutes." + key(name) + ".staff", staff);
        save();
    }
    public void unmute(String name) {
        yml.set("mutes." + key(name), null);
        save();
    }
    public long getMuteRemainingSec(String name) {
        long u = yml.getLong("mutes." + key(name) + ".until", 0L);
        if (u == Long.MAX_VALUE) return Long.MAX_VALUE / 2000L;
        return u > System.currentTimeMillis() ? (u - System.currentTimeMillis()) / 1000L : 0L;
    }
    public String getMuteReason(String name) {
        return yml.getString("mutes." + key(name) + ".reason", "Нарушение правил чата");
    }
    public String getMuteStaff(String name) {
        return yml.getString("mutes." + key(name) + ".staff", "Модерация");
    }

    public void ban(String name, long durationMs, String reason) {
        ban(name, durationMs, reason, "Консоль");
    }
    public void ban(String name, long durationMs, String reason, String staff) {
        yml.set("bans." + key(name) + ".until", durationMs <= 0 ? Long.MAX_VALUE : System.currentTimeMillis() + durationMs);
        yml.set("bans." + key(name) + ".reason", reason);
        yml.set("bans." + key(name) + ".staff", staff);
        save();
    }
    public void unban(String name) {
        yml.set("bans." + key(name), null);
        save();
    }
    public String getActiveBanReason(String name) {
        long u = yml.getLong("bans." + key(name) + ".until", 0L);
        if (u > System.currentTimeMillis()) {
            return yml.getString("bans." + key(name) + ".reason", "Нарушение правил сервера");
        }
        return null;
    }
    public long getBanRemainingSec(String name) {
        long u = yml.getLong("bans." + key(name) + ".until", 0L);
        if (u == Long.MAX_VALUE) return -1L;
        return u > System.currentTimeMillis() ? (u - System.currentTimeMillis()) / 1000L : 0L;
    }
    public String getBanStaff(String name) {
        return yml.getString("bans." + key(name) + ".staff", "Модерация");
    }

    // ================= v5.9: IP-MUTE (/muteip), IP-BAN (/banip), WARNS (/warn) & PUNISHMENT HISTORY (/history) =================
    public void muteIp(String ip, String targetLabel, long durationMs, String reason, String staff) {
        if (ip == null || ip.isEmpty()) return;
        String ik = "ipmutes." + ipKey(ip);
        long until = durationMs <= 0 ? Long.MAX_VALUE : System.currentTimeMillis() + durationMs;
        yml.set(ik + ".ip", ip);
        yml.set(ik + ".target", targetLabel == null ? ip : targetLabel);
        yml.set(ik + ".until", Long.valueOf(until));
        yml.set(ik + ".reason", reason);
        yml.set(ik + ".staff", staff);
        save();
    }

    public boolean unmuteIp(String ipOrNick) {
        String ip = resolveIpForTarget(ipOrNick);
        if (ip.isEmpty()) ip = findPunishedIp("ipmutes", ipOrNick);
        if (ip.isEmpty()) return false;
        String ik = "ipmutes." + ipKey(ip);
        boolean existed = yml.contains(ik);
        yml.set(ik, null);
        save();
        return existed;
    }

    public long getIpMuteRemainingSec(String ip) {
        if (ip == null || ip.isEmpty()) return 0L;
        long u = yml.getLong("ipmutes." + ipKey(ip) + ".until", 0L);
        if (u == Long.MAX_VALUE) return Long.MAX_VALUE / 2000L;
        return u > System.currentTimeMillis() ? (u - System.currentTimeMillis()) / 1000L : 0L;
    }

    public String getIpMuteReason(String ip) {
        return yml.getString("ipmutes." + ipKey(ip) + ".reason", "Нарушение правил чата (IP)");
    }

    public String getIpMuteStaff(String ip) {
        return yml.getString("ipmutes." + ipKey(ip) + ".staff", "Модерация");
    }

    public String getIpMuteTarget(String ip) {
        return yml.getString("ipmutes." + ipKey(ip) + ".target", ip);
    }

    public void banIp(String ip, String targetLabel, long durationMs, String reason, String staff) {
        if (ip == null || ip.isEmpty()) return;
        String ik = "ipbans." + ipKey(ip);
        long until = durationMs <= 0 ? Long.MAX_VALUE : System.currentTimeMillis() + durationMs;
        yml.set(ik + ".ip", ip);
        yml.set(ik + ".target", targetLabel == null ? ip : targetLabel);
        yml.set(ik + ".until", Long.valueOf(until));
        yml.set(ik + ".reason", reason);
        yml.set(ik + ".staff", staff);
        save();
    }

    public boolean unbanIp(String ipOrNick) {
        String ip = resolveIpForTarget(ipOrNick);
        if (ip.isEmpty()) ip = findPunishedIp("ipbans", ipOrNick);
        if (ip.isEmpty()) return false;
        String ik = "ipbans." + ipKey(ip);
        boolean existed = yml.contains(ik);
        yml.set(ik, null);
        save();
        return existed;
    }

    public String getActiveIpBanReason(String ip) {
        if (ip == null || ip.isEmpty()) return null;
        long u = yml.getLong("ipbans." + ipKey(ip) + ".until", 0L);
        if (u > System.currentTimeMillis()) {
            return yml.getString("ipbans." + ipKey(ip) + ".reason", "Обход блокировки / ЧСП по IP");
        }
        return null;
    }

    public long getIpBanRemainingSec(String ip) {
        if (ip == null || ip.isEmpty()) return 0L;
        long u = yml.getLong("ipbans." + ipKey(ip) + ".until", 0L);
        if (u == Long.MAX_VALUE) return -1L;
        return u > System.currentTimeMillis() ? (u - System.currentTimeMillis()) / 1000L : 0L;
    }

    public String getIpBanStaff(String ip) {
        return yml.getString("ipbans." + ipKey(ip) + ".staff", "Модерация");
    }

    public String getIpBanTarget(String ip) {
        return yml.getString("ipbans." + ipKey(ip) + ".target", ip);
    }

    private String findPunishedIp(String rootNode, String nick) {
        if (nick == null || nick.isEmpty()) return "";
        ConfigurationSection sec = yml.getConfigurationSection(rootNode);
        if (sec == null) return "";
        for (String k : sec.getKeys(false)) {
            String t = sec.getString(k + ".target", "");
            if (nick.equalsIgnoreCase(t)) {
                return sec.getString(k + ".ip", "");
            }
        }
        return "";
    }

    public List<String> getWarns(String name) {
        return new ArrayList<String>(yml.getStringList("warns." + key(name)));
    }

    public int addWarn(String name, String staff, String reason) {
        List<String> list = getWarns(name);
        String stamp = new java.text.SimpleDateFormat("dd.MM HH:mm").format(new java.util.Date());
        list.add("&8[" + stamp + "] &e" + staff + "&7: &f" + reason);
        yml.set("warns." + key(name), list);
        save();
        return list.size();
    }

    public int removeLastWarn(String name) {
        List<String> list = getWarns(name);
        if (!list.isEmpty()) list.remove(list.size() - 1);
        yml.set("warns." + key(name), list.isEmpty() ? null : list);
        save();
        return list.size();
    }

    public void clearWarns(String name) {
        yml.set("warns." + key(name), null);
        save();
    }

    public void appendPunishHistory(String targetName, String typeBadge, String staff, String durationLabel, String reason) {
        if (targetName == null || targetName.isEmpty()) return;
        String path = "punishHistory." + key(targetName);
        List<String> list = new ArrayList<String>(yml.getStringList(path));
        String stamp = new java.text.SimpleDateFormat("dd.MM HH:mm").format(new java.util.Date());
        list.add(0, "&8[" + stamp + "] " + typeBadge + " &7от &e" + staff + " &8(" + durationLabel + "&8): &f" + reason);
        while (list.size() > 15) list.remove(list.size() - 1);
        yml.set(path, list);
        save();
    }

    public List<String> getPunishHistory(String targetName) {
        return new ArrayList<String>(yml.getStringList("punishHistory." + key(targetName)));
    }

    // StaffWork (/sw) persistent counters
    public int getStaffBans(String staff) { return yml.getInt("staff." + key(staff) + ".bans", 0); }
    public void addStaffBan(String staff) {
        yml.set("staff." + key(staff) + ".bans", getStaffBans(staff) + 1);
        save();
    }
    public int getStaffMutes(String staff) { return yml.getInt("staff." + key(staff) + ".mutes", 0); }
    public void addStaffMute(String staff) {
        yml.set("staff." + key(staff) + ".mutes", getStaffMutes(staff) + 1);
        save();
    }
    public int getStaffChecks(String staff) { return yml.getInt("staff." + key(staff) + ".checks", 0); }
    public void addStaffCheck(String staff) {
        yml.set("staff." + key(staff) + ".checks", getStaffChecks(staff) + 1);
        save();
    }
    public int getStaffSpeks(String staff) { return yml.getInt("staff." + key(staff) + ".speks", 0); }
    public void addStaffSpek(String staff) {
        yml.set("staff." + key(staff) + ".speks", getStaffSpeks(staff) + 1);
        save();
    }
    public long getStaffWorkSeconds(String staff) { return yml.getLong("staff." + key(staff) + ".workSec", 0L); }
    public void addStaffWorkSeconds(String staff, long sec) {
        if (sec <= 0) return;
        yml.set("staff." + key(staff) + ".workSec", getStaffWorkSeconds(staff) + sec);
        save();
    }

    public boolean isSbEnabled(Player p) { return !sbDisabled.contains(p.getUniqueId()); }
    public boolean toggleSb(Player p) {
        if (sbDisabled.contains(p.getUniqueId())) { sbDisabled.remove(p.getUniqueId()); return true; }
        sbDisabled.add(p.getUniqueId()); return false;
    }

    public boolean isVanished(Player p) { return vanished.contains(p.getUniqueId()); }
    public boolean toggleVanish(Player p) {
        if (vanished.contains(p.getUniqueId())) { vanished.remove(p.getUniqueId()); return false; }
        vanished.add(p.getUniqueId()); return true;
    }

    public static String writeLoc(Location l) {
        if (l == null || l.getWorld() == null) return null;
        return l.getWorld().getName() + ";" + l.getX() + ";" + l.getY() + ";" + l.getZ() + ";" + l.getYaw() + ";" + l.getPitch();
    }

    public static Location readLoc(String s) {
        if (s == null || s.isEmpty()) return null;
        try {
            String[] p = s.split(";");
            World w = Bukkit.getWorld(p[0]);
            if (w == null) return null;
            return new Location(w, Double.parseDouble(p[1]), Double.parseDouble(p[2]), Double.parseDouble(p[3]),
                    Float.parseFloat(p[4]), Float.parseFloat(p[5]));
        } catch (Exception e) {
            return null;
        }
    }

    // ================= v5.0: CASE SET, TRAP SKINS, BUYER LEVEL, BOUNTIES & CLOUD PROMOS =================
    public void setCaseKeys(String name, String caseId, int amount) {
        yml.set("players." + key(name) + ".cases." + caseId.toLowerCase(Locale.ROOT), Math.max(0, amount));
        save();
    }

    public String getSelectedTrapSkin(String name) {
        return yml.getString("players." + key(name) + ".trapSkin", "classic");
    }

    public void setSelectedTrapSkin(String name, String skinId) {
        yml.set("players." + key(name) + ".trapSkin", skinId.toLowerCase(Locale.ROOT));
        save();
    }

    public boolean hasTrapSkin(String name, String skinId) {
        if ("classic".equalsIgnoreCase(skinId)) return true;
        return yml.getBoolean("players." + key(name) + ".trapSkins." + skinId.toLowerCase(Locale.ROOT), false);
    }

    public void unlockTrapSkin(String name, String skinId) {
        yml.set("players." + key(name) + ".trapSkins." + skinId.toLowerCase(Locale.ROOT), Boolean.TRUE);
        save();
    }

    public String getSelectedPlastSkin(String name) {
        return yml.getString("players." + key(name) + ".plastSkin", "classic");
    }

    public void setSelectedPlastSkin(String name, String skinId) {
        yml.set("players." + key(name) + ".plastSkin", skinId.toLowerCase(Locale.ROOT));
        save();
    }

    public boolean hasPlastSkin(String name, String skinId) {
        if ("classic".equalsIgnoreCase(skinId)) return true;
        return yml.getBoolean("players." + key(name) + ".plastSkins." + skinId.toLowerCase(Locale.ROOT), false);
    }

    public void unlockPlastSkin(String name, String skinId) {
        yml.set("players." + key(name) + ".plastSkins." + skinId.toLowerCase(Locale.ROOT), Boolean.TRUE);
        save();
    }

    public double getBuyerSoldTotal(String name) {
        return yml.getDouble("players." + key(name) + ".buyerSold", 0.0);
    }

    public void addBuyerSold(String name, double delta) {
        yml.set("players." + key(name) + ".buyerSold", getBuyerSoldTotal(name) + Math.max(0.0, delta));
    }

    public double getBuyerMultiplier(String name) {
        double sold = getBuyerSoldTotal(name);
        // Up to x2.00 multiplier (+0.10x per 25,000$ sold!)
        double bonus = Math.min(1.0, Math.floor(sold / 25000.0) * 0.10);
        return Math.round((1.0 + bonus) * 100.0) / 100.0;
    }

    public double getBounty(String target) {
        return yml.getDouble("bounties." + key(target), 0.0);
    }

    public void addBounty(String target, double amt) {
        yml.set("bounties." + key(target), getBounty(target) + Math.max(0.0, amt));
        save();
    }

    public double claimBounty(String target) {
        double b = getBounty(target);
        if (b > 0) {
            yml.set("bounties." + key(target), null);
            save();
        }
        return b;
    }

    public Map<String, Double> getActiveBounties() {
        Map<String, Double> out = new LinkedHashMap<String, Double>();
        ConfigurationSection sec = yml.getConfigurationSection("bounties");
        if (sec != null) {
            for (String k : sec.getKeys(false)) {
                double v = sec.getDouble(k, 0.0);
                if (v > 0) out.put(k, Double.valueOf(v));
            }
        }
        return out;
    }

    public static final class ServerPromo {
        public String code;
        public int maxUses;
        public int usedCount;
        public double coins;
        public int relics;
        public String caseId;
        public int caseQty;
        public String rankId;
        public boolean active = true;
    }

    public synchronized void syncServerPromo(String code, int maxUses, double coins, int relics,
                                             String caseId, int caseQty, String rankId) {
        if (code == null || code.trim().isEmpty()) return;
        String c = code.trim().toUpperCase(Locale.ROOT);
        String p = "serverPromos." + c;
        yml.set(p + ".maxUses", maxUses);
        yml.set(p + ".coins", coins);
        yml.set(p + ".relics", relics);
        yml.set(p + ".caseId", (caseId == null || "none".equalsIgnoreCase(caseId)) ? "" : caseId.toLowerCase(Locale.ROOT));
        yml.set(p + ".caseQty", Math.max(0, caseQty));
        yml.set(p + ".rankId", (rankId == null || "none".equalsIgnoreCase(rankId)) ? "" : rankId.toLowerCase(Locale.ROOT));
        yml.set(p + ".active", Boolean.TRUE);
        save();
    }

    public synchronized void deleteServerPromo(String code) {
        if (code == null) return;
        yml.set("serverPromos." + code.trim().toUpperCase(Locale.ROOT), null);
        save();
    }

    public synchronized ServerPromo getServerPromo(String code) {
        if (code == null) return null;
        String c = code.trim().toUpperCase(Locale.ROOT);
        ConfigurationSection sec = yml.getConfigurationSection("serverPromos." + c);
        if (sec == null || !sec.getBoolean("active", true)) return null;
        ServerPromo sp = new ServerPromo();
        sp.code = c;
        sp.maxUses = sec.getInt("maxUses", 0);
        sp.usedCount = sec.getInt("usedCount", 0);
        sp.coins = sec.getDouble("coins", 0.0);
        sp.relics = sec.getInt("relics", 0);
        sp.caseId = sec.getString("caseId", "");
        sp.caseQty = sec.getInt("caseQty", 0);
        sp.rankId = sec.getString("rankId", "");
        sp.active = true;
        return sp;
    }

    public synchronized List<ServerPromo> getAllServerPromos() {
        List<ServerPromo> list = new ArrayList<ServerPromo>();
        ConfigurationSection root = yml.getConfigurationSection("serverPromos");
        if (root != null) {
            for (String k : root.getKeys(false)) {
                ServerPromo sp = getServerPromo(k);
                if (sp != null) list.add(sp);
            }
        }
        return list;
    }

    public synchronized void incrementServerPromoUsed(String code) {
        String c = code.trim().toUpperCase(Locale.ROOT);
        int cur = yml.getInt("serverPromos." + c + ".usedCount", 0);
        yml.set("serverPromos." + c + ".usedCount", cur + 1);
        save();
    }

    // ================= v5.7 FPS BOOSTER, QUICK FREEZE, SANTA GIFT HUNT & TOP LEADERBOARDS =================
    private final Set<UUID> quickFrozenPlayers = new HashSet<UUID>();

    public boolean isFpsBoost(Player p) {
        if (p == null) return false;
        return yml.getBoolean("players." + key(p.getName()) + ".fpsBoost", false);
    }

    public boolean toggleFpsBoost(Player p) {
        boolean next = !isFpsBoost(p);
        yml.set("players." + key(p.getName()) + ".fpsBoost", Boolean.valueOf(next));
        save();
        return next;
    }

    public boolean isQuickFrozen(Player p) {
        return p != null && quickFrozenPlayers.contains(p.getUniqueId());
    }

    public boolean toggleQuickFreeze(UUID id) {
        if (quickFrozenPlayers.contains(id)) {
            quickFrozenPlayers.remove(id);
            return false;
        }
        quickFrozenPlayers.add(id);
        return true;
    }

    public void clearQuickFreeze(UUID id) {
        quickFrozenPlayers.remove(id);
    }

    public boolean hasFoundSpawnGift(String name, int giftIndex) {
        return yml.getBoolean("players." + key(name) + ".spawnGifts.g" + giftIndex, false);
    }

    public void markFoundSpawnGift(String name, int giftIndex) {
        yml.set("players." + key(name) + ".spawnGifts.g" + giftIndex, Boolean.TRUE);
        save();
    }

    public int getFoundSpawnGiftCount(String name) {
        int c = 0;
        for (int i = 0; i < 10; i++) {
            if (hasFoundSpawnGift(name, i)) c++;
        }
        return c;
    }

    public List<Map.Entry<String, Double>> getTopCoins(int limit) {
        List<Map.Entry<String, Double>> list = new ArrayList<Map.Entry<String, Double>>();
        ConfigurationSection sec = yml.getConfigurationSection("players");
        if (sec != null) {
            for (String k : sec.getKeys(false)) {
                double val = sec.getDouble(k + ".coins", 0.0);
                String disp = sec.getString(k + ".name", k);
                list.add(new java.util.AbstractMap.SimpleEntry<String, Double>(disp, Double.valueOf(val)));
            }
        }
        Collections.sort(list, new Comparator<Map.Entry<String, Double>>() {
            @Override
            public int compare(Map.Entry<String, Double> a, Map.Entry<String, Double> b) {
                return Double.compare(b.getValue().doubleValue(), a.getValue().doubleValue());
            }
        });
        return list.size() > limit ? list.subList(0, limit) : list;
    }

    public List<Map.Entry<String, Integer>> getTopKills(int limit) {
        List<Map.Entry<String, Integer>> list = new ArrayList<Map.Entry<String, Integer>>();
        ConfigurationSection sec = yml.getConfigurationSection("players");
        if (sec != null) {
            for (String k : sec.getKeys(false)) {
                int val = sec.getInt(k + ".kills", 0);
                String disp = sec.getString(k + ".name", k);
                list.add(new java.util.AbstractMap.SimpleEntry<String, Integer>(disp, Integer.valueOf(val)));
            }
        }
        Collections.sort(list, new Comparator<Map.Entry<String, Integer>>() {
            @Override
            public int compare(Map.Entry<String, Integer> a, Map.Entry<String, Integer> b) {
                return Integer.compare(b.getValue().intValue(), a.getValue().intValue());
            }
        });
        return list.size() > limit ? list.subList(0, limit) : list;
    }

    // ================= СИСТЕМА ТИММЕЙТОВ / ДРУЗЕЙ (/tm, /friend, /friends, /tc) + KILLSTREAKS & DUELS v6.0 =================
    private final Map<String, Set<String>> friendInvites = new HashMap<String, Set<String>>();
    private final Map<String, Integer> activeKillstreaks = new HashMap<String, Integer>();

    public static final class ActiveWagerDuel {
        public final String playerA;
        public final String playerB;
        public final double betEach;
        public final long startedAtMs;

        public ActiveWagerDuel(String playerA, String playerB, double betEach, long startedAtMs) {
            this.playerA = playerA;
            this.playerB = playerB;
            this.betEach = betEach;
            this.startedAtMs = startedAtMs;
        }
    }

    private final Map<String, ActiveWagerDuel> activeWagerDuels = new HashMap<String, ActiveWagerDuel>();

    public List<String> getTeammateKeys(String name) {
        List<String> raw = yml.getStringList("players." + key(name) + ".teammates");
        return raw != null ? new ArrayList<String>(raw) : new ArrayList<String>();
    }

    public String getStoredDisplayName(String k) {
        return yml.getString("players." + key(k) + ".name", k);
    }

    public boolean isDirectTeammate(String nameA, String nameB) {
        if (nameA == null || nameB == null) return false;
        return getTeammateKeys(nameA).contains(key(nameB));
    }

    /**
     * Проверяет, являются ли два игрока ТИММЕЙТАМИ:
     * 1) Добавили друг друга через /tm (/friend);
     * 2) Состоят в одном Клане (/clan) или в союзных кланах (/clan ally)!
     */
    public boolean areTeammates(String nameA, String nameB) {
        if (nameA == null || nameB == null || nameA.equalsIgnoreCase(nameB)) return false;
        if (isDirectTeammate(nameA, nameB)) return true;
        Clan c1 = getClanOf(nameA);
        Clan c2 = getClanOf(nameB);
        if (c1 != null && c2 != null) {
            if (c1.equals(c2)) return true;
            if (c1.allies.contains(c2.id) || c2.allies.contains(c1.id)) return true;
        }
        return false;
    }

    public boolean isTeammatePvpAllowed(String nameA, String nameB) {
        if (!areTeammates(nameA, nameB)) return true;
        if (isDirectTeammate(nameA, nameB)) {
            return isTeammatePvpEnabled(nameA) && isTeammatePvpEnabled(nameB);
        }
        Clan c1 = getClanOf(nameA);
        Clan c2 = getClanOf(nameB);
        if (c1 != null && c2 != null && c1.equals(c2)) {
            return c1.friendlyFire;
        }
        return false;
    }

    public boolean isTeammatePvpEnabled(String name) {
        return yml.getBoolean("players." + key(name) + ".teammatePvp", false);
    }

    public boolean toggleTeammatePvp(String name) {
        boolean next = !isTeammatePvpEnabled(name);
        yml.set("players." + key(name) + ".teammatePvp", Boolean.valueOf(next));
        save();
        return next;
    }

    public void sendTeammateInvite(String senderName, String targetName) {
        Set<String> set = friendInvites.get(key(targetName));
        if (set == null) {
            set = new LinkedHashSetCompat();
            friendInvites.put(key(targetName), set);
        }
        String sk = key(senderName);
        set.remove(sk);
        set.add(sk);
    }

    public String popTeammateInvite(String targetName, String optionalSender) {
        Set<String> set = friendInvites.get(key(targetName));
        if (set == null || set.isEmpty()) return null;
        if (optionalSender != null && !optionalSender.trim().isEmpty()) {
            String sk = key(optionalSender.trim());
            if (set.remove(sk)) return sk;
            return null;
        }
        String last = null;
        for (String s : set) last = s;
        if (last != null) set.remove(last);
        return last;
    }

    public void addMutualTeammates(String nameA, String nameB) {
        String ka = key(nameA);
        String kb = key(nameB);
        if (ka.equals(kb)) return;
        List<String> la = getTeammateKeys(ka);
        if (!la.contains(kb)) la.add(kb);
        List<String> lb = getTeammateKeys(kb);
        if (!lb.contains(ka)) lb.add(ka);
        yml.set("players." + ka + ".teammates", la);
        yml.set("players." + kb + ".teammates", lb);
        if (yml.getString("players." + ka + ".name") == null) yml.set("players." + ka + ".name", nameA);
        if (yml.getString("players." + kb + ".name") == null) yml.set("players." + kb + ".name", nameB);
        Set<String> invA = friendInvites.get(ka);
        if (invA != null) invA.remove(kb);
        Set<String> invB = friendInvites.get(kb);
        if (invB != null) invB.remove(ka);
        save();
    }

    public boolean removeMutualTeammates(String nameA, String nameB) {
        String ka = key(nameA);
        String kb = key(nameB);
        List<String> la = getTeammateKeys(ka);
        List<String> lb = getTeammateKeys(kb);
        boolean ch = la.remove(kb) | lb.remove(ka);
        if (ch) {
            yml.set("players." + ka + ".teammates", la);
            yml.set("players." + kb + ".teammates", lb);
            save();
        }
        return ch;
    }

    public int incrementKillstreak(String name) {
        String k = key(name);
        int next = (activeKillstreaks.containsKey(k) ? activeKillstreaks.get(k).intValue() : 0) + 1;
        activeKillstreaks.put(k, Integer.valueOf(next));
        int best = yml.getInt("players." + k + ".bestStreak", 0);
        if (next > best) {
            yml.set("players." + k + ".bestStreak", Integer.valueOf(next));
        }
        return next;
    }

    public int getKillstreak(String name) {
        Integer v = activeKillstreaks.get(key(name));
        return v != null ? v.intValue() : 0;
    }

    public int resetKillstreak(String name) {
        Integer prev = activeKillstreaks.remove(key(name));
        return prev != null ? prev.intValue() : 0;
    }

    public void startWagerDuel(String a, String b, double betEach) {
        ActiveWagerDuel d = new ActiveWagerDuel(a, b, betEach, System.currentTimeMillis());
        activeWagerDuels.put(key(a), d);
        activeWagerDuels.put(key(b), d);
    }

    public ActiveWagerDuel getActiveWagerDuel(String name) {
        return activeWagerDuels.get(key(name));
    }

    public ActiveWagerDuel finishWagerDuel(String name) {
        ActiveWagerDuel d = activeWagerDuels.remove(key(name));
        if (d != null) {
            activeWagerDuels.remove(key(d.playerA));
            activeWagerDuels.remove(key(d.playerB));
        }
        return d;
    }
}
