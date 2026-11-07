package com.example.circlemage.client;

import com.example.circlemage.CircleMageMod;
import com.example.circlemage.entity.WanderingMageEntity;
import net.minecraft.client.model.Dilation;
import net.minecraft.client.model.TexturedModelData;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.MobEntityRenderer;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.client.render.entity.model.EntityModelLayer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

/**
 * Renders the Wandering Mage as a robed humanoid (a standard biped with a custom 64x64 mage skin).
 * The skin is chosen by the mage's circle, with a distinct black-and-gold robe for The Prodigy. The
 * floating rank/name above the head is vanilla custom-name rendering (set on the entity at spawn).
 */
public class WanderingMageRenderer extends MobEntityRenderer<WanderingMageEntity, BipedEntityModel<WanderingMageEntity>> {

    public static final EntityModelLayer MAGE_LAYER =
            new EntityModelLayer(new Identifier(CircleMageMod.MOD_ID, "wandering_mage"), "main");

    private static final Identifier[] CIRCLE_TEXTURES = {
            new Identifier(CircleMageMod.MOD_ID, "textures/entity/wandering_mage_c1.png"),
            new Identifier(CircleMageMod.MOD_ID, "textures/entity/wandering_mage_c2.png"),
            new Identifier(CircleMageMod.MOD_ID, "textures/entity/wandering_mage_c3.png"),
    };
    private static final Identifier PRODIGY_TEXTURE =
            new Identifier(CircleMageMod.MOD_ID, "textures/entity/wandering_mage_prodigy.png");

    public WanderingMageRenderer(EntityRendererFactory.Context context) {
        super(context, new BipedEntityModel<>(context.getPart(MAGE_LAYER)), 0.5f);
    }

    /** The biped model data, baked onto the standard 64x64 player-skin layout. */
    public static TexturedModelData getTexturedModelData() {
        return TexturedModelData.of(BipedEntityModel.getModelData(new Dilation(0.0f), 0.0f), 64, 64);
    }

    @Override
    public Identifier getTexture(WanderingMageEntity entity) {
        if (entity.isProdigy()) {
            return PRODIGY_TEXTURE;
        }
        int index = Math.max(1, Math.min(3, entity.getCircle())) - 1;
        return CIRCLE_TEXTURES[index];
    }

    @Override
    public void render(WanderingMageEntity entity, float yaw, float tickDelta, MatrixStack matrices,
                       VertexConsumerProvider vertexConsumers, int light) {
        super.render(entity, yaw, tickDelta, matrices, vertexConsumers, light);
        if (entity.isCasting()) {
            renderTelegraph(entity, matrices, vertexConsumers);
        }
    }

    /**
     * Draws the locked-trajectory telegraph: a translucent red beam from the mage's head along the
     * exact direction the Magic Missile will fly, stopping at the first block it would hit. The
     * direction is the entity's synced, locked aim — it does not change during the cast.
     */
    private void renderTelegraph(WanderingMageEntity entity, MatrixStack matrices, VertexConsumerProvider vertexConsumers) {
        Vec3d dir = entity.getAimDirection();
        if (dir.lengthSquared() < 1.0e-6) {
            return;
        }
        dir = dir.normalize();

        double eyeHeight = entity.getEyeHeight(entity.getPose());
        // Find how far the shot travels before a block stops it (max range = the missile's 50 blocks).
        Vec3d eye = entity.getEyePos();
        double length = 50.0;
        HitResult hit = entity.getWorld().raycast(new RaycastContext(eye, eye.add(dir.multiply(50.0)),
                RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, entity));
        if (hit.getType() == HitResult.Type.BLOCK) {
            length = eye.distanceTo(hit.getPos());
        }

        // Build two axes perpendicular to the beam so we can draw a few parallel lines for thickness.
        Vec3d ref = Math.abs(dir.y) < 0.99 ? new Vec3d(0, 1, 0) : new Vec3d(1, 0, 0);
        Vec3d perp1 = dir.crossProduct(ref).normalize();
        Vec3d perp2 = dir.crossProduct(perp1).normalize();

        Matrix4f pose = matrices.peek().getPositionMatrix();
        Matrix3f normal = matrices.peek().getNormalMatrix();
        VertexConsumer vc = vertexConsumers.getBuffer(RenderLayer.getLines());

        double t = 0.07; // beam half-thickness
        Vec3d[] offsets = {
                Vec3d.ZERO,
                perp1.multiply(t), perp1.multiply(-t),
                perp2.multiply(t), perp2.multiply(-t),
        };
        for (Vec3d o : offsets) {
            double sx = o.x;
            double sy = eyeHeight + o.y;
            double sz = o.z;
            double ex = dir.x * length + o.x;
            double ey = eyeHeight + dir.y * length + o.y;
            double ez = dir.z * length + o.z;
            vc.vertex(pose, (float) sx, (float) sy, (float) sz).color(255, 45, 45, 150)
                    .normal(normal, (float) dir.x, (float) dir.y, (float) dir.z).next();
            vc.vertex(pose, (float) ex, (float) ey, (float) ez).color(255, 45, 45, 150)
                    .normal(normal, (float) dir.x, (float) dir.y, (float) dir.z).next();
        }
    }
}
