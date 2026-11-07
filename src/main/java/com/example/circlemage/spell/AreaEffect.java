package com.example.circlemage.spell;

import com.example.circlemage.mana.ManaPlayer;
import com.example.circlemage.particle.ModParticles;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.particle.DefaultParticleType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

/**
 * A lingering, circular ground effect (fireball fire-field or area-slow field). Each server tick it
 * affects entities within its radius and draws a particle boundary so players can see its edge.
 */
public class AreaEffect {

    public enum Type {
        FIRE,
        SLOW
    }

    private final ServerWorld world;
    private final Vec3d center;
    private final double radius;
    private final Type type;
    private int remainingTicks;
    /** Slowness amplifier for SLOW fields (0 = Slowness I, 1 = Slowness II). */
    private final int slowAmplifier;
    /** Extra magic damage per second for FIRE fields (enhanced fireball). */
    private final float fireBonusDamage;
    /** When true, SLOW fields also apply Poison (used by the Dark Orb's active-chant burst). */
    private boolean poison = false;
    /** Optional entity (by UUID) that this field never affects — e.g. the caster of Fire Escape. */
    private java.util.UUID excludedUuid = null;

    public AreaEffect(ServerWorld world, Vec3d center, double radius, Type type, int durationTicks) {
        this(world, center, radius, type, durationTicks, 0, 0.0f);
    }

    public AreaEffect(ServerWorld world, Vec3d center, double radius, Type type, int durationTicks,
                      int slowAmplifier, float fireBonusDamage) {
        this.world = world;
        this.center = center;
        this.radius = radius;
        this.type = type;
        this.remainingTicks = durationTicks;
        this.slowAmplifier = slowAmplifier;
        this.fireBonusDamage = fireBonusDamage;
    }

    /** Makes a SLOW field also apply Poison each second. Returns {@code this} for chaining. */
    public AreaEffect setPoison(boolean poison) {
        this.poison = poison;
        return this;
    }

    /** This field will never affect the given entity (e.g. the caster of Fire Escape). */
    public AreaEffect setExcluded(java.util.UUID uuid) {
        this.excludedUuid = uuid;
        return this;
    }

    public boolean isExpired() {
        return remainingTicks <= 0;
    }

    /** True if the given position lies within this effect's cylinder (radius horizontally, ±radius vertically). */
    public boolean contains(Vec3d pos) {
        double dx = pos.x - center.x;
        double dz = pos.z - center.z;
        double dy = Math.abs(pos.y - center.y);
        return (dx * dx + dz * dz) <= (radius * radius) && dy <= radius;
    }

    public void tick() {
        remainingTicks--;
        applyToEntities();
        drawBoundary();
    }

    private void applyToEntities() {
        Box box = new Box(
                center.x - radius, center.y - radius, center.z - radius,
                center.x + radius, center.y + radius, center.z + radius);

        for (Entity entity : world.getOtherEntities(null, box)) {
            double dx = entity.getX() - center.x;
            double dz = entity.getZ() - center.z;
            if ((dx * dx + dz * dz) > (radius * radius)) {
                continue;
            }
            // The excluded entity (e.g. the Fire Escape caster) is never affected.
            if (excludedUuid != null && entity.getUuid().equals(excludedUuid)) {
                continue;
            }
            // Players with anti-magic immunity are unaffected.
            if (entity instanceof ServerPlayerEntity player
                    && ManaPlayer.get(player).isImmune(world.getTime())) {
                continue;
            }

            if (type == Type.FIRE) {
                entity.setOnFireFor(3);
                // Enhanced fireball: extra magic damage once per second.
                if (fireBonusDamage > 0.0f && world.getTime() % 20 == 0 && entity instanceof LivingEntity living) {
                    living.damage(world.getDamageSources().magic(), fireBonusDamage);
                }
            } else if (type == Type.SLOW && entity instanceof LivingEntity living) {
                // Slowness (amplifier configurable), refreshed each tick so it persists only while inside.
                living.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 40, slowAmplifier, false, true, true));
                if (poison && world.getTime() % 25 == 0) {
                    living.addStatusEffect(new StatusEffectInstance(StatusEffects.POISON, 40, 0, false, true, true));
                }
            }
        }
    }

    private void drawBoundary() {
        // Throttle particle spawns to keep things light.
        if (world.getTime() % 3 != 0) {
            return;
        }

        DefaultParticleType boundaryParticle = type == Type.FIRE ? ModParticles.EMBER : ModParticles.FROST;
        int points = Math.max(16, (int) (radius * 6));
        for (int i = 0; i < points; i++) {
            double angle = (Math.PI * 2.0 * i) / points;
            double px = center.x + Math.cos(angle) * radius;
            double pz = center.z + Math.sin(angle) * radius;
            world.spawnParticles(boundaryParticle, px, center.y + 0.2, pz, 1, 0.0, 0.02, 0.0, 0.0);
        }

        // A few interior particles so the filled area reads as well.
        for (int i = 0; i < 6; i++) {
            double r = radius * Math.sqrt(world.random.nextDouble());
            double angle = world.random.nextDouble() * Math.PI * 2.0;
            double px = center.x + Math.cos(angle) * r;
            double pz = center.z + Math.sin(angle) * r;
            world.spawnParticles(boundaryParticle, px, center.y + 0.2, pz, 1, 0.0, 0.0, 0.0, 0.0);
        }
    }
}
