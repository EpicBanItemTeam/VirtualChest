package com.github.ustc_zzzz.virtualchest.inventory.util;

import org.spongepowered.api.data.DataQuery;
import org.spongepowered.api.data.DataView;
import org.spongepowered.api.item.inventory.ItemStackSnapshot;
import org.spongepowered.api.util.annotation.NonnullByDefault;

/**
 * @author ustc_zzzz
 */
@NonnullByDefault
public class VirtualChestHandheldItem extends VirtualChestItemTemplateWithNBT
{
    private static final DataQuery COUNT = DataQuery.of("Count");
    private static final DataQuery SEARCH_INVENTORY = DataQuery.of("SearchInventory");
    private static final DataQuery REPETITION_UPPER_LIMIT = DataQuery.of("RepetitionUpperLimit");

    private final int count;
    private final boolean searchInventory;
    private final int repetitionLimit;

    public VirtualChestHandheldItem()
    {
        super();
        this.count = 0;
        this.repetitionLimit = 0;
        this.searchInventory = false;
    }

    public VirtualChestHandheldItem(DataView view)
    {
        super(view.copy());
        this.count = view.getInt(COUNT).orElse(1);
        this.repetitionLimit = view.getInt(REPETITION_UPPER_LIMIT).orElse(0);
        this.searchInventory = view.getBoolean(SEARCH_INVENTORY).orElse(Boolean.FALSE);
    }

    @Override
    public boolean matchItem(ItemStackSnapshot item)
    {
        return this.count <= 0 || super.matchItem(item);
    }

    public int getCount()
    {
        return this.count;
    }

    public int getRepetition(int count)
    {
        return this.count <= 0 ? this.repetitionLimit : Math.min(this.repetitionLimit, count / this.count - 1);
    }

    public boolean isSearchingInventory()
    {
        return this.searchInventory;
    }
}
