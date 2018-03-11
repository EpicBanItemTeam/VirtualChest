package com.github.ustc_zzzz.virtualchest.inventory.util;

import org.spongepowered.api.data.DataQuery;
import org.spongepowered.api.data.DataView;
import org.spongepowered.api.item.inventory.ItemStackSnapshot;
import org.spongepowered.api.util.annotation.NonnullByDefault;

/**
 * @author ustc_zzzz
 */
@NonnullByDefault
public class VirtualChestItemTemplateWithNBTAndCount extends VirtualChestItemTemplateWithNBT
{
    private static final DataQuery COUNT = DataQuery.of("Count");

    private final int count;

    public VirtualChestItemTemplateWithNBTAndCount()
    {
        super();
        this.count = 0;
    }

    public VirtualChestItemTemplateWithNBTAndCount(DataView view)
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
