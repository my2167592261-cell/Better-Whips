package com.betterwhips.client;

import com.betterwhips.client.LightningWhipHitEffects;
import com.betterwhips.client.WhipShockwaveEffects;
import com.betterwhips.network.LightningWhipNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public final class LightningWhipClientNetwork {
    private LightningWhipClientNetwork() {
    }

    public static void handleAttackSpeedStacks(LightningWhipNetwork.AttackSpeedStacksPayload payload, IPayloadContext context) {
    }

    public static void handleHitGlowBurst(LightningWhipNetwork.HitGlowBurstPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> LightningWhipHitEffects.spawnBurst(payload.x(), payload.y(), payload.z(), payload.seed()));
    }

    public static void handleChainHit(LightningWhipNetwork.ChainHitPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> LightningWhipHitEffects.spawnChain(payload));
    }

    public static void handleDirectWrap(LightningWhipNetwork.DirectWrapPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> LightningWhipHitEffects.spawnDirectWrap(payload));
    }

    public static void handleShockwave(LightningWhipNetwork.WhipShockwavePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            ClientLevel level = Minecraft.getInstance().level;
            if (level != null) {
                WhipShockwaveEffects.spawnWhipShockwave(level, payload.x(), payload.y(), payload.z(), payload.seed());
            }
        });
    }
}
