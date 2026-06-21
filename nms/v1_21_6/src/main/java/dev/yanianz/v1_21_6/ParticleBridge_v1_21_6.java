package dev.yanianz.v1_21_6;

import dev.yanianz.reminions.nms.ApiBackedNmsAdapter;
import dev.yanianz.reminions.nms.ParticleBridge;
import org.bukkit.Particle;

/** Particle bridge for 1.21.6 – 1.21.7. Particle names resolved at runtime via the shared adapter. */
public final class ParticleBridge_v1_21_6 implements ParticleBridge {
    @Override public Particle cropGrowParticle()  { return ApiBackedNmsAdapter.PARTICLE_BRIDGE.cropGrowParticle();  }
    @Override public Particle magicParticle()     { return ApiBackedNmsAdapter.PARTICLE_BRIDGE.magicParticle();     }
    @Override public Particle explosionParticle() { return ApiBackedNmsAdapter.PARTICLE_BRIDGE.explosionParticle(); }
    @Override public Particle splashParcicle()    { return ApiBackedNmsAdapter.PARTICLE_BRIDGE.splashParcicle();    }
    @Override public Particle dustParticle()      { return ApiBackedNmsAdapter.PARTICLE_BRIDGE.dustParticle();      }
    @Override public Particle entityParticle()    { return ApiBackedNmsAdapter.PARTICLE_BRIDGE.entityParticle();    }
    @Override public Particle bubbleParticle()    { return ApiBackedNmsAdapter.PARTICLE_BRIDGE.bubbleParticle();    }
}
