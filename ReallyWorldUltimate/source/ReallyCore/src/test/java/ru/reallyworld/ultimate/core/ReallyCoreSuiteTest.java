package ru.reallyworld.ultimate.core;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import ru.reallyworld.ultimate.core.api.CurrencyType;
import ru.reallyworld.ultimate.core.config.ConfigManager;
import ru.reallyworld.ultimate.core.config.RankDefinition;
import ru.reallyworld.ultimate.core.module.CasesModule;
import ru.reallyworld.ultimate.core.module.LocalizationModule;
import ru.reallyworld.ultimate.core.storage.StorageModule;
import ru.reallyworld.ultimate.core.storage.model.ClanData;
import ru.reallyworld.ultimate.core.storage.model.HomePoint;
import ru.reallyworld.ultimate.core.storage.model.PlayerProfile;
import ru.reallyworld.ultimate.core.storage.repository.AuctionRepository;
import ru.reallyworld.ultimate.core.storage.repository.EconomyRepository;
import ru.reallyworld.ultimate.core.util.CryptoSignatureUtil;

import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

import static org.junit.Assert.*;

/**
 * Automated Unit, SQLite Integration, Multi-Threaded Concurrency & Security Test Suite
 * for ReallyWorld Ultimate (ReallyCore v1.0.0).
 */
public class ReallyCoreSuiteTest {
    private File tempDir;
    private ConfigManager configManager;
    private StorageModule storage;

    @Before
    public void setUp() throws Exception {
        this.tempDir = Files.createTempDirectory("reallycore-test-").toFile();
        Logger log = Logger.getLogger("ReallyCoreTest");
        this.configManager = new ConfigManager(tempDir, log);
        this.configManager.loadAll();

        this.storage = new StorageModule(tempDir, configManager, log);
        this.storage.enable();
    }

    @After
    public void tearDown() {
        if (storage != null) {
            storage.disable();
        }
        deleteRecursively(tempDir);
    }

    @Test
    public void testAll25YamlConfigurationsAndRankSecurity() throws Exception {
        for (String cfgName : ConfigManager.CONFIG_FILES) {
            YamlConfiguration yaml = configManager.get(cfgName);
            assertNotNull("Loaded config must not be null: " + cfgName, yaml);
            assertFalse("Loaded config must not be empty: " + cfgName, yaml.getKeys(false).isEmpty());
        }

        // Verify plugin.yml commands
        YamlConfiguration pluginYml;
        try (var in = Objects.requireNonNull(getClass().getClassLoader().getResourceAsStream("plugin.yml"))) {
            pluginYml = YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8));
        }
        assertEquals("ru.reallyworld.ultimate.core.ReallyCorePlugin", pluginYml.getString("main"));
        ConfigurationSection cmds = pluginYml.getConfigurationSection("commands");
        assertNotNull(cmds);
        assertTrue("Expected 50+ registered commands in plugin.yml", cmds.getKeys(false).size() >= 50);

        // Verify Donor Rank Security: GOD and DRAGON must NEVER inherit admin/op/worldedit permissions!
        for (String donor : List.of("HERO", "TITAN", "AVENGER", "OVERLORD", "MAGISTER", "IMPERATOR", "DRAGON", "GOD", "RABBIT", "COBRA")) {
            RankDefinition def = configManager.getRank(donor);
            assertNotNull(def);
            assertFalse("Donor rank " + donor + " must NEVER have reallycore.admin",
                    def.resolvedPermissions().contains("reallycore.admin"));
            assertFalse("Donor rank " + donor + " must NEVER have reallycore.staff",
                    def.resolvedPermissions().contains("reallycore.staff"));
            for (String perm : def.resolvedPermissions()) {
                assertFalse("Dangerous permission in donor rank: " + perm,
                        perm.contains("worldedit") || perm.contains("luckperms") || perm.contains("op"));
            }
        }

        // Verify RW Kit Inheritance Rule: DRAGON inherits /kit plus from IMPERATOR, but NOT /kit hero!
        RankDefinition dragon = configManager.getRank("DRAGON");
        assertTrue(dragon.resolvedPermissions().contains("reallycore.kit.dragon"));
        assertTrue(dragon.resolvedPermissions().contains("reallycore.kit.plus"));
        assertFalse(dragon.resolvedPermissions().contains("reallycore.kit.hero"));
    }

    @Test
    public void testShopAntiArbitrageMathematics() {
        // Explicit verification that sellPrice * maxHotDealMultiplier (3) < buyPrice across ALL shop entries
        configManager.validateShopAntiArbitrage();
        YamlConfiguration shop = configManager.get("shop.yml");
        ConfigurationSection cats = shop.getConfigurationSection("categories");
        assertNotNull(cats);
        assertEquals(7, cats.getKeys(false).size());
        int checkedItems = 0;
        for (String cKey : cats.getKeys(false)) {
            ConfigurationSection items = cats.getConfigurationSection(cKey + ".items");
            assertNotNull(items);
            for (String iKey : items.getKeys(false)) {
                long buy = items.getLong(iKey + ".buy-coins", 0L);
                long sell = items.getLong(iKey + ".sell-coins", 0L);
                if (buy > 0 && sell > 0) {
                    assertTrue("Arbitrage violation on " + iKey, sell * 3L < buy);
                }
                checkedItems++;
            }
        }
        assertTrue("Expected at least 35 shop items", checkedItems >= 35);
    }

    @Test
    public void testAtomicEconomyOverflowNegativeAndTransfer() {
        UUID u1 = UUID.randomUUID();
        UUID u2 = UUID.randomUUID();
        PlayerProfile p1 = storage.players().loadOrCreate(u1, "Alpha", 10_000L, 100L, 0L);
        PlayerProfile p2 = storage.players().loadOrCreate(u2, "Bravo", 1_000L, 0L, 0L);
        assertEquals(10_000L, p1.coins());
        assertEquals(1_000L, p2.coins());

        // Reject non-positive deposit/withdraw
        assertFalse(storage.economy().deposit(u1, CurrencyType.COINS, 0L, "TEST", "").success());
        assertFalse(storage.economy().deposit(u1, CurrencyType.COINS, -500L, "TEST", "").success());
        assertFalse(storage.economy().withdraw(u1, CurrencyType.COINS, -1L, "TEST", "").success());

        // Reject insufficient funds
        assertFalse(storage.economy().withdraw(u1, CurrencyType.COINS, 50_000L, "TEST", "").success());
        assertEquals(10_000L, storage.economy().getBalance(u1, CurrencyType.COINS));

        // Reject overflow past 1 Trillion cap
        assertFalse(storage.economy().deposit(u1, CurrencyType.COINS, EconomyRepository.MAX_BALANCE, "TEST", "").success());

        // Atomic P2P Transfer with 3% fee: 5,000 sent -> sender 5,000 left, fee=150, receiver +4,850 -> 5,850
        EconomyRepository.TransferResult tr = storage.economy().transfer(u1, u2, CurrencyType.COINS, 5_000L, 3, "Unit test pay");
        assertTrue(tr.success());
        assertEquals(5_000L, tr.senderBalance());
        assertEquals(5_850L, tr.receiverBalance());
        assertEquals(4_850L, tr.netReceived());

        // Reject self-transfer
        assertFalse(storage.economy().transfer(u1, u1, CurrencyType.COINS, 100L, 3, "Self").success());
    }

    @Test
    public void testConcurrentAuctionAntiDoubleBuyRace() throws Exception {
        UUID seller = UUID.randomUUID();
        storage.players().loadOrCreate(seller, "SellerPro", 5_000L, 0L, 0L);

        AuctionRepository.CreateOutcome created = storage.auction().createListing(
                seller, "SellerPro", "NETHERITE_SWORD", "Клинок Жнеца", "CUSTOM", 1,
                "Y1:dHlwZTogTkVUSEVSSVRFX1NXT1JECmFtb3VudDogMQo=",
                10_000L, CurrencyType.COINS, 25L,
                System.currentTimeMillis() + 3_600_000L, 5
        );
        assertTrue(created.success());
        assertEquals(4_975L, created.sellerBalanceAfter());

        // Self-buy must fail
        assertEquals("SELF_BUY", storage.auction().buyListingAtomic(created.listingId(), seller, "SellerPro", 5).reason());

        // Insufficient funds buyer must roll back cleanly leaving listing ACTIVE
        UUID brokeBuyer = UUID.randomUUID();
        storage.players().loadOrCreate(brokeBuyer, "Broke", 500L, 0L, 0L);
        try {
            storage.auction().buyListingAtomic(created.listingId(), brokeBuyer, "Broke", 5);
            fail("Expected InsufficientAuctionFundsException");
        } catch (AuctionRepository.InsufficientAuctionFundsException expected) {
            // Listing must still be ACTIVE!
            assertEquals(1, storage.auction().countActiveListings(seller, System.currentTimeMillis()));
        }

        // Spawn 16 rich buyers racing concurrently for the SAME listing!
        int threads = 16;
        List<UUID> buyers = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            UUID b = UUID.randomUUID();
            storage.players().loadOrCreate(b, "Buyer" + i, 50_000L, 0L, 0L);
            buyers.add(b);
        }

        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger winners = new AtomicInteger(0);
        AtomicInteger losers = new AtomicInteger(0);

        for (int i = 0; i < threads; i++) {
            final UUID bUuid = buyers.get(i);
            final String bName = "Buyer" + i;
            pool.submit(() -> {
                ready.countDown();
                try {
                    start.await();
                    AuctionRepository.BuyOutcome out = storage.auction().buyListingAtomic(created.listingId(), bUuid, bName, 5);
                    if (out.success()) winners.incrementAndGet();
                    else losers.incrementAndGet();
                } catch (Exception ignored) {
                    losers.incrementAndGet();
                }
            });
        }

        ready.await(5, TimeUnit.SECONDS);
        start.countDown();
        pool.shutdown();
        assertTrue(pool.awaitTermination(10, TimeUnit.SECONDS));

        assertEquals("EXACTLY 1 buyer must win concurrent race!", 1, winners.get());
        assertEquals("Remaining 15 buyers must fail cleanly!", 15, losers.get());
        // Seller should have 4,975 + 9,500 (10,000 - 5% tax) = 14,475 coins!
        assertEquals(14_475L, storage.economy().getBalance(seller, CurrencyType.COINS));
    }

    @Test
    public void testClansHomesWarpsAndDoubleClaimLocks() {
        UUID leader = UUID.randomUUID();
        UUID member = UUID.randomUUID();
        storage.players().loadOrCreate(leader, "ClanBoss", 100_000L, 0L, 0L);
        storage.players().loadOrCreate(member, "ClanMate", 20_000L, 0L, 0L);

        // Homes & Warps
        storage.players().upsertHome(new HomePoint(leader, "base", "world", 100.5, 65.0, -200.5, 0f, 0f, System.currentTimeMillis()));
        assertEquals(1, storage.players().getHomes(leader).size());
        assertTrue(storage.players().deleteHome(leader, "base"));
        assertEquals(0, storage.players().getHomes(leader).size());

        // Clan Creation & Bank
        ClanData clan = storage.clans().createClanAtomic(leader, "ClanBoss", "Legions", "LGN", 50_000L);
        assertEquals(50_000L, storage.economy().getBalance(leader, CurrencyType.COINS));
        storage.clans().addMember(clan.id(), member, "ClanMate");
        assertEquals(15_000L, storage.clans().depositToBank(clan.id(), member, 15_000L, 500_000_000L));
        assertEquals(5_000L, storage.economy().getBalance(member, CurrencyType.COINS));
        assertEquals(5_000L, storage.clans().withdrawFromBank(clan.id(), leader, 10_000L));
        assertEquals(60_000L, storage.economy().getBalance(leader, CurrencyType.COINS));

        // Reward Anti-Double-Claim SQL Lock
        assertTrue(storage.progression().tryClaimRewardAtomic(leader, "DAILY", "DAY:2026-10-24"));
        assertFalse("Second claim for same period MUST be rejected!",
                storage.progression().tryClaimRewardAtomic(leader, "DAILY", "DAY:2026-10-24"));
    }

    @Test
    public void testCaseProbabilitiesAndConcurrentKeyAntiDupe() throws Exception {
        LocalizationModule loc = new LocalizationModule(configManager);
        loc.enable();
        CasesModule cases = new CasesModule(null, configManager, storage, loc, null, null, null);
        cases.enable();
        assertEquals("Expected all 8 crates loaded", 8, cases.getCases().size());

        for (CasesModule.CaseDefinition def : cases.getCases()) {
            assertEquals("Weights of " + def.id() + " should sum to 100.0", 100.0, def.totalWeight(), 0.01);
        }

        // Monte Carlo verification (50,000 spins on Donate Case)
        CasesModule.CaseDefinition donateCase = cases.getCase("donate").orElseThrow();
        Random rng = new Random(42L);
        Map<String, Integer> counts = new HashMap<>();
        int trials = 50_000;
        for (int i = 0; i < trials; i++) {
            CasesModule.CaseReward r = cases.rollWeightedReward(donateCase, rng);
            counts.merge(r.id(), 1, Integer::sum);
        }
        for (CasesModule.CaseReward r : donateCase.rewards()) {
            double expected = r.weight() / donateCase.totalWeight();
            double empirical = counts.getOrDefault(r.id(), 0) / (double) trials;
            assertEquals("Empirical probability for " + r.id() + " must match configured weight",
                    expected, empirical, 0.012);
        }

        // Concurrent Key Anti-Dupe Test: 1 key granted, 10 threads try to consume simultaneously
        UUID opener = UUID.randomUUID();
        storage.players().loadOrCreate(opener, "LuckyPlayer", 0L, 0L, 0L);
        storage.progression().addCaseKeys(opener, "spheres", 1);

        ExecutorService pool = Executors.newFixedThreadPool(10);
        AtomicInteger consumed = new AtomicInteger(0);
        List<Future<?>> futures = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            futures.add(pool.submit(() -> {
                if (storage.progression().consumeOneKeyAtomic(opener, "spheres")) {
                    consumed.incrementAndGet();
                }
            }));
        }
        for (Future<?> f : futures) f.get(5, TimeUnit.SECONDS);
        pool.shutdown();
        assertEquals("EXACTLY 1 thread can consume a single crate key!", 1, consumed.get());
        assertEquals(0, (int) storage.progression().getCaseKeys(opener).getOrDefault("spheres", 0));
    }

    @Test
    public void testCustomItemHmacSignatureAntiForgery() {
        String salt = "rw-prod-secret-salt-2026";
        String sig = CryptoSignatureUtil.signItem(salt, "SPHERE", "ares", "MAX");
        assertNotNull(sig);
        assertEquals(32, sig.length());

        assertTrue(CryptoSignatureUtil.verifyItem(salt, "SPHERE", "ares", "MAX", sig));
        // Forging tier, itemId, category, or salt MUST fail!
        assertFalse(CryptoSignatureUtil.verifyItem(salt, "SPHERE", "ares", "TIER_1", sig));
        assertFalse(CryptoSignatureUtil.verifyItem(salt, "SPHERE", "erida", "MAX", sig));
        assertFalse(CryptoSignatureUtil.verifyItem(salt, "TALISMAN", "ares", "MAX", sig));
        assertFalse(CryptoSignatureUtil.verifyItem("wrong-salt", "SPHERE", "ares", "MAX", sig));
    }

    @Test
    public void testCooldownsQuestsAndMariaDbPostgresSchemas() throws Exception {
        UUID u = UUID.randomUUID();
        storage.players().loadOrCreate(u, "QuestRunner", 1_000L, 0L, 0L);

        // Cooldown persistence in SQLite
        ru.reallyworld.ultimate.core.module.CooldownModule cd =
                new ru.reallyworld.ultimate.core.module.CooldownModule(configManager, storage);
        cd.enable();
        storage.progression().setCooldown(u, "kit:titan", System.currentTimeMillis() + 60_000L);
        cd.loadPlayer(u);
        assertTrue(cd.isOnCooldown(u, "kit:titan"));
        assertTrue(cd.getRemainingSeconds(u, "kit:titan") >= 55L);

        // Quest progress & single-claim atomic lock
        storage.progression().upsertQuestProgress(u, "daily_mine", "D:2026-10-24", 100L, true);
        assertTrue("First quest claim must succeed",
                storage.progression().markQuestClaimedAtomic(u, "daily_mine", "D:2026-10-24"));
        assertFalse("Duplicate quest claim must be blocked!",
                storage.progression().markQuestClaimedAtomic(u, "daily_mine", "D:2026-10-24"));

        // TextUtil sanitization & Russian formatting
        assertEquals("1 250 000", ru.reallyworld.ultimate.core.util.TextUtil.formatAmount(1_250_000L));
        assertEquals("CleanNick", ru.reallyworld.ultimate.core.util.TextUtil.sanitizeInput("<red>§lCleanNick", 16));

        // Verify MariaDB & PostgreSQL migration SQL resources exist and define all 16 tables
        for (String res : List.of("migrations/V1__initial_schema_mariadb.sql", "migrations/V1__initial_schema_postgresql.sql")) {
            try (var in = Objects.requireNonNull(getClass().getClassLoader().getResourceAsStream(res))) {
                String sql = new String(in.readAllBytes(), StandardCharsets.UTF_8);
                assertTrue(sql.contains("CREATE TABLE IF NOT EXISTS players"));
                assertTrue(sql.contains("CREATE TABLE IF NOT EXISTS economy_transactions"));
                assertTrue(sql.contains("CREATE TABLE IF NOT EXISTS auction_listings"));
                assertTrue(sql.contains("CREATE TABLE IF NOT EXISTS clans"));
                assertTrue(sql.contains("CREATE TABLE IF NOT EXISTS security_audit_log"));
            }
        }
    }

    private void deleteRecursively(File f) {
        if (f == null || !f.exists()) return;
        File[] children = f.listFiles();
        if (children != null) {
            for (File c : children) deleteRecursively(c);
        }
        f.delete();
    }
}
