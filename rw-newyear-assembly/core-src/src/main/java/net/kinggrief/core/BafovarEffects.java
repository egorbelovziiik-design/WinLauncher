package net.kinggrief.core;

import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;
import java.util.List;

/** Instant-effect purchases are not a transaction with the player's entity data.
 * Verify the actual effects (Paper can report success after a cancelled event),
 * and charge only after all changes succeeded. Rollback is best effort because
 * another plugin may also cancel the restoration event. All calls are on the
 * primary server thread; this class never edits effects asynchronously. */
final class BafovarEffects {
    enum Result { SUCCESS, INSUFFICIENT, NOTHING, CONFLICT, BLOCKED, SAVE_FAILED, INVALID }
    private static final class Change {
        final PotionEffectType type; final PotionEffect before;
        Change(PotionEffectType type,PotionEffect before){this.type=type;this.before=before;}
    }
    private final KingGriefCore plugin;
    BafovarEffects(KingGriefCore plugin){this.plugin=plugin;}

    private boolean affordable(Player player,int cost){
        double coins=plugin.getStore().getCoins(player.getName());
        return Double.isFinite(coins)&&coins>=cost;
    }
    private static boolean durationAtLeast(PotionEffect effect,int ticks){
        return effect.getDuration()<0||effect.getDuration()>=ticks;
    }
    private static boolean covered(PotionEffect current,PotionEffect wanted){
        // A near-immediate second click should not cost the full package price.
        int tolerance=Math.min(200,Math.max(1,wanted.getDuration()/20));
        return current!=null&&current.getAmplifier()==wanted.getAmplifier()
                &&durationAtLeast(current,Math.max(1,wanted.getDuration()-tolerance));
    }
    private static boolean equivalent(PotionEffect a,PotionEffect b){
        if(a==null||b==null)return a==b;
        return a.getType().equals(b.getType())&&a.getDuration()==b.getDuration()
                &&a.getAmplifier()==b.getAmplifier()&&a.isAmbient()==b.isAmbient()
                &&a.hasParticles()==b.hasParticles()&&a.hasIcon()==b.hasIcon();
    }
    private void rollback(Player player,List<Change> changed){
        for(int i=changed.size()-1;i>=0;i--){
            Change c=changed.get(i);
            PotionEffect current=player.getPotionEffect(c.type);
            if(equivalent(current,c.before))continue;
            if(current!=null)player.removePotionEffect(c.type);
            if(player.hasPotionEffect(c.type)){
                plugin.getLogger().warning("[Bafovar] Another plugin prevented rollback of "+c.type.getKey()+" for "+player.getName()+"; no fee was committed.");
                continue;
            }
            if(c.before!=null){
                player.addPotionEffect(c.before);
                if(!equivalent(player.getPotionEffect(c.type),c.before))
                    plugin.getLogger().warning("[Bafovar] Another plugin prevented restoration of "+c.type.getKey()+" for "+player.getName()+"; no fee was committed.");
            }
        }
    }
    Result apply(Player player,int cost,PotionEffect[] requested){
        if(player==null||cost<=0||requested==null||requested.length==0)return Result.INVALID;
        if(!affordable(player,cost))return Result.INSUFFICIENT;
        boolean needsChange=false;
        for(PotionEffect wanted:requested){
            if(wanted==null||wanted.getType()==null||wanted.getDuration()<=0)return Result.INVALID;
            PotionEffect before=player.getPotionEffect(wanted.getType());
            if(before!=null&&before.getAmplifier()>wanted.getAmplifier()){
                if(!durationAtLeast(before,wanted.getDuration()))return Result.CONFLICT;
                continue; // never replace a stronger external effect
            }
            if(before==null||before.getAmplifier()<wanted.getAmplifier()||!covered(before,wanted))needsChange=true;
        }
        if(!needsChange)return Result.NOTHING;
        List<Change> changed=new ArrayList<Change>();
        for(PotionEffect wanted:requested){
            PotionEffect before=player.getPotionEffect(wanted.getType());
            if(before!=null&&(before.getAmplifier()>wanted.getAmplifier()||covered(before,wanted)))continue;
            player.addPotionEffect(wanted);
            PotionEffect actual=player.getPotionEffect(wanted.getType());
            if(!equivalent(before,actual))changed.add(new Change(wanted.getType(),before));
            if(actual==null||actual.getAmplifier()<wanted.getAmplifier()||!durationAtLeast(actual,wanted.getDuration())){
                rollback(player,changed);return Result.BLOCKED;
            }
        }
        if(!plugin.getStore().takeCoinsDurably(player.getName(),cost)){
            rollback(player,changed);return Result.SAVE_FAILED;
        }
        return Result.SUCCESS;
    }
    Result cleanse(Player player,int cost,PotionEffectType[] types){
        if(player==null||cost<=0||types==null)return Result.INVALID;
        if(!affordable(player,cost))return Result.INSUFFICIENT;
        List<Change> changed=new ArrayList<Change>();
        for(PotionEffectType type:types){
            if(type==null){rollback(player,changed);return Result.INVALID;}
            PotionEffect before=player.getPotionEffect(type);
            if(before==null)continue;
            player.removePotionEffect(type);
            PotionEffect actual=player.getPotionEffect(type);
            if(!equivalent(before,actual))changed.add(new Change(type,before));
            if(actual!=null){rollback(player,changed);return Result.BLOCKED;}
        }
        if(changed.isEmpty())return Result.NOTHING;
        if(!plugin.getStore().takeCoinsDurably(player.getName(),cost)){
            rollback(player,changed);return Result.SAVE_FAILED;
        }
        return Result.SUCCESS;
    }
    static PotionEffect effect(String modern,String legacy,int ticks,int amplifier){
        PotionEffectType type=ColorUtil.potType(modern,legacy);
        return type==null?null:new PotionEffect(type,ticks,amplifier,true,false,true);
    }
}
