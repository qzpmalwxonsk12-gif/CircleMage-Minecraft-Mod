package com.example.circlemage.spell;

import com.example.circlemage.CircleMageMod;
import com.example.circlemage.entity.*;
import com.example.circlemage.mana.*;
import com.example.circlemage.network.ModNetworking;
import com.example.circlemage.particle.ModParticles;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import java.util.ArrayList;
import java.util.Optional;

public final class RevampMagic {
    private RevampMagic() {}
    public static LivingEntity target(ServerPlayer player) {
        Vec3 start=player.getEyePosition(), end=player.pick(32,1,false).getLocation();
        LivingEntity best=null;double distance=Double.MAX_VALUE;
        for(LivingEntity e:player.level().getEntitiesOfClass(LivingEntity.class,new AABB(start,end).inflate(2),x -> x!=player && x.isAlive() && !x.isSpectator())) {
            var hit=e.getBoundingBox().inflate(2).clip(start,end);
            if(hit.isPresent() && start.distanceToSqr(hit.get())<distance){best=e;distance=start.distanceToSqr(hit.get());}
        }
        return best;
    }
    public static Vec3 point(ServerPlayer player) { LivingEntity e=target(player); return e==null?player.pick(32,1,false).getLocation():e.position(); }
    public static Vec3 ground(ServerLevel w,Vec3 point) {
        BlockPos start=BlockPos.containing(point);
        for(int i=0;i<7;i++){BlockPos p=start.below(i);if(w.getBlockState(p).isRedstoneConductor(w,p))return new Vec3(point.x,p.getY()+1,point.z);}
        return null;
    }
    public static String failure(ServerPlayer p,Spell s) {
        if((s==Spell.WATER_BUBBLE||s==Spell.VINE_GRAB||s==Spell.TRACKING_MARK)&&target(p)==null)return "Aim at a living target within 32 blocks (2-block cursor tolerance).";
        if((s==Spell.STONE_SPIKE||s==Spell.STONE_FIST||s==Spell.STONE_DOME)&&ground(p.level(),s==Spell.STONE_DOME&&p.getXRot()>65?p.position():point(p))==null)return "Earth magic needs solid ground within 6 blocks below the target.";
        if(s==Spell.SOOTHING_WATER&&ArcaneEffectEntity.steamAt(p.level(),p.getEyePosition()))return "Soothing Water must be cast before entering steam.";
        if(s==Spell.FLAME_REBIRTH&&p.getHealth()<=3)return "You need more than 1.5 hearts to survive Flame Rebirth.";
        if(s==Spell.DASH&&p.hasEffect(RevampStatusEffects.ROOTED))return "Vines prevent Wind Escape; cleanse them first.";
        return null;
    }
    public static void cast(ServerPlayer p,Spell s) {
        ServerLevel w=p.level();LivingEntity target=target(p);Vec3 look=p.getViewVector(1);
        switch(s){
            case ICE_VOLLEY -> {
                for(int i=-1;i<=1;i++)w.addFreshEntity(new IceSpikeEntity(w,p,look.yRot(i*.012f)));
                sound(p,SoundEvents.GLASS_PLACE,1.6f);
            }
            case STONE_SPIKE,STONE_FIST,STONE_DOME -> {
                boolean self=s==Spell.STONE_DOME&&p.getXRot()>65;
                Vec3 pos=ground(w,self?p.position():point(p));
                if(pos!=null)w.addFreshEntity(new ArcaneEffectEntity(w,p,self?p:target,pos,s==Spell.STONE_DOME?ArcaneEffectEntity.DOME:s==Spell.STONE_FIST?ArcaneEffectEntity.FIST:ArcaneEffectEntity.SPIKE,s==Spell.STONE_DOME?120:24));
                sound(p,SoundEvents.STONE_PLACE,.6f);
            }
            case WATER_BUBBLE -> { if(target!=null){ArcaneEffectEntity.removeBubble(w,target);w.addFreshEntity(new ArcaneEffectEntity(w,p,target,target.getEyePosition(),ArcaneEffectEntity.BUBBLE,80));}sound(p,SoundEvents.PLAYER_SPLASH,1.4f); }
            case VINE_GRAB -> { if(target!=null){target.addEffect(new MobEffectInstance(RevampStatusEffects.ROOTED,60));w.addFreshEntity(new ArcaneEffectEntity(w,p,target,target.position(),ArcaneEffectEntity.VINES,60));}sound(p,SoundEvents.GRASS_PLACE,.8f); }
            case SOOTHING_WATER -> {p.addEffect(new MobEffectInstance(RevampStatusEffects.SOOTHING,600));p.clearFire();sound(p,SoundEvents.AMETHYST_BLOCK_CHIME,1.2f);}
            case TRACKING_MARK -> {if(target!=null)target.addEffect(new MobEffectInstance(MobEffects.GLOWING,600));sound(p,SoundEvents.AMETHYST_BLOCK_CHIME,1.8f);}
            case FLAME_REBIRTH -> {
                p.setHealth(p.getHealth()-3); // Exact health payment, unaffected by armor/absorption.
                cleanse(p);ArcaneEffectEntity.removeBubble(w,p);
                BlockPos base=p.blockPosition();for(BlockPos b:BlockPos.betweenClosed(base.offset(-1,0,-1),base.offset(1,2,1)))
                    if(w.getBlockState(b).is(Blocks.COBWEB))w.destroyBlock(b,false,p);
                w.sendParticles(ModParticles.EMBER,p.getX(),p.getY()+1,p.getZ(),32,.4,.7,.4,.07);
                sound(p,SoundEvents.FIRECHARGE_USE,1.2f);
            }
            case STEAM_EXPLOSION -> {
                Vec3 center=p.pick(3,1,false).getLocation().subtract(look.scale(.25));
                w.addFreshEntity(new ArcaneEffectEntity(w,p,null,center,ArcaneEffectEntity.STEAM,620));
                sound(p,SoundEvents.PLAYER_SPLASH,.65f);
            }
            default -> throw new IllegalArgumentException("Not a revamp spell: "+s);
        }
    }
    public static void cleanse(LivingEntity e){
        for(var effect:new ArrayList<>(e.getActiveEffects()))if(effect.getEffect().value().getCategory()==MobEffectCategory.HARMFUL)e.removeEffect(effect.getEffect());
        e.clearFire();
    }
    public static void guardianEpiphany(WanderingMageEntity guard,ServerLevel w){
        cleanse(guard);ArcaneEffectEntity.dispel(w,guard);
        w.sendParticles(ModParticles.RADIANCE,guard.getX(),guard.getY()+1,guard.getZ(),30,.5,.8,.5,.1);
        w.playSound(null,guard.blockPosition(),SoundEvents.FIRE_EXTINGUISH,SoundSource.HOSTILE,1,.7f);
        for(ServerPlayer p:w.players())if(p.distanceToSqr(guard)<=32*32&&p.hasLineOfSight(guard)){
            ManaData data=ManaPlayer.get(p);
            if(!data.hasAntiMagicEpiphany()){
                data.setAntiMagicEpiphany(true);
                com.example.circlemage.util.Messages.send(p, Component.literal("Epiphany! You gained insights into anti magic and how to break the 4th circle. Keep performing spells to understand Dispel.").withStyle(ChatFormatting.GOLD,ChatFormatting.BOLD),false);
                ModNetworking.syncToClient(p);
            }
        }
    }
    private static void sound(ServerPlayer p,SoundEvent e,float pitch){p.level().playSound(null,p.blockPosition(),e,SoundSource.PLAYERS,.8f,pitch);}
}
