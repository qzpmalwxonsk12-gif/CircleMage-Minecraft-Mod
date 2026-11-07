package com.example.circlemage.mixin;

import net.minecraft.client.render.GameRenderer;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Exposes {@link GameRenderer}'s (private) post-processor controls for the impact-frame desaturation. */
@Mixin(GameRenderer.class)
public interface GameRendererAccessor {

    @Invoker("loadPostProcessor")
    void circlemage$loadPostProcessor(Identifier id);

    @Invoker("disablePostProcessor")
    void circlemage$disablePostProcessor();
}
