package com.github.ustc_zzzz.virtualchest.action;

import com.github.ustc_zzzz.virtualchest.VirtualChestPlugin;
import com.github.ustc_zzzz.virtualchest.economy.VirtualChestEconomyManager;
import com.github.ustc_zzzz.virtualchest.permission.VirtualChestPermissionManager;
import com.github.ustc_zzzz.virtualchest.unsafe.SpongeUnimplemented;
import com.google.common.collect.Multimaps;
import com.google.common.collect.SortedSetMultimap;
import org.slf4j.Logger;
import org.spongepowered.api.Sponge;
import org.spongepowered.api.command.CommandResult;
import org.spongepowered.api.entity.living.player.Player;
import org.spongepowered.api.item.inventory.ItemStack;
import org.spongepowered.api.item.inventory.ItemStackSnapshot;
import org.spongepowered.api.network.ChannelBinding;
import org.spongepowered.api.network.ChannelRegistrar;
import org.spongepowered.api.scheduler.Scheduler;
import org.spongepowered.api.scheduler.Task;
import org.spongepowered.api.text.Text;
import org.spongepowered.api.text.chat.ChatTypes;
import org.spongepowered.api.text.serializer.TextSerializers;
import org.spongepowered.api.text.title.Title;
import org.spongepowered.api.util.Tristate;
import org.spongepowered.api.util.Tuple;

import java.lang.ref.WeakReference;
import java.math.BigDecimal;
import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * @author ustc_zzzz
 */
public final class VirtualChestActions
{
    private static final char SEQUENCE_SPLITTER = ';';
    private static final char PREFIX_SPLITTER = ':';

    private final VirtualChestPlugin plugin;
    private final Map<String, VirtualChestActionExecutor> executors = new HashMap<>();

    private final Scheduler scheduler = Sponge.getScheduler();
    private final Set<UUID> activatedPlayers = new HashSet<>();
    private final Queue<Callback> queuedCallbacks = new ArrayDeque<>();
    private final SortedSetMultimap<Callback, String> ignoredPermissions;

    private ChannelBinding.RawDataChannel bungeeCordChannel;

    public VirtualChestActions(VirtualChestPlugin plugin)
    {
        this.plugin = plugin;
        this.ignoredPermissions = Multimaps.newSortedSetMultimap(new IdentityHashMap<>(), TreeSet::new);

        Task.Builder taskBuilder = this.scheduler.createTaskBuilder().intervalTicks(1);
        taskBuilder.name("VirtualChestActionExecutor").execute(this::tick).submit(plugin);

        registerPrefix("console", this::processConsole);
        registerPrefix("tell", this::processTell);
        registerPrefix("tellraw", this::processTellraw);
        registerPrefix("broadcast", this::processBroadcast);
        registerPrefix("title", this::processTitle);
        registerPrefix("bigtitle", this::processBigtitle);
        registerPrefix("subtitle", this::processSubtitle);
        registerPrefix("delay", this::processDelay);
        registerPrefix("connect", this::processConnect);
        registerPrefix("cost", this::processCost);
        registerPrefix("cost-item", this::processCostItem);

        registerPrefix("", this::process);

        TitleManager.enable(plugin);
    }

    public void init()
    {
        ChannelRegistrar channelRegistrar = Sponge.getChannelRegistrar();
        this.bungeeCordChannel = channelRegistrar.getOrCreateRaw(this.plugin, "BungeeCord");
    }

    public void registerPrefix(String prefix, VirtualChestActionExecutor executor)
    {
        this.executors.put(prefix, executor);
    }

    public boolean isPlayerActivated(UUID uuid)
    {
        return this.activatedPlayers.contains(uuid);
    }

    public void runCommand(Player player, String commandString, List<String> ignoredPermissions)
    {
        LinkedList<Tuple<String, String>> commandList = parseCommand(commandString).stream().flatMap(command ->
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
                return Stream.of(Tuple.of(prefix, this.plugin.getPlaceholderManager().parseAction(player, suffix)));
            }
            else if (!command.isEmpty())
            {
                return Stream.of(Tuple.of("", this.plugin.getPlaceholderManager().parseAction(player, command)));
            }
            else
            {
                return Stream.empty();
            }
        }).collect(Collectors.toCollection(LinkedList::new));
        this.queuedCallbacks.offer(new Callback(player, commandList, ignoredPermissions));
    }

    private void tick(Task task)
    {
        CommandResult init = CommandResult.success();
        while (true)
        {
            Callback callback = this.queuedCallbacks.poll();
            if (Objects.isNull(callback))
            {
                break;
            }
            callback.accept(init);
        }
        this.activatedPlayers.clear();
    }

    private void process(Player player, String command, Consumer<CommandResult> callback)
    {
        callback.accept(Sponge.getCommandManager().process(player, command));
    }

    private void processCostItem(Player player, String command, Consumer<CommandResult> callback)
    {
        int count = Integer.parseInt(command.replaceFirst("\\s++$", ""));
        ItemStack stackUsed = SpongeUnimplemented.getItemHeldByMouse(player).createStack();
        int stackUsedQuantity = stackUsed.getQuantity();
        if (stackUsedQuantity > count)
        {
            stackUsed.setQuantity(stackUsedQuantity - count);
            SpongeUnimplemented.setItemHeldByMouse(player, stackUsed.createSnapshot());
            callback.accept(CommandResult.success());
        }
        else
        {
            SpongeUnimplemented.setItemHeldByMouse(player, ItemStackSnapshot.NONE);
            callback.accept(CommandResult.empty());
        }
    }

    private void processCost(Player player, String command, Consumer<CommandResult> callback)
    {
        int index = command.lastIndexOf(':');
        String currencyName = index < 0 ? "" : command.substring(0, index).toLowerCase();
        BigDecimal cost = new BigDecimal(command.substring(index + 1).replaceFirst("\\s++$", ""));
        VirtualChestEconomyManager economyManager = this.plugin.getEconomyManager();
        boolean isSuccessful = true;
        switch (cost.signum())
        {
        case 1:
            isSuccessful = economyManager.withdrawBalance(currencyName, player, cost, false);
            break;
        case -1:
            isSuccessful = economyManager.depositBalance(currencyName, player, cost.negate(), false);
            break;
        }
        callback.accept(isSuccessful ? CommandResult.success() : CommandResult.empty());

    }

    private void processConnect(Player player, String command, Consumer<CommandResult> callback)
    {
        this.bungeeCordChannel.sendTo(player, buf ->
        {
            buf.writeUTF("Connect");
            buf.writeUTF(command.replaceFirst("\\s++$", ""));
        });
        callback.accept(CommandResult.success());
    }

    private void processDelay(Player player, String command, Consumer<CommandResult> callback)
    {
        try
        {
            int delayTick = Integer.parseInt(command.replaceFirst("\\s++$", ""));
            if (delayTick <= 0)
            {
                throw new NumberFormatException();
            }
            Runnable taskExecutor = () -> callback.accept(CommandResult.success());
            this.scheduler.createTaskBuilder().delayTicks(delayTick).execute(taskExecutor).submit(this.plugin);
        }
        catch (NumberFormatException e)
        {
            callback.accept(CommandResult.empty());
        }
    }

    private void processBigtitle(Player player, String command, Consumer<CommandResult> callback)
    {
        Text text = TextSerializers.FORMATTING_CODE.deserialize(command);
        TitleManager.pushBigtitle(text, player);
        callback.accept(CommandResult.success());
    }

    private void processSubtitle(Player player, String command, Consumer<CommandResult> callback)
    {
        Text text = TextSerializers.FORMATTING_CODE.deserialize(command);
        TitleManager.pushSubtitle(text, player);
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

    private void processTellraw(Player player, String command, Consumer<CommandResult> callback)
    {
        player.sendMessage(TextSerializers.JSON.deserialize(command));
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
        Tristate isCommandFinished = Tristate.TRUE;
        for (char c : commandSequence.toCharArray())
        {
            if (c != SEQUENCE_SPLITTER)
            {
                if (isCommandFinished == Tristate.UNDEFINED)
                {
                    commands.add(stringBuilder.toString());
                    stringBuilder.setLength(0);
                }
                if (isCommandFinished != Tristate.FALSE && Character.isWhitespace(c))
                {
                    isCommandFinished = Tristate.TRUE;
                    continue;
                }
            }
            else if (isCommandFinished != Tristate.UNDEFINED)
            {
                isCommandFinished = Tristate.UNDEFINED;
                continue;
            }
            isCommandFinished = Tristate.FALSE;
            stringBuilder.append(c);
        }
        commands.add(stringBuilder.toString());
        return commands;
    }

    private class Callback implements Consumer<CommandResult>
    {
        private final WeakReference<Player> playerReference;
        private final Queue<Tuple<String, String>> commandList;
        private final VirtualChestPermissionManager permissionManager;
        private final Logger logger = VirtualChestActions.this.plugin.getLogger();

        private Callback(Player p, LinkedList<Tuple<String, String>> commandList, List<String> ignoredPermissions)
        {
            this.commandList = commandList;
            this.playerReference = new WeakReference<>(p);
            this.permissionManager = VirtualChestActions.this.plugin.getPermissionManager();

            this.permissionManager.clearIgnored(p);
            VirtualChestActions.this.ignoredPermissions.putAll(this, ignoredPermissions);
            this.permissionManager.addIgnored(p, VirtualChestActions.this.ignoredPermissions.values());
        }

        @Override
        public void accept(CommandResult commandResult)
        {
            Player player = this.playerReference.get();
            if (!Objects.isNull(player))
            {
                Tuple<String, String> t = this.commandList.poll();
                if (Objects.isNull(t))
                {
                    this.permissionManager.clearIgnored(player);
                    VirtualChestActions.this.ignoredPermissions.removeAll(this);
                    this.permissionManager.addIgnored(player, VirtualChestActions.this.ignoredPermissions.values());
                }
                else
                {
                    UUID uuid = player.getUniqueId();
                    VirtualChestActions.this.activatedPlayers.add(uuid);
                    String prefix = t.getFirst(), suffix = t.getSecond();
                    String command = prefix.isEmpty() ? suffix : prefix + ": " + suffix;
                    this.logger.debug("Player {} is now executing {}", player.getName(), command);
                    VirtualChestActions.this.executors.get(prefix).doAction(player, suffix, this);
                }
            }
        }
    }

    private static class TitleManager
    {
        private static final Map<Player, Text> BIGTITLES = new WeakHashMap<>();
        private static final Map<Player, Text> SUBTITLES = new WeakHashMap<>();

        private static Task task;

        private static void sendTitle(Task task)
        {
            Map<Player, Title.Builder> builderMap = new HashMap<>();
            if (!BIGTITLES.isEmpty())
            {
                for (Map.Entry<Player, Text> entry : BIGTITLES.entrySet())
                {
                    builderMap.compute(entry.getKey(), (player, builder) ->
                            Optional.ofNullable(builder).orElseGet(Title::builder).title(entry.getValue()));
                }
                BIGTITLES.clear();
            }
            if (!SUBTITLES.isEmpty())
            {
                for (Map.Entry<Player, Text> entry : SUBTITLES.entrySet())
                {
                    builderMap.compute(entry.getKey(), (player, builder) ->
                            Optional.ofNullable(builder).orElseGet(Title::builder).subtitle(entry.getValue()));
                }
                SUBTITLES.clear();
            }
            builderMap.forEach((player, builder) -> player.sendTitle(builder.build()));
        }

        private static void pushBigtitle(Text title, Player player)
        {
            BIGTITLES.put(player, title);
        }

        private static void pushSubtitle(Text title, Player player)
        {
            SUBTITLES.put(player, title);
        }

        private static void enable(Object plugin)
        {
            Optional.ofNullable(task).ifPresent(Task::cancel);
            Task.Builder builder = Sponge.getScheduler().createTaskBuilder().intervalTicks(1);
            task = builder.name("VirtualChestTitleManager").execute(TitleManager::sendTitle).submit(plugin);
        }
    }
}
