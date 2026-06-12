package dev.yanianz.reminions.config;

import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.entity.EntityType;

public record AnimationConfig(
        Material beforePlaceTree,
        Material leaveTree,
        Material logTree,
        Material fruitTree,
        int heightLog,
        Material block,
        Material crop,
        Particle cropParticle,
        EntityType entityKill,
        Particle entityParticleKill,
        EntityType entityCatch,
        Particle entityParticleCatch
) {
    public AnimationConfig(Material beforePlaceTree, Material leaveTree, Material logTree,
                           Material fruitTree, int heightLog) {
        this(beforePlaceTree, leaveTree, logTree, fruitTree, heightLog, null, null, null, null, null, null, null);
    }

    public AnimationConfig(Material block) {
        this(null, null, null, null, 0, block, null, null, null, null, null, null);
    }

    public AnimationConfig(Material crop, Particle cropParticle) {
        this(null, null, null, null, 0, null, crop, cropParticle, null, null, null, null);
    }

    public AnimationConfig(EntityType entityKill, Particle entityParticleKill) {
        this(null, null, null, null, 0, null, null, null, entityKill, entityParticleKill, null, null);
    }

    /** isCatch=true routes entity+particle to the entityCatch/entityParticleCatch fields. */
    public AnimationConfig(EntityType entityCatch, Particle entityParticleCatch, boolean isCatch) {
        this(null, null, null, null, 0, null, null, null, null, null, entityCatch, entityParticleCatch);
    }
}
