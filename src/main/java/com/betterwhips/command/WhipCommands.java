package com.betterwhips.command;

import com.betterwhips.item.WhipDamageDebug;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

public final class WhipCommands {
    private WhipCommands() {}

    public static void register(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        dispatcher.register(Commands.literal("betterwhips")
                .then(Commands.literal("damage")
                        .executes(ctx -> set(ctx.getSource().getPlayerOrException(), null))
                        .then(Commands.literal("on")
                                .executes(ctx -> set(ctx.getSource().getPlayerOrException(), true)))
                        .then(Commands.literal("off")
                                .executes(ctx -> set(ctx.getSource().getPlayerOrException(), false)))));
    }

    private static int set(ServerPlayer player, Boolean enabled) {
        boolean value = enabled == null ? WhipDamageDebug.toggle(player)
                : WhipDamageDebug.setEnabled(player, enabled);
        player.sendSystemMessage(Component.translatable(value
                ? "command.better_whips.damage.enabled"
                : "command.better_whips.damage.disabled"));
        return value ? 1 : 0;
    }
}
