package ru.reallyworld.ultimate.core.module;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import ru.reallyworld.ultimate.core.api.CurrencyType;
import ru.reallyworld.ultimate.core.api.ReallyModule;
import ru.reallyworld.ultimate.core.config.ConfigManager;
import ru.reallyworld.ultimate.core.storage.StorageModule;
import ru.reallyworld.ultimate.core.storage.model.PlayerProfile;
import ru.reallyworld.ultimate.core.storage.repository.ProgressionRepository.QuestState;
import ru.reallyworld.ultimate.core.util.TextUtil;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.IsoFields;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Multi-category Quest Engine (Daily, Weekly, Combat, Economy, Event, Main).
 * Tracks player actions and guarantees atomic single-claim reward payout.
 */
public final class QuestsModule implements ReallyModule {
    public record QuestDef(
            String id,
            String category,
            String title,
            String objective,
            long target,
            long rewardCoins,
            long rewardRilliks,
            long rewardBpXp,
            String rewardItemSpec
    ) {}

    private final ConfigManager configManager;
    private final StorageModule storage;
    private final LocalizationModule locale;
    private final Map<String, QuestDef> quests = new LinkedHashMap<>();
    private final Map<UUID, Map<String, QuestState>> playerCache = new ConcurrentHashMap<>();

    private PlayerModule playerModule;
    private EconomyModule economyModule;
    private KitsModule kitsModule;
    private BattlePassModule battlePassModule;
    private volatile boolean enabled;

    public QuestsModule(ConfigManager configManager, StorageModule storage, LocalizationModule locale) {
        this.configManager = configManager;
        this.storage = storage;
        this.locale = locale;
    }

    public void bindModules(
            PlayerModule playerModule,
            EconomyModule economyModule,
            KitsModule kitsModule,
            BattlePassModule battlePassModule
    ) {
        this.playerModule = playerModule;
        this.economyModule = economyModule;
        this.kitsModule = kitsModule;
        this.battlePassModule = battlePassModule;
    }

    @Override
    public String id() { return "quests"; }

    @Override
    public String displayName() { return "QuestsModule (/quests)"; }

    @Override
    public boolean isEnabled() { return enabled; }

    @Override
    public void enable() {
        this.enabled = configManager.main().getBoolean("modules.quests", true);
        reloadQuests();
    }

    @Override
    public void reload() {
        reloadQuests();
    }

    @Override
    public void disable() {
        this.enabled = false;
        playerCache.clear();
    }

    private synchronized void reloadQuests() {
        quests.clear();
        ConfigurationSection sec = configManager.get("quests.yml").getConfigurationSection("quests");
        if (sec == null) return;
        for (String id : sec.getKeys(false)) {
            ConfigurationSection q = sec.getConfigurationSection(id);
            quests.put(id, new QuestDef(
                    id,
                    q.getString("category", "DAILY"),
                    q.getString("title", id),
                    q.getString("objective", "MINE_BLOCKS"),
                    q.getLong("target", 10L),
                    q.getLong("rewards.coins", 0L),
                    q.getLong("rewards.rilliks", 0L),
                    q.getLong("rewards.bp-xp", 0L),
                    q.getString("rewards.custom-item", "")
            ));
        }
    }

    public Collection<QuestDef> getAllQuests() {
        return quests.values();
    }

    public String currentPeriodKey(String category) {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        return switch (category.toUpperCase(Locale.ROOT)) {
            case "DAILY" -> "D:" + today;
            case "WEEKLY" -> "W:" + today.get(IsoFields.WEEK_BASED_YEAR) + "-" + today.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR);
            default -> "PERMANENT";
        };
    }

    public QuestState getState(UUID playerUuid, QuestDef def) {
        Map<String, QuestState> map = playerCache.computeIfAbsent(
                playerUuid, u -> new ConcurrentHashMap<>(storage.progression().loadPlayerQuests(u)));
        String pKey = currentPeriodKey(def.category());
        return map.getOrDefault(def.id() + ":" + pKey, new QuestState(def.id(), pKey, 0L, false, false));
    }

    public void recordProgress(Player player, String objectiveType, long delta) {
        if (!enabled || delta <= 0) return;
        UUID uuid = player.getUniqueId();
        Map<String, QuestState> map = playerCache.computeIfAbsent(
                uuid, u -> new ConcurrentHashMap<>(storage.progression().loadPlayerQuests(u)));

        for (QuestDef def : quests.values()) {
            if (!def.objective().equalsIgnoreCase(objectiveType)) continue;
            String pKey = currentPeriodKey(def.category());
            String mapKey = def.id() + ":" + pKey;
            QuestState cur = map.getOrDefault(mapKey, new QuestState(def.id(), pKey, 0L, false, false));
            if (cur.completed()) continue;

            long nextProg = Math.min(def.target(), cur.progress() + delta);
            boolean nowDone = nextProg >= def.target();
            QuestState next = new QuestState(def.id(), pKey, nextProg, nowDone, false);
            map.put(mapKey, next);
            storage.runAsync(() -> storage.progression().upsertQuestProgress(uuid, def.id(), pKey, nextProg, nowDone));
            if (nowDone) {
                locale.sendRaw(player, "<#FBBC00>Задание выполнено: <white>" + def.title()
                        + "</white>! Заберите награду в <gold>/quests</gold>.");
            }
        }
    }

    public boolean claimQuestReward(Player player, String questId) {
        QuestDef def = quests.get(questId);
        if (def == null) return false;
        QuestState st = getState(player.getUniqueId(), def);
        if (!st.completed() || st.claimed()) {
            locale.sendRaw(player, "<red>Это задание ещё не выполнено или награда уже получена!");
            return false;
        }
        String pKey = currentPeriodKey(def.category());
        if (!storage.progression().markQuestClaimedAtomic(player.getUniqueId(), def.id(), pKey)) {
            return false;
        }
        playerCache.computeIfAbsent(player.getUniqueId(), k -> new ConcurrentHashMap<>())
                .put(def.id() + ":" + pKey, new QuestState(def.id(), pKey, st.progress(), true, true));

        if (def.rewardCoins() > 0 && economyModule != null) {
            economyModule.deposit(player.getUniqueId(), CurrencyType.COINS, def.rewardCoins(), "QUEST_REWARD", def.id());
        }
        if (def.rewardRilliks() > 0 && economyModule != null) {
            economyModule.deposit(player.getUniqueId(), CurrencyType.RILLIKS, def.rewardRilliks(), "QUEST_REWARD", def.id());
        }
        if (def.rewardBpXp() > 0 && battlePassModule != null) {
            battlePassModule.addXp(player, def.rewardBpXp());
        }
        if (def.rewardItemSpec() != null && !def.rewardItemSpec().isBlank() && kitsModule != null) {
            for (ItemStack item : kitsModule.parseItemSpec("CUSTOM:" + def.rewardItemSpec())) {
                player.getInventory().addItem(item).values()
                        .forEach(rem -> player.getWorld().dropItemNaturally(player.getLocation(), rem));
            }
        }
        if (playerModule != null) {
            PlayerProfile p = playerModule.getProfile(player);
            p.setQuestsCompleted(p.questsCompleted() + 1);
            storage.runAsync(() -> storage.players().saveProfileStats(p));
        }
        locale.sendRaw(player, "<green>Награда за квест «" + def.title() + "<green>» получена! (<gold>+"
                + TextUtil.formatAmount(def.rewardCoins()) + " монет</gold>)");
        return true;
    }
}
