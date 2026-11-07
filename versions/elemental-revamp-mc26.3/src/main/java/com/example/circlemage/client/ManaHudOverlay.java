package com.example.circlemage.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;

/**
 * Renders the mana bar at the bottom of the screen, to the right of the hotbar, using the custom
 * runic frame / orb textures. The fill brightens and pulses while channelling.
 */
public final class ManaHudOverlay {

    private static final Identifier FRAME = Identifier.fromNamespaceAndPath("circlemage", "textures/gui/mana_frame.png");
    private static final Identifier FILL = Identifier.fromNamespaceAndPath("circlemage", "textures/gui/mana_fill.png");
    private static final Identifier ORB = Identifier.fromNamespaceAndPath("circlemage", "textures/gui/mana_orb.png");

    private static final int FRAME_W = 104;
    private static final int FRAME_H = 14;
    private static final int INNER_W = 100; // fillable track width
    private static final int INNER_H = 10;

    private ManaHudOverlay() {
    }

    public static void render(GuiGraphicsExtractor context, float tickDelta) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.gui.hud.isHidden()) {
            return;
        }

        int screenWidth = client.getWindow().getGuiScaledWidth();
        int screenHeight = client.getWindow().getGuiScaledHeight();

        // Frame just to the right of the centred 182px hotbar, kept on-screen on narrow windows.
        int x = screenWidth / 2 + 95;
        boolean narrow=x+FRAME_W+4>screenWidth;
        if (x + FRAME_W + 4 > screenWidth) {
            x = screenWidth - FRAME_W - 4;
        }
        int y = screenHeight - (narrow ? 72 : 20);

        double current = ClientManaState.currentMana;
        long threshold = Math.max(1L, ClientManaState.threshold);
        double fraction = Mth.clamp(current / threshold, 0.0, 1.0);
        int fillPx = (int) Math.round(INNER_W * fraction);

        boolean channeling = ClientManaState.channeling;
        float time = (client.level != null ? client.level.getGameTime() : 0L) + tickDelta;

        float orbPulse = channeling ? .8f + .2f * Mth.sin(time * .3f) : 1f;
        int orbColor=0xFF0000FF|((int)(orbPulse*255)<<16)|((int)(orbPulse*255)<<8);
        context.blit(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED, ORB,x-20,y-3,0f,0f,16,16,16,16,orbColor);
        context.blit(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED, FRAME,x,y,0f,0f,FRAME_W,FRAME_H,FRAME_W,FRAME_H);
        if(fillPx>0) {
            int color=channeling ? ((int)((.9f+.1f*Mth.sin(time*.3f))*255)<<24)|0xFFFFFF : 0xFFD1E0FF;
            context.blit(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED, FILL,x+2,y+2,0f,0f,fillPx,INNER_H,INNER_W,INNER_H,color);
        }

        // Numeric label "current/threshold" centred over the bar (compacted for large values).
        Font tr = client.font;
        String label = compact((long) Math.floor(current)) + "/" + compact(threshold);
        int textX = x + 2 + (INNER_W - tr.width(label)) / 2;
        int textY = y + 2 + (INNER_H - tr.lineHeight) / 2 + 1;
        context.text(tr, label, textX, textY, 0xFFFFFFFF, true);

        // "Mana" caption above the bar — brighter while channelling.
        context.text(tr, "Mana", x, y - 11, channeling ? 0xFF77FFFF : 0xFFAADDFF, true);
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
