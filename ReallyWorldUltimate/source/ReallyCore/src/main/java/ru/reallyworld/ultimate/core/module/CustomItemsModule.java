package ru.reallyworld.ultimate.core.module;

import net.kyori.adventure.text.Component;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ArmorMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.potion.PotionEffectTypeCategory;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;
import ru.reallyworld.ultimate.core.api.RarityType;
import ru.reallyworld.ultimate.core.api.ReallyModule;
import ru.reallyworld.ultimate.core.config.ConfigManager;
import ru.reallyworld.ultimate.core.util.CryptoSignatureUtil;
import ru.reallyworld.ultimate.core.util.TextUtil;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Authentic ReallyWorld Custom Item Engine:
 * - 15 Spheres (+ Anvil Tier Upgrades TIER_1..MAX)
 * - 12 Talismans (strictly 1 active talisman cap, zero inventory stacking)
 * - 9 Active Artifacts
 * - 7 Custom Weapons across all 1.21 weapon classes
 * - 7 4-Piece Custom Armor Sets with Full-Set Bonuses
 * - 14 Tactical PvP Consumables (Trapka, Plast, Divine Aura, Disorientation, Reveal Dust, Fire Whirlwind, Stun, Anti-Flight, Elixirs, Scrolls)
 * Every item is cryptographically signed in PersistentDataContainer (HMAC-SHA256).
 */
public final class CustomItemsModule implements ReallyModule, Listener {
    public record VerifiedCustomItem(String category, String itemId, String tier, boolean soulbound) {}

    private final JavaPlugin plugin;
    private final ConfigManager configManager;
    private final LocalizationModule locale;
    private final CooldownModule cooldowns;
    private final SecurityModule security;

    private final NamespacedKey keyCategory;
    private final NamespacedKey keyItemId;
    private final NamespacedKey keyTier;
    private final NamespacedKey keySoulbound;
    private final NamespacedKey keyHmac;

    // Tracks temporary Trapka / Plast blocks so players cannot mine them for free obsidian and they always roll back!
    private final Set<Location> temporaryStructureBlocks = ConcurrentHashMap.newKeySet();

    private SpawnWorldModule spawnWorldModule;
    private ClanModule clanModule;
    private BukkitTask passiveBuffTicker;
    private volatile boolean enabled;

    public CustomItemsModule(
            JavaPlugin plugin,
            ConfigManager configManager,
            LocalizationModule locale,
            CooldownModule cooldowns,
            SecurityModule security
    ) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.locale = locale;
        this.cooldowns = cooldowns;
        this.security = security;

        String ns = "reallycore";
        this.keyCategory = plugin != null ? new NamespacedKey(plugin, "rw_category") : new NamespacedKey(ns, "rw_category");
        this.keyItemId = plugin != null ? new NamespacedKey(plugin, "rw_item_id") : new NamespacedKey(ns, "rw_item_id");
        this.keyTier = plugin != null ? new NamespacedKey(plugin, "rw_tier") : new NamespacedKey(ns, "rw_tier");
        this.keySoulbound = plugin != null ? new NamespacedKey(plugin, "rw_soulbound") : new NamespacedKey(ns, "rw_soulbound");
        this.keyHmac = plugin != null ? new NamespacedKey(plugin, "rw_hmac") : new NamespacedKey(ns, "rw_hmac");
    }

    public void bindModules(SpawnWorldModule spawnWorldModule, ClanModule clanModule) {
        this.spawnWorldModule = spawnWorldModule;
        this.clanModule = clanModule;
    }

    @Override
    public String id() { return "custom-items"; }

    @Override
    public String displayName() { return "CustomItemsModule (Spheres, Talismans, Artifacts, Gear, Consumables)"; }

    @Override
    public boolean isEnabled() { return enabled; }

    @Override
    public void enable() {
        this.enabled = configManager.main().getBoolean("modules.custom-items", true);
        if (plugin != null && Bukkit.getServer() != null) {
            Bukkit.getPluginManager().registerEvents(this, plugin);
            this.passiveBuffTicker = Bukkit.getScheduler().runTaskTimer(plugin, this::tickPassiveEquipment, 40L, 40L);
        }
    }

    @Override
    public void reload() {
        this.enabled = configManager.main().getBoolean("modules.custom-items", true);
    }

    @Override
    public void disable() {
        this.enabled = false;
        if (passiveBuffTicker != null) passiveBuffTicker.cancel();
        // Cleanly revert all active Trapka/Plast temporary blocks on shutdown
        for (Location loc : temporaryStructureBlocks) {
            if (loc.getWorld() != null) {
                loc.getBlock().setType(Material.AIR, false);
            }
        }
        temporaryStructureBlocks.clear();
    }

    // ---- ITEM FACTORY ----
    public Optional<ItemStack> createCustomItem(String rawCategory, String rawId, int amount) {
        return createCustomItemWithTier(rawCategory, rawId, null, amount);
    }

    public Optional<ItemStack> createCustomItemWithTier(String rawCategory, String rawId, String explicitTier, int amount) {
        if (rawCategory == null || rawId == null) return Optional.empty();
        String cat = rawCategory.trim().toUpperCase(Locale.ROOT);
        String id = rawId.trim().toLowerCase(Locale.ROOT);

        return switch (cat) {
            case "SPHERE", "SPHERES" -> buildSphere(id, explicitTier == null ? "TIER_1" : explicitTier, amount);
            case "TALISMAN", "TALISMANS" -> buildTalisman(id, amount);
            case "ARTIFACT", "ARTIFACTS" -> buildArtifact(id, amount);
            case "WEAPON", "WEAPONS" -> buildWeapon(id, amount);
            case "ARMOR", "ARMOR_PIECE" -> buildArmorPiece(id, amount);
            case "CONSUMABLE", "CONSUMABLES" -> buildConsumable(id, amount);
            default -> Optional.empty();
        };
    }

    public List<ItemStack> createArmorSet(String setId) {
        List<ItemStack> pieces = new ArrayList<>();
        for (String slot : List.of("HELMET", "CHESTPLATE", "LEGGINGS", "BOOTS")) {
            buildArmorPiece(setId.toLowerCase(Locale.ROOT) + ":" + slot, 1).ifPresent(pieces::add);
        }
        return pieces;
    }

    private Optional<ItemStack> buildSphere(String id, String tier, int amount) {
        var cfg = configManager.get("spheres.yml");
        ConfigurationSection s = cfg.getConfigurationSection("spheres." + id);
        if (s == null) return Optional.empty();

        double tierMult = cfg.getDouble("settings.tiers." + tier + ".multiplier", 1.0);
        String tierSuffix = cfg.getString("settings.tiers." + tier + ".suffix", "<gray>[I]");
        RarityType rarity = RarityType.fromString(s.getString("rarity", "EPIC"));
        ItemStack stack = new ItemStack(Material.PLAYER_HEAD, Math.max(1, amount));
        ItemMeta meta = stack.getItemMeta();

        meta.displayName(TextUtil.mm(s.getString("name", id) + " " + tierSuffix));
        List<Component> lore = new ArrayList<>();
        lore.add(TextUtil.mm("<dark_gray>Категория: <gray>Боевая Сфера (Левая рука)"));
        lore.add(TextUtil.mm("<dark_gray>Редкость: " + rarity.colorTag() + rarity.displayName()));
        lore.add(Component.empty());
        for (String line : s.getStringList("lore")) {
            lore.add(TextUtil.mm(line));
        }
        lore.add(TextUtil.mm("<dark_gray>Множитель уровня: <#FBBC00>x" + tierMult));
        lore.add(TextUtil.mm("<dark_gray>Соедините 2 одинаковые сферы в наковальне для улучшения!"));
        meta.lore(lore);

        applyModelKey(meta, s.getString("item-model"));
        stampSignedPdc(meta, "SPHERE", id, tier, false);
        stack.setItemMeta(meta);
        return Optional.of(stack);
    }

    private Optional<ItemStack> buildTalisman(String id, int amount) {
        var cfg = configManager.get("talismans.yml");
        ConfigurationSection t = cfg.getConfigurationSection("talismans." + id);
        if (t == null) return Optional.empty();

        RarityType rarity = RarityType.fromString(t.getString("rarity", "LEGENDARY"));
        ItemStack stack = new ItemStack(Material.TOTEM_OF_UNDYING, Math.max(1, amount));
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(TextUtil.mm(t.getString("name", id)));
        List<Component> lore = new ArrayList<>();
        lore.add(TextUtil.mm("<dark_gray>Категория: <gray>Талисман ReallyWorld (Макс. 1 активный)"));
        lore.add(TextUtil.mm("<dark_gray>Редкость: " + rarity.colorTag() + rarity.displayName()));
        lore.add(Component.empty());
        for (String line : t.getStringList("lore")) {
            lore.add(TextUtil.mm(line));
        }
        lore.add(TextUtil.mm("<yellow>Возьмите в левую руку для активации эффекта!"));
        meta.lore(lore);
        applyModelKey(meta, t.getString("item-model"));
        stampSignedPdc(meta, "TALISMAN", id, "BASE", false);
        stack.setItemMeta(meta);
        return Optional.of(stack);
    }

    private Optional<ItemStack> buildArtifact(String id, int amount) {
        var cfg = configManager.get("artifacts.yml");
        ConfigurationSection a = cfg.getConfigurationSection("artifacts." + id);
        if (a == null) return Optional.empty();

        Material mat = Material.matchMaterial(a.getString("material", "BLAZE_ROD"));
        ItemStack stack = new ItemStack(mat == null ? Material.BLAZE_ROD : mat, Math.max(1, amount));
        ItemMeta meta = stack.getItemMeta();
        RarityType rarity = RarityType.fromString(a.getString("rarity", "EPIC"));
        meta.displayName(TextUtil.mm(a.getString("name", id)));
        List<Component> lore = new ArrayList<>();
        lore.add(TextUtil.mm("<dark_gray>Категория: <gray>Активный Артефакт (ПКМ)"));
        lore.add(TextUtil.mm("<dark_gray>Редкость: " + rarity.colorTag() + rarity.displayName()));
        lore.add(TextUtil.mm("<dark_gray>Перезарядка: <#FF8C00>" + a.getInt("cooldown-seconds", 30) + " сек."));
        lore.add(Component.empty());
        for (String line : a.getStringList("lore")) {
            lore.add(TextUtil.mm(line));
        }
        meta.lore(lore);
        applyModelKey(meta, a.getString("item-model"));
        stampSignedPdc(meta, "ARTIFACT", id, "BASE", false);
        stack.setItemMeta(meta);
        return Optional.of(stack);
    }

    private Optional<ItemStack> buildWeapon(String id, int amount) {
        var cfg = configManager.get("weapons.yml");
        ConfigurationSection w = cfg.getConfigurationSection("weapons." + id);
        if (w == null) return Optional.empty();

        Material mat = Material.matchMaterial(w.getString("material", "NETHERITE_SWORD"));
        ItemStack stack = new ItemStack(mat == null ? Material.NETHERITE_SWORD : mat, Math.max(1, amount));
        ItemMeta meta = stack.getItemMeta();
        RarityType rarity = RarityType.fromString(w.getString("rarity", "LEGENDARY"));
        meta.displayName(TextUtil.mm(w.getString("name", id)));

        List<Component> lore = new ArrayList<>();
        lore.add(TextUtil.mm("<dark_gray>Редкость: " + rarity.colorTag() + rarity.displayName()));
        for (String line : w.getStringList("lore")) {
            lore.add(TextUtil.mm(line));
        }
        meta.lore(lore);

        ConfigurationSection enchSec = w.getConfigurationSection("enchantments");
        if (enchSec != null) {
            for (String eKey : enchSec.getKeys(false)) {
                Enchantment ench = Registry.ENCHANTMENT.get(NamespacedKey.minecraft(eKey.toLowerCase(Locale.ROOT)));
                if (ench != null) {
                    meta.addEnchant(ench, enchSec.getInt(eKey, 1), true);
                }
            }
        }
        double bonusDmg = w.getDouble("bonus-attack-damage", 0.0);
        if (bonusDmg > 0.0) {
            meta.addAttributeModifier(Attribute.ATTACK_DAMAGE, new AttributeModifier(
                    new NamespacedKey("reallycore", "wpn_dmg_" + id),
                    bonusDmg,
                    AttributeModifier.Operation.ADD_NUMBER,
                    EquipmentSlotGroup.MAINHAND
            ));
        }
        applyModelKey(meta, w.getString("item-model"));
        stampSignedPdc(meta, "WEAPON", id, "BASE", false);
        stack.setItemMeta(meta);
        return Optional.of(stack);
    }

    private Optional<ItemStack> buildArmorPiece(String compositeId, int amount) {
        // compositeId format: "dragon:helmet" or "dragon_helmet"
        String setId;
        String pieceSlot;
        if (compositeId.contains(":")) {
            String[] parts = compositeId.split(":", 2);
            setId = parts[0].toUpperCase(Locale.ROOT);
            pieceSlot = parts[1].toUpperCase(Locale.ROOT);
        } else {
            int idx = compositeId.lastIndexOf('_');
            if (idx <= 0) return Optional.empty();
            setId = compositeId.substring(0, idx).toUpperCase(Locale.ROOT);
            pieceSlot = compositeId.substring(idx + 1).toUpperCase(Locale.ROOT);
        }

        var cfg = configManager.get("armor.yml");
        ConfigurationSection s = cfg.getConfigurationSection("sets." + setId);
        if (s == null) return Optional.empty();
        String baseMat = s.getString("base-material", "NETHERITE");
        Material mat = Material.matchMaterial(baseMat + "_" + pieceSlot);
        if (mat == null) return Optional.empty();

        ItemStack stack = new ItemStack(mat, Math.max(1, amount));
        ItemMeta meta = stack.getItemMeta();
        RarityType rarity = RarityType.fromString(s.getString("rarity", "EPIC"));
        String pieceRu = switch (pieceSlot) {
            case "HELMET" -> "Шлем";
            case "CHESTPLATE" -> "Нагрудник";
            case "LEGGINGS" -> "Поножи";
            default -> "Ботинки";
        };
        meta.displayName(TextUtil.mm(s.getString("display-prefix", setId) + " <gray>• <white>" + pieceRu));
        List<Component> lore = new ArrayList<>();
        lore.add(TextUtil.mm("<dark_gray>Комплект брони: " + s.getString("display-prefix", setId)));
        lore.add(TextUtil.mm("<dark_gray>Редкость: " + rarity.colorTag() + rarity.displayName()));
        lore.add(Component.empty());
        for (String line : s.getStringList("lore")) {
            lore.add(TextUtil.mm(line));
        }
        meta.lore(lore);

        ConfigurationSection enchSec = s.getConfigurationSection("enchantments");
        if (enchSec != null) {
            for (String eKey : enchSec.getKeys(false)) {
                Enchantment ench = Registry.ENCHANTMENT.get(NamespacedKey.minecraft(eKey.toLowerCase(Locale.ROOT)));
                if (ench != null) {
                    meta.addEnchant(ench, enchSec.getInt(eKey, 1), true);
                }
            }
        }
        stampSignedPdc(meta, "ARMOR", setId.toLowerCase(Locale.ROOT), pieceSlot, false);
        stack.setItemMeta(meta);
        return Optional.of(stack);
    }

    private Optional<ItemStack> buildConsumable(String id, int amount) {
        var cfg = configManager.get("consumables.yml");
        ConfigurationSection c = cfg.getConfigurationSection("consumables." + id);
        if (c == null) return Optional.empty();

        Material mat = Material.matchMaterial(c.getString("material", "NETHER_STAR"));
        ItemStack stack = new ItemStack(mat == null ? Material.NETHER_STAR : mat, Math.max(1, amount));
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(TextUtil.mm(c.getString("name", id)));
        List<Component> lore = new ArrayList<>();
        for (String line : c.getStringList("lore")) {
            lore.add(TextUtil.mm(line));
        }
        int cd = c.getInt("cooldown-seconds", 0);
        if (cd > 0) {
            lore.add(TextUtil.mm("<dark_gray>Перезарядка: <#FF8C00>" + cd + " сек."));
        }
        meta.lore(lore);

        if (meta instanceof PotionMeta pm && "CUSTOM_POTION".equalsIgnoreCase(c.getString("action"))) {
            String hex = c.getString("potion-color", "#FBBC00").replace("#", "");
            try {
                pm.setColor(Color.fromRGB(Integer.parseInt(hex, 16)));
            } catch (Exception ignored) {}
            for (String effSpec : c.getStringList("effects")) {
                String[] parts = effSpec.split(":");
                if (parts.length == 3) {
                    PotionEffectType pet = Registry.POTION_EFFECT_TYPE.get(NamespacedKey.minecraft(parts[0].toLowerCase(Locale.ROOT)));
                    if (pet != null) {
                        pm.addCustomEffect(new PotionEffect(pet, Integer.parseInt(parts[2]) * 20, Integer.parseInt(parts[1]), true, true, true), true);
                    }
                }
            }
        }
        meta.addItemFlags(ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
        applyModelKey(meta, c.getString("item-model"));
        stampSignedPdc(meta, "CONSUMABLE", id, "BASE", false);
        stack.setItemMeta(meta);
        return Optional.of(stack);
    }

    private void applyModelKey(ItemMeta meta, String rawModel) {
        if (rawModel == null || rawModel.isBlank()) return;
        try {
            NamespacedKey modelKey = NamespacedKey.fromString(rawModel);
            if (modelKey != null) {
                meta.setItemModel(modelKey);
            }
        } catch (Throwable ignored) {}
    }

    private void stampSignedPdc(ItemMeta meta, String category, String itemId, String tier, boolean soulbound) {
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        String sig = CryptoSignatureUtil.signItem(security.itemSalt(), category, itemId, tier);
        pdc.set(keyCategory, PersistentDataType.STRING, category);
        pdc.set(keyItemId, PersistentDataType.STRING, itemId);
        pdc.set(keyTier, PersistentDataType.STRING, tier);
        pdc.set(keySoulbound, PersistentDataType.BYTE, (byte) (soulbound ? 1 : 0));
        pdc.set(keyHmac, PersistentDataType.STRING, sig);
    }

    public Optional<VerifiedCustomItem> verifyCustomItem(ItemStack stack) {
        if (stack == null || stack.getType() == Material.AIR || !stack.hasItemMeta()) {
            return Optional.empty();
        }
        PersistentDataContainer pdc = stack.getItemMeta().getPersistentDataContainer();
        String cat = pdc.get(keyCategory, PersistentDataType.STRING);
        String id = pdc.get(keyItemId, PersistentDataType.STRING);
        String tier = pdc.get(keyTier, PersistentDataType.STRING);
        String sig = pdc.get(keyHmac, PersistentDataType.STRING);
        if (cat == null || id == null || sig == null) {
            return Optional.empty();
        }
        if (!CryptoSignatureUtil.verifyItem(security.itemSalt(), cat, id, tier, sig)) {
            return Optional.empty();
        }
        Byte sb = pdc.get(keySoulbound, PersistentDataType.BYTE);
        return Optional.of(new VerifiedCustomItem(cat, id, tier == null ? "BASE" : tier, sb != null && sb == 1));
    }

    public boolean isCustomItem(ItemStack stack) {
        return verifyCustomItem(stack).isPresent();
    }

    public boolean isSoulbound(ItemStack stack) {
        return verifyCustomItem(stack).map(VerifiedCustomItem::soulbound).orElse(false);
    }

    public Set<String> listItemIds(String category) {
        return switch (category.toUpperCase(Locale.ROOT)) {
            case "SPHERE" -> getSectionKeys("spheres.yml", "spheres");
            case "TALISMAN" -> getSectionKeys("talismans.yml", "talismans");
            case "ARTIFACT" -> getSectionKeys("artifacts.yml", "artifacts");
            case "WEAPON" -> getSectionKeys("weapons.yml", "weapons");
            case "ARMOR" -> getSectionKeys("armor.yml", "sets");
            case "CONSUMABLE" -> getSectionKeys("consumables.yml", "consumables");
            default -> Set.of();
        };
    }

    private Set<String> getSectionKeys(String file, String path) {
        ConfigurationSection sec = configManager.get(file).getConfigurationSection(path);
        return sec == null ? Set.of() : sec.getKeys(false);
    }

    // ---- PASSIVE EQUIPMENT ENGINE (Spheres, 1-Slot Talismans, Full-Set Armor) ----
    private void tickPassiveEquipment() {
        if (!enabled) return;
        for (Player p : Bukkit.getOnlinePlayers()) {
            ItemStack offhand = p.getInventory().getItemInOffHand();
            Optional<VerifiedCustomItem> offVer = verifyCustomItem(offhand);
            if (offVer.isPresent()) {
                VerifiedCustomItem item = offVer.get();
                if ("SPHERE".equals(item.category())) {
                    applySphereEffects(p, item.itemId());
                } else if ("TALISMAN".equals(item.category())) {
                    // Strictly 1 active talisman (off-hand only; inventory duplicates are ignored!)
                    applyTalismanEffects(p, item.itemId());
                }
            }
            applyFullArmorSetBonus(p);
        }
    }

    private void applySphereEffects(Player player, String sphereId) {
        List<String> effects = configManager.get("spheres.yml").getStringList("spheres." + sphereId + ".effects");
        applyShortPotionSpecs(player, effects);
    }

    private void applyTalismanEffects(Player player, String talismanId) {
        List<String> effects = configManager.get("talismans.yml").getStringList("talismans." + talismanId + ".effects");
        applyShortPotionSpecs(player, effects);
    }

    private void applyFullArmorSetBonus(Player player) {
        ItemStack[] armor = player.getInventory().getArmorContents();
        if (armor.length < 4) return;
        String activeSet = null;
        for (ItemStack piece : armor) {
            Optional<VerifiedCustomItem> ver = verifyCustomItem(piece);
            if (ver.isEmpty() || !"ARMOR".equals(ver.get().category())) {
                return;
            }
            if (activeSet == null) {
                activeSet = ver.get().itemId();
            } else if (!activeSet.equals(ver.get().itemId())) {
                return;
            }
        }
        if (activeSet != null) {
            List<String> bonus = configManager.get("armor.yml")
                    .getStringList("sets." + activeSet.toUpperCase(Locale.ROOT) + ".set-bonus.effects");
            applyShortPotionSpecs(player, bonus);
        }
    }

    private void applyShortPotionSpecs(Player player, List<String> specs) {
        for (String spec : specs) {
            String[] parts = spec.split(":");
            if (parts.length >= 2) {
                PotionEffectType type = Registry.POTION_EFFECT_TYPE.get(NamespacedKey.minecraft(parts[0].toLowerCase(Locale.ROOT)));
                if (type != null) {
                    int amp = Integer.parseInt(parts[1]);
                    player.addPotionEffect(new PotionEffect(type, 80, amp, true, false, true));
                }
            }
        }
    }

    // ---- ANVIL SPHERE UPGRADE (TIER_1 + TIER_1 -> TIER_2, etc.) ----
    @EventHandler(priority = EventPriority.HIGH)
    public void onAnvilSphereCombine(PrepareAnvilEvent event) {
        if (!enabled || !configManager.get("spheres.yml").getBoolean("settings.anvil-upgrade-enabled", true)) {
            return;
        }
        ItemStack left = event.getInventory().getFirstItem();
        ItemStack right = event.getInventory().getSecondItem();
        Optional<VerifiedCustomItem> v1 = verifyCustomItem(left);
        Optional<VerifiedCustomItem> v2 = verifyCustomItem(right);
        if (v1.isEmpty() || v2.isEmpty()) {
            // Prevent renaming signed custom items in anvils to stop confusion/scams
            if (v1.isPresent() || v2.isPresent()) {
                event.setResult(null);
            }
            return;
        }
        if (!"SPHERE".equals(v1.get().category()) || !"SPHERE".equals(v2.get().category())) {
            event.setResult(null);
            return;
        }
        if (!v1.get().itemId().equals(v2.get().itemId()) || !v1.get().tier().equals(v2.get().tier())) {
            event.setResult(null);
            return;
        }
        String nextTier = switch (v1.get().tier()) {
            case "TIER_1" -> "TIER_2";
            case "TIER_2" -> "TIER_3";
            case "TIER_3" -> "MAX";
            default -> null;
        };
        if (nextTier == null) {
            event.setResult(null);
            return;
        }
        buildSphere(v1.get().itemId(), nextTier, 1).ifPresent(upgraded -> {
            event.getInventory().setRepairCost(configManager.get("spheres.yml").getInt("settings.anvil-xp-levels-cost", 20));
            event.setResult(upgraded);
        });
    }

    // ---- CUSTOM WEAPON ON-HIT ABILITIES ----
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onCustomWeaponHit(EntityDamageByEntityEvent event) {
        if (!enabled || !(event.getDamager() instanceof Player attacker) || !(event.getEntity() instanceof LivingEntity victim)) {
            return;
        }
        Optional<VerifiedCustomItem> ver = verifyCustomItem(attacker.getInventory().getItemInMainHand());
        if (ver.isEmpty() || !"WEAPON".equals(ver.get().category())) return;

        var wSec = configManager.get("weapons.yml").getConfigurationSection("weapons." + ver.get().itemId());
        if (wSec == null) return;
        double chance = wSec.getDouble("proc-chance", 0.0);
        if (ThreadLocalRandom.current().nextDouble() > chance) return;

        String ability = wSec.getString("ability", "");
        switch (ability) {
            case "BLEED" -> victim.addPotionEffect(new PotionEffect(PotionEffectType.WITHER, 60, 1));
            case "ARMOR_SHRED" -> victim.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 80, 0));
            case "THUNDER_STRIKE" -> victim.getWorld().strikeLightningEffect(victim.getLocation());
            case "WITHER_STRIKE" -> {
                victim.addPotionEffect(new PotionEffect(PotionEffectType.WITHER, 80, 1));
                victim.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, 60, 0));
            }
            case "WIND_SLAM" -> victim.setVelocity(victim.getVelocity().add(new Vector(0, 0.55, 0)));
            default -> {}
        }
    }

    // ---- RIGHT-CLICK ARTIFACTS & RW CONSUMABLES ----
    @EventHandler(priority = EventPriority.HIGH)
    public void onRightClickCustomItem(PlayerInteractEvent event) {
        if (!enabled) return;
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        ItemStack stack = event.getItem();
        Optional<VerifiedCustomItem> verOpt = verifyCustomItem(stack);
        if (verOpt.isEmpty()) return;

        VerifiedCustomItem ver = verOpt.get();
        Player player = event.getPlayer();

        // Prevent placing Sphere PlayerHeads or Talisman Totems on blocks
        if ("SPHERE".equals(ver.category()) || "TALISMAN".equals(ver.category())) {
            if (event.getAction() == Action.RIGHT_CLICK_BLOCK) {
                event.setCancelled(true);
            }
            return;
        }

        if ("ARTIFACT".equals(ver.category())) {
            event.setCancelled(true);
            activateArtifact(player, ver.itemId());
        } else if ("CONSUMABLE".equals(ver.category())) {
            var cSec = configManager.get("consumables.yml").getConfigurationSection("consumables." + ver.itemId());
            if (cSec == null || "CUSTOM_POTION".equalsIgnoreCase(cSec.getString("action"))) {
                return; // Let vanilla drinkable potion consume normally
            }
            event.setCancelled(true);
            activateConsumable(player, stack, ver.itemId(), cSec);
        }
    }

    private void activateArtifact(Player player, String artifactId) {
        if (spawnWorldModule != null && spawnWorldModule.isProtectedSpawn(player.getLocation())) {
            locale.sendRaw(player, "<red>Боевые артефакты отключены на мирном Спавне!");
            return;
        }
        String cdKey = "artifact:" + artifactId;
        long rem = cooldowns.getRemainingSeconds(player.getUniqueId(), cdKey);
        if (rem > 0) {
            locale.send(player, "on-cooldown", Map.of("time", TextUtil.formatDuration(rem)));
            return;
        }
        var aSec = configManager.get("artifacts.yml").getConfigurationSection("artifacts." + artifactId);
        if (aSec == null) return;

        int cdSec = aSec.getInt("cooldown-seconds", 35);
        double radius = aSec.getDouble("radius", 8.0);
        String ability = aSec.getString("ability", "");

        switch (ability) {
            case "LIGHTNING_CHAIN" -> {
                double dmg = aSec.getDouble("damage", 8.0);
                for (Player target : getNearbyEnemies(player, radius)) {
                    target.getWorld().strikeLightningEffect(target.getLocation());
                    target.damage(dmg, player);
                }
            }
            case "DASH_FORWARD" -> {
                double boost = aSec.getDouble("boost-power", 1.85);
                Vector dir = player.getLocation().getDirection().normalize().multiply(boost).setY(0.45);
                player.setVelocity(dir);
                player.getWorld().playSound(player.getLocation(), Sound.ENTITY_WIND_CHARGE_WIND_BURST, 1f, 1.2f);
            }
            case "HEAL_PULSE" -> {
                double heal = aSec.getDouble("heal-amount", 10.0);
                double maxHp = Objects.requireNonNull(player.getAttribute(Attribute.MAX_HEALTH)).getValue();
                player.setHealth(Math.min(maxHp, player.getHealth() + heal));
                player.getWorld().spawnParticle(Particle.HEART, player.getLocation().add(0, 1.5, 0), 12, 0.5, 0.5, 0.5);
            }
            case "FROST_NOVA" -> {
                for (Player target : getNearbyEnemies(player, radius)) {
                    target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 80, 2));
                    target.setFreezeTicks(140);
                }
            }
            case "SHADOW_CLOAK" -> {
                int dur = aSec.getInt("duration-seconds", 6) * 20;
                player.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, dur, 0));
                player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, dur, 2));
            }
            case "GRAVITY_PULL" -> {
                for (Player target : getNearbyEnemies(player, radius)) {
                    Vector pull = player.getLocation().toVector().subtract(target.getLocation().toVector()).normalize().multiply(0.9).setY(0.25);
                    target.setVelocity(pull);
                }
            }
            case "BERSERK_RAGE" -> {
                int dur = aSec.getInt("duration-seconds", 8) * 20;
                player.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, dur, 1));
                player.addPotionEffect(new PotionEffect(PotionEffectType.HASTE, dur, 1));
            }
            case "IMMORTAL_AEGIS" -> {
                int dur = aSec.getInt("duration-seconds", 5) * 20;
                player.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, dur, 2));
                player.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION, dur, 3));
            }
            case "METEOR_STRIKE" -> {
                double dmg = aSec.getDouble("damage", 10.0);
                for (Player target : getNearbyEnemies(player, radius)) {
                    target.setFireTicks(100);
                    target.damage(dmg, player);
                    target.getWorld().spawnParticle(Particle.FLAME, target.getLocation().add(0, 1, 0), 30, 0.4, 0.5, 0.4, 0.05);
                }
            }
        }
        cooldowns.setCooldown(player.getUniqueId(), cdKey, cdSec, false);
        locale.sendRaw(player, "<#FBBC00>Активирован артефакт: " + aSec.getString("name", artifactId));
    }

    private void activateConsumable(Player player, ItemStack stack, String id, ConfigurationSection cSec) {
        String action = cSec.getString("action", "");
        if (!action.equals("GRANT_XP_LEVELS") && !action.equals("CLAN_BUFF")
                && spawnWorldModule != null && spawnWorldModule.isProtectedSpawn(player.getLocation())) {
            locale.sendRaw(player, "<red>Боевые расходники нельзя использовать на мирном Спавне!");
            return;
        }
        String cdKey = "consumable:" + id;
        long rem = cooldowns.getRemainingSeconds(player.getUniqueId(), cdKey);
        if (rem > 0) {
            locale.send(player, "on-cooldown", Map.of("time", TextUtil.formatDuration(rem)));
            return;
        }

        switch (action) {
            case "SPAWN_CAGE" -> {
                Material cageMat = Material.matchMaterial(cSec.getString("cage-material", "CRYING_OBSIDIAN"));
                spawnTemporaryCage(player.getLocation(), cageMat == null ? Material.CRYING_OBSIDIAN : cageMat,
                        cSec.getInt("duration-seconds", 15));
            }
            case "SPAWN_PLATFORM" -> {
                Material platMat = Material.matchMaterial(cSec.getString("platform-material", "OBSIDIAN"));
                spawnTemporaryPlatform(player.getLocation(), platMat == null ? Material.OBSIDIAN : platMat,
                        cSec.getInt("duration-seconds", 15));
            }
            case "DISORIENT_AOE" -> {
                double r = cSec.getDouble("radius", 10.0);
                for (Player enemy : getNearbyEnemies(player, r)) {
                    enemy.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 80, 0));
                    enemy.addPotionEffect(new PotionEffect(PotionEffectType.NAUSEA, 100, 0));
                    enemy.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 80, 1));
                    Location l = enemy.getLocation();
                    l.setYaw((l.getYaw() + 180f) % 360f);
                    enemy.teleport(l);
                }
            }
            case "REVEAL_INVISIBLE" -> {
                double r = cSec.getDouble("radius", 18.0);
                int dur = cSec.getInt("glow-seconds", 20) * 20;
                for (Player enemy : getNearbyEnemies(player, r)) {
                    enemy.removePotionEffect(PotionEffectType.INVISIBILITY);
                    enemy.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, dur, 0));
                }
                player.getWorld().spawnParticle(Particle.END_ROD, player.getLocation().add(0, 1, 0), 45, 2.0, 1.0, 2.0, 0.05);
            }
            case "CLEANSE_AND_SHIELD" -> {
                for (PotionEffect eff : player.getActivePotionEffects()) {
                    if (eff.getType().getEffectCategory() == PotionEffectType.Category.HARMFUL) {
                        player.removePotionEffect(eff.getType());
                    }
                }
                player.setFireTicks(0);
                int dur = cSec.getInt("shield-seconds", 4) * 20;
                player.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, dur, 1));
                player.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION, dur * 3, 1));
                player.getWorld().playSound(player.getLocation(), Sound.ITEM_TOTEM_USE, 0.8f, 1.3f);
            }
            case "FIRE_TORNADO" -> {
                double r = cSec.getDouble("radius", 9.0);
                int fireTicks = cSec.getInt("fire-seconds", 8) * 20;
                for (Player enemy : getNearbyEnemies(player, r)) {
                    enemy.setFireTicks(fireTicks);
                    enemy.setVelocity(new Vector(0, 0.85, 0));
                }
                player.getWorld().spawnParticle(Particle.FLAME, player.getLocation().add(0, 1, 0), 60, 2.0, 1.5, 2.0, 0.08);
            }
            case "STUN_AOE" -> {
                double r = cSec.getDouble("radius", 7.0);
                int stunTicks = (int) Math.round(cSec.getDouble("stun-seconds", 2.5) * 20);
                for (Player enemy : getNearbyEnemies(player, r)) {
                    enemy.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, stunTicks, 6));
                    enemy.addPotionEffect(new PotionEffect(PotionEffectType.MINING_FATIGUE, stunTicks, 2));
                }
            }
            case "GROUND_AOE" -> {
                double r = cSec.getDouble("radius", 16.0);
                for (Player enemy : getNearbyEnemies(player, r)) {
                    enemy.setFlying(false);
                    enemy.setGliding(false);
                    enemy.setVelocity(new Vector(0, -0.8, 0));
                }
            }
            case "GRANT_XP_LEVELS" -> {
                int lvls = cSec.getInt("xp-levels", 30);
                player.giveExpLevels(lvls);
                player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.1f);
            }
            case "CLAN_BUFF" -> {
                int dur = cSec.getInt("duration-seconds", 30) * 20;
                player.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, dur, 0));
                player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, dur, 1));
                if (clanModule != null) {
                    clanModule.getPlayerClan(player.getUniqueId()).ifPresent(clan -> {
                        for (Player mate : Bukkit.getOnlinePlayers()) {
                            if (mate.getWorld().equals(player.getWorld())
                                    && mate.getLocation().distanceSquared(player.getLocation()) <= 400
                                    && clanModule.getMember(clan, mate.getUniqueId()).isPresent()) {
                                mate.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, dur, 0));
                                mate.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, dur, 1));
                            }
                        }
                    });
                }
            }
            default -> { return; }
        }

        // Consume 1 item safely
        stack.setAmount(stack.getAmount() - 1);
        int cdSec = cSec.getInt("cooldown-seconds", 15);
        if (cdSec > 0) {
            cooldowns.setCooldown(player.getUniqueId(), cdKey, cdSec, false);
        }
        locale.sendRaw(player, "<#FBBC00>Использован расходник: " + cSec.getString("name", id));
    }

    private void spawnTemporaryCage(Location center, Material wallMat, int durationSeconds) {
        World w = center.getWorld();
        if (w == null) return;
        int cx = center.getBlockX();
        int cy = center.getBlockY();
        int cz = center.getBlockZ();
        List<Location> placed = new ArrayList<>();

        for (int dx = -2; dx <= 2; dx++) {
            for (int dy = -1; dy <= 3; dy++) {
                for (int dz = -2; dz <= 2; dz++) {
                    boolean border = Math.abs(dx) == 2 || Math.abs(dz) == 2 || dy == -1 || dy == 3;
                    if (!border) continue;
                    Block b = w.getBlockAt(cx + dx, cy + dy, cz + dz);
                    if (b.getType().isAir() || b.isReplaceable()) {
                        b.setType(wallMat, false);
                        Location bLoc = b.getLocation();
                        temporaryStructureBlocks.add(bLoc);
                        placed.add(bLoc);
                    }
                }
            }
        }
        w.playSound(center, Sound.BLOCK_RESPAWN_ANCHOR_SET_SPAWN, 1f, 0.9f);
        scheduleRollback(placed, durationSeconds);
    }

    private void spawnTemporaryPlatform(Location center, Material mat, int durationSeconds) {
        World w = center.getWorld();
        if (w == null) return;
        int cx = center.getBlockX();
        int cy = center.getBlockY() - 1;
        int cz = center.getBlockZ();
        List<Location> placed = new ArrayList<>();
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                Block b = w.getBlockAt(cx + dx, cy, cz + dz);
                if (b.getType().isAir() || b.isReplaceable() || b.isLiquid()) {
                    b.setType(mat, false);
                    Location bLoc = b.getLocation();
                    temporaryStructureBlocks.add(bLoc);
                    placed.add(bLoc);
                }
            }
        }
        w.playSound(center, Sound.BLOCK_ANVIL_LAND, 0.7f, 1.2f);
        scheduleRollback(placed, durationSeconds);
    }

    private void scheduleRollback(List<Location> blocks, int seconds) {
        if (plugin == null || blocks.isEmpty()) return;
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            for (Location loc : blocks) {
                temporaryStructureBlocks.remove(loc);
                if (loc.getWorld() != null) {
                    loc.getBlock().setType(Material.AIR, false);
                }
            }
        }, seconds * 20L);
    }

    // Prevent mining temporary Trapka / Plast blocks for infinite obsidian farming!
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onBreakTemporaryBlock(BlockBreakEvent event) {
        if (temporaryStructureBlocks.contains(event.getBlock().getLocation())) {
            event.setCancelled(true);
            locale.sendRaw(event.getPlayer(), "<red>Временные блоки Трапки/Пласта исчезнут автоматически!");
        }
    }

    private List<Player> getNearbyEnemies(Player source, double radius) {
        double rSq = radius * radius;
        List<Player> out = new ArrayList<>();
        for (Player p : source.getWorld().getPlayers()) {
            if (p.equals(source) || p.getGameMode() == GameMode.SPECTATOR || p.getGameMode() == GameMode.CREATIVE) continue;
            if (p.getLocation().distanceSquared(source.getLocation()) > rSq) continue;
            if (clanModule != null && !clanModule.canDamage(source.getUniqueId(), p.getUniqueId())) continue;
            out.add(p);
        }
        return out;
    }
}
