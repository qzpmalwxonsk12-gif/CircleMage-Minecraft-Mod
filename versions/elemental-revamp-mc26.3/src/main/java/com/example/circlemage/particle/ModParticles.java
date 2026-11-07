package com.example.circlemage.particle;

import com.example.circlemage.CircleMageMod;
import net.fabricmc.fabric.api.particle.v1.FabricParticleTypes;
import net.minecraft.core.Registry;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;

/**
 * Custom particle types used by Circle Mage spells. Each is backed by a hand-made texture in
 * {@code assets/circlemage/textures/particle/} (referenced from {@code assets/circlemage/particles/}).
 * Registered in the common {@code PARTICLE_TYPE} registry so the server can spawn them and every
 * nearby client renders them through the factories registered in the client initializer.
 */
public final class ModParticles {

    /** Cyan arcane sparkle — magic missile trail, channelling aura, generic arcana. */
    public static final SimpleParticleType ARCANE = reg("arcane");
    /** Bright white-cyan mote — magic missile core sparks, impact bursts. */
    public static final SimpleParticleType ARCANE_TRAIL = reg("arcane_trail");
    /** Rising fire ember — fireball field, fire array. */
    public static final SimpleParticleType EMBER = reg("ember");
    /** Falling frost crystal — area-slow field. */
    public static final SimpleParticleType FROST = reg("frost");
    /** Electric spark — thunder. */
    public static final SimpleParticleType SPARK = reg("spark");
    /** Wind wisp — wall of wind, wind array. */
    public static final SimpleParticleType GALE = reg("gale");
    /** White-gold radiance — anti-magic surge. */
    public static final SimpleParticleType RADIANCE = reg("radiance");
    /** Cyan hex shard — barrier shimmer, shatter. */
    public static final SimpleParticleType BARRIER_SHARD = reg("barrier_shard");
    /** Spinning element runes for carved arrays. */
    public static final SimpleParticleType RUNE_FIRE = reg("rune_fire");
    public static final SimpleParticleType RUNE_THUNDER = reg("rune_thunder");
    public static final SimpleParticleType RUNE_WIND = reg("rune_wind");
    public static final SimpleParticleType RUNE_BIND = reg("rune_bind");
    /** Purple mote — bind array trigger. */
    public static final SimpleParticleType BIND_MOTE = reg("bind_mote");
    /** Gold-white spark — Spear of Light trail and impact. */
    public static final SimpleParticleType LIGHT = reg("light");
    /** Dark violet void mote — Dark Orb trail, explosion, oil-and-water absorb. */
    public static final SimpleParticleType VOID = reg("void");
    /** Grey smoke puff — Hollow Purple aftermath cloud. */
    public static final SimpleParticleType SMOKE = reg("smoke");
    /** Tan debris mote — Hollow Purple dust kicked up by the blast. */
    public static final SimpleParticleType DUST = reg("dust");

    private ModParticles() {
    }

    private static SimpleParticleType reg(String name) {
        return Registry.register(BuiltInRegistries.PARTICLE_TYPE,
                Identifier.fromNamespaceAndPath(CircleMageMod.MOD_ID, name), FabricParticleTypes.simple());
    }

    /** Force class-load so the static registrations above run. */
    public static void register() {
    }
}
