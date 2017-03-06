package com.github.ustc_zzzz.virtualchest.action;

import com.github.ustc_zzzz.virtualchest.VirtualChestPlugin;
import org.spongepowered.api.Sponge;
import org.spongepowered.api.command.CommandResult;
import org.spongepowered.api.entity.living.player.Player;
import org.spongepowered.api.text.Text;
import org.spongepowered.api.text.chat.ChatTypes;
import org.spongepowered.api.text.serializer.TextSerializers;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;

/**
 * @author ustc_zzzz
 */
public final class VirtualChestActions
{
    private static final char SEQUENCE_SPLITTER = ';';
    private static final char PREFIX_SPLITTER = ':';

    private final VirtualChestPlugin plugin;
    private final Map<String, BiFunction<Player, String, CommandResult>> executors = new HashMap<>();

    public VirtualChestActions(VirtualChestPlugin plugin)
    {
        this.plugin = plugin;

        registerPrefix("console", this::processConsole);
        registerPrefix("tell", this::processTell);
        registerPrefix("broadcast", this::processBroadcast);
        registerPrefix("title", this::processTitle);
    }

    public void registerPrefix(String prefix, BiFunction<Player, String, CommandResult> executor)
    {
        this.executors.put(prefix, executor);
    }

    public void runCommand(Player player, String commandString)
    {
        List<String> commands = parseCommand(commandString);

        for (String command : commands)
        {
            int colonPos = command.indexOf(PREFIX_SPLITTER);
            String prefix = colonPos > 0 ? command.substring(0, colonPos) : "";
            if (this.executors.containsKey(prefix))
            {
                int suffixPosition = colonPos + 1;
                while (Character.isWhitespace(command.charAt(suffixPosition)))
                {
                    ++suffixPosition;
                }
                String suffix = command.substring(suffixPosition);
                this.executors.get(prefix).apply(player, suffix);
            }
            else
            {
                Sponge.getCommandManager().process(player, command);
            }
        }
    }

    private CommandResult processTitle(Player player, String command)
    {
        Text text = TextSerializers.FORMATTING_CODE.deserialize(command);
        player.sendMessage(ChatTypes.ACTION_BAR, text);
        return CommandResult.success();
    }

    private CommandResult processBroadcast(Player player, String command)
    {
        Text text = TextSerializers.FORMATTING_CODE.deserialize(command);
        Sponge.getServer().getBroadcastChannel().send(text);
        return CommandResult.success();
    }

    private CommandResult processTell(Player player, String command)
    {
        player.sendMessage(TextSerializers.FORMATTING_CODE.deserialize(command));
        return CommandResult.success();
    }

    private CommandResult processConsole(Player player, String command)
    {
        return Sponge.getCommandManager().process(Sponge.getServer().getConsole(), command);
    }

    private static List<String> parseCommand(String commandSequence)
    {
        StringBuilder stringBuilder = new StringBuilder();
        List<String> commands = new LinkedList<>();
        int i = 0, length = commandSequence.length();
        while (i < length)
        {
            char c = commandSequence.charAt(i);
            if (c != SEQUENCE_SPLITTER)
            {
                stringBuilder.append(c);
                ++i;
            }
            else if (++i < length)
            {
                char next = commandSequence.charAt(i);
                if (next != SEQUENCE_SPLITTER)
                {
                    while (i < length && Character.isWhitespace(commandSequence.charAt(i)))
                    {
                        ++i;
                    }
                    if (stringBuilder.length() > 0)
                    {
                        commands.add(stringBuilder.toString());
                        stringBuilder.setLength(0);
                    }
                }
                else
                {
                    stringBuilder.append(';');
                    ++i;
                }
            }
        }
        if (stringBuilder.length() > 0)
        {
            commands.add(stringBuilder.toString());
        }
        return commands;
    }
}
