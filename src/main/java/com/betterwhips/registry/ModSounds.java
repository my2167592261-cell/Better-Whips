package com.betterwhips.registry;

import com.betterwhips.BetterWhipsMod;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModSounds {
    private ModSounds() {}

    public static final DeferredRegister<SoundEvent> SOUND_EVENTS =
            DeferredRegister.create(BuiltInRegistries.SOUND_EVENT, BetterWhipsMod.MOD_ID);

    public static final DeferredHolder<SoundEvent, SoundEvent> LANDING_QUAKE_1 =
            SOUND_EVENTS.register("landing_quake_1", SoundEvent::createVariableRangeEvent);
    public static final DeferredHolder<SoundEvent, SoundEvent> LANDING_QUAKE_2 =
            SOUND_EVENTS.register("landing_quake_2", SoundEvent::createVariableRangeEvent);
    public static final DeferredHolder<SoundEvent, SoundEvent> SHOCKWAVE =
            SOUND_EVENTS.register("shockwave", SoundEvent::createVariableRangeEvent);
    public static final DeferredHolder<SoundEvent, SoundEvent> LIGHTNING_ARC =
            SOUND_EVENTS.register("lightning_arc", SoundEvent::createVariableRangeEvent);
    public static final DeferredHolder<SoundEvent, SoundEvent> WHIP_SWING =
            SOUND_EVENTS.register("whip_swing", SoundEvent::createVariableRangeEvent);
    public static final DeferredHolder<SoundEvent, SoundEvent> WHIP_CRACK =
            SOUND_EVENTS.register("whip_crack", SoundEvent::createVariableRangeEvent);
    public static final DeferredHolder<SoundEvent, SoundEvent> CRYSTAL_WHIP_HIT =
            SOUND_EVENTS.register("crystal_whip_hit", SoundEvent::createVariableRangeEvent);
    public static final DeferredHolder<SoundEvent, SoundEvent> LIGHTNING_WHIP_HIT =
            SOUND_EVENTS.register("lightning_whip_hit", SoundEvent::createVariableRangeEvent);
}
