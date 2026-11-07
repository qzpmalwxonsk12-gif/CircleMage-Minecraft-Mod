package com.example.circlemage.mixin;
import com.example.circlemage.client.ClientImpactState;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
/** Add the impact effect during update, before the renderer extracts its requested effect list. */
@Mixin(GameRenderer.class)
public abstract class GameRendererAccessor {
    @Inject(method="update",at=@At("TAIL"))
    private void circlemage$impact(DeltaTracker delta,CallbackInfo ci) {
        if(ClientImpactState.active()) ((GameRenderer)(Object)this).getRequestedPostEffects()
            .add(Identifier.fromNamespaceAndPath("circlemage","desaturate"));
    }
}
