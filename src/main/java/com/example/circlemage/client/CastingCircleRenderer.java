package com.example.circlemage.client;

import com.example.circlemage.entity.WanderingMageEntity;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
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

    private static final Identifier RUNE_TEX = new Identifier("circlemage", "textures/particle/rune.png");
    private static final RenderLayer LAYER = RenderLayer.getEntityTranslucentEmissive(RUNE_TEX);
    private static final int FULL_BRIGHT = 0xF000F0;

    private CastingCircleRenderer() {
    }

    public static void register() {
        WorldRenderEvents.AFTER_ENTITIES.register(CastingCircleRenderer::onRender);
    }

    private static void onRender(WorldRenderContext ctx) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.world == null || ctx.consumers() == null) {
            return;
        }
        float tickDelta = ctx.tickDelta();
        float time = client.world.getTime() + tickDelta;
        Vec3d cam = ctx.camera().getPos();
        MatrixStack matrices = ctx.matrixStack();

        // Local player's charge circle.
        if (ModKeybinds.isPriming() && !client.player.isSpectator()) {
            float progress = ModKeybinds.primeProgress();
            float[] rgb = spellColor(ModKeybinds.primingSpellId());
            Vec3d eye = client.player.getCameraPosVec(tickDelta);
            Vec3d look = client.player.getRotationVec(tickDelta);
            Vec3d center = eye.add(look.multiply(2.0)).add(0.0, -0.35, 0.0);
            float radius = 0.55f + 0.75f * progress;
            float alpha = 0.35f + 0.6f * progress;
            float spin = time * (4.0f + 8.0f * progress);
            drawCircle(matrices, ctx.consumers(), cam, center, look, radius, rgb, alpha, spin);
        }

        // Wandering Mage cast telegraphs (they lock their aim, then fire along it).
        for (Entity e : client.world.getEntities()) {
            if (e instanceof WanderingMageEntity mage && mage.isCasting()) {
                Vec3d aim = mage.getAimDirection();
                if (aim.lengthSquared() < 1.0e-4) {
                    continue;
                }
                aim = aim.normalize();
                Vec3d center = mage.getCameraPosVec(tickDelta).add(aim.multiply(1.1));
                drawCircle(matrices, ctx.consumers(), cam, center, aim, 0.8f,
                        new float[]{0.6f, 0.85f, 1.0f}, 0.85f, time * 7.0f);
            }
        }
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
    private static void drawCircle(MatrixStack matrices, VertexConsumerProvider consumers, Vec3d cam,
                                   Vec3d center, Vec3d facing, float radius, float[] rgb, float alpha,
                                   float angleDeg) {
        Vec3d n = facing.normalize();
        Vec3d ref = Math.abs(n.y) > 0.98 ? new Vec3d(1, 0, 0) : new Vec3d(0, 1, 0);
        Vec3d u = n.crossProduct(ref).normalize();
        Vec3d v = n.crossProduct(u).normalize();
        double a = Math.toRadians(angleDeg);
        Vec3d ur = u.multiply(Math.cos(a)).add(v.multiply(Math.sin(a))).multiply(radius);
        Vec3d vr = v.multiply(Math.cos(a)).subtract(u.multiply(Math.sin(a))).multiply(radius);

        matrices.push();
        matrices.translate(center.x - cam.x, center.y - cam.y, center.z - cam.z);
        Matrix4f pose = matrices.peek().getPositionMatrix();
        Matrix3f normal = matrices.peek().getNormalMatrix();
        VertexConsumer vc = consumers.getBuffer(LAYER);

        int r = (int) (rgb[0] * 255), g = (int) (rgb[1] * 255), b = (int) (rgb[2] * 255);
        int al = (int) (Math.min(1.0f, alpha) * 255);

        // front
        vertex(vc, pose, normal, ur.negate().add(vr.negate()), 0f, 1f, r, g, b, al);
        vertex(vc, pose, normal, ur.add(vr.negate()), 1f, 1f, r, g, b, al);
        vertex(vc, pose, normal, ur.add(vr), 1f, 0f, r, g, b, al);
        vertex(vc, pose, normal, ur.negate().add(vr), 0f, 0f, r, g, b, al);
        // back
        vertex(vc, pose, normal, ur.negate().add(vr), 0f, 0f, r, g, b, al);
        vertex(vc, pose, normal, ur.add(vr), 1f, 0f, r, g, b, al);
        vertex(vc, pose, normal, ur.add(vr.negate()), 1f, 1f, r, g, b, al);
        vertex(vc, pose, normal, ur.negate().add(vr.negate()), 0f, 1f, r, g, b, al);

        matrices.pop();
    }

    private static void vertex(VertexConsumer vc, Matrix4f pose, Matrix3f normal, Vec3d p,
                               float texU, float texV, int r, int g, int b, int a) {
        vc.vertex(pose, (float) p.x, (float) p.y, (float) p.z)
                .color(r, g, b, a)
                .texture(texU, texV)
                .overlay(OverlayTexture.DEFAULT_UV)
                .light(FULL_BRIGHT)
                .normal(normal, 0.0f, 1.0f, 0.0f)
                .next();
    }
}
