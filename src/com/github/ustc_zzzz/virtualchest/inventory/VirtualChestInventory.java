package com.github.ustc_zzzz.virtualchest.inventory;

import com.github.ustc_zzzz.virtualchest.VirtualChestPlugin;
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
import org.spongepowered.api.event.cause.Cause;
import org.spongepowered.api.event.item.inventory.ClickInventoryEvent;
import org.spongepowered.api.event.item.inventory.InteractInventoryEvent;
import org.spongepowered.api.item.inventory.Inventory;
import org.spongepowered.api.item.inventory.InventoryArchetypes;
import org.spongepowered.api.item.inventory.ItemStackSnapshot;
import org.spongepowered.api.item.inventory.Slot;
import org.spongepowered.api.item.inventory.property.InventoryDimension;
import org.spongepowered.api.item.inventory.property.InventoryTitle;
import org.spongepowered.api.item.inventory.property.SlotIndex;
import org.spongepowered.api.item.inventory.property.SlotPos;
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
    public static final DataQuery TITLE = Queries.TEXT_TITLE;
    public static final DataQuery HEIGHT = DataQuery.of("Rows");
    public static final DataQuery ITEM_TYPE = DataQuery.of("ItemType");
    public static final DataQuery UNSAFE_DAMAGE = DataQuery.of("UnsafeDamage");
    public static final DataQuery UPDATE_INTERVAL_TICK = DataQuery.of("UpdateIntervalTick");
    public static final DataQuery TRIGGER_ITEM = DataQuery.of("TriggerItem");

    public static final String KEY_PREFIX = "Position-";

    private Logger logger;
    private final VirtualChestPlugin plugin;

    final Multimap<SlotIndex, VirtualChestItem> items;
    final int height;
    final Text title;
    final VirtualChestTriggerItem triggerItem;
    final int updateIntervalTick;

    VirtualChestInventory(VirtualChestPlugin plugin, VirtualChestInventoryBuilder builder)
    {
        this.plugin = plugin;
        this.logger = plugin.getLogger();

        this.title = builder.title;
        this.height = builder.height;
        this.triggerItem = builder.triggerItem;
        this.updateIntervalTick = builder.updateIntervalTick;
        this.items = ImmutableMultimap.copyOf(builder.items);
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
        VirtualChestEventListener listener = new VirtualChestEventListener(player);
        return Inventory.builder().of(InventoryArchetypes.CHEST).withCarrier(player)
                .property(InventoryTitle.PROPERTY_NAME, new InventoryTitle(this.title))
                // why is it 'NAM'?
                .property(InventoryDimension.PROPERTY_NAM, new InventoryDimension(9, this.height))
                .listener(ClickInventoryEvent.class, listener::fireClickEvent)
                .listener(InteractInventoryEvent.Open.class, listener::fireOpenEvent)
                .listener(InteractInventoryEvent.Close.class, listener::fireCloseEvent)
                .build(this.plugin);
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
        return items.stream().filter(i -> i.setInventory(player, slot, pos)).findFirst();
    }

    public static String slotPosToKey(SlotIndex index) throws InvalidDataException
    {
        // SlotPos(2, 3) will be converted to "Position-3-4"
        int value = Objects.requireNonNull(index.getValue());
        int x = value % 9, y = value / 9;
        return String.format(KEY_PREFIX + "%d-%d", x + 1, y + 1);
    }

    public static SlotIndex keyToSlotPos(String key) throws InvalidDataException
    {
        // "2-3" will be converted to SlotPos(1, 2)
        if (!key.startsWith(KEY_PREFIX))
        {
            throw new InvalidDataException("Invalid format of key representation (" + key + ")! It should starts with 'Position-', such as 'Position-1-1'.");
        }
        int length = key.length(), dashIndex = length - 2;
        if (dashIndex < 0 || key.charAt(dashIndex) != '-')
        {
            throw new InvalidDataException("Invalid key representation (" + key + ") for slot pos!");
        }
        int x = Integer.valueOf(key.substring(KEY_PREFIX.length(), dashIndex)) - 1;
        int y = Integer.valueOf(key.substring(dashIndex + 1)) - 1;
        return SlotIndex.of(x + y * 9);
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
        container.set(VirtualChestInventory.TITLE, this.title);
        container.set(VirtualChestInventory.HEIGHT, this.height);
        container.set(VirtualChestInventory.TRIGGER_ITEM, this.triggerItem);
        container.set(VirtualChestInventory.UPDATE_INTERVAL_TICK, this.updateIntervalTick);
        for (Map.Entry<SlotIndex, Collection<VirtualChestItem>> entry : this.items.asMap().entrySet())
        {
            Collection<VirtualChestItem> items = entry.getValue();
            switch (items.size())
            {
            case 0:
                break;
            case 1:
                DataContainer data = VirtualChestItem.serialize(this.plugin, items.iterator().next());
                container.set(DataQuery.of(VirtualChestInventory.slotPosToKey(entry.getKey())), data);
                break;
            default:
                List<DataContainer> containerList = new LinkedList<>();
                items.forEach(item -> containerList.add(VirtualChestItem.serialize(this.plugin, item)));
                container.set(DataQuery.of(VirtualChestInventory.slotPosToKey(entry.getKey())), containerList);
            }
        }
        return container;
    }

    private class VirtualChestEventListener
    {
        private final Player player;
        private final SlotIndex slotToListen;
        private final Map<SlotIndex, VirtualChestItem> itemsInSlots;

        private final SpongeExecutorService executorService = Sponge.getScheduler().createSyncExecutor(plugin);

        private Optional<Task> autoUpdateTask = Optional.empty();

        private VirtualChestEventListener(Player player)
        {
            this.player = player;
            this.itemsInSlots = new TreeMap<>();
            this.slotToListen = SlotIndex.lessThan(height * 9);
        }

        private void refreshMappingFrom(Inventory inventory, Map<SlotIndex, VirtualChestItem> itemMap)
        {
            this.itemsInSlots.clear();
            this.itemsInSlots.putAll(itemMap);
        }

        private void fireOpenEvent(InteractInventoryEvent.Open e)
        {
            Inventory i = e.getTargetInventory().first();
            if (updateIntervalTick > 0)
            {
                autoUpdateTask = Optional.of(Sponge.getScheduler().createTaskBuilder()
                        .execute(task -> refreshMappingFrom(i, updateInventory(player, i)))
                        .delayTicks(updateIntervalTick).intervalTicks(updateIntervalTick).submit(plugin));
            }
            logger.debug("Player {} opens the chest GUI", player.getName());
            plugin.getScriptManager().onOpeningInventory(player);
            refreshMappingFrom(i, updateInventory(player, i));
        }

        private void fireCloseEvent(InteractInventoryEvent.Close e)
        {
            autoUpdateTask.ifPresent(Task::cancel);
            autoUpdateTask = Optional.empty();
            logger.debug("Player {} closes the chest GUI", player.getName());
        }

        private void fireClickEvent(ClickInventoryEvent e)
        {
            CompletableFuture<Boolean> future = CompletableFuture.completedFuture(Boolean.TRUE);
            for (SlotTransaction slotTransaction : e.getTransactions())
            {
                Slot slot = slotTransaction.getSlot();
                SlotIndex pos = SpongeUnimplemented.getSlotOrdinal(slot);
                if (SpongeUnimplemented.isSlotInInventory(slot, e.getTargetInventory()) && slotToListen.matches(pos))
                {
                    e.setCancelled(true);
                    if (itemsInSlots.containsKey(pos))
                    {
                        VirtualChestItem item = itemsInSlots.get(pos);
                        future = future.thenApplyAsync(previous ->
                        {
                            String playerName = player.getName();
                            logger.debug("Player {} tries to click the chest GUI at {}", playerName, slotPosToKey(pos));
                            if (e instanceof ClickInventoryEvent.Primary)
                            {
                                return item.doPrimaryAction(player) && previous;
                            }
                            if (e instanceof ClickInventoryEvent.Secondary)
                            {
                                return item.doSecondaryAction(player) && previous;
                            }
                            return previous;
                        }, executorService);
                    }
                }
            }
            future.thenAccept(this::closeInventoryIfRequested);
        }

        private void closeInventoryIfRequested(boolean shouldKeepInventoryOpen)
        {
            if (!shouldKeepInventoryOpen)
            {
                player.closeInventory(Cause.source(plugin).build());
            }
        }
    }
}
