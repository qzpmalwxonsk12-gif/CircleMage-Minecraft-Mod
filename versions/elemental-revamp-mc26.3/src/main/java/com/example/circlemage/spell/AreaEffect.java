package com.example.circlemage.spell;

import com.example.circlemage.mana.ManaPlayer;
import com.example.circlemage.particle.ModParticles;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * A lingering, circular ground effect (fireball fire-field or area-slow field). Each server tick it
 * affects entities within its radius and draws a particle boundary so players can see its edge.
 */
public class AreaEffect {
    public net.minecraft.world.level.Level world() { return world; }

    public enum Type {
        FIRE,
        SLOW
    }

    private final ServerLevel world;
    private final Vec3 center;
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

    public AreaEffect(ServerLevel world, Vec3 center, double radius, Type type, int durationTicks) {
        this(world, center, radius, type, durationTicks, 0, 0.0f);
    }

    public AreaEffect(ServerLevel world, Vec3 center, double radius, Type type, int durationTicks,
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
    public boolean contains(Vec3 pos) {
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
        AABB box = new AABB(
                center.x - radius, center.y - radius, center.z - radius,
                center.x + radius, center.y + radius, center.z + radius);

        for (Entity entity : world.getEntities((Entity)null, box)) {
            double dx = entity.getX() - center.x;
            double dz = entity.getZ() - center.z;
            if ((dx * dx + dz * dz) > (radius * radius)) {
                continue;
            }
            // The excluded entity (e.g. the Fire Escape caster) is never affected.
            if (excludedUuid != null && entity.getUUID().equals(excludedUuid)) {
                continue;
            }
            // Players with anti-magic immunity are unaffected.
            if (entity instanceof ServerPlayer player
                    && ManaPlayer.get(player).isImmune(world.getGameTime())) {
                continue;
            }

            if (type == Type.FIRE) {
                entity.igniteForSeconds(3);
                // Enhanced fireball: extra magic damage once per second.
                if (fireBonusDamage > 0.0f && world.getGameTime() % 20 == 0 && entity instanceof LivingEntity living) {
                    living.hurt(world.damageSources().magic(), fireBonusDamage);
                }
            } else if (type == Type.SLOW && entity instanceof LivingEntity living) {
                // Slowness (amplifier configurable), refreshed each tick so it persists only while inside.
                living.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 40, slowAmplifier, false, true, true));
                if (poison && world.getGameTime() % 25 == 0) {
                    living.addEffect(new MobEffectInstance(MobEffects.POISON, 40, 0, false, true, true));
                }
            }
        }
    }

    private void drawBoundary() {
        // Throttle particle spawns to keep things light.
        if (world.getGameTime() % 3 != 0) {
            return;
        }

        SimpleParticleType boundaryParticle = type == Type.FIRE ? ModParticles.EMBER : ModParticles.FROST;
        int points = Math.max(16, (int) (radius * 6));
        for (int i = 0; i < points; i++) {
            double angle = (Math.PI * 2.0 * i) / points;
            double px = center.x + Math.cos(angle) * radius;
            double pz = center.z + Math.sin(angle) * radius;
            world.sendParticles(boundaryParticle, px, center.y + 0.2, pz, 1, 0.0, 0.02, 0.0, 0.0);
        }

        // A few interior particles so the filled area reads as well.
        for (int i = 0; i < 6; i++) {
            double r = radius * Math.sqrt(world.getRandom().nextDouble());
            double angle = world.getRandom().nextDouble() * Math.PI * 2.0;
            double px = center.x + Math.cos(angle) * r;
            double pz = center.z + Math.sin(angle) * r;
            world.sendParticles(boundaryParticle, px, center.y + 0.2, pz, 1, 0.0, 0.0, 0.0, 0.0);
        }
    }
}
