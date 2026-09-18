package com.betterwhips.network;

import com.betterwhips.BetterWhipsMod;
import com.betterwhips.client.DestructionWhipClientNetwork;
import com.betterwhips.item.DestructionWhipCombat;
import com.betterwhips.physics.DestructionWhipDimensions;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public final class DestructionWhipNetwork {

    private static final int WHIP_POINT_COUNT = DestructionWhipDimensions.POINT_COUNT;
    private static final PrecisionAttackPayload PRECISION_ATTACK = PrecisionAttackPayload.empty();

    private DestructionWhipNetwork() {}

    public static void register(RegisterPayloadHandlersEvent event) {

        PayloadRegistrar registrar = event.registrar("14");
        registrar.playToServer(
                PrecisionAttackPayload.TYPE,
                PrecisionAttackPayload.STREAM_CODEC,
                DestructionWhipNetwork::handlePrecisionAttack);
        registrar.playToClient(
                WhipShockwavePayload.TYPE,
                WhipShockwavePayload.STREAM_CODEC,
                DestructionWhipClientNetwork::handleShockwave);
        registrar.playToClient(
                AttackSpeedStacksPayload.TYPE,
                AttackSpeedStacksPayload.STREAM_CODEC,
                DestructionWhipClientNetwork::handleAttackSpeedStacks);
        registrar.playToClient(
                HitGlowBurstPayload.TYPE,
                HitGlowBurstPayload.STREAM_CODEC,
                DestructionWhipClientNetwork::handleHitGlowBurst);
        registrar.playToClient(
                BodyLaserPayload.TYPE,
                BodyLaserPayload.STREAM_CODEC,
                DestructionWhipClientNetwork::handleBodyLaser);
    }

    public static void sendPrecisionAttack() {
        sendPrecisionAttack(0L, -1, Vec3.ZERO, Vec3.ZERO);
    }

    public static void sendPrecisionAttack(long motionSeed) {
        sendPrecisionAttack(motionSeed, -1, Vec3.ZERO, Vec3.ZERO);
    }

    public static void sendPrecisionAttack(long motionSeed, int targetEntityId,
                                           Vec3 targetEyeOffset, Vec3 endEyeOffset) {
        PacketDistributor.sendToServer(PrecisionAttackPayload.empty(
                motionSeed, targetEntityId, targetEyeOffset, endEyeOffset));
    }

    public static void sendPrecisionAttack(Vec3[] points, Vec3[] previous,
                                           double substepSeconds, Vec3 handleAxis,
                                           Vec3 attackDirection, long motionSeed,
                                           int targetEntityId, Vec3 targetEyeOffset,
                                           Vec3 endEyeOffset) {
        if (points == null || previous == null
                || points.length != WHIP_POINT_COUNT || previous.length != WHIP_POINT_COUNT) {
            sendPrecisionAttack(motionSeed, targetEntityId, targetEyeOffset, endEyeOffset);
            return;
        }
        Vec3 axis = handleAxis == null ? Vec3.ZERO : handleAxis;
        Vec3 direction = attackDirection == null ? Vec3.ZERO : attackDirection;
        Vec3 targetOffset = targetEyeOffset == null ? Vec3.ZERO : targetEyeOffset;
        Vec3 endOffset = endEyeOffset == null ? Vec3.ZERO : endEyeOffset;
        PacketDistributor.sendToServer(new PrecisionAttackPayload(
                true, motionSeed, targetEntityId, targetOffset, endOffset,
                substepSeconds, axis, direction, points.clone(), previous.clone()));
    }

    public static void sendShockwave(ServerLevel level, Vec3 impact, long seed) {
        WhipShockwavePayload payload = new WhipShockwavePayload(
                impact.x, impact.y, impact.z, seed);
        for (ServerPlayer player : level.players()) {
            if (player.distanceToSqr(impact) <= 256.0D * 256.0D) {
                PacketDistributor.sendToPlayer(player, payload);
            }
        }
    }
    public static void sendAttackSpeedStacks(ServerPlayer player, int stacks) {
        if (player == null) {
            return;
        }
        int clamped = Math.max(0, Math.min(5, stacks));
        PacketDistributor.sendToPlayer(player, new AttackSpeedStacksPayload(clamped));
    }

    public static void sendHitGlowBurst(ServerLevel level, Vec3 position, Vec3 slashDirection,
                                        boolean magicMirror, long seed) {
        if (level == null || position == null) {
            return;
        }
        Vec3 direction = slashDirection == null ? Vec3.ZERO : slashDirection;
        HitGlowBurstPayload payload = new HitGlowBurstPayload(position.x, position.y, position.z,
                direction.x, direction.y, direction.z, magicMirror, seed);
        for (ServerPlayer player : level.players()) {
            if (player.distanceToSqr(position) <= 128.0D * 128.0D) {
                PacketDistributor.sendToPlayer(player, payload);
            }
        }
    }

    public static void sendBodyLaser(ServerLevel level, ServerPlayer owner, int sourceSegment,
                                     net.minecraft.world.entity.LivingEntity target,
                                     Vec3 fallbackFrom, Vec3 fallbackTo,
                                     float fullWidth, long seed) {
        if (level == null || owner == null || target == null
                || fallbackFrom == null || fallbackTo == null) {
            return;
        }
        float width = Math.max(0.001F, fullWidth);
        BodyLaserPayload payload = new BodyLaserPayload(
                owner.getId(), sourceSegment, target.getId(),
                fallbackFrom.x, fallbackFrom.y, fallbackFrom.z,
                fallbackTo.x, fallbackTo.y, fallbackTo.z,
                width, seed);
        Vec3 center = fallbackFrom.lerp(fallbackTo, 0.5D);
        for (ServerPlayer player : level.players()) {
            if (player.distanceToSqr(center) <= 128.0D * 128.0D) {
                PacketDistributor.sendToPlayer(player, payload);
            }
        }
    }

    private static void handlePrecisionAttack(PrecisionAttackPayload payload,
                                              IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player) {
            DestructionWhipCombat.PrecisionPoseSnapshot snapshot = payload.hasPose()
                    ? new DestructionWhipCombat.PrecisionPoseSnapshot(
                            payload.points(), payload.previous(), payload.substepSeconds(),
                            payload.handleAxis(), payload.attackDirection())
                    : null;
            DestructionWhipCombat.tryBeginPrecision(player, snapshot, payload.motionSeed(),
                    payload.targetEntityId(), payload.targetEyeOffset(), payload.endEyeOffset());
        }
    }

    public record PrecisionAttackPayload(boolean hasPose, long motionSeed,
                                         int targetEntityId, Vec3 targetEyeOffset,
                                         Vec3 endEyeOffset, double substepSeconds,
                                         Vec3 handleAxis, Vec3 attackDirection,
                                         Vec3[] points, Vec3[] previous)
            implements CustomPacketPayload {
        public static final Type<PrecisionAttackPayload> TYPE = new Type<>(
                ResourceLocation.fromNamespaceAndPath(
                        BetterWhipsMod.MOD_ID, "destruction_whip_precision"));
        public static final StreamCodec<RegistryFriendlyByteBuf, PrecisionAttackPayload> STREAM_CODEC =
                StreamCodec.of(
                        (buffer, payload) -> {
                            buffer.writeBoolean(payload.hasPose);
                            buffer.writeLong(payload.motionSeed);
                            buffer.writeInt(payload.targetEntityId);
                            writeVec3(buffer, payload.targetEyeOffset);
                            writeVec3(buffer, payload.endEyeOffset);
                            if (!payload.hasPose) {
                                return;
                            }
                            buffer.writeDouble(payload.substepSeconds);
                            writeVec3(buffer, payload.handleAxis);
                            writeVec3(buffer, payload.attackDirection);
                            for (int i = 0; i < WHIP_POINT_COUNT; ++i) {
                                writeVec3(buffer, payload.points[i]);
                                writeVec3(buffer, payload.previous[i]);
                            }
                        },
                        buffer -> {
                            boolean hasPose = buffer.readBoolean();
                            long motionSeed = buffer.readLong();
                            int targetEntityId = buffer.readInt();
                            Vec3 targetEyeOffset = readVec3(buffer);
                            Vec3 endEyeOffset = readVec3(buffer);
                            if (!hasPose) {
                                return empty(motionSeed, targetEntityId,
                                        targetEyeOffset, endEyeOffset);
                            }
                            double substepSeconds = buffer.readDouble();
                            Vec3 handleAxis = readVec3(buffer);
                            Vec3 attackDirection = readVec3(buffer);
                            Vec3[] points = new Vec3[WHIP_POINT_COUNT];
                            Vec3[] previous = new Vec3[WHIP_POINT_COUNT];
                            for (int i = 0; i < WHIP_POINT_COUNT; ++i) {
                                points[i] = readVec3(buffer);
                                previous[i] = readVec3(buffer);
                            }
                            return new PrecisionAttackPayload(
                                    true, motionSeed, targetEntityId,
                                    targetEyeOffset, endEyeOffset, substepSeconds,
                                    handleAxis, attackDirection, points, previous);
                        });

        static PrecisionAttackPayload empty() {
            return empty(0L, -1, Vec3.ZERO, Vec3.ZERO);
        }

        static PrecisionAttackPayload empty(long motionSeed) {
            return empty(motionSeed, -1, Vec3.ZERO, Vec3.ZERO);
        }

        static PrecisionAttackPayload empty(long motionSeed, int targetEntityId,
                                            Vec3 targetEyeOffset, Vec3 endEyeOffset) {
            return new PrecisionAttackPayload(
                    false, motionSeed, targetEntityId,
                    targetEyeOffset == null ? Vec3.ZERO : targetEyeOffset,
                    endEyeOffset == null ? Vec3.ZERO : endEyeOffset,
                    0.0D, Vec3.ZERO, Vec3.ZERO,
                    new Vec3[0], new Vec3[0]);
        }

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
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

    public record AttackSpeedStacksPayload(int stacks) implements CustomPacketPayload {
        public static final Type<AttackSpeedStacksPayload> TYPE = new Type<>(
                ResourceLocation.fromNamespaceAndPath(
                        BetterWhipsMod.MOD_ID, "destruction_whip_attack_speed_stacks"));
        public static final StreamCodec<RegistryFriendlyByteBuf, AttackSpeedStacksPayload> STREAM_CODEC =
                StreamCodec.of(
                        (buffer, payload) -> buffer.writeVarInt(payload.stacks),
                        buffer -> new AttackSpeedStacksPayload(buffer.readVarInt()));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record HitGlowBurstPayload(double x, double y, double z,
                                     double dx, double dy, double dz,
                                     boolean magicMirror, long seed)
            implements CustomPacketPayload {
        public static final Type<HitGlowBurstPayload> TYPE = new Type<>(
                ResourceLocation.fromNamespaceAndPath(
                        BetterWhipsMod.MOD_ID, "destruction_whip_hit_glow_burst"));
        public static final StreamCodec<RegistryFriendlyByteBuf, HitGlowBurstPayload> STREAM_CODEC =
                StreamCodec.of(
                        (buffer, payload) -> {
                            buffer.writeDouble(payload.x);
                            buffer.writeDouble(payload.y);
                            buffer.writeDouble(payload.z);
                            buffer.writeDouble(payload.dx);
                            buffer.writeDouble(payload.dy);
                            buffer.writeDouble(payload.dz);
                            buffer.writeBoolean(payload.magicMirror);
                            buffer.writeLong(payload.seed);
                        },
                        buffer -> new HitGlowBurstPayload(
                                buffer.readDouble(), buffer.readDouble(),
                                buffer.readDouble(), buffer.readDouble(),
                                buffer.readDouble(), buffer.readDouble(),
                                buffer.readBoolean(), buffer.readLong()));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record BodyLaserPayload(int ownerEntityId, int sourceSegment, int targetEntityId,
                                   double fallbackFromX, double fallbackFromY, double fallbackFromZ,
                                   double fallbackToX, double fallbackToY, double fallbackToZ,
                                   float fullWidth, long seed)
            implements CustomPacketPayload {
        public static final Type<BodyLaserPayload> TYPE = new Type<>(
                ResourceLocation.fromNamespaceAndPath(
                        BetterWhipsMod.MOD_ID, "destruction_whip_body_laser"));
        public static final StreamCodec<RegistryFriendlyByteBuf, BodyLaserPayload> STREAM_CODEC =
                StreamCodec.of(
                        (buffer, payload) -> {
                            buffer.writeVarInt(payload.ownerEntityId);
                            buffer.writeVarInt(payload.sourceSegment);
                            buffer.writeVarInt(payload.targetEntityId);
                            buffer.writeDouble(payload.fallbackFromX);
                            buffer.writeDouble(payload.fallbackFromY);
                            buffer.writeDouble(payload.fallbackFromZ);
                            buffer.writeDouble(payload.fallbackToX);
                            buffer.writeDouble(payload.fallbackToY);
                            buffer.writeDouble(payload.fallbackToZ);
                            buffer.writeFloat(payload.fullWidth);
                            buffer.writeLong(payload.seed);
                        },
                        buffer -> new BodyLaserPayload(
                                buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt(),
                                buffer.readDouble(), buffer.readDouble(), buffer.readDouble(),
                                buffer.readDouble(), buffer.readDouble(), buffer.readDouble(),
                                buffer.readFloat(), buffer.readLong()));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record WhipShockwavePayload(double x, double y, double z, long seed)
            implements CustomPacketPayload {
        public static final Type<WhipShockwavePayload> TYPE = new Type<>(
                ResourceLocation.fromNamespaceAndPath(
                        BetterWhipsMod.MOD_ID, "destruction_whip_shockwave"));
        public static final StreamCodec<RegistryFriendlyByteBuf, WhipShockwavePayload> STREAM_CODEC =
                StreamCodec.of(
                        (buffer, payload) -> {
                            buffer.writeDouble(payload.x);
                            buffer.writeDouble(payload.y);
                            buffer.writeDouble(payload.z);
                            buffer.writeLong(payload.seed);
                        },
                        buffer -> new WhipShockwavePayload(
                                buffer.readDouble(), buffer.readDouble(),
                                buffer.readDouble(), buffer.readLong()));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }
}
