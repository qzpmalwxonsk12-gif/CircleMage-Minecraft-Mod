package com.example.circlemage.client;

import com.example.circlemage.CircleMageMod;
import com.example.circlemage.entity.MasterEntity;
import net.minecraft.client.model.Dilation;
import net.minecraft.client.model.TexturedModelData;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.MobEntityRenderer;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.client.render.entity.model.EntityModelLayer;
import net.minecraft.util.Identifier;

/**
 * Renders The Master as a tall robed archmage (a biped with a custom 64x64 skin). Slightly larger than
 * a Wandering Mage to read as a boss. The floating name and the boss bar are handled elsewhere.
 */
public class MasterRenderer extends MobEntityRenderer<MasterEntity, BipedEntityModel<MasterEntity>> {

    public static final EntityModelLayer MASTER_LAYER =
            new EntityModelLayer(new Identifier(CircleMageMod.MOD_ID, "master"), "main");

    private static final Identifier TEXTURE = new Identifier(CircleMageMod.MOD_ID, "textures/entity/the_master.png");

    public MasterRenderer(EntityRendererFactory.Context context) {
        super(context, new BipedEntityModel<>(context.getPart(MASTER_LAYER)), 0.6f);
        this.shadowRadius = 0.6f;
    }

    public static TexturedModelData getTexturedModelData() {
        return TexturedModelData.of(BipedEntityModel.getModelData(new Dilation(0.6f), 0.0f), 64, 64);
    }

    @Override
    public Identifier getTexture(MasterEntity entity) {
        return TEXTURE;
    }
}
