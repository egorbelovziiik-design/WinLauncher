package ru.reallyworld.ultimate.core.module;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import ru.reallyworld.ultimate.core.api.CurrencyType;
import ru.reallyworld.ultimate.core.api.ReallyModule;
import ru.reallyworld.ultimate.core.config.ConfigManager;
import ru.reallyworld.ultimate.core.storage.StorageModule;
import ru.reallyworld.ultimate.core.storage.model.AuctionListing;
import ru.reallyworld.ultimate.core.storage.repository.AuctionRepository;
import ru.reallyworld.ultimate.core.util.ItemSerializer;
import ru.reallyworld.ultimate.core.util.TextUtil;

import java.util.*;

/**
 * Crash-safe Player Auction House (/ah) Module.
 * Prevents item duplication via atomic hand-removal + SQL CAS transactions.
 */
public final class AuctionModule implements ReallyModule {
    private final JavaPlugin plugin;
    private final ConfigManager configManager;
    private final StorageModule storage;
    private final LocalizationModule locale;
    private final PlayerModule playerModule;
    private final CustomItemsModule customItemsModule;
    private final QuestsModule questsModule;

    private BukkitTask expiryTask;
    private volatile boolean enabled;

    public AuctionModule(
            JavaPlugin plugin,
            ConfigManager configManager,
            StorageModule storage,
            LocalizationModule locale,
            PlayerModule playerModule,
            CustomItemsModule customItemsModule,
            QuestsModule questsModule
    ) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.storage = storage;
        this.locale = locale;
        this.playerModule = playerModule;
        this.customItemsModule = customItemsModule;
        this.questsModule = questsModule;
    }

    @Override
    public String id() { return "auction"; }

    @Override
    public String displayName() { return "AuctionModule (/ah)"; }

    @Override
    public boolean isEnabled() { return enabled; }

    @Override
    public void enable() {
        this.enabled = configManager.main().getBoolean("modules.auction", true);
        if (plugin != null && Bukkit.getServer() != null) {
            long intervalTicks = Math.max(10L, configManager.get("auction.yml").getLong("expiration-check-interval-seconds", 60L)) * 20L;
            this.expiryTask = Bukkit.getScheduler().runTaskTimerAsynchronously(
                    plugin,
                    () -> storage.auction().expireOverdueListings(System.currentTimeMillis()),
                    intervalTicks, intervalTicks
            );
        }
    }

    @Override
    public void reload() {
        this.enabled = configManager.main().getBoolean("modules.auction", true);
    }

    @Override
    public void disable() {
        this.enabled = false;
        if (expiryTask != null) expiryTask.cancel();
    }

    public boolean sellFromMainHand(Player seller, long priceCoins) {
        var cfg = configManager.get("auction.yml");
        long minPrice = cfg.getLong("min-price-coins", 100L);
        long maxPrice = cfg.getLong("max-price-coins", 500_000_000L);
        if (priceCoins < minPrice || priceCoins > maxPrice) {
            locale.sendRaw(seller, "<red>Цена лота должна быть от <gold>" + TextUtil.formatAmount(minPrice)
                    + "</gold> до <gold>" + TextUtil.formatAmount(maxPrice) + "</gold> монет!");
            return false;
        }

        ItemStack inHand = seller.getInventory().getItemInMainHand();
        if (inHand == null || inHand.getType() == Material.AIR || inHand.getAmount() <= 0) {
            locale.sendRaw(seller, "<red>Возьмите предмет в основную руку перед продажей!");
            return false;
        }
        Set<String> blacklisted = new HashSet<>(cfg.getStringList("blacklisted-materials"));
        if (blacklisted.contains(inHand.getType().name())) {
            locale.sendRaw(seller, "<red>Этот предмет запрещено выставлять на аукцион!");
            return false;
        }
        if (customItemsModule != null && customItemsModule.isSoulbound(inHand)) {
            locale.sendRaw(seller, "<red>Привязанные предметы нельзя продавать на аукционе!");
            return false;
        }

        int maxSlots = playerModule.getRank(seller).auctionSlots();
        long feeCoins = cfg.getLong("listing-fee-coins", 25L);
        long expMillis = System.currentTimeMillis() + (cfg.getLong("listing-duration-hours", 48L) * 3_600_000L);

        ItemStack snapshot = inHand.clone();
        String serialized = ItemSerializer.serialize(snapshot);
        String displayName = (snapshot.hasItemMeta() && snapshot.getItemMeta().hasDisplayName())
                ? TextUtil.plain(snapshot.getItemMeta().displayName())
                : snapshot.getType().name();
        String category = classifyAuctionCategory(snapshot);

        // Anti-dupe: clear item from hand BEFORE committing SQL listing; restore immediately if SQL rejects
        seller.getInventory().setItemInMainHand(null);
        AuctionRepository.CreateOutcome outcome;
        try {
            outcome = storage.auction().createListing(
                    seller.getUniqueId(),
                    seller.getName(),
                    snapshot.getType().name(),
                    displayName,
                    category,
                    snapshot.getAmount(),
                    serialized,
                    priceCoins,
                    CurrencyType.COINS,
                    feeCoins,
                    expMillis,
                    maxSlots
            );
        } catch (Exception e) {
            seller.getInventory().setItemInMainHand(snapshot);
            locale.sendRaw(seller, "<red>Ошибка создания лота. Предмет возвращён в руку.");
            return false;
        }

        if (!outcome.success()) {
            seller.getInventory().setItemInMainHand(snapshot);
            if ("SLOTS_FULL".equals(outcome.reason())) {
                locale.send(seller, "auction.slots-full", Map.of("max", String.valueOf(maxSlots)));
            } else {
                locale.send(seller, "economy.not-enough-coins", Map.of("needed", TextUtil.formatAmount(feeCoins)));
            }
            return false;
        }

        playerModule.findProfile(seller.getUniqueId())
                .ifPresent(p -> p.setBalance(CurrencyType.COINS, outcome.sellerBalanceAfter()));
        if (questsModule != null) {
            questsModule.recordProgress(seller, "AUCTION_LISTINGS", 1L);
        }
        locale.send(seller, "auction.listed", Map.of(
                "item", displayName + " x" + snapshot.getAmount(),
                "price", TextUtil.formatAmount(priceCoins)
        ));
        return true;
    }

    public boolean buyListing(Player buyer, long listingId) {
        if (buyer.getInventory().firstEmpty() == -1) {
            locale.sendRaw(buyer, "<red>Освободите хотя бы 1 слот в инвентаре перед покупкой!");
            return false;
        }
        int taxPct = configManager.get("auction.yml").getInt("sale-tax-percent", 5);
        AuctionRepository.BuyOutcome outcome;
        try {
            outcome = storage.auction().buyListingAtomic(listingId, buyer.getUniqueId(), buyer.getName(), taxPct);
        } catch (AuctionRepository.InsufficientAuctionFundsException e) {
            locale.sendRaw(buyer, "<red>Недостаточно монет для покупки этого лота!");
            return false;
        }

        if (!outcome.success()) {
            switch (outcome.reason() == null ? "" : outcome.reason()) {
                case "SELF_BUY" -> locale.send(buyer, "auction.self-buy");
                case "EXPIRED" -> locale.sendRaw(buyer, "<red>Срок действия этого лота только что истёк!");
                default -> locale.send(buyer, "auction.already-sold");
            }
            return false;
        }

        AuctionListing sold = outcome.listing();
        playerModule.findProfile(buyer.getUniqueId())
                .ifPresent(p -> p.setBalance(sold.currency(), outcome.buyerBalanceAfter()));
        playerModule.findProfile(sold.sellerUuid())
                .ifPresent(p -> p.setBalance(sold.currency(), storage.economy().getBalance(sold.sellerUuid(), sold.currency())));

        ItemStack item = ItemSerializer.deserialize(sold.itemSerialized());
        Map<Integer, ItemStack> overflow = buyer.getInventory().addItem(item);
        overflow.values().forEach(rem -> buyer.getWorld().dropItemNaturally(buyer.getLocation(), rem));

        locale.send(buyer, "auction.bought", Map.of(
                "item", sold.itemDisplayName() + " x" + sold.itemAmount(),
                "seller", sold.sellerName(),
                "price", TextUtil.formatAmount(sold.price())
        ));
        Player onlineSeller = Bukkit.getPlayer(sold.sellerUuid());
        if (onlineSeller != null) {
            locale.send(onlineSeller, "auction.sold-notify", Map.of(
                    "buyer", buyer.getName(),
                    "item", sold.itemDisplayName() + " x" + sold.itemAmount(),
                    "net", TextUtil.formatAmount(outcome.sellerNetReceived())
            ));
        }
        return true;
    }

    public boolean cancelOrReturnListing(Player seller, long listingId) {
        if (seller.getInventory().firstEmpty() == -1) {
            locale.sendRaw(seller, "<red>Освободите хотя бы 1 слот в инвентаре для возврата предмета!");
            return false;
        }
        Optional<AuctionListing> returned = storage.auction().claimReturnAtomic(listingId, seller.getUniqueId());
        if (returned.isEmpty()) {
            locale.sendRaw(seller, "<red>Лот уже продан или забран!");
            return false;
        }
        ItemStack stack = ItemSerializer.deserialize(returned.get().itemSerialized());
        Map<Integer, ItemStack> overflow = seller.getInventory().addItem(stack);
        overflow.values().forEach(rem -> seller.getWorld().dropItemNaturally(seller.getLocation(), rem));
        locale.send(seller, "auction.returned");
        return true;
    }

    public List<AuctionListing> browse(String category, String search, String sortMode, int page, int pageSize) {
        int safePage = Math.max(0, page);
        return storage.auction().findActive(category, search, sortMode, pageSize, safePage * pageSize);
    }

    public List<AuctionListing> getSellerClaimableOrActive(UUID sellerUuid) {
        return storage.auction().findPlayerClaimableOrActive(sellerUuid);
    }

    public List<AuctionListing> getHistory(UUID playerUuid) {
        return storage.auction().findPlayerHistory(playerUuid, 36);
    }

    public String classifyAuctionCategory(ItemStack stack) {
        if (customItemsModule != null && customItemsModule.isCustomItem(stack)) {
            return "CUSTOM";
        }
        String m = stack.getType().name();
        if (m.endsWith("_SWORD") || m.endsWith("_AXE") || m.equals("BOW") || m.equals("CROSSBOW")
                || m.equals("TRIDENT") || m.equals("MACE")) {
            return "WEAPONS";
        }
        if (m.endsWith("_HELMET") || m.endsWith("_CHESTPLATE") || m.endsWith("_LEGGINGS")
                || m.endsWith("_BOOTS") || m.equals("SHIELD") || m.equals("ELYTRA")) {
            return "ARMOR";
        }
        if (m.contains("POTION") || m.contains("APPLE") || m.equals("TOTEM_OF_UNDYING") || m.equals("ENDER_PEARL")) {
            return "POTIONS";
        }
        if (stack.getType().isBlock()) {
            return "BLOCKS";
        }
        return "RESOURCES";
    }
}
