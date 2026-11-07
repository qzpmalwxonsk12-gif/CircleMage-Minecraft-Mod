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
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.damage.DamageTypes;
import net.minecraft.entity.mob.Monster;
import net.minecraft.entity.effect.StatusEffectCategory;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.registry.tag.DamageTypeTags;
import net.minecraft.server.network.ServerPlayerEntity;
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

    @Inject(method = "canHaveStatusEffect", at = @At("HEAD"), cancellable = true)
    private void circlemage$blockHarmfulWhileImmune(StatusEffectInstance effect, CallbackInfoReturnable<Boolean> cir) {
        LivingEntity self = (LivingEntity) (Object) this;
        if (self instanceof PlayerEntity player
                && ManaPlayer.get(player).isImmune(self.getWorld().getTime())
                && effect.getEffectType().getCategory() == StatusEffectCategory.HARMFUL) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "damage", at = @At("HEAD"), cancellable = true)
    private void circlemage$domeAbsorb(DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
        LivingEntity self = (LivingEntity) (Object) this;
        if (self.getWorld().isClient || !(self instanceof PlayerEntity player)) {
            return;
        }
        // Don't trap the player on unavoidable damage (void, /kill, etc.).
        if (source.isIn(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            return;
        }
        ManaData data = ManaPlayer.get(player);
        int domeId = data.getActiveDomeId();
        if (domeId < 0) {
            return;
        }
        Entity entity = self.getWorld().getEntityById(domeId);
        if (entity instanceof MagicBarrierEntity dome && dome.isActiveDome()) {
            dome.absorbHit();
            cir.setReturnValue(false); // the dome took the hit, not the player
        }
    }

    @Inject(method = "damage", at = @At("HEAD"))
    private void circlemage$lawUnlockTracking(DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
        LivingEntity self = (LivingEntity) (Object) this;
        if (self.getWorld().isClient || !(self instanceof ServerPlayerEntity player)) {
            return;
        }
        ManaData data = ManaPlayer.get(player);
        // Comprehending the void unlocks the Space laws.
        if (source.isOf(DamageTypes.OUT_OF_WORLD)) {
            LawUnlocks.recordVoidComprehension(player, data);
        }
        // Being struck by each hostile mob unlocks the Law of Causality.
        if (source.getAttacker() instanceof Monster) {
            LawUnlocks.recordHostileHit(player, data, source.getAttacker());
        }
    }

    @Inject(method = "damage", at = @At("HEAD"), cancellable = true)
    private void circlemage$lawDamage(DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
        LivingEntity self = (LivingEntity) (Object) this;
        if (self.getWorld().isClient || !(self instanceof PlayerEntity player)) {
            return;
        }
        ManaData data = ManaPlayer.get(player);
        if (!data.isLawsUnlocked() || data.getCircle() < Circles.MAX_CIRCLE) {
            return;
        }
        Law law = Law.byId(data.getSelectedLaw());

        // Love of the World grants "feather falling": fall damage is negated.
        if (law == Law.LOVE_OF_WORLD && source.isIn(DamageTypeTags.IS_FALL)) {
            cir.setReturnValue(false);
            return;
        }

        // Law of Causality: reflect/negate incoming damage by spending mana.
        if (law == Law.CAUSALITY && !source.isIn(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            float hearts = amount / 2.0f;
            Entity attacker = source.getAttacker();
            boolean living = attacker instanceof LivingEntity;
            double cost = (living ? 1000.0 : 3000.0) * hearts; // non-living sources cost 3x to negate
            if (data.trySpendMana(cost)) {
                if (living) {
                    // Reflect with a plain magic source so it can't bounce back forever.
                    attacker.damage(self.getWorld().getDamageSources().magic(), amount);
                }
                if (player instanceof ServerPlayerEntity sp) {
                    ModNetworking.syncToClient(sp);
                }
                cir.setReturnValue(false);
            }
        }
    }

    @Inject(method = "modifyAppliedDamage", at = @At("RETURN"), cancellable = true)
    private void circlemage$reinforceReduction(DamageSource source, float amount, CallbackInfoReturnable<Float> cir) {
        LivingEntity self = (LivingEntity) (Object) this;
        if (self instanceof PlayerEntity player) {
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
