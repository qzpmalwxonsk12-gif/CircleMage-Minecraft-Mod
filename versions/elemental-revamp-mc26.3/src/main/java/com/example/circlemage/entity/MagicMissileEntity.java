package com.example.circlemage.entity;

import com.example.circlemage.mana.ManaPlayer;
import com.example.circlemage.particle.ModParticles;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * A gravity-free magic bolt. Flies straight toward where it was launched, optionally homing onto a
 * locked target, and despawns after travelling its maximum range. Has two forms:
 * <ul>
 *     <li>{@code MAGIC_MISSILE} (default) — the cyan arcane orb.</li>
 *     <li>Spear of Light ({@link #setSpear}) — the 5th-circle transform: faster, hits harder,
 *     rendered as a golden lance.</li>
 * </ul>
 */
public class MagicMissileEntity extends Projectile {

    /** Base blocks per tick (Magic Missile). Spear of Light launches faster. */
    public static final double SPEED = 1.5;
    private static final double MAX_DISTANCE = 50.0;
    private static final double HOMING_STRENGTH = 0.25;

    /** Synced so the client renderer can pick the orb vs. lance look and the trail colour. */
    private static final EntityDataAccessor<Boolean> SPEAR =
            SynchedEntityData.defineId(MagicMissileEntity.class, EntityDataSerializers.BOOLEAN);
    /** Synced so the client extrapolates at the same speed the server moves at (avoids jitter). */
    private static final EntityDataAccessor<Float> SPEED_DT =
            SynchedEntityData.defineId(MagicMissileEntity.class, EntityDataSerializers.FLOAT);
    /** Synced: the Fireball (fire bolt) variant — orange orb, ember trail, ignites on hit. */
    private static final EntityDataAccessor<Boolean> FIRE =
            SynchedEntityData.defineId(MagicMissileEntity.class, EntityDataSerializers.BOOLEAN);

    private double traveled = 0.0;
    private float damage = 4.0f;
    private int homingTargetId = -1;
    /** Server-side: whether this bolt was cast under an active chant (used for the orb/spear interaction). */
    private boolean enhanced = false;
    /** Server-side: whether this bolt was fully charged (held to the cap) when cast. */
    private boolean fullyCharged = false;
    /** Combination modes. chaos = Chaos Spear (explodes, no debuff); markMissile = Thunder Mark bolt. */
    private boolean chaos = false;
    private boolean markMissile = false;
    /** While >= 0, the (chaos) bolt has detonated and is leaving residual energy before despawning. */
    private int residualTicks = -1;

    public MagicMissileEntity(EntityType<? extends MagicMissileEntity> type, Level world) {
        super(type, world);
        this.setNoGravity(true);
    }

    public MagicMissileEntity(Level world, LivingEntity owner, float damage) {
        this(ModEntities.MAGIC_MISSILE, world);
        this.setOwner(owner);
        this.damage = damage;
        Vec3 eye = owner.getEyePosition();
        this.setPos(eye.x, eye.y - 0.1, eye.z);
    }

    public void setDamage(float damage) {
        this.damage = damage;
    }

    public void setHomingTarget(Entity target) {
        this.homingTargetId = target == null ? -1 : target.getId();
    }

    /** Switch to the Spear of Light form and set its travel speed (blocks/tick). */
    public void setSpear(boolean spear, double speed) {
        this.entityData.set(SPEAR, spear);
        this.entityData.set(SPEED_DT, (float) speed);
    }

    public boolean isSpear() {
        return this.entityData.get(SPEAR);
    }

    /** Switch to the Fireball form (ember trail; ignites whatever it strikes). */
    public void setFireBolt(boolean fire) {
        this.entityData.set(FIRE, fire);
    }

    public boolean isFireBolt() {
        return this.entityData.get(FIRE);
    }

    public void setEnhanced(boolean enhanced) {
        this.enhanced = enhanced;
    }

    public boolean isEnhanced() {
        return this.enhanced;
    }

    public void setFullyCharged(boolean fullyCharged) {
        this.fullyCharged = fullyCharged;
    }

    public boolean isFullyCharged() {
        return this.fullyCharged;
    }

    public void setChaos(boolean chaos) {
        this.chaos = chaos;
    }

    public void setMarkMissile(boolean markMissile) {
        this.markMissile = markMissile;
    }

    private double speed() {
        return this.entityData.get(SPEED_DT);
    }

    public void launch(Vec3 direction) {
        this.setDeltaMovement(direction.normalize().scale(speed()));
    }

    @Override
    protected void defineSynchedData(net.minecraft.network.syncher.SynchedEntityData.Builder builder) {
        builder.define(SPEAR, false);
        builder.define(SPEED_DT, (float) SPEED);
        builder.define(FIRE, false);
    }

    @Override
    public void tick() {
        super.tick();

        // Chaos Spear residual energy: hover and leak black-and-white particles for ~5s after impact.
        if (residualTicks >= 0) {
            this.setDeltaMovement(Vec3.ZERO);
            if (this.level() instanceof ServerLevel sw && residualTicks % 2 == 0) {
                double a = sw.getRandom().nextDouble() * Math.PI * 2.0;
                double r = sw.getRandom().nextDouble() * 2.5;
                sw.sendParticles(ModParticles.VOID, getX() + Math.cos(a) * r, getY() + sw.getRandom().nextDouble() * 1.5,
                        getZ() + Math.sin(a) * r, 1, 0.02, 0.05, 0.02, 0.01);
                sw.sendParticles(ModParticles.ARCANE_TRAIL, getX() - Math.cos(a) * r, getY() + sw.getRandom().nextDouble() * 1.5,
                        getZ() - Math.sin(a) * r, 1, 0.02, 0.05, 0.02, 0.01);
            }
            if (--residualTicks < 0) {
                this.discard();
            }
            return;
        }

        double speed = speed();
        Vec3 velocity = this.getDeltaMovement();

        // Course correction: steer toward the locked target if one is set and still valid.
        Entity target = homingTargetId >= 0 ? this.level().getEntity(homingTargetId) : null;
        if (target != null && target.isAlive()) {
            Vec3 toTarget = target.getBoundingBox().getCenter().subtract(this.position()).normalize();
            velocity = velocity.normalize().add(toTarget.scale(HOMING_STRENGTH)).normalize().scale(speed);
            this.setDeltaMovement(velocity);
        }

        // Collision check along the path we are about to travel.
        HitResult hitResult = ProjectileUtil.getHitResultOnMoveVector(this, this::canHitEntity);
        if (hitResult.getType() != HitResult.Type.MISS) {
            this.onHit(hitResult);
            if (this.isRemoved()) {
                return;
            }
        }

        // Advance with constant velocity (gravity disabled).
        Vec3 nextPos = this.position().add(velocity);
        this.setPos(nextPos.x, nextPos.y, nextPos.z);

        // Comet trail (server spawns particles so every nearby client sees them).
        if (this.level() instanceof ServerLevel serverWorld) {
            if (chaos) {
                // Unstable black-and-white energy that alternates and orbits the lance.
                long t = serverWorld.getGameTime();
                serverWorld.sendParticles((t % 2 == 0) ? ModParticles.VOID : ModParticles.LIGHT,
                        getX(), getY(), getZ(), 2, 0.06, 0.06, 0.06, 0.02);
                double a = t * 0.7;
                double r = 0.45;
                serverWorld.sendParticles(ModParticles.ARCANE_TRAIL,
                        getX() + Math.cos(a) * r, getY(), getZ() + Math.sin(a) * r, 1, 0, 0, 0, 0.0);
                serverWorld.sendParticles(ModParticles.VOID,
                        getX() - Math.cos(a) * r, getY(), getZ() - Math.sin(a) * r, 1, 0, 0, 0, 0.0);
                if (t % 6 == 0) {
                    serverWorld.playSound(null, getX(), getY(), getZ(),
                            SoundEvents.BEACON_AMBIENT, SoundSource.PLAYERS, 0.3f, 0.4f);
                }
            } else if (markMissile) {
                serverWorld.sendParticles(ModParticles.SPARK, getX(), getY(), getZ(), 2, 0.05, 0.05, 0.05, 0.04);
            } else if (isFireBolt()) {
                serverWorld.sendParticles(ModParticles.EMBER, getX(), getY(), getZ(), 3, 0.06, 0.06, 0.06, 0.02);
                serverWorld.sendParticles(ModParticles.SMOKE, getX(), getY(), getZ(), 1, 0.03, 0.03, 0.03, 0.005);
            } else {
                SimpleParticleType trail = isSpear() ? ModParticles.LIGHT : ModParticles.ARCANE;
                serverWorld.sendParticles(trail, this.getX(), this.getY(), this.getZ(), 3, 0.06, 0.06, 0.06, 0.01);
                serverWorld.sendParticles(ModParticles.ARCANE_TRAIL, this.getX(), this.getY(), this.getZ(), 1, 0.02, 0.02, 0.02, 0.0);
            }
        }

        // Track distance and fizzle after the maximum range.
        traveled += velocity.length();
        if (traveled >= MAX_DISTANCE) {
            this.discard();
        }
    }

    @Override
    protected void onHitEntity(EntityHitResult entityHitResult) {
        super.onHitEntity(entityHitResult);
        if (this.level().isClientSide()) {
            return;
        }
        if (chaos) {
            chaosExplode();
            return;
        }
        Entity hit = entityHitResult.getEntity();
        Entity owner = this.getOwner();
        // Mage's Robes reduce incoming magic damage (notably Magic Missile).
        float dealt = this.damage;
        if (hit instanceof LivingEntity living) {
            dealt *= (1.0f - com.example.circlemage.item.MageRobeItem.resistanceOf(living));
        }
        // If this hits a Dark Orb, the orb's own damage() handles the absorb/explode interaction.
        hit.hurt(this.damageSources().indirectMagic(this, owner), dealt);
        if (isFireBolt()) {
            hit.igniteForSeconds(5);
        }
        spawnBurst();
        this.discard();
    }

    @Override
    protected void onHitBlock(BlockHitResult blockHitResult) {
        super.onHitBlock(blockHitResult);
        if (this.level().isClientSide()) {
            return;
        }
        if (chaos) {
            chaosExplode();
            return;
        }
        if (markMissile) {
            placeThunderMark(blockHitResult);
            this.discard();
            return;
        }
        spawnBurst();
        this.discard();
    }

    /**
     * Chaos Spear detonation: the same blast as the Dark Orb's normal chaos blast (terrain-rending
     * explosion + black-and-white bloom + falloff damage) but WITHOUT any of its AoE debuffs (no
     * slowness/poison field, no immobilise). Leaves residual energy for ~5s.
     */
    private void chaosExplode() {
        if (this.level() instanceof ServerLevel world) {
            Vec3 c = this.position();
            double radius = 12.0;

            world.explode(this, c.x, c.y, c.z, 10.0f, false, Level.ExplosionInteraction.TNT);

            AABB box = new AABB(c.x - radius, c.y - radius, c.z - radius, c.x + radius, c.y + radius, c.z + radius);
            for (LivingEntity living : world.getEntitiesOfClass(LivingEntity.class, box,
                    e -> e.isAlive() && e != this.getOwner())) {
                double d = living.position().distanceTo(c);
                if (d > radius) {
                    continue;
                }
                float dmg = (float) (40.0 * (1.0 - d / radius)) + 6.0f;
                living.hurt(this.damageSources().indirectMagic(this, this.getOwner()), dmg);
            }

            // Purple-and-white bloom (matches the normal chaos blast).
            world.sendParticles(ModParticles.RADIANCE, c.x, c.y + 1.0, c.z, 40, 0.6, 0.6, 0.6, 0.1);
            world.sendParticles(ModParticles.ARCANE_TRAIL, c.x, c.y + 1.0, c.z, 60, 1.0, 1.0, 1.0, 0.3);
            for (int i = 0; i < 260; i++) {
                double phi = Math.acos(1.0 - 2.0 * world.getRandom().nextDouble());
                double theta = world.getRandom().nextDouble() * Math.PI * 2.0;
                double rr = radius * Math.cbrt(world.getRandom().nextDouble());
                double sx = Math.sin(phi) * Math.cos(theta);
                double sy = Math.cos(phi);
                double sz = Math.sin(phi) * Math.sin(theta);
                world.sendParticles(ModParticles.VOID, c.x + sx * rr, c.y + 1.0 + sy * rr, c.z + sz * rr, 1, 0, 0, 0, 0.0);
            }
            for (double ring = 2.0; ring <= radius; ring += 2.0) {
                int pts = (int) (ring * 6);
                for (int i = 0; i < pts; i++) {
                    double a = (Math.PI * 2.0 * i) / pts;
                    double px = c.x + Math.cos(a) * ring;
                    double pz = c.z + Math.sin(a) * ring;
                    world.sendParticles(ModParticles.VOID, px, c.y + 0.3, pz, 1, 0, 0, 0, 0.0);
                    if (((int) ring) % 4 == 0) {
                        world.sendParticles(ModParticles.LIGHT, px, c.y + 0.3, pz, 1, 0, 0, 0, 0.0);
                    }
                }
            }
            world.playSound(null, c.x, c.y, c.z, SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, 4.0f, 0.6f);
            world.playSound(null, c.x, c.y, c.z, SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 4.0f, 0.5f);
        }
        residualTicks = 100; // 5s of residual energy particles before despawn
        this.setNoGravity(true);
        this.setDeltaMovement(Vec3.ZERO);
    }

    private void placeThunderMark(BlockHitResult hit) {
        if (this.getOwner() instanceof ServerPlayer owner && this.level() instanceof ServerLevel world) {
            Vec3 p = hit.getLocation();
            String dim = world.dimension().identifier().toString();
            ManaPlayer.get(owner).setThunderMark(p.x, p.y, p.z, dim);
            com.example.circlemage.util.Messages.send(owner, net.minecraft.network.chat.Component.literal("Thunder Mark placed.")
                    .withStyle(net.minecraft.ChatFormatting.AQUA), true);
            com.example.circlemage.network.ModNetworking.syncToClient(owner);
            world.sendParticles(ModParticles.SPARK, p.x, p.y + 0.1, p.z, 40, 0.4, 0.2, 0.4, 0.4);
            world.playSound(null, p.x, p.y, p.z, SoundEvents.LIGHTNING_BOLT_IMPACT, SoundSource.PLAYERS, 1.0f, 1.6f);
        }
    }

    private void spawnBurst() {
        if (this.level() instanceof ServerLevel serverWorld) {
            if (isFireBolt()) {
                serverWorld.sendParticles(ModParticles.EMBER, this.getX(), this.getY(0.5), this.getZ(), 18, 0.2, 0.25, 0.2, 0.1);
                serverWorld.sendParticles(ModParticles.SMOKE, this.getX(), this.getY(0.5), this.getZ(), 5, 0.15, 0.2, 0.15, 0.03);
                return;
            }
            SimpleParticleType main = isSpear() ? ModParticles.LIGHT : ModParticles.ARCANE;
            serverWorld.sendParticles(main, this.getX(), this.getY(0.5), this.getZ(), 16, 0.2, 0.25, 0.2, 0.1);
            serverWorld.sendParticles(ModParticles.ARCANE_TRAIL, this.getX(), this.getY(0.5), this.getZ(), 7, 0.13, 0.18, 0.13, 0.07);
        }
    }

    @Override
    protected boolean canHitEntity(Entity entity) {
        if (markMissile) {
            return false; // the mark bolt only reacts to blocks
        }
        return super.canHitEntity(entity) && entity != this.getOwner();
    }

    public void addAdditionalSaveDataLegacy(CompoundTag nbt) {
        
        nbt.putDouble("traveled", traveled);
        nbt.putFloat("damage", damage);
        nbt.putBoolean("spear", isSpear());
        nbt.putBoolean("fireBolt", isFireBolt());
        nbt.putFloat("speed", (float) speed());
        nbt.putBoolean("enhanced", enhanced);
        nbt.putBoolean("fullyCharged", fullyCharged);
        nbt.putBoolean("chaos", chaos);
        nbt.putBoolean("markMissile", markMissile);
        nbt.putInt("residualTicks", residualTicks);
    }

    public void readAdditionalSaveDataLegacy(CompoundTag nbt) {
        
        traveled = nbt.getDoubleOr("traveled", 0d);
        damage = nbt.getFloatOr("damage", 0f);
        this.entityData.set(SPEAR, nbt.getBooleanOr("spear", false));
        this.entityData.set(FIRE, nbt.getBooleanOr("fireBolt", false));
        if (nbt.contains("speed")) {
            this.entityData.set(SPEED_DT, nbt.getFloatOr("speed", 0f));
        }
        enhanced = nbt.getBooleanOr("enhanced", false);
        fullyCharged = nbt.getBooleanOr("fullyCharged", false);
        chaos = nbt.getBooleanOr("chaos", false);
        markMissile = nbt.getBooleanOr("markMissile", false);
        residualTicks = nbt.contains("residualTicks") ? nbt.getIntOr("residualTicks", 0) : -1;
    }

    @Override protected void addAdditionalSaveData(net.minecraft.world.level.storage.ValueOutput value) { super.addAdditionalSaveData(value); CompoundTag tag = new CompoundTag(); addAdditionalSaveDataLegacy(tag); com.example.circlemage.util.NbtBridge.write(value, tag); }

    @Override protected void readAdditionalSaveData(net.minecraft.world.level.storage.ValueInput value) { super.readAdditionalSaveData(value); readAdditionalSaveDataLegacy(com.example.circlemage.util.NbtBridge.read(value)); }
}
