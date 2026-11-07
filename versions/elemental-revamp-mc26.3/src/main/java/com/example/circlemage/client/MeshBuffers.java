package com.example.circlemage.client;

import com.mojang.blaze3d.vertex.PoseStack;
import java.util.*;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderType;
import org.joml.*;

/** CPU mesh extraction. Submitted render states contain immutable vertices, never live entities. */
public final class MeshBuffers {
    private final Map<RenderType, Vertex> batches = new LinkedHashMap<>();
    public Vertex getBuffer(RenderType type) { return batches.computeIfAbsent(type, ignored -> new Vertex()); }
    public List<Batch> snapshot() {
        return batches.entrySet().stream().map(e -> new Batch(e.getKey(), List.copyOf(e.getValue().vertices))).toList();
    }
    public static void submit(List<Batch> batches, PoseStack pose, SubmitNodeCollector collector) {
        for (Batch batch : batches) collector.submitCustomGeometry(pose, batch.type(), (matrix, out) -> {
            for (Point v : batch.vertices()) {
                out.addVertex(matrix, v.x(), v.y(), v.z()).setColor(v.color()).setUv(v.u(), v.v())
                    .setOverlay(v.overlay()).setLight(v.light()).setNormal(matrix, v.nx(), v.ny(), v.nz()).setLineWidth(2);
            }
        });
    }
    public record Batch(RenderType type, List<Point> vertices) {}
    public record Point(float x, float y, float z, int color, float u, float v, int overlay, int light, float nx, float ny, float nz) {}
    public static final class Vertex {
        private final List<Point> vertices = new ArrayList<>();
        private float x,y,z,u,v,nx,ny,nz;
        private int color=-1, overlay=655360, light=15728880;
        public Vertex vertex(Matrix4f matrix, float x, float y, float z) {
            Vector3f p = matrix.transformPosition(new Vector3f(x,y,z)); this.x=p.x;this.y=p.y;this.z=p.z; return this;
        }
        public Vertex color(int r,int g,int b,int a) { color=(a<<24)|(r<<16)|(g<<8)|b; return this; }
        public Vertex color(float r,float g,float b,float a) { return color((int)(r*255),(int)(g*255),(int)(b*255),(int)(a*255)); }
        public Vertex uv(float u,float v) {this.u=u;this.v=v;return this;}
        public Vertex overlayCoords(int overlay) {this.overlay=overlay;return this;}
        public Vertex uv2(int light) {this.light=light;return this;}
        public Vertex normal(Matrix3f matrix,float x,float y,float z) {
            Vector3f n=matrix.transform(new Vector3f(x,y,z)).normalize();nx=n.x;ny=n.y;nz=n.z;return this;
        }
        public void endVertex() {vertices.add(new Point(x,y,z,color,u,v,overlay,light,nx,ny,nz));}
    }
}
