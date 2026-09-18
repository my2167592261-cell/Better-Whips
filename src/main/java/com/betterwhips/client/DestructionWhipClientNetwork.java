package com.betterwhips.client;

import com.betterwhips.network.DestructionWhipNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public final class DestructionWhipClientNetwork {
    private DestructionWhipClientNetwork() {}

    public static void handleAttackSpeedStacks(DestructionWhipNetwork.AttackSpeedStacksPayload payload,
                                               IPayloadContext context) {

    }

    public static void handleHitGlowBurst(DestructionWhipNetwork.HitGlowBurstPayload payload,
                                          IPayloadContext context) {
        context.enqueueWork(() -> DestructionWhipHitEffects.spawnBurst(
                payload.x(), payload.y(), payload.z(),
                payload.dx(), payload.dy(), payload.dz(), payload.magicMirror(), payload.seed()));
    }

    public static void handleBodyLaser(DestructionWhipNetwork.BodyLaserPayload payload,
                                       IPayloadContext context) {
        context.enqueueWork(() -> DestructionWhipHitEffects.spawnLaser(
                payload.ownerEntityId(), payload.sourceSegment(), payload.targetEntityId(),
                payload.fallbackFromX(), payload.fallbackFromY(), payload.fallbackFromZ(),
                payload.fallbackToX(), payload.fallbackToY(), payload.fallbackToZ(),
                payload.fullWidth(), payload.seed()));
    }

    public static void handleShockwave(DestructionWhipNetwork.WhipShockwavePayload payload,
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
