package com.github.ustc_zzzz.virtualchest.placeholder;

import me.rojo8399.placeholderapi.PlaceholderService;
import org.spongepowered.api.entity.living.player.Player;
import org.spongepowered.api.service.ServiceManager;
import org.spongepowered.api.text.Text;
import org.spongepowered.api.text.TextRepresentable;
import org.spongepowered.api.text.TextTemplate;
import org.spongepowered.api.text.serializer.TextSerializer;
import org.spongepowered.api.text.serializer.TextSerializers;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author ustc_zzzz
 */
class PlaceholderAPIParser implements VirtualChestPlaceholderManager.Parser
{
    private static final TextSerializer PLAIN_FORMATTER = TextSerializers.PLAIN;
    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("[%]([^%]+)[%]", Pattern.CASE_INSENSITIVE);

    private final PlaceholderService placeholderService;

    PlaceholderAPIParser(ServiceManager serviceManager)
    {
        this.placeholderService = serviceManager.provideUnchecked(PlaceholderService.class);
    }

    private TextTemplate toTemplate(String text)
    {
        List<TextRepresentable> parts = new LinkedList<>();
        Matcher matcher = PLACEHOLDER_PATTERN.matcher(text);
        int lastIndex = 0;
        while (matcher.find())
        {
            parts.add(PLAIN_FORMATTER.deserialize(text.substring(lastIndex, matcher.start())));
            parts.add(TextTemplate.arg(text.substring(matcher.start() + 1, matcher.end() - 1)).build());
            lastIndex = matcher.end();
        }
        if (lastIndex < text.length())
        {
            parts.add(Text.builder(text.substring(lastIndex)).build());
        }
        return TextTemplate.of("%", "%", parts.toArray());
    }

    @Override
    public String replace(Player player, String textToBeReplaced, Function<? super String, String> transformation)
    {
        Map<String, Object> args = new HashMap<>();
        TextTemplate template = this.toTemplate(textToBeReplaced);
        for (Map.Entry<String, Object> entry : this.placeholderService.fillPlaceholders(player, template).entrySet())
        {
            String replacement = PLAIN_FORMATTER.serialize(Text.of(entry.getValue()));
            args.put(entry.getKey(), transformation.apply(replacement));
        }
        return PLAIN_FORMATTER.serialize(template.apply(args).build());
    }
}
