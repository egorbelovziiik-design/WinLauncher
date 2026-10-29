package net.kinggrief.probe;

import net.kinggrief.core.ItemFactory;
import net.kinggrief.core.KingGriefCore;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

/** Crash after the order/payment commit but before the seller inventory save. */
public final class OrderCrashProbe extends JavaPlugin implements Listener {
    private KingGriefCore core;private Player a,b;private boolean run,check;
    @Override public void onEnable(){core=(KingGriefCore)Bukkit.getPluginManager().getPlugin("KingGriefCore");check=Files.exists(getDataFolder().toPath().resolve("restart"));Bukkit.getPluginManager().registerEvents(this,this);}
    @EventHandler public void join(PlayerJoinEvent e){
        if("OrbTester".equals(e.getPlayer().getName()))a=e.getPlayer();
        if("OrbEnemy".equals(e.getPlayer().getName()))b=e.getPlayer();
        if(a!=null&&b!=null&&!run){run=true;Bukkit.getScheduler().runTaskLater(this,()->{
            try{if(check)verify();else prepare();}catch(Throwable error){getLogger().severe("KG-ORDER-CRASH FAIL " + error);error.printStackTrace();}
        },30L);}
    }
    private static Field field(Object o,String n)throws Exception{Field f=o.getClass().getDeclaredField(n);f.setAccessible(true);return f;}
    private static Method method(Object o,String n,Class<?>...p)throws Exception{Method m=o.getClass().getDeclaredMethod(n,p);m.setAccessible(true);return m;}
    private ItemStack custom(int amount){ItemStack i=ItemFactory.arrowIce(amount);ItemMeta m=i.getItemMeta();m.getPersistentDataContainer().set(new NamespacedKey("kg-order-probe","marker"),PersistentDataType.STRING,"уникальная-вещь");i.setItemMeta(m);return i;}
    private void prepare()throws Exception{
        a.getInventory().clear();b.getInventory().clear();
        core.getStore().setCoins(a.getName(),1000);core.getStore().setCoins(b.getName(),1000);core.getStore().save();
        a.getInventory().setItemInMainHand(custom(1));b.getInventory().setItemInMainHand(custom(2));
        a.performCommand("buyorder create 3 100");
        if(core.getStore().getCoins(a.getName())!=700)throw new AssertionError("order not reserved");
        a.saveData();b.saveData();
        Object manager=field(core,"buyOrders").get(core);
        Class<?> cls=Class.forName("net.kinggrief.core.BuyOrderManager$Journal",true,core.getClass().getClassLoader());
        Constructor<?> ctor=cls.getDeclaredConstructor();ctor.setAccessible(true);Object j=ctor.newInstance();UUID id=UUID.randomUUID();
        field(j,"id").set(j,id);field(j,"player").set(j,b.getUniqueId());field(j,"owner").set(j,a.getUniqueId());
        field(j,"playerName").set(j,b.getName());field(j,"kind").set(j,"SALE");field(j,"mailId").set(j,id.toString());
        field(j,"orderId").setInt(j,1);field(j,"remaining").setInt(j,3);field(j,"amount").setInt(j,2);field(j,"price").setLong(j,100);
        field(j,"item").set(j,method(manager,"bytes",ItemStack.class).invoke(manager,custom(2)));
        ItemStack[] after=new ItemStack[36];
        field(j,"after").set(j,method(manager,"encodeStorage",ItemStack[].class).invoke(manager,(Object)after));
        Path dir=(Path)field(manager,"directory").get(manager);Path path=dir.resolve(b.getUniqueId()+"_"+id+".order");field(j,"path").set(j,path);
        method(manager,"write",cls).invoke(manager,j);
        boolean ok=(Boolean)method(manager,"applyEconomic",cls).invoke(manager,j);
        if(!ok||!Files.exists(path)||core.getStore().getCoins(b.getName())!=1200||b.getInventory().getItemInMainHand().getAmount()!=2)
            throw new AssertionError("economic-commit crash point not reached");
        getLogger().info("KG-ORDER-CRASH READY " + id);
    }
    private void verify()throws Exception{
        if(core.getStore().getCoins(a.getName())!=700||core.getStore().getCoins(b.getName())!=1200)throw new AssertionError("economic replay double-paid or lost escrow");
        if(b.getInventory().getItemInMainHand().getType()!=org.bukkit.Material.AIR)throw new AssertionError("seller inventory not recovered");
        a.performCommand("buyorder claim");
        int count=0;for(ItemStack i:a.getInventory().getStorageContents())if(i!=null&&i.isSimilar(custom(1)))count+=i.getAmount();
        if(count!=3)throw new AssertionError("buyer mailbox did not preserve exact custom items: " + count);
        a.performCommand("buyorder claim");int again=0;for(ItemStack i:a.getInventory().getStorageContents())if(i!=null&&i.isSimilar(custom(1)))again+=i.getAmount();
        if(again!=3)throw new AssertionError("claim repeated");
        a.performCommand("buyorder cancel 1");
        if(core.getStore().getCoins(a.getName())!=800||core.getStore().getCoins(b.getName())!=1200)throw new AssertionError("remaining escrow refund incorrect");
        Path dir=core.getDataFolder().toPath().resolve("buy-orders/pending");
        try(java.util.stream.Stream<Path> files=Files.list(dir)){if(files.anyMatch(p->p.toString().endsWith(".order")))throw new AssertionError("order journal remains");}
        getLogger().info("KG-ORDER-CRASH PASS: SIGKILL after payment, before item save recovered once without duplicate payment");
    }
}
