package com.github.ustc_zzzz.virtualchest.inventory;

import com.github.ustc_zzzz.virtualchest.VirtualChestPlugin;
import com.github.ustc_zzzz.virtualchest.inventory.item.VirtualChestItem;
import com.github.ustc_zzzz.virtualchest.inventory.trigger.VirtualChestTriggerItem;
import com.github.ustc_zzzz.virtualchest.unsafe.SpongeUnimplemented;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;
import org.slf4j.Logger;
import org.spongepowered.api.Sponge;
import org.spongepowered.api.data.DataQuery;
import org.spongepowered.api.data.Queries;
import org.spongepowered.api.data.persistence.InvalidDataException;
import org.spongepowered.api.entity.living.player.Player;
import org.spongepowered.api.event.cause.Cause;
import org.spongepowered.api.event.item.inventory.ClickInventoryEvent;
import org.spongepowered.api.event.item.inventory.InteractInventoryEvent;
import org.spongepowered.api.item.inventory.*;
import org.spongepowered.api.item.inventory.property.InventoryDimension;
import org.spongepowered.api.item.inventory.property.InventoryTitle;
import org.spongepowered.api.item.inventory.property.SlotPos;
import org.spongepowered.api.item.inventory.transaction.SlotTransaction;
import org.spongepowered.api.scheduler.SpongeExecutorService;
import org.spongepowered.api.scheduler.Task;
import org.spongepowered.api.text.Text;

import java.util.*;

/**
 * @author ustc_zzzz
 */
public final class VirtualChestInventory
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

    final Multimap<SlotPos, VirtualChestItem> items;
    final int height;
    final Text title;
    final VirtualChestTriggerItem triggerItem;
    final int updateIntervalTick;

    VirtualChestInventory(
            VirtualChestPlugin plugin,
            Text title,
            int height,
            Multimap<SlotPos, VirtualChestItem> items,
            VirtualChestTriggerItem triggerItem,
            int updateIntervalTick)
    {
        this.plugin = plugin;
        this.logger = plugin.getLogger();

        this.title = title;
        this.height = height;
        this.items = ImmutableMultimap.copyOf(items);
        this.triggerItem = triggerItem;
        this.updateIntervalTick = updateIntervalTick;
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
        Inventory chestInventory = Inventory.builder().of(InventoryArchetypes.CHEST).withCarrier(player)
                .property(InventoryTitle.PROPERTY_NAME, new InventoryTitle(this.title))
                // why is it 'NAM'?
                .property(InventoryDimension.PROPERTY_NAM, new InventoryDimension(9, this.height))
                .listener(ClickInventoryEvent.class, listener::fireClickEvent)
                .listener(InteractInventoryEvent.Open.class, listener::fireOpenEvent)
                .listener(InteractInventoryEvent.Close.class, listener::fireCloseEvent)
                .build(this.plugin);
        listener.refreshMappingFrom(chestInventory, this.updateInventory(player, chestInventory));
        return chestInventory;
    }

    private Map<SlotPos, VirtualChestItem> updateInventory(Player player, Inventory chestInventory)
    {
        int i = -1, j = 0;
        ImmutableMap.Builder<SlotPos, VirtualChestItem> itemBuilder = ImmutableMap.builder();
        for (Slot slot : chestInventory.<Slot>slots())
        {
            if (++i >= 9)
            {
                ++j;
                i = 0;
            }
            SlotPos pos = SlotPos.of(i, j);
            this.setItemInInventory(player, slot, pos).ifPresent(item -> itemBuilder.put(pos, item));
        }
        return itemBuilder.build();
    }

    private Optional<VirtualChestItem> setItemInInventory(Player player, Slot slot, SlotPos pos)
    {
        Collection<VirtualChestItem> items = this.items.get(pos);
        return items.stream().filter(i -> i.setInventory(player, slot, pos)).findFirst();
    }

    public static String slotPosToKey(SlotPos slotPos) throws InvalidDataException
    {
        // SlotPos(2, 3) will be converted to "Position-3-4"
        int x = slotPos.getX(), y = slotPos.getY();
        if (y < 0 || y >= 9)
        {
            throw new InvalidDataException("Y position (" + y + ") out of bound!");
        }
        return String.format(KEY_PREFIX + "%d-%d", x + 1, y + 1);
    }

    public static SlotPos keyToSlotPos(String key) throws InvalidDataException
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
        return SlotPos.of(Integer.valueOf(key.substring(KEY_PREFIX.length(), dashIndex)) - 1, Integer.valueOf(key.substring(dashIndex + 1)) - 1);
    }

    private class VirtualChestEventListener
    {
        private final Comparator<Slot> slotComparator;
        private final Map<Slot, VirtualChestItem> slotToItem;
        private final Map<Slot, SlotPos> slotToSlotPos;
        private final Player player;

        private Optional<Task> autoUpdateTask = Optional.empty();
        private SpongeExecutorService executorService = Sponge.getScheduler().createSyncExecutor(plugin);

        private VirtualChestEventListener(Player player)
        {
            this.slotComparator = Comparator.comparingInt(SpongeUnimplemented::getSlotOrdinal);
            this.slotToItem = new TreeMap<>(this.slotComparator);
            this.slotToSlotPos = new TreeMap<>(this.slotComparator);
            this.player = player;
        }

        private void refreshMappingFrom(Inventory inventory, Map<SlotPos, VirtualChestItem> itemMap)
        {
            this.slotToItem.clear();
            this.slotToSlotPos.clear();
            int i = -1, j = 0;
            for (Slot slot : inventory.<Slot>slots())
            {
                if (++i >= 9)
                {
                    ++j;
                    i = 0;
                }
                SlotPos slotPos = SlotPos.of(i, j);
                Optional.ofNullable(itemMap.get(slotPos)).ifPresent(item ->
                {
                    slotToItem.put(slot, item);
                    slotToSlotPos.put(slot, slotPos);
                });
            }
        }

        private void fireOpenEvent(InteractInventoryEvent.Open e)
        {
            Inventory chestInventory = e.getTargetInventory().first();
            if (updateIntervalTick > 0)
            {
                autoUpdateTask = Optional.of(Sponge.getScheduler().createTaskBuilder()
                        .execute(task -> refreshMappingFrom(chestInventory, updateInventory(player, chestInventory)))
                        .intervalTicks(updateIntervalTick).submit(plugin));
            }
            logger.debug("Player {} opens the chest GUI", player.getName());
        }

        private void fireCloseEvent(InteractInventoryEvent.Close e)
        {
            autoUpdateTask.ifPresent(Task::cancel);
            autoUpdateTask = Optional.empty();
            logger.debug("Player {} closes the chest GUI", player.getName());
        }

        private void fireClickEvent(ClickInventoryEvent e)
        {
            boolean shouldCloseInventory = false;
            for (SlotTransaction slotTransaction : e.getTransactions())
            {
                Slot slot = slotTransaction.getSlot();
                if (SpongeUnimplemented.isSlotInInventory(slot, e.getTargetInventory())
                        && SpongeUnimplemented.getSlotOrdinal(slot) < height * 9)
                {
                    e.setCancelled(true);
                    if (slotToItem.containsKey(slot))
                    {
                        SlotPos pos = slotToSlotPos.get(slot);
                        VirtualChestItem item = slotToItem.get(slot);
                        if (transferEventsToItem(e, pos, item))
                        {
                            shouldCloseInventory = true;
                        }
                    }
                }
            }
            if (shouldCloseInventory)
            {
                executorService.submit(() -> player.closeInventory(Cause.source(plugin).build()));
            }
        }

        private boolean transferEventsToItem(ClickInventoryEvent event, SlotPos pos, VirtualChestItem item)
        {
            logger.debug("Player {} tries to click the chest GUI at {}", player.getName(), slotPosToKey(pos));
            if (event instanceof ClickInventoryEvent.Primary)
            {
                executorService.submit(() -> item.doPrimaryAction(player));
            }
            if (event instanceof ClickInventoryEvent.Secondary)
            {
                executorService.submit(() -> item.doSecondaryAction(player));
            }
            return item.shouldCloseInventory();
        }
    }
}
