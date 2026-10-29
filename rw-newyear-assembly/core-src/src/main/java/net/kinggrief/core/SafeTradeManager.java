package net.kinggrief.core;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.channels.FileChannel;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/** Item-for-item trades with opt-in, immutable previews and a recoverable commit journal.
 * All Bukkit/inventory access is on the server thread. This does not protect against
 * third-party plugins that directly rewrite player data, or guarantee a power-loss fsync
 * for Paper's player files. Never remove pending journals while a trade is unresolved. */
public final class SafeTradeManager implements Listener {
    private static final int STORAGE = 36, MAX_OFFERS = 9;
    private static final int MAX_ITEM_BYTES = 65536, MAX_JOURNAL_BYTES = 1024 * 1024;
    private static final long MAGIC = 0x4b47545241444531L; // KGTRADE1
    private final KingGriefCore plugin;
    private final Path directory;
    private final Map<UUID, Invite> invites = new HashMap<UUID, Invite>();
    private final Map<UUID, Session> sessions = new HashMap<UUID, Session>();
    private final Map<UUID, Journal> pending = new HashMap<UUID, Journal>();
    private final Map<UUID, String> damaged = new HashMap<UUID, String>();
    private Method encode, decode;
    private boolean available = true, shuttingDown;

    private static final class Invite {
        final UUID sender;
        final long expires;
        Invite(UUID sender) { this.sender = sender; this.expires = System.currentTimeMillis() + 60000L; }
    }
    private static final class Session {
        final UUID a, b;
        final Map<Integer, ItemStack> offerA = new LinkedHashMap<Integer, ItemStack>();
        final Map<Integer, ItemStack> offerB = new LinkedHashMap<Integer, ItemStack>();
        Inventory viewA, viewB;
        boolean readyA, readyB, scheduled, committing, ended;
        Session(UUID a, UUID b) { this.a = a; this.b = b; }
        UUID other(UUID id) { return a.equals(id) ? b : a; }
        Map<Integer, ItemStack> offers(UUID id) { return a.equals(id) ? offerA : offerB; }
    }
    /** Marker holder: contents are only copies, never escrowed items. */
    public static final class TradeHolder implements InventoryHolder {
        private final Session session;
        private final UUID viewer;
        private Inventory inventory;
        private TradeHolder(Session session, UUID viewer) { this.session = session; this.viewer = viewer; }
        @Override public Inventory getInventory() { return inventory; }
    }
    private static final class Journal {
        UUID id, a, b;
        boolean doneA, doneB;
        byte[][] afterA, afterB;
        Path path;
    }

    public SafeTradeManager(KingGriefCore plugin) {
        this.plugin = plugin;
        this.directory = plugin.getDataFolder().toPath().resolve("safe-trades").resolve("pending");
    }

    public void start() {
        try {
            Files.createDirectories(directory);
            try {
                // Paper 1.21.4 serializes full item components, including PDC.
                encode = ItemStack.class.getMethod("serializeAsBytes");
                decode = ItemStack.class.getMethod("deserializeBytes", byte[].class);
            } catch (Exception missingApi) {
                available = false;
                plugin.getLogger().severe("[Trade] Paper item serialization unavailable: " + missingApi);
            }
            // Still scan filenames if serialization is unavailable: affected
            // players must be blocked rather than entering with unresolved data.
            loadJournals();
        } catch (Exception error) {
            available = false;
            plugin.getLogger().severe("[Trade] Cannot inspect the recovery journal; stopping to protect player inventories: " + error);
            Bukkit.shutdown();
        }
    }

    void recoverOnlinePlayers() { for (Player player : Bukkit.getOnlinePlayers()) recover(player); }

    public void shutdown() {
        shuttingDown = true;
        for (Session session : new ArrayList<Session>(sessions.values())) cancelSession(session, "Сервер выключается.");
        invites.clear();
        // A player whose journal could not be applied must not continue playing
        // through a plugin reload with an unresolved inventory snapshot.
        for (Player p : Bukkit.getOnlinePlayers()) if (isBlocked(p.getUniqueId())) {
            p.kickPlayer(ColorUtil.c("&cОбмен ожидает восстановления. Сообщите администратору."));
        }
    }

    public boolean handleCommand(Player player, String[] args) {
        if (!available || isBlocked(player.getUniqueId())) {
            player.sendMessage(ColorUtil.c("&cОбмен временно недоступен: проверьте журнал восстановления у администратора."));
            return true;
        }
        if (args.length == 0) {
            player.sendMessage(ColorUtil.c("&e/trade <ник> &7— приглашение; &e/trade accept <ник> &7— принять; &e/trade deny &7— отказ."));
            return true;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        if ("deny".equals(sub)) {
            invites.remove(player.getUniqueId());
            player.sendMessage(ColorUtil.c("&7Приглашение на обмен отклонено."));
            return true;
        }
        if ("cancel".equals(sub)) {
            Session s = sessions.get(player.getUniqueId());
            if (s != null) cancelSession(s, "Обмен отменён игроком " + player.getName() + ".");
            else player.sendMessage(ColorUtil.c("&7Активного обмена нет."));
            return true;
        }
        if ("accept".equals(sub)) {
            Invite invite = invites.get(player.getUniqueId());
            if (invite == null || invite.expires < System.currentTimeMillis()) {
                invites.remove(player.getUniqueId());
                player.sendMessage(ColorUtil.c("&cДействующего приглашения нет."));
                return true;
            }
            Player from = Bukkit.getPlayer(invite.sender);
            if (from == null || (args.length > 1 && !from.getName().equalsIgnoreCase(args[1]))) {
                player.sendMessage(ColorUtil.c("&cИгрок не найден или приглашение от другого игрока."));
                return true;
            }
            if (!canTrade(player) || !canTrade(from)) return true;
            invites.remove(player.getUniqueId());
            open(from, player);
            return true;
        }
        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null || target.equals(player)) {
            player.sendMessage(ColorUtil.c("&cУкажите ник другого игрока, который сейчас в сети."));
            return true;
        }
        if (!canTrade(player) || !canTrade(target)) return true;
        Invite previous = invites.get(target.getUniqueId());
        if (previous != null && previous.expires >= System.currentTimeMillis()) {
            player.sendMessage(ColorUtil.c("&eУ игрока уже есть действующее приглашение."));
            return true;
        }
        invites.put(target.getUniqueId(), new Invite(player.getUniqueId()));
        player.sendMessage(ColorUtil.c("&aПриглашение отправлено игроку &f" + target.getName() + "&a на 60 секунд."));
        target.sendMessage(ColorUtil.c("&#80D8FF🤝 " + player.getName() + " предлагает обмен. &e/trade accept " + player.getName() + " &7или &e/trade deny"));
        return true;
    }

    private boolean canTrade(Player p) {
        if (!p.isOnline() || p.isDead() || !plugin.getStore().isAuthenticated(p)
                || plugin.getModeration().isFrozenOrAscending(p) || isBlocked(p.getUniqueId())
                || (plugin.getBuyOrders() != null && plugin.getBuyOrders().isBlocked(p.getUniqueId()))
                || sessions.containsKey(p.getUniqueId())) {
            p.sendMessage(ColorUtil.c("&cСейчас нельзя начать обмен: проверьте вход, состояние игрока и другие сделки."));
            return false;
        }
        if (!empty(p.getItemOnCursor())) {
            p.sendMessage(ColorUtil.c("&cУберите предмет с курсора перед обменом."));
            return false;
        }
        return true;
    }

    private void open(Player a, Player b) {
        Session s = new Session(a.getUniqueId(), b.getUniqueId());
        sessions.put(s.a, s); sessions.put(s.b, s);
        TradeHolder ha = new TradeHolder(s, s.a), hb = new TradeHolder(s, s.b);
        s.viewA = ha.inventory = Bukkit.createInventory(ha, 54, ColorUtil.c("&#80D8FFОбмен с " + b.getName()));
        s.viewB = hb.inventory = Bukkit.createInventory(hb, 54, ColorUtil.c("&#80D8FFОбмен с " + a.getName()));
        refresh(s);
        a.openInventory(s.viewA); b.openInventory(s.viewB);
        a.sendMessage(ColorUtil.c("&7ЛКМ по предмету в своём инвентаре добавляет весь стак. Подтверждение сбрасывается при изменении."));
        b.sendMessage(ColorUtil.c("&7ЛКМ по предмету в своём инвентаре добавляет весь стак. Подтверждение сбрасывается при изменении."));
    }

    private void refresh(Session s) {
        render(s, s.a, s.viewA, s.offerA, s.offerB, s.readyA, s.readyB);
        render(s, s.b, s.viewB, s.offerB, s.offerA, s.readyB, s.readyA);
    }
    private void render(Session s, UUID viewer, Inventory inv, Map<Integer, ItemStack> own,
                        Map<Integer, ItemStack> other, boolean ready, boolean otherReady) {
        if (inv == null) return;
        inv.clear();
        ItemStack filler = ItemFactory.icon(Material.BLACK_STAINED_GLASS_PANE, "&8 ");
        for (int i = 0; i < 54; i++) inv.setItem(i, filler);
        inv.setItem(4, ItemFactory.icon(Material.BOOK, "&#80D8FFВаше предложение ↑", "&7Второй игрок ↓", "&7ЛКМ по инвентарю — добавить/убрать стак"));
        for (int i = 9; i <= 17; i++) inv.setItem(i, null);
        for (int i = 27; i <= 35; i++) inv.setItem(i, null);
        int i = 9; for (ItemStack item : own.values()) inv.setItem(i++, item.clone());
        i = 27; for (ItemStack item : other.values()) inv.setItem(i++, item.clone());
        inv.setItem(45, ItemFactory.icon(ready ? Material.LIME_DYE : Material.GRAY_DYE,
                ready ? "&a✔ Вы подтвердили" : "&eПодтвердить предложение", "&7Любое изменение сбрасывает оба подтверждения"));
        inv.setItem(49, ItemFactory.icon(otherReady ? Material.LIME_WOOL : Material.YELLOW_WOOL,
                otherReady ? "&aВторой игрок подтвердил" : "&eОжидание второго игрока"));
        inv.setItem(53, ItemFactory.icon(Material.BARRIER, "&cОтменить обмен"));
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onClick(InventoryClickEvent event) {
        Inventory top = event.getView().getTopInventory();
        if (!(top.getHolder() instanceof TradeHolder)) return;
        boolean alreadyCancelled = event.isCancelled();
        event.setCancelled(true); // including number keys, double-click, drop and shift-click
        if (alreadyCancelled || !(event.getWhoClicked() instanceof Player)) return;
        Player p = (Player)event.getWhoClicked();
        TradeHolder h = (TradeHolder)top.getHolder();
        Session s = h.session;
        if (!h.viewer.equals(p.getUniqueId()) || sessions.get(h.viewer) != s || s.ended || s.scheduled || s.committing) return;
        if (event.getClick() != ClickType.LEFT) return;
        if (!empty(event.getCursor())) {
            p.sendMessage(ColorUtil.c("&cУберите предмет с курсора.")); return;
        }
        if (event.getClickedInventory() == top) {
            if (event.getRawSlot() == 53) { cancelSession(s, "Обмен отменён игроком " + p.getName() + "."); return; }
            if (event.getRawSlot() != 45) return;
            if (s.offerA.isEmpty() && s.offerB.isEmpty()) {
                p.sendMessage(ColorUtil.c("&eДобавьте хотя бы один предмет.")); return;
            }
            if (!offersUnchanged(s)) { resetOffers(s); return; }
            if (s.a.equals(h.viewer)) s.readyA = !s.readyA; else s.readyB = !s.readyB;
            refresh(s);
            if (s.readyA && s.readyB) {
                s.scheduled = true;
                Bukkit.getScheduler().runTask(plugin, new Runnable() { @Override public void run() { commit(s); } });
            }
            return;
        }
        if (event.getClickedInventory() == null || !event.getClickedInventory().equals(p.getInventory())) return;
        int slot = event.getSlot();
        if (slot < 0 || slot >= STORAGE) return;
        Map<Integer, ItemStack> offers = s.offers(h.viewer);
        if (offers.containsKey(Integer.valueOf(slot))) offers.remove(Integer.valueOf(slot));
        else {
            ItemStack item = p.getInventory().getStorageContents()[slot];
            if (empty(item)) return;
            if ("menu_star".equals(ItemFactory.getCustomId(item))) {
                p.sendMessage(ColorUtil.c("&cЗвезду меню нельзя передать.")); return;
            }
            if (offers.size() >= MAX_OFFERS) { p.sendMessage(ColorUtil.c("&cМожно предложить до 9 стаков.")); return; }
            offers.put(Integer.valueOf(slot), item.clone());
        }
        s.readyA = false; s.readyB = false;
        refresh(s);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDrag(InventoryDragEvent e) {
        if (e.getView().getTopInventory().getHolder() instanceof TradeHolder) e.setCancelled(true);
    }
    @EventHandler public void onClose(InventoryCloseEvent e) {
        if (!(e.getInventory().getHolder() instanceof TradeHolder)) return;
        Session s = ((TradeHolder)e.getInventory().getHolder()).session;
        if (!s.committing && !s.ended) cancelSession(s, "Окно обмена закрыто.");
    }
    @EventHandler public void onQuit(PlayerQuitEvent e) {
        UUID id = e.getPlayer().getUniqueId();
        Session s = sessions.get(id);
        if (s != null && !s.committing) cancelSession(s, e.getPlayer().getName() + " вышел с сервера.");
        invites.remove(id);
        Iterator<Map.Entry<UUID, Invite>> it = invites.entrySet().iterator();
        while (it.hasNext()) if (id.equals(it.next().getValue().sender)) it.remove();
    }
    @EventHandler(priority = EventPriority.LOWEST)
    public void onJoin(PlayerJoinEvent event) { recover(event.getPlayer()); }

    private boolean offersUnchanged(Session s) {
        Player a = Bukkit.getPlayer(s.a), b = Bukkit.getPlayer(s.b);
        return a != null && b != null && a.isOnline() && b.isOnline() && !a.isDead() && !b.isDead()
                && plugin.getStore().isAuthenticated(a) && plugin.getStore().isAuthenticated(b)
                && !plugin.getModeration().isFrozenOrAscending(a) && !plugin.getModeration().isFrozenOrAscending(b)
                && empty(a.getItemOnCursor()) && empty(b.getItemOnCursor())
                && matches(a, s.offerA) && matches(b, s.offerB)
                && a.getOpenInventory().getTopInventory() == s.viewA
                && b.getOpenInventory().getTopInventory() == s.viewB;
    }
    private boolean matches(Player p, Map<Integer, ItemStack> offers) {
        ItemStack[] storage = p.getInventory().getStorageContents();
        for (Map.Entry<Integer, ItemStack> e : offers.entrySet()) {
            int slot = e.getKey().intValue();
            if (slot < 0 || slot >= storage.length || !e.getValue().equals(storage[slot])) return false;
        }
        return true;
    }
    private void resetOffers(Session s) {
        s.offerA.clear(); s.offerB.clear(); s.readyA = false; s.readyB = false; s.scheduled = false;
        notify(s, "&cИнвентарь или состояние изменились: предложения и подтверждения сброшены.");
        refresh(s);
    }
    private void commit(Session s) {
        if (s.ended || s.committing || !s.readyA || !s.readyB || !offersUnchanged(s) || !available) {
            if (!s.ended) resetOffers(s);
            return;
        }
        Player a = Bukkit.getPlayer(s.a), b = Bukkit.getPlayer(s.b);
        try {
            ItemStack[] afterA = exchanged(a, s.offerA, s.offerB);
            ItemStack[] afterB = exchanged(b, s.offerB, s.offerA);
            if (afterA == null || afterB == null) {
                s.readyA = false; s.readyB = false; s.scheduled = false;
                notify(s, "&cНедостаточно места для всего обмена. Освободите ячейки и подтвердите снова.");
                refresh(s); return;
            }
            Journal j = new Journal();
            j.id = UUID.randomUUID(); j.a = s.a; j.b = s.b;
            j.afterA = encodeStorage(afterA); j.afterB = encodeStorage(afterB);
            j.path = directory.resolve(j.a + "_" + j.b + "_" + j.id + ".trade");
            // Durable intent precedes every inventory mutation. Once prepared the
            // trade must be completed, even if a process dies between the two saves.
            write(j);
            pending.put(j.a, j); pending.put(j.b, j);
            s.committing = true;
            boolean okA = apply(j, a), okB = apply(j, b);
            s.ended = true; sessions.remove(s.a); sessions.remove(s.b);
            if (okA && okB) {
                cleanup(j);
                a.closeInventory(); b.closeInventory();
                a.sendMessage(ColorUtil.c("&a✔ Обмен завершён."));
                b.sendMessage(ColorUtil.c("&a✔ Обмен завершён."));
            } else {
                if (!okA && a.isOnline()) a.kickPlayer(ColorUtil.c("&cОбмен ожидает восстановления. Обратитесь к администратору."));
                if (!okB && b.isOnline()) b.kickPlayer(ColorUtil.c("&cОбмен ожидает восстановления. Обратитесь к администратору."));
                if (okA && a.isOnline()) a.closeInventory();
                if (okB && b.isOnline()) b.closeInventory();
            }
        } catch (Exception error) {
            plugin.getLogger().severe("[Trade] Could not prepare trade, no items moved: " + error);
            s.readyA = false; s.readyB = false; s.scheduled = false;
            notify(s, "&cНе удалось безопасно подготовить обмен. Предметы не перемещены.");
            refresh(s);
        }
    }

    private ItemStack[] exchanged(Player p, Map<Integer, ItemStack> own, Map<Integer, ItemStack> incoming) {
        ItemStack[] source = p.getInventory().getStorageContents();
        ItemStack[] copy = new ItemStack[STORAGE];
        for (int i = 0; i < STORAGE; i++) copy[i] = empty(source[i]) ? null : source[i].clone();
        for (Integer slot : own.keySet()) copy[slot.intValue()] = null;
        Inventory trial = Bukkit.createInventory(null, STORAGE);
        trial.setContents(copy);
        for (ItemStack item : incoming.values()) {
            if (!trial.addItem(item.clone()).isEmpty()) return null;
        }
        return trial.getContents();
    }
    private static boolean empty(ItemStack item) { return item == null || item.getType() == Material.AIR || item.getAmount() <= 0; }
    private void notify(Session s, String message) {
        Player a = Bukkit.getPlayer(s.a), b = Bukkit.getPlayer(s.b);
        if (a != null) a.sendMessage(ColorUtil.c(message));
        if (b != null) b.sendMessage(ColorUtil.c(message));
    }
    private void cancelSession(Session s, String reason) {
        if (s == null || s.ended || s.committing) return;
        s.ended = true; sessions.remove(s.a); sessions.remove(s.b);
        notify(s, "&7" + reason + " Предметы остались у владельцев.");
        // Closing during an InventoryClick/InventoryCloseEvent is unsafe; defer it.
        if (!shuttingDown) Bukkit.getScheduler().runTask(plugin, new Runnable() {
            @Override public void run() {
                Player a = Bukkit.getPlayer(s.a), b = Bukkit.getPlayer(s.b);
                if (a != null && a.getOpenInventory().getTopInventory() == s.viewA) a.closeInventory();
                if (b != null && b.getOpenInventory().getTopInventory() == s.viewB) b.closeInventory();
            }
        });
    }

    private byte[][] encodeStorage(ItemStack[] storage) throws Exception {
        if (storage.length != STORAGE) throw new IOException("unexpected storage size");
        byte[][] out = new byte[STORAGE][];
        for (int i = 0; i < STORAGE; i++) if (!empty(storage[i])) {
            byte[] data = (byte[])encode.invoke(storage[i]);
            if (data.length == 0 || data.length > MAX_ITEM_BYTES) throw new IOException("item in slot " + i + " is too large");
            out[i] = data;
        }
        return out;
    }
    private ItemStack[] decodeStorage(byte[][] bytes) throws Exception {
        if (bytes == null || bytes.length != STORAGE) throw new IOException("invalid storage snapshot");
        ItemStack[] out = new ItemStack[STORAGE];
        for (int i = 0; i < STORAGE; i++) if (bytes[i] != null) {
            out[i] = (ItemStack)decode.invoke(null, (Object)bytes[i]);
            if (empty(out[i])) throw new IOException("invalid item in snapshot");
        }
        return out;
    }

    private void write(Journal j) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream data = new DataOutputStream(bytes);
        data.writeLong(MAGIC); writeUuid(data, j.id); writeUuid(data, j.a); writeUuid(data, j.b);
        data.writeBoolean(j.doneA); data.writeBoolean(j.doneB);
        writeItems(data, j.afterA); writeItems(data, j.afterB); data.flush();
        byte[] body = bytes.toByteArray();
        if (body.length + 32 > MAX_JOURNAL_BYTES) throw new IOException("inventory snapshots exceed journal size limit");
        byte[] checksum = MessageDigest.getInstance("SHA-256").digest(body);
        Path tmp = Files.createTempFile(directory, ".trade-", ".tmp");
        try {
            try (FileChannel channel = FileChannel.open(tmp, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING)) {
                java.nio.ByteBuffer all = java.nio.ByteBuffer.allocate(body.length + checksum.length).put(body).put(checksum);
                all.flip(); while (all.hasRemaining()) channel.write(all);
                channel.force(true);
            }
            try { Files.move(tmp, j.path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
            catch (AtomicMoveNotSupportedException noAtomicMove) { throw new IOException("atomic journal rename unavailable", noAtomicMove); }
            forceDirectory();
        } finally {
            try { Files.deleteIfExists(tmp); }
            catch (IOException error) { plugin.getLogger().warning("[Trade] Could not remove temporary journal " + tmp + ": " + error); }
        }
    }
    private void forceDirectory() {
        try (FileChannel channel = FileChannel.open(directory, StandardOpenOption.READ)) { channel.force(true); }
        catch (Exception error) { plugin.getLogger().warning("[Trade] Directory fsync unavailable: " + error.getMessage()); }
    }
    private static void writeUuid(DataOutputStream out, UUID id) throws IOException {
        out.writeLong(id.getMostSignificantBits()); out.writeLong(id.getLeastSignificantBits());
    }
    private static UUID readUuid(DataInputStream in) throws IOException { return new UUID(in.readLong(), in.readLong()); }
    private static void writeItems(DataOutputStream out, byte[][] items) throws IOException {
        if (items == null || items.length != STORAGE) throw new IOException("invalid item snapshot");
        for (byte[] item : items) {
            if (item == null) out.writeInt(-1);
            else { if (item.length <= 0 || item.length > MAX_ITEM_BYTES) throw new IOException("invalid item size"); out.writeInt(item.length); out.write(item); }
        }
    }
    private static byte[][] readItems(DataInputStream in) throws IOException {
        byte[][] items = new byte[STORAGE][];
        for (int i = 0; i < STORAGE; i++) {
            int n = in.readInt();
            if (n == -1) continue;
            if (n <= 0 || n > MAX_ITEM_BYTES || n > in.available()) throw new IOException("invalid item length");
            items[i] = new byte[n]; in.readFully(items[i]);
        }
        return items;
    }
    private Journal read(Path path) throws Exception {
        long size = Files.size(path);
        if (size < 100 || size > MAX_JOURNAL_BYTES) throw new IOException("invalid journal size");
        byte[] all = Files.readAllBytes(path), body = Arrays.copyOf(all, all.length - 32);
        byte[] expected = Arrays.copyOfRange(all, all.length - 32, all.length);
        if (!MessageDigest.isEqual(expected, MessageDigest.getInstance("SHA-256").digest(body))) throw new IOException("journal checksum mismatch");
        DataInputStream in = new DataInputStream(new ByteArrayInputStream(body));
        if (in.readLong() != MAGIC) throw new IOException("unknown journal version");
        Journal j = new Journal(); j.id = readUuid(in); j.a = readUuid(in); j.b = readUuid(in);
        if (j.a.equals(j.b)) throw new IOException("duplicate participants");
        j.doneA = in.readBoolean(); j.doneB = in.readBoolean();
        j.afterA = readItems(in); j.afterB = readItems(in); j.path = path;
        if (in.available() != 0 || !path.getFileName().toString().equals(j.a + "_" + j.b + "_" + j.id + ".trade")) throw new IOException("journal identity mismatch");
        // Decode before allowing a player back in. Corruption or incompatible item
        // formats should block the affected accounts instead of overwriting them.
        decodeStorage(j.afterA); decodeStorage(j.afterB);
        return j;
    }
    private void loadJournals() throws Exception {
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(directory, "*.trade")) {
            for (Path path : stream) {
                try {
                    Journal j = read(path);
                    if ((!j.doneA && pending.containsKey(j.a)) || (!j.doneB && pending.containsKey(j.b))) throw new IOException("overlapping pending trades");
                    if (!j.doneA) pending.put(j.a, j);
                    if (!j.doneB) pending.put(j.b, j);
                    if (j.doneA && j.doneB) cleanup(j);
                    else plugin.getLogger().warning("[Trade] Pending trade " + j.id + " will be recovered as players join.");
                } catch (Exception error) {
                    available = false;
                    String[] parts = path.getFileName().toString().split("_");
                    boolean identified = false;
                    if (parts.length == 3) try {
                        UUID first = UUID.fromString(parts[0]), second = UUID.fromString(parts[1]);
                        damaged.put(first, path.toString()); damaged.put(second, path.toString()); identified = true;
                    } catch (IllegalArgumentException invalidName) { /* fail closed below */ }
                    plugin.getLogger().severe("[Trade] Cannot read " + path + "; new trades disabled and affected players blocked: " + error);
                    if (!identified) {
                        plugin.getLogger().severe("[Trade] Journal participants cannot be identified; stopping to protect inventories.");
                        Bukkit.shutdown();
                    }
                }
            }
        }
    }
    private boolean isBlocked(UUID id) { return pending.containsKey(id) || damaged.containsKey(id); }
    boolean isBlockedForRecovery(UUID id) { return isBlocked(id); }
    boolean isBusy(UUID id) { return isBlocked(id) || sessions.containsKey(id); }
    private void recover(Player player) {
        UUID id = player.getUniqueId();
        if (damaged.containsKey(id)) {
            player.kickPlayer(ColorUtil.c("&cЖурнал обмена повреждён. Обратитесь к администратору.")); return;
        }
        Journal j = pending.get(id);
        if (j == null) return;
        if (plugin.getBuyOrders() != null && plugin.getBuyOrders().isBlocked(id)) {
            player.kickPlayer(ColorUtil.c("&cНесколько операций ожидают восстановления. Обратитесь к администратору.")); return;
        }
        if (!apply(j, player)) {
            player.kickPlayer(ColorUtil.c("&cНе удалось восстановить обмен. Обратитесь к администратору.")); return;
        }
        player.sendMessage(ColorUtil.c("&a✔ Незавершённый обмен восстановлен."));
        if (j.doneA && j.doneB) cleanup(j);
    }
    private boolean apply(Journal j, Player p) {
        UUID id = p.getUniqueId();
        boolean a = j.a.equals(id);
        if ((!a && !j.b.equals(id)) || (a ? j.doneA : j.doneB)) return false;
        try {
            ItemStack[] after = decodeStorage(a ? j.afterA : j.afterB);
            p.getInventory().setStorageContents(after);
            p.saveData();
            if (a) j.doneA = true; else j.doneB = true;
            try { write(j); }
            catch (Exception error) { if (a) j.doneA = false; else j.doneB = false; throw error; }
            pending.remove(id);
            p.updateInventory();
            return true;
        } catch (Exception error) {
            plugin.getLogger().severe("[Trade] Recovery pending for " + id + " in " + j.path + ": " + error);
            return false;
        }
    }
    private void cleanup(Journal j) {
        if (!j.doneA || !j.doneB) return;
        try { Files.deleteIfExists(j.path); forceDirectory(); }
        catch (Exception error) { plugin.getLogger().warning("[Trade] Completed journal could not be removed: " + error); }
    }
}
