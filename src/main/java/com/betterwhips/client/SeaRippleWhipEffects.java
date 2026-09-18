package com.betterwhips.client;

import com.betterwhips.network.SeaRippleWhipNetwork.Impact;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import java.util.*;

public final class SeaRippleWhipEffects {
    private static final List<Burst> BURSTS=new ArrayList<>();
    private SeaRippleWhipEffects() {}
    public static void receive(Impact packet) {
        ClientLevel level=Minecraft.getInstance().level;
        if(level==null || !level.dimension().location().equals(packet.dimension()))return;
        if(BURSTS.size()>=48)BURSTS.removeFirst();
        BURSTS.add(new Burst(level,level.getGameTime(),packet));
    }
    public static void clear() { BURSTS.clear(); }
    static void tick() {
        ClientLevel level=Minecraft.getInstance().level;
        if(level==null){clear();return;}
        BURSTS.removeIf(b->b.level!=level || level.getGameTime()-b.start>11 || level.getGameTime()<b.start);
    }
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if(event.getStage()!=RenderLevelStageEvent.Stage.AFTER_PARTICLES || ShaderCompat.isIrisShadowPass()
                || !SeaRippleWhipVfx.ready() || BURSTS.isEmpty())return;
        Minecraft mc=Minecraft.getInstance();if(mc.level==null)return;
        var buffers=mc.renderBuffers().bufferSource();float partial=event.getPartialTick().getGameTimeDeltaPartialTick(false);
        for(RenderType type:new RenderType[]{SeaRippleWhipVfx.WATER,SeaRippleWhipVfx.GLOW}) {
            VertexConsumer out=buffers.getBuffer(type);
            for(Burst burst:BURSTS) {
                if(burst.level!=mc.level)continue;Impact hit=burst.packet;
                float age=(mc.level.getGameTime()-burst.start)+partial;if(age<0 || age>10)continue;
                Vec3 center=hit.position();Entity entity=mc.level.getEntity(hit.entityId());
                if(entity!=null && entity.getUUID().equals(hit.uuid()) && entity.position().distanceToSqr(center)<16) {
                    Vec3 offset=new Vec3(Mth.lerp(partial,entity.xo,entity.getX())-entity.getX(),
                        Mth.lerp(partial,entity.yo,entity.getY())-entity.getY(),Mth.lerp(partial,entity.zo,entity.getZ())-entity.getZ());
                    center=center.lerp(entity.getBoundingBox().getCenter().add(offset),Math.min(1,age*.35));
                }
                if(center.distanceToSqr(event.getCamera().getPosition())>4096)continue;
                SeaRippleWhipWaterMesh.impact(event.getPoseStack(),out,center.subtract(event.getCamera().getPosition()),
                    hit.direction(),age,hit.size(),hit.empowered(),hit.seed());
            }
            buffers.endBatch(type);
        }
    }
    private record Burst(ClientLevel level,long start,Impact packet) {}
}
