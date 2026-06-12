package dev.yanianz.reminions.core.minion;

import java.util.Map;
import dev.yanianz.reminions.core.product.Product;

public record MinionUpgrade(String id, String minionId, int level, double productionSpeed, int radius, int maxStorage, MinionUpgrade.Requirement requirement) {
   public record ItemRequirement(Product product, String display, int amount) {
   }

   public record Requirement(Map<Character, MinionUpgrade.ItemRequirement> items, char[] shape) {
   }
}
