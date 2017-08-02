package com.github.ustc_zzzz.virtualchest.inventory.util;

import com.github.ustc_zzzz.virtualchest.inventory.VirtualChestInventory;
import org.spongepowered.api.data.DataContainer;
import org.spongepowered.api.item.ItemType;
import org.spongepowered.api.item.inventory.ItemStackSnapshot;

/**
 * @author ustc_zzzz
 */
public class VirtualChestItemTemplate
{
    private final DataContainer container;

    public VirtualChestItemTemplate(DataContainer dataContainer)
    {
        container = dataContainer;
    }

    public boolean matchItem(ItemStackSnapshot item)
    {
        return matchItemType(item) && matchItemDamage(item);
    }

    private boolean matchItemType(ItemStackSnapshot item)
    {
        ItemType t = item.getType();
        return container.getCatalogType(VirtualChestInventory.ITEM_TYPE, ItemType.class).filter(t::equals).isPresent();
    }

    private boolean matchItemDamage(ItemStackSnapshot item)
    {
        Integer d = item.toContainer().getInt(VirtualChestInventory.UNSAFE_DAMAGE).get();
        return container.getInt(VirtualChestInventory.UNSAFE_DAMAGE).orElse(d).equals(d);
    }
}
