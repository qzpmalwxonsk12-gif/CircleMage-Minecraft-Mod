package com.example.circlemage.particle;

import com.example.circlemage.CircleMageMod;
import net.fabricmc.fabric.api.particle.v1.FabricParticleTypes;
import net.minecraft.particle.DefaultParticleType;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

/**
 * Custom particle types used by Circle Mage spells. Each is backed by a hand-made texture in
 * {@code assets/circlemage/textures/particle/} (referenced from {@code assets/circlemage/particles/}).
 * Registered in the common {@code PARTICLE_TYPE} registry so the server can spawn them and every
 * nearby client renders them through the factories registered in the client initializer.
 */
public final class ModParticles {

    /** Cyan arcane sparkle — magic missile trail, channelling aura, generic arcana. */
    public static final DefaultParticleType ARCANE = reg("arcane");
    /** Bright white-cyan mote — magic missile core sparks, impact bursts. */
    public static final DefaultParticleType ARCANE_TRAIL = reg("arcane_trail");
    /** Rising fire ember — fireball field, fire array. */
    public static final DefaultParticleType EMBER = reg("ember");
    /** Falling frost crystal — area-slow field. */
    public static final DefaultParticleType FROST = reg("frost");
    /** Electric spark — thunder. */
    public static final DefaultParticleType SPARK = reg("spark");
    /** Wind wisp — wall of wind, wind array. */
    public static final DefaultParticleType GALE = reg("gale");
    /** White-gold radiance — anti-magic surge. */
    public static final DefaultParticleType RADIANCE = reg("radiance");
    /** Cyan hex shard — barrier shimmer, shatter. */
    public static final DefaultParticleType BARRIER_SHARD = reg("barrier_shard");
    /** Spinning element runes for carved arrays. */
    public static final DefaultParticleType RUNE_FIRE = reg("rune_fire");
    public static final DefaultParticleType RUNE_THUNDER = reg("rune_thunder");
    public static final DefaultParticleType RUNE_WIND = reg("rune_wind");
    public static final DefaultParticleType RUNE_BIND = reg("rune_bind");
    /** Purple mote — bind array trigger. */
    public static final DefaultParticleType BIND_MOTE = reg("bind_mote");
    /** Gold-white spark — Spear of Light trail and impact. */
    public static final DefaultParticleType LIGHT = reg("light");
    /** Dark violet void mote — Dark Orb trail, explosion, oil-and-water absorb. */
    public static final DefaultParticleType VOID = reg("void");
    /** Grey smoke puff — Hollow Purple aftermath cloud. */
    public static final DefaultParticleType SMOKE = reg("smoke");
    /** Tan debris mote — Hollow Purple dust kicked up by the blast. */
    public static final DefaultParticleType DUST = reg("dust");

    private ModParticles() {
    }

    private static DefaultParticleType reg(String name) {
        return Registry.register(Registries.PARTICLE_TYPE,
                new Identifier(CircleMageMod.MOD_ID, name), FabricParticleTypes.simple());
    }

    /** Force class-load so the static registrations above run. */
    public static void register() {
    }
}
