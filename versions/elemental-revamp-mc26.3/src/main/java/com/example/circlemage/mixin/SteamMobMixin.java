package com.example.circlemage.mixin;
import com.example.circlemage.entity.ArcaneEffectEntity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(Mob.class)
public abstract class SteamMobMixin {
    @Inject(method="setTarget",at=@At("HEAD"),cancellable=true)
    private void circlemage$preventTarget(LivingEntity target,CallbackInfo ci){
        Mob mob=(Mob)(Object)this;
        if(target!=null&&ArcaneEffectEntity.obscures(mob.level(),mob.getEyePosition(),target.getEyePosition()))ci.cancel();
    }
    @Inject(method="tick",at=@At("HEAD"))
    private void circlemage$loseTarget(CallbackInfo ci){
        Mob mob=(Mob)(Object)this;LivingEntity target=mob.getTarget();
        if(!mob.level().isClientSide()&&target!=null&&ArcaneEffectEntity.obscures(mob.level(),mob.getEyePosition(),target.getEyePosition())){mob.setTarget(null);mob.getNavigation().stop();}
    }
}
