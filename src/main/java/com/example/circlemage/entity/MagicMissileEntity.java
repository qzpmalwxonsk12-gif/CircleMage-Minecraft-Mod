package com.example.circlemage.entity;

import com.example.circlemage.mana.ManaPlayer;
import com.example.circlemage.particle.ModParticles;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.entity.projectile.ProjectileUtil;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.listener.ClientPlayPacketListener;
import net.minecraft.network.packet.s2c.play.EntitySpawnS2CPacket;
import net.minecraft.particle.DefaultParticleType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

/**
 * A gravity-free magic bolt. Flies straight toward where it was launched, optionally homing onto a
 * locked target, and despawns after travelling its maximum range. Has two forms:
 * <ul>
 *     <li>{@code MAGIC_MISSILE} (default) — the cyan arcane orb.</li>
 *     <li>Spear of Light ({@link #setSpear}) — the 5th-circle transform: faster, hits harder,
 *     rendered as a golden lance.</li>
 * </ul>
 */
public class MagicMissileEntity extends ProjectileEntity {

    /** Base blocks per tick (Magic Missile). Spear of Light launches faster. */
    public static final double SPEED = 1.5;
    private static final double MAX_DISTANCE = 50.0;
    private static final double HOMING_STRENGTH = 0.25;

    /** Synced so the client renderer can pick the orb vs. lance look and the trail colour. */
    private static final TrackedData<Boolean> SPEAR =
            DataTracker.registerData(MagicMissileEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
    /** Synced so the client extrapolates at the same speed the server moves at (avoids jitter). */
    private static final TrackedData<Float> SPEED_DT =
            DataTracker.registerData(MagicMissileEntity.class, TrackedDataHandlerRegistry.FLOAT);
    /** Synced: the Fireball (fire bolt) variant — orange orb, ember trail, ignites on hit. */
    private static final TrackedData<Boolean> FIRE =
            DataTracker.registerData(MagicMissileEntity.class, TrackedDataHandlerRegistry.BOOLEAN);

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

    public MagicMissileEntity(EntityType<? extends MagicMissileEntity> type, World world) {
        super(type, world);
        this.setNoGravity(true);
    }

    public MagicMissileEntity(World world, LivingEntity owner, float damage) {
        this(ModEntities.MAGIC_MISSILE, world);
        this.setOwner(owner);
        this.damage = damage;
        Vec3d eye = owner.getEyePos();
        this.setPosition(eye.x, eye.y - 0.1, eye.z);
    }

    public void setDamage(float damage) {
        this.damage = damage;
    }

    public void setHomingTarget(Entity target) {
        this.homingTargetId = target == null ? -1 : target.getId();
    }

    /** Switch to the Spear of Light form and set its travel speed (blocks/tick). */
    public void setSpear(boolean spear, double speed) {
        this.dataTracker.set(SPEAR, spear);
        this.dataTracker.set(SPEED_DT, (float) speed);
    }

    public boolean isSpear() {
        return this.dataTracker.get(SPEAR);
    }

    /** Switch to the Fireball form (ember trail; ignites whatever it strikes). */
    public void setFireBolt(boolean fire) {
        this.dataTracker.set(FIRE, fire);
    }

    public boolean isFireBolt() {
        return this.dataTracker.get(FIRE);
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
        return this.dataTracker.get(SPEED_DT);
    }

    public void launch(Vec3d direction) {
        this.setVelocity(direction.normalize().multiply(speed()));
    }

    @Override
    protected void initDataTracker() {
        this.dataTracker.startTracking(SPEAR, false);
        this.dataTracker.startTracking(SPEED_DT, (float) SPEED);
        this.dataTracker.startTracking(FIRE, false);
    }

    @Override
    public void tick() {
        super.tick();

        // Chaos Spear residual energy: hover and leak black-and-white particles for ~5s after impact.
        if (residualTicks >= 0) {
            this.setVelocity(Vec3d.ZERO);
            if (this.getWorld() instanceof ServerWorld sw && residualTicks % 2 == 0) {
                double a = sw.random.nextDouble() * Math.PI * 2.0;
                double r = sw.random.nextDouble() * 2.5;
                sw.spawnParticles(ModParticles.VOID, getX() + Math.cos(a) * r, getY() + sw.random.nextDouble() * 1.5,
                        getZ() + Math.sin(a) * r, 1, 0.02, 0.05, 0.02, 0.01);
                sw.spawnParticles(ModParticles.ARCANE_TRAIL, getX() - Math.cos(a) * r, getY() + sw.random.nextDouble() * 1.5,
                        getZ() - Math.sin(a) * r, 1, 0.02, 0.05, 0.02, 0.01);
            }
            if (--residualTicks < 0) {
                this.discard();
            }
            return;
        }

        double speed = speed();
        Vec3d velocity = this.getVelocity();

        // Course correction: steer toward the locked target if one is set and still valid.
        Entity target = homingTargetId >= 0 ? this.getWorld().getEntityById(homingTargetId) : null;
        if (target != null && target.isAlive()) {
            Vec3d toTarget = target.getBoundingBox().getCenter().subtract(this.getPos()).normalize();
            velocity = velocity.normalize().add(toTarget.multiply(HOMING_STRENGTH)).normalize().multiply(speed);
            this.setVelocity(velocity);
        }

        // Collision check along the path we are about to travel.
        HitResult hitResult = ProjectileUtil.getCollision(this, this::canHit);
        if (hitResult.getType() != HitResult.Type.MISS) {
            this.onCollision(hitResult);
            if (this.isRemoved()) {
                return;
            }
        }

        // Advance with constant velocity (gravity disabled).
        Vec3d nextPos = this.getPos().add(velocity);
        this.setPosition(nextPos.x, nextPos.y, nextPos.z);

        // Comet trail (server spawns particles so every nearby client sees them).
        if (this.getWorld() instanceof ServerWorld serverWorld) {
            if (chaos) {
                // Unstable black-and-white energy that alternates and orbits the lance.
                long t = serverWorld.getTime();
                serverWorld.spawnParticles((t % 2 == 0) ? ModParticles.VOID : ModParticles.LIGHT,
                        getX(), getY(), getZ(), 2, 0.06, 0.06, 0.06, 0.02);
                double a = t * 0.7;
                double r = 0.45;
                serverWorld.spawnParticles(ModParticles.ARCANE_TRAIL,
                        getX() + Math.cos(a) * r, getY(), getZ() + Math.sin(a) * r, 1, 0, 0, 0, 0.0);
                serverWorld.spawnParticles(ModParticles.VOID,
                        getX() - Math.cos(a) * r, getY(), getZ() - Math.sin(a) * r, 1, 0, 0, 0, 0.0);
                if (t % 6 == 0) {
                    serverWorld.playSound(null, getX(), getY(), getZ(),
                            SoundEvents.BLOCK_BEACON_AMBIENT, SoundCategory.PLAYERS, 0.3f, 0.4f);
                }
            } else if (markMissile) {
                serverWorld.spawnParticles(ModParticles.SPARK, getX(), getY(), getZ(), 2, 0.05, 0.05, 0.05, 0.04);
            } else if (isFireBolt()) {
                serverWorld.spawnParticles(ModParticles.EMBER, getX(), getY(), getZ(), 3, 0.06, 0.06, 0.06, 0.02);
                serverWorld.spawnParticles(ModParticles.SMOKE, getX(), getY(), getZ(), 1, 0.03, 0.03, 0.03, 0.005);
            } else {
                DefaultParticleType trail = isSpear() ? ModParticles.LIGHT : ModParticles.ARCANE;
                serverWorld.spawnParticles(trail, this.getX(), this.getY(), this.getZ(), 3, 0.06, 0.06, 0.06, 0.01);
                serverWorld.spawnParticles(ModParticles.ARCANE_TRAIL, this.getX(), this.getY(), this.getZ(), 1, 0.02, 0.02, 0.02, 0.0);
            }
        }

        // Track distance and fizzle after the maximum range.
        traveled += velocity.length();
        if (traveled >= MAX_DISTANCE) {
            this.discard();
        }
    }

    @Override
    protected void onEntityHit(EntityHitResult entityHitResult) {
        super.onEntityHit(entityHitResult);
        if (this.getWorld().isClient) {
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
        hit.damage(this.getDamageSources().indirectMagic(this, owner), dealt);
        if (isFireBolt()) {
            hit.setOnFireFor(5);
        }
        spawnBurst();
        this.discard();
    }

    @Override
    protected void onBlockHit(BlockHitResult blockHitResult) {
        super.onBlockHit(blockHitResult);
        if (this.getWorld().isClient) {
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
        if (this.getWorld() instanceof ServerWorld world) {
            Vec3d c = this.getPos();
            double radius = 12.0;

            world.createExplosion(this, c.x, c.y, c.z, 10.0f, false, World.ExplosionSourceType.TNT);

            Box box = new Box(c.x - radius, c.y - radius, c.z - radius, c.x + radius, c.y + radius, c.z + radius);
            for (LivingEntity living : world.getEntitiesByClass(LivingEntity.class, box,
                    e -> e.isAlive() && e != this.getOwner())) {
                double d = living.getPos().distanceTo(c);
                if (d > radius) {
                    continue;
                }
                float dmg = (float) (40.0 * (1.0 - d / radius)) + 6.0f;
                living.damage(this.getDamageSources().indirectMagic(this, this.getOwner()), dmg);
            }

            // Purple-and-white bloom (matches the normal chaos blast).
            world.spawnParticles(ModParticles.RADIANCE, c.x, c.y + 1.0, c.z, 40, 0.6, 0.6, 0.6, 0.1);
            world.spawnParticles(ModParticles.ARCANE_TRAIL, c.x, c.y + 1.0, c.z, 60, 1.0, 1.0, 1.0, 0.3);
            for (int i = 0; i < 260; i++) {
                double phi = Math.acos(1.0 - 2.0 * world.random.nextDouble());
                double theta = world.random.nextDouble() * Math.PI * 2.0;
                double rr = radius * Math.cbrt(world.random.nextDouble());
                double sx = Math.sin(phi) * Math.cos(theta);
                double sy = Math.cos(phi);
                double sz = Math.sin(phi) * Math.sin(theta);
                world.spawnParticles(ModParticles.VOID, c.x + sx * rr, c.y + 1.0 + sy * rr, c.z + sz * rr, 1, 0, 0, 0, 0.0);
            }
            for (double ring = 2.0; ring <= radius; ring += 2.0) {
                int pts = (int) (ring * 6);
                for (int i = 0; i < pts; i++) {
                    double a = (Math.PI * 2.0 * i) / pts;
                    double px = c.x + Math.cos(a) * ring;
                    double pz = c.z + Math.sin(a) * ring;
                    world.spawnParticles(ModParticles.VOID, px, c.y + 0.3, pz, 1, 0, 0, 0, 0.0);
                    if (((int) ring) % 4 == 0) {
                        world.spawnParticles(ModParticles.LIGHT, px, c.y + 0.3, pz, 1, 0, 0, 0, 0.0);
                    }
                }
            }
            world.playSound(null, c.x, c.y, c.z, SoundEvents.ENTITY_WARDEN_SONIC_BOOM, SoundCategory.PLAYERS, 4.0f, 0.6f);
            world.playSound(null, c.x, c.y, c.z, SoundEvents.ENTITY_GENERIC_EXPLODE, SoundCategory.PLAYERS, 4.0f, 0.5f);
        }
        residualTicks = 100; // 5s of residual energy particles before despawn
        this.setNoGravity(true);
        this.setVelocity(Vec3d.ZERO);
    }

    private void placeThunderMark(BlockHitResult hit) {
        if (this.getOwner() instanceof ServerPlayerEntity owner && this.getWorld() instanceof ServerWorld world) {
            Vec3d p = hit.getPos();
            String dim = world.getRegistryKey().getValue().toString();
            ManaPlayer.get(owner).setThunderMark(p.x, p.y, p.z, dim);
            owner.sendMessage(net.minecraft.text.Text.literal("Thunder Mark placed.")
                    .formatted(net.minecraft.util.Formatting.AQUA), true);
            com.example.circlemage.network.ModNetworking.syncToClient(owner);
            world.spawnParticles(ModParticles.SPARK, p.x, p.y + 0.1, p.z, 40, 0.4, 0.2, 0.4, 0.4);
            world.playSound(null, p.x, p.y, p.z, SoundEvents.ENTITY_LIGHTNING_BOLT_IMPACT, SoundCategory.PLAYERS, 1.0f, 1.6f);
        }
    }

    private void spawnBurst() {
        if (this.getWorld() instanceof ServerWorld serverWorld) {
            if (isFireBolt()) {
                serverWorld.spawnParticles(ModParticles.EMBER, this.getX(), this.getBodyY(0.5), this.getZ(), 18, 0.2, 0.25, 0.2, 0.1);
                serverWorld.spawnParticles(ModParticles.SMOKE, this.getX(), this.getBodyY(0.5), this.getZ(), 5, 0.15, 0.2, 0.15, 0.03);
                return;
            }
            DefaultParticleType main = isSpear() ? ModParticles.LIGHT : ModParticles.ARCANE;
            serverWorld.spawnParticles(main, this.getX(), this.getBodyY(0.5), this.getZ(), 16, 0.2, 0.25, 0.2, 0.1);
            serverWorld.spawnParticles(ModParticles.ARCANE_TRAIL, this.getX(), this.getBodyY(0.5), this.getZ(), 7, 0.13, 0.18, 0.13, 0.07);
        }
    }

    @Override
    protected boolean canHit(Entity entity) {
        if (markMissile) {
            return false; // the mark bolt only reacts to blocks
        }
        return super.canHit(entity) && entity != this.getOwner();
    }

    @Override
    public void writeCustomDataToNbt(NbtCompound nbt) {
        super.writeCustomDataToNbt(nbt);
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

    @Override
    public void readCustomDataFromNbt(NbtCompound nbt) {
        super.readCustomDataFromNbt(nbt);
        traveled = nbt.getDouble("traveled");
        damage = nbt.getFloat("damage");
        this.dataTracker.set(SPEAR, nbt.getBoolean("spear"));
        this.dataTracker.set(FIRE, nbt.getBoolean("fireBolt"));
        if (nbt.contains("speed")) {
            this.dataTracker.set(SPEED_DT, nbt.getFloat("speed"));
        }
        enhanced = nbt.getBoolean("enhanced");
        fullyCharged = nbt.getBoolean("fullyCharged");
        chaos = nbt.getBoolean("chaos");
        markMissile = nbt.getBoolean("markMissile");
        residualTicks = nbt.contains("residualTicks") ? nbt.getInt("residualTicks") : -1;
    }

    @Override
    public Packet<ClientPlayPacketListener> createSpawnPacket() {
        return new EntitySpawnS2CPacket(this);
    }
}
