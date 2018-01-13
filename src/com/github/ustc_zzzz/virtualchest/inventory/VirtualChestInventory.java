package com.github.ustc_zzzz.virtualchest.inventory;

import com.github.ustc_zzzz.virtualchest.VirtualChestPlugin;
import com.github.ustc_zzzz.virtualchest.action.VirtualChestActionDispatcher;
import com.github.ustc_zzzz.virtualchest.action.VirtualChestActionIntervalManager;
import com.github.ustc_zzzz.virtualchest.inventory.item.VirtualChestItem;
import com.github.ustc_zzzz.virtualchest.inventory.trigger.VirtualChestTriggerItem;
import com.github.ustc_zzzz.virtualchest.unsafe.SpongeUnimplemented;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMultimap;
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

    final Multimap<SlotIndex, VirtualChestItem> items;
    final Text title;
    final int height;
    final VirtualChestTriggerItem triggerItem;
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
        this.triggerItem = builder.triggerItem;
        this.openActionCommand = builder.openActionCommand;
        this.closeActionCommand = builder.closeActionCommand;
        this.updateIntervalTick = builder.updateIntervalTick;
        this.items = ImmutableMultimap.copyOf(builder.items);
        this.acceptableActionIntervalTick = builder.actionIntervalTick.map(OptionalInt::of).orElse(OptionalInt.empty());
    }

    public boolean matchItemForOpeningWithPrimaryAction(ItemStackSnapshot item)
    {
        return triggerItem.matchItemForOpeningWithPrimaryAction(item);
    }

    public boolean matchItemForOpeningWithSecondaryAction(ItemStackSnapshot item)
    {
        return triggerItem.matchItemForOpeningWithSecondaryAction(item);
    }

    public Inventory createInventory(Player player)
    {
        UUID uuid = player.getUniqueId();
        if (!inventories.containsKey(uuid))
        {
            VirtualChestEventListener listener = new VirtualChestEventListener(player);
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

    private Map<SlotIndex, VirtualChestItem> updateInventory(Player player, Inventory chestInventory)
    {
        int i = 0;
        ImmutableMap.Builder<SlotIndex, VirtualChestItem> itemBuilder = ImmutableMap.builder();
        for (Slot slot : chestInventory.<Slot>slots())
        {
            SlotIndex pos = SlotIndex.of(i++);
            this.setItemInInventory(player, slot, pos).ifPresent(item -> itemBuilder.put(pos, item));
        }
        return itemBuilder.build();
    }

    private Optional<VirtualChestItem> setItemInInventory(Player player, Slot slot, SlotIndex pos)
    {
        Collection<VirtualChestItem> items = this.items.get(pos);
        for (VirtualChestItem i : items)
        {
            if (i.setInventory(player, slot, pos))
            {
                return Optional.of(i);
            }
        }
        slot.clear();
        return Optional.empty();
    }

    public static String slotIndexToKey(SlotIndex index) throws InvalidDataException
    {
        // SlotIndex(21) will be converted to "Slot21"
        return KEY_PREFIX + index.getValue();
    }

    public static SlotIndex keyToSlotIndex(String key) throws InvalidDataException
    {
        // "Slot21" will be converted to SlotIndex(21)
        return SlotIndex.of(key.substring(KEY_PREFIX.length()));
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
        container.set(TRIGGER_ITEM, this.triggerItem);
        container.set(UPDATE_INTERVAL_TICK, this.updateIntervalTick);
        this.openActionCommand.ifPresent(c -> container.set(OPEN_ACTION_COMMAND, c));
        this.closeActionCommand.ifPresent(c -> container.set(CLOSE_ACTION_COMMAND, c));
        for (Map.Entry<SlotIndex, Collection<VirtualChestItem>> entry : this.items.asMap().entrySet())
        {
            Collection<VirtualChestItem> items = entry.getValue();
            switch (items.size())
            {
            case 0:
                break;
            case 1:
                DataContainer data = VirtualChestItem.serialize(this.plugin, items.iterator().next());
                container.set(DataQuery.of(slotIndexToKey(entry.getKey())), data);
                break;
            default:
                List<DataContainer> containerList = new LinkedList<>();
                items.forEach(item -> containerList.add(VirtualChestItem.serialize(this.plugin, item)));
                container.set(DataQuery.of(slotIndexToKey(entry.getKey())), containerList);
            }
        }
        return container;
    }

    private class VirtualChestEventListener
    {
        private final UUID playerUniqueId;
        private final SlotIndex slotToListen;
        private final List<String> parsedOpenAction;
        private final List<String> parsedCloseAction;
        private final Map<SlotIndex, VirtualChestItem> itemsInSlots;

        private final SpongeExecutorService executorService = Sponge.getScheduler().createSyncExecutor(plugin);

        private VirtualChestEventListener(Player player)
        {
            this.itemsInSlots = new TreeMap<>();
            this.parsedOpenAction = VirtualChestActionDispatcher.parseCommand(openActionCommand.orElse(""));
            this.parsedCloseAction = VirtualChestActionDispatcher.parseCommand(closeActionCommand.orElse(""));
            this.slotToListen = SlotIndex.lessThan(height * 9);
            this.playerUniqueId = player.getUniqueId();
        }

        private void refreshMappingFrom(Inventory inventory, Map<SlotIndex, VirtualChestItem> itemMap)
        {
            this.itemsInSlots.clear();
            this.itemsInSlots.putAll(itemMap);
        }

        private boolean processClickEvent(ClickInventoryEvent e, Player player, SlotIndex pos)
        {
            VirtualChestItem item = itemsInSlots.get(pos);
            String log = "Player {} tries to click the chest GUI at {}, primary: {}, secondary: {}, shift: {}";

            boolean isShift = e instanceof ClickInventoryEvent.Shift;
            boolean isPrimary = e instanceof ClickInventoryEvent.Primary;
            boolean isSecondary = e instanceof ClickInventoryEvent.Secondary;

            logger.debug(log, player.getName(), slotIndexToKey(pos), isPrimary, isSecondary, isShift);
            Optional<VirtualChestActionDispatcher> optional = item.getAction(isPrimary, isSecondary, isShift);
            return optional.map(d -> d.runCommand(plugin, player, item.getIgnoredPermissions())).orElse(Boolean.TRUE);
        }

        private void fireOpenEvent(InteractInventoryEvent.Open e)
        {
            Optional<Player> optional = Sponge.getServer().getPlayer(playerUniqueId);
            if (optional.isPresent())
            {
                Player player = optional.get();
                Container targetContainer = e.getTargetInventory();
                Inventory targetInventory = targetContainer.first();
                plugin.getVirtualChestActions().submitCommands(player, parsedOpenAction, ImmutableList.of());
                if (updateIntervalTick > 0)
                {
                    Task.Builder builder = Sponge.getScheduler().createTaskBuilder().execute(task ->
                    {
                        if (player.getOpenInventory().filter(targetContainer::equals).isPresent())
                        {
                            refreshMappingFrom(targetInventory, updateInventory(player, targetInventory));
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
                refreshMappingFrom(targetInventory, updateInventory(player, targetInventory));
            }
        }

        private void fireCloseEvent(InteractInventoryEvent.Close e)
        {
            Optional<Player> optional = Sponge.getServer().getPlayer(playerUniqueId);
            if (optional.isPresent())
            {
                Player player = optional.get();
                plugin.getVirtualChestActions().submitCommands(player, parsedCloseAction, ImmutableList.of());
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
                    boolean allowAction = actionIntervalManager.allowAction(player, acceptableActionIntervalTick);
                    if (allowAction && itemsInSlots.containsKey(pos))
                    {
                        future = future.thenApplyAsync(p -> processClickEvent(e, player, pos) && p, executorService);
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
    }
}
