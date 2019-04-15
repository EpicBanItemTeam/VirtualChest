package com.github.ustc_zzzz.virtualchest.action;

import com.github.ustc_zzzz.virtualchest.VirtualChestPlugin;
import com.github.ustc_zzzz.virtualchest.api.action.VirtualChestActionExecutor;
import com.github.ustc_zzzz.virtualchest.api.action.VirtualChestActionExecutor.Context;
import com.github.ustc_zzzz.virtualchest.api.action.VirtualChestActionExecutor.HandheldItemContext;
import com.github.ustc_zzzz.virtualchest.economy.VirtualChestEconomyManager;
import com.github.ustc_zzzz.virtualchest.placeholder.VirtualChestPlaceholderManager;
import com.github.ustc_zzzz.virtualchest.unsafe.SpongeUnimplemented;
import com.google.common.collect.*;
import org.slf4j.Logger;
import org.spongepowered.api.GameRegistry;
import org.spongepowered.api.Sponge;
import org.spongepowered.api.command.CommandResult;
import org.spongepowered.api.command.CommandSource;
import org.spongepowered.api.command.source.ConsoleSource;
import org.spongepowered.api.effect.sound.SoundCategory;
import org.spongepowered.api.effect.sound.SoundType;
import org.spongepowered.api.entity.living.player.Player;
import org.spongepowered.api.event.cause.Cause;
import org.spongepowered.api.event.impl.AbstractEvent;
import org.spongepowered.api.item.inventory.ItemStack;
import org.spongepowered.api.item.inventory.ItemStackSnapshot;
import org.spongepowered.api.item.inventory.Slot;
import org.spongepowered.api.network.ChannelBinding;
import org.spongepowered.api.network.ChannelRegistrar;
import org.spongepowered.api.scheduler.Task;
import org.spongepowered.api.text.Text;
import org.spongepowered.api.text.chat.ChatTypes;
import org.spongepowered.api.text.serializer.TextSerializers;
import org.spongepowered.api.text.title.Title;
import org.spongepowered.api.util.Tuple;
import org.spongepowered.api.util.annotation.NonnullByDefault;
import org.spongepowered.api.world.Location;
import org.spongepowered.api.world.World;

import java.lang.ref.WeakReference;
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Stream;

/**
 * @author ustc_zzzz
 */
public final class VirtualChestActions
{
    private static final char PREFIX_SPLITTER = ':';

    private final Logger logger;
    private final VirtualChestPlugin plugin;
    private final ListMultimap<String, VirtualChestActionExecutor> executors = ArrayListMultimap.create();

    private final SetMultimap<String, UUID> activateUUIDMap = Multimaps.synchronizedSetMultimap(HashMultimap.create());

    private ChannelBinding.RawDataChannel bungeeCordChannel;

    public VirtualChestActions(VirtualChestPlugin plugin)
    {
        this.plugin = plugin;
        this.logger = plugin.getLogger();

        Task.builder().intervalTicks(1).name("VirtualChestActionExecutor").execute(this::tick).submit(plugin);

        TitleManager.enable(plugin);
    }

    public void init()
    {
        ChannelRegistrar channelRegistrar = Sponge.getChannelRegistrar();
        this.bungeeCordChannel = channelRegistrar.getOrCreateRaw(this.plugin, "BungeeCord");

        this.executors.put("", this::process);

        this.executors.put("console", this::processConsole);
        this.executors.put("tell", this::processTell);
        this.executors.put("tellraw", this::processTellraw);
        this.executors.put("broadcast", this::processBroadcast);
        this.executors.put("title", this::processTitle);
        this.executors.put("bigtitle", this::processBigtitle);
        this.executors.put("subtitle", this::processSubtitle);
        this.executors.put("delay", this::processDelay);
        this.executors.put("connect", this::processConnect);
        this.executors.put("cost", this::processCost);
        this.executors.put("cost-item", this::processCostItem);
        this.executors.put("sound", this::processSound);
        this.executors.put("sound-with-pitch", this::processSoundWithPitch);

        Sponge.getEventManager().post(new LoadEvent());
    }

    public Set<UUID> getActivatedActions(String identifier)
    {
        return this.activateUUIDMap.get(identifier);
    }

    public CompletableFuture<CommandResult> submitCommands(Player player, Stream<String> commands,
                                                           ClassToInstanceMap<Context> context, boolean record)
    {
        VirtualChestPlaceholderManager placeholderManager = this.plugin.getPlaceholderManager();
        LinkedList<Tuple<String, String>> commandList = new LinkedList<>();
        commands.forEach(command ->
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
                commandList.add(Tuple.of(prefix, placeholderManager.parseText(player, suffix)));
            }
            else if (!command.isEmpty())
            {
                commandList.add(Tuple.of("", placeholderManager.parseText(player, command)));
            }
        });
        plugin.getLogger().debug("Player {} tries to run {} command(s)", player.getName(), commandList.size());
        return new Callback(commandList, context, record).start();
    }

    private void tick(Task task)
    {
        this.activateUUIDMap.clear();
    }

    private CompletableFuture<CommandResult> process(CommandResult parent, String command,
                                                     ClassToInstanceMap<Context> contextMap)
    {
        Optional<Player> playerOptional = contextMap.getInstance(Context.PLAYER).getPlayer();
        CommandSource source = playerOptional.isPresent() ? playerOptional.get() : Sponge.getServer().getConsole();
        if (!command.replaceFirst("\\s++$", "").isEmpty())
        {
            return CompletableFuture.completedFuture(Sponge.getCommandManager().process(source, command));
        }
        else
        {
            return CompletableFuture.completedFuture(CommandResult.success());
        }
    }

    private CompletableFuture<CommandResult> processSoundWithPitch(CommandResult parent, String command,
                                                                   ClassToInstanceMap<Context> contextMap)
    {
        contextMap.getInstance(Context.PLAYER).getPlayer().ifPresent(player ->
        {
            int index = command.lastIndexOf(':');
            String prefix = index > 0 ? command.substring(0, index).toLowerCase() : "";
            String suffix = command.substring(index + 1).replaceFirst("\\s++$", "");
            SoundManager.playSound(prefix, player, player.getLocation(), Double.parseDouble(suffix));
        });
        return CompletableFuture.completedFuture(CommandResult.success());
    }

    private CompletableFuture<CommandResult> processSound(CommandResult parent, String command,
                                                          ClassToInstanceMap<Context> contextMap)
    {
        contextMap.getInstance(Context.PLAYER).getPlayer().ifPresent(player ->
        {
            String prefix = command.replaceFirst("\\s++$", "");
            SoundManager.playSound(prefix, player, player.getLocation(), Double.NaN);
        });
        return CompletableFuture.completedFuture(CommandResult.success());
    }

    private CompletableFuture<CommandResult> processCostItem(CommandResult parent, String command,
                                                             ClassToInstanceMap<Context> contextMap)
    {
        int count = Integer.parseInt(command.replaceFirst("\\s++$", ""));
        HandheldItemContext itemTemplate = contextMap.getInstance(Context.HANDHELD_ITEM);
        Optional<Player> playerOptional = contextMap.getInstance(Context.PLAYER).getPlayer();
        if (Objects.isNull(itemTemplate) || !playerOptional.isPresent())
        {
            return CompletableFuture.completedFuture(CommandResult.empty());
        }
        Player player = playerOptional.get();
        ItemStackSnapshot itemHeldByMouse = SpongeUnimplemented.getItemHeldByMouse(player);
        int stackUsedQuantity = SpongeUnimplemented.getCount(itemHeldByMouse);
        if (itemTemplate.matchItem(itemHeldByMouse))
        {
            count -= stackUsedQuantity;
            if (count < 0)
            {
                ItemStack stackUsed = itemHeldByMouse.createStack();
                stackUsed.setQuantity(-count);
                SpongeUnimplemented.setItemHeldByMouse(player, stackUsed.createSnapshot());
            }
            else
            {
                SpongeUnimplemented.setItemHeldByMouse(player, ItemStackSnapshot.NONE);
            }
        }
        if (count <= 0)
        {
            return CompletableFuture.completedFuture(CommandResult.success());
        }
        else
        {
            for (Slot slot : player.getInventory().<Slot>slots())
            {
                Optional<ItemStack> stackOptional = slot.peek();
                if (stackOptional.isPresent())
                {
                    ItemStackSnapshot slotItem = stackOptional.get().createSnapshot();
                    int slotItemSize = SpongeUnimplemented.getCount(slotItem);
                    if (itemTemplate.matchItem(slotItem))
                    {
                        count -= slotItemSize;
                        if (count < 0)
                        {
                            ItemStack slotItemStack = slotItem.createStack();
                            slotItemStack.setQuantity(-count);
                            slot.set(slotItemStack);
                        }
                        else
                        {
                            slot.clear();
                        }
                    }
                }
                if (count <= 0)
                {
                    return CompletableFuture.completedFuture(CommandResult.success());
                }
            }
            return CompletableFuture.completedFuture(CommandResult.empty());
        }
    }

    private CompletableFuture<CommandResult> processCost(CommandResult parent, String command,
                                                         ClassToInstanceMap<Context> contextMap)
    {
        boolean isSuccessful = true;
        Optional<Player> playerOptional = contextMap.getInstance(Context.PLAYER).getPlayer();
        if (playerOptional.isPresent())
        {
            Player p = playerOptional.get();
            int index = command.lastIndexOf(':');
            String currencyName = index < 0 ? "" : command.substring(0, index).toLowerCase();
            BigDecimal cost = new BigDecimal(command.substring(index + 1).replaceFirst("\\s++$", ""));
            VirtualChestEconomyManager economyManager = this.plugin.getEconomyManager();
            switch (cost.signum())
            {
            case 1:
                isSuccessful = economyManager.withdrawBalance(currencyName, p, cost);
                break;
            case -1:
                isSuccessful = economyManager.depositBalance(currencyName, p, cost.negate());
                break;
            }
        }
        return CompletableFuture.completedFuture(isSuccessful ? CommandResult.success() : CommandResult.empty());
    }

    private CompletableFuture<CommandResult> processConnect(CommandResult parent, String command,
                                                            ClassToInstanceMap<Context> contextMap)
    {
        Optional<Player> playerOptional = contextMap.getInstance(Context.PLAYER).getPlayer();
        if (playerOptional.isPresent())
        {
            Player p = playerOptional.get();
            this.bungeeCordChannel.sendTo(p, buf ->
            {
                buf.writeUTF("Connect");
                buf.writeUTF(command.replaceFirst("\\s++$", ""));
            });
        }
        return CompletableFuture.completedFuture(CommandResult.success());
    }

    private CompletableFuture<CommandResult> processDelay(CommandResult parent, String command,
                                                          ClassToInstanceMap<Context> contextMap)
    {
        CompletableFuture<CommandResult> future = new CompletableFuture<>();
        try
        {
            int delayTick = Integer.parseInt(command.replaceFirst("\\s++$", ""));
            if (delayTick <= 0)
            {
                throw new NumberFormatException();
            }
            Runnable taskExecutor = () -> future.complete(CommandResult.success());
            Task.builder().delayTicks(delayTick).execute(taskExecutor).submit(this.plugin);
        }
        catch (NumberFormatException e)
        {
            future.complete(CommandResult.empty());
        }
        return future;
    }

    private CompletableFuture<CommandResult> processBigtitle(CommandResult parent, String command,
                                                             ClassToInstanceMap<Context> contextMap)
    {
        Text text = TextSerializers.FORMATTING_CODE.deserialize(command);
        contextMap.getInstance(Context.PLAYER).getPlayer().ifPresent(p -> TitleManager.pushBigtitle(text, p));
        return CompletableFuture.completedFuture(CommandResult.success());
    }

    private CompletableFuture<CommandResult> processSubtitle(CommandResult parent, String command,
                                                             ClassToInstanceMap<Context> contextMap)
    {
        Text text = TextSerializers.FORMATTING_CODE.deserialize(command);
        contextMap.getInstance(Context.PLAYER).getPlayer().ifPresent(p -> TitleManager.pushSubtitle(text, p));
        return CompletableFuture.completedFuture(CommandResult.success());
    }

    private CompletableFuture<CommandResult> processTitle(CommandResult parent, String command,
                                                          ClassToInstanceMap<Context> contextMap)
    {
        Text text = TextSerializers.FORMATTING_CODE.deserialize(command);
        contextMap.getInstance(Context.PLAYER).getPlayer().ifPresent(p -> p.sendMessage(ChatTypes.ACTION_BAR, text));
        return CompletableFuture.completedFuture(CommandResult.success());
    }

    private CompletableFuture<CommandResult> processBroadcast(CommandResult parent, String command,
                                                              ClassToInstanceMap<Context> contextMap)
    {
        Text text = TextSerializers.FORMATTING_CODE.deserialize(command);
        Sponge.getServer().getBroadcastChannel().send(text);
        return CompletableFuture.completedFuture(CommandResult.success());
    }

    private CompletableFuture<CommandResult> processTellraw(CommandResult parent, String command,
                                                            ClassToInstanceMap<Context> contextMap)
    {
        Text text = TextSerializers.JSON.deserialize(command);
        contextMap.getInstance(Context.PLAYER).getPlayer().ifPresent(p -> p.sendMessage(text));
        return CompletableFuture.completedFuture(CommandResult.success());
    }

    private CompletableFuture<CommandResult> processTell(CommandResult parent, String command,
                                                         ClassToInstanceMap<Context> contextMap)
    {
        Text text = TextSerializers.FORMATTING_CODE.deserialize(command);
        contextMap.getInstance(Context.PLAYER).getPlayer().ifPresent(p -> p.sendMessage(text));
        return CompletableFuture.completedFuture(CommandResult.success());
    }

    private CompletableFuture<CommandResult> processConsole(CommandResult parent, String command,
                                                            ClassToInstanceMap<Context> contextMap)
    {
        ConsoleSource source = Sponge.getServer().getConsole();
        return CompletableFuture.completedFuture(Sponge.getCommandManager().process(source, command));
    }

    private class Callback implements Consumer<CommandResult>
    {
        private int actionOrder = -1;

        private final boolean record;
        private final UUID actionUUID;
        private final ClassToInstanceMap<Context> context;
        private final Queue<Tuple<String, String>> commandList;
        private final CompletableFuture<CommandResult> future = new CompletableFuture<>();

        private Callback(LinkedList<Tuple<String, String>> commandList,
                         ClassToInstanceMap<Context> contextMap, boolean record)
        {
            this.record = record;
            this.context = contextMap;
            this.commandList = commandList;
            this.actionUUID = contextMap.getInstance(Context.ACTION_UUID).getActionUniqueId();
        }

        private CompletableFuture<CommandResult> start()
        {
            this.accept(CommandResult.success());
            return this.future;
        }

        @Override
        public void accept(CommandResult commandResult)
        {
            Optional<Player> playerOptional = context.getInstance(Context.PLAYER).getPlayer();
                Tuple<String, String> t = commandList.poll();
                if (Objects.isNull(t))
                {
                    playerOptional.ifPresent(p -> activateUUIDMap.remove(p.getIdentifier(), actionUUID));
                    logger.debug("All the commands for {} has been executed", actionUUID);
                    future.complete(commandResult);
                }
                else
                {
                    ++actionOrder;
                    String prefix = t.getFirst(), suffix = t.getSecond();
                    String command = prefix.isEmpty() ? suffix : prefix + ": " + suffix;
                    String escapedCommand = '\'' + SpongeUnimplemented.escapeString(command) + '\'';
                    playerOptional.ifPresent(p -> activateUUIDMap.put(p.getIdentifier(), actionUUID));
                    if (record)
                    {
                        plugin.getRecordManager().recordExecution(actionUUID, actionOrder, prefix, suffix);
                        logger.debug("{} is not executed for {}", escapedCommand, actionUUID);
                    }
                    CompletableFuture<CommandResult> future = CompletableFuture.completedFuture(commandResult);
                    for (VirtualChestActionExecutor action : executors.get(prefix))
                    {
                        future = future.thenCompose(parent -> action.execute(parent, suffix, context));
                    }
                    future.thenAccept(this);
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

    private static class SoundManager
    {
        private static final SoundCategory soundCategory;

        private static void playSound(String command, Player player, Location<World> location, double pitch)
        {
            double volume;
            SoundType soundType;
            GameRegistry registry = Sponge.getRegistry();
            Optional<SoundType> soundTypeOptional = registry.getType(SoundType.class, command);
            if (soundTypeOptional.isPresent())
            {
                volume = 1;
                soundType = soundTypeOptional.get();
            }
            else
            {
                int index = command.lastIndexOf(':');
                String id = index > 0 ? command.substring(0, index).toLowerCase() : "";
                Supplier<RuntimeException> error = () -> new NoSuchElementException("No value available for " + id);
                soundType = registry.getType(SoundType.class, id).orElseThrow(error);
                volume = Double.parseDouble(command.substring(index + 1));
            }
            if (Double.isNaN(pitch))
            {
                player.playSound(soundType, soundCategory, location.getPosition(), volume);
            }
            else
            {
                player.playSound(soundType, soundCategory, location.getPosition(), volume, pitch);
            }
        }

        static
        {
            // lazy initialization
            String id = "minecraft:player";
            soundCategory = Sponge.getRegistry().getType(SoundCategory.class, id).orElseThrow(RuntimeException::new);
        }
    }

    @NonnullByDefault
    private class LoadEvent extends AbstractEvent implements VirtualChestActionExecutor.LoadEvent
    {
        @Override
        public Cause getCause()
        {
            return SpongeUnimplemented.createCause(plugin);
        }

        @Override
        public void register(String prefix, VirtualChestActionExecutor actionExecutor)
        {
            VirtualChestActions.this.executors.put(prefix, actionExecutor);
        }
    }
}
