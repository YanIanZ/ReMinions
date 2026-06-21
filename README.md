<div align="center">

# ⚙️ ReMinions

**High-performance Paper minion plugin for Minecraft 1.20.0 – 1.22+**

[![License](https://img.shields.io/badge/License-PolyForm%20Perimeter-blue?style=flat-square)](LICENSE)
[![Java](https://img.shields.io/badge/Java-21-orange?style=flat-square&logo=openjdk)](https://adoptium.net)
[![Paper](https://img.shields.io/badge/Paper-1.20%20%E2%80%93%2026.2+-white?style=flat-square)](https://papermc.io/downloads/paper)
[![Version](https://img.shields.io/badge/Version-1.0.0-green?style=flat-square)](https://github.com/YanIanZ/ReMinions/releases)

</div>

---

## Features

- **5 minion categories** — Farming, Mining, Combat, Lumberjack, Fisherman (155 bundled)
- **Modifier system** — 54 modifiers including Compactor (24 vanilla recipes), Auto-Sell (price boost up to 1.5×), Lucky Clover tiers, Speed Amplifier tiers, Radius Expander tiers, XP Booster ladder, Storage tiers
- **Live fuel duration** — fuel items carry an absolute expiry timestamp; the lore counts down in real time even while the item sits in a player's inventory. Infinite-duration FUEL modifiers are rejected at config load
- **Skin system** — per-minion cosmetic skins with level progression
- **Multi-database** — SQLite · MySQL · Redis · MongoDB
- **PlaceholderAPI** — `%reminions_<placeholder>%` — full list below
- **Public API** — `BoboAPI` (see [`BoboAPI`](plugin/src/main/java/dev/yanianz/reminions/api/BoboAPI.java))
- **Soft integrations** — Vault · LuckPerms · AuraSkills · EcoSkills · MMOItems · ItemsAdder · CraftEngine · SuperiorSkyblock2 · SourbyCraftSWM · ShopGUIPlus · EconomyShopGUI · Essentials

---

## Requirements

| Requirement | Version |
|-------------|---------|
| Paper | 1.20.x · 1.21.0 – 1.21.11 · **26.1.x / 26.2.x** (latest tested) |
| Minecraft | 1.20.0 – 1.22.x |
| Java | 21+ |

### Bundled NMS adapter groups

The shaded jar contains a per-bucket adapter probed at boot. Order is newest-first; first one that links wins.

| Group | Minecraft range | Implementation |
|-------|-----------------|----------------|
| `v26_1_2`   | 1.22.x (Paper 26.1–26.2+) | Real NMS · paperweight |
| `v1_21_11`  | 1.21.11 | Real NMS · paperweight |
| `v1_21_8`   | 1.21.8 – 1.21.10 | Real NMS · paperweight (compiled against 1.21.4 bundle, SlotDisplay API stable since 1.21.2) |
| `v1_21_6`   | 1.21.6 – 1.21.7 | Real NMS · paperweight (compiled against 1.21.4 bundle) |
| `v1_21_5`   | 1.21.5 | Real NMS · paperweight |
| `v1_21_4`   | 1.21.4 | Real NMS · paperweight |
| `v1_21_2`   | 1.21.2 – 1.21.3 | Real NMS · paperweight |
| `v1_21`     | 1.21.0 – 1.21.1 | Real NMS · paperweight · legacy recipe-id ghost packet |
| `v1_20_5`   | 1.20.5 – 1.20.6 | Real NMS · paperweight · legacy recipe-id ghost packet |
| `v1_20_3`   | 1.20.3 – 1.20.4 | Real NMS · paperweight `reobfJar` (Spigot-mapped runtime · `v1_20_R3`) |
| `v1_20_2`   | 1.20.2 | Real NMS · paperweight `reobfJar` (Spigot-mapped runtime · `v1_20_R2`) |
| `v1_20`     | 1.20.0 – 1.20.1 | Real NMS · paperweight `reobfJar` (Spigot-mapped runtime · `v1_20_R1`) |

> The 1.21.6 – 1.21.10 buckets compile against the 1.21.4 dev bundle because the codebook tool inside the current paperweight release (2.0.0-beta.21) can't read class-file v69 entries shipped inside newer Paper bundles. The SlotDisplay packet shape used by the impl is stable across 1.21.2 – 1.21.10, so runtime linkage is verified by `NMSHandlerProvider`'s `LinkageError` fallback.
>
> Pre-1.20.5 buckets use paperweight's `reobfJar` task because Paper's runtime was Spigot-mapped there. The Mojang-mapped source is reobfuscated into versioned `org.bukkit.craftbukkit.v1_20_R*` packages before the shaded jar consumes it.
>
> All buckets share `ParticleResolver` for the 1.20.5 particle rename (e.g. `VILLAGER_HAPPY` → `HAPPY_VILLAGER`).

---

## Build

```bash
./gradlew :plugin:shadowJar
```

Output: `plugin/build/libs/ReMinions-1.0.0.jar`

---

## Commands

| Command | Alias | Permission |
|---------|-------|------------|
| `/reminions` | `/minions` | `reminions.admin` |

---

## PlaceholderAPI

### Server-wide
| Placeholder | Returns |
|-------------|---------|
| `%reminions_server_total_minions%` | All active minions across all players |
| `%reminions_server_total_players%` | Players with at least one minion |
| `%reminions_server_total_minion_configs%` | Loaded minion yml count |
| `%reminions_server_total_modifier_configs%` | Loaded modifier yml count |
| `%reminions_server_total_skin_configs%` | Loaded skin yml count |
| `%reminions_server_total_storage_configs%` | Loaded storage yml count |

### Per-player slot & progression
| Placeholder | Returns |
|-------------|---------|
| `%reminions_player_current_minions%` | Active minion count |
| `%reminions_player_max_minions%` | Slot cap |
| `%reminions_player_slots_available%` | Remaining free slots |
| `%reminions_player_slots_percent%` | `current/max` as `XX%` |
| `%reminions_player_unique_minions%` | Distinct minion ids ever placed |
| `%reminions_player_needed_unique_minions%` | Unique-minion count needed for next reward |
| `%reminions_player_progress_next_reward%` | `XX.X%` toward next reward |

### Per-player aggregates
| Placeholder | Returns |
|-------------|---------|
| `%reminions_player_total_earnings%` | Lifetime auto-sell revenue (raw) |
| `%reminions_player_total_earnings_formatted%` | `1.2M` / `8.4K` / `123` |
| `%reminions_player_total_collected%` | Sum of `collected` across all minions |
| `%reminions_player_total_modifiers%` | Total modifier items placed |
| `%reminions_player_total_storage_used%` | Items sitting in all minion storages |
| `%reminions_player_total_storage_max%` | Combined storage capacity |
| `%reminions_player_total_storage_percent%` | Used vs max as `XX.X%` |
| `%reminions_player_total_active_minions%` | Minions not in `POSITION_INVALID` / `FULLY` |
| `%reminions_player_minion_count_<TYPE>%` | Count for `MINER`/`FARMER`/`LUMBERJACK`/`KILLER`/`FISHERMAN` |
| `%reminions_player_minion_id_count_<id>%` | Count of a specific minion id (e.g. `diamond`) |
| `%reminions_player_recent_production_hourly%` | Items produced in last 60 minutes (rolling window) |
| `%reminions_player_recent_production_daily%` | Hourly rate × 24 (approximation) |
| `%reminions_player_estimated_items_per_hour%` | Sum of expected drops × actions-per-hour across all owned minions |
| `%reminions_player_estimated_items_per_day%` | Hourly estimate × 24 |

### Per-minion (lookup by index)
Format: `%reminions_player_minion_index_<n>_<field>%`

| `<field>` | Returns |
|-----------|---------|
| `status` | `IDLE` / `WORKING_BREAK` / `WORKING_PLACE` / `POSITION_INVALID` / `FULLY` |
| `id` | Minion yml id (e.g. `diamond_minion`) |
| `type` | Minion type enum |
| `level` | Numeric level |
| `level_roman` | `IV`, `XII`, etc. |
| `display` | Coloured display name with roman level filled in |
| `owner` | Owner player name |
| `collected` | Items collected lifetime |
| `production_speed` | Seconds between actions |
| `base_radius` | Work radius (blocks) |
| `modifier_count` | Modifiers currently placed |
| `world` / `x` / `y` / `z` | Location parts |
| `skin` | Skin id currently equipped |
| `storage_name` | Storage type id (empty when no storage block) |
| `storage_used` / `storage_max` / `storage_percent` | Storage fill |
| `has_auto_sell` | `true` / `false` |
| `auto_sell_multiplier` | Effective price multiplier (capped at 1.5×) |
| `auto_sell_total_sold` | Lifetime items auto-sold |
| `auto_sell_total_earned` | Lifetime auto-sell revenue |
| `modifier_<TYPE>_count` | Active modifier count on this minion for the given type (e.g. `modifier_SPEED_count`). Excludes expired modifiers. |

---

## Public API (`BoboAPI`)

Access from your plugin:

```java
ReMinions plugin = (ReMinions) Bukkit.getPluginManager().getPlugin("ReMinions");
BoboAPI api = plugin.getApi();
```

Surface (selected):

| Method | Purpose |
|--------|---------|
| `createMinion(skinId, ownerId, location, meta)` | Spawn a new minion programmatically |
| `removeMinion(ownerId, minionId)` | Despawn + delete |
| `applySkin(minionId, skinId)` | Hot-swap the cosmetic skin |
| `setMinionLevel(minionId, level)` | Set level (validated against config) |
| `getMinion(minionId)` / `findMinion(minionId)` | Lookup (nullable / Optional) |
| `getAllMinions()` / `getMinionsByOwner(ownerId)` | Bulk queries |
| `getMinionsInWorld(world)` | World-scoped query |
| `getMinionsByType(ownerId, type)` | Per-type filter |
| `getMinionsByConfigId(ownerId, id)` | Filter by yml id |
| `getAutoSellPriceMultiplier(minionId)` | Effective auto-sell price (≤ 1.5×) |
| `removeModifier(minionId, modifierId)` | Detach a specific modifier |
| `getMinionStorage(minionId)` / `hasStorage(minionId)` | Storage block lookup |
| `getCollectedCount(minionId)` / `addCollected(...)` | Counter access |
| `getMinionConfig(id)` / `getAllMinionConfigs()` | Catalog reads |
| `getModifierConfig(id)` / `getAllModifierConfigs()` | Catalog reads |
| `getSkin(id)` / `getAvailableSkins()` | Catalog reads |
| `getMaxMinions(ownerId)` / `getCurrentMinions(ownerId)` / `canPlaceMinion(ownerId)` | Slot checks |
| `getTotalEarnings(ownerId)` / `getUniqueMinionsCount(ownerId)` | Player stats |

`null` returns indicate "no such entity"; void mutators are no-ops when the target is missing.

---

## Events

`MinionItemsProduceEvent` · `MinionItemsRemoveEvent` · `MinionSellItemsEvent` · `MinionPlaceEvent` — all under `dev.yanianz.reminions.api.events`.

---

## Project Structure

```
ReMinions/
├── plugin/
│   ├── src/main/java/dev/yanianz/reminions/   # Plugin source
│   ├── src/main/resources/                    # plugin.yml + configs
│   └── src/stubs/java/                        # Legacy import migration stubs
├── nms/
│   ├── v1_20*/                                # API-only buckets for 1.20.x
│   ├── v1_21*/                                # API-only buckets for 1.21.0 – 1.21.10
│   ├── v1_21_11/                              # NMS adapter (Mojang-mapped, Paper 1.21.11)
│   └── v26_1_2/                               # NMS adapter (Paper 26.1.x / 26.2.x)
├── build.gradle.kts
├── settings.gradle.kts
└── gradle.properties
```

---

## License

Copyright © 2026 YanIanZ — [PolyForm Perimeter License 1.0.0](LICENSE)

> Use and modify freely. Redistribution as a competing product is prohibited.
