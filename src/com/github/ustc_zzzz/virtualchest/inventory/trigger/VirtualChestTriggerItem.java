package com.github.ustc_zzzz.virtualchest.inventory.trigger;

import com.github.ustc_zzzz.virtualchest.inventory.VirtualChestInventory;
import org.spongepowered.api.data.*;
import org.spongepowered.api.item.ItemType;
import org.spongepowered.api.item.inventory.ItemStackSnapshot;

import java.util.function.Predicate;

/**
 * @author ustc_zzzz
 */
public class VirtualChestTriggerItem implements DataSerializable
{
    private static final DataQuery ENABLE_PRIMARY_ACTION = DataQuery.of("EnablePrimaryAction");
    private static final DataQuery ENABLE_SECONDARY_ACTION = DataQuery.of("EnableSecondaryAction");

    private final DataContainer container;

    private final boolean enablePrimary;
    private final boolean enableSecondary;

    private final Predicate<ItemStackSnapshot> matchItemType;
    private final Predicate<ItemStackSnapshot> matchItemDamage;

    public VirtualChestTriggerItem()
    {
        this.container = new MemoryDataContainer();

        this.enablePrimary = false;
        this.enableSecondary = false;

        this.matchItemType = snapshot -> false;
        this.matchItemDamage = snapshot -> false;
    }

    public VirtualChestTriggerItem(DataView triggerItemConfiguration)
    {
        this.container = triggerItemConfiguration.copy();

        this.enablePrimary = triggerItemConfiguration.getBoolean(ENABLE_PRIMARY_ACTION).orElse(true);
        this.enableSecondary = triggerItemConfiguration.getBoolean(ENABLE_SECONDARY_ACTION).orElse(true);

        this.matchItemType = snapshot -> matchItemType(snapshot, this.container);
        this.matchItemDamage = snapshot -> matchItemDamage(snapshot, this.container);
    }

    public boolean matchItemForOpeningWithPrimaryAction(ItemStackSnapshot item)
    {
        return this.enablePrimary && this.matchItemType.and(this.matchItemDamage).test(item);
    }

    public boolean matchItemForOpeningWithSecondaryAction(ItemStackSnapshot item)
    {
        return this.enableSecondary && this.matchItemType.and(this.matchItemDamage).test(item);
    }

    private static boolean matchItemType(ItemStackSnapshot item, DataView container)
    {
        ItemType t = item.getType();
        return container.getCatalogType(VirtualChestInventory.ITEM_TYPE, ItemType.class).filter(t::equals).isPresent();
    }

    private static boolean matchItemDamage(ItemStackSnapshot item, DataView container)
    {
        Integer d = item.toContainer().getInt(VirtualChestInventory.UNSAFE_DAMAGE).get();
        return container.getInt(VirtualChestInventory.UNSAFE_DAMAGE).orElse(d).equals(d);
    }

    @Override
    public int getContentVersion()
    {
        return 0;
    }

    @Override
    public DataContainer toContainer()
    {
        return this.container.copy();
    }
}
