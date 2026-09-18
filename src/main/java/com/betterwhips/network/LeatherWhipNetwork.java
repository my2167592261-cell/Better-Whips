package com.betterwhips.network;

import com.betterwhips.BetterWhipsMod;
import com.betterwhips.client.LeatherWhipClientNetwork;
import com.betterwhips.item.LeatherWhipCombat;
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

public final class LeatherWhipNetwork {

    private static final int WHIP_POINT_COUNT = 57;
    private static final PrecisionAttackPayload PRECISION_ATTACK = PrecisionAttackPayload.empty();

    private LeatherWhipNetwork() {}

    public static void register(RegisterPayloadHandlersEvent event) {

        PayloadRegistrar registrar = event.registrar("7");
        registrar.playToServer(
                PrecisionAttackPayload.TYPE,
                PrecisionAttackPayload.STREAM_CODEC,
                LeatherWhipNetwork::handlePrecisionAttack);
        registrar.playToClient(
                WhipShockwavePayload.TYPE,
                WhipShockwavePayload.STREAM_CODEC,
                LeatherWhipClientNetwork::handleShockwave);
        registrar.playToClient(
                AttackSpeedStacksPayload.TYPE,
                AttackSpeedStacksPayload.STREAM_CODEC,
                LeatherWhipClientNetwork::handleAttackSpeedStacks);
        registrar.playToClient(
                HitGlowBurstPayload.TYPE,
                HitGlowBurstPayload.STREAM_CODEC,
                LeatherWhipClientNetwork::handleHitGlowBurst);
    }

    public static void sendPrecisionAttack() {
        PacketDistributor.sendToServer(PRECISION_ATTACK);
    }

    public static void sendPrecisionAttack(Vec3[] points, Vec3[] previous,
                                           double substepSeconds, Vec3 handleAxis,
                                           Vec3 attackDirection) {
        if (points == null || previous == null
                || points.length != WHIP_POINT_COUNT || previous.length != WHIP_POINT_COUNT) {
            sendPrecisionAttack();
            return;
        }
        Vec3 axis = handleAxis == null ? Vec3.ZERO : handleAxis;
        Vec3 direction = attackDirection == null ? Vec3.ZERO : attackDirection;
        PacketDistributor.sendToServer(new PrecisionAttackPayload(
                true, substepSeconds, axis, direction, points.clone(), previous.clone()));
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

    public static void sendHitGlowBurst(ServerLevel level, Vec3 position, long seed) {
        if (level == null || position == null) {
            return;
        }
        HitGlowBurstPayload payload = new HitGlowBurstPayload(position.x, position.y, position.z, seed);
        for (ServerPlayer player : level.players()) {
            if (player.distanceToSqr(position) <= 128.0D * 128.0D) {
                PacketDistributor.sendToPlayer(player, payload);
            }
        }
    }

    private static void handlePrecisionAttack(PrecisionAttackPayload payload,
                                              IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player) {
            LeatherWhipCombat.PrecisionPoseSnapshot snapshot = payload.hasPose()
                    ? new LeatherWhipCombat.PrecisionPoseSnapshot(
                            payload.points(), payload.previous(), payload.substepSeconds(),
                            payload.handleAxis(), payload.attackDirection())
                    : null;
            LeatherWhipCombat.tryBeginPrecision(player, snapshot);
        }
    }

    public record PrecisionAttackPayload(boolean hasPose, double substepSeconds, Vec3 handleAxis,
                                         Vec3 attackDirection, Vec3[] points, Vec3[] previous)
            implements CustomPacketPayload {
        public static final Type<PrecisionAttackPayload> TYPE = new Type<>(
                ResourceLocation.fromNamespaceAndPath(
                        BetterWhipsMod.MOD_ID, "leather_whip_precision"));
        public static final StreamCodec<RegistryFriendlyByteBuf, PrecisionAttackPayload> STREAM_CODEC =
                StreamCodec.of(
                        (buffer, payload) -> {
                            buffer.writeBoolean(payload.hasPose);
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
                            if (!buffer.readBoolean()) {
                                return empty();
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
                                    true, substepSeconds, handleAxis, attackDirection, points, previous);
                        });

        static PrecisionAttackPayload empty() {
            return new PrecisionAttackPayload(
                    false, 0.0D, Vec3.ZERO, Vec3.ZERO, new Vec3[0], new Vec3[0]);
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
                        BetterWhipsMod.MOD_ID, "leather_whip_attack_speed_stacks"));
        public static final StreamCodec<RegistryFriendlyByteBuf, AttackSpeedStacksPayload> STREAM_CODEC =
                StreamCodec.of(
                        (buffer, payload) -> buffer.writeVarInt(payload.stacks),
                        buffer -> new AttackSpeedStacksPayload(buffer.readVarInt()));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record HitGlowBurstPayload(double x, double y, double z, long seed)
            implements CustomPacketPayload {
        public static final Type<HitGlowBurstPayload> TYPE = new Type<>(
                ResourceLocation.fromNamespaceAndPath(
                        BetterWhipsMod.MOD_ID, "leather_whip_hit_glow_burst"));
        public static final StreamCodec<RegistryFriendlyByteBuf, HitGlowBurstPayload> STREAM_CODEC =
                StreamCodec.of(
                        (buffer, payload) -> {
                            buffer.writeDouble(payload.x);
                            buffer.writeDouble(payload.y);
                            buffer.writeDouble(payload.z);
                            buffer.writeLong(payload.seed);
                        },
                        buffer -> new HitGlowBurstPayload(
                                buffer.readDouble(), buffer.readDouble(),
                                buffer.readDouble(), buffer.readLong()));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record WhipShockwavePayload(double x, double y, double z, long seed)
            implements CustomPacketPayload {
        public static final Type<WhipShockwavePayload> TYPE = new Type<>(
                ResourceLocation.fromNamespaceAndPath(
                        BetterWhipsMod.MOD_ID, "leather_whip_shockwave"));
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
