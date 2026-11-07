package com.example.circlemage.entity;

import com.example.circlemage.mana.ManaData;
import com.example.circlemage.mana.ManaPlayer;
import com.example.circlemage.network.ModNetworking;
import com.example.circlemage.particle.ModParticles;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.listener.ClientPlayPacketListener;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.EntitySpawnS2CPacket;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.Box;
import net.minecraft.world.World;

import java.util.UUID;

/**
 * A translucent magic barrier. In WALL mode it is a solid 5x5 wall that no entity can pass through.
 * In DOME mode it is a dodecahedral shield that follows its owner and absorbs incoming attacks
 * (handled by a damage mixin), costing mana per hit and, while the owner sneaks, per second.
 */
public class MagicBarrierEntity extends Entity {

    public static final byte MODE_WALL = 0;
    public static final byte MODE_DOME = 1;

    private static final TrackedData<Byte> MODE =
            DataTracker.registerData(MagicBarrierEntity.class, TrackedDataHandlerRegistry.BYTE);
    private static final TrackedData<Float> HALF_X =
            DataTracker.registerData(MagicBarrierEntity.class, TrackedDataHandlerRegistry.FLOAT);
    private static final TrackedData<Float> HALF_Y =
            DataTracker.registerData(MagicBarrierEntity.class, TrackedDataHandlerRegistry.FLOAT);
    private static final TrackedData<Float> HALF_Z =
            DataTracker.registerData(MagicBarrierEntity.class, TrackedDataHandlerRegistry.FLOAT);

    private static final double DOME_UPKEEP_PER_TICK = 500.0 / 20.0; // 500 mana/sec while sneaking
    private static final int DOME_HIT_COST = 500;

    private UUID ownerUuid;
    private int lifetimeTicks = 140;
    private int hitsRemaining = 5;

    public MagicBarrierEntity(EntityType<? extends MagicBarrierEntity> type, World world) {
        super(type, world);
    }

    @Override
    protected void initDataTracker() {
        this.dataTracker.startTracking(MODE, MODE_WALL);
        this.dataTracker.startTracking(HALF_X, 0.5f);
        this.dataTracker.startTracking(HALF_Y, 0.5f);
        this.dataTracker.startTracking(HALF_Z, 0.5f);
    }

    public void initWall(PlayerEntity owner, float halfX, float halfY, float halfZ, int lifetime) {
        this.ownerUuid = owner.getUuid();
        this.dataTracker.set(MODE, MODE_WALL);
        this.dataTracker.set(HALF_X, halfX);
        this.dataTracker.set(HALF_Y, halfY);
        this.dataTracker.set(HALF_Z, halfZ);
        this.lifetimeTicks = lifetime;
        this.setPosition(this.getX(), this.getY(), this.getZ()); // recompute bounding box
    }

    public void initDome(PlayerEntity owner, int lifetime, int hits) {
        this.ownerUuid = owner.getUuid();
        this.dataTracker.set(MODE, MODE_DOME);
        this.dataTracker.set(HALF_X, 1.6f);
        this.dataTracker.set(HALF_Y, 1.6f);
        this.dataTracker.set(HALF_Z, 1.6f);
        this.lifetimeTicks = lifetime;
        this.hitsRemaining = hits;
        this.setPosition(this.getX(), this.getY(), this.getZ());
    }

    public boolean isDome() {
        return this.dataTracker.get(MODE) == MODE_DOME;
    }

    public float getHalfX() {
        return this.dataTracker.get(HALF_X);
    }

    public float getHalfY() {
        return this.dataTracker.get(HALF_Y);
    }

    public float getHalfZ() {
        return this.dataTracker.get(HALF_Z);
    }

    /** Only an active dome with hits remaining intercepts damage. */
    public boolean isActiveDome() {
        return !this.isRemoved() && isDome() && hitsRemaining > 0;
    }

    @Override
    public boolean isCollidable() {
        // The wall physically blocks; the dome keeps mobs out via repulsion (see repelMobs) so it
        // never traps its owner.
        return this.dataTracker.get(MODE) == MODE_WALL;
    }

    @Override
    public boolean isFireImmune() {
        // Magic barriers don't burn.
        return true;
    }

    @Override
    protected Box calculateBoundingBox() {
        try {
            float hx = this.dataTracker.get(HALF_X);
            float hy = this.dataTracker.get(HALF_Y);
            float hz = this.dataTracker.get(HALF_Z);
            return new Box(getX() - hx, getY() - hy, getZ() - hz, getX() + hx, getY() + hy, getZ() + hz);
        } catch (Exception e) {
            return super.calculateBoundingBox();
        }
    }

    /** Called from the damage mixin when this dome absorbs an attack aimed at its owner. */
    public void absorbHit() {
        if (this.getWorld().isClient || !(this.getWorld() instanceof ServerWorld world)) {
            return;
        }
        PlayerEntity owner = resolveOwner(world);
        boolean sustaining = owner != null && owner.isSneaking();
        boolean paid = owner != null && ManaPlayer.get(owner).trySpendMana(DOME_HIT_COST);
        if (owner instanceof ServerPlayerEntity serverOwner) {
            ModNetworking.syncToClient(serverOwner);
        }

        world.spawnParticles(ModParticles.BARRIER_SHARD, getX(), getY(), getZ(), 24, 1.2, 1.2, 1.2, 0.2);
        world.spawnParticles(ModParticles.ARCANE_TRAIL, getX(), getY(), getZ(), 10, 1.0, 1.0, 1.0, 0.15);
        world.playSound(null, getX(), getY(), getZ(),
                SoundEvents.BLOCK_GLASS_BREAK, SoundCategory.PLAYERS, 0.8f, 1.4f);

        if (sustaining) {
            // While shifting the dome is invincible — it only shatters if the hit can't be paid for.
            if (!paid) {
                shatter();
            }
        } else {
            hitsRemaining--;
            if (hitsRemaining <= 0) {
                shatter();
            }
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (this.getWorld().isClient || !(this.getWorld() instanceof ServerWorld world)) {
            return;
        }

        if (isDome()) {
            PlayerEntity owner = resolveOwner(world);
            if (owner == null || !owner.isAlive()) {
                shatter();
                return;
            }
            this.setPosition(owner.getX(), owner.getY() + 1.0, owner.getZ());

            if (owner.isSneaking()) {
                // While shifting: 500 mana/sec upkeep, no timer expiry, invincible to hits.
                if (!ManaPlayer.get(owner).trySpendMana(DOME_UPKEEP_PER_TICK)) {
                    shatter();
                    return;
                }
                if (owner instanceof ServerPlayerEntity serverOwner) {
                    ModNetworking.syncToClient(serverOwner);
                }
            } else if (--lifetimeTicks <= 0) {
                shatter();
                return;
            }
            repelMobs(world, owner);
            spawnDomeParticles(world);
        } else {
            if (--lifetimeTicks <= 0) {
                this.discard();
                return;
            }
            spawnWallParticles(world);
        }
    }

    /**
     * Keeps non-player mobs out of the dome by holding any that stray inside at the shell and shoving
     * them outward. This is movement only — hostile mobs can still strike the dome (the damage mixin
     * intercepts hits aimed at the owner).
     */
    private void repelMobs(ServerWorld world, PlayerEntity owner) {
        double r = 1.75;
        double cx = getX();
        double cz = getZ();
        Box box = new Box(cx - r, getY() - r, cz - r, cx + r, getY() + r, cz + r);
        for (LivingEntity mob : world.getEntitiesByClass(LivingEntity.class, box,
                e -> e != owner && e.isAlive() && !(e instanceof PlayerEntity))) {
            double dx = mob.getX() - cx;
            double dz = mob.getZ() - cz;
            double distSq = dx * dx + dz * dz;
            if (distSq >= r * r) {
                continue;
            }
            double dist = Math.sqrt(distSq);
            double nx;
            double nz;
            if (dist < 1.0e-4) {
                double a = world.random.nextDouble() * Math.PI * 2.0;
                nx = Math.cos(a);
                nz = Math.sin(a);
            } else {
                nx = dx / dist;
                nz = dz / dist;
            }
            mob.setPosition(cx + nx * r, mob.getY(), cz + nz * r);
            mob.addVelocity(nx * 0.4, 0.02, nz * 0.4);
            mob.velocityModified = true;
        }
    }

    private PlayerEntity resolveOwner(ServerWorld world) {
        return ownerUuid == null ? null : world.getPlayerByUuid(ownerUuid);
    }

    private void shatter() {
        if (this.getWorld() instanceof ServerWorld world) {
            world.spawnParticles(ModParticles.BARRIER_SHARD, getX(), getY(), getZ(), 40, 1.2, 1.2, 1.2, 0.18);
            world.spawnParticles(ModParticles.ARCANE_TRAIL, getX(), getY(), getZ(), 16, 1.0, 1.0, 1.0, 0.12);
            world.playSound(null, getX(), getY(), getZ(),
                    SoundEvents.BLOCK_GLASS_BREAK, SoundCategory.PLAYERS, 1.0f, 0.8f);
            PlayerEntity owner = resolveOwner(world);
            if (owner != null) {
                ManaData data = ManaPlayer.get(owner);
                if (data.getActiveDomeId() == this.getId()) {
                    data.setActiveDomeId(-1);
                }
            }
        }
        this.discard();
    }

    /**
     * Light shimmer on the wall face. The solid panel itself is drawn by the entity renderer; these
     * are sparse sparks that crawl across it so it reads as live energy rather than a static plane.
     */
    private void spawnWallParticles(ServerWorld world) {
        if (world.getTime() % 5 != 0) {
            return;
        }
        float hx = this.dataTracker.get(HALF_X);
        float hy = this.dataTracker.get(HALF_Y);
        float hz = this.dataTracker.get(HALF_Z);
        boolean spanX = hx >= hz;
        double wideHalf = Math.max(hx, hz);
        for (int i = 0; i < 4; i++) {
            double wide = (world.random.nextDouble() * 2.0 - 1.0) * wideHalf;
            double fx = getX() + (spanX ? wide : 0.0);
            double fz = getZ() + (spanX ? 0.0 : wide);
            double fy = getY() - hy + world.random.nextDouble() * 2.0 * hy;
            world.spawnParticles(ModParticles.BARRIER_SHARD, fx, fy, fz, 1, 0.0, 0.0, 0.0, 0.0);
        }
    }

    /**
     * Light shimmer on the dome surface, scattered across the sphere. The translucent shield shell is
     * drawn by the entity renderer.
     */
    private void spawnDomeParticles(ServerWorld world) {
        if (world.getTime() % 4 != 0) {
            return;
        }
        double cx = getX();
        double cy = getY();
        double cz = getZ();
        double r = 1.6;
        for (int i = 0; i < 5; i++) {
            double phi = Math.acos(1.0 - 2.0 * world.random.nextDouble());
            double theta = world.random.nextDouble() * Math.PI * 2.0;
            double sx = Math.sin(phi) * Math.cos(theta);
            double sy = Math.cos(phi);
            double sz = Math.sin(phi) * Math.sin(theta);
            world.spawnParticles(ModParticles.BARRIER_SHARD, cx + sx * r, cy + sy * r, cz + sz * r, 1, 0.0, 0.0, 0.0, 0.0);
        }
    }

    @Override
    protected void readCustomDataFromNbt(NbtCompound nbt) {
        if (nbt.containsUuid("Owner")) {
            this.ownerUuid = nbt.getUuid("Owner");
        }
        this.lifetimeTicks = nbt.getInt("Lifetime");
        this.hitsRemaining = nbt.getInt("Hits");
        this.dataTracker.set(MODE, nbt.getByte("Mode"));
        this.dataTracker.set(HALF_X, nbt.getFloat("HalfX"));
        this.dataTracker.set(HALF_Y, nbt.getFloat("HalfY"));
        this.dataTracker.set(HALF_Z, nbt.getFloat("HalfZ"));
    }

    @Override
    protected void writeCustomDataToNbt(NbtCompound nbt) {
        if (ownerUuid != null) {
            nbt.putUuid("Owner", ownerUuid);
        }
        nbt.putInt("Lifetime", lifetimeTicks);
        nbt.putInt("Hits", hitsRemaining);
        nbt.putByte("Mode", this.dataTracker.get(MODE));
        nbt.putFloat("HalfX", this.dataTracker.get(HALF_X));
        nbt.putFloat("HalfY", this.dataTracker.get(HALF_Y));
        nbt.putFloat("HalfZ", this.dataTracker.get(HALF_Z));
    }

    @Override
    public Packet<ClientPlayPacketListener> createSpawnPacket() {
        return new EntitySpawnS2CPacket(this);
    }
}
