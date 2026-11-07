package com.example.circlemage.spell;

import com.example.circlemage.entity.MagicMissileEntity;
import com.example.circlemage.mana.ManaData;
import com.example.circlemage.particle.ModParticles;
import net.minecraft.entity.LivingEntity;
import net.minecraft.particle.DefaultParticleType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.HashSet;
import java.util.Set;

/**
 * The registered combination spells and the dispatcher that picks one from a primed + pressed pair.
 * Combination order matters: only the pairs below exist. Where a "fully charged" ingredient is
 * required, it must be the primed (held) spell, since only the primed spell carries a charge.
 */
public final class Combinations {

    public static final CombinationSpell CHAOS_SPEAR = new ChaosSpear();
    public static final CombinationSpell THUNDER_MARK = new ThunderMark();
    public static final CombinationSpell THUNDER_WARP = new ThunderWarp();
    public static final CombinationSpell FIRE_ESCAPE = new FireEscape();
    public static final CombinationSpell BLAZE = new Blaze();
    public static final CombinationSpell CHAIN_LIGHTNING = new ChainLightning();
    public static final CombinationSpell DIVINE_WRATH = new DivineWrath();

    private static final CombinationSpell[] ALL = {
            CHAOS_SPEAR, THUNDER_MARK, THUNDER_WARP, FIRE_ESCAPE, BLAZE, CHAIN_LIGHTNING, DIVINE_WRATH
    };

    private Combinations() {
    }

    /** First combination matching the primed + pressed pair (and charge), or null. */
    public static CombinationSpell find(Spell primed, boolean primedFull, Spell second) {
        if (primed == null || second == null) {
            return null;
        }
        for (CombinationSpell c : ALL) {
            if (c.matches(primed, primedFull, second)) {
                return c;
            }
        }
        return null;
    }

    // ------------------------------------------------------------------------
    // Shared targeting / particle helpers
    // ------------------------------------------------------------------------

    /** Nearest living entity to a point, within maxDist, excluding the player. */
    private static LivingEntity nearestToPoint(ServerWorld world, ServerPlayerEntity player, Vec3d point, double maxDist) {
        Box box = new Box(point.subtract(maxDist, maxDist, maxDist), point.add(maxDist, maxDist, maxDist));
        LivingEntity best = null;
        double bestSq = maxDist * maxDist;
        for (LivingEntity e : world.getEntitiesByClass(LivingEntity.class, box, x -> x.isAlive() && x != player)) {
            double dsq = e.getBoundingBox().getCenter().squaredDistanceTo(point);
            if (dsq <= bestSq) {
                bestSq = dsq;
                best = e;
            }
        }
        return best;
    }

    /** First living entity along the aim ray within range and lateral tolerance. */
    private static LivingEntity findAlongRay(ServerWorld world, ServerPlayerEntity player, Vec3d eye, Vec3d dir,
                                             double range, double lateral) {
        Box box = player.getBoundingBox().expand(range);
        LivingEntity best = null;
        double bestForward = range;
        for (LivingEntity e : world.getEntitiesByClass(LivingEntity.class, box, x -> x.isAlive() && x != player)) {
            Vec3d to = e.getBoundingBox().getCenter().subtract(eye);
            double forward = to.dotProduct(dir);
            if (forward <= 0 || forward > range) {
                continue;
            }
            double lat = to.subtract(dir.multiply(forward)).length();
            if (lat > lateral) {
                continue;
            }
            if (forward < bestForward) {
                bestForward = forward;
                best = e;
            }
        }
        return best;
    }

    private static LivingEntity nearestWithin(ServerWorld world, Vec3d point, double radius,
                                              Set<Integer> visited, ServerPlayerEntity player) {
        Box box = new Box(point.subtract(radius, radius, radius), point.add(radius, radius, radius));
        LivingEntity best = null;
        double bestSq = radius * radius;
        for (LivingEntity e : world.getEntitiesByClass(LivingEntity.class, box,
                x -> x.isAlive() && x != player && !visited.contains(x.getId()))) {
            double dsq = e.getBoundingBox().getCenter().squaredDistanceTo(point);
            if (dsq <= bestSq) {
                bestSq = dsq;
                best = e;
            }
        }
        return best;
    }

    /** Draws a particle beam between two points. */
    private static void beam(ServerWorld world, Vec3d from, Vec3d to, DefaultParticleType core, DefaultParticleType edge) {
        double dist = from.distanceTo(to);
        int steps = Math.max(4, (int) (dist * 3));
        for (int i = 0; i <= steps; i++) {
            double t = (double) i / steps;
            double x = MathHelper.lerp(t, from.x, to.x);
            double y = MathHelper.lerp(t, from.y, to.y);
            double z = MathHelper.lerp(t, from.z, to.z);
            world.spawnParticles(core, x, y, z, 1, 0.02, 0.02, 0.02, 0.0);
            if (i % 2 == 0) {
                world.spawnParticles(edge, x, y, z, 1, 0.05, 0.05, 0.05, 0.0);
            }
        }
    }

    // ------------------------------------------------------------------------
    // 1. Chaos Spear — Spear of Light + Dark Orb (either order)
    // ------------------------------------------------------------------------

    private static final class ChaosSpear extends CombinationSpell {
        ChaosSpear() {
            super(0, "Chaos Spear", 2000, 30);
        }

        @Override
        public boolean matches(Spell primed, boolean primedFull, Spell second) {
            return (primed == Spell.SPEAR_OF_LIGHT && second == Spell.DARK_ORB)
                    || (primed == Spell.DARK_ORB && second == Spell.SPEAR_OF_LIGHT);
        }

        @Override
        public boolean cast(ServerWorld world, ServerPlayerEntity player, ManaData data) {
            Vec3d look = player.getRotationVec(1.0f);
            Vec3d cursor = player.raycast(50.0, 1.0f, false).getPos();
            LivingEntity target = nearestToPoint(world, player, cursor, 2.0);

            MagicMissileEntity spear = new MagicMissileEntity(world, player, 12.0f);
            spear.setSpear(true, MagicMissileEntity.SPEED * 2.5);
            spear.setChaos(true);
            spear.setEnhanced(true);
            spear.launch(look);
            if (target != null) {
                spear.setHomingTarget(target);
            }
            world.spawnEntity(spear);
            world.playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.BLOCK_BEACON_DEACTIVATE, SoundCategory.PLAYERS, 1.2f, 0.5f);
            return true;
        }
    }

    // ------------------------------------------------------------------------
    // 2. Thunder Mark — Magic Missile + Thunder
    // ------------------------------------------------------------------------

    private static final class ThunderMark extends CombinationSpell {
        ThunderMark() {
            super(1, "Thunder Mark", 1000, 20);
        }

        @Override
        public boolean matches(Spell primed, boolean primedFull, Spell second) {
            return primed == Spell.MAGIC_MISSILE && second == Spell.THUNDER;
        }

        @Override
        public boolean cast(ServerWorld world, ServerPlayerEntity player, ManaData data) {
            Vec3d look = player.getRotationVec(1.0f);
            MagicMissileEntity m = new MagicMissileEntity(world, player, 0.0f);
            m.setMarkMissile(true);
            m.launch(look);
            world.spawnEntity(m);
            world.playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.ITEM_TRIDENT_THROW, SoundCategory.PLAYERS, 1.0f, 1.4f);
            return true;
        }
    }

    // ------------------------------------------------------------------------
    // 3. Thunder Warp — Fully Charged Wind Wall + Thunder (requires a Thunder Mark)
    // ------------------------------------------------------------------------

    private static final class ThunderWarp extends CombinationSpell {
        ThunderWarp() {
            super(2, "Thunder Warp", 2000, 40);
        }

        @Override
        public boolean matches(Spell primed, boolean primedFull, Spell second) {
            return primed == Spell.WALL_OF_WIND && primedFull && second == Spell.THUNDER;
        }

        @Override
        public String requirementError(ServerPlayerEntity player, ManaData data) {
            if (!data.hasThunderMark()) {
                return "You have no Thunder Mark to warp to.";
            }
            String dim = ((ServerWorld) player.getWorld()).getRegistryKey().getValue().toString();
            if (!dim.equals(data.getMarkDim())) {
                return "Your Thunder Mark is in another dimension.";
            }
            double dx = player.getX() - data.getMarkX();
            double dy = player.getY() - data.getMarkY();
            double dz = player.getZ() - data.getMarkZ();
            if (dx * dx + dy * dy + dz * dz > 10000.0 * 10000.0) {
                return "Your Thunder Mark is too far away (>10000 blocks).";
            }
            return null;
        }

        @Override
        public boolean cast(ServerWorld world, ServerPlayerEntity player, ManaData data) {
            Vec3d from = player.getPos();
            double tx = data.getMarkX();
            double ty = data.getMarkY();
            double tz = data.getMarkZ();

            // Departure: lightning, dust, sparks.
            strike(world, player, from.x, from.y, from.z);
            world.spawnParticles(ModParticles.SPARK, from.x, from.y + 1.0, from.z, 60, 0.5, 1.0, 0.5, 0.4);
            world.spawnParticles(ModParticles.DUST, from.x, from.y, from.z, 30, 0.6, 0.2, 0.6, 0.3);

            // Travel: a parabolic lightning arc of sparks.
            Vec3d target = new Vec3d(tx, ty, tz);
            double peak = 3.0 + from.distanceTo(target) * 0.05;
            int steps = 48;
            for (int i = 0; i <= steps; i++) {
                double t = (double) i / steps;
                double x = MathHelper.lerp(t, from.x, tx);
                double z = MathHelper.lerp(t, from.z, tz);
                double y = MathHelper.lerp(t, from.y, ty) + Math.sin(Math.PI * t) * peak;
                world.spawnParticles(ModParticles.SPARK, x, y + 1.0, z, 1, 0.0, 0.0, 0.0, 0.0);
            }

            // Teleport, keeping momentum and taking no fall damage.
            Vec3d vel = player.getVelocity();
            player.fallDistance = 0.0f;
            player.networkHandler.requestTeleport(tx, ty, tz, player.getYaw(), player.getPitch());
            player.setVelocity(vel);
            player.velocityModified = true;
            player.fallDistance = 0.0f;

            // Arrival: lightning strike, dust cloud, electrical shockwave, flash.
            strike(world, player, tx, ty, tz);
            world.spawnParticles(ModParticles.SPARK, tx, ty + 1.0, tz, 90, 0.7, 1.2, 0.7, 0.6);
            world.spawnParticles(ModParticles.DUST, tx, ty, tz, 50, 1.0, 0.2, 1.0, 0.35);
            world.spawnParticles(ModParticles.RADIANCE, tx, ty + 1.0, tz, 30, 0.4, 0.6, 0.4, 0.3);
            for (double ring = 1.5; ring <= 5.0; ring += 1.5) {
                int pts = (int) (ring * 6);
                for (int i = 0; i < pts; i++) {
                    double a = (Math.PI * 2.0 * i) / pts;
                    world.spawnParticles(ModParticles.SPARK, tx + Math.cos(a) * ring, ty + 0.3, tz + Math.sin(a) * ring,
                            1, 0, 0, 0, 0.0);
                }
            }
            world.playSound(null, tx, ty, tz, SoundEvents.ENTITY_LIGHTNING_BOLT_THUNDER, SoundCategory.PLAYERS, 2.0f, 1.0f);
            return true;
        }
    }

    // ------------------------------------------------------------------------
    // 4. Fire Escape — Wind Wall (not full) + Fireball
    // ------------------------------------------------------------------------

    private static final class FireEscape extends CombinationSpell {
        FireEscape() {
            super(3, "Fire Escape", 1250, 30);
        }

        @Override
        public boolean matches(Spell primed, boolean primedFull, Spell second) {
            return primed == Spell.WALL_OF_WIND && !primedFull && second == Spell.FIREBALL;
        }

        @Override
        public boolean cast(ServerWorld world, ServerPlayerEntity player, ManaData data) {
            Vec3d origin = player.getPos();
            Vec3d look = player.getRotationVec(1.0f);
            Vec3d back = new Vec3d(-look.x, 0.0, -look.z);
            if (back.lengthSquared() < 1.0e-4) {
                back = new Vec3d(0, 0, 1);
            }
            back = back.normalize().multiply(2.4).add(0, 0.45, 0);
            player.addVelocity(back.x, back.y, back.z);
            player.velocityModified = true;

            // 6-block fire zone for 5s where the player was standing — never burns the caster.
            AreaEffectManager.add(new AreaEffect(world, origin, 6.0, AreaEffect.Type.FIRE, 100, 0, 1.0f)
                    .setExcluded(player.getUuid()));

            world.spawnParticles(ModParticles.GALE, player.getX(), player.getBodyY(0.5), player.getZ(), 30, 0.4, 0.4, 0.4, 0.4);
            world.spawnParticles(ModParticles.EMBER, origin.x, origin.y + 0.4, origin.z, 90, 3.0, 0.5, 3.0, 0.06);
            world.spawnParticles(ModParticles.SMOKE, origin.x, origin.y + 0.6, origin.z, 30, 2.5, 0.5, 2.5, 0.03);
            world.playSound(null, origin.x, origin.y, origin.z, SoundEvents.ITEM_FIRECHARGE_USE, SoundCategory.PLAYERS, 1.4f, 0.8f);
            world.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ENTITY_PHANTOM_FLAP, SoundCategory.PLAYERS, 1.2f, 0.7f);
            return true;
        }
    }

    // ------------------------------------------------------------------------
    // 5. Blaze — Fully Charged Wind Wall + Fireball (channelled flamethrower)
    // ------------------------------------------------------------------------

    private static final class Blaze extends CombinationSpell {
        Blaze() {
            super(4, "Blaze", 500, 80); // 500 to ignite, then 500/s drained while it burns
        }

        @Override
        public boolean matches(Spell primed, boolean primedFull, Spell second) {
            return primed == Spell.WALL_OF_WIND && primedFull && second == Spell.FIREBALL;
        }

        @Override
        public boolean cast(ServerWorld world, ServerPlayerEntity player, ManaData data) {
            data.setBlazeEndTick(world.getTime() + 60); // up to 3s, ticked & drained by the mod
            world.playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.ENTITY_BLAZE_SHOOT, SoundCategory.PLAYERS, 1.2f, 0.7f);
            return true;
        }
    }

    // ------------------------------------------------------------------------
    // 6. Chain Lightning — Thunder (not full) + Spear of Light
    // ------------------------------------------------------------------------

    private static final class ChainLightning extends CombinationSpell {
        ChainLightning() {
            super(5, "Chain Lightning", 3000, 40);
        }

        @Override
        public boolean matches(Spell primed, boolean primedFull, Spell second) {
            return primed == Spell.THUNDER && !primedFull && second == Spell.SPEAR_OF_LIGHT;
        }

        @Override
        public boolean cast(ServerWorld world, ServerPlayerEntity player, ManaData data) {
            Vec3d eye = player.getEyePos();
            Vec3d look = player.getRotationVec(1.0f);
            LivingEntity current = findAlongRay(world, player, eye, look, 20.0, 3.0);
            if (current == null) {
                // Fall back to whatever is nearest the crosshair point.
                Vec3d cursor = player.raycast(20.0, 1.0f, false).getPos();
                current = nearestToPoint(world, player, cursor, 4.0);
            }
            if (current == null) {
                beam(world, eye, eye.add(look.multiply(20.0)), ModParticles.SPARK, ModParticles.LIGHT);
                world.playSound(null, player.getX(), player.getY(), player.getZ(),
                        SoundEvents.ENTITY_LIGHTNING_BOLT_IMPACT, SoundCategory.PLAYERS, 1.0f, 1.6f);
                return true;
            }

            Set<Integer> visited = new HashSet<>();
            Vec3d prev = eye;
            float dmg = 10.0f;     // 5 hearts
            double radius = 5.0;
            for (int chain = 0; chain < 5 && current != null; chain++) {
                visited.add(current.getId());
                Vec3d cp = current.getBoundingBox().getCenter();
                beam(world, prev, cp, ModParticles.SPARK, ModParticles.LIGHT);
                world.spawnParticles(ModParticles.RADIANCE, cp.x, cp.y, cp.z, 12, 0.2, 0.2, 0.2, 0.2);
                world.spawnParticles(ModParticles.SPARK, cp.x, cp.y, cp.z, 16, 0.3, 0.3, 0.3, 0.3);
                current.damage(player.getDamageSources().indirectMagic(player, player), dmg);
                world.playSound(null, cp.x, cp.y, cp.z, SoundEvents.ENTITY_LIGHTNING_BOLT_THUNDER, SoundCategory.PLAYERS, 1.2f, 1.4f);

                prev = cp;
                dmg *= 0.8f;
                radius -= 1.0;
                if (radius < 1.0) {
                    break;
                }
                current = nearestWithin(world, cp, radius, visited, player);
            }
            return true;
        }
    }

    // ------------------------------------------------------------------------
    // 7. Divine Wrath — Fully Charged Thunder + Spear of Light (tracking sky beam)
    // ------------------------------------------------------------------------

    private static final class DivineWrath extends CombinationSpell {
        DivineWrath() {
            super(6, "Divine Wrath", 4000, 60);
        }

        @Override
        public boolean matches(Spell primed, boolean primedFull, Spell second) {
            return primed == Spell.THUNDER && primedFull && second == Spell.SPEAR_OF_LIGHT;
        }

        @Override
        public String requirementError(ServerPlayerEntity player, ManaData data) {
            Vec3d cursor = player.raycast(50.0, 1.0f, false).getPos();
            if (nearestToPoint((ServerWorld) player.getWorld(), player, cursor, 4.0) == null) {
                return "No target under your cursor for Divine Wrath.";
            }
            return null;
        }

        @Override
        public boolean cast(ServerWorld world, ServerPlayerEntity player, ManaData data) {
            Vec3d cursor = player.raycast(50.0, 1.0f, false).getPos();
            LivingEntity target = nearestToPoint(world, player, cursor, 4.0);
            if (target == null) {
                return false;
            }
            // Mark the target and gather light overhead; the beam itself is ticked by the mod for 3s.
            data.setDivineWrath(target.getId(), world.getTime() + 60);
            Vec3d tp = target.getPos();
            world.spawnParticles(ModParticles.RADIANCE, tp.x, tp.y + 1.0, tp.z, 30, 0.5, 0.8, 0.5, 0.1);
            world.spawnParticles(ModParticles.LIGHT, tp.x, tp.y + 8.0, tp.z, 40, 1.0, 2.0, 1.0, 0.05);
            world.playSound(null, tp.x, tp.y, tp.z, SoundEvents.BLOCK_BEACON_ACTIVATE, SoundCategory.PLAYERS, 1.5f, 1.2f);
            return true;
        }
    }
}
