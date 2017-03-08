package com.github.ustc_zzzz.virtualchest.action;

import org.spongepowered.api.command.CommandResult;
import org.spongepowered.api.entity.living.player.Player;

import java.util.function.Consumer;

/**
 * @author ustc_zzzz
 */
public interface VirtualChestActionExecutor
{
    void doAction(Player player, String command, Consumer<CommandResult> callback);
}
