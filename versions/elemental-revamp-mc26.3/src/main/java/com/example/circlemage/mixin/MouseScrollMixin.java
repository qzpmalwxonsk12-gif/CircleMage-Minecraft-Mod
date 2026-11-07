package com.example.circlemage.mixin;

import com.example.circlemage.client.ModKeybinds;
import com.example.circlemage.client.SpellSelectorState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Spell-wheel scrolling: while the selector modifier (default Left Alt) is held in-game, the mouse
 * wheel cycles the spell selector instead of the hotbar.
 */
@Mixin(MouseHandler.class)
public class MouseScrollMixin {

    @Inject(method = "onScroll", at = @At("HEAD"), cancellable = true)
    private void circlemage$spellWheel(long window, double horizontal, double vertical, CallbackInfo ci) {
        Minecraft client = Minecraft.getInstance();
        if (client.gui.screen() != null || client.player == null || vertical == 0.0) {
            return;
        }
        if (ModKeybinds.selectModifierKey == null || !ModKeybinds.selectModifierKey.isDown()) {
            return;
        }
        SpellSelectorState.cycle(vertical > 0.0 ? -1 : 1);
        ci.cancel();
    }
}
