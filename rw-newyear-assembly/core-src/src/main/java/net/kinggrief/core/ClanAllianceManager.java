package net.kinggrief.core;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Mutual alliance proposals. Existing alliances are preserved during upgrade. */
final class ClanAllianceManager {
    private static final long INVITE_MS = 10 * 60 * 1000L;
    private static final class Invite {
        final DataStore.Clan from, to;
        final long expires;
        Invite(DataStore.Clan from, DataStore.Clan to, long expires) {
            this.from=from; this.to=to; this.expires=expires;
        }
    }
    private final KingGriefCore plugin;
    private final Map<String, Invite> invites = new HashMap<String, Invite>();
    ClanAllianceManager(KingGriefCore plugin) { this.plugin=plugin; }
    private static String key(DataStore.Clan from, DataStore.Clan to) { return from.id + "->" + to.id; }
    private void clean() {
        long now=System.currentTimeMillis();
        Iterator<Invite> it=invites.values().iterator();
        while (it.hasNext()) {
            Invite inv=it.next();
            if (inv.expires<=now || plugin.getStore().getClanById(inv.from.id)!=inv.from
                    || plugin.getStore().getClanById(inv.to.id)!=inv.to) it.remove();
        }
    }
    boolean handles(String sub) {
        return "ally".equals(sub) || "allyaccept".equals(sub) || "allydeny".equals(sub)
                || "allypvp".equals(sub) || "neutral".equals(sub) || "unally".equals(sub);
    }
    void handle(Player p, DataStore.Clan my, int role, String sub, String[] args) {
        if (role<3 || args.length<2) {
            p.sendMessage(ColorUtil.c("&eЛидер/зам: /clan ally <тег>, allyaccept <тег>, allydeny <тег>, allypvp <тег>, neutral <тег>")); return;
        }
        DataStore.Clan other=plugin.getStore().getClanById(args[1].toLowerCase(Locale.ROOT));
        if (other==null) {
            // Renaming the visible tag does not change the clan's stable ID.
            for (DataStore.Clan candidate:plugin.getStore().getAllClans()) if (candidate.tag.equalsIgnoreCase(args[1])) {
                if (other!=null) { p.sendMessage(ColorUtil.c("&cНесколько кланов с таким тегом; укажите исходный ID.")); return; }
                other=candidate;
            }
        }
        if (other==null || other==my) { p.sendMessage(ColorUtil.c("&cДругой клан не найден.")); return; }
        clean();
        if ("neutral".equals(sub) || "unally".equals(sub)) {
            invites.remove(key(my,other)); invites.remove(key(other,my));
            if (!changeAlliance(my,other,false)) { p.sendMessage(ColorUtil.c("&cНе удалось сохранить расторжение союза.")); return; }
            notifyClan(my,"&eСоюз с ["+other.tag+"] расторгнут; настройки огня сброшены.");
            notifyClan(other,"&eКлан ["+my.tag+"] расторг союз с вами."); return;
        }
        boolean allied=my.allies.contains(other.id) || other.allies.contains(my.id);
        if ("allypvp".equals(sub)) {
            if (!allied) { p.sendMessage(ColorUtil.c("&cСначала заключите союз с этим кланом.")); return; }
            boolean old=my.allyPvp.contains(other.id), next=!old;
            if (next) my.allyPvp.add(other.id); else my.allyPvp.remove(other.id);
            try { plugin.getStore().transact(data -> null); }
            catch (Exception error) {
                if (old) my.allyPvp.add(other.id); else my.allyPvp.remove(other.id);
                p.sendMessage(ColorUtil.c("&cНе удалось сохранить настройку союзного PvP.")); return;
            }
            boolean enabled=next && other.allyPvp.contains(my.id);
            notifyClan(my,enabled?"&cPvP с союзом ["+other.tag+"] ВКЛЮЧЕНО по согласию обоих кланов."
                    : next?"&eВаш клан согласен на PvP с ["+other.tag+"]; ждём согласия второго клана."
                    :"&aPvP с союзом ["+other.tag+"] выключено.");
            notifyClan(other,enabled?"&cPvP с союзом ["+my.tag+"] ВКЛЮЧЕНО по согласию обоих кланов."
                    : next?"&eКлан ["+my.tag+"] предлагает PvP союзников: /clan allypvp "+my.id
                    :"&aКлан ["+my.tag+"] отключил PvP союзников.");
            return;
        }
        if ("allydeny".equals(sub)) {
            Invite removed=invites.remove(key(other,my));
            p.sendMessage(ColorUtil.c(removed==null?"&7Активного предложения союза нет.":"&eПредложение союза отклонено."));
            if (removed!=null) notifyClan(other,"&eКлан ["+my.tag+"] отклонил предложение союза.");
            return;
        }
        if (allied) { p.sendMessage(ColorUtil.c("&aВаши кланы уже в союзе.")); return; }
        Invite incoming=invites.get(key(other,my));
        if ("allyaccept".equals(sub) || incoming!=null) {
            if (incoming==null) { p.sendMessage(ColorUtil.c("&cАктивного предложения от этого клана нет.")); return; }
            if (!changeAlliance(my,other,true)) { p.sendMessage(ColorUtil.c("&cНе удалось сохранить союз. Предложение остаётся активным.")); return; }
            invites.remove(key(other,my)); invites.remove(key(my,other));
            notifyClan(my,"&#00E676🤝 Союз с ["+other.tag+"] подтверждён. Союзное PvP по умолчанию выключено.");
            notifyClan(other,"&#00E676🤝 Союз с ["+my.tag+"] подтверждён. Союзное PvP по умолчанию выключено.");
            return;
        }
        if (invites.size()>=128 && !invites.containsKey(key(my,other))) { p.sendMessage(ColorUtil.c("&cСлишком много активных предложений. Попробуйте позже.")); return; }
        invites.put(key(my,other),new Invite(my,other,System.currentTimeMillis()+INVITE_MS));
        notifyClan(my,"&eПредложение союза отправлено ["+other.tag+"] на 10 минут.");
        notifyClan(other,"&#FFD740Клан ["+my.tag+"] предлагает союз: /clan allyaccept "+my.id+" или /clan allydeny "+my.id);
    }
    private boolean changeAlliance(DataStore.Clan a, DataStore.Clan b, boolean allied) {
        boolean aa=a.allies.contains(b.id), bb=b.allies.contains(a.id), ap=a.allyPvp.contains(b.id), bp=b.allyPvp.contains(a.id);
        if (allied) { a.allies.add(b.id); b.allies.add(a.id); }
        else { a.allies.remove(b.id); b.allies.remove(a.id); }
        a.allyPvp.remove(b.id); b.allyPvp.remove(a.id);
        try { plugin.getStore().transact(data -> null); return true; }
        catch (Exception error) {
            restore(a.allies,b.id,aa); restore(b.allies,a.id,bb); restore(a.allyPvp,b.id,ap); restore(b.allyPvp,a.id,bp);
            plugin.getLogger().warning("[Clans] Alliance update was not committed: "+error.getMessage()); return false;
        }
    }
    private static void restore(Set<String> set, String value, boolean present) { if (present) set.add(value); else set.remove(value); }
    private void notifyClan(DataStore.Clan clan, String message) {
        for (Player player:Bukkit.getOnlinePlayers()) if (clan.members.contains(player.getName().toLowerCase(Locale.ROOT)))
            player.sendMessage(ColorUtil.c("&#B388FF[КЛАН "+clan.tag+"] "+message));
    }
}
