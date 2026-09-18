package com.betterwhips.client;

import com.betterwhips.physics.SeaRippleWhipMotion;
import com.betterwhips.physics.SeaRippleWhipMotion.Stroke;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import java.util.function.UnaryOperator;

public final class SeaRippleWhipWaterMesh {
    private SeaRippleWhipWaterMesh() {}
    private static Vec3 unit(Vec3 v,Vec3 fallback) { return v.lengthSqr()<1e-10?fallback:v.normalize(); }
    private static double smooth01(double t) {
        t=Mth.clamp(t,0.0D,1.0D);
        return t*t*(3.0D-2.0D*t);
    }
    private static Vec3 rotateAround(Vec3 v,Vec3 axis,double angle) {
        double c=Math.cos(angle),s=Math.sin(angle);
        return v.scale(c).add(axis.cross(v).scale(s)).add(axis.scale(axis.dot(v)*(1.0D-c)));
    }

    private static Vec3 stableRibbonWidth(Vec3 tangent,Vec3 transported,Vec3 preferred,double u) {
        Vec3 previous=transported.subtract(tangent.scale(transported.dot(tangent)));
        Vec3 reference=preferred.subtract(tangent.scale(preferred.dot(tangent)));
        if(previous.lengthSqr()<1e-8)previous=reference;
        if(reference.lengthSqr()<1e-8)return unit(previous,tangent.cross(Math.abs(tangent.y)<.9?new Vec3(0,1,0):new Vec3(1,0,0)));
        previous=previous.normalize();reference=reference.normalize();
        if(previous.dot(reference)<0.0D)previous=previous.scale(-1.0D);
        double signed=Math.atan2(tangent.dot(reference.cross(previous)),Mth.clamp(reference.dot(previous),-1.0D,1.0D));
        double tipLock=smooth01((u-.58D)/.42D);
        double maxTwist=Math.toRadians(12.0D-8.0D*tipLock);
        return unit(rotateAround(reference,tangent,Mth.clamp(signed,-maxTwist,maxTwist)),reference);
    }
    private static void vertex(PoseStack p,VertexConsumer out,Vec3 v,float u,float w,float alpha,float hue) {
        out.addVertex(p.last().pose(),(float)v.x,(float)v.y,(float)v.z).setUv(u,w)
            .setColor(.28f+hue*.25f,.78f,1f,Mth.clamp(alpha,0,1));
    }
    private static void quad(PoseStack p,VertexConsumer out,Vec3 a,Vec3 b,Vec3 c,Vec3 d,
            float u0,float u1,float v0,float v1,float alpha,float hue) {
        vertex(p,out,a,u0,v0,alpha,hue);vertex(p,out,b,u1,v0,alpha,hue);
        vertex(p,out,c,u1,v1,alpha,hue);vertex(p,out,d,u0,v1,alpha,hue);
    }

    public static void ribbon(PoseStack p,VertexConsumer out,Vec3[] path,Vec3 preferredWidth,
            float width,float alpha,float phase) {
        if(path.length<2 || alpha<=.001f)return;
        Vec3[] previous=null;Vec3 x=preferredWidth;Vec3 preferred=unit(preferredWidth,new Vec3(1,0,0));float length=0;
        for(int i=0;i<path.length;i++) {
            Vec3 tangent=unit(path[Math.min(i+1,path.length-1)].subtract(path[Math.max(0,i-1)]),new Vec3(0,0,1));
            float u=i/(float)(path.length-1);
            x=stableRibbonWidth(tangent,x,preferred,u);
            Vec3 y=tangent.cross(x).normalize();
            float w=width*(.32f+.68f*(float)Math.sin(Math.PI*Math.pow(u,.72)))*(1-.78f*(float)Math.pow(u,10));
            Vec3[] ring=new Vec3[6];
            for(int k=0;k<6;k++) {
                double angle=k*Math.PI/3;
                ring[k]=path[i].add(x.scale(Math.cos(angle)*w)).add(y.scale(Math.sin(angle)*w*.32));
            }
            if(previous!=null) {
                float next=length+(float)path[i].distanceTo(path[i-1]);
                for(int k=0;k<6;k++)quad(p,out,previous[k],ring[k],ring[(k+1)%6],previous[(k+1)%6],
                    length+phase,next+phase,k/6f,(k+1)/6f,alpha,phase);
                length=next;
            }
            if(i==0 || i==path.length-1)for(int k=0;k<6;k++)
                quad(p,out,path[i],ring[k],ring[(k+1)%6],path[i],length+phase,length+phase+.05f,0,1,alpha,phase);
            previous=ring;
        }
    }

    public static void stroke(PoseStack p,VertexConsumer out,Stroke stroke,double age,Vec3 root,Vec3 anchorDelta,
            UnaryOperator<Vec3> space,float alpha) {
        if(stroke.kind()==SeaRippleWhipMotion.IDLE || age<1 || age>stroke.duration()+2)return;
        float active=(float)SeaRippleWhipMotion.attackVisualAuthority(stroke,age);
        if(active<.001f)return;
        Vec3 width=SeaRippleWhipMotion.right(stroke.aim());
        Vec3 origin=space.apply(root);Vec3 widthLocal=space.apply(root.add(width)).subtract(origin).normalize();
        boolean blade=stroke.kind()==SeaRippleWhipMotion.BLADE;
        if(blade)return;
        float broad=stroke.empowered()?.17f:.065f;
        Vec3[] now=anchored(stroke,age,root,anchorDelta);
        ribbon(p,out,transform(now,space),widthLocal,broad,alpha*active,.19f);
        int steps=stroke.empowered()?9:5;
        double history=stroke.duration()*(stroke.empowered()?.26:.13);
        Vec3[] previous=now;
        for(int j=1;j<=steps;j++) {
            double past=Math.max(stroke.duration()*.18,age-history*j/steps);
            if(past>=age)break;
            Vec3[] curve=anchored(stroke,past,root,anchorDelta);
            float fade=(1-j/(float)(steps+1));
            for(int i=5;i<SeaRippleWhipMotion.SEGMENTS;i++) {
                float u0=i/(float)SeaRippleWhipMotion.SEGMENTS,u1=(i+1)/(float)SeaRippleWhipMotion.SEGMENTS;
                float radial=SeaRippleWhipMotion.smooth(.12f,.34f,u0);
                quad(p,out,space.apply(previous[i]),space.apply(previous[i+1]),space.apply(curve[i+1]),space.apply(curve[i]),
                    u0*5,u1*5,(j-1)/(float)steps,j/(float)steps,alpha*active*fade*radial*(stroke.empowered()?.66f:.30f),.63f);
            }
            if(j==steps/2 || j==steps)ribbon(p,out,transform(curve,space),widthLocal,
                broad*.22f,alpha*active*fade*.55f,.73f);
            previous=curve;
        }
    }
    private static Vec3[] anchored(Stroke stroke,double age,Vec3 root,Vec3 delta) {
        return anchored(stroke,age,root,delta,SeaRippleWhipMotion.sweepLeaderTail(stroke));
    }

    private static Vec3[] anchored(Stroke stroke,double age,Vec3 root,Vec3 delta,int tail) {
        Vec3[] path=SeaRippleWhipMotion.sample(stroke,age,root,tail);
        if(stroke.kind()!=SeaRippleWhipMotion.BLADE) {
            for(int i=0;i<path.length;i++) { double u=i/(double)(path.length-1);path[i]=path[i].add(delta.scale((1-u)*(1-u))); }
        }
        return path;
    }
    public static Vec3[] transform(Vec3[] path,UnaryOperator<Vec3> transform) {
        Vec3[] result=new Vec3[path.length];for(int i=0;i<path.length;i++)result[i]=transform.apply(path[i]);return result;
    }

    public static void impact(PoseStack p,VertexConsumer out,Vec3 center,Vec3 aim,float age,
            float size,boolean empowered,long seed) {
        float t=Mth.clamp(age/10,0,1),fade=(1-t)*(1-t);
        Vec3 forward=unit(aim,new Vec3(0,0,1));
        Vec3 right=SeaRippleWhipMotion.right(forward);

        double randomRoll=((seed>>>11)&0x1FFFFFL)/(double)0x200000L*Math.PI*2.0D;
        right=unit(rotateAround(right,forward,randomRoll),right);
        Vec3 up=unit(right.cross(forward),new Vec3(0,1,0));
        float extent=size*(.60f+t*.95f)*(empowered?1.2f:1);
        for(int cut=0;cut<(empowered?2:1);cut++) {
            Vec3[] arc=new Vec3[25];
            for(int i=0;i<arc.length;i++) {
                double a=-2.1+i/24.0*3.7+cut*.7;
                arc[i]=center.add(right.scale(Math.cos(a)*extent))
                    .add(up.scale(Math.sin(a)*extent*.62)).add(forward.scale(Math.sin(a*1.4)*extent*.22));
            }
            ribbon(p,out,arc,forward,.10f*(1-t),fade,.22f+cut*.45f);
        }
        int count=empowered?16:9;
        for(int i=0;i<count;i++) {
            double random=((seed^(i*0x9e3779b97f4a7c15L))>>>40)/16777216.0;
            double angle=i*2.399963+random;
            Vec3 direction=right.scale(Math.cos(angle)).add(up.scale(Math.sin(angle))).add(forward.scale((random-.5)*.85)).normalize();
            Vec3 point=center.add(direction.scale(extent*(.38+t*(.8+random)))).add(0,-.65*t*t,0);
            droplet(p,out,point,direction,.025f+.028f*(float)random,fade,random);
        }
    }
    private static void droplet(PoseStack p,VertexConsumer out,Vec3 center,Vec3 direction,float radius,float alpha,double seed) {
        Vec3 x=unit(direction.cross(new Vec3(0,1,0)),new Vec3(1,0,0)),y=direction.cross(x).normalize();
        Vec3 tip=center.add(direction.scale(radius*3.1)),back=center.subtract(direction.scale(radius*1.3));
        Vec3[] ring={center.add(x.scale(radius)),center.add(y.scale(radius)),center.subtract(x.scale(radius)),center.subtract(y.scale(radius))};
        for(int i=0;i<4;i++)quad(p,out,tip,ring[i],back,ring[(i+1)%4],0,1,0,1,alpha,(float)seed);
    }
}
