package com.betterwhips.registry;

import com.betterwhips.BetterWhipsMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModEffects {
    public static final DeferredRegister<MobEffect> MOB_EFFECTS =
            DeferredRegister.create(Registries.MOB_EFFECT, BetterWhipsMod.MOD_ID);

    public static final DeferredHolder<MobEffect, MobEffect> WATER_PROTECTION_READY =
            MOB_EFFECTS.register("water_protection_ready",
                    () -> new MarkerEffect(MobEffectCategory.BENEFICIAL, 0x45DFF4));

    public static final DeferredHolder<MobEffect, MobEffect> WATER_PROTECTION_COOLDOWN =
            MOB_EFFECTS.register("water_protection_cooldown",
                    () -> new MarkerEffect(MobEffectCategory.NEUTRAL, 0x4B7182));

    private ModEffects() {}

    private static final class MarkerEffect extends MobEffect {
        private MarkerEffect(MobEffectCategory category, int color) {
            super(category, color);
        }
    }
}
