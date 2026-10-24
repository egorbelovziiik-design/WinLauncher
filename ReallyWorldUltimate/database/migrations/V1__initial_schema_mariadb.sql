-- =======================================================================
-- ReallyWorld Ultimate — V1 Initial Schema (MariaDB 10.11+ / MySQL 8.0+)
-- Для крупных высоконагруженных серверов и мультисерверных связок
-- =======================================================================

CREATE TABLE IF NOT EXISTS schema_migrations (
    version INT PRIMARY KEY,
    description VARCHAR(255) NOT NULL,
    applied_at BIGINT NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS players (
    uuid CHAR(36) PRIMARY KEY,
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
    tp_toggle TINYINT(1) NOT NULL DEFAULT 1,
    scoreboard_enabled TINYINT(1) NOT NULL DEFAULT 1,
    pm_enabled TINYINT(1) NOT NULL DEFAULT 1,
    equipped_talisman_id VARCHAR(64) DEFAULT NULL,
    battlepass_season INT NOT NULL DEFAULT 1,
    battlepass_xp BIGINT NOT NULL DEFAULT 0,
    battlepass_premium TINYINT(1) NOT NULL DEFAULT 0,
    daily_streak INT NOT NULL DEFAULT 0,
    last_daily_day BIGINT NOT NULL DEFAULT 0,
    first_join_at BIGINT NOT NULL,
    last_seen_at BIGINT NOT NULL,
    INDEX idx_players_username (username),
    INDEX idx_players_coins (coins DESC),
    INDEX idx_players_rilliks (rilliks DESC),
    INDEX idx_players_kills (kills DESC),
    INDEX idx_players_playtime (playtime_seconds DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS economy_transactions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tx_uuid CHAR(36) NOT NULL UNIQUE,
    actor_uuid CHAR(36) DEFAULT NULL,
    target_uuid CHAR(36) DEFAULT NULL,
    currency VARCHAR(16) NOT NULL,
    operation VARCHAR(32) NOT NULL,
    amount BIGINT NOT NULL,
    balance_before BIGINT NOT NULL,
    balance_after BIGINT NOT NULL,
    reason VARCHAR(255) NOT NULL,
    created_at BIGINT NOT NULL,
    INDEX idx_eco_tx_actor (actor_uuid, created_at DESC),
    INDEX idx_eco_tx_target (target_uuid, created_at DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS player_homes (
    player_uuid CHAR(36) NOT NULL,
    home_name VARCHAR(32) NOT NULL,
    world_name VARCHAR(64) NOT NULL,
    x DOUBLE NOT NULL,
    y DOUBLE NOT NULL,
    z DOUBLE NOT NULL,
    yaw FLOAT NOT NULL,
    pitch FLOAT NOT NULL,
    created_at BIGINT NOT NULL,
    PRIMARY KEY (player_uuid, home_name),
    CONSTRAINT fk_homes_player FOREIGN KEY (player_uuid) REFERENCES players(uuid) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS server_warps (
    warp_name VARCHAR(32) PRIMARY KEY,
    display_name VARCHAR(128) NOT NULL,
    category VARCHAR(32) NOT NULL DEFAULT 'SERVER',
    permission VARCHAR(64) DEFAULT NULL,
    world_name VARCHAR(64) NOT NULL,
    x DOUBLE NOT NULL,
    y DOUBLE NOT NULL,
    z DOUBLE NOT NULL,
    yaw FLOAT NOT NULL,
    pitch FLOAT NOT NULL,
    created_by VARCHAR(64) NOT NULL,
    created_at BIGINT NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS player_cooldowns (
    player_uuid CHAR(36) NOT NULL,
    cooldown_key VARCHAR(96) NOT NULL,
    expires_at BIGINT NOT NULL,
    PRIMARY KEY (player_uuid, cooldown_key),
    INDEX idx_cooldowns_expires (expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS player_ignores (
    player_uuid CHAR(36) NOT NULL,
    ignored_uuid CHAR(36) NOT NULL,
    created_at BIGINT NOT NULL,
    PRIMARY KEY (player_uuid, ignored_uuid)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS auction_listings (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    seller_uuid CHAR(36) NOT NULL,
    seller_name VARCHAR(32) NOT NULL,
    item_material VARCHAR(64) NOT NULL,
    item_display_name VARCHAR(255) NOT NULL,
    item_category VARCHAR(32) NOT NULL,
    item_amount INT NOT NULL,
    item_serialized MEDIUMTEXT NOT NULL,
    price BIGINT NOT NULL CHECK (price > 0),
    currency VARCHAR(16) NOT NULL DEFAULT 'COINS',
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    buyer_uuid CHAR(36) DEFAULT NULL,
    buyer_name VARCHAR(32) DEFAULT NULL,
    created_at BIGINT NOT NULL,
    expires_at BIGINT NOT NULL,
    resolved_at BIGINT DEFAULT NULL,
    INDEX idx_ah_status_expires (status, expires_at),
    INDEX idx_ah_seller (seller_uuid, status),
    INDEX idx_ah_category (status, item_category, price)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS clans (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(32) NOT NULL UNIQUE,
    tag VARCHAR(16) NOT NULL UNIQUE,
    description VARCHAR(255) NOT NULL DEFAULT 'Боевой клан ReallyWorld Ultimate',
    leader_uuid CHAR(36) NOT NULL,
    bank_coins BIGINT NOT NULL DEFAULT 0 CHECK (bank_coins >= 0),
    rating_points INT NOT NULL DEFAULT 0,
    kills INT NOT NULL DEFAULT 0,
    deaths INT NOT NULL DEFAULT 0,
    captures INT NOT NULL DEFAULT 0,
    pvp_friendly_fire TINYINT(1) NOT NULL DEFAULT 0,
    home_world VARCHAR(64) DEFAULT NULL,
    home_x DOUBLE DEFAULT NULL,
    home_y DOUBLE DEFAULT NULL,
    home_z DOUBLE DEFAULT NULL,
    home_yaw FLOAT DEFAULT NULL,
    home_pitch FLOAT DEFAULT NULL,
    created_at BIGINT NOT NULL,
    INDEX idx_clans_rating (rating_points DESC, kills DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS clan_members (
    player_uuid CHAR(36) PRIMARY KEY,
    player_name VARCHAR(32) NOT NULL,
    clan_id BIGINT NOT NULL,
    role VARCHAR(16) NOT NULL DEFAULT 'MEMBER',
    contributed_coins BIGINT NOT NULL DEFAULT 0,
    joined_at BIGINT NOT NULL,
    INDEX idx_clan_members_clan (clan_id),
    CONSTRAINT fk_cm_clan FOREIGN KEY (clan_id) REFERENCES clans(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS clan_relations (
    clan_id BIGINT NOT NULL,
    target_clan_id BIGINT NOT NULL,
    relation_type VARCHAR(16) NOT NULL,
    created_at BIGINT NOT NULL,
    PRIMARY KEY (clan_id, target_clan_id),
    CONSTRAINT fk_cr_clan FOREIGN KEY (clan_id) REFERENCES clans(id) ON DELETE CASCADE,
    CONSTRAINT fk_cr_target FOREIGN KEY (target_clan_id) REFERENCES clans(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS clan_progress (
    clan_id BIGINT NOT NULL,
    entry_key VARCHAR(96) NOT NULL,
    progress BIGINT NOT NULL DEFAULT 0,
    claimed TINYINT(1) NOT NULL DEFAULT 0,
    updated_at BIGINT NOT NULL,
    PRIMARY KEY (clan_id, entry_key),
    CONSTRAINT fk_cp_clan FOREIGN KEY (clan_id) REFERENCES clans(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS player_case_keys (
    player_uuid CHAR(36) NOT NULL,
    case_id VARCHAR(48) NOT NULL,
    amount INT NOT NULL DEFAULT 0 CHECK (amount >= 0),
    PRIMARY KEY (player_uuid, case_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS case_history (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    open_uuid CHAR(36) NOT NULL UNIQUE,
    player_uuid CHAR(36) NOT NULL,
    player_name VARCHAR(32) NOT NULL,
    case_id VARCHAR(48) NOT NULL,
    reward_id VARCHAR(64) NOT NULL,
    reward_display VARCHAR(255) NOT NULL,
    opened_at BIGINT NOT NULL,
    INDEX idx_case_history_player (player_uuid, opened_at DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS player_quests (
    player_uuid CHAR(36) NOT NULL,
    quest_id VARCHAR(64) NOT NULL,
    period_key VARCHAR(32) NOT NULL,
    progress BIGINT NOT NULL DEFAULT 0,
    completed TINYINT(1) NOT NULL DEFAULT 0,
    claimed TINYINT(1) NOT NULL DEFAULT 0,
    updated_at BIGINT NOT NULL,
    PRIMARY KEY (player_uuid, quest_id, period_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS player_claimed_rewards (
    player_uuid CHAR(36) NOT NULL,
    reward_key VARCHAR(96) NOT NULL,
    period_key VARCHAR(32) NOT NULL,
    claimed_at BIGINT NOT NULL,
    PRIMARY KEY (player_uuid, reward_key, period_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS security_audit_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    actor_uuid CHAR(36) DEFAULT NULL,
    actor_name VARCHAR(64) NOT NULL,
    action_type VARCHAR(64) NOT NULL,
    details TEXT NOT NULL,
    created_at BIGINT NOT NULL,
    INDEX idx_security_audit_time (created_at DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
