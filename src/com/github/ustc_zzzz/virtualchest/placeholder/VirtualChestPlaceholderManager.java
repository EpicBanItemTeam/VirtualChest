package com.github.ustc_zzzz.virtualchest.placeholder;

import com.github.ustc_zzzz.virtualchest.VirtualChestPlugin;
import com.github.ustc_zzzz.virtualchest.unsafe.SpongeUnimplemented;
import com.google.common.collect.Maps;
import com.google.gson.JsonPrimitive;
import me.rojo8399.placeholderapi.PlaceholderService;
import org.spongepowered.api.Sponge;
import org.spongepowered.api.entity.living.player.Player;
import org.spongepowered.api.service.ProviderRegistration;
import org.spongepowered.api.service.ServiceManager;
import org.spongepowered.api.text.Text;
import org.spongepowered.api.text.TextRepresentable;
import org.spongepowered.api.text.TextTemplate;
import org.spongepowered.api.text.serializer.TextSerializer;
import org.spongepowered.api.text.serializer.TextSerializers;

import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author ustc_zzzz
 */
public class VirtualChestPlaceholderManager
{
    private static final String ARG_BOUNDARY = "%";
    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("[%]([^%\\s]+)[%]", Pattern.CASE_INSENSITIVE);

    private final String papiVersion;
    private final PlaceholderService papiService;

    public VirtualChestPlaceholderManager(VirtualChestPlugin plugin)
    {
        ProviderRegistration<PlaceholderService> registration;
        ServiceManager serviceManager = Sponge.getServiceManager();
        plugin.getLogger().info("Try to load the PlaceholderAPI service ... ");
        registration = serviceManager.getRegistration(PlaceholderService.class).orElseThrow(RuntimeException::new);
        this.papiVersion = registration.getPlugin().getVersion().orElse("unknown");
        this.papiService = registration.getProvider();
    }

    public String getPlaceholderAPIVersion()
    {
        return this.papiVersion;
    }

    public Object replacePlaceholder(Player player, String token)
    {
        TextTemplate template = TextTemplate.of(ARG_BOUNDARY, ARG_BOUNDARY, TextTemplate.arg(token));
        return Objects.requireNonNull(this.papiService.fillPlaceholders(template, player, player).get(token));
    }

    public String parseJavaScriptLiteral(String text, String functionIdentifier)
    {
        StringBuilder builder = new StringBuilder();
        Matcher matcher = PLACEHOLDER_PATTERN.matcher(text);
        int lastIndex = 0;
        while (matcher.find())
        {
            String matched = new JsonPrimitive(text.substring(matcher.start() + 1, matcher.end() - 1)).toString();
            builder.append(text, lastIndex, matcher.start()).append(functionIdentifier);
            builder.append("(").append(matched).append(")");
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
        TextSerializer s = TextSerializers.FORMATTING_CODE;
        TextTemplate template = this.toTemplate(textToBeReplaced);
        Map<String, Object> placeholders = this.papiService.fillPlaceholders(template, player, player);
        return template.apply(Maps.transformValues(placeholders, v -> s.serialize(Text.of(v)))).build().toPlain();
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
