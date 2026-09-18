package com.betterwhips.item;

import com.betterwhips.registry.ModEffects;
import com.betterwhips.registry.ModItems;
import net.minecraft.core.Holder;
import net.minecraft.world.Difficulty;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class SeaRippleWhipProtection {
    private static final int PROTECTION_TICKS=5*20;
    private static final int COOLDOWN_TICKS=25*20;
    private static final int READY_MARKER_REFRESH=30;
    private static final Map<UUID,State> STATES=new HashMap<>();

    private SeaRippleWhipProtection() {}

    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        if(!(event.getEntity() instanceof ServerPlayer player) || !holding(player) || !(event.getAmount()>0.0F))return;
        long now=now(player);
        State state=STATES.computeIfAbsent(player.getUUID(),id->new State());
        float reduction=difficultyReduction(player.level().getDifficulty());

        if(now<state.protectionUntil) {
            event.setAmount(Math.max(0.0F,event.getAmount()-reduction));
            return;
        }

        if(now<state.cooldownUntil)return;

        event.setAmount(Math.max(0.0F,event.getAmount()-reduction));
        state.protectionUntil=now+PROTECTION_TICKS;
        state.cooldownUntil=now+COOLDOWN_TICKS;
        player.removeEffect(ModEffects.WATER_PROTECTION_READY);
        applyMarker(player,ModEffects.WATER_PROTECTION_COOLDOWN,COOLDOWN_TICKS);
    }

    public static void onServerTick(ServerTickEvent.Post event) {
        long now=event.getServer().overworld().getGameTime();
        for(ServerPlayer player:event.getServer().getPlayerList().getPlayers()) {
            State state=STATES.computeIfAbsent(player.getUUID(),id->new State());
            syncMarkers(player,state,now);
        }
    }

    public static void onServerStopped(ServerStoppedEvent event) {
        STATES.clear();
    }

    private static long now(ServerPlayer player) {
        return player.serverLevel().getServer().overworld().getGameTime();
    }

    private static void syncMarkers(ServerPlayer player,State state,long now) {
        if(!holding(player) || !player.isAlive() || player.isSpectator()) {
            clearMarkers(player);
            return;
        }

        if(now<state.cooldownUntil) {
            player.removeEffect(ModEffects.WATER_PROTECTION_READY);
            int remaining=(int)Math.max(1L,Math.min(Integer.MAX_VALUE,state.cooldownUntil-now));
            ensureCountdownMarker(player,ModEffects.WATER_PROTECTION_COOLDOWN,remaining);
            return;
        }

        player.removeEffect(ModEffects.WATER_PROTECTION_COOLDOWN);
        ensureReadyMarker(player);
    }

    private static void ensureReadyMarker(ServerPlayer player) {
        MobEffectInstance current=player.getEffect(ModEffects.WATER_PROTECTION_READY);
        if(current==null || current.getDuration()<=10)
            applyMarker(player,ModEffects.WATER_PROTECTION_READY,READY_MARKER_REFRESH);
    }

    private static void ensureCountdownMarker(ServerPlayer player,Holder<MobEffect> effect,int remaining) {
        MobEffectInstance current=player.getEffect(effect);
        if(current==null || Math.abs(current.getDuration()-remaining)>5)applyMarker(player,effect,remaining);
    }

    private static void applyMarker(ServerPlayer player,Holder<MobEffect> effect,int duration) {
        player.addEffect(new MobEffectInstance(effect,duration,0,true,false,true));
    }

    private static void clearMarkers(ServerPlayer player) {
        player.removeEffect(ModEffects.WATER_PROTECTION_READY);
        player.removeEffect(ModEffects.WATER_PROTECTION_COOLDOWN);
    }

    private static boolean holding(ServerPlayer player) {
        return player.getMainHandItem().is(ModItems.UNTAMED_SEA_WHIP.get());
    }

    private static float difficultyReduction(Difficulty difficulty) {
        return switch(difficulty) {
            case HARD -> 12.0F;
            case NORMAL -> 8.0F;
            case EASY, PEACEFUL -> 5.0F;
        };
    }

    private static final class State {
        long protectionUntil;
        long cooldownUntil;
    }
}
