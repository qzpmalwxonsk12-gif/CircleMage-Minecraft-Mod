package com.example.circlemage.mixin;
import com.example.circlemage.spell.RevampStatusEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(Entity.class)
public abstract class SoothingFireMixin {
    @Inject(method="setRemainingFireTicks",at=@At("HEAD"),cancellable=true)
    private void circlemage$noIgnition(int ticks,CallbackInfo ci){
        Entity self=(Entity)(Object)this;if(ticks>0&&self instanceof LivingEntity e&&e.hasEffect(RevampStatusEffects.SOOTHING))ci.cancel();
    }
}
