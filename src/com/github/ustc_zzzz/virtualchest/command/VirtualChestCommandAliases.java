package com.github.ustc_zzzz.virtualchest.command;

import com.github.ustc_zzzz.virtualchest.VirtualChestPlugin;
import com.github.ustc_zzzz.virtualchest.inventory.VirtualChestInventory;
import com.github.ustc_zzzz.virtualchest.translation.VirtualChestTranslation;
import com.google.common.collect.ImmutableList;
import com.google.common.reflect.TypeToken;
import ninja.leaping.configurate.commented.CommentedConfigurationNode;
import ninja.leaping.configurate.objectmapping.ObjectMappingException;
import org.spongepowered.api.Sponge;
import org.spongepowered.api.command.*;
import org.spongepowered.api.command.args.CommandContext;
import org.spongepowered.api.command.args.GenericArguments;
import org.spongepowered.api.command.spec.CommandSpec;
import org.spongepowered.api.data.persistence.InvalidDataException;
import org.spongepowered.api.entity.living.player.Player;
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
        return aliasToMapping.keySet();
    }

    private void removeMapping(String alias) throws InvalidVirtualChestCommandAliasException
    {
        Optional<CommandMapping> mapping = Optional.ofNullable(aliasToMapping.get(alias));
        if (mapping.isPresent() && commandManager.containsMapping(mapping.get()))
        {
            commandManager.removeMapping(mapping.get());
        }
        else
        {
            throw new InvalidVirtualChestCommandAliasException("Command mapping does not exist for '" + alias + "'");
        }
    }

    private void insertMapping(String alias, CommandCallable callable)
    {
        if (commandManager.get(alias).isPresent())
        {
            throw new InvalidVirtualChestCommandAliasException("Command mapping has already exist for '" + alias + "'");
        }
        else
        {
            Optional<CommandMapping> mapping = commandManager.register(this.plugin, callable, alias);
            mapping.ifPresent(m -> aliasToMapping.put(alias, m));
        }
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
            node.getNode("menu-example").setValue("example");
            node.getNode("menu-example2").setValue("example2");
        }
        try
        {
            for (String alias : getRegisteredAliases())
            {
                removeMapping(alias);
            }
            for (Map.Entry<Object, ? extends CommentedConfigurationNode> entry : node.getChildrenMap().entrySet())
            {
                String alias = String.valueOf(entry.getKey());
                String guiName = entry.getValue().getString();
                CommandSpec callable = CommandSpec.builder()
                        .arguments(GenericArguments.playerOrSource(Text.of("player")))
                        .description(translation.take("virtualchest.commandAlias.description", alias, guiName))
                        .executor((src, args) -> this.processAliasCommand(guiName, src, args)).build();
                insertMapping(alias, callable);
            }
        }
        catch (InvalidVirtualChestCommandAliasException e)
        {
            throw new IOException(e);
        }
    }

    public void saveConfig(CommentedConfigurationNode node) throws IOException
    {
        node.setComment(node.getComment().orElse(this.translation
                .take("virtualchest.config.commandAliases.comment").toPlain()));
    }

    public static class InvalidVirtualChestCommandAliasException extends InvalidDataException
    {
        public InvalidVirtualChestCommandAliasException()
        {
            super();
        }

        public InvalidVirtualChestCommandAliasException(String errorMessage)
        {
            super(errorMessage);
        }
    }
}
