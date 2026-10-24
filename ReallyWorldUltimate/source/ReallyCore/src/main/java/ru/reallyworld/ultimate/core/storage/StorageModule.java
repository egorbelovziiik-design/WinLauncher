package ru.reallyworld.ultimate.core.storage;

import org.bukkit.configuration.file.YamlConfiguration;
import ru.reallyworld.ultimate.core.api.ReallyModule;
import ru.reallyworld.ultimate.core.config.ConfigManager;
import ru.reallyworld.ultimate.core.storage.repository.*;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Locale;
import java.util.concurrent.*;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Thread-safe JDBC Storage Module supporting SQLite (WAL), MariaDB, and PostgreSQL.
 * Runs V1 SQL migrations on enable, provides atomic SQL transaction templates,
 * and exposes an async worker pool so the Paper main thread is never blocked.
 */
public final class StorageModule implements ReallyModule {
    @FunctionalInterface
    public interface SqlWork<T> {
        T execute(Connection conn) throws SQLException;
    }

    private final File dataFolder;
    private final ConfigManager configManager;
    private final Logger logger;
    private final ReentrantLock sqliteWriteLock = new ReentrantLock(true);

    private volatile boolean enabled;
    private String storageType = "SQLITE";
    private String jdbcUrl;
    private String dbUser;
    private String dbPass;
    private Connection sharedSqliteConnection;
    private ExecutorService asyncExecutor;

    private PlayerRepository playerRepository;
    private EconomyRepository economyRepository;
    private AuctionRepository auctionRepository;
    private ClanRepository clanRepository;
    private ProgressionRepository progressionRepository;

    public StorageModule(File dataFolder, ConfigManager configManager, Logger logger) {
        this.dataFolder = dataFolder;
        this.configManager = configManager;
        this.logger = logger;
    }

    @Override
    public String id() { return "storage"; }

    @Override
    public String displayName() { return "StorageModule (SQL)"; }

    @Override
    public boolean isEnabled() { return enabled; }

    @Override
    public void enable() throws Exception {
        YamlConfiguration cfg = configManager.main();
        this.storageType = cfg.getString("storage.type", "SQLITE").toUpperCase(Locale.ROOT);
        int threads = Math.max(2, cfg.getInt("storage.pool.maximum-pool-size", 4));
        this.asyncExecutor = Executors.newFixedThreadPool(threads, r -> {
            Thread t = new Thread(r, "ReallyCore-SQL-Worker");
            t.setDaemon(true);
            return t;
        });

        switch (storageType) {
            case "MARIADB", "MYSQL" -> {
                Class.forName("org.mariadb.jdbc.Driver");
                String host = cfg.getString("storage.mariadb.host", "127.0.0.1");
                int port = cfg.getInt("storage.mariadb.port", 3306);
                String db = cfg.getString("storage.mariadb.database", "reallyworld");
                String params = cfg.getString("storage.mariadb.parameters", "?useUnicode=true&characterEncoding=utf8");
                this.jdbcUrl = "jdbc:mariadb://" + host + ":" + port + "/" + db + params;
                this.dbUser = cfg.getString("storage.mariadb.username", "reallyworld");
                this.dbPass = configManager.resolveEnvValue(cfg.getString("storage.mariadb.password", ""));
            }
            case "POSTGRESQL", "POSTGRES" -> {
                Class.forName("org.postgresql.Driver");
                String host = cfg.getString("storage.postgresql.host", "127.0.0.1");
                int port = cfg.getInt("storage.postgresql.port", 5432);
                String db = cfg.getString("storage.postgresql.database", "reallyworld");
                this.jdbcUrl = "jdbc:postgresql://" + host + ":" + port + "/" + db;
                this.dbUser = cfg.getString("storage.postgresql.username", "reallyworld");
                this.dbPass = configManager.resolveEnvValue(cfg.getString("storage.postgresql.password", ""));
            }
            default -> {
                this.storageType = "SQLITE";
                Class.forName("org.sqlite.JDBC");
                String relPath = cfg.getString("storage.sqlite.file", "data/reallycore.db");
                File dbFile = new File(dataFolder, relPath);
                if (dbFile.getParentFile() != null && !dbFile.getParentFile().exists()) {
                    dbFile.getParentFile().mkdirs();
                }
                this.jdbcUrl = "jdbc:sqlite:" + dbFile.getAbsolutePath();
                this.sharedSqliteConnection = DriverManager.getConnection(this.jdbcUrl);
                try (Statement st = this.sharedSqliteConnection.createStatement()) {
                    st.execute("PRAGMA journal_mode = WAL;");
                    st.execute("PRAGMA synchronous = NORMAL;");
                    st.execute("PRAGMA foreign_keys = ON;");
                    st.execute("PRAGMA busy_timeout = 5000;");
                }
            }
        }

        runMigrations();

        this.playerRepository = new PlayerRepository(this);
        this.economyRepository = new EconomyRepository(this);
        this.auctionRepository = new AuctionRepository(this);
        this.clanRepository = new ClanRepository(this);
        this.progressionRepository = new ProgressionRepository(this);
        this.enabled = true;
    }

    private void runMigrations() throws Exception {
        String resource = switch (storageType) {
            case "MARIADB", "MYSQL" -> "migrations/V1__initial_schema_mariadb.sql";
            case "POSTGRESQL", "POSTGRES" -> "migrations/V1__initial_schema_postgresql.sql";
            default -> "migrations/V1__initial_schema_sqlite.sql";
        };
        try (InputStream in = StorageModule.class.getClassLoader().getResourceAsStream(resource)) {
            if (in == null) {
                throw new IllegalStateException("Missing SQL migration resource: " + resource);
            }
            String sqlScript = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))
                    .lines()
                    .filter(line -> !line.trim().startsWith("--"))
                    .collect(Collectors.joining("\n"));
            inTransaction(conn -> {
                try (Statement stmt = conn.createStatement()) {
                    for (String rawStmt : sqlScript.split(";")) {
                        String trimmed = rawStmt.trim();
                        if (!trimmed.isEmpty()) {
                            stmt.execute(trimmed);
                        }
                    }
                }
                return null;
            });
        }
    }

    public <T> T query(SqlWork<T> work) {
        if ("SQLITE".equals(storageType)) {
            sqliteWriteLock.lock();
            try {
                return work.execute(ensureSqliteOpen());
            } catch (SQLException e) {
                throw new RuntimeException("SQLite query failed: " + e.getMessage(), e);
            } finally {
                sqliteWriteLock.unlock();
            }
        }
        try (Connection conn = DriverManager.getConnection(jdbcUrl, dbUser, dbPass)) {
            return work.execute(conn);
        } catch (SQLException e) {
            throw new RuntimeException("SQL query failed: " + e.getMessage(), e);
        }
    }

    public <T> T inTransaction(SqlWork<T> work) {
        if ("SQLITE".equals(storageType)) {
            sqliteWriteLock.lock();
            try {
                Connection conn = ensureSqliteOpen();
                boolean prevAuto = conn.getAutoCommit();
                conn.setAutoCommit(false);
                try {
                    T result = work.execute(conn);
                    conn.commit();
                    return result;
                } catch (Throwable t) {
                    try { conn.rollback(); } catch (SQLException ignored) {}
                    throw (t instanceof RuntimeException re) ? re : new RuntimeException(t);
                } finally {
                    try { conn.setAutoCommit(prevAuto); } catch (SQLException ignored) {}
                }
            } catch (SQLException e) {
                throw new RuntimeException("SQLite transaction error: " + e.getMessage(), e);
            } finally {
                sqliteWriteLock.unlock();
            }
        }
        try (Connection conn = DriverManager.getConnection(jdbcUrl, dbUser, dbPass)) {
            conn.setAutoCommit(false);
            try {
                T result = work.execute(conn);
                conn.commit();
                return result;
            } catch (Throwable t) {
                try { conn.rollback(); } catch (SQLException ignored) {}
                throw (t instanceof RuntimeException re) ? re : new RuntimeException(t);
            }
        } catch (SQLException e) {
            throw new RuntimeException("SQL transaction error: " + e.getMessage(), e);
        }
    }

    private Connection ensureSqliteOpen() throws SQLException {
        if (sharedSqliteConnection == null || sharedSqliteConnection.isClosed()) {
            sharedSqliteConnection = DriverManager.getConnection(jdbcUrl);
            try (Statement st = sharedSqliteConnection.createStatement()) {
                st.execute("PRAGMA foreign_keys = ON;");
                st.execute("PRAGMA busy_timeout = 5000;");
            }
        }
        return sharedSqliteConnection;
    }

    public <T> CompletableFuture<T> supplyAsync(Callable<T> task) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return task.call();
            } catch (RuntimeException re) {
                throw re;
            } catch (Exception e) {
                throw new CompletionException(e);
            }
        }, asyncExecutor);
    }

    public CompletableFuture<Void> runAsync(Runnable task) {
        return CompletableFuture.runAsync(task, asyncExecutor);
    }

    public String storageType() { return storageType; }
    public PlayerRepository players() { return playerRepository; }
    public EconomyRepository economy() { return economyRepository; }
    public AuctionRepository auction() { return auctionRepository; }
    public ClanRepository clans() { return clanRepository; }
    public ProgressionRepository progression() { return progressionRepository; }

    @Override
    public String healthStatus() {
        if (!enabled) return "DISABLED";
        try {
            return query(conn -> conn.isValid(2) ? "OK (" + storageType + ")" : "DEGRADED");
        } catch (Exception e) {
            return "ERROR: " + e.getMessage();
        }
    }

    @Override
    public void disable() {
        this.enabled = false;
        if (asyncExecutor != null) {
            asyncExecutor.shutdown();
            try {
                asyncExecutor.awaitTermination(5, TimeUnit.SECONDS);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
        }
        if (sharedSqliteConnection != null) {
            sqliteWriteLock.lock();
            try {
                sharedSqliteConnection.close();
            } catch (SQLException e) {
                logger.warning("Error closing SQLite connection: " + e.getMessage());
            } finally {
                sqliteWriteLock.unlock();
            }
        }
    }
}
