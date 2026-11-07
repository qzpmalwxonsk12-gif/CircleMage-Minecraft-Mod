package com.example.circlemage.client;

import com.example.circlemage.network.ModNetworking;
import com.example.circlemage.spell.Law;
import com.example.circlemage.client.ClientPackets;
import net.fabricmc.fabric.api.networking.v1.FriendlyByteBufs;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;

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
        super(Component.literal("Laws"));
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
            Button button = Button.builder(
                            Component.literal(law.displayName + (law != Law.NONE && !unlocked ? " (locked)" : "")),
                            b -> selectLaw(law))
                    .bounds(x, y, BTN_W, BTN_H)
                    .build();
            button.active = unlocked;
            this.addRenderableWidget(button);
        }

        // Open the Storage law's extra inventory.
        this.addRenderableWidget(Button.builder(Component.literal("Open Storage"), b -> {
            ClientPackets.send(ModNetworking.OPEN_STORAGE, FriendlyByteBufs.empty());
            this.onClose();
        }).bounds(this.width / 2 - 60, startY + rows * (BTN_H + GAP) + 10, 120, BTN_H).build());

        int footerY = startY + rows * (BTN_H + GAP) + 10 + BTN_H + GAP;
        this.addRenderableWidget(Button.builder(Component.literal("<< Magic"),
                        b -> net.minecraft.client.Minecraft.getInstance().gui.setScreen(new MagicScreen()))
                .bounds(this.width / 2 - 122, footerY, 120, BTN_H).build());
        this.addRenderableWidget(Button.builder(Component.literal("Done"), b -> this.onClose())
                .bounds(this.width / 2 + 2, footerY, 120, BTN_H).build());
    }

    private void selectLaw(Law law) {
        ClientManaState.selectedLaw = law.ordinal();
        FriendlyByteBuf buf = FriendlyByteBufs.create();
        buf.writeInt(law.ordinal());
        ClientPackets.send(ModNetworking.SELECT_LAW, buf);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, width, height, 0xA0100D18);
        super.extractRenderState(context, mouseX, mouseY, delta);
        String title = "Choose a Law";
        context.centeredText(this.font, Component.literal(title).withStyle(ChatFormatting.GOLD),
                this.width / 2, 24, 0xFFFFFFFF);
        String active = "Active: " + Law.byId(ClientManaState.selectedLaw).displayName
                + (ClientManaState.circle >= 9 ? "" : "  (usable at the 9th circle)");
        context.centeredText(this.font, Component.literal(active).withStyle(ChatFormatting.LIGHT_PURPLE),
                this.width / 2, 38, 0xFFFFFFFF);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
