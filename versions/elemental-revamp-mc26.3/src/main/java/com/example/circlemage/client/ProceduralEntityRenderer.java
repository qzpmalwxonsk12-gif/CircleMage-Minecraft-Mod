package com.example.circlemage.client;

import com.mojang.blaze3d.vertex.PoseStack;
import java.util.List;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.*;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;

/** Extracts existing procedural spell shapes into the 26.3 render-state pipeline. */
public abstract class ProceduralEntityRenderer<E extends Entity> extends EntityRenderer<E, ProceduralEntityRenderer.State> {
    public static class State extends EntityRenderState { List<MeshBuffers.Batch> meshes=List.of(); }
    protected ProceduralEntityRenderer(EntityRendererProvider.Context context) { super(context); }
    @Override public State createRenderState() { return new State(); }
    @Override public void extractRenderState(E entity, State state, float delta) {
        super.extractRenderState(entity,state,delta);
        MeshBuffers buffers = new MeshBuffers();
        render(entity, entity.getYRot(), delta, new PoseStack(), buffers, state.lightCoords);
        state.meshes=buffers.snapshot();
    }
    @Override public void submit(State state, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera) {
        MeshBuffers.submit(state.meshes,pose,collector);
        super.submit(state,pose,collector,camera);
    }
    public void render(E entity,float yaw,float delta,PoseStack pose,MeshBuffers buffers,int light) {}
    public abstract Identifier getTexture(E entity);
    public boolean shouldRender(E entity,Frustum frustum,double x,double y,double z) { return super.shouldRender(entity,frustum,x,y,z,0); }
    @Override public boolean shouldRender(E entity,Frustum frustum,double x,double y,double z,float delta) { return shouldRender(entity,frustum,x,y,z); }
}
