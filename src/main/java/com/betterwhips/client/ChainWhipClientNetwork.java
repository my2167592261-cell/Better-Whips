package com.betterwhips.client;

import com.betterwhips.network.ChainWhipNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public final class ChainWhipClientNetwork {
    private ChainWhipClientNetwork() {}

    public static void handleAttackSpeedStacks(ChainWhipNetwork.AttackSpeedStacksPayload payload,
                                               IPayloadContext context) {

    }

    public static void handleHitGlowBurst(ChainWhipNetwork.HitGlowBurstPayload payload,
                                          IPayloadContext context) {
        context.enqueueWork(() -> ChainWhipHitEffects.spawnBurst(
                payload.x(), payload.y(), payload.z(), payload.seed()));
    }

    public static void handleShockwave(ChainWhipNetwork.WhipShockwavePayload payload,
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
