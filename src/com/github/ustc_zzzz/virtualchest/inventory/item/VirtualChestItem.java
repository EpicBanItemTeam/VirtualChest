package com.github.ustc_zzzz.virtualchest.inventory.item;

import com.github.ustc_zzzz.virtualchest.VirtualChestPlugin;
import com.github.ustc_zzzz.virtualchest.action.VirtualChestActionDispatcher;
import com.github.ustc_zzzz.virtualchest.inventory.VirtualChestInventory;
import com.github.ustc_zzzz.virtualchest.timings.VirtualChestTimings;
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
    public static final DataQuery PRIMARY_SHIFT_ACTION = DataQuery.of("PrimaryShiftAction");
    public static final DataQuery SECONDARY_SHIFT_ACTION = DataQuery.of("SecondaryShiftAction");
    public static final DataQuery IGNORED_PERMISSIONS = DataQuery.of("IgnoredPermissions");

    private final VirtualChestPlugin plugin;
    private final VirtualChestItemStackSerializer serializer;

    private final DataView serializedStack;
    private final List<String> ignoredPermissions;
    private final Tuple<String, CompiledScript> requirements;
    private final VirtualChestActionDispatcher primaryAction;
    private final VirtualChestActionDispatcher secondaryAction;
    private final VirtualChestActionDispatcher primaryShiftAction;
    private final VirtualChestActionDispatcher secondaryShiftAction;

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
        DataView serializedStack = data.getView(ITEM).orElseThrow(() -> new InvalidDataException("Expected Item"));

        String requirementString = data.getString(REQUIREMENTS).orElse("");
        Tuple<String, CompiledScript> requirements = plugin.getScriptManager().prepare(requirementString);

        List<DataView> primaryList = getViewListOrSingletonList(PRIMARY_ACTION, data);
        VirtualChestActionDispatcher primaryAction = new VirtualChestActionDispatcher(primaryList);

        List<DataView> secondaryList = getViewListOrSingletonList(SECONDARY_ACTION, data);
        VirtualChestActionDispatcher secondaryAction = new VirtualChestActionDispatcher(secondaryList);

        List<DataView> primaryShiftList = getViewListOrSingletonList(PRIMARY_SHIFT_ACTION, data);
        List<DataView> primaryShiftListFinal = primaryShiftList.isEmpty() ? primaryList : primaryShiftList;
        VirtualChestActionDispatcher primaryShiftAction = new VirtualChestActionDispatcher(primaryShiftListFinal);

        List<DataView> secondaryShiftList = getViewListOrSingletonList(SECONDARY_SHIFT_ACTION, data);
        List<DataView> secondaryShiftListFinal = secondaryShiftList.isEmpty() ? secondaryList : secondaryShiftList;
        VirtualChestActionDispatcher secondaryShiftAction = new VirtualChestActionDispatcher(secondaryShiftListFinal);

        List<String> ignoredPermissions = data.getStringList(IGNORED_PERMISSIONS).orElse(ImmutableList.of());

        return new VirtualChestItem(plugin, serializedStack, requirements,
                primaryAction, secondaryAction, primaryShiftAction, secondaryShiftAction, ignoredPermissions);
    }

    private VirtualChestItem(
            VirtualChestPlugin plugin,
            DataView serializedStack,
            Tuple<String, CompiledScript> requirements,
            VirtualChestActionDispatcher primaryAction,
            VirtualChestActionDispatcher secondaryAction,
            VirtualChestActionDispatcher primaryShiftAction,
            VirtualChestActionDispatcher secondaryShiftAction,
            List<String> ignoredPermissions)
    {
        this.plugin = plugin;
        this.serializer = new VirtualChestItemStackSerializer(plugin);

        this.serializedStack = serializedStack;
        this.requirements = requirements;
        this.primaryAction = primaryAction;
        this.secondaryAction = secondaryAction;
        this.primaryShiftAction = primaryShiftAction;
        this.secondaryShiftAction = secondaryShiftAction;
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
        VirtualChestTimings.CHECK_REQUIREMENTS.startTimingIfSync();
        boolean matchRequirements = this.plugin.getScriptManager().execute(player, this.requirements);
        VirtualChestTimings.CHECK_REQUIREMENTS.stopTimingIfSync();
        if (matchRequirements)
        {
            try
            {
                VirtualChestTimings.SET_ITEM_IN_INVENTORIES.startTimingIfSync();
                inventory.set(this.serializer.apply(player, this.serializedStack));
                return true;
            }
            catch (InvalidDataException e)
            {
                String posString = VirtualChestInventory.slotIndexToKey(pos);
                throw new InvalidDataException("Find error when generating item at " + posString, e);
            }
            finally
            {
                VirtualChestTimings.SET_ITEM_IN_INVENTORIES.stopTimingIfSync();
            }
        }
        return false;
    }

    public Optional<VirtualChestActionDispatcher> getAction(boolean isPrimary, boolean isSecondary, boolean isShift)
    {
        if (isPrimary)
        {
            return isShift ? Optional.of(this.primaryShiftAction) : Optional.of(this.primaryAction);
        }
        if (isSecondary)
        {
            return isShift ? Optional.of(this.secondaryShiftAction) : Optional.of(this.secondaryAction);
        }
        return Optional.empty();
    }

    public List<String> getIgnoredPermissions()
    {
        return this.ignoredPermissions;
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
