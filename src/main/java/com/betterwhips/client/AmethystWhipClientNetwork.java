package com.betterwhips.client;

import com.betterwhips.network.AmethystWhipNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public final class AmethystWhipClientNetwork {
    private AmethystWhipClientNetwork() {}

    public static void handleAttackSpeedStacks(AmethystWhipNetwork.AttackSpeedStacksPayload payload,
                                               IPayloadContext context) {

    }

    public static void handleAmethystProjectile(AmethystWhipNetwork.AmethystProjectilePayload payload,
                                                 IPayloadContext context) {
        context.enqueueWork(() -> AmethystWhipProjectileEffects.spawn(
                payload.x(), payload.y(), payload.z(),
                payload.vx(), payload.vy(), payload.vz(),
                payload.ignoredTargetEntityId(), payload.seed()));
    }

    public static void handleHitGlowBurst(AmethystWhipNetwork.HitGlowBurstPayload payload,
                                          IPayloadContext context) {
        context.enqueueWork(() -> AmethystWhipHitEffects.spawnBurst(
                payload.x(), payload.y(), payload.z(),
                payload.dx(), payload.dy(), payload.dz(), payload.magicMirror(), payload.seed()));
    }

    public static void handleShockwave(AmethystWhipNetwork.WhipShockwavePayload payload,
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
