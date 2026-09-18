package com.betterwhips.client;

import com.betterwhips.client.LightningWhipVfx.Style;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import java.util.ArrayList;
import java.util.List;

final class LightningArcMesh {
    private LightningArcMesh() {}
    static void emit(PoseStack stack, VertexConsumer out, Vec3[] points, int seed,
            float intensity, float thickness, Style style) {
        emit(stack, out, points, seed, intensity, thickness, style, null);
    }

    static void emit(PoseStack stack, VertexConsumer out, Vec3[] points, int seed,
            float intensity, float thickness, Style style, float[] pointVisibility) {
        if (points.length < 2 || intensity <= .003f) return;
        if (pointVisibility != null && pointVisibility.length != points.length) return;
        List<Vec3> refined = new ArrayList<>();
        List<Float> refinedVisibility = pointVisibility == null ? null : new ArrayList<>();
        for (int i = 0; i < points.length; i++) {
            Vec3 p = points[i];
            if (p == null || !Double.isFinite(p.x + p.y + p.z)) return;
            if (i > 0) {
                double distance = points[i-1].distanceTo(p);
                if (distance < 1e-7) continue;
                int steps = style == Style.LASH ? Math.min(4, (int)Math.ceil(distance / .095)) : 1;
                for (int j = 1; j < steps; j++) {
                    double t = (double)j / steps;
                    refined.add(points[i-1].lerp(p, t));
                    if (refinedVisibility != null) {
                        refinedVisibility.add((float)(pointVisibility[i-1] +
                                (pointVisibility[i] - pointVisibility[i-1]) * t));
                    }
                }
            }
            refined.add(p);
            if (refinedVisibility != null) refinedVisibility.add(pointVisibility[i]);
        }
        if (refined.size() < 2) return;
        Matrix4f matrix = stack.last().pose();
        float scale = thickness * (float)Math.cbrt(Math.abs(matrix.determinant()));
        if (!Float.isFinite(scale) || scale < 1e-5f) return;
        int encodedScale = Math.max(1, Math.min(127, Math.round(64f + 8f * (float)(Math.log(scale)/Math.log(2)))));
        Knot[] knots = new Knot[refined.size()];
        Vec3 frame = null;
        float u = 0f;
        for (int i = 0; i < knots.length; i++) {
            Vec3 p = refined.get(i);
            if (i > 0) u += (float)p.distanceTo(refined.get(i-1));
            Vec3 tangent = refined.get(Math.min(i+1,knots.length-1)).subtract(refined.get(Math.max(0,i-1))).normalize();
            if (tangent.lengthSqr() < .1) tangent = new Vec3(0,1,0);
            if (frame != null) frame = frame.subtract(tangent.scale(frame.dot(tangent)));
            if (frame == null || frame.lengthSqr() < 1e-6) {
                Vec3 reference = Math.abs(tangent.y) < .85 ? new Vec3(0,1,0) : new Vec3(1,0,0);
                frame = reference.subtract(tangent.scale(reference.dot(tangent)));
            }
            frame = frame.normalize();
            Vector3f pos = matrix.transformPosition(p.toVector3f());
            Vector3f t = matrix.transformDirection(tangent.toVector3f()).normalize();
            Vector3f n = matrix.transformDirection(frame.toVector3f()).normalize();
            n.sub(new Vector3f(t).mul(n.dot(t))).normalize();
            int[] oct = octEncode(n);
            float localVisibility = refinedVisibility == null ? 1.0f :
                    Math.max(0.0f, Math.min(1.0f, refinedVisibility.get(i)));
            int localAlpha = Math.max(1, Math.min(255, Math.round(intensity * localVisibility * 255f)));
            knots[i] = new Knot(pos.x,pos.y,pos.z,t.x,t.y,t.z,oct[0],oct[1],u,localAlpha);
        }
        if (u < .001f || u > 31f) return;
        int mode = style == Style.LASH ? 0 : 16;
        int sides = style == Style.LASH ? 8 : 6;
        int seedByte = Math.floorMod(seed, 251);
        tube(out,knots,0,knots.length-1,sides,seedByte,mode,encodedScale);
        if (style != Style.WRAP) {
            tube(out,knots,0,knots.length-1,6,seedByte,mode+1,encodedScale);
            if (style == Style.LASH) tube(out,knots,0,knots.length-1,6,seedByte,2,encodedScale);
        }
        if (style == Style.LASH && u > .6f) {
            for (int branch = 0; branch < 4; branch++) {
                float start = u * (.10f + branch*.21f + Math.floorMod(seed+branch*7,11)*.003f);
                float end = Math.min(u*.97f, start + Math.min(.95f,u*.18f));
                int first = 0;
                while (first < knots.length-2 && knots[first].u < start) first++;
                int last = first+1;
                while (last < knots.length-1 && knots[last].u < end) last++;
                tube(out,knots,first,last,6,seedByte,3+branch,encodedScale);
            }
        }
    }
    private static void tube(VertexConsumer out, Knot[] knots, int first, int last, int sides,
            int seed, int strand, int scale) {
        int start = Math.round(knots[first].u*1000f), end = Math.round(knots[last].u*1000f);
        for (int layer = 0; layer < 2; layer++) {
            int code = scale + layer*128;
            for (int i = first; i < last; i++) {
                for (int side = 0; side < sides; side++) {
                    float a = (float)(Math.PI*2*side/sides), b = (float)(Math.PI*2*(side+1)/sides);
                    vertex(out,knots[i],a,seed,strand,code,start,end);
                    vertex(out,knots[i],b,seed,strand,code,start,end);
                    vertex(out,knots[i+1],b,seed,strand,code,start,end);
                    vertex(out,knots[i+1],a,seed,strand,code,start,end);
                }
            }

            for (int cap : new int[]{first,last}) {
                for (int side = 0; side < sides; side++) {
                    vertex(out,knots[cap],(float)(Math.PI*2*side/sides),seed,strand,code,start,end);
                    vertex(out,knots[cap],-1f,seed,strand,code,start,end);
                    vertex(out,knots[cap],-1f,seed,strand,code,start,end);
                    vertex(out,knots[cap],(float)(Math.PI*2*(side+1)/sides),seed,strand,code,start,end);
                }
            }
        }
    }
    private static int[] octEncode(Vector3f n) {
        float inverse = 1f/(Math.abs(n.x)+Math.abs(n.y)+Math.abs(n.z));
        float x=n.x*inverse,y=n.y*inverse;
        if (n.z < 0f) {
            float oldX=x;
            x=(1f-Math.abs(y))*(oldX>=0?1:-1);
            y=(1f-Math.abs(oldX))*(y>=0?1:-1);
        }
        return new int[]{Math.round(x*32767f),Math.round(y*32767f)};
    }
    private static void vertex(VertexConsumer out, Knot p, float angle, int seed, int strand,
            int scaleAndLayer, int start, int end) {

        out.addVertex(p.x,p.y,p.z).setColor(seed,strand,scaleAndLayer,p.alpha)
            .setUv(p.u,angle).setUv1(p.nx,p.ny).setUv2(start,end).setNormal(p.tx,p.ty,p.tz);
    }
    private record Knot(float x,float y,float z,float tx,float ty,float tz,int nx,int ny,float u,int alpha) {}
}
