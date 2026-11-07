package com.example.circlemage.spell;

import com.example.circlemage.mana.ManaPlayer;
import com.example.circlemage.particle.ModParticles;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.LightningEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.particle.DefaultParticleType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import java.util.List;

/**
 * A carved magic array: a visible 5-block-radius rune circle on the ground that fires its selected
 * effect when a living entity steps inside. It re-arms every 2 seconds and expires after its lifetime.
 */
public class MagicArray {

    private static final double RADIUS = 5.0;
    private static final int REARM_TICKS = 40; // 2s between triggers

    private final ServerWorld world;
    private final Vec3d center;
    private final ArrayType type;
    private int remainingTicks;
    private long nextTriggerTick;

    public MagicArray(ServerWorld world, Vec3d center, ArrayType type, int durationTicks) {
        this.world = world;
        this.center = center;
        this.type = type;
        this.remainingTicks = durationTicks;
        // Arm after a short delay so the caster can step off first.
        this.nextTriggerTick = world.getTime() + 20;
    }

    public boolean isExpired() {
        return remainingTicks <= 0;
    }

    public void tick() {
        remainingTicks--;
        drawCircle();
        maybeTrigger();
    }

    private List<LivingEntity> entitiesInside() {
        Box box = new Box(
                center.x - RADIUS, center.y - 2.0, center.z - RADIUS,
                center.x + RADIUS, center.y + 3.0, center.z + RADIUS);
        return world.getEntitiesByClass(LivingEntity.class, box, living -> {
            double dx = living.getX() - center.x;
            double dz = living.getZ() - center.z;
            return (dx * dx + dz * dz) <= (RADIUS * RADIUS) && living.isAlive();
        });
    }

    private void maybeTrigger() {
        if (world.getTime() < nextTriggerTick) {
            return;
        }
        List<LivingEntity> inside = entitiesInside();
        if (inside.isEmpty()) {
            return;
        }
        nextTriggerTick = world.getTime() + REARM_TICKS;
        applyEffect(inside);
    }

    private void applyEffect(List<LivingEntity> inside) {
        switch (type) {
            case FIRE -> {
                for (LivingEntity living : inside) {
                    if (isImmune(living)) {
                        continue;
                    }
                    living.setOnFireFor(5);
                }
                world.spawnParticles(ModParticles.EMBER, center.x, center.y + 0.4, center.z, 50, RADIUS / 2, 0.3, RADIUS / 2, 0.04);
            }
            case THUNDER -> {
                LightningEntity bolt = EntityType.LIGHTNING_BOLT.create(world);
                if (bolt != null) {
                    bolt.refreshPositionAfterTeleport(center.x, center.y, center.z);
                    world.spawnEntity(bolt);
                }
                world.spawnParticles(ModParticles.SPARK, center.x, center.y + 0.5, center.z, 40, RADIUS / 2, 0.6, RADIUS / 2, 0.15);
            }
            case WIND -> {
                for (LivingEntity living : inside) {
                    Vec3d push = living.getPos().subtract(center);
                    push = new Vec3d(push.x, 0, push.z);
                    if (push.lengthSquared() < 1.0e-4) {
                        push = new Vec3d(world.random.nextDouble() - 0.5, 0, world.random.nextDouble() - 0.5);
                    }
                    push = push.normalize().multiply(2.0).add(0, 0.5, 0);
                    living.addVelocity(push.x, push.y, push.z);
                    living.velocityModified = true;
                }
                world.spawnParticles(ModParticles.GALE, center.x, center.y + 0.5, center.z, 40, RADIUS / 2, 0.4, RADIUS / 2, 0.12);
            }
            case BIND -> {
                for (LivingEntity living : inside) {
                    if (isImmune(living)) {
                        continue;
                    }
                    // Slowness IV (amplifier 3) for 3 seconds.
                    living.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 60, 3, false, true, true));
                }
                world.spawnParticles(ModParticles.BIND_MOTE, center.x, center.y + 0.4, center.z, 40, RADIUS / 2, 0.3, RADIUS / 2, 0.05);
            }
        }
    }

    private boolean isImmune(LivingEntity living) {
        return living instanceof ServerPlayerEntity player
                && ManaPlayer.get(player).isImmune(world.getTime());
    }

    private void drawCircle() {
        long t = world.getTime();
        if (t % 4 != 0) {
            return;
        }
        DefaultParticleType element = switch (type) {
            case FIRE -> ModParticles.EMBER;
            case THUNDER -> ModParticles.SPARK;
            case WIND -> ModParticles.GALE;
            case BIND -> ModParticles.BIND_MOTE;
        };
        double y = center.y + 0.15;

        // Outer ring + a concentric inner ring trace the array boundary with element motes.
        int points = 48;
        for (int i = 0; i < points; i++) {
            double angle = (Math.PI * 2.0 * i) / points;
            world.spawnParticles(element, center.x + Math.cos(angle) * RADIUS, y, center.z + Math.sin(angle) * RADIUS,
                    1, 0.0, 0.0, 0.0, 0.0);
        }
        for (int i = 0; i < points / 2; i++) {
            double angle = (Math.PI * 2.0 * i) / (points / 2.0);
            world.spawnParticles(element, center.x + Math.cos(angle) * RADIUS * 0.62, y, center.z + Math.sin(angle) * RADIUS * 0.62,
                    1, 0.0, 0.0, 0.0, 0.0);
        }

        // Slowly-rotating rune glyphs (large, custom textures) make it read as a carved magic array.
        if (t % 12 == 0) {
            DefaultParticleType rune = switch (type) {
                case FIRE -> ModParticles.RUNE_FIRE;
                case THUNDER -> ModParticles.RUNE_THUNDER;
                case WIND -> ModParticles.RUNE_WIND;
                case BIND -> ModParticles.RUNE_BIND;
            };
            int glyphs = 6;
            double rot = (t % 240) * (Math.PI * 2.0 / 240.0);
            for (int i = 0; i < glyphs; i++) {
                double angle = (Math.PI * 2.0 * i) / glyphs + rot;
                world.spawnParticles(rune, center.x + Math.cos(angle) * RADIUS * 0.82, y + 0.05,
                        center.z + Math.sin(angle) * RADIUS * 0.82, 1, 0.0, 0.0, 0.0, 0.0);
            }
            // A single central glyph.
            world.spawnParticles(rune, center.x, y + 0.05, center.z, 1, 0.0, 0.0, 0.0, 0.0);
        }
    }
}
