package com.example.circlemage.client;

import com.example.circlemage.entity.WanderingMageEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.example.circlemage.client.MeshBuffers.Vertex;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelExtractionContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelExtractionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.fabricmc.fabric.api.client.rendering.v1.FabricRenderState;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import com.example.circlemage.client.MeshBuffers;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

/**
 * Draws world-space <b>casting circles</b> — the mod's signature rune ring (the barrier/particle
 * rune glyph) floating in the air:
 * <ul>
 *     <li>In front of the local player while a spell is held-to-charge: it grows, brightens, spins
 *     faster and takes the spell's elemental colour as the charge builds.</li>
 *     <li>In front of any Wandering Mage committing to a Magic Missile — the telegraph players
 *     learn to dodge.</li>
 * </ul>
 */
public final class CastingCircleRenderer {

    private static final Identifier RUNE_TEX = Identifier.fromNamespaceAndPath("circlemage", "textures/particle/rune.png");
    private static final RenderType LAYER = RenderTypes.entityTranslucentEmissive(RUNE_TEX);
    private static final int FULL_BRIGHT = 0xF000F0;

    private CastingCircleRenderer() {
    }

    private static final RenderStateDataKey<java.util.List<MeshBuffers.Batch>> CIRCLES=RenderStateDataKey.create();
    public static void register() {
        LevelExtractionEvents.END_EXTRACTION.register(CastingCircleRenderer::onExtract);
        LevelRenderEvents.COLLECT_SUBMITS.register(ctx -> MeshBuffers.submit(((FabricRenderState)ctx.levelState()).getDataOrDefault(CIRCLES,java.util.List.of()),ctx.poseStack(),ctx.submitNodeCollector()));
    }

    private static void onExtract(LevelExtractionContext ctx) {
        ((FabricRenderState)ctx.levelState()).setData(CIRCLES,java.util.List.of());
        MeshBuffers meshes=new MeshBuffers();
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.level == null) {
            return;
        }
        float tickDelta = ctx.deltaTracker().getGameTimeDeltaPartialTick(false);
        float time = client.level.getGameTime() + tickDelta;
        Vec3 cam = ctx.camera().position();
        PoseStack matrices = new PoseStack();

        // Local player's charge circle.
        if (ModKeybinds.isPriming() && !client.player.isSpectator()) {
            float progress = ModKeybinds.primeProgress();
            float[] rgb = spellColor(ModKeybinds.primingSpellId());
            Vec3 eye = client.player.getEyePosition(tickDelta);
            Vec3 look = client.player.getViewVector(tickDelta);
            Vec3 center = eye.add(look.scale(2.0)).add(0.0, -0.35, 0.0);
            float radius = 0.55f + 0.75f * progress;
            float alpha = 0.35f + 0.6f * progress;
            float spin = time * (4.0f + 8.0f * progress);
            drawCircle(matrices, meshes, cam, center, look, radius, rgb, alpha, spin);
        }

        // Wandering Mage cast telegraphs (they lock their aim, then fire along it).
        for (Entity e : client.level.entitiesForRendering()) {
            if (e instanceof WanderingMageEntity mage && mage.isCasting()) {
                Vec3 aim = mage.getAimDirection();
                if (aim.lengthSqr() < 1.0e-4) {
                    continue;
                }
                aim = aim.normalize();
                Vec3 center = mage.getEyePosition(tickDelta).add(aim.scale(1.1));
                drawCircle(matrices, meshes, cam, center, aim, 0.8f,
                        new float[]{0.6f, 0.85f, 1.0f}, 0.85f, time * 7.0f);
            }
        }
        ((FabricRenderState)ctx.levelState()).setData(CIRCLES,meshes.snapshot());
    }

    /** The rune ring's tint per (chantable) spell element. */
    private static float[] spellColor(int spellId) {
        return switch (spellId) {
            case 1, 11 -> new float[]{1.0f, 0.55f, 0.25f};  // Area Fire / Fireball: flame orange
            case 2 -> new float[]{0.75f, 0.5f, 1.0f};   // Slow / Dark Orb: violet
            case 4 -> new float[]{0.65f, 1.0f, 0.8f};   // Wall of Wind: pale green
            case 5 -> new float[]{1.0f, 0.9f, 0.4f};    // Thunder: gold
            default -> new float[]{0.7f, 0.85f, 1.0f};  // Bolt & friends: arcane blue-white
        };
    }

    /**
     * A double-sided textured quad centred on {@code center}, lying in the plane perpendicular to
     * {@code facing}, rotated by {@code angleDeg} about that axis.
     */
    private static void drawCircle(PoseStack matrices, MeshBuffers consumers, Vec3 cam,
                                   Vec3 center, Vec3 facing, float radius, float[] rgb, float alpha,
                                   float angleDeg) {
        Vec3 n = facing.normalize();
        Vec3 ref = Math.abs(n.y) > 0.98 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0);
        Vec3 u = n.cross(ref).normalize();
        Vec3 v = n.cross(u).normalize();
        double a = Math.toRadians(angleDeg);
        Vec3 ur = u.scale(Math.cos(a)).add(v.scale(Math.sin(a))).scale(radius);
        Vec3 vr = v.scale(Math.cos(a)).subtract(u.scale(Math.sin(a))).scale(radius);

        matrices.pushPose();
        matrices.translate(center.x - cam.x, center.y - cam.y, center.z - cam.z);
        Matrix4f pose = matrices.last().pose();
        Matrix3f normal = matrices.last().normal();
        Vertex vc = consumers.getBuffer(LAYER);

        int r = (int) (rgb[0] * 255), g = (int) (rgb[1] * 255), b = (int) (rgb[2] * 255);
        int al = (int) (Math.min(1.0f, alpha) * 255);

        // front
        vertex(vc, pose, normal, ur.reverse().add(vr.reverse()), 0f, 1f, r, g, b, al);
        vertex(vc, pose, normal, ur.add(vr.reverse()), 1f, 1f, r, g, b, al);
        vertex(vc, pose, normal, ur.add(vr), 1f, 0f, r, g, b, al);
        vertex(vc, pose, normal, ur.reverse().add(vr), 0f, 0f, r, g, b, al);
        // back
        vertex(vc, pose, normal, ur.reverse().add(vr), 0f, 0f, r, g, b, al);
        vertex(vc, pose, normal, ur.add(vr), 1f, 0f, r, g, b, al);
        vertex(vc, pose, normal, ur.add(vr.reverse()), 1f, 1f, r, g, b, al);
        vertex(vc, pose, normal, ur.reverse().add(vr.reverse()), 0f, 1f, r, g, b, al);

        matrices.popPose();
    }

    private static void vertex(Vertex vc, Matrix4f pose, Matrix3f normal, Vec3 p,
                               float texU, float texV, int r, int g, int b, int a) {
        vc.vertex(pose, (float) p.x, (float) p.y, (float) p.z)
                .color(r, g, b, a)
                .uv(texU, texV)
                .overlayCoords(OverlayTexture.NO_OVERLAY)
                .uv2(FULL_BRIGHT)
                .normal(normal, 0.0f, 1.0f, 0.0f)
                .endVertex();
    }
}
