package com.example.circlemage.client;

import com.example.circlemage.spell.ArrayType;
import com.example.circlemage.spell.PassiveChant;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/**
 * The consolidated Magehood page — circle level, spell forms, passive chants, and magic arrays, all in
 * one clean screen (opened with the Magic Menu key) instead of overlapping the vanilla inventory.
 */
public class MagicScreen extends Screen {

    private static final int COL_W = 120;
    private static final int GAP = 18;
    private static final int BTN_H = 20;
    private static final int ROW = BTN_H + 3;

    public MagicScreen() {
        super(Text.literal("Magehood"));
    }

    @Override
    protected void init() {
        int totalW = COL_W * 3 + GAP * 2;
        int startX = (this.width - totalW) / 2;
        int colForms = startX;
        int colChants = startX + COL_W + GAP;
        int colArrays = startX + (COL_W + GAP) * 2;
        int top = 64;

        // --- Spell Forms ---
        int y = top;
        boolean spear = ClientManaState.circle >= 5 && !ClientManaState.revertSpear;
        ButtonWidget bolt = ButtonWidget.builder(
                        Text.literal("Bolt: " + (spear ? "Spear of Light" : "Magic Missile")),
                        b -> toggleAndRefresh(0))
                .dimensions(colForms, y, COL_W, BTN_H).build();
        bolt.active = ClientManaState.circle >= 5;
        this.addDrawableChild(bolt);
        y += ROW;

        boolean darkOrb = ClientManaState.circle >= 6 && !ClientManaState.revertDarkOrb;
        ButtonWidget slow = ButtonWidget.builder(
                        Text.literal("Slow: " + (darkOrb ? "Dark Orb" : "Area Slow")),
                        b -> toggleAndRefresh(2))
                .dimensions(colForms, y, COL_W, BTN_H).build();
        slow.active = ClientManaState.circle >= 6;
        this.addDrawableChild(slow);

        // --- Passive Chants ---
        if (ClientManaState.chantUnlocked) {
            int cy = top;
            for (PassiveChant chant : PassiveChant.values()) {
                boolean active = chant.ordinal() == ClientManaState.selectedChant;
                ButtonWidget button = ButtonWidget.builder(
                                Text.literal(chant.displayName + (active ? "  (active)" : "")),
                                b -> {
                                    CircleMageClient.selectChant(chant.ordinal());
                                    this.clearAndInit();
                                })
                        .dimensions(colChants, cy, COL_W, BTN_H).build();
                this.addDrawableChild(button);
                cy += ROW;
            }
        }

        // --- Magic Arrays ---
        if (ClientManaState.arrayUnlocked) {
            int ay = top;
            for (ArrayType type : ArrayType.values()) {
                boolean selected = type.ordinal() == ClientManaState.selectedArray;
                ButtonWidget button = ButtonWidget.builder(
                                Text.literal(type.displayName + (selected ? "  (selected)" : "")),
                                b -> {
                                    CircleMageClient.selectArray(type.ordinal());
                                    this.clearAndInit();
                                })
                        .dimensions(colArrays, ay, COL_W, BTN_H).build();
                this.addDrawableChild(button);
                ay += ROW;
            }
        }

        // --- Footer: Laws + Done ---
        int footerY = this.height - 32;
        if (ClientManaState.lawsUnlocked) {
            this.addDrawableChild(ButtonWidget.builder(Text.literal("Laws >>"),
                            b -> MinecraftClient.getInstance().setScreen(new LawSelectionScreen()))
                    .dimensions(this.width / 2 - COL_W - 4, footerY, COL_W, BTN_H).build());
            this.addDrawableChild(ButtonWidget.builder(Text.literal("Done"), b -> this.close())
                    .dimensions(this.width / 2 + 4, footerY, COL_W, BTN_H).build());
        } else {
            this.addDrawableChild(ButtonWidget.builder(Text.literal("Done"), b -> this.close())
                    .dimensions(this.width / 2 - COL_W / 2, footerY, COL_W, BTN_H).build());
        }
    }

    private void toggleAndRefresh(int baseSpellId) {
        CircleMageClient.toggleForm(baseSpellId);
        this.clearAndInit();
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        this.renderBackground(context);
        super.render(context, mouseX, mouseY, delta);

        // Title + circle/buff line.
        context.drawCenteredTextWithShadow(this.textRenderer, Text.literal("Magehood").formatted(Formatting.LIGHT_PURPLE),
                this.width / 2, 22, 0xFFFFFFFF);
        String level = ClientManaState.circle >= 1
                ? ("Circle " + ClientManaState.circle + " Mage"
                + (ClientManaState.unlockedBuffs > 0 ? "  (" + ClientManaState.unlockedBuffs + " realm buff"
                + (ClientManaState.unlockedBuffs == 1 ? "" : "s") + ")" : ""))
                : "Aspiring mage";
        context.drawCenteredTextWithShadow(this.textRenderer, Text.literal(level).formatted(Formatting.AQUA),
                this.width / 2, 36, 0xFFFFFFFF);

        // Column headers + locked notes.
        int totalW = COL_W * 3 + GAP * 2;
        int startX = (this.width - totalW) / 2;
        int colForms = startX;
        int colChants = startX + COL_W + GAP;
        int colArrays = startX + (COL_W + GAP) * 2;
        int headerY = 64 - 12;
        header(context, "Spell Forms", colForms, headerY);
        header(context, "Passive Chants", colChants, headerY);
        header(context, "Magic Arrays", colArrays, headerY);
        if (!ClientManaState.chantUnlocked) {
            locked(context, colChants, 64);
        }
        if (!ClientManaState.arrayUnlocked) {
            locked(context, colArrays, 64);
        }
    }

    private void header(DrawContext context, String text, int x, int y) {
        context.drawTextWithShadow(this.textRenderer, Text.literal(text).formatted(Formatting.GOLD), x, y, 0xFFFFFFFF);
    }

    private void locked(DrawContext context, int x, int y) {
        context.drawTextWithShadow(this.textRenderer, Text.literal("Locked").formatted(Formatting.DARK_RED), x, y, 0xFFFFFFFF);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
