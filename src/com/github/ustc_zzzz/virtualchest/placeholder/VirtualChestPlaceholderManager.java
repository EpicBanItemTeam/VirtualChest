package com.github.ustc_zzzz.virtualchest.placeholder;

import com.github.ustc_zzzz.virtualchest.VirtualChestPlugin;
import com.github.ustc_zzzz.virtualchest.unsafe.PlaceholderAPIUtils;
import org.apache.commons.lang3.StringEscapeUtils;
import org.slf4j.Logger;
import org.spongepowered.api.entity.living.player.Player;
import org.spongepowered.api.text.Text;
import org.spongepowered.api.text.TextRepresentable;
import org.spongepowered.api.text.TextTemplate;
import org.spongepowered.api.text.serializer.TextSerializers;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author ustc_zzzz
 */
public class VirtualChestPlaceholderManager
{
    private static final String ARG_BOUNDARY = "%";
    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("[%]([^%]+)[%]", Pattern.CASE_INSENSITIVE);

    private final Logger logger;

    public VirtualChestPlaceholderManager(VirtualChestPlugin plugin)
    {
        this.logger = plugin.getLogger();
    }

    public void init()
    {
        this.logger.info("Try to load the PlaceholderAPI service ... ");
        if (!PlaceholderAPIUtils.isPlaceholderAPIAvailable())
        {
            this.logger.warn("VirtualChest could not find the PlaceholderAPI service. ");
            this.logger.warn("Features related to PlaceholderAPI may not work normally. ");
            this.logger.warn("Maybe you should look for a PlaceholderAPI plugin and download it?");
        }
    }

    public Map<String, Object> getPlaceholderAPIMap(Player player, String textToBeReplaced)
    {
        TextTemplate template = this.toTemplate(textToBeReplaced);
        return PlaceholderAPIUtils.fillPlaceholders(template, player);
    }

    public String parseJavaScriptLiteral(String text, String functionIdentifier)
    {
        StringBuilder builder = new StringBuilder();
        Matcher matcher = PLACEHOLDER_PATTERN.matcher(text);
        int lastIndex = 0;
        while (matcher.find())
        {
            // TODO: depends on apache commons, should be replaced with what is implemented by the plugin itself
            String matched = StringEscapeUtils.escapeEcmaScript(text.substring(matcher.start() + 1, matcher.end() - 1));
            builder.append(text.substring(lastIndex, matcher.start()));
            builder.append(functionIdentifier).append("('").append(matched).append("')");
            lastIndex = matcher.end();
        }
        if (lastIndex < text.length())
        {
            builder.append(text.substring(lastIndex));
        }
        return builder.toString();
    }

    public String parseText(Player player, String textToBeReplaced)
    {
        Map<String, Object> args = new HashMap<>();
        TextTemplate template = this.toTemplate(textToBeReplaced);
        for (Map.Entry<String, Object> entry : PlaceholderAPIUtils.fillPlaceholders(template, player).entrySet())
        {
            args.put(entry.getKey(), TextSerializers.PLAIN.serialize(Text.of(entry.getValue())));
        }
        return TextSerializers.PLAIN.serialize(template.apply(args).build());
    }

    private TextTemplate toTemplate(String text)
    {
        List<TextRepresentable> parts = new LinkedList<>();
        Matcher matcher = PLACEHOLDER_PATTERN.matcher(text);
        int lastIndex = 0;
        while (matcher.find())
        {
            parts.add(TextSerializers.PLAIN.deserialize(text.substring(lastIndex, matcher.start())));
            parts.add(TextTemplate.arg(text.substring(matcher.start() + 1, matcher.end() - 1)).build());
            lastIndex = matcher.end();
        }
        if (lastIndex < text.length())
        {
            parts.add(Text.builder(text.substring(lastIndex)).build());
        }
        return TextTemplate.of(ARG_BOUNDARY, ARG_BOUNDARY, parts.toArray());
    }
}
