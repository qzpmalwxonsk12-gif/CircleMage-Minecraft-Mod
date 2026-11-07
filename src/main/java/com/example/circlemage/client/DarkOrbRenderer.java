package com.example.circlemage.client;

import com.example.circlemage.entity.DarkOrbEntity;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

/**
 * Draws the Dark Orb as a slowly-spinning void sphere. While it is absorbing an active-chant Spear of
 * Light, a bright counter-rotating halo swirls over it (the white-and-black, oil-and-water look).
 */
public class DarkOrbRenderer extends EntityRenderer<DarkOrbEntity> {

    private static final Identifier ORB_TEX = new Identifier("circlemage", "textures/entity/dark_orb.png");
    private static final Identifier SWIRL_TEX = new Identifier("circlemage", "textures/entity/magic_missile.png");
    private static final RenderLayer ORB_LAYER = RenderLayer.getEntityTranslucentEmissive(ORB_TEX);
    private static final RenderLayer SWIRL_LAYER = RenderLayer.getEntityTranslucentEmissive(SWIRL_TEX);
    private static final int FULL_BRIGHT = 0xF000F0;

    public DarkOrbRenderer(EntityRendererFactory.Context context) {
        super(context);
    }

    @Override
    public void render(DarkOrbEntity entity, float yaw, float tickDelta, MatrixStack matrices,
                       VertexConsumerProvider vertexConsumers, int light) {
        // While detonating, the body is gone — the blast particles carry the visuals.
        if (entity.isDetonating()) {
            super.render(entity, yaw, tickDelta, matrices, vertexConsumers, light);
            return;
        }

        float time = entity.getWorld().getTime() + tickDelta;
        float scale = entity.getScale(); // shrinks during the compression phase

        matrices.push();
        matrices.multiply(this.dispatcher.getRotation());

        // Void body (doubled size, scaled by the compression state).
        drawBillboard(matrices, vertexConsumers.getBuffer(ORB_LAYER),
                (0.84f + 0.08f * MathHelper.sin(time * 0.25f)) * scale, time * 6.0f, 255, 255, 255, 255);

        // Absorbing: a brighter, larger, counter-spinning halo.
        if (entity.isAbsorbing()) {
            drawBillboard(matrices, vertexConsumers.getBuffer(SWIRL_LAYER),
                    1.24f + 0.12f * MathHelper.sin(time * 0.5f), -time * 11.0f, 255, 255, 255, 200);
        }

        matrices.pop();
        super.render(entity, yaw, tickDelta, matrices, vertexConsumers, light);
    }

    private void drawBillboard(MatrixStack matrices, VertexConsumer vc, float s, float spinDeg,
                               int r, int g, int b, int a) {
        matrices.push();
        matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(spinDeg));
        Matrix4f pose = matrices.peek().getPositionMatrix();
        Matrix3f normal = matrices.peek().getNormalMatrix();
        // Double-sided.
        vertex(vc, pose, normal, -s, -s, 0f, 1f, r, g, b, a);
        vertex(vc, pose, normal, s, -s, 1f, 1f, r, g, b, a);
        vertex(vc, pose, normal, s, s, 1f, 0f, r, g, b, a);
        vertex(vc, pose, normal, -s, s, 0f, 0f, r, g, b, a);
        vertex(vc, pose, normal, -s, s, 0f, 0f, r, g, b, a);
        vertex(vc, pose, normal, s, s, 1f, 0f, r, g, b, a);
        vertex(vc, pose, normal, s, -s, 1f, 1f, r, g, b, a);
        vertex(vc, pose, normal, -s, -s, 0f, 1f, r, g, b, a);
        matrices.pop();
    }

    private void vertex(VertexConsumer vc, Matrix4f pose, Matrix3f normal, float x, float y, float u, float v,
                        int r, int g, int b, int a) {
        vc.vertex(pose, x, y, 0.0f)
                .color(r, g, b, a)
                .texture(u, v)
                .overlay(OverlayTexture.DEFAULT_UV)
                .light(FULL_BRIGHT)
                .normal(normal, 0.0f, 0.0f, 1.0f)
                .next();
    }

    @Override
    public Identifier getTexture(DarkOrbEntity entity) {
        return ORB_TEX;
    }
}
