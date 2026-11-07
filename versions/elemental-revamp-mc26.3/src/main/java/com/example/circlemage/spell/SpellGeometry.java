package com.example.circlemage.spell;
import net.minecraft.world.phys.Vec3;
public final class SpellGeometry {
    private SpellGeometry() {}
    public static boolean segmentHitsSphere(Vec3 start, Vec3 end, Vec3 center, double radius) {
        Vec3 delta=end.subtract(start);
        double t=delta.lengthSqr()<1e-10?0:Math.max(0,Math.min(1,center.subtract(start).dot(delta)/delta.lengthSqr()));
        return start.add(delta.scale(t)).distanceToSqr(center)<=radius*radius;
    }
}
