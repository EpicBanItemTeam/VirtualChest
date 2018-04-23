package com.github.ustc_zzzz.virtualchest.inventory;

import co.aikar.timings.Timing;
import com.github.ustc_zzzz.virtualchest.VirtualChestPlugin;
import com.github.ustc_zzzz.virtualchest.action.VirtualChestActionDispatcher;
import com.github.ustc_zzzz.virtualchest.action.VirtualChestActionIntervalManager;
import com.github.ustc_zzzz.virtualchest.inventory.item.VirtualChestItem;
import com.github.ustc_zzzz.virtualchest.inventory.trigger.VirtualChestTriggerItem;
import com.github.ustc_zzzz.virtualchest.timings.VirtualChestTimings;
import com.github.ustc_zzzz.virtualchest.unsafe.SpongeUnimplemented;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Multimap;
import org.slf4j.Logger;
import org.spongepowered.api.Sponge;
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
import org.spongepowered.api.util.annotation.NonnullByDefault;

import java.util.*;
import java.util.concurrent.CompletableFuture;

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
    private final VirtualChestActionIntervalManager actionIntervalManager;
    private final Map<UUID, Inventory> inventories = new HashMap<>();

    final List<Collection<VirtualChestItem>> items;
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
        this.actionIntervalManager = plugin.getActionIntervalManager();

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
            VirtualChestEventListener listener = new VirtualChestEventListener(player, inventoryName);
            Inventory chestInventory = Inventory.builder().of(InventoryArchetypes.CHEST).withCarrier(player)
                    .property(InventoryTitle.PROPERTY_NAME, new InventoryTitle(this.title))
                    .property(InventoryDimension.PROPERTY_NAME, new InventoryDimension(9, this.height))
                    .listener(ClickInventoryEvent.class, listener::fireClickEvent)
                    .listener(InteractInventoryEvent.Open.class, listener::fireOpenEvent)
                    .listener(InteractInventoryEvent.Close.class, listener::fireCloseEvent)
                    .build(this.plugin);
            inventories.put(uuid, chestInventory);
            return chestInventory;
        }
        return inventories.get(uuid);
    }

    private <T> List<Collection<T>> createListFromMultiMap(Multimap<SlotIndex, T> list, int max)
    {
        ImmutableList.Builder<Collection<T>> builder = ImmutableList.builder();
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
        Collection<VirtualChestItem> items = this.items.get(index);
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
            Collection<VirtualChestItem> items = this.items.get(i);
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

    private class VirtualChestEventListener
    {
        private final String name;
        private final UUID playerUniqueId;
        private final SlotIndex slotToListen;
        private final List<String> parsedOpenAction;
        private final List<String> parsedCloseAction;

        private final SpongeExecutorService executorService = Sponge.getScheduler().createSyncExecutor(plugin);

        private VirtualChestEventListener(Player player, String inventoryName)
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
                Container targetContainer = e.getTargetInventory();
                Inventory targetInventory = targetContainer.first();
                Timing timing = VirtualChestTimings.updateAndRefreshMappings(name);
                String key = VirtualChestItem.IGNORED_PERMISSIONS.toString();
                plugin.getVirtualChestActions().submitCommands(player, parsedOpenAction, ImmutableMap.of(key, ImmutableList.of()));
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
                logger.debug("Player {} opens the chest GUI", player.getName());
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
                String key = VirtualChestItem.IGNORED_PERMISSIONS.toString();
                plugin.getVirtualChestActions().submitCommands(player, parsedCloseAction, ImmutableMap.of(key, ImmutableList.of()));
                logger.debug("Player {} closes the chest GUI", player.getName());
                actionIntervalManager.onClosingInventory(player);
            }
        }

        private void fireClickEvent(ClickInventoryEvent e)
        {
            Optional<Player> optional = Sponge.getServer().getPlayer(playerUniqueId);
            if (!optional.isPresent())
            {
                return;
            }
            Player player = optional.get();
            CompletableFuture<Boolean> future = CompletableFuture.completedFuture(Boolean.TRUE);
            for (SlotTransaction slotTransaction : e.getTransactions())
            {
                Slot slot = slotTransaction.getSlot();
                SlotIndex pos = SpongeUnimplemented.getSlotOrdinal(slot);
                if (SpongeUnimplemented.isSlotInInventory(slot, e.getTargetInventory()) && slotToListen.matches(pos))
                {
                    e.setCancelled(true);
                    if (actionIntervalManager.allowAction(player, acceptableActionIntervalTick))
                    {
                        int index = Objects.requireNonNull(pos.getValue());
                        future = this.wrapFuture(future, e, player, index);
                    }
                }
            }
            future.thenAccept(shouldKeepInventoryOpen ->
            {
                if (!shouldKeepInventoryOpen)
                {
                    SpongeUnimplemented.closeInventory(player, plugin);
                }
            });
        }

        private CompletableFuture<Boolean> wrapFuture(CompletableFuture<Boolean> future, ClickInventoryEvent e, Player player, int slotIndex)
        {
            Timing timing = VirtualChestTimings.updateAndRefreshMapping(name, slotIndex);
            timing.startTimingIfSync();

            boolean isShift = e instanceof ClickInventoryEvent.Shift;
            boolean isPrimary = e instanceof ClickInventoryEvent.Primary;
            boolean isSecondary = e instanceof ClickInventoryEvent.Secondary;

            Optional<VirtualChestActionDispatcher> dispatcherOptional = Optional.empty();
            List<String> ignoredPermissions = new ArrayList<>();
            for (VirtualChestItem i : items.get(slotIndex))
            {
                if (i.matchRequirements(player, slotIndex, name))
                {
                    dispatcherOptional = i.getAction(isPrimary, isSecondary, isShift);
                    ignoredPermissions.addAll(i.getIgnoredPermissions());
                    break;
                }
            }

            if (dispatcherOptional.isPresent())
            {
                VirtualChestActionDispatcher actionDispatcher = dispatcherOptional.get();
                future = future.thenCompose(p ->
                {
                    String log = "Player {} tries to click the chest GUI at {}, primary: {}, secondary: {}, shift: {}";
                    logger.debug(log, player.getName(), slotIndexToKey(slotIndex), isPrimary, isSecondary, isShift);
                    return actionDispatcher.runCommand(plugin, player, ignoredPermissions).thenApply(b -> b && p);
                });
            }

            timing.stopTimingIfSync();

            return future;
        }
    }
}
