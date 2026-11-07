package com.example.circlemage.client;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;

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

    public static void render(DrawContext context, float tickDelta) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.options.hudHidden || client.player.isSpectator()) {
            return;
        }

        int screenWidth = client.getWindow().getScaledWidth();
        int screenHeight = client.getWindow().getScaledHeight();

        // Only learned spells appear on the wheel (v2.1). Nothing learned -> no spell HUD.
        java.util.List<Integer> ids = SpellSelectorState.available();
        if (ids.isEmpty()) {
            return;
        }
        int selectedId = SpellSelectorState.selectedSpellId();
        int rowW = ids.size() * (ICON + GAP) - GAP;
        // End the row just left of the centred 182px hotbar, mirroring the mana bar's offset.
        int x0 = screenWidth / 2 - 95 - rowW;
        if (x0 < 4) {
            x0 = 4;
        }
        int y = screenHeight - 18;

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();

        for (int i = 0; i < ids.size(); i++) {
            int id = ids.get(i);
            int ix = x0 + i * (ICON + GAP);
            boolean selected = id == selectedId;
            Identifier icon = SpellSelectorState.icon(id);

            int size = selected ? SEL_ICON : ICON;
            int dx = ix - (size - ICON) / 2;
            int dy = y - (size - ICON); // grow upward so the row's baseline stays put

            if (!selected) {
                RenderSystem.setShaderColor(0.75f, 0.75f, 0.8f, 0.9f);
            }
            context.drawTexture(icon, dx, dy, 0f, 0f, size, size, size, size);
            RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);

            // Cooldown: a dark sweep rising from the bottom of the icon.
            float cd = SpellSelectorState.cooldownFraction(id);
            if (cd > 0f) {
                int hh = Math.round(size * cd);
                context.fill(dx, dy + size - hh, dx + size, dy + size, 0xAA101018);
            }
            if (selected) {
                context.drawBorder(dx - 1, dy - 1, size + 2, size + 2, 0xFFB490FF);
            }
        }

        RenderSystem.disableBlend();

        // Selected spell name above the row (brightens while charging).
        TextRenderer tr = client.textRenderer;
        boolean priming = ModKeybinds.isPriming();
        int nameSpellId = priming ? ModKeybinds.primingSpellId() : selectedId;
        String name = SpellSelectorState.displayName(nameSpellId);
        Text label = Text.literal(name).formatted(priming ? Formatting.GOLD : Formatting.LIGHT_PURPLE);
        int textX = x0 + (rowW - tr.getWidth(label)) / 2;
        context.drawText(tr, label, textX, y - 15, 0xFFFFFFFF, true);

        // Charge bar while holding-to-charge.
        if (priming) {
            float p = ModKeybinds.primeProgress();
            int bw = rowW;
            int by = y - 20;
            context.fill(x0 - 1, by - 1, x0 + bw + 1, by + 3, 0xA0000000);
            int fill = Math.round(bw * p);
            int color = p >= 1.0f ? 0xFFFFD766 : 0xFFB080FF;
            context.fill(x0, by, x0 + fill, by + 2, color);
        }
    }
}
