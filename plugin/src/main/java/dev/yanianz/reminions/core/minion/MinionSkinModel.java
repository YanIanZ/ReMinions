package dev.yanianz.reminions.core.minion;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import dev.yanianz.reminions.ReMinions;
import dev.yanianz.reminions.config.MinionSkinConfig;
import dev.yanianz.reminions.utils.Text;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.persistence.PersistentDataType;

public class MinionSkinModel {
    private final MinionSkinConfig skinConfig;
    private ArmorStand stand;
    private final List<ArmorStand> holoStands = new ArrayList<>();
    private boolean isPermanent;

    public MinionSkinModel(MinionSkinConfig skinConfig) {
        this.skinConfig = skinConfig;
    }

    public void spawn(Location location, UUID minionId) {
        this.stand = location.getWorld().spawn(location, ArmorStand.class);
        this.stand.setArms(true);
        this.stand.setGravity(false);
        this.stand.setInvulnerable(true);
        this.stand.setCollidable(false);
        this.stand.setSmall(true);
        this.stand.setBasePlate(false);
        this.stand.setPersistent(false);
        this.stand.setDisabledSlots(
                EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS,
                EquipmentSlot.FEET, EquipmentSlot.HAND, EquipmentSlot.OFF_HAND);
        this.stand.getPersistentDataContainer().set(
                dev.yanianz.reminions.Keys.MINION_ARMORSTAND, PersistentDataType.STRING, minionId.toString());
        this.stand.setCustomNameVisible(false);
        this.skinConfig.getSlots().forEach((slot, itemBuilder) -> this.stand.setItem(slot, itemBuilder.toBuild()));
    }

    public void addHolograms(MinionStatus status, int displaySeconds) {
        if (displaySeconds >= 0 || !this.isPermanent) {
            for (ArmorStand holo : this.holoStands) {
                if (!holo.isDead()) holo.remove();
            }
            this.holoStands.clear();

            Location base = this.stand.getLocation().clone().add(0.0, 1.2, 0.0);
            double lineSpacing = 0.25;

            for (String line : this.skinConfig.getHolograms().getOrDefault(status, new String[0])) {
                Location holoLoc = base.clone().add(0.0, this.holoStands.size() * lineSpacing, 0.0);
                ArmorStand holo = holoLoc.getWorld().spawn(holoLoc, ArmorStand.class);
                holo.setVisible(false);
                holo.setMarker(true);
                holo.setPersistent(false);
                holo.customName(Text.parseComponent(line));
                holo.setCustomNameVisible(true);
                holo.setGravity(false);
                holo.setCollidable(false);
                this.holoStands.add(holo);
            }

            if (displaySeconds > 0) {
                Bukkit.getServer().getScheduler().runTaskLater(ReMinions.getPlugin(), () -> {
                    for (ArmorStand holo : this.holoStands) {
                        if (!holo.isDead()) holo.remove();
                    }
                    this.holoStands.clear();
                }, displaySeconds * 20L);
            } else {
                this.isPermanent = true;
            }
        }
    }

    public void despawn() {
        this.holoStands.forEach(Entity::remove);
        this.holoStands.clear();
        this.stand.remove();
    }

    public MinionSkinConfig getSkinConfig()    { return this.skinConfig; }
    public ArmorStand getStand()               { return this.stand; }
    public List<ArmorStand> getHoloStands()    { return this.holoStands; }
    public boolean isPermanent()               { return this.isPermanent; }
}
