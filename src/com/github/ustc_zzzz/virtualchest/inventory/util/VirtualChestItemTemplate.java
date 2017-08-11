package com.github.ustc_zzzz.virtualchest.inventory.util;

import com.github.ustc_zzzz.virtualchest.inventory.VirtualChestInventory;
import org.spongepowered.api.data.DataContainer;
import org.spongepowered.api.data.DataSerializable;
import org.spongepowered.api.item.ItemType;
import org.spongepowered.api.item.inventory.ItemStackSnapshot;
import org.spongepowered.api.util.annotation.NonnullByDefault;

/**
 * @author ustc_zzzz
 */
@NonnullByDefault
public class VirtualChestItemTemplate implements DataSerializable
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

    @Override
    public int getContentVersion()
    {
        return 0;
    }

    @Override
    public DataContainer toContainer()
    {
        return container;
    }

    @Override
    public boolean equals(Object that)
    {
        if (this == that)
        {
            return true;
        }
        if (that == null || this.getClass() != that.getClass())
        {
            return false;
        }
        return ((VirtualChestItemTemplate) that).container.equals(this.container);
    }

    @Override
    public int hashCode()
    {
        return this.container.hashCode();
    }
}
