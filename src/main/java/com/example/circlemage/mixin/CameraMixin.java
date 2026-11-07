package com.example.circlemage.mixin;

import com.example.circlemage.client.ClientShakeState;
import net.minecraft.client.render.Camera;
import net.minecraft.entity.Entity;
import net.minecraft.world.BlockView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Applies Circle Mage's camera shake (e.g. the Hollow Purple detonation) by nudging the render
 * camera's yaw/pitch after it updates. View-only — it doesn't change where the player actually aims.
 */
@Mixin(Camera.class)
public abstract class CameraMixin {

    @Shadow
    protected abstract void setRotation(float yaw, float pitch);

    @Shadow
    public abstract float getYaw();

    @Shadow
    public abstract float getPitch();

    @Inject(method = "update", at = @At("TAIL"))
    private void circlemage$shake(BlockView area, Entity focusedEntity, boolean thirdPerson,
                                 boolean inverseView, float tickDelta, CallbackInfo ci) {
        float s = ClientShakeState.current();
        if (s <= 0.001f) {
            return;
        }
        float dyaw = (float) ((Math.random() * 2.0 - 1.0) * s);
        float dpitch = (float) ((Math.random() * 2.0 - 1.0) * s);
        this.setRotation(this.getYaw() + dyaw, this.getPitch() + dpitch);
    }
}
