package com.betterwhips.network;

import com.betterwhips.client.SeaRippleWhipClientState;
import com.betterwhips.client.SeaRippleWhipEffects;
import com.betterwhips.item.SeaRippleWhipCombat;
import com.betterwhips.physics.SeaRippleWhipMotion;
import com.betterwhips.physics.SeaRippleWhipMotion.Stroke;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import java.util.UUID;

public final class SeaRippleWhipNetwork {
    public static final int ATTACK=0, BLADE=1;
    private SeaRippleWhipNetwork() {}
    private static ResourceLocation id(String path) { return ResourceLocation.fromNamespaceAndPath("better_whips",path); }
    public static void register(RegisterPayloadHandlersEvent event) {
        var registrar=event.registrar("nilah-blade-1");
        registrar.playToServer(Request.TYPE,Request.CODEC,(payload,context)->context.enqueueWork(()->{
            if(context.player() instanceof ServerPlayer player) SeaRippleWhipCombat.request(player,payload.action());
        }));
        registrar.playToClient(Snapshot.TYPE,Snapshot.CODEC,(payload,context)->context.enqueueWork(()->SeaRippleWhipClientState.receive(payload)));
        registrar.playToClient(Impact.TYPE,Impact.CODEC,(payload,context)->context.enqueueWork(()->SeaRippleWhipEffects.receive(payload)));
    }
    public static void request(int action) { PacketDistributor.sendToServer(new Request(action)); }
    public static void nearby(ServerLevel level,Vec3 position,CustomPacketPayload packet) {
        for(ServerPlayer player:level.players()) if(player.distanceToSqr(position)<=9216)PacketDistributor.sendToPlayer(player,packet);
    }
    private static void vec(RegistryFriendlyByteBuf b,Vec3 p) { b.writeDouble(p.x);b.writeDouble(p.y);b.writeDouble(p.z); }
    private static Vec3 vec(RegistryFriendlyByteBuf b) {
        Vec3 p=new Vec3(b.readDouble(),b.readDouble(),b.readDouble());
        if(!SeaRippleWhipMotion.finite(p))throw new IllegalArgumentException("Non-finite sea VFX vector");return p;
    }
    private static void stroke(RegistryFriendlyByteBuf b,Stroke s) {
        b.writeByte(s.kind());b.writeLong(s.startTick());b.writeVarInt(s.duration());vec(b,s.origin());vec(b,s.aim());
        b.writeByte(s.handSign());b.writeBoolean(s.empowered());
    }
    private static Stroke stroke(RegistryFriendlyByteBuf b) {
        return new Stroke(b.readUnsignedByte(),b.readLong(),b.readVarInt(),vec(b),vec(b),b.readByte(),b.readBoolean());
    }
    public record Request(int action) implements CustomPacketPayload {
        public static final Type<Request> TYPE=new Type<>(id("sea_blade_action"));
        public Request { if(action<ATTACK || action>BLADE)throw new IllegalArgumentException("Invalid sea action"); }
        public static final StreamCodec<RegistryFriendlyByteBuf,Request> CODEC=StreamCodec.of((b,p)->b.writeByte(p.action),b->new Request(b.readUnsignedByte()));
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }
    public record Snapshot(int entityId,UUID uuid,ResourceLocation dimension,long serverTick,Stroke stroke,
            long nextAttack,long bladeReady,long empoweredUntil) implements CustomPacketPayload {
        public static final Type<Snapshot> TYPE=new Type<>(id("sea_blade_state"));
        public Snapshot {
            if(uuid==null || dimension==null || stroke==null)throw new IllegalArgumentException("Invalid sea state");
        }
        public static final StreamCodec<RegistryFriendlyByteBuf,Snapshot> CODEC=StreamCodec.of((b,p)->{
            b.writeVarInt(p.entityId);b.writeUUID(p.uuid);b.writeResourceLocation(p.dimension);b.writeLong(p.serverTick);
            SeaRippleWhipNetwork.stroke(b,p.stroke);b.writeLong(p.nextAttack);b.writeLong(p.bladeReady);b.writeLong(p.empoweredUntil);
        },b->new Snapshot(b.readVarInt(),b.readUUID(),b.readResourceLocation(),b.readLong(),SeaRippleWhipNetwork.stroke(b),b.readLong(),b.readLong(),b.readLong()));
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }
    public record Impact(ResourceLocation dimension,Vec3 position,Vec3 direction,
            int entityId,UUID uuid,boolean empowered,float size,long seed) implements CustomPacketPayload {
        public static final Type<Impact> TYPE=new Type<>(id("sea_blade_impact"));
        public Impact {
            if(dimension==null || !SeaRippleWhipMotion.finite(position) || !SeaRippleWhipMotion.finite(direction)
                || uuid==null || !Float.isFinite(size) || size<0 || size>16)
                throw new IllegalArgumentException("Invalid sea impact");
        }
        public static final StreamCodec<RegistryFriendlyByteBuf,Impact> CODEC=StreamCodec.of((b,p)->{
            b.writeResourceLocation(p.dimension);vec(b,p.position);vec(b,p.direction);b.writeVarInt(p.entityId);
            b.writeUUID(p.uuid);b.writeBoolean(p.empowered);b.writeFloat(p.size);b.writeLong(p.seed);
        },b->new Impact(b.readResourceLocation(),vec(b),vec(b),b.readVarInt(),b.readUUID(),b.readBoolean(),b.readFloat(),b.readLong()));
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }
}
