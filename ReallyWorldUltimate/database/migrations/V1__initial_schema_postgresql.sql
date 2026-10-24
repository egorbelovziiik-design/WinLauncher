-- =======================================================================
-- ReallyWorld Ultimate — V1 Initial Schema (PostgreSQL 15+)
-- =======================================================================

CREATE TABLE IF NOT EXISTS schema_migrations (
    version INT PRIMARY KEY,
    description VARCHAR(255) NOT NULL,
    applied_at BIGINT NOT NULL
);

CREATE TABLE IF NOT EXISTS players (
    uuid VARCHAR(36) PRIMARY KEY,
    username VARCHAR(32) NOT NULL,
    rank_id VARCHAR(32) NOT NULL DEFAULT 'PLAYER',
    custom_prefix VARCHAR(128) DEFAULT NULL,
    nickname VARCHAR(64) DEFAULT NULL,
    coins BIGINT NOT NULL DEFAULT 0 CHECK (coins >= 0),
    rilliks BIGINT NOT NULL DEFAULT 0 CHECK (rilliks >= 0),
    tokens BIGINT NOT NULL DEFAULT 0 CHECK (tokens >= 0),
    kills INT NOT NULL DEFAULT 0,
    deaths INT NOT NULL DEFAULT 0,
    kill_streak INT NOT NULL DEFAULT 0,
    best_kill_streak INT NOT NULL DEFAULT 0,
    playtime_seconds BIGINT NOT NULL DEFAULT 0,
    blocks_mined BIGINT NOT NULL DEFAULT 0,
    boss_kills INT NOT NULL DEFAULT 0,
    events_won INT NOT NULL DEFAULT 0,
    quests_completed INT NOT NULL DEFAULT 0,
    cases_opened INT NOT NULL DEFAULT 0,
    grants_used INT NOT NULL DEFAULT 0,
    tp_toggle SMALLINT NOT NULL DEFAULT 1,
    scoreboard_enabled SMALLINT NOT NULL DEFAULT 1,
    pm_enabled SMALLINT NOT NULL DEFAULT 1,
    equipped_talisman_id VARCHAR(64) DEFAULT NULL,
    battlepass_season INT NOT NULL DEFAULT 1,
    battlepass_xp BIGINT NOT NULL DEFAULT 0,
    battlepass_premium SMALLINT NOT NULL DEFAULT 0,
    daily_streak INT NOT NULL DEFAULT 0,
    last_daily_day BIGINT NOT NULL DEFAULT 0,
    first_join_at BIGINT NOT NULL,
    last_seen_at BIGINT NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_players_username ON players (LOWER(username));
CREATE INDEX IF NOT EXISTS idx_players_coins ON players (coins DESC);
CREATE INDEX IF NOT EXISTS idx_players_rilliks ON players (rilliks DESC);
CREATE INDEX IF NOT EXISTS idx_players_kills ON players (kills DESC);
CREATE INDEX IF NOT EXISTS idx_players_playtime ON players (playtime_seconds DESC);

CREATE TABLE IF NOT EXISTS economy_transactions (
    id BIGSERIAL PRIMARY KEY,
    tx_uuid VARCHAR(36) NOT NULL UNIQUE,
    actor_uuid VARCHAR(36) DEFAULT NULL,
    target_uuid VARCHAR(36) DEFAULT NULL,
    currency VARCHAR(16) NOT NULL,
    operation VARCHAR(32) NOT NULL,
    amount BIGINT NOT NULL,
    balance_before BIGINT NOT NULL,
    balance_after BIGINT NOT NULL,
    reason VARCHAR(255) NOT NULL,
    created_at BIGINT NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_eco_tx_actor ON economy_transactions (actor_uuid, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_eco_tx_target ON economy_transactions (target_uuid, created_at DESC);

CREATE TABLE IF NOT EXISTS player_homes (
    player_uuid VARCHAR(36) NOT NULL REFERENCES players(uuid) ON DELETE CASCADE,
    home_name VARCHAR(32) NOT NULL,
    world_name VARCHAR(64) NOT NULL,
    x DOUBLE PRECISION NOT NULL,
    y DOUBLE PRECISION NOT NULL,
    z DOUBLE PRECISION NOT NULL,
    yaw REAL NOT NULL,
    pitch REAL NOT NULL,
    created_at BIGINT NOT NULL,
    PRIMARY KEY (player_uuid, home_name)
);

CREATE TABLE IF NOT EXISTS server_warps (
    warp_name VARCHAR(32) PRIMARY KEY,
    display_name VARCHAR(128) NOT NULL,
    category VARCHAR(32) NOT NULL DEFAULT 'SERVER',
    permission VARCHAR(64) DEFAULT NULL,
    world_name VARCHAR(64) NOT NULL,
    x DOUBLE PRECISION NOT NULL,
    y DOUBLE PRECISION NOT NULL,
    z DOUBLE PRECISION NOT NULL,
    yaw REAL NOT NULL,
    pitch REAL NOT NULL,
    created_by VARCHAR(64) NOT NULL,
    created_at BIGINT NOT NULL
);

CREATE TABLE IF NOT EXISTS player_cooldowns (
    player_uuid VARCHAR(36) NOT NULL,
    cooldown_key VARCHAR(96) NOT NULL,
    expires_at BIGINT NOT NULL,
    PRIMARY KEY (player_uuid, cooldown_key)
);

CREATE INDEX IF NOT EXISTS idx_cooldowns_expires ON player_cooldowns (expires_at);

CREATE TABLE IF NOT EXISTS player_ignores (
    player_uuid VARCHAR(36) NOT NULL,
    ignored_uuid VARCHAR(36) NOT NULL,
    created_at BIGINT NOT NULL,
    PRIMARY KEY (player_uuid, ignored_uuid)
);

CREATE TABLE IF NOT EXISTS auction_listings (
    id BIGSERIAL PRIMARY KEY,
    seller_uuid VARCHAR(36) NOT NULL,
    seller_name VARCHAR(32) NOT NULL,
    item_material VARCHAR(64) NOT NULL,
    item_display_name VARCHAR(255) NOT NULL,
    item_category VARCHAR(32) NOT NULL,
    item_amount INT NOT NULL,
    item_serialized TEXT NOT NULL,
    price BIGINT NOT NULL CHECK (price > 0),
    currency VARCHAR(16) NOT NULL DEFAULT 'COINS',
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    buyer_uuid VARCHAR(36) DEFAULT NULL,
    buyer_name VARCHAR(32) DEFAULT NULL,
    created_at BIGINT NOT NULL,
    expires_at BIGINT NOT NULL,
    resolved_at BIGINT DEFAULT NULL
);

CREATE INDEX IF NOT EXISTS idx_ah_status_expires ON auction_listings (status, expires_at);
CREATE INDEX IF NOT EXISTS idx_ah_seller ON auction_listings (seller_uuid, status);
CREATE INDEX IF NOT EXISTS idx_ah_category ON auction_listings (status, item_category, price);

CREATE TABLE IF NOT EXISTS clans (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(32) NOT NULL UNIQUE,
    tag VARCHAR(16) NOT NULL UNIQUE,
    description VARCHAR(255) NOT NULL DEFAULT 'Боевой клан ReallyWorld Ultimate',
    leader_uuid VARCHAR(36) NOT NULL,
    bank_coins BIGINT NOT NULL DEFAULT 0 CHECK (bank_coins >= 0),
    rating_points INT NOT NULL DEFAULT 0,
    kills INT NOT NULL DEFAULT 0,
    deaths INT NOT NULL DEFAULT 0,
    captures INT NOT NULL DEFAULT 0,
    pvp_friendly_fire SMALLINT NOT NULL DEFAULT 0,
    home_world VARCHAR(64) DEFAULT NULL,
    home_x DOUBLE PRECISION DEFAULT NULL,
    home_y DOUBLE PRECISION DEFAULT NULL,
    home_z DOUBLE PRECISION DEFAULT NULL,
    home_yaw REAL DEFAULT NULL,
    home_pitch REAL DEFAULT NULL,
    created_at BIGINT NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_clans_rating ON clans (rating_points DESC, kills DESC);

CREATE TABLE IF NOT EXISTS clan_members (
    player_uuid VARCHAR(36) PRIMARY KEY,
    player_name VARCHAR(32) NOT NULL,
    clan_id BIGINT NOT NULL REFERENCES clans(id) ON DELETE CASCADE,
    role VARCHAR(16) NOT NULL DEFAULT 'MEMBER',
    contributed_coins BIGINT NOT NULL DEFAULT 0,
    joined_at BIGINT NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_clan_members_clan ON clan_members (clan_id);

CREATE TABLE IF NOT EXISTS clan_relations (
    clan_id BIGINT NOT NULL REFERENCES clans(id) ON DELETE CASCADE,
    target_clan_id BIGINT NOT NULL REFERENCES clans(id) ON DELETE CASCADE,
    relation_type VARCHAR(16) NOT NULL,
    created_at BIGINT NOT NULL,
    PRIMARY KEY (clan_id, target_clan_id)
);

CREATE TABLE IF NOT EXISTS clan_progress (
    clan_id BIGINT NOT NULL REFERENCES clans(id) ON DELETE CASCADE,
    entry_key VARCHAR(96) NOT NULL,
    progress BIGINT NOT NULL DEFAULT 0,
    claimed SMALLINT NOT NULL DEFAULT 0,
    updated_at BIGINT NOT NULL,
    PRIMARY KEY (clan_id, entry_key)
);

CREATE TABLE IF NOT EXISTS player_case_keys (
    player_uuid VARCHAR(36) NOT NULL,
    case_id VARCHAR(48) NOT NULL,
    amount INT NOT NULL DEFAULT 0 CHECK (amount >= 0),
    PRIMARY KEY (player_uuid, case_id)
);

CREATE TABLE IF NOT EXISTS case_history (
    id BIGSERIAL PRIMARY KEY,
    open_uuid VARCHAR(36) NOT NULL UNIQUE,
    player_uuid VARCHAR(36) NOT NULL,
    player_name VARCHAR(32) NOT NULL,
    case_id VARCHAR(48) NOT NULL,
    reward_id VARCHAR(64) NOT NULL,
    reward_display VARCHAR(255) NOT NULL,
    opened_at BIGINT NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_case_history_player ON case_history (player_uuid, opened_at DESC);

CREATE TABLE IF NOT EXISTS player_quests (
    player_uuid VARCHAR(36) NOT NULL,
    quest_id VARCHAR(64) NOT NULL,
    period_key VARCHAR(32) NOT NULL,
    progress BIGINT NOT NULL DEFAULT 0,
    completed SMALLINT NOT NULL DEFAULT 0,
    claimed SMALLINT NOT NULL DEFAULT 0,
    updated_at BIGINT NOT NULL,
    PRIMARY KEY (player_uuid, quest_id, period_key)
);

CREATE TABLE IF NOT EXISTS player_claimed_rewards (
    player_uuid VARCHAR(36) NOT NULL,
    reward_key VARCHAR(96) NOT NULL,
    period_key VARCHAR(32) NOT NULL,
    claimed_at BIGINT NOT NULL,
    PRIMARY KEY (player_uuid, reward_key, period_key)
);

CREATE TABLE IF NOT EXISTS security_audit_log (
    id BIGSERIAL PRIMARY KEY,
    actor_uuid VARCHAR(36) DEFAULT NULL,
    actor_name VARCHAR(64) NOT NULL,
    action_type VARCHAR(64) NOT NULL,
    details TEXT NOT NULL,
    created_at BIGINT NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_security_audit_time ON security_audit_log (created_at DESC);
