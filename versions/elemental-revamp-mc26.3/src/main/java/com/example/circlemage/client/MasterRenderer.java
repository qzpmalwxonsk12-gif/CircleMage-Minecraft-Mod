package com.example.circlemage.client;

import com.example.circlemage.CircleMageMod;
import com.example.circlemage.entity.MasterEntity;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.resources.Identifier;

/**
 * Renders The Master as a tall robed archmage (a biped with a custom 64x64 skin). Slightly larger than
 * a Wandering Mage to read as a boss. The floating name and the boss bar are handled elsewhere.
 */
public class MasterRenderer extends HumanoidMobRenderer<MasterEntity, HumanoidRenderState, HumanoidModel<HumanoidRenderState>> {

    public static final ModelLayerLocation MASTER_LAYER =
            new ModelLayerLocation(Identifier.fromNamespaceAndPath(CircleMageMod.MOD_ID, "master"), "main");

    private static final Identifier TEXTURE = Identifier.fromNamespaceAndPath(CircleMageMod.MOD_ID, "textures/entity/the_master.png");

    public MasterRenderer(EntityRendererProvider.Context context) {
        super(context, new HumanoidModel<>(context.bakeLayer(MASTER_LAYER)), 0.6f);
        this.shadowRadius = 0.6f;
    }

    public static LayerDefinition getTexturedModelData() {
        return LayerDefinition.create(HumanoidModel.createMesh(new CubeDeformation(0.6f), 0.0f), 64, 64);
    }

    @Override
    public Identifier getTextureLocation(HumanoidRenderState entity) {
        return TEXTURE;
    }
    @Override public HumanoidRenderState createRenderState() { return new HumanoidRenderState(); }
}
