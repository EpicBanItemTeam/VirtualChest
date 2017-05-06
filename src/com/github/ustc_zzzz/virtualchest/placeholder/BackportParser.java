package com.github.ustc_zzzz.virtualchest.placeholder;

import org.spongepowered.api.entity.living.player.Player;
import org.spongepowered.api.util.Tuple;

import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * @author ustc_zzzz
 */
class BackportParser implements VirtualChestPlaceholderManager.Parser
{
    private final Set<Tuple<Pattern, Function<Player, String>>> placeholders;

    BackportParser(Map<String, Function<Player, String>> placeholders)
    {
        this.placeholders = placeholders.entrySet().stream()
                .map(e -> new Tuple<>(Pattern.compile(e.getKey(), Pattern.LITERAL), e.getValue()))
                .collect(Collectors.toSet());
    }

    @Override
    public String replace(Player player, String textToBeReplaced, Function<? super String, String> transformation)
    {
        for (Tuple<Pattern, Function<Player, String>> entry : placeholders)
        {
            String replacement = transformation.apply(entry.getSecond().apply(player));
            textToBeReplaced = entry.getFirst().matcher(textToBeReplaced).replaceAll(replacement);
        }
        return textToBeReplaced;
    }
}
