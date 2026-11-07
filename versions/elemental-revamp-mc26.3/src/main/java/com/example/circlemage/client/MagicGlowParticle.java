package com.example.circlemage.client;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.Mth;

/**
 * A flexible, full-bright billboard particle used for every Circle Mage spell effect. Behaviour
 * (colour tint, size curve, gravity, drift, lifetime, spin, fade-in) is driven entirely by a
 * {@link Settings} record, so one class backs all the custom particle types.
 */
public class MagicGlowParticle extends SingleQuadParticle {

    /**
     * @param r,g,b        colour multiplier applied to the texture (use 1,1,1 to keep a pre-coloured texture)
     * @param scaleStart   render size at spawn
     * @param scaleEnd     render size at death (interpolated over the lifetime)
     * @param gravity      downward pull (negative = rises)
     * @param rise         constant vertical velocity added at spawn
     * @param jitter       random spread added to the spawn velocity
     * @param lifeMin/Max  lifetime range in ticks
     * @param spin         radians of rotation per tick
     * @param fadeIn       fade alpha up over the first fifth of life (otherwise start opaque)
     */
    public record Settings(float r, float g, float b, float scaleStart, float scaleEnd,
                           float gravity, float rise, float jitter, int lifeMin, int lifeMax,
                           float spin, boolean fadeIn) {
    }

    private final float scaleStart;
    private final float scaleEnd;
    private final float spin;
    private final boolean fadeIn;

    MagicGlowParticle(ClientLevel world, double x, double y, double z,
                      double vx, double vy, double vz, Settings s, SpriteSet sprites) {
        super(world, x, y, z, sprites.get(net.minecraft.util.RandomSource.create()));
        this.setSprite(sprites.get(this.random));
        this.lifetime = s.lifeMin() + this.random.nextInt(Math.max(1, s.lifeMax() - s.lifeMin() + 1));
        this.gravity = s.gravity();
        this.hasPhysics = false;
        this.setColor(s.r(), s.g(), s.b());
        this.scaleStart = s.scaleStart();
        this.scaleEnd = s.scaleEnd();
        this.quadSize = s.scaleStart();
        this.spin = s.spin();
        this.fadeIn = s.fadeIn();
        this.alpha = s.fadeIn() ? 0.0f : 1.0f;

        this.xd = vx + (this.random.nextDouble() - 0.5) * s.jitter();
        this.yd = vy + s.rise() + (this.random.nextDouble() - 0.5) * s.jitter();
        this.zd = vz + (this.random.nextDouble() - 0.5) * s.jitter();

        this.roll = this.random.nextFloat() * (float) Math.PI * 2.0f;
        this.oRoll = this.roll;
    }

    @Override
    protected SingleQuadParticle.Layer getLayer() {
        return SingleQuadParticle.Layer.TRANSLUCENT;
    }

    @Override
    protected int getLightCoords(float tint) {
        // Full-bright: magic should glow regardless of ambient light.
        return 0xF000F0;
    }

    @Override
    public void tick() {
        this.oRoll = this.roll;
        super.tick();
        if (this.removed) {
            return;
        }
        this.roll += this.spin;

        float life = (float) this.age / (float) this.lifetime;
        this.quadSize = Mth.lerp(life, this.scaleStart, this.scaleEnd);
        if (this.fadeIn && life < 0.2f) {
            this.alpha = life / 0.2f;
        } else {
            this.alpha = Mth.clamp((1.0f - life) / 0.8f, 0.0f, 1.0f);
        }
    }

    /** Binds one set of {@link Settings} to a sprite atlas entry for a registered particle type. */
    public static class Factory implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprites;
        private final Settings settings;

        public Factory(SpriteSet sprites, Settings settings) {
            this.sprites = sprites;
            this.settings = settings;
        }

        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel world, double x, double y, double z,
                                       double vx, double vy, double vz, net.minecraft.util.RandomSource random) {
            return new MagicGlowParticle(world, x, y, z, vx, vy, vz, this.settings, this.sprites);
        }
    }
}
