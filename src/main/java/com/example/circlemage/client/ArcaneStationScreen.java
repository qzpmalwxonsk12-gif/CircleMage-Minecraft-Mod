package com.example.circlemage.client;

import com.example.circlemage.block.ArcaneStationScreenHandler;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

/** The Arcane Station's assembly screen: the four A/B/C/D slots over the player inventory. */
public class ArcaneStationScreen extends HandledScreen<ArcaneStationScreenHandler> {

    private static final Identifier TEXTURE = new Identifier("circlemage", "textures/gui/arcane_station.png");

    public ArcaneStationScreen(ArcaneStationScreenHandler handler, PlayerInventory inventory, Text title) {
        super(handler, inventory, title);
        this.backgroundHeight = 166;
        this.playerInventoryTitleY = this.backgroundHeight - 94;
    }

    @Override
    protected void drawBackground(DrawContext context, float delta, int mouseX, int mouseY) {
        int x = (this.width - this.backgroundWidth) / 2;
        int y = (this.height - this.backgroundHeight) / 2;
        // Pass the real texture size — the short overload assumes a 256x256 sheet and stretched
        // this 176x166 texture out of proportion (the v2.1 UI bug).
        context.drawTexture(TEXTURE, x, y, 0f, 0f, this.backgroundWidth, this.backgroundHeight,
                this.backgroundWidth, this.backgroundHeight);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        this.renderBackground(context);
        super.render(context, mouseX, mouseY, delta);
        this.drawMouseoverTooltip(context, mouseX, mouseY);
    }
}
