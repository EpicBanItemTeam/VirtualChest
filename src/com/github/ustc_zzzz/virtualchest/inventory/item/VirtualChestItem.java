package com.github.ustc_zzzz.virtualchest.inventory.item;

import com.github.ustc_zzzz.virtualchest.VirtualChestPlugin;
import com.google.common.base.Objects;
import com.google.common.collect.ImmutableList;
import org.spongepowered.api.Sponge;
import org.spongepowered.api.data.*;
import org.spongepowered.api.data.persistence.InvalidDataException;
import org.spongepowered.api.entity.living.player.Player;
import org.spongepowered.api.event.item.inventory.ClickInventoryEvent;
import org.spongepowered.api.item.inventory.Inventory;

import java.util.List;

/**
 * @author ustc_zzzz
 */
public class VirtualChestItem
{
    public static final DataQuery ITEM = DataQuery.of("Item");
    public static final DataQuery KEEP_OPEN = DataQuery.of("KeepOpen");
    public static final DataQuery PRIMARY_ACTION = DataQuery.of("PrimaryAction");
    public static final DataQuery SECONDARY_ACTION = DataQuery.of("SecondaryAction");
    public static final DataQuery IGNORED_PERMISSIONS = DataQuery.of("IgnoredPermissions");
    public static final DataQuery REQUIRED_PERMISSIONS = DataQuery.of("RequiredPermissions");
    public static final DataQuery REJECTED_PERMISSIONS = DataQuery.of("RejectedPermissions");

    private final VirtualChestPlugin plugin;

    private final DataView serializedStack;
    private final String primaryAction;
    private final String secondaryAction;
    private final boolean keepInventoryOpen;
    private final List<String> ignoredPermissions;
    private final List<String> requiredPermissions;
    private final List<String> rejectedPermissions;

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
        return container;
    }

    public static VirtualChestItem deserialize(VirtualChestPlugin plugin, DataView data) throws InvalidDataException
    {
        return new VirtualChestItem(plugin,
                data.getView(ITEM).orElseThrow(() -> new InvalidDataException("Expected Item")),
                data.getString(PRIMARY_ACTION).orElse(""),
                data.getString(SECONDARY_ACTION).orElse(""),
                data.getBoolean(KEEP_OPEN).orElse(Boolean.FALSE),
                data.getStringList(IGNORED_PERMISSIONS).orElse(ImmutableList.of()),
                data.getStringList(REQUIRED_PERMISSIONS).orElse(ImmutableList.of()),
                data.getStringList(REJECTED_PERMISSIONS).orElse(ImmutableList.of()));
    }

    private VirtualChestItem(
            VirtualChestPlugin plugin,
            DataView stack,
            String primaryAction,
            String secondaryAction,
            boolean keepInventoryOpen,
            List<String> ignoredPermissions,
            List<String> requiredPermissions,
            List<String> rejectedPermissions)
    {
        this.plugin = plugin;

        this.serializedStack = stack;
        this.primaryAction = primaryAction;
        this.secondaryAction = secondaryAction;
        this.keepInventoryOpen = keepInventoryOpen;
        this.ignoredPermissions = ignoredPermissions;
        this.requiredPermissions = requiredPermissions;
        this.rejectedPermissions = rejectedPermissions;
    }

    private void doAction(Player player, String actionCommand)
    {
        this.plugin.getPermissionManager().setIgnoredPermissions(player, this.ignoredPermissions);
        this.plugin.getVirtualChestActions().runCommand(player, actionCommand);
    }

    private String getActionCommand(ClickInventoryEvent event)
    {
        String actionCommand = "";
        if (event instanceof ClickInventoryEvent.Primary)
        {
            actionCommand = primaryAction;
        }
        if (event instanceof ClickInventoryEvent.Secondary)
        {
            actionCommand = secondaryAction;
        }
        return actionCommand;
    }

    public boolean setInventory(Player player, Inventory inventory)
    {
        boolean allPermissionRequired = this.requiredPermissions.stream().allMatch(player::hasPermission);
        boolean anyPermissionRejected = this.rejectedPermissions.stream().anyMatch(player::hasPermission);
        if (!allPermissionRequired || anyPermissionRejected)
        {
            return false;
        }
        try
        {
            inventory.set(new VirtualChestItemStackSerializer(plugin, player).apply(serializedStack));
            return true;
        }
        catch (InvalidDataException e)
        {
            this.plugin.getLogger().error("Find error when generating item at " + serializedStack.getName(), e);
            return false;
        }
    }

    public Action fireEvent(Player player, ClickInventoryEvent event)
    {
        String actionCommandSequence = this.getActionCommand(event);
        if (!actionCommandSequence.isEmpty())
        {
            Sponge.getScheduler().createTaskBuilder().delayTicks(1).name("VirtualChestItemAction")
                    .execute(task -> this.doAction(player, actionCommandSequence)).submit(this.plugin);
        }
        return this.keepInventoryOpen ? Action.KEEP_INVENTORY_OPEN : Action.CLOSE_INVENTORY;
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

    public enum Action
    {
        CLOSE_INVENTORY, KEEP_INVENTORY_OPEN
    }

}
