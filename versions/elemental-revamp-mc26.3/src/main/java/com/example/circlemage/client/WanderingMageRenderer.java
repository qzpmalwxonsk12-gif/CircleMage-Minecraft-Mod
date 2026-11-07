package com.example.circlemage.client;

import com.example.circlemage.CircleMageMod;
import com.example.circlemage.entity.WanderingMageEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.example.circlemage.client.MeshBuffers.Vertex;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import com.example.circlemage.client.MeshBuffers;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

/**
 * Renders the Wandering Mage as a robed humanoid (a standard biped with a custom 64x64 mage skin).
 * The skin is chosen by the mage's circle, with a distinct black-and-gold robe for The Prodigy. The
 * floating rank/name above the head is vanilla custom-name rendering (set on the entity at spawn).
 */
public class WanderingMageRenderer extends HumanoidMobRenderer<WanderingMageEntity, WanderingMageRenderer.State, HumanoidModel<WanderingMageRenderer.State>> {

    public static final ModelLayerLocation MAGE_LAYER =
            new ModelLayerLocation(Identifier.fromNamespaceAndPath(CircleMageMod.MOD_ID, "wandering_mage"), "main");

    private static final Identifier[] CIRCLE_TEXTURES = {
            Identifier.fromNamespaceAndPath(CircleMageMod.MOD_ID, "textures/entity/wandering_mage_c1.png"),
            Identifier.fromNamespaceAndPath(CircleMageMod.MOD_ID, "textures/entity/wandering_mage_c2.png"),
            Identifier.fromNamespaceAndPath(CircleMageMod.MOD_ID, "textures/entity/wandering_mage_c3.png"),
    };
    private static final Identifier PRODIGY_TEXTURE =
            Identifier.fromNamespaceAndPath(CircleMageMod.MOD_ID, "textures/entity/wandering_mage_prodigy.png");

    public WanderingMageRenderer(EntityRendererProvider.Context context) {
        super(context, new HumanoidModel<>(context.bakeLayer(MAGE_LAYER)), 0.5f);
    }

    /** The biped model data, baked onto the standard 64x64 player-skin layout. */
    public static LayerDefinition getTexturedModelData() {
        return LayerDefinition.create(HumanoidModel.createMesh(new CubeDeformation(0.0f), 0.0f), 64, 64);
    }

    private Identifier textureFor(WanderingMageEntity entity) {
        if (entity.isProdigy()) {
            return PRODIGY_TEXTURE;
        }
        int index = Math.max(1, Math.min(3, entity.getCircle())) - 1;
        return CIRCLE_TEXTURES[index];
    }

    public static class State extends HumanoidRenderState {
        Identifier texture;
        java.util.List<MeshBuffers.Batch> telegraph=java.util.List.of();
    }
    @Override public State createRenderState() { return new State(); }
    @Override public Identifier getTextureLocation(State state) { return state.texture; }
    @Override public void extractRenderState(WanderingMageEntity entity, State state, float delta) {
        super.extractRenderState(entity,state,delta); state.texture=textureFor(entity);
        MeshBuffers buffers=new MeshBuffers();
        if(entity.isCasting())renderTelegraph(entity,new PoseStack(),buffers);
        state.telegraph=buffers.snapshot();
    }
    @Override public void submit(State state,PoseStack pose,net.minecraft.client.renderer.SubmitNodeCollector collector,net.minecraft.client.renderer.state.level.CameraRenderState camera) {
        super.submit(state,pose,collector,camera); MeshBuffers.submit(state.telegraph,pose,collector);
    }

    /**
     * Draws the locked-trajectory telegraph: a translucent red beam from the mage's head along the
     * exact direction the Magic Missile will fly, stopping at the first block it would hit. The
     * direction is the entity's synced, locked aim — it does not change during the cast.
     */
    private void renderTelegraph(WanderingMageEntity entity, PoseStack matrices, MeshBuffers vertexConsumers) {
        Vec3 dir = entity.getAimDirection();
        if (dir.lengthSqr() < 1.0e-6) {
            return;
        }
        dir = dir.normalize();

        double eyeHeight = entity.getEyeHeight(entity.getPose());
        // Find how far the shot travels before a block stops it (max range = the missile's 50 blocks).
        Vec3 eye = entity.getEyePosition();
        double length = 50.0;
        HitResult hit = entity.level().clip(new ClipContext(eye, eye.add(dir.scale(50.0)),
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, entity));
        if (hit.getType() == HitResult.Type.BLOCK) {
            length = eye.distanceTo(hit.getLocation());
        }

        // Build two axes perpendicular to the beam so we can draw a few parallel lines for thickness.
        Vec3 ref = Math.abs(dir.y) < 0.99 ? new Vec3(0, 1, 0) : new Vec3(1, 0, 0);
        Vec3 perp1 = dir.cross(ref).normalize();
        Vec3 perp2 = dir.cross(perp1).normalize();

        Matrix4f pose = matrices.last().pose();
        Matrix3f normal = matrices.last().normal();
        Vertex vc = vertexConsumers.getBuffer(RenderTypes.lines());

        double t = 0.07; // beam half-thickness
        Vec3[] offsets = {
                Vec3.ZERO,
                perp1.scale(t), perp1.scale(-t),
                perp2.scale(t), perp2.scale(-t),
        };
        for (Vec3 o : offsets) {
            double sx = o.x;
            double sy = eyeHeight + o.y;
            double sz = o.z;
            double ex = dir.x * length + o.x;
            double ey = eyeHeight + dir.y * length + o.y;
            double ez = dir.z * length + o.z;
            vc.vertex(pose, (float) sx, (float) sy, (float) sz).color(255, 45, 45, 150)
                    .normal(normal, (float) dir.x, (float) dir.y, (float) dir.z).endVertex();
            vc.vertex(pose, (float) ex, (float) ey, (float) ez).color(255, 45, 45, 150)
                    .normal(normal, (float) dir.x, (float) dir.y, (float) dir.z).endVertex();
        }
    }
}
