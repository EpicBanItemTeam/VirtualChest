package com.github.ustc_zzzz.virtualchest.inventory.util;

import org.spongepowered.api.data.DataContainer;
import org.spongepowered.api.data.DataQuery;
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
    public static final DataQuery ITEM_TYPE = DataQuery.of("ItemType");
    public static final DataQuery UNSAFE_DAMAGE = DataQuery.of("UnsafeDamage");

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
        return container.getCatalogType(ITEM_TYPE, ItemType.class).filter(t::equals).isPresent();
    }

    private boolean matchItemDamage(ItemStackSnapshot item)
    {
        Integer d = item.toContainer().getInt(UNSAFE_DAMAGE).orElse(0);
        return container.getInt(UNSAFE_DAMAGE).orElse(d).equals(d);
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
