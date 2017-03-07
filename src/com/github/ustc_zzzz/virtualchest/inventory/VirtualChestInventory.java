package com.github.ustc_zzzz.virtualchest.inventory;

import com.github.ustc_zzzz.virtualchest.VirtualChestPlugin;
import com.github.ustc_zzzz.virtualchest.unsafe.SpongeUnimplemented;
import com.google.common.collect.ImmutableMap;
import org.spongepowered.api.Sponge;
import org.spongepowered.api.data.DataContainer;
import org.spongepowered.api.data.DataQuery;
import org.spongepowered.api.data.Queries;
import org.spongepowered.api.data.persistence.InvalidDataException;
import org.spongepowered.api.entity.living.player.Player;
import org.spongepowered.api.event.cause.Cause;
import org.spongepowered.api.event.item.inventory.ClickInventoryEvent;
import org.spongepowered.api.event.item.inventory.InteractInventoryEvent;
import org.spongepowered.api.item.ItemType;
import org.spongepowered.api.item.inventory.Inventory;
import org.spongepowered.api.item.inventory.InventoryArchetypes;
import org.spongepowered.api.item.inventory.ItemStackSnapshot;
import org.spongepowered.api.item.inventory.Slot;
import org.spongepowered.api.item.inventory.property.InventoryDimension;
import org.spongepowered.api.item.inventory.property.InventoryTitle;
import org.spongepowered.api.item.inventory.property.SlotPos;
import org.spongepowered.api.item.inventory.transaction.SlotTransaction;
import org.spongepowered.api.text.Text;

import java.util.Comparator;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
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
    public static final DataQuery TRIGGER_ITEM = DataQuery.of("TriggerItem");
    public static final DataQuery PRIMARY_ACTION = DataQuery.of("EnablePrimaryAction");
    public static final DataQuery SECONDARY_ACTION = DataQuery.of("EnableSecondaryAction");

    private final VirtualChestPlugin plugin;

    final Map<SlotPos, VirtualChestItem> items;
    final int height;
    final Text title;
    final Optional<DataContainer> openItemPredicate;

    VirtualChestInventory(
            VirtualChestPlugin plugin,
            Text title,
            int height,
            Map<SlotPos, VirtualChestItem> items,
            Optional<DataContainer> openItemPredicate)
    {
        this.plugin = plugin;
        this.title = title;
        this.height = height;
        this.items = ImmutableMap.copyOf(items);
        this.openItemPredicate = openItemPredicate;
    }

    private boolean matchItemForOpening(ItemStackSnapshot item)
    {
        if (openItemPredicate.isPresent())
        {
            DataContainer container = openItemPredicate.get();
            ItemType itemType = item.getType();
            Integer itemDamage = item.toContainer().getInt(UNSAFE_DAMAGE).get();
            boolean matchItemType = container.getCatalogType(ITEM_TYPE, ItemType.class).filter(itemType::equals).isPresent();
            boolean matchDamage = container.getInt(UNSAFE_DAMAGE).orElse(itemDamage).equals(itemDamage);
            return matchItemType && matchDamage;
        }
        else
        {
            return false;
        }
    }

    public boolean matchItemForOpeningWithPrimaryAction(ItemStackSnapshot item)
    {
        return openItemPredicate.map(d -> matchItemForOpening(item) && d.getBoolean(PRIMARY_ACTION).orElse(true)).orElse(false);
    }

    public boolean matchItemForOpeningWithSecondaryAction(ItemStackSnapshot item)
    {
        return openItemPredicate.map(d -> matchItemForOpening(item) && d.getBoolean(SECONDARY_ACTION).orElse(true)).orElse(false);
    }

    public Inventory createInventory(Player player)
    {
        Inventory chestInventory = Inventory.builder().of(InventoryArchetypes.CHEST).withCarrier(player)
                .property(InventoryTitle.PROPERTY_NAME, new InventoryTitle(this.title))
                // why is it 'NAM'?
                .property(InventoryDimension.PROPERTY_NAM, new InventoryDimension(9, this.height))
                .listener(InteractInventoryEvent.class, new VirtualChestEventListener(player)).build(this.plugin);
        int i = -1, j = 0;
        for (Slot slot : chestInventory.<Slot>slots())
        {
            if (++i >= 9)
            {
                ++j;
                i = 0;
            }
            Optional.ofNullable(items.get(SlotPos.of(i, j))).ifPresent(item -> item.setInventory(slot));
        }
        return chestInventory;
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
        private final Map<Slot, VirtualChestItem> slotToItem;
        private final Player player;

        private VirtualChestEventListener(Player player)
        {
            this.slotToItem = new TreeMap<>(Comparator.comparingInt(SpongeUnimplemented::getSlotOrdinal));
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
        }

        private void fireOpenEvent(InteractInventoryEvent.Open e)
        {
            int i = -1, j = 0;
            for (Slot slot : e.getTargetInventory().first().<Slot>slots())
            {
                if (++i >= 9)
                {
                    ++j;
                    i = 0;
                }
                Optional.ofNullable(items.get(SlotPos.of(i, j))).ifPresent(item -> slotToItem.put(slot, item));
            }
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
