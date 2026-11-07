package com.example.circlemage.client;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.item.ItemStackRenderState;
public final class BookRenderState extends EntityRenderState {
    final ItemStackRenderState item = new ItemStackRenderState();
    float scale, spinSpeed, bob;
}
