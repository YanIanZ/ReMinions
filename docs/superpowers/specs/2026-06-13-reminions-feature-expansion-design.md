# ReMinions Feature Expansion — Design Spec
Date: 2026-06-13

## Scope

Single-pass implementation (Approach A): all changes in one commit. Majority are config/YML files; two new Java listener classes; AuraSkills removal is pure deletion.

Out of scope (deferred): Beacon system, collection system, Bazaar/AH sell target, additional minion types.

---

## 1. AuraSkills Removal

**Motivation:** XP generation consolidates to EcoSkills only. AuraSkills is soft-dep and adds dead weight.

**Changes:**
- Delete `plugin/src/main/java/dev/yanianz/reminions/listener/AuraSkillListener.java`
- `ReMinions.java` — remove `import dev.aurelium.auraskills.api.AuraSkillsApi`, `loadAuraSkills()` method, and its call in `onEnable()`
- `plugin/build.gradle.kts` — remove `compileOnly(libs.auraskills)`
- `gradle/libs.versions.toml` — remove `auraskills` version and library entries
- `plugin/src/main/resources/plugin.yml` — remove `AuraSkills` from `softdepend`

EcoSkillListener is unchanged.

---

## 2. Fuel System (4 tiers)

All config-driven. `ModifierCategory.FUEL` and `ModifierType.SPEED` with `duration` already exist — zero new Java code.

| File | Duration | Speed Multiplier |
|---|---|---|
| `modifiers/lava_bucket.yml` | 3600s (60 min) | +0.10 (10%) |
| `modifiers/enchanted_lava_bucket.yml` | 7200s (120 min) — update existing | +0.30 (30%) |
| `modifiers/solar_panel.yml` | -1 (permanent) | +0.15 (15%) |
| `modifiers/plasma_bucket.yml` | 10800s (180 min) | +0.50 (50%) |

Items: `LAVA_BUCKET`, `LAVA_BUCKET` (enchanted glow via `unbreakable: true`), `DAYLIGHT_DETECTOR`, `BUCKET` (custom model or name-differentiated).

---

## 3. Super Compactor 3000

New file: `modifiers/super_compactor_3000.yml`

- `category: UPGRADE`, `type: ITEM_UPGRADES`, `duration: -1`, `unbreakable: true`
- `upgrade_products`: same pairs as `compactor.yml` but `required_product_amount: 64` → 1 compressed/block output
- Covers: cobblestone→stone, iron ingot→iron block, gold ingot→gold block, diamond→diamond block, coal→coal block, lapis→lapis block

---

## 4. Diamond Spreading

New file: `modifiers/diamond_spreading.yml`

- `category: UPGRADE`, `type: ITEM_UPGRADES`, `duration: -1`, `unbreakable: true`
- Single `upgrade_product` entry: `chance: 0.1`, `required_product: null` (no consume), produces 1 `DIAMOND`

**Code change required in `MinionConfig.java` — `applyUpgradeProducts()`:**

Current behavior (line 567): `if (upgrade.getRequiredProduct() == null) continue;` — skips all null-required entries.

New behavior: split into two sub-loops:
1. Existing: `requiredProduct != null` → consume-and-produce (unchanged)
2. New: `requiredProduct == null && chance > 0` → roll chance, produce directly into inventory without consuming anything (bonus drop)

This is the minimal change needed. No new ModifierType, no new config fields.

---

## 5. SuperiorSkyBlock2 Integration

**Dependency:**
- `plugin/build.gradle.kts`: `compileOnly(files("libs/SuperiorSkyblock2.jar"))`
- Copy `SuperiorSkyblock2-2026.1.jar` → `plugin/libs/SuperiorSkyblock2.jar`
- `plugin.yml` softdepend: add `SuperiorSkyblock2`

**`PlayerListener.java` — place check:**
- When player places a minion, if SSB2 is loaded: call `SuperiorSkyblockAPI.getIslandAtLocation(loc)`
- If result is null OR island owner/member does not include player UUID → cancel placement, send message `ssb2_not_your_island`
- Gate behind `config.getBoolean("settings.superiorskyblock_integration", true)`

**New `SuperiorSkyblockListener.java`:**
- Listens `IslandDeleteEvent` (from SSB2 API)
- On fire: iterate `playerManager.getPlayerMinions(islandOwnerUUID)`, call `minionManager.removeMinion()` for each minion whose `Location3f.toLocation()` falls within the deleted island's bounds
- Registered in `ReMinions.onEnable()` conditionally if SSB2 is present

**Config message to add:**
```yaml
ssb2_not_your_island: "&cYou can only place minions on your own island."
```

---

## 6. SourbyCraft SWM Integration

**Dependency:**
- Copy `SourbyCraftSWM.jar` from `/Users/rheninxy/Sourby/SourbyCraft/TestServer/plugins/SourbyCraftSWM.jar` → `plugin/libs/SourbyCraftSWM.jar`
- `plugin/build.gradle.kts`: `compileOnly(files("libs/SourbyCraftSWM.jar"))`
- `plugin.yml` softdepend: add `SourbyCraftSWM`

**New `SwmWorldListener.java`:**
- Package: `dev.yanianz.reminions.listener`
- Listens `dev.iyanz.sourbycraft.swm.api.events.LoadSlimeWorldEvent`
- On fire: get world name from event → iterate `playerManager.getAllMinions()` → filter minions where `minion.getLoc().getWorld().equals(worldName)` → call `minion.spawn(skinManager.get(minion.getCurrentSkin()))` for each
- Handles the case where SWM loads a world after plugin startup, at which point minions in that world have no spawned ArmorStand (viewer-based respawn in MinionThreadTask covers online players, but this ensures correct state on load)
- Registered in `ReMinions.onEnable()` conditionally if SourbyCraftSWM is present

**Unload:** Already handled — `Location3f.toLocation()` returns `null` for unloaded worlds; `MinionThreadTask.updateViewers()` skips null worlds; `processMinion()` short-circuits when location is null. No additional code needed.

---

## File Changelist

| Action | File |
|---|---|
| DELETE | `listener/AuraSkillListener.java` |
| MODIFY | `ReMinions.java` |
| MODIFY | `build.gradle.kts` |
| MODIFY | `gradle/libs.versions.toml` |
| MODIFY | `plugin.yml` |
| MODIFY | `modifiers/enchanted_lava_bucket.yml` |
| NEW | `modifiers/lava_bucket.yml` |
| NEW | `modifiers/solar_panel.yml` |
| NEW | `modifiers/plasma_bucket.yml` |
| NEW | `modifiers/super_compactor_3000.yml` |
| NEW | `modifiers/diamond_spreading.yml` |
| NEW | `listener/SuperiorSkyblockListener.java` |
| NEW | `listener/SwmWorldListener.java` |
| COPY | `plugin/libs/SuperiorSkyblock2.jar` |
| COPY | `plugin/libs/SourbyCraftSWM.jar` |
| MODIFY | `config.yml` (add `ssb2_not_your_island` message + integration toggle) |
| MODIFY | `config/MinionConfig.java` (applyUpgradeProducts — bonus drop path for null requiredProduct) |
