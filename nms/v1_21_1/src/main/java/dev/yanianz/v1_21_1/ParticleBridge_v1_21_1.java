package dev.yanianz.v1_21_1;

import dev.yanianz.reminions.nms.ParticleBridge;
import org.bukkit.Particle;

public final class ParticleBridge_v1_21_1 implements ParticleBridge {
   @Override
   public Particle cropGrowParticle() {
      return Particle.HAPPY_VILLAGER;
   }

   @Override
   public Particle magicParticle() {
      return Particle.EFFECT;
   }

   @Override
   public Particle explosionParticle() {
      return Particle.EXPLOSION;
   }

   @Override
   public Particle splashParcicle() {
      return Particle.SPLASH;
   }

   @Override
   public Particle dustParticle() {
      return Particle.DUST;
   }

   @Override
   public Particle entityParticle() {
      return Particle.ANGRY_VILLAGER;
   }

   @Override
   public Particle bubbleParticle() {
      return Particle.BUBBLE;
   }
}
