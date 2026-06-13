package dev.yanianz.reminions.booster;

/**
 * Categories of boosts a {@link BoosterProvider} can supply for a minion's owner.
 * Multipliers are applied multiplicatively on top of the minion's own modifier values
 * (so a 1.0 multiplier means "no change", 2.0 means "double").
 */
public enum BoostKind {
    SPEED,
    PRODUCTION,
    EXP,
    SELL_PRICE
}
