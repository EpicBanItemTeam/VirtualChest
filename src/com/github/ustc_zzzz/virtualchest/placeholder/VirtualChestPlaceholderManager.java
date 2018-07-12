package com.github.ustc_zzzz.virtualchest.placeholder;

import com.github.ustc_zzzz.virtualchest.VirtualChestPlugin;
import com.github.ustc_zzzz.virtualchest.unsafe.PlaceholderAPIUtils;
import com.github.ustc_zzzz.virtualchest.unsafe.SpongeUnimplemented;
import org.slf4j.Logger;
import org.spongepowered.api.entity.living.player.Player;
import org.spongepowered.api.text.Text;
import org.spongepowered.api.text.TextRepresentable;
import org.spongepowered.api.text.TextTemplate;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author ustc_zzzz
 */
public class VirtualChestPlaceholderManager
{
    private static final String ARG_BOUNDARY = "%";
    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("[%]([^%\\s]+)[%]", Pattern.CASE_INSENSITIVE);

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

    public Object replacePlaceholder(Player player, String token)
    {
        TextTemplate template = TextTemplate.of(ARG_BOUNDARY, ARG_BOUNDARY, TextTemplate.arg(token));
        return Objects.requireNonNull(PlaceholderAPIUtils.fillPlaceholders(template, player).get(token));
    }

    public String parseJavaScriptLiteral(String text, String functionIdentifier)
    {
        StringBuilder builder = new StringBuilder();
        Matcher matcher = PLACEHOLDER_PATTERN.matcher(text);
        int lastIndex = 0;
        while (matcher.find())
        {
            String matched = SpongeUnimplemented.escapeString(text.substring(matcher.start() + 1, matcher.end() - 1));
            builder.append(text, lastIndex, matcher.start()).append(functionIdentifier);
            builder.append("('").append(matched).append("')");
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
        PlaceholderAPIUtils.fillPlaceholders(template, player).forEach((k, v) -> args.put(k, Text.of(v).toPlain()));
        return template.apply(args).build().toPlain();
    }

    private TextTemplate toTemplate(String text)
    {
        int lastIndex;
        List<TextRepresentable> parts = new LinkedList<>();
        Matcher matcher = PLACEHOLDER_PATTERN.matcher(text);
        for (lastIndex = 0; matcher.find(); lastIndex = matcher.end())
        {
            parts.add(Text.of(text.substring(lastIndex, matcher.start())));
            parts.add(TextTemplate.arg(text.substring(matcher.start() + 1, matcher.end() - 1)).build());
        }
        if (lastIndex < text.length())
        {
            parts.add(Text.builder(text.substring(lastIndex)).build());
        }
        return TextTemplate.of(ARG_BOUNDARY, ARG_BOUNDARY, parts.toArray());
    }
}
