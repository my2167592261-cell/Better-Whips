package com.betterwhips.network;

import com.betterwhips.client.LightningWhipClientNetwork;
import com.betterwhips.item.LightningWhipCombat;
import com.betterwhips.item.LightningWhipChain;
import com.betterwhips.item.LightningWhipTimeStop;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public final class LightningWhipNetwork {
    private static final int WHIP_POINT_COUNT = 57;
    private static final PrecisionAttackPayload PRECISION_ATTACK = PrecisionAttackPayload.empty();

    private LightningWhipNetwork() {
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("9");
        registrar.playToServer(PrecisionAttackPayload.TYPE, PrecisionAttackPayload.STREAM_CODEC, LightningWhipNetwork::handlePrecisionAttack);
        registrar.playToClient(WhipShockwavePayload.TYPE, WhipShockwavePayload.STREAM_CODEC, LightningWhipClientNetwork::handleShockwave);
        registrar.playToClient(AttackSpeedStacksPayload.TYPE, AttackSpeedStacksPayload.STREAM_CODEC, LightningWhipClientNetwork::handleAttackSpeedStacks);
        registrar.playToClient(HitGlowBurstPayload.TYPE, HitGlowBurstPayload.STREAM_CODEC, LightningWhipClientNetwork::handleHitGlowBurst);
        registrar.playToClient(ChainHitPayload.TYPE, ChainHitPayload.STREAM_CODEC, LightningWhipClientNetwork::handleChainHit);
        registrar.playToClient(DirectWrapPayload.TYPE, DirectWrapPayload.STREAM_CODEC, LightningWhipClientNetwork::handleDirectWrap);
    }

    public static void sendPrecisionAttack() {
        PacketDistributor.sendToServer(PRECISION_ATTACK, new CustomPacketPayload[0]);
    }

    public static void sendPrecisionAttack(Vec3[] points, Vec3[] previous, double substepSeconds, Vec3 handleAxis, Vec3 attackDirection) {
        if (points == null || previous == null || points.length != 57 || previous.length != 57) {
            LightningWhipNetwork.sendPrecisionAttack();
            return;
        }
        Vec3 axis = handleAxis == null ? Vec3.ZERO : handleAxis;
        Vec3 direction = attackDirection == null ? Vec3.ZERO : attackDirection;
        PacketDistributor.sendToServer(new PrecisionAttackPayload(true, substepSeconds, axis, direction, (Vec3[])points.clone(), (Vec3[])previous.clone()), new CustomPacketPayload[0]);
    }

    public static void sendShockwave(ServerLevel level, Vec3 impact, long seed) {
        WhipShockwavePayload payload = new WhipShockwavePayload(impact.x, impact.y, impact.z, seed);
        for (ServerPlayer player : level.players()) {
            if (!(player.distanceToSqr(impact) <= 65536.0)) continue;
            PacketDistributor.sendToPlayer(player, payload, new CustomPacketPayload[0]);
        }
    }

    public static void sendAttackSpeedStacks(ServerPlayer player, int stacks) {
        if (player == null) {
            return;
        }
        int clamped = Math.max(0, Math.min(5, stacks));
        PacketDistributor.sendToPlayer(player, new AttackSpeedStacksPayload(clamped), new CustomPacketPayload[0]);
    }

    public static void sendHitGlowBurst(ServerLevel level, Vec3 position, long seed) {
        if (level == null || position == null) {
            return;
        }
        HitGlowBurstPayload payload = new HitGlowBurstPayload(position.x, position.y, position.z, seed);
        for (ServerPlayer player : level.players()) {
            if (!(player.distanceToSqr(position) <= 16384.0)) continue;
            PacketDistributor.sendToPlayer(player, payload, new CustomPacketPayload[0]);
        }
    }

    public static void sendDirectWrap(ServerLevel level, LivingEntity target, long seed) {
        if (level == null || target == null) return;
        DirectWrapPayload payload = new DirectWrapPayload(seed, ArcTarget.of(target));
        Vec3 center = target.getBoundingBox().getCenter();
        for (ServerPlayer viewer : level.players()) {
            if (viewer.distanceToSqr(center) <= 16384.0) PacketDistributor.sendToPlayer(viewer, payload);
        }
    }

    public static void sendArcRoute(ServerLevel level, List<ArcTarget> route, long seed) {
        if (level == null || route == null || route.isEmpty()) return;
        ChainHitPayload payload = new ChainHitPayload(seed, route);
        for (ServerPlayer viewer : level.players()) {
            for (ArcTarget endpoint : payload.targets()) {
                if (viewer.distanceToSqr(endpoint.center()) > 16384.0) continue;
                PacketDistributor.sendToPlayer(viewer, payload);
                break;
            }
        }
    }

    private static void handlePrecisionAttack(PrecisionAttackPayload payload, IPayloadContext context) {
        Player player = context.player();
        if (player instanceof ServerPlayer) {
            ServerPlayer player2 = (ServerPlayer)player;
            LightningWhipCombat.PrecisionPoseSnapshot snapshot = payload.hasPose() ? new LightningWhipCombat.PrecisionPoseSnapshot(payload.points(), payload.previous(), payload.substepSeconds(), payload.handleAxis(), payload.attackDirection()) : null;
            LightningWhipCombat.tryBeginPrecision(player2, snapshot);
        }
    }

    private static void writeVec3(RegistryFriendlyByteBuf buffer, Vec3 value) {
        Vec3 safe = value == null ? Vec3.ZERO : value;
        buffer.writeDouble(safe.x);
        buffer.writeDouble(safe.y);
        buffer.writeDouble(safe.z);
    }

    private static Vec3 readVec3(RegistryFriendlyByteBuf buffer) {
        return new Vec3(buffer.readDouble(), buffer.readDouble(), buffer.readDouble());
    }

    public record PrecisionAttackPayload(boolean hasPose, double substepSeconds, Vec3 handleAxis, Vec3 attackDirection, Vec3[] points, Vec3[] previous) implements CustomPacketPayload
    {
        public static final CustomPacketPayload.Type<PrecisionAttackPayload> TYPE = new CustomPacketPayload.Type(ResourceLocation.fromNamespaceAndPath("better_whips", "lightning_whip_precision"));
        public static final StreamCodec<RegistryFriendlyByteBuf, PrecisionAttackPayload> STREAM_CODEC = StreamCodec.of((buffer, payload) -> {
            buffer.writeBoolean(payload.hasPose);
            if (!payload.hasPose) {
                return;
            }
            buffer.writeDouble(payload.substepSeconds);
            LightningWhipNetwork.writeVec3(buffer, payload.handleAxis);
            LightningWhipNetwork.writeVec3(buffer, payload.attackDirection);
            for (int i = 0; i < 57; ++i) {
                LightningWhipNetwork.writeVec3(buffer, payload.points[i]);
                LightningWhipNetwork.writeVec3(buffer, payload.previous[i]);
            }
        }, buffer -> {
            if (!buffer.readBoolean()) {
                return PrecisionAttackPayload.empty();
            }
            double substepSeconds = buffer.readDouble();
            Vec3 handleAxis = LightningWhipNetwork.readVec3(buffer);
            Vec3 attackDirection = LightningWhipNetwork.readVec3(buffer);
            Vec3[] points = new Vec3[57];
            Vec3[] previous = new Vec3[57];
            for (int i = 0; i < 57; ++i) {
                points[i] = LightningWhipNetwork.readVec3(buffer);
                previous[i] = LightningWhipNetwork.readVec3(buffer);
            }
            return new PrecisionAttackPayload(true, substepSeconds, handleAxis, attackDirection, points, previous);
        });

        static PrecisionAttackPayload empty() {
            return new PrecisionAttackPayload(false, 0.0, Vec3.ZERO, Vec3.ZERO, new Vec3[0], new Vec3[0]);
        }

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record WhipShockwavePayload(double x, double y, double z, long seed) implements CustomPacketPayload
    {
        public static final CustomPacketPayload.Type<WhipShockwavePayload> TYPE = new CustomPacketPayload.Type(ResourceLocation.fromNamespaceAndPath("better_whips", "lightning_whip_shockwave"));
        public static final StreamCodec<RegistryFriendlyByteBuf, WhipShockwavePayload> STREAM_CODEC = StreamCodec.of((buffer, payload) -> {
            buffer.writeDouble(payload.x);
            buffer.writeDouble(payload.y);
            buffer.writeDouble(payload.z);
            buffer.writeLong(payload.seed);
        }, buffer -> new WhipShockwavePayload(buffer.readDouble(), buffer.readDouble(), buffer.readDouble(), buffer.readLong()));

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record AttackSpeedStacksPayload(int stacks) implements CustomPacketPayload
    {
        public static final CustomPacketPayload.Type<AttackSpeedStacksPayload> TYPE = new CustomPacketPayload.Type(ResourceLocation.fromNamespaceAndPath("better_whips", "lightning_whip_attack_speed_stacks"));
        public static final StreamCodec<RegistryFriendlyByteBuf, AttackSpeedStacksPayload> STREAM_CODEC = StreamCodec.of((buffer, payload) -> buffer.writeVarInt(payload.stacks), buffer -> new AttackSpeedStacksPayload(buffer.readVarInt()));

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record ArcTarget(int entityId, UUID uuid, Vec3 center, float radiusX, float halfHeight, float radiusZ) {
        public ArcTarget {
            if (uuid == null || center == null || !Double.isFinite(center.x+center.y+center.z)
                    || !Float.isFinite(radiusX+halfHeight+radiusZ)
                    || radiusX <= 0 || halfHeight <= 0 || radiusZ <= 0) {
                throw new IllegalArgumentException("Invalid electric arc endpoint");
            }
        }
        public static ArcTarget of(LivingEntity entity) {
            AABB box = entity.getBoundingBox();
            return new ArcTarget(entity.getId(),entity.getUUID(),box.getCenter(),
                Math.max(.01f,(float)box.getXsize()*.5f),Math.max(.01f,(float)box.getYsize()*.5f),
                Math.max(.01f,(float)box.getZsize()*.5f));
        }
    }

    public record ChainHitPayload(long seed, List<ArcTarget> targets) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<ChainHitPayload> TYPE = new CustomPacketPayload.Type<>(
            ResourceLocation.fromNamespaceAndPath("better_whips","lightning_whip_chain_hit"));
        public ChainHitPayload {
            targets = List.copyOf(targets);
            if (targets.size() != LightningWhipChain.ROUTE_ENDPOINTS)
                throw new IllegalArgumentException("Electric route must contain direct source plus six hops");
        }
        public static final StreamCodec<RegistryFriendlyByteBuf,ChainHitPayload> STREAM_CODEC = StreamCodec.of((buffer,payload) -> {
            buffer.writeLong(payload.seed);
            buffer.writeVarInt(payload.targets.size());
            for (ArcTarget target : payload.targets) {
                buffer.writeVarInt(target.entityId());
                buffer.writeUUID(target.uuid());
                writeVec3(buffer,target.center());
                buffer.writeFloat(target.radiusX());
                buffer.writeFloat(target.halfHeight());
                buffer.writeFloat(target.radiusZ());
            }
        }, buffer -> {
            long seed = buffer.readLong();
            int count = buffer.readVarInt();
            if (count != LightningWhipChain.ROUTE_ENDPOINTS)
                throw new IllegalArgumentException("Invalid electric route endpoint count: "+count);
            List<ArcTarget> targets = new ArrayList<>(count);
            for (int i=0;i<count;i++) targets.add(new ArcTarget(buffer.readVarInt(),buffer.readUUID(),readVec3(buffer),
                buffer.readFloat(),buffer.readFloat(),buffer.readFloat()));
            return new ChainHitPayload(seed,targets);
        });
        @Override public CustomPacketPayload.Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record DirectWrapPayload(long seed, ArcTarget target) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<DirectWrapPayload> TYPE = new CustomPacketPayload.Type<>(
                ResourceLocation.fromNamespaceAndPath("better_whips", "lightning_whip_direct_wrap"));
        public static final StreamCodec<RegistryFriendlyByteBuf, DirectWrapPayload> STREAM_CODEC = StreamCodec.of(
                (buffer, payload) -> {
                    buffer.writeLong(payload.seed);
                    ArcTarget target = payload.target;
                    buffer.writeVarInt(target.entityId());
                    buffer.writeUUID(target.uuid());
                    writeVec3(buffer, target.center());
                    buffer.writeFloat(target.radiusX());
                    buffer.writeFloat(target.halfHeight());
                    buffer.writeFloat(target.radiusZ());
                },
                buffer -> new DirectWrapPayload(buffer.readLong(),
                        new ArcTarget(buffer.readVarInt(), buffer.readUUID(), readVec3(buffer),
                                buffer.readFloat(), buffer.readFloat(), buffer.readFloat())));
        @Override public CustomPacketPayload.Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record HitGlowBurstPayload(double x, double y, double z, long seed) implements CustomPacketPayload
    {
        public static final CustomPacketPayload.Type<HitGlowBurstPayload> TYPE = new CustomPacketPayload.Type(ResourceLocation.fromNamespaceAndPath("better_whips", "lightning_whip_hit_glow_burst"));
        public static final StreamCodec<RegistryFriendlyByteBuf, HitGlowBurstPayload> STREAM_CODEC = StreamCodec.of((buffer, payload) -> {
            buffer.writeDouble(payload.x);
            buffer.writeDouble(payload.y);
            buffer.writeDouble(payload.z);
            buffer.writeLong(payload.seed);
        }, buffer -> new HitGlowBurstPayload(buffer.readDouble(), buffer.readDouble(), buffer.readDouble(), buffer.readLong()));

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }
}
