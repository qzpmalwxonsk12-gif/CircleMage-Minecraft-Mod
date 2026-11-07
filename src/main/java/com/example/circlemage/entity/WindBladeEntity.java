package com.example.circlemage.entity;

import com.example.circlemage.particle.ModParticles;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.entity.projectile.ProjectileUtil;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.listener.ClientPlayPacketListener;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.EntitySpawnS2CPacket;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

/**
 * A Wind Blade — a fast, gravity-free slicing projectile. Flies straight, leaves a gale streak, and on
 * impact deals magic damage plus a sharp knockback. Cast by the player's Wind Blade spell as a quick
 * 5-blade volley; each blade deals 1/3 of a Magic Missile's damage.
 */
public class WindBladeEntity extends ProjectileEntity {

    private static final double SPEED = 2.2;
    private static final double MAX_DISTANCE = 32.0;

    private float damage = 5.0f;
    private double traveled = 0.0;

    public WindBladeEntity(EntityType<? extends WindBladeEntity> type, World world) {
        super(type, world);
        this.setNoGravity(true);
    }

    public WindBladeEntity(World world, LivingEntity owner, float damage) {
        this(ModEntities.WIND_BLADE, world);
        this.setOwner(owner);
        this.damage = damage;
        Vec3d eye = owner.getEyePos();
        this.setPosition(eye.x, eye.y - 0.1, eye.z);
    }

    public void launch(Vec3d direction) {
        this.setVelocity(direction.normalize().multiply(SPEED));
    }

    @Override
    protected void initDataTracker() {
    }

    @Override
    public void tick() {
        super.tick();
        Vec3d velocity = this.getVelocity();

        HitResult hit = ProjectileUtil.getCollision(this, this::canHit);
        if (hit.getType() != HitResult.Type.MISS) {
            this.onCollision(hit);
            if (this.isRemoved()) {
                return;
            }
        }

        Vec3d next = this.getPos().add(velocity);
        this.setPosition(next.x, next.y, next.z);

        if (this.getWorld() instanceof ServerWorld world) {
            world.spawnParticles(ModParticles.GALE, getX(), getY(), getZ(), 4, 0.12, 0.12, 0.12, 0.02);
        }

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
        Entity hit = entityHitResult.getEntity();
        hit.damage(this.getDamageSources().indirectMagic(this, this.getOwner()), this.damage);
        if (hit instanceof LivingEntity living) {
            Vec3d push = this.getVelocity().multiply(1, 0, 1).normalize().multiply(0.9);
            living.addVelocity(push.x, 0.3, push.z);
            living.velocityModified = true;
        }
        burst();
        this.discard();
    }

    @Override
    protected void onBlockHit(BlockHitResult blockHitResult) {
        super.onBlockHit(blockHitResult);
        if (this.getWorld().isClient) {
            return;
        }
        burst();
        this.discard();
    }

    private void burst() {
        if (this.getWorld() instanceof ServerWorld world) {
            world.spawnParticles(ModParticles.GALE, getX(), getBodyY(0.5), getZ(), 16, 0.3, 0.3, 0.3, 0.15);
            world.playSound(null, getX(), getY(), getZ(), SoundEvents.ENTITY_PHANTOM_FLAP, SoundCategory.HOSTILE, 0.8f, 1.6f);
        }
    }

    @Override
    protected boolean canHit(Entity entity) {
        return super.canHit(entity) && entity != this.getOwner();
    }

    @Override
    public void writeCustomDataToNbt(NbtCompound nbt) {
        super.writeCustomDataToNbt(nbt);
        nbt.putFloat("damage", damage);
        nbt.putDouble("traveled", traveled);
    }

    @Override
    public void readCustomDataFromNbt(NbtCompound nbt) {
        super.readCustomDataFromNbt(nbt);
        damage = nbt.getFloat("damage");
        traveled = nbt.getDouble("traveled");
    }

    @Override
    public Packet<ClientPlayPacketListener> createSpawnPacket() {
        return new EntitySpawnS2CPacket(this);
    }
}
