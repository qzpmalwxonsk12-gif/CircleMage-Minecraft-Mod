package com.example.circlemage.entity;

import com.example.circlemage.mana.ManaData;
import com.example.circlemage.mana.ManaPlayer;
import com.example.circlemage.network.ModNetworking;
import com.example.circlemage.particle.ModParticles;
import java.util.UUID;
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
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

/**
 * A translucent magic barrier. In WALL mode it is a solid 5x5 wall that no entity can pass through.
 * In DOME mode it is a dodecahedral shield that follows its owner and absorbs incoming attacks
 * (handled by a damage mixin), costing mana per hit and, while the owner sneaks, per second.
 */
public class MagicBarrierEntity extends Entity {

    public static final byte MODE_WALL = 0;
    public static final byte MODE_DOME = 1;

    private static final EntityDataAccessor<Byte> MODE =
            SynchedEntityData.defineId(MagicBarrierEntity.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Float> HALF_X =
            SynchedEntityData.defineId(MagicBarrierEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> HALF_Y =
            SynchedEntityData.defineId(MagicBarrierEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> HALF_Z =
            SynchedEntityData.defineId(MagicBarrierEntity.class, EntityDataSerializers.FLOAT);

    private static final double DOME_UPKEEP_PER_TICK = 500.0 / 20.0; // 500 mana/sec while sneaking
    private static final int DOME_HIT_COST = 500;

    private UUID ownerUuid;
    private int lifetimeTicks = 140;
    private int hitsRemaining = 5;

    public MagicBarrierEntity(EntityType<? extends MagicBarrierEntity> type, Level world) {
        super(type, world);
    }

    @Override
    protected void defineSynchedData(net.minecraft.network.syncher.SynchedEntityData.Builder builder) {
        builder.define(MODE, MODE_WALL);
        builder.define(HALF_X, 0.5f);
        builder.define(HALF_Y, 0.5f);
        builder.define(HALF_Z, 0.5f);
    }

    public void initWall(Player owner, float halfX, float halfY, float halfZ, int lifetime) {
        this.ownerUuid = owner.getUUID();
        this.entityData.set(MODE, MODE_WALL);
        this.entityData.set(HALF_X, halfX);
        this.entityData.set(HALF_Y, halfY);
        this.entityData.set(HALF_Z, halfZ);
        this.lifetimeTicks = lifetime;
        this.setPos(this.getX(), this.getY(), this.getZ()); // recompute bounding box
    }

    public void initDome(Player owner, int lifetime, int hits) {
        this.ownerUuid = owner.getUUID();
        this.entityData.set(MODE, MODE_DOME);
        this.entityData.set(HALF_X, 1.6f);
        this.entityData.set(HALF_Y, 1.6f);
        this.entityData.set(HALF_Z, 1.6f);
        this.lifetimeTicks = lifetime;
        this.hitsRemaining = hits;
        this.setPos(this.getX(), this.getY(), this.getZ());
    }

    public boolean isDome() {
        return this.entityData.get(MODE) == MODE_DOME;
    }

    public float getHalfX() {
        return this.entityData.get(HALF_X);
    }

    public float getHalfY() {
        return this.entityData.get(HALF_Y);
    }

    public float getHalfZ() {
        return this.entityData.get(HALF_Z);
    }

    /** Only an active dome with hits remaining intercepts damage. */
    public boolean isActiveDome() {
        return !this.isRemoved() && isDome() && hitsRemaining > 0;
    }

    @Override
    public boolean canBeCollidedWith(Entity other) {
        // The wall physically blocks; the dome keeps mobs out via repulsion (see repelMobs) so it
        // never traps its owner.
        return this.entityData.get(MODE) == MODE_WALL;
    }

    @Override
    public boolean fireImmune() {
        // Magic barriers don't burn.
        return true;
    }

    @Override
    protected AABB makeBoundingBox(net.minecraft.world.phys.Vec3 position) {
        try {
            float hx = this.entityData.get(HALF_X);
            float hy = this.entityData.get(HALF_Y);
            float hz = this.entityData.get(HALF_Z);
            return new AABB(position.x - hx, position.y - hy, position.z - hz, position.x + hx, position.y + hy, position.z + hz);
        } catch (Exception e) {
            return super.makeBoundingBox(position);
        }
    }

    /** Called from the damage mixin when this dome absorbs an attack aimed at its owner. */
    public void absorbHit() {
        if (this.level().isClientSide() || !(this.level() instanceof ServerLevel world)) {
            return;
        }
        Player owner = resolveOwner(world);
        boolean sustaining = owner != null && owner.isShiftKeyDown();
        boolean paid = owner != null && ManaPlayer.get(owner).trySpendMana(DOME_HIT_COST);
        if (owner instanceof ServerPlayer serverOwner) {
            ModNetworking.syncToClient(serverOwner);
        }

        world.sendParticles(ModParticles.BARRIER_SHARD, getX(), getY(), getZ(), 24, 1.2, 1.2, 1.2, 0.2);
        world.sendParticles(ModParticles.ARCANE_TRAIL, getX(), getY(), getZ(), 10, 1.0, 1.0, 1.0, 0.15);
        world.playSound(null, getX(), getY(), getZ(),
                SoundEvents.GLASS_BREAK, SoundSource.PLAYERS, 0.8f, 1.4f);

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
        if (this.level().isClientSide() || !(this.level() instanceof ServerLevel world)) {
            return;
        }

        if (isDome()) {
            Player owner = resolveOwner(world);
            if (owner == null || !owner.isAlive()) {
                shatter();
                return;
            }
            this.setPos(owner.getX(), owner.getY() + 1.0, owner.getZ());

            if (owner.isShiftKeyDown()) {
                // While shifting: 500 mana/sec upkeep, no timer expiry, invincible to hits.
                if (!ManaPlayer.get(owner).trySpendMana(DOME_UPKEEP_PER_TICK)) {
                    shatter();
                    return;
                }
                if (owner instanceof ServerPlayer serverOwner) {
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
    private void repelMobs(ServerLevel world, Player owner) {
        double r = 1.75;
        double cx = getX();
        double cz = getZ();
        AABB box = new AABB(cx - r, getY() - r, cz - r, cx + r, getY() + r, cz + r);
        for (LivingEntity mob : world.getEntitiesOfClass(LivingEntity.class, box,
                e -> e != owner && e.isAlive() && !(e instanceof Player))) {
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
                double a = world.getRandom().nextDouble() * Math.PI * 2.0;
                nx = Math.cos(a);
                nz = Math.sin(a);
            } else {
                nx = dx / dist;
                nz = dz / dist;
            }
            mob.setPos(cx + nx * r, mob.getY(), cz + nz * r);
            mob.push(nx * 0.4, 0.02, nz * 0.4);
            mob.syncVelocity = true;
        }
    }

    private Player resolveOwner(ServerLevel world) {
        return ownerUuid == null ? null : world.getPlayerByUUID(ownerUuid);
    }

    private void shatter() {
        if (this.level() instanceof ServerLevel world) {
            world.sendParticles(ModParticles.BARRIER_SHARD, getX(), getY(), getZ(), 40, 1.2, 1.2, 1.2, 0.18);
            world.sendParticles(ModParticles.ARCANE_TRAIL, getX(), getY(), getZ(), 16, 1.0, 1.0, 1.0, 0.12);
            world.playSound(null, getX(), getY(), getZ(),
                    SoundEvents.GLASS_BREAK, SoundSource.PLAYERS, 1.0f, 0.8f);
            Player owner = resolveOwner(world);
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
    private void spawnWallParticles(ServerLevel world) {
        if (world.getGameTime() % 5 != 0) {
            return;
        }
        float hx = this.entityData.get(HALF_X);
        float hy = this.entityData.get(HALF_Y);
        float hz = this.entityData.get(HALF_Z);
        boolean spanX = hx >= hz;
        double wideHalf = Math.max(hx, hz);
        for (int i = 0; i < 4; i++) {
            double wide = (world.getRandom().nextDouble() * 2.0 - 1.0) * wideHalf;
            double fx = getX() + (spanX ? wide : 0.0);
            double fz = getZ() + (spanX ? 0.0 : wide);
            double fy = getY() - hy + world.getRandom().nextDouble() * 2.0 * hy;
            world.sendParticles(ModParticles.BARRIER_SHARD, fx, fy, fz, 1, 0.0, 0.0, 0.0, 0.0);
        }
    }

    /**
     * Light shimmer on the dome surface, scattered across the sphere. The translucent shield shell is
     * drawn by the entity renderer.
     */
    private void spawnDomeParticles(ServerLevel world) {
        if (world.getGameTime() % 4 != 0) {
            return;
        }
        double cx = getX();
        double cy = getY();
        double cz = getZ();
        double r = 1.6;
        for (int i = 0; i < 5; i++) {
            double phi = Math.acos(1.0 - 2.0 * world.getRandom().nextDouble());
            double theta = world.getRandom().nextDouble() * Math.PI * 2.0;
            double sx = Math.sin(phi) * Math.cos(theta);
            double sy = Math.cos(phi);
            double sz = Math.sin(phi) * Math.sin(theta);
            world.sendParticles(ModParticles.BARRIER_SHARD, cx + sx * r, cy + sy * r, cz + sz * r, 1, 0.0, 0.0, 0.0, 0.0);
        }
    }

    protected void readAdditionalSaveDataLegacy(CompoundTag nbt) {
        if (nbt.read("Owner", net.minecraft.core.UUIDUtil.CODEC).isPresent()) {
            this.ownerUuid = nbt.read("Owner", net.minecraft.core.UUIDUtil.CODEC).orElse(null);
        }
        this.lifetimeTicks = nbt.getIntOr("Lifetime", 0);
        this.hitsRemaining = nbt.getIntOr("Hits", 0);
        this.entityData.set(MODE, nbt.getByteOr("Mode", (byte)0));
        this.entityData.set(HALF_X, nbt.getFloatOr("HalfX", 0f));
        this.entityData.set(HALF_Y, nbt.getFloatOr("HalfY", 0f));
        this.entityData.set(HALF_Z, nbt.getFloatOr("HalfZ", 0f));
    }

    protected void addAdditionalSaveDataLegacy(CompoundTag nbt) {
        if (ownerUuid != null) {
            nbt.store("Owner", net.minecraft.core.UUIDUtil.CODEC, ownerUuid);
        }
        nbt.putInt("Lifetime", lifetimeTicks);
        nbt.putInt("Hits", hitsRemaining);
        nbt.putByte("Mode", this.entityData.get(MODE));
        nbt.putFloat("HalfX", this.entityData.get(HALF_X));
        nbt.putFloat("HalfY", this.entityData.get(HALF_Y));
        nbt.putFloat("HalfZ", this.entityData.get(HALF_Z));
    }

    @Override protected void addAdditionalSaveData(net.minecraft.world.level.storage.ValueOutput value) {  CompoundTag tag = new CompoundTag(); addAdditionalSaveDataLegacy(tag); com.example.circlemage.util.NbtBridge.write(value, tag); }

    @Override protected void readAdditionalSaveData(net.minecraft.world.level.storage.ValueInput value) {  readAdditionalSaveDataLegacy(com.example.circlemage.util.NbtBridge.read(value)); }

    @Override public boolean hurtServer(net.minecraft.server.level.ServerLevel world, net.minecraft.world.damagesource.DamageSource source, float amount) { return false; }
}
