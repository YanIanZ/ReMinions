package dev.yanianz.reminions.booster;

import java.util.UUID;

/**
 * A source of player-scoped multiplier boosts. Implementations typically wrap a third-party
 * booster plugin (PrisonBoosters, Boosters by Zrips, EcoBoosters, ...) via reflection so
 * ReMinions does not require a compile-time dependency on each one.
 *
 * <p>A returned multiplier of {@code 1.0} means "no change". Returning {@code 1.0} from
 * {@link #getMultiplier} is the safe default when the kind is not supported by this provider.
 */
public interface BoosterProvider {

    /** Unique identifier used in config (e.g. "prisonboosters", "zrips_boosters", "eco"). */
    String id();

    /** Returns true when the backing plugin is currently available and queryable. */
    boolean isAvailable();

    /**
     * Returns the multiplier for the given player and boost kind. {@code 1.0} means "no boost".
     * Implementations should swallow reflection errors and return {@code 1.0} on failure.
     */
    double getMultiplier(UUID player, BoostKind kind);
}
