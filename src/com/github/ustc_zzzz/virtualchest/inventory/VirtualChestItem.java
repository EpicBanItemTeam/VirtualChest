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
import org.spongepowered.api.text.serializer.TextSerializers;

import java.util.Optional;
import java.util.stream.Collectors;

/**
 * @author ustc_zzzz
 */
public class VirtualChestItem
{
    public static final DataQuery ITEM = DataQuery.of("Item");
    public static final DataQuery KEEP_OPEN = DataQuery.of("KeepOpen");
    public static final DataQuery PRIMARY_ACTION = DataQuery.of("PrimaryAction");
    public static final DataQuery MIDDLE_ACTION = DataQuery.of("MiddleAction");
    public static final DataQuery SECONDARY_ACTION = DataQuery.of("SecondaryAction");

    public static final DataQuery LORE = Keys.ITEM_LORE.getQuery();
    public static final DataQuery DISPLAY_NAME = Keys.DISPLAY_NAME.getQuery();
    public static final DataQuery ENCHANTMENTS = Keys.ITEM_ENCHANTMENTS.getQuery();

    private static final DataManager DATA_MANAGER = Sponge.getDataManager();

    private final VirtualChestPlugin plugin;

    private final ItemStack stack;
    private final String primaryAction;
    private final String middleAction;
    private final String secondaryAction;
    private final boolean keepInventoryOpen;

    public static DataContainer serialize(VirtualChestPlugin plugin, VirtualChestItem item) throws InvalidDataException
    {
        DataContainer data = new MemoryDataContainer().set(ITEM, item.stack).set(KEEP_OPEN, item.keepInventoryOpen);
        if (!item.primaryAction.isEmpty())
        {
            data.set(PRIMARY_ACTION, item.primaryAction);
        }
        if (!item.middleAction.isEmpty())
        {
            data.set(MIDDLE_ACTION, item.middleAction);
        }
        if (!item.secondaryAction.isEmpty())
        {
            data.set(SECONDARY_ACTION, item.secondaryAction);
        }
        return data;
    }

    public static VirtualChestItem deserialize(VirtualChestPlugin plugin, DataView data) throws InvalidDataException
    {
        Optional<ItemStack> optional = data.getView(ITEM).flatMap(VirtualChestItem::deserializeItemStack);
        return new VirtualChestItem(plugin, optional.orElseThrow(() -> new InvalidDataException("Expected Item")),
                data.getBoolean(KEEP_OPEN).orElse(Boolean.FALSE), data.getString(PRIMARY_ACTION).orElse(""),
                data.getString(MIDDLE_ACTION).orElse(""), data.getString(SECONDARY_ACTION).orElse(""));
    }

    private static Optional<ItemStack> deserializeItemStack(DataView v)
    {
        Optional<ItemStack> stackOptional = DATA_MANAGER.deserialize(ItemStack.class, v);
        if (stackOptional.isPresent())
        {
            ItemStack stack = stackOptional.get();
            v.getStringList(LORE).ifPresent(loreList -> stack.offer(Keys.ITEM_LORE, loreList
                    .stream().map(TextSerializers.FORMATTING_CODE::deserialize).collect(Collectors.toList())));
            v.getString(DISPLAY_NAME).ifPresent(displayName -> stack.offer(Keys.DISPLAY_NAME,
                    TextSerializers.FORMATTING_CODE.deserialize(displayName)));
            v.getStringList(ENCHANTMENTS).ifPresent(enchantments -> stack.offer(Keys.ITEM_ENCHANTMENTS, enchantments
                    .stream().map(VirtualChestItem::deserializeItemEnchantment).collect(Collectors.toList())));
        }
        return stackOptional;
    }

    private static ItemEnchantment deserializeItemEnchantment(String e)
    {
        int colonFirstIndex = e.indexOf(':'), colonLastIndex = e.lastIndexOf(':');
        int level = colonFirstIndex == colonLastIndex ? 0 : Integer.valueOf(e.substring(colonLastIndex + 1));
        String enchantmentId = colonFirstIndex == colonLastIndex ? e : e.substring(0, colonLastIndex);
        Optional<Enchantment> optional = Sponge.getRegistry().getType(Enchantment.class, enchantmentId);
        Enchantment enchantment = optional.orElseThrow(() -> new InvalidDataException("Invalid enchantment"));
        return new ItemEnchantment(enchantment, level);
    }

    private VirtualChestItem(VirtualChestPlugin plugin, ItemStack stack, boolean keeyOpen,
                             String primaryAction, String middleAction, String secondaryAction)
    {
        this.plugin = plugin;

        this.stack = stack;
        this.keepInventoryOpen = keeyOpen;
        this.primaryAction = primaryAction;
        this.middleAction = middleAction;
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
        if (event instanceof ClickInventoryEvent.Middle)
        {
            actionCommand = middleAction;
        }
        if (event instanceof ClickInventoryEvent.Secondary)
        {
            actionCommand = secondaryAction;
        }
        return actionCommand;
    }

    public void setInventory(Inventory inventory)
    {
        inventory.set(this.stack);
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
                .add("Item", this.stack)
                .add("KeepOpen", this.keepInventoryOpen)
                .add("PrimaryAction", this.primaryAction)
                .add("MiddleAction", this.middleAction)
                .add("SecondaryAction", this.secondaryAction)
                .toString();
    }

    public enum Action
    {
        CLOSE_INVENTORY, KEEP_INVENTORY_OPEN
    }
}
