package net.kinggrief.core;

import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import java.io.IOException;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/** A shared, persistent decoration goal. It does not modify the spawn or its blocks. */
final class WinterTreeManager {
    private static final int[] GOALS = {30, 100, 300};
    private static final int[] COINS = {5000, 15000, 35000};
    private static final int DAILY_LIMIT = 10;
    private final KingGriefCore plugin;
    WinterTreeManager(KingGriefCore plugin) { this.plugin=plugin; }

    void command(Player p, String[] args) {
        String sub=args.length==0 ? "status" : args[0].toLowerCase(Locale.ROOT);
        if ("status".equals(sub)) status(p);
        else if ("donate".equals(sub)) donate(p,args);
        else if ("claim".equals(sub)) claim(p);
        else if ("top".equals(sub)) top(p);
        else p.sendMessage(ColorUtil.c("&e/tree <status|donate [1-10]|claim|top>"));
    }
    private void status(Player p) {
        String name=p.getName().toLowerCase(Locale.ROOT), today=day();
        try {
            int[] v=plugin.getStore().inspect(data -> {
                int total=Math.max(0,data.getInt("winterTree.total",0));
                int mine=Math.max(0,data.getInt("winterTree.donors."+name+".total",0));
                String path="winterTree.donors."+name;
                int daily=today.equals(data.getString(path+".day"))?Math.max(0,data.getInt(path+".daily",0)):0;
                return new int[]{total,mine,daily};
            });
            int stage=stage(v[0]), next=stage<GOALS.length?GOALS[stage]:GOALS[GOALS.length-1];
            p.sendMessage(ColorUtil.c("&#80D8FF❄ Общая ёлка: &f"+v[0]+"/"+next+" ❄ &8| &eэтап "+stage+"/3"));
            p.sendMessage(ColorUtil.c("&7Ваш вклад: &f"+v[1]+" ❄ &8| &7сегодня: &f"+v[2]+"/"+DAILY_LIMIT+" (UTC)"));
            p.sendMessage(ColorUtil.c("&e/tree donate [1-10] &8• &e/tree claim &8• &e/tree top"));
        } catch (Exception error) {p.sendMessage(ColorUtil.c("&cНе удалось прочитать состояние ёлки."));}
    }
    private void donate(Player p,String[] args) {
        int requested=1;
        if(args.length>2){p.sendMessage(ColorUtil.c("&e/tree donate [1-10]"));return;}
        if(args.length==2) try {requested=Integer.parseInt(args[1]);} catch(NumberFormatException ignored){requested=0;}
        if(requested<1||requested>DAILY_LIMIT){p.sendMessage(ColorUtil.c("&cКоличество — от 1 до "+DAILY_LIMIT+" снежинок."));return;}
        final int amount=requested;
        final String name=p.getName().toLowerCase(Locale.ROOT), today=day();
        try {
            int[] result=plugin.getStore().transact(data -> {
                String path="winterTree.donors."+name, balance="players."+name+".snowflakes";
                int total=data.getInt("winterTree.total",0), previous=data.getInt(path+".total",0);
                int daily=today.equals(data.getString(path+".day"))?data.getInt(path+".daily",0):0;
                int snow=data.getInt(balance,0);
                if(total<0||total>=GOALS[GOALS.length-1]) throw new IllegalArgumentException("Цель уже достигнута.");
                if(daily<0||daily+amount>DAILY_LIMIT)throw new IllegalArgumentException("Сегодняшний лимит: "+DAILY_LIMIT+" снежинок (UTC).");
                if(amount>GOALS[GOALS.length-1]-total)throw new IllegalArgumentException("До цели осталось "+(GOALS[GOALS.length-1]-total)+" снежинок.");
                if(snow<amount)throw new IllegalArgumentException("Недостаточно снежинок.");
                if(previous<0 || (long)previous+amount>Integer.MAX_VALUE)throw new IOException("invalid donor balance");
                data.set(balance,snow-amount);
                if(previous==0) data.set(path+".joinedAt",total);
                data.set(path+".day",today); data.set(path+".daily",daily+amount);
                data.set(path+".total",previous+amount); data.set("winterTree.total",total+amount);
                return new int[]{total,total+amount};
            });
            p.sendMessage(ColorUtil.c("&#80D8FFСпасибо! В ёлку внесено &f"+amount+" ❄&7. Прогресс: &f"+result[1]+"/300"));
            int before=stage(result[0]), after=stage(result[1]);
            if(after>before)Bukkit.broadcastMessage(ColorUtil.c("&#FFD740✦ Игроки украсили ёлку! Достигнут этап &f"+after+"/3&7. Участникам: &e/tree claim"));
        } catch(IllegalArgumentException rejected){p.sendMessage(ColorUtil.c("&c"+rejected.getMessage()));}
        catch(Exception error){plugin.getLogger().warning("[Tree] Donation not committed: "+error.getMessage());p.sendMessage(ColorUtil.c("&cНе удалось сохранить взнос. Снежинки не списаны."));}
    }
    private void claim(Player p) {
        final String name=p.getName().toLowerCase(Locale.ROOT);
        try {
            int[] result=plugin.getStore().transact(data -> {
                int total=data.getInt("winterTree.total",0), contribution=data.getInt("winterTree.donors."+name+".total",0);
                int joinedAt=data.getInt("winterTree.donors."+name+".joinedAt",0);
                if(contribution<=0)throw new IllegalArgumentException("Сначала внесите хотя бы одну снежинку: /tree donate");
                int earned=0, count=0;boolean last=false;
                for(int i=0;i<GOALS.length;i++) if(joinedAt<GOALS[i]&&total>=GOALS[i]&&!data.getBoolean("winterTree.donors."+name+".claimed."+(i+1),false)) {
                    earned+=COINS[i];count++;if(i==GOALS.length-1)last=true;
                }
                if(count==0)throw new IllegalArgumentException("Новых наград пока нет.");
                String base="players."+name;
                double coins=data.getDouble(base+".coins",plugin.getConfig().getDouble("starting-coins",2500.0));
                if(!Double.isFinite(coins)||coins<0||!Double.isFinite(coins+earned)||coins+earned<=coins)throw new IOException("invalid coin balance");
                int keys=data.getInt(base+".cases.legendary",0);
                if(last&&(keys<0||keys==Integer.MAX_VALUE))throw new IOException("invalid key balance");
                data.set(base+".coins",coins+earned);
                if(last){data.set(base+".cases.legendary",keys+1);data.set(base+".titles.tree_friend",true);}
                for(int i=0;i<GOALS.length;i++)if(joinedAt<GOALS[i]&&total>=GOALS[i])data.set("winterTree.donors."+name+".claimed."+(i+1),true);
                return new int[]{earned,count,last?1:0};
            });
            p.sendMessage(ColorUtil.c("&#00E676Награда ёлки: &f+"+ColorUtil.fmtMoney(result[0])+" монет"+(result[2]>0?" &8• &dключ legendary &8• &bтитул Друга ёлки":"")+"&7. Этапов: "+result[1]));
        } catch(IllegalArgumentException rejected){p.sendMessage(ColorUtil.c("&e"+rejected.getMessage()));}
        catch(Exception error){plugin.getLogger().warning("[Tree] Claim not committed: "+error.getMessage());p.sendMessage(ColorUtil.c("&cНе удалось сохранить награду. Попробуйте позже."));}
    }
    private void top(Player p) {
        try {
            List<String[]> entries=plugin.getStore().inspect(data -> {
                List<String[]> out=new ArrayList<String[]>();
                ConfigurationSection section=data.getConfigurationSection("winterTree.donors");
                if(section!=null)for(String name:section.getKeys(false)){
                    int value=section.getInt(name+".total",0);
                    if(value>0&&name.matches("[a-z0-9_]{1,16}"))out.add(new String[]{name,Integer.toString(value)});
                }
                return out;
            });
            entries.sort(Comparator.<String[]>comparingInt(e->Integer.parseInt(e[1])).reversed().thenComparing(e->e[0]));
            p.sendMessage(ColorUtil.c("&#80D8FF✦ Топ помощников зимней ёлки:"));
            for(int i=0;i<Math.min(10,entries.size());i++)p.sendMessage(ColorUtil.c("&7"+(i+1)+". &f"+entries.get(i)[0]+" &8— &b"+entries.get(i)[1]+" ❄"));
            if(entries.isEmpty())p.sendMessage(ColorUtil.c("&7Пока нет взносов."));
        } catch(Exception error){p.sendMessage(ColorUtil.c("&cНе удалось прочитать топ."));}
    }
    private static int stage(int total){int stage=0;for(int goal:GOALS)if(total>=goal)stage++;return stage;}
    private static String day(){return LocalDate.now(ZoneOffset.UTC).toString();}
}
