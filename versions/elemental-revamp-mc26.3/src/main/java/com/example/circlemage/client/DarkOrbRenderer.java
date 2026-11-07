package com.example.circlemage.client;

import com.example.circlemage.entity.DarkOrbEntity;
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
import net.minecraft.util.Mth;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

/**
 * Draws the Dark Orb as a slowly-spinning void sphere. While it is absorbing an active-chant Spear of
 * Light, a bright counter-rotating halo swirls over it (the white-and-black, oil-and-water look).
 */
public class DarkOrbRenderer extends ProceduralEntityRenderer<DarkOrbEntity> {

    private static final Identifier ORB_TEX = Identifier.fromNamespaceAndPath("circlemage", "textures/entity/dark_orb.png");
    private static final Identifier SWIRL_TEX = Identifier.fromNamespaceAndPath("circlemage", "textures/entity/magic_missile.png");
    private static final RenderType ORB_LAYER = RenderTypes.entityTranslucentEmissive(ORB_TEX);
    private static final RenderType SWIRL_LAYER = RenderTypes.entityTranslucentEmissive(SWIRL_TEX);
    private static final int FULL_BRIGHT = 0xF000F0;

    public DarkOrbRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public void render(DarkOrbEntity entity, float yaw, float tickDelta, PoseStack matrices,
                       MeshBuffers vertexConsumers, int light) {
        // While detonating, the body is gone — the blast particles carry the visuals.
        if (entity.isDetonating()) {
            super.render(entity, yaw, tickDelta, matrices, vertexConsumers, light);
            return;
        }

        float time = entity.level().getGameTime() + tickDelta;
        float scale = entity.getScale(); // shrinks during the compression phase

        matrices.pushPose();
        matrices.rotate(net.minecraft.client.Minecraft.getInstance().gameRenderer.mainCamera().rotation());

        // Void body (doubled size, scaled by the compression state).
        drawBillboard(matrices, vertexConsumers.getBuffer(ORB_LAYER),
                (0.84f + 0.08f * Mth.sin(time * 0.25f)) * scale, time * 6.0f, 255, 255, 255, 255);

        // Absorbing: a brighter, larger, counter-spinning halo.
        if (entity.isAbsorbing()) {
            drawBillboard(matrices, vertexConsumers.getBuffer(SWIRL_LAYER),
                    1.24f + 0.12f * Mth.sin(time * 0.5f), -time * 11.0f, 255, 255, 255, 200);
        }

        matrices.popPose();
        super.render(entity, yaw, tickDelta, matrices, vertexConsumers, light);
    }

    private void drawBillboard(PoseStack matrices, Vertex vc, float s, float spinDeg,
                               int r, int g, int b, int a) {
        matrices.pushPose();
        matrices.rotate(Axis.ZP.rotationDegrees(spinDeg));
        Matrix4f pose = matrices.last().pose();
        Matrix3f normal = matrices.last().normal();
        // Double-sided.
        vertex(vc, pose, normal, -s, -s, 0f, 1f, r, g, b, a);
        vertex(vc, pose, normal, s, -s, 1f, 1f, r, g, b, a);
        vertex(vc, pose, normal, s, s, 1f, 0f, r, g, b, a);
        vertex(vc, pose, normal, -s, s, 0f, 0f, r, g, b, a);
        vertex(vc, pose, normal, -s, s, 0f, 0f, r, g, b, a);
        vertex(vc, pose, normal, s, s, 1f, 0f, r, g, b, a);
        vertex(vc, pose, normal, s, -s, 1f, 1f, r, g, b, a);
        vertex(vc, pose, normal, -s, -s, 0f, 1f, r, g, b, a);
        matrices.popPose();
    }

    private void vertex(Vertex vc, Matrix4f pose, Matrix3f normal, float x, float y, float u, float v,
                        int r, int g, int b, int a) {
        vc.vertex(pose, x, y, 0.0f)
                .color(r, g, b, a)
                .uv(u, v)
                .overlayCoords(OverlayTexture.NO_OVERLAY)
                .uv2(FULL_BRIGHT)
                .normal(normal, 0.0f, 0.0f, 1.0f)
                .endVertex();
    }

    @Override
    public Identifier getTexture(DarkOrbEntity entity) {
        return ORB_TEX;
    }
}
