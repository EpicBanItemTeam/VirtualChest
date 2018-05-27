package com.github.ustc_zzzz.virtualchest.inventory;

import co.aikar.timings.Timing;
import com.github.ustc_zzzz.virtualchest.VirtualChestPlugin;
import com.github.ustc_zzzz.virtualchest.action.VirtualChestActionDispatcher;
import com.github.ustc_zzzz.virtualchest.action.VirtualChestActionIntervalManager;
import com.github.ustc_zzzz.virtualchest.action.VirtualChestActions;
import com.github.ustc_zzzz.virtualchest.inventory.item.VirtualChestItem;
import com.github.ustc_zzzz.virtualchest.inventory.trigger.VirtualChestTriggerItem;
import com.github.ustc_zzzz.virtualchest.permission.VirtualChestPermissionManager;
import com.github.ustc_zzzz.virtualchest.record.VirtualChestRecordManager;
import com.github.ustc_zzzz.virtualchest.timings.VirtualChestTimings;
import com.github.ustc_zzzz.virtualchest.unsafe.SpongeUnimplemented;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Multimap;
import org.slf4j.Logger;
import org.spongepowered.api.Sponge;
import org.spongepowered.api.command.CommandResult;
import org.spongepowered.api.data.*;
import org.spongepowered.api.data.persistence.InvalidDataException;
import org.spongepowered.api.entity.living.player.Player;
import org.spongepowered.api.event.item.inventory.ClickInventoryEvent;
import org.spongepowered.api.event.item.inventory.InteractInventoryEvent;
import org.spongepowered.api.item.inventory.*;
import org.spongepowered.api.item.inventory.property.InventoryDimension;
import org.spongepowered.api.item.inventory.property.InventoryTitle;
import org.spongepowered.api.item.inventory.property.SlotIndex;
import org.spongepowered.api.item.inventory.transaction.SlotTransaction;
import org.spongepowered.api.scheduler.SpongeExecutorService;
import org.spongepowered.api.scheduler.Task;
import org.spongepowered.api.text.Text;
import org.spongepowered.api.util.Coerce;
import org.spongepowered.api.util.Tristate;
import org.spongepowered.api.util.Tuple;
import org.spongepowered.api.util.annotation.NonnullByDefault;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * @author ustc_zzzz
 */
@NonnullByDefault
public final class VirtualChestInventory implements DataSerializable
{
    static final DataQuery TITLE = Queries.TEXT_TITLE;
    static final DataQuery HEIGHT = DataQuery.of("Rows");
    static final DataQuery OPEN_ACTION_COMMAND = DataQuery.of("OpenActionCommand");
    static final DataQuery CLOSE_ACTION_COMMAND = DataQuery.of("CloseActionCommand");
    static final DataQuery UPDATE_INTERVAL_TICK = DataQuery.of("UpdateIntervalTick");
    static final DataQuery ACCEPTABLE_ACTION_INTERVAL_TICK = DataQuery.of("AcceptableActionIntervalTick");
    static final DataQuery TRIGGER_ITEM = DataQuery.of("TriggerItem");

    static final String KEY_PREFIX = "Slot";

    private final Logger logger;
    private final VirtualChestPlugin plugin;
    private final SpongeExecutorService executorService;
    private final VirtualChestRecordManager recordManager;
    private final VirtualChestActionIntervalManager actionIntervalManager;

    private final Map<UUID, Inventory> inventories = new HashMap<>();

    final List<List<VirtualChestItem>> items;
    final Text title;
    final int height;
    final List<VirtualChestTriggerItem> triggerItems;
    final Optional<String> openActionCommand;
    final Optional<String> closeActionCommand;
    final int updateIntervalTick;
    final OptionalInt acceptableActionIntervalTick;

    VirtualChestInventory(VirtualChestPlugin plugin, VirtualChestInventoryBuilder builder)
    {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
        this.recordManager = plugin.getRecordManager();
        this.actionIntervalManager = plugin.getActionIntervalManager();
        this.executorService = Sponge.getScheduler().createSyncExecutor(plugin);

        this.title = builder.title;
        this.height = builder.height;
        this.triggerItems = ImmutableList.copyOf(builder.triggerItems);
        this.openActionCommand = builder.openActionCommand;
        this.closeActionCommand = builder.closeActionCommand;
        this.updateIntervalTick = builder.updateIntervalTick;
        this.items = this.createListFromMultiMap(builder.items, builder.height * 9);
        this.acceptableActionIntervalTick = builder.actionIntervalTick.map(OptionalInt::of).orElse(OptionalInt.empty());
    }

    public boolean matchItemForOpeningWithPrimaryAction(ItemStackSnapshot item)
    {
        return triggerItems.stream().anyMatch(t -> t.matchItemForOpeningWithPrimaryAction(item));
    }

    public boolean matchItemForOpeningWithSecondaryAction(ItemStackSnapshot item)
    {
        return triggerItems.stream().anyMatch(t -> t.matchItemForOpeningWithSecondaryAction(item));
    }

    public Inventory createInventory(Player player, String inventoryName)
    {
        UUID uuid = player.getUniqueId();
        if (!inventories.containsKey(uuid))
        {
            EventListener listener = new EventListener(player, inventoryName);
            Inventory chestInventory = Inventory.builder().of(InventoryArchetypes.CHEST).withCarrier(player)
                    .property(InventoryDimension.PROPERTY_NAME, new InventoryDimension(9, this.height))
                    .property(InventoryTitle.PROPERTY_NAME, new InventoryTitle(this.title))
                    .listener(InteractInventoryEvent.Close.class, listener::fireCloseEvent)
                    .listener(InteractInventoryEvent.Open.class, listener::fireOpenEvent)
                    .listener(ClickInventoryEvent.class, listener::fireClickEvent)
                    .build(this.plugin);
            inventories.put(uuid, chestInventory);
            return chestInventory;
        }
        return inventories.get(uuid);
    }

    private <T> List<List<T>> createListFromMultiMap(Multimap<SlotIndex, T> list, int max)
    {
        ImmutableList.Builder<List<T>> builder = ImmutableList.builder();
        for (int i = 0; i < max; ++i)
        {
            builder.add(ImmutableList.copyOf(list.get(SlotIndex.of(i))));
        }
        return builder.build();
    }

    private void updateInventory(Player player, Inventory inventory, String name)
    {
        int index = 0;
        for (Slot slot : inventory.<Slot>slots())
        {
            Timing timing = VirtualChestTimings.updateAndRefreshMapping(name, index);
            timing.startTimingIfSync();
            this.setItemInInventory(player, slot, index++, name);
            timing.stopTimingIfSync();
        }
    }

    private void setItemInInventory(Player player, Slot slot, int index, String name)
    {
        List<VirtualChestItem> items = this.items.get(index);
        for (VirtualChestItem i : items)
        {
            if (i.matchRequirements(player, index, name))
            {
                i.fillInventory(player, slot, index, name);
                return;
            }
        }
        slot.clear();
    }

    public static String slotIndexToKey(int index) throws InvalidDataException
    {
        // SlotIndex(21) will be converted to "Slot21"
        return KEY_PREFIX + index;
    }

    public static int keyToSlotIndex(String key) throws InvalidDataException
    {
        // "Slot21" will be converted to SlotIndex(21)
        return Coerce.toInteger(key.substring(KEY_PREFIX.length()));
    }

    @Override
    public int getContentVersion()
    {
        return 0;
    }

    @Override
    public DataContainer toContainer()
    {
        DataContainer container = new MemoryDataContainer();
        container.set(TITLE, this.title);
        container.set(HEIGHT, this.height);
        switch (this.triggerItems.size())
        {
        case 0:
            break;
        case 1:
            container.set(TRIGGER_ITEM, this.triggerItems.iterator().next().toContainer());
            break;
        default:
            List<DataContainer> containerList = new LinkedList<>();
            this.triggerItems.forEach(t -> containerList.add(t.toContainer()));
            container.set(TRIGGER_ITEM, containerList);
        }
        container.set(UPDATE_INTERVAL_TICK, this.updateIntervalTick);
        this.openActionCommand.ifPresent(c -> container.set(OPEN_ACTION_COMMAND, c));
        this.closeActionCommand.ifPresent(c -> container.set(CLOSE_ACTION_COMMAND, c));
        for (int i = 0; i < this.items.size(); i++)
        {
            List<VirtualChestItem> items = this.items.get(i);
            switch (items.size())
            {
            case 0:
                break;
            case 1:
                DataContainer data = VirtualChestItem.serialize(this.plugin, items.iterator().next());
                container.set(DataQuery.of(slotIndexToKey(i)), data);
                break;
            default:
                List<DataContainer> containerList = new LinkedList<>();
                items.forEach(item -> containerList.add(VirtualChestItem.serialize(this.plugin, item)));
                container.set(DataQuery.of(slotIndexToKey(i)), containerList);
            }
        }
        return container;
    }

    public static class ClickStatus
    {
        public final boolean isShift;
        public final boolean isPrimary;
        public final boolean isSecondary;

        public ClickStatus(boolean isShift, boolean isPrimary, boolean isSecondary)
        {
            this.isShift = isShift;
            this.isPrimary = isPrimary;
            this.isSecondary = isSecondary;
        }

        public ClickStatus(ClickInventoryEvent e)
        {
            this.isShift = e instanceof ClickInventoryEvent.Shift;
            this.isPrimary = e instanceof ClickInventoryEvent.Primary;
            this.isSecondary = e instanceof ClickInventoryEvent.Secondary;
        }

        @Override
        public String toString()
        {
            String format = "ClickStatus{isShift=%s, isPrimary=%s, isSecondary=%s}";
            return String.format(format, this.isShift, this.isPrimary, this.isSecondary);
        }
    }

    private class EventListener
    {
        private final String name;
        private final UUID playerUniqueId;
        private final SlotIndex slotToListen;
        private final List<String> parsedOpenAction;
        private final List<String> parsedCloseAction;

        private EventListener(Player player, String inventoryName)
        {
            this.parsedOpenAction = VirtualChestActionDispatcher.parseCommand(openActionCommand.orElse(""));
            this.parsedCloseAction = VirtualChestActionDispatcher.parseCommand(closeActionCommand.orElse(""));
            this.slotToListen = SlotIndex.lessThan(height * 9);
            this.playerUniqueId = player.getUniqueId();
            this.name = inventoryName;
        }

        private void fireOpenEvent(InteractInventoryEvent.Open e)
        {
            Optional<Player> optional = Sponge.getServer().getPlayer(playerUniqueId);
            if (optional.isPresent())
            {
                Player player = optional.get();
                UUID actionUUID = UUID.randomUUID();
                Container targetContainer = e.getTargetInventory();
                Inventory targetInventory = targetContainer.first();
                Timing timing = VirtualChestTimings.updateAndRefreshMappings(name);
                Map<String, Object> context = ImmutableMap.of(VirtualChestActions.ACTION_UUID_KEY, actionUUID);

                recordManager.recordOpen(actionUUID, name, player);
                logger.debug("Player {} opens the chest GUI", player.getName());
                plugin.getVirtualChestActions().submitCommands(player, parsedOpenAction.stream(), context);

                if (updateIntervalTick > 0)
                {
                    Task.Builder builder = Sponge.getScheduler().createTaskBuilder().execute(task ->
                    {
                        if (player.getOpenInventory().filter(targetContainer::equals).isPresent())
                        {
                            timing.startTimingIfSync();
                            updateInventory(player, targetInventory, name);
                            timing.stopTimingIfSync();
                        }
                        else
                        {
                            task.cancel();
                        }
                    });
                    builder.delayTicks(updateIntervalTick).intervalTicks(updateIntervalTick).submit(plugin);
                }

                plugin.getScriptManager().onOpeningInventory(player);

                timing.startTimingIfSync();
                updateInventory(player, targetInventory, name);
                timing.stopTimingIfSync();
            }
        }

        private void fireCloseEvent(InteractInventoryEvent.Close e)
        {
            Optional<Player> optional = Sponge.getServer().getPlayer(playerUniqueId);
            if (optional.isPresent())
            {
                Player player = optional.get();
                UUID actionUUID = UUID.randomUUID();
                Map<String, Object> context = ImmutableMap.of(VirtualChestActions.ACTION_UUID_KEY, actionUUID);

                recordManager.recordClose(actionUUID, name, player);
                logger.debug("Player {} closes the chest GUI", player.getName());
                plugin.getVirtualChestActions().submitCommands(player, parsedCloseAction.stream(), context);

                actionIntervalManager.onClosingInventory(player);
            }
        }

        private void fireClickEvent(ClickInventoryEvent e)
        {
            Optional<Player> optional = Sponge.getServer().getPlayer(playerUniqueId);
            if (optional.isPresent())
            {
                Player player = optional.get();
                CompletableFuture<Boolean> future = CompletableFuture.supplyAsync(() -> Boolean.TRUE, executorService);
                for (SlotTransaction slotTransaction : e.getTransactions())
                {
                    Slot slot = slotTransaction.getSlot();
                    Container targetContainer = e.getTargetInventory();
                    SlotIndex pos = SpongeUnimplemented.getSlotOrdinal(slot);
                    if (SpongeUnimplemented.isSlotInInventory(slot, targetContainer) && slotToListen.matches(pos))
                    {
                        e.setCancelled(true);
                        if (actionIntervalManager.allowAction(player, acceptableActionIntervalTick))
                        {
                            int index = Objects.requireNonNull(pos.getValue());
                            future = future.thenCompose(b -> this.runCommand(e, player, index).thenApply(a -> a && b));
                        }
                    }
                }
                future.thenAccept(shouldKeepInventoryOpen -> this.closeWhile(shouldKeepInventoryOpen, player));
            }
        }

        private void closeWhile(boolean shouldKeepInventoryOpen, Player player)
        {
            if (!shouldKeepInventoryOpen)
            {
                SpongeUnimplemented.closeInventory(player, plugin);
            }
        }

        private CompletableFuture<Boolean> runCommand(ClickInventoryEvent e, Player player, int slotIndex)
        {
            Timing timing = VirtualChestTimings.updateAndRefreshMapping(name, slotIndex);
            timing.startTimingIfSync();

            String playerName = player.getName();
            ClickStatus status = new ClickStatus(e);
            String keyString = slotIndexToKey(slotIndex);
            CompletableFuture<Boolean> future = new CompletableFuture<>();
            VirtualChestPermissionManager permissionManager = plugin.getPermissionManager();

            List<VirtualChestItem> items = VirtualChestInventory.this.items.get(slotIndex);
            logger.debug("Player {} tries to click the chest GUI at {} with {}", playerName, keyString, status);

            int size = items.size();
            List<CompletableFuture<?>> setFutures = new ArrayList<>(size);
            List<Supplier<CompletableFuture<?>>> clearFutures = new ArrayList<>(size);
            for (VirtualChestItem item : items)
            {
                UUID actionUUID = UUID.randomUUID();
                List<String> list = item.getIgnoredPermissions();
                setFutures.add(permissionManager.setIgnored(player, actionUUID, list));
                clearFutures.add(() ->
                {
                    if (future.isDone() || !item.matchRequirements(player, slotIndex, playerName))
                    {
                        return permissionManager.clearIgnored(player, actionUUID);
                    }

                    Optional<VirtualChestActionDispatcher> optional = item.getAction(status);
                    recordManager.recordSlotClick(actionUUID, name, slotIndex, status, player);
                    logger.debug("Player {} now submits an action: {}", playerName, actionUUID);

                    if (!optional.isPresent())
                    {
                        future.complete(true);
                        return permissionManager.clearIgnored(player, actionUUID);
                    }

                    Tuple<Boolean, CompletableFuture<CommandResult>> tuple;
                    tuple = optional.get().runCommand(plugin, actionUUID, player);

                    future.complete(tuple.getFirst());
                    return tuple.getSecond().thenCompose(r -> permissionManager.clearIgnored(player, actionUUID));
                });
            }

            timing.stopTimingIfSync();

            CompletableFuture.allOf(setFutures.toArray(new CompletableFuture[0]))
                    .thenComposeAsync(v -> CompletableFuture.allOf(clearFutures.stream().map(Supplier::get)
                            .toArray(CompletableFuture[]::new)), executorService).thenRun(() -> future.complete(true));

            return future;
        }
    }
}
