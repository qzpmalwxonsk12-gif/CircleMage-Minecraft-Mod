package com.example.circlemage.client;
import com.example.circlemage.entity.TutorialBookEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.*;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;
import net.minecraft.world.item.*;
/** Extracts the floating book and its absorption animation before rendering. */
public class TutorialBookRenderer extends EntityRenderer<TutorialBookEntity, BookRenderState> {
    private final ItemModelResolver items;
    public TutorialBookRenderer(EntityRendererProvider.Context context) { super(context); items=context.getItemModelResolver(); }
    @Override public BookRenderState createRenderState() { return new BookRenderState(); }
    @Override public void extractRenderState(TutorialBookEntity entity, BookRenderState state, float delta) {
        super.extractRenderState(entity,state,delta);
        items.updateForNonLiving(state.item,new ItemStack(Items.ENCHANTED_BOOK),ItemDisplayContext.GROUND,entity);
        float progress=entity.getState()==1 ? Mth.clamp((entity.getAnim()+delta)/(TutorialBookEntity.RISE_TICKS+TutorialBookEntity.SHOOT_TICKS),0,1) : 0;
        state.scale=Mth.clamp(1-progress*progress,.05f,1);
        state.spinSpeed=3+22*progress;
        state.bob=entity.getState()==0 ? Mth.sin(state.ageInTicks*.12f)*.06f : 0;
    }
    @Override public void submit(BookRenderState state, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera) {
        pose.pushPose(); pose.translate(0,.4+state.bob,0); pose.scale(state.scale,state.scale,state.scale);
        pose.rotate(Axis.YP.rotationDegrees(state.ageInTicks*state.spinSpeed)); pose.rotate(Axis.XP.rotationDegrees(12));
        state.item.submit(pose,collector,state.lightCoords,OverlayTexture.NO_OVERLAY,state.outlineColor);
        pose.popPose(); super.submit(state,pose,collector,camera);
    }
}
