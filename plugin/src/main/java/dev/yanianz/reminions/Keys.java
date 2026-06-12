package dev.yanianz.reminions;

import org.bukkit.NamespacedKey;

/**
 * Centralised pre-allocated {@link NamespacedKey} constants. Avoids allocating /
 * regex-validating a fresh key on every PDC lookup in hot paths.
 */
public final class Keys {
    private Keys() {}

    public static final String NAMESPACE = "reminions";

    public static final NamespacedKey MINION_ARMORSTAND      = new NamespacedKey(NAMESPACE, "minion_armorstand");
    public static final NamespacedKey ENTITY_MINION          = new NamespacedKey(NAMESPACE, "entity_minion");
    public static final NamespacedKey ITEM_ID                = new NamespacedKey(NAMESPACE, "item_id");
    public static final NamespacedKey MINION_INVENTORY_ITEM  = new NamespacedKey(NAMESPACE, "minion_inventory_item");

    public static NamespacedKey of(String key) {
        return new NamespacedKey(NAMESPACE, key);
    }
}
