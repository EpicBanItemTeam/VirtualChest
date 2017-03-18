package com.github.ustc_zzzz.virtualchest.inventory;

import com.github.ustc_zzzz.virtualchest.VirtualChestPlugin;
import com.github.ustc_zzzz.virtualchest.unsafe.SpongeUnimplemented;
import com.google.common.collect.*;
import org.spongepowered.api.Sponge;
import org.spongepowered.api.data.DataQuery;
import org.spongepowered.api.data.Queries;
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
import org.spongepowered.api.item.inventory.property.SlotPos;
import org.spongepowered.api.item.inventory.transaction.SlotTransaction;
import org.spongepowered.api.scheduler.Task;
import org.spongepowered.api.text.Text;

import java.util.*;
import java.util.function.Consumer;

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
                .listener(InteractInventoryEvent.class, listener).build(this.plugin);
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
            Collection<VirtualChestItem> items = Optional.ofNullable(this.items.get(pos)).orElseGet(ImmutableList::of);
            setItemInInventory(player, items, slot).ifPresent(item -> itemBuilder.put(pos, item));
        }
        return itemBuilder.build();
    }

    private Optional<VirtualChestItem> setItemInInventory(Player player, Collection<VirtualChestItem> items, Slot slot)
    {
        Optional<VirtualChestItem> result = Optional.empty();
        for (VirtualChestItem item : items)
        {
            if (item.setInventory(player, slot))
            {
                result = Optional.of(item);
                break;
            }
        }
        return result;
    }

    public static String slotPosToKey(SlotPos slotPos) throws InvalidDataException
    {
        // SlotPos(2, 3) will be converted to "Position-3-4"
        int x = slotPos.getX(), y = slotPos.getY();
        if (y < 0 || y >= 9)
        {
            throw new InvalidDataException("Y position (" + y + ") out of bound!");
        }
        return String.format("Position-%d-%d", x + 1, y + 1);
    }

    public static SlotPos keyToSlotPos(String key) throws InvalidDataException
    {
        // "2-3" will be converted to SlotPos(1, 2)
        if (!key.startsWith("Position-"))
        {
            throw new InvalidDataException("Invalid format of key representation (" + key + ")! It should starts with 'Position-', such as 'Position-1-1'.");
        }
        int length = key.length(), dashIndex = length - 2;
        if (dashIndex < 0 || key.charAt(dashIndex) != '-')
        {
            throw new InvalidDataException("Invalid key representation (" + key + ") for slot pos!");
        }
        return SlotPos.of(Integer.valueOf(key.substring("Position-".length(), dashIndex)) - 1, Integer.valueOf(key.substring(dashIndex + 1)) - 1);
    }

    private class VirtualChestEventListener implements Consumer<InteractInventoryEvent>
    {
        private final Comparator<Slot> slotComparator;
        private final Map<Slot, VirtualChestItem> slotToItem;
        private final Player player;

        private Optional<Task> autoUpdateTask = Optional.empty();

        private VirtualChestEventListener(Player player)
        {
            this.slotComparator = Comparator.comparingInt(SpongeUnimplemented::getSlotOrdinal);
            this.slotToItem = new TreeMap<>(this.slotComparator);
            this.player = player;
        }

        @Override
        public void accept(InteractInventoryEvent event)
        {
            if (event instanceof ClickInventoryEvent)
            {
                fireClickEvent((ClickInventoryEvent) event);
            }
            else if (event instanceof InteractInventoryEvent.Open)
            {
                fireOpenEvent((InteractInventoryEvent.Open) event);
            }
            else if (event instanceof InteractInventoryEvent.Close)
            {
                fireCloseEvent((InteractInventoryEvent.Close) event);
            }
        }

        private void refreshMappingFrom(Inventory inventory, Map<SlotPos, VirtualChestItem> itemMap)
        {
            this.slotToItem.clear();
            int i = -1, j = 0;
            for (Slot slot : inventory.<Slot>slots())
            {
                if (++i >= 9)
                {
                    ++j;
                    i = 0;
                }
                Optional.ofNullable(itemMap.get(SlotPos.of(i, j))).ifPresent(item -> slotToItem.put(slot, item));
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
        }

        private void fireCloseEvent(InteractInventoryEvent.Close e)
        {
            autoUpdateTask.ifPresent(Task::cancel);
            autoUpdateTask = Optional.empty();
        }

        private void fireClickEvent(ClickInventoryEvent e)
        {
            boolean doCloseInventory = false;
            for (SlotTransaction slotTransaction : e.getTransactions())
            {
                Slot slot = slotTransaction.getSlot();
                Inventory parentInventory = slot.parent();
                if (parentInventory.equals(e.getTargetInventory().first()))
                {
                    e.setCancelled(true);
                    if (slotToItem.containsKey(slot))
                    {
                        VirtualChestItem virtualChestItem = slotToItem.get(slot);
                        if (VirtualChestItem.Action.CLOSE_INVENTORY.equals(virtualChestItem.fireEvent(this.player, e)))
                        {
                            doCloseInventory = true;
                        }
                    }
                }
            }
            if (doCloseInventory)
            {
                Sponge.getScheduler().createTaskBuilder().delayTicks(1)
                        .execute(task -> player.closeInventory(Cause.source(plugin).build())).submit(plugin);
            }
        }
    }
}
