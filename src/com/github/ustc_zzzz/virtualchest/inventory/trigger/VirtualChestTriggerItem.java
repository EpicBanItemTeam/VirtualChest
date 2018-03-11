package com.github.ustc_zzzz.virtualchest.inventory.trigger;

import com.github.ustc_zzzz.virtualchest.inventory.util.VirtualChestItemTemplateWithNBT;
import org.spongepowered.api.data.DataQuery;
import org.spongepowered.api.data.DataView;
import org.spongepowered.api.data.MemoryDataContainer;
import org.spongepowered.api.item.inventory.ItemStackSnapshot;

/**
 * @author ustc_zzzz
 */
public class VirtualChestTriggerItem extends VirtualChestItemTemplateWithNBT
{
    private static final DataQuery ENABLE_PRIMARY_ACTION = DataQuery.of("EnablePrimaryAction");
    private static final DataQuery ENABLE_SECONDARY_ACTION = DataQuery.of("EnableSecondaryAction");

    private final boolean enablePrimary;
    private final boolean enableSecondary;

    public VirtualChestTriggerItem()
    {
        super();
        this.enablePrimary = false;
        this.enableSecondary = false;
    }

    public VirtualChestTriggerItem(DataView triggerItemConfiguration)
    {
        super(triggerItemConfiguration.copy());
        this.enablePrimary = triggerItemConfiguration.getBoolean(ENABLE_PRIMARY_ACTION).orElse(true);
        this.enableSecondary = triggerItemConfiguration.getBoolean(ENABLE_SECONDARY_ACTION).orElse(true);
    }

    public boolean matchItemForOpeningWithPrimaryAction(ItemStackSnapshot item)
    {
        return this.enablePrimary && this.matchItem(item);
    }

    public boolean matchItemForOpeningWithSecondaryAction(ItemStackSnapshot item)
    {
        return this.enableSecondary && this.matchItem(item);
    }
}
