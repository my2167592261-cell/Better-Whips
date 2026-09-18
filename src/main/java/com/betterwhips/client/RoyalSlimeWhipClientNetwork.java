package com.betterwhips.client;

import com.betterwhips.network.RoyalSlimeWhipNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public final class RoyalSlimeWhipClientNetwork {
    private RoyalSlimeWhipClientNetwork() {}

    public static void handleAttackSpeedStacks(RoyalSlimeWhipNetwork.AttackSpeedStacksPayload payload,
                                               IPayloadContext context) {
        context.enqueueWork(() -> RoyalSlimeWhipHudOverlay.setStacks(payload.stacks()));
    }

    public static void handleHitGlowBurst(RoyalSlimeWhipNetwork.HitGlowBurstPayload payload,
                                          IPayloadContext context) {
        context.enqueueWork(() -> RoyalSlimeWhipHitEffects.spawnBurst(
                payload.x(), payload.y(), payload.z(), payload.seed()));
    }

    public static void handleShockwave(RoyalSlimeWhipNetwork.WhipShockwavePayload payload,
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
