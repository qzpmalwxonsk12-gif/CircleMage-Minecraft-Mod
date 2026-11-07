package com.example.circlemage.boss;

import com.example.circlemage.particle.ModParticles;
import com.example.circlemage.spell.AreaEffect;
import com.example.circlemage.spell.AreaEffectManager;
import net.minecraft.entity.LivingEntity;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Heightmap;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Meteor Rain — a Boss Zone mechanic that continuously rains telegraphed magical impacts inside the
 * zone. Each impact paints a red ground circle that fills over its warning period, then lands. Two
 * projectile types: frequent small <b>Magic Missile</b> bursts and rarer, larger <b>Fireball</b>
 * impacts that leave temporary fire. Positions are randomised so there are always safe gaps — the
 * attack is meant to be threatening but always dodgeable.
 */
public class MeteorRain {

    private static final DustParticleEffect RED = new DustParticleEffect(new Vector3f(1.0f, 0.08f, 0.08f), 1.5f);

    // Type A — Magic Missile rain: high frequency, small circle, moderate damage.
    // A_INTERVAL is the "1x" cadence (1 impact / 10 ticks); the zone's rate multiplier scales from there.
    private static final int A_INTERVAL = 10;
    private static final int A_WARN = 25;         // 1.25s telegraph
    private static final double A_RADIUS = 1.0;
    private static final float A_DAMAGE = 4.0f;   // 2 hearts
    private static final int A_MAX = 160;         // concurrency cap (raised for the high base rates)

    // Type B — Fireball rain: lower frequency, ~3-block circle, leaves temporary fire.
    private static final int B_INTERVAL = 70;
    private static final int B_WARN = 40;         // 2s telegraph
    private static final double B_RADIUS = 3.0;
    private static final float B_DAMAGE = 8.0f;   // 4 hearts at the centre, falls off
    private static final int B_FIRE_TICKS = 80;   // 4s of fire, then it disappears
    private static final int B_MAX = 48;

    private final BossZone zone;
    private final List<Impact> impacts = new ArrayList<>();

    public MeteorRain(BossZone zone) {
        this.zone = zone;
    }

    public void tick() {
        ServerWorld world = zone.getWorld();

        // Separate per-type rate multipliers (base 50x missile / 30x fireball). Expected impacts per
        // tick = rate / baseInterval, so high rates spawn several per tick (honouring up to 1000x).
        spawnBatch(world, false, zone.getMeteorRateMissile() / (double) A_INTERVAL);
        spawnBatch(world, true, zone.getMeteorRateFireball() / (double) B_INTERVAL);

        Iterator<Impact> it = impacts.iterator();
        while (it.hasNext()) {
            Impact imp = it.next();
            imp.age++;
            if (imp.age % 2 == 0) { // throttle telegraph particle spawns; the dust lingers between frames
                telegraph(world, imp);
            }
            if (imp.age >= imp.warn) {
                land(world, imp);
                it.remove();
            }
        }
    }

    /** Spawns {@code expected} impacts this tick (whole part + fractional chance), respecting the cap. */
    private void spawnBatch(ServerWorld world, boolean fireball, double expected) {
        int max = fireball ? B_MAX : A_MAX;
        int current = countType(fireball);
        int n = (int) expected;
        if (world.random.nextDouble() < expected - n) {
            n++;
        }
        for (int i = 0; i < n && current < max; i++, current++) {
            spawnImpact(world, fireball);
        }
    }

    private int countType(boolean fireball) {
        int n = 0;
        for (Impact i : impacts) {
            if (i.fireball == fireball) {
                n++;
            }
        }
        return n;
    }

    /** Picks a random ground position within the zone's horizontal disc and queues a telegraphed impact. */
    private void spawnImpact(ServerWorld world, boolean fireball) {
        Vec3d c = zone.getCenter();
        double maxR = zone.getRadius() * 0.9;
        double ang = world.random.nextDouble() * Math.PI * 2.0;
        double r = Math.sqrt(world.random.nextDouble()) * maxR; // uniform over the disc
        double x = c.x + Math.cos(ang) * r;
        double z = c.z + Math.sin(ang) * r;
        int y = world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, (int) Math.floor(x), (int) Math.floor(z));
        Vec3d pos = new Vec3d(x, y, z);
        if (!zone.contains(pos)) {
            return; // ground here falls outside the sphere — skip rather than hit outside the zone
        }
        impacts.add(new Impact(pos, fireball ? B_RADIUS : A_RADIUS, fireball ? B_WARN : A_WARN, fireball));
    }

    /** Red circle that fills with red over the warning period. */
    private void telegraph(ServerWorld world, Impact imp) {
        double progress = Math.min(1.0, (double) imp.age / imp.warn);
        double rad = imp.radius;
        double y = imp.pos.y + 0.15;

        // Outline ring (always visible).
        int ringPts = Math.max(8, (int) (rad * 8));
        for (int i = 0; i < ringPts; i++) {
            double a = (Math.PI * 2.0 * i) / ringPts;
            world.spawnParticles(RED, imp.pos.x + Math.cos(a) * rad, y, imp.pos.z + Math.sin(a) * rad, 1, 0, 0, 0, 0.0);
        }
        // Fill outward as the warning elapses.
        double fill = rad * progress;
        int fillCount = (int) (8.0 * rad * rad * progress) + 2;
        for (int i = 0; i < fillCount; i++) {
            double a = world.random.nextDouble() * Math.PI * 2.0;
            double rr = Math.sqrt(world.random.nextDouble()) * fill;
            world.spawnParticles(RED, imp.pos.x + Math.cos(a) * rr, y, imp.pos.z + Math.sin(a) * rr, 1, 0, 0, 0, 0.0);
        }
    }

    private void land(ServerWorld world, Impact imp) {
        if (imp.fireball) {
            landFireball(world, imp);
        } else {
            landMissile(world, imp);
        }
    }

    /** Type A: a small arcane burst that explodes on impact (reuses Magic Missile visuals/particles). */
    private void landMissile(ServerWorld world, Impact imp) {
        Vec3d p = imp.pos;
        world.spawnParticles(ModParticles.ARCANE, p.x, p.y + 0.3, p.z, 24, 0.3, 0.3, 0.3, 0.12);
        world.spawnParticles(ModParticles.ARCANE_TRAIL, p.x, p.y + 0.3, p.z, 10, 0.3, 0.3, 0.3, 0.1);
        damage(world, p, A_RADIUS + 0.5, A_DAMAGE, false);
        world.playSound(null, p.x, p.y, p.z, SoundEvents.ENTITY_ILLUSIONER_MIRROR_MOVE, SoundCategory.HOSTILE, 0.8f, 1.4f);
    }

    /** Type B: a larger blast that leaves a short-lived fire field. */
    private void landFireball(ServerWorld world, Impact imp) {
        Vec3d p = imp.pos;
        world.spawnParticles(ModParticles.EMBER, p.x, p.y + 0.4, p.z, 60, B_RADIUS * 0.5, 0.4, B_RADIUS * 0.5, 0.1);
        world.spawnParticles(ModParticles.SMOKE, p.x, p.y + 0.4, p.z, 18, B_RADIUS * 0.4, 0.3, B_RADIUS * 0.4, 0.04);
        damage(world, p, B_RADIUS, B_DAMAGE, true);
        // Temporary fire that naturally disappears (no block changes — entities burn while inside).
        AreaEffectManager.add(new AreaEffect(world, p, B_RADIUS, AreaEffect.Type.FIRE, B_FIRE_TICKS, 0, 1.0f));
        world.playSound(null, p.x, p.y, p.z, SoundEvents.ITEM_FIRECHARGE_USE, SoundCategory.HOSTILE, 1.2f, 0.8f);
        world.playSound(null, p.x, p.y, p.z, SoundEvents.ENTITY_GENERIC_EXPLODE, SoundCategory.HOSTILE, 0.7f, 1.2f);
    }

    /** Damages living entities within {@code radius} of the impact (and only inside the zone). */
    private void damage(ServerWorld world, Vec3d p, double radius, float damage, boolean fire) {
        Box box = new Box(p.x - radius, p.y - radius, p.z - radius, p.x + radius, p.y + radius, p.z + radius);
        for (LivingEntity e : world.getEntitiesByClass(LivingEntity.class, box, LivingEntity::isAlive)) {
            double d = e.getPos().distanceTo(p);
            if (d > radius || !zone.contains(e.getPos())) {
                continue;
            }
            float dealt = (float) (damage * (1.0 - 0.4 * (d / radius))); // mild falloff
            e.damage(world.getDamageSources().magic(), dealt);
            if (fire) {
                e.setOnFireFor(4);
            }
        }
    }

    private static final class Impact {
        final Vec3d pos;
        final double radius;
        final int warn;
        final boolean fireball;
        int age = 0;

        Impact(Vec3d pos, double radius, int warn, boolean fireball) {
            this.pos = pos;
            this.radius = radius;
            this.warn = warn;
            this.fireball = fireball;
        }
    }
}
