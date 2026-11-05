package net.kinggrief.captchatest;

import io.papermc.paper.event.player.AsyncChatEvent;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;

/** Disposable test-only plugin. Reflection inspects answers solely to drive real protocol clients. */
public final class CaptchaProbe extends JavaPlugin implements Listener {
    private Plugin hub, captcha;
    private int checks;
    private boolean finished;
    private final AtomicInteger deniedLogins = new AtomicInteger();
    private final AtomicInteger cancelledChats = new AtomicInteger();
    private int cancelledRegistration, closeEvents, npcEvents, menuClicks, moves;

    @Override public void onEnable() {
        hub = Bukkit.getPluginManager().getPlugin("KingHub");
        captcha = Bukkit.getPluginManager().getPlugin("KingCaptcha");
        Bukkit.getPluginManager().registerEvents(this, this);
        if (hub == null || !hub.isEnabled()) { fail("KingHub failed to enable"); return; }
        later(30, () -> {
            if (captcha == null) onlyHub();
            else start();
        });
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onJoin(PlayerJoinEvent event) {
        if (event.getPlayer().getName().equals("CaptchaFree")) {
            event.getPlayer().addAttachment(this, "kingcaptcha.bypass", true);
        }
        if (event.getPlayer().getName().equals("CaptchaOp")) event.getPlayer().setOp(true);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
    public void onPrelogin(AsyncPlayerPreLoginEvent event) {
        if (event.getName().equals("CaptchaBad") && event.getLoginResult() != AsyncPlayerPreLoginEvent.Result.ALLOWED)
            deniedLogins.incrementAndGet();
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        if (event.getPlayer().getName().equals("CaptchaOne") && event.getMessage().startsWith("/register") && event.isCancelled()) cancelledRegistration++;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
    public void onClose(InventoryCloseEvent event) {
        if (event.getPlayer().getName().equals("CaptchaOne")) closeEvents++;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
    public void onNpc(PlayerInteractEntityEvent event) {
        if (event.getPlayer().getName().equals("CaptchaOne") && event.getRightClicked().getScoreboardTags().contains("khub_npc_grief1")) npcEvents++;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
    public void onChat(AsyncChatEvent event) {
        if (event.getPlayer().getName().equals("CaptchaOne") && event.isCancelled()) cancelledChats.incrementAndGet();
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
    public void onMove(PlayerMoveEvent event) {
        if (event.getPlayer().getName().equals("CaptchaOne")) moves++;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
    public void onClick(InventoryClickEvent event) {
        if (event.getWhoClicked().getName().equals("CaptchaOne")) menuClicks++;
    }

    private void later(long ticks, Runnable action) {
        Bukkit.getScheduler().runTaskLater(this, () -> run(action), ticks);
    }

    private void run(Runnable action) {
        if (finished) return;
        try { action.run(); }
        catch (Throwable failure) {
            getLogger().severe("KG-CAPTCHA EXCEPTION " + failure);
            failure.printStackTrace();
            fail("exception: " + failure);
        }
    }

    private void waitFor(String description, BooleanSupplier condition, int maxTicks, Runnable next) {
        new BukkitRunnable() {
            int elapsed;
            @Override public void run() {
                if (finished) { cancel(); return; }
                try {
                    if (condition.getAsBoolean()) { cancel(); next.run(); return; }
                    elapsed += 5;
                    if (elapsed >= maxTicks) { cancel(); fail("timed out: " + description); }
                } catch (Throwable ex) { cancel(); fail("waiting for " + description + ": " + ex); }
            }
        }.runTaskTimer(this, 0L, 5L);
    }

    private void check(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
        checks++;
        getLogger().info("KG-CAPTCHA PASS " + checks + " " + message);
    }

    private void fail(String message) {
        if (finished) return;
        finished = true;
        getLogger().severe("KG-CAPTCHA RESULT FAIL checks=" + checks + " " + message);
    }

    private void pass() {
        if (finished) return;
        finished = true;
        getLogger().info("KG-CAPTCHA RESULT PASS checks=" + checks);
    }

    private void request(String text) { getLogger().info("KG-CAPTCHA REQUEST " + text); }
    private Player player(String name) { return Bukkit.getPlayerExact(name); }

    private static Object get(Object target, String field) {
        try { Field f = target.getClass().getDeclaredField(field); f.setAccessible(true); return f.get(target); }
        catch (ReflectiveOperationException ex) { throw new IllegalStateException(ex); }
    }
    private static void set(Object target, String field, Object value) {
        try { Field f = target.getClass().getDeclaredField(field); f.setAccessible(true); f.set(target, value); }
        catch (ReflectiveOperationException ex) { throw new IllegalStateException(ex); }
    }
    private static void invoke(Object target, String method, Player p) {
        try { Method m=target.getClass().getMethod(method, Player.class); m.invoke(target, p); }
        catch (ReflectiveOperationException ex) { throw new IllegalStateException(ex); }
    }
    @SuppressWarnings("unchecked") private Set<UUID> authed() { return (Set<UUID>) get(hub, "authedPlayers"); }
    @SuppressWarnings("unchecked") private Map<UUID, Object> sessions() { return (Map<UUID, Object>) get(captcha, "pending"); }
    @SuppressWarnings("unchecked") private Map<UUID, Long> transfers() { return (Map<UUID, Long>) get(hub, "transferCooldowns"); }
    @SuppressWarnings("unchecked") private Collection<String> ssoQueue() { return (Collection<String>) get(hub, "pendingSsoJson"); }
    private Object session(Player p) { return sessions().get(p.getUniqueId()); }
    private boolean verified(Player p) {
        try { return (boolean) captcha.getClass().getMethod("isVerified", UUID.class).invoke(captcha, p.getUniqueId()); }
        catch (ReflectiveOperationException ex) { throw new IllegalStateException(ex); }
    }
    private int integer(Object session, String name) { return (int) get(session, name); }
    private boolean menu(Player player) {
        Object s = session(player);
        return s != null && Boolean.TRUE.equals(get(s, "displayed"))
                && player.getOpenInventory().getTopInventory().equals(get(s, "menu"));
    }
    private void click(int client, int slot, int button, int mode) { request("CLICK " + client + " " + slot + " " + button + " " + mode); }
    private int wrong(Object s) { return integer(s, "expectedSlot") == 10 ? 11 : 10; }

    private void start() {
        check(captcha.isEnabled(), "KingCaptcha is enabled");
        check(hub.getDescription().getVersion().equals("1.2.4"), "tested KingHub 1.2.4 is loaded");
        check(captcha.getDescription().getVersion().equals("1.0.0"), "tested KingCaptcha 1.0.0 is loaded");
        waitFor("all players and captcha menus", () -> {
            for (String name : new String[]{"CaptchaOne", "CaptchaBad", "CaptchaLate", "CaptchaOp", "CaptchaSso"}) {
                Player p = player(name); if (p == null || !menu(p)) return false;
            }
            return player("CaptchaFree") != null;
        }, 600, () -> {
            Player one=player("CaptchaOne"), op=player("CaptchaOp"), free=player("CaptchaFree"), sso=player("CaptchaSso");
            check(!verified(one) && session(one) != null, "a fresh session is not verified");
            check(op.isOp() && !verified(op) && session(op) != null, "OP does not bypass captcha by default");
            check(verified(free) && session(free) == null, "explicit bypass permission works");
            Object s = session(one);
            Inventory inventory=one.getOpenInventory().getTopInventory();
            check(inventory.getSize() == 36 && one.getOpenInventory().getTitle().contains((String)get(s,"target")), "styled 36-slot menu names its target");
            int expected=integer(s,"expectedSlot");
            check(expected >= 10 && expected <= 16 && inventory.getItem(expected) != null, "answer is one of seven displayed choices");
            check(one.getInventory().getItem(4) != null && one.getInventory().getItem(4).getType()==Material.COMPASS, "KingHub compass is intact");
            one.getInventory().setItem(5, new ItemStack(Material.DIAMOND, 7));
            // Model an already-authenticated player and call the public transfer
            // boundaries directly, bypassing command and click cancellation.
            authed().add(one.getUniqueId());
            invoke(hub,"openGriefSelectorGui",one);
            check(menu(one), "direct selector cannot replace a pending captcha");
            invoke(hub,"connectToGrief1",one);
            check(!transfers().containsKey(one.getUniqueId()) && ssoQueue().isEmpty(), "direct transfer cannot create an SSO token before captcha");
            Bukkit.dispatchCommand(one,"grief");
            check(!transfers().containsKey(one.getUniqueId()), "direct command dispatch cannot bypass transfer gate");
            ItemStack compass=one.getInventory().getItem(4);
            PlayerInteractEvent compassClick=new PlayerInteractEvent(one,Action.RIGHT_CLICK_AIR,compass,null,null,EquipmentSlot.HAND);
            Bukkit.getPluginManager().callEvent(compassClick);
            check(compassClick.isCancelled() && menu(one), "compass cannot open selector while pending");
            Entity npc=one.getWorld().getEntities().stream().filter(e -> e instanceof Interaction
                    && e.getScoreboardTags().contains("khub_npc_grief1")).findFirst().orElse(null);
            check(npc!=null, "NPC hitbox exists before verification");
            PlayerInteractEntityEvent syntheticNpc = new PlayerInteractEntityEvent(one,npc,EquipmentSlot.HAND);
            Bukkit.getPluginManager().callEvent(syntheticNpc);
            check(syntheticNpc.isCancelled() && menu(one), "NPC event cannot replace the captcha even if an older handler ignores cancellation");
            Location before=one.getLocation().clone();
            // Also validate the handler directly; the protocol client may need
            // to finish its spawn/teleport acknowledgement before moving.
            PlayerMoveEvent movement=new PlayerMoveEvent(one,before,before.clone().add(1,0,0));
            Bukkit.getPluginManager().callEvent(movement);
            check(movement.getTo().distanceSquared(before)<0.0001, "pending movement event is constrained even while authenticated");
            int beforeMoves=moves;
            request("ENTITY 0 " + npc.getEntityId() + " 0");
            request("MOVE 0 " + (before.getX()+1) + " " + before.getY() + " " + before.getZ());
            request("CHAT 0 captcha_blocked_message");
            later(20, () -> {
                getLogger().info("KG-CAPTCHA DIAG movements=" + (moves-beforeMoves) + " displacement=" + one.getLocation().distanceSquared(before)
                        + " chatCancelled=" + cancelledChats.get() + " npcEvents=" + npcEvents);
                check(menu(one), "real NPC packet cannot replace the pending captcha");
                check(one.getLocation().distanceSquared(before)<0.01, "movement packet does not move a pending player");
                check(cancelledChats.get()>0, "real chat packet is cancelled while pending");
                authed().remove(one.getUniqueId());
                request("EXPECTPACK 0 0");
                request("COMMAND 0 register testpass testpass");
                later(15, () -> {
                    check(cancelledRegistration > 0 && !authed().contains(one.getUniqueId()), "registration command is blocked before captcha");
                    request("CLOSE 0");
                    later(15, () -> {
                        check(closeEvents > 0 && menu(one), "closing the window automatically reopens it");
                        request("COMMAND 0 captcha");
                        later(10, () -> invalidClicks(one));
                    });
                });
            });
        });
    }

    private void invalidClicks(Player one) {
        Object s=session(one); int target=integer(s,"expectedSlot");
        click(0,target,0,1); // shift
        later(8, () -> {
            click(0,target,1,2); // number key
            later(8, () -> {
                click(0,target,0,6); // double click
                later(8, () -> {
                    click(0,41,0,0); // own inventory
                    later(12, () -> {
                        check(menuClicks >= 4 && integer(s,"step")==0 && integer(s,"mistakes")==0, "shift, number key, double click and bottom inventory do not answer");
                        check(one.getInventory().getItem(5)!=null && one.getInventory().getItem(5).getAmount()==7, "inventory items were not moved or overwritten");
                        click(0,wrong(s),0,0);
                        waitFor("wrong choice and refreshed menu", () -> integer(s,"mistakes")==1 && menu(one), 120, () -> {
                            check(integer(s,"step")==0, "a wrong choice counts without advancing");
                            solve(one,0,() -> normalSolved(one));
                        });
                    });
                });
            });
        });
    }

    private void solve(Player p, int index, Runnable done) {
        if (verified(p)) { done.run(); return; }
        Object s=session(p);
        if (s==null || !menu(p)) throw new IllegalStateException("missing menu for " + p.getName());
        int before=integer(s,"step");
        click(index,integer(s,"expectedSlot"),0,0);
        waitFor("network answer " + p.getName(), () -> verified(p) || integer(s,"step")>before, 140, () -> {
            later(8, () -> solve(p,index,done));
        });
    }

    private void normalSolved(Player one) {
        check(verified(one) && session(one)==null, "three real menu clicks complete the session");
        check(one.getInventory().getItem(5)!=null && one.getInventory().getItem(5).getAmount()==7, "real player inventory survives verification");
        later(30, () -> {
            request("EXPECTPACK 0 1");
            request("COMMAND 0 register testpass testpass");
            waitFor("registration and selector", () -> authed().contains(one.getUniqueId())
                    && one.getOpenInventory().getTitle().contains("ВЫБОР СЕРВЕРА"), 140, () -> {
                check(true, "registration and server selector work after verification");
                Entity npc = one.getWorld().getEntities().stream().filter(e -> e instanceof Interaction
                        && e.getScoreboardTags().contains("khub_npc_grief1")).findFirst().orElse(null);
                check(npc != null, "KingHub NPC hitbox exists");
                int beforeNpc=npcEvents;
                request("CLOSE 0");
                later(8, () -> {
                    request("ENTITY 0 " + npc.getEntityId() + " 0");
                    waitFor("real NPC click opens selector", () -> npcEvents>beforeNpc && one.getOpenInventory().getTitle().contains("ВЫБОР СЕРВЕРА"), 140, () -> {
                        check(true, "real NPC click opens selector after verification");
                        click(0,13,0,0);
                        waitFor("selector initiates proxy SSO", () -> transfers().containsKey(one.getUniqueId()), 140, () -> {
                            check(!ssoQueue().isEmpty(), "real selector click initiates SSO/transfer after verification");
                            ssoSession();
                        });
                    });
                });
            });
        });
    }

    private void ssoSession() {
        Player sso=player("CaptchaSso");
        waitFor("signed incoming transfer cookie", () -> authed().contains(sso.getUniqueId()), 160, () -> {
            check(sso.isTransferred() && !verified(sso) && menu(sso), "real signed SSO authenticates but does not bypass captcha");
            invoke(hub,"connectToGrief1",sso);
            check(!transfers().containsKey(sso.getUniqueId()), "authenticated SSO player is still blocked at transfer boundary");
            solve(sso,4,() -> {
                check(verified(sso) && authed().contains(sso.getUniqueId()), "SSO authentication survives captcha");
                request("COMMAND 4 servers");
                waitFor("SSO selector", () -> sso.getOpenInventory().getTitle().contains("ВЫБОР СЕРВЕРА"), 120, () -> {
                    check(true,"SSO player can open selector after captcha without a second login");
                    reconnectOne();
                });
            });
        });
    }

    private void reconnectOne() {
        request("DISCONNECT 0");
        waitFor("normal disconnect", () -> player("CaptchaOne")==null, 120, () -> {
            request("RECONNECT 0");
            waitFor("normal reconnect", () -> player("CaptchaOne")!=null && menu(player("CaptchaOne")), 240, () -> {
                Player p=player("CaptchaOne");
                check(!verified(p) && authed().contains(p.getUniqueId()), "reconnect asks captcha again while KingHub auto-login remains valid");
                invoke(hub,"openGriefSelectorGui",p);
                check(menu(p), "auto-login does not bypass selector gate");
                solve(p,0,() -> {
                    check(verified(p) && authed().contains(p.getUniqueId()), "auto-login player can complete the new challenge");
                    badAttempt();
                });
            });
        });
    }

    private void wrongAnswer(Player p, int index, int wantedMistakes, Runnable after) {
        Object s=session(p); click(index,wrong(s),0,0);
        waitFor("wrong answer " + wantedMistakes, () -> integer(s,"mistakes")>=wantedMistakes, 140, () -> later(8, after));
    }

    private void badAttempt() {
        Player p=player("CaptchaBad");
        wrongAnswer(p,1,1,() -> {
            request("DISCONNECT 1");
            waitFor("bad player's disconnect", () -> player("CaptchaBad")==null, 120, () -> {
                request("RECONNECT 1");
                waitFor("bad player returns", () -> player("CaptchaBad")!=null && menu(player("CaptchaBad")), 240, () -> {
                    Player again=player("CaptchaBad");
                    check(integer(session(again),"mistakes")==1, "partial mistakes survive a disconnect");
                    wrongAnswer(again,1,2,() -> wrongAnswer(again,1,3,() -> {
                        waitFor("kick after mistakes", () -> player("CaptchaBad")==null, 120, () -> {
                            check(true,"three wrong answers kick the player");
                            request("RECONNECT 1");
                            waitFor("cooldown rejection", () -> deniedLogins.get()>0, 140, () -> {
                                check(player("CaptchaBad")==null, "reconnect is rejected during cooldown");
                                later(90, () -> {
                                    request("RECONNECT 1");
                                    waitFor("reconnect after cooldown", () -> player("CaptchaBad")!=null && menu(player("CaptchaBad")), 240, () -> {
                                        check(integer(session(player("CaptchaBad")),"mistakes")==0, "after cooldown a new attempt starts cleanly");
                                        timeout();
                                    });
                                });
                            });
                        });
                    }));
                });
            });
        });
    }

    private void timeout() {
        Player late=player("CaptchaLate");
        Object s=session(late);
        set(s,"deadlineNanos",System.nanoTime()-1_000_000_000L);
        waitFor("timeout kick", () -> player("CaptchaLate")==null, 100, () -> {
            check(!sessions().containsKey(late.getUniqueId()), "expired challenge kicks and clears the session");
            request("EXPECTPACK 0 2"); // normal reconnect receives a second pack only after its second verification
            Player sso=player("CaptchaSso");
            int queued=ssoQueue().size();
            Bukkit.getPluginManager().disablePlugin(captcha);
            check(!captcha.isEnabled() && hub.isEnabled(), "disabling captcha does not disable the hub");
            invoke(hub,"connectToGrief1",sso);
            check(!transfers().containsKey(sso.getUniqueId()) && ssoQueue().size()==queued, "installed but disabled captcha fails closed at the transfer boundary");
            later(10, this::pass);
        });
    }

    private void onlyHub() {
        waitFor("HubOnly client", () -> player("HubOnly")!=null, 500, () -> {
            Player p=player("HubOnly");
            check(hub.getDescription().getVersion().equals("1.2.4"), "Hub 1.2.4 enables without KingCaptcha");
            request("COMMAND 0 register testpass testpass");
            waitFor("HubOnly login and selector", () -> authed().contains(p.getUniqueId())
                    && p.getOpenInventory().getTitle().contains("ВЫБОР СЕРВЕРА"), 140, () -> {
                check(true, "without KingCaptcha, registration and selector remain functional");
                check(p.getInventory().getItem(4)!=null && p.getInventory().getItem(4).getType()==Material.COMPASS, "HubOnly keeps compass");
                pass();
            });
        });
    }
}
