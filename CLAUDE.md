# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

**PSDK** (Paper Server Development Kit) is a monolithic PaperMC plugin for a Minecraft 1.21.4 server. It is written in Java 21 and packages multiple game-server systems into a single jar deployed to a Paper server.

## Build

Two build systems exist. Gradle is preferred (used by `build.sh`):

```bash
./gradlew shadowJar        # preferred
# or
mvn clean package          # alternative
```

Gradle output: `build/libs/psdk-1.0.0.jar`  
Maven output: `target/psdk-1.0.0.jar`

Both shade `sqlite-jdbc` under the `com.psdk.libs.sqlite` relocation. No tests exist.

## Architecture

All systems are wired together in [PSDK.java](src/main/java/com/psdk/PSDK.java) (the `JavaPlugin` main class). Managers are instantiated in `onEnable()` and injected by constructor into commands and listeners. There is no DI framework — every class receives `PSDK plugin` and calls `plugin.getXxxManager()` to cross-reference other subsystems.

### Subsystems

Organização por feature — cada pacote contém o manager, comandos, listeners, GUI e modelos do seu sistema:

| Pacote | Conteúdo |
|---|---|
| `database/` | `DatabaseManager` (conexão SQLite, WAL) + `SchemaManager` (auto-migração via `schema.sql`). |
| `economy/` | `EconomyManager` (reais + coins, top-caches async) + `CoinsCommand`, `ReaisCommand`, `EcoCommand`. |
| `crates/` | `CrateManager`, `KeyManager`, `HologramManager`, `Crate`, `CrateKey`, GUI, listeners e comandos do sistema de caixas. Inclui `CratesExpansion` (PlaceholderAPI). |
| `thepit/` | Tudo do The Pit: `ArenaManager`, `PlayerDataManager`, `LevelManager`, `KitManager`, `KitLootManager`, `CombatManager`, modelos `ArenaData`/`PlayerData`, listeners de combate/arena/minas, comandos (`/arena`, `/thepit`, `/stats`, `/kills`, `/spawn`). Inclui `ThePitExpansion` (PlaceholderAPI). |
| `region/` | `RegionManager`, `Region`, `RegionFlag`, wand listener, proteção e `RegionCommand`. |
| `shop/` | `ShopManager`, `ShopGUI`, `ShopGUIListener` (mantido no plugin por ser referenciado por comandos) e comandos da loja. |
| `colina/` | `ColinaManager` e seus três comandos. |
| `pitems/` | `PSDKItems` (itens especiais temporizados), `PSDKItemExpireTask`, listener e `PItemCommand`. |
| `util/` | `ItemSerializer` (Base64 para ItemStack), `SkullUtils`, `WandUtils` (estado de wand por jogador). |

### Key data flow patterns

- **All persistence is SQLite, no YAML config files for data.** The `settings` table stores arbitrary key-value server config (e.g. `crates_spawn` location).
- **Item serialization** uses Base64-encoded `BukkitObjectOutputStream` (`ItemSerializer`) to store `ItemStack` objects in the DB as `item_data TEXT` columns.
- **Economy operations are synchronous** (direct JDBC on the main thread). Top-cache refresh is the only async DB work.
- **Wand tools** (arena wand, region wand) store per-player state in `WandUtils` and dispatch to the correct listener based on NBT data on the item.
- **Player-facing text uses MiniMessage** (Adventure API) formatting strings like `<#color>text` and `<bold>`.
- **All in-game text is in Brazilian Portuguese** — keep that language for messages, log output, and command feedback.

### Schema migrations

`src/main/resources/schema.sql` is the **single source of truth** for the database schema. On startup, `SchemaManager` compares it against the live DB and:
- Creates missing tables.
- Adds missing columns (`ALTER TABLE … ADD COLUMN`).
- Skips type changes (SQLite limitation) with a warning.

Migration is gated by the plugin version in `plugin.yml`. Incrementing the version triggers a fresh migration pass. The current schema version is stored in the `_meta` table.

### Soft dependencies

- **Vault** — for external economy bridge (`Economy` interface). Optional; checked at runtime.
- **PlaceholderAPI** — for `%psdk_*%` placeholders. Optional; checked at runtime.
- **LuckPerms** — accessed via `Bukkit.getServicesManager()` in `ColinaManager` for prefix/suffix resolution. Not declared in `plugin.yml` `softdepend`; fails gracefully if absent.
- **Nexo** — listed in `softdepend` but not yet integrated in code.

## Adding a new command

1. Create the class inside the package do seu sistema (ex: `com.psdk.shop.MeuCommand`), implement `CommandExecutor` (e `TabCompleter` se necessário).
2. Adicionar import explícito em `PSDK.java` e registrar em `PSDK#registerCommands()` via `registerCmd("name", new MeuCommand(this))`.
3. Declarar o comando em `src/main/resources/plugin.yml`.

## Adding a new system

1. Criar um novo pacote `com.psdk.<sistema>/`.
2. Adicionar tabelas/colunas em `src/main/resources/schema.sql` (o `SchemaManager` cuida de todo DDL — não criar tabelas manualmente no construtor do manager).
3. Criar o manager com `PSDK plugin` no construtor.
4. Instanciar em `PSDK#onEnable()`, adicionar getter e registrar listeners em `PSDK#registerListeners()`.
5. Bump de versão em `plugin.yml` para a migração rodar no próximo start.
