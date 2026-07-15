-- PSDK Database Schema
-- This file is the single source of truth for all database tables.
-- The plugin reads this at startup and auto-migrates existing databases.

CREATE TABLE IF NOT EXISTS _meta (
    key   TEXT PRIMARY KEY,
    value TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS settings (
    key   TEXT PRIMARY KEY,
    value TEXT
);

CREATE TABLE IF NOT EXISTS crate_keys (
    player_uuid TEXT NOT NULL,
    crate_name  TEXT NOT NULL,
    saldo       INTEGER NOT NULL DEFAULT 0,
    PRIMARY KEY (player_uuid, crate_name)
);

CREATE TABLE IF NOT EXISTS crates (
    name                  TEXT PRIMARY KEY,
    tipo                  TEXT NOT NULL,
    visual                TEXT NOT NULL,
    custom_hologram_item  TEXT DEFAULT '',
    cor                   TEXT DEFAULT '<#fcc850>',
    titulo_menu           TEXT,
    limite_global         INTEGER DEFAULT -1,
    mundo                 TEXT DEFAULT '',
    loc_x                 REAL DEFAULT 0,
    loc_y                 REAL DEFAULT 0,
    loc_z                 REAL DEFAULT 0,
    block_display_uuid    TEXT DEFAULT '',
    interaction_uuid      TEXT DEFAULT '',
    key_material          TEXT DEFAULT 'TRIPWIRE_HOOK',
    key_display_name      TEXT DEFAULT '',
    key_lore              TEXT DEFAULT '',
    key_nbt_key           TEXT DEFAULT '',
    nexo_key_id           TEXT DEFAULT '',
    preco_token           REAL DEFAULT 100
);

CREATE TABLE IF NOT EXISTS crate_items (
    crate_name TEXT NOT NULL,
    slot       INTEGER NOT NULL,
    item_data  TEXT NOT NULL,
    PRIMARY KEY (crate_name, slot)
);

CREATE TABLE IF NOT EXISTS player_data (
    uuid               TEXT PRIMARY KEY NOT NULL,
    name               TEXT NOT NULL,
    level              INTEGER DEFAULT 1,
    xp                 INTEGER DEFAULT 0,
    kills              INTEGER DEFAULT 0,
    deaths             INTEGER DEFAULT 0,
    blocks_placed      INTEGER DEFAULT 0,
    blocks_broken      INTEGER DEFAULT 0,
    total_playtime_ms  INTEGER DEFAULT 0,
    last_updated       INTEGER DEFAULT 0
);

CREATE TABLE IF NOT EXISTS arena_meta (
    id     TEXT PRIMARY KEY DEFAULT 'main',
    world  TEXT,
    pos1_x INTEGER, pos1_y INTEGER, pos1_z INTEGER,
    pos2_x INTEGER, pos2_y INTEGER, pos2_z INTEGER
);

CREATE TABLE IF NOT EXISTS arena_blocks (
    x          INTEGER NOT NULL,
    y          INTEGER NOT NULL,
    z          INTEGER NOT NULL,
    block_data TEXT NOT NULL,
    PRIMARY KEY (x, y, z)
);

CREATE TABLE IF NOT EXISTS boss_arena_blocks (
    x          INTEGER NOT NULL,
    y          INTEGER NOT NULL,
    z          INTEGER NOT NULL,
    block_data TEXT NOT NULL,
    PRIMARY KEY (x, y, z)
);

CREATE TABLE IF NOT EXISTS player_economy (
    uuid   TEXT PRIMARY KEY NOT NULL,
    name   TEXT NOT NULL DEFAULT '',
    tokens REAL NOT NULL DEFAULT 0,
    coins  REAL NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS thepit_kit_loot (
    slot      INTEGER NOT NULL,
    idx       INTEGER NOT NULL,
    item_data TEXT NOT NULL,
    amount    INTEGER NOT NULL DEFAULT 1,
    chance    INTEGER NOT NULL DEFAULT 100,
    PRIMARY KEY (slot, idx)
);

CREATE TABLE IF NOT EXISTS regions (
    name           TEXT PRIMARY KEY,
    world          TEXT NOT NULL,
    x1 INTEGER, y1 INTEGER, z1 INTEGER,
    x2 INTEGER, y2 INTEGER, z2 INTEGER,
    priority       INTEGER DEFAULT 0,
    flags          TEXT DEFAULT '',
    entry_tp_world TEXT, entry_tp_x REAL, entry_tp_y REAL, entry_tp_z REAL, entry_tp_yaw REAL, entry_tp_pitch REAL,
    exit_tp_world  TEXT, exit_tp_x REAL, exit_tp_y REAL, exit_tp_z REAL, exit_tp_yaw REAL, exit_tp_pitch REAL
);

CREATE TABLE IF NOT EXISTS colina (
    id    TEXT PRIMARY KEY DEFAULT 'main',
    world TEXT,
    x     REAL,
    y     REAL,
    z     REAL,
    dono  TEXT DEFAULT 'Sem Dono'
);

CREATE TABLE IF NOT EXISTS player_settings (
    uuid    TEXT NOT NULL,
    setting TEXT NOT NULL,
    value   INTEGER NOT NULL DEFAULT 1,
    PRIMARY KEY (uuid, setting)
);

CREATE TABLE IF NOT EXISTS clans (
    id            INTEGER PRIMARY KEY AUTOINCREMENT,
    tag           TEXT NOT NULL UNIQUE,
    name          TEXT NOT NULL,
    leader        TEXT NOT NULL,
    color         TEXT NOT NULL DEFAULT '#FFFFFF',
    public        INTEGER NOT NULL DEFAULT 0,
    created       INTEGER NOT NULL DEFAULT 0,
    friendly_fire INTEGER NOT NULL DEFAULT 0,
    ally_ff       INTEGER NOT NULL DEFAULT 0,
    description   TEXT NOT NULL DEFAULT ''
);

CREATE TABLE IF NOT EXISTS clan_allies (
    clan_id       INTEGER NOT NULL,
    ally_clan_id  INTEGER NOT NULL,
    PRIMARY KEY (clan_id, ally_clan_id)
);

CREATE TABLE IF NOT EXISTS clan_rivals (
    clan_id        INTEGER NOT NULL,
    rival_clan_id  INTEGER NOT NULL,
    PRIMARY KEY (clan_id, rival_clan_id)
);

CREATE TABLE IF NOT EXISTS clan_requests (
    clan_id      INTEGER NOT NULL,
    player_uuid  TEXT NOT NULL,
    player_name  TEXT NOT NULL,
    requested_at INTEGER NOT NULL DEFAULT 0,
    PRIMARY KEY (clan_id, player_uuid)
);

CREATE TABLE IF NOT EXISTS clan_treasury (
    clan_id INTEGER PRIMARY KEY NOT NULL,
    coins   REAL NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS clan_roles (
    id            INTEGER PRIMARY KEY AUTOINCREMENT,
    clan_id       INTEGER NOT NULL,
    name          TEXT NOT NULL,
    position      INTEGER NOT NULL DEFAULT 0,
    perm_invite   INTEGER NOT NULL DEFAULT 0,
    perm_kick     INTEGER NOT NULL DEFAULT 0,
    perm_chest    INTEGER NOT NULL DEFAULT 1,
    perm_market   INTEGER NOT NULL DEFAULT 1,
    perm_pvp      INTEGER NOT NULL DEFAULT 0,
    perm_treasury INTEGER NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS clan_ally_requests (
    clan_id        INTEGER NOT NULL,
    target_clan_id INTEGER NOT NULL,
    requested_at   INTEGER NOT NULL DEFAULT 0,
    PRIMARY KEY (clan_id, target_clan_id)
);

CREATE TABLE IF NOT EXISTS clan_logs (
    id      INTEGER PRIMARY KEY AUTOINCREMENT,
    clan_id INTEGER NOT NULL,
    actor   TEXT NOT NULL DEFAULT '',
    action  TEXT NOT NULL,
    detail  TEXT NOT NULL DEFAULT '',
    at      INTEGER NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS clan_members (
    clan_id     INTEGER NOT NULL,
    player_uuid TEXT NOT NULL,
    player_name TEXT NOT NULL,
    role        TEXT NOT NULL DEFAULT 'membro',
    role_id     INTEGER NOT NULL DEFAULT 0,
    joined_at   INTEGER NOT NULL DEFAULT 0,
    PRIMARY KEY (clan_id, player_uuid)
);

CREATE TABLE IF NOT EXISTS clan_invites (
    clan_id     INTEGER NOT NULL,
    player_uuid TEXT NOT NULL,
    invited_at  INTEGER NOT NULL DEFAULT 0,
    PRIMARY KEY (clan_id, player_uuid)
);

CREATE TABLE IF NOT EXISTS clan_permissions (
    clan_id       INTEGER NOT NULL,
    player_uuid   TEXT NOT NULL,
    perm_invite   INTEGER NOT NULL DEFAULT 0,
    perm_kick     INTEGER NOT NULL DEFAULT 0,
    perm_chest    INTEGER NOT NULL DEFAULT 1,
    perm_market   INTEGER NOT NULL DEFAULT 1,
    perm_pvp      INTEGER NOT NULL DEFAULT 0,
    perm_treasury INTEGER NOT NULL DEFAULT 0,
    PRIMARY KEY (clan_id, player_uuid)
);

CREATE TABLE IF NOT EXISTS clan_chest (
    clan_id   INTEGER NOT NULL,
    page      INTEGER NOT NULL DEFAULT 0,
    inventory TEXT NOT NULL DEFAULT '',
    PRIMARY KEY (clan_id, page)
);

CREATE TABLE IF NOT EXISTS clan_market_items (
    id        INTEGER PRIMARY KEY AUTOINCREMENT,
    clan_id   INTEGER NOT NULL,
    seller    TEXT NOT NULL,
    item_data TEXT NOT NULL,
    price     REAL NOT NULL,
    listed_at INTEGER NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS clan_colors (
    name              TEXT PRIMARY KEY,
    color_hex         TEXT NOT NULL,
    display_name      TEXT NOT NULL,
    lore              TEXT DEFAULT '',
    key_material      TEXT DEFAULT 'TRIPWIRE_HOOK',
    key_display_name  TEXT DEFAULT 'Chave de Cor',
    key_lore          TEXT DEFAULT '',
    animation_enabled INTEGER DEFAULT 1,
    animation_style   TEXT DEFAULT 'PULSE'
);

CREATE TABLE IF NOT EXISTS clan_player_colors (
    player_uuid TEXT NOT NULL,
    color_name  TEXT NOT NULL,
    unlocked    INTEGER NOT NULL DEFAULT 0,
    PRIMARY KEY (player_uuid, color_name)
);

CREATE TABLE IF NOT EXISTS clan_color_items (
    color_name TEXT NOT NULL,
    slot       INTEGER NOT NULL,
    item_data  TEXT NOT NULL,
    PRIMARY KEY (color_name, slot)
);

CREATE TABLE IF NOT EXISTS clan_activated_colors (
    clan_id    INTEGER NOT NULL,
    color_name TEXT NOT NULL,
    activated_at INTEGER NOT NULL DEFAULT 0,
    PRIMARY KEY (clan_id, color_name)
);

CREATE TABLE IF NOT EXISTS clan_color_packets (
    id           INTEGER PRIMARY KEY AUTOINCREMENT,
    name         TEXT NOT NULL UNIQUE,
    display_name TEXT NOT NULL,
    material     TEXT NOT NULL DEFAULT 'FIREWORK_ROCKET',
    nexo_id      TEXT NOT NULL DEFAULT '',
    color_names  TEXT NOT NULL DEFAULT ''
);

CREATE TABLE IF NOT EXISTS player_ec_extended (
    player_uuid TEXT NOT NULL,
    slot        INTEGER NOT NULL,
    item_data   TEXT NOT NULL,
    PRIMARY KEY (player_uuid, slot)
);

CREATE TABLE IF NOT EXISTS marriages (
    uuid1       TEXT PRIMARY KEY NOT NULL,
    uuid2       TEXT NOT NULL,
    married_at  INTEGER NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS kit_cooldowns (
    player_uuid TEXT NOT NULL,
    kit_key     TEXT NOT NULL,
    expire_at   INTEGER NOT NULL,
    PRIMARY KEY (player_uuid, kit_key)
);

CREATE TABLE IF NOT EXISTS marriage_remarry_cooldown (
    player_uuid  TEXT PRIMARY KEY,
    available_at INTEGER NOT NULL
);

CREATE TABLE IF NOT EXISTS playtime_key_rewards (
    player_uuid    TEXT NOT NULL,
    crate_name     TEXT NOT NULL,
    accumulated_ms INTEGER NOT NULL DEFAULT 0,
    pending        INTEGER NOT NULL DEFAULT 0,
    PRIMARY KEY (player_uuid, crate_name)
);

CREATE TABLE IF NOT EXISTS bounties (
    target_uuid TEXT PRIMARY KEY NOT NULL,
    target_name TEXT NOT NULL,
    amount      REAL NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS player_period_stats (
    uuid                TEXT PRIMARY KEY,
    name                TEXT NOT NULL,
    weekly_kills        INTEGER DEFAULT 0,
    monthly_kills       INTEGER DEFAULT 0,
    weekly_coins        REAL DEFAULT 0,
    monthly_coins       REAL DEFAULT 0,
    weekly_playtime_ms  INTEGER DEFAULT 0,
    monthly_playtime_ms INTEGER DEFAULT 0
);

CREATE TABLE IF NOT EXISTS top_boards (
    id                 TEXT PRIMARY KEY,
    type               TEXT NOT NULL,
    world              TEXT NOT NULL,
    x                  REAL NOT NULL,
    y                  REAL NOT NULL,
    z                  REAL NOT NULL,
    display_uuid       TEXT DEFAULT '',
    interaction_uuid   TEXT DEFAULT '',
    period             TEXT DEFAULT 'weekly',
    page               INTEGER DEFAULT 0
);

-- Blocos temporários das Jaulas (item especial). Guarda o estado ORIGINAL de cada
-- bloco substituído por vidro vermelho, para restauração em reload/crash/desligamento.
-- Uma queda do servidor não pode deixar vidros abandonados no mapa.
CREATE TABLE IF NOT EXISTS cage_blocks (
    cage_id    TEXT NOT NULL,
    world      TEXT NOT NULL,
    x          INTEGER NOT NULL,
    y          INTEGER NOT NULL,
    z          INTEGER NOT NULL,
    block_data TEXT NOT NULL,
    PRIMARY KEY (world, x, y, z)
);

-- Cooldowns de habilidades/itens especiais (Ender Pearl, Jaula, Troque de Posição, TNT...).
-- Persistido para que relogar NÃO burle o cooldown. Guarda o instante de expiração
-- (epoch ms). Entradas expiradas são descartadas ao carregar.
CREATE TABLE IF NOT EXISTS ability_cooldowns (
    uuid      TEXT NOT NULL,
    ability   TEXT NOT NULL,
    expire_at INTEGER NOT NULL,
    PRIMARY KEY (uuid, ability)
);

-- Localização de reconexão do Skill Pit: onde o jogador estava ao sair SEM Combat Log.
-- Usada para restaurar a posição exata na arena de PvP (ou mandar ao spawn se área segura).
CREATE TABLE IF NOT EXISTS pit_reconnect (
    uuid   TEXT PRIMARY KEY NOT NULL,
    world  TEXT NOT NULL,
    x      REAL NOT NULL,
    y      REAL NOT NULL,
    z      REAL NOT NULL,
    yaw    REAL NOT NULL,
    pitch  REAL NOT NULL,
    in_pvp INTEGER NOT NULL DEFAULT 0
);

-- Sessões de restore de inventário (uma por boot/crash; várias podem coexistir).
CREATE TABLE IF NOT EXISTS combat_restore_sessions (
    session_id   TEXT PRIMARY KEY NOT NULL,
    created_at   INTEGER NOT NULL DEFAULT 0,
    reason       TEXT NOT NULL DEFAULT 'uptime'
);

-- Backup de inventário por sessão + jogador (várias sessões pendentes ao mesmo tempo).
CREATE TABLE IF NOT EXISTS player_combat_inventory_backups (
    session_id      TEXT NOT NULL,
    player_uuid     TEXT NOT NULL,
    name            TEXT NOT NULL,
    inventory_data  TEXT NOT NULL,
    saved_at        INTEGER NOT NULL DEFAULT 0,
    in_combat       INTEGER NOT NULL DEFAULT 1,
    PRIMARY KEY (session_id, player_uuid)
);
