package com.example.circlemage.client;

import com.example.circlemage.block.ArcaneStationScreenHandler;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;

/** The Arcane Station's assembly screen: the four A/B/C/D slots over the player inventory. */
public class ArcaneStationScreen extends AbstractContainerScreen<ArcaneStationScreenHandler> {

    private static final Identifier TEXTURE = Identifier.fromNamespaceAndPath("circlemage", "textures/gui/arcane_station.png");

    public ArcaneStationScreen(ArcaneStationScreenHandler handler, Inventory inventory, Component title) {
        super(handler, inventory, title);
        this.inventoryLabelY = this.imageHeight - 94;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        super.extractBackground(context, mouseX, mouseY, delta);
        int x = (this.width - this.imageWidth) / 2;
        int y = (this.height - this.imageHeight) / 2;
        // Pass the real texture size — the short overload assumes a 256x256 sheet and stretched
        // this 176x166 texture out of proportion (the v2.1 UI bug).
        context.blit(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED,TEXTURE, x, y, 0f, 0f, this.imageWidth, this.imageHeight,
                this.imageWidth, this.imageHeight);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        super.extractRenderState(context, mouseX, mouseY, delta);
        this.extractTooltip(context, mouseX, mouseY);
    }
}
