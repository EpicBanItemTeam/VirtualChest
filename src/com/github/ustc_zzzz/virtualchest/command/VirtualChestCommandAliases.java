package com.github.ustc_zzzz.virtualchest.command;

import com.github.ustc_zzzz.virtualchest.VirtualChestPlugin;
import com.github.ustc_zzzz.virtualchest.translation.VirtualChestTranslation;
import com.google.common.collect.ImmutableSet;
import ninja.leaping.configurate.commented.CommentedConfigurationNode;
import org.spongepowered.api.Sponge;
import org.spongepowered.api.command.*;
import org.spongepowered.api.command.args.CommandContext;
import org.spongepowered.api.command.args.GenericArguments;
import org.spongepowered.api.command.spec.CommandSpec;
import org.spongepowered.api.data.persistence.InvalidDataException;
import org.spongepowered.api.entity.living.player.Player;
import org.spongepowered.api.plugin.PluginContainer;
import org.spongepowered.api.text.Text;

import java.io.IOException;
import java.util.*;

/**
 * @author ustc_zzzz
 */
public class VirtualChestCommandAliases
{
    private final VirtualChestPlugin plugin;
    private final CommandManager commandManager = Sponge.getCommandManager();
    private final Map<String, CommandMapping> aliasToMapping = new LinkedHashMap<>();
    private final VirtualChestTranslation translation;

    public VirtualChestCommandAliases(VirtualChestPlugin plugin)
    {
        this.plugin = plugin;
        this.translation = plugin.getTranslation();
    }

    private Set<String> getRegisteredAliases()
    {
        return ImmutableSet.copyOf(aliasToMapping.keySet());
    }

    private void removeMapping(String alias)
    {
        Optional<CommandMapping> mapping = Optional.ofNullable(aliasToMapping.remove(alias));
        if (!mapping.isPresent() || !commandManager.containsMapping(mapping.get()))
        {
            throw new InvalidDataException("Command mapping does not exist for '" + alias + "'");
        }
        commandManager.removeMapping(mapping.get());
    }

    private void insertMapping(String alias, CommandCallable callable)
    {
        Optional<CommandMapping> mapping = commandManager.register(this.plugin, callable, alias);
        if (aliasToMapping.containsKey(alias) || !mapping.isPresent())
        {
            throw new InvalidDataException("Command mapping has already existed for '" + alias + "'");
        }
        aliasToMapping.put(alias, testMapping(alias, mapping.get()));
    }

    private CommandMapping testMapping(String alias, CommandMapping mapping)
    {
        if (!commandManager.get(alias).get().equals(mapping))
        {
            PluginContainer pluginContainer = commandManager.get(alias).flatMap(commandManager::getOwner).get();
            this.plugin.getLogger().warn("The command '" + alias + "' is not actually registered because " +
                    "it conflicts with the command '" + pluginContainer.getId() + ":" + alias + "'.");
            this.plugin.getLogger().warn("Because of the low priority this command could only be used with " +
                    "a prefix such as '" + VirtualChestPlugin.PLUGIN_ID + ":" + alias + "'.");
            this.plugin.getLogger().warn("Please configure the part of command aliases in " +
                    "'config/sponge/global.conf' to enable it manually:");
            this.plugin.getLogger().warn("commands {");
            this.plugin.getLogger().warn("    aliases {");
            this.plugin.getLogger().warn("        " + alias + "=" + VirtualChestPlugin.PLUGIN_ID);
            this.plugin.getLogger().warn("    }");
            this.plugin.getLogger().warn("}");
            this.plugin.getLogger().warn("For more information about command priorities, please refer to:");
            this.plugin.getLogger().warn("https://docs.spongepowered.org/" +
                    "stable/en/server/getting-started/configuration/sponge-conf.html");
        }
        return mapping;
    }

    private CommandSpec generateCallable(String alias, String guiName)
    {
        return CommandSpec.builder()
                .arguments(GenericArguments.playerOrSource(Text.of("player")))
                .description(translation.take("virtualchest.commandAlias.description", alias, guiName))
                .executor((src, args) -> this.processAliasCommand(guiName, src, args)).build();
    }

    private CommandResult processAliasCommand(String name, CommandSource src, CommandContext args) throws CommandException
    {
        Collection<Player> players = args.getAll("player");
        for (Player player : players)
        {
            commandManager.process(src, "virtualchest open " + name + " " + player.getName());
        }
        return CommandResult.success();
    }

    public void loadConfig(CommentedConfigurationNode node) throws IOException
    {
        if (node.isVirtual() || node.getChildrenMap().isEmpty())
        {
            node.getNode("m-e").setValue("example");
            node.getNode("m-e2").setValue("example2");
            node.getNode("menu-example").setValue("example");
            node.getNode("menu-example2").setValue("example2");
        }
        for (String alias : getRegisteredAliases())
        {
            removeMapping(alias);
        }
        for (Map.Entry<Object, ? extends CommentedConfigurationNode> entry : node.getChildrenMap().entrySet())
        {
            String alias = String.valueOf(entry.getKey());
            String guiName = entry.getValue().getString();
            insertMapping(alias, generateCallable(alias, guiName));
        }
    }

    public void saveConfig(CommentedConfigurationNode node) throws IOException
    {
        node.setComment(node.getComment().orElse(this.translation
                .take("virtualchest.config.commandAliases.comment").toPlain()));
    }
}
