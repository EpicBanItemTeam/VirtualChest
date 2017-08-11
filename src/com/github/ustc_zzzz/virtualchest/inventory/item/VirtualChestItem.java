package com.github.ustc_zzzz.virtualchest.inventory.item;

import com.github.ustc_zzzz.virtualchest.VirtualChestPlugin;
import com.github.ustc_zzzz.virtualchest.action.VirtualChestActionDispatcher;
import com.github.ustc_zzzz.virtualchest.inventory.VirtualChestInventory;
import com.google.common.base.Objects;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;
import org.spongepowered.api.data.DataContainer;
import org.spongepowered.api.data.DataQuery;
import org.spongepowered.api.data.DataView;
import org.spongepowered.api.data.MemoryDataContainer;
import org.spongepowered.api.data.persistence.InvalidDataException;
import org.spongepowered.api.entity.living.player.Player;
import org.spongepowered.api.item.inventory.Inventory;
import org.spongepowered.api.item.inventory.property.SlotPos;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * @author ustc_zzzz
 */
public class VirtualChestItem
{
    public static final DataQuery ITEM = DataQuery.of("Item");
    public static final DataQuery PRIMARY_ACTION = DataQuery.of("PrimaryAction");
    public static final DataQuery SECONDARY_ACTION = DataQuery.of("SecondaryAction");
    public static final DataQuery REQUIRED_BALANCES = DataQuery.of("RequiredBalances");
    public static final DataQuery IGNORED_PERMISSIONS = DataQuery.of("IgnoredPermissions");
    public static final DataQuery REQUIRED_PERMISSIONS = DataQuery.of("RequiredPermissions");
    public static final DataQuery REJECTED_PERMISSIONS = DataQuery.of("RejectedPermissions");

    private final VirtualChestPlugin plugin;
    private final VirtualChestItemStackSerializer serializer;

    private final DataView serializedStack;
    private final VirtualChestActionDispatcher primaryAction;
    private final VirtualChestActionDispatcher secondaryAction;
    private final Multimap<String, BigDecimal> requiredBalances;
    private final List<String> ignoredPermissions;
    private final List<String> requiredPermissions;
    private final List<String> rejectedPermissions;

    public static DataContainer serialize(VirtualChestPlugin plugin, VirtualChestItem item) throws InvalidDataException
    {
        DataContainer container = new MemoryDataContainer();
        container.set(ITEM, item.serializedStack);
        item.primaryAction.getObjectForSerialization().ifPresent(o -> container.set(PRIMARY_ACTION, o));
        item.secondaryAction.getObjectForSerialization().ifPresent(o -> container.set(SECONDARY_ACTION, o));
        if (!item.ignoredPermissions.isEmpty())
        {
            container.set(IGNORED_PERMISSIONS, item.ignoredPermissions);
        }
        if (!item.requiredPermissions.isEmpty())
        {
            container.set(REQUIRED_PERMISSIONS, item.requiredPermissions);
        }
        if (!item.rejectedPermissions.isEmpty())
        {
            container.set(REJECTED_PERMISSIONS, item.rejectedPermissions);
        }
        return container;
    }

    public static VirtualChestItem deserialize(VirtualChestPlugin plugin, DataView data) throws InvalidDataException
    {
        return new VirtualChestItem(plugin,
                data.getView(ITEM).orElseThrow(() -> new InvalidDataException("Expected Item")),
                new VirtualChestActionDispatcher(getViewListOrSingletonList(PRIMARY_ACTION, data)),
                new VirtualChestActionDispatcher(getViewListOrSingletonList(SECONDARY_ACTION, data)),
                deserializeRequiredBalances(data.getStringList(REQUIRED_BALANCES).orElse(ImmutableList.of())),
                data.getStringList(IGNORED_PERMISSIONS).orElse(ImmutableList.of()),
                data.getStringList(REQUIRED_PERMISSIONS).orElse(ImmutableList.of()),
                data.getStringList(REJECTED_PERMISSIONS).orElse(ImmutableList.of()));
    }

    private static Multimap<String, BigDecimal> deserializeRequiredBalances(List<String> list)
    {
        ImmutableMultimap.Builder<String, BigDecimal> builder = ImmutableMultimap.builder();
        for (String s : list)
        {
            int index = s.lastIndexOf(':');
            String currencyName = index < 0 ? "" : s.substring(0, index).toLowerCase();
            BigDecimal cost = new BigDecimal(s.substring(index + 1));
            builder.put(currencyName, cost);
        }
        return builder.build();
    }

    private VirtualChestItem(
            VirtualChestPlugin plugin,
            DataView serializedStack,
            VirtualChestActionDispatcher primaryAction,
            VirtualChestActionDispatcher secondaryAction,
            Multimap<String, BigDecimal> requiredBalances,
            List<String> ignoredPermissions,
            List<String> requiredPermissions,
            List<String> rejectedPermissions)
    {
        this.plugin = plugin;
        this.serializer = new VirtualChestItemStackSerializer(plugin);

        this.serializedStack = serializedStack;
        this.primaryAction = primaryAction;
        this.secondaryAction = secondaryAction;
        this.requiredBalances = requiredBalances;
        this.ignoredPermissions = ignoredPermissions;
        this.requiredPermissions = requiredPermissions;
        this.rejectedPermissions = rejectedPermissions;
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

    public boolean setInventory(Player player, Inventory inventory, SlotPos pos)
    {
        if (!hasAllCostPossessed(player) || !hasAllPermissionRequired(player) || !hasNoPermissionRejected(player))
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
            String posString = VirtualChestInventory.slotPosToKey(pos);
            throw new InvalidDataException("Find error when generating item at " + posString, e);
        }
    }

    private boolean hasNoPermissionRejected(Player player)
    {
        return this.rejectedPermissions.stream().noneMatch(player::hasPermission);
    }

    private boolean hasAllPermissionRequired(Player player)
    {
        return this.requiredPermissions.stream().allMatch(player::hasPermission);
    }

    private boolean hasAllCostPossessed(Player player)
    {
        return this.requiredBalances.entries().stream().allMatch(entry -> this.plugin
                .getEconomyManager().withdrawBalance(entry.getKey(), player, entry.getValue(), true));
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

    public String toString()
    {
        return Objects.toStringHelper(this)
                .add("Item", this.serializedStack)
                .add("PrimaryAction", this.primaryAction)
                .add("SecondaryAction", this.secondaryAction)
                .add("RequiredPermissions", this.requiredPermissions)
                .add("RejectedPermissions", this.rejectedPermissions)
                .toString();
    }
}
