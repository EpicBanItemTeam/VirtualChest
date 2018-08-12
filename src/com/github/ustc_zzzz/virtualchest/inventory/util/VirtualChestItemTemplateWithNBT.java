package com.github.ustc_zzzz.virtualchest.inventory.util;

import com.github.ustc_zzzz.virtualchest.unsafe.SpongeUnimplemented;
import org.spongepowered.api.data.DataQuery;
import org.spongepowered.api.data.DataView;
import org.spongepowered.api.data.MemoryDataContainer;
import org.spongepowered.api.item.inventory.ItemStackSnapshot;
import org.spongepowered.api.util.annotation.NonnullByDefault;

import java.util.Optional;

/**
 * @author ustc_zzzz
 */
@NonnullByDefault
public class VirtualChestItemTemplateWithNBT extends VirtualChestItemTemplate
{
    private static final DataQuery UNSAFE_NBT = DataQuery.of("UnsafeData");

    private final Optional<DataView> nbt;

    public VirtualChestItemTemplateWithNBT()
    {
        super(SpongeUnimplemented.newDataContainer(DataView.SafetyMode.ALL_DATA_CLONED));
        this.nbt = Optional.empty();
    }

    public VirtualChestItemTemplateWithNBT(DataView view)
    {
        super(view.copy());
        this.nbt = this.toContainer().getView(UNSAFE_NBT);
    }

    @Override
    public boolean matchItem(ItemStackSnapshot item)
    {
        return super.matchItem(item) && SpongeUnimplemented.isNBTMatched(this.nbt, item);
    }
}
