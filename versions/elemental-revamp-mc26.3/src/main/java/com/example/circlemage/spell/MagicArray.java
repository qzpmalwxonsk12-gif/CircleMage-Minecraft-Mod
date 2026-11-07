package com.example.circlemage.spell;

import com.example.circlemage.mana.ManaPlayer;
import com.example.circlemage.particle.ModParticles;
import java.util.List;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * A carved magic array: a visible 5-block-radius rune circle on the ground that fires its selected
 * effect when a living entity steps inside. It re-arms every 2 seconds and expires after its lifetime.
 */
public class MagicArray {

    private static final double RADIUS = 5.0;
    private static final int REARM_TICKS = 40; // 2s between triggers

    private final ServerLevel world;
    private final Vec3 center;
    private final ArrayType type;
    private int remainingTicks;
    private long nextTriggerTick;

    public MagicArray(ServerLevel world, Vec3 center, ArrayType type, int durationTicks) {
        this.world = world;
        this.center = center;
        this.type = type;
        this.remainingTicks = durationTicks;
        // Arm after a short delay so the caster can step off first.
        this.nextTriggerTick = world.getGameTime() + 20;
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
        AABB box = new AABB(
                center.x - RADIUS, center.y - 2.0, center.z - RADIUS,
                center.x + RADIUS, center.y + 3.0, center.z + RADIUS);
        return world.getEntitiesOfClass(LivingEntity.class, box, living -> {
            double dx = living.getX() - center.x;
            double dz = living.getZ() - center.z;
            return (dx * dx + dz * dz) <= (RADIUS * RADIUS) && living.isAlive();
        });
    }

    private void maybeTrigger() {
        if (world.getGameTime() < nextTriggerTick) {
            return;
        }
        List<LivingEntity> inside = entitiesInside();
        if (inside.isEmpty()) {
            return;
        }
        nextTriggerTick = world.getGameTime() + REARM_TICKS;
        applyEffect(inside);
    }

    private void applyEffect(List<LivingEntity> inside) {
        switch (type) {
            case FIRE -> {
                for (LivingEntity living : inside) {
                    if (isImmune(living)) {
                        continue;
                    }
                    living.igniteForSeconds(5);
                }
                world.sendParticles(ModParticles.EMBER, center.x, center.y + 0.4, center.z, 50, RADIUS / 2, 0.3, RADIUS / 2, 0.04);
            }
            case THUNDER -> {
                LightningBolt bolt = net.minecraft.world.entity.EntityTypes.LIGHTNING_BOLT.create(world, net.minecraft.world.entity.EntitySpawnReason.COMMAND);
                if (bolt != null) {
                    bolt.snapTo(center.x, center.y, center.z);
                    world.addFreshEntity(bolt);
                }
                world.sendParticles(ModParticles.SPARK, center.x, center.y + 0.5, center.z, 40, RADIUS / 2, 0.6, RADIUS / 2, 0.15);
            }
            case WIND -> {
                for (LivingEntity living : inside) {
                    Vec3 push = living.position().subtract(center);
                    push = new Vec3(push.x, 0, push.z);
                    if (push.lengthSqr() < 1.0e-4) {
                        push = new Vec3(world.getRandom().nextDouble() - 0.5, 0, world.getRandom().nextDouble() - 0.5);
                    }
                    push = push.normalize().scale(2.0).add(0, 0.5, 0);
                    living.push(push.x, push.y, push.z);
                    living.syncVelocity = true;
                }
                world.sendParticles(ModParticles.GALE, center.x, center.y + 0.5, center.z, 40, RADIUS / 2, 0.4, RADIUS / 2, 0.12);
            }
            case BIND -> {
                for (LivingEntity living : inside) {
                    if (isImmune(living)) {
                        continue;
                    }
                    // Slowness IV (amplifier 3) for 3 seconds.
                    living.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 60, 3, false, true, true));
                }
                world.sendParticles(ModParticles.BIND_MOTE, center.x, center.y + 0.4, center.z, 40, RADIUS / 2, 0.3, RADIUS / 2, 0.05);
            }
        }
    }

    private boolean isImmune(LivingEntity living) {
        return living instanceof ServerPlayer player
                && ManaPlayer.get(player).isImmune(world.getGameTime());
    }

    private void drawCircle() {
        long t = world.getGameTime();
        if (t % 4 != 0) {
            return;
        }
        SimpleParticleType element = switch (type) {
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
            world.sendParticles(element, center.x + Math.cos(angle) * RADIUS, y, center.z + Math.sin(angle) * RADIUS,
                    1, 0.0, 0.0, 0.0, 0.0);
        }
        for (int i = 0; i < points / 2; i++) {
            double angle = (Math.PI * 2.0 * i) / (points / 2.0);
            world.sendParticles(element, center.x + Math.cos(angle) * RADIUS * 0.62, y, center.z + Math.sin(angle) * RADIUS * 0.62,
                    1, 0.0, 0.0, 0.0, 0.0);
        }

        // Slowly-rotating rune glyphs (large, custom textures) make it read as a carved magic array.
        if (t % 12 == 0) {
            SimpleParticleType rune = switch (type) {
                case FIRE -> ModParticles.RUNE_FIRE;
                case THUNDER -> ModParticles.RUNE_THUNDER;
                case WIND -> ModParticles.RUNE_WIND;
                case BIND -> ModParticles.RUNE_BIND;
            };
            int glyphs = 6;
            double rot = (t % 240) * (Math.PI * 2.0 / 240.0);
            for (int i = 0; i < glyphs; i++) {
                double angle = (Math.PI * 2.0 * i) / glyphs + rot;
                world.sendParticles(rune, center.x + Math.cos(angle) * RADIUS * 0.82, y + 0.05,
                        center.z + Math.sin(angle) * RADIUS * 0.82, 1, 0.0, 0.0, 0.0, 0.0);
            }
            // A single central glyph.
            world.sendParticles(rune, center.x, y + 0.05, center.z, 1, 0.0, 0.0, 0.0, 0.0);
        }
    }
}
