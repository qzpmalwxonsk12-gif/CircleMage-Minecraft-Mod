package com.example.circlemage.entity;

import com.example.circlemage.particle.ModParticles;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Law of Space (Spatial Rend): a 1-block-wide, 10-block-tall blade of torn space (centred on the
 * caster's body — 4 up, 4 down) launched along the look direction at very high speed. It annihilates
 * every block in its path and kills anything caught, travelling up to 75 blocks.
 */
public class SpatialRendEntity extends Entity {

    private static final double SPEED = 5.0;        // blocks per tick (very high)
    private static final double MAX_DISTANCE = 75.0;
    private static final int HALF_DOWN = 4;
    private static final int HALF_UP = 5;           // 4 down + middle + 4 up = 10 tall

    private Vec3 direction = new Vec3(0, 0, 1);
    private LivingEntity owner;
    private double traveled = 0.0;

    public SpatialRendEntity(EntityType<? extends SpatialRendEntity> type, Level world) {
        super(type, world);
        this.noPhysics = true;
        this.setNoGravity(true);
    }

    public SpatialRendEntity(Level world, LivingEntity owner, Vec3 look) {
        this(ModEntities.SPATIAL_REND, world);
        this.owner = owner;
        this.direction = look.lengthSqr() < 1.0e-6 ? new Vec3(0, 0, 1) : look.normalize();
        this.setDeltaMovement(this.direction); // carries the direction to the client renderer
        this.setPos(owner.getX(), owner.getY(0.5), owner.getZ());
    }

    public Vec3 getRendDirection() {
        return direction;
    }

    @Override
    protected void defineSynchedData(net.minecraft.network.syncher.SynchedEntityData.Builder builder) {
    }

    @Override
    public void tick() {
        super.tick();
        Vec3 start = this.position();

        if (this.level() instanceof ServerLevel world) {
            for (int s = 1; s <= (int) SPEED; s++) {
                carve(world, start.add(direction.scale(s)));
            }
        }

        Vec3 next = start.add(direction.scale(SPEED));
        this.setPos(next.x, next.y, next.z);
        traveled += SPEED;
        if (traveled >= MAX_DISTANCE) {
            this.discard();
        }
    }

    /** Annihilates the 1x10 column at {@code p} and kills anything in it. */
    private void carve(ServerLevel world, Vec3 p) {
        for (int dy = -HALF_DOWN; dy <= HALF_UP; dy++) {
            BlockPos pos = BlockPos.containing(p.x, p.y + dy, p.z);
            BlockState state = world.getBlockState(pos);
            if (!state.isAir() && state.getDestroySpeed(world, pos) >= 0) {
                world.removeBlock(pos, false);
            }
        }
        AABB box = new AABB(p.x - 0.8, p.y - HALF_DOWN, p.z - 0.8, p.x + 0.8, p.y + HALF_UP, p.z + 0.8);
        for (LivingEntity e : world.getEntitiesOfClass(LivingEntity.class, box, x -> x != owner && x.isAlive())) {
            e.hurt(world.damageSources().magic(), 1000.0f);
        }
        // Space rip particles up the column.
        for (int dy = -HALF_DOWN; dy <= HALF_UP; dy += 2) {
            world.sendParticles(ModParticles.ARCANE, p.x, p.y + dy, p.z, 1, 0.1, 0.1, 0.1, 0.02);
            world.sendParticles(ModParticles.VOID, p.x, p.y + dy + 1, p.z, 1, 0.1, 0.1, 0.1, 0.02);
        }
    }

    protected void readAdditionalSaveDataLegacy(CompoundTag nbt) {
        traveled = nbt.getDoubleOr("traveled", 0d);
        if (nbt.contains("dirX")) {
            direction = new Vec3(nbt.getDoubleOr("dirX", 0d), nbt.getDoubleOr("dirY", 0d), nbt.getDoubleOr("dirZ", 0d));
        }
    }

    protected void addAdditionalSaveDataLegacy(CompoundTag nbt) {
        nbt.putDouble("traveled", traveled);
        nbt.putDouble("dirX", direction.x);
        nbt.putDouble("dirY", direction.y);
        nbt.putDouble("dirZ", direction.z);
    }

    @Override protected void addAdditionalSaveData(net.minecraft.world.level.storage.ValueOutput value) {  CompoundTag tag = new CompoundTag(); addAdditionalSaveDataLegacy(tag); com.example.circlemage.util.NbtBridge.write(value, tag); }

    @Override protected void readAdditionalSaveData(net.minecraft.world.level.storage.ValueInput value) {  readAdditionalSaveDataLegacy(com.example.circlemage.util.NbtBridge.read(value)); }

    @Override public boolean hurtServer(net.minecraft.server.level.ServerLevel world, net.minecraft.world.damagesource.DamageSource source, float amount) { return false; }
}
