package com.example.circlemage.client;
import com.mojang.blaze3d.vertex.PoseStack;
import com.example.circlemage.client.MeshBuffers.Vertex;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;

/** Small procedural meshes using vanilla textures; no external art dependency. */
final class SpellMeshes {
    static final Identifier STONE=Identifier.fromNamespaceAndPath("minecraft","textures/block/stone.png");
    static final Identifier ICE=Identifier.fromNamespaceAndPath("minecraft","textures/block/packed_ice.png");
    static final Identifier WHITE=Identifier.fromNamespaceAndPath("minecraft","textures/block/white_concrete.png");
    static final Identifier VINE=Identifier.fromNamespaceAndPath("minecraft","textures/block/vine.png");
    private SpellMeshes(){}
    static void vertex(Vertex v,PoseStack.Pose m,float x,float y,float z,float u,float t,int rgb,float alpha,int light){
        vertex(v,m,x,y,z,u,t,rgb,alpha,light,0,1,0);
    }
    static void vertex(Vertex v,PoseStack.Pose m,float x,float y,float z,float u,float t,int rgb,float alpha,int light,float nx,float ny,float nz){
        v.vertex(m.pose(),x,y,z).color(((rgb>>16)&255)/255f,((rgb>>8)&255)/255f,(rgb&255)/255f,alpha).uv(u,t).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light).normal(m.normal(),nx,ny,nz).endVertex();
    }
    static void cone(Vertex v,PoseStack.Pose m,float radius,float height,int rgb,float alpha,int light){
        for(int i=0;i<4;i++){double a=i*Math.PI/2,b=(i+1)*Math.PI/2;
            double slope=radius/Math.max(.001,height),len=Math.sqrt(1+slope*slope);float nx=(float)(Math.cos((a+b)/2)/len),ny=(float)(slope/len),nz=(float)(Math.sin((a+b)/2)/len);
            vertex(v,m,(float)Math.cos(a)*radius,0,(float)Math.sin(a)*radius,0,1,rgb,alpha,light,nx,ny,nz);
            vertex(v,m,0,height,0,.5f,0,rgb,alpha,light,nx,ny,nz);vertex(v,m,0,height,0,.5f,0,rgb,alpha,light,nx,ny,nz);
            vertex(v,m,(float)Math.cos(b)*radius,0,(float)Math.sin(b)*radius,1,1,rgb,alpha,light,nx,ny,nz);
        }
    }
    static void sphere(Vertex v,PoseStack.Pose m,float radius,boolean hemisphere,int rgb,float alpha,int light){
        int rows=6,columns=16;
        for(int row=0;row<rows;row++)for(int col=0;col<columns;col++){
            double p0=row*Math.PI/rows*(hemisphere?.5:1),p1=(row+1)*Math.PI/rows*(hemisphere?.5:1);
            double a0=col*Math.PI*2/columns,a1=(col+1)*Math.PI*2/columns;
            double[] ps={p0,p1,p1,p0},as={a0,a0,a1,a1};
            for(int i=0;i<4;i++){float nx=(float)(Math.sin(ps[i])*Math.cos(as[i])),ny=(float)Math.cos(ps[i]),nz=(float)(Math.sin(ps[i])*Math.sin(as[i]));
                vertex(v,m,nx*radius,ny*radius,nz*radius,i<2?0:1,i%3==0?0:1,rgb,alpha,light,nx,ny,nz);}
        }
    }

    static void cube(PoseStack pose,MeshBuffers buffers,int light) {
        Vertex v=buffers.getBuffer(net.minecraft.client.renderer.rendertype.RenderTypes.entityCutout(STONE));
        float[][] points={{0,0,0},{1,0,0},{1,1,0},{0,1,0},{0,0,1},{1,0,1},{1,1,1},{0,1,1}};
        int[][] faces={{0,3,2,1},{4,5,6,7},{0,4,7,3},{1,2,6,5},{0,1,5,4},{3,7,6,2}};
        float[][] normals={{0,0,-1},{0,0,1},{-1,0,0},{1,0,0},{0,-1,0},{0,1,0}};
        for(int face=0;face<6;face++)for(int i=0;i<4;i++) {
            float[] p=points[faces[face][i]],n=normals[face];
            vertex(v,pose.last(),p[0],p[1],p[2],i==1||i==2?1:0,i>=2?0:1,0xFFFFFF,1,light,n[0],n[1],n[2]);
        }
    }
}
