package com.betterwhips.client;

import com.betterwhips.network.LightningWhipNetwork.ArcTarget;
import com.betterwhips.network.LightningWhipNetwork.ChainHitPayload;
import com.betterwhips.network.LightningWhipNetwork.DirectWrapPayload;
import com.betterwhips.item.LightningWhipTimeStop;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class LightningWhipHitEffects {
    private static final List<ChainEffect> ACTIVE = new ArrayList<>();
    private static final List<ImpactEffect> IMPACTS = new ArrayList<>();
    private static final int MAX_ACTIVE = 48;
    private static final int MAX_IMPACTS = 24;
    private static final int IMPACT_TICKS = 9;
    private static final int MAX_TARGET_DRAWS = 96;
    private static final int WRAP_TICKS = 6;
    private static final int HOP_TICKS = 4;
    private static final float LINK_FADE_TICKS = 3f;
    private static final double MAX_RENDER_DISTANCE_SQR = 6400.0;

    private static final double MAX_ARC_LINK_DISTANCE = 12.5;
    private LightningWhipHitEffects() {}

    public static void spawnChain(ChainHitPayload payload) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) return;
        if (ACTIVE.size() >= MAX_ACTIVE) ACTIVE.removeFirst();
        TargetTrack[] targets = payload.targets().stream().map(TargetTrack::new).toArray(TargetTrack[]::new);
        long spawnTick = level.getGameTime();

        ACTIVE.add(new ChainEffect(level, spawnTick, payload.seed(), targets, false));
    }

    public static void spawnDirectWrap(DirectWrapPayload payload) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) return;
        if (ACTIVE.size() >= MAX_ACTIVE) ACTIVE.removeFirst();
        long spawnTick = level.getGameTime();
        ACTIVE.add(new ChainEffect(level, spawnTick, payload.seed(),
                new TargetTrack[]{new TargetTrack(payload.target())}, true));
        LightningWhipTimeStop.freezeNow(level, payload.target().uuid(), spawnTick);
    }

    public static void spawnBurst(double x,double y,double z,long seed) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) return;
        if (IMPACTS.size() >= MAX_IMPACTS) IMPACTS.removeFirst();
        IMPACTS.add(new ImpactEffect(level, level.getGameTime(), seed, new Vec3(x,y,z)));
    }
    private static float arrival(int index) {
        return index * HOP_TICKS;
    }
    public static void clientTick() {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) { clear(); return; }
        long now = level.getGameTime();
        ACTIVE.removeIf(effect -> effect.level != level || now-effect.spawnTick < 0
            || now-effect.spawnTick > arrival(effect.targets.length-1)+WRAP_TICKS+1);
        IMPACTS.removeIf(effect -> effect.level != level || now-effect.spawnTick < 0
            || now-effect.spawnTick > IMPACT_TICKS+1L);
    }
    public static void clear() { ACTIVE.clear(); IMPACTS.clear(); }
    public static void onClientTick(ClientTickEvent.Post event) { clientTick(); }
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) { clear(); }
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES || ShaderCompat.isIrisShadowPass()) return;
        render(event.getPoseStack(),event.getCamera().getPosition(),event.getPartialTick().getGameTimeDeltaPartialTick(false));
    }

    public static void render(PoseStack pose,Vec3 camera,float partialTick) {
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level == null || (ACTIVE.isEmpty() && IMPACTS.isEmpty()) || !LightningWhipVfx.ready()) return;
        MultiBufferSource.BufferSource buffers = minecraft.renderBuffers().bufferSource();
        VertexConsumer out = buffers.getBuffer(LightningWhipVfx.RENDER_TYPE);
        int remaining = MAX_TARGET_DRAWS;
        for (int e = ACTIVE.size()-1; e >= 0 && remaining > 0; e--) {
            ChainEffect effect = ACTIVE.get(e);
            if (effect.level != level) continue;

            float age = (float)(level.getGameTime()-effect.spawnTick)+partialTick;
            for (TargetTrack target : effect.targets) target.update(level,partialTick);
            for (int i=0;i<effect.targets.length && remaining>0;i++) {
                TargetTrack target = effect.targets[i];
                int seed = (int)(effect.seed^(effect.seed>>>32))+i*53;
                float wrapAge = age-arrival(i);
                if (effect.drawWraps && wrapAge>=0 && wrapAge<WRAP_TICKS
                        && target.center.distanceToSqr(camera)<=MAX_RENDER_DISTANCE_SQR) {
                    float fade = 1f-smooth(.36f,1f,wrapAge/WRAP_TICKS);
                    drawWrap(pose,out,camera,target,wrapAge,seed,fade);
                    remaining--;
                }
                if (i==0) continue;
                TargetTrack from = effect.targets[i-1];
                float linkAge = age-(i-1)*HOP_TICKS;
                if (linkAge<0 || linkAge>HOP_TICKS+LINK_FADE_TICKS) continue;
                if (from.center.distanceToSqr(camera)>MAX_RENDER_DISTANCE_SQR
                        && target.center.distanceToSqr(camera)>MAX_RENDER_DISTANCE_SQR) continue;
                float progress = Mth.clamp(linkAge/HOP_TICKS,0f,1f);
                float fade = 1f-smooth(1f,LINK_FADE_TICKS,linkAge-HOP_TICKS);
                drawLink(pose,out,camera,from,target,progress,seed+113,fade);
            }
        }
        for (int i = IMPACTS.size() - 1; i >= 0; --i) {
            ImpactEffect impact = IMPACTS.get(i);
            if (impact.level != level || impact.center.distanceToSqr(camera) > MAX_RENDER_DISTANCE_SQR) continue;
            float age = (float)(level.getGameTime() - impact.spawnTick) + partialTick;
            if (age >= 0.0F && age <= IMPACT_TICKS) {
                drawElectromagneticImpact(pose, out, camera, impact.center, age, impact.seed);
            }
        }
        buffers.endBatch(LightningWhipVfx.RENDER_TYPE);
    }

    private static void drawElectromagneticImpact(PoseStack pose, VertexConsumer out, Vec3 camera,
            Vec3 center, float age, long seed) {
        float life = Mth.clamp(age / IMPACT_TICKS, 0.0F, 1.0F);
        float pop = 1.0F - (1.0F-life)*(1.0F-life)*(1.0F-life);
        float fade = 1.0F - smooth(.38F, 1.0F, life);
        if (fade <= .01F) return;

        ImpactBasis basis = impactBasis(center, camera, seed);
        int baseSeed = (int)(seed ^ (seed >>> 32));
        double radius = .16 + .72 * pop;

        for (int ring = 0; ring < 2; ++ring) {
            double phase = randomUnit(seed, 11 + ring) * Math.PI * 2.0
                    + (ring == 0 ? 1.0 : -1.0) * life * .95;
            double ringRadius = radius * (ring == 0 ? 1.0 : .72);
            for (int piece = 0; piece < 3; ++piece) {
                double start = phase + piece * Math.PI * 2.0 / 3.0;
                Vec3[] arc = impactArc(center, camera, basis, ringRadius, start, start + .46 * Math.PI, 10);
                LightningWhipVfx.renderArc(pose, out, arc, baseSeed + ring*79 + piece*23,
                        fade * (ring == 0 ? .92F : .68F), ring == 0 ? .76F : .58F, LightningWhipVfx.Style.LINK);
            }
        }

        for (int ray = 0; ray < 7; ++ray) {
            double angle = randomUnit(seed, 101 + ray) * Math.PI * 2.0 + life * (ray%2==0 ? .24 : -.24);
            double inner = .035 + .035 * randomUnit(seed, 201 + ray);
            double outer = radius * (.78 + .52 * randomUnit(seed, 301 + ray));
            Vec3[] spoke = impactSpoke(center, camera, basis, angle, inner, outer, ray);
            LightningWhipVfx.renderArc(pose, out, spoke, baseSeed + 401 + ray*31,
                    fade * (.72F + .24F*(float)randomUnit(seed, 501 + ray)), .46F, LightningWhipVfx.Style.LINK);
        }

        for (int axis = 0; axis < 2; ++axis) {
            double angle = axis * Math.PI * .5 + randomUnit(seed, 701) * .35;
            Vec3[] core = impactSpoke(center, camera, basis, angle, -.11, .11, axis + 17);
            LightningWhipVfx.renderArc(pose, out, core, baseSeed + 911 + axis*43,
                    fade, .82F, LightningWhipVfx.Style.LINK);
        }
    }

    private static Vec3[] impactArc(Vec3 center, Vec3 camera, ImpactBasis basis, double radius,
            double start, double end, int count) {
        Vec3[] points = new Vec3[Math.max(2, count)];
        for (int i = 0; i < points.length; ++i) {
            double t = (double)i / (points.length - 1);
            double angle = start + (end-start)*t;
            Vec3 offset = basis.right.scale(Math.cos(angle)*radius)
                    .add(basis.up.scale(Math.sin(angle)*radius));
            points[i] = center.add(offset).subtract(camera);
        }
        return points;
    }

    private static Vec3[] impactSpoke(Vec3 center, Vec3 camera, ImpactBasis basis, double angle,
            double inner, double outer, int bendSeed) {
        Vec3 radial = basis.right.scale(Math.cos(angle)).add(basis.up.scale(Math.sin(angle)));
        Vec3 tangent = basis.right.scale(-Math.sin(angle)).add(basis.up.scale(Math.cos(angle)));
        Vec3[] points = new Vec3[5];
        for (int i = 0; i < points.length; ++i) {
            double t = (double)i/(points.length-1);
            double distance = inner + (outer-inner)*t;
            double bend = Math.sin(t*Math.PI) * ((bendSeed&1)==0 ? .055 : -.055);
            points[i] = center.add(radial.scale(distance)).add(tangent.scale(bend)).subtract(camera);
        }
        return points;
    }

    private static ImpactBasis impactBasis(Vec3 center, Vec3 camera, long seed) {
        Vec3 normal = camera.subtract(center);
        if (normal.lengthSqr() < 1.0E-8) normal = new Vec3(0,0,1);
        normal = normal.normalize();
        Vec3 reference = Math.abs(normal.y) < .92 ? new Vec3(0,1,0) : new Vec3(1,0,0);
        Vec3 right = reference.cross(normal).normalize();
        Vec3 up = normal.cross(right).normalize();
        double roll = randomUnit(seed, 3) * Math.PI * 2.0;
        Vec3 rolledRight = right.scale(Math.cos(roll)).add(up.scale(Math.sin(roll)));
        Vec3 rolledUp = up.scale(Math.cos(roll)).subtract(right.scale(Math.sin(roll)));
        return new ImpactBasis(rolledRight, rolledUp);
    }

    private static double randomUnit(long seed, long salt) {
        long z = seed + salt * 0x9E3779B97F4A7C15L;
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        z ^= z >>> 31;
        return (z >>> 11) * 0x1.0p-53;
    }

    private static void drawWrap(PoseStack pose,VertexConsumer out,Vec3 camera,TargetTrack target,
            float age,int seed,float fade) {
        for (int lane=0;lane<2;lane++) {
            Vec3[] arc=wrapPath(target,camera,age,seed,lane);
            LightningWhipVfx.renderArc(pose,out,arc,seed+lane*37,fade,.63f,LightningWhipVfx.Style.WRAP);
        }
    }

    private static Vec3[] wrapPath(TargetTrack target,Vec3 camera,float age,int seed,int lane) {
        double rx=Math.min(2.5,target.rx*1.35+.09), rz=Math.min(2.5,target.rz*1.35+.09);
        double h=Math.min(4,target.hy);
        double phase=Math.floorMod(seed,4096)*.013+age*.105;

        Vec3[] arc=new Vec3[43];
        for (int i=0;i<arc.length;i++) {
            double t=(double)i/(arc.length-1), angle=phase+lane*Math.PI+t*Math.PI*2*1.35;
            double c=Math.cos(angle), sn=Math.sin(angle);
            double x=Math.copySign(Math.pow(Math.abs(c),.65),c)*rx;
            double z=Math.copySign(Math.pow(Math.abs(sn),.65),sn)*rz;
            double y=(lane==0 ? 2*t-1 : 1-2*t)*h*.87+Math.sin(angle*1.2)*h*.05;
            arc[i]=target.center.add(x,y,z).subtract(camera);
        }
        return arc;
    }

    private static void drawLink(PoseStack pose,VertexConsumer out,Vec3 camera,TargetTrack from,
            TargetTrack to,float progress,int seed,float fade) {
        Vec3[] arc=linkPath(from,to,camera,progress,seed);
        LightningWhipVfx.renderArc(pose,out,arc,seed,fade,.86f,LightningWhipVfx.Style.LINK);
    }

    private static Vec3[] linkPath(TargetTrack from,TargetTrack to,Vec3 camera,float progress,int seed) {
        if (progress<=.005f) return new Vec3[0];
        Vec3 direction=to.center.subtract(from.center);
        double distance=direction.length();
        if (from.source.uuid().equals(to.source.uuid()) || distance < .05) {
            return selfHopPath(from, camera, progress, seed);
        }

        if (distance > MAX_ARC_LINK_DISTANCE) return new Vec3[0];
        direction=direction.scale(1.0/distance);
        Vec3 start=surface(from,direction), end=surface(to,direction.scale(-1));
        if (end.subtract(start).dot(direction)<=.02) { start=from.center; end=to.center; }
        int count=Math.max(4,Math.min(72,(int)Math.ceil(start.distanceTo(end)*progress/.10)+1));
        Vec3[] arc=new Vec3[count];
        for (int i=0;i<count;i++) {
            double t=(double)i/(count-1)*progress;
            arc[i]=start.lerp(end,t).add(0,Math.sin(Math.PI*t)*Math.min(.24,distance*.07),0).subtract(camera);
        }
        return arc;
    }

    private static Vec3[] selfHopPath(TargetTrack target, Vec3 camera, float progress, int seed) {
        int count = Math.max(4, Math.min(32, (int)Math.ceil(22 * progress) + 1));
        Vec3[] arc = new Vec3[count];

        double phase = Math.floorMod(seed, 4096) * .013;
        Vec3 side = new Vec3(Math.cos(phase), 0.0, Math.sin(phase));
        double bodyRadius = Math.max(.16, Math.max(target.rx, target.rz));
        Vec3 start = target.center
                .add(side.scale(bodyRadius + .06))
                .add(0.0, Math.min(target.hy * .18, .16), 0.0);
        Vec3 apex = target.center
                .add(side.scale(bodyRadius * .18))
                .add(0.0, target.hy + Math.max(.55, target.hy * .38), 0.0);
        Vec3 end = target.center
                .add(side.scale(-bodyRadius * .20))
                .add(0.0, Math.min(target.hy * .72, target.hy - .04), 0.0);

        for (int i = 0; i < count; ++i) {
            double t = (double)i / (count - 1) * progress;
            Vec3 point;
            if (t <= .5) {
                double u = t * 2.0;
                double eased = u * u * (3.0 - 2.0 * u);
                point = start.lerp(apex, eased);
            } else {
                double u = (t - .5) * 2.0;

                double eased = u * u;
                point = apex.lerp(end, eased);
            }
            arc[i] = point.subtract(camera);
        }
        return arc;
    }

    private static Vec3 surface(TargetTrack target,Vec3 direction) {
        double distance=Math.min((target.rx+.08)/Math.max(.0001,Math.abs(direction.x)),
            Math.min((target.hy+.08)/Math.max(.0001,Math.abs(direction.y)),
                     (target.rz+.08)/Math.max(.0001,Math.abs(direction.z))));
        return target.center.add(direction.scale(distance));
    }
    private static float smooth(float start,float end,float x) {
        float t=Mth.clamp((x-start)/(end-start),0f,1f);
        return t*t*(3f-2f*t);
    }
    private record ImpactEffect(ClientLevel level,long spawnTick,long seed,Vec3 center) {}
    private record ImpactBasis(Vec3 right,Vec3 up) {}
    private record ChainEffect(ClientLevel level,long spawnTick,long seed,TargetTrack[] targets,
                               boolean drawWraps) {}
    private static final class TargetTrack {
        final ArcTarget source;
        Vec3 center;
        double rx,hy,rz;
        TargetTrack(ArcTarget source) {
            this.source=source; center=source.center();
            rx=source.radiusX(); hy=source.halfHeight(); rz=source.radiusZ();
        }
        void update(ClientLevel level,float partialTick) {
            Entity entity=level.getEntity(source.entityId());
            if (entity == null || !entity.getUUID().equals(source.uuid()) || entity.isRemoved()) return;
            AABB box=entity.getBoundingBox();
            center=box.getCenter().add(entity.getPosition(partialTick).subtract(entity.position()));
            rx=Math.max(.01,box.getXsize()*.5);
            hy=Math.max(.01,box.getYsize()*.5);
            rz=Math.max(.01,box.getZsize()*.5);
        }
    }
}
