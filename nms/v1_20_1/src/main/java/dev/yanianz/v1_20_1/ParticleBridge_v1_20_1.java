package dev.yanianz.v1_20_1;

import dev.yanianz.reminions.nms.ParticleBridge;
import org.bukkit.Particle;

public final class ParticleBridge_v1_20_1 implements ParticleBridge {
   @Override
   public Particle cropGrowParticle() {
      return Particle.VILLAGER_HAPPY;
   }

   @Override
   public Particle magicParticle() {
      return Particle.SPELL_MOB;
   }

   @Override
   public Particle explosionParticle() {
      return Particle.EXPLOSION_NORMAL;
   }

   @Override
   public Particle splashParcicle() {
      return Particle.WATER_SPLASH;
   }

   @Override
   public Particle dustParticle() {
      return Particle.REDSTONE;
   }

   @Override
   public Particle entityParticle() {
      return Particle.VILLAGER_ANGRY;
   }

   @Override
   public Particle bubbleParticle() {
      return Particle.BUBBLE_POP;
   }
}
