package ru.reallyworld.ultimate.core.module;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;
import ru.reallyworld.ultimate.core.api.ReallyModule;
import ru.reallyworld.ultimate.core.config.ConfigManager;
import ru.reallyworld.ultimate.core.storage.StorageModule;
import ru.reallyworld.ultimate.core.storage.model.ClanData;
import ru.reallyworld.ultimate.core.storage.model.ClanData.ClanMember;
import ru.reallyworld.ultimate.core.storage.model.ClanData.ClanRole;
import ru.reallyworld.ultimate.core.util.TextUtil;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * Full ReallyWorld Clan System:
 * - Creation (50,000 coins), Tags, Roles (LEADER, COLEADER, ELDER, MEMBER)
 * - Diplomacy (ALLY, ENEMY), Friendly Fire control, Clan Home, Clan Bank
 * - Clan Chat (@message or /clan chat), PvP & Event Rating, Top Clans
 */
public final class ClanModule implements ReallyModule, Listener {
    private static final Pattern ALPHANUM_CYRILLIC = Pattern.compile("^[A-Za-zА-Яа-яЁё0-9_]+$");
    public record ClanInvite(long clanId, String clanName, UUID inviter, long expiresAt) {}

    private final JavaPlugin plugin;
    private final ConfigManager configManager;
    private final StorageModule storage;
    private final LocalizationModule locale;
    private final PlayerModule playerModule;

    private final Map<Long, ClanData> clansById = new ConcurrentHashMap<>();
    private final Map<UUID, Long> playerToClanId = new ConcurrentHashMap<>();
    private final Map<UUID, ClanInvite> pendingInvites = new ConcurrentHashMap<>();
    private volatile boolean enabled;

    public ClanModule(
            JavaPlugin plugin,
            ConfigManager configManager,
            StorageModule storage,
            LocalizationModule locale,
            PlayerModule playerModule
    ) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.storage = storage;
        this.locale = locale;
        this.playerModule = playerModule;
    }

    @Override
    public String id() { return "clans"; }

    @Override
    public String displayName() { return "ClanModule (/clan)"; }

    @Override
    public boolean isEnabled() { return enabled; }

    @Override
    public void enable() {
        this.enabled = configManager.main().getBoolean("modules.clans", true);
        reloadCacheFromDb();
        if (plugin != null && Bukkit.getServer() != null) {
            Bukkit.getPluginManager().registerEvents(this, plugin);
        }
    }

    @Override
    public void reload() {
        reloadCacheFromDb();
    }

    @Override
    public void disable() {
        this.enabled = false;
        clansById.clear();
        playerToClanId.clear();
        pendingInvites.clear();
    }

    public synchronized void reloadCacheFromDb() {
        clansById.clear();
        playerToClanId.clear();
        for (ClanData clan : storage.clans().loadAllClans()) {
            clansById.put(clan.id(), clan);
            for (ClanMember m : clan.members()) {
                playerToClanId.put(m.playerUuid(), clan.id());
            }
        }
    }

    public Optional<ClanData> getPlayerClan(UUID playerUuid) {
        Long id = playerToClanId.get(playerUuid);
        return id == null ? Optional.empty() : Optional.ofNullable(clansById.get(id));
    }

    public Optional<ClanData> findClanByNameOrTag(String query) {
        if (query == null) return Optional.empty();
        for (ClanData c : clansById.values()) {
            if (c.name().equalsIgnoreCase(query) || c.tag().equalsIgnoreCase(query)) {
                return Optional.of(c);
            }
        }
        return Optional.empty();
    }

    public List<ClanData> getTopClans(int limit) {
        return clansById.values().stream()
                .sorted(Comparator.comparingInt(ClanData::ratingPoints).reversed())
                .limit(limit)
                .toList();
    }

    public Optional<ClanMember> getMember(ClanData clan, UUID playerUuid) {
        return clan.members().stream().filter(m -> m.playerUuid().equals(playerUuid)).findFirst();
    }

    public boolean canDamage(UUID attacker, UUID victim) {
        Optional<ClanData> c1 = getPlayerClan(attacker);
        Optional<ClanData> c2 = getPlayerClan(victim);
        if (c1.isEmpty() || c2.isEmpty()) return true;
        if (c1.get().id() == c2.get().id()) {
            return c1.get().friendlyFire();
        }
        String rel = c1.get().relations().get(c2.get().id());
        if ("ALLY".equalsIgnoreCase(rel)
                && configManager.get("clans.yml").getBoolean("relations.ally-prevent-pvp", true)) {
            return false;
        }
        return true;
    }

    public boolean createClan(Player leader, String rawName, String rawTag) {
        if (getPlayerClan(leader.getUniqueId()).isPresent()) {
            locale.send(leader, "clan.already-in-clan");
            return false;
        }
        var cfg = configManager.get("clans.yml");
        String name = rawName.trim();
        String tag = rawTag.trim().toUpperCase(Locale.ROOT);
        if (name.length() < cfg.getInt("creation.min-name-length", 3)
                || name.length() > cfg.getInt("creation.max-name-length", 16)
                || !ALPHANUM_CYRILLIC.matcher(name).matches()) {
            locale.sendRaw(leader, "<red>Название клана должно быть от 3 до 16 букв/цифр!");
            return false;
        }
        if (tag.length() < cfg.getInt("creation.min-tag-length", 2)
                || tag.length() > cfg.getInt("creation.max-tag-length", 5)
                || !ALPHANUM_CYRILLIC.matcher(tag).matches()) {
            locale.sendRaw(leader, "<red>Тег клана должен быть от 2 до 5 символов!");
            return false;
        }

        long cost = cfg.getLong("creation.cost-coins", 50_000L);
        try {
            ClanData created = storage.clans().createClanAtomic(leader.getUniqueId(), leader.getName(), name, tag, cost);
            clansById.put(created.id(), created);
            playerToClanId.put(leader.getUniqueId(), created.id());
            playerModule.findProfile(leader.getUniqueId())
                    .ifPresent(p -> p.setBalance(ru.reallyworld.ultimate.core.api.CurrencyType.COINS,
                            storage.economy().getBalance(leader.getUniqueId(), ru.reallyworld.ultimate.core.api.CurrencyType.COINS)));
            locale.send(leader, "clan.created", Map.of("tag", tag, "name", name));
            return true;
        } catch (IllegalArgumentException ex) {
            locale.sendRaw(leader, "<red>Клан с таким именем или тегом уже существует!");
            return false;
        } catch (IllegalStateException ex) {
            locale.send(leader, "economy.not-enough-coins", Map.of("needed", TextUtil.formatAmount(cost)));
            return false;
        }
    }

    public void invitePlayer(Player inviter, Player target) {
        ClanData clan = getPlayerClan(inviter.getUniqueId()).orElse(null);
        if (clan == null) {
            locale.send(inviter, "clan.not-in-clan");
            return;
        }
        ClanMember actor = getMember(clan, inviter.getUniqueId()).orElse(null);
        if (actor == null || actor.role().power() < ClanRole.ELDER.power()) {
            locale.sendRaw(inviter, "<red>Приглашать игроков могут только Старейшины и выше!");
            return;
        }
        int maxMembers = configManager.get("clans.yml").getInt("limits.base-max-members", 15);
        if (clan.members().size() >= maxMembers) {
            locale.sendRaw(inviter, "<red>В клане достигнут лимит участников (" + maxMembers + ")!");
            return;
        }
        if (getPlayerClan(target.getUniqueId()).isPresent()) {
            locale.sendRaw(inviter, "<red>Этот игрок уже состоит в другом клане!");
            return;
        }
        int expSec = configManager.get("clans.yml").getInt("limits.invite-expire-seconds", 60);
        pendingInvites.put(target.getUniqueId(), new ClanInvite(
                clan.id(), clan.name(), inviter.getUniqueId(), System.currentTimeMillis() + expSec * 1000L));
        locale.send(inviter, "clan.invited", Map.of("player", target.getName()));
        locale.send(target, "clan.invite-received", Map.of("name", clan.name(), "tag", clan.tag()));
    }

    public void respondInvite(Player target, boolean accept) {
        ClanInvite inv = pendingInvites.remove(target.getUniqueId());
        if (inv == null || System.currentTimeMillis() > inv.expiresAt()) {
            locale.sendRaw(target, "<red>У вас нет активных приглашений в клан.");
            return;
        }
        if (!accept) {
            locale.sendRaw(target, "<yellow>Вы отклонили приглашение в клан.");
            return;
        }
        if (getPlayerClan(target.getUniqueId()).isPresent()) {
            locale.send(target, "clan.already-in-clan");
            return;
        }
        storage.clans().addMember(inv.clanId(), target.getUniqueId(), target.getName());
        reloadCacheFromDb();
        ClanData updated = clansById.get(inv.clanId());
        if (updated != null) {
            broadcastToClan(updated, locale.component("clan.joined", Map.of("player", target.getName())));
        }
    }

    public void leaveClan(Player player) {
        ClanData clan = getPlayerClan(player.getUniqueId()).orElse(null);
        if (clan == null) {
            locale.send(player, "clan.not-in-clan");
            return;
        }
        if (clan.leaderUuid().equals(player.getUniqueId())) {
            locale.sendRaw(player, "<red>Глава не может покинуть клан! Используйте <gold>/clan disband</gold>.");
            return;
        }
        storage.clans().removeMember(player.getUniqueId());
        reloadCacheFromDb();
        locale.send(player, "clan.left");
    }

    public void kickMember(Player actorPlayer, String targetName) {
        ClanData clan = getPlayerClan(actorPlayer.getUniqueId()).orElse(null);
        if (clan == null) {
            locale.send(actorPlayer, "clan.not-in-clan");
            return;
        }
        ClanMember actor = getMember(clan, actorPlayer.getUniqueId()).orElse(null);
        ClanMember victim = clan.members().stream()
                .filter(m -> m.playerName().equalsIgnoreCase(targetName))
                .findFirst().orElse(null);
        if (victim == null) {
            locale.sendRaw(actorPlayer, "<red>Участник не найден в вашем клане.");
            return;
        }
        if (actor == null || actor.role().power() <= victim.role().power()) {
            locale.sendRaw(actorPlayer, "<red>У вас недостаточно полномочий в клане!");
            return;
        }
        storage.clans().removeMember(victim.playerUuid());
        reloadCacheFromDb();
        locale.sendRaw(actorPlayer, "<green>Игрок <white>" + victim.playerName() + "</white> исключён из клана.");
    }

    public void changeRole(Player actorPlayer, String targetName, boolean promote) {
        ClanData clan = getPlayerClan(actorPlayer.getUniqueId()).orElse(null);
        if (clan == null || !clan.leaderUuid().equals(actorPlayer.getUniqueId())) {
            locale.sendRaw(actorPlayer, "<red>Изменять ранги участников может только Глава клана!");
            return;
        }
        ClanMember target = clan.members().stream()
                .filter(m -> m.playerName().equalsIgnoreCase(targetName))
                .findFirst().orElse(null);
        if (target == null || target.playerUuid().equals(actorPlayer.getUniqueId())) {
            locale.sendRaw(actorPlayer, "<red>Укажите корректного участника клана!");
            return;
        }
        ClanRole next = switch (target.role()) {
            case MEMBER -> promote ? ClanRole.ELDER : ClanRole.MEMBER;
            case ELDER -> promote ? ClanRole.COLEADER : ClanRole.MEMBER;
            case COLEADER -> promote ? ClanRole.COLEADER : ClanRole.ELDER;
            case LEADER -> ClanRole.LEADER;
        };
        storage.clans().updateMemberRole(target.playerUuid(), next);
        reloadCacheFromDb();
        locale.sendRaw(actorPlayer, "<green>Ранг игрока <white>" + target.playerName() + "</white> изменён на <gold>" + next.displayRu() + "</gold>!");
    }

    public void disband(Player leader) {
        ClanData clan = getPlayerClan(leader.getUniqueId()).orElse(null);
        if (clan == null || !clan.leaderUuid().equals(leader.getUniqueId())) {
            locale.sendRaw(leader, "<red>Распустить клан может только его Глава!");
            return;
        }
        broadcastToClan(clan, locale.component("clan.disbanded", Map.of()));
        storage.clans().disbandClan(clan.id());
        reloadCacheFromDb();
    }

    public void setClanHome(Player actor) {
        ClanData clan = getPlayerClan(actor.getUniqueId()).orElse(null);
        if (clan == null) {
            locale.send(actor, "clan.not-in-clan");
            return;
        }
        ClanMember m = getMember(clan, actor.getUniqueId()).orElse(null);
        if (m == null || m.role().power() < ClanRole.COLEADER.power()) {
            locale.sendRaw(actor, "<red>Установить базу клана может только Глава или Со-лидер!");
            return;
        }
        Location l = actor.getLocation();
        storage.clans().updateClanHome(clan.id(), l.getWorld().getName(), l.getX(), l.getY(), l.getZ(), l.getYaw(), l.getPitch());
        reloadCacheFromDb();
        locale.sendRaw(actor, "<green>Точка базы клана успешно установлена!");
    }

    public void teleportClanHome(Player player) {
        ClanData clan = getPlayerClan(player.getUniqueId()).orElse(null);
        if (clan == null) {
            locale.send(player, "clan.not-in-clan");
            return;
        }
        if (clan.homeWorld() == null || clan.homeX() == null) {
            locale.sendRaw(player, "<red>Точка базы клана ещё не установлена (<gold>/clan sethome</gold>)!");
            return;
        }
        World w = Bukkit.getWorld(clan.homeWorld());
        if (w == null) return;
        Location dest = new Location(w, clan.homeX(), clan.homeY(), clan.homeZ(),
                clan.homeYaw() == null ? 0f : clan.homeYaw(), clan.homePitch() == null ? 0f : clan.homePitch());
        playerModule.teleportWithWarmup(player, dest, (p, ok) -> {
            if (ok) locale.sendRaw(p, "<green>Вы телепортировались на базу клана <gold>[" + clan.tag() + "]</gold>!");
        });
    }

    public void setRelation(Player actor, String targetClanQuery, String relType) {
        ClanData clan = getPlayerClan(actor.getUniqueId()).orElse(null);
        if (clan == null || !clan.leaderUuid().equals(actor.getUniqueId())) {
            locale.sendRaw(actor, "<red>Управлять дипломатией может только Глава клана!");
            return;
        }
        ClanData target = findClanByNameOrTag(targetClanQuery).orElse(null);
        if (target == null || target.id() == clan.id()) {
            locale.sendRaw(actor, "<red>Целевой клан не найден!");
            return;
        }
        storage.clans().setRelation(clan.id(), target.id(), relType);
        reloadCacheFromDb();
        locale.sendRaw(actor, "<green>Отношение с кланом <gold>[" + target.tag() + "]</gold> установлено: <white>" + relType + "</white>!");
    }

    public void toggleFriendlyFire(Player actor) {
        ClanData clan = getPlayerClan(actor.getUniqueId()).orElse(null);
        if (clan == null || !clan.leaderUuid().equals(actor.getUniqueId())) {
            locale.sendRaw(actor, "<red>Переключать клановый PvP может только Глава!");
            return;
        }
        boolean next = !clan.friendlyFire();
        storage.clans().updateClanStats(clan.id(), clan.ratingPoints(), clan.kills(), clan.deaths(), clan.captures(), next);
        reloadCacheFromDb();
        locale.sendRaw(actor, "<green>Огонь по своим в клане теперь: " + (next ? "<red>ВКЛЮЧЁН" : "<green>ВЫКЛЮЧЕН"));
    }

    public void depositBank(Player player, long amount) {
        ClanData clan = getPlayerClan(player.getUniqueId()).orElse(null);
        if (clan == null) {
            locale.send(player, "clan.not-in-clan");
            return;
        }
        long maxBank = configManager.get("clans.yml").getLong("limits.max-bank-coins", 500_000_000L);
        try {
            long updated = storage.clans().depositToBank(clan.id(), player.getUniqueId(), amount, maxBank);
            playerModule.findProfile(player.getUniqueId())
                    .ifPresent(p -> p.setBalance(ru.reallyworld.ultimate.core.api.CurrencyType.COINS,
                            storage.economy().getBalance(player.getUniqueId(), ru.reallyworld.ultimate.core.api.CurrencyType.COINS)));
            reloadCacheFromDb();
            locale.sendRaw(player, "<green>Вы внесли <gold>" + TextUtil.formatAmount(amount)
                    + "</gold> монет в казну клана! Баланс казны: <gold>" + TextUtil.formatAmount(updated) + "</gold>");
        } catch (Exception e) {
            locale.sendRaw(player, "<red>Недостаточно монет или превышен лимит казны клана!");
        }
    }

    public void withdrawBank(Player player, long amount) {
        ClanData clan = getPlayerClan(player.getUniqueId()).orElse(null);
        if (clan == null || !clan.leaderUuid().equals(player.getUniqueId())) {
            locale.sendRaw(player, "<red>Снимать монеты из казны может только Глава клана!");
            return;
        }
        try {
            long updated = storage.clans().withdrawFromBank(clan.id(), player.getUniqueId(), amount);
            playerModule.findProfile(player.getUniqueId())
                    .ifPresent(p -> p.setBalance(ru.reallyworld.ultimate.core.api.CurrencyType.COINS,
                            storage.economy().getBalance(player.getUniqueId(), ru.reallyworld.ultimate.core.api.CurrencyType.COINS)));
            reloadCacheFromDb();
            locale.sendRaw(player, "<green>Вы сняли <gold>" + TextUtil.formatAmount(amount)
                    + "</gold> монет из казны клана! Остаток: <gold>" + TextUtil.formatAmount(updated) + "</gold>");
        } catch (Exception e) {
            locale.sendRaw(player, "<red>Недостаточно средств в казне клана!");
        }
    }

    public void sendClanChat(Player sender, String message) {
        ClanData clan = getPlayerClan(sender.getUniqueId()).orElse(null);
        if (clan == null) {
            locale.send(sender, "clan.not-in-clan");
            return;
        }
        ClanMember mem = getMember(clan, sender.getUniqueId()).orElse(null);
        String roleName = mem == null ? "Участник" : mem.role().displayRu();
        Component comp = locale.componentNoPrefix("clan.chat-format", Map.of(
                "role", roleName,
                "player", sender.getName(),
                "message", TextUtil.sanitizeInput(message, 200)
        ));
        broadcastToClan(clan, comp);
    }

    public void broadcastToClan(ClanData clan, Component msg) {
        if (Bukkit.getServer() == null) return;
        for (ClanMember m : clan.members()) {
            Player online = Bukkit.getPlayer(m.playerUuid());
            if (online != null) online.sendMessage(msg);
        }
    }

    public void recordPvpKill(UUID killerUuid, UUID victimUuid) {
        getPlayerClan(killerUuid).ifPresent(c -> {
            int gain = configManager.get("clans.yml").getInt("rating.points-per-kill", 3);
            storage.runAsync(() -> {
                storage.clans().updateClanStats(c.id(), c.ratingPoints() + gain, c.kills() + 1, c.deaths(), c.captures(), c.friendlyFire());
                reloadCacheFromDb();
            });
        });
        getPlayerClan(victimUuid).ifPresent(c -> {
            int loss = configManager.get("clans.yml").getInt("rating.points-lost-per-death", 1);
            storage.runAsync(() -> {
                storage.clans().updateClanStats(c.id(), Math.max(0, c.ratingPoints() - loss), c.kills(), c.deaths() + 1, c.captures(), c.friendlyFire());
                reloadCacheFromDb();
            });
        });
    }

    public void addEventRating(long clanId, int deltaPoints, boolean isCapture) {
        ClanData c = clansById.get(clanId);
        if (c == null) return;
        storage.runAsync(() -> {
            storage.clans().updateClanStats(
                    c.id(),
                    c.ratingPoints() + deltaPoints,
                    c.kills(),
                    c.deaths(),
                    c.captures() + (isCapture ? 1 : 0),
                    c.friendlyFire()
            );
            reloadCacheFromDb();
        });
    }

    // Support `@message` shortcut for Clan Chat
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onClanChatPrefix(AsyncChatEvent event) {
        if (!enabled) return;
        String prefix = configManager.get("clans.yml").getString("chat.prefix-symbol", "@");
        String plain = PlainTextComponentSerializer.plainText().serialize(event.message());
        if (plain.startsWith(prefix) && plain.length() > prefix.length()) {
            if (getPlayerClan(event.getPlayer().getUniqueId()).isPresent()) {
                event.setCancelled(true);
                sendClanChat(event.getPlayer(), plain.substring(prefix.length()).trim());
            }
        }
    }
}
