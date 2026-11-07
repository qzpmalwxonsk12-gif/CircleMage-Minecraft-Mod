package com.example.circlemage.client;

import com.example.circlemage.network.ModNetworking;
import com.example.circlemage.spell.Law;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/**
 * The Laws selection "page" — a dedicated screen (opened from the inventory) where the mage picks the
 * one active Law, grouped into a grid. Has a button to open the Storage law's extra inventory.
 */
public class LawSelectionScreen extends Screen {

    private static final int COLS = 3;
    private static final int BTN_W = 116;
    private static final int BTN_H = 20;
    private static final int GAP = 4;

    public LawSelectionScreen() {
        super(Text.literal("Laws"));
    }

    @Override
    protected void init() {
        Law[] laws = Law.values();
        int rows = (laws.length + COLS - 1) / COLS;
        int gridW = COLS * BTN_W + (COLS - 1) * GAP;
        int startX = (this.width - gridW) / 2;
        int startY = this.height / 2 - (rows * (BTN_H + GAP)) / 2;

        for (int i = 0; i < laws.length; i++) {
            Law law = laws[i];
            int col = i % COLS;
            int row = i / COLS;
            int x = startX + col * (BTN_W + GAP);
            int y = startY + row * (BTN_H + GAP);
            boolean unlocked = ClientManaState.isLawUnlocked(law.ordinal());
            ButtonWidget button = ButtonWidget.builder(
                            Text.literal(law.displayName + (law != Law.NONE && !unlocked ? " (locked)" : "")),
                            b -> selectLaw(law))
                    .dimensions(x, y, BTN_W, BTN_H)
                    .build();
            button.active = unlocked;
            this.addDrawableChild(button);
        }

        // Open the Storage law's extra inventory.
        this.addDrawableChild(ButtonWidget.builder(Text.literal("Open Storage"), b -> {
            ClientPlayNetworking.send(ModNetworking.OPEN_STORAGE, PacketByteBufs.empty());
            this.close();
        }).dimensions(this.width / 2 - 60, startY + rows * (BTN_H + GAP) + 10, 120, BTN_H).build());

        int footerY = startY + rows * (BTN_H + GAP) + 10 + BTN_H + GAP;
        this.addDrawableChild(ButtonWidget.builder(Text.literal("<< Magic"),
                        b -> net.minecraft.client.MinecraftClient.getInstance().setScreen(new MagicScreen()))
                .dimensions(this.width / 2 - 122, footerY, 120, BTN_H).build());
        this.addDrawableChild(ButtonWidget.builder(Text.literal("Done"), b -> this.close())
                .dimensions(this.width / 2 + 2, footerY, 120, BTN_H).build());
    }

    private void selectLaw(Law law) {
        ClientManaState.selectedLaw = law.ordinal();
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeInt(law.ordinal());
        ClientPlayNetworking.send(ModNetworking.SELECT_LAW, buf);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        this.renderBackground(context);
        super.render(context, mouseX, mouseY, delta);
        String title = "Choose a Law";
        context.drawCenteredTextWithShadow(this.textRenderer, Text.literal(title).formatted(Formatting.GOLD),
                this.width / 2, 24, 0xFFFFFFFF);
        String active = "Active: " + Law.byId(ClientManaState.selectedLaw).displayName
                + (ClientManaState.circle >= 9 ? "" : "  (usable at the 9th circle)");
        context.drawCenteredTextWithShadow(this.textRenderer, Text.literal(active).formatted(Formatting.LIGHT_PURPLE),
                this.width / 2, 38, 0xFFFFFFFF);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
