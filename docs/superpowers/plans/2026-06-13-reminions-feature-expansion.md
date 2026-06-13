# ReMinions Feature Expansion — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Remove AuraSkills, add 4-tier fuel system, Super Compactor 3000, Diamond Spreading, SuperiorSkyBlock2 island placement guard, and SourbyCraft SWM world-load respawn — all in one pass.

**Architecture:** Majority of changes are YAML config files (zero new logic). Two new listener classes for SSB2 and SWM integration follow the existing soft-dep pattern in `ReMinions.onEnable()`. One small Java change in `MinionConfig.applyUpgradeProducts()` enables bonus-drop products with no required input.

**Tech Stack:** Paper 1.21.11, SuperiorSkyblock2-2026.1.jar, SourbyCraftSWM.jar (local libs/), Gradle with `libs.versions.toml`

---

## File Map

| Action | Path |
|---|---|
| DELETE | `plugin/src/main/java/dev/yanianz/reminions/listener/AuraSkillListener.java` |
| MODIFY | `plugin/src/main/java/dev/yanianz/reminions/ReMinions.java` |
| MODIFY | `plugin/build.gradle.kts` |
| MODIFY | `gradle/libs.versions.toml` |
| MODIFY | `plugin/src/main/resources/plugin.yml` |
| MODIFY | `plugin/src/main/resources/modifiers/enchanted_lava_bucket.yml` |
| NEW | `plugin/src/main/resources/modifiers/lava_bucket.yml` |
| NEW | `plugin/src/main/resources/modifiers/solar_panel.yml` |
| NEW | `plugin/src/main/resources/modifiers/plasma_bucket.yml` |
| NEW | `plugin/src/main/resources/modifiers/super_compactor_3000.yml` |
| NEW | `plugin/src/main/resources/modifiers/diamond_spreading.yml` |
| MODIFY | `plugin/src/main/java/dev/yanianz/reminions/config/MinionConfig.java` |
| NEW | `plugin/src/main/java/dev/yanianz/reminions/listener/SuperiorSkyblockListener.java` |
| MODIFY | `plugin/src/main/java/dev/yanianz/reminions/listener/PlayerListener.java` |
| NEW | `plugin/src/main/java/dev/yanianz/reminions/listener/SwmWorldListener.java` |
| COPY | `plugin/libs/SuperiorSkyblock2.jar` (from `/Users/rheninxy/Downloads/SuperiorSkyblock2-2026.1.jar`) |
| COPY | `plugin/libs/SourbyCraftSWM.jar` (from `/Users/rheninxy/Sourby/SourbyCraft/TestServer/plugins/SourbyCraftSWM.jar`) |
| MODIFY | `plugin/src/main/resources/config.yml` |

---

## Task 1: Remove AuraSkills

**Files:**
- Delete: `plugin/src/main/java/dev/yanianz/reminions/listener/AuraSkillListener.java`
- Modify: `plugin/src/main/java/dev/yanianz/reminions/ReMinions.java`
- Modify: `plugin/build.gradle.kts`
- Modify: `gradle/libs.versions.toml`
- Modify: `plugin/src/main/resources/plugin.yml`

- [ ] **Step 1: Delete AuraSkillListener.java**

```bash
rm plugin/src/main/java/dev/yanianz/reminions/listener/AuraSkillListener.java
```

- [ ] **Step 2: Remove AuraSkills from ReMinions.java**

In `plugin/src/main/java/dev/yanianz/reminions/ReMinions.java`, remove:
- The import line: `import dev.aurelium.auraskills.api.AuraSkillsApi;`
- The import line: `import dev.yanianz.reminions.listener.AuraSkillListener;`
- The call `loadAuraSkills();` inside `onEnable()` (around line 100)
- The entire `loadAuraSkills()` method (around lines 205–222):

```java
// DELETE this entire method:
private void loadAuraSkills() {
    if (this.getServer().getPluginManager().getPlugin("AuraSkills") == null) {
        DebugLogger.info("AuraSkills not found, skipping integration.");
    } else {
        try {
            AuraSkillsApi auraApi = AuraSkillsApi.get();
            if (auraApi == null) {
                DebugLogger.warn("AuraSkills API not available, integration skipped.");
                return;
            }
            this.getServer().getPluginManager()
                    .registerEvents(new AuraSkillListener(this.minionManager, this.modifierManager, this.config0, auraApi), this);
            DebugLogger.info("AuraSkills successfully hooked.");
        } catch (Throwable e) {
            DebugLogger.warn("Failed to hook into AuraSkills: " + e.getMessage());
        }
    }
}
```

- [ ] **Step 3: Remove auraskills from libs.versions.toml**

In `gradle/libs.versions.toml`, remove:
```toml
# In [versions]:
auraskills        = "2.3.12"

# In [libraries]:
auraskills        = { module = "dev.aurelium:auraskills-api-bukkit",     version.ref = "auraskills" }
```

- [ ] **Step 4: Remove auraskills from build.gradle.kts**

In `plugin/build.gradle.kts`, remove:
```kotlin
compileOnly(libs.auraskills)
```

- [ ] **Step 5: Remove AuraSkills from plugin.yml softdepend**

In `plugin/src/main/resources/plugin.yml`, update `softdepend` from:
```yaml
softdepend:
  - Vault
  - AuraSkills
  - MMOItems
  - PlaceholderAPI
  - EcoItems
  - LuckPerms
  - CraftEngine
```
to:
```yaml
softdepend:
  - Vault
  - MMOItems
  - PlaceholderAPI
  - EcoItems
  - LuckPerms
  - CraftEngine
```

- [ ] **Step 6: Verify build compiles**

```bash
cd /Users/rheninxy/Recaf/BeeMinionsRework-project && ./gradlew :plugin:compileJava 2>&1 | tail -20
```
Expected: `BUILD SUCCESSFUL` with no errors referencing AuraSkills.

- [ ] **Step 7: Commit**

```bash
git add -A
git commit -m "refactor: remove AuraSkills integration, consolidate XP to EcoSkills"
```

---

## Task 2: Fuel System — 4 tiers

**Files:**
- Modify: `plugin/src/main/resources/modifiers/enchanted_lava_bucket.yml`
- New: `plugin/src/main/resources/modifiers/lava_bucket.yml`
- New: `plugin/src/main/resources/modifiers/solar_panel.yml`
- New: `plugin/src/main/resources/modifiers/plasma_bucket.yml`

- [ ] **Step 1: Update enchanted_lava_bucket.yml (duration 7200s, speed +30%)**

Full content of `plugin/src/main/resources/modifiers/enchanted_lava_bucket.yml`:
```yaml
category: FUEL
type: SPEED
duration: 7200
multiplier: 0.3
unbreakable: false

item:
  material: LAVA_BUCKET
  name: "&6⚡ &eEnchanted Lava Bucket"
  lore:
    - "&7A supercharged lava bucket that"
    - "&7turbochages your minion for &e%duration%&7."
    - ""
    - "&6✦ &e+30% Speed"
    - "&7Duration: &e2 hours"
    - "&8(Right-click on minion to activate)"
  unbreakable: true
  enchants:
    - "luck_of_the_sea:1"
```

- [ ] **Step 2: Create lava_bucket.yml (3600s, +10%)**

Create `plugin/src/main/resources/modifiers/lava_bucket.yml`:
```yaml
category: FUEL
type: SPEED
duration: 3600
multiplier: 0.1
unbreakable: false

item:
  material: LAVA_BUCKET
  name: "&c🔥 &6Lava Bucket"
  lore:
    - "&7A bucket of lava that provides"
    - "&7basic fuel for your minion."
    - ""
    - "&6✦ &e+10% Speed"
    - "&7Duration: &e1 hour"
    - "&8(Right-click on minion to activate)"
```

- [ ] **Step 3: Create solar_panel.yml (permanent, +15%)**

Create `plugin/src/main/resources/modifiers/solar_panel.yml`:
```yaml
category: FUEL
type: SPEED
duration: -1
multiplier: 0.15
unbreakable: true

item:
  material: DAYLIGHT_DETECTOR
  name: "&e☀ &aSolar Panel"
  lore:
    - "&7Harnesses sunlight to permanently"
    - "&7boost your minion's speed."
    - ""
    - "&a✦ &e+15% Speed"
    - "&7Duration: &aPermanent"
    - "&8(Place inside the minion)"
```

- [ ] **Step 4: Create plasma_bucket.yml (10800s, +50%)**

Create `plugin/src/main/resources/modifiers/plasma_bucket.yml`:
```yaml
category: FUEL
type: SPEED
duration: 10800
multiplier: 0.5
unbreakable: false

item:
  material: BUCKET
  name: "&b⚡ &3Plasma Bucket"
  lore:
    - "&7An experimental plasma container"
    - "&7that massively accelerates your minion."
    - ""
    - "&b✦ &3+50% Speed"
    - "&7Duration: &b3 hours"
    - "&8(Right-click on minion to activate)"
  custom_model_data: 10501
```

- [ ] **Step 5: Commit**

```bash
git add plugin/src/main/resources/modifiers/
git commit -m "feat: add 4-tier fuel system (lava bucket, enchanted lava, solar panel, plasma)"
```

---

## Task 3: Super Compactor 3000

**Files:**
- New: `plugin/src/main/resources/modifiers/super_compactor_3000.yml`

- [ ] **Step 1: Create super_compactor_3000.yml**

Create `plugin/src/main/resources/modifiers/super_compactor_3000.yml`:
```yaml
category: UPGRADE
type: ITEM_UPGRADES
duration: -1
multiplier: 1
unbreakable: true

item:
  material: PISTON
  name: "&8⚙ &7Super Compactor 3000"
  lore:
    - "&7Compresses 64 resources into"
    - "&7a single compact block."
    - ""
    - "&8✦ &764 items → 1 block"
    - "&8(Place inside the minion)"

upgrade_products:
  cobblestone_to_stone_64:
    type: vanilla
    price: 0.0
    amount: 1
    chance: 1.0
    required_product: cobblestone
    required_product_amount: 64
    material: STONE
    name: "&7Super Compacted Stone"
    lore:
      - "&7Created from 64 Cobblestone"

  iron_ingot_to_block_64:
    type: vanilla
    price: 0.0
    amount: 1
    chance: 1.0
    required_product: iron_ingot
    required_product_amount: 64
    material: IRON_BLOCK
    name: "&fSuper Compacted Iron"
    lore:
      - "&7Created from 64 Iron Ingots"

  gold_ingot_to_block_64:
    type: vanilla
    price: 0.0
    amount: 1
    chance: 1.0
    required_product: gold_ingot
    required_product_amount: 64
    material: GOLD_BLOCK
    name: "&6Super Compacted Gold"
    lore:
      - "&7Created from 64 Gold Ingots"

  diamond_to_block_64:
    type: vanilla
    price: 0.0
    amount: 1
    chance: 1.0
    required_product: diamond
    required_product_amount: 64
    material: DIAMOND_BLOCK
    name: "&bSuper Compacted Diamond"
    lore:
      - "&7Created from 64 Diamonds"

  coal_to_block_64:
    type: vanilla
    price: 0.0
    amount: 1
    chance: 1.0
    required_product: coal
    required_product_amount: 64
    material: COAL_BLOCK
    name: "&8Super Compacted Coal"
    lore:
      - "&7Created from 64 Coal"

  lapis_to_block_64:
    type: vanilla
    price: 0.0
    amount: 1
    chance: 1.0
    required_product: lapis_lazuli
    required_product_amount: 64
    material: LAPIS_BLOCK
    name: "&9Super Compacted Lapis"
    lore:
      - "&7Created from 64 Lapis Lazuli"
```

- [ ] **Step 2: Commit**

```bash
git add plugin/src/main/resources/modifiers/super_compactor_3000.yml
git commit -m "feat: add Super Compactor 3000 modifier (64:1 compression)"
```

---

## Task 4: Diamond Spreading — YAML + code change

**Files:**
- New: `plugin/src/main/resources/modifiers/diamond_spreading.yml`
- Modify: `plugin/src/main/java/dev/yanianz/reminions/config/MinionConfig.java`

- [ ] **Step 1: Create diamond_spreading.yml**

Create `plugin/src/main/resources/modifiers/diamond_spreading.yml`:
```yaml
category: UPGRADE
type: ITEM_UPGRADES
duration: -1
multiplier: 1
unbreakable: true

item:
  material: DIAMOND
  name: "&b💎 &3Diamond Spreading"
  lore:
    - "&7Each action your minion takes"
    - "&7has a chance to produce a bonus diamond."
    - ""
    - "&b✦ &310% Chance per action"
    - "&8(Place inside the minion)"

upgrade_products:
  bonus_diamond:
    type: vanilla
    price: 0.0
    amount: 1
    chance: 0.1
    material: DIAMOND
    name: "&bBonus Diamond"
    lore:
      - "&3Lucky drop from Diamond Spreading"
```

- [ ] **Step 2: Add bonus-drop path in MinionConfig.applyUpgradeProducts()**

In `plugin/src/main/java/dev/yanianz/reminions/config/MinionConfig.java`, find the method `applyUpgradeProducts`. It ends with `return produced;`. Add a second loop **before** the return statement:

```java
// Bonus-drop path: upgrade_products with no required_product — roll chance and insert directly.
for (Product upgrade : new ArrayList<>(productMap.values())) {
    if (upgrade.getRequiredProduct() != null) continue;
    if (upgrade.getChance() <= 0.0) continue;
    if (Math.random() > upgrade.getChance()) continue;
    ItemStack item = upgrade.buildItem();
    int inserted = this.tryInsert(minion, item, upgrade.getAmount(), minion.getInventory(), storageInv,
            useAutoSellTarget, autoSellMod, upgrade);
    if (inserted > 0) produced = true;
}
```

The method should now look like:
```java
private boolean applyUpgradeProducts(Minion minion, MinionModifierData autoSellMod,
                                     MinionInventory primaryInv, MinionInventory storageInv,
                                     boolean useAutoSellTarget,
                                     Map<String, Product> productMap,
                                     Map<String, Integer> buffered) {
    boolean produced = false;
    // --- existing consume-and-produce loop (unchanged) ---
    for (Product upgrade : new ArrayList<>(productMap.values())) {
        if (upgrade.getRequiredProduct() == null) continue;
        // ... (existing code unchanged)
    }

    // Bonus-drop path: upgrade_products with no required_product — roll chance and insert directly.
    for (Product upgrade : new ArrayList<>(productMap.values())) {
        if (upgrade.getRequiredProduct() != null) continue;
        if (upgrade.getChance() <= 0.0) continue;
        if (Math.random() > upgrade.getChance()) continue;
        ItemStack item = upgrade.buildItem();
        int inserted = this.tryInsert(minion, item, upgrade.getAmount(), minion.getInventory(), storageInv,
                useAutoSellTarget, autoSellMod, upgrade);
        if (inserted > 0) produced = true;
    }

    return produced;
}
```

- [ ] **Step 3: Verify build**

```bash
./gradlew :plugin:compileJava 2>&1 | tail -10
```
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 4: Commit**

```bash
git add plugin/src/main/resources/modifiers/diamond_spreading.yml \
        plugin/src/main/java/dev/yanianz/reminions/config/MinionConfig.java
git commit -m "feat: add Diamond Spreading modifier with bonus-drop logic"
```

---

## Task 5: SuperiorSkyBlock2 Integration

**Files:**
- Copy: `plugin/libs/SuperiorSkyblock2.jar`
- Modify: `plugin/build.gradle.kts`
- Modify: `plugin/src/main/resources/plugin.yml`
- Modify: `plugin/src/main/resources/config.yml`
- New: `plugin/src/main/java/dev/yanianz/reminions/listener/SuperiorSkyblockListener.java`
- Modify: `plugin/src/main/java/dev/yanianz/reminions/listener/PlayerListener.java`
- Modify: `plugin/src/main/java/dev/yanianz/reminions/ReMinions.java`

- [ ] **Step 1: Copy SSB2 jar to libs/**

```bash
mkdir -p plugin/libs
cp /Users/rheninxy/Downloads/SuperiorSkyblock2-2026.1.jar plugin/libs/SuperiorSkyblock2.jar
```

- [ ] **Step 2: Add compileOnly dependency in build.gradle.kts**

In `plugin/build.gradle.kts`, inside the `dependencies { }` block, add after the existing `compileOnly(fileTree(...))` line:
```kotlin
compileOnly(files("libs/SuperiorSkyblock2.jar"))
```

- [ ] **Step 3: Add SuperiorSkyblock2 to plugin.yml softdepend**

In `plugin/src/main/resources/plugin.yml`, add `SuperiorSkyblock2` to the `softdepend` list:
```yaml
softdepend:
  - Vault
  - MMOItems
  - PlaceholderAPI
  - EcoItems
  - LuckPerms
  - CraftEngine
  - SuperiorSkyblock2
```

- [ ] **Step 4: Add config message and toggle**

In `plugin/src/main/resources/config.yml`, inside the `messages:` section, add:
```yaml
  ssb2_not_your_island: "&cYou can only place minions on your own island."
```

Also inside `settings:`, add:
```yaml
  superiorskyblock_integration: true
```

- [ ] **Step 5: Create SuperiorSkyblockListener.java**

Create `plugin/src/main/java/dev/yanianz/reminions/listener/SuperiorSkyblockListener.java`:
```java
package dev.yanianz.reminions.listener;

import com.bgsoftware.superiorskyblock.api.events.IslandDisbandEvent;
import com.bgsoftware.superiorskyblock.api.island.Island;
import dev.yanianz.reminions.core.minion.Minion;
import dev.yanianz.reminions.managers.PlayerManager;
import org.bukkit.Location;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

import java.util.List;

public class SuperiorSkyblockListener implements Listener {

    private final PlayerManager playerManager;

    public SuperiorSkyblockListener(PlayerManager playerManager) {
        this.playerManager = playerManager;
    }

    @EventHandler
    public void onIslandDisband(IslandDisbandEvent event) {
        Island island = event.getIsland();
        List<Minion> allMinions = this.playerManager.getAllMinions();
        for (Minion minion : List.copyOf(allMinions)) {
            Location loc = minion.getLoc().toLocation();
            if (loc != null && island.isInside(loc)) {
                minion.despawn();
                this.playerManager.removeMinion(
                        this.playerManager.getById(minion.getOwner()),
                        minion
                );
            }
        }
    }
}
```

- [ ] **Step 6: Add island placement guard in PlayerListener.java**

In `plugin/src/main/java/dev/yanianz/reminions/listener/PlayerListener.java`, add the SSB2 check inside `onInteract()`, after the `isWorldRestricted` check and before the `tooCloseToAnotherMinion` call. The placement location is `placeLoc` (already computed above this point).

First add the import at the top of the file:
```java
import com.bgsoftware.superiorskyblock.api.SuperiorSkyblockAPI;
import com.bgsoftware.superiorskyblock.api.island.Island;
import com.bgsoftware.superiorskyblock.api.wrappers.SuperiorPlayer;
```

Then add the check inside `onInteract()` after the `isWorldRestricted` block:
```java
// SSB2 island ownership guard
if (this.plugin.isSuperiorSkyblockEnabled()
        && this.plugin.getConfig0().getBoolean("settings.superiorskyblock_integration", true)) {
    Island island = SuperiorSkyblockAPI.getIslandAt(placeLoc);
    if (island == null) {
        this.plugin.getConfig0().sendMessage(player, "ssb2_not_your_island");
        return;
    }
    SuperiorPlayer superiorPlayer = SuperiorSkyblockAPI.getPlayer(player.getUniqueId());
    if (!island.isMember(superiorPlayer)) {
        this.plugin.getConfig0().sendMessage(player, "ssb2_not_your_island");
        return;
    }
}
```

- [ ] **Step 7: Add isSuperiorSkyblockEnabled() to ReMinions.java and register listener**

In `ReMinions.java`, add a field and method:
```java
private boolean superiorSkyblockEnabled = false;

public boolean isSuperiorSkyblockEnabled() {
    return this.superiorSkyblockEnabled;
}
```

Add a `loadSuperiorSkyblock()` method:
```java
private void loadSuperiorSkyblock() {
    if (this.getServer().getPluginManager().getPlugin("SuperiorSkyblock2") == null) {
        DebugLogger.info("SuperiorSkyblock2 not found, skipping integration.");
        return;
    }
    this.superiorSkyblockEnabled = true;
    this.getServer().getPluginManager()
            .registerEvents(new SuperiorSkyblockListener(this.playerManager), this);
    DebugLogger.info("SuperiorSkyblock2 successfully hooked.");
}
```

Add the call inside `onEnable()` after `loadEcoSkills();`:
```java
loadSuperiorSkyblock();
```

Add the import:
```java
import dev.yanianz.reminions.listener.SuperiorSkyblockListener;
```

- [ ] **Step 8: Verify build**

```bash
./gradlew :plugin:compileJava 2>&1 | tail -10
```
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 9: Commit**

```bash
git add plugin/libs/SuperiorSkyblock2.jar \
        plugin/build.gradle.kts \
        plugin/src/main/resources/plugin.yml \
        plugin/src/main/resources/config.yml \
        plugin/src/main/java/dev/yanianz/reminions/listener/SuperiorSkyblockListener.java \
        plugin/src/main/java/dev/yanianz/reminions/listener/PlayerListener.java \
        plugin/src/main/java/dev/yanianz/reminions/ReMinions.java
git commit -m "feat: add SuperiorSkyblock2 integration (island placement guard + disband cleanup)"
```

---

## Task 6: SourbyCraft SWM Integration

**Files:**
- Copy: `plugin/libs/SourbyCraftSWM.jar`
- Modify: `plugin/build.gradle.kts`
- Modify: `plugin/src/main/resources/plugin.yml`
- New: `plugin/src/main/java/dev/yanianz/reminions/listener/SwmWorldListener.java`
- Modify: `plugin/src/main/java/dev/yanianz/reminions/ReMinions.java`

- [ ] **Step 1: Copy SourbyCraftSWM jar to libs/**

```bash
cp /Users/rheninxy/Sourby/SourbyCraft/TestServer/plugins/SourbyCraftSWM.jar plugin/libs/SourbyCraftSWM.jar
```

- [ ] **Step 2: Add compileOnly dependency in build.gradle.kts**

In `plugin/build.gradle.kts`, add after the SuperiorSkyblock2 line:
```kotlin
compileOnly(files("libs/SourbyCraftSWM.jar"))
```

- [ ] **Step 3: Add SourbyCraftSWM to plugin.yml softdepend**

```yaml
softdepend:
  - Vault
  - MMOItems
  - PlaceholderAPI
  - EcoItems
  - LuckPerms
  - CraftEngine
  - SuperiorSkyblock2
  - SourbyCraftSWM
```

- [ ] **Step 4: Create SwmWorldListener.java**

Create `plugin/src/main/java/dev/yanianz/reminions/listener/SwmWorldListener.java`:
```java
package dev.yanianz.reminions.listener;

import dev.iyanz.sourbycraft.swm.api.events.LoadSlimeWorldEvent;
import dev.yanianz.reminions.core.minion.Minion;
import dev.yanianz.reminions.managers.PlayerManager;
import dev.yanianz.reminions.managers.SkinManager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

public class SwmWorldListener implements Listener {

    private final PlayerManager playerManager;
    private final SkinManager skinManager;

    public SwmWorldListener(PlayerManager playerManager, SkinManager skinManager) {
        this.playerManager = playerManager;
        this.skinManager = skinManager;
    }

    @EventHandler
    public void onSlimeWorldLoad(LoadSlimeWorldEvent event) {
        String worldName = event.getSlimeWorld().getName();
        for (Minion minion : this.playerManager.getAllMinions()) {
            if (!worldName.equals(minion.getLoc().getWorldName())) continue;
            if (minion.getSkinModel() != null) continue; // already spawned
            minion.spawn(this.skinManager.get(minion.getCurrentSkin()));
        }
    }
}
```

- [ ] **Step 5: Add loadSwm() to ReMinions.java and register listener**

In `ReMinions.java`, add a `loadSwm()` method:
```java
private void loadSwm() {
    if (this.getServer().getPluginManager().getPlugin("SourbyCraftSWM") == null) {
        DebugLogger.info("SourbyCraftSWM not found, skipping integration.");
        return;
    }
    this.getServer().getPluginManager()
            .registerEvents(new SwmWorldListener(this.playerManager, this.skinManager), this);
    DebugLogger.info("SourbyCraftSWM successfully hooked.");
}
```

Add the call inside `onEnable()` after `loadSuperiorSkyblock();`:
```java
loadSwm();
```

Add the import:
```java
import dev.yanianz.reminions.listener.SwmWorldListener;
```

- [ ] **Step 6: Verify build**

```bash
./gradlew :plugin:compileJava 2>&1 | tail -10
```
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 7: Full build and confirm jar**

```bash
./gradlew shadowJar 2>&1 | tail -10
ls -lh build/libs/
```
Expected: `ReMinions-*.jar` present with recent timestamp.

- [ ] **Step 8: Commit**

```bash
git add plugin/libs/SourbyCraftSWM.jar \
        plugin/build.gradle.kts \
        plugin/src/main/resources/plugin.yml \
        plugin/src/main/java/dev/yanianz/reminions/listener/SwmWorldListener.java \
        plugin/src/main/java/dev/yanianz/reminions/ReMinions.java
git commit -m "feat: add SourbyCraft SWM integration (respawn minions on world load)"
```
