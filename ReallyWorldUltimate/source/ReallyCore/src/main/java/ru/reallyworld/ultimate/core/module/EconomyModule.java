package ru.reallyworld.ultimate.core.module;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import ru.reallyworld.ultimate.core.api.CurrencyType;
import ru.reallyworld.ultimate.core.api.ReallyModule;
import ru.reallyworld.ultimate.core.config.ConfigManager;
import ru.reallyworld.ultimate.core.storage.StorageModule;
import ru.reallyworld.ultimate.core.storage.model.PlayerProfile;
import ru.reallyworld.ultimate.core.storage.repository.EconomyRepository;
import ru.reallyworld.ultimate.core.util.TextUtil;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Multi-currency Economy, Balanced 7-Category /shop, Donate Rillik Shop,
 * /sell (hand/all), and Rotating Hot-Deal Buyer (/buyer).
 */
public final class EconomyModule implements ReallyModule {
    public record ShopCategory(String id, String title, Material icon, int slot, Map<String, ShopEntry> entries) {}
    public record ShopEntry(String id, String categoryId, Material material, int amount, long buyCoins, long sellCoins) {}
    public record RillikEntry(String id, String type, String targetId, int amount, long priceRilliks, String display, Material icon) {}
    public record HotDeal(Material material, int multiplier, long expiresAtMillis) {}

    private final JavaPlugin plugin;
    private final ConfigManager configManager;
    private final StorageModule storage;
    private final LocalizationModule locale;
    private final PlayerModule playerModule;

    private final Map<String, ShopCategory> categories = new LinkedHashMap<>();
    private final Map<Material, Long> unitSellPrices = new EnumMap<>(Material.class);
    private final Map<String, RillikEntry> rillikShop = new LinkedHashMap<>();
    private final Map<Material, HotDeal> activeHotDeals = new ConcurrentHashMap<>();

    private NamespacedKey customCategoryKey;
    private CustomItemsModule customItemsModule;
    private CasesModule casesModule;
    private QuestsModule questsModule;
    private BukkitTask buyerRotationTask;
    private volatile boolean enabled;

    public EconomyModule(
            JavaPlugin plugin,
            ConfigManager configManager,
            StorageModule storage,
            LocalizationModule locale,
            PlayerModule playerModule
    ) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.storage = storage;
        this.locale = locale;
        this.playerModule = playerModule;
        if (plugin != null) {
            this.customCategoryKey = new NamespacedKey(plugin, "rw_category");
        }
    }

    public void bindModules(CustomItemsModule customItemsModule, CasesModule casesModule, QuestsModule questsModule) {
        this.customItemsModule = customItemsModule;
        this.casesModule = casesModule;
        this.questsModule = questsModule;
    }

    @Override
    public String id() { return "economy"; }

    @Override
    public String displayName() { return "EconomyModule (Coins + Rilliks + Shop/Buyer)"; }

    @Override
    public boolean isEnabled() { return enabled; }

    @Override
    public void enable() {
        this.enabled = configManager.main().getBoolean("modules.economy", true);
        reloadShopCatalog();
        rotateHotDeals();
        if (plugin != null && Bukkit.getServer() != null) {
            long rotTicks = Math.max(60L, configManager.get("economy.yml").getLong("buyer.rotation-minutes", 30L) * 60L) * 20L;
            this.buyerRotationTask = Bukkit.getScheduler().runTaskTimer(plugin, this::rotateHotDeals, rotTicks, rotTicks);
        }
    }

    @Override
    public void reload() {
        reloadShopCatalog();
        rotateHotDeals();
    }

    @Override
    public void disable() {
        this.enabled = false;
        if (buyerRotationTask != null) buyerRotationTask.cancel();
    }

    private synchronized void reloadShopCatalog() {
        categories.clear();
        unitSellPrices.clear();
        rillikShop.clear();

        var shopCfg = configManager.get("shop.yml");
        ConfigurationSection catSec = shopCfg.getConfigurationSection("categories");
        if (catSec != null) {
            for (String catId : catSec.getKeys(false)) {
                ConfigurationSection c = catSec.getConfigurationSection(catId);
                Material icon = Material.matchMaterial(c.getString("icon", "CHEST"));
                Map<String, ShopEntry> entries = new LinkedHashMap<>();
                ConfigurationSection itemsSec = c.getConfigurationSection("items");
                if (itemsSec != null) {
                    for (String itemId : itemsSec.getKeys(false)) {
                        Material mat = Material.matchMaterial(itemId);
                        if (mat == null) continue;
                        int amt = Math.max(1, itemsSec.getInt(itemId + ".amount", 1));
                        long buy = itemsSec.getLong(itemId + ".buy-coins", 0L);
                        long sell = itemsSec.getLong(itemId + ".sell-coins", 0L);
                        entries.put(itemId, new ShopEntry(itemId, catId, mat, amt, buy, sell));
                        if (sell > 0) {
                            long unitSell = Math.max(1L, sell / amt);
                            unitSellPrices.put(mat, unitSell);
                        }
                    }
                }
                categories.put(catId, new ShopCategory(
                        catId,
                        c.getString("title", catId),
                        icon == null ? Material.CHEST : icon,
                        c.getInt("slot", 10),
                        Collections.unmodifiableMap(entries)
                ));
            }
        }

        ConfigurationSection rSec = shopCfg.getConfigurationSection("rillik-shop.items");
        if (rSec != null) {
            for (String key : rSec.getKeys(false)) {
                ConfigurationSection item = rSec.getConfigurationSection(key);
                Material icon = Material.matchMaterial(item.getString("icon", "NETHER_STAR"));
                rillikShop.put(key, new RillikEntry(
                        key,
                        item.getString("type", "CUSTOM"),
                        item.getString("id", ""),
                        Math.max(1, item.getInt("amount", 1)),
                        item.getLong("price-rilliks", 100L),
                        item.getString("display", key),
                        icon == null ? Material.NETHER_STAR : icon
                ));
            }
        }
    }

    public void rotateHotDeals() {
        activeHotDeals.clear();
        List<Material> candidates = new ArrayList<>(unitSellPrices.keySet());
        if (candidates.isEmpty()) return;
        Collections.shuffle(candidates, ThreadLocalRandom.current());
        int count = Math.min(candidates.size(), configManager.get("economy.yml").getInt("buyer.hot-deals-count", 4));
        List<Integer> mults = configManager.get("economy.yml").getIntegerList("buyer.hot-deal-multipliers");
        if (mults.isEmpty()) mults = List.of(2, 3);
        long exp = System.currentTimeMillis() + (configManager.get("economy.yml").getLong("buyer.rotation-minutes", 30L) * 60_000L);
        for (int i = 0; i < count; i++) {
            int mult = mults.get(ThreadLocalRandom.current().nextInt(mults.size()));
            activeHotDeals.put(candidates.get(i), new HotDeal(candidates.get(i), mult, exp));
        }
    }

    // ---- ATOMIC BALANCE OPERATIONS ----
    public long getBalance(UUID uuid, CurrencyType type) {
        return playerModule.findProfile(uuid)
                .map(p -> p.balance(type))
                .orElseGet(() -> Math.max(0L, storage.economy().getBalance(uuid, type)));
    }

    public EconomyRepository.EconomyResult deposit(UUID uuid, CurrencyType type, long amount, String txType, String details) {
        EconomyRepository.EconomyResult res = storage.economy().deposit(uuid, type, amount, txType, details);
        if (res.success()) {
            playerModule.findProfile(uuid).ifPresent(p -> p.setBalance(type, res.newBalance()));
        }
        return res;
    }

    public EconomyRepository.EconomyResult withdraw(UUID uuid, CurrencyType type, long amount, String txType, String details) {
        EconomyRepository.EconomyResult res = storage.economy().withdraw(uuid, type, amount, txType, details);
        if (res.success()) {
            playerModule.findProfile(uuid).ifPresent(p -> p.setBalance(type, res.newBalance()));
        }
        return res;
    }

    public EconomyRepository.EconomyResult setBalance(UUID uuid, CurrencyType type, long amount, String actor, String details) {
        EconomyRepository.EconomyResult res = storage.economy().setBalance(uuid, type, amount, actor, details);
        if (res.success()) {
            playerModule.findProfile(uuid).ifPresent(p -> p.setBalance(type, res.newBalance()));
        }
        return res;
    }

    public EconomyRepository.TransferResult pay(Player sender, UUID receiverUuid, String receiverName, long grossCoins) {
        long minAmt = configManager.get("economy.yml").getLong("transfer.min-amount", 50L);
        long maxAmt = configManager.get("economy.yml").getLong("transfer.max-amount", 50_000_000L);
        if (grossCoins < minAmt || grossCoins > maxAmt) {
            return new EconomyRepository.TransferResult(false, 0, 0, 0, "LIMIT_OUT_OF_RANGE");
        }
        int feePct = configManager.get("economy.yml").getInt("transfer.fee-percent", 3);
        EconomyRepository.TransferResult res = storage.economy().transfer(
                sender.getUniqueId(), receiverUuid, CurrencyType.COINS, grossCoins, feePct,
                "Player /pay to " + receiverName
        );
        if (res.success()) {
            playerModule.findProfile(sender.getUniqueId()).ifPresent(p -> p.setBalance(CurrencyType.COINS, res.senderBalance()));
            playerModule.findProfile(receiverUuid).ifPresent(p -> p.setBalance(CurrencyType.COINS, res.receiverBalance()));
        }
        return res;
    }

    // ---- SHOP & BUYER ----
    public Collection<ShopCategory> getShopCategories() {
        return categories.values();
    }

    public ShopCategory getShopCategory(String id) {
        return categories.get(id);
    }

    public Collection<RillikEntry> getRillikEntries() {
        return rillikShop.values();
    }

    public Map<Material, HotDeal> getActiveHotDeals() {
        return Collections.unmodifiableMap(activeHotDeals);
    }

    public long getEffectiveUnitSellPrice(Material material, boolean viaBuyerHotDeals) {
        Long base = unitSellPrices.get(material);
        if (base == null || base <= 0) return 0L;
        if (viaBuyerHotDeals) {
            HotDeal hd = activeHotDeals.get(material);
            if (hd != null && System.currentTimeMillis() < hd.expiresAtMillis()) {
                return base * hd.multiplier();
            }
        }
        return base;
    }

    public boolean buyFromCoinShop(Player player, ShopEntry entry, int multiplierBundles) {
        if (entry.buyCoins() <= 0) {
            locale.sendRaw(player, "<red>Этот предмет нельзя купить в магазине.");
            return false;
        }
        int bundles = Math.max(1, Math.min(multiplierBundles, 8));
        int totalItems = entry.amount() * bundles;
        long totalCost = Math.multiplyExact(entry.buyCoins(), bundles);

        EconomyRepository.EconomyResult res = withdraw(
                player.getUniqueId(), CurrencyType.COINS, totalCost,
                "SHOP_BUY", "Bought " + totalItems + "x " + entry.material().name()
        );
        if (!res.success()) {
            locale.send(player, "economy.not-enough-coins", Map.of("needed", TextUtil.formatAmount(totalCost)));
            return false;
        }
        ItemStack stack = new ItemStack(entry.material(), totalItems);
        Map<Integer, ItemStack> overflow = player.getInventory().addItem(stack);
        overflow.values().forEach(rem -> player.getWorld().dropItemNaturally(player.getLocation(), rem));
        locale.send(player, "economy.shop-bought", Map.of(
                "item", entry.material().name(),
                "amount", String.valueOf(totalItems),
                "price", TextUtil.formatAmount(totalCost),
                "currency", "монет"
        ));
        return true;
    }

    public boolean buyFromRillikShop(Player player, RillikEntry entry) {
        EconomyRepository.EconomyResult res = withdraw(
                player.getUniqueId(), CurrencyType.RILLIKS, entry.priceRilliks(),
                "RILLIK_SHOP_BUY", "Bought " + entry.id()
        );
        if (!res.success()) {
            locale.send(player, "economy.not-enough-rilliks", Map.of("needed", TextUtil.formatAmount(entry.priceRilliks())));
            return false;
        }

        if ("CASE_KEY".equalsIgnoreCase(entry.type()) && casesModule != null) {
            casesModule.giveKeys(player.getUniqueId(), entry.targetId(), entry.amount());
        } else if (customItemsModule != null) {
            Optional<ItemStack> built = customItemsModule.createCustomItem(entry.type(), entry.targetId(), entry.amount());
            if (built.isPresent()) {
                Map<Integer, ItemStack> overflow = player.getInventory().addItem(built.get());
                overflow.values().forEach(rem -> player.getWorld().dropItemNaturally(player.getLocation(), rem));
            }
        }
        locale.send(player, "economy.shop-bought", Map.of(
                "item", TextUtil.plain(TextUtil.mm(entry.display())),
                "amount", String.valueOf(entry.amount()),
                "price", TextUtil.formatAmount(entry.priceRilliks()),
                "currency", "рилликов"
        ));
        return true;
    }

    public boolean isSellableVanillaItem(ItemStack stack) {
        if (stack == null || stack.getType() == Material.AIR || stack.getAmount() <= 0) return false;
        if (customCategoryKey != null && stack.hasItemMeta()
                && stack.getItemMeta().getPersistentDataContainer().has(customCategoryKey, PersistentDataType.STRING)) {
            return false; // Protect custom spheres/talismans/weapons from accidental /sell all!
        }
        return unitSellPrices.containsKey(stack.getType());
    }

    public long sellHand(Player player, boolean useHotDeals) {
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (!isSellableVanillaItem(hand)) {
            locale.send(player, "economy.sell-nothing");
            return 0L;
        }
        int count = hand.getAmount();
        long unit = getEffectiveUnitSellPrice(hand.getType(), useHotDeals);
        long earned = Math.multiplyExact(unit, count);
        player.getInventory().setItemInMainHand(null);
        deposit(player.getUniqueId(), CurrencyType.COINS, earned, "SHOP_SELL_HAND", "Sold " + count + "x " + hand.getType());
        if (questsModule != null) questsModule.recordProgress(player, "BUYER_EARN_COINS", earned);
        locale.send(player, "economy.shop-sold", Map.of("amount", String.valueOf(count), "price", TextUtil.formatAmount(earned)));
        return earned;
    }

    public long sellAllInventory(Player player, Material filterMat, boolean useHotDeals) {
        int totalItems = 0;
        long totalEarned = 0L;
        ItemStack[] contents = player.getInventory().getStorageContents();
        for (int slot = 0; slot < contents.length; slot++) {
            ItemStack st = contents[slot];
            if (!isSellableVanillaItem(st)) continue;
            if (filterMat != null && st.getType() != filterMat) continue;
            long unit = getEffectiveUnitSellPrice(st.getType(), useHotDeals);
            if (unit <= 0) continue;
            totalItems += st.getAmount();
            totalEarned = Math.addExact(totalEarned, Math.multiplyExact(unit, st.getAmount()));
            player.getInventory().setItem(slot, null);
        }
        if (totalItems <= 0 || totalEarned <= 0) {
            locale.send(player, "economy.sell-nothing");
            return 0L;
        }
        deposit(player.getUniqueId(), CurrencyType.COINS, totalEarned, "SHOP_SELL_ALL", "Sold " + totalItems + " items");
        if (questsModule != null) questsModule.recordProgress(player, "BUYER_EARN_COINS", totalEarned);
        locale.send(player, "economy.shop-sold", Map.of(
                "amount", String.valueOf(totalItems),
                "price", TextUtil.formatAmount(totalEarned)
        ));
        return totalEarned;
    }
}
