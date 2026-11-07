package com.example.circlemage.client;

import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleFactory;
import net.minecraft.client.particle.ParticleTextureSheet;
import net.minecraft.client.particle.SpriteBillboardParticle;
import net.minecraft.client.particle.SpriteProvider;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.particle.DefaultParticleType;
import net.minecraft.util.math.MathHelper;

/**
 * A flexible, full-bright billboard particle used for every Circle Mage spell effect. Behaviour
 * (colour tint, size curve, gravity, drift, lifetime, spin, fade-in) is driven entirely by a
 * {@link Settings} record, so one class backs all the custom particle types.
 */
public class MagicGlowParticle extends SpriteBillboardParticle {

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

    MagicGlowParticle(ClientWorld world, double x, double y, double z,
                      double vx, double vy, double vz, Settings s, SpriteProvider sprites) {
        super(world, x, y, z);
        this.setSprite(sprites.getSprite(this.random));
        this.maxAge = s.lifeMin() + this.random.nextInt(Math.max(1, s.lifeMax() - s.lifeMin() + 1));
        this.gravityStrength = s.gravity();
        this.collidesWithWorld = false;
        this.setColor(s.r(), s.g(), s.b());
        this.scaleStart = s.scaleStart();
        this.scaleEnd = s.scaleEnd();
        this.scale = s.scaleStart();
        this.spin = s.spin();
        this.fadeIn = s.fadeIn();
        this.alpha = s.fadeIn() ? 0.0f : 1.0f;

        this.velocityX = vx + (this.random.nextDouble() - 0.5) * s.jitter();
        this.velocityY = vy + s.rise() + (this.random.nextDouble() - 0.5) * s.jitter();
        this.velocityZ = vz + (this.random.nextDouble() - 0.5) * s.jitter();

        this.angle = this.random.nextFloat() * (float) Math.PI * 2.0f;
        this.prevAngle = this.angle;
    }

    @Override
    public ParticleTextureSheet getType() {
        return ParticleTextureSheet.PARTICLE_SHEET_TRANSLUCENT;
    }

    @Override
    public int getBrightness(float tint) {
        // Full-bright: magic should glow regardless of ambient light.
        return 0xF000F0;
    }

    @Override
    public void tick() {
        this.prevAngle = this.angle;
        super.tick();
        if (this.dead) {
            return;
        }
        this.angle += this.spin;

        float life = (float) this.age / (float) this.maxAge;
        this.scale = MathHelper.lerp(life, this.scaleStart, this.scaleEnd);
        if (this.fadeIn && life < 0.2f) {
            this.alpha = life / 0.2f;
        } else {
            this.alpha = MathHelper.clamp((1.0f - life) / 0.8f, 0.0f, 1.0f);
        }
    }

    /** Binds one set of {@link Settings} to a sprite atlas entry for a registered particle type. */
    public static class Factory implements ParticleFactory<DefaultParticleType> {
        private final SpriteProvider sprites;
        private final Settings settings;

        public Factory(SpriteProvider sprites, Settings settings) {
            this.sprites = sprites;
            this.settings = settings;
        }

        @Override
        public Particle createParticle(DefaultParticleType type, ClientWorld world, double x, double y, double z,
                                       double vx, double vy, double vz) {
            return new MagicGlowParticle(world, x, y, z, vx, vy, vz, this.settings, this.sprites);
        }
    }
}
