package net.kinggrief.core;

import java.io.IOException;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;

/** Per-player fortune wheel. All state transitions run on the Paper main thread. */
final class FortuneWheelManager {
    static final long COOLDOWN_MS = 15L * 60L * 1000L;
    private static final int SNOW_COST = 5;
    private static final double COIN_COST = 15000.0;
    private final KingGriefCore plugin;
    private final Random rng = new Random();

    private enum Prize {
        COINS("&a+30 000$ монет"),
        PLAST("&#A855F74× Пласт + 2× Трапка"),
        MANDARIN("&#FF91008× Мандаринов + Зелье Киллера"),
        RELICS("&#00E5FF+25 Риликов ❖"),
        AURA("&#FFD7402× Божья Аура + 2× Дезориентация"),
        CASE("&#80D8FFНовогодний кейс (/cases)"),
        SANTA("&#FF1744&lДЖЕКПОТ: ШАР САНТЫ!");
        final String label;
        Prize(String label) { this.label = label; }
        static Prize roll(int value) {
            if(value<0||value>=100)throw new IllegalArgumentException("bad wheel roll");
            return value<22?COINS:value<42?PLAST:value<58?MANDARIN:value<74?RELICS:value<88?AURA:value<97?CASE:SANTA;
        }
        ItemStack[] items() {
            switch(this) {
                case PLAST: return new ItemStack[]{ItemFactory.plast(4),ItemFactory.trapka(2)};
                case MANDARIN: return new ItemStack[]{ItemFactory.nyMandarin(8),ItemFactory.potionKiller(1)};
                case AURA: return new ItemStack[]{ItemFactory.bozhyaAura(2),ItemFactory.dezorient(2)};
                case SANTA: return new ItemStack[]{ItemFactory.sharSanty()};
                default: return new ItemStack[0];
            }
        }
    }
    private enum Delivery { DELIVERED, WAITING, REVIEW }
    private static final class Pending {
        final Prize prize; final String state, receipt;
        Pending(Prize prize,String state,String receipt){this.prize=prize;this.state=state;this.receipt=receipt;}
    }

    FortuneWheelManager(KingGriefCore plugin){this.plugin=plugin;}
    private static String playerBase(Player p) { return "players."+p.getName().toLowerCase(Locale.ROOT); }
    private static String wheel(Player p) { return playerBase(p)+".fortuneWheel"; }
    private static void section(YamlConfiguration y,String path)throws IOException {
        if(y.contains(path)&&!y.isConfigurationSection(path))throw new IOException("invalid section: "+path);
    }
    private static long whole(YamlConfiguration y,String path,long fallback,long maximum)throws IOException {
        Object value=y.get(path);
        if(value==null)return fallback;
        if(!(value instanceof Number))throw new IOException("invalid integer: "+path);
        Number n=(Number)value;double d=n.doubleValue();long v=n.longValue();
        if(!Double.isFinite(d)||d!=(double)v||v<0||v>maximum)throw new IOException("out of range: "+path);
        return v;
    }
    private static long readyAt(YamlConfiguration y,Player p)throws IOException {
        section(y,"players");section(y,playerBase(p));section(y,wheel(p));
        return whole(y,wheel(p)+".readyAt",0,Long.MAX_VALUE);
    }
    private static Pending pending(YamlConfiguration y,Player p)throws IOException {
        readyAt(y,p);
        String base=wheel(p)+".pending";section(y,base);
        if(!y.contains(base))return null;
        Object rawId=y.get(base+".prize"),rawState=y.get(base+".state"),rawReceipt=y.get(base+".receipt");
        if(!(rawId instanceof String)||!(rawState instanceof String)||!(rawReceipt instanceof String))throw new IOException("invalid wheel pending prize");
        Prize prize;
        try { prize=Prize.valueOf((String)rawId);UUID.fromString((String)rawReceipt); }
        catch(IllegalArgumentException e){throw new IOException("invalid wheel prize/receipt",e);}
        String state=(String)rawState;
        if(prize.items().length==0||(!state.equals("ready")&&!state.equals("delivering")))throw new IOException("invalid wheel delivery state");
        return new Pending(prize,state,(String)rawReceipt);
    }
    private boolean eligible(Player p) {
        if(p==null||!p.isOnline()||!Bukkit.isPrimaryThread())return false;
        if(!p.getName().matches("[A-Za-z0-9_]{1,16}")||!plugin.getStore().isAuthenticated(p)) {
            p.sendMessage(ColorUtil.c("&cСначала войдите в аккаунт."));return false;
        }
        if(plugin.getStore().isQuickFrozen(p)||plugin.getModeration().isFrozenOrAscending(p)) {
            p.sendMessage(ColorUtil.c("&cКолесо недоступно во время проверки."));return false;
        }
        return true;
    }
    private static String timeLeft(long millis) {
        long seconds=(millis+999)/1000;
        return (seconds/60)+" мин " + (seconds%60)+" сек";
    }
    void status(Player p) {
        if(!eligible(p))return;
        try {
            long remaining=Math.max(0,plugin.getStore().inspect(y->readyAt(y,p))-System.currentTimeMillis());
            Pending pending=plugin.getStore().inspect(y->pending(y,p));
            p.sendMessage(ColorUtil.c(remaining>0?"&eКолесо: следующий прокрут через &f"+timeLeft(remaining)+"&e.":"&aКолесо готово к прокрутке."));
            p.sendMessage(ColorUtil.c("&7Цена: 5 снежинок или 15 000$; общий перерыв — 15 минут."));
            if(pending!=null)p.sendMessage(ColorUtil.c(pending.state.equals("ready")?"&eПриз ожидает: освободите место и введите /wheel claim.":"&cВыдача приза требует проверки администратора; повторная выдача заблокирована."));
        } catch(Exception error){refuse(p,error);}
    }
    void claim(Player p) {
        if(!eligible(p))return;
        try {
            Pending pending=plugin.getStore().inspect(y->pending(y,p));
            if(pending==null){p.sendMessage(ColorUtil.c("&eОжидающих призов колеса нет."));return;}
            Delivery delivery=deliver(p,pending);
            if(delivery==Delivery.DELIVERED)p.sendMessage(ColorUtil.c("&aВы получили приз колеса: "+pending.prize.label+"&a."));
        } catch(Exception error){refuse(p,error);}
    }
    void spin(Player p,boolean useSnowflakes) {
        if(!eligible(p))return;
        final DataStore store=plugin.getStore();
        final long now=System.currentTimeMillis();
        boolean committed=false;
        try {
            long remaining=store.inspect(y->readyAt(y,p))-now;
            if(remaining>0){p.sendMessage(ColorUtil.c("&eКолесо ещё отдыхает: &f"+timeLeft(remaining)+"&e. Валюта не списана."));return;}
            Pending old=store.inspect(y->pending(y,p));
            if(old!=null){
                p.sendMessage(ColorUtil.c(old.state.equals("ready")?"&eСначала получите прошлый приз: /wheel claim.":"&cПрошлая выдача требует проверки администратора. Валюта не списана."));return;
            }
            if(useSnowflakes && store.getSnowflakes(p.getName())<SNOW_COST) {
                p.sendMessage(ColorUtil.c("&cНужно 5 снежинок или /wheel money за 15 000$. Валюта не списана."));return;
            }
            if(!useSnowflakes && store.getCoins(p.getName())<COIN_COST) {
                p.sendMessage(ColorUtil.c("&cНужно 15 000$ или /wheel snow за 5 снежинок. Валюта не списана."));return;
            }
            // Check every item outcome BEFORE rolling: a full inventory cannot
            // be used to reroll for a currency prize without paying.
            for(Prize candidate:Prize.values()) {
                ItemStack[] items=candidate.items();
                if(items.length>0&&!InventoryCapacity.canFit(p,items)) {
                    p.sendMessage(ColorUtil.c("&cОсвободите место для двух предметов колеса. Валюта не списана."));return;
                }
            }
            final Prize result=Prize.roll(rng.nextInt(100));
            final String receipt=UUID.randomUUID().toString(),base=playerBase(p),w=wheel(p);
            final int effectiveRelics=result==Prize.RELICS?store.getAwardRelics(p.getName(),p.getUniqueId()):0;
            if(now<=0||now>Long.MAX_VALUE-COOLDOWN_MS)throw new IOException("invalid system clock");
            store.transact(y->{
                if(readyAt(y,p)>now||pending(y,p)!=null)throw new IOException("wheel state changed before commit");
                String coinsPath=base+".coins",snowPath=base+".snowflakes";
                Object rawCoins=y.get(coinsPath);
                if(rawCoins!=null&&!(rawCoins instanceof Number))throw new IOException("invalid coin balance");
                double coins=rawCoins==null?plugin.getConfig().getDouble("starting-coins",2500.0):((Number)rawCoins).doubleValue();
                if(!Double.isFinite(coins)||coins<0)throw new IOException("invalid coin balance");
                if(useSnowflakes){
                    long snow=whole(y,snowPath,0,Integer.MAX_VALUE);
                    if(snow<SNOW_COST)throw new IOException("snowflake balance changed");
                    y.set(snowPath,(int)snow-SNOW_COST);
                } else {
                    double after=coins-COIN_COST;
                    if(coins<COIN_COST||!Double.isFinite(after)||after>=coins)throw new IOException("coin balance changed or cannot represent payment");
                    coins=after;y.set(coinsPath,coins);
                }
                if(result==Prize.COINS){
                    double next=coins+30000.0;
                    if(!Double.isFinite(next)||next<=coins)throw new IOException("coin prize cannot be represented");
                    y.set(coinsPath,next);
                } else if(result==Prize.RELICS){
                    long local=whole(y,base+".relics",50,Integer.MAX_VALUE);
                    long value=Math.max(local,(long)effectiveRelics);
                    if(value>Integer.MAX_VALUE-25L)throw new IOException("relic balance at limit");
                    y.set(base+".relics",(int)value+25);
                } else if(result==Prize.CASE){
                    section(y,base+".cases");
                    long keys=whole(y,base+".cases.summer",0,Integer.MAX_VALUE);
                    if(keys==Integer.MAX_VALUE)throw new IOException("case key balance at limit");
                    y.set(base+".cases.summer",(int)keys+1);
                } else if(result.items().length>0){
                    y.set(w+".pending.prize",result.name());y.set(w+".pending.state","ready");y.set(w+".pending.receipt",receipt);
                }
                // The fee, any local balance prize, and cooldown commit together.
                section(y,w+".last");
                y.set(w+".readyAt",now+COOLDOWN_MS);
                y.set(w+".last.prize",result.name());y.set(w+".last.receipt",receipt);y.set(w+".last.at",now);
                y.set(w+".last.payment",useSnowflakes?"snow":"money");
                return null;
            });
            committed=true;
            if(result==Prize.RELICS)store.syncAwardRelics(p.getName(),p.getUniqueId());
            Delivery delivery=result.items().length==0?Delivery.DELIVERED:deliver(p,new Pending(result,"ready",receipt));
            plugin.getHud().updatePlayerHud(p);
            if(result==Prize.SANTA)Bukkit.broadcastMessage(ColorUtil.c("&#FF4081&l🎡 [КОЛЕСО ФОРТУНЫ] &f"+p.getName()+" &fвыиграл Шар Санты!"));
            p.sendMessage(ColorUtil.c("&#FF4081🎡 Прокрут сохранён. Следующий — через 15 минут."));
            animate(p,result,delivery);
        } catch(Exception error){
            if(!committed)refuse(p,error);
            else {
                plugin.getLogger().severe("[Wheel] Spin was committed for "+p.getName()+" but follow-up failed: "+error);
                p.sendMessage(ColorUtil.c("&cПрокрут уже сохранён. Проверьте /wheel status и /wheel claim; при проблеме обратитесь к администратору."));
            }
        }
    }
    private Delivery deliver(Player p,Pending pending)throws Exception {
        if(pending.state.equals("delivering")) {
            p.sendMessage(ColorUtil.c("&cПовторная выдача заблокирована: обратитесь к администратору с квитанцией "+pending.receipt+"."));
            return Delivery.REVIEW;
        }
        ItemStack[] items=pending.prize.items();
        if(!InventoryCapacity.canFit(p,items)) {
            p.sendMessage(ColorUtil.c("&eПриз сохранён. Освободите место и введите /wheel claim."));return Delivery.WAITING;
        }
        String base=wheel(p)+".pending";
        plugin.getStore().transact(y->{
            Pending latest=pending(y,p);
            if(latest==null||!latest.state.equals("ready")||!latest.receipt.equals(pending.receipt))throw new IOException("pending prize changed");
            y.set(base+".state","delivering");return null;
        });
        try {
            Map<Integer,ItemStack> leftovers=p.getInventory().addItem(items);
            if(!leftovers.isEmpty()) {
                // A third-party change during delivery makes replay ambiguous.
                // Keep the receipt in 'delivering' for manual reconciliation.
                plugin.getLogger().severe("[Wheel] Partial item delivery for "+p.getName()+" receipt "+pending.receipt+"; administrator must reconcile the prize and inventory.");
                p.sendMessage(ColorUtil.c("&cИнвентарь изменился при выдаче. Администратору нужна квитанция "+pending.receipt+"."));
                return Delivery.REVIEW;
            }
            p.saveData(); // reduces the window, but Paper/filesystem do not promise an atomic cross-file commit.
            plugin.getStore().transact(y->{
                Pending latest=pending(y,p);
                if(latest==null||!latest.state.equals("delivering")||!latest.receipt.equals(pending.receipt))throw new IOException("delivery receipt changed");
                y.set(base,null);return null;
            });
            return Delivery.DELIVERED;
        } catch(Exception error) {
            plugin.getLogger().severe("[Wheel] Delivery for "+p.getName()+" receipt "+pending.receipt+" needs manual review: "+error);
            p.sendMessage(ColorUtil.c("&cВыдача требует проверки администратора. Квитанция: "+pending.receipt+"."));
            return Delivery.REVIEW;
        }
    }
    private void animate(Player p,Prize result,Delivery delivery) {
        new BukkitRunnable(){int step=0;public void run(){
            if(!p.isOnline()){cancel();return;}
            if(step<8){
                String[] symbols={"&#FFD740★ &f❄ &#00E5FF❖","&#00E5FF❖ &f★ &#FF4081🎁","&#FF4081🎁 &f❄ &#FFD740★"};
                p.sendTitle(ColorUtil.c("&#FF4081&l🎡 КОЛЕСО ФОРТУНЫ"),ColorUtil.c(symbols[step%3]),0,8,0);
                ColorUtil.sound(p,"block.note_block.hat",.6f,.8f+step*.08f);step++;return;
            }
            p.sendTitle(ColorUtil.c("&#FF4081&l🎡 КОЛЕСО ФОРТУНЫ"),ColorUtil.c("&fПриз: "+result.label),0,50,10);
            p.sendMessage(ColorUtil.c("&#FF4081🎡 Вы выиграли: "+result.label+"&f!"));
            if(delivery==Delivery.WAITING)p.sendMessage(ColorUtil.c("&eПредметы сохранены до /wheel claim."));
            ColorUtil.sound(p,"entity.player.levelup",1f,1.3f);cancel();
        }}.runTaskTimer(plugin,0L,4L);
    }
    private void refuse(Player p,Exception error) {
        plugin.getLogger().warning("[Wheel] Operation refused for "+p.getName()+": "+error.getMessage());
        p.sendMessage(ColorUtil.c("&cНе удалось безопасно выполнить операцию. Проверьте журнал сервера; повтор не списывал валюту."));
    }
}
