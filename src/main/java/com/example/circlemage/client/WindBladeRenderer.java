package com.example.circlemage.client;

import com.example.circlemage.entity.WindBladeEntity;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.util.Identifier;

/**
 * The Wind Blade is visualised entirely by its server-spawned gale particle streak, so the renderer
 * draws no geometry of its own.
 */
public class WindBladeRenderer extends EntityRenderer<WindBladeEntity> {

    private static final Identifier TEXTURE = new Identifier("circlemage", "textures/particle/gale.png");

    public WindBladeRenderer(EntityRendererFactory.Context context) {
        super(context);
    }

    @Override
    public Identifier getTexture(WindBladeEntity entity) {
        return TEXTURE;
    }
}
