package ru.reallyworld.ultimate.core.module;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import ru.reallyworld.ultimate.core.api.ReallyModule;
import ru.reallyworld.ultimate.core.config.ConfigManager;
import ru.reallyworld.ultimate.core.util.TextUtil;

import java.util.*;

/**
 * Donor & Gameplay Kit Engine (/kit, /kits) supporting Vanilla, Enchanted,
 * and Signed Custom ReallyWorld Items with Persistent Cooldowns.
 */
public final class KitsModule implements ReallyModule {
    public record KitDefinition(
            String id,
            String displayName,
            Material icon,
            String permission,
            long cooldownSeconds,
            List<String> itemSpecs
    ) {}

    private final ConfigManager configManager;
    private final LocalizationModule locale;
    private final CooldownModule cooldowns;
    private final CustomItemsModule customItems;
    private final Map<String, KitDefinition> kits = new LinkedHashMap<>();
    private volatile boolean enabled;

    public KitsModule(
            ConfigManager configManager,
            LocalizationModule locale,
            CooldownModule cooldowns,
            CustomItemsModule customItems
    ) {
        this.configManager = configManager;
        this.locale = locale;
        this.cooldowns = cooldowns;
        this.customItems = customItems;
    }

    @Override
    public String id() { return "kits"; }

    @Override
    public String displayName() { return "KitsModule (14 Kits)"; }

    @Override
    public boolean isEnabled() { return enabled; }

    @Override
    public void enable() {
        this.enabled = true;
        reloadKits();
    }

    @Override
    public void reload() {
        reloadKits();
    }

    @Override
    public void disable() {
        this.enabled = false;
    }

    private synchronized void reloadKits() {
        kits.clear();
        ConfigurationSection sec = configManager.get("kits.yml").getConfigurationSection("kits");
        if (sec == null) return;
        for (String id : sec.getKeys(false)) {
            ConfigurationSection k = sec.getConfigurationSection(id);
            Material icon = Material.matchMaterial(k.getString("icon", "CHEST"));
            kits.put(id.toLowerCase(Locale.ROOT), new KitDefinition(
                    id.toLowerCase(Locale.ROOT),
                    k.getString("display-name", id),
                    icon == null ? Material.CHEST : icon,
                    k.getString("permission", "reallycore.kit." + id.toLowerCase(Locale.ROOT)),
                    k.getLong("cooldown-seconds", 86400L),
                    List.copyOf(k.getStringList("items"))
            ));
        }
    }

    public Collection<KitDefinition> getKits() {
        return kits.values();
    }

    public Optional<KitDefinition> getKit(String id) {
        return Optional.ofNullable(kits.get(id.toLowerCase(Locale.ROOT)));
    }

    public boolean claimKit(Player player, String kitId) {
        KitDefinition kit = kits.get(kitId.toLowerCase(Locale.ROOT));
        if (kit == null) {
            locale.sendRaw(player, "<red>Набор <white>" + kitId + "</white> не найден!");
            return false;
        }
        if (kit.permission() != null && !kit.permission().isBlank() && !player.hasPermission(kit.permission())) {
            locale.send(player, "no-permission");
            return false;
        }
        String cdKey = "kit:" + kit.id();
        long rem = cooldowns.getRemainingSeconds(player.getUniqueId(), cdKey);
        if (rem > 0 && !player.hasPermission("reallycore.admin")) {
            locale.send(player, "on-cooldown", Map.of("time", TextUtil.formatDuration(rem)));
            return false;
        }

        deliverKitItemsDirect(player, kit.id());
        cooldowns.setCooldown(player.getUniqueId(), cdKey, kit.cooldownSeconds(), true);
        locale.sendRaw(player, "<green>Вы получили набор " + kit.displayName() + "<green>!");
        return true;
    }

    public void deliverKitItemsDirect(Player player, String kitId) {
        KitDefinition kit = kits.get(kitId.toLowerCase(Locale.ROOT));
        if (kit == null) return;
        for (String spec : kit.itemSpecs()) {
            for (ItemStack built : parseItemSpec(spec)) {
                Map<Integer, ItemStack> overflow = player.getInventory().addItem(built);
                overflow.values().forEach(st -> player.getWorld().dropItemNaturally(player.getLocation(), st));
            }
        }
    }

    public List<ItemStack> parseItemSpec(String spec) {
        if (spec == null || spec.isBlank()) return List.of();
        String[] parts = spec.split(":");
        if (parts.length >= 4 && "CUSTOM".equalsIgnoreCase(parts[0])) {
            String cat = parts[1];
            String itemId = parts[2];
            int amt = Integer.parseInt(parts[3]);
            if ("ARMOR_SET".equalsIgnoreCase(cat)) {
                return customItems.createArmorSet(itemId);
            }
            return customItems.createCustomItem(cat, itemId, amt).map(List::of).orElse(List.of());
        }
        if (parts.length >= 3 && "VANILLA".equalsIgnoreCase(parts[0])) {
            Material mat = Material.matchMaterial(parts[1]);
            if (mat == null) return List.of();
            int amt = Math.max(1, Integer.parseInt(parts[2]));
            ItemStack st = new ItemStack(mat, amt);
            if (parts.length >= 4 && !parts[3].isBlank()) {
                ItemMeta meta = st.getItemMeta();
                for (String enchPair : parts[3].split(",")) {
                    String[] kv = enchPair.split("=");
                    if (kv.length == 2) {
                        Enchantment e = Registry.ENCHANTMENT.get(NamespacedKey.minecraft(kv[0].toLowerCase(Locale.ROOT)));
                        if (e != null) {
                            meta.addEnchant(e, Integer.parseInt(kv[1]), true);
                        }
                    }
                }
                st.setItemMeta(meta);
            }
            return List.of(st);
        }
        return List.of();
    }
}
