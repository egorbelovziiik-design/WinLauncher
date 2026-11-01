package net.kinggrief.probe;

import net.kinggrief.core.ItemFactory;
import net.kinggrief.core.KingGriefCore;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.function.BooleanSupplier;

/** Tests late protection cancellation and accepted PvP side effects on disposable Paper. */
public final class CombatProtectionProbe extends JavaPlugin implements Listener {
    private KingGriefCore core;
    private Player a, b;
    private World world;
    private boolean started, finished, cancelHit, observedCancel, triggerNested, processingNested;
    private boolean nestedCancelled;
    private double observedFinal, nestedFinal;
    private int checks;

    @Override public void onEnable() {
        core = (KingGriefCore) Bukkit.getPluginManager().getPlugin("KingGriefCore");
        Bukkit.getPluginManager().registerEvents(this, this);
    }

    @EventHandler public void onJoin(PlayerJoinEvent e) {
        if ("FightTester".equals(e.getPlayer().getName())) a = e.getPlayer();
        if ("FightTarget".equals(e.getPlayer().getName())) b = e.getPlayer();
        if (a != null && b != null && !started) {
            started = true;
            later(30, this::begin);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void thirdPartyProtection(EntityDamageByEntityEvent e) {
        if (!e.getEntity().equals(b)) return;
        if (triggerNested && !processingNested) {
            triggerNested = false;
            processingNested = true;
            b.setNoDamageTicks(0); b.setLastDamage(0); b.damage(4.0, a);
            processingNested = false;
            e.setCancelled(true);
        } else if (cancelHit && !processingNested) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void observe(EntityDamageByEntityEvent e) {
        if (e.getEntity().equals(b)) {
            if (processingNested) { nestedCancelled = e.isCancelled(); nestedFinal = e.getFinalDamage(); }
            else { observedCancel = e.isCancelled(); observedFinal = e.getFinalDamage(); }
        }
    }

    private void later(long ticks, Runnable work) {
        Bukkit.getScheduler().runTaskLater(this, () -> {
            try { work.run(); } catch (Throwable error) { fail(error); }
        }, ticks);
    }

    private void ok(boolean condition, String description) {
        if (!condition) throw new AssertionError(description);
        checks++;
        getLogger().info("KG-COMBAT PASS " + description);
    }

    private void fail(Throwable error) {
        if (finished) return;
        finished = true;
        getLogger().severe("KG-COMBAT RESULT FAIL " + error);
        error.printStackTrace();
    }

    private Object field(Object obj, String name) {
        try { Field f = obj.getClass().getDeclaredField(name); f.setAccessible(true); return f.get(obj); }
        catch (ReflectiveOperationException error) { throw new RuntimeException(error); }
    }

    @SuppressWarnings("unchecked")
    private void clearCooldowns() {
        ((Map<String, Long>) field(field(core, "gameplayListener"), "itemCooldowns")).clear();
    }

    private void reset() {
        clearCooldowns();
        for (Player player : new Player[]{a, b}) {
            for (PotionEffect effect : player.getActivePotionEffects()) player.removePotionEffect(effect.getType());
            player.getInventory().clear();
            player.setGameMode(GameMode.SURVIVAL);
            player.setFireTicks(0);
            player.setFreezeTicks(0);
            player.setVelocity(new Vector());
            player.setFallDistance(0);
            player.setNoDamageTicks(0);
            player.setLastDamage(0);
            player.setCooldown(Material.SHIELD, 0);
            player.setHealth(20);
        }
        a.teleport(new Location(world, 75.5, 94, 10.5, 0, 0));
        b.teleport(new Location(world, 77.5, 94, 10.5, 0, 0));
        a.setHealth(10);
    }

    private void hit(boolean cancel) {
        b.setNoDamageTicks(0);
        b.setLastDamage(0);
        cancelHit = cancel;
        observedCancel = false;
        observedFinal = -1;
        b.damage(4.0, a);
        cancelHit = false;
    }

    private void check(String label, Runnable equip, BooleanSupplier effect) { check(label, equip, effect, effect); }

    private void check(String label, Runnable equip, BooleanSupplier anyEffect, BooleanSupplier allEffects) {
        reset(); equip.run();
        hit(true);
        ok(observedCancel && b.getHealth() == 20 && !anyEffect.getAsBoolean(), label + ": late cancellation produces no effect");
        hit(false);
        ok(!observedCancel && b.getHealth() < 20 && allEffects.getAsBoolean(), label + ": accepted hit applies its effect");
    }

    private int armorDamage() {
        ItemStack chest = b.getInventory().getChestplate();
        return chest != null && chest.getItemMeta() instanceof Damageable
                ? ((Damageable) chest.getItemMeta()).getDamage() : -1;
    }

    private void setArmor(Player player, String set) {
        player.getInventory().setHelmet(ItemFactory.customArmorPiece(set, "helmet"));
        player.getInventory().setChestplate(ItemFactory.customArmorPiece(set, "chestplate"));
        player.getInventory().setLeggings(ItemFactory.customArmorPiece(set, "leggings"));
        player.getInventory().setBoots(ItemFactory.customArmorPiece(set, "boots"));
    }

    private void begin() {
        world = core.getSpawnEngine().getSpawnArrival().getWorld();
        ok(core.getSpawnEngine().isSpawnActive(), "prepared spawn is active");
        world.setPVP(true);
        a.teleport(new Location(world, 75.5, 94, 10.5, 0, 0));
        b.teleport(new Location(world, 77.5, 94, 10.5, 0, 0));
        later(50, this::runCases);
    }

    private void runCases() {
        check("frost blade", () -> a.getInventory().setItemInMainHand(ItemFactory.swordFrost()),
                () -> b.getFreezeTicks() > 0 || b.hasPotionEffect(PotionEffectType.SLOWNESS),
                () -> b.getFreezeTicks() > 0 && b.hasPotionEffect(PotionEffectType.SLOWNESS));
        check("northern blade", () -> a.getInventory().setItemInMainHand(ItemFactory.swordSever()),
                () -> b.getFreezeTicks() > 0 || b.hasPotionEffect(PotionEffectType.SLOWNESS),
                () -> b.getFreezeTicks() > 0 && b.hasPotionEffect(PotionEffectType.SLOWNESS));
        check("wither blade", () -> a.getInventory().setItemInMainHand(ItemFactory.swordWither()),
                () -> b.hasPotionEffect(PotionEffectType.WITHER));
        check("venom blade", () -> a.getInventory().setItemInMainHand(ItemFactory.swordVenom()),
                () -> b.hasPotionEffect(PotionEffectType.POISON) || b.hasPotionEffect(PotionEffectType.WEAKNESS),
                () -> b.hasPotionEffect(PotionEffectType.POISON) && b.hasPotionEffect(PotionEffectType.WEAKNESS));
        check("phoenix blade", () -> a.getInventory().setItemInMainHand(ItemFactory.swordPhoenix()),
                () -> b.getFireTicks() > 0 || a.hasPotionEffect(PotionEffectType.REGENERATION),
                () -> b.getFireTicks() > 0 && a.hasPotionEffect(PotionEffectType.REGENERATION));
        check("katana", () -> a.getInventory().setItemInMainHand(ItemFactory.swordKatana()),
                () -> a.hasPotionEffect(PotionEffectType.SPEED));
        check("void blade", () -> a.getInventory().setItemInMainHand(ItemFactory.swordVoid()),
                () -> b.getVelocity().lengthSquared() > 0.01);
        check("Thor axe", () -> a.getInventory().setItemInMainHand(ItemFactory.axeThor()),
                () -> b.getCooldown(Material.SHIELD) > 0 || b.hasPotionEffect(PotionEffectType.SLOWNESS),
                () -> b.getCooldown(Material.SHIELD) > 0 && b.hasPotionEffect(PotionEffectType.SLOWNESS));
        check("Zeus blade", () -> a.getInventory().setItemInMainHand(ItemFactory.swordZeus()),
                () -> b.getCooldown(Material.SHIELD) > 0,
                () -> b.getCooldown(Material.SHIELD) > 0 && observedFinal >= 8.0);
        check("shadow rapier", () -> a.getInventory().setItemInMainHand(ItemFactory.swordPhantom()),
                () -> b.hasPotionEffect(PotionEffectType.BLINDNESS));
        check("winter hammer", () -> a.getInventory().setItemInMainHand(ItemFactory.swordZima()),
                () -> b.hasPotionEffect(PotionEffectType.SLOWNESS) || b.getVelocity().lengthSquared() > 0.01,
                () -> b.hasPotionEffect(PotionEffectType.SLOWNESS) && b.getVelocity().lengthSquared() > 0.01);
        check("Dracula sword", () -> a.getInventory().setItemInMainHand(ItemFactory.swordDracula()),
                () -> a.getHealth() > 10);
        check("Dracula talisman", () -> {a.getInventory().setItemInMainHand(new ItemStack(Material.DIAMOND_SWORD));a.getInventory().setItemInOffHand(ItemFactory.talDraculy());},
                () -> a.getHealth() > 10);
        check("vampire elytra", () -> {a.getInventory().setItemInMainHand(new ItemStack(Material.DIAMOND_SWORD));a.getInventory().setChestplate(ItemFactory.elytraVampire());},
                () -> a.getHealth() > 10);
        check("Dracula armor set", () -> {a.getInventory().setItemInMainHand(new ItemStack(Material.DIAMOND_SWORD));setArmor(a,"dracula");},
                () -> a.getHealth() > 10);
        check("reaper scythe", () -> a.getInventory().setItemInMainHand(ItemFactory.scytheReaper()),
                () -> a.getHealth() > 10 || b.hasPotionEffect(PotionEffectType.WITHER),
                () -> a.getHealth() > 10 && b.hasPotionEffect(PotionEffectType.WITHER));
        check("Midas sword", () -> a.getInventory().setItemInMainHand(ItemFactory.swordMidas()),
                () -> a.hasPotionEffect(PotionEffectType.ABSORPTION));
        check("frost retaliation", () -> {a.getInventory().setItemInMainHand(new ItemStack(Material.DIAMOND_SWORD));b.getInventory().setItemInOffHand(ItemFactory.talMoroza());},
                () -> a.getFreezeTicks() > 0 || a.hasPotionEffect(PotionEffectType.SLOWNESS),
                () -> a.getFreezeTicks() > 0 && a.hasPotionEffect(PotionEffectType.SLOWNESS));
        check("phoenix elytra retaliation", () -> {a.getInventory().setItemInMainHand(new ItemStack(Material.DIAMOND_SWORD));b.getInventory().setChestplate(ItemFactory.elytraPhoenix());},
                () -> a.getFireTicks() > 0);
        check("frost armor retaliation", () -> {a.getInventory().setItemInMainHand(new ItemStack(Material.DIAMOND_SWORD));setArmor(b,"frost");},
                () -> a.getFreezeTicks() > 0);
        check("armor oxidation", () -> {a.getInventory().setItemInMainHand(ItemFactory.swordKrush());b.getInventory().setChestplate(new ItemStack(Material.DIAMOND_CHESTPLATE));},
                () -> armorDamage() > 0);
        check("owner blade", () -> {a.getInventory().setItemInMainHand(ItemFactory.swordOwner());b.getInventory().setChestplate(new ItemStack(Material.DIAMOND_CHESTPLATE));},
                () -> a.getHealth() > 10 || armorDamage() > 0,
                () -> a.getHealth() > 10 && armorDamage() >= 2);
        reset(); a.getInventory().setItemInMainHand(ItemFactory.maceKrush());a.setFallDistance(2.0f);
        hit(true);
        ok(observedCancel && a.getFallDistance() == 2.0f, "mace late cancellation does not consume fall distance");
        hit(false);
        ok(!observedCancel && a.getFallDistance() == 0.0f && observedFinal > 4.0, "accepted mace consumes fall distance and adds damage");

        reset();a.getInventory().setItemInMainHand(ItemFactory.axeThor());hit(false);
        ok(!observedCancel && observedFinal >= 9 && b.hasPotionEffect(PotionEffectType.SLOWNESS), "accepted Thor proc starts cooldown");
        b.removePotionEffect(PotionEffectType.SLOWNESS);b.setCooldown(Material.SHIELD,0);b.setHealth(20);hit(true);
        ok(observedCancel && !b.hasPotionEffect(PotionEffectType.SLOWNESS) && b.getCooldown(Material.SHIELD)==0,
                "blocked hit during cooldown does not cause secondary effects");
        hit(false);
        ok(!observedCancel && Math.abs(observedFinal-4.0)<1e-9 && !b.hasPotionEffect(PotionEffectType.SLOWNESS)
                && b.getCooldown(Material.SHIELD)>0, "existing Thor cooldown suppresses bonus but keeps normal shield break");

        reset();a.getInventory().setItemInMainHand(ItemFactory.axeThor());
        nestedFinal=-1;nestedCancelled=true;triggerNested=true;hit(false);
        ok(observedCancel && !nestedCancelled && Math.abs(nestedFinal-4.0)<1e-9
                && !b.hasPotionEffect(PotionEffectType.SLOWNESS), "nested accepted hit cannot steal a pending proc");
        b.setHealth(20);hit(false);
        ok(!observedCancel && observedFinal>=9 && b.hasPotionEffect(PotionEffectType.SLOWNESS),
                "cancelling the outer hit releases its reserved proc");
        ok(((Map<?,?>)field(field(core,"gameplayListener"),"pendingPvpHits")).isEmpty()
                && ((Map<?,?>)field(field(core,"gameplayListener"),"pendingPvpCooldowns")).isEmpty(),
                "accepted, cancelled and nested events leave no pending PvP state");
        finished = true;
        core.getHud().clearCombat(a.getUniqueId());core.getHud().clearCombat(b.getUniqueId());
        getLogger().info("KG-COMBAT RESULT PASS " + checks + " checks");
    }
}
