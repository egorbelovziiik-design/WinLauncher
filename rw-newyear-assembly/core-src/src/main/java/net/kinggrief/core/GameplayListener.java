package net.kinggrief.core;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.server.ServerCommandEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class GameplayListener implements Listener {

    private final KingGriefCore plugin;
    private final Map<UUID, Long> pvpUntil = new HashMap<UUID, Long>();
    private final Map<UUID, BossBar> pvpBars = new HashMap<UUID, BossBar>();
    private final Map<String, Long> itemCooldowns = new HashMap<String, Long>();
    private final Map<UUID, Long> parkourCooldown = new HashMap<UUID, Long>();
    private final Set<UUID> drilling = new HashSet<UUID>();

    public GameplayListener(KingGriefCore plugin) {
        this.plugin = plugin;
    }

    public boolean isAuthed(Player p) {
        return !plugin.getConfig().getBoolean("enable-builtin-auth", true) || plugin.getStore().isAuthenticated(p);
    }

    public void setAuthed(Player p, boolean state) {
        plugin.getStore().setAuthenticated(p, state);
    }

    public boolean isInPvp(Player p) {
        Long until = pvpUntil.get(p.getUniqueId());
        return until != null && until.longValue() > System.currentTimeMillis();
    }

    public void startLoops() {
        new BukkitRunnable() {
            int tickSec = 0;

            @Override
            public void run() {
                tickSec++;
                long now = System.currentTimeMillis();
                for (Player p : Bukkit.getOnlinePlayers()) {
                    if (!isAuthed(p)) {
                        if (tickSec % 3 == 0) sendAuthPrompt(p);
                        continue;
                    }
                    Long until = pvpUntil.get(p.getUniqueId());
                    if (until != null) {
                        long remMs = until.longValue() - now;
                        BossBar bar = pvpBars.get(p.getUniqueId());
                        if (remMs <= 0) {
                            pvpUntil.remove(p.getUniqueId());
                            if (bar != null) {
                                bar.removeAll();
                                pvpBars.remove(p.getUniqueId());
                            }
                            ColorUtil.actionBar(p, "&#00E676✔ Вы вышли из режима боя! Теперь можно выходить.");
                        } else if (bar != null) {
                            int sec = (int) Math.ceil(remMs / 1000.0);
                            bar.setTitle(ColorUtil.c("&#FF1744&l⚔ РЕЖИМ ПВП: &f" + sec + " сек. &7— Не выходите с сервера!"));
                            bar.setProgress(Math.max(0.0, Math.min(1.0, remMs / 30000.0)));
                        }
                    }

                    // Offhand Spheres & Talismans passive loop (every 2s)
                    if (tickSec % 2 == 0) {
                        ItemStack off = p.getInventory().getItemInOffHand();
                        String id = ItemFactory.getCustomId(off);
                        if ("sphere_andromeda".equals(id)) {
                            ColorUtil.addPot(p, "strength", "INCREASE_DAMAGE", 80, 1);
                            ColorUtil.addPot(p, "speed", "SPEED", 80, 1);
                            ColorUtil.addPot(p, "fire_resistance", "FIRE_RESISTANCE", 80, 0);
                        } else if ("sphere_chimera".equals(id)) {
                            ColorUtil.addPot(p, "speed", "SPEED", 80, 2);
                            ColorUtil.addPot(p, "haste", "FAST_DIGGING", 80, 1);
                            ColorUtil.addPot(p, "jump_boost", "JUMP", 80, 1);
                        } else if ("talisman_krush".equals(id)) {
                            ColorUtil.addPot(p, "strength", "INCREASE_DAMAGE", 80, 2);
                            ColorUtil.addPot(p, "resistance", "DAMAGE_RESISTANCE", 80, 0);
                            ColorUtil.addPot(p, "absorption", "ABSORPTION", 80, 0);
                        } else if ("talisman_frost".equals(id)) {
                            ColorUtil.addPot(p, "regeneration", "REGENERATION", 80, 1);
                            ColorUtil.addPot(p, "resistance", "DAMAGE_RESISTANCE", 80, 1);
                            ColorUtil.addPot(p, "fire_resistance", "FIRE_RESISTANCE", 80, 0);
                        }
                    }
                }
            }
        }.runTaskTimer(plugin, 20L, 20L);
    }

    private void sendAuthPrompt(Player p) {
        if (plugin.getStore().isRegistered(p.getName())) {
            p.sendTitle(ColorUtil.c("&#00E5FF&l🔐 АВТОРИЗАЦИЯ"),
                    ColorUtil.c("&fВведите в чат: &#00E676/login <пароль>"), 0, 50, 10);
        } else {
            p.sendTitle(ColorUtil.c("&#FFD740&l🎄 РЕГИСТРАЦИЯ"),
                    ColorUtil.c("&fВведите: &#00E676/reg <пароль> <повтор>"), 0, 50, 10);
        }
    }

    // ================= 1. LOGIN / JOIN / QUIT / RESOURCEPACK =================
    @EventHandler
    public void onPreLogin(AsyncPlayerPreLoginEvent e) {
        String reason = plugin.getStore().getActiveBanReason(e.getName());
        if (reason != null) {
            e.disallow(AsyncPlayerPreLoginEvent.Result.KICK_BANNED, ColorUtil.c(
                    "&#FF1744&l⛔ ВЫ ЗАБАНЕНЫ НА KINGGRIEF\n\n&7Причина: &f" + reason
                            + "\n\n&#00E676Разбан за 99₽: &fhttps://kinggrief.vercel.app"));
        }
    }

    @EventHandler
    public void onJoin(final PlayerJoinEvent e) {
        final Player p = e.getPlayer();
        plugin.getStore().ensureProfileInitialized(p);
        RankManager.RankDef rk = plugin.getRanks().getRank(p);
        e.setJoinMessage(ColorUtil.c("&8[&#00E676+&8] " + rk.chatPrefix + "&f" + p.getName()));

        Location sp = plugin.getSpawn().getSpawnLocation();
        if (!p.hasPlayedBefore() || p.getLocation().getWorld() == null) {
            p.teleport(sp);
        }

        if (!p.hasPlayedBefore()) {
            ItemFactory.giveKitItems(p, "start");
            ItemFactory.giveKitItems(p, "newyear");
            p.getInventory().setItem(8, ItemFactory.menuStar());
            plugin.getStore().setCoins(p.getName(), 2500.0);
        } else if (p.getInventory().getItem(8) == null) {
            p.getInventory().setItem(8, ItemFactory.menuStar());
        }

        if (!plugin.getConfig().getBoolean("enable-builtin-auth", true) || plugin.getStore().trySessionAutoLogin(p)) {
            setAuthed(p, true);
            p.sendTitle(ColorUtil.c("&#FF1744&l🎄 KING&#FFD740&lGRIEF &#80D8FF&lЗИМА"),
                    ColorUtil.c("&fМеню: &#00E676/menu &8| &fКейсы: &#FFD740/cases &8| &fРТП: &#00E5FF/rtp"), 10, 65, 15);
            ColorUtil.sound(p, "entity.player.levelup", 0.8f, 1.2f);
        } else {
            setAuthed(p, false);
            p.teleport(sp);
            sendAuthPrompt(p);
        }

        p.sendMessage(ColorUtil.c("&8&m                                                    "));
        p.sendMessage(ColorUtil.c("  &#80D8FF❄ &#FF1744&lKING&#FFD740&lGRIEF &8— &#00E5FFНовогодний ReallyWorld v3.0 &#80D8FF❄"));
        p.sendMessage(ColorUtil.c("  &7• Главное меню: &#00E676/menu &8| &7Привилегии: &#FFD740/donate &8| &7РТП: &#00E5FF/rtp"));
        p.sendMessage(ColorUtil.c("  &7• Халява зимы: &#FF4081/promo NEWYEAR &8и &#76FF03/gifts"));
        p.sendMessage(ColorUtil.c("  &7• Сайт автодоната: &#FFD740https://kinggrief.vercel.app"));
        p.sendMessage(ColorUtil.c("&8&m                                                    "));

        if (plugin.getConfig().getBoolean("auto-send-resource-pack", true)) {
            new BukkitRunnable() {
                @Override
                public void run() {
                    if (!p.isOnline()) return;
                    try {
                        String url = plugin.getConfig().getString("resource-pack-url", "https://kinggrief.vercel.app/kinggrief-mega-rp.zip");
                        String sha1 = plugin.getConfig().getString("resource-pack-sha1", "67958285eae977aa1ce6afcbf8a94c57f3c691ce");
                        byte[] hash = ColorUtil.hexToBytes(sha1);
                        String prompt = ColorUtil.c(plugin.getConfig().getString("resource-pack-prompt",
                                "&#80D8FF❄ Установите Новогодний Ресурспак KINGGRIEF!"));
                        if (hash != null) p.setResourcePack(url, hash, prompt, false);
                        else p.setResourcePack(url);
                    } catch (Throwable ignored) {}
                }
            }.runTaskLater(plugin, 25L);
        }

        plugin.getHud().updatePlayerHud(p);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        Player p = e.getPlayer();
        e.setQuitMessage(ColorUtil.c("&8[&#FF5252-&8] &7" + p.getName()));
        setAuthed(p, false);
        plugin.getHud().removePlayer(p);

        if (isInPvp(p)) {
            pvpUntil.remove(p.getUniqueId());
            p.setHealth(0.0);
            Bukkit.broadcastMessage(ColorUtil.c("&#FF1744☠ &fИгрок &c" + p.getName()
                    + " &fвышел во время &#FF1744PvP &fи потерял все ресурсы!"));
        }
        BossBar bar = pvpBars.remove(p.getUniqueId());
        if (bar != null) bar.removeAll();
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent e) {
        e.setRespawnLocation(plugin.getSpawn().getSpawnLocation());
    }

    // ================= 2. AUTH LOCKS & ICE PARKOUR =================
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onMove(PlayerMoveEvent e) {
        if (e.getTo() == null) return;
        Player p = e.getPlayer();
        if (!isAuthed(p)) {
            if (e.getFrom().getBlockX() != e.getTo().getBlockX() || e.getFrom().getBlockZ() != e.getTo().getBlockZ()) {
                Location back = e.getFrom().clone();
                back.setYaw(e.getTo().getYaw());
                back.setPitch(e.getTo().getPitch());
                e.setTo(back);
            }
            return;
        }
        if (plugin.getSpawn().getSpawnWorld().equals(e.getTo().getWorld())) {
            int bx = e.getTo().getBlockX(), by = e.getTo().getBlockY(), bz = e.getTo().getBlockZ();
            if (Math.abs(bx - 30) <= 1 && Math.abs(by - 111) <= 1 && Math.abs(bz + 28) <= 1) {
                long cd = parkourCooldown.containsKey(p.getUniqueId()) ? parkourCooldown.get(p.getUniqueId()).longValue() : 0L;
                if (System.currentTimeMillis() > cd) {
                    parkourCooldown.put(p.getUniqueId(), System.currentTimeMillis() + 180000L);
                    plugin.getStore().addCoins(p.getName(), 5000.0);
                    plugin.getStore().addRelics(p.getName(), 25);
                    plugin.getStore().save();
                    ColorUtil.spawnFirework(p.getLocation());
                    ColorUtil.sound(p, "ui.toast.challenge_complete", 1f, 1.2f);
                    Bukkit.broadcastMessage(ColorUtil.c("&#80D8FF❄ &fИгрок &#00E676" + p.getName()
                            + " &fпрошёл &bЛедяной Паркур &fи получил &#FFEA00+5 000$ &fи &#00E5FF+25 риликов ❖&f!"));
                    p.teleport(plugin.getSpawn().getSpawnLocation());
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onCmdPreprocess(PlayerCommandPreprocessEvent e) {
        Player p = e.getPlayer();
        String msg = e.getMessage().toLowerCase().trim();
        if (!isAuthed(p)) {
            if (!msg.startsWith("/reg") && !msg.startsWith("/l ") && !msg.equals("/l") && !msg.startsWith("/login")) {
                e.setCancelled(true);
                sendAuthPrompt(p);
                p.sendMessage(ColorUtil.c("&cСначала авторизуйтесь: &e/reg <пароль> <повтор> &cили &e/login <пароль>"));
            }
            return;
        }
        if (isInPvp(p)) {
            if (msg.startsWith("/spawn") || msg.startsWith("/rtp") || msg.startsWith("/home")
                    || msg.startsWith("/tpa") || msg.startsWith("/fly") || msg.startsWith("/back")) {
                e.setCancelled(true);
                p.sendMessage(ColorUtil.c("&#FF1744⚔ Нельзя телепортироваться во время PvP-боя!"));
            }
        }
    }

    @EventHandler
    public void onDrop(PlayerDropItemEvent e) {
        if (!isAuthed(e.getPlayer())) e.setCancelled(true);
    }

    // ================= 3. NPC CLICKS, CASES, AIRDROP & CUSTOM ARTIFACTS =================
    @EventHandler
    public void onEntityInteract(PlayerInteractAtEntityEvent e) {
        Player p = e.getPlayer();
        if (!isAuthed(p)) {
            e.setCancelled(true);
            return;
        }
        String cmd = plugin.getSpawn().getNpcCommand(e.getRightClicked());
        if (cmd != null) {
            e.setCancelled(true);
            handleNpcCommand(p, cmd);
        }
    }

    private void handleNpcCommand(Player p, String cmd) {
        ColorUtil.sound(p, "entity.villager.yes", 0.9f, 1.1f);
        if ("pvp_pit".equals(cmd) || "pvp".equals(cmd)) {
            p.teleport(new Location(plugin.getSpawn().getSpawnWorld(), 0.5, 100.0, 26.5, 0f, 15f));
            p.sendMessage(ColorUtil.c("&#FF1744⚔ &fПрыгай вниз в &cPvP-Яму &fили заходи в &eКабинки 1x1 &fсправа!"));
        } else if ("djamshut_tip".equals(cmd) || "djamshut".equals(cmd)) {
            p.sendMessage(ColorUtil.c("&#FACC15👳 Джамшут: &fЭй, брат, прыгай в яму — там замес, а за руду иди ко мне в &e/buyer&f!"));
            plugin.getGuis().openBuyerMenu(p);
        } else {
            p.performCommand(cmd);
        }
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent e) {
        Player p = e.getPlayer();
        if (!isAuthed(p)) {
            e.setCancelled(true);
            return;
        }
        if (e.getClickedBlock() != null) {
            Block b = e.getClickedBlock();
            SpawnEngine.CasePedestal cp = plugin.getSpawn().getCaseAtBlock(b);
            if (cp != null) {
                e.setCancelled(true);
                if (e.getAction() == Action.LEFT_CLICK_BLOCK) {
                    plugin.getGuis().openCasePreview(p, cp.id);
                } else if (e.getHand() == EquipmentSlot.HAND) {
                    plugin.getGuis().trySpinCase(p, cp.id);
                }
                return;
            }
            if (b.getType() == Material.ENDER_CHEST && plugin.getSpawn().isAirdropBlock(b.getLocation())) {
                e.setCancelled(true);
                plugin.getSpawn().claimAirdrop(p);
                return;
            }
            if (b.getType().name().contains("CHEST") || b.getType().name().contains("SHULKER")
                    || b.getType().name().contains("DOOR") || b.getType() == Material.BARREL || b.getType() == Material.FURNACE) {
                DataStore.Claim cl = plugin.getStore().getClaimAt(b.getLocation());
                if (cl != null && !cl.canAccess(p.getName()) && !p.hasPermission("kinggrief.admin")) {
                    e.setCancelled(true);
                    p.sendMessage(ColorUtil.c("&cЭтот сундук защищён приватом игрока &e" + cl.owner + "&c!"));
                    return;
                }
            }
        }

        if (e.getHand() != EquipmentSlot.HAND) return;
        if (e.getAction() != Action.RIGHT_CLICK_AIR && e.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        ItemStack hand = p.getInventory().getItemInMainHand();
        String cid = ItemFactory.getCustomId(hand);
        if (cid == null) return;

        if ("menu_star".equals(cid) || "menu_compass".equals(cid)) {
            e.setCancelled(true);
            plugin.getGuis().openMainMenu(p);
            return;
        }
        if (cid.startsWith("claim_") || cid.startsWith("sphere_") || cid.startsWith("talisman_") || "drill_3x3".equals(cid)) {
            return;
        }

        e.setCancelled(true);
        String cdKey = p.getUniqueId() + ":" + cid;
        long ready = itemCooldowns.containsKey(cdKey) ? itemCooldowns.get(cdKey).longValue() : 0L;
        if (System.currentTimeMillis() < ready) {
            long sec = (ready - System.currentTimeMillis()) / 1000L + 1L;
            ColorUtil.actionBar(p, "&cПерезарядка предмета: &e" + sec + " сек.");
            return;
        }

        if ("trapka".equals(cid)) {
            if (plugin.getSpawn().isInSpawnRegion(p.getLocation()) && !plugin.getSpawn().isInPvpZone(p.getLocation())) {
                p.sendMessage(ColorUtil.c("&cТрапку нельзя ставить в мирной зоне спавна!"));
                return;
            }
            consumeOne(p, hand);
            itemCooldowns.put(cdKey, System.currentTimeMillis() + 25000L);
            deployTrapka(p.getLocation());
            ColorUtil.sound(p, "block.anvil.place", 1f, 0.7f);
            p.sendMessage(ColorUtil.c("&#A855F7⛓ &fВы активировали &dТрапку 5×5&f! Исчезнет через 15 секунд."));
        } else if ("plast".equals(cid)) {
            if (plugin.getSpawn().isInSpawnRegion(p.getLocation()) && !plugin.getSpawn().isInPvpZone(p.getLocation())) {
                p.sendMessage(ColorUtil.c("&cПласт нельзя ставить в мирной зоне спавна!"));
                return;
            }
            consumeOne(p, hand);
            itemCooldowns.put(cdKey, System.currentTimeMillis() + 18000L);
            deployPlast(p.getLocation());
            ColorUtil.sound(p, "block.stone.place", 1f, 0.8f);
            p.sendMessage(ColorUtil.c("&#38BDF8🧱 &fВы установили &bПласт 5×5&f! Исчезнет через 15 секунд."));
        } else if ("god_aura".equals(cid)) {
            consumeOne(p, hand);
            itemCooldowns.put(cdKey, System.currentTimeMillis() + 30000L);
            for (PotionEffect pe : p.getActivePotionEffects()) {
                String n = pe.getType().getName().toUpperCase();
                if (n.contains("POISON") || n.contains("WITHER") || n.contains("SLOW") || n.contains("BLIND") || n.contains("WEAK")) {
                    p.removePotionEffect(pe.getType());
                }
            }
            ColorUtil.addPot(p, "regeneration", "REGENERATION", 160, 2);
            ColorUtil.addPot(p, "absorption", "ABSORPTION", 600, 1);
            ColorUtil.addPot(p, "fire_resistance", "FIRE_RESISTANCE", 600, 0);
            p.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, p.getLocation().add(0, 1, 0), 35, 0.5, 0.6, 0.5, 0.15);
            ColorUtil.sound(p, "item.totem.use", 0.9f, 1.2f);
            p.sendMessage(ColorUtil.c("&#FFEE58✨ &fАктивирована &eБожья Аура&f! Негативные эффекты сняты."));
        } else if ("dezor".equals(cid)) {
            consumeOne(p, hand);
            itemCooldowns.put(cdKey, System.currentTimeMillis() + 30000L);
            int hit = 0;
            for (Player other : p.getWorld().getPlayers()) {
                if (!other.equals(p) && other.getLocation().distanceSquared(p.getLocation()) <= 81.0) {
                    ColorUtil.addPot(other, "blindness", "BLINDNESS", 100, 0);
                    ColorUtil.addPot(other, "slowness", "SLOW", 120, 1);
                    ColorUtil.addPot(other, "nausea", "CONFUSION", 120, 0);
                    other.sendMessage(ColorUtil.c("&#FF4081👁 &fИгрок &e" + p.getName() + " &fприменил против вас &dДезориентацию&f!"));
                    hit++;
                }
            }
            ColorUtil.sound(p, "entity.elder_guardian.curse", 0.9f, 1.2f);
            p.sendMessage(ColorUtil.c("&#FF4081👁 &fДезориентация поразила врагов: &e" + hit));
        } else if ("yavnaya_pyl".equals(cid)) {
            consumeOne(p, hand);
            itemCooldowns.put(cdKey, System.currentTimeMillis() + 20000L);
            int rev = 0;
            for (Player other : p.getWorld().getPlayers()) {
                if (!other.equals(p) && other.getLocation().distanceSquared(p.getLocation()) <= 324.0) {
                    ColorUtil.addPot(other, "glowing", "GLOWING", 300, 0);
                    rev++;
                }
            }
            p.getWorld().spawnParticle(Particle.END_ROD, p.getLocation().add(0, 1, 0), 40, 2.0, 1.0, 2.0, 0.05);
            ColorUtil.sound(p, "block.beacon.activate", 1f, 1.4f);
            p.sendMessage(ColorUtil.c("&#00E5FF💨 &fЯвная пыль подсветила игроков вокруг: &b" + rev));
        } else if ("fire_tornado".equals(cid)) {
            consumeOne(p, hand);
            itemCooldowns.put(cdKey, System.currentTimeMillis() + 25000L);
            for (Player other : p.getWorld().getPlayers()) {
                if (!other.equals(p) && other.getLocation().distanceSquared(p.getLocation()) <= 64.0) {
                    other.setFireTicks(140);
                    other.setVelocity(new Vector(0, 0.95, 0));
                }
            }
            p.getWorld().spawnParticle(Particle.FLAME, p.getLocation().add(0, 1, 0), 60, 1.5, 1.0, 1.5, 0.08);
            ColorUtil.sound(p, "entity.blaze.shoot", 1f, 0.8f);
            p.sendMessage(ColorUtil.c("&#FF6D00🌪 &fВы выпустили &6Огненный Смерч&f!"));
        } else if ("grinch_bomb".equals(cid) || "ice_bomb".equals(cid)) {
            consumeOne(p, hand);
            itemCooldowns.put(cdKey, System.currentTimeMillis() + 20000L);
            for (Player other : p.getWorld().getPlayers()) {
                if (!other.equals(p) && other.getLocation().distanceSquared(p.getLocation()) <= 64.0) {
                    ColorUtil.addPot(other, "slowness", "SLOW", 100, 3);
                    other.setFreezeTicks(220);
                }
            }
            p.getWorld().spawnParticle(Particle.SNOWFLAKE, p.getLocation().add(0, 1, 0), 75, 2.0, 1.2, 2.0, 0.05);
            ColorUtil.sound(p, "block.glass.break", 1f, 0.8f);
            p.sendMessage(ColorUtil.c("&#69F0AE❄ &fЛедяная Бомба Гринча заморозила врагов вокруг!"));
        } else if ("ny_candy".equals(cid)) {
            consumeOne(p, hand);
            itemCooldowns.put(cdKey, System.currentTimeMillis() + 15000L);
            ColorUtil.addPot(p, "speed", "SPEED", 300, 2);
            ColorUtil.addPot(p, "regeneration", "REGENERATION", 100, 2);
            p.setFoodLevel(20);
            ColorUtil.sound(p, "entity.generic.eat", 1f, 1.2f);
            p.sendMessage(ColorUtil.c("&#FF4081🍭 &fНовогодний леденец дал Скорость III и Исцеление!"));
        }
    }

    private void consumeOne(Player p, ItemStack stack) {
        if (p.getGameMode() == GameMode.CREATIVE) return;
        if (stack.getAmount() <= 1) p.getInventory().setItemInMainHand(null);
        else stack.setAmount(stack.getAmount() - 1);
    }

    private void deployTrapka(Location center) {
        final List<Block> placed = new ArrayList<Block>();
        World w = center.getWorld();
        if (w == null) return;
        int cx = center.getBlockX(), cy = center.getBlockY(), cz = center.getBlockZ();
        for (int dx = -2; dx <= 2; dx++) {
            for (int dy = -1; dy <= 3; dy++) {
                for (int dz = -2; dz <= 2; dz++) {
                    boolean shell = Math.abs(dx) == 2 || Math.abs(dz) == 2 || dy == -1 || dy == 3;
                    if (!shell) continue;
                    Block b = w.getBlockAt(cx + dx, cy + dy, cz + dz);
                    if (b.getType() == Material.AIR || b.getType() == Material.SNOW || b.getType() == Material.SHORT_GRASS) {
                        b.setType((dx + dy + dz) % 2 == 0 ? Material.OBSIDIAN : Material.CRYING_OBSIDIAN, false);
                        placed.add(b);
                    }
                }
            }
        }
        new BukkitRunnable() {
            @Override
            public void run() {
                for (Block b : placed) {
                    if (b.getType() == Material.OBSIDIAN || b.getType() == Material.CRYING_OBSIDIAN) {
                        b.setType(Material.AIR, false);
                    }
                }
            }
        }.runTaskLater(plugin, 300L);
    }

    private void deployPlast(Location loc) {
        final List<Block> placed = new ArrayList<Block>();
        World w = loc.getWorld();
        if (w == null) return;
        int cx = loc.getBlockX(), cy = loc.getBlockY() - 1, cz = loc.getBlockZ();
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                Block b = w.getBlockAt(cx + dx, cy, cz + dz);
                if (b.getType() == Material.AIR || b.getType() == Material.WATER) {
                    b.setType(Math.abs(dx) == 2 || Math.abs(dz) == 2 ? Material.OBSIDIAN : Material.DRIED_KELP_BLOCK, false);
                    placed.add(b);
                }
            }
        }
        new BukkitRunnable() {
            @Override
            public void run() {
                for (Block b : placed) {
                    if (b.getType() == Material.OBSIDIAN || b.getType() == Material.DRIED_KELP_BLOCK) {
                        b.setType(Material.AIR, false);
                    }
                }
            }
        }.runTaskLater(plugin, 300L);
    }

    // ================= 4. BLOCK PROTECTION, AUTO-MINE, CLAIMS & 3x3 DRILL =================
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        Player p = e.getPlayer();
        if (!isAuthed(p)) {
            e.setCancelled(true);
            return;
        }
        Block b = e.getBlock();
        Location loc = b.getLocation();

        if (plugin.getSpawn().isInSpawnRegion(loc)) {
            if (!plugin.getSpawn().isInAutoMineOres(loc) && !(p.getGameMode() == GameMode.CREATIVE && p.hasPermission("kinggrief.admin"))) {
                e.setCancelled(true);
                ColorUtil.actionBar(p, "&cСпавн защищён! Копайте руду на &e/mine &cили выйдите через &a/rtp");
                return;
            }
        } else {
            DataStore.Claim cl = plugin.getStore().getClaimAt(loc);
            if (cl != null) {
                if (!cl.canAccess(p.getName()) && !p.hasPermission("kinggrief.admin")) {
                    e.setCancelled(true);
                    p.sendMessage(ColorUtil.c("&cЭта территория запривачена игроком &e" + cl.owner + "&c!"));
                    return;
                }
                if (cl.isCenter(loc)) {
                    if (!cl.owner.equalsIgnoreCase(p.getName()) && !p.hasPermission("kinggrief.admin")) {
                        e.setCancelled(true);
                        p.sendMessage(ColorUtil.c("&cТолько владелец &e" + cl.owner + " &cможет сломать центральный блок привата!"));
                        return;
                    }
                    plugin.getStore().removeClaim(cl);
                    plugin.getStore().save();
                    p.sendMessage(ColorUtil.c("&#FF5252🛡 Приват #" + cl.id + " удалён!"));
                }
            }
        }

        String matName = b.getType().name();
        if (matName.endsWith("_ORE") || matName.equals("ANCIENT_DEBRIS")) {
            plugin.getStore().addMined(p.getName());
            if ("miner".equals(plugin.getStore().getJob(p.getName()))) {
                double pay = matName.contains("DIAMOND") || matName.contains("EMERALD") ? 180.0 : 55.0;
                plugin.getStore().addCoins(p.getName(), pay);
                ColorUtil.actionBar(p, "&#FFEE58⛏ Работа Шахтёра: &#00E676+" + (int) pay + "$");
            }
        } else if (matName.endsWith("_LOG") && "woodcutter".equals(plugin.getStore().getJob(p.getName()))) {
            plugin.getStore().addCoins(p.getName(), 30.0);
            ColorUtil.actionBar(p, "&#84CC16🪓 Работа Лесоруба: &#00E676+30$");
        } else if ((matName.equals("WHEAT") || matName.equals("CARROTS") || matName.equals("POTATOES"))
                && "farmer".equals(plugin.getStore().getJob(p.getName()))) {
            plugin.getStore().addCoins(p.getName(), 35.0);
            ColorUtil.actionBar(p, "&#FACC15🌾 Работа Фермера: &#00E676+35$");
        }

        ItemStack tool = p.getInventory().getItemInMainHand();
        if ("drill_3x3".equals(ItemFactory.getCustomId(tool)) && !drilling.contains(p.getUniqueId())
                && !plugin.getSpawn().isInSpawnRegion(loc)) {
            drilling.add(p.getUniqueId());
            try {
                for (int dx = -1; dx <= 1; dx++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        if (dx == 0 && dz == 0) continue;
                        Block nb = b.getRelative(dx, 0, dz);
                        String nm = nb.getType().name();
                        if (nm.contains("STONE") || nm.contains("DEEPSLATE") || nm.endsWith("_ORE") || nm.equals("NETHERRACK") || nm.equals("DIRT")) {
                            DataStore.Claim nc = plugin.getStore().getClaimAt(nb.getLocation());
                            if (nc == null || nc.canAccess(p.getName())) {
                                nb.breakNaturally(tool);
                            }
                        }
                    }
                }
            } finally {
                drilling.remove(p.getUniqueId());
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent e) {
        Player p = e.getPlayer();
        if (!isAuthed(p)) {
            e.setCancelled(true);
            return;
        }
        Location loc = e.getBlockPlaced().getLocation();
        if (plugin.getSpawn().isInSpawnRegion(loc)
                && !(p.getGameMode() == GameMode.CREATIVE && p.hasPermission("kinggrief.admin"))) {
            e.setCancelled(true);
            ColorUtil.actionBar(p, "&cНа спавне нельзя строить! Используйте &a/rtp");
            return;
        }
        DataStore.Claim existing = plugin.getStore().getClaimAt(loc);
        if (existing != null && !existing.canAccess(p.getName()) && !p.hasPermission("kinggrief.admin")) {
            e.setCancelled(true);
            p.sendMessage(ColorUtil.c("&cЭта территория запривачена игроком &e" + existing.owner + "&c!"));
            return;
        }

        String cid = ItemFactory.getCustomId(e.getItemInHand());
        if (cid != null && cid.startsWith("claim_")) {
            int radius = Integer.parseInt(cid.substring("claim_".length()));
            RankManager.RankDef rk = plugin.getRanks().getRank(p);
            List<DataStore.Claim> myClaims = plugin.getStore().getClaimsOf(p.getName());
            if (myClaims.size() >= rk.maxClaims) {
                e.setCancelled(true);
                p.sendMessage(ColorUtil.c("&cВы достигли лимита приватов вашего ранга (&e" + rk.maxClaims + "&c)! Повысьте ранг: &6/donate"));
                return;
            }
            if (plugin.getStore().overlapsAnyClaim(loc.getWorld().getName(), loc.getBlockX(), loc.getBlockZ(), radius)) {
                e.setCancelled(true);
                p.sendMessage(ColorUtil.c("&cЗдесь слишком близко чужой приват!"));
                return;
            }
            DataStore.Claim created = plugin.getStore().createClaim(p.getName(), loc, radius);
            plugin.getStore().save();
            ColorUtil.sound(p, "block.beacon.activate", 1f, 1.2f);
            p.sendMessage(ColorUtil.c("&#00E676🛡 Приват #" + created.id + " (" + (radius * 2) + "×" + (radius * 2)
                    + ") успешно создан! &7Добавить друга: &f/rg add <ник>"));
        }
    }

    // ================= 5. PVP PROTECTION, ANTI-RELOG & KILLS =================
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent e) {
        if (plugin.getSpawn().getNpcCommand(e.getEntity()) != null) {
            e.setCancelled(true);
            return;
        }
        if (e.getEntity() instanceof Player) {
            Player victim = (Player) e.getEntity();
            if (!isAuthed(victim)) {
                e.setCancelled(true);
                return;
            }
            if (plugin.getSpawn().isInSpawnRegion(victim.getLocation())
                    && !plugin.getSpawn().isInPvpZone(victim.getLocation())) {
                e.setCancelled(true);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPvp(EntityDamageByEntityEvent e) {
        Entity target = e.getEntity();
        String npcCmd = plugin.getSpawn().getNpcCommand(target);
        if (npcCmd != null) {
            e.setCancelled(true);
            if (e.getDamager() instanceof Player) {
                handleNpcCommand((Player) e.getDamager(), npcCmd);
            }
            return;
        }
        if (!(target instanceof Player)) return;
        Player attacker = null;
        if (e.getDamager() instanceof Player) attacker = (Player) e.getDamager();
        else if (e.getDamager() instanceof org.bukkit.entity.Projectile
                && ((org.bukkit.entity.Projectile) e.getDamager()).getShooter() instanceof Player) {
            attacker = (Player) ((org.bukkit.entity.Projectile) e.getDamager()).getShooter();
        }
        if (attacker == null || attacker.equals(target)) return;
        Player victim = (Player) target;

        if (!isAuthed(attacker) || !isAuthed(victim)) {
            e.setCancelled(true);
            return;
        }
        if ((plugin.getSpawn().isInSpawnRegion(attacker.getLocation()) && !plugin.getSpawn().isInPvpZone(attacker.getLocation()))
                || (plugin.getSpawn().isInSpawnRegion(victim.getLocation()) && !plugin.getSpawn().isInPvpZone(victim.getLocation()))) {
            e.setCancelled(true);
            ColorUtil.actionBar(attacker, "&cPvP разрешено в &4PvP-Яме&c, &eКабинках 1x1 &cили после &a/rtp&c!");
            return;
        }
        DataStore.Clan c1 = plugin.getStore().getClanOf(attacker.getName());
        DataStore.Clan c2 = plugin.getStore().getClanOf(victim.getName());
        if (c1 != null && c2 != null && c1.id.equalsIgnoreCase(c2.id) && !c1.friendlyFire) {
            e.setCancelled(true);
            ColorUtil.actionBar(attacker, "&eСоклановец! Включить урон в клане: /clan pvp");
            return;
        }

        tagPvp(attacker);
        tagPvp(victim);
    }

    private void tagPvp(Player p) {
        pvpUntil.put(p.getUniqueId(), System.currentTimeMillis() + 30000L);
        if (p.isFlying() && p.getGameMode() == GameMode.SURVIVAL) {
            p.setFlying(false);
            p.setAllowFlight(false);
        }
        BossBar bar = pvpBars.get(p.getUniqueId());
        if (bar == null) {
            bar = Bukkit.createBossBar(ColorUtil.c("&#FF1744&l⚔ РЕЖИМ ПВП: &f30 сек."), BarColor.RED, BarStyle.SOLID);
            bar.addPlayer(p);
            pvpBars.put(p.getUniqueId(), bar);
        }
        bar.setProgress(1.0);
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent e) {
        Player dead = e.getEntity();
        plugin.getStore().setBack(dead, dead.getLocation());
        plugin.getStore().addDeath(dead.getName());
        pvpUntil.remove(dead.getUniqueId());
        BossBar b = pvpBars.remove(dead.getUniqueId());
        if (b != null) b.removeAll();

        Player killer = dead.getKiller();
        if (killer != null && !killer.equals(dead)) {
            plugin.getStore().addKill(killer.getName());
            plugin.getStore().addCoins(killer.getName(), 500.0);
            ColorUtil.actionBar(killer, "&#FF1744⚔ Убийство " + dead.getName() + "! &#00E676+500$");
        }
        plugin.getStore().save();
    }

    @EventHandler
    public void onMobDeath(EntityDeathEvent e) {
        if (e.getEntity() instanceof Player) return;
        Player killer = e.getEntity().getKiller();
        if (killer != null && "hunter".equals(plugin.getStore().getJob(killer.getName()))) {
            plugin.getStore().addCoins(killer.getName(), 80.0);
            ColorUtil.actionBar(killer, "&#FF5252⚔ Работа Охотника: &#00E676+80$");
        }
    }

    // ================= 6. GUI INVENTORY CLICK ROUTER =================
    @EventHandler
    public void onInventoryClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player)) return;
        Inventory top = e.getView().getTopInventory();
        if (!(top.getHolder() instanceof GuiManager.KgMenuHolder)) return;

        e.setCancelled(true);
        Player p = (Player) e.getWhoClicked();
        if (e.getClickedInventory() == null || !e.getClickedInventory().equals(top)) return;

        GuiManager.KgMenuHolder h = (GuiManager.KgMenuHolder) top.getHolder();
        int slot = e.getSlot();
        String type = h.menuType;

        if ("main".equals(type)) {
            if (slot == 19) plugin.getGuis().openRtpMenu(p);
            else if (slot == 20) { p.closeInventory(); p.performCommand("spawn"); }
            else if (slot == 21) plugin.getGuis().openShopMenu(p);
            else if (slot == 22) plugin.getGuis().openDonateMenu(p);
            else if (slot == 23) plugin.getGuis().openAllCasesMenu(p);
            else if (slot == 24) plugin.getGuis().openKitsMenu(p);
            else if (slot == 25) plugin.getGuis().openNyShop(p);
            else if (slot == 28) plugin.getGuis().openAuctionMenu(p, 0);
            else if (slot == 29) plugin.getGuis().openBuyerMenu(p);
            else if (slot == 30) plugin.getGuis().openJobsMenu(p);
            else if (slot == 31) plugin.getGuis().openClanTopMenu(p);
            else if (slot == 32) plugin.getGuis().openBattlePassMenu(p);
            else if (slot == 33) plugin.getGuis().openGiftsMenu(p);
            else if (slot == 34) { p.closeInventory(); p.performCommand("mine"); }
            else if (slot == 48) plugin.getGuis().openKpMenu(p);
            else if (slot == 49) {
                p.closeInventory();
                p.sendMessage(ColorUtil.c("&#00E676🌐 Официальный сайт: &#FFD740https://kinggrief.vercel.app"));
            }
            else if (slot == 50) plugin.getGuis().openMediaMenu(p);
        } else if ("donate".equals(type)) {
            if (slot == 29) plugin.getGuis().openAllCasesMenu(p);
            else if (slot == 31) plugin.getGuis().openShopMenu(p);
            else if (slot == 39) plugin.getGuis().openKpMenu(p);
            else if (slot == 41) plugin.getGuis().openMediaMenu(p);
            else if (e.getCurrentItem() != null && e.getCurrentItem().getType() != Material.BLACK_STAINED_GLASS_PANE) {
                p.closeInventory();
                p.sendMessage(ColorUtil.c("&#FFD740👑 Покупка привилегий с авто-выдачей за 5 сек: &#00E676https://kinggrief.vercel.app"));
            }
        } else if ("kp".equals(type)) {
            if (slot == 15) plugin.getGuis().openDonateMenu(p);
            else if (slot == 22) { p.closeInventory(); p.performCommand("kit kp"); }
        } else if ("media".equals(type)) {
            if (slot == 13) { p.closeInventory(); p.performCommand("kit media"); }
        } else if ("shop".equals(type)) {
            if (slot == 49) { plugin.getGuis().openMainMenu(p); return; }
            handleRelicShopPurchase(p, slot);
        } else if ("nyshop".equals(type)) {
            if (slot == 10) { p.closeInventory(); p.performCommand("kit newyear"); }
            else if (slot == 12) buyWithCoins(p, 5000.0, ItemFactory.nyCandy(4), "Новогодний Леденец ×4");
            else if (slot == 14) buyWithCoins(p, 8000.0, ItemFactory.grinchBomb(4), "Ледяная Бомба Гринча ×4");
            else if (slot == 16) {
                if (plugin.getStore().takeCoins(p.getName(), 250000.0)) {
                    plugin.getStore().addCaseKeys(p.getName(), "donate", 1);
                    plugin.getStore().save();
                    ColorUtil.sound(p, "entity.player.levelup", 1f, 1.2f);
                    p.sendMessage(ColorUtil.c("&#FFD740🎄 &fВы купили &e1 Ключ Донат-Кейса &fза 250 000$! Откройте в &a/cases"));
                } else {
                    p.sendMessage(ColorUtil.c("&cНедостаточно монет! Нужно 250 000$. Заработайте в /buyer и /jobs!"));
                }
            }
        } else if ("gifts".equals(type)) {
            if (slot == 11) {
                long now = System.currentTimeMillis();
                if (now < plugin.getStore().getDailyReadyAt(p.getName())) {
                    long rem = (plugin.getStore().getDailyReadyAt(p.getName()) - now) / 1000L;
                    p.sendMessage(ColorUtil.c("&eПодарок Снегурочки будет доступен через: &f" + ColorUtil.fmtTime(rem)));
                } else {
                    plugin.getStore().setDailyReadyAt(p.getName(), now + 86400000L);
                    plugin.getStore().addCoins(p.getName(), 7500.0);
                    plugin.getStore().addRelics(p.getName(), 25);
                    plugin.getStore().save();
                    ColorUtil.sound(p, "entity.player.levelup", 1f, 1.2f);
                    p.sendMessage(ColorUtil.c("&#80D8FF🤶 Снегурочка подарила вам &#FFEA00+7 500$ &fи &#00E5FF+25 риликов ❖&f!"));
                    plugin.getGuis().openGiftsMenu(p);
                }
            } else if (slot == 13) {
                p.closeInventory();
                p.performCommand("kit start");
                p.performCommand("kit newyear");
            } else if (slot == 15) {
                p.closeInventory();
                p.performCommand("promo NEWYEAR");
            }
        } else if ("rtp".equals(type)) {
            World w = plugin.getSpawn().getSpawnWorld();
            if (slot == 10) {
                p.closeInventory();
                Location dst = plugin.getGuis().findSafeRtpLocation(w, 350, 1800);
                p.teleport(dst);
                ColorUtil.sound(p, "entity.enderman.teleport", 1f, 1f);
                p.sendMessage(ColorUtil.c("&#00E676🧭 Телепортация на координаты: &f" + dst.getBlockX() + ", " + dst.getBlockY() + ", " + dst.getBlockZ()));
            } else if (slot == 12) {
                p.closeInventory();
                Location dst = plugin.getGuis().findSafeRtpLocation(w, 1800, 5500);
                p.teleport(dst);
                ColorUtil.sound(p, "entity.enderman.teleport", 1f, 1f);
                p.sendMessage(ColorUtil.c("&#FFD740🏔 Далёкая телепортация: &f" + dst.getBlockX() + ", " + dst.getBlockY() + ", " + dst.getBlockZ()));
            } else if (slot == 14) {
                if (plugin.getRanks().getRank(p).weight < 110) {
                    p.sendMessage(ColorUtil.c("&cVIP-телепортация доступна от привилегии GOD (149₽)! &e/donate"));
                    return;
                }
                p.closeInventory();
                Location dst = plugin.getGuis().findSafeRtpLocation(w, 5500, 9000);
                p.teleport(dst);
                p.sendMessage(ColorUtil.c("&#FF5252🔥 VIP-телепортация: &f" + dst.getBlockX() + ", " + dst.getBlockY() + ", " + dst.getBlockZ()));
            } else if (slot == 16) {
                Location air = plugin.getSpawn().getActiveAirdropLoc();
                p.closeInventory();
                if (air == null) {
                    p.sendMessage(ColorUtil.c("&eСейчас нет активного Мистического Аирдропа! Следите за чатом."));
                } else {
                    int ax = air.getBlockX() + 55, az = air.getBlockZ() + 55;
                    int ay = air.getWorld().getHighestBlockYAt(ax, az) + 1;
                    p.teleport(new Location(air.getWorld(), ax + 0.5, ay, az + 0.5));
                    p.sendMessage(ColorUtil.c("&#FF4081☄ Вы телепортированы к Мистическому Аирдропу!"));
                }
            }
        } else if ("buyer".equals(type)) {
            handleBuyerClick(p, slot);
        } else if ("jobs".equals(type)) {
            if (slot == 10) setJob(p, "miner", "Шахтёр");
            else if (slot == 12) setJob(p, "woodcutter", "Лесоруб");
            else if (slot == 14) setJob(p, "hunter", "Охотник");
            else if (slot == 16) setJob(p, "farmer", "Фермер");
            else if (slot == 22) {
                plugin.getStore().setJob(p.getName(), "none");
                plugin.getStore().save();
                p.sendMessage(ColorUtil.c("&eВы уволились с работы."));
                plugin.getGuis().openJobsMenu(p);
            }
        } else if ("bp".equals(type)) {
            if (slot >= 10 && slot <= 16) {
                int lvl = slot - 9;
                int[] reqs = new int[]{25, 75, 150, 300, 500, 800, 1200};
                int score = plugin.getStore().getMinedBlocks(p.getName()) + plugin.getStore().getKills(p.getName()) * 20;
                if (plugin.getStore().hasClaimedBp(p.getName(), lvl)) {
                    p.sendMessage(ColorUtil.c("&eЭта награда уже получена!"));
                    return;
                }
                if (score < reqs[lvl - 1]) {
                    p.sendMessage(ColorUtil.c("&cНедостаточно очков сезона (&e" + score + "/" + reqs[lvl - 1] + "&c)! Копайте руду и сражайтесь."));
                    return;
                }
                plugin.getStore().setClaimedBp(p.getName(), lvl);
                if (lvl == 1) plugin.getStore().addCoins(p.getName(), 5000);
                else if (lvl == 2) plugin.getStore().addRelics(p.getName(), 25);
                else if (lvl == 3) plugin.getStore().addCaseKeys(p.getName(), "money", 1);
                else if (lvl == 4) { plugin.getStore().addRelics(p.getName(), 50); p.getInventory().addItem(ItemFactory.godAura(2)); }
                else if (lvl == 5) plugin.getStore().addCaseKeys(p.getName(), "summer", 1);
                else if (lvl == 6) { plugin.getStore().addRelics(p.getName(), 150); p.getInventory().addItem(ItemFactory.trapka(2)); }
                else if (lvl == 7) { plugin.getStore().addRelics(p.getName(), 300); p.getInventory().addItem(ItemFactory.sphereChimera()); }
                plugin.getStore().save();
                ColorUtil.sound(p, "ui.toast.challenge_complete", 1f, 1.2f);
                p.sendMessage(ColorUtil.c("&#38BDF8⭐ &fВы забрали награду Боевого Пропуска &e#" + lvl + "&f!"));
                plugin.getGuis().openBattlePassMenu(p);
            }
        } else if ("kits".equals(type)) {
            Map<Integer, String> map = new HashMap<Integer, String>();
            map.put(10, "start"); map.put(11, "newyear"); map.put(12, "pegas"); map.put(13, "bunny");
            map.put(14, "vampire"); map.put(15, "god"); map.put(16, "king"); map.put(19, "media");
            map.put(20, "kp"); map.put(21, "dmoder"); map.put(22, "dadmin"); map.put(23, "dkp");
            map.put(24, "opka"); map.put(25, "downer"); map.put(31, "curator");
            if (map.containsKey(slot)) {
                p.closeInventory();
                p.performCommand("kit " + map.get(slot));
            }
        } else if ("ah".equals(type)) {
            if (slot == 45 && h.page > 0) plugin.getGuis().openAuctionMenu(p, h.page - 1);
            else if (slot == 53) plugin.getGuis().openAuctionMenu(p, h.page + 1);
            else if (slot < 36) {
                List<DataStore.AuctionLot> lots = plugin.getStore().getAuctionLots();
                int idx = h.page * 36 + slot;
                if (idx < lots.size()) {
                    DataStore.AuctionLot lot = lots.get(idx);
                    if (lot.seller.equalsIgnoreCase(p.getName())) {
                        DataStore.AuctionLot rem = plugin.getStore().removeAuctionLot(lot.id);
                        if (rem != null) {
                            p.getInventory().addItem(rem.item);
                            plugin.getStore().save();
                            p.sendMessage(ColorUtil.c("&eВы сняли свой лот #" + rem.id + " с аукциона."));
                            plugin.getGuis().openAuctionMenu(p, h.page);
                        }
                    } else {
                        if (!plugin.getStore().takeCoins(p.getName(), lot.price)) {
                            p.sendMessage(ColorUtil.c("&cНедостаточно монет для покупки лота (&e" + ColorUtil.fmtMoney(lot.price) + "$&c)!"));
                            return;
                        }
                        DataStore.AuctionLot bought = plugin.getStore().removeAuctionLot(lot.id);
                        if (bought != null) {
                            plugin.getStore().addCoins(bought.seller, bought.price);
                            p.getInventory().addItem(bought.item);
                            plugin.getStore().save();
                            ColorUtil.sound(p, "entity.experience_orb.pickup", 1f, 1.2f);
                            p.sendMessage(ColorUtil.c("&#00E676⚖ Вы купили лот #" + bought.id + " за " + ColorUtil.fmtMoney(bought.price) + "$!"));
                            Player sellerOnline = Bukkit.getPlayerExact(bought.seller);
                            if (sellerOnline != null) {
                                sellerOnline.sendMessage(ColorUtil.c("&#00E676⚖ Игрок &f" + p.getName()
                                        + " &aкупил ваш лот на аукционе за &#FFEA00+" + ColorUtil.fmtMoney(bought.price) + "$&a!"));
                            }
                            plugin.getGuis().openAuctionMenu(p, h.page);
                        }
                    }
                }
            }
        } else if ("cases".equals(type)) {
            int[] slots = new int[]{10,11,12,13,14,15,16, 20,21,23,24};
            for (int i = 0; i < slots.length && i < SpawnEngine.CASES.size(); i++) {
                if (slots[i] == slot) {
                    String cid = SpawnEngine.CASES.get(i).id;
                    if (e.getClick() == ClickType.RIGHT) plugin.getGuis().openCasePreview(p, cid);
                    else plugin.getGuis().trySpinCase(p, cid);
                    return;
                }
            }
        } else if ("case_preview".equals(type) && slot == 22) {
            plugin.getGuis().openAllCasesMenu(p);
        }
    }

    private void handleRelicShopPurchase(Player p, int slot) {
        int price = 0;
        ItemStack reward = null;
        String caseKey = null;
        if (slot == 10) { price = 499; reward = ItemFactory.sphereAndromeda(); }
        else if (slot == 11) { price = 399; reward = ItemFactory.sphereChimera(); }
        else if (slot == 12) { price = 549; reward = ItemFactory.talismanKrush(); }
        else if (slot == 13) { price = 449; reward = ItemFactory.talismanFrost(); }
        else if (slot == 14) { price = 649; reward = ItemFactory.drill3x3(); }
        else if (slot == 15) { price = 199; reward = ItemFactory.godAura(4); }
        else if (slot == 16) { price = 199; reward = ItemFactory.dezor(4); }
        else if (slot == 19) { price = 149; reward = ItemFactory.yavnayaPyl(6); }
        else if (slot == 20) { price = 179; reward = ItemFactory.fireTornado(4); }
        else if (slot == 21) { price = 229; reward = ItemFactory.trapka(3); }
        else if (slot == 22) { price = 149; reward = ItemFactory.plast(6); }
        else if (slot == 23) { price = 99;  reward = ItemFactory.claimBlock(Material.DIAMOND_ORE, 12, "&#38BDF8💎 Приват 25×25 блоков"); }
        else if (slot == 24) { price = 199; reward = ItemFactory.claimBlock(Material.EMERALD_ORE, 20, "&#22C55E💚 Приват 40×40 блоков"); }
        else if (slot == 25) { price = 249; reward = new ItemStack(Material.ENCHANTED_GOLDEN_APPLE, 8); }
        else if (slot == 31) { price = 299; caseKey = "summer"; }
        if (price <= 0) return;

        if (!plugin.getStore().takeRelics(p, price)) {
            ColorUtil.sound(p, "entity.villager.no", 1f, 1f);
            p.sendMessage(ColorUtil.c("&cНедостаточно риликов (&b" + price + " ❖&c)! Пополните от 19₽: &#FFD740https://kinggrief.vercel.app/relics"));
            return;
        }
        if (caseKey != null) {
            plugin.getStore().addCaseKeys(p.getName(), caseKey, 1);
            p.sendMessage(ColorUtil.c("&#00E676✔ Вы купили Ключ Сезонного Кейса! Откройте его в &e/cases"));
        } else if (reward != null) {
            p.getInventory().addItem(reward);
            p.sendMessage(ColorUtil.c("&#00E676✔ Покупка успешно выдана в ваш инвентарь!"));
        }
        plugin.getStore().save();
        ColorUtil.sound(p, "entity.player.levelup", 1f, 1.3f);
        plugin.getGuis().openShopMenu(p);
    }

    private void buyWithCoins(Player p, double price, ItemStack item, String name) {
        if (!plugin.getStore().takeCoins(p.getName(), price)) {
            ColorUtil.sound(p, "entity.villager.no", 1f, 1f);
            p.sendMessage(ColorUtil.c("&cНедостаточно монет (&e" + ColorUtil.fmtMoney(price) + "$&c)!"));
            return;
        }
        p.getInventory().addItem(item);
        plugin.getStore().save();
        ColorUtil.sound(p, "entity.experience_orb.pickup", 1f, 1.2f);
        p.sendMessage(ColorUtil.c("&#00E676✔ Вы купили &f" + name + " &aза &e" + ColorUtil.fmtMoney(price) + "$&a!"));
    }

    private void handleBuyerClick(Player p, int slot) {
        if (slot == 31) {
            double total = 0.0;
            for (Object[] row : GuiManager.BUYER_ITEMS) {
                Material m = (Material) row[0];
                int pack = ((Integer) row[1]).intValue();
                double pay = ((Double) row[2]).doubleValue();
                double unit = pay / pack;
                int count = countMaterial(p, m);
                if (count > 0) {
                    removeMaterial(p, m, count);
                    total += count * unit;
                }
            }
            if (total <= 0) {
                p.sendMessage(ColorUtil.c("&eВ вашем инвентаре нет ресурсов для продажи Скупщику!"));
            } else {
                plugin.getStore().addCoins(p.getName(), total);
                plugin.getStore().save();
                ColorUtil.sound(p, "entity.player.levelup", 1f, 1.3f);
                p.sendMessage(ColorUtil.c("&#00E676💰 Вы продали все ресурсы Скупщику за &#FFEA00+" + ColorUtil.fmtMoney(total) + "$&a!"));
            }
            return;
        }
        int[] slots = new int[]{10,11,12,13,14,15,16, 19,20,21,22,23,24,25, 29,33};
        for (int i = 0; i < slots.length && i < GuiManager.BUYER_ITEMS.length; i++) {
            if (slots[i] == slot) {
                Material m = (Material) GuiManager.BUYER_ITEMS[i][0];
                int req = ((Integer) GuiManager.BUYER_ITEMS[i][1]).intValue();
                double pay = ((Double) GuiManager.BUYER_ITEMS[i][2]).doubleValue();
                String lbl = (String) GuiManager.BUYER_ITEMS[i][3];
                if (countMaterial(p, m) < req) {
                    ColorUtil.sound(p, "entity.villager.no", 1f, 1f);
                    p.sendMessage(ColorUtil.c("&cНедостаточно ресурса: нужно &e" + lbl));
                    return;
                }
                removeMaterial(p, m, req);
                plugin.getStore().addCoins(p.getName(), pay);
                plugin.getStore().save();
                ColorUtil.sound(p, "entity.experience_orb.pickup", 1f, 1.2f);
                p.sendMessage(ColorUtil.c("&#00E676💰 Продано &f" + lbl + " &aза &#FFEA00+" + ColorUtil.fmtMoney(pay) + "$&a!"));
                return;
            }
        }
    }

    private int countMaterial(Player p, Material m) {
        int c = 0;
        for (ItemStack it : p.getInventory().getStorageContents()) {
            if (it != null && it.getType() == m && ItemFactory.getCustomId(it) == null) {
                c += it.getAmount();
            }
        }
        return c;
    }

    private void removeMaterial(Player p, Material m, int amount) {
        int rem = amount;
        ItemStack[] cont = p.getInventory().getStorageContents();
        for (int i = 0; i < cont.length && rem > 0; i++) {
            ItemStack it = cont[i];
            if (it != null && it.getType() == m && ItemFactory.getCustomId(it) == null) {
                if (it.getAmount() <= rem) {
                    rem -= it.getAmount();
                    cont[i] = null;
                } else {
                    it.setAmount(it.getAmount() - rem);
                    rem = 0;
                }
            }
        }
        p.getInventory().setStorageContents(cont);
    }

    private void setJob(Player p, String job, String title) {
        plugin.getStore().setJob(p.getName(), job);
        plugin.getStore().save();
        ColorUtil.sound(p, "entity.experience_orb.pickup", 1f, 1.2f);
        p.sendMessage(ColorUtil.c("&#00E676⛏ Вы устроились на работу: &e" + title + "&a!"));
        plugin.getGuis().openJobsMenu(p);
    }

    // ================= 7. CHAT FORMATTER & CONSOLE BRIDGE SYNC =================
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onChat(AsyncPlayerChatEvent e) {
        Player p = e.getPlayer();
        if (!isAuthed(p)) {
            e.setCancelled(true);
            p.sendMessage(ColorUtil.c("&cАвторизуйтесь перед отправкой сообщений в чат!"));
            return;
        }
        long muteRem = plugin.getStore().getMuteRemainingSec(p.getName());
        if (muteRem > 0) {
            e.setCancelled(true);
            p.sendMessage(ColorUtil.c("&cУ вас мут чата ещё на &e" + ColorUtil.fmtTime(muteRem)
                    + "&c! Размут за 49₽: &ehttps://kinggrief.vercel.app"));
            return;
        }
        String raw = e.getMessage().trim();
        DataStore.Clan clan = plugin.getStore().getClanOf(p.getName());

        if (raw.startsWith("@") && raw.length() > 1 && clan != null) {
            e.setCancelled(true);
            String body = raw.substring(1).trim();
            String line = ColorUtil.c("&8[&#B388FFКлан " + clan.tag + "&8] &e" + p.getName() + "&7: &f" + body);
            for (Player online : Bukkit.getOnlinePlayers()) {
                if (clan.members.contains(online.getName().toLowerCase())) {
                    online.sendMessage(line);
                }
            }
            return;
        }

        e.setCancelled(true);
        String text = raw.startsWith("!") && raw.length() > 1 ? raw.substring(1).trim() : raw;
        if (plugin.getRanks().getRank(p).weight >= 50) {
            text = ColorUtil.c(text);
        }
        RankManager.RankDef rk = plugin.getRanks().getRank(p);
        String clanTag = clan != null ? "&8[&#B388FF" + clan.tag + "&8] " : "";
        String chan = raw.startsWith("!") ? "&#FFD740[G] " : "&#80D8FF[❄] ";
        String formatted = ColorUtil.c(chan + clanTag + rk.chatPrefix + "&f" + p.getName() + " &8» &f") + text;
        Bukkit.broadcastMessage(formatted);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onConsoleBridgeCommand(ServerCommandEvent e) {
        String cmd = e.getCommand().trim();
        String low = cmd.toLowerCase();
        String[] parts = cmd.split("\\s+");

        if (parts.length >= 5 && low.startsWith("lp user ") && "parent".equalsIgnoreCase(parts[2])
                && ("add".equalsIgnoreCase(parts[3]) || "set".equalsIgnoreCase(parts[3]))) {
            plugin.getRanks().setRank(parts[1], parts[4].toLowerCase(), false);
            return;
        }
        if (parts.length >= 4 && ("p".equalsIgnoreCase(parts[0]) || "points".equalsIgnoreCase(parts[0]))
                && "give".equalsIgnoreCase(parts[1])) {
            try {
                int amt = Integer.parseInt(parts[3]);
                plugin.getStore().addRelics(parts[2], amt);
                plugin.getStore().save();
                if (Bukkit.getPluginManager().getPlugin("PlayerPoints") == null) {
                    e.setCancelled(true);
                }
            } catch (NumberFormatException ignored) {}
            return;
        }
        if (parts.length >= 5 && (("cases".equalsIgnoreCase(parts[0]) && "give".equalsIgnoreCase(parts[1]))
                || ("dc".equalsIgnoreCase(parts[0]) && "givekey".equalsIgnoreCase(parts[1])))) {
            try {
                String nick = parts[2];
                String caseId = parts[3].toLowerCase();
                int amt = Integer.parseInt(parts[4]);
                plugin.getStore().addCaseKeys(nick, caseId, amt);
                plugin.getStore().save();
                Player online = Bukkit.getPlayerExact(nick);
                if (online != null) {
                    online.sendMessage(ColorUtil.c("&#FFD740🎁 Вам начислено &#00E676+" + amt
                            + " ключ(ей) &fот кейса &e" + caseId.toUpperCase() + "&f! Откройте в &a/cases"));
                    ColorUtil.sound(online, "entity.player.levelup", 1f, 1.2f);
                }
                e.setCancelled(true);
            } catch (NumberFormatException ignored) {}
            return;
        }
        if (parts.length >= 2 && "unban".equalsIgnoreCase(parts[0])) {
            plugin.getStore().unban(parts[1]);
            plugin.getStore().save();
        } else if (parts.length >= 2 && "unmute".equalsIgnoreCase(parts[0])) {
            plugin.getStore().unmute(parts[1]);
            plugin.getStore().save();
        }
    }
}
