package com.example.circlemage.client;

import com.example.circlemage.entity.MagicBarrierEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.example.circlemage.client.MeshBuffers.Vertex;
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
 * Renders the magic barrier as live, translucent, full-bright energy geometry:
 * <ul>
 *     <li>WALL mode — a box of glowing rune panels sized to the barrier's extents.</li>
 *     <li>DOME mode — a hexagonal energy shell (UV sphere) around the caster.</li>
 * </ul>
 * Both pulse gently and are drawn double-sided so they're visible from inside and out. The faint
 * shimmer sparks still come from particles spawned in the entity's tick.
 */
public class MagicBarrierRenderer extends ProceduralEntityRenderer<MagicBarrierEntity> {

    private static final Identifier PANEL_TEX = Identifier.fromNamespaceAndPath("circlemage", "textures/entity/barrier_panel.png");
    private static final Identifier DOME_TEX = Identifier.fromNamespaceAndPath("circlemage", "textures/entity/barrier_dome.png");
    private static final RenderType PANEL_LAYER = RenderTypes.entityTranslucentEmissive(PANEL_TEX);
    private static final RenderType DOME_LAYER = RenderTypes.entityTranslucentEmissive(DOME_TEX);
    private static final int FULL_BRIGHT = 0xF000F0;

    // Cyan tint kept near-white so the texture's own colour shows through.
    private static final int TR = 190, TG = 235, TB = 255;

    public MagicBarrierRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public void render(MagicBarrierEntity entity, float yaw, float tickDelta, PoseStack matrices,
                       MeshBuffers vertexConsumers, int light) {
        float time = entity.level().getGameTime() + tickDelta;
        int alpha = (int) (255 * (0.32f + 0.12f * Mth.sin(time * 0.15f)));

        matrices.pushPose();
        Matrix4f pose = matrices.last().pose();
        Matrix3f normal = matrices.last().normal();

        if (entity.isDome()) {
            Vertex vc = vertexConsumers.getBuffer(DOME_LAYER);
            renderDome(vc, pose, normal, entity.getHalfX(), alpha);
        } else {
            Vertex vc = vertexConsumers.getBuffer(PANEL_LAYER);
            renderWall(vc, pose, normal, entity.getHalfX(), entity.getHalfY(), entity.getHalfZ(), Math.min(255, alpha + 60));
        }

        matrices.popPose();
        super.render(entity, yaw, tickDelta, matrices, vertexConsumers, light);
    }

    // --- Wall: a box of rune panels ------------------------------------------

    private void renderWall(Vertex vc, Matrix4f m, Matrix3f n, float hx, float hy, float hz, int a) {
        // +X / -X faces
        quad(vc, m, n, 1, 0, 0, a,
                hx, -hy, -hz, hx, -hy, hz, hx, hy, hz, hx, hy, -hz);
        quad(vc, m, n, -1, 0, 0, a,
                -hx, -hy, hz, -hx, -hy, -hz, -hx, hy, -hz, -hx, hy, hz);
        // +Z / -Z faces
        quad(vc, m, n, 0, 0, 1, a,
                -hx, -hy, hz, hx, -hy, hz, hx, hy, hz, -hx, hy, hz);
        quad(vc, m, n, 0, 0, -1, a,
                hx, -hy, -hz, -hx, -hy, -hz, -hx, hy, -hz, hx, hy, -hz);
        // +Y / -Y caps
        quad(vc, m, n, 0, 1, 0, a,
                -hx, hy, -hz, hx, hy, -hz, hx, hy, hz, -hx, hy, hz);
        quad(vc, m, n, 0, -1, 0, a,
                -hx, -hy, hz, hx, -hy, hz, hx, -hy, -hz, -hx, -hy, -hz);
    }

    // --- Dome: a hexagonal energy shell --------------------------------------

    private void renderDome(Vertex vc, Matrix4f m, Matrix3f n, float radius, int a) {
        final int lat = 12;
        final int lon = 18;
        final float uRep = 5.0f;
        final float vRep = 2.5f;
        for (int i = 0; i < lat; i++) {
            float t0 = (float) Math.PI * i / lat;
            float t1 = (float) Math.PI * (i + 1) / lat;
            for (int j = 0; j < lon; j++) {
                float p0 = (float) (2.0 * Math.PI) * j / lon;
                float p1 = (float) (2.0 * Math.PI) * (j + 1) / lon;
                float u0 = (float) j / lon * uRep;
                float u1 = (float) (j + 1) / lon * uRep;
                float v0 = (float) i / lat * vRep;
                float v1 = (float) (i + 1) / lat * vRep;

                float[] a0 = sphere(radius, t0, p0);
                float[] a1 = sphere(radius, t1, p0);
                float[] a2 = sphere(radius, t1, p1);
                float[] a3 = sphere(radius, t0, p1);

                quadUV(vc, m, n, a,
                        a0[0], a0[1], a0[2], u0, v0,
                        a1[0], a1[1], a1[2], u0, v1,
                        a2[0], a2[1], a2[2], u1, v1,
                        a3[0], a3[1], a3[2], u1, v0);
            }
        }
    }

    private static float[] sphere(float r, float theta, float phi) {
        float st = Mth.sin(theta);
        return new float[]{
                r * st * Mth.cos(phi),
                r * Mth.cos(theta),
                r * st * Mth.sin(phi)
        };
    }

    // --- Vertex helpers (double-sided) ---------------------------------------

    /** Axis-aligned-ish quad with whole-texture UVs, drawn double-sided. */
    private void quad(Vertex vc, Matrix4f m, Matrix3f n, float nx, float ny, float nz, int a,
                      float x0, float y0, float z0, float x1, float y1, float z1,
                      float x2, float y2, float z2, float x3, float y3, float z3) {
        quadUV(vc, m, n, a,
                x0, y0, z0, 0f, 1f,
                x1, y1, z1, 1f, 1f,
                x2, y2, z2, 1f, 0f,
                x3, y3, z3, 0f, 0f);
    }

    private void quadUV(Vertex vc, Matrix4f m, Matrix3f n, int a,
                        float x0, float y0, float z0, float u0, float v0,
                        float x1, float y1, float z1, float u1, float v1,
                        float x2, float y2, float z2, float u2, float v2,
                        float x3, float y3, float z3, float u3, float v3) {
        // Front winding.
        vert(vc, m, n, x0, y0, z0, u0, v0, a);
        vert(vc, m, n, x1, y1, z1, u1, v1, a);
        vert(vc, m, n, x2, y2, z2, u2, v2, a);
        vert(vc, m, n, x3, y3, z3, u3, v3, a);
        // Reverse winding so it's visible from the other side too.
        vert(vc, m, n, x3, y3, z3, u3, v3, a);
        vert(vc, m, n, x2, y2, z2, u2, v2, a);
        vert(vc, m, n, x1, y1, z1, u1, v1, a);
        vert(vc, m, n, x0, y0, z0, u0, v0, a);
    }

    private void vert(Vertex vc, Matrix4f m, Matrix3f n, float x, float y, float z, float u, float v, int a) {
        vc.vertex(m, x, y, z)
                .color(TR, TG, TB, a)
                .uv(u, v)
                .overlayCoords(OverlayTexture.NO_OVERLAY)
                .uv2(FULL_BRIGHT)
                .normal(n, 0.0f, 1.0f, 0.0f)
                .endVertex();
    }

    @Override
    public Identifier getTexture(MagicBarrierEntity entity) {
        return entity.isDome() ? DOME_TEX : PANEL_TEX;
    }
}
