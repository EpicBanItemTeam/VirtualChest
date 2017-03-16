package com.github.ustc_zzzz.virtualchest.inventory;

import com.github.ustc_zzzz.virtualchest.VirtualChestPlugin;
import com.google.common.base.Objects;
import org.spongepowered.api.Sponge;
import org.spongepowered.api.data.*;
import org.spongepowered.api.data.key.Keys;
import org.spongepowered.api.data.meta.ItemEnchantment;
import org.spongepowered.api.data.persistence.InvalidDataException;
import org.spongepowered.api.entity.living.player.Player;
import org.spongepowered.api.event.item.inventory.ClickInventoryEvent;
import org.spongepowered.api.item.Enchantment;
import org.spongepowered.api.item.inventory.Inventory;
import org.spongepowered.api.item.inventory.ItemStack;
import org.spongepowered.api.text.Text;

import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * @author ustc_zzzz
 */
public class VirtualChestItem
{
    public static final DataQuery ITEM = DataQuery.of("Item");
    public static final DataQuery KEEP_OPEN = DataQuery.of("KeepOpen");
    public static final DataQuery PRIMARY_ACTION = DataQuery.of("PrimaryAction");
    public static final DataQuery SECONDARY_ACTION = DataQuery.of("SecondaryAction");

    private final VirtualChestPlugin plugin;

    private final DataView serializedStack;
    private final String primaryAction;
    private final String secondaryAction;
    private final boolean keepInventoryOpen;

    public static DataContainer serialize(VirtualChestPlugin plugin, VirtualChestItem item) throws InvalidDataException
    {
        DataContainer data = new MemoryDataContainer().set(ITEM, item.serializedStack).set(KEEP_OPEN, item.keepInventoryOpen);
        if (!item.primaryAction.isEmpty())
        {
            data.set(PRIMARY_ACTION, item.primaryAction);
        }
        if (!item.secondaryAction.isEmpty())
        {
            data.set(SECONDARY_ACTION, item.secondaryAction);
        }
        return data;
    }

    public static VirtualChestItem deserialize(VirtualChestPlugin plugin, DataView data) throws InvalidDataException
    {
        return new VirtualChestItem(plugin, data.getView(ITEM).orElseThrow(() -> new InvalidDataException("Expected Item")),
                data.getBoolean(KEEP_OPEN).orElse(Boolean.FALSE), data.getString(PRIMARY_ACTION).orElse(""),
                data.getString(SECONDARY_ACTION).orElse(""));
    }

    private VirtualChestItem(VirtualChestPlugin plugin, DataView stack, boolean keeyOpen,
                             String primaryAction, String secondaryAction)
    {
        this.plugin = plugin;

        this.serializedStack = stack;
        this.keepInventoryOpen = keeyOpen;
        this.primaryAction = primaryAction;
        this.secondaryAction = secondaryAction;
    }

    private void doAction(Player player, String actionCommand)
    {
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
        Optional<ItemStack> stackOptional = new VirtualChestItemStackBuilder(plugin, player).build(serializedStack);
        if (stackOptional.isPresent())
        {
            inventory.set(stackOptional.get());
            return true;
        }
        else
        {
            return false;
        }
    }

    public Action fireEvent(Player player, ClickInventoryEvent event)
    {
        String actionCommandSequence = this.getActionCommand(event);
        if (!actionCommandSequence.isEmpty())
        {
            Sponge.getScheduler().createTaskBuilder().delayTicks(2).name("VirtualChestItemAction")
                    .execute(task -> this.doAction(player, actionCommandSequence)).submit(this.plugin);
        }
        return this.keepInventoryOpen ? Action.KEEP_INVENTORY_OPEN : Action.CLOSE_INVENTORY;
    }

    public String toString()
    {
        return Objects.toStringHelper(this)
                .add("Item", this.serializedStack)
                .add("KeepOpen", this.keepInventoryOpen)
                .add("PrimaryAction", this.primaryAction)
                .add("SecondaryAction", this.secondaryAction)
                .toString();
    }

    public enum Action
    {
        CLOSE_INVENTORY, KEEP_INVENTORY_OPEN
    }
}
