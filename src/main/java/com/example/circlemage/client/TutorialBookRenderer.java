package com.example.circlemage.client;

import com.example.circlemage.entity.TutorialBookEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.item.ItemRenderer;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;

/**
 * Renders the intro tutorial book as a floating, slowly spinning enchanted book. While it's being
 * absorbed it spins faster and shrinks toward nothing (the item flies to the reader server-side).
 */
public class TutorialBookRenderer extends EntityRenderer<TutorialBookEntity> {

    private final ItemStack book = new ItemStack(Items.ENCHANTED_BOOK);

    public TutorialBookRenderer(EntityRendererFactory.Context context) {
        super(context);
    }

    @Override
    public void render(TutorialBookEntity entity, float yaw, float tickDelta, MatrixStack matrices,
                       VertexConsumerProvider vertexConsumers, int light) {
        float time = entity.age + tickDelta;

        matrices.push();

        float scale = 1.0f;
        float spinSpeed = 3.0f;
        if (entity.getState() == 1) {
            float anim = entity.getAnim() + tickDelta;
            float total = TutorialBookEntity.RISE_TICKS + TutorialBookEntity.SHOOT_TICKS;
            float progress = MathHelper.clamp(anim / total, 0f, 1f);
            spinSpeed = 3.0f + 22.0f * progress;                 // whirl faster as it launches
            scale = MathHelper.clamp(1.0f - progress * progress, 0.05f, 1.0f); // shrink away
        }

        // Idle bob (only meaningful while resting; absorb overrides position server-side).
        float bob = entity.getState() == 0 ? MathHelper.sin(time * 0.12f) * 0.06f : 0f;
        matrices.translate(0.0, 0.4 + bob, 0.0);
        matrices.scale(scale, scale, scale);
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(time * spinSpeed));
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(12.0f));

        ItemRenderer itemRenderer = MinecraftClient.getInstance().getItemRenderer();
        itemRenderer.renderItem(book, ModelTransformationMode.GROUND, light, OverlayTexture.DEFAULT_UV,
                matrices, vertexConsumers, entity.getWorld(), entity.getId());

        matrices.pop();
        super.render(entity, yaw, tickDelta, matrices, vertexConsumers, light);
    }

    @Override
    public Identifier getTexture(TutorialBookEntity entity) {
        return new Identifier("minecraft", "textures/item/enchanted_book.png");
    }
}
