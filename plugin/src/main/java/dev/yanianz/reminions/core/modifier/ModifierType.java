package dev.yanianz.reminions.core.modifier;

public enum ModifierType {
   SPEED,
   RADIUS,
   ITEM_UPGRADES,
   STORAGE,
   AUTO_SELL,
   /** Boosts the per-roll chance of a {@link dev.yanianz.reminions.core.product.Product}'s rare drop. */
   LUCK,
   /** Multiplies the total output amount produced per work cycle. */
   PRODUCTION_BOOST,
   /** Multiplies EcoSkills XP gained on produce/take events. */
   EXP_BOOST;
}
