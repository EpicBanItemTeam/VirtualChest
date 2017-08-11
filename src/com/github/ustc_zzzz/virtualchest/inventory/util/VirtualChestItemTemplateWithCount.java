package com.github.ustc_zzzz.virtualchest.inventory.util;

import org.spongepowered.api.data.DataQuery;
import org.spongepowered.api.data.DataView;
import org.spongepowered.api.data.MemoryDataContainer;
import org.spongepowered.api.item.inventory.ItemStackSnapshot;
import org.spongepowered.api.util.annotation.NonnullByDefault;

/**
 * @author ustc_zzzz
 */
@NonnullByDefault
public class VirtualChestItemTemplateWithCount extends VirtualChestItemTemplate
{
    private static final DataQuery COUNT = DataQuery.of("Count");

    private final int count;

    public VirtualChestItemTemplateWithCount()
    {
        super(new MemoryDataContainer());
        this.count = 0;
    }

    public VirtualChestItemTemplateWithCount(DataView view)
    {
        super(view.copy());
        this.count = view.getInt(COUNT).orElse(1);
    }

    @Override
    public boolean matchItem(ItemStackSnapshot item)
    {
        return this.count == 0 || super.matchItem(item) && item.getCount() >= this.count;
    }
}
