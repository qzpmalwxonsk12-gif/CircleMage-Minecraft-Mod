package com.example.circlemage.client;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;

/**
 * Renders the mana bar at the bottom of the screen, to the right of the hotbar, using the custom
 * runic frame / orb textures. The fill brightens and pulses while channelling.
 */
public final class ManaHudOverlay {

    private static final Identifier FRAME = new Identifier("circlemage", "textures/gui/mana_frame.png");
    private static final Identifier FILL = new Identifier("circlemage", "textures/gui/mana_fill.png");
    private static final Identifier ORB = new Identifier("circlemage", "textures/gui/mana_orb.png");

    private static final int FRAME_W = 104;
    private static final int FRAME_H = 14;
    private static final int INNER_W = 100; // fillable track width
    private static final int INNER_H = 10;

    private ManaHudOverlay() {
    }

    public static void render(DrawContext context, float tickDelta) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.options.hudHidden) {
            return;
        }

        int screenWidth = client.getWindow().getScaledWidth();
        int screenHeight = client.getWindow().getScaledHeight();

        // Frame just to the right of the centred 182px hotbar, kept on-screen on narrow windows.
        int x = screenWidth / 2 + 95;
        if (x + FRAME_W + 4 > screenWidth) {
            x = screenWidth - FRAME_W - 4;
        }
        int y = screenHeight - 20;

        double current = ClientManaState.currentMana;
        long threshold = Math.max(1L, ClientManaState.threshold);
        double fraction = MathHelper.clamp(current / threshold, 0.0, 1.0);
        int fillPx = (int) Math.round(INNER_W * fraction);

        boolean channeling = ClientManaState.channeling;
        float time = (client.world != null ? client.world.getTime() : 0L) + tickDelta;

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();

        // Mana orb icon to the left of the bar (pulses while channelling).
        float orbPulse = channeling ? 0.8f + 0.2f * MathHelper.sin(time * 0.3f) : 1.0f;
        RenderSystem.setShaderColor(orbPulse, orbPulse, 1.0f, 1.0f);
        context.drawTexture(ORB, x - 20, y - 3, 0f, 0f, 16, 16, 16, 16);
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);

        // Frame / dark track.
        context.drawTexture(FRAME, x, y, 0f, 0f, FRAME_W, FRAME_H, FRAME_W, FRAME_H);

        // Fill, clipped to the current fraction. Channelling brightens it; otherwise it's slightly muted.
        if (fillPx > 0) {
            if (channeling) {
                float pulse = 0.9f + 0.1f * MathHelper.sin(time * 0.3f);
                RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, pulse);
            } else {
                // Slightly muted when idle so channelling reads as a clear brighten.
                RenderSystem.setShaderColor(0.82f, 0.88f, 1.0f, 1.0f);
            }
            context.drawTexture(FILL, x + 2, y + 2, fillPx, INNER_H, 0f, 0f, fillPx, INNER_H, INNER_W, INNER_H);
            RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
        }

        RenderSystem.disableBlend();

        // Numeric label "current/threshold" centred over the bar (compacted for large values).
        TextRenderer tr = client.textRenderer;
        String label = compact((long) Math.floor(current)) + "/" + compact(threshold);
        int textX = x + 2 + (INNER_W - tr.getWidth(label)) / 2;
        int textY = y + 2 + (INNER_H - tr.fontHeight) / 2 + 1;
        context.drawText(tr, label, textX, textY, 0xFFFFFFFF, true);

        // "Mana" caption above the bar — brighter while channelling.
        context.drawText(tr, "Mana", x, y - 11, channeling ? 0xFF77FFFF : 0xFFAADDFF, true);
    }

    /** Compacts large mana values, e.g. 10000000000 -> "10B". */
    private static String compact(long value) {
        if (value >= 1_000_000_000L) {
            return String.format("%.1fB", value / 1_000_000_000.0);
        }
        if (value >= 1_000_000L) {
            return String.format("%.1fM", value / 1_000_000.0);
        }
        if (value >= 10_000L) {
            return String.format("%.1fK", value / 1_000.0);
        }
        return Long.toString(value);
    }
}

