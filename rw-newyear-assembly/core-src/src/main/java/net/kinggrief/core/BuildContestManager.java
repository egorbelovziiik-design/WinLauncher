package net.kinggrief.core;

import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/** Opt-in contest for existing Core claims. Never edits blocks, claims or player inventories. */
final class BuildContestManager {
    private static final String ROOT="buildContest";
    private static final int MAX_ENTRIES=64, MAX_VOTERS=2048, MAX_PENDING=512, PAGE_SIZE=6;
    private static final long MAX_MINUTES=7L*24*60;
    private static final class Entry {
        UUID owner;String name,claim,world;int number,x,y,z,radius;
    }
    private static final class State {
        String phase,round,result;long deadline;int sequence;
        final List<Entry> entries=new ArrayList<Entry>();
        final Map<UUID,Integer> issued=new LinkedHashMap<UUID,Integer>();
        final Map<UUID,UUID> votes=new LinkedHashMap<UUID,UUID>();
        Entry entry(UUID id){for(Entry e:entries)if(e.owner.equals(id))return e;return null;}
        Entry entry(int number){for(Entry e:entries)if(e.number==number)return e;return null;}
    }
    private static final class Vote {
        final UUID voter,target;final String round;final long accepted;
        Vote(UUID voter,UUID target,String round,long accepted){this.voter=voter;this.target=target;this.round=round;this.accepted=accepted;}
    }
    private static final class Result {
        String message,winner;int votes;
    }
    private final KingGriefCore plugin;
    private final Map<UUID,Vote> pending=new LinkedHashMap<UUID,Vote>();
    private final Map<UUID,Long> lastEntryChange=new LinkedHashMap<UUID,Long>();
    private BukkitRunnable ticker;
    private long lastWarning,lastVoteFailureNotice;
    BuildContestManager(KingGriefCore plugin){this.plugin=plugin;}
    void start(){ticker=new BukkitRunnable(){@Override public void run(){if(flush())finishIfDue();}};ticker.runTaskTimer(plugin,20L,20L);}
    void shutdown(){if(ticker!=null)ticker.cancel();flush();}
    private static UUID uuid(String value){try{return UUID.fromString(value);}catch(Exception ignored){return null;}}
    private static String p(UUID player){return ROOT+".entries."+player;}
    private static String safe(String text){return text==null?"?":text.replace('§','_').replace('&','_').replace('\n','_').replace('\r','_');}
    private static State read(YamlConfiguration data){
        State state=new State();state.phase=data.getString(ROOT+".phase","idle");state.round=data.getString(ROOT+".round","");
        state.deadline=data.getLong(ROOT+".deadline",0);state.sequence=data.getInt(ROOT+".sequence",0);state.result=data.getString(ROOT+".result","");
        java.util.Set<Integer> usedNumbers=new java.util.HashSet<Integer>();
        ConfigurationSection issued=data.getConfigurationSection(ROOT+".issued");
        if(issued!=null){
            int seen=0;for(String key:issued.getKeys(false)){
                if(++seen>MAX_ENTRIES)break;UUID owner=uuid(key);int number=issued.getInt(key,0);
                if(owner!=null&&number>=1&&number<=MAX_ENTRIES&&number<=state.sequence&&usedNumbers.add(number))state.issued.put(owner,number);
            }
        }
        ConfigurationSection entries=data.getConfigurationSection(ROOT+".entries");
        if(entries!=null){
            int seen=0;for(String key:entries.getKeys(false)){
                if(++seen>MAX_ENTRIES)break;UUID owner=uuid(key);if(owner==null)continue;
                Entry e=new Entry();e.owner=owner;e.name=entries.getString(key+".name","");e.claim=entries.getString(key+".claim","");
                e.world=entries.getString(key+".world","");e.number=entries.getInt(key+".number",0);e.x=entries.getInt(key+".x");
                e.y=entries.getInt(key+".y");e.z=entries.getInt(key+".z");e.radius=entries.getInt(key+".radius",-1);
                if(!e.name.matches("[A-Za-z0-9_]{1,16}")||e.claim.length()==0||e.claim.length()>100||e.world.length()==0||e.world.length()>128
                        ||e.number<1||e.number>MAX_ENTRIES||e.number>state.sequence||e.radius<0||e.radius>4096)continue;
                Integer reserved=state.issued.get(owner);
                if(reserved!=null&&reserved.intValue()!=e.number)continue;
                // Active contests created before issued-numbers were introduced are upgraded lazily.
                if(reserved==null){if(!usedNumbers.add(e.number))continue;state.issued.put(owner,e.number);}
                state.entries.add(e);
            }
        }
        Collections.sort(state.entries,Comparator.comparingInt(e->e.number));
        ConfigurationSection votes=data.getConfigurationSection(ROOT+".votes");
        if(votes!=null){
            int seen=0;for(String key:votes.getKeys(false)){
                if(++seen>MAX_VOTERS)break;UUID voter=uuid(key),target=uuid(votes.getString(key));
                if(voter!=null&&target!=null&&!voter.equals(target))state.votes.put(voter,target);
            }
        }
        return state;
    }
    private State state() throws Exception{return plugin.getStore().inspect(BuildContestManager::read);}
    private boolean valid(Entry entry){
        if(entry==null)return false;
        for(DataStore.Claim claim:plugin.getStore().getClaimsOf(entry.name)){
            if(claim.id.equals(entry.claim)&&claim.world.equalsIgnoreCase(entry.world)&&claim.cx==entry.x&&claim.cy==entry.y
                    &&claim.cz==entry.z&&claim.radius==entry.radius)return true;
        }
        return false;
    }
    private int validEntries(State state){int count=0;for(Entry entry:state.entries)if(valid(entry))count++;return count;}
    private static boolean running(State state){return "entries".equals(state.phase)||"voting".equals(state.phase);}
    private boolean mayChangeEntry(Player player){
        Long previous=lastEntryChange.get(player.getUniqueId());
        if(previous!=null&&System.nanoTime()-previous.longValue()<1_000_000_000L){
            send(player,"&eПодождите секунду перед следующим изменением заявки.");return false;
        }
        return true;
    }
    private void markEntryChange(UUID player){
        lastEntryChange.remove(player);lastEntryChange.put(player,System.nanoTime());
        while(lastEntryChange.size()>4096)lastEntryChange.remove(lastEntryChange.keySet().iterator().next());
    }
    void command(CommandSender sender,String[] args,boolean admin){
        if(sender instanceof Player&&!plugin.getStore().isAuthenticated((Player)sender)){send(sender,"&cСначала войдите в аккаунт.");return;}
        String sub=args.length==0?"status":args[0].toLowerCase(Locale.ROOT);
        if("start".equals(sub)||"voting".equals(sub)||"finish".equals(sub)||"cancel".equals(sub)){
            if(!admin){send(sender,"&cУправлять конкурсом могут OWNER, ТЕХ.АДМИН и консоль.");return;}
            if("start".equals(sub)){begin(sender,args);return;}
            if("voting".equals(sub)){startVoting(sender,args);return;}
            if("finish".equals(sub)){if(args.length!=1)send(sender,"&e/buildcontest finish");else finish(sender,false);return;}
            if(args.length!=2||!"confirm".equalsIgnoreCase(args[1])){send(sender,"&e/buildcontest cancel confirm &7— отменить текущий конкурс без победителя.");return;}
            cancel(sender);return;
        }
        if("status".equals(sub)&&args.length<=1){show(sender);return;}
        if("list".equals(sub)&&args.length<=2){
            int page=1;if(args.length==2)try{page=Integer.parseInt(args[1]);}catch(NumberFormatException ignored){page=0;}
            if(page<1){send(sender,"&cУкажите номер страницы от 1.");return;}list(sender,page);return;
        }
        if(!(sender instanceof Player)){send(sender,"&cУчаствовать и голосовать могут только игроки.");return;}
        Player player=(Player)sender;
        if("enter".equals(sub)){
            if(args.length!=2||!"confirm".equalsIgnoreCase(args[1])){
                send(sender,"&e/buildcontest enter confirm &7— подать свой встроенный приват. Его мир и координаты станут публичными.");return;
            }
            enter(player);return;
        }
        if("withdraw".equals(sub)&&args.length==1){withdraw(player);return;}
        if("vote".equals(sub)&&args.length==3){vote(player,args[1],args[2]);return;}
        send(sender,"&e/buildcontest &8| &e/buildcontest list [страница] &8| &e/buildcontest enter confirm &8| &e/buildcontest withdraw");
        send(sender,"&7Для голоса нажмите кнопку у участника в списке. Победитель получает только косметический титул.");
    }
    private void send(CommandSender sender,String message){
        try{sender.sendMessage(ColorUtil.c(message));}catch(RuntimeException error){warn("notification",error);}
    }
    private void announce(String message){
        try{Bukkit.broadcastMessage(ColorUtil.c(message));}catch(RuntimeException error){warn("broadcast",error);}
    }
    private static int minutes(String[] args){
        if(args.length!=2)return 0;try{int value=Integer.parseInt(args[1]);return value>=1&&value<=MAX_MINUTES?value:0;}
        catch(NumberFormatException ignored){return 0;}
    }
    private void begin(CommandSender sender,String[] args){
        int duration=minutes(args);if(duration==0){send(sender,"&e/buildcontest start <1–10080 минут>");return;}
        try{
            State current=state();if(running(current)){send(sender,"&cКонкурс уже идёт; сначала завершите или отмените его.");return;}
            if(!"idle".equals(current.phase)&&!"closed".equals(current.phase)){send(sender,"&cНеизвестное состояние конкурса; проверьте data.yml.");return;}
            if(!pending.isEmpty()){send(sender,"&cЕсть несохранённые голоса; сначала повторите операцию после их обработки.");return;}
            final String round=UUID.randomUUID().toString();final long deadline=System.currentTimeMillis()+duration*60_000L;
            plugin.getStore().transact(data->{data.set(ROOT+".round",round);data.set(ROOT+".phase","entries");data.set(ROOT+".deadline",deadline);
                data.set(ROOT+".sequence",0);data.set(ROOT+".entries",null);data.set(ROOT+".issued",null);data.set(ROOT+".votes",null);return null;});
            announce("&#80D8FF✦ Конкурс построек: приём заявок открыт на &f"+duration+" мин. &8| &e/buildcontest");
        }catch(Exception error){send(sender,"&cНе удалось сохранить новый конкурс. Изменения не применены.");warn("start",error);}
    }
    private void startVoting(CommandSender sender,String[] args){
        int duration=minutes(args);if(duration==0){send(sender,"&e/buildcontest voting <1–10080 минут>");return;}
        try{
            State current=state();if(!"entries".equals(current.phase)){send(sender,"&cСейчас нет приёма заявок.");return;}
            if(validEntries(current)<2){send(sender,"&cДля голосования нужны минимум два действующих привата участников.");return;}
            final String round=current.round;final long deadline=System.currentTimeMillis()+duration*60_000L;
            plugin.getStore().transact(data->{State live=read(data);if(!"entries".equals(live.phase)||!round.equals(live.round))throw new IllegalStateException("round changed");
                data.set(ROOT+".phase","voting");data.set(ROOT+".deadline",deadline);return null;});
            announce("&#FFD740✦ Голосование за постройки открыто на &f"+duration+" мин. &8| &e/buildcontest list");
        }catch(Exception error){send(sender,"&cНе удалось сохранить начало голосования.");warn("voting",error);}
    }
    private void enter(Player player){
        DataStore.Claim claim=plugin.getStore().getClaimAt(player.getLocation());
        if(claim==null||!claim.owner.equalsIgnoreCase(player.getName())){send(player,"&cВстаньте внутри своего встроенного привата. Чужой приват подать нельзя.");return;}
        try{
            State current=state();long now=System.currentTimeMillis();
            if(!"entries".equals(current.phase)||now>=current.deadline){send(player,"&cПриём заявок закрыт.");return;}
            final UUID owner=player.getUniqueId();
            if(current.entry(owner)!=null){send(player,"&eВы уже участвуете. До голосования заявку можно отозвать: /buildcontest withdraw");return;}
            Integer reserved=current.issued.get(owner);final boolean first=reserved==null;
            if(current.entries.size()>=MAX_ENTRIES||(first&&current.sequence>=MAX_ENTRIES)){send(player,"&cЛимит участников этого конкурса достигнут.");return;}
            if(!mayChangeEntry(player))return;
            final String round=current.round;final int number=first?current.sequence+1:reserved.intValue();
            plugin.getStore().transact(data->{State live=read(data);Integer existing=live.issued.get(owner);
                boolean consistent=first?existing==null&&live.sequence+1==number:existing!=null&&existing.intValue()==number;
                if(!"entries".equals(live.phase)||!round.equals(live.round)||System.currentTimeMillis()>=live.deadline
                        ||live.entry(owner)!=null||!consistent)throw new IllegalStateException("entry phase changed");
                String root=p(owner);data.set(root+".name",player.getName());data.set(root+".claim",claim.id);data.set(root+".world",claim.world);
                data.set(root+".x",claim.cx);data.set(root+".y",claim.cy);data.set(root+".z",claim.cz);data.set(root+".radius",claim.radius);
                data.set(root+".number",number);data.set(ROOT+".issued."+owner,number);if(first)data.set(ROOT+".sequence",number);return null;});
            markEntryChange(owner);
            send(player,"&#00E676Заявка №"+number+" сохранена. Мир и координаты привата опубликованы в /buildcontest list.");
        }catch(Exception error){send(player,"&cЗаявка не сохранена; приват не изменён.");warn("entry",error);}
    }
    private void withdraw(Player player){
        try{
            State current=state();UUID owner=player.getUniqueId();
            if((!"entries".equals(current.phase)&&!"closed".equals(current.phase))||current.entry(owner)==null){
                send(player,"&cОтозвать заявку можно до голосования или после завершения конкурса.");return;
            }
            if(!mayChangeEntry(player))return;
            final String round=current.round,phase=current.phase;
            plugin.getStore().transact(data->{State live=read(data);Entry entry=live.entry(owner);
                if(!phase.equals(live.phase)||!round.equals(live.round)||entry==null)throw new IllegalStateException("entry changed");
                data.set(ROOT+".issued."+owner,entry.number);data.set(p(owner),null);return null;});
            markEntryChange(owner);
            send(player,"&#00E676Заявка отозвана; приват и постройка не изменены.");
        }catch(Exception error){send(player,"&cЗаявку не удалось отозвать.");warn("withdraw",error);}
    }
    private void vote(Player player,String round,String numberText){
        int number;try{number=Integer.parseInt(numberText);}catch(NumberFormatException ignored){send(player,"&cНекорректный номер участника.");return;}
        try{
            State current=state();long now=System.currentTimeMillis();
            if(!"voting".equals(current.phase)||now>=current.deadline||!current.round.equals(round)){
                send(player,"&cГолосование завершено или список устарел. Обновите /buildcontest list.");return;
            }
            Entry target=current.entry(number);UUID voter=player.getUniqueId();
            if(target==null||!valid(target)){send(player,"&cЗаявка больше недоступна. Обновите список.");return;}
            if(voter.equals(target.owner)){send(player,"&cЗа свою постройку голосовать нельзя.");return;}
            if(!current.votes.containsKey(voter)&&!pending.containsKey(voter)&&current.votes.size()+pending.size()>=MAX_VOTERS){send(player,"&cЛимит голосов достигнут.");return;}
            if(!pending.containsKey(voter)&&pending.size()>=MAX_PENDING){send(player,"&eОчередь голосов заполнена, повторите позже.");return;}
            if(target.owner.equals(current.votes.get(voter))&&!pending.containsKey(voter)){send(player,"&7Ваш голос уже сохранён за этой постройкой.");return;}
            pending.put(voter,new Vote(voter,target.owner,current.round,now));
            send(player,"&eГолос поставлен в очередь; подтверждение придёт после сохранения. Его можно изменить до окончания.");
        }catch(Exception error){send(player,"&cНе удалось прочитать конкурс.");warn("vote",error);}
    }
    private boolean flush(){
        if(pending.isEmpty())return true;List<Vote> batch=new ArrayList<Vote>(pending.values());
        final Map<UUID,String> replies=new LinkedHashMap<UUID,String>();
        try{
            plugin.getStore().transact(data->{
                State state=read(data);int count=state.votes.size();Map<UUID,Boolean> validity=new LinkedHashMap<UUID,Boolean>();
                for(Vote vote:batch){
                    Entry target=state.entry(vote.target);Boolean available=validity.get(vote.target);
                    if(available==null){available=Boolean.valueOf(valid(target));validity.put(vote.target,available);}
                    if(!"voting".equals(state.phase)||!state.round.equals(vote.round)||vote.accepted>=state.deadline||!available.booleanValue()||vote.voter.equals(vote.target)){
                        replies.put(vote.voter,"&cГолос не сохранён: конкурс или заявка изменились.");continue;
                    }
                    if(!state.votes.containsKey(vote.voter)&&count>=MAX_VOTERS){replies.put(vote.voter,"&cЛимит голосов достигнут.");continue;}
                    if(!state.votes.containsKey(vote.voter))count++;
                    data.set(ROOT+".votes."+vote.voter,vote.target.toString());state.votes.put(vote.voter,vote.target);
                    replies.put(vote.voter,"&#00E676Голос сохранён за постройку №"+target.number+" (&f"+target.name+"&#00E676).");
                }
                return null;
            });
        }catch(Exception error){
            // Keep accepted votes in the bounded queue; a failed save must not close the round.
            long now=System.nanoTime();
            if(lastVoteFailureNotice==0||now-lastVoteFailureNotice>=30_000_000_000L){
                lastVoteFailureNotice=now;
                for(Vote vote:batch){Player player=Bukkit.getPlayer(vote.voter);if(player!=null&&player.isOnline())
                    send(player,"&cГолос ещё не сохранён: очередь повторит запись. При аварийной остановке он может потеряться.");}
            }
            warn("vote batch",error);return false;
        }
        for(Vote vote:batch)pending.remove(vote.voter,vote);
        for(Map.Entry<UUID,String> reply:replies.entrySet()){
            Player player=Bukkit.getPlayer(reply.getKey());
            if(player!=null&&player.isOnline())try{send(player,reply.getValue());}catch(RuntimeException error){warn("notification",error);}
        }
        return true;
    }
    private Result result(State state){
        Result result=new Result();Map<UUID,Integer> counts=new LinkedHashMap<UUID,Integer>();
        for(Entry entry:state.entries)if(valid(entry))counts.put(entry.owner,0);
        for(Map.Entry<UUID,UUID> vote:state.votes.entrySet())if(!vote.getKey().equals(vote.getValue())&&counts.containsKey(vote.getValue()))
            counts.put(vote.getValue(),counts.get(vote.getValue())+1);
        if(counts.size()<2){result.message="меньше двух действующих участников";return result;}
        int best=0;boolean tied=false;Entry winner=null;
        for(Entry entry:state.entries){Integer count=counts.get(entry.owner);if(count==null)continue;
            if(count>best){best=count;winner=entry;tied=false;}else if(count==best&&count>0)tied=true;
        }
        if(best==0){result.message="нет голосов";return result;}
        if(tied){result.message="ничья ("+best+" голосов)";return result;}
        result.winner=winner.name;result.votes=best;result.message="победитель "+winner.name+" ("+best+" голосов)";return result;
    }
    private void finishIfDue(){
        try{State state=state();if("voting".equals(state.phase)&&System.currentTimeMillis()>=state.deadline)finish(null,true);}
        catch(Exception error){warn("deadline",error);}
    }
    private void finish(CommandSender sender,boolean due){
        if(!flush()){if(sender!=null)send(sender,"&cГолоса не сохранены; голосование не закрыто.");return;}
        Result completed;
        try{
            State current=state();if(!"voting".equals(current.phase)){if(sender!=null)send(sender,"&cГолосование сейчас не идёт.");return;}
            if(due&&System.currentTimeMillis()<current.deadline)return;
            final String round=current.round;
            completed=plugin.getStore().transact(data->{State live=read(data);if(!"voting".equals(live.phase)||!round.equals(live.round))
                throw new IllegalStateException("round changed");Result computed=result(live);
                data.set(ROOT+".phase","closed");data.set(ROOT+".deadline",0L);data.set(ROOT+".result",computed.message);
                if(computed.winner!=null)data.set("players."+computed.winner.toLowerCase(Locale.ROOT)+".titles.build_master",true);return computed;});
        }catch(Exception error){if(sender!=null)send(sender,"&cРезультат не сохранён; конкурс остаётся открытым.");warn("finish",error);return;}
        try{Bukkit.broadcastMessage(ColorUtil.c("&#FFD740✦ Конкурс построек завершён: &f"+completed.message+"&7."
                    +(completed.winner==null?"":" Победителю открыт косметический титул /titles.")));}
        catch(RuntimeException error){warn("result notification",error);}
    }
    private void cancel(CommandSender sender){
        try{
            State current=state();if(!running(current)){send(sender,"&cСейчас нет активного конкурса.");return;}
            final String round=current.round;
            plugin.getStore().transact(data->{State live=read(data);if(!running(live)||!round.equals(live.round))throw new IllegalStateException("round changed");
                data.set(ROOT+".phase","closed");data.set(ROOT+".deadline",0L);data.set(ROOT+".result","отменён администрацией");return null;});
        }catch(Exception error){send(sender,"&cНе удалось сохранить отмену конкурса.");warn("cancel",error);return;}
        List<Vote> cancelled=new ArrayList<Vote>(pending.values());pending.clear();
        try{
            for(Vote vote:cancelled){Player player=Bukkit.getPlayer(vote.voter);if(player!=null&&player.isOnline())send(player,"&eКонкурс отменён, голос не сохранён.");}
            Bukkit.broadcastMessage(ColorUtil.c("&eКонкурс построек отменён администрацией без победителя."));
        }catch(RuntimeException error){warn("cancel notification",error);}
    }
    private void show(CommandSender sender){
        try{
            State state=state();long remaining=Math.max(0,(state.deadline-System.currentTimeMillis()+999)/1000);
            if("entries".equals(state.phase))send(sender,"&#80D8FFКонкурс построек &8• &fприём заявок "
                    +(remaining>0?"ещё "+ColorUtil.fmtTime(remaining):"окончен, ожидается администратор")+" &8| &7заявок: &f"+state.entries.size());
            else if("voting".equals(state.phase))send(sender,"&#FFD740Конкурс построек &8• &fголосование "
                    +(remaining>0?"ещё "+ColorUtil.fmtTime(remaining):"завершается")+" &8| &7сохранённых голосов: &f"+state.votes.size());
            else send(sender,"&7Конкурс построек не идёт."+(state.result.isEmpty()?"":" Последний результат: &f"+safe(state.result)));
            send(sender,"&e/buildcontest list &7— заявки и публичные координаты. Конкурс не изменяет приват или блоки.");
        }catch(Exception error){send(sender,"&cНе удалось прочитать состояние конкурса.");warn("status",error);}
    }
    private void list(CommandSender sender,int page){
        try{
            State state=state();int pages=Math.max(1,(state.entries.size()+PAGE_SIZE-1)/PAGE_SIZE);
            if(page>pages){send(sender,"&cТакой страницы нет. Всего: "+pages);return;}
            send(sender,"&#80D8FFЗаявки конкурса &8• &f"+page+"/"+pages+" &8| &7координаты опубликованы участниками");
            int from=(page-1)*PAGE_SIZE,to=Math.min(state.entries.size(),from+PAGE_SIZE);
            for(int i=from;i<to;i++){
                Entry entry=state.entries.get(i);boolean valid=valid(entry);
                send(sender," &8#"+entry.number+" &f"+entry.name+" &7— "+safe(entry.world)+" &8[&f"+entry.x+", "+entry.y+", "+entry.z+"&8]"
                        +(valid?"":" &c(приват больше недоступен)"));
                if(valid&&sender instanceof Player&&"voting".equals(state.phase)&&System.currentTimeMillis()<state.deadline){
                    TextComponent button=new TextComponent(ColorUtil.c("    &e[ ГОЛОСОВАТЬ ЗА №"+entry.number+" ]"));
                    button.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND,"/buildcontest vote "+state.round+" "+entry.number));
                    button.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,new ComponentBuilder(ColorUtil.c("&7Один голос на аккаунт, его можно изменить. За себя голосовать нельзя.")).create()));
                    ((Player)sender).spigot().sendMessage(button);
                }
            }
            if(state.entries.isEmpty())send(sender,"&7Заявок пока нет.");
            send(sender,"&8Постройки не замораживаются и могут измениться; приват проверяется при голосе и подведении итогов.");
        }catch(Exception error){send(sender,"&cНе удалось прочитать заявки.");warn("list",error);}
    }
    private void warn(String action,Exception error){
        long now=System.currentTimeMillis();if(now-lastWarning>=10_000L||now<lastWarning){lastWarning=now;plugin.getLogger().warning("[BuildContest] "+action+" failed: "+error.getMessage());}
    }
}
