package com.github.ustc_zzzz.virtualchest.inventory.item;

import com.github.ustc_zzzz.virtualchest.VirtualChestPlugin;
import com.github.ustc_zzzz.virtualchest.inventory.VirtualChestInventory;
import com.github.ustc_zzzz.virtualchest.inventory.util.VirtualChestItemTemplateWithCount;
import com.github.ustc_zzzz.virtualchest.unsafe.SpongeUnimplemented;
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
import java.util.List;
import java.util.Optional;

/**
 * @author ustc_zzzz
 */
public class VirtualChestItem
{
    public static final DataQuery ITEM = DataQuery.of("Item");
    public static final DataQuery KEEP_OPEN = DataQuery.of("KeepOpen");
    public static final DataQuery PRIMARY_ACTION = DataQuery.of("PrimaryAction");
    public static final DataQuery SECONDARY_ACTION = DataQuery.of("SecondaryAction");
    public static final DataQuery REQUIRED_BALANCES = DataQuery.of("RequiredBalances");
    public static final DataQuery IGNORED_PERMISSIONS = DataQuery.of("IgnoredPermissions");
    public static final DataQuery REQUIRED_PERMISSIONS = DataQuery.of("RequiredPermissions");
    public static final DataQuery REJECTED_PERMISSIONS = DataQuery.of("RejectedPermissions");
    public static final DataQuery PRIMARY_REQUIRED_ITEM = DataQuery.of("PrimaryRequiredItem");
    public static final DataQuery SECONDARY_REQUIRED_ITEM = DataQuery.of("SecondaryRequiredItem");

    private final VirtualChestPlugin plugin;
    private final VirtualChestItemStackSerializer serializer;

    private final DataView serializedStack;
    private final String primaryAction;
    private final String secondaryAction;
    private final boolean keepInventoryOpen;
    private final Multimap<String, BigDecimal> requiredBalances;
    private final List<String> ignoredPermissions;
    private final List<String> requiredPermissions;
    private final List<String> rejectedPermissions;
    private final VirtualChestItemTemplateWithCount primaryRequiredItem;
    private final VirtualChestItemTemplateWithCount secondaryRequiredItem;

    public static DataContainer serialize(VirtualChestPlugin plugin, VirtualChestItem item) throws InvalidDataException
    {
        DataContainer container = new MemoryDataContainer();
        container.set(ITEM, item.serializedStack);
        container.set(KEEP_OPEN, item.keepInventoryOpen);
        if (!item.primaryAction.isEmpty())
        {
            container.set(PRIMARY_ACTION, item.primaryAction);
        }
        if (!item.secondaryAction.isEmpty())
        {
            container.set(SECONDARY_ACTION, item.secondaryAction);
        }
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
        container.set(PRIMARY_REQUIRED_ITEM, item.primaryRequiredItem);
        container.set(SECONDARY_REQUIRED_ITEM, item.secondaryRequiredItem);
        return container;
    }

    public static VirtualChestItem deserialize(VirtualChestPlugin plugin, DataView data) throws InvalidDataException
    {
        return new VirtualChestItem(plugin,
                data.getView(ITEM).orElseThrow(() -> new InvalidDataException("Expected Item")),
                data.getString(PRIMARY_ACTION).orElse(""),
                data.getString(SECONDARY_ACTION).orElse(""),
                data.getBoolean(KEEP_OPEN).orElse(Boolean.FALSE),
                deserializeRequiredBalances(data.getStringList(REQUIRED_BALANCES).orElse(ImmutableList.of())),
                data.getStringList(IGNORED_PERMISSIONS).orElse(ImmutableList.of()),
                data.getStringList(REQUIRED_PERMISSIONS).orElse(ImmutableList.of()),
                data.getStringList(REJECTED_PERMISSIONS).orElse(ImmutableList.of()),
                deserializeRequiredItem(data.getView(PRIMARY_REQUIRED_ITEM)),
                deserializeRequiredItem(data.getView(SECONDARY_REQUIRED_ITEM)));
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

    private static VirtualChestItemTemplateWithCount deserializeRequiredItem(Optional<DataView> view)
    {
        return view.map(VirtualChestItemTemplateWithCount::new).orElseGet(VirtualChestItemTemplateWithCount::new);
    }

    private VirtualChestItem(
            VirtualChestPlugin plugin,
            DataView stack,
            String primaryAction,
            String secondaryAction,
            boolean keepInventoryOpen,
            Multimap<String, BigDecimal> requiredBalances,
            List<String> ignoredPermissions,
            List<String> requiredPermissions,
            List<String> rejectedPermissions,
            VirtualChestItemTemplateWithCount primaryRequiredItem,
            VirtualChestItemTemplateWithCount secondaryRequiredItem)
    {
        this.plugin = plugin;
        this.serializer = new VirtualChestItemStackSerializer(plugin);

        this.serializedStack = stack;
        this.primaryAction = primaryAction;
        this.secondaryAction = secondaryAction;
        this.keepInventoryOpen = keepInventoryOpen;
        this.requiredBalances = requiredBalances;
        this.ignoredPermissions = ignoredPermissions;
        this.requiredPermissions = requiredPermissions;
        this.rejectedPermissions = rejectedPermissions;
        this.primaryRequiredItem = primaryRequiredItem;
        this.secondaryRequiredItem = secondaryRequiredItem;
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

    public boolean shouldCloseInventory()
    {
        return !this.keepInventoryOpen;
    }

    public void doPrimaryAction(Player player)
    {
        if (!this.primaryAction.isEmpty())
        {
            if (this.primaryRequiredItem.matchItem(SpongeUnimplemented.getItemHeldByMouse(player)))
            {
                this.plugin.getVirtualChestActions().runCommand(player, this.primaryAction, this.ignoredPermissions);
            }
        }
    }

    public void doSecondaryAction(Player player)
    {
        if (!this.secondaryAction.isEmpty())
        {
            if (this.secondaryRequiredItem.matchItem(SpongeUnimplemented.getItemHeldByMouse(player)))
            {
                this.plugin.getVirtualChestActions().runCommand(player, this.secondaryAction, this.ignoredPermissions);
            }
        }
    }

    public String toString()
    {
        return Objects.toStringHelper(this)
                .add("Item", this.serializedStack)
                .add("PrimaryAction", this.primaryAction)
                .add("SecondaryAction", this.secondaryAction)
                .add("KeepOpen", this.keepInventoryOpen)
                .add("RequiredPermissions", this.requiredPermissions)
                .add("RejectedPermissions", this.rejectedPermissions)
                .toString();
    }
}
