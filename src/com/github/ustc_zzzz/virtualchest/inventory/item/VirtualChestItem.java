package com.github.ustc_zzzz.virtualchest.inventory.item;

import com.github.ustc_zzzz.virtualchest.VirtualChestPlugin;
import com.github.ustc_zzzz.virtualchest.action.VirtualChestActionDispatcher;
import com.github.ustc_zzzz.virtualchest.inventory.VirtualChestInventory;
import com.google.common.base.Objects;
import com.google.common.collect.ImmutableList;
import org.spongepowered.api.data.DataContainer;
import org.spongepowered.api.data.DataQuery;
import org.spongepowered.api.data.DataView;
import org.spongepowered.api.data.MemoryDataContainer;
import org.spongepowered.api.data.persistence.InvalidDataException;
import org.spongepowered.api.entity.living.player.Player;
import org.spongepowered.api.item.inventory.Inventory;
import org.spongepowered.api.item.inventory.property.SlotIndex;
import org.spongepowered.api.util.Tuple;

import javax.script.CompiledScript;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * @author ustc_zzzz
 */
public class VirtualChestItem
{
    public static final DataQuery ITEM = DataQuery.of("Item");
    public static final DataQuery REQUIREMENTS = DataQuery.of("Requirements");
    public static final DataQuery PRIMARY_ACTION = DataQuery.of("PrimaryAction");
    public static final DataQuery SECONDARY_ACTION = DataQuery.of("SecondaryAction");
    public static final DataQuery IGNORED_PERMISSIONS = DataQuery.of("IgnoredPermissions");

    private final VirtualChestPlugin plugin;
    private final VirtualChestItemStackSerializer serializer;

    private final DataView serializedStack;
    private final List<String> ignoredPermissions;
    private final Tuple<String, CompiledScript> requirements;
    private final VirtualChestActionDispatcher primaryAction;
    private final VirtualChestActionDispatcher secondaryAction;

    public static DataContainer serialize(VirtualChestPlugin plugin, VirtualChestItem item) throws InvalidDataException
    {
        DataContainer container = new MemoryDataContainer();
        container.set(ITEM, item.serializedStack);
        item.primaryAction.getObjectForSerialization().ifPresent(o -> container.set(PRIMARY_ACTION, o));
        item.secondaryAction.getObjectForSerialization().ifPresent(o -> container.set(SECONDARY_ACTION, o));
        if (!item.requirements.getFirst().isEmpty())
        {
            container.set(REQUIREMENTS, item.requirements.getFirst());
        }
        if (!item.ignoredPermissions.isEmpty())
        {
            container.set(IGNORED_PERMISSIONS, item.ignoredPermissions);
        }
        return container;
    }

    public static VirtualChestItem deserialize(VirtualChestPlugin plugin, DataView data) throws InvalidDataException
    {
        return new VirtualChestItem(plugin,
                data.getView(ITEM).orElseThrow(() -> new InvalidDataException("Expected Item")),
                plugin.getScriptManager().prepare(data.getString(REQUIREMENTS).orElse("")),
                new VirtualChestActionDispatcher(getViewListOrSingletonList(SECONDARY_ACTION, data)),
                new VirtualChestActionDispatcher(getViewListOrSingletonList(PRIMARY_ACTION, data)),
                data.getStringList(IGNORED_PERMISSIONS).orElse(ImmutableList.of())
        );
    }

    private VirtualChestItem(
            VirtualChestPlugin plugin,
            DataView serializedStack,
            Tuple<String, CompiledScript> requirements,
            VirtualChestActionDispatcher secondaryAction,
            VirtualChestActionDispatcher primaryAction,
            List<String> ignoredPermissions)
    {
        this.plugin = plugin;
        this.serializer = new VirtualChestItemStackSerializer(plugin);

        this.serializedStack = serializedStack;
        this.requirements = requirements;
        this.primaryAction = primaryAction;
        this.secondaryAction = secondaryAction;
        this.ignoredPermissions = ignoredPermissions;
    }

    public static List<DataView> getViewListOrSingletonList(DataQuery key, DataView view)
    {
        Optional<List<?>> listOptional = view.getList(key);
        if (!listOptional.isPresent())
        {
            return view.getView(key).map(Collections::singletonList).orElseGet(Collections::emptyList);
        }
        ImmutableList.Builder<DataView> builder = ImmutableList.builder();
        for (Object data : listOptional.get())
        {
            DataContainer container = new MemoryDataContainer(DataView.SafetyMode.NO_DATA_CLONED);
            container.set(key, data).getView(key).ifPresent(builder::add);
        }
        return builder.build();
    }

    public boolean setInventory(Player player, Inventory inventory, SlotIndex pos)
    {
        if (!this.plugin.getScriptManager().execute(player, this.requirements))
        {
            return false;
        }
        try
        {
            inventory.set(this.serializer.apply(player, this.serializedStack));
            return true;
        }
        catch (InvalidDataException e)
        {
            String posString = VirtualChestInventory.slotIndexToKey(pos);
            throw new InvalidDataException("Find error when generating item at " + posString, e);
        }
    }

    public boolean doPrimaryAction(Player player)
    {
        this.plugin.getPermissionManager().setIgnoredPermissions(player, this.ignoredPermissions);
        return this.primaryAction.runCommand(this.plugin, player);
    }

    public boolean doSecondaryAction(Player player)
    {
        this.plugin.getPermissionManager().setIgnoredPermissions(player, this.ignoredPermissions);
        return this.secondaryAction.runCommand(this.plugin, player);
    }

//    public String toString()
//    {
//        return Objects.toStringHelper(this)
//                .add("Item", this.serializedStack)
//                .add("Requirements", this.requirements)
//                .add("IgnoredPermissions", this.ignoredPermissions)
//                .add("PrimaryAction", this.primaryAction)
//                .add("SecondaryAction", this.secondaryAction)
//                .toString();
//    }
}
