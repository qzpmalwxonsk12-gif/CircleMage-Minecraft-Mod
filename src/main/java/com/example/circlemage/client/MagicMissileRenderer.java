package com.example.circlemage.client;

import com.example.circlemage.entity.MagicMissileEntity;
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
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

/**
 * Renders the bolt in one of two forms:
 * <ul>
 *     <li>Magic Missile — a glowing, spinning, camera-facing orb.</li>
 *     <li>Spear of Light — a golden lance (two crossed quads) oriented along its flight path.</li>
 * </ul>
 * Trails come from particles spawned in the entity's tick.
 */
public class MagicMissileRenderer extends EntityRenderer<MagicMissileEntity> {

    private static final Identifier ORB_TEX = new Identifier("circlemage", "textures/entity/magic_missile.png");
    private static final Identifier SPEAR_TEX = new Identifier("circlemage", "textures/entity/spear.png");
    private static final RenderLayer ORB_LAYER = RenderLayer.getEntityTranslucentEmissive(ORB_TEX);
    private static final RenderLayer SPEAR_LAYER = RenderLayer.getEntityTranslucentEmissive(SPEAR_TEX);
    private static final int FULL_BRIGHT = 0xF000F0;

    public MagicMissileRenderer(EntityRendererFactory.Context context) {
        super(context);
    }

    @Override
    public void render(MagicMissileEntity entity, float yaw, float tickDelta, MatrixStack matrices,
                       VertexConsumerProvider vertexConsumers, int light) {
        float time = entity.getWorld().getTime() + tickDelta;

        if (entity.isSpear()) {
            renderSpear(entity, matrices, vertexConsumers);
        } else {
            renderOrb(matrices, vertexConsumers, time, entity.isFireBolt());
        }
        super.render(entity, yaw, tickDelta, matrices, vertexConsumers, light);
    }

    // --- Magic Missile: spinning billboard orb (tinted orange for the Fireball) ---

    private void renderOrb(MatrixStack matrices, VertexConsumerProvider vertexConsumers, float time, boolean fire) {
        float size = 0.42f + 0.05f * MathHelper.sin(time * 0.4f);
        int r = fire ? 255 : 255;
        int g = fire ? 150 : 255;
        int b = fire ? 60 : 255;
        matrices.push();
        matrices.multiply(this.dispatcher.getRotation());
        matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(time * 9.0f));
        Matrix4f pose = matrices.peek().getPositionMatrix();
        Matrix3f normal = matrices.peek().getNormalMatrix();
        VertexConsumer vc = vertexConsumers.getBuffer(ORB_LAYER);
        billboardQuad(vc, pose, normal, size, false, r, g, b);
        billboardQuad(vc, pose, normal, size, true, r, g, b);
        matrices.pop();
    }

    private void billboardQuad(VertexConsumer vc, Matrix4f pose, Matrix3f normal, float s, boolean reversed,
                               int r, int g, int b) {
        if (reversed) {
            tintedVertex(vc, pose, normal, -s, -s, 0f, 1f, r, g, b);
            tintedVertex(vc, pose, normal, -s, s, 0f, 0f, r, g, b);
            tintedVertex(vc, pose, normal, s, s, 1f, 0f, r, g, b);
            tintedVertex(vc, pose, normal, s, -s, 1f, 1f, r, g, b);
        } else {
            tintedVertex(vc, pose, normal, -s, -s, 0f, 1f, r, g, b);
            tintedVertex(vc, pose, normal, s, -s, 1f, 1f, r, g, b);
            tintedVertex(vc, pose, normal, s, s, 1f, 0f, r, g, b);
            tintedVertex(vc, pose, normal, -s, s, 0f, 0f, r, g, b);
        }
    }

    private void tintedVertex(VertexConsumer vc, Matrix4f pose, Matrix3f normal, float x, float y,
                              float u, float v, int r, int g, int b) {
        vc.vertex(pose, x, y, 0f)
                .color(r, g, b, 255)
                .texture(u, v)
                .overlay(OverlayTexture.DEFAULT_UV)
                .light(FULL_BRIGHT)
                .normal(normal, 0.0f, 0.0f, 1.0f)
                .next();
    }

    // --- Spear of Light: lance oriented along travel -------------------------

    private void renderSpear(MagicMissileEntity entity, MatrixStack matrices, VertexConsumerProvider vertexConsumers) {
        Vec3d v = entity.getVelocity();
        if (v.lengthSquared() < 1.0e-6) {
            v = new Vec3d(0, 0, 1);
        }
        v = v.normalize();
        float yawDeg = (float) (Math.atan2(v.x, v.z) * 180.0 / Math.PI);
        float pitchDeg = (float) (Math.atan2(v.y, Math.sqrt(v.x * v.x + v.z * v.z)) * 180.0 / Math.PI);

        matrices.push();
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(yawDeg));
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-pitchDeg));
        Matrix4f pose = matrices.peek().getPositionMatrix();
        Matrix3f normal = matrices.peek().getNormalMatrix();
        VertexConsumer vc = vertexConsumers.getBuffer(SPEAR_LAYER);

        float zTip = 1.05f, zTail = -0.85f;
        // Wide, dim glow aura first, then the bright core lance — both as crossed quads so the
        // lance reads as a glowing 3D shaft from any angle.
        lanceQuad(vc, pose, normal, 0.36f, zTip + 0.25f, zTail - 0.25f, true, 90);
        lanceQuad(vc, pose, normal, 0.36f, zTip + 0.25f, zTail - 0.25f, false, 90);
        lanceQuad(vc, pose, normal, 0.18f, zTip, zTail, true, 255);
        lanceQuad(vc, pose, normal, 0.18f, zTip, zTail, false, 255);

        matrices.pop();
    }

    /** A length-wise quad. {@code horizontal=true} spans X (flat), false spans Y (upright). Double-sided. */
    private void lanceQuad(VertexConsumer vc, Matrix4f pose, Matrix3f normal, float hw, float zTip, float zTail,
                           boolean horizontal, int alpha) {
        float ax = horizontal ? hw : 0f;
        float ay = horizontal ? 0f : hw;
        // front winding (tip uv v=0, tail v=1)
        vertex(vc, pose, normal, -ax, -ay, zTail, 0f, 1f, alpha);
        vertex(vc, pose, normal, ax, ay, zTail, 1f, 1f, alpha);
        vertex(vc, pose, normal, ax, ay, zTip, 1f, 0f, alpha);
        vertex(vc, pose, normal, -ax, -ay, zTip, 0f, 0f, alpha);
        // back winding
        vertex(vc, pose, normal, -ax, -ay, zTip, 0f, 0f, alpha);
        vertex(vc, pose, normal, ax, ay, zTip, 1f, 0f, alpha);
        vertex(vc, pose, normal, ax, ay, zTail, 1f, 1f, alpha);
        vertex(vc, pose, normal, -ax, -ay, zTail, 0f, 1f, alpha);
    }

    private void vertex(VertexConsumer vc, Matrix4f pose, Matrix3f normal, float x, float y, float z, float u, float v, int alpha) {
        vc.vertex(pose, x, y, z)
                .color(255, 255, 255, alpha)
                .texture(u, v)
                .overlay(OverlayTexture.DEFAULT_UV)
                .light(FULL_BRIGHT)
                .normal(normal, 0.0f, 0.0f, 1.0f)
                .next();
    }

    @Override
    public Identifier getTexture(MagicMissileEntity entity) {
        return entity.isSpear() ? SPEAR_TEX : ORB_TEX;
    }
}
