package com.betterwhips.network;

import com.betterwhips.BetterWhipsMod;
import com.betterwhips.item.WindwhispererWhipCombat;
import com.betterwhips.physics.WindwhispererWhipDimensions;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public final class WindwhispererWhipNetwork {
    private static final int WHIP_POINT_COUNT = WindwhispererWhipDimensions.POINT_COUNT;
    private static final PrecisionAttackPayload PRECISION_ATTACK = PrecisionAttackPayload.empty();
    private WindwhispererWhipNetwork() {}

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("windwhisperer_whip_v1");
        registrar.playToServer(PrecisionAttackPayload.TYPE, PrecisionAttackPayload.STREAM_CODEC,
                WindwhispererWhipNetwork::handlePrecisionAttack);
    }

    public static void sendPrecisionAttack() { PacketDistributor.sendToServer(PRECISION_ATTACK); }

    public static void sendPrecisionAttack(Vec3[] points, Vec3[] previous,
                                           double substepSeconds, Vec3 handleAxis,
                                           Vec3 attackDirection) {
        if (points == null || previous == null
                || points.length != WHIP_POINT_COUNT || previous.length != WHIP_POINT_COUNT) {
            sendPrecisionAttack();
            return;
        }
        PacketDistributor.sendToServer(new PrecisionAttackPayload(true, substepSeconds,
                handleAxis == null ? Vec3.ZERO : handleAxis,
                attackDirection == null ? Vec3.ZERO : attackDirection,
                points.clone(), previous.clone()));
    }

    public static void sendShockwave(ServerLevel level, Vec3 impact, long seed) {}

    public static void sendHitGlowBurst(ServerLevel level, Vec3 position, Vec3 slashDirection,
                                        boolean magicMirror, long seed) {}

    private static void handlePrecisionAttack(PrecisionAttackPayload payload, IPayloadContext context) {
        if (context.player() instanceof net.minecraft.server.level.ServerPlayer player) {
            WindwhispererWhipCombat.PrecisionPoseSnapshot snapshot = payload.hasPose()
                    ? new WindwhispererWhipCombat.PrecisionPoseSnapshot(payload.points(), payload.previous(),
                            payload.substepSeconds(), payload.handleAxis(), payload.attackDirection())
                    : null;
            WindwhispererWhipCombat.tryBeginPrecision(player, snapshot);
        }
    }

    public record PrecisionAttackPayload(boolean hasPose, double substepSeconds, Vec3 handleAxis,
                                         Vec3 attackDirection, Vec3[] points, Vec3[] previous)
            implements CustomPacketPayload {
        public static final Type<PrecisionAttackPayload> TYPE = new Type<>(
                ResourceLocation.fromNamespaceAndPath(BetterWhipsMod.MOD_ID, "windwhisperer_whip_precision"));
        public static final StreamCodec<RegistryFriendlyByteBuf, PrecisionAttackPayload> STREAM_CODEC =
                StreamCodec.of((buffer, payload) -> {
                    buffer.writeBoolean(payload.hasPose);
                    if (!payload.hasPose) return;
                    buffer.writeDouble(payload.substepSeconds);
                    writeVec3(buffer, payload.handleAxis);
                    writeVec3(buffer, payload.attackDirection);
                    for (int i = 0; i < WHIP_POINT_COUNT; ++i) {
                        writeVec3(buffer, payload.points[i]);
                        writeVec3(buffer, payload.previous[i]);
                    }
                }, buffer -> {
                    if (!buffer.readBoolean()) return empty();
                    double dt = buffer.readDouble();
                    Vec3 axis = readVec3(buffer);
                    Vec3 direction = readVec3(buffer);
                    Vec3[] points = new Vec3[WHIP_POINT_COUNT];
                    Vec3[] previous = new Vec3[WHIP_POINT_COUNT];
                    for (int i = 0; i < WHIP_POINT_COUNT; ++i) {
                        points[i] = readVec3(buffer);
                        previous[i] = readVec3(buffer);
                    }
                    return new PrecisionAttackPayload(true, dt, axis, direction, points, previous);
                });

        static PrecisionAttackPayload empty() {
            return new PrecisionAttackPayload(false, 0.0D, Vec3.ZERO, Vec3.ZERO,
                    new Vec3[0], new Vec3[0]);
        }
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    private static void writeVec3(RegistryFriendlyByteBuf buffer, Vec3 value) {
        Vec3 safe = value == null ? Vec3.ZERO : value;
        buffer.writeDouble(safe.x); buffer.writeDouble(safe.y); buffer.writeDouble(safe.z);
    }
    private static Vec3 readVec3(RegistryFriendlyByteBuf buffer) {
        return new Vec3(buffer.readDouble(), buffer.readDouble(), buffer.readDouble());
    }
}
