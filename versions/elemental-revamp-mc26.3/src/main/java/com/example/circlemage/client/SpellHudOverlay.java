package com.example.circlemage.client;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/**
 * The v2 spell-selector HUD, mirrored to the left of the hotbar (the mana bar sits on the right):
 * a row of spell icons with the selected one popped and outlined, client-estimated cooldowns as a
 * dark sweep over each icon, the selected spell's name above, and a charge bar while a spell is
 * held-to-charge.
 */
public final class SpellHudOverlay {

    private static final int ICON = 12;      // icon size in the row
    private static final int GAP = 2;
    private static final int SEL_ICON = 16;  // the selected icon renders larger

    private SpellHudOverlay() {
    }

    public static void render(GuiGraphicsExtractor context, float tickDelta) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.gui.hud.isHidden() || client.player.isSpectator()) {
            return;
        }

        int screenWidth = client.getWindow().getGuiScaledWidth();
        int screenHeight = client.getWindow().getGuiScaledHeight();

        // Only learned spells appear on the wheel (v2.1). Nothing learned -> no spell HUD.
        java.util.List<Integer> ids = SpellSelectorState.available();
        if (ids.isEmpty()) {
            return;
        }
        int selectedId = SpellSelectorState.selectedSpellId();
        int columns = Math.min(ids.size(), Math.max(1,(screenWidth/2-99+GAP)/(ICON+GAP)));
        int rows = (ids.size()+columns-1)/columns;
        int rowW = columns * (ICON + GAP) - GAP;
        // End the row just left of the centred 182px hotbar, mirroring the mana bar's offset.
        int x0 = screenWidth / 2 - 95 - rowW;
        if (x0 < 4) {
            x0 = 4;
        }
        int y = screenHeight - 18;


        for (int i = 0; i < ids.size(); i++) {
            int id = ids.get(i);
            int ix = x0 + (i%columns) * (ICON + GAP);
            int rowY = y - (rows-1-i/columns)*(SEL_ICON+GAP);
            boolean selected = id == selectedId;
            Identifier icon = SpellSelectorState.icon(id);

            int size = selected ? SEL_ICON : ICON;
            int dx = ix - (size - ICON) / 2;
            int dy = rowY - (size - ICON);

            context.blit(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED,icon,dx,dy,0f,0f,size,size,size,size,selected ? 0xFFFFFFFF : 0xE5BFBFCC);

            // Cooldown: a dark sweep rising from the bottom of the icon.
            float cd = SpellSelectorState.cooldownFraction(id);
            if (cd > 0f) {
                int hh = Math.round(size * cd);
                context.fill(dx, dy + size - hh, dx + size, dy + size, 0xAA101018);
            }
            if (selected) {
                context.outline(dx - 1, dy - 1, size + 2, size + 2, 0xFFB490FF);
            }
        }


        // Selected spell name above the row (brightens while charging).
        Font tr = client.font;
        boolean priming = ModKeybinds.isPriming();
        int nameSpellId = priming ? ModKeybinds.primingSpellId() : selectedId;
        String name = SpellSelectorState.displayName(nameSpellId);
        if(tr.width(name)>rowW)name=tr.plainSubstrByWidth(name,Math.max(8,rowW-9))+"...";
        Component label = Component.literal(name).withStyle(priming ? ChatFormatting.GOLD : ChatFormatting.LIGHT_PURPLE);
        int textX = x0 + (rowW - tr.width(label)) / 2;
        int topY=y-(rows-1)*(SEL_ICON+GAP);
        context.text(tr, label, textX, topY - 15, 0xFFFFFFFF, true);

        // Charge bar while holding-to-charge.
        if (priming) {
            float p = ModKeybinds.primeProgress();
            int bw = rowW;
            int by = topY - 20;
            context.fill(x0 - 1, by - 1, x0 + bw + 1, by + 3, 0xA0000000);
            int fill = Math.round(bw * p);
            int color = p >= 1.0f ? 0xFFFFD766 : 0xFFB080FF;
            context.fill(x0, by, x0 + fill, by + 2, color);
        }
    }
}
