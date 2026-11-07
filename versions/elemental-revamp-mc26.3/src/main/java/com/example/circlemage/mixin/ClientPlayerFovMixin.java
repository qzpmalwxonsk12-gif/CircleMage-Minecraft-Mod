package com.example.circlemage.mixin;

import com.example.circlemage.client.ClientManaState;
import net.minecraft.client.player.AbstractClientPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * While channelling, the -100% movement-speed lock drives the FOV multiplier down to ~0.5, producing
 * a strong zoom-in. This halves that effect — pulling the multiplier halfway back toward 1.0 — so the
 * channelling zoom is about half as intense.
 */
@Mixin(AbstractClientPlayer.class)
public class ClientPlayerFovMixin {

    @Inject(method = "getFieldOfViewModifier", at = @At("RETURN"), cancellable = true)
    private void circlemage$dampenChannelZoom(CallbackInfoReturnable<Float> cir) {
        if (ClientManaState.channeling) {
            float f = cir.getReturnValueF();
            cir.setReturnValue(1.0f - (1.0f - f) * 0.5f);
        }
    }
}
