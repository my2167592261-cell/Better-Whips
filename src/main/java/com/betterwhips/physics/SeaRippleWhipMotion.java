package com.betterwhips.physics;

import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class SeaRippleWhipMotion {
    public static final int IDLE=0, SWEEP_LEFT=1, SWEEP_RIGHT=2, BLADE=3;
    public static final int TAILS=5, SEGMENTS=35;
    private static final double LINK_LENGTH=4.375D/SEGMENTS;

    private static final int ROOT_STIFF_POINTS=10;
    private static final double ROOT_STIFF_RELEASE_EXPONENT=2.15D;

    private static final double GUIDE_MAX_BEND_ROOT_RADIANS=Math.toRadians(5.0D);
    private static final double GUIDE_MAX_BEND_BODY_RADIANS=Math.toRadians(17.0D);
    private static final double GUIDE_MAX_BEND_TIP_RADIANS=Math.toRadians(24.0D);

    private static final double TAIL_LANE=0.058D;

    private static final double SWEEP_FOLLOW_DELAY_PROGRESS=0.032D;

    private SeaRippleWhipMotion() {}

    public record Stroke(int kind,long startTick,int duration,Vec3 origin,Vec3 aim,int handSign,boolean empowered) {
        public Stroke {
            if(kind<0 || kind>BLADE || duration<1 || duration>100 || !finite(origin) || !finite(aim)
                    || aim.lengthSqr()<.25 || aim.lengthSqr()>4 || Math.abs(handSign)!=1)
                throw new IllegalArgumentException("Invalid sea whip stroke");
            aim=aim.normalize();
        }
        public static Stroke idle(Vec3 origin,Vec3 aim,int side,long now) {
            return new Stroke(IDLE,now,1,origin,aim,side,false);
        }
    }

    public static boolean finite(Vec3 p) { return p!=null && Double.isFinite(p.x+p.y+p.z); }

    public static Vec3 horizontal(Vec3 aim) {
        Vec3 flat=new Vec3(aim.x,0,aim.z);
        return flat.lengthSqr()<1e-8 ? new Vec3(0,0,1) : flat.normalize();
    }

    public static Vec3 right(Vec3 aim) {
        Vec3 f=horizontal(aim);
        return new Vec3(-f.z,0,f.x);
    }

    public static Vec3 grip(Vec3 feet,Vec3 aim,double eyeHeight,int side) {
        return feet.add(0,eyeHeight-.43,0).add(right(aim).scale(side*.36)).add(horizontal(aim).scale(.20));
    }

    public static float smooth(float a,float b,float value) {
        float t=Mth.clamp((value-a)/(b-a),0f,1f);
        return t*t*(3f-2f*t);
    }

    public static boolean canHit(Stroke s,double age) {
        double t=age/s.duration;

        return (s.kind==SWEEP_LEFT || s.kind==SWEEP_RIGHT || s.kind==BLADE) && t>=.16 && t<=.84;
    }

    public static double attackVisualAuthority(Stroke stroke,double age) {
        if(stroke==null || stroke.kind==IDLE || age<0.0D)return 0.0D;
        double progress=age/stroke.duration;
        double in=smooth(.08f,.24f,(float)progress);
        double out=1.0D-smooth(.70f,1.06f,(float)progress);
        return Mth.clamp(in*out,0.0D,1.0D);
    }

    public static int sweepLeaderTail(Stroke stroke) {
        if(stroke==null)return 2;
        if(stroke.kind==SWEEP_LEFT)return 0;
        if(stroke.kind==SWEEP_RIGHT)return TAILS-1;
        return 2;
    }

    private static int sweepFollowerRank(Stroke stroke,int tail) {
        int index=Math.max(0,Math.min(TAILS-1,tail));
        return Math.abs(index-sweepLeaderTail(stroke));
    }

    private static double sweepFollowerDelay(Stroke stroke,int tail) {
        return sweepFollowerRank(stroke,tail)*SWEEP_FOLLOW_DELAY_PROGRESS;
    }

    public static double attackDrive(Stroke stroke,double age,int tail) {
        if(stroke==null || stroke.kind==IDLE || age<0.0D || age>stroke.duration)return 0.0D;
        double progress=Mth.clamp(age/stroke.duration,0.0D,1.0D);
        double phase;
        if(stroke.kind==SWEEP_LEFT || stroke.kind==SWEEP_RIGHT)phase=sweepFollowerDelay(stroke,tail);
        else phase=stroke.kind==BLADE ? 0.0D : 0.0D;
        double local=Mth.clamp(progress-phase,0.0D,1.0D);
        double in=smooth(0.00f,.16f,(float)local);
        double out=1.0D-smooth(.78f,1.00f,(float)local);
        return Mth.clamp(in*out,0.0D,1.0D);
    }

    public static Vec3[] sample(Stroke stroke,double age,Vec3 root,int tail) {
        Vec3 forward=stroke.aim;
        Vec3 right=right(forward);
        Vec3 up=right.cross(forward).normalize();
        Vec3 flat=horizontal(forward);
        double progress=Mth.clamp(age/stroke.duration,0,1);

        double sampleAge=age;
        double sampleProgress=progress;
        if(stroke.kind==SWEEP_LEFT || stroke.kind==SWEEP_RIGHT) {
            double delayProgress=sweepFollowerDelay(stroke,tail);
            sampleAge=Math.max(0.0D,age-delayProgress*stroke.duration);
            sampleProgress=Mth.clamp(progress-delayProgress,0.0D,1.0D);
        }

        boolean idle=stroke.kind==IDLE || (stroke.kind==BLADE && age>stroke.duration);
        double lane=(tail-2)*TAIL_LANE;
        double reach=stroke.empowered ? SeaRippleWhipTuning.EMPOWERED_REACH : SeaRippleWhipTuning.BASE_REACH;
        Vec3[] out=new Vec3[SEGMENTS+1];

        for(int i=0;i<=SEGMENTS;i++) {
            double u=i/(double)SEGMENTS;
            Vec3 rest=flat.scale(1.9*u+.22*Math.sin(Math.PI*u))
                .add(right.scale(.64*Math.sin(u*Math.PI*2.0)*u))
                .add(0,-.78*Math.sin(Math.PI*u)+.035*Math.sin(sampleAge*.10-u*8)*u,0);
            Vec3 offset=rest;

            if(!idle && (stroke.kind==SWEEP_LEFT || stroke.kind==SWEEP_RIGHT)) {
                double direction=(stroke.kind==SWEEP_LEFT ? 1 : -1)*stroke.handSign;

                double localProgress=sampleProgress;
                double delayed=Mth.clamp((localProgress-.105*u-.075)/.60,0,1);
                double halfAngle=stroke.empowered?64:104;
                double angle=Math.toRadians(-halfAngle+2*halfAngle*smooth(0,1,(float)delayed))*direction;
                double extension=.94+.06*Math.sin(Math.PI*delayed);
                offset=forward.scale(Math.cos(angle)*reach*u*extension)
                    .add(right.scale(Math.sin(angle)*reach*u*extension))
                    .add(up.scale(Math.sin(angle)*.19*u*direction));

                double blend=smooth(0,.14f,(float)localProgress)*(1-smooth(.78f,1,(float)localProgress));
                offset=rest.lerp(offset,blend);
            } else if(!idle && stroke.kind==BLADE) {
                double local=progress;
                double thrust=smooth(.04f,.26f,(float)local)*(1-smooth(.78f,1,(float)local));
                offset=rest.lerp(bladeCurvePoint(forward,right,up,tail,u),thrust);
            }

            double spread=0.0D;
            double verticalSpread=0.0D;
            if(idle) {
                spread=lane*u;
                verticalSpread=lane*.22*u;
            } else if(stroke.kind==BLADE) {
                spread=0.0D;
                verticalSpread=0.0D;
            }
            out[i]=root.add(offset).add(right.scale(spread)).add(up.scale(verticalSpread));
        }

        out[0]=root;
        stiffenRoot(out);

        constrainGuideCurvature(out,stroke.kind!=BLADE);
        return out;
    }

    private static Vec3 bladeCurvePoint(Vec3 forward,Vec3 right,Vec3 up,int tail,double u) {
        double lane=(tail-2)*TAIL_LANE;
        double bias=(tail-2)/2.0D;
        double absBias=Math.abs(bias);
        Vec3 p0=Vec3.ZERO;
        Vec3 p1=forward.scale(SeaRippleWhipTuning.BLADE_REACH*0.26D)
            .add(right.scale(lane*(5.8D+1.6D*absBias)))
            .add(up.scale(0.68D+0.16D*absBias));
        Vec3 p2=forward.scale(SeaRippleWhipTuning.BLADE_REACH*0.80D)
            .add(right.scale(lane*(1.40D+0.60D*absBias)))
            .add(up.scale(0.42D+0.10D*absBias));
        Vec3 p3=forward.scale(SeaRippleWhipTuning.BLADE_REACH).add(up.scale(0.20D));
        return cubic(p0,p1,p2,p3,u);
    }

    private static Vec3 cubic(Vec3 a,Vec3 b,Vec3 c,Vec3 d,double t) {
        double it=1.0D-t;
        return a.scale(it*it*it)
            .add(b.scale(3.0D*it*it*t))
            .add(c.scale(3.0D*it*t*t))
            .add(d.scale(t*t*t));
    }

    private static void stiffenRoot(Vec3[] path) {
        if(path==null || path.length<3)return;
        Vec3 initial=path[1].subtract(path[0]);
        if(!finite(initial) || initial.lengthSqr()<1e-10)return;
        Vec3 axis=initial.normalize();
        Vec3[] original=path.clone();
        double arc=0.0D;
        int end=Math.min(ROOT_STIFF_POINTS,path.length-1);
        for(int i=1;i<=end;i++) {
            double segment=original[i].distanceTo(original[i-1]);
            if(!Double.isFinite(segment) || segment<1e-8)segment=LINK_LENGTH;
            arc+=segment;
            double u=i/(double)end;
            double release=Math.pow(smooth(0f,1f,(float)u),ROOT_STIFF_RELEASE_EXPONENT);
            Vec3 straight=path[0].add(axis.scale(arc));
            path[i]=straight.lerp(original[i],release);
        }
    }

    private static void constrainGuideCurvature(Vec3[] path,boolean authoredLengths) {
        if(path==null || path.length!=SEGMENTS+1)return;
        Vec3[] desired=path.clone();
        Vec3 first=desired[1].subtract(desired[0]);
        Vec3 previousDirection=unit(first,new Vec3(0,0,1));
        Vec3 root=desired[0];
        path[0]=root;
        for(int segment=0;segment<SEGMENTS;segment++) {
            Vec3 direct=desired[segment+1].subtract(path[segment]);
            Vec3 local=desired[segment+1].subtract(desired[segment]);
            Vec3 wanted=unit(direct,unit(local,previousDirection));
            double maxAngle=guideMaxBendRadians(segment);
            Vec3 limited=segment==0?wanted:limitTurn(previousDirection,wanted,maxAngle);
            double length=authoredLengths?LINK_LENGTH:local.length();
            if(!Double.isFinite(length) || length<1.0E-8D)length=LINK_LENGTH;
            path[segment+1]=path[segment].add(limited.scale(length));
            previousDirection=limited;
        }
    }

    private static double guideMaxBendRadians(int segment) {
        if(segment<=1)return GUIDE_MAX_BEND_ROOT_RADIANS;
        double u=segment/(double)Math.max(1,SEGMENTS-1);
        double rootRelease=smooth(0.03f,.30f,(float)u);
        double bodyToTip=smooth(.56f,1.0f,(float)u);
        double body=GUIDE_MAX_BEND_ROOT_RADIANS
                +(GUIDE_MAX_BEND_BODY_RADIANS-GUIDE_MAX_BEND_ROOT_RADIANS)*rootRelease;
        return body+(GUIDE_MAX_BEND_TIP_RADIANS-GUIDE_MAX_BEND_BODY_RADIANS)*bodyToTip;
    }

    private static Vec3 limitTurn(Vec3 previous,Vec3 wanted,double maxAngle) {
        Vec3 from=unit(previous,new Vec3(0,0,1));
        Vec3 to=unit(wanted,from);
        double dot=Mth.clamp(from.dot(to),-1.0D,1.0D);
        double angle=Math.acos(dot);
        if(!Double.isFinite(angle) || angle<=maxAngle)return to;
        Vec3 perpendicular=to.subtract(from.scale(dot));
        if(perpendicular.lengthSqr()<1.0E-10D) {
            Vec3 fallback=Math.abs(from.y)<.9D?new Vec3(0,1,0):new Vec3(1,0,0);
            perpendicular=fallback.subtract(from.scale(fallback.dot(from)));
        }
        perpendicular=unit(perpendicular,new Vec3(1,0,0));
        return unit(from.scale(Math.cos(maxAngle)).add(perpendicular.scale(Math.sin(maxAngle))),from);
    }

    private static Vec3 unit(Vec3 value,Vec3 fallback) {
        if(finite(value) && value.lengthSqr()>=1.0E-10D)return value.normalize();
        if(finite(fallback) && fallback.lengthSqr()>=1.0E-10D)return fallback.normalize();
        return new Vec3(0,0,1);
    }

    public static boolean segmentHits(AABB bounds,Vec3 a,Vec3 b,double radius) {
        AABB box=bounds.inflate(radius);
        return box.contains(a) || box.contains(b) || box.clip(a,b).isPresent();
    }

    public static Vec3[][] contactSegmentsForTail(Stroke stroke,double age,Vec3 previousRoot,Vec3 root,int tail) {
        int index=Math.max(0,Math.min(TAILS-1,tail));
        java.util.ArrayList<Vec3[]> segments=new java.util.ArrayList<>();
        Vec3[] previous=null;
        for(int step=0;step<=6;step++) {
            double alpha=step/6.0,at=age-1+alpha;
            if(!canHit(stroke,at))continue;
            Vec3[] curve=sample(stroke,at,previousRoot.lerp(root,alpha),index);
            for(int i=4;i<SEGMENTS;i++) {
                segments.add(new Vec3[]{curve[i],curve[i+1]});
                if(previous!=null)segments.add(new Vec3[]{previous[i],curve[i]});
            }
            previous=curve;
        }
        return segments.toArray(Vec3[][]::new);
    }

    public static Vec3[][] contactSegments(Stroke stroke,double age,Vec3 previousRoot,Vec3 root) {
        java.util.ArrayList<Vec3[]> segments=new java.util.ArrayList<>();
        Vec3[][] previous=new Vec3[TAILS][];
        for(int step=0;step<=6;step++) {
            double alpha=step/6.0,at=age-1+alpha;
            if(!canHit(stroke,at))continue;
            for(int tail=0;tail<TAILS;tail++) {
                Vec3[] curve=sample(stroke,at,previousRoot.lerp(root,alpha),tail);

                for(int i=4;i<SEGMENTS;i++) {
                    segments.add(new Vec3[]{curve[i],curve[i+1]});
                    if(previous[tail]!=null)segments.add(new Vec3[]{previous[tail][i],curve[i]});
                }
                previous[tail]=curve;
            }
        }
        return segments.toArray(Vec3[][]::new);
    }

    public static boolean hits(AABB bounds,Vec3[][] segments,double radius) {
        AABB box=bounds.inflate(radius);
        for(Vec3[] edge:segments)
            if(box.contains(edge[0]) || box.contains(edge[1]) || box.clip(edge[0],edge[1]).isPresent())return true;
        return false;
    }

    public static Vec3 bestContact(AABB bounds,Vec3[][] segments,double radius,Vec3 eye,Vec3 aim) {
        if(bounds==null || segments==null || eye==null || aim==null)return null;
        AABB box=bounds.inflate(radius);
        Vec3 view=aim.lengthSqr()>1.0E-10D?aim.normalize():new Vec3(0,0,1);
        Vec3 best=null;double bestDot=-Double.MAX_VALUE,bestDistance=Double.MAX_VALUE;
        for(Vec3[] edge:segments) {
            if(edge==null || edge.length<2 || edge[0]==null || edge[1]==null)continue;
            Vec3 a=edge[0],b=edge[1];
            if(box.contains(a)) {
                Vec3 delta=a.subtract(eye);double distance=delta.lengthSqr();
                double dot=distance>1.0E-10D?delta.dot(view)/Math.sqrt(distance):1.0D;
                if(Double.isFinite(dot) && (dot>bestDot || dot==bestDot && distance<bestDistance)) { best=a;bestDot=dot;bestDistance=distance; }
            }
            if(box.contains(b)) {
                Vec3 delta=b.subtract(eye);double distance=delta.lengthSqr();
                double dot=distance>1.0E-10D?delta.dot(view)/Math.sqrt(distance):1.0D;
                if(Double.isFinite(dot) && (dot>bestDot || dot==bestDot && distance<bestDistance)) { best=b;bestDot=dot;bestDistance=distance; }
            }
            java.util.Optional<Vec3> clipped=box.clip(a,b);
            if(clipped.isPresent()) {
                Vec3 point=clipped.get(),delta=point.subtract(eye);double distance=delta.lengthSqr();
                double dot=distance>1.0E-10D?delta.dot(view)/Math.sqrt(distance):1.0D;
                if(Double.isFinite(dot) && (dot>bestDot || dot==bestDot && distance<bestDistance)) { best=point;bestDot=dot;bestDistance=distance; }
            }
        }
        return best;
    }

    public static boolean sweptHit(AABB bounds,Stroke stroke,double age,Vec3 previousRoot,Vec3 root,double radius) {
        return hits(bounds,contactSegments(stroke,age,previousRoot,root),radius);
    }

    public static float[] armPose(Stroke s,double age) {
        if(age<0 || age>s.duration || s.kind==IDLE)return new float[]{-.38f,0f,-.05f*s.handSign};
        float t=(float)(age/s.duration), pulse=(float)Math.sin(Math.PI*t);
        float side=s.handSign;
        if(s.kind==BLADE)return new float[]{-.38f-pulse*.95f,side*pulse*.16f,-side*pulse*.18f};
        float sign=(s.kind==SWEEP_LEFT?1:-1)*side;
        float sweep=-1.25f+2.5f*smooth(.05f,.78f,t);
        return new float[]{-.38f-pulse*.64f,sign*sweep*pulse,sign*pulse*.32f};
    }
}
