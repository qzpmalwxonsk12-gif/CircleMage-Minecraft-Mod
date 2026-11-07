package com.example.circlemage.client;
import com.example.circlemage.entity.ArcaneEffectEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import com.example.circlemage.client.MeshBuffers;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Blocks;

public class ArcaneEffectRenderer extends ProceduralEntityRenderer<ArcaneEffectEntity>{
    public ArcaneEffectRenderer(EntityRendererProvider.Context context){super(context);}
    @Override public boolean shouldRender(ArcaneEffectEntity e,Frustum frustum,double x,double y,double z){
        double radius=e.kind()==ArcaneEffectEntity.STEAM?6:e.kind()==ArcaneEffectEntity.DOME?3:2.5;
        return e.shouldRenderAtSqrDistance(x*x+y*y+z*z)&&frustum.isVisible(e.getBoundingBox().inflate(radius));
    }
    @Override public void render(ArcaneEffectEntity e,float yaw,float delta,PoseStack m,MeshBuffers b,int light){
        m.pushPose();float t=e.elapsed()+delta,rise=Math.min(1,t/8f);
        switch(e.kind()){
            case ArcaneEffectEntity.SPIKE -> SpellMeshes.cone(b.getBuffer(RenderTypes.entityCutout(SpellMeshes.STONE)),m.last(),.8f,2.4f*rise,0xC3B49A,1,light);
            case ArcaneEffectEntity.FIST -> {
                m.translate(-.6,-1.3+rise*1.9,-.6);m.scale(1.2f,1.2f,1.2f);
                SpellMeshes.cube(m,b,light);
                m.pushPose();m.translate(.3,-.5,.3);m.scale(.4f,.6f,.4f);SpellMeshes.cube(m,b,light);m.popPose();
                for(int i=0;i<4;i++){m.pushPose();m.translate(.03+i*.24,.9,-.05);m.scale(.23f,.5f,.55f);SpellMeshes.cube(m,b,light);m.popPose();}
                m.pushPose();m.translate(.92,.3,.25);m.rotate(Axis.ZP.rotationDegrees(-30));m.scale(.35f,.6f,.45f);SpellMeshes.cube(m,b,light);m.popPose();
            }
            case ArcaneEffectEntity.DOME -> SpellMeshes.sphere(b.getBuffer(RenderTypes.entityTranslucent(SpellMeshes.STONE)),m.last(),3*rise,true,0xB8A68A,.67f,light);
            case ArcaneEffectEntity.BUBBLE -> {m.scale(1,1.15f,1);m.rotate(Axis.YP.rotationDegrees(t*2));SpellMeshes.sphere(b.getBuffer(RenderTypes.entityTranslucent(SpellMeshes.WHITE)),m.last(),.55f,false,0x63D5FC,.27f,15728880);}
            case ArcaneEffectEntity.VINES -> {
                for(int i=0;i<6;i++){m.pushPose();m.rotate(Axis.YP.rotationDegrees(i*60+t*.4f));m.translate(.5,0,0);SpellMeshes.cone(b.getBuffer(RenderTypes.entityCutout(SpellMeshes.VINE)),m.last(),.23f,1.7f*rise,0x5C994F,1,light);m.popPose();}
            }
            case ArcaneEffectEntity.STEAM -> {
                if(t<20){SpellMeshes.sphere(b.getBuffer(RenderTypes.entityTranslucent(SpellMeshes.WHITE)),m.last(),Math.min(2,t*.12f),false,0x62C9EE,.45f,15728880);
                    for(int i=0;i<8;i++){m.pushPose();double a=i*Math.PI/4+t*.12;m.translate(Math.cos(a)*2,Math.sin(t*.2+i)*.6,Math.sin(a)*2);SpellMeshes.sphere(b.getBuffer(RenderTypes.entityTranslucent(SpellMeshes.WHITE)),m.last(),.25f,false,0xFF9B49,.75f,15728880);m.popPose();}
                }else{float alpha=Math.min(.10f,(620-t)/200f);SpellMeshes.sphere(b.getBuffer(RenderTypes.entityTranslucent(SpellMeshes.WHITE)),m.last(),6,false,0xDCEAF1,Math.max(0,alpha),light);}
            }
        }
        m.popPose();super.render(e,yaw,delta,m,b,light);
    }
    @Override public Identifier getTexture(ArcaneEffectEntity e){return SpellMeshes.WHITE;}
}
