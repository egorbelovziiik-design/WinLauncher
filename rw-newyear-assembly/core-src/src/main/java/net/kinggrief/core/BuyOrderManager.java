package net.kinggrief.core;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.HashMap;
import java.util.UUID;

/** Escrowed buy orders. Balances, orders and mailbox are committed in one data.yml
 * transaction; inventory changes use a replayable journal. All calls are on the
 * server thread. Paper player saves and external plugins are not in that transaction. */
public final class BuyOrderManager implements Listener {
    private static final long MAGIC=0x4b474f5244455231L; // KGORDER1
    private static final int STORAGE=36, MAX_ITEM=65536, MAX_JOURNAL=512*1024;
    private static final int MAX_OPEN_PER_BUYER=10, MAX_ORDERS=1000, MAX_MAIL=128;
    private static final int MAX_MAIL_ENCODED_BYTES=1024*1024;
    private static final long MAX_TOTAL=2_000_000_000L, MAX_PRICE=100_000_000L;
    private final KingGriefCore plugin;
    private final DataStore store;
    private final Path directory;
    private final Map<UUID,Journal> pending=new HashMap<UUID,Journal>();
    private final Map<UUID,String> damaged=new HashMap<UUID,String>();
    private Method encode,decode;
    private boolean available=true;

    private static final class Order {
        int id,remaining; UUID owner; String ownerName,template; long price;
    }
    private static final class Journal {
        UUID id,player,owner; String playerName,kind,mailId; int orderId,remaining,amount;
        long price; byte[] item; byte[][] after; boolean done; Path path;
    }
    public BuyOrderManager(KingGriefCore plugin) {
        this.plugin=plugin;this.store=plugin.getStore();
        this.directory=plugin.getDataFolder().toPath().resolve("buy-orders").resolve("pending");
    }
    public void start() {
        try {
            Files.createDirectories(directory);
            try {
                encode=ItemStack.class.getMethod("serializeAsBytes");
                decode=ItemStack.class.getMethod("deserializeBytes",byte[].class);
            } catch(Exception error) { available=false;plugin.getLogger().severe("[BuyOrders] Paper item serialization unavailable: " + error); }
            loadPending();
            pruneCompletedMarkers();
            Bukkit.getScheduler().runTaskTimer(plugin, new Runnable() {
                @Override public void run() { pruneCompletedMarkers(); }
            }, 20L * 60 * 60 * 24, 20L * 60 * 60 * 24);
        } catch(Exception error) {
            available=false;
            plugin.getLogger().severe("[BuyOrders] Cannot inspect recovery journals; stopping to protect accounts: " + error);
            Bukkit.shutdown();
        }
        for(Player p:Bukkit.getOnlinePlayers()) recover(p);
    }
    public void shutdown() {
        for(Player p:Bukkit.getOnlinePlayers()) if(isBlocked(p.getUniqueId()))
            p.kickPlayer(ColorUtil.c("&cЗаказ ожидает восстановления. Обратитесь к администратору."));
    }
    boolean isBlocked(UUID id) {return pending.containsKey(id)||damaged.containsKey(id);}

    public boolean handleCommand(Player p,String[] args) {
        if(!available || isBlocked(p.getUniqueId()) || plugin.getSafeTrades().isBusy(p.getUniqueId())
                || p.isDead() || plugin.getModeration().isFrozenOrAscending(p) || !empty(p.getItemOnCursor())) {
            p.sendMessage(ColorUtil.c("&cЗаказы недоступны: завершите обмен, уберите предмет с курсора или обратитесь к администратору."));return true;
        }
        if(args.length==0) {help(p);return true;}
        try {
            String sub=args[0].toLowerCase(Locale.ROOT);
            if("create".equals(sub)) {
                if(args.length!=3){help(p);return true;}
                create(p,parseInt(args[1],1,2304),parseLong(args[2],1,MAX_PRICE));
            } else if("list".equals(sub)) list(p,args.length>1?parseInt(args[1],1,100000):1);
            else if("sell".equals(sub)) {
                if(args.length<2||args.length>3){help(p);return true;}
                sell(p,parseInt(args[1],1,Integer.MAX_VALUE),args.length==3?parseInt(args[2],1,2304):0);
            } else if("cancel".equals(sub)) {
                if(args.length!=2){help(p);return true;} cancel(p,parseInt(args[1],1,Integer.MAX_VALUE));
            } else if("claim".equals(sub)) claim(p);
            else help(p);
        } catch(IllegalArgumentException error) {
            p.sendMessage(ColorUtil.c("&c" + error.getMessage()));
        } catch(Exception error) {
            plugin.getLogger().severe("[BuyOrders] Request failed safely for " + p.getUniqueId() + ": " + error);
            p.sendMessage(ColorUtil.c("&cЗаказ не выполнен. Проверьте сообщение администратора и журнал сервера."));
        }
        return true;
    }
    private void help(Player p) {
        p.sendMessage(ColorUtil.c("&#80D8FFЗаказы: &e/buyorder create <кол-во> <цена за шт.> &7(предмет в руке)"));
        p.sendMessage(ColorUtil.c("&e/buyorder list [страница] &7| &e/buyorder sell <id> [кол-во] &7| &e/buyorder cancel <id> &7| &e/buyorder claim"));
    }
    private static int parseInt(String s,int min,int max) {
        try {int n=Integer.parseInt(s);if(n<min||n>max)throw new NumberFormatException();return n;}
        catch(NumberFormatException e){throw new IllegalArgumentException("Число вне допустимого диапазона " + min + "–" + max + ".");}
    }
    private static long parseLong(String s,long min,long max) {
        try {long n=Long.parseLong(s);if(n<min||n>max)throw new NumberFormatException();return n;}
        catch(NumberFormatException e){throw new IllegalArgumentException("Цена должна быть целым числом от " + min + " до " + max + ".");}
    }
    private static boolean empty(ItemStack item){return item==null||item.getType()==Material.AIR||item.getAmount()<=0;}
    private byte[] bytes(ItemStack item)throws Exception {
        byte[] b=(byte[])encode.invoke(item);
        if(b.length==0||b.length>MAX_ITEM)throw new IllegalArgumentException("Предмет слишком большой для заказа.");
        return b;
    }
    private ItemStack item(byte[] data)throws Exception {
        if(data==null||data.length==0||data.length>MAX_ITEM)throw new IOException("invalid item bytes");
        ItemStack result=(ItemStack)decode.invoke(null,(Object)data);
        if(empty(result))throw new IOException("empty serialized item");return result;
    }
    private static String b64(byte[] b){return Base64.getEncoder().encodeToString(b);}
    private static byte[] unb64(String text)throws IOException {
        if(text==null||text.length()>MAX_ITEM*2)throw new IOException("invalid encoded item");
        try {return Base64.getDecoder().decode(text);}catch(IllegalArgumentException e){throw new IOException("invalid item encoding",e);}
    }
    private Order readOrder(YamlConfiguration y,int id) throws Exception {
        String k="buy-orders.orders."+id;
        if(!y.isConfigurationSection(k))return null;
        Order o=new Order();o.id=id;o.owner=UUID.fromString(y.getString(k+".owner-id",""));
        o.ownerName=y.getString(k+".owner-name","");o.template=y.getString(k+".item","");
        o.remaining=y.getInt(k+".remaining",0);o.price=y.getLong(k+".price",0);
        if(o.remaining<1||o.remaining>2304||o.price<1||o.price>MAX_PRICE
                ||o.ownerName.isEmpty()||o.template==null||o.template.isEmpty()||o.template.length()>MAX_ITEM*2
                ||(long)o.remaining*o.price>MAX_TOTAL)throw new IOException("invalid order " + id);
        // Do not deserialize up to a thousand unrelated item NBT blobs on every
        // list/create. The selected order is fully decoded before use.
        return o;
    }
    private List<Order> orders(YamlConfiguration y)throws Exception {
        List<Order> result=new ArrayList<Order>();ConfigurationSection sec=y.getConfigurationSection("buy-orders.orders");
        if(sec!=null) for(String key:sec.getKeys(false)) {
            try {int id=Integer.parseInt(key);Order o=readOrder(y,id);if(o!=null)result.add(o);}
            catch(NumberFormatException bad){throw new IOException("invalid order identifier " + key,bad);}
        }
        Collections.sort(result,(a,b)->Integer.compare(a.id,b.id));return result;
    }
    private void create(Player p,int amount,long price)throws Exception {
        long total=amount*price;
        if(total>MAX_TOTAL)throw new IllegalArgumentException("Сумма заказа больше допустимого лимита " + MAX_TOTAL + ".");
        ItemStack held=p.getInventory().getItemInMainHand();
        if(empty(held)||"menu_star".equals(ItemFactory.getCustomId(held)))throw new IllegalArgumentException("Возьмите нужный предмет в главную руку.");
        ItemStack template=held.clone();template.setAmount(1);String encoded=b64(bytes(template));
        int id=store.transact(y->{
            List<Order> all=orders(y);int own=0;for(Order o:all)if(o.owner.equals(p.getUniqueId()))own++;
            if(own>=MAX_OPEN_PER_BUYER||all.size()>=MAX_ORDERS)throw new IllegalArgumentException("Достигнут лимит открытых заказов.");
            double balance=store.getCoins(p.getName());
            if(!Double.isFinite(balance)||balance<total)throw new IllegalArgumentException("Недостаточно монет для резерва: нужно " + total + ".");
            int next=y.getInt("buy-orders.next-id",1);
            if(next<1||next==Integer.MAX_VALUE||y.isConfigurationSection("buy-orders.orders."+next))throw new IOException("order sequence unavailable");
            String k="buy-orders.orders."+next;
            y.set(k+".owner-id",p.getUniqueId().toString());y.set(k+".owner-name",p.getName());
            y.set(k+".item",encoded);y.set(k+".remaining",amount);y.set(k+".price",price);
            y.set("buy-orders.next-id",next+1);store.setCoins(p.getName(),balance-total);
            return Integer.valueOf(next);
        }).intValue();
        p.sendMessage(ColorUtil.c("&a✔ Заказ #"+id+" создан. Зарезервировано &f"+total+"$&a; предмет-шаблон остался у вас."));
    }
    private void list(Player p,int page)throws Exception {
        List<Order> all=store.inspect(y->orders(y));int pages=Math.max(1,(all.size()+5)/6);
        if(page>pages)throw new IllegalArgumentException("Доступных страниц: " + pages + ".");
        p.sendMessage(ColorUtil.c("&#80D8FF&lЗаказы на покупку &7("+page+"/"+pages+")"));
        for(int i=(page-1)*6;i<Math.min(page*6,all.size());i++) {
            Order o=all.get(i);ItemStack wanted=item(unb64(o.template));ItemMeta meta=wanted.getItemMeta();
            String label=meta!=null&&meta.hasDisplayName()?meta.getDisplayName():wanted.getType().name();
            p.sendMessage(ColorUtil.c("&e#"+o.id+" &f"+label+" &7×"+o.remaining+" &8| &a"+o.price+"$/шт &8| &f"+o.ownerName));
        }
        p.sendMessage(ColorUtil.c("&7Держа такой же предмет: &e/buyorder sell <id> [кол-во]&7. Получение: &e/buyorder claim"));
    }
    private void cancel(Player p,int id)throws Exception {
        long refund=store.transact(y->{
            Order o=readOrder(y,id);
            if(o==null||!o.owner.equals(p.getUniqueId()))throw new IllegalArgumentException("Заказ не найден или он принадлежит другому игроку.");
            long sum=o.remaining*o.price;double balance=store.getCoins(p.getName());
            if(!Double.isFinite(balance)||balance+sum>9e15)throw new IllegalArgumentException("Баланс недоступен для возврата.");
            store.setCoins(p.getName(),balance+sum);y.set("buy-orders.orders."+id,null);return Long.valueOf(sum);
        }).longValue();
        p.sendMessage(ColorUtil.c("&a✔ Заказ #"+id+" отменён, возвращено &f"+refund+"$&a."));
    }
    private static void checkMailCapacity(ConfigurationSection mailbox,int incomingBytes)throws IOException {
        if(mailbox==null)return;
        java.util.Set<String> keys=mailbox.getKeys(false);
        if(keys.size()>=MAX_MAIL)throw new IllegalArgumentException("Почта покупателя заполнена.");
        long encoded=4L*((incomingBytes+2L)/3L);
        for(String key:keys) {
            String item=mailbox.getString(key+".item");
            if(item==null||item.length()>MAX_ITEM*2)throw new IOException("invalid mailbox item " + key);
            encoded+=item.length();
            if(encoded>MAX_MAIL_ENCODED_BYTES)throw new IllegalArgumentException("Почта покупателя заполнена; ему нужно забрать покупки.");
        }
    }
    private void sell(Player p,int id,int requested)throws Exception {
        Order o=store.inspect(y->readOrder(y,id));
        if(o==null)throw new IllegalArgumentException("Заказ не найден.");
        if(o.owner.equals(p.getUniqueId()))throw new IllegalArgumentException("Нельзя продавать в собственный заказ.");
        ItemStack held=p.getInventory().getItemInMainHand(), template=item(unb64(o.template));
        if(empty(held)||!held.isSimilar(template))throw new IllegalArgumentException("Предмет в руке не совпадает с заказом, включая его свойства.");
        int qty=requested==0?Math.min(o.remaining,held.getAmount()):requested;
        if(qty>o.remaining||qty>held.getAmount())throw new IllegalArgumentException("В руке или заказе недостаточно предметов.");
        ItemStack delivered=held.clone();delivered.setAmount(qty);
        byte[] deliveredBytes=bytes(delivered);
        store.inspect(y->{
            ConfigurationSection mailbox=y.getConfigurationSection("buy-orders.mail."+o.owner);
            checkMailCapacity(mailbox,deliveredBytes.length);
            double balance=store.getCoins(p.getName());
            if(!Double.isFinite(balance)||balance+qty*o.price>9e15)throw new IllegalArgumentException("Баланс продавца недоступен.");
            return Boolean.TRUE;
        });
        ItemStack[] after=copy(p.getInventory().getStorageContents());int slot=p.getInventory().getHeldItemSlot();
        if(qty==held.getAmount())after[slot]=null;else after[slot].setAmount(held.getAmount()-qty);
        Journal j=new Journal();j.id=UUID.randomUUID();j.player=p.getUniqueId();j.playerName=p.getName();
        j.owner=o.owner;j.kind="SALE";j.mailId=j.id.toString();j.orderId=id;j.remaining=o.remaining;j.amount=qty;j.price=o.price;
        j.item=deliveredBytes;j.after=encodeStorage(after);j.path=directory.resolve(j.player+"_"+j.id+".order");
        commit(p,j,"&a✔ Продано &f"+qty+" &aпредметов за &f"+(qty*o.price)+"$&a. Покупатель получит их через /buyorder claim.");
    }
    private void claim(Player p)throws Exception {
        String owner=p.getUniqueId().toString();
        String[] mail=store.inspect(y->{
            ConfigurationSection sec=y.getConfigurationSection("buy-orders.mail."+owner);
            if(sec==null||sec.getKeys(false).isEmpty())return null;
            List<String> keys=new ArrayList<String>(sec.getKeys(false));Collections.sort(keys);
            String id=keys.get(0);return new String[]{id,sec.getString(id+".item","")};
        });
        if(mail==null){p.sendMessage(ColorUtil.c("&7В почте заказов нет предметов."));return;}
        byte[] data=unb64(mail[1]);ItemStack incoming=item(data);
        Inventory trial=Bukkit.createInventory(null,STORAGE);trial.setContents(copy(p.getInventory().getStorageContents()));
        if(!trial.addItem(incoming.clone()).isEmpty())throw new IllegalArgumentException("Освободите место для предмета из почты.");
        Journal j=new Journal();j.id=UUID.randomUUID();j.player=p.getUniqueId();j.playerName=p.getName();
        j.owner=p.getUniqueId();j.kind="CLAIM";j.mailId=mail[0];j.item=data;j.after=encodeStorage(trial.getContents());
        j.path=directory.resolve(j.player+"_"+j.id+".order");
        commit(p,j,"&a✔ Предмет из заказа получен. Для следующего используйте /buyorder claim.");
    }
    private static ItemStack[] copy(ItemStack[] source)throws IOException {
        if(source.length!=STORAGE)throw new IOException("invalid storage size");ItemStack[] out=new ItemStack[STORAGE];
        for(int i=0;i<STORAGE;i++)out[i]=empty(source[i])?null:source[i].clone();return out;
    }
    private byte[][] encodeStorage(ItemStack[] items)throws Exception {
        if(items.length!=STORAGE)throw new IOException("invalid storage");byte[][] out=new byte[STORAGE][];
        for(int i=0;i<STORAGE;i++)if(!empty(items[i]))out[i]=bytes(items[i]);return out;
    }
    private ItemStack[] decodeStorage(byte[][] data)throws Exception {
        if(data==null||data.length!=STORAGE)throw new IOException("invalid snapshot");ItemStack[] out=new ItemStack[STORAGE];
        for(int i=0;i<STORAGE;i++)if(data[i]!=null)out[i]=item(data[i]);return out;
    }
    private void commit(Player p,Journal j,String success)throws Exception {
        // Prepared intent is durable before either balances or player inventory changes.
        write(j);pending.put(j.player,j);
        if(!applyEconomic(j) || !applyInventory(j,p)) {
            available=false;
            p.kickPlayer(ColorUtil.c("&cОперация заказа ожидает восстановления. Обратитесь к администратору."));return;
        }
        cleanup(j);p.sendMessage(ColorUtil.c(success));
    }
    private boolean applyEconomic(Journal j) {
        try {
            String marker="buy-orders.transactions."+j.id;
            if(store.inspect(y->Boolean.valueOf(y.contains(marker))).booleanValue())return true;
            store.transact(y->{
                if(y.contains(marker))return Boolean.TRUE;
                if("SALE".equals(j.kind)) {
                    Order o=readOrder(y,j.orderId);
                    if(o==null||!o.owner.equals(j.owner)||o.remaining!=j.remaining||o.price!=j.price
                            ||j.amount<1||j.amount>o.remaining||!item(j.item).isSimilar(item(unb64(o.template))))throw new IOException("order changed before journal replay");
                    String mail="buy-orders.mail."+j.owner;
                    ConfigurationSection mailbox=y.getConfigurationSection(mail);
                    checkMailCapacity(mailbox,j.item.length);
                    if(y.contains(mail+"."+j.mailId))throw new IOException("mail id already exists");
                    double balance=store.getCoins(j.playerName);long amount=j.amount*j.price;
                    if(!Double.isFinite(balance)||balance+amount>9e15)throw new IOException("invalid seller balance");
                    store.setCoins(j.playerName,balance+amount);y.set(mail+"."+j.mailId+".item",b64(j.item));
                    y.set(mail+"."+j.mailId+".order",j.orderId);y.set(mail+"."+j.mailId+".seller",j.playerName);
                    int left=o.remaining-j.amount;
                    if(left==0)y.set("buy-orders.orders."+j.orderId,null);
                    else y.set("buy-orders.orders."+j.orderId+".remaining",left);
                } else if("CLAIM".equals(j.kind)) {
                    String mail="buy-orders.mail."+j.owner+"."+j.mailId;
                    if(!j.owner.equals(j.player)||!Arrays.equals(j.item,unb64(y.getString(mail+".item",null))))throw new IOException("mail changed before journal replay");
                    y.set(mail,null);
                } else throw new IOException("unknown transaction type");
                y.set(marker,System.currentTimeMillis());return Boolean.TRUE;
            });
            return true;
        } catch(Exception error){plugin.getLogger().severe("[BuyOrders] Economic replay pending " + j.path + ": " + error);return false;}
    }
    private boolean applyInventory(Journal j,Player p) {
        if(j.done||!p.getUniqueId().equals(j.player))return false;
        try {
            p.getInventory().setStorageContents(decodeStorage(j.after));p.saveData();j.done=true;
            try{write(j);}catch(Exception error){j.done=false;throw error;}
            pending.remove(j.player);p.updateInventory();return true;
        } catch(Exception error){plugin.getLogger().severe("[BuyOrders] Inventory replay pending " + j.path + ": " + error);return false;}
    }
    @EventHandler(priority=EventPriority.LOWEST) public void onJoin(PlayerJoinEvent e){recover(e.getPlayer());}
    private void recover(Player p) {
        UUID id=p.getUniqueId();
        if(damaged.containsKey(id)) {p.kickPlayer(ColorUtil.c("&cЖурнал заказа повреждён. Обратитесь к администратору."));return;}
        Journal j=pending.get(id);if(j==null)return;
        if(plugin.getSafeTrades().isBlockedForRecovery(id)||!applyEconomic(j)||!applyInventory(j,p)) {
            available=false;p.kickPlayer(ColorUtil.c("&cЗаказ ожидает восстановления. Обратитесь к администратору."));return;
        }
        cleanup(j);p.sendMessage(ColorUtil.c("&a✔ Незавершённая операция заказа восстановлена."));
    }
    private void cleanup(Journal j) {
        if(!j.done)return;
        try{Files.deleteIfExists(j.path);forceDirectory();}
        catch(Exception e){plugin.getLogger().warning("[BuyOrders] Could not remove completed journal: " + e);}
    }
    private void loadPending()throws Exception {
        try(DirectoryStream<Path> paths=Files.newDirectoryStream(directory,"*.order")) {
            for(Path path:paths) {
                try {
                    Journal j=read(path);
                    if(pending.containsKey(j.player))throw new IOException("overlapping player journals");
                    if(!applyEconomic(j))throw new IOException("economic replay failed");
                    if(j.done)cleanup(j);else {pending.put(j.player,j);plugin.getLogger().warning("[BuyOrders] Pending operation " + j.id + " will restore on join.");}
                } catch(Exception error) {
                    available=false;String[] parts=path.getFileName().toString().split("_");boolean identified=false;
                    if(parts.length==2)try{damaged.put(UUID.fromString(parts[0]),path.toString());identified=true;}catch(IllegalArgumentException ignored){}
                    plugin.getLogger().severe("[BuyOrders] Invalid recovery journal " + path + ": " + error);
                    if(!identified){plugin.getLogger().severe("[BuyOrders] Cannot identify affected account; stopping to protect inventories.");Bukkit.shutdown();}
                }
            }
        }
    }
    /** Keep idempotency markers while any journal exists. With none present,
     * old completed markers can be removed without making a replay repeat. */
    private void pruneCompletedMarkers() {
        if(!available)return;
        try(DirectoryStream<Path> paths=Files.newDirectoryStream(directory,"*.order")) {
            if(paths.iterator().hasNext())return;
            long cutoff=System.currentTimeMillis()-30L*24*60*60*1000;
            List<String> old=store.inspect(y->{
                List<String> ids=new ArrayList<String>();
                ConfigurationSection sec=y.getConfigurationSection("buy-orders.transactions");
                if(sec!=null)for(String id:sec.getKeys(false))if(sec.getLong(id,Long.MAX_VALUE)<cutoff)ids.add(id);
                return ids;
            });
            if(old.isEmpty())return;
            store.transact(y->{for(String id:old)y.set("buy-orders.transactions."+id,null);return Boolean.TRUE;});
        } catch(Exception error){plugin.getLogger().warning("[BuyOrders] Completed transaction cleanup skipped: " + error);}
    }
    private void write(Journal j)throws Exception {
        ByteArrayOutputStream buf=new ByteArrayOutputStream();DataOutputStream out=new DataOutputStream(buf);
        out.writeLong(MAGIC);uuid(out,j.id);uuid(out,j.player);uuid(out,j.owner);out.writeUTF(j.playerName);out.writeUTF(j.kind);out.writeUTF(j.mailId);
        out.writeInt(j.orderId);out.writeInt(j.remaining);out.writeInt(j.amount);out.writeLong(j.price);out.writeBoolean(j.done);
        block(out,j.item);for(byte[] b:j.after)block(out,b);out.flush();byte[] body=buf.toByteArray();
        if(body.length+32>MAX_JOURNAL)throw new IOException("journal too large");byte[] hash=MessageDigest.getInstance("SHA-256").digest(body);
        Path tmp=Files.createTempFile(directory,".order-",".tmp");
        try {
            try(FileChannel c=FileChannel.open(tmp,StandardOpenOption.WRITE,StandardOpenOption.TRUNCATE_EXISTING)) {
                ByteBuffer data=ByteBuffer.allocate(body.length+32).put(body).put(hash);data.flip();while(data.hasRemaining())c.write(data);c.force(true);
            }
            Files.move(tmp,j.path,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);forceDirectory();
        } finally {try{Files.deleteIfExists(tmp);}catch(IOException error){plugin.getLogger().warning("[BuyOrders] Temp cleanup failed: " + error);}}
    }
    private void forceDirectory(){try(FileChannel c=FileChannel.open(directory,StandardOpenOption.READ)){c.force(true);}catch(Exception e){plugin.getLogger().warning("[BuyOrders] Directory fsync unavailable: " + e);}}
    private static void uuid(DataOutputStream out,UUID u)throws IOException{out.writeLong(u.getMostSignificantBits());out.writeLong(u.getLeastSignificantBits());}
    private static UUID uuid(DataInputStream in)throws IOException{return new UUID(in.readLong(),in.readLong());}
    private static void block(DataOutputStream out,byte[] bytes)throws IOException {
        if(bytes==null)out.writeInt(-1);else {if(bytes.length==0||bytes.length>MAX_ITEM)throw new IOException("bad item length");out.writeInt(bytes.length);out.write(bytes);}
    }
    private static byte[] block(DataInputStream in)throws IOException {
        int n=in.readInt();if(n==-1)return null;if(n<1||n>MAX_ITEM||n>in.available())throw new IOException("bad item length");
        byte[] b=new byte[n];in.readFully(b);return b;
    }
    private Journal read(Path path)throws Exception {
        long n=Files.size(path);if(n<100||n>MAX_JOURNAL)throw new IOException("bad journal size");
        byte[] all=Files.readAllBytes(path),body=Arrays.copyOf(all,all.length-32),hash=Arrays.copyOfRange(all,all.length-32,all.length);
        if(!MessageDigest.isEqual(hash,MessageDigest.getInstance("SHA-256").digest(body)))throw new IOException("bad journal checksum");
        DataInputStream in=new DataInputStream(new ByteArrayInputStream(body));if(in.readLong()!=MAGIC)throw new IOException("unsupported journal");
        Journal j=new Journal();j.id=uuid(in);j.player=uuid(in);j.owner=uuid(in);j.playerName=in.readUTF();j.kind=in.readUTF();j.mailId=in.readUTF();
        j.orderId=in.readInt();j.remaining=in.readInt();j.amount=in.readInt();j.price=in.readLong();j.done=in.readBoolean();
        j.item=block(in);j.after=new byte[STORAGE][];for(int i=0;i<STORAGE;i++)j.after[i]=block(in);j.path=path;
        if(in.available()!=0||!path.getFileName().toString().equals(j.player+"_"+j.id+".order"))throw new IOException("journal identity mismatch");
        UUID.fromString(j.mailId);if(j.playerName.isEmpty()||j.playerName.length()>32)throw new IOException("bad player name");
        if(!"SALE".equals(j.kind)&&!"CLAIM".equals(j.kind))throw new IOException("bad operation type");
        item(j.item);decodeStorage(j.after);return j;
    }
}
