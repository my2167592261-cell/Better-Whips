package com.betterwhips.client;

import com.betterwhips.network.SeaRippleWhipNetwork;
import com.betterwhips.physics.SeaRippleWhipMotion;
import com.betterwhips.physics.SeaRippleWhipMotion.Stroke;
import com.betterwhips.registry.ModItems;
import com.mojang.blaze3d.vertex.*;
import com.mojang.math.Axis;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.*;
import org.joml.*;
import java.util.*;
import java.lang.Math;
import java.util.function.UnaryOperator;

public final class SeaRippleWhipPhysics {
    private static final Map<Key,Socket> SOCKETS=new HashMap<>();
    private static final ThreadLocal<ArrayDeque<RenderContext>> CONTEXT=ThreadLocal.withInitial(ArrayDeque::new);
    private static final RenderType MODEL=RenderType.entityTranslucent(ResourceLocation.fromNamespaceAndPath(
        "better_whips","textures/item/untamed_sea_whip.png"));
    private static ClientLevel world;

    private static final double ATTACK_VFX_BODY_AUTHORITY = 1.0D;

    private static final double RIBBON_MAX_TWIST_BODY_RADIANS = Math.toRadians(11.0D);
    private static final double RIBBON_MAX_TWIST_TIP_RADIANS = Math.toRadians(3.5D);

    private static final Vec3 FIRST_PERSON_HANDLE_PIVOT = new Vec3(0.0D, 3.0D / 16.0D, 0.0D);
    private static final int FIRST_PERSON_VFX_TANGENT_SAMPLE = 5;
    private SeaRippleWhipPhysics() {}
    private static boolean holds(Player p,HumanoidArm arm) {
        return (arm==p.getMainArm()?p.getMainHandItem():p.getOffhandItem()).is(ModItems.UNTAMED_SEA_WHIP.get());
    }
    public static void onInteractionKey(InputEvent.InteractionKeyMappingTriggered event) {
        Minecraft mc=Minecraft.getInstance();Player p=mc.player;
        if(p==null || !p.getMainHandItem().is(ModItems.UNTAMED_SEA_WHIP.get()))return;
        if(event.isAttack() || event.isUseItem()) {
            event.setCanceled(true);event.setSwingHand(false);
            if(event.isAttack())SeaRippleWhipClientState.input(SeaRippleWhipNetwork.ATTACK);
            else if(event.getHand()==InteractionHand.MAIN_HAND)SeaRippleWhipClientState.input(SeaRippleWhipNetwork.BLADE);
        }
    }
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc=Minecraft.getInstance();SeaRippleWhipClientState.tick();SeaRippleWhipEffects.tick();
        if(mc.level!=world){SOCKETS.clear();SeaRippleWhipIdlePhysics.clear();world=mc.level;}
        if(mc.level==null || mc.isPaused())return;
        if(mc.options.keyAttack.isDown())SeaRippleWhipClientState.input(SeaRippleWhipNetwork.ATTACK);
        SOCKETS.entrySet().removeIf(e->mc.level.getGameTime()-e.getValue().tick>20);
        SeaRippleWhipIdlePhysics.prune(mc.level);
    }
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        SOCKETS.clear();SeaRippleWhipIdlePhysics.clear();CONTEXT.remove();world=null;SeaRippleWhipClientState.clear();SeaRippleWhipEffects.clear();
    }
    public static void pushRenderContext(LivingEntity holder,HumanoidArm arm,boolean firstPerson) {
        CONTEXT.get().push(new RenderContext(holder,arm,firstPerson));
    }
    public static void popRenderContext() {
        ArrayDeque<RenderContext> stack=CONTEXT.get();if(!stack.isEmpty())stack.pop();if(stack.isEmpty())CONTEXT.remove();
    }
    static RenderContext currentRenderContext() { return CONTEXT.get().peek(); }
    static void captureRenderedSocket(PoseStack pose,RenderContext context) {
        if(!(context.holder instanceof Player p) || !holds(p,context.arm))return;
        Camera camera=Minecraft.getInstance().gameRenderer.getMainCamera();
        Vec3[] roots=new Vec3[5],directions=new Vec3[5],widths=new Vec3[5];
        for(int tail=0;tail<5;tail++) {
            Vec3 root=localRoot(tail);
            roots[tail]=capture(pose,root,context.firstPerson,camera);
            directions[tail]=capture(pose,root.add(SeaRippleWhipGeometry.tailRootDirectionLocalX(tail),
                SeaRippleWhipGeometry.tailRootDirectionLocalY(tail),SeaRippleWhipGeometry.tailRootDirectionLocalZ(tail)),context.firstPerson,camera).subtract(roots[tail]).normalize();
            widths[tail]=capture(pose,root.add(SeaRippleWhipGeometry.tailWidthAxisLocalX(tail),
                SeaRippleWhipGeometry.tailWidthAxisLocalY(tail),SeaRippleWhipGeometry.tailWidthAxisLocalZ(tail)),context.firstPerson,camera).subtract(roots[tail]).normalize();
        }
        SOCKETS.put(new Key(p.getUUID(),context.arm),new Socket(roots,directions,widths,p.level().getGameTime(),context.firstPerson));
    }
    private static Vec3 capture(PoseStack pose,Vec3 local,boolean hand,Camera camera) {
        Vector3f v=pose.last().pose().transformPosition(local.toVector3f());Vec3 result=new Vec3(v.x,v.y,v.z);
        if(hand)result=WhipFirstPersonFov.removeFromCapturedWorldVector(result,camera);
        return result.add(camera.getPosition());
    }
    private static Vec3 localRoot(int tail) {
        return new Vec3(SeaRippleWhipGeometry.tailRootLocalX(tail),SeaRippleWhipGeometry.tailRootLocalY(tail),SeaRippleWhipGeometry.tailRootLocalZ(tail));
    }

    static void alignFirstPersonGripToAttackVfx(PoseStack pose,RenderContext context) {
        if(pose==null || context==null || !context.firstPerson || !(context.holder instanceof Player p))return;
        Camera camera=Minecraft.getInstance().gameRenderer.getMainCamera();
        if(!localFirstPerson(p,camera))return;

        Stroke stroke=SeaRippleWhipClientState.stroke(p,context.arm);
        float partial=Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false);
        double age=SeaRippleWhipClientState.age(p,stroke,partial);
        double authority=SeaRippleWhipMotion.attackVisualAuthority(stroke,age);
        if(authority<=1.0E-5D || stroke.kind()==SeaRippleWhipMotion.IDLE)return;

        int authorityTail=SeaRippleWhipMotion.sweepLeaderTail(stroke);
        Vec3[] guide=SeaRippleWhipMotion.sample(stroke,age,Vec3.ZERO,authorityTail);
        if(guide==null || guide.length<2)return;
        int tangentIndex=Math.min(FIRST_PERSON_VFX_TANGENT_SAMPLE,guide.length-1);
        Vec3 desiredTangentWorld=unitDirection(guide[tangentIndex].subtract(guide[0]),stroke.aim());
        Vec3 desiredWidthWorld=SeaRippleWhipMotion.right(stroke.aim());

        UnaryOperator<Vec3> toLocal=handSpace(pose,camera);
        if(toLocal==null)return;
        Vec3 cameraOrigin=camera.getPosition();
        Vec3 localOrigin=toLocal.apply(cameraOrigin);
        Vec3 targetDirection=unitDirection(toLocal.apply(cameraOrigin.add(desiredTangentWorld)).subtract(localOrigin),null);
        Vec3 targetWidth=toLocal.apply(cameraOrigin.add(desiredWidthWorld)).subtract(localOrigin);

        Vec3 sourceDirection=unitDirection(new Vec3(
            SeaRippleWhipGeometry.tailRootDirectionLocalX(authorityTail),
            SeaRippleWhipGeometry.tailRootDirectionLocalY(authorityTail),
            SeaRippleWhipGeometry.tailRootDirectionLocalZ(authorityTail)),new Vec3(0,1,0));
        Vec3 sourceWidth=new Vec3(
            SeaRippleWhipGeometry.tailWidthAxisLocalX(authorityTail),
            SeaRippleWhipGeometry.tailWidthAxisLocalY(authorityTail),
            SeaRippleWhipGeometry.tailWidthAxisLocalZ(authorityTail));

        Quaternionf full=frameAlignment(sourceDirection,sourceWidth,targetDirection,targetWidth);
        if(full==null)return;
        Quaternionf blended=new Quaternionf().identity().slerp(full,(float)Mth.clamp(authority,0.0D,1.0D));

        pose.translate(FIRST_PERSON_HANDLE_PIVOT.x,FIRST_PERSON_HANDLE_PIVOT.y,FIRST_PERSON_HANDLE_PIVOT.z);
        pose.mulPose(blended);
        pose.translate(-FIRST_PERSON_HANDLE_PIVOT.x,-FIRST_PERSON_HANDLE_PIVOT.y,-FIRST_PERSON_HANDLE_PIVOT.z);
    }

    private static Quaternionf frameAlignment(Vec3 sourceDirection,Vec3 sourceWidth,
                                               Vec3 targetDirection,Vec3 targetWidth) {
        Vec3 sz=unitDirection(sourceDirection,new Vec3(0,1,0));
        Vec3 sx=orthogonalWidth(sz,sourceWidth);
        Vec3 sy=unitDirection(sz.cross(sx),new Vec3(0,0,1));
        sx=unitDirection(sy.cross(sz),sx);

        Vec3 tz=unitDirection(targetDirection,sz);
        Vec3 tx=orthogonalWidth(tz,targetWidth);
        Vec3 ty=unitDirection(tz.cross(tx),sy);
        tx=unitDirection(ty.cross(tz),tx);

        Matrix3f sourceMatrix=new Matrix3f(sx.toVector3f(),sy.toVector3f(),sz.toVector3f());
        Matrix3f targetMatrix=new Matrix3f(tx.toVector3f(),ty.toVector3f(),tz.toVector3f());
        Quaternionf sourceQ=new Quaternionf().setFromNormalized(sourceMatrix);
        Quaternionf targetQ=new Quaternionf().setFromNormalized(targetMatrix);
        Quaternionf delta=targetQ.mul(new Quaternionf(sourceQ).conjugate()).normalize();
        return Float.isFinite(delta.x) && Float.isFinite(delta.y) && Float.isFinite(delta.z) && Float.isFinite(delta.w)
            ? delta : null;
    }

    private static Vec3 orthogonalWidth(Vec3 direction,Vec3 width) {
        Vec3 projected=width==null?Vec3.ZERO:width.subtract(direction.scale(width.dot(direction)));
        if(projected.lengthSqr()<1.0E-10D) {
            Vec3 fallback=Math.abs(direction.y)<.9D?new Vec3(0,1,0):new Vec3(1,0,0);
            projected=fallback.subtract(direction.scale(fallback.dot(direction)));
        }
        return unitDirection(projected,new Vec3(1,0,0));
    }
    private static boolean localFirstPerson(Player p,Camera camera) {
        Minecraft mc=Minecraft.getInstance();
        return p==mc.player && mc.options.getCameraType().isFirstPerson() && camera.getEntity()==p
            && camera.getPosition().distanceToSqr(p.getEyePosition())<=2.25;
    }
    private static Frame frame(Player p,HumanoidArm arm,float partial) {
        Stroke s=SeaRippleWhipClientState.stroke(p,arm);double age=SeaRippleWhipClientState.age(p,s,partial);
        if(age<0 || age>s.duration() || s.kind()==SeaRippleWhipMotion.IDLE) {
            s=Stroke.idle(p.position(),p.getViewVector(partial),arm==HumanoidArm.RIGHT?1:-1,(long)SeaRippleWhipClientState.clock(p,0));
            age=p.level().getGameTime()+partial;
        }
        Vec3 feet=new Vec3(Mth.lerp(partial,p.xo,p.getX()),Mth.lerp(partial,p.yo,p.getY()),Mth.lerp(partial,p.zo,p.getZ()));
        Vec3 root=SeaRippleWhipMotion.grip(feet,s.aim(),p.getEyeHeight(),s.handSign());
        Socket socket=SOCKETS.get(new Key(p.getUUID(),arm));
        if(socket!=null && p.level().getGameTime()-socket.tick>2)socket=null;
        return new Frame(p,arm,partial,s,age,root,socket);
    }
    private static Vec3[] curve(Frame frame,int tail) {
        Vec3[] guide=SeaRippleWhipMotion.sample(frame.stroke,frame.age,frame.root,tail);
        Vec3 actual=frame.root;
        Vec3 direction=guide.length>1?guide[1].subtract(guide[0]):frame.stroke.aim();
        boolean normalSweep=frame.stroke.kind()==SeaRippleWhipMotion.SWEEP_LEFT
            || frame.stroke.kind()==SeaRippleWhipMotion.SWEEP_RIGHT;
        if(frame.socket!=null) {
            actual=frame.socket.roots[tail];
            Vec3 delta=actual.subtract(frame.root);
            direction=frame.socket.directions[tail];
            for(int i=0;i<guide.length;i++) {
                double u=i/(double)(guide.length-1);
                guide[i]=guide[i].add(delta.scale((1-u)*(1-u)));

                if(!normalSweep && i<5)guide[i]=actual.add(direction.scale(i*.125))
                    .lerp(guide[i],SeaRippleWhipMotion.smooth(0,5,i));
            }
            guide[0]=actual;
            if(normalSweep && guide.length>1)direction=unitDirection(guide[1].subtract(guide[0]),direction);
        }
        double attackDrive=SeaRippleWhipMotion.attackDrive(frame.stroke,frame.age,tail);
        Vec3[] physical=SeaRippleWhipIdlePhysics.sample(
            frame.player,frame.arm,tail,actual,direction,guide,frame.partial,attackDrive);
        return attackEffectPriorityPath(frame,physical,guide);
    }

    private static Vec3[] attackEffectPriorityPath(Frame frame,Vec3[] physical,Vec3[] guide) {
        double authority=SeaRippleWhipMotion.attackVisualAuthority(frame.stroke,frame.age);
        boolean normalSweep=frame.stroke.kind()==SeaRippleWhipMotion.SWEEP_LEFT
            || frame.stroke.kind()==SeaRippleWhipMotion.SWEEP_RIGHT;
        boolean blade=frame.stroke.kind()==SeaRippleWhipMotion.BLADE;
        if((!normalSweep && !blade) || authority<=1.0E-5D || guide==null)return physical;
        if(blade)return guide.clone();
        if(physical==null)return guide.clone();
        Vec3[] result=physical.clone();
        int count=Math.min(result.length,guide.length);
        double blend=Mth.clamp(authority*ATTACK_VFX_BODY_AUTHORITY,0.0D,1.0D);
        for(int i=0;i<count;i++)result[i]=result[i].lerp(guide[i],blend);
        return result;
    }

    private static Vec3 unitDirection(Vec3 value,Vec3 fallback) {
        if(value!=null && Double.isFinite(value.x) && Double.isFinite(value.y)
                && Double.isFinite(value.z) && value.lengthSqr()>1.0E-10D)return value.normalize();
        if(fallback!=null && Double.isFinite(fallback.x) && Double.isFinite(fallback.y)
                && Double.isFinite(fallback.z) && fallback.lengthSqr()>1.0E-10D)return fallback.normalize();
        return new Vec3(0,0,1);
    }

    private static double smooth01(double t) {
        t=Mth.clamp(t,0.0D,1.0D);
        return t*t*(3.0D-2.0D*t);
    }

    private static Vec3 rotateAround(Vec3 v,Vec3 axis,double angle) {
        double c=Math.cos(angle),s=Math.sin(angle);
        return v.scale(c).add(axis.cross(v).scale(s)).add(axis.scale(axis.dot(v)*(1.0D-c)));
    }

    private static Vec3 stableRibbonWidth(Vec3 axis,Vec3 transported,Vec3 preferred,double u) {
        Vec3 previous=transported.subtract(axis.scale(transported.dot(axis)));
        Vec3 reference=preferred.subtract(axis.scale(preferred.dot(axis)));
        if(reference.lengthSqr()<1.0E-8D) {
            if(previous.lengthSqr()>=1.0E-8D)return previous.normalize();
            Vec3 fallback=axis.cross(Math.abs(axis.y)<.9?new Vec3(0,1,0):new Vec3(1,0,0));
            return fallback.lengthSqr()<1.0E-8D?new Vec3(1,0,0):fallback.normalize();
        }
        reference=reference.normalize();
        if(previous.lengthSqr()<1.0E-8D)return reference;
        previous=previous.normalize();
        if(previous.dot(reference)<0.0D)previous=previous.scale(-1.0D);
        double signed=Math.atan2(axis.dot(reference.cross(previous)),Mth.clamp(reference.dot(previous),-1.0D,1.0D));
        double tipLock=smooth01((u-.58D)/.42D);
        double limit=Mth.lerp(tipLock,RIBBON_MAX_TWIST_BODY_RADIANS,RIBBON_MAX_TWIST_TIP_RADIANS);
        return rotateAround(reference,axis,Mth.clamp(signed,-limit,limit)).normalize();
    }
    private static void segments(PoseStack pose,VertexConsumer out,Frame frame,UnaryOperator<Vec3> space,
            ClientLevel level,int overlay,boolean hand) {
        for(int tail=0;tail<5;tail++) {
            Vec3[] path=curve(frame,tail),local=SeaRippleWhipWaterMesh.transform(path,space);
            Vec3 width=frame.socket==null?SeaRippleWhipMotion.right(frame.stroke.aim()):frame.socket.widths[tail];
            Vec3 preferredWidth=space.apply(path[0].add(width)).subtract(space.apply(path[0])).normalize();
            Vec3 transportedWidth=preferredWidth;
            if(hand)local[0]=localRoot(tail);
            for(int i=0;i<35;i++) {
                Vec3 chord=local[i+1].subtract(local[i]);double length=chord.length();
                if(!Double.isFinite(length) || length<1e-6)continue;
                Vec3 z=chord.scale(-1/length);
                double u=(i+.5D)/35.0D;
                Vec3 x=stableRibbonWidth(z,transportedWidth,preferredWidth,u);
                Vec3 y=z.cross(x).normalize();transportedWidth=x;
                Quaternionf rotation=new Quaternionf().setFromNormalized(new Matrix3f(x.toVector3f(),y.toVector3f(),z.toVector3f()));
                pose.pushPose();pose.translate(local[i].x,local[i].y,local[i].z);pose.mulPose(rotation);
                pose.scale(1,1,(float)(length/SeaRippleWhipGeometry.REST_LENGTHS[i]));
                int light=LevelRenderer.getLightColor(level,BlockPos.containing(path[i]));
                SeaRippleWhipGeometry.INSTANCE.renderDynamicSegment(tail,i,pose,out,light,overlay);pose.popPose();
            }
        }
    }
    private static UnaryOperator<Vec3> handSpace(PoseStack pose,Camera camera) {
        Matrix4f inverse=new Matrix4f(pose.last().pose());
        if(!Float.isFinite(inverse.determinant()) || Math.abs(inverse.determinant())<1e-8)return null;
        inverse.invert();return point->{
            Vec3 v=WhipFirstPersonFov.applyToCapturedWorldVector(point.subtract(camera.getPosition()),camera);
            Vector3f local=inverse.transformPosition(v.toVector3f());return new Vec3(local.x,local.y,local.z);
        };
    }
    static void renderFirstPersonHandLash(PoseStack pose,RenderContext context,VertexConsumer out,int overlay) {
        if(!(context.holder instanceof Player p) || !(p.level() instanceof ClientLevel level) || !context.firstPerson)return;
        Camera camera=Minecraft.getInstance().gameRenderer.getMainCamera();if(!localFirstPerson(p,camera))return;
        UnaryOperator<Vec3> space=handSpace(pose,camera);if(space==null)return;
        segments(pose,out,frame(p,context.arm,Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false)),space,level,overlay,true);
    }
    static void renderFirstPersonWater(PoseStack pose,RenderContext context,MultiBufferSource buffers) {
        if(!SeaRippleWhipVfx.ready() || !(context.holder instanceof Player p) || !context.firstPerson)return;
        Camera camera=Minecraft.getInstance().gameRenderer.getMainCamera();if(!localFirstPerson(p,camera))return;
        UnaryOperator<Vec3> space=handSpace(pose,camera);if(space==null)return;
        Frame frame=frame(p,context.arm,Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false));
        for(RenderType type:new RenderType[]{SeaRippleWhipVfx.WATER,SeaRippleWhipVfx.GLOW}) {
            water(pose,buffers.getBuffer(type),frame,space,SeaRippleWhipClientState.empoweredRemaining(p)>0);
            if(buffers instanceof MultiBufferSource.BufferSource source)source.endBatch(type);
        }
    }
    private static void water(PoseStack pose,VertexConsumer out,Frame frame,UnaryOperator<Vec3> space,boolean empowered) {
        for(int tail=0;tail<5;tail++) {
            Vec3[] path=curve(frame,tail);Vec3 width=frame.socket==null?SeaRippleWhipMotion.right(frame.stroke.aim()):frame.socket.widths[tail];
            Vec3 localWidth=space.apply(path[0].add(width)).subtract(space.apply(path[0])).normalize();
            SeaRippleWhipWaterMesh.ribbon(pose,out,SeaRippleWhipWaterMesh.transform(path,space),localWidth,
                empowered?.085f:.062f,empowered?.54f:.30f,tail*.17f);
        }
        int authorityTail=SeaRippleWhipMotion.sweepLeaderTail(frame.stroke);
        Vec3 delta=frame.socket==null?Vec3.ZERO:frame.socket.roots[authorityTail].subtract(frame.root);
        SeaRippleWhipWaterMesh.stroke(pose,out,frame.stroke,frame.age,frame.root,delta,space,1f);
    }
    public static void onRenderLevel(RenderLevelStageEvent event) {
        boolean model=event.getStage()==RenderLevelStageEvent.Stage.AFTER_ENTITIES;
        if(!model && event.getStage()!=RenderLevelStageEvent.Stage.AFTER_PARTICLES || ShaderCompat.isIrisShadowPass())return;
        Minecraft mc=Minecraft.getInstance();if(mc.level==null || !model && !SeaRippleWhipVfx.ready())return;
        float partial=event.getPartialTick().getGameTimeDeltaPartialTick(false);
        MultiBufferSource.BufferSource buffers=mc.renderBuffers().bufferSource();PoseStack pose=event.getPoseStack();
        UnaryOperator<Vec3> space=p->p.subtract(event.getCamera().getPosition());
        RenderType[] types=model?new RenderType[]{MODEL}:new RenderType[]{SeaRippleWhipVfx.WATER,SeaRippleWhipVfx.GLOW};
        for(RenderType type:types) {
            VertexConsumer out=null;
            for(Player p:mc.level.players()) {
                if(p.isInvisible() || p.distanceToSqr(event.getCamera().getPosition())>9216 || localFirstPerson(p,event.getCamera()))continue;
                for(HumanoidArm arm:HumanoidArm.values())if(holds(p,arm)) {
                    if(out==null)out=buffers.getBuffer(type);Frame frame=frame(p,arm,partial);
                    if(model) {
                        WhipPerformanceTuning.pushGeometryQuality(p);
                        try { segments(pose,out,frame,space,mc.level,OverlayTexture.NO_OVERLAY,false); }
                        finally { WhipPerformanceTuning.popGeometryQuality(); }
                    }
                    else water(pose,out,frame,space,SeaRippleWhipClientState.empoweredRemaining(p)>0);
                }
            }
            if(out!=null)buffers.endBatch(type);
        }
    }
    public static void onRenderArm(RenderArmEvent event) {
        Player p=event.getPlayer();if(!holds(p,event.getArm()))return;
        ArmPose arm=getArmPose(p,event.getArm(),Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false));
        PoseStack pose=event.getPoseStack();float side=event.getArm()==HumanoidArm.RIGHT?1:-1;
        WhipFirstPersonFov.applyToViewModel(pose);pose.translate(side*.025,-.035,0);
        pose.mulPose(Axis.ZP.rotation(arm.roll*.50f));pose.mulPose(Axis.YP.rotation(arm.yaw*.45f));pose.mulPose(Axis.XP.rotation((arm.pitch+.38f)*.55f));
    }
    public static void applyFirstPersonItemTransform(PoseStack pose,Player p,HumanoidArm arm,float partial,float equip) {
        ArmPose angle=getArmPose(p,arm,partial);float side=arm==HumanoidArm.RIGHT?1:-1;
        WhipFirstPersonFov.applyToViewModel(pose);pose.translate(side*.56,-.52-equip*.60,-.72);pose.translate(side*.025,.055,-.035);
        pose.mulPose(Axis.YP.rotationDegrees(side*42+Mth.RAD_TO_DEG*angle.yaw*.70f));
        pose.mulPose(Axis.XP.rotationDegrees(-22+Mth.RAD_TO_DEG*(angle.pitch+.38f)*.90f));
        pose.mulPose(Axis.ZP.rotationDegrees(-side*8+Mth.RAD_TO_DEG*angle.roll*.82f));
    }
    public static ArmPose getArmPose(Player p,HumanoidArm arm,float partial) {
        Stroke s=SeaRippleWhipClientState.stroke(p,arm);float[] result=SeaRippleWhipMotion.armPose(s,SeaRippleWhipClientState.age(p,s,partial));
        return new ArmPose(result[0],result[1],result[2]);
    }
    public static ArmPose getThirdPersonArmPose(Player p,HumanoidArm arm,float partial) {
        ArmPose pose=getArmPose(p,arm,partial);return new ArmPose(-.76f-pose.pitch,pose.yaw,pose.roll);
    }
    public record ArmPose(float pitch,float yaw,float roll) {}
    public record RenderContext(LivingEntity holder,HumanoidArm arm,boolean firstPerson) {}
    private record Key(UUID player,HumanoidArm arm) {}
    private record Socket(Vec3[] roots,Vec3[] directions,Vec3[] widths,long tick,boolean firstPerson) {}
    private record Frame(Player player,HumanoidArm arm,float partial,Stroke stroke,double age,Vec3 root,Socket socket) {}
}
