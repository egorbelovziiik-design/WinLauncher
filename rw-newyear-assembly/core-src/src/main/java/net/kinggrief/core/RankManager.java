package net.kinggrief.core;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public final class RankManager {

    public static final class RankDef {
        public final String id;
        public int weight;
        public final int sortOrder;
        public final int glyphIndex; // 0..39 -> \uEA00 + glyphIndex*8 + frame
        public String chatPrefix;
        public String shortLabel;
        public final int maxHomes;
        public final int maxClaims;
        public final String kitName;
        public final int tier; // 0=player, 1=vip, 2=god/king, 3=media, 4=dkp, 5=opka/downer, 6=kp, 7=owner
        public final int priceRub;
        public boolean hasCustomTextOverride = false;

        public RankDef(String id, int weight, int sortOrder, int glyphIndex, String chatPrefix, String shortLabel,
                       int maxHomes, int maxClaims, String kitName, int tier, int priceRub) {
            this.id = id;
            this.weight = weight;
            this.sortOrder = sortOrder;
            this.glyphIndex = glyphIndex;
            this.chatPrefix = chatPrefix;
            this.shortLabel = shortLabel;
            this.maxHomes = maxHomes;
            this.maxClaims = maxClaims;
            this.kitName = kitName;
            this.tier = tier;
            this.priceRub = priceRub;
        }
    }

    private final Map<String, RankDef> ranks = new LinkedHashMap<String, RankDef>();
    private final DataStore store;

    public RankManager(DataStore store) {
        this.store = store;
        registerAll();
        loadCustomRankOverrides();
    }

    private void add(int glyphIdx, String id, int weight, int homes, int claims, String kit, int tier, int priceRub) {
        String low = id.toLowerCase(Locale.ROOT);
        char frame0 = (char) (0xEA00 + glyphIdx * 8);
        // ONLY the graphical badge glyph — NO duplicate rank word on the right!
        String badgeOnly = "&f" + frame0;
        String chatPref = badgeOnly + " &f";
        ranks.put(low, new RankDef(low, weight, glyphIdx + 1, glyphIdx, chatPref, badgeOnly, homes, claims, kit, tier, priceRub));
    }

    private void registerAll() {
        add(0,  "owner",     300, 500, 99, "owner",     7, 0);
        add(1,  "techadmin", 290, 500, 99, "owner",     7, 0);
        add(2,  "curator",   275, 500, 50, "curator",   6, 0);
        add(3,  "kp",        255,  30, 30, "kp",        6, 0);
        add(4,  "glmoder",   245,  25, 25, "kp",        6, 0);
        add(5,  "stmoder",   235,  20, 20, "kp",        6, 0);
        add(6,  "moder",     225,  15, 15, "kp",        6, 0);
        add(7,  "sthelper",  215,  12, 12, "kp",        6, 0);
        add(8,  "helper",    205,  10, 10, "kp",        6, 0);

        add(9,  "downer",    190, 500, 99, "downer",    5, 1499);
        add(10, "opka",      178,  60, 50, "opka",      5, 999);
        add(11, "dkp",       168,  45, 35, "dkp",       4, 899);
        add(12, "dadmin",    160,  40, 30, "dadmin",    4, 799);
        add(13, "dmladmin",  150,  36, 25, "dmladmin",  4, 599);
        add(14, "dglmoder",  140,  32, 22, "dglmoder",  4, 449);
        add(15, "dstmoder",  130,  28, 20, "dstmoder",  4, 349);
        add(16, "dmoder",    120,  24, 18, "dmoder",    4, 249);
        add(17, "dhelper",   115,  20, 16, "dhelper",   4, 219);

        add(18, "mediaplus", 110,  20, 20, "media",     3, 0);
        add(19, "media",     108,  15, 15, "media",     3, 0);
        add(20, "yt",        106,  15, 12, "media",     3, 0);
        add(21, "twitch",    104,  15, 12, "media",     3, 0);
        add(22, "tiktok",    102,  15, 12, "media",     3, 0);

        add(23, "sponsor",    98,  22, 15, "sponsor",   2, 229);
        add(24, "bull",       94,  20, 14, "bull",      2, 209);
        add(25, "king",       90,  18, 12, "king",      2, 199);
        add(26, "dragon",     80,  16, 11, "dragon",    2, 179);
        add(27, "imperator",  75,  15, 10, "imperator", 2, 169);
        add(28, "overlord",   70,  14,  9, "overlord",  2, 159);
        add(29, "god",        65,  13,  8, "god",       2, 149);
        add(30, "dracula",    60,  12,  7, "dracula",   1, 129);
        add(31, "tiger",      55,  11,  7, "tiger",     1, 115);
        add(32, "vampire",    50,  10,  6, "vampire",   1, 99);
        add(33, "rabbit",     45,   8,  6, "rabbit",    1, 89);
        add(34, "titan",      40,   7,  5, "titan",     1, 69);
        add(35, "bunny",      35,   6,  5, "bunny",     1, 59);
        add(36, "hero",       30,   5,  4, "hero",      1, 49);
        add(37, "cobra",      25,   4,  4, "cobra",     1, 39);
        add(38, "pegas",      20,   3,  3, "pegas",     1, 19);
        add(39, "default",    10,   2,  2, "start",     0, 0);
    }

    /** Returns ONLY the animated 8-frame graphical badge glyph — NO text on the right! */
    public String getAnimatedBadge(RankDef r, int animFrame) {
        if (r == null) r = byId("default");
        if (r.hasCustomTextOverride && r.glyphIndex < 0) {
            return r.shortLabel;
        }
        int g = (r.glyphIndex >= 0 && r.glyphIndex < 40) ? r.glyphIndex : 39;
        int f = Math.abs(animFrame) % 8;
        char c = (char) (0xEA00 + g * 8 + f);
        return "&f" + c;
    }

    public String getAnimatedPrefix(Player p, int animFrame) {
        String customUserPref = store.getUserCustomPrefix(p.getName());
        RankDef r = getRank(p);
        String base;
        if (customUserPref != null && !customUserPref.isEmpty()) {
            base = customUserPref.endsWith(" ") ? customUserPref : (customUserPref + " ");
        } else {
            base = getAnimatedBadge(r, animFrame) + " &f";
        }
        String title = store.getActiveTitleDisplay(p.getName());
        if (title != null && !title.isEmpty()) {
            return base + title + " &f";
        }
        return base;
    }

    public void loadCustomRankOverrides() {
        for (Map.Entry<String, Map<String, Object>> e : store.getLpGroupOverrides().entrySet()) {
            String gid = e.getKey().toLowerCase(Locale.ROOT);
            Map<String, Object> meta = e.getValue();
            int w = meta.containsKey("weight") ? ((Number) meta.get("weight")).intValue() : 15;
            RankDef existing = ranks.get(gid);
            if (existing != null) {
                if (meta.containsKey("weight")) existing.weight = w;
            } else {
                String pref = meta.containsKey("prefix") ? String.valueOf(meta.get("prefix")) : ("&#38BDF8&l[" + gid.toUpperCase(Locale.ROOT) + "] &f");
                RankDef custom = new RankDef(gid, w, 39, -1, pref.endsWith(" ") ? pref : (pref + " "), pref.trim(), 5, 4, "pegas", 1, 0);
                custom.hasCustomTextOverride = true;
                ranks.put(gid, custom);
            }
        }
    }

    public RankDef createOrUpdateGroup(String id, int weight, String prefix) {
        String low = id.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_.-]", "");
        if (low.isEmpty()) return byId("default");
        RankDef d = ranks.get(low);
        if (d == null) {
            String p = (prefix == null || prefix.isEmpty()) ? ("&#38BDF8&l[" + low.toUpperCase(Locale.ROOT) + "] &f") : prefix;
            d = new RankDef(low, weight, 39, -1, p.endsWith(" ") ? p : (p + " "), p.trim(), 5, 4, "pegas", 1, 0);
            d.hasCustomTextOverride = true;
            ranks.put(low, d);
        } else {
            if (weight > 0) d.weight = weight;
            if (prefix != null && !prefix.isEmpty() && d.glyphIndex < 0) {
                d.chatPrefix = prefix.endsWith(" ") ? prefix : (prefix + " ");
                d.shortLabel = prefix.trim();
            }
        }
        store.saveLpGroupMeta(low, d.weight, d.chatPrefix);
        return d;
    }

    public boolean deleteCustomGroup(String id) {
        String low = id.toLowerCase(Locale.ROOT);
        if ("default".equals(low) || "owner".equals(low)) return false;
        ranks.remove(low);
        store.deleteLpGroup(low);
        return true;
    }

    public Collection<RankDef> allRanks() {
        return Collections.unmodifiableCollection(ranks.values());
    }

    public boolean exists(String id) {
        return id != null && ranks.containsKey(id.toLowerCase(Locale.ROOT));
    }

    public RankDef byId(String id) {
        if (id == null) return ranks.get("default");
        RankDef d = ranks.get(id.toLowerCase(Locale.ROOT));
        return d != null ? d : ranks.get("default");
    }

    public RankDef getRank(Player p) {
        if (p == null) return ranks.get("default");
        String stored = store.getSavedRank(p.getName());
        String lpGroup = readLuckPermsPrimaryGroup(p.getUniqueId());
        RankDef rStored = byId(stored);
        RankDef rLp = byId(lpGroup);
        return rLp.weight > rStored.weight ? rLp : rStored;
    }

    /** ONLY OWNER in CREATIVE mode can break/place blocks in spawn_world! */
    public boolean canModifySpawn(Player p) {
        if (p == null) return false;
        RankDef r = getRank(p);
        return ("owner".equalsIgnoreCase(r.id) || "techadmin".equalsIgnoreCase(r.id))
                && p.getGameMode() == GameMode.CREATIVE;
    }

    /** Strict Privilege-Only Kits */
    public boolean canClaimKit(Player p, String kitId) {
        if (p == null || kitId == null) return false;
        String k = kitId.toLowerCase(Locale.ROOT);
        if ("start".equals(k) || "newyear".equals(k)) return true;
        RankDef my = getRank(p);
        if ("owner".equals(my.id) || "techadmin".equals(my.id)) return true;
        if ("owner".equals(k)) return "owner".equals(my.id);
        if ("curator".equals(k)) return "curator".equals(my.id);
        if ("kp".equals(k)) return my.tier == 6;
        if ("media".equals(k)) return my.tier == 3 || "downer".equals(my.id);
        RankDef req = byId(k);
        if (req == null || "default".equals(req.id)) return false;
        if (my.tier == 0) return false;
        if (my.tier == 6 || my.tier == 3) return req.weight <= 90;
        return my.weight >= req.weight;
    }

    public String getEffectivePrefix(Player p) {
        return getAnimatedPrefix(p, 0);
    }

    public void setRank(String playerName, String rankId, boolean syncLp) {
        RankDef def = byId(rankId);
        store.setSavedRank(playerName, def.id);
        store.save();
        if (syncLp && Bukkit.getPluginManager().getPlugin("LuckPerms") != null) {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "lp user " + playerName + " parent set " + def.id);
        }
        Player online = Bukkit.getPlayerExact(playerName);
        if (online != null) {
            store.refreshPermissions(online, this);
        }
    }

    private String readLuckPermsPrimaryGroup(UUID uuid) {
        try {
            if (Bukkit.getPluginManager().getPlugin("LuckPerms") == null) return "default";
            Class<?> prov = Class.forName("net.luckperms.api.LuckPermsProvider");
            Object api = prov.getMethod("get").invoke(null);
            Object um = api.getClass().getMethod("getUserManager").invoke(api);
            Object user = um.getClass().getMethod("getUser", UUID.class).invoke(um, uuid);
            if (user == null) return "default";
            Object pg = user.getClass().getMethod("getPrimaryGroup").invoke(user);
            return pg != null ? String.valueOf(pg) : "default";
        } catch (Throwable ignored) {
            return "default";
        }
    }

    public void syncAllGroupsToLuckPermsIfPresent() {
        if (Bukkit.getPluginManager().getPlugin("LuckPerms") == null) return;
        List<RankDef> list = new ArrayList<RankDef>(ranks.values());
        for (RankDef r : list) {
            if (!"default".equals(r.id)) {
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "lp creategroup " + r.id);
            }
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "lp group " + r.id + " setweight " + r.weight);
        }
    }
}
