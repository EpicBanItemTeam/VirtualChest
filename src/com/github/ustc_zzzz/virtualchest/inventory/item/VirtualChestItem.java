package com.github.ustc_zzzz.virtualchest.inventory.item;

import co.aikar.timings.Timing;
import com.github.ustc_zzzz.virtualchest.VirtualChestPlugin;
import com.github.ustc_zzzz.virtualchest.action.VirtualChestActionDispatcher;
import com.github.ustc_zzzz.virtualchest.inventory.VirtualChestInventory;
import com.github.ustc_zzzz.virtualchest.timings.VirtualChestTimings;
import com.github.ustc_zzzz.virtualchest.unsafe.SpongeUnimplemented;
import com.google.common.collect.ImmutableList;
import org.spongepowered.api.data.DataContainer;
import org.spongepowered.api.data.DataQuery;
import org.spongepowered.api.data.DataView;
import org.spongepowered.api.data.persistence.InvalidDataException;
import org.spongepowered.api.entity.living.player.Player;
import org.spongepowered.api.item.inventory.Inventory;
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
    public static final DataQuery IGNORED_PERMISSIONS = DataQuery.of("IgnoredPermissions");

    public static final DataQuery ACTION = DataQuery.of("Action");
    public static final DataQuery PRIMARY_ACTION = DataQuery.of("PrimaryAction");
    public static final DataQuery SECONDARY_ACTION = DataQuery.of("SecondaryAction");
    public static final DataQuery PRIMARY_SHIFT_ACTION = DataQuery.of("PrimaryShiftAction");
    public static final DataQuery SECONDARY_SHIFT_ACTION = DataQuery.of("SecondaryShiftAction");

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
        DataContainer container = SpongeUnimplemented.newDataContainer(DataView.SafetyMode.ALL_DATA_CLONED);

        container.set(ITEM, item.serializedStack);
        container.set(REQUIREMENTS, item.requirements.getFirst());
        container.set(IGNORED_PERMISSIONS, item.ignoredPermissions);

        item.primaryAction.getObjectForSerialization().ifPresent(o -> container.set(PRIMARY_ACTION, o));
        item.secondaryAction.getObjectForSerialization().ifPresent(o -> container.set(SECONDARY_ACTION, o));
        item.primaryShiftAction.getObjectForSerialization().ifPresent(o -> container.set(PRIMARY_SHIFT_ACTION, o));
        item.secondaryShiftAction.getObjectForSerialization().ifPresent(o -> container.set(SECONDARY_SHIFT_ACTION, o));

        return container;
    }

    public static VirtualChestItem deserialize(VirtualChestPlugin plugin, DataView data) throws InvalidDataException
    {
        DataView serializedStack = data.getView(ITEM).orElseThrow(() -> new InvalidDataException("Expected Item"));

        String requirementString = data.getString(REQUIREMENTS).orElse("");
        Tuple<String, CompiledScript> requirements = plugin.getScriptManager().prepare(requirementString);

        List<String> ignoredPermissions = data.getStringList(IGNORED_PERMISSIONS).orElse(ImmutableList.of());

        List<DataView> actionList = getViewListOrSingletonList(ACTION, data);
        List<DataView> primaryList = getViewListOrSingletonList(PRIMARY_ACTION, data);
        List<DataView> secondaryList = getViewListOrSingletonList(SECONDARY_ACTION, data);
        List<DataView> primaryShiftList = getViewListOrSingletonList(PRIMARY_SHIFT_ACTION, data);
        List<DataView> secondaryShiftList = getViewListOrSingletonList(SECONDARY_SHIFT_ACTION, data);

        List<DataView> primaryListFinal = primaryList.isEmpty() ? actionList : primaryList;
        List<DataView> secondaryListFinal = secondaryList.isEmpty() ? actionList : secondaryList;
        List<DataView> primaryShiftListFinal = primaryShiftList.isEmpty() ? primaryListFinal : primaryShiftList;
        List<DataView> secondaryShiftListFinal = secondaryShiftList.isEmpty() ? secondaryListFinal : secondaryShiftList;

        VirtualChestActionDispatcher primaryAction = new VirtualChestActionDispatcher(primaryListFinal);
        VirtualChestActionDispatcher secondaryAction = new VirtualChestActionDispatcher(secondaryListFinal);
        VirtualChestActionDispatcher primaryShiftAction = new VirtualChestActionDispatcher(primaryShiftListFinal);
        VirtualChestActionDispatcher secondaryShiftAction = new VirtualChestActionDispatcher(secondaryShiftListFinal);

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
            DataContainer container = SpongeUnimplemented.newDataContainer(DataView.SafetyMode.NO_DATA_CLONED);
            container.set(key, data).getView(key).ifPresent(builder::add);
        }
        return builder.build();
    }

    public void fillInventory(Player player, Inventory inventory, int index, String name)
    {
        try (Timing ignored = VirtualChestTimings.setItemInInventory(name, index).startTiming())
        {
            inventory.set(this.serializer.apply(player, this.serializedStack));
        }
        catch (InvalidDataException e)
        {
            String posString = VirtualChestInventory.slotIndexToKey(index);
            throw new InvalidDataException("Find error when generating item at " + posString, e);
        }
    }

    public boolean matchRequirements(Player player, int index, String name)
    {
        try (Timing ignored = VirtualChestTimings.checkRequirements(name, index).startTiming())
        {
            return this.plugin.getScriptManager().execute(player, this.requirements);
        }
    }

    public Optional<VirtualChestActionDispatcher> getAction(VirtualChestInventory.ClickStatus status)
    {
        if (status.isPrimary)
        {
            return status.isShift ? Optional.of(this.primaryShiftAction) : Optional.of(this.primaryAction);
        }
        if (status.isSecondary)
        {
            return status.isShift ? Optional.of(this.secondaryShiftAction) : Optional.of(this.secondaryAction);
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
