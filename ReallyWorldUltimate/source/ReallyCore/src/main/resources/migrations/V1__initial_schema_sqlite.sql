-- =======================================================================
-- ReallyWorld Ultimate — V1 Initial Schema (SQLite 3 / WAL Mode)
-- Все денежные суммы хранятся в ЦЕЛОЧИСЛЕННОМ типе INTEGER (64-bit signed long),
-- использование FLOAT/DOUBLE для финансов СТРОГО ЗАПРЕЩЕНО.
-- =======================================================================

PRAGMA foreign_keys = ON;

CREATE TABLE IF NOT EXISTS schema_migrations (
    version INTEGER PRIMARY KEY,
    description TEXT NOT NULL,
    applied_at INTEGER NOT NULL
);

-- Профили игроков, балансы 3 валют и базовая статистика
CREATE TABLE IF NOT EXISTS players (
    uuid TEXT PRIMARY KEY,
    username TEXT NOT NULL,
    rank_id TEXT NOT NULL DEFAULT 'PLAYER',
    custom_prefix TEXT DEFAULT NULL,
    nickname TEXT DEFAULT NULL,
    coins INTEGER NOT NULL DEFAULT 0 CHECK (coins >= 0),
    rilliks INTEGER NOT NULL DEFAULT 0 CHECK (rilliks >= 0),
    tokens INTEGER NOT NULL DEFAULT 0 CHECK (tokens >= 0),
    kills INTEGER NOT NULL DEFAULT 0,
    deaths INTEGER NOT NULL DEFAULT 0,
    kill_streak INTEGER NOT NULL DEFAULT 0,
    best_kill_streak INTEGER NOT NULL DEFAULT 0,
    playtime_seconds INTEGER NOT NULL DEFAULT 0,
    blocks_mined INTEGER NOT NULL DEFAULT 0,
    boss_kills INTEGER NOT NULL DEFAULT 0,
    events_won INTEGER NOT NULL DEFAULT 0,
    quests_completed INTEGER NOT NULL DEFAULT 0,
    cases_opened INTEGER NOT NULL DEFAULT 0,
    grants_used INTEGER NOT NULL DEFAULT 0,
    tp_toggle INTEGER NOT NULL DEFAULT 1,
    scoreboard_enabled INTEGER NOT NULL DEFAULT 1,
    pm_enabled INTEGER NOT NULL DEFAULT 1,
    equipped_talisman_id TEXT DEFAULT NULL,
    battlepass_season INTEGER NOT NULL DEFAULT 1,
    battlepass_xp INTEGER NOT NULL DEFAULT 0,
    battlepass_premium INTEGER NOT NULL DEFAULT 0,
    daily_streak INTEGER NOT NULL DEFAULT 0,
    last_daily_day INTEGER NOT NULL DEFAULT 0,
    first_join_at INTEGER NOT NULL,
    last_seen_at INTEGER NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_players_username ON players(LOWER(username));
CREATE INDEX IF NOT EXISTS idx_players_coins ON players(coins DESC);
CREATE INDEX IF NOT EXISTS idx_players_rilliks ON players(rilliks DESC);
CREATE INDEX IF NOT EXISTS idx_players_kills ON players(kills DESC);
CREATE INDEX IF NOT EXISTS idx_players_playtime ON players(playtime_seconds DESC);

-- Неизменяемый журнал всех финансовых транзакций (аудит и антидюп)
CREATE TABLE IF NOT EXISTS economy_transactions (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    tx_uuid TEXT NOT NULL UNIQUE,
    actor_uuid TEXT,
    target_uuid TEXT,
    currency TEXT NOT NULL,
    operation TEXT NOT NULL,
    amount INTEGER NOT NULL,
    balance_before INTEGER NOT NULL,
    balance_after INTEGER NOT NULL,
    reason TEXT NOT NULL,
    created_at INTEGER NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_eco_tx_actor ON economy_transactions(actor_uuid, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_eco_tx_target ON economy_transactions(target_uuid, created_at DESC);

-- Точки домов игроков (/sethome, /home, /delhome, /homes)
CREATE TABLE IF NOT EXISTS player_homes (
    player_uuid TEXT NOT NULL,
    home_name TEXT NOT NULL,
    world_name TEXT NOT NULL,
    x REAL NOT NULL,
    y REAL NOT NULL,
    z REAL NOT NULL,
    yaw REAL NOT NULL,
    pitch REAL NOT NULL,
    created_at INTEGER NOT NULL,
    PRIMARY KEY (player_uuid, home_name),
    FOREIGN KEY (player_uuid) REFERENCES players(uuid) ON DELETE CASCADE
);

-- Серверные варпы (/warp, /warps)
CREATE TABLE IF NOT EXISTS server_warps (
    warp_name TEXT PRIMARY KEY,
    display_name TEXT NOT NULL,
    category TEXT NOT NULL DEFAULT 'SERVER',
    permission TEXT DEFAULT NULL,
    world_name TEXT NOT NULL,
    x REAL NOT NULL,
    y REAL NOT NULL,
    z REAL NOT NULL,
    yaw REAL NOT NULL,
    pitch REAL NOT NULL,
    created_by TEXT NOT NULL,
    created_at INTEGER NOT NULL
);

-- Персистентные кулдауны (чтобы перезаход не сбрасывал КД на киты, донат-команды и артефакты)
CREATE TABLE IF NOT EXISTS player_cooldowns (
    player_uuid TEXT NOT NULL,
    cooldown_key TEXT NOT NULL,
    expires_at INTEGER NOT NULL,
    PRIMARY KEY (player_uuid, cooldown_key)
);

CREATE INDEX IF NOT EXISTS idx_cooldowns_expires ON player_cooldowns(expires_at);

-- Игнор-лист (/ignore)
CREATE TABLE IF NOT EXISTS player_ignores (
    player_uuid TEXT NOT NULL,
    ignored_uuid TEXT NOT NULL,
    created_at INTEGER NOT NULL,
    PRIMARY KEY (player_uuid, ignored_uuid)
);

-- Аукцион (/ah): активные, проданные, истёкшие и возвращённые лоты
CREATE TABLE IF NOT EXISTS auction_listings (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    seller_uuid TEXT NOT NULL,
    seller_name TEXT NOT NULL,
    item_material TEXT NOT NULL,
    item_display_name TEXT NOT NULL,
    item_category TEXT NOT NULL,
    item_amount INTEGER NOT NULL,
    item_serialized TEXT NOT NULL,
    price INTEGER NOT NULL CHECK (price > 0),
    currency TEXT NOT NULL DEFAULT 'COINS',
    status TEXT NOT NULL DEFAULT 'ACTIVE', -- ACTIVE, SOLD, EXPIRED, RETURNED
    buyer_uuid TEXT DEFAULT NULL,
    buyer_name TEXT DEFAULT NULL,
    created_at INTEGER NOT NULL,
    expires_at INTEGER NOT NULL,
    resolved_at INTEGER DEFAULT NULL
);

CREATE INDEX IF NOT EXISTS idx_ah_status_expires ON auction_listings(status, expires_at);
CREATE INDEX IF NOT EXISTS idx_ah_seller ON auction_listings(seller_uuid, status);
CREATE INDEX IF NOT EXISTS idx_ah_category ON auction_listings(status, item_category, price);

-- Кланы (/clan)
CREATE TABLE IF NOT EXISTS clans (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    name TEXT NOT NULL UNIQUE,
    tag TEXT NOT NULL UNIQUE,
    description TEXT NOT NULL DEFAULT 'Боевой клан ReallyWorld Ultimate',
    leader_uuid TEXT NOT NULL,
    bank_coins INTEGER NOT NULL DEFAULT 0 CHECK (bank_coins >= 0),
    rating_points INTEGER NOT NULL DEFAULT 0,
    kills INTEGER NOT NULL DEFAULT 0,
    deaths INTEGER NOT NULL DEFAULT 0,
    captures INTEGER NOT NULL DEFAULT 0,
    pvp_friendly_fire INTEGER NOT NULL DEFAULT 0,
    home_world TEXT DEFAULT NULL,
    home_x REAL DEFAULT NULL,
    home_y REAL DEFAULT NULL,
    home_z REAL DEFAULT NULL,
    home_yaw REAL DEFAULT NULL,
    home_pitch REAL DEFAULT NULL,
    created_at INTEGER NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_clans_rating ON clans(rating_points DESC, kills DESC);

CREATE TABLE IF NOT EXISTS clan_members (
    player_uuid TEXT PRIMARY KEY,
    player_name TEXT NOT NULL,
    clan_id INTEGER NOT NULL,
    role TEXT NOT NULL DEFAULT 'MEMBER', -- LEADER, COLEADER, ELDER, MEMBER
    contributed_coins INTEGER NOT NULL DEFAULT 0,
    joined_at INTEGER NOT NULL,
    FOREIGN KEY (clan_id) REFERENCES clans(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_clan_members_clan ON clan_members(clan_id);

CREATE TABLE IF NOT EXISTS clan_relations (
    clan_id INTEGER NOT NULL,
    target_clan_id INTEGER NOT NULL,
    relation_type TEXT NOT NULL, -- ALLY, ENEMY
    created_at INTEGER NOT NULL,
    PRIMARY KEY (clan_id, target_clan_id),
    FOREIGN KEY (clan_id) REFERENCES clans(id) ON DELETE CASCADE,
    FOREIGN KEY (target_clan_id) REFERENCES clans(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS clan_progress (
    clan_id INTEGER NOT NULL,
    entry_key TEXT NOT NULL, -- quest:<id> or achievement:<id>
    progress INTEGER NOT NULL DEFAULT 0,
    claimed INTEGER NOT NULL DEFAULT 0,
    updated_at INTEGER NOT NULL,
    PRIMARY KEY (clan_id, entry_key),
    FOREIGN KEY (clan_id) REFERENCES clans(id) ON DELETE CASCADE
);

-- Ключи от кейсов и история открытий
CREATE TABLE IF NOT EXISTS player_case_keys (
    player_uuid TEXT NOT NULL,
    case_id TEXT NOT NULL,
    amount INTEGER NOT NULL DEFAULT 0 CHECK (amount >= 0),
    PRIMARY KEY (player_uuid, case_id)
);

CREATE TABLE IF NOT EXISTS case_history (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    open_uuid TEXT NOT NULL UNIQUE,
    player_uuid TEXT NOT NULL,
    player_name TEXT NOT NULL,
    case_id TEXT NOT NULL,
    reward_id TEXT NOT NULL,
    reward_display TEXT NOT NULL,
    opened_at INTEGER NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_case_history_player ON case_history(player_uuid, opened_at DESC);

-- Прогресс квестов игроков (/quests)
CREATE TABLE IF NOT EXISTS player_quests (
    player_uuid TEXT NOT NULL,
    quest_id TEXT NOT NULL,
    period_key TEXT NOT NULL, -- PERMANENT, YYYY-MM-DD (daily), YYYY-WW (weekly)
    progress INTEGER NOT NULL DEFAULT 0,
    completed INTEGER NOT NULL DEFAULT 0,
    claimed INTEGER NOT NULL DEFAULT 0,
    updated_at INTEGER NOT NULL,
    PRIMARY KEY (player_uuid, quest_id, period_key)
);

-- Полученные награды, достижения и уровни Боевого Пропуска (защита от повторной выдачи!)
CREATE TABLE IF NOT EXISTS player_claimed_rewards (
    player_uuid TEXT NOT NULL,
    reward_key TEXT NOT NULL,
    period_key TEXT NOT NULL,
    claimed_at INTEGER NOT NULL,
    PRIMARY KEY (player_uuid, reward_key, period_key)
);

-- Журнал безопасности и административных действий
CREATE TABLE IF NOT EXISTS security_audit_log (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    actor_uuid TEXT,
    actor_name TEXT NOT NULL,
    action_type TEXT NOT NULL,
    details TEXT NOT NULL,
    created_at INTEGER NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_security_audit_time ON security_audit_log(created_at DESC);
