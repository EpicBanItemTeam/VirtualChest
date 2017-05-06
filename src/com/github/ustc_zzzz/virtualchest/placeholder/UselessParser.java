package com.github.ustc_zzzz.virtualchest.placeholder;

import org.spongepowered.api.entity.living.player.Player;

import java.util.function.Function;

/**
 * @author ustc_zzzz
 */
class UselessParser implements VirtualChestPlaceholderManager.Parser
{
    @Override
    public String replace(Player player, String text, Function<? super String, String> replacementTransformation)
    {
        return text;
    }
}
