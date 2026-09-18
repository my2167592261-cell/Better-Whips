package com.betterwhips.client;

import com.betterwhips.network.GoldenHeavyWhipNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public final class GoldenHeavyWhipClientNetwork {
    private GoldenHeavyWhipClientNetwork() {}

    public static void handleAttackSpeedStacks(GoldenHeavyWhipNetwork.AttackSpeedStacksPayload payload,
                                               IPayloadContext context) {

    }

    public static void handleHitGlowBurst(GoldenHeavyWhipNetwork.HitGlowBurstPayload payload,
                                          IPayloadContext context) {
        context.enqueueWork(() -> GoldenHeavyWhipHitEffects.spawnBurst(
                payload.x(), payload.y(), payload.z(), payload.seed()));
    }

    public static void handleShockwave(GoldenHeavyWhipNetwork.WhipShockwavePayload payload,
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
