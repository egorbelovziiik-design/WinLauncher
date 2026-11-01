package net.kinggrief.core;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockDropItemEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.ItemMergeEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.IOException;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Opt-in, best-effort sale of freshly mined, ordinary resources. Items are first
 * delivered normally. We only sell a bounded observed increase in exact storage
 * or offhand slots, and abandon the batch if saleable slots change unexpectedly.
 * No item is removed unless the entire money/progress batch was saved.
 *
 * Player data and Core data are separate stores: a crash between the credit and
 * player-data save can still duplicate resources. This is not an atomic trade.
 */
final class AutoBuyerManager implements Listener {
    private static final int MAX_PLAYERS = 256, MAX_TRACKED_ITEMS = 4096, MAX_BATCH = 8, MAX_PER_MATERIAL = 4096;
    private static final long ITEM_TTL_NANOS = 60_000_000_000L, PENDING_TTL_NANOS = 30_000_000_000L;
    private static final class TrackedItem {
        final UUID owner;
        final Material material;
        final int amount;
        final long created;
        TrackedItem(UUID owner, Material material, int amount) { this.owner=owner; this.material=material; this.amount=amount; created=System.nanoTime(); }
    }
    private static final class Dropped {
        final Item item;
        final ItemStack original;
        Dropped(Item item) { this.item=item; this.original=item.getItemStack().clone(); }
    }
    private static final class Pending {
        final UUID player;
        final long created = System.nanoTime();
        final Map<Material, Integer> amounts = new EnumMap<Material, Integer>(Material.class);
        final int[] addedToSlot;
        ItemStack[] expected;
        Pending(UUID player, ItemStack[] expected) { this.player=player; this.expected=expected; addedToSlot=new int[expected.length]; }
    }
    private static final class Pickup {
        final Player player;
        final EntityPickupItemEvent event;
        final Material material;
        final int amount;
        final ItemStack[] before;
        Pickup(Player player, EntityPickupItemEvent event, Material material, int amount, ItemStack[] before) {
            this.player=player; this.event=event; this.material=material; this.amount=amount; this.before=before;
        }
    }
    private static final class Sale {
        final Player player;
        final ItemStack[] before, after;
        final long count;
        final double earned;
        Sale(Player player, ItemStack[] before, ItemStack[] after, long count, double earned) {
            this.player=player; this.before=before; this.after=after; this.count=count; this.earned=earned;
        }
    }

    private final KingGriefCore plugin;
    private final Map<UUID, TrackedItem> tracked = new LinkedHashMap<UUID, TrackedItem>();
    private final Map<UUID, Pending> pending = new LinkedHashMap<UUID, Pending>();
    private final Map<UUID, Pickup> pickups = new LinkedHashMap<UUID, Pickup>();
    private final Map<UUID, Long> toggles = new LinkedHashMap<UUID, Long>();
    private long lastToggle;
    private BukkitRunnable timer;

    AutoBuyerManager(KingGriefCore plugin) { this.plugin=plugin; }
    void start() {
        timer = new BukkitRunnable() { @Override public void run() { flush(); prune(); } };
        timer.runTaskTimer(plugin, 40L, 40L);
    }
    void shutdown() {
        if (timer != null) timer.cancel();
        // Pending batches are only pointers to items already delivered to players.
        // Do not sell while the server is shutting down or players are being kicked.
        tracked.clear(); pending.clear(); pickups.clear(); toggles.clear();
    }

    private boolean allowed(Player p) {
        return p != null && p.isOnline() && !p.isDead()
                && (p.getGameMode() == GameMode.SURVIVAL || p.getGameMode() == GameMode.ADVENTURE)
                && plugin.getStore().isAuthenticated(p) && !plugin.getStore().isQuickFrozen(p)
                && !plugin.getModeration().isFrozenOrAscending(p);
    }
    boolean enabled(Player p) {
        if (p == null) return false;
        try { return plugin.getStore().inspect(data -> data.getBoolean("autoBuyer." + p.getUniqueId() + ".enabled", false)); }
        catch (Exception error) { return false; }
    }
    void command(Player p, String[] args) {
        String sub = args.length == 0 ? "status" : args[0].toLowerCase(Locale.ROOT);
        if (args.length > 1) { usage(p); return; }
        if ("status".equals(sub)) {
            p.sendMessage(ColorUtil.c("&#80D8FFАвтоскупка добычи: " + (enabled(p) ? "&aВКЛЮЧЕНА" : "&cВЫКЛЮЧЕНА")));
            p.sendMessage(ColorUtil.c("&7Учитываются только новые обычные ресурсы добычи, попавшие в инвентарь; старый запас сохраняется."));
            p.sendMessage(ColorUtil.c("&7Если слоты ресурсов изменились, очередь переполнена или запись не удалась, ресурсы остаются у вас."));
            usage(p); return;
        }
        if ("prices".equals(sub)) {
            p.sendMessage(ColorUtil.c("&#80D8FFБазовые цены за 1 шт. (до множителя скупщика и ранга):"));
            p.sendMessage(ColorUtil.c("&7Уголь &f25&7, железо &f70&7, золото &f110&7, алмаз &f350&7, изумруд &f420&7, лазурит &f35&7."));
            p.sendMessage(ColorUtil.c("&7Сырое железо &f40&7, сырое золото &f60&7, сырая медь &f15&7, медь &f25&7, редстоун &f20&7, кварц &f25&7."));
            p.sendMessage(ColorUtil.c("&7Самородок золота &f10&7, незеритовый скрап/древние обломки &f500&7. Шёлковая руда не продаётся."));
            return;
        }
        if (!"on".equals(sub) && !"off".equals(sub)) { usage(p); return; }
        if (!allowed(p)) { p.sendMessage(ColorUtil.c("&cНастройка недоступна во время проверки или заморозки.")); return; }
        boolean requested = "on".equals(sub);
        if (enabled(p) == requested) { p.sendMessage(ColorUtil.c(requested ? "&aАвтоскупка уже включена." : "&eАвтоскупка уже выключена.")); return; }
        long now=System.nanoTime(); Long previous=toggles.get(p.getUniqueId());
        if ((previous != null && now-previous.longValue() < 1_000_000_000L) || (lastToggle != 0 && now-lastToggle < 200_000_000L)) {
            p.sendMessage(ColorUtil.c("&eПодождите немного перед изменением настройки.")); return;
        }
        toggles.put(p.getUniqueId(), Long.valueOf(now)); lastToggle=now;
        while (toggles.size() > MAX_PLAYERS) toggles.remove(toggles.keySet().iterator().next());
        try {
            plugin.getStore().transact(data -> { data.set("autoBuyer." + p.getUniqueId() + ".enabled", Boolean.valueOf(requested)); return null; });
            if (!requested) forget(p.getUniqueId());
            p.sendMessage(ColorUtil.c(requested ? "&aАвтоскупка включена. Цены: /autosell prices" : "&eАвтоскупка выключена. Ожидающие ресурсы останутся у вас."));
        } catch (Exception error) {
            plugin.getLogger().warning("[AutoBuyer] Setting was not saved for " + p.getName() + ": " + error.getMessage());
            p.sendMessage(ColorUtil.c("&cНастройку не удалось сохранить; она не изменена."));
        }
    }
    private void usage(Player p) { p.sendMessage(ColorUtil.c("&e/autosell <on|off|status|prices>")); }

    private static int price(Material type) {
        switch (type) {
            case COAL: return 25;
            case IRON_INGOT: return 70;
            case GOLD_INGOT: return 110;
            case DIAMOND: return 350;
            case EMERALD: return 420;
            case LAPIS_LAZULI: return 35;
            case RAW_IRON: return 40;
            case RAW_GOLD: return 60;
            case RAW_COPPER: return 15;
            case COPPER_INGOT: return 25;
            case REDSTONE: return 20;
            case QUARTZ: return 25;
            case GOLD_NUGGET: return 10;
            case NETHERITE_SCRAP: case ANCIENT_DEBRIS: return 500;
            default: return 0;
        }
    }
    private static boolean ore(Material type) {
        return type != null && (type.name().endsWith("_ORE") || type == Material.ANCIENT_DEBRIS || type == Material.GILDED_BLACKSTONE);
    }
    private static boolean plain(ItemStack stack) {
        return stack != null && !stack.getType().isAir() && stack.getAmount() > 0 && !stack.hasItemMeta() && price(stack.getType()) > 0;
    }
    private static boolean empty(ItemStack stack) { return stack == null || stack.getType().isAir() || stack.getAmount() <= 0; }
    private static ItemStack[] snapshot(Player p) {
        ItemStack[] contents=p.getInventory().getStorageContents();
        // Minecraft can merge a normal pickup into the offhand before storage.
        // Include it in the exact before/after check rather than selling old items.
        ItemStack[] out=new ItemStack[contents.length+1];
        for (int i=0;i<contents.length;i++) out[i]=empty(contents[i])?null:contents[i].clone();
        ItemStack offhand=p.getInventory().getItemInOffHand();
        out[contents.length]=empty(offhand)?null:offhand.clone();
        return out;
    }
    private static boolean same(ItemStack[] a, ItemStack[] b) {
        if (a.length != b.length) return false;
        for (int i=0;i<a.length;i++) {
            // Unrelated tools can wear on each break. Ignore their changes, but
            // never ignore the appearance/disappearance of a saleable stack.
            if (!plain(a[i]) && !plain(b[i])) continue;
            if (empty(a[i]) || empty(b[i]) || !a[i].equals(b[i])) return false;
        }
        return true;
    }
    /** Saleable slots must be unchanged except additions of this ordinary resource. */
    private static boolean onlyAddition(ItemStack[] before, ItemStack[] after, Material type, int amount) {
        if (before.length != after.length || amount <= 0) return false;
        long added=0;
        for (int i=0;i<before.length;i++) {
            ItemStack a=before[i], b=after[i];
            if (!plain(a) && !plain(b)) continue;
            if (empty(a) && plain(b) && b.getType()==type) { added+=b.getAmount(); continue; }
            if (!empty(a) && !empty(b) && a.equals(b)) continue;
            if (plain(a) && plain(b) && a.getType()==type && a.isSimilar(b) && b.getAmount()>=a.getAmount()) {
                added+=b.getAmount()-a.getAmount(); continue;
            }
            return false;
        }
        return added==amount;
    }
    private void invalidateIfChanged(Player p, ItemStack[] current) {
        Pending batch=pending.get(p.getUniqueId());
        if (batch != null && !same(batch.expected,current)) pending.remove(p.getUniqueId());
    }
    private void record(Player p, ItemStack[] before, ItemStack[] after, Material material, int amount) {
        if (!allowed(p) || !enabled(p) || price(material) <= 0) return;
        invalidateIfChanged(p,before);
        if (!onlyAddition(before,after,material,amount)) return;
        Pending batch=pending.get(p.getUniqueId());
        if (batch == null) {
            if (pending.size() >= MAX_PLAYERS) return; // the actual items were already delivered
            batch=new Pending(p.getUniqueId(),after); pending.put(p.getUniqueId(),batch);
        }
        int old=batch.amounts.containsKey(material)?batch.amounts.get(material).intValue():0;
        int allowance=Math.min(amount,MAX_PER_MATERIAL-old), recorded=0;
        for (int i=0;i<after.length && recorded<allowance;i++) {
            if (!plain(after[i]) || after[i].getType()!=material) continue;
            int was=empty(before[i])?0:before[i].getAmount();
            int added=Math.max(0,after[i].getAmount()-was);
            int take=Math.min(added,allowance-recorded);
            batch.addedToSlot[i]+=take; recorded+=take;
        }
        if (recorded>0) batch.amounts.put(material,Integer.valueOf(old+recorded));
        batch.expected=after;
    }
    private void settle(Pickup pickup, ItemStack[] after) {
        if (pickups.get(pickup.player.getUniqueId()) != pickup) return;
        pickups.remove(pickup.player.getUniqueId());
        if (!pickup.event.isCancelled()) record(pickup.player,pickup.before,after,pickup.material,pickup.amount);
    }
    private void settlePrevious(Player p, ItemStack[] current) {
        Pickup previous=pickups.get(p.getUniqueId());
        if (previous != null) settle(previous,current);
    }

    /** Adds a direct mining reward normally, then observes the exact inventory increase. */
    Map<Integer, ItemStack> giveMined(Player p, ItemStack stack) {
        boolean observe=allowed(p) && enabled(p) && plain(stack);
        ItemStack[] before=observe?snapshot(p):null;
        if (observe) settlePrevious(p,before);
        int amount=stack.getAmount(); Material material=stack.getType();
        Map<Integer, ItemStack> overflow=p.getInventory().addItem(stack);
        if (observe) {
            int left=0; for (ItemStack rest:overflow.values()) if (rest != null) left+=rest.getAmount();
            int added=amount-left;
            if (added>0) record(p,before,snapshot(p),material,added);
        }
        return overflow;
    }
    /** Tracks a freshly spawned overflow item without touching its data or pickup rules. */
    void trackOverflow(Player owner, Item item) { track(owner,item); }
    private void track(Player owner, Item item) {
        if (!allowed(owner) || !enabled(owner) || item == null || !item.isValid() || !plain(item.getItemStack())
                || !item.getPersistentDataContainer().isEmpty()) return;
        ItemStack stack=item.getItemStack();
        tracked.put(item.getUniqueId(),new TrackedItem(owner.getUniqueId(),stack.getType(),stack.getAmount()));
        while (tracked.size()>MAX_TRACKED_ITEMS) tracked.remove(tracked.keySet().iterator().next());
    }
    @EventHandler(priority=EventPriority.MONITOR, ignoreCancelled=true)
    public void onOreDrops(BlockDropItemEvent event) {
        if (!ore(event.getBlockState().getType()) || !allowed(event.getPlayer()) || !enabled(event.getPlayer())) return;
        final List<Dropped> items=new ArrayList<Dropped>();
        for (Item item:event.getItems()) { if (items.size()>=256) break; items.add(new Dropped(item)); }
        Bukkit.getScheduler().runTask(plugin,() -> {
            if (event.isCancelled()) return;
            for (Dropped drop:items) {
                // If an item merged or a plugin altered the drop before we could
                // observe it, do not treat the larger/changed stack as new ore.
                if (event.getItems().contains(drop.item) && drop.original.equals(drop.item.getItemStack())) track(event.getPlayer(),drop.item);
            }
        });
    }
    @EventHandler(priority=EventPriority.MONITOR, ignoreCancelled=true)
    public void onMerge(ItemMergeEvent event) {
        // Once resources merge with any other entity their provenance is ambiguous.
        tracked.remove(event.getEntity().getUniqueId()); tracked.remove(event.getTarget().getUniqueId());
    }
    @EventHandler(priority=EventPriority.MONITOR, ignoreCancelled=true)
    public void onPickup(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player)) { tracked.remove(event.getItem().getUniqueId()); return; }
        Player p=(Player)event.getEntity();
        TrackedItem source=tracked.remove(event.getItem().getUniqueId());
        if (source==null && !pickups.containsKey(p.getUniqueId())) return;
        ItemStack[] before=snapshot(p);
        settlePrevious(p,before);
        if (source==null || !source.owner.equals(p.getUniqueId()) || System.nanoTime()-source.created>ITEM_TTL_NANOS
                || !allowed(p) || !enabled(p) || !event.getItem().getPersistentDataContainer().isEmpty()) return;
        ItemStack stack=event.getItem().getItemStack();
        // On Paper/Spigot the event stack is temporarily set to the amount that fits;
        // getRemaining() is the part left on the ground, not a subtraction from it.
        if (!plain(stack) || stack.getType()!=source.material || stack.getAmount()>source.amount) return;
        invalidateIfChanged(p,before);
        Pickup candidate=new Pickup(p,event,stack.getType(),stack.getAmount(),before);
        pickups.put(p.getUniqueId(),candidate);
        Bukkit.getScheduler().runTask(plugin,() -> settle(candidate,snapshot(p)));
    }
    @EventHandler public void onQuit(PlayerQuitEvent event) { forget(event.getPlayer().getUniqueId()); toggles.remove(event.getPlayer().getUniqueId()); }
    private void forget(UUID player) {
        pending.remove(player); pickups.remove(player);
        Iterator<Map.Entry<UUID,TrackedItem>> iterator=tracked.entrySet().iterator();
        while (iterator.hasNext()) if (iterator.next().getValue().owner.equals(player)) iterator.remove();
    }
    private void prune() {
        long now=System.nanoTime();
        Iterator<Map.Entry<UUID,TrackedItem>> iterator=tracked.entrySet().iterator();
        while (iterator.hasNext()) if (now-iterator.next().getValue().created>ITEM_TTL_NANOS) iterator.remove();
    }
    private Sale prepare(Player p, Pending batch) {
        if (!allowed(p) || !enabled(p) || System.nanoTime()-batch.created>PENDING_TTL_NANOS) return null;
        ItemStack cursor=p.getItemOnCursor();
        if (!empty(cursor)) return null;
        ItemStack[] before=snapshot(p);
        if (!same(batch.expected,before)) return null;
        ItemStack[] after=before.clone();
        double multiplier=plugin.getStore().getBuyerMultiplier(p.getName())*(1.0+plugin.getRanks().getRank(p).tier*0.12);
        if (!Double.isFinite(multiplier) || multiplier<=0) return null;
        long count=0; double raw=0.0;
        for (int i=0;i<after.length;i++) {
            int wanted=batch.addedToSlot[i];
            if (wanted<=0) continue;
            ItemStack it=after[i];
            if (!plain(it) || it.getAmount()<wanted) return null;
            count+=wanted; raw+=(double)wanted*price(it.getType());
            if (wanted==it.getAmount()) after[i]=null;
            else { ItemStack replacement=it.clone(); replacement.setAmount(it.getAmount()-wanted); after[i]=replacement; }
        }
        double earned=raw*multiplier;
        return count>0 && Double.isFinite(earned) && earned>0 ? new Sale(p,before,after,count,earned) : null;
    }
    private void flush() {
        if (pending.isEmpty()) return;
        List<Sale> sales=new ArrayList<Sale>();
        Iterator<Map.Entry<UUID,Pending>> iterator=pending.entrySet().iterator();
        int examined=0;
        while (iterator.hasNext() && examined++<MAX_BATCH) {
            Map.Entry<UUID,Pending> entry=iterator.next(); iterator.remove();
            Player player=Bukkit.getPlayer(entry.getKey());
            if (player==null || pickups.containsKey(entry.getKey())) continue;
            Sale sale=prepare(player,entry.getValue()); if (sale!=null) sales.add(sale);
        }
        if (sales.isEmpty()) return;
        try {
            plugin.getStore().transact(data -> {
                for (Sale sale:sales) {
                    String name=sale.player.getName().toLowerCase(Locale.ROOT);
                    if (!name.matches("[a-z0-9_]{1,16}")) throw new IOException("invalid player name");
                    String base="players."+name;
                    double coins=data.getDouble(base+".coins",plugin.getConfig().getDouble("starting-coins",2500.0));
                    double sold=data.getDouble(base+".buyerSold",0.0);
                    double nextCoins=coins+sale.earned, nextSold=sold+sale.earned;
                    if (!Double.isFinite(coins) || coins<0 || !Double.isFinite(sold) || sold<0
                            || !Double.isFinite(nextCoins) || nextCoins<=coins || !Double.isFinite(nextSold) || nextSold<=sold)
                        throw new IOException("invalid buyer balance");
                    data.set(base+".coins",Double.valueOf(nextCoins));
                    data.set(base+".buyerSold",Double.valueOf(nextSold));
                }
                return null;
            });
        } catch (Exception error) {
            plugin.getLogger().warning("[AutoBuyer] Batch was not committed: "+error.getMessage());
            for (Sale sale:sales) sale.player.sendMessage(ColorUtil.c("&cАвтоскупка не сохранилась. Ресурсы остались у вас."));
            return;
        }
        for (Sale sale:sales) {
            for (int slot=0;slot<sale.after.length;slot++) {
                ItemStack before=sale.before[slot], after=sale.after[slot];
                if (empty(before) && empty(after)) continue;
                if (!empty(before) && !empty(after) && before.equals(after)) continue;
                int inventorySlot=slot==sale.after.length-1 ? 40 : slot;
                sale.player.getInventory().setItem(inventorySlot,after);
            }
            try { sale.player.saveData(); }
            catch (RuntimeException error) { plugin.getLogger().warning("[AutoBuyer] Player save failed after credit for "+sale.player.getName()+": "+error.getMessage()); }
            ColorUtil.actionBar(sale.player,"&#00E676Автоскупка: &f"+sale.count+" шт. &8• &a+"+ColorUtil.fmtMoney(sale.earned)+"$");
        }
    }
}
