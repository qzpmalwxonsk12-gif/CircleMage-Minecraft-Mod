package com.example.circlemage.mixin;
import com.example.circlemage.entity.ArcaneEffectEntity;
import com.example.circlemage.spell.RevampStatusEffects;
import net.minecraft.client.*;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.fog.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Mixin(FogRenderer.class)
public abstract class SteamFogMixin {
    @Inject(method="setupFog",at=@At("RETURN"))
    private void circlemage$steamFog(Camera camera,int distance,DeltaTracker delta,float brightness,ClientLevel level,CallbackInfoReturnable<FogData> ci){
        Minecraft c=Minecraft.getInstance();
        if(c.player==null||c.player.isSpectator()||c.player.hasEffect(RevampStatusEffects.SOOTHING)) return;
        if(ArcaneEffectEntity.steamAt(level,camera.position())) {
            FogData fog=ci.getReturnValue(); fog.environmentalStart=.25f; fog.environmentalEnd=3.5f;
            fog.renderDistanceStart=.25f; fog.renderDistanceEnd=3.5f; fog.skyEnd=3.5f; fog.cloudEnd=3.5f;
            fog.color=new org.joml.Vector4f(.80f,.87f,.91f,1);
        }
    }
}
