package com.example.circlemage.client;

import com.example.circlemage.entity.WindBladeEntity;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.Identifier;

/**
 * The Wind Blade is visualised entirely by its server-spawned gale particle streak, so the renderer
 * draws no geometry of its own.
 */
public class WindBladeRenderer extends ProceduralEntityRenderer<WindBladeEntity> {

    private static final Identifier TEXTURE = Identifier.fromNamespaceAndPath("circlemage", "textures/particle/gale.png");

    public WindBladeRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public Identifier getTexture(WindBladeEntity entity) {
        return TEXTURE;
    }
}
