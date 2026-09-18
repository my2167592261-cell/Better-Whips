package com.betterwhips.item;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class WhipDamageDebug {
    private static final long DPS_WINDOW_TICKS = 20L;
    private static final Map<UUID, State> STATES = new HashMap<>();

    private WhipDamageDebug() {}

    public static boolean isEnabled(ServerPlayer player) {
        State state = STATES.get(player.getUUID());
        return state != null && state.enabled;
    }

    public static boolean setEnabled(ServerPlayer player, boolean enabled) {
        State state = STATES.computeIfAbsent(player.getUUID(), ignored -> new State());
        state.enabled = enabled;
        if (!enabled) {
            state.hits.clear();
        }
        return enabled;
    }

    public static boolean toggle(ServerPlayer player) {
        return setEnabled(player, !isEnabled(player));
    }

    public static void record(net.minecraft.world.entity.player.Player owner, float actualDamage) {
        if (!(owner instanceof ServerPlayer player)) {
            return;
        }
        State state = STATES.get(player.getUUID());
        if (state == null || !state.enabled) {
            return;
        }

        long now = player.serverLevel().getGameTime();
        float clamped = Math.max(0.0F, actualDamage);
        state.hits.addLast(new Hit(now, clamped));
        while (!state.hits.isEmpty() && state.hits.peekFirst().tick < now - (DPS_WINDOW_TICKS - 1L)) {
            state.hits.removeFirst();
        }

        float dps = 0.0F;
        for (Hit hit : state.hits) {
            dps += hit.damage;
        }
        player.displayClientMessage(Component.literal(
                String.format(java.util.Locale.ROOT, "单次伤害 %.2f  |  DPS %.2f", clamped, dps)), true);
    }

    private static final class State {
        boolean enabled;
        final ArrayDeque<Hit> hits = new ArrayDeque<>();
    }

    private record Hit(long tick, float damage) {}
}
