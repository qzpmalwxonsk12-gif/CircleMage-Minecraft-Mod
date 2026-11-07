package com.example.circlemage.client;

import com.example.circlemage.entity.SpatialRendEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.example.circlemage.client.MeshBuffers.Vertex;
import com.mojang.math.Axis;
import com.example.circlemage.client.MeshBuffers;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

/**
 * Renders the Spatial Rend as a tall (10-block), 1-wide, space-textured blade oriented across its
 * travel direction. Full-bright and double-sided.
 */
public class SpatialRendRenderer extends ProceduralEntityRenderer<SpatialRendEntity> {

    private static final Identifier TEXTURE = Identifier.fromNamespaceAndPath("circlemage", "textures/entity/spatial_rend.png");
    private static final RenderType LAYER = RenderTypes.entityTranslucentEmissive(TEXTURE);
    private static final int FULL_BRIGHT = 0xF000F0;

    public SpatialRendRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public void render(SpatialRendEntity entity, float yaw, float tickDelta, PoseStack matrices,
                       MeshBuffers vertexConsumers, int light) {
        Vec3 dir = entity.getRendDirection();
        float yawDeg = (float) (Math.atan2(dir.x, dir.z) * 180.0 / Math.PI);

        matrices.pushPose();
        matrices.rotate(Axis.YP.rotationDegrees(yawDeg)); // local +Z = travel
        Matrix4f pose = matrices.last().pose();
        Matrix3f normal = matrices.last().normal();
        Vertex vc = vertexConsumers.getBuffer(LAYER);

        float hw = 0.55f;  // ~1 block wide
        float bottom = -4.0f;
        float top = 5.0f;  // 10 tall, centred on the caster's body

        // Front and back faces.
        quad(vc, pose, normal, -hw, bottom, hw, top, false);
        quad(vc, pose, normal, -hw, bottom, hw, top, true);

        matrices.popPose();
        super.render(entity, yaw, tickDelta, matrices, vertexConsumers, light);
    }

    private void quad(Vertex vc, Matrix4f pose, Matrix3f normal, float x0, float y0, float x1, float y1, boolean reversed) {
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

    private void vertex(Vertex vc, Matrix4f pose, Matrix3f normal, float x, float y, float u, float v) {
        vc.vertex(pose, x, y, 0.0f)
                .color(255, 255, 255, 255)
                .uv(u, v)
                .overlayCoords(OverlayTexture.NO_OVERLAY)
                .uv2(FULL_BRIGHT)
                .normal(normal, 0.0f, 0.0f, 1.0f)
                .endVertex();
    }

    @Override
    public Identifier getTexture(SpatialRendEntity entity) {
        return TEXTURE;
    }
}
