package com.betterwhips.client;

import com.betterwhips.network.EmeraldWhipNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public final class EmeraldWhipClientNetwork {
    private EmeraldWhipClientNetwork() {}

    public static void handleAttackSpeedStacks(EmeraldWhipNetwork.AttackSpeedStacksPayload payload,
                                               IPayloadContext context) {

    }

    public static void handleEmeraldProjectile(EmeraldWhipNetwork.EmeraldProjectilePayload payload,
                                               IPayloadContext context) {
        context.enqueueWork(() -> EmeraldWhipProjectileEffects.spawn(
                payload.x(), payload.y(), payload.z(), payload.targetEntityId(), payload.seed()));
    }

    public static void handleHitGlowBurst(EmeraldWhipNetwork.HitGlowBurstPayload payload,
                                          IPayloadContext context) {
        context.enqueueWork(() -> EmeraldWhipHitEffects.spawnBurst(
                payload.x(), payload.y(), payload.z(), payload.seed()));
    }

    public static void handleShockwave(EmeraldWhipNetwork.WhipShockwavePayload payload,
                                       IPayloadContext context) {
        context.enqueueWork(() -> {
            ClientLevel level = Minecraft.getInstance().level;
            if (level != null) {
                WhipShockwaveEffects.spawnWhipShockwave(
                        level, payload.x(), payload.y(), payload.z(), payload.seed());
            }
        });
    }

}
