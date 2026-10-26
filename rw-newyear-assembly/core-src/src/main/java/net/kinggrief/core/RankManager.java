package net.kinggrief.core;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class RankManager {

    private static final Set<String> STALE_PLAIN_LABELS = new HashSet<String>(Arrays.asList(
            "owner", "techadmin", "tech.adm", "тех.админ", "curator", "куратор", "kp", "кп",
            "glmoder", "гл.модер", "stmoder", "ст.модер", "moder", "модер", "sthelper", "ст.хелпер",
            "helper", "хелпер", "downer", "d.owner", "д.овнер", "opka", "опка", "dkp", "д.кп",
            "dadmin", "д.админ", "dmladmin", "д.мл.админ", "dglmoder", "д.гл.модер", "dstmoder", "д.ст.модер",
            "dmoder", "д.модер", "dhelper", "д.хелпер", "media+", "mediaplus", "медиа+", "media", "медиа",
            "youtube", "yt", "ютуб", "twitch", "твич", "tiktok", "тикток", "sponsor", "спонсор",
            "bull", "булл", "king", "кинг", "dragon", "драгон", "imperator", "император",
            "overlord", "оверлорд", "god", "год", "dracula", "дракула", "tiger", "тигр",
            "vampire", "вампир", "rabbit", "кролик", "titan", "титан", "bunny", "банни",
            "hero", "герой", "cobra", "кобра", "pegas", "пегас", "player", "игрок", "default"
    ));

    public static final class RankDef {
        public final String id;
        public int weight;
        public final int sortOrder;
        public final int glyphIndex; // 0..39 -> \uEA00 + glyphIndex*8 + frame
        public String chatPrefix;
        public String shortLabel;
        public String chatTextColor;
        public final int maxHomes;
        public final int maxClaims;
        public final String kitName;
        public final int tier; // 0=player, 1=vip, 2=god/king, 3=media, 4=dkp, 5=opka/downer, 6=kp, 7=owner
        public final int priceRub;
        public boolean hasCustomTextOverride = false;

        public RankDef(String id, int weight, int sortOrder, int glyphIndex, String chatPrefix, String shortLabel,
                       String chatTextColor, int maxHomes, int maxClaims, String kitName, int tier, int priceRub) {
            this.id = id;
            this.weight = weight;
            this.sortOrder = sortOrder;
            this.glyphIndex = glyphIndex;
            this.chatPrefix = chatPrefix;
            this.shortLabel = shortLabel;
            this.chatTextColor = chatTextColor;
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

    private void add(int glyphIdx, String id, int weight, int homes, int claims, String kit, int tier, int priceRub, String chatColor) {
        String low = id.toLowerCase(Locale.ROOT);
        char frame0 = (char) (0xEA00 + glyphIdx * 8);
        // ONLY the luxury OWNER-style graphical badge glyph — ZERO duplicate plain text!
        String badgeOnly = "&f" + frame0;
        String chatPref = badgeOnly + " &f";
        ranks.put(low, new RankDef(low, weight, glyphIdx + 1, glyphIdx, chatPref, badgeOnly, chatColor, homes, claims, kit, tier, priceRub));
    }

    private void registerAll() {
        add(0,  "owner",     300, 500, 99, "owner",     7, 0,    "&#FF6B8B");
        add(1,  "techadmin", 290, 500, 99, "owner",     7, 0,    "&#7DD3FC");
        add(2,  "curator",   275, 500, 50, "curator",   6, 0,    "&#86EFAC");
        add(3,  "kp",        255,  30, 30, "kp",        6, 0,    "&#6EE7B7");
        add(4,  "glmoder",   245,  25, 25, "kp",        6, 0,    "&#D8B4FE");
        add(5,  "stmoder",   235,  20, 20, "kp",        6, 0,    "&#A5B4FC");
        add(6,  "moder",     225,  15, 15, "kp",        6, 0,    "&#93C5FD");
        add(7,  "sthelper",  215,  12, 12, "kp",        6, 0,    "&#BAE6FD");
        add(8,  "helper",    205,  10, 10, "kp",        6, 0,    "&#E0F2FE");

        add(9,  "downer",    190, 500, 99, "downer",    5, 1499, "&#FDE047");
        add(10, "opka",      178,  60, 50, "opka",      5, 999,  "&#F0ABFC");
        add(11, "dkp",       168,  45, 35, "dkp",       4, 899,  "&#FDBA74");
        add(12, "dadmin",    160,  40, 30, "dadmin",    4, 799,  "&#FCA5A5");
        add(13, "dmladmin",  150,  36, 25, "dmladmin",  4, 599,  "&#FED7AA");
        add(14, "dglmoder",  140,  32, 22, "dglmoder",  4, 449,  "&#E9D5FF");
        add(15, "dstmoder",  130,  28, 20, "dstmoder",  4, 349,  "&#BFDBFE");
        add(16, "dmoder",    120,  24, 18, "dmoder",    4, 249,  "&#99F6E4");
        add(17, "dhelper",   115,  20, 16, "dhelper",   4, 219,  "&#CCFBF1");

        add(18, "mediaplus", 110,  20, 20, "media",     3, 0,    "&#FDA4AF");
        add(19, "media",     108,  15, 15, "media",     3, 0,    "&#F9A8D4");
        add(20, "yt",        106,  15, 12, "media",     3, 0,    "&#FECDD3");
        add(21, "twitch",    104,  15, 12, "media",     3, 0,    "&#D8B4FE");
        add(22, "tiktok",    102,  15, 12, "media",     3, 0,    "&#67E8F9");

        add(23, "sponsor",    98,  22, 15, "sponsor",   2, 229,  "&#FEF08A");
        add(24, "bull",       94,  20, 14, "bull",      2, 209,  "&#FDBA74");
        add(25, "king",       90,  18, 12, "king",      2, 199,  "&#FDE047");
        add(26, "dragon",     80,  16, 11, "dragon",    2, 179,  "&#F5D0FE");
        add(27, "imperator",  75,  15, 10, "imperator", 2, 169,  "&#FECDD3");
        add(28, "overlord",   70,  14,  9, "overlord",  2, 159,  "&#99F6E4");
        add(29, "god",        65,  13,  8, "god",       2, 149,  "&#FEF9C3");
        add(30, "dracula",    60,  12,  7, "dracula",   1, 129,  "&#FCA5A5");
        add(31, "tiger",      55,  11,  7, "tiger",     1, 115,  "&#FED7AA");
        add(32, "vampire",    50,  10,  6, "vampire",   1, 99,   "&#FECACA");
        add(33, "rabbit",     45,   8,  6, "rabbit",    1, 89,   "&#BAE6FD");
        add(34, "titan",      40,   7,  5, "titan",     1, 69,   "&#BFDBFE");
        add(35, "bunny",      35,   6,  5, "bunny",     1, 59,   "&#FBCFE8");
        add(36, "hero",       30,   5,  4, "hero",      1, 49,   "&#BAE6FD");
        add(37, "cobra",      25,   4,  4, "cobra",     1, 39,   "&#BBF7D0");
        add(38, "pegas",      20,   3,  3, "pegas",     1, 19,   "&#D1FAE5");
        add(39, "default",    10,   2,  2, "start",     0, 0,    "&7");
    }

    /** Strips legacy \uE900..\uE96F boxes and stale plain-text rank names like "[Модер]" or "OPKA". */
    public static String sanitizeCustomPrefix(String raw) {
        if (raw == null || raw.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < raw.length(); i++) {
            char ch = raw.charAt(i);
            if (ch >= 0xE900 && ch <= 0xE96F) continue;
            if (ch >= 0xEA00 && ch <= 0xEB3F) continue;
            sb.append(ch);
        }
        String cleaned = sb.toString().trim();
        String stripped = ChatColor.stripColor(ColorUtil.c(cleaned)).replace("[", "").replace("]", "").trim().toLowerCase(Locale.ROOT);
        if (stripped.isEmpty() || STALE_PLAIN_LABELS.contains(stripped)) {
            return "";
        }
        return cleaned;
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
        String customUserPref = sanitizeCustomPrefix(store.getUserCustomPrefix(p.getName()));
        RankDef r = getRank(p);
        String base = getAnimatedBadge(r, animFrame) + " ";
        if (!customUserPref.isEmpty()) {
            base = base + customUserPref + " ";
        }
        String title = store.getActiveTitleDisplay(p.getName());
        if (title != null && !title.isEmpty()) {
            return base + title + " &f";
        }
        return base + "&f";
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
                RankDef custom = new RankDef(gid, w, 39, -1, pref.endsWith(" ") ? pref : (pref + " "), pref.trim(), "&#BAE6FD", 5, 4, "pegas", 1, 0);
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
            d = new RankDef(low, weight, 39, -1, p.endsWith(" ") ? p : (p + " "), p.trim(), "&#BAE6FD", 5, 4, "pegas", 1, 0);
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
        return "owner".equalsIgnoreCase(r.id) && p.getGameMode() == GameMode.CREATIVE;
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
        store.setUserCustomPrefix(playerName, "");
        store.save();
        if (syncLp && Bukkit.getPluginManager().getPlugin("LuckPerms") != null) {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "lp user " + playerName + " meta clear prefix");
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
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "lp group " + r.id + " meta clear prefix");
            if (r.glyphIndex >= 0 && r.glyphIndex < 40) {
                char f0 = (char) (0xEA00 + r.glyphIndex * 8);
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "lp group " + r.id + " meta setprefix " + r.weight + " \"&f" + f0 + " &f\"");
            }
            if (!"owner".equals(r.id)) {
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "lp group " + r.id + " permission unset luckperms.*");
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "lp group " + r.id + " permission unset kinggrief.lp");
            }
        }
    }
}
