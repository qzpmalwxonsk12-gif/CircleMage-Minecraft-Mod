package com.example.circlemage.client;

import com.example.circlemage.entity.MagicMissileEntity;
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
import net.minecraft.world.phys.Vec3;
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
public class MagicMissileRenderer extends ProceduralEntityRenderer<MagicMissileEntity> {

    private static final Identifier ORB_TEX = Identifier.fromNamespaceAndPath("circlemage", "textures/entity/magic_missile.png");
    private static final Identifier SPEAR_TEX = Identifier.fromNamespaceAndPath("circlemage", "textures/entity/spear.png");
    private static final RenderType ORB_LAYER = RenderTypes.entityTranslucentEmissive(ORB_TEX);
    private static final RenderType SPEAR_LAYER = RenderTypes.entityTranslucentEmissive(SPEAR_TEX);
    private static final int FULL_BRIGHT = 0xF000F0;

    public MagicMissileRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public void render(MagicMissileEntity entity, float yaw, float tickDelta, PoseStack matrices,
                       MeshBuffers vertexConsumers, int light) {
        float time = entity.level().getGameTime() + tickDelta;

        if (entity.isSpear()) {
            renderSpear(entity, matrices, vertexConsumers);
        } else {
            renderOrb(matrices, vertexConsumers, time, entity.isFireBolt());
        }
        super.render(entity, yaw, tickDelta, matrices, vertexConsumers, light);
    }

    // --- Magic Missile: spinning billboard orb (tinted orange for the Fireball) ---

    private void renderOrb(PoseStack matrices, MeshBuffers vertexConsumers, float time, boolean fire) {
        float size = 0.42f + 0.05f * Mth.sin(time * 0.4f);
        int r = fire ? 255 : 255;
        int g = fire ? 150 : 255;
        int b = fire ? 60 : 255;
        matrices.pushPose();
        matrices.rotate(net.minecraft.client.Minecraft.getInstance().gameRenderer.mainCamera().rotation());
        matrices.rotate(Axis.ZP.rotationDegrees(time * 9.0f));
        Matrix4f pose = matrices.last().pose();
        Matrix3f normal = matrices.last().normal();
        Vertex vc = vertexConsumers.getBuffer(ORB_LAYER);
        billboardQuad(vc, pose, normal, size, false, r, g, b);
        billboardQuad(vc, pose, normal, size, true, r, g, b);
        matrices.popPose();
    }

    private void billboardQuad(Vertex vc, Matrix4f pose, Matrix3f normal, float s, boolean reversed,
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

    private void tintedVertex(Vertex vc, Matrix4f pose, Matrix3f normal, float x, float y,
                              float u, float v, int r, int g, int b) {
        vc.vertex(pose, x, y, 0f)
                .color(r, g, b, 255)
                .uv(u, v)
                .overlayCoords(OverlayTexture.NO_OVERLAY)
                .uv2(FULL_BRIGHT)
                .normal(normal, 0.0f, 0.0f, 1.0f)
                .endVertex();
    }

    // --- Spear of Light: lance oriented along travel -------------------------

    private void renderSpear(MagicMissileEntity entity, PoseStack matrices, MeshBuffers vertexConsumers) {
        Vec3 v = entity.getDeltaMovement();
        if (v.lengthSqr() < 1.0e-6) {
            v = new Vec3(0, 0, 1);
        }
        v = v.normalize();
        float yawDeg = (float) (Math.atan2(v.x, v.z) * 180.0 / Math.PI);
        float pitchDeg = (float) (Math.atan2(v.y, Math.sqrt(v.x * v.x + v.z * v.z)) * 180.0 / Math.PI);

        matrices.pushPose();
        matrices.rotate(Axis.YP.rotationDegrees(yawDeg));
        matrices.rotate(Axis.XP.rotationDegrees(-pitchDeg));
        Matrix4f pose = matrices.last().pose();
        Matrix3f normal = matrices.last().normal();
        Vertex vc = vertexConsumers.getBuffer(SPEAR_LAYER);

        float zTip = 1.05f, zTail = -0.85f;
        // Wide, dim glow aura first, then the bright core lance — both as crossed quads so the
        // lance reads as a glowing 3D shaft from any angle.
        lanceQuad(vc, pose, normal, 0.36f, zTip + 0.25f, zTail - 0.25f, true, 90);
        lanceQuad(vc, pose, normal, 0.36f, zTip + 0.25f, zTail - 0.25f, false, 90);
        lanceQuad(vc, pose, normal, 0.18f, zTip, zTail, true, 255);
        lanceQuad(vc, pose, normal, 0.18f, zTip, zTail, false, 255);

        matrices.popPose();
    }

    /** A length-wise quad. {@code horizontal=true} spans X (flat), false spans Y (upright). Double-sided. */
    private void lanceQuad(Vertex vc, Matrix4f pose, Matrix3f normal, float hw, float zTip, float zTail,
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

    private void vertex(Vertex vc, Matrix4f pose, Matrix3f normal, float x, float y, float z, float u, float v, int alpha) {
        vc.vertex(pose, x, y, z)
                .color(255, 255, 255, alpha)
                .uv(u, v)
                .overlayCoords(OverlayTexture.NO_OVERLAY)
                .uv2(FULL_BRIGHT)
                .normal(normal, 0.0f, 0.0f, 1.0f)
                .endVertex();
    }

    @Override
    public Identifier getTexture(MagicMissileEntity entity) {
        return entity.isSpear() ? SPEAR_TEX : ORB_TEX;
    }
}
