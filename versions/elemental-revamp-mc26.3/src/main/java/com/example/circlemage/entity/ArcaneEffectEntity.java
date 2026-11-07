package com.example.circlemage.entity;

import com.example.circlemage.network.ModNetworking;
import com.example.circlemage.particle.ModParticles;
import com.example.circlemage.spell.RevampStatusEffects;
import com.example.circlemage.spell.SpellGeometry;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import java.util.*;

/** Synced, finite-lived spell geometry. No temporary terrain blocks or static World references. */
public class ArcaneEffectEntity extends Entity {
    public static final int BUBBLE = 0, DOME = 1, SPIKE = 2, FIST = 3, VINES = 4, STEAM = 5;
    private static final EntityDataAccessor<Integer> KIND = SynchedEntityData.defineId(ArcaneEffectEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> ELAPSED = SynchedEntityData.defineId(ArcaneEffectEntity.class, EntityDataSerializers.INT);
    private UUID targetUuid, ownerUuid;
    private int elapsed, lifetime = 80;
    private final Set<UUID> insiders = new HashSet<>();
    public ArcaneEffectEntity(EntityType<? extends ArcaneEffectEntity> type, Level world) { super(type, world); setNoGravity(true); noPhysics = true; }
    public ArcaneEffectEntity(ServerLevel world, LivingEntity owner, LivingEntity target, Vec3 pos, int kind, int ticks) {
        this(ModEntities.ARCANE_EFFECT, world); ownerUuid = owner.getUUID(); targetUuid = target == null ? null : target.getUUID();
        entityData.set(KIND, kind); lifetime = ticks; setPos(pos);
        if (kind == DOME) for (LivingEntity e : world.getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(3), x -> x.isAlive()))
            if (inDome(e.position())) insiders.add(e.getUUID());
    }
    @Override protected void defineSynchedData(net.minecraft.network.syncher.SynchedEntityData.Builder builder) { builder.define(KIND, BUBBLE); builder.define(ELAPSED, 0); }
    public int kind() { return entityData.get(KIND); }
    public int elapsed() { return entityData.get(ELAPSED); }
    public boolean activeSteam() { return kind() == STEAM && elapsed() >= 20 && !isRemoved(); }
    public boolean inSteam(Vec3 p) { return activeSteam() && p.distanceToSqr(position()) <= 36; }
    private boolean inDome(Vec3 p) { return p.y >= getY()-.2 && p.y <= getY()+3 && p.distanceToSqr(position()) <= 9; }
    @Override public void tick() {
        super.tick();
        if (!(level() instanceof ServerLevel world)) {
            if(kind()==STEAM && elapsed()<20 && tickCount%2==0)for(int i=0;i<8;i++){
                double a=i*Math.PI/4+tickCount*.12;level().addParticle(net.minecraft.core.particles.ParticleTypes.FLAME,getX()+Math.cos(a)*2,getY()+Math.sin(tickCount*.2+i)*.6,getZ()+Math.sin(a)*2,0,.01,0);
            }
            if(activeSteam() && tickCount%2==0) for(int i=0;i<4;i++) {
                double dx=random.nextGaussian()*2.1,dy=random.nextGaussian()*1.8,dz=random.nextGaussian()*2.1;
                if(dx*dx+dy*dy+dz*dz<36)level().addParticle(net.minecraft.core.particles.ParticleTypes.CLOUD,getX()+dx,getY()+dy,getZ()+dz,0,.008,0);
            }
            return;
        }
        elapsed++; entityData.set(ELAPSED, elapsed);
        Entity raw = targetUuid == null ? null : world.getEntity(targetUuid);
        LivingEntity target = raw instanceof LivingEntity l && l.isAlive() ? l : null;
        Entity owner = ownerUuid == null ? null : world.getEntity(ownerUuid);
        if ((kind() == BUBBLE || kind() == VINES) && target == null) { discard(); return; }
        switch (kind()) {
            case BUBBLE -> {
                setPos(target.getEyePosition());
                target.setAirSupply(0);
                if (elapsed % 20 == 0) target.hurt(world.damageSources().drown(), 1.5f);
                if (elapsed % 4 == 0) sphereParticles(world, net.minecraft.core.particles.ParticleTypes.BUBBLE, .5, 12);
            }
            case VINES -> { setPos(target.position()); if (!target.hasEffect(RevampStatusEffects.ROOTED)) { discard(); return; } }
            case SPIKE, FIST -> {
                if (elapsed == 8) {
                    for (LivingEntity e : world.getEntitiesOfClass(LivingEntity.class, new AABB(getX()-1.1,getY(),getZ()-1.1,getX()+1.1,getY()+3,getZ()+1.1), x -> x.isAlive() && x != owner)) {
                        e.hurt(world.damageSources().indirectMagic(this, owner), kind() == SPIKE ? 3f : 5f);
                        if (kind() == FIST) { Vec3 d = e.position().subtract(position()).add(0,.1,0).normalize(); e.push(d.x*.45,.25,d.z*.45); e.syncVelocity = true; }
                    }
                    world.playSound(null,getX(),getY(),getZ(),SoundEvents.STONE_BREAK,SoundSource.PLAYERS,.8f,.6f);
                    ModNetworking.sendShakeFalloff(world,getX(),getY(),getZ(),16,.4f,5);
                }
            }
            case DOME -> containDome(world);
            case STEAM -> {
                if (elapsed == 20) {
                    world.playSound(null,getX(),getY(),getZ(),SoundEvents.GENERIC_EXPLODE,SoundSource.PLAYERS,.8f,1.6f);
                    ModNetworking.sendShakeFalloff(world,getX(),getY(),getZ(),20,.5f,8);
                    sphereParticles(world, ModParticles.FROST, 2, 36);
                }
                if (elapsed > 20 && (elapsed-20)%40 == 0) {
                    for (LivingEntity e : world.getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(6), x -> x.isAlive() && !x.hasEffect(RevampStatusEffects.SOOTHING)))
                        if (e.position().distanceToSqr(position()) <= 36) e.hurt(world.damageSources().indirectMagic(this, owner),1f);
                }
            }
        }
        if (elapsed >= lifetime) { if (kind() == BUBBLE && target != null) target.setAirSupply(target.getMaxAirSupply()); discard(); }
    }
    private void containDome(ServerLevel world) {
        for (Entity e : world.getEntities(this,getBoundingBox().inflate(4),x -> !x.isSpectator() && !(x instanceof ArcaneEffectEntity))) {
            Vec3 relative = e.position().subtract(position()); double d = Math.sqrt(relative.x*relative.x+relative.z*relative.z);
            boolean inside = insiders.contains(e.getUUID());
            Vec3 previous=e.position().subtract(e.getDeltaMovement());
            if (e instanceof Projectile && (previous.distanceToSqr(position()) >= 9 || e.position().distanceToSqr(position()) >= 9)
                    && SpellGeometry.segmentHitsSphere(previous,e.position(),position(),3) && Math.max(previous.y,e.getY())>=getY()) { e.discard(); continue; }
            if (!(e instanceof LivingEntity)) continue;
            double height=Math.max(0,relative.y)+e.getBbHeight();
            double allowed=Math.sqrt(Math.max(.25,9-Math.min(2.8,height)*Math.min(2.8,height)))-.15;
            if (inside && (d > allowed || height > 2.8 || relative.y < -.2)) {
                double scale = d<.01?1:Math.min(1,allowed/d); e.teleportTo(getX()+relative.x*scale,getY()+Math.max(0,Math.min(relative.y,Math.max(0,2.8-e.getBbHeight()))),getZ()+relative.z*scale);
                e.setDeltaMovement(Vec3.ZERO); e.syncVelocity=true;
                if (e instanceof Mob mob) mob.getNavigation().stop();
            } else if (!inside && inDome(e.position())) {
                e.teleportTo(getX()+(d<.01?3.2:relative.x/d*3.2),e.getY(),getZ()+(d<.01?0:relative.z/d*3.2)); e.setDeltaMovement(Vec3.ZERO); e.syncVelocity=true;
            }
        }
    }
    private void sphereParticles(ServerLevel w, net.minecraft.core.particles.ParticleOptions particle, double radius, int count) {
        for (int i=0;i<count;i++) { double a=i*2.399963, y=1-2*(i+.5)/count, r=Math.sqrt(1-y*y);
            w.sendParticles(particle,getX()+Math.cos(a)*r*radius,getY()+y*radius,getZ()+Math.sin(a)*r*radius,1,0,0,0,0); }
    }
    public static boolean steamAt(Level world, Vec3 pos) {
        return world.getEntitiesOfClass(ArcaneEffectEntity.class,new AABB(pos,pos).inflate(7),e -> e.inSteam(pos)).size() > 0;
    }
    public static boolean obscures(Level world, Vec3 from, Vec3 to) {
        for (ArcaneEffectEntity e : world.getEntitiesOfClass(ArcaneEffectEntity.class,new AABB(from,to).inflate(7),x -> x.activeSteam() || x.kind()==DOME))
            if (SpellGeometry.segmentHitsSphere(from,to,e.position(),e.kind()==DOME?3:6) && (e.kind()!=DOME || Math.max(from.y,to.y)>=e.getY())) return true;
        return false;
    }
    public static void dispel(Level world, LivingEntity target) {
        for (ArcaneEffectEntity e : world.getEntitiesOfClass(ArcaneEffectEntity.class,target.getBoundingBox().inflate(8),x -> true))
            if ((e.kind()==BUBBLE || e.kind()==VINES) && target.getUUID().equals(e.targetUuid)) e.discard();
            else if (e.inSteam(target.getEyePosition()) || (e.kind()==DOME && e.inDome(target.position()))) e.discard();
        target.setAirSupply(target.getMaxAirSupply());
    }
    public static void removeBubble(Level world, LivingEntity target) {
        for (ArcaneEffectEntity e : world.getEntitiesOfClass(ArcaneEffectEntity.class,target.getBoundingBox().inflate(4),x -> x.kind()==BUBBLE && target.getUUID().equals(x.targetUuid))) e.discard();
        target.setAirSupply(target.getMaxAirSupply());
    }
    protected void addAdditionalSaveDataLegacy(CompoundTag nbt) {
        nbt.putInt("kind",kind());nbt.putInt("elapsed",elapsed);nbt.putInt("lifetime",lifetime);
        if(targetUuid!=null)nbt.store("target", net.minecraft.core.UUIDUtil.CODEC, targetUuid);if(ownerUuid!=null)nbt.store("owner", net.minecraft.core.UUIDUtil.CODEC, ownerUuid);
        net.minecraft.nbt.ListTag list=new net.minecraft.nbt.ListTag();for(UUID id:insiders)list.add(net.minecraft.nbt.StringTag.valueOf(id.toString()));nbt.put("insiders",list);
    }
    protected void readAdditionalSaveDataLegacy(CompoundTag nbt) {
        entityData.set(KIND,nbt.getIntOr("kind", 0));elapsed=nbt.getIntOr("elapsed", 0);entityData.set(ELAPSED,elapsed);lifetime=nbt.getIntOr("lifetime", 0);
        targetUuid=nbt.read("target", net.minecraft.core.UUIDUtil.CODEC).isPresent()?nbt.read("target", net.minecraft.core.UUIDUtil.CODEC).orElse(null):null;ownerUuid=nbt.read("owner", net.minecraft.core.UUIDUtil.CODEC).isPresent()?nbt.read("owner", net.minecraft.core.UUIDUtil.CODEC).orElse(null):null;
        var list=nbt.getListOrEmpty("insiders");for(int i=0;i<list.size();i++)try{insiders.add(UUID.fromString(list.getString(i).orElse("")));}catch(IllegalArgumentException ignored){}
    }
    @Override public boolean shouldRenderAtSqrDistance(double distance){return distance<128*128;}

    @Override protected void addAdditionalSaveData(net.minecraft.world.level.storage.ValueOutput value) {  CompoundTag tag = new CompoundTag(); addAdditionalSaveDataLegacy(tag); com.example.circlemage.util.NbtBridge.write(value, tag); }

    @Override protected void readAdditionalSaveData(net.minecraft.world.level.storage.ValueInput value) {  readAdditionalSaveDataLegacy(com.example.circlemage.util.NbtBridge.read(value)); }

    @Override public boolean hurtServer(net.minecraft.server.level.ServerLevel world, net.minecraft.world.damagesource.DamageSource source, float amount) { return false; }
}
