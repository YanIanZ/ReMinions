package dev.yanianz.reminions.api;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import dev.yanianz.reminions.config.Config;
import dev.yanianz.reminions.config.MinionConfig;
import dev.yanianz.reminions.config.MinionSkinConfig;
import dev.yanianz.reminions.core.minion.Minion;
import dev.yanianz.reminions.core.minion.MinionInventory;
import dev.yanianz.reminions.core.minion.MinionMeta;
import dev.yanianz.reminions.core.minion.MinionUpgrade;
import dev.yanianz.reminions.core.player.PlayerMinions;
import dev.yanianz.reminions.managers.MinionManager;
import dev.yanianz.reminions.managers.PlayerManager;
import dev.yanianz.reminions.managers.SkinManager;
import dev.yanianz.reminions.utils.Location3f;
import org.bukkit.Location;

public class BoboAPI {
    private final Config config;
    private final PlayerManager playerManager;
    private final MinionManager minionManager;
    private final SkinManager skinManager;

    public BoboAPI(Config config, PlayerManager playerManager, MinionManager minionManager, SkinManager skinManager) {
        this.config = config;
        this.playerManager = playerManager;
        this.minionManager = minionManager;
        this.skinManager = skinManager;
    }

    public Minion createMinion(String skinId, UUID ownerId, Location location, MinionMeta meta) {
        PlayerMinions playerMinions = this.playerManager.getById(ownerId);
        if (playerMinions == null) return null;
        MinionConfig minionConfig = this.minionManager.get(meta.getId());
        if (minionConfig == null) return null;
        MinionUpgrade upgrade = minionConfig.getUpgrade(meta.getLevel());
        if (upgrade == null) return null;
        MinionSkinConfig skin = this.skinManager.get(skinId);
        if (skin == null) return null;

        MinionInventory inventory = new MinionInventory(new ArrayList<>(), meta.getCollected());
        Minion minion = new Minion(UUID.randomUUID(), meta.getId(), ownerId,
                minionConfig.type(), inventory, minionConfig, new Location3f(location));
        inventory.setMaxSlots(upgrade.maxStorage());
        minion.setLevel(meta.getLevel());
        minion.setSkinLevel(skinId);
        minion.setCacheProductionSpeed(minionConfig.getProductionSpeed(meta.getLevel()));
        minion.setLastGenerated(System.currentTimeMillis());
        this.playerManager.addMinion(playerMinions, minion);
        minionConfig.updateUniqueMinions(playerMinions, this.config, null, meta.getLevel());
        return minion;
    }

    public Minion getMinion(UUID minionId) {
        return this.playerManager.getMinionById(minionId);
    }

    public boolean removeMinion(UUID ownerId, UUID minionId) {
        Minion minion = this.getMinion(minionId);
        if (minion == null) return false;
        this.playerManager.removeMinion(ownerId, minion);
        return true;
    }

    public void despawnMinion(UUID ownerId, UUID minionId) {
        Minion minion = this.getMinion(minionId);
        if (minion != null) minion.despawn();
    }

    public void spawnMinion(UUID ownerId, String skinId, UUID minionId) {
        Minion minion = this.getMinion(minionId);
        if (minion == null) return;
        MinionSkinConfig skin = this.skinManager.get(skinId);
        if (skin != null) minion.spawn(skin);
    }

    public PlayerMinions getPlayerData(UUID ownerId) {
        return this.playerManager.getById(ownerId);
    }

    public int getMaxMinions(UUID ownerId) {
        PlayerMinions playerMinions = this.getPlayerData(ownerId);
        return playerMinions != null ? playerMinions.getMaxMinions() : 0;
    }

    public int getCurrentMinions(UUID ownerId) {
        PlayerMinions playerMinions = this.getPlayerData(ownerId);
        return playerMinions != null ? playerMinions.getCurrentMinions() : 0;
    }

    public boolean canPlaceMinion(UUID ownerId) {
        PlayerMinions playerMinions = this.getPlayerData(ownerId);
        return playerMinions != null && playerMinions.getCurrentMinions() < playerMinions.getMaxMinions();
    }

    public void applySkin(UUID minionId, String skinId) {
        Minion minion = this.getMinion(minionId);
        if (minion == null) return;
        MinionSkinConfig skin = this.skinManager.get(skinId);
        if (skin != null) {
            minion.setSkin(skinId);
            minion.despawn();
            minion.spawn(skin);
        }
    }

    public MinionSkinConfig getSkin(String skinId) {
        return this.skinManager.get(skinId);
    }

    public List<MinionSkinConfig> getAvailableSkins() {
        return new ArrayList<>(this.skinManager.getAll());
    }
}
