package com.example.circlemage.mixin;

import com.example.circlemage.entity.MagicBarrierEntity;
import com.example.circlemage.mana.Circles;
import com.example.circlemage.mana.ManaData;
import com.example.circlemage.mana.ManaPlayer;
import com.example.circlemage.network.ModNetworking;
import com.example.circlemage.spell.Law;
import com.example.circlemage.spell.LawUnlocks;
import com.example.circlemage.spell.MagicResistance;
import com.example.circlemage.spell.PassiveChant;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Player combat tweaks: block harmful effects during anti-magic immunity, and apply the Reinforce
 * passive chant's 30% damage reduction.
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin {

    @Inject(method="hasLineOfSight(Lnet/minecraft/world/entity/Entity;)Z",at=@At("HEAD"),cancellable=true)
    private void circlemage$steamBlocksSight(Entity target,CallbackInfoReturnable<Boolean> cir) {
        LivingEntity self=(LivingEntity)(Object)this;
        if(self instanceof net.minecraft.world.entity.Mob && com.example.circlemage.entity.ArcaneEffectEntity.obscures(self.level(),self.getEyePosition(),target.getEyePosition()))cir.setReturnValue(false);
    }
    @Inject(method="aiStep",at=@At("TAIL"))
    private void circlemage$roots(org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci) {
        LivingEntity self=(LivingEntity)(Object)this;
        if(self.hasEffect(com.example.circlemage.spell.RevampStatusEffects.ROOTED)) {
            self.setDeltaMovement(0,Math.min(0,self.getDeltaMovement().y),0);
            if(self instanceof net.minecraft.world.entity.Mob mob)mob.getNavigation().stop();
        }
        if(self.hasEffect(com.example.circlemage.spell.RevampStatusEffects.SOOTHING))self.clearFire();
    }
    @Inject(method="hurtServer",at=@At("HEAD"),cancellable=true)
    private void circlemage$soothingFire(net.minecraft.server.level.ServerLevel level, DamageSource source,float amount,CallbackInfoReturnable<Boolean> cir) {
        LivingEntity self=(LivingEntity)(Object)this;
        if(self.hasEffect(com.example.circlemage.spell.RevampStatusEffects.SOOTHING)&&source.is(DamageTypeTags.IS_FIRE))cir.setReturnValue(false);
    }

    @Inject(method = "canBeAffected", at = @At("HEAD"), cancellable = true)
    private void circlemage$blockHarmfulWhileImmune(MobEffectInstance effect, CallbackInfoReturnable<Boolean> cir) {
        LivingEntity self = (LivingEntity) (Object) this;
        if (self instanceof Player player
                && ManaPlayer.get(player).isImmune(self.level().getGameTime())
                && effect.getEffect().value().getCategory() == MobEffectCategory.HARMFUL) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "hurtServer", at = @At("HEAD"), cancellable = true)
    private void circlemage$domeAbsorb(net.minecraft.server.level.ServerLevel level, DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
        LivingEntity self = (LivingEntity) (Object) this;
        if (self.level().isClientSide() || !(self instanceof Player player)) {
            return;
        }
        // Don't trap the player on unavoidable damage (void, /kill, etc.).
        if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            return;
        }
        ManaData data = ManaPlayer.get(player);
        int domeId = data.getActiveDomeId();
        if (domeId < 0) {
            return;
        }
        Entity entity = self.level().getEntity(domeId);
        if (entity instanceof MagicBarrierEntity dome && dome.isActiveDome()) {
            dome.absorbHit();
            cir.setReturnValue(false); // the dome took the hit, not the player
        }
    }

    @Inject(method = "hurtServer", at = @At("HEAD"))
    private void circlemage$lawUnlockTracking(net.minecraft.server.level.ServerLevel level, DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
        LivingEntity self = (LivingEntity) (Object) this;
        if (self.level().isClientSide() || !(self instanceof ServerPlayer player)) {
            return;
        }
        ManaData data = ManaPlayer.get(player);
        // Comprehending the void unlocks the Space laws.
        if (source.is(DamageTypes.FELL_OUT_OF_WORLD)) {
            LawUnlocks.recordVoidComprehension(player, data);
        }
        // Being struck by each hostile mob unlocks the Law of Causality.
        if (source.getEntity() instanceof Enemy) {
            LawUnlocks.recordHostileHit(player, data, source.getEntity());
        }
    }

    @Inject(method = "hurtServer", at = @At("HEAD"), cancellable = true)
    private void circlemage$lawDamage(net.minecraft.server.level.ServerLevel level, DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
        LivingEntity self = (LivingEntity) (Object) this;
        if (self.level().isClientSide() || !(self instanceof Player player)) {
            return;
        }
        ManaData data = ManaPlayer.get(player);
        if (!data.isLawsUnlocked() || data.getCircle() < Circles.MAX_CIRCLE) {
            return;
        }
        Law law = Law.byId(data.getSelectedLaw());

        // Love of the World grants "feather falling": fall damage is negated.
        if (law == Law.LOVE_OF_WORLD && source.is(DamageTypeTags.IS_FALL)) {
            cir.setReturnValue(false);
            return;
        }

        // Law of Causality: reflect/negate incoming damage by spending mana.
        if (law == Law.CAUSALITY && !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            float hearts = amount / 2.0f;
            Entity attacker = source.getEntity();
            boolean living = attacker instanceof LivingEntity;
            double cost = (living ? 1000.0 : 3000.0) * hearts; // non-living sources cost 3x to negate
            if (data.trySpendMana(cost)) {
                if (living) {
                    // Reflect with a plain magic source so it can't bounce back forever.
                    attacker.hurt(self.level().damageSources().magic(), amount);
                }
                if (player instanceof ServerPlayer sp) {
                    ModNetworking.syncToClient(sp);
                }
                cir.setReturnValue(false);
            }
        }
    }

    @Inject(method = "getDamageAfterMagicAbsorb", at = @At("RETURN"), cancellable = true)
    private void circlemage$reinforceReduction(DamageSource source, float amount, CallbackInfoReturnable<Float> cir) {
        LivingEntity self = (LivingEntity) (Object) this;
        if (self instanceof Player player) {
            var data = ManaPlayer.get(player);
            float reduced = cir.getReturnValue();
            // Innate magic resistance: 5% per circle, applied to fire / raw magic harm.
            if (MagicResistance.isResistible(source)) {
                reduced *= (1.0f - MagicResistance.forCircle(data.getCircle()));
            }
            // Reinforce passive chant: a further 30% reduction to all damage.
            if (data.isPassiveChantUnlocked() && PassiveChant.byId(data.getSelectedChant()) == PassiveChant.REINFORCE) {
                reduced *= 0.70f;
            }
            cir.setReturnValue(reduced);
        }
    }
}
