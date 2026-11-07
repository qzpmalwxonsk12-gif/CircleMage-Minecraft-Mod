package com.example.circlemage.client;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * The hardcore Law of Life screen. You've already been revived (10x mana); this just asks whether
 * you're going to give up — and the only answers are "No" and "No".
 */
public class GiveUpScreen extends Screen {

    public GiveUpScreen() {
        super(Component.literal("Are you going to give up?"));
    }

    @Override
    protected void init() {
        int y = this.height / 2;
        this.addRenderableWidget(Button.builder(Component.literal("No"), b -> this.onClose())
                .bounds(this.width / 2 - 154, y, 150, 20).build());
        this.addRenderableWidget(Button.builder(Component.literal("No"), b -> this.onClose())
                .bounds(this.width / 2 + 4, y, 150, 20).build());
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, width, height, 0xA0100D18);
        context.centeredText(this.font,
                Component.literal("Are you going to give up?").withStyle(ChatFormatting.RED, ChatFormatting.BOLD),
                this.width / 2, this.height / 2 - 40, 0xFFFFFFFF);
        context.centeredText(this.font,
                Component.literal("The Law of Life answers for you.").withStyle(ChatFormatting.GRAY),
                this.width / 2, this.height / 2 - 24, 0xFFFFFFFF);
        super.extractRenderState(context, mouseX, mouseY, delta);
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return true;
    }
}
