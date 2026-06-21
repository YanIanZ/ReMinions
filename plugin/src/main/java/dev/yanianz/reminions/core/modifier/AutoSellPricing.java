package dev.yanianz.reminions.core.modifier;

import dev.yanianz.reminions.ReMinions;
import dev.yanianz.reminions.config.ModifierConfig;
import dev.yanianz.reminions.core.minion.MinionModifierData;

/**
 * Computes the effective sell-price multiplier contributed by an auto-sell modifier.
 *
 * <p>The yml {@code multiplier} on an auto-sell modifier is interpreted as a positive bonus
 * (e.g. {@code 0.50} → sells for 1.5× base). The effective multiplier is clamped to
 * {@link #MAX_MULTIPLIER} so a misconfigured modifier can never grant infinite revenue.</p>
 */
public final class AutoSellPricing {

    /** Hard cap on the effective auto-sell price multiplier. */
    public static final double MAX_MULTIPLIER = 1.5;

    private AutoSellPricing() {}

    /**
     * Returns the per-item price multiplier for the given auto-sell modifier instance, clamped
     * to {@link #MAX_MULTIPLIER}. Returns {@code 1.0} when {@code data} is {@code null} or the
     * referenced modifier config has been removed at runtime.
     */
    public static double effectiveMultiplier(MinionModifierData data) {
        if (data == null) return 1.0;
        ModifierConfig modConfig = ReMinions.getPlugin().getModifierManager().get(data.getName());
        double bonus = modConfig == null ? 0.0 : modConfig.value();
        double effective = 1.0 + Math.max(0.0, bonus);
        return Math.min(MAX_MULTIPLIER, effective);
    }
}
