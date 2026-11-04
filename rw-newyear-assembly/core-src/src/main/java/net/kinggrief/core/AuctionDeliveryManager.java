package net.kinggrief.core;

import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Durable auction purchase receipts. Player inventory and YAML are separate
 * files: an interrupted 'delivering' receipt is intentionally NOT replayed. */
final class AuctionDeliveryManager implements Listener {
    private static final int MAX_PENDING = 32;
    private final KingGriefCore plugin;

    private static final class Receipt {
        final int id;
        final String state;
        final ItemStack item;
        Receipt(int id, String state, ItemStack item) { this.id = id; this.state = state; this.item = item; }
    }
    enum Outcome { DELIVERED, WAITING, REVIEW, FAILED }

    AuctionDeliveryManager(KingGriefCore plugin) { this.plugin = plugin; }
    private static String base(UUID player) { return "auctionDeliveries." + player; }
    private static String path(UUID player, int id) { return base(player) + "." + id; }
    static String listingPath(UUID player) { return "auctionListings." + player; }

    static void addListing(YamlConfiguration data, UUID player, DataStore.AuctionLot lot) throws IOException {
        String root = "auctionListings", path = listingPath(player);
        if ((data.contains(root) && !data.isConfigurationSection(root)) || data.contains(path))
            throw new IOException("previous auction listing needs review or listing section is invalid");
        data.set(path + ".state", "taking");
        data.set(path + ".id", lot.id);
        data.set(path + ".seller", lot.seller);
        data.set(path + ".price", lot.price);
        data.set(path + ".item", lot.item.clone());
        data.set(path + ".createdAt", lot.createdAt);
    }
    static DataStore.AuctionLot listing(YamlConfiguration data, UUID player) throws IOException {
        String root = "auctionListings", path = listingPath(player);
        if (data.contains(root) && !data.isConfigurationSection(root)) throw new IOException("invalid listing section");
        if (!data.contains(path)) return null;
        if (!data.isConfigurationSection(path) || !"taking".equals(data.getString(path + ".state")))
            throw new IOException("invalid listing journal");
        Object rawId = data.get(path + ".id"), rawPrice = data.get(path + ".price"), rawTime = data.get(path + ".createdAt");
        String seller = data.getString(path + ".seller");
        ItemStack item;
        try { item = data.getItemStack(path + ".item"); }
        catch (RuntimeException error) { throw new IOException("invalid listing item", error); }
        if (!(rawId instanceof Number) || !(rawPrice instanceof Number) || !(rawTime instanceof Number)
                || ((Number) rawId).longValue() <= 0 || ((Number) rawId).longValue() > Integer.MAX_VALUE
                || ((Number) rawId).doubleValue() != ((Number) rawId).longValue()
                || !Double.isFinite(((Number) rawPrice).doubleValue()) || ((Number) rawPrice).doubleValue() <= 0
                || ((Number) rawTime).longValue() <= 0 || seller == null || !seller.matches("[A-Za-z0-9_]{1,16}")
                || item == null || item.getType().isAir() || item.getAmount() <= 0) throw new IOException("invalid listing values");
        DataStore.AuctionLot lot = new DataStore.AuctionLot();
        lot.id = ((Number) rawId).intValue(); lot.seller = seller; lot.price = ((Number) rawPrice).doubleValue();
        lot.createdAt = ((Number) rawTime).longValue(); lot.item = item.clone();
        return lot;
    }
    static boolean sameListing(DataStore.AuctionLot a, DataStore.AuctionLot b) {
        return a != null && b != null && a.id == b.id && a.seller.equals(b.seller)
                && Double.compare(a.price, b.price) == 0 && a.createdAt == b.createdAt && a.item.equals(b.item);
    }

    /** Called only inside the same transaction that debits and removes the lot. */
    static void addReceipt(YamlConfiguration data, UUID player, DataStore.AuctionLot lot) throws IOException {
        String root = "auctionDeliveries", owner = base(player), path = path(player, lot.id);
        if ((data.contains(root) && !data.isConfigurationSection(root))
                || (data.contains(owner) && !data.isConfigurationSection(owner))) throw new IOException("invalid auction delivery section");
        ConfigurationSection entries = data.getConfigurationSection(owner);
        if (entries != null && entries.getKeys(false).size() >= MAX_PENDING) throw new IOException("auction delivery limit reached");
        if (data.contains(path) || lot.id <= 0 || lot.item == null || lot.item.getType().isAir() || lot.item.getAmount() <= 0)
            throw new IOException("invalid or duplicate auction receipt");
        data.set(path + ".state", "ready");
        data.set(path + ".item", lot.item.clone());
        data.set(path + ".seller", lot.seller);
        data.set(path + ".price", lot.price);
        data.set(path + ".createdAt", System.currentTimeMillis());
    }
    private static Receipt receipt(YamlConfiguration data, UUID player, int id) throws IOException {
        String path = path(player, id);
        if (!data.contains(path)) return null;
        if (!data.isConfigurationSection(path)) throw new IOException("invalid receipt section " + id);
        Object raw = data.get(path + ".state");
        ItemStack item;
        try { item = data.getItemStack(path + ".item"); }
        catch (RuntimeException badItem) { throw new IOException("invalid receipt item " + id, badItem); }
        if (!(raw instanceof String) || (!"ready".equals(raw) && !"delivering".equals(raw))
                || item == null || item.getType().isAir() || item.getAmount() <= 0)
            throw new IOException("invalid receipt " + id);
        return new Receipt(id, (String) raw, item.clone());
    }
    private static List<Receipt> receipts(YamlConfiguration data, UUID player) throws IOException {
        String root = "auctionDeliveries", owner = base(player);
        if ((data.contains(root) && !data.isConfigurationSection(root))
                || (data.contains(owner) && !data.isConfigurationSection(owner))) throw new IOException("invalid delivery list");
        List<Receipt> result = new ArrayList<Receipt>();
        ConfigurationSection entries = data.getConfigurationSection(owner);
        if (entries == null) return result;
        for (String key : entries.getKeys(false)) {
            int id;
            try { id = Integer.parseInt(key); } catch (NumberFormatException error) { throw new IOException("invalid receipt id", error); }
            if (id <= 0 || !Integer.toString(id).equals(key)) throw new IOException("invalid receipt id");
            Receipt receipt = receipt(data, player, id);
            if (receipt == null) throw new IOException("missing receipt " + id);
            result.add(receipt);
        }
        result.sort(Comparator.comparingInt(r -> r.id));
        return result;
    }
    boolean eligible(Player player) {
        return player != null && player.isOnline() && !player.isDead()
                && player.getGameMode() != org.bukkit.GameMode.SPECTATOR && Bukkit.isPrimaryThread()
                && plugin.getStore().isAuthenticated(player) && !plugin.getStore().isQuickFrozen(player)
                && !plugin.getModeration().isFrozenOrAscending(player);
    }

    void sell(Player player, ItemStack selected, double price) {
        if (!eligible(player)) return;
        int slot = player.getInventory().getHeldItemSlot();
        ItemStack original = selected.clone();
        DataStore.AuctionLot lot = plugin.getStore().beginAuctionListing(player, original, price);
        if (lot == null) {
            player.sendMessage(ColorUtil.c("&cЛот не подготовлен: предмет остался в руке. Проверьте /ah pending и сохранение данных."));
            return;
        }
        try {
            ItemStack current = player.getInventory().getItem(slot);
            if (player.getInventory().getHeldItemSlot() != slot || current == null || !current.equals(original)) {
                listingReview(player, lot.id); return;
            }
            player.getInventory().setItem(slot, null);
            player.saveData();
            if (!plugin.getStore().finishAuctionListing(player.getUniqueId(), lot.id)) { listingReview(player, lot.id); return; }
            player.sendMessage(ColorUtil.c("&#00E676⚖ Лот #" + lot.id + " выставлен за &a" + ColorUtil.fmtMoney(price) + "$&7!"));
        } catch (Exception error) {
            plugin.getLogger().severe("[Auction] Listing needs review: " + player.getUniqueId() + " lot #" + lot.id + ": " + error);
            listingReview(player, lot.id);
        }
    }
    private void listingReview(Player player, int id) {
        player.sendMessage(ColorUtil.c("&cВыставление #" + id + " остановлено для проверки. Не повторяйте операцию; сообщите администрации ID и UUID &f" + player.getUniqueId()));
    }

    void pending(Player player, String[] args) {
        if (!eligible(player)) return;
        int requested = 1;
        if (args.length == 2) try { requested = Integer.parseInt(args[1]); } catch (NumberFormatException error) { requested = 0; }
        if (args.length > 2 || requested < 1) { player.sendMessage(ColorUtil.c("&e/ah pending [страница]")); return; }
        try {
            List<Receipt> entries = plugin.getStore().inspect(data -> receipts(data, player.getUniqueId()));
            int pages = Math.max(1, (entries.size() + 7) / 8), page = Math.min(requested, pages);
            player.sendMessage(ColorUtil.c("&#FFD740Покупки аукциона: &f" + entries.size() + "/" + MAX_PENDING + " &8(" + page + "/" + pages + ")"));
            for (int i = (page - 1) * 8; i < Math.min(entries.size(), page * 8); i++) {
                Receipt r = entries.get(i);
                player.sendMessage(ColorUtil.c(" &8• &f#" + r.id + " &7" + r.item.getType().name() + " ×" + r.item.getAmount()
                        + ("ready".equals(r.state) ? " &aготово к выдаче" : " &cнужна проверка администратора")));
            }
            if (entries.isEmpty()) player.sendMessage(ColorUtil.c(" &8• &7Ожидающих покупок нет."));
            else player.sendMessage(ColorUtil.c("&7Получить: &f/ah claim [ID]"));
            DataStore.AuctionLot listing = plugin.getStore().inspect(data -> listing(data, player.getUniqueId()));
            if (listing != null) player.sendMessage(ColorUtil.c("&cВыставление #" + listing.id + " ожидает проверки; новые продажи временно заблокированы."));
            if (listing != null || !entries.isEmpty()) player.sendMessage(ColorUtil.c("&7Для проверки сообщите ID и UUID &f" + player.getUniqueId()));
            if (page < pages) player.sendMessage(ColorUtil.c("&7Далее: &f/ah pending " + (page + 1)));
        } catch (Exception error) { refuse(player, error); }
    }
    void claim(Player player, String[] args) {
        if (!eligible(player)) return;
        if (args.length > 2) { player.sendMessage(ColorUtil.c("&e/ah claim [ID]")); return; }
        try {
            int id;
            if (args.length == 2) {
                try { id = Integer.parseInt(args[1]); } catch (NumberFormatException error) { id = -1; }
                if (id <= 0) { player.sendMessage(ColorUtil.c("&eУкажите положительный ID из /ah pending.")); return; }
            } else {
                List<Receipt> entries = plugin.getStore().inspect(data -> receipts(data, player.getUniqueId()));
                if (entries.isEmpty()) { player.sendMessage(ColorUtil.c("&eОжидающих покупок нет.")); return; }
                Receipt next = entries.get(0);
                for (Receipt candidate : entries) if ("ready".equals(candidate.state)) { next = candidate; break; }
                id = next.id;
            }
            deliver(player, id);
        } catch (Exception error) { refuse(player, error); }
    }

    Outcome deliver(Player player, int id) {
        if (!eligible(player) || id <= 0) return Outcome.FAILED;
        final UUID uuid = player.getUniqueId();
        try {
            Receipt pending = plugin.getStore().inspect(data -> receipt(data, uuid, id));
            if (pending == null) { player.sendMessage(ColorUtil.c("&eПокупка #" + id + " не найдена.")); return Outcome.FAILED; }
            if ("delivering".equals(pending.state)) { review(player, id); return Outcome.REVIEW; }
            if (!InventoryCapacity.canFit(player, pending.item)) {
                player.sendMessage(ColorUtil.c("&eПокупка #" + id + " сохранена. Освободите место и введите &f/ah claim " + id));
                return Outcome.WAITING;
            }
            plugin.getStore().transact(data -> {
                Receipt latest = receipt(data, uuid, id);
                if (latest == null || !"ready".equals(latest.state) || !latest.item.equals(pending.item))
                    throw new IOException("auction receipt changed");
                data.set(path(uuid, id) + ".state", "delivering");
                return null;
            });
            try {
                Map<Integer, ItemStack> remaining = player.getInventory().addItem(pending.item.clone());
                if (!remaining.isEmpty()) {
                    plugin.getLogger().severe("[Auction] Partial delivery: " + uuid + " lot #" + id + "; manual reconciliation required.");
                    review(player, id);
                    return Outcome.REVIEW;
                }
                player.saveData(); // reduces the window; this is not an atomic/fsynced cross-file transaction
                plugin.getStore().transact(data -> {
                    Receipt latest = receipt(data, uuid, id);
                    if (latest == null || !"delivering".equals(latest.state) || !latest.item.equals(pending.item))
                        throw new IOException("auction receipt changed during delivery");
                    data.set(path(uuid, id), null);
                    return null;
                });
                player.sendMessage(ColorUtil.c("&#00E676✔ Покупка #" + id + " выдана в инвентарь."));
                return Outcome.DELIVERED;
            } catch (Exception error) {
                plugin.getLogger().severe("[Auction] Delivery needs review: " + uuid + " lot #" + id + ": " + error);
                review(player, id);
                return Outcome.REVIEW;
            }
        } catch (Exception error) { refuse(player, error); return Outcome.FAILED; }
    }
    /** OWNER/TECH.ADMIN/console only; permission is checked by KingGriefCore. */
    void admin(CommandSender sender, String[] args) {
        if (args.length < 2) { sender.sendMessage(ColorUtil.c("&e/ah review <UUID> &8| &e/ah resolve <UUID> <buy|sell> <ID> <done|retry|publish|cancel> confirm")); return; }
        UUID uuid;
        try { uuid = UUID.fromString(args[1]); if (!uuid.toString().equalsIgnoreCase(args[1])) throw new IllegalArgumentException(); }
        catch (IllegalArgumentException badId) { sender.sendMessage(ColorUtil.c("&cУкажите полный UUID игрока.")); return; }
        if ("review".equalsIgnoreCase(args[0])) {
            if (args.length != 2) { sender.sendMessage(ColorUtil.c("&e/ah review <UUID>")); return; }
            try {
                DataStore.AuctionLot listing = plugin.getStore().inspect(data -> listing(data, uuid));
                List<Receipt> entries = plugin.getStore().inspect(data -> receipts(data, uuid));
                sender.sendMessage(ColorUtil.c("&eАукцион " + uuid + ":"));
                if (listing != null) sender.sendMessage(ColorUtil.c(" &cSELL #" + listing.id + " taking: &f" + listing.item.getType().name() + " ×" + listing.item.getAmount() + " &7от " + listing.seller));
                for (Receipt r : entries) sender.sendMessage(ColorUtil.c(" &7BUY #" + r.id + " " + r.state + ": &f" + r.item.getType().name() + " ×" + r.item.getAmount()));
                if (listing == null && entries.isEmpty()) sender.sendMessage(ColorUtil.c("&7Журналов нет."));
                sender.sendMessage(ColorUtil.c("&8Проверяйте фактический инвентарь/player.dat и резервные копии перед решением."));
            } catch (Exception error) { sender.sendMessage(ColorUtil.c("&cНе удалось прочитать журнал; данные не изменены.")); }
            return;
        }
        if (!"resolve".equalsIgnoreCase(args[0]) || args.length != 6 || !"confirm".equalsIgnoreCase(args[5])) {
            sender.sendMessage(ColorUtil.c("&e/ah resolve <UUID> <buy|sell> <ID> <done|retry|publish|cancel> confirm")); return;
        }
        Player target = Bukkit.getPlayer(uuid);
        if (target != null && target.isOnline()) { sender.sendMessage(ColorUtil.c("&cИгрок должен выйти с сервера перед сверкой.")); return; }
        int id;
        try { id = Integer.parseInt(args[3]); } catch (NumberFormatException error) { id = -1; }
        if (id <= 0) { sender.sendMessage(ColorUtil.c("&cНекорректный ID.")); return; }
        final int receiptId = id;
        String operation = args[2].toLowerCase(java.util.Locale.ROOT), action = args[4].toLowerCase(java.util.Locale.ROOT);
        try {
            if ("buy".equals(operation) && ("done".equals(action) || "retry".equals(action))) {
                plugin.getStore().transact(data -> {
                    Receipt r = receipt(data, uuid, receiptId);
                    if (r == null || !"delivering".equals(r.state)) throw new IOException("receipt is not awaiting review");
                    if ("done".equals(action)) data.set(path(uuid, receiptId), null);
                    else data.set(path(uuid, receiptId) + ".state", "ready");
                    return null;
                });
            } else if ("sell".equals(operation) && "publish".equals(action)) {
                if (!plugin.getStore().finishAuctionListing(uuid, receiptId)) throw new IOException("listing not found or could not be committed");
            } else if ("sell".equals(operation) && "cancel".equals(action)) {
                plugin.getStore().transact(data -> {
                    DataStore.AuctionLot l = listing(data, uuid);
                    if (l == null || l.id != receiptId) throw new IOException("listing not found");
                    data.set(listingPath(uuid), null);
                    return null;
                });
            } else { sender.sendMessage(ColorUtil.c("&cДопустимо: buy done|retry или sell publish|cancel.")); return; }
            plugin.getLogger().warning("[Auction] Manual resolution by " + sender.getName() + ": " + uuid + " " + operation + " #" + id + " " + action);
            sender.sendMessage(ColorUtil.c("&aРешение записано. Оно не проверяет и не изменяет player.dat; сохраняйте протокол сверки."));
        } catch (Exception error) {
            plugin.getLogger().warning("[Auction] Manual resolution failed: " + error.getMessage());
            sender.sendMessage(ColorUtil.c("&cРешение не сохранено: журнал не найден, состояние изменилось или запись невозможна."));
        }
    }

    private void review(Player player, int id) {
        player.sendMessage(ColorUtil.c("&cВыдача #" + id + " остановлена для проверки; повтор заблокирован. Сообщите администрации ID и UUID &f" + player.getUniqueId()));
    }
    private void refuse(Player player, Exception error) {
        plugin.getLogger().warning("[Auction] Could not inspect or update deliveries for " + player.getUniqueId() + ": " + error.getMessage());
        player.sendMessage(ColorUtil.c("&cНе удалось проверить покупки аукциона. Предметы не выдаются повторно; обратитесь к администрации."));
    }
    @EventHandler public void join(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) return;
            try {
                int size = plugin.getStore().inspect(data -> receipts(data, player.getUniqueId()).size());
                DataStore.AuctionLot listing = plugin.getStore().inspect(data -> listing(data, player.getUniqueId()));
                if (size > 0 || listing != null) player.sendMessage(ColorUtil.c("&eУ вас есть покупки или выставление, ожидающие действий. После входа: &f/ah pending"));
            } catch (Exception error) { refuse(player, error); }
        }, 80L);
    }
}
