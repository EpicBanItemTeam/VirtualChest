package com.github.ustc_zzzz.virtualchest.action;

import com.github.ustc_zzzz.virtualchest.VirtualChestPlugin;
import com.github.ustc_zzzz.virtualchest.economy.VirtualChestEconomyManager;
import com.github.ustc_zzzz.virtualchest.inventory.item.VirtualChestItem;
import com.github.ustc_zzzz.virtualchest.inventory.util.VirtualChestHandheldItem;
import com.github.ustc_zzzz.virtualchest.permission.VirtualChestPermissionManager;
import com.github.ustc_zzzz.virtualchest.placeholder.VirtualChestPlaceholderManager;
import com.github.ustc_zzzz.virtualchest.unsafe.SpongeUnimplemented;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Multimaps;
import com.google.common.collect.SortedSetMultimap;
import org.slf4j.Logger;
import org.spongepowered.api.GameRegistry;
import org.spongepowered.api.Sponge;
import org.spongepowered.api.command.CommandResult;
import org.spongepowered.api.effect.sound.SoundCategory;
import org.spongepowered.api.effect.sound.SoundType;
import org.spongepowered.api.entity.living.player.Player;
import org.spongepowered.api.item.inventory.ItemStack;
import org.spongepowered.api.item.inventory.ItemStackSnapshot;
import org.spongepowered.api.item.inventory.Slot;
import org.spongepowered.api.network.ChannelBinding;
import org.spongepowered.api.network.ChannelRegistrar;
import org.spongepowered.api.scheduler.Scheduler;
import org.spongepowered.api.scheduler.Task;
import org.spongepowered.api.text.Text;
import org.spongepowered.api.text.chat.ChatTypes;
import org.spongepowered.api.text.serializer.TextSerializers;
import org.spongepowered.api.text.title.Title;
import org.spongepowered.api.util.Tuple;
import org.spongepowered.api.world.Location;
import org.spongepowered.api.world.World;

import java.lang.ref.WeakReference;
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * @author ustc_zzzz
 */
public final class VirtualChestActions
{
    private static final char PREFIX_SPLITTER = ':';

    private final VirtualChestPlugin plugin;
    private final Map<String, VirtualChestActionExecutor> executors = new HashMap<>();

    private final Scheduler scheduler = Sponge.getScheduler();
    private final Set<String> activatedIdentifiers = new HashSet<>();

    private final Queue<Callback> queuedCallbacks;
    private final SortedSetMultimap<Callback, String> permissionMap;

    private ChannelBinding.RawDataChannel bungeeCordChannel;

    public VirtualChestActions(VirtualChestPlugin plugin)
    {
        this.plugin = plugin;
        this.queuedCallbacks = new ConcurrentLinkedQueue<>();
        this.permissionMap = Multimaps.newSortedSetMultimap(new IdentityHashMap<>(), TreeSet::new);

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
        registerPrefix("sound", this::processSound);
        registerPrefix("sound-with-pitch", this::processSoundWithPitch);

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

    public boolean isPlayerActivated(String identifier)
    {
        return this.activatedIdentifiers.contains(identifier);
    }

    public CompletableFuture<Void> submitCommands(Player player, List<String> commands, Map<String, Object> context)
    {
        plugin.getLogger().debug("Player {} tries to run {} command(s)", player.getName(), commands.size());
        VirtualChestPlaceholderManager placeholderManager = this.plugin.getPlaceholderManager();
        LinkedList<Tuple<String, String>> commandList = new LinkedList<>();
        for (String command : commands)
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
        }
        return new Callback(player, commandList, context).start();
    }

    private void tick(Task task)
    {
        for (Callback c = this.queuedCallbacks.poll(); Objects.nonNull(c); c = this.queuedCallbacks.poll())
        {
            c.acceptFirst();
        }
        this.activatedIdentifiers.clear();
    }

    private void process(Player player, String command,
                         Map<String, Object> context, Consumer<CommandResult> callback)
    {
        if (!command.replaceFirst("\\s++$", "").isEmpty())
        {
            callback.accept(Sponge.getCommandManager().process(player, command));
        }
    }

    private void processSoundWithPitch(Player player, String command,
                                       Map<String, Object> context, Consumer<CommandResult> callback)
    {
        int index = command.lastIndexOf(':');
        String prefix = index > 0 ? command.substring(0, index).toLowerCase() : "";
        String suffix = command.substring(index + 1).replaceFirst("\\s++$", "");
        SoundManager.playSound(prefix, player, player.getLocation(), Double.parseDouble(suffix));
        callback.accept(CommandResult.success());
    }

    private void processSound(Player player, String command,
                              Map<String, Object> context, Consumer<CommandResult> callback)
    {
        SoundManager.playSound(command.replaceFirst("\\s++$", ""), player, player.getLocation(), Double.NaN);
        callback.accept(CommandResult.success());
    }

    private void processCostItem(Player player, String command,
                                 Map<String, Object> context, Consumer<CommandResult> callback)
    {
        int count = Integer.parseInt(command.replaceFirst("\\s++$", ""));
        String key = VirtualChestActionDispatcher.HANDHELD_ITEM.toString();
        VirtualChestHandheldItem itemTemplate = (VirtualChestHandheldItem) context.get(key);
        ItemStackSnapshot itemHeldByMouse = SpongeUnimplemented.getItemHeldByMouse(player);
        int stackUsedQuantity = itemHeldByMouse.getCount();
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
            callback.accept(CommandResult.success());
        }
        else
        {
            for (Slot slot : player.getInventory().<Slot>slots())
            {
                Optional<ItemStack> stackOptional = slot.peek();
                if (stackOptional.isPresent())
                {
                    ItemStackSnapshot slotItem = stackOptional.get().createSnapshot();
                    int slotItemSize = slotItem.getCount();
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
                    callback.accept(CommandResult.success());
                    break;
                }
            }
            if (count > 0)
            {
                callback.accept(CommandResult.empty());
            }
        }
    }

    private void processCost(Player player, String command,
                             Map<String, Object> context, Consumer<CommandResult> callback)
    {
        int index = command.lastIndexOf(':');
        String currencyName = index < 0 ? "" : command.substring(0, index).toLowerCase();
        BigDecimal cost = new BigDecimal(command.substring(index + 1).replaceFirst("\\s++$", ""));
        VirtualChestEconomyManager economyManager = this.plugin.getEconomyManager();
        boolean isSuccessful = true;
        switch (cost.signum())
        {
        case 1:
            isSuccessful = economyManager.withdrawBalance(currencyName, player, cost);
            break;
        case -1:
            isSuccessful = economyManager.depositBalance(currencyName, player, cost.negate());
            break;
        }
        callback.accept(isSuccessful ? CommandResult.success() : CommandResult.empty());
    }

    private void processConnect(Player player, String command,
                                Map<String, Object> context, Consumer<CommandResult> callback)
    {
        this.bungeeCordChannel.sendTo(player, buf ->
        {
            buf.writeUTF("Connect");
            buf.writeUTF(command.replaceFirst("\\s++$", ""));
        });
        callback.accept(CommandResult.success());
    }

    private void processDelay(Player player, String command,
                              Map<String, Object> context, Consumer<CommandResult> callback)
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

    private void processBigtitle(Player player, String command,
                                 Map<String, Object> context, Consumer<CommandResult> callback)
    {
        Text text = TextSerializers.FORMATTING_CODE.deserialize(command);
        TitleManager.pushBigtitle(text, player);
        callback.accept(CommandResult.success());
    }

    private void processSubtitle(Player player, String command,
                                 Map<String, Object> context, Consumer<CommandResult> callback)
    {
        Text text = TextSerializers.FORMATTING_CODE.deserialize(command);
        TitleManager.pushSubtitle(text, player);
        callback.accept(CommandResult.success());
    }

    private void processTitle(Player player, String command,
                              Map<String, Object> context, Consumer<CommandResult> callback)
    {
        Text text = TextSerializers.FORMATTING_CODE.deserialize(command);
        player.sendMessage(ChatTypes.ACTION_BAR, text);
        callback.accept(CommandResult.success());
    }

    private void processBroadcast(Player player, String command,
                                  Map<String, Object> context, Consumer<CommandResult> callback)
    {
        Text text = TextSerializers.FORMATTING_CODE.deserialize(command);
        Sponge.getServer().getBroadcastChannel().send(text);
        callback.accept(CommandResult.success());
    }

    private void processTellraw(Player player, String command,
                                Map<String, Object> context, Consumer<CommandResult> callback)
    {
        player.sendMessage(TextSerializers.JSON.deserialize(command));
        callback.accept(CommandResult.success());
    }

    private void processTell(Player player, String command,
                             Map<String, Object> context, Consumer<CommandResult> callback)
    {
        player.sendMessage(TextSerializers.FORMATTING_CODE.deserialize(command));
        callback.accept(CommandResult.success());
    }

    private void processConsole(Player player, String command,
                                Map<String, Object> context, Consumer<CommandResult> callback)
    {
        callback.accept(Sponge.getCommandManager().process(Sponge.getServer().getConsole(), command));
    }

    private class Callback implements Consumer<CommandResult>
    {
        private final Map<String, Object> context;
        private final List<String> ignoredPermissions;
        private final WeakReference<Player> playerReference;
        private final Queue<Tuple<String, String>> commandList;
        private final VirtualChestPermissionManager permissionManager;
        private final Logger logger = VirtualChestActions.this.plugin.getLogger();
        private final CompletableFuture<Void> completableFuture = new CompletableFuture<>();

        @SuppressWarnings("unchecked")
        private Callback(Player p, LinkedList<Tuple<String, String>> commandList, Map<String, Object> context)
        {
            this.context = context;
            this.commandList = commandList;
            this.playerReference = new WeakReference<>(p);
            this.permissionManager = VirtualChestActions.this.plugin.getPermissionManager();
            this.ignoredPermissions = (List<String>) context.get(VirtualChestItem.IGNORED_PERMISSIONS.toString());
        }

        private CompletableFuture<Void> start()
        {
            Player p = playerReference.get();
            permissionMap.putAll(this, ignoredPermissions);
            permissionManager.setIgnored(p, permissionMap.values()).thenRun(() -> queuedCallbacks.offer(this));
            return completableFuture;
        }

        private void acceptFirst()
        {
            this.accept(CommandResult.success());
            completableFuture.complete(null);
        }

        @Override
        public void accept(CommandResult commandResult)
        {
            Player player = playerReference.get();
            if (Objects.nonNull(player))
            {
                Tuple<String, String> t = commandList.poll();
                if (Objects.isNull(t))
                {
                    logger.debug("Player {} has executed all the commands", player.getName());
                    permissionMap.removeAll(this);
                    permissionManager.setIgnored(player, permissionMap.values());
                }
                else
                {
                    activatedIdentifiers.add(player.getIdentifier());
                    String prefix = t.getFirst(), suffix = t.getSecond();
                    String command = prefix.isEmpty() ? suffix : prefix + ": " + suffix;
                    String escapedCommand = '\'' + SpongeUnimplemented.escapeString(command) + '\'';
                    logger.debug("Player {} is now executing {}", player.getName(), escapedCommand);
                    executors.get(prefix).doAction(player, suffix, context, this);
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

    private static class SoundManager
    {
        private static SoundCategory soundCategory;

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
            // noinspection ConstantConditions
            soundCategory = Sponge.getRegistry().getType(SoundCategory.class, "minecraft:player").get();
        }
    }
}
