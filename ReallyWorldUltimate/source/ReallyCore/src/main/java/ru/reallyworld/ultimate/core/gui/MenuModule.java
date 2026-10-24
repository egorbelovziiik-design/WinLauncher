package ru.reallyworld.ultimate.core.gui;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import ru.reallyworld.ultimate.core.api.CurrencyType;
import ru.reallyworld.ultimate.core.api.ReallyModule;
import ru.reallyworld.ultimate.core.config.ConfigManager;
import ru.reallyworld.ultimate.core.config.RankDefinition;
import ru.reallyworld.ultimate.core.module.*;
import ru.reallyworld.ultimate.core.storage.model.*;
import ru.reallyworld.ultimate.core.storage.repository.PlayerRepository.LeaderboardEntry;
import ru.reallyworld.ultimate.core.storage.repository.ProgressionRepository;
import ru.reallyworld.ultimate.core.util.ItemSerializer;
import ru.reallyworld.ultimate.core.util.TextUtil;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Interactive Russian GUI Menu Engine (18 Menus, 0 decorative dead buttons).
 * Hardened against all inventory click/swap/hotkey/drag duplication exploits.
 */
public final class MenuModule implements ReallyModule, Listener {
    private static final List<String> AUCTION_CATEGORIES = List.of("ALL", "CUSTOM", "WEAPONS", "ARMOR", "POTIONS", "BLOCKS", "RESOURCES");
    private static final List<String> AUCTION_SORTS = List.of("NEWEST", "CHEAPEST", "EXPENSIVE", "EXPIRING");

    private final JavaPlugin plugin;
    private final ConfigManager configManager;
    private final LocalizationModule locale;
    private final CooldownModule cooldowns;
    private final PlayerModule playerModule;
    private final EconomyModule economyModule;
    private final AuctionModule auctionModule;
    private final ClanModule clanModule;
    private final CasesModule casesModule;
    private final KitsModule kitsModule;
    private final QuestsModule questsModule;
    private final BattlePassModule battlePassModule;
    private final RewardsModule rewardsModule;
    private final StatisticsModule statisticsModule;
    private final EventsModule eventsModule;

    private volatile boolean enabled;

    public MenuModule(
            JavaPlugin plugin,
            ConfigManager configManager,
            LocalizationModule locale,
            CooldownModule cooldowns,
            PlayerModule playerModule,
            EconomyModule economyModule,
            AuctionModule auctionModule,
            ClanModule clanModule,
            CasesModule casesModule,
            KitsModule kitsModule,
            QuestsModule questsModule,
            BattlePassModule battlePassModule,
            RewardsModule rewardsModule,
            StatisticsModule statisticsModule,
            EventsModule eventsModule
    ) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.locale = locale;
        this.cooldowns = cooldowns;
        this.playerModule = playerModule;
        this.economyModule = economyModule;
        this.auctionModule = auctionModule;
        this.clanModule = clanModule;
        this.casesModule = casesModule;
        this.kitsModule = kitsModule;
        this.questsModule = questsModule;
        this.battlePassModule = battlePassModule;
        this.rewardsModule = rewardsModule;
        this.statisticsModule = statisticsModule;
        this.eventsModule = eventsModule;
    }

    @Override
    public String id() { return "menus"; }

    @Override
    public String displayName() { return "MenuModule (18 Interactive Russian GUIs)"; }

    @Override
    public boolean isEnabled() { return enabled; }

    @Override
    public void enable() {
        this.enabled = configManager.main().getBoolean("modules.menus", true);
        if (plugin != null && Bukkit.getServer() != null) {
            Bukkit.getPluginManager().registerEvents(this, plugin);
        }
    }

    @Override
    public void reload() {
        this.enabled = configManager.main().getBoolean("modules.menus", true);
    }

    @Override
    public void disable() {
        this.enabled = false;
        if (Bukkit.getServer() != null) {
            for (Player online : Bukkit.getOnlinePlayers()) {
                if (online.getOpenInventory().getTopInventory().getHolder() instanceof ReallyGuiHolder) {
                    online.closeInventory();
                }
            }
        }
    }

    // ---- ANTI-STEAL / ANTI-DUPE EVENT ROUTER ----
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof ReallyGuiHolder holder)) return;
        event.setCancelled(true); // Block ALL vanilla item movements when a ReallyCore GUI is open

        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (event.getClickedInventory() == null || !event.getClickedInventory().equals(event.getInventory())) {
            return; // Ignore clicks in bottom player inventory
        }
        // Block hotkey swaps / double-click collect
        if (event.getClick() == ClickType.DOUBLE_CLICK || event.getClick() == ClickType.SWAP_OFFHAND
                || event.getClick() == ClickType.NUMBER_KEY) {
            return;
        }
        var handler = holder.handlerFor(event.getSlot());
        if (handler != null) {
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.55f, 1.2f);
            handler.accept(event);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof ReallyGuiHolder) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onInventoryClose(InventoryCloseEvent event) {
        if (event.getInventory().getHolder() instanceof ReallyGuiHolder holder && holder.closeCallback() != null) {
            holder.closeCallback().run();
        }
    }

    public void openNamedMenu(Player player, String name) {
        switch (name.toUpperCase(Locale.ROOT)) {
            case "PROFILE" -> openProfile(player);
            case "SHOP" -> openShopHome(player);
            case "BUYER" -> openBuyer(player);
            case "AUCTION" -> openAuction(player, "ALL", null, "NEWEST", 0);
            case "CLAN", "CLANS" -> openClan(player);
            case "CASES" -> openCases(player);
            case "DONATE" -> openDonate(player);
            case "KITS" -> openKits(player);
            case "REWARDS" -> openRewards(player);
            case "QUESTS" -> openQuests(player);
            case "EVENTS" -> openEvents(player);
            case "STATS" -> openStats(player);
            case "TOP" -> openTop(player, "coins");
            case "BATTLEPASS", "BP" -> openBattlePass(player, 0);
            case "SETTINGS" -> openSettings(player);
            case "WARPS" -> openWarps(player);
            default -> openMainMenu(player);
        }
    }

    // =========================================================================
    // 1. MAIN MENU (/menu)
    // =========================================================================
    public void openMainMenu(Player player) {
        PlayerProfile p = playerModule.getProfile(player);
        RankDefinition rank = playerModule.getRank(player);
        Inventory inv = createFrame("MAIN", 54, title("main"));
        ReallyGuiHolder h = (ReallyGuiHolder) inv.getHolder();

        setButton(inv, h, 4, Material.PLAYER_HEAD, "<bold><#FBBC00>Профиль • " + player.getName(), List.of(
                "<gray>Привилегия: " + rank.displayName(),
                "<gray>Монеты: <#FBBC00>" + TextUtil.formatAmount(p.coins()) + " ⛃",
                "<gray>Риллики: <#FF6A00>" + TextUtil.formatAmount(p.rilliks()) + " ❖",
                "<gray>Убийств / Смертей: <white>" + p.kills() + " / " + p.deaths() + " <gray>(K/D " + p.kdRatio() + ")",
                "",
                "<yellow>Нажмите, чтобы открыть профиль!"
        ), e -> openProfile(player));

        setButton(inv, h, 19, Material.EMERALD, "<bold><#55FF55>Магазин Сервера", List.of(
                "<gray>7 категорий блоков, брони, зелий и еды",
                "<gray>Честная экономика без дюпов",
                "", "<yellow>Нажмите для открытия (/shop)"
        ), e -> openShopHome(player));

        setButton(inv, h, 20, Material.GOLD_INGOT, "<bold><#FBBC00>Скупщик Ресурсов", List.of(
                "<gray>Продавайте добытые ресурсы за монеты!",
                "<gray>Горячие предложения <#FF4500>x2 и x3</#FF4500> каждые 30 мин.",
                "", "<yellow>Нажмите для открытия (/buyer)"
        ), e -> openBuyer(player));

        setButton(inv, h, 21, Material.GOLDEN_CARROT, "<bold><#FF8C00>Аукцион Игроков", List.of(
                "<gray>Торгуйте редкими сферами, талисманами и бронёй",
                "<gray>Поиск, фильтры и безопасные сделки",
                "", "<yellow>Нажмите для открытия (/ah)"
        ), e -> openAuction(player, "ALL", null, "NEWEST", 0));

        setButton(inv, h, 22, Material.ENDER_CHEST, "<bold><#FF4500>Кейсы ReallyWorld", List.of(
                "<gray>8 уникальных кейсов: Донат, Сферы, Талисманы,",
                "<gray>Префиксы, Риллики, Монеты и Киты!",
                "", "<yellow>Нажмите для открытия (/cases)"
        ), e -> openCases(player));

        setButton(inv, h, 23, Material.NETHER_STAR, "<bold><#FBBC00>Донат и Магазин Рилликов", List.of(
                "<gray>Привилегии от <green>HERO</green> до <#FF2222>GOD</#FF2222>",
                "<gray>Боевые Сферы, Талисманы, Трапки и Артефакты",
                "", "<yellow>Нажмите для открытия (/donate)"
        ), e -> openDonate(player));

        setButton(inv, h, 24, Material.DIAMOND_CHESTPLATE, "<bold><#55FFFF>Наборы (/kits)", List.of(
                "<gray>Стартовый набор, наборы привилегий и PLUS",
                "", "<yellow>Нажмите для выбора кита!"
        ), e -> openKits(player));

        setButton(inv, h, 25, Material.NETHERITE_SWORD, "<bold><#FF6A00>Кланы (/clan)", List.of(
                "<gray>Создайте клан, собирайте союзников,",
                "<gray>захватывайте Талисманы и выходите в ТОП!",
                "", "<yellow>Нажмите для управления кланом!"
        ), e -> openClan(player));

        setButton(inv, h, 28, Material.COMPASS, "<bold><#55FF55>Рандомная Телепортация", List.of(
                "<gray>Мгновенный поиск безопасной локации для выживания",
                "<gray>ЛКМ — Обычный RTP • ПКМ — Дальний RTP (/rtpfar)",
                "", "<yellow>Нажмите для телепортации!"
        ), e -> {
            player.closeInventory();
            if (e.isRightClick() && player.hasPermission("reallycore.rtpfar")) {
                playerModule.randomTeleport(player, true);
            } else {
                playerModule.randomTeleport(player, false);
            }
        });

        setButton(inv, h, 29, Material.ENDER_PEARL, "<bold><#55FFFF>Варпы Сервера (/warps)", List.of(
                "<gray>Спавн, Колизей PvP, Зал Кейсов, Автошахта",
                "", "<yellow>Нажмите для выбора локации!"
        ), e -> openWarps(player));

        setButton(inv, h, 30, Material.WRITABLE_BOOK, "<bold><#FBBC00>Квесты и Задания", List.of(
                "<gray>Ежедневные, еженедельные и боевые испытания",
                "", "<yellow>Нажмите для открытия (/quests)"
        ), e -> openQuests(player));

        setButton(inv, h, 31, Material.DRAGON_EGG, "<bold><#FF4500>Ивенты и Боссы", List.of(
                "<gray>Статус сейчас: <white>" + eventsModule.getStatusSummary(),
                "<gray>Аирдроп, Метеорит, Захват Талисмана и 5 Боссов",
                "", "<yellow>Нажмите для расписания (/events)"
        ), e -> openEvents(player));

        setButton(inv, h, 32, Material.EXPERIENCE_BOTTLE, "<bold><#FFD700>Боевой Пропуск", List.of(
                "<gray>Сезон 1: «Эра Дракона» — 30 уровней наград",
                "", "<yellow>Нажмите для открытия (/bp)"
        ), e -> openBattlePass(player, 0));

        setButton(inv, h, 33, Material.CHEST_MINECART, "<bold><#55FF55>Ежедневные Награды", List.of(
                "<gray>7-дневная серия входов, бонусы за онлайн и достижения",
                "", "<yellow>Нажмите для получения (/rewards)"
        ), e -> openRewards(player));

        setButton(inv, h, 34, Material.BEACON, "<bold><#FBBC00>Топы и Рейтинги (/top)", List.of(
                "<gray>Лучшие богачи, убийцы, кланы и охотники на боссов",
                "", "<yellow>Нажмите для просмотра!"
        ), e -> openTop(player, "coins"));

        setButton(inv, h, 48, Material.BOOK, "<bold><white>Подробная Статистика", List.of(
                "<gray>Все показатели вашего персонажа",
                "", "<yellow>Нажмите (/stats)"
        ), e -> openStats(player));

        setButton(inv, h, 50, Material.COMPARATOR, "<bold><white>Настройки Игрока", List.of(
                "<gray>Управление Скорбордом, запросами TPA и ЛС",
                "", "<yellow>Нажмите для настройки!"
        ), e -> openSettings(player));

        addCloseButton(inv, h, 49, player);
        player.openInventory(inv);
    }

    // =========================================================================
    // 2. PROFILE (/profile)
    // =========================================================================
    public void openProfile(Player player) {
        PlayerProfile p = playerModule.getProfile(player);
        RankDefinition rank = playerModule.getRank(player);
        Inventory inv = createFrame("PROFILE", 54, title("profile"));
        ReallyGuiHolder h = (ReallyGuiHolder) inv.getHolder();

        String clanStr = clanModule.getPlayerClan(player.getUniqueId()).map(c -> "[" + c.tag() + "] " + c.name()).orElse("Не состоит");
        setButton(inv, h, 13, Material.PLAYER_HEAD, "<bold><#FBBC00>" + player.getName(), List.of(
                "<gray>Привилегия: " + rank.displayName(),
                "<gray>Титул: " + (p.customPrefix() != null && !p.customPrefix().isBlank() ? p.customPrefix() : "<dark_gray>Не выбран"),
                "<gray>Клан: <white>" + clanStr,
                "<gray>Монеты: <#FBBC00>" + TextUtil.formatAmount(p.coins()) + " ⛃",
                "<gray>Риллики: <#FF6A00>" + TextUtil.formatAmount(p.rilliks()) + " ❖",
                "<gray>Множитель монет за киллы: <#55FF55>x" + (int) rank.killCoinMultiplier(),
                "<gray>Время в игре: <white>" + TextUtil.formatDuration(p.playtimeSeconds())
        ), null);

        List<HomePoint> homes = playerModule.getHomes(player.getUniqueId());
        int slot = 28;
        for (HomePoint hp : homes) {
            if (slot > 34) break;
            setButton(inv, h, slot++, Material.RED_BED, "<#FBBC00>Точка дома: <white>" + hp.name(), List.of(
                    "<gray>Мир: <white>" + hp.worldName(),
                    "<gray>Координаты: <white>" + (int) hp.x() + ", " + (int) hp.y() + ", " + (int) hp.z(),
                    "",
                    "<yellow>ЛКМ — Телепортироваться • ПКМ — Удалить дом"
            ), e -> {
                if (e.isRightClick()) {
                    playerModule.deleteHome(player, hp.name());
                    openProfile(player);
                } else {
                    player.closeInventory();
                    var w = Bukkit.getWorld(hp.worldName());
                    if (w != null) {
                        playerModule.teleportWithWarmup(player, new org.bukkit.Location(w, hp.x(), hp.y(), hp.z(), hp.yaw(), hp.pitch()), null);
                    }
                }
            });
        }
        if (homes.isEmpty()) {
            setButton(inv, h, 31, Material.WHITE_BED, "<gray>У вас пока нет точек дома", List.of(
                    "<gray>Лимит вашего ранга: <#FBBC00>" + rank.maxHomes(),
                    "<yellow>Используйте /sethome <название>"
            ), null);
        }

        addBackAndClose(inv, h, player);
        player.openInventory(inv);
    }

    // =========================================================================
    // 3. SHOP (/shop) & CATEGORY VIEW
    // =========================================================================
    public void openShopHome(Player player) {
        Inventory inv = createFrame("SHOP", 54, title("shop"));
        ReallyGuiHolder h = (ReallyGuiHolder) inv.getHolder();

        for (EconomyModule.ShopCategory cat : economyModule.getShopCategories()) {
            setButton(inv, h, cat.slot(), cat.icon(), cat.title(), List.of(
                    "<gray>Товаров в разделе: <white>" + cat.entries().size(),
                    "", "<yellow>Нажмите для просмотра товаров!"
            ), e -> openShopCategory(player, cat.id()));
        }

        setButton(inv, h, 31, Material.NETHER_STAR, "<bold><#FF4500>Магазин за Риллики ❖", List.of(
                "<gray>Боевые Сферы, Талисманы, Артефакты,",
                "<gray>Трапки, Божья Аура и Ключи от кейсов!",
                "", "<yellow>Нажмите для открытия!"
        ), e -> openDonate(player));

        setButton(inv, h, 33, Material.GOLD_INGOT, "<bold><#FBBC00>Скупщик (/buyer)", List.of(
                "<gray>Горячие предложения x2/x3 на ресурсы!",
                "", "<yellow>Перейти к Скупщику"
        ), e -> openBuyer(player));

        addBackAndClose(inv, h, player);
        player.openInventory(inv);
    }

    public void openShopCategory(Player player, String categoryId) {
        EconomyModule.ShopCategory cat = economyModule.getShopCategory(categoryId);
        if (cat == null) {
            openShopHome(player);
            return;
        }
        Inventory inv = createFrame("SHOP_CAT", 54, cat.title());
        ReallyGuiHolder h = (ReallyGuiHolder) inv.getHolder();

        int[] grid = contentSlots();
        int idx = 0;
        for (EconomyModule.ShopEntry entry : cat.entries().values()) {
            if (idx >= grid.length) break;
            List<String> lore = new ArrayList<>();
            lore.add("<gray>Количество за 1 покупку: <white>x" + entry.amount());
            if (entry.buyCoins() > 0) {
                lore.add("<gray>Купить: <#FBBC00>" + TextUtil.formatAmount(entry.buyCoins()) + " монет");
            }
            if (entry.sellCoins() > 0) {
                lore.add("<gray>Продать (x" + entry.amount() + "): <#55FF55>" + TextUtil.formatAmount(entry.sellCoins()) + " монет");
            }
            lore.add("");
            if (entry.buyCoins() > 0) lore.add("<yellow>ЛКМ — Купить x" + entry.amount() + " • Shift+ЛКМ — Купить x" + (entry.amount() * 4));
            if (entry.sellCoins() > 0) lore.add("<gold>ПКМ — Продать все такие предметы из инвентаря");

            ItemStack icon = new ItemStack(entry.material(), Math.min(64, entry.amount()));
            setItemButton(inv, h, grid[idx++], icon, "<white>" + entry.material().name(), lore, e -> {
                if (e.isLeftClick() && entry.buyCoins() > 0) {
                    economyModule.buyFromCoinShop(player, entry, e.isShiftClick() ? 4 : 1);
                } else if (e.isRightClick() && entry.sellCoins() > 0) {
                    economyModule.sellAllInventory(player, entry.material(), false);
                }
            });
        }

        setButton(inv, h, 45, Material.ARROW, "<yellow>← К категориям магазина", List.of(), e -> openShopHome(player));
        addCloseButton(inv, h, 49, player);
        player.openInventory(inv);
    }

    // =========================================================================
    // 4. BUYER (/buyer) — Скупщик с x2/x3 горячими сделками
    // =========================================================================
    public void openBuyer(Player player) {
        Inventory inv = createFrame("BUYER", 54, title("buyer"));
        ReallyGuiHolder h = (ReallyGuiHolder) inv.getHolder();

        int slot = 19;
        for (EconomyModule.HotDeal hd : economyModule.getActiveHotDeals().values()) {
            if (slot > 25) break;
            long baseUnit = economyModule.getEffectiveUnitSellPrice(hd.material(), false);
            long boostedUnit = economyModule.getEffectiveUnitSellPrice(hd.material(), true);
            long remSec = Math.max(0L, (hd.expiresAtMillis() - System.currentTimeMillis()) / 1000L);
            setButton(inv, h, slot++, hd.material(), "<bold><#FF4500>ГОРЯЧИЙ СПРОС x" + hd.multiplier() + " • <white>" + hd.material().name(), List.of(
                    "<gray>Обычная цена: <dark_gray>" + baseUnit + " ⛃/шт.",
                    "<gray>Цена сейчас: <#55FF55>" + boostedUnit + " ⛃/шт. <gold>(x" + hd.multiplier() + "!)",
                    "<gray>До смены товара: <white>" + TextUtil.formatDuration(remSec),
                    "",
                    "<yellow>Нажмите, чтобы продать весь этот ресурс из инвентаря!"
            ), e -> {
                economyModule.sellAllInventory(player, hd.material(), true);
                openBuyer(player);
            });
        }

        setButton(inv, h, 30, Material.HOPPER, "<bold><#FBBC00>Продать предмет в руке", List.of(
                "<gray>Продаёт предмет из основной руки с учётом бонуса x2/x3",
                "", "<yellow>Нажмите для продажи!"
        ), e -> {
            economyModule.sellHand(player, true);
            openBuyer(player);
        });

        setButton(inv, h, 32, Material.CHEST, "<bold><#55FF55>Продать ВСЕ ресурсы из инвентаря", List.of(
                "<gray>Безопасно продаёт все ванильные ресурсы,",
                "<gray>не затрагивая кастомные сферы, броню и оружие!",
                "", "<yellow>Нажмите для мгновенной сдачи!"
        ), e -> {
            economyModule.sellAllInventory(player, null, true);
            openBuyer(player);
        });

        addBackAndClose(inv, h, player);
        player.openInventory(inv);
    }

    // =========================================================================
    // 5. AUCTION HOUSE (/ah) — Active, My Listings/Expired, History
    // =========================================================================
    public void openAuction(Player player, String category, String search, String sortMode, int page) {
        Inventory inv = createFrame("AUCTION", 54, title("auction"));
        ReallyGuiHolder h = (ReallyGuiHolder) inv.getHolder();

        int[] grid = contentSlots();
        List<AuctionListing> listings = auctionModule.browse(category, search, sortMode, page, grid.length);
        long now = System.currentTimeMillis();

        for (int i = 0; i < Math.min(listings.size(), grid.length); i++) {
            AuctionListing l = listings.get(i);
            ItemStack display;
            try {
                display = ItemSerializer.deserialize(l.itemSerialized()).clone();
            } catch (Exception ex) {
                display = new ItemStack(Material.BARRIER);
            }
            ItemMeta meta = display.getItemMeta();
            List<Component> lore = meta.hasLore() ? new ArrayList<>(Objects.requireNonNull(meta.lore())) : new ArrayList<>();
            lore.add(Component.empty());
            lore.add(TextUtil.mm("<dark_gray>────────────────────"));
            lore.add(TextUtil.mm("<gray>Продавец: <white>" + l.sellerName()));
            lore.add(TextUtil.mm("<gray>Цена: <#FBBC00>" + TextUtil.formatAmount(l.price()) + " монет"));
            lore.add(TextUtil.mm("<gray>Истекает через: <white>" + TextUtil.formatDuration((l.expiresAt() - now) / 1000L)));
            lore.add(TextUtil.mm(l.sellerUuid().equals(player.getUniqueId())
                    ? "<yellow>Ваш лот — нажмите, чтобы снять с продажи!"
                    : "<#55FF55>Нажмите, чтобы безопасно купить!"));
            meta.lore(lore);
            display.setItemMeta(meta);

            final int slotPos = grid[i];
            inv.setItem(slotPos, display);
            h.onClick(slotPos, e -> {
                if (l.sellerUuid().equals(player.getUniqueId())) {
                    auctionModule.cancelOrReturnListing(player, l.id());
                } else {
                    auctionModule.buyListing(player, l.id());
                }
                openAuction(player, category, search, sortMode, page);
            });
        }

        // Controls row
        setButton(inv, h, 45, Material.ARROW, "<yellow>← В меню", List.of(), e -> openMainMenu(player));
        if (page > 0) {
            setButton(inv, h, 46, Material.SPECTRAL_ARROW, "<yellow>« Предыдущая страница", List.of(),
                    e -> openAuction(player, category, search, sortMode, page - 1));
        }
        setButton(inv, h, 47, Material.CHEST, "<#FBBC00>Мои лоты и возврат", List.of(
                "<gray>Отмена активных и возврат истёкших предметов",
                "", "<yellow>Нажмите для открытия!"
        ), e -> openAuctionMyItems(player));

        int nextCatIdx = (AUCTION_CATEGORIES.indexOf(category) + 1) % AUCTION_CATEGORIES.size();
        setButton(inv, h, 48, Material.HOPPER, "<#FF8C00>Категория: <white>" + category, List.of(
                "<gray>Нажмите для переключения фильтра категорий"
        ), e -> openAuction(player, AUCTION_CATEGORIES.get(nextCatIdx), search, sortMode, 0));

        addCloseButton(inv, h, 49, player);

        int nextSortIdx = (AUCTION_SORTS.indexOf(sortMode) + 1) % AUCTION_SORTS.size();
        setButton(inv, h, 50, Material.COMPARATOR, "<#55FFFF>Сортировка: <white>" + sortMode, List.of(
                "<gray>NEWEST / CHEAPEST / EXPENSIVE / EXPIRING",
                "<yellow>Нажмите для смены сортировки!"
        ), e -> openAuction(player, category, search, AUCTION_SORTS.get(nextSortIdx), 0));

        setButton(inv, h, 51, Material.BOOK, "<#FBBC00>История сделок", List.of(
                "<gray>Ваши последние покупки и продажи"
        ), e -> openAuctionHistory(player));

        if (listings.size() >= grid.length) {
            setButton(inv, h, 52, Material.SPECTRAL_ARROW, "<yellow>Следующая страница »", List.of(),
                    e -> openAuction(player, category, search, sortMode, page + 1));
        }
        setButton(inv, h, 53, Material.SUNFLOWER, "<#55FF55>Обновить список", List.of(
                "<gray>Подсказка: выставить предмет — <gold>/ah sell <цена></gold>"
        ), e -> openAuction(player, category, search, sortMode, page));

        player.openInventory(inv);
    }

    public void openAuctionMyItems(Player player) {
        Inventory inv = createFrame("AUCTION_MY", 54, "<bold><#FBBC00>МОИ ЛОТЫ И ВОЗВРАТ</bold>");
        ReallyGuiHolder h = (ReallyGuiHolder) inv.getHolder();
        int[] grid = contentSlots();
        List<AuctionListing> items = auctionModule.getSellerClaimableOrActive(player.getUniqueId());
        for (int i = 0; i < Math.min(items.size(), grid.length); i++) {
            AuctionListing l = items.get(i);
            ItemStack st = ItemSerializer.deserialize(l.itemSerialized()).clone();
            setItemButton(inv, h, grid[i], st, "<#FBBC00>" + l.itemDisplayName(), List.of(
                    "<gray>Статус: <white>" + l.status(),
                    "<gray>Цена: <#FBBC00>" + TextUtil.formatAmount(l.price()) + " ⛃",
                    "", "<yellow>Нажмите, чтобы вернуть предмет в инвентарь!"
            ), e -> {
                auctionModule.cancelOrReturnListing(player, l.id());
                openAuctionMyItems(player);
            });
        }
        setButton(inv, h, 45, Material.ARROW, "<yellow>← Назад на аукцион", List.of(),
                e -> openAuction(player, "ALL", null, "NEWEST", 0));
        addCloseButton(inv, h, 49, player);
        player.openInventory(inv);
    }

    public void openAuctionHistory(Player player) {
        Inventory inv = createFrame("AUCTION_HIST", 54, "<bold><#FBBC00>ИСТОРИЯ СДЕЛОК</bold>");
        ReallyGuiHolder h = (ReallyGuiHolder) inv.getHolder();
        int[] grid = contentSlots();
        List<AuctionListing> hist = auctionModule.getHistory(player.getUniqueId());
        for (int i = 0; i < Math.min(hist.size(), grid.length); i++) {
            AuctionListing l = hist.get(i);
            boolean soldByMe = l.sellerUuid().equals(player.getUniqueId());
            Material m = Material.matchMaterial(l.itemMaterial());
            setButton(inv, h, grid[i], m == null ? Material.PAPER : m,
                    (soldByMe ? "<#55FF55>[ПРОДАНО] " : "<#55FFFF>[КУПЛЕНО] ") + "<white>" + l.itemDisplayName() + " x" + l.itemAmount(),
                    List.of(
                            "<gray>Продавец: <white>" + l.sellerName(),
                            "<gray>Покупатель: <white>" + (l.buyerName() == null ? "—" : l.buyerName()),
                            "<gray>Сумма: <#FBBC00>" + TextUtil.formatAmount(l.price()) + " монет"
                    ), null);
        }
        setButton(inv, h, 45, Material.ARROW, "<yellow>← Назад на аукцион", List.of(),
                e -> openAuction(player, "ALL", null, "NEWEST", 0));
        addCloseButton(inv, h, 49, player);
        player.openInventory(inv);
    }

    // =========================================================================
    // 6. CLAN GUI (/clan)
    // =========================================================================
    public void openClan(Player player) {
        Inventory inv = createFrame("CLAN", 54, title("clan"));
        ReallyGuiHolder h = (ReallyGuiHolder) inv.getHolder();
        Optional<ClanData> clanOpt = clanModule.getPlayerClan(player.getUniqueId());

        if (clanOpt.isEmpty()) {
            setButton(inv, h, 22, Material.NETHERITE_SWORD, "<bold><#FBBC00>У вас ещё нет клана!", List.of(
                    "<gray>Стоимость создания: <#FBBC00>50 000 монет",
                    "<gray>Команда: <gold>/clan create <имя> <тег></gold>",
                    "<gray>Принять приглашение: <gold>/clan accept</gold>",
                    "",
                    "<yellow>Нажмите, чтобы посмотреть ТОП кланов!"
            ), e -> openTop(player, "clans"));
        } else {
            ClanData c = clanOpt.get();
            setButton(inv, h, 4, Material.TOTEM_OF_UNDYING, "<bold><#FBBC00>[" + c.tag() + "] " + c.name(), List.of(
                    "<gray>Рейтинг клана: <#FF6A00>" + c.ratingPoints() + " очков",
                    "<gray>Участников: <white>" + c.members().size() + "/15",
                    "<gray>Казна клана: <#FBBC00>" + TextUtil.formatAmount(c.bankCoins()) + " ⛃",
                    "<gray>Убийств / Захватов: <white>" + c.kills() + " / " + c.captures()
            ), null);

            setButton(inv, h, 20, Material.ENDER_PEARL, "<#55FFFF>Телепорт на базу клана", List.of(
                    "<yellow>Нажмите для телепортации (/clan home)"
            ), e -> {
                player.closeInventory();
                clanModule.teleportClanHome(player);
            });

            setButton(inv, h, 22, Material.GOLD_BLOCK, "<#FBBC00>Внести 10 000 ⛃ в казну", List.of(
                    "<gray>Текущая казна: <white>" + TextUtil.formatAmount(c.bankCoins()) + " ⛃",
                    "<yellow>Нажмите, чтобы пополнить казну клана!"
            ), e -> {
                clanModule.depositBank(player, 10_000L);
                openClan(player);
            });

            setButton(inv, h, 24, Material.IRON_SWORD, "<#FF4500>Огонь по своим: " + (c.friendlyFire() ? "<red>ВКЛ" : "<green>ВЫКЛ"), List.of(
                    "<gray>Только для Главы клана",
                    "<yellow>Нажмите для переключения!"
            ), e -> {
                clanModule.toggleFriendlyFire(player);
                openClan(player);
            });

            int mSlot = 28;
            for (ClanData.ClanMember m : c.members()) {
                if (mSlot > 34) break;
                setButton(inv, h, mSlot++, Material.PLAYER_HEAD, "<white>" + m.playerName(), List.of(
                        "<gray>Должность: <#FBBC00>" + m.role().displayRu(),
                        "<gray>Вклад в казну: <white>" + TextUtil.formatAmount(m.contributedCoins()) + " ⛃"
                ), null);
            }
        }

        addBackAndClose(inv, h, player);
        player.openInventory(inv);
    }

    // =========================================================================
    // 7, 8, 9. CASES (/cases), PREVIEW & ANIMATED ROULETTE SPIN
    // =========================================================================
    public void openCases(Player player) {
        Inventory inv = createFrame("CASES", 54, title("cases"));
        ReallyGuiHolder h = (ReallyGuiHolder) inv.getHolder();
        Map<String, Integer> keys = casesModule.getPlayerKeys(player.getUniqueId());

        for (CasesModule.CaseDefinition def : casesModule.getCases()) {
            int myKeys = keys.getOrDefault(def.id(), 0);
            List<String> lore = new ArrayList<>();
            lore.add("<gray>Ваши ключи: <#55FF55>" + myKeys + " шт.");
            if (def.keyPriceRilliks() > 0) {
                lore.add("<gray>Цена 1 ключа: <#FF6A00>" + def.keyPriceRilliks() + " рилликов ❖");
            } else if (def.keyPriceCoins() > 0) {
                lore.add("<gray>Цена 1 ключа: <#FBBC00>" + TextUtil.formatAmount(def.keyPriceCoins()) + " монет ⛃");
            } else {
                lore.add("<gray>Добывается на ивентах и у Боссов!");
            }
            lore.add("");
            lore.add("<#55FF55>ЛКМ — Открыть кейс с анимацией!");
            lore.add("<#55FFFF>ПКМ — Просмотр всех наград и шансов");
            lore.add("<#FBBC00>Shift+ЛКМ — Купить 1 ключ");

            setButton(inv, h, def.slot(), def.icon(), def.title(), lore, e -> {
                if (e.isRightClick()) {
                    openCasePreview(player, def);
                } else if (e.isShiftClick()) {
                    casesModule.buyKey(player, def);
                    openCases(player);
                } else {
                    startCaseSpin(player, def);
                }
            });
        }

        // Recent server drops history item
        List<String> histLore = new ArrayList<>();
        for (ProgressionRepository.CaseHistoryEntry drop : casesModule.getRecentDrops(6)) {
            histLore.add("<white>" + drop.playerName() + " <dark_gray>→ " + drop.rewardDisplay());
        }
        if (histLore.isEmpty()) histLore.add("<gray>Пока никто не открывал кейсы");
        setButton(inv, h, 40, Material.KNOWLEDGE_BOOK, "<bold><#FBBC00>Последние Выигрыши", histLore, null);

        addBackAndClose(inv, h, player);
        player.openInventory(inv);
    }

    public void openCasePreview(Player player, CasesModule.CaseDefinition def) {
        Inventory inv = createFrame("CASE_PREVIEW", 54, "<bold><#FBBC00>СОДЕРЖИМОЕ • </bold>" + def.title());
        ReallyGuiHolder h = (ReallyGuiHolder) inv.getHolder();
        int[] grid = contentSlots();
        int idx = 0;
        for (CasesModule.CaseReward r : def.rewards()) {
            if (idx >= grid.length) break;
            double pct = Math.round((r.weight() / def.totalWeight()) * 10000.0) / 100.0;
            Material icon = switch (r.rarity()) {
                case MYTHIC -> Material.NETHER_STAR;
                case LEGENDARY -> Material.TOTEM_OF_UNDYING;
                case EPIC -> Material.AMETHYST_SHARD;
                case RARE -> Material.DIAMOND;
                default -> Material.GOLD_NUGGET;
            };
            setButton(inv, h, grid[idx++], icon, r.display(), List.of(
                    "<gray>Редкость: " + r.rarity().colorTag() + r.rarity().displayName(),
                    "<gray>Точный шанс: <#FBBC00>" + pct + "%"
            ), null);
        }
        setButton(inv, h, 45, Material.ARROW, "<yellow>← Назад к кейсам", List.of(), e -> openCases(player));
        addCloseButton(inv, h, 49, player);
        player.openInventory(inv);
    }

    public void startCaseSpin(Player player, CasesModule.CaseDefinition def) {
        Optional<CasesModule.CaseReward> rolledOpt = casesModule.consumeKeyAndRoll(player, def);
        if (rolledOpt.isEmpty()) return;
        CasesModule.CaseReward finalReward = rolledOpt.get();

        if (!configManager.get("cases.yml").getBoolean("settings.spin-animation-enabled", true) || plugin == null) {
            casesModule.deliverRolledReward(player, def, finalReward);
            openCases(player);
            return;
        }

        Inventory inv = createFrame("CASE_SPIN", 27, "<bold><#FF6A00>ПРОКРУТКА • </bold>" + def.title());
        ReallyGuiHolder h = (ReallyGuiHolder) inv.getHolder();
        inv.setItem(4, namedItem(Material.HOPPER, "<bold><#FBBC00>▼ ВАШ ПРИЗ ▼", List.of()));
        inv.setItem(22, namedItem(Material.REDSTONE_TORCH, "<bold><#FBBC00>▲ ВАШ ПРИЗ ▲", List.of()));

        AtomicBoolean delivered = new AtomicBoolean(false);
        Runnable deliverOnce = () -> {
            if (delivered.compareAndSet(false, true)) {
                casesModule.deliverRolledReward(player, def, finalReward);
            }
        };
        // Guarantee delivery even if player presses ESC mid-animation!
        h.onClose(deliverOnce);
        player.openInventory(inv);

        List<CasesModule.CaseReward> strip = new ArrayList<>();
        for (int i = 0; i < 24; i++) {
            strip.add(casesModule.rollWeightedReward(def, ThreadLocalRandom.current()));
        }
        strip.set(18, finalReward); // Slot 13 lands on index 18 at step 14!

        new BukkitRunnable() {
            int step = 0;

            @Override
            public void run() {
                if (!player.isOnline() || delivered.get()) {
                    deliverOnce.run();
                    cancel();
                    return;
                }
                for (int col = 0; col < 9; col++) {
                    CasesModule.CaseReward r = strip.get((step + col) % strip.size());
                    Material mat = switch (r.rarity()) {
                        case MYTHIC -> Material.NETHER_STAR;
                        case LEGENDARY -> Material.TOTEM_OF_UNDYING;
                        case EPIC -> Material.AMETHYST_SHARD;
                        case RARE -> Material.DIAMOND;
                        default -> Material.GOLD_INGOT;
                    };
                    inv.setItem(9 + col, namedItem(mat, r.display(), List.of(r.rarity().colorTag() + r.rarity().displayName())));
                }
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 0.6f, 1.0f + (step * 0.04f));
                step++;
                if (step > 14) {
                    cancel();
                    deliverOnce.run();
                }
            }
        }.runTaskTimer(plugin, 0L, 3L);
    }

    // =========================================================================
    // 10. DONATE & RILLIK SHOP (/donate)
    // =========================================================================
    public void openDonate(Player player) {
        Inventory inv = createFrame("DONATE", 54, title("donate"));
        ReallyGuiHolder h = (ReallyGuiHolder) inv.getHolder();
        RankDefinition currentRank = playerModule.getRank(player);

        int rSlot = 10;
        for (RankDefinition rank : configManager.getPrimaryRanks()) {
            if (!rank.purchasable()) continue;
            if (rSlot == 17) rSlot = 19;
            if (rSlot > 25) break;

            long discount = currentRank.purchasable() && currentRank.weight() < rank.weight() ? currentRank.priceRilliks() : 0L;
            long netCost = Math.max(1L, rank.priceRilliks() - discount);
            boolean alreadyOwned = currentRank.weight() >= rank.weight();

            setButton(inv, h, rSlot++, Material.TOTEM_OF_UNDYING, rank.displayName() + " <dark_gray>• " + rank.prefix(), List.of(
                    "<gray>Точек дома: <white>" + rank.maxHomes() + " <gray>• Слотов /ah: <white>" + rank.auctionSlots(),
                    "<gray>Множитель монет PvP: <#55FF55>x" + (int) rank.killCoinMultiplier(),
                    alreadyOwned
                            ? "<green>✔ У вас уже открыт этот или более высокий ранг!"
                            : "<gray>Стоимость доплаты: <#FF6A00>" + TextUtil.formatAmount(netCost) + " рилликов ❖",
                    "",
                    alreadyOwned ? "<gray>Недоступно к повторной покупке" : "<yellow>Нажмите для покупки за Риллики!"
            ), e -> {
                if (alreadyOwned) return;
                var res = economyModule.withdraw(player.getUniqueId(), CurrencyType.RILLIKS, netCost, "BUY_RANK", rank.id());
                if (!res.success()) {
                    locale.send(player, "economy.not-enough-rilliks", Map.of("needed", TextUtil.formatAmount(netCost)));
                    return;
                }
                playerModule.setPlayerRank(player.getUniqueId(), player.getName(), rank.id());
                locale.sendRaw(player, "<#FBBC00>Поздравляем! Вы приобрели привилегию " + rank.displayName() + "<#FBBC00>!");
                openDonate(player);
            });
        }

        int itemSlot = 28;
        for (EconomyModule.RillikEntry re : economyModule.getRillikEntries()) {
            if (itemSlot == 35) itemSlot = 37;
            if (itemSlot > 43) break;
            setButton(inv, h, itemSlot++, re.icon(), re.display() + " <gray>x" + re.amount(), List.of(
                    "<gray>Цена: <#FF6A00>" + re.priceRilliks() + " рилликов ❖",
                    "", "<yellow>Нажмите для покупки!"
            ), e -> {
                economyModule.buyFromRillikShop(player, re);
                openDonate(player);
            });
        }

        addBackAndClose(inv, h, player);
        player.openInventory(inv);
    }

    // =========================================================================
    // 11. KITS (/kits)
    // =========================================================================
    public void openKits(Player player) {
        Inventory inv = createFrame("KITS", 54, title("kits"));
        ReallyGuiHolder h = (ReallyGuiHolder) inv.getHolder();
        int[] grid = contentSlots();
        int idx = 0;

        for (KitsModule.KitDefinition kit : kitsModule.getKits()) {
            if (idx >= grid.length) break;
            boolean hasPerm = kit.permission() == null || kit.permission().isBlank() || player.hasPermission(kit.permission());
            long rem = cooldowns.getRemainingSeconds(player.getUniqueId(), "kit:" + kit.id());
            String status = !hasPerm
                    ? "<red>✖ Требуется соответствующая привилегия"
                    : (rem > 0 ? "<yellow>⏳ Доступно через: " + TextUtil.formatDuration(rem) : "<#55FF55>✔ Доступно — нажмите для получения!");

            setButton(inv, h, grid[idx++], kit.icon(), kit.displayName(), List.of(
                    "<gray>Перезарядка: <white>" + TextUtil.formatDuration(kit.cooldownSeconds()),
                    "<gray>Предметов в наборе: <white>" + kit.itemSpecs().size(),
                    "", status
            ), e -> {
                if (kitsModule.claimKit(player, kit.id())) {
                    openKits(player);
                }
            });
        }
        addBackAndClose(inv, h, player);
        player.openInventory(inv);
    }

    // =========================================================================
    // 12. REWARDS (/rewards)
    // =========================================================================
    public void openRewards(Player player) {
        Inventory inv = createFrame("REWARDS", 54, title("rewards"));
        ReallyGuiHolder h = (ReallyGuiHolder) inv.getHolder();
        PlayerProfile p = playerModule.getProfile(player);
        Set<String> claimed = rewardsModule.getClaimedKeys(player);

        setButton(inv, h, 11, Material.SUNFLOWER, "<bold><#FBBC00>Ежедневная Награда (/daily)", List.of(
                "<gray>Текущая серия входов: <white>" + p.dailyStreak() + " / 7 дн.",
                "", "<yellow>Нажмите, чтобы забрать сегодняшнюю награду!"
        ), e -> {
            rewardsModule.claimDaily(player);
            openRewards(player);
        });

        setButton(inv, h, 15, Material.CHEST_MINECART, "<bold><#FF8C00>Еженедельный Бонус", List.of(
                "<gray>Требуется минимум 3 часа общего онлайна",
                "<gray>Награда: <gold>15 000 монет + Ключ Монет</gold>",
                "", "<yellow>Нажмите для получения!"
        ), e -> {
            rewardsModule.claimWeekly(player);
            openRewards(player);
        });

        var ptSec = configManager.get("rewards.yml").getConfigurationSection("playtime-milestones");
        int slot = 19;
        if (ptSec != null) {
            for (String key : ptSec.getKeys(false)) {
                boolean done = claimed.contains("PLAYTIME_" + key + ":ONCE");
                long req = ptSec.getLong(key + ".seconds", 3600L);
                setButton(inv, h, slot++, done ? Material.MINECART : Material.CLOCK,
                        "<#55FFFF>" + ptSec.getString(key + ".title", key), List.of(
                                "<gray>Ваш онлайн: <white>" + TextUtil.formatDuration(p.playtimeSeconds()) + " / " + TextUtil.formatDuration(req),
                                done ? "<green>✔ Получено" : "<yellow>Нажмите для получения!"
                        ), e -> {
                            rewardsModule.claimPlaytimeMilestone(player, key);
                            openRewards(player);
                        });
            }
        }

        var achSec = configManager.get("rewards.yml").getConfigurationSection("achievements");
        int aSlot = 28;
        if (achSec != null) {
            for (String key : achSec.getKeys(false)) {
                if (aSlot > 34) break;
                boolean done = claimed.contains("ACH_" + key + ":ONCE");
                setButton(inv, h, aSlot++, done ? Material.LIME_DYE : Material.BLAZE_POWDER,
                        "<#FBBC00>Достижение: <white>" + achSec.getString(key + ".title", key), List.of(
                                "<gray>Награда: <gold>" + achSec.getLong(key + ".coins", 0) + " ⛃ / "
                                        + achSec.getLong(key + ".rilliks", 0) + " ❖",
                                done ? "<green>✔ Получено" : "<yellow>Нажмите для проверки и получения!"
                        ), e -> {
                            rewardsModule.claimAchievement(player, key);
                            openRewards(player);
                        });
            }
        }

        addBackAndClose(inv, h, player);
        player.openInventory(inv);
    }

    // =========================================================================
    // 13. QUESTS (/quests)
    // =========================================================================
    public void openQuests(Player player) {
        Inventory inv = createFrame("QUESTS", 54, title("quests"));
        ReallyGuiHolder h = (ReallyGuiHolder) inv.getHolder();
        int[] grid = contentSlots();
        int idx = 0;

        for (QuestsModule.QuestDef q : questsModule.getAllQuests()) {
            if (idx >= grid.length) break;
            var st = questsModule.getState(player.getUniqueId(), q);
            Material mat = st.claimed() ? Material.BOOK : (st.completed() ? Material.ENCHANTED_BOOK : Material.WRITABLE_BOOK);
            String status = st.claimed()
                    ? "<green>✔ Награда получена"
                    : (st.completed() ? "<#FBBC00>★ ВЫПОЛНЕНО — Нажмите, чтобы забрать!" : "<gray>В процессе выполнения...");
            setButton(inv, h, grid[idx++], mat, q.title(), List.of(
                    "<gray>Категория: <white>" + q.category(),
                    "<gray>Прогресс: <#55FFFF>" + st.progress() + " / " + q.target(),
                    "<gray>Награда: <#FBBC00>" + TextUtil.formatAmount(q.rewardCoins()) + " ⛃"
                            + (q.rewardRilliks() > 0 ? " <gray>+ <#FF6A00>" + q.rewardRilliks() + " ❖" : "")
                            + " <gray>+ <#FFD700>" + q.rewardBpXp() + " XP БП",
                    "", status
            ), e -> {
                if (questsModule.claimQuestReward(player, q.id())) {
                    openQuests(player);
                }
            });
        }
        addBackAndClose(inv, h, player);
        player.openInventory(inv);
    }

    // =========================================================================
    // 14. EVENTS & BOSSES (/events)
    // =========================================================================
    public void openEvents(Player player) {
        Inventory inv = createFrame("EVENTS", 54, title("events"));
        ReallyGuiHolder h = (ReallyGuiHolder) inv.getHolder();

        EventsModule.ActiveWorldEvent ev = eventsModule.getActiveEvent();
        EventsModule.ActiveBoss boss = eventsModule.getActiveBoss();

        List<String> statusLore = new ArrayList<>();
        if (ev != null) {
            statusLore.add("<gray>Активный ивент: " + ev.title);
            statusLore.add("<gray>Координаты: <white>X:" + ev.location.getBlockX() + " Y:" + ev.location.getBlockY() + " Z:" + ev.location.getBlockZ());
        } else if (boss != null) {
            statusLore.add("<gray>Активный Босс: " + boss.displayName);
            statusLore.add("<gray>Координаты: <white>X:" + boss.entity.getLocation().getBlockX() + " Z:" + boss.entity.getLocation().getBlockZ());
        } else {
            statusLore.add("<gray>Следующий авто-ивент через: <#FBBC00>" + TextUtil.formatDuration(eventsModule.getSecondsUntilNextEvent()));
        }
        setButton(inv, h, 4, Material.BEACON, "<bold><#FBBC00>Радар Событий", statusLore, null);

        int[] grid = contentSlots();
        int idx = 0;
        ConfigurationSection evSec = configManager.get("events.yml").getConfigurationSection("events");
        if (evSec != null) {
            for (String key : evSec.getKeys(false)) {
                if (idx >= grid.length) break;
                setButton(inv, h, grid[idx++], Material.CAMPFIRE, evSec.getString(key + ".title", key), List.of(
                        "<gray>" + evSec.getString(key + ".description", ""),
                        "<gray>Длительность: <white>" + TextUtil.formatDuration(evSec.getLong(key + ".duration-seconds", 300))
                ), null);
            }
        }
        ConfigurationSection bSec = configManager.get("bosses.yml").getConfigurationSection("bosses");
        if (bSec != null) {
            for (String key : bSec.getKeys(false)) {
                if (idx >= grid.length) break;
                setButton(inv, h, grid[idx++], Material.WITHER_SKELETON_SKULL, bSec.getString(key + ".name", key), List.of(
                        "<gray>Здоровье: <#FF4500>" + (int) bSec.getDouble(key + ".max-health") + " ❤ (3 фазы)",
                        "<gray>Награда Топ-1: <#FBBC00>" + TextUtil.formatAmount(bSec.getLong(key + ".rewards.top-1-coins"))
                                + " ⛃ + " + bSec.getLong(key + ".rewards.top-1-rilliks") + " ❖"
                ), null);
            }
        }

        addBackAndClose(inv, h, player);
        player.openInventory(inv);
    }

    // =========================================================================
    // 15 & 16. STATS (/stats) & TOP LEADERBOARDS (/top)
    // =========================================================================
    public void openStats(Player player) {
        PlayerProfile p = playerModule.getProfile(player);
        Inventory inv = createFrame("STATS", 45, title("stats"));
        ReallyGuiHolder h = (ReallyGuiHolder) inv.getHolder();

        setButton(inv, h, 20, Material.NETHERITE_SWORD, "<bold><#FF4500>Боевая Статистика", List.of(
                "<gray>Убийств игроков: <white>" + p.kills(),
                "<gray>Смертей: <white>" + p.deaths(),
                "<gray>Коэффициент K/D: <#FBBC00>" + p.kdRatio(),
                "<gray>Текущая серия: <white>" + p.killStreak() + " <gray>(Рекорд: <#55FF55>" + p.bestKillStreak() + "<gray>)",
                "<gray>Побеждено Боссов: <#FF6A00>" + p.bossKills()
        ), null);

        setButton(inv, h, 22, Material.GOLD_BLOCK, "<bold><#FBBC00>Экономика и Кейсы", List.of(
                "<gray>Монеты: <#FBBC00>" + TextUtil.formatAmount(p.coins()) + " ⛃",
                "<gray>Риллики: <#FF6A00>" + TextUtil.formatAmount(p.rilliks()) + " ❖",
                "<gray>Открыто кейсов: <white>" + p.casesOpened(),
                "<gray>Выполнено квестов: <white>" + p.questsCompleted()
        ), null);

        setButton(inv, h, 24, Material.DIAMOND_PICKAXE, "<bold><#55FFFF>Активность в Мире", List.of(
                "<gray>Время на сервере: <white>" + TextUtil.formatDuration(p.playtimeSeconds()),
                "<gray>Вскопано блоков: <white>" + TextUtil.formatAmount(p.blocksMined()),
                "<gray>Уровень БП: <#FFD700>" + battlePassModule.calculateLevel(p.battlepassXp())
        ), null);

        setButton(inv, h, 36, Material.ARROW, "<yellow>← В меню", List.of(), e -> openMainMenu(player));
        addCloseButton(inv, h, 40, player);
        player.openInventory(inv);
    }

    public void openTop(Player player, String category) {
        Inventory inv = createFrame("TOP", 54, title("top"));
        ReallyGuiHolder h = (ReallyGuiHolder) inv.getHolder();

        setButton(inv, h, 10, Material.GOLD_INGOT, "<#FBBC00>Топ по Монетам", List.of(), e -> openTop(player, "coins"));
        setButton(inv, h, 11, Material.NETHERITE_SWORD, "<#FF4500>Топ по Убийствам", List.of(), e -> openTop(player, "kills"));
        setButton(inv, h, 12, Material.TOTEM_OF_UNDYING, "<#FF8C00>Топ Кланов", List.of(), e -> openTop(player, "clans"));
        setButton(inv, h, 14, Material.CLOCK, "<#55FFFF>Топ по Онлайну", List.of(), e -> openTop(player, "playtime_seconds"));
        setButton(inv, h, 15, Material.DRAGON_HEAD, "<#AA00AA>Топ Охотников на Боссов", List.of(), e -> openTop(player, "boss_kills"));

        int[] slots = {20, 21, 22, 23, 24, 29, 30, 31, 32, 33};
        if ("clans".equalsIgnoreCase(category)) {
            List<ClanData> clans = statisticsModule.getTopClans();
            for (int i = 0; i < Math.min(clans.size(), slots.length); i++) {
                ClanData c = clans.get(i);
                setButton(inv, h, slots[i], Material.SHIELD, "<#FBBC00>#" + (i + 1) + " <white>[" + c.tag() + "] " + c.name(), List.of(
                        "<gray>Рейтинг: <#FF6A00>" + c.ratingPoints() + " очков",
                        "<gray>Участников: <white>" + c.members().size(),
                        "<gray>Казна: <#FBBC00>" + TextUtil.formatAmount(c.bankCoins()) + " ⛃"
                ), null);
            }
        } else {
            List<LeaderboardEntry> list = statisticsModule.getTopPlayers(category);
            for (int i = 0; i < Math.min(list.size(), slots.length); i++) {
                LeaderboardEntry row = list.get(i);
                String valStr = "playtime_seconds".equals(category)
                        ? TextUtil.formatDuration(row.value())
                        : TextUtil.formatAmount(row.value());
                setButton(inv, h, slots[i], Material.PLAYER_HEAD, "<#FBBC00>#" + (i + 1) + " <white>" + row.username(), List.of(
                        "<gray>Ранг: " + configManager.getRank(row.rankId()).displayName(),
                        "<gray>Показатель: <#55FF55>" + valStr
                ), null);
            }
        }

        addBackAndClose(inv, h, player);
        player.openInventory(inv);
    }

    // =========================================================================
    // 17, 18. BATTLE PASS (/bp), SETTINGS & WARPS (/warps)
    // =========================================================================
    public void openBattlePass(Player player, int page) {
        Inventory inv = createFrame("BATTLEPASS", 54, title("battlepass"));
        ReallyGuiHolder h = (ReallyGuiHolder) inv.getHolder();
        PlayerProfile p = playerModule.getProfile(player);
        int myLvl = battlePassModule.calculateLevel(p.battlepassXp());
        Set<String> claimed = rewardsModule.getClaimedKeys(player);
        String seasonId = "S" + configManager.get("battlepass.yml").getInt("season-id", 1);

        setButton(inv, h, 4, Material.NETHER_STAR, "<bold><#FFD700>Ваш Уровень БП: " + myLvl, List.of(
                "<gray>Опыт: <white>" + p.battlepassXp() + " XP",
                "<gray>Статус Премиум: " + (p.battlepassPremium() ? "<#55FF55>АКТИВЕН" : "<red>Не куплен (Нажмите для покупки за 399 ❖)")
        ), e -> {
            if (!p.battlepassPremium() && battlePassModule.buyPremiumPass(player)) {
                openBattlePass(player, page);
            }
        });

        int startLvl = page * 7 + 1;
        for (int col = 0; col < 7; col++) {
            int lvl = startLvl + col;
            if (lvl > 30) break;
            boolean unlocked = myLvl >= lvl;
            boolean freeClaimed = claimed.contains("BP_FREE_" + lvl + ":" + seasonId);
            boolean premClaimed = claimed.contains("BP_PREM_" + lvl + ":" + seasonId);

            setButton(inv, h, 19 + col, freeClaimed ? Material.MINECART : (unlocked ? Material.CHEST_MINECART : Material.FURNACE_MINECART),
                    "<#55FF55>Уровень " + lvl + " (Бесплатный)", List.of(
                            freeClaimed ? "<green>✔ Получено" : (unlocked ? "<yellow>Нажмите, чтобы забрать!" : "<red>Требуется " + lvl + " ур.")
                    ), e -> {
                        if (battlePassModule.claimTrackLevel(player, lvl, false)) openBattlePass(player, page);
                    });

            setButton(inv, h, 28 + col, premClaimed ? Material.ENDER_CHEST : Material.GOLD_BLOCK,
                    "<#FBBC00>Уровень " + lvl + " (Премиум)", List.of(
                            premClaimed ? "<green>✔ Получено" : (unlocked && p.battlepassPremium()
                                    ? "<yellow>Нажмите, чтобы забрать!" : "<red>Требуется Премиум и " + lvl + " ур.")
                    ), e -> {
                        if (battlePassModule.claimTrackLevel(player, lvl, true)) openBattlePass(player, page);
                    });
        }

        if (page > 0) {
            setButton(inv, h, 46, Material.SPECTRAL_ARROW, "<yellow>« Назад", List.of(), e -> openBattlePass(player, page - 1));
        }
        if (startLvl + 7 <= 30) {
            setButton(inv, h, 52, Material.SPECTRAL_ARROW, "<yellow>Вперёд »", List.of(), e -> openBattlePass(player, page + 1));
        }
        addBackAndClose(inv, h, player);
        player.openInventory(inv);
    }

    public void openSettings(Player player) {
        PlayerProfile p = playerModule.getProfile(player);
        Inventory inv = createFrame("SETTINGS", 45, title("settings"));
        ReallyGuiHolder h = (ReallyGuiHolder) inv.getHolder();

        setButton(inv, h, 20, Material.PAINTING, "<#FBBC00>Боковая панель (Scoreboard): " + (p.scoreboardEnabled() ? "<green>ВКЛ" : "<red>ВЫКЛ"),
                List.of("<yellow>Нажмите для переключения!"), e -> {
                    p.setScoreboardEnabled(!p.scoreboardEnabled());
                    if (!p.scoreboardEnabled() && Bukkit.getScoreboardManager() != null) {
                        player.setScoreboard(Bukkit.getScoreboardManager().getNewScoreboard());
                    }
                    openSettings(player);
                });

        setButton(inv, h, 22, Material.ENDER_PEARL, "<#55FFFF>Запросы телепортации (/tpa): " + (p.tpToggle() ? "<green>ВКЛ" : "<red>ВЫКЛ"),
                List.of("<yellow>Нажмите для переключения!"), e -> {
                    p.setTpToggle(!p.tpToggle());
                    openSettings(player);
                });

        setButton(inv, h, 24, Material.WRITABLE_BOOK, "<#55FF55>Личные сообщения: " + (p.pmEnabled() ? "<green>ВКЛ" : "<red>ВЫКЛ"),
                List.of("<yellow>Нажмите для переключения!"), e -> {
                    p.setPmEnabled(!p.pmEnabled());
                    openSettings(player);
                });

        setButton(inv, h, 36, Material.ARROW, "<yellow>← В меню", List.of(), e -> openMainMenu(player));
        addCloseButton(inv, h, 40, player);
        player.openInventory(inv);
    }

    public void openWarps(Player player) {
        Inventory inv = createFrame("WARPS", 45, title("warps"));
        ReallyGuiHolder h = (ReallyGuiHolder) inv.getHolder();
        int[] slots = {19, 20, 21, 22, 23, 24, 25};
        int idx = 0;
        for (WarpPoint wp : playerModule.getWarps()) {
            if (idx >= slots.length) break;
            Material icon = switch (wp.category().toUpperCase(Locale.ROOT)) {
                case "PVP" -> Material.NETHERITE_SWORD;
                case "DONATE" -> Material.ENDER_CHEST;
                case "ECONOMY" -> Material.EMERALD;
                case "FARM" -> Material.DIAMOND_PICKAXE;
                default -> Material.BEACON;
            };
            setButton(inv, h, slots[idx++], icon, "<bold><#FBBC00>" + wp.displayName(), List.of(
                    "<gray>Команда: <white>/warp " + wp.name(),
                    "", "<yellow>Нажмите для телепортации!"
            ), e -> {
                player.closeInventory();
                var w = Bukkit.getWorld(wp.worldName());
                if (w != null) {
                    playerModule.teleportWithWarmup(player, new org.bukkit.Location(w, wp.x(), wp.y(), wp.z(), wp.yaw(), wp.pitch()), null);
                }
            });
        }
        setButton(inv, h, 36, Material.ARROW, "<yellow>← В меню", List.of(), e -> openMainMenu(player));
        addCloseButton(inv, h, 40, player);
        player.openInventory(inv);
    }

    // ---- GUI HELPERS ----
    private String title(String key) {
        return configManager.get("menus.yml").getString("titles." + key, "<bold><#FBBC00>REALLYWORLD</bold>");
    }

    private Inventory createFrame(String type, int size, String mmTitle) {
        ReallyGuiHolder holder = new ReallyGuiHolder(type);
        Inventory inv = Bukkit.createInventory(holder, size, TextUtil.mm(mmTitle));
        holder.bindInventory(inv);
        ItemStack pane = namedItem(Material.GRAY_STAINED_GLASS_PANE, " ", List.of());
        ItemStack accent = namedItem(Material.ORANGE_STAINED_GLASS_PANE, " ", List.of());
        for (int i = 0; i < size; i++) {
            if (i < 9 || i >= size - 9 || i % 9 == 0 || i % 9 == 8) {
                inv.setItem(i, (i == 0 || i == 8 || i == size - 9 || i == size - 1) ? accent : pane);
            }
        }
        return inv;
    }

    private int[] contentSlots() {
        return new int[]{
                10, 11, 12, 13, 14, 15, 16,
                19, 20, 21, 22, 23, 24, 25,
                28, 29, 30, 31, 32, 33, 34,
                37, 38, 39, 40, 41, 42, 43
        };
    }

    private void addBackAndClose(Inventory inv, ReallyGuiHolder h, Player player) {
        setButton(inv, h, 45, Material.ARROW, "<yellow>← Главное меню", List.of(), e -> openMainMenu(player));
        addCloseButton(inv, h, 49, player);
    }

    private void addCloseButton(Inventory inv, ReallyGuiHolder h, int slot, Player player) {
        setButton(inv, h, slot, Material.BARRIER, "<red>✖ Закрыть окно", List.of(), e -> player.closeInventory());
    }

    private void setButton(
            Inventory inv,
            ReallyGuiHolder holder,
            int slot,
            Material material,
            String name,
            List<String> loreLines,
            java.util.function.Consumer<InventoryClickEvent> handler
    ) {
        inv.setItem(slot, namedItem(material, name, loreLines));
        if (handler != null) holder.onClick(slot, handler);
    }

    private void setItemButton(
            Inventory inv,
            ReallyGuiHolder holder,
            int slot,
            ItemStack base,
            String name,
            List<String> loreLines,
            java.util.function.Consumer<InventoryClickEvent> handler
    ) {
        ItemStack copy = base.clone();
        ItemMeta meta = copy.getItemMeta();
        if (meta != null) {
            meta.displayName(TextUtil.mm(name));
            List<Component> comps = new ArrayList<>(loreLines.size());
            for (String l : loreLines) comps.add(TextUtil.mm(l));
            meta.lore(comps);
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
            copy.setItemMeta(meta);
        }
        inv.setItem(slot, copy);
        if (handler != null) holder.onClick(slot, handler);
    }

    private ItemStack namedItem(Material mat, String name, List<String> loreLines) {
        ItemStack st = new ItemStack(mat);
        ItemMeta meta = st.getItemMeta();
        if (meta != null) {
            meta.displayName(TextUtil.mm(name));
            if (!loreLines.isEmpty()) {
                List<Component> comps = new ArrayList<>(loreLines.size());
                for (String l : loreLines) comps.add(TextUtil.mm(l));
                meta.lore(comps);
            }
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
            st.setItemMeta(meta);
        }
        return st;
    }
}
