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
        public String chatPrefix;
        public String shortLabel;
        public final int maxHomes;
        public final int maxClaims;
        public final String kitName;
        public final int tier; // 0=player, 1=vip, 2=god/king, 3=media, 4=dkp, 5=opka/downer, 6=kp, 7=owner
        public final int priceRub;

        public RankDef(String id, int weight, int sortOrder, String chatPrefix, String shortLabel,
                       int maxHomes, int maxClaims, String kitName, int tier, int priceRub) {
            this.id = id;
            this.weight = weight;
            this.sortOrder = sortOrder;
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

    private void add(String id, int weight, int sortOrder, String chatPrefix, String shortLabel,
                     int homes, int claims, String kit, int tier, int priceRub) {
        String low = id.toLowerCase(Locale.ROOT);
        ranks.put(low, new RankDef(low, weight, sortOrder, chatPrefix, shortLabel, homes, claims, kit, tier, priceRub));
    }

    private void registerAll() {
        add("owner",     300,  1, "&f\uE950 &#FF1744&lOWNER &f",        "&f\uE950 &#FF1744&lOWNER",     500, 99, "owner",     7, 0);
        add("techadmin", 290,  2, "&#F43F5E&l[Тех.Админ] &f",           "&#F43F5E&lТех.Админ",          500, 99, "owner",     7, 0);
        add("curator",   275,  3, "&f\uE940 &#22C55E&lCURATOR &f",      "&f\uE940 &#22C55E&lCURATOR",   500, 50, "curator",   6, 0);
        add("kp",        255,  4, "&f\uE964 &#10B981&l[КП] &f",         "&#10B981&lКП",                  30, 30, "kp",        6, 0);
        add("glmoder",   245,  5, "&f\uE966 &#7C3AED&l[Гл.Модер] &f",   "&#7C3AED&lГл.Модер",            25, 25, "kp",        6, 0);
        add("stmoder",   235,  6, "&f\uE966 &#4F46E5&l[Ст.Модер] &f",   "&#4F46E5&lСт.Модер",            20, 20, "kp",        6, 0);
        add("moder",     225,  7, "&f\uE966 &#2563EB&l[Модер] &f",      "&#2563EB&lМодер",               15, 15, "kp",        6, 0);
        add("sthelper",  215,  8, "&f\uE967 &#0EA5E9&l[Ст.Хелпер] &f",  "&#0EA5E9&lСт.Хелпер",           12, 12, "kp",        6, 0);
        add("helper",    205,  9, "&f\uE967 &#38BDF8&l[Хелпер] &f",     "&#38BDF8&lХелпер",              10, 10, "kp",        6, 0);

        add("downer",    190, 10, "&f\uE960 &#FACC15&lD.OWNER &f",      "&#FACC15&l👑 D.OWNER",         500, 99, "downer",    5, 1499);
        add("opka",      178, 11, "&f\uE961 &#C026D3&lOPKA &f",         "&#C026D3&l🔥 OPKA",             60, 50, "opka",      5, 999);
        add("dkp",       168, 12, "&f\uE963 &#FF6D00&lДКП &f",          "&#FF6D00&l⚡ ДКП",              45, 35, "dkp",       4, 899);
        add("dadmin",    160, 13, "&f\uE968 &#DC2626&lD.ADMIN &f",      "&#DC2626&lD.ADMIN",             40, 30, "dadmin",    4, 799);
        add("dmladmin",  150, 14, "&#F97316&lD.ML.ADMIN &f",            "&#F97316&lD.ML.ADMIN",          36, 25, "dmladmin",  4, 599);
        add("dglmoder",  140, 15, "&#8B5CF6&lD.GLMODER &f",             "&#8B5CF6&lD.GLMODER",           32, 22, "dglmoder",  4, 449);
        add("dstmoder",  130, 16, "&#3B82F6&lD.ST.MODER &f",            "&#3B82F6&lD.ST.MODER",          28, 20, "dstmoder",  4, 349);
        add("dmoder",    120, 17, "&#14B8A6&lD.MODER &f",               "&#14B8A6&lD.MODER",             24, 18, "dmoder",    4, 249);
        add("dhelper",   115, 18, "&#2DD4BF&lD.HELPER &f",              "&#2DD4BF&lD.HELPER",            20, 16, "dhelper",   4, 219);

        add("mediaplus", 110, 19, "&f\uE965 &#F43F5E&lMEDIA+ &f",       "&#F43F5E&l🎬 MEDIA+",           20, 20, "media",     3, 0);
        add("media",     108, 20, "&#EC4899&l🎬 MEDIA &f",              "&#EC4899&l🎬 MEDIA",            15, 15, "media",     3, 0);
        add("yt",        106, 21, "&#FF0000&lYOU&#FFFFFF&lTUBE &f",     "&#FF0000&lYOUTUBE",             15, 12, "media",     3, 0);
        add("twitch",    104, 22, "&#9146FF&lTWITCH &f",                "&#9146FF&lTWITCH",              15, 12, "media",     3, 0);
        add("tiktok",    102, 23, "&#FF0050&lTik&#00F2EA&lTok &f",      "&#FF0050&lTikTok",              15, 12, "media",     3, 0);

        add("sponsor",    98, 24, "&#FBBF24&l★ SPONSOR &f",             "&#FBBF24&lSPONSOR",             22, 15, "sponsor",   2, 229);
        add("bull",       94, 25, "&#EA580C&lBULL &f",                  "&#EA580C&lBULL",                20, 14, "bull",      2, 209);
        add("king",       90, 26, "&f\uE962 &#A855F7&lKING &f",         "&#A855F7&l👑 KING",             18, 12, "king",      2, 199);
        add("dragon",     80, 27, "&#D946EF&lDRAGON &f",                "&#D946EF&lDRAGON",              16, 11, "dragon",    2, 179);
        add("imperator",  75, 28, "&#F43F5E&lIMPERATOR &f",             "&#F43F5E&lIMPERATOR",           15, 10, "imperator", 2, 169);
        add("overlord",   70, 29, "&#2DD4BF&lOVERLORD &f",              "&#2DD4BF&lOVERLORD",            14,  9, "overlord",  2, 159);
        add("god",        65, 30, "&f\uE969 &#FACC15&lGOD &f",          "&#FACC15&l⚡ GOD",              13,  8, "god",       2, 149);
        add("dracula",    60, 31, "&#DC2626&lDRACULA &f",               "&#DC2626&lDRACULA",             12,  7, "dracula",   1, 129);
        add("tiger",      55, 32, "&#FB923C&lTIGER &f",                 "&#FB923C&lTIGER",               11,  7, "tiger",     1, 115);
        add("vampire",    50, 33, "&f\uE96A &#EF4444&lVAMPIRE &f",      "&#EF4444&lVAMPIRE",             10,  6, "vampire",   1, 99);
        add("rabbit",     45, 34, "&f\uE96B &#38BDF8&lRABBIT &f",       "&#38BDF8&lRABBIT",               8,  6, "rabbit",    1, 89);
        add("titan",      40, 35, "&#60A5FA&lTITAN &f",                 "&#60A5FA&lTITAN",                7,  5, "titan",     1, 69);
        add("bunny",      35, 36, "&f\uE96C &#EC4899&lBUNNY &f",        "&#EC4899&lBUNNY",                6,  5, "bunny",     1, 59);
        add("hero",       30, 37, "&#38BDF8&lHERO &f",                  "&#38BDF8&lHERO",                 5,  4, "hero",      1, 49);
        add("cobra",      25, 38, "&#84CC16&lCOBRA &f",        "&#84CC16&lCOBRA",                4,  4, "cobra",     1, 39);
        add("pegas",      20, 39, "&f\uE96E &#22C55E&lPEGAS &f",        "&#22C55E&lPEGAS",                3,  3, "pegas",     1, 19);
        add("default",    10, 40, "&f\uE96F &#B0BEC5",                  "&#B0BEC5Игрок",                  2,  2, "start",     0, 0);
    }

    public void loadCustomRankOverrides() {
        for (Map.Entry<String, Map<String, Object>> e : store.getLpGroupOverrides().entrySet()) {
            String gid = e.getKey().toLowerCase(Locale.ROOT);
            Map<String, Object> meta = e.getValue();
            int w = meta.containsKey("weight") ? ((Number) meta.get("weight")).intValue() : 15;
            String pref = meta.containsKey("prefix") ? String.valueOf(meta.get("prefix")) : ("&7[" + gid.toUpperCase(Locale.ROOT) + "] &f");
            RankDef existing = ranks.get(gid);
            if (existing != null) {
                if (meta.containsKey("weight")) existing.weight = w;
                if (meta.containsKey("prefix")) {
                    existing.chatPrefix = pref.endsWith(" ") ? pref : (pref + " ");
                }
            } else {
                add(gid, w, 39, pref.endsWith(" ") ? pref : (pref + " "), pref.trim(), 5, 4, "pegas", 1, 0);
            }
        }
    }

    public RankDef createOrUpdateGroup(String id, int weight, String prefix) {
        String low = id.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_.-]", "");
        if (low.isEmpty()) return byId("default");
        RankDef d = ranks.get(low);
        if (d == null) {
            String p = (prefix == null || prefix.isEmpty()) ? ("&#38BDF8&l[" + low.toUpperCase(Locale.ROOT) + "] &f") : prefix;
            add(low, weight, 39, p.endsWith(" ") ? p : (p + " "), p.trim(), 5, 4, "pegas", 1, 0);
            d = ranks.get(low);
        } else {
            if (weight > 0) d.weight = weight;
            if (prefix != null && !prefix.isEmpty()) {
                d.chatPrefix = prefix.endsWith(" ") ? prefix : (prefix + " ");
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

    /**
     * Строгая проверка: ломать и строить на спавне может ТОЛЬКО ранг OWNER в режиме CREATIVE!
     */
    public boolean canModifySpawn(Player p) {
        if (p == null) return false;
        RankDef r = getRank(p);
        return ("owner".equalsIgnoreCase(r.id) || "techadmin".equalsIgnoreCase(r.id))
                && p.getGameMode() == GameMode.CREATIVE;
    }

    /**
     * Строгая проверка доступа к китам ТОЛЬКО по привилегиям (без обхода через обычную опку).
     */
    public boolean canClaimKit(Player p, String kitId) {
        if (p == null || kitId == null) return false;
        String k = kitId.toLowerCase(Locale.ROOT);
        if ("start".equals(k) || "newyear".equals(k)) {
            return true;
        }
        RankDef my = getRank(p);
        if ("owner".equals(my.id) || "techadmin".equals(my.id)) {
            return true;
        }
        if ("owner".equals(k)) {
            return "owner".equals(my.id);
        }
        if ("curator".equals(k)) {
            return "curator".equals(my.id);
        }
        if ("kp".equals(k)) {
            return my.tier == 6; // helper..curator
        }
        if ("media".equals(k)) {
            return my.tier == 3 || "downer".equals(my.id);
        }
        // Донат и ДКП киты: строго если у игрока донат-привилегия с нужным весом!
        RankDef req = byId(k);
        if (req == null || "default".equals(req.id)) return false;
        if (my.tier == 0) return false; // Обычный игрок не может брать донат-киты!
        if (my.tier == 6) {
            // Персонал КП может брать свой кит КП + базовые до KING, но не OPKA/DOWNER/DKP
            return req.weight <= 90;
        }
        if (my.tier == 3) {
            // Медиа может брать media + киты до KING
            return req.weight <= 90;
        }
        return my.weight >= req.weight;
    }

    public String getEffectivePrefix(Player p) {
        String customUserPref = store.getUserCustomPrefix(p.getName());
        RankDef r = getRank(p);
        String base = (customUserPref != null && !customUserPref.isEmpty())
                ? (customUserPref.endsWith(" ") ? customUserPref : customUserPref + " ")
                : r.chatPrefix;
        String title = store.getActiveTitleDisplay(p.getName());
        if (title != null && !title.isEmpty()) {
            return base + title + " &f";
        }
        return base;
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
