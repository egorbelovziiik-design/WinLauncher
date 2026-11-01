package net.kinggrief.core;

import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/** A voluntary, temporary vote between already available activities; never teleports players. */
final class MiniGameVoteManager {
    private enum Choice {
        PARKOUR("parkour","Паркур","/warp parkour"), DUEL("duel","Дуэли","/duel <ник> 0");
        final String id,label,join;
        Choice(String id,String label,String join){this.id=id;this.label=label;this.join=join;}
        static Choice parse(String id){for(Choice c:values())if(c.id.equalsIgnoreCase(id))return c;return null;}
    }
    private static final int MAX_VOTERS=4096;
    private final KingGriefCore plugin;
    private final Map<UUID,Choice> votes=new HashMap<UUID,Choice>();
    private long deadline;
    private String lastResult;
    private BukkitRunnable ticker;
    MiniGameVoteManager(KingGriefCore plugin){this.plugin=plugin;}
    void start(){ticker=new BukkitRunnable(){@Override public void run(){finishIfDue();}};ticker.runTaskTimer(plugin,20L,20L);}
    void shutdown(){if(ticker!=null)ticker.cancel();votes.clear();deadline=0;}
    void command(CommandSender sender,String[] args,boolean admin){
        if(sender instanceof Player&&!plugin.getStore().isAuthenticated((Player)sender)){
            sender.sendMessage(ColorUtil.c("&cСначала войдите в аккаунт."));return;
        }
        finishIfDue();String sub=args.length==0?"status":args[0].toLowerCase(Locale.ROOT);
        if("start".equals(sub)){
            if(!admin){sender.sendMessage(ColorUtil.c("&cНачать голосование могут OWNER, ТЕХ.АДМИН и консоль."));return;}
            if(args.length>2){sender.sendMessage(ColorUtil.c("&e/gamevote start [60–300 секунд]"));return;}
            int seconds=120;if(args.length==2)try{seconds=Integer.parseInt(args[1]);}catch(NumberFormatException error){seconds=0;}
            if(seconds<60||seconds>300){sender.sendMessage(ColorUtil.c("&cПродолжительность — от 60 до 300 секунд."));return;}
            if(deadline!=0){sender.sendMessage(ColorUtil.c("&eГолосование уже идёт: /gamevote"));return;}
            votes.clear();lastResult=null;deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(seconds);
            Bukkit.broadcastMessage(ColorUtil.c("&#80D8FF&l✦ ГОЛОСОВАНИЕ ЗА ИГРУ &7("+seconds+" сек.)"));
            Bukkit.broadcastMessage(ColorUtil.c("&e/gamevote parkour &8— &fПаркур &8| &e/gamevote duel &8— &fДуэли"));
            Bukkit.broadcastMessage(ColorUtil.c("&7Голос можно изменить. Победитель объявляется без автоматического телепорта или запуска матча."));
            return;
        }
        if("cancel".equals(sub)){
            if(args.length!=1){sender.sendMessage(ColorUtil.c("&e/gamevote cancel"));return;}
            if(!admin){sender.sendMessage(ColorUtil.c("&cОтменить голосование могут OWNER, ТЕХ.АДМИН и консоль."));return;}
            if(deadline==0){sender.sendMessage(ColorUtil.c("&eСейчас голосование не идёт."));return;}
            deadline=0;votes.clear();lastResult="отменено администрацией";
            Bukkit.broadcastMessage(ColorUtil.c("&eГолосование за игру отменено администрацией."));return;
        }
        if("status".equals(sub)&&args.length<=1){status(sender);return;}
        Choice choice=Choice.parse(sub);
        if(choice==null||args.length!=1){sender.sendMessage(ColorUtil.c("&e/gamevote [parkour|duel] &7— голосование; &e/gamevote &7— состояние."));return;}
        if(!(sender instanceof Player)){sender.sendMessage(ColorUtil.c("&cГолосовать могут только игроки."));return;}
        Player player=(Player)sender;
        if(deadline==0){player.sendMessage(ColorUtil.c("&eСейчас голосование не идёт."));return;}
        if(!votes.containsKey(player.getUniqueId())&&votes.size()>=MAX_VOTERS){player.sendMessage(ColorUtil.c("&cДостигнут лимит голосов."));return;}
        Choice before=votes.put(player.getUniqueId(),choice);
        player.sendMessage(ColorUtil.c(before==choice?"&7Ваш голос уже за «"+choice.label+"».":"&#00E676Ваш голос: &f"+choice.label+"&7. Его можно изменить до конца голосования."));
    }
    private void status(CommandSender sender){
        if(deadline==0){sender.sendMessage(ColorUtil.c("&7Сейчас голосование не идёт."+(lastResult==null?"":" Последний результат: &f"+lastResult)));return;}
        long seconds=Math.max(0,TimeUnit.NANOSECONDS.toSeconds(Math.max(0,deadline-System.nanoTime())+999_999_999L));
        sender.sendMessage(ColorUtil.c("&#80D8FFГолосование за игру &8• &f"+seconds+" сек. &8| &7Паркур: &f"+count(Choice.PARKOUR)+" &8| &7Дуэли: &f"+count(Choice.DUEL)));
        if(sender instanceof Player){
            Player p=(Player)sender;
            for(Choice choice:Choice.values()){
                TextComponent button=new TextComponent(ColorUtil.c(" &e[ " + choice.label + " ]"));
                button.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND,"/gamevote "+choice.id));
                button.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,new ComponentBuilder(ColorUtil.c("&7Нажмите, чтобы проголосовать.")).create()));
                p.spigot().sendMessage(button);
            }
        }
    }
    private int count(Choice choice){int n=0;for(Choice vote:votes.values())if(vote==choice)n++;return n;}
    private void finishIfDue(){if(deadline!=0&&System.nanoTime()-deadline>=0)finish();}
    private void finish(){
        int parkour=count(Choice.PARKOUR),duel=count(Choice.DUEL);deadline=0;votes.clear();
        if(parkour==0&&duel==0){lastResult="нет голосов";Bukkit.broadcastMessage(ColorUtil.c("&eГолосование завершено: голосов не было."));return;}
        if(parkour==duel){lastResult="ничья ("+parkour+":"+duel+")";Bukkit.broadcastMessage(ColorUtil.c("&eГолосование завершено вничью: &f"+parkour+":"+duel+"&e. Игра автоматически не запускается."));return;}
        Choice winner=parkour>duel?Choice.PARKOUR:Choice.DUEL;
        lastResult=winner.label+" ("+parkour+":"+duel+")";
        Bukkit.broadcastMessage(ColorUtil.c("&#00E676Победило голосование за &f"+winner.label+" &8("+parkour+":"+duel+")&7. Для участия: &e"+winner.join));
        Bukkit.broadcastMessage(ColorUtil.c("&7Матч автоматически не запускается; телепорта и списания монет нет."));
    }
}
