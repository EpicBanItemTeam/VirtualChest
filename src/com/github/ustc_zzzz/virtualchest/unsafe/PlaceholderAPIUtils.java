package com.github.ustc_zzzz.virtualchest.unsafe;

import com.google.common.base.Throwables;
import com.google.common.collect.Maps;
import org.spongepowered.api.Sponge;
import org.spongepowered.api.entity.living.player.Player;
import org.spongepowered.api.plugin.PluginContainer;
import org.spongepowered.api.plugin.PluginManager;
import org.spongepowered.api.text.TextTemplate;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.Map;

/**
 * @author ustc_zzzz
 */
public class PlaceholderAPIUtils
{
    private static final String API_VERSION;
    private static final Class<?> API_SERVICE;
    private static final MethodHandle API_FILL_PLACEHOLDERS;

    public static String getPlaceholderAPIVersion()
    {
        return API_VERSION.isEmpty() ? "unknown" : API_VERSION;
    }

    public static boolean isPlaceholderAPIAvailable()
    {
        return !API_VERSION.isEmpty();
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> fillPlaceholders(TextTemplate template, Player player)
    {
        try
        {
            if (API_VERSION.isEmpty())
            {
                String openArg = template.getOpenArgString(), closeArg = template.getCloseArgString();
                return Maps.asMap(template.getArguments().keySet(), argument -> openArg + argument + closeArg);
            }
            else if (API_VERSION.startsWith("3."))
            {
                Object service = Sponge.getServiceManager().provideUnchecked(API_SERVICE);
                return (Map<String, Object>) API_FILL_PLACEHOLDERS.invoke(service, player, template);
            }
            else if (API_VERSION.startsWith("4."))
            {
                Object service = Sponge.getServiceManager().provideUnchecked(API_SERVICE);
                return (Map<String, Object>) API_FILL_PLACEHOLDERS.invoke(service, template, player, player);
            }
            else
            {
                // I really don't know what happened, maybe it is 5.x
                Object service = Sponge.getServiceManager().provideUnchecked(API_SERVICE);
                return (Map<String, Object>) API_FILL_PLACEHOLDERS.invoke(service, template, player, player);
            }
        }
        catch (Throwable throwable)
        {
            throw new UnsupportedOperationException(throwable);
        }
    }

    static
    {
        try
        {
            PluginManager pluginManager = Sponge.getPluginManager();
            API_VERSION = pluginManager.getPlugin("placeholderapi").flatMap(PluginContainer::getVersion).orElse("");

            MethodHandles.Lookup lookup = MethodHandles.publicLookup();
            if (API_VERSION.isEmpty())
            {
                API_SERVICE = null;
                API_FILL_PLACEHOLDERS = null;
            }
            else if (API_VERSION.startsWith("3."))
            {
                API_SERVICE = Class.forName("me.rojo8399.placeholderapi.PlaceholderService");
                MethodType type = MethodType.methodType(Map.class, Player.class, TextTemplate.class);
                API_FILL_PLACEHOLDERS = lookup.findVirtual(API_SERVICE, "fillPlaceholders", type);
            }
            else if (API_VERSION.startsWith("4."))
            {
                API_SERVICE = Class.forName("me.rojo8399.placeholderapi.PlaceholderService");
                MethodType type = MethodType.methodType(Map.class, TextTemplate.class, Object.class, Object.class);
                API_FILL_PLACEHOLDERS = lookup.findVirtual(API_SERVICE, "fillPlaceholders", type);
            }
            else
            {
                // I really don't know what happened, maybe it is 5.x
                API_SERVICE = Class.forName("me.rojo8399.placeholderapi.PlaceholderService");
                MethodType type = MethodType.methodType(Map.class, TextTemplate.class, Object.class, Object.class);
                API_FILL_PLACEHOLDERS = lookup.findVirtual(API_SERVICE, "fillPlaceholders", type);
            }
        }
        catch (ReflectiveOperationException e)
        {
            throw Throwables.propagate(e);
        }
    }

    private PlaceholderAPIUtils()
    {
    }
}
