package net.kinggrief.core;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * A name ban is written here before an online animation starts. Completing the
 * ban and deleting the journal entry are one durable data.yml transaction.
 * World/player files are separate stores; this is not a power-loss guarantee.
 */
final class PendingBanJournal {
    private static final String ROOT = "pendingAnimatedBans";
    private final DataStore store;

    static final class Entry {
        final String receipt, name, ip, staff, reason;
        final long duration;
        final boolean countStaff;
        Entry(String receipt, String name, String ip, String staff, String reason, long duration, boolean countStaff) {
            this.receipt=receipt; this.name=name; this.ip=ip; this.staff=staff; this.reason=reason;
            this.duration=duration; this.countStaff=countStaff;
        }
        boolean ipBan() { return !ip.isEmpty(); }
    }
    static final class Cancellation {
        final boolean hadIp;
        final Set<String> names;
        Cancellation(boolean hadIp, Set<String> names) { this.hadIp=hadIp; this.names=names; }
    }

    PendingBanJournal(DataStore store) { this.store=store; }
    static boolean validName(String name) { return name != null && name.matches("[A-Za-z0-9_]{1,16}"); }
    private static String key(String name) { return name.toLowerCase(Locale.ROOT); }
    private static long expires(long now, long duration) {
        return duration <= 0 || duration > Long.MAX_VALUE - now ? Long.MAX_VALUE : now + duration;
    }
    private String ipPath(String ip) throws IOException {
        String key=store.ipKey(ip);
        if (ip == null || ip.isEmpty() || ip.length()>128 || !key.matches("[a-z0-9_%\\-]+"))
            throw new IOException("invalid IP address");
        return "ipbans."+key;
    }
    private static ConfigurationSection root(YamlConfiguration y) throws IOException {
        if (!y.contains(ROOT)) return null;
        ConfigurationSection result=y.getConfigurationSection(ROOT);
        if (result==null) throw new IOException("invalid pending ban journal");
        return result;
    }
    private static Entry read(ConfigurationSection root, String nameKey) throws IOException {
        if (root==null || !root.contains(nameKey)) return null;
        ConfigurationSection section=root.getConfigurationSection(nameKey);
        if (section==null || !validName(nameKey)) throw new IOException("invalid pending ban record");
        Object rawDuration=section.get("durationMs"), rawCount=section.get("countStaff");
        String receipt=section.getString("receipt"), name=section.getString("name"), ip=section.getString("ip", "");
        String staff=section.getString("staff"), reason=section.getString("reason");
        if (!validName(name) || !name.equalsIgnoreCase(nameKey) || receipt==null || staff==null || reason==null
                || !(rawDuration instanceof Number) || !(rawCount instanceof Boolean)) throw new IOException("invalid pending ban fields");
        try { UUID.fromString(receipt); } catch (IllegalArgumentException error) { throw new IOException("invalid pending ban receipt",error); }
        long duration=((Number)rawDuration).longValue();
        boolean count=((Boolean)rawCount).booleanValue();
        if (duration<0 || (count && !validName(staff)) || ip==null || ip.length()>128) throw new IOException("invalid pending ban values");
        return new Entry(receipt,name,ip,staff,reason,duration,count);
    }
    private static List<Entry> entries(YamlConfiguration y) throws IOException {
        ConfigurationSection root=root(y); List<Entry> result=new ArrayList<Entry>();
        if (root!=null) for(String key:root.getKeys(false)) result.add(read(root,key));
        return result;
    }
    List<Entry> all() throws Exception { return store.inspect(PendingBanJournal::entries); }
    boolean has(String name) throws Exception {
        if (!validName(name)) return false;
        return store.inspect(y -> read(root(y),key(name))!=null);
    }
    private static void writeIp(YamlConfiguration y,String path,String ip,String target,long duration,String reason,String staff) {
        y.set(path+".ip",ip); y.set(path+".target",target); y.set(path+".until",expires(System.currentTimeMillis(),duration));
        y.set(path+".reason",reason); y.set(path+".staff",staff);
    }
    Entry queue(String name,String ip,long duration,String reason,String staff,boolean countStaff) throws Exception {
        if (!validName(name) || duration<0 || reason==null || staff==null || (countStaff && !validName(staff)))
            throw new IOException("invalid ban parameters");
        final String checkedIp=ip==null?"":ip;
        final String ipPath=checkedIp.isEmpty()?null:ipPath(checkedIp);
        final Entry entry=new Entry(UUID.randomUUID().toString(),name,checkedIp,staff,reason,duration,countStaff);
        return store.transact(y -> {
            if (read(root(y),key(name))!=null) throw new IOException("ban animation already pending");
            String base=ROOT+"."+key(name);
            y.set(base+".receipt",entry.receipt); y.set(base+".name",name); y.set(base+".ip",checkedIp);
            y.set(base+".staff",staff); y.set(base+".reason",reason); y.set(base+".durationMs",duration); y.set(base+".countStaff",countStaff);
            if (ipPath!=null) writeIp(y,ipPath,checkedIp,name,duration,reason,staff);
            return entry;
        });
    }
    void banLiteralIp(String ip,String targetLabel,long duration,String reason,String staff,boolean countStaff) throws Exception {
        if (duration<0 || reason==null || staff==null || (countStaff&&!validName(staff))) throw new IOException("invalid IP ban parameters");
        String path=ipPath(ip);
        store.transact(y -> {
            writeIp(y,path,ip,targetLabel,duration,reason,staff);
            if(countStaff) incrementStaff(y,staff);
            return null;
        });
    }
    private static void incrementStaff(YamlConfiguration y,String staff) {
        String path="staff."+key(staff)+".bans";
        int current=Math.max(0,y.getInt(path,0));
        y.set(path,current==Integer.MAX_VALUE?current:current+1);
    }
    boolean complete(Entry wanted) throws Exception {
        return store.transact(y -> {
            Entry entry=read(root(y),key(wanted.name));
            if(entry==null || !entry.receipt.equals(wanted.receipt)) return false;
            String path="bans."+key(entry.name);
            y.set(path+".until",expires(System.currentTimeMillis(),entry.duration));
            y.set(path+".reason",entry.reason); y.set(path+".staff",entry.staff);
            String historyPath="punishHistory."+key(entry.name);
            List<String> history=new ArrayList<String>(y.getStringList(historyPath));
            String stamp=new SimpleDateFormat("dd.MM HH:mm").format(new Date());
            history.add(0,"&8["+stamp+"] "+(entry.ipBan()?"&#FF1744[БАН-IP]":"&#FF1744[БАН]")
                    +" &7от &e"+entry.staff+" &8("+ModerationManager.formatDurationRu(entry.duration)+"&8): &f"+entry.reason);
            while(history.size()>15)history.remove(history.size()-1);
            y.set(historyPath,history);
            if(entry.countStaff)incrementStaff(y,entry.staff);
            y.set(ROOT+"."+key(entry.name),null);
            return true;
        });
    }
    void cancelName(String name) throws Exception {
        if (!validName(name)) throw new IOException("invalid player name");
        store.transact(y -> {
            read(root(y),key(name)); // fail closed on a malformed record
            y.set(ROOT+"."+key(name),null); y.set("bans."+key(name),null);
            return null;
        });
    }
    Cancellation cancelIp(String target) throws Exception {
        if (target==null || target.isEmpty()) throw new IOException("empty target");
        final boolean literal=DataStore.looksLikeIp(target);
        if (!literal && !validName(target)) throw new IOException("invalid player name");
        String resolved="";
        if (literal) resolved=target;
        else {
            // A failed/unfinished ban may refer to an earlier IP even if the
            // player subsequently tried to connect from a different address.
            for (Entry entry : all()) if (entry.name.equalsIgnoreCase(target) && !entry.ip.isEmpty()) {
                resolved=entry.ip; break;
            }
            if (resolved.isEmpty()) resolved=store.findPunishedIp("ipbans",target);
            if (resolved.isEmpty()) resolved=store.resolveIpForTarget(target);
        }
        final String ip=resolved;
        final String path=ip.isEmpty()?null:ipPath(ip);
        return store.transact(y -> {
            List<Entry> pending=entries(y); Set<String> names=new LinkedHashSet<String>();
            for(Entry entry:pending) {
                boolean matches=literal ? (!entry.ip.isEmpty() && entry.ip.equalsIgnoreCase(target)) : entry.name.equalsIgnoreCase(target);
                if(matches) { y.set(ROOT+"."+key(entry.name),null); names.add(entry.name); }
            }
            boolean hadIp=path!=null && y.contains(path);
            if(path!=null)y.set(path,null);
            if(!literal)y.set("bans."+key(target),null);
            return new Cancellation(hadIp,names);
        });
    }
}
