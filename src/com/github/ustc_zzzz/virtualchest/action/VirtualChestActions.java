package com.github.ustc_zzzz.virtualchest.action;

import com.github.ustc_zzzz.virtualchest.VirtualChestPlugin;
import org.spongepowered.api.Sponge;
import org.spongepowered.api.command.CommandResult;
import org.spongepowered.api.entity.living.player.Player;
import org.spongepowered.api.scheduler.Task;
import org.spongepowered.api.text.Text;
import org.spongepowered.api.text.chat.ChatTypes;
import org.spongepowered.api.text.serializer.TextSerializers;
import org.spongepowered.api.text.title.Title;
import org.spongepowered.api.util.Tuple;

import java.lang.ref.WeakReference;
import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * @author ustc_zzzz
 */
public final class VirtualChestActions
{
    private static final char SEQUENCE_SPLITTER = ';';
    private static final char PREFIX_SPLITTER = ':';

    private final VirtualChestPlugin plugin;
    private final Map<String, VirtualChestActionExecutor> executors = new HashMap<>();

    public VirtualChestActions(VirtualChestPlugin plugin)
    {
        this.plugin = plugin;

        registerPrefix("console", this::processConsole);
        registerPrefix("tell", this::processTell);
        registerPrefix("broadcast", this::processBroadcast);
        registerPrefix("title", this::processTitle);
        registerPrefix("bigtitle", this::processBigtitle);
        registerPrefix("delay", this::processDelay);

        registerPrefix("", this::process);
    }

    public void registerPrefix(String prefix, VirtualChestActionExecutor executor)
    {
        this.executors.put(prefix, executor);
    }

    public void runCommand(Player player, String commandString)
    {
        LinkedList<Tuple<String, String>> commandList = parseCommand(commandString).stream().map(command ->
        {
            int colonPos = command.indexOf(PREFIX_SPLITTER);
            String prefix = colonPos > 0 ? command.substring(0, colonPos) : "";
            if (this.executors.containsKey(prefix))
            {
                int length = command.length(), suffixPosition = colonPos + 1;
                while (suffixPosition < length && Character.isWhitespace(command.charAt(suffixPosition)))
                {
                    ++suffixPosition;
                }
                String suffix = command.substring(suffixPosition);
                return Tuple.of(prefix, this.plugin.getPlaceholderParser().parseAction(player, suffix));
            }
            else
            {
                return Tuple.of("", this.plugin.getPlaceholderParser().parseAction(player, command));
            }
        }).collect(Collectors.toCollection(LinkedList::new));
        new Callback(player, commandList).accept(CommandResult.empty());
    }

    private void process(Player player, String command, Consumer<CommandResult> callback)
    {
        callback.accept(Sponge.getCommandManager().process(player, command));
    }

    private void processDelay(Player player, String command, Consumer<CommandResult> callback)
    {
        try
        {
            int delayTick = Integer.parseInt(command);
            if (delayTick <= 0)
            {
                throw new NumberFormatException();
            }
            Consumer<Task> taskExecutor = task -> callback.accept(CommandResult.success());
            Sponge.getScheduler().createTaskBuilder().delayTicks(delayTick).execute(taskExecutor).submit(this.plugin);
        }
        catch (NumberFormatException e)
        {
            callback.accept(CommandResult.empty());
        }
    }

    private void processBigtitle(Player player, String command, Consumer<CommandResult> callback)
    {
        Text text = TextSerializers.FORMATTING_CODE.deserialize(command);
        player.sendTitle(Title.of(text));
        callback.accept(CommandResult.success());
    }

    private void processTitle(Player player, String command, Consumer<CommandResult> callback)
    {
        Text text = TextSerializers.FORMATTING_CODE.deserialize(command);
        player.sendMessage(ChatTypes.ACTION_BAR, text);
        callback.accept(CommandResult.success());
    }

    private void processBroadcast(Player player, String command, Consumer<CommandResult> callback)
    {
        Text text = TextSerializers.FORMATTING_CODE.deserialize(command);
        Sponge.getServer().getBroadcastChannel().send(text);
        callback.accept(CommandResult.success());
    }

    private void processTell(Player player, String command, Consumer<CommandResult> callback)
    {
        player.sendMessage(TextSerializers.FORMATTING_CODE.deserialize(command));
        callback.accept(CommandResult.success());
    }

    private void processConsole(Player player, String command, Consumer<CommandResult> callback)
    {
        callback.accept(Sponge.getCommandManager().process(Sponge.getServer().getConsole(), command));
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

    private class Callback implements Consumer<CommandResult>
    {
        private final WeakReference<Player> player;
        private final LinkedList<Tuple<String, String>> commandList;

        private Callback(Player player, LinkedList<Tuple<String, String>> commandList)
        {
            this.player = new WeakReference<>(player);
            this.commandList = commandList;
        }

        @Override
        public void accept(CommandResult commandResult)
        {
            Optional<Player> playerOptional = Optional.ofNullable(player.get());
            if (!playerOptional.isPresent())
            {
                commandList.clear();
            }
            else if (!commandList.isEmpty())
            {
                Player p = playerOptional.get();
                Tuple<String, String> t = commandList.pop();
                executors.get(t.getFirst()).doAction(p, t.getSecond(), this);
            }
        }
    }
}
