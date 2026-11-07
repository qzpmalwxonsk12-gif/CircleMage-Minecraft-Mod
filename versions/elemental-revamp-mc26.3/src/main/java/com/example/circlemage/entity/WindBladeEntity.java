package com.example.circlemage.entity;

import com.example.circlemage.particle.ModParticles;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * A Wind Blade — a fast, gravity-free slicing projectile. Flies straight, leaves a gale streak, and on
 * impact deals magic damage plus a sharp knockback. Cast by the player's Wind Blade spell as a quick
 * 5-blade volley; each blade deals 1/3 of a Magic Missile's damage.
 */
public class WindBladeEntity extends Projectile {

    private static final double SPEED = 2.2;
    private static final double MAX_DISTANCE = 32.0;

    private float damage = 5.0f;
    private double traveled = 0.0;

    public WindBladeEntity(EntityType<? extends WindBladeEntity> type, Level world) {
        super(type, world);
        this.setNoGravity(true);
    }

    public WindBladeEntity(Level world, LivingEntity owner, float damage) {
        this(ModEntities.WIND_BLADE, world);
        this.setOwner(owner);
        this.damage = damage;
        Vec3 eye = owner.getEyePosition();
        this.setPos(eye.x, eye.y - 0.1, eye.z);
    }

    public void launch(Vec3 direction) {
        this.setDeltaMovement(direction.normalize().scale(SPEED));
    }

    @Override
    protected void defineSynchedData(net.minecraft.network.syncher.SynchedEntityData.Builder builder) {
    }

    @Override
    public void tick() {
        super.tick();
        Vec3 velocity = this.getDeltaMovement();

        HitResult hit = ProjectileUtil.getHitResultOnMoveVector(this, this::canHitEntity);
        if (hit.getType() != HitResult.Type.MISS) {
            this.onHit(hit);
            if (this.isRemoved()) {
                return;
            }
        }

        Vec3 next = this.position().add(velocity);
        this.setPos(next.x, next.y, next.z);

        if (this.level() instanceof ServerLevel world) {
            world.sendParticles(ModParticles.GALE, getX(), getY(), getZ(), 4, 0.12, 0.12, 0.12, 0.02);
        }

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
        Entity hit = entityHitResult.getEntity();
        if(hit instanceof LivingEntity l) l.damageCooldownTime = 0;
        hit.hurt(this.damageSources().indirectMagic(this, this.getOwner()), this.damage);
        if (hit instanceof LivingEntity living) {
            Vec3 push = this.getDeltaMovement().multiply(1, 0, 1).normalize().scale(0.18);
            living.push(push.x, 0.06, push.z);
            living.syncVelocity = true;
        }
        burst();
        this.discard();
    }

    @Override
    protected void onHitBlock(BlockHitResult blockHitResult) {
        super.onHitBlock(blockHitResult);
        if (this.level().isClientSide()) {
            return;
        }
        burst();
        this.discard();
    }

    private void burst() {
        if (this.level() instanceof ServerLevel world) {
            world.sendParticles(ModParticles.GALE, getX(), getY(0.5), getZ(), 16, 0.3, 0.3, 0.3, 0.15);
            world.playSound(null, getX(), getY(), getZ(), SoundEvents.PHANTOM_FLAP, SoundSource.HOSTILE, 0.8f, 1.6f);
        }
    }

    @Override
    protected boolean canHitEntity(Entity entity) {
        return super.canHitEntity(entity) && entity != this.getOwner();
    }

    public void addAdditionalSaveDataLegacy(CompoundTag nbt) {
        
        nbt.putFloat("damage", damage);
        nbt.putDouble("traveled", traveled);
    }

    public void readAdditionalSaveDataLegacy(CompoundTag nbt) {
        
        damage = nbt.getFloatOr("damage", 0f);
        traveled = nbt.getDoubleOr("traveled", 0d);
    }

    @Override protected void addAdditionalSaveData(net.minecraft.world.level.storage.ValueOutput value) { super.addAdditionalSaveData(value); CompoundTag tag = new CompoundTag(); addAdditionalSaveDataLegacy(tag); com.example.circlemage.util.NbtBridge.write(value, tag); }

    @Override protected void readAdditionalSaveData(net.minecraft.world.level.storage.ValueInput value) { super.readAdditionalSaveData(value); readAdditionalSaveDataLegacy(com.example.circlemage.util.NbtBridge.read(value)); }
}
