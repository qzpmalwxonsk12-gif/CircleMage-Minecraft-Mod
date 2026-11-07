package com.example.circlemage.client;
import com.example.circlemage.entity.IceSpikeEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.example.circlemage.client.MeshBuffers;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
public class IceSpikeRenderer extends ProceduralEntityRenderer<IceSpikeEntity>{
    public IceSpikeRenderer(EntityRendererProvider.Context context){super(context);}
    @Override public void render(IceSpikeEntity e,float yaw,float delta,PoseStack matrices,MeshBuffers buffers,int light){
        matrices.pushPose();var d=e.getDeltaMovement().normalize();if(d.lengthSqr()>0)matrices.rotate(new Quaternionf().rotationTo(0,1,0,(float)d.x,(float)d.y,(float)d.z));matrices.translate(0,-.45,0);
        SpellMeshes.cone(buffers.getBuffer(RenderTypes.entityCutout(SpellMeshes.ICE)),matrices.last(),.13f,.9f,0xCCEFFF,1,light);
        matrices.popPose();super.render(e,yaw,delta,matrices,buffers,light);
    }
    @Override public Identifier getTexture(IceSpikeEntity e){return SpellMeshes.ICE;}
}
