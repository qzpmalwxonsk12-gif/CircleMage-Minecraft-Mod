package com.example.circlemage.client;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/**
 * The hardcore Law of Life screen. You've already been revived (10x mana); this just asks whether
 * you're going to give up — and the only answers are "No" and "No".
 */
public class GiveUpScreen extends Screen {

    public GiveUpScreen() {
        super(Text.literal("Are you going to give up?"));
    }

    @Override
    protected void init() {
        int y = this.height / 2;
        this.addDrawableChild(ButtonWidget.builder(Text.literal("No"), b -> this.close())
                .dimensions(this.width / 2 - 154, y, 150, 20).build());
        this.addDrawableChild(ButtonWidget.builder(Text.literal("No"), b -> this.close())
                .dimensions(this.width / 2 + 4, y, 150, 20).build());
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        this.renderBackground(context);
        context.drawCenteredTextWithShadow(this.textRenderer,
                Text.literal("Are you going to give up?").formatted(Formatting.RED, Formatting.BOLD),
                this.width / 2, this.height / 2 - 40, 0xFFFFFFFF);
        context.drawCenteredTextWithShadow(this.textRenderer,
                Text.literal("The Law of Life answers for you.").formatted(Formatting.GRAY),
                this.width / 2, this.height / 2 - 24, 0xFFFFFFFF);
        super.render(context, mouseX, mouseY, delta);
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return true;
    }
}
