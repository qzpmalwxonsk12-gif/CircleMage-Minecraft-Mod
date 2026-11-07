package com.example.circlemage.client;

import com.example.circlemage.entity.SpatialRendEntity;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

/**
 * Renders the Spatial Rend as a tall (10-block), 1-wide, space-textured blade oriented across its
 * travel direction. Full-bright and double-sided.
 */
public class SpatialRendRenderer extends EntityRenderer<SpatialRendEntity> {

    private static final Identifier TEXTURE = new Identifier("circlemage", "textures/entity/spatial_rend.png");
    private static final RenderLayer LAYER = RenderLayer.getEntityTranslucentEmissive(TEXTURE);
    private static final int FULL_BRIGHT = 0xF000F0;

    public SpatialRendRenderer(EntityRendererFactory.Context context) {
        super(context);
    }

    @Override
    public void render(SpatialRendEntity entity, float yaw, float tickDelta, MatrixStack matrices,
                       VertexConsumerProvider vertexConsumers, int light) {
        Vec3d dir = entity.getDirection();
        float yawDeg = (float) (Math.atan2(dir.x, dir.z) * 180.0 / Math.PI);

        matrices.push();
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(yawDeg)); // local +Z = travel
        Matrix4f pose = matrices.peek().getPositionMatrix();
        Matrix3f normal = matrices.peek().getNormalMatrix();
        VertexConsumer vc = vertexConsumers.getBuffer(LAYER);

        float hw = 0.55f;  // ~1 block wide
        float bottom = -4.0f;
        float top = 5.0f;  // 10 tall, centred on the caster's body

        // Front and back faces.
        quad(vc, pose, normal, -hw, bottom, hw, top, false);
        quad(vc, pose, normal, -hw, bottom, hw, top, true);

        matrices.pop();
        super.render(entity, yaw, tickDelta, matrices, vertexConsumers, light);
    }

    private void quad(VertexConsumer vc, Matrix4f pose, Matrix3f normal, float x0, float y0, float x1, float y1, boolean reversed) {
        if (reversed) {
            vertex(vc, pose, normal, x0, y0, 0f, 1f);
            vertex(vc, pose, normal, x0, y1, 0f, 0f);
            vertex(vc, pose, normal, x1, y1, 1f, 0f);
            vertex(vc, pose, normal, x1, y0, 1f, 1f);
        } else {
            vertex(vc, pose, normal, x0, y0, 0f, 1f);
            vertex(vc, pose, normal, x1, y0, 1f, 1f);
            vertex(vc, pose, normal, x1, y1, 1f, 0f);
            vertex(vc, pose, normal, x0, y1, 0f, 0f);
        }
    }

    private void vertex(VertexConsumer vc, Matrix4f pose, Matrix3f normal, float x, float y, float u, float v) {
        vc.vertex(pose, x, y, 0.0f)
                .color(255, 255, 255, 255)
                .texture(u, v)
                .overlay(OverlayTexture.DEFAULT_UV)
                .light(FULL_BRIGHT)
                .normal(normal, 0.0f, 0.0f, 1.0f)
                .next();
    }

    @Override
    public Identifier getTexture(SpatialRendEntity entity) {
        return TEXTURE;
    }
}
