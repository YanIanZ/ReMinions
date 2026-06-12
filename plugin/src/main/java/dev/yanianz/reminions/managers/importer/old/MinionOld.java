package dev.yanianz.reminions.managers.importer.old;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.inventory.ItemStack;

public record MinionOld(
   UUID internal,
   UUID owner,
   String id,
   int level,
   double timeBetweenAction,
   int productionRadius,
   int maxStorage,
   int maxSlots,
   double resourceGenerated,
   Map<String, Object> position,
   List<ItemStack> storage,
   Map<String, List<ModifierDataOld>> modifiers,
   String skin,
   boolean lastPositionCorrectly,
   Map<String, Integer> unlockMinions
) {
   public Location convertLocation() {
      return new Location(
         Bukkit.getWorld(this.position.get("world").toString()),
         Double.parseDouble(this.position.get("x").toString()),
         Double.parseDouble(this.position.get("y").toString()),
         Double.parseDouble(this.position.get("z").toString())
      );
   }
}
