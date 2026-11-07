package com.example.circlemage.entity;

import com.example.circlemage.particle.ModParticles;
import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.listener.ClientPlayPacketListener;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.EntitySpawnS2CPacket;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

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

    private Vec3d direction = new Vec3d(0, 0, 1);
    private LivingEntity owner;
    private double traveled = 0.0;

    public SpatialRendEntity(EntityType<? extends SpatialRendEntity> type, World world) {
        super(type, world);
        this.noClip = true;
        this.setNoGravity(true);
    }

    public SpatialRendEntity(World world, LivingEntity owner, Vec3d look) {
        this(ModEntities.SPATIAL_REND, world);
        this.owner = owner;
        this.direction = look.lengthSquared() < 1.0e-6 ? new Vec3d(0, 0, 1) : look.normalize();
        this.setVelocity(this.direction); // carries the direction to the client renderer
        this.setPosition(owner.getX(), owner.getBodyY(0.5), owner.getZ());
    }

    public Vec3d getDirection() {
        return direction;
    }

    @Override
    protected void initDataTracker() {
    }

    @Override
    public void tick() {
        super.tick();
        Vec3d start = this.getPos();

        if (this.getWorld() instanceof ServerWorld world) {
            for (int s = 1; s <= (int) SPEED; s++) {
                carve(world, start.add(direction.multiply(s)));
            }
        }

        Vec3d next = start.add(direction.multiply(SPEED));
        this.setPosition(next.x, next.y, next.z);
        traveled += SPEED;
        if (traveled >= MAX_DISTANCE) {
            this.discard();
        }
    }

    /** Annihilates the 1x10 column at {@code p} and kills anything in it. */
    private void carve(ServerWorld world, Vec3d p) {
        for (int dy = -HALF_DOWN; dy <= HALF_UP; dy++) {
            BlockPos pos = BlockPos.ofFloored(p.x, p.y + dy, p.z);
            BlockState state = world.getBlockState(pos);
            if (!state.isAir() && state.getHardness(world, pos) >= 0) {
                world.removeBlock(pos, false);
            }
        }
        Box box = new Box(p.x - 0.8, p.y - HALF_DOWN, p.z - 0.8, p.x + 0.8, p.y + HALF_UP, p.z + 0.8);
        for (LivingEntity e : world.getEntitiesByClass(LivingEntity.class, box, x -> x != owner && x.isAlive())) {
            e.damage(world.getDamageSources().magic(), 1000.0f);
        }
        // Space rip particles up the column.
        for (int dy = -HALF_DOWN; dy <= HALF_UP; dy += 2) {
            world.spawnParticles(ModParticles.ARCANE, p.x, p.y + dy, p.z, 1, 0.1, 0.1, 0.1, 0.02);
            world.spawnParticles(ModParticles.VOID, p.x, p.y + dy + 1, p.z, 1, 0.1, 0.1, 0.1, 0.02);
        }
    }

    @Override
    protected void readCustomDataFromNbt(NbtCompound nbt) {
        traveled = nbt.getDouble("traveled");
        if (nbt.contains("dirX")) {
            direction = new Vec3d(nbt.getDouble("dirX"), nbt.getDouble("dirY"), nbt.getDouble("dirZ"));
        }
    }

    @Override
    protected void writeCustomDataToNbt(NbtCompound nbt) {
        nbt.putDouble("traveled", traveled);
        nbt.putDouble("dirX", direction.x);
        nbt.putDouble("dirY", direction.y);
        nbt.putDouble("dirZ", direction.z);
    }

    @Override
    public Packet<ClientPlayPacketListener> createSpawnPacket() {
        return new EntitySpawnS2CPacket(this);
    }
}
