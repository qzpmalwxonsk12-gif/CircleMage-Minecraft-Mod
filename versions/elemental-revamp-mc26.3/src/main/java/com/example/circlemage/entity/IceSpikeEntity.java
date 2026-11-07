package com.example.circlemage.entity;

import com.example.circlemage.particle.ModParticles;
import com.example.circlemage.spell.RevampStatusEffects;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
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

public class IceSpikeEntity extends Projectile {
    public IceSpikeEntity(EntityType<? extends IceSpikeEntity> type, Level world) { super(type, world); setNoGravity(true); }
    public IceSpikeEntity(Level world, LivingEntity owner, Vec3 direction) {
        this(ModEntities.ICE_SPIKE, world); setOwner(owner);
        setPos(owner.getEyePosition().add(0, -.1, 0)); setDeltaMovement(direction.normalize().scale(1.8));
    }
    @Override protected void defineSynchedData(net.minecraft.network.syncher.SynchedEntityData.Builder builder) {}
    @Override public void tick() {
        super.tick();
        HitResult hit = ProjectileUtil.getHitResultOnMoveVector(this, this::canHitEntity);
        if (hit.getType() != HitResult.Type.MISS) onHit(hit);
        if (isRemoved()) return;
        setPos(position().add(getDeltaMovement()));
        if (level() instanceof ServerLevel w) w.sendParticles(ModParticles.FROST, getX(), getY(), getZ(), 2, .04, .04, .04, .01);
        if (tickCount > 40) discard();
    }
    @Override protected void onHitEntity(EntityHitResult hit) {
        super.onHitEntity(hit);
        if (level().isClientSide()) return;
        if (hit.getEntity() instanceof LivingEntity living) {
            living.damageCooldownTime = 0; // All three independent volley hits must register.
            if (living.hurtOrSimulate(damageSources().indirectMagic(this, getOwner()), 1f)) {
                MobEffectInstance old = living.getEffect(RevampStatusEffects.FROSTED);
                int level = old == null ? 0 : Math.min(5, old.getAmplifier() + 1);
                living.addEffect(new MobEffectInstance(RevampStatusEffects.FROSTED, 80, level));
            }
        }
        burst(); discard();
    }
    @Override protected void onHitBlock(BlockHitResult hit) { super.onHitBlock(hit); if (!level().isClientSide()) { burst(); discard(); } }
    private void burst() {
        if (level() instanceof ServerLevel w) {
            w.sendParticles(ModParticles.FROST, getX(), getY(), getZ(), 14, .18, .18, .18, .06);
            w.playSound(null, getX(), getY(), getZ(), SoundEvents.GLASS_BREAK, SoundSource.PLAYERS, .35f, 1.8f);
        }
    }
    @Override protected boolean canHitEntity(Entity e) { return super.canHitEntity(e) && e != getOwner(); }
    public void addAdditionalSaveDataLegacy(CompoundTag nbt) {  }
    public void readAdditionalSaveDataLegacy(CompoundTag nbt) {  }

    @Override protected void addAdditionalSaveData(net.minecraft.world.level.storage.ValueOutput value) { super.addAdditionalSaveData(value); CompoundTag tag = new CompoundTag(); addAdditionalSaveDataLegacy(tag); com.example.circlemage.util.NbtBridge.write(value, tag); }

    @Override protected void readAdditionalSaveData(net.minecraft.world.level.storage.ValueInput value) { super.readAdditionalSaveData(value); readAdditionalSaveDataLegacy(com.example.circlemage.util.NbtBridge.read(value)); }
}
