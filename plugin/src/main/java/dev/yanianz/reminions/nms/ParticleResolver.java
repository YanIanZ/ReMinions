package dev.yanianz.reminions.nms;

import org.bukkit.Particle;

/**
 * Resolves a {@link Particle} from a list of candidate enum names. Used by version-bucketed
 * NMS adapters to handle the 1.20.5 particle rename (e.g. {@code VILLAGER_HAPPY} →
 * {@code HAPPY_VILLAGER}) without needing per-version compile classpaths.
 *
 * <p>Returns the first candidate that exists on the running server's {@link Particle} enum.
 * If nothing matches, returns {@code null} — callers may fall back to whatever stable
 * particle they prefer.</p>
 */
public final class ParticleResolver {

    private ParticleResolver() {}

    public static Particle resolve(String... candidates) {
        for (String name : candidates) {
            if (name == null) continue;
            try {
                return Particle.valueOf(name);
            } catch (IllegalArgumentException ignored) {
                // candidate doesn't exist on this server — try the next one
            }
        }
        return null;
    }
}
