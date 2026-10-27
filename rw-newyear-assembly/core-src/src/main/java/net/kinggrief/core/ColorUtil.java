package net.kinggrief.core;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Color;
import org.bukkit.FireworkEffect;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Player;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ColorUtil {
    private static final Pattern HEX_PATTERN = Pattern.compile("&#([A-Fa-f0-9]{6})");
    private static final DecimalFormat MONEY_FMT;

    static {
        DecimalFormatSymbols sym = new DecimalFormatSymbols(Locale.US);
        sym.setGroupingSeparator(' ');
        MONEY_FMT = new DecimalFormat("#,##0", sym);
    }

    private ColorUtil() {}

    public static String c(String text) {
        if (text == null || text.isEmpty()) return "";
        Matcher matcher = HEX_PATTERN.matcher(text);
        StringBuffer sb = new StringBuffer();
        while (matcher.find()) {
            String hex = matcher.group(1);
            StringBuilder rep = new StringBuilder("§x");
            for (int i = 0; i < 6; i++) {
                rep.append('§').append(Character.toLowerCase(hex.charAt(i)));
            }
            matcher.appendReplacement(sb, Matcher.quoteReplacement(rep.toString()));
        }
        matcher.appendTail(sb);
        return ChatColor.translateAlternateColorCodes('&', sb.toString());
    }

    public static String fmtMoney(double amount) {
        if (amount >= 1_000_000_000.0) {
            return String.format(Locale.US, "%.2fB", amount / 1_000_000_000.0);
        }
        return MONEY_FMT.format(Math.round(amount));
    }

    public static String fmtInt(long val) {
        return MONEY_FMT.format(val);
    }

    public static String fmtTime(long seconds) {
        if (seconds <= 0) return "0с";
        long h = seconds / 3600;
        long m = (seconds % 3600) / 60;
        long s = seconds % 60;
        if (h > 0) return h + "ч " + m + "м";
        if (m > 0) return m + "м " + s + "с";
        return s + "с";
    }

    public static void sound(Player p, String soundName, float vol, float pitch) {
        if (p == null || !p.isOnline()) return;
        try {
            String key = soundName.contains(":") ? soundName : "minecraft:" + soundName;
            p.playSound(p.getLocation(), key, vol, pitch);
        } catch (Throwable ignored) {
        }
    }

    public static void actionBar(Player p, String msg) {
        if (p == null || !p.isOnline()) return;
        String colored = c(msg);
        try {
            // Try Paper / modern Spigot sendActionBar(String) if present or Bungee Component
            for (Method m : p.getClass().getMethods()) {
                if (m.getName().equals("sendActionBar") && m.getParameterCount() == 1
                        && m.getParameterTypes()[0] == String.class) {
                    m.invoke(p, colored);
                    return;
                }
            }
            Object spigot = p.spigot();
            Class<?> cmtCls = Class.forName("net.md_5.bungee.api.ChatMessageType");
            Class<?> bcCls = Class.forName("net.md_5.bungee.api.chat.BaseComponent");
            Class<?> tcCls = Class.forName("net.md_5.bungee.api.chat.TextComponent");
            Object actionEnum = Enum.valueOf((Class<Enum>) cmtCls, "ACTION_BAR");
            Object arr = tcCls.getMethod("fromLegacyText", String.class).invoke(null, colored);
            for (Method sm : spigot.getClass().getMethods()) {
                if (sm.getName().equals("sendMessage") && sm.getParameterCount() == 2
                        && sm.getParameterTypes()[0] == cmtCls) {
                    sm.invoke(spigot, actionEnum, arr);
                    return;
                }
            }
        } catch (Throwable ignored) {
        }
    }

    public static Material mat(String... candidates) {
        for (String name : candidates) {
            if (name == null || name.isEmpty()) continue;
            String clean = name.trim();
            if (clean.regionMatches(true, 0, "minecraft:", 0, 10)) {
                clean = clean.substring(10);
            }
            try {
                Material direct = Material.getMaterial(clean.toUpperCase(Locale.ROOT));
                if (direct != null) return direct;
            } catch (Throwable ignored) {}
            try {
                Material matched = Material.matchMaterial(clean.toLowerCase(Locale.ROOT));
                if (matched != null) return matched;
            } catch (Throwable ignored) {}
        }
        return Material.QUARTZ_BLOCK;
    }

    public static Enchantment ench(String modernKey, String legacyName) {
        try {
            Enchantment e = Enchantment.getByKey(NamespacedKey.minecraft(modernKey.toLowerCase(Locale.ROOT)));
            if (e != null) return e;
        } catch (Throwable ignored) {
        }
        try {
            Method m = Enchantment.class.getMethod("getByName", String.class);
            Object res = m.invoke(null, legacyName);
            if (res instanceof Enchantment) return (Enchantment) res;
        } catch (Throwable ignored) {
        }
        return null;
    }

    public static PotionEffectType potType(String modernKey, String legacyName) {
        try {
            Method m = PotionEffectType.class.getMethod("getByKey", NamespacedKey.class);
            Object res = m.invoke(null, NamespacedKey.minecraft(modernKey.toLowerCase(Locale.ROOT)));
            if (res instanceof PotionEffectType) return (PotionEffectType) res;
        } catch (Throwable ignored) {
        }
        try {
            PotionEffectType t = PotionEffectType.getByName(modernKey.toUpperCase(Locale.ROOT));
            if (t != null) return t;
        } catch (Throwable ignored) {
        }
        try {
            return PotionEffectType.getByName(legacyName.toUpperCase(Locale.ROOT));
        } catch (Throwable ignored) {
            return null;
        }
    }

    public static void addPot(org.bukkit.entity.LivingEntity p, String modernKey, String legacyName, int durationTicks, int amplifier) {
        if (p == null) return;
        PotionEffectType t = potType(modernKey, legacyName);
        if (t != null) {
            // particles = false -> ZERO screen-blocking swirls in front of 3D custom items!
            p.addPotionEffect(new PotionEffect(t, durationTicks, amplifier, true, false, true));
        }
    }

    public static void applyCustomItemModel(ItemMeta meta, String modelId, int cmdData) {
        if (meta == null) return;
        try {
            meta.setCustomModelData(Integer.valueOf(cmdData));
        } catch (Throwable ignored) {
        }
        if (modelId != null && !modelId.isEmpty()) {
            try {
                Method setModel = ItemMeta.class.getMethod("setItemModel", NamespacedKey.class);
                setModel.invoke(meta, new NamespacedKey("kinggrief", modelId));
            } catch (Throwable ignored) {
            }
        }
    }

    public static void applyArmorTrimSafe(ItemMeta meta, String trimMaterialKey, String trimPatternKey) {
        if (meta == null) return;
        try {
            Class<?> armorMetaCls = Class.forName("org.bukkit.inventory.meta.ArmorMeta");
            if (!armorMetaCls.isInstance(meta)) return;
            Class<?> registryCls = Class.forName("org.bukkit.Registry");
            Object matReg = registryCls.getField("TRIM_MATERIAL").get(null);
            Object patReg = registryCls.getField("TRIM_PATTERN").get(null);
            Method getM = registryCls.getMethod("get", NamespacedKey.class);
            Object tMat = getM.invoke(matReg, NamespacedKey.minecraft(trimMaterialKey.toLowerCase(Locale.ROOT)));
            Object tPat = getM.invoke(patReg, NamespacedKey.minecraft(trimPatternKey.toLowerCase(Locale.ROOT)));
            if (tMat != null && tPat != null) {
                Class<?> trimMatCls = Class.forName("org.bukkit.inventory.meta.trim.TrimMaterial");
                Class<?> trimPatCls = Class.forName("org.bukkit.inventory.meta.trim.TrimPattern");
                Class<?> armorTrimCls = Class.forName("org.bukkit.inventory.meta.trim.ArmorTrim");
                Object trim = armorTrimCls.getConstructor(trimMatCls, trimPatCls).newInstance(tMat, tPat);
                armorMetaCls.getMethod("setTrim", armorTrimCls).invoke(meta, trim);
            }
        } catch (Throwable ignored) {
        }
    }

    public static void setMaxHealthSafe(Player p, double targetHp) {
        if (p == null) return;
        double clamped = Math.max(20.0, Math.min(80.0, targetHp));
        try {
            org.bukkit.attribute.Attribute attr = null;
            try {
                Class<?> regCls = Class.forName("org.bukkit.Registry");
                Object attrReg = regCls.getField("ATTRIBUTE").get(null);
                Object found = regCls.getMethod("get", NamespacedKey.class).invoke(attrReg, NamespacedKey.minecraft("max_health"));
                if (found == null) {
                    found = regCls.getMethod("get", NamespacedKey.class).invoke(attrReg, NamespacedKey.minecraft("generic.max_health"));
                }
                if (found instanceof org.bukkit.attribute.Attribute) {
                    attr = (org.bukkit.attribute.Attribute) found;
                }
            } catch (Throwable ignored) {}
            if (attr == null) {
                for (String fn : new String[]{"MAX_HEALTH", "GENERIC_MAX_HEALTH"}) {
                    try {
                        Object v = org.bukkit.attribute.Attribute.class.getField(fn).get(null);
                        if (v instanceof org.bukkit.attribute.Attribute) {
                            attr = (org.bukkit.attribute.Attribute) v;
                            break;
                        }
                    } catch (Throwable ignored) {}
                }
            }
            if (attr != null) {
                org.bukkit.attribute.AttributeInstance inst = p.getAttribute(attr);
                if (inst != null && Math.abs(inst.getBaseValue() - clamped) > 0.05) {
                    inst.setBaseValue(clamped);
                    if (p.getHealth() > clamped) p.setHealth(clamped);
                }
            }
        } catch (Throwable ignored) {
        }
    }

    public static void spawnFirework(Location loc) {
        if (loc == null || loc.getWorld() == null) return;
        try {
            Location up = loc.clone().add(0, 1.2, 0);
            loc.getWorld().spawnParticle(org.bukkit.Particle.TOTEM_OF_UNDYING, up, 45, 0.6, 0.8, 0.6, 0.2);
            loc.getWorld().spawnParticle(org.bukkit.Particle.END_ROD, up, 30, 0.5, 0.7, 0.5, 0.12);
            loc.getWorld().playSound(up, "minecraft:entity.firework_rocket.launch", 1.0f, 1.1f);
            loc.getWorld().playSound(up, "minecraft:entity.firework_rocket.twinkle", 1.0f, 1.1f);
        } catch (Throwable ignored) {
        }
    }

    public static String sha256(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(hash.length * 2);
            for (byte b : hash) sb.append(String.format("%02x", b & 0xff));
            return sb.toString();
        } catch (Exception e) {
            return Integer.toHexString(input.hashCode());
        }
    }

    public static byte[] hexToBytes(String hex) {
        if (hex == null || hex.length() != 40) return null;
        try {
            byte[] out = new byte[20];
            for (int i = 0; i < 20; i++) {
                out[i] = (byte) Integer.parseInt(hex.substring(i * 2, i * 2 + 2), 16);
            }
            return out;
        } catch (Exception e) {
            return null;
        }
    }
}
