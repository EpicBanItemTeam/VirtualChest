package com.github.ustc_zzzz.virtualchest.inventory;

import com.github.ustc_zzzz.virtualchest.VirtualChestPlugin;
import com.github.ustc_zzzz.virtualchest.inventory.item.VirtualChestItem;
import com.github.ustc_zzzz.virtualchest.inventory.trigger.VirtualChestTriggerItem;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;
import org.spongepowered.api.data.DataQuery;
import org.spongepowered.api.data.DataView;
import org.spongepowered.api.data.persistence.AbstractDataBuilder;
import org.spongepowered.api.data.persistence.InvalidDataException;
import org.spongepowered.api.item.inventory.property.SlotIndex;
import org.spongepowered.api.text.Text;
import org.spongepowered.api.text.serializer.TextSerializers;
import org.spongepowered.api.util.annotation.NonnullByDefault;

import java.util.Optional;

/**
 * @author ustc_zzzz
 */
@NonnullByDefault
public class VirtualChestInventoryBuilder extends AbstractDataBuilder<VirtualChestInventory>
{
    private final VirtualChestPlugin plugin;

    Text title = Text.of();
    int height = 0;
    int updateIntervalTick = 0;
    Optional<String> openActionCommand = Optional.empty();
    Optional<String> closeActionCommand = Optional.empty();
    VirtualChestTriggerItem triggerItem = new VirtualChestTriggerItem();
    Multimap<SlotIndex, VirtualChestItem> items = ArrayListMultimap.create();
    Optional<Integer> actionIntervalTick = Optional.empty();

    public VirtualChestInventoryBuilder(VirtualChestPlugin plugin)
    {
        super(VirtualChestInventory.class, 0);
        this.plugin = plugin;
    }

    public VirtualChestInventoryBuilder title(Text title)
    {
        this.title = title;
        return this;
    }

    public VirtualChestInventoryBuilder height(int height)
    {
        this.height = height;
        return this;
    }

    public VirtualChestInventoryBuilder item(SlotIndex pos, VirtualChestItem item)
    {
        this.items.put(pos, item);
        return this;
    }

    public VirtualChestInventoryBuilder updateIntervalTick(int updateIntervalTick)
    {
        this.updateIntervalTick = updateIntervalTick;
        return this;
    }

    public VirtualChestInventoryBuilder openActionCommand(String openActionCommand)
    {
        this.openActionCommand = Optional.of(openActionCommand);
        return this;
    }

    public VirtualChestInventoryBuilder closeActionCommand(String closeActionCommand)
    {
        this.closeActionCommand = Optional.of(closeActionCommand);
        return this;
    }

    public VirtualChestInventoryBuilder acceptableActionIntervalTick(int acceptableActionIntervalTick)
    {
        this.actionIntervalTick = Optional.of(acceptableActionIntervalTick);
        return this;
    }

    public VirtualChestInventoryBuilder triggerItem(VirtualChestTriggerItem triggerItem)
    {
        this.triggerItem = triggerItem;
        return this;
    }

    public VirtualChestInventory build()
    {
        if (this.title.isEmpty())
        {
            throw new InvalidDataException("Expected title");
        }
        if (this.height == 0)
        {
            throw new InvalidDataException("Expected height");
        }
        return new VirtualChestInventory(this.plugin, this);
    }

    @Override
    protected Optional<VirtualChestInventory> buildContent(DataView view) throws InvalidDataException
    {
        this.items.clear();
        for (DataQuery key : view.getKeys(false))
        {
            String keyString = key.toString();
            if (keyString.startsWith(VirtualChestInventory.KEY_PREFIX))
            {
                SlotIndex slotIndex = SlotIndex.of(VirtualChestInventory.keyToSlotIndex(keyString));
                for (DataView dataView : VirtualChestItem.getViewListOrSingletonList(key, view))
                {
                    VirtualChestItem item = VirtualChestItem.deserialize(plugin, dataView);
                    this.items.put(slotIndex, item);
                }
            }
        }

        this.title = view.getString(VirtualChestInventory.TITLE)
                .map(TextSerializers.FORMATTING_CODE::deserialize)
                .orElseThrow(() -> new InvalidDataException("Expected title"));

        this.height = view.getInt(VirtualChestInventory.HEIGHT)
                .orElseThrow(() -> new InvalidDataException("Expected height"));

        this.triggerItem = view.getView(VirtualChestInventory.TRIGGER_ITEM)
                .map(VirtualChestTriggerItem::new).orElseGet(VirtualChestTriggerItem::new);

        this.openActionCommand = view.getString(VirtualChestInventory.OPEN_ACTION_COMMAND);

        this.closeActionCommand = view.getString(VirtualChestInventory.CLOSE_ACTION_COMMAND);

        this.updateIntervalTick = view.getInt(VirtualChestInventory.UPDATE_INTERVAL_TICK).orElse(0);

        this.actionIntervalTick = view.getInt(VirtualChestInventory.ACCEPTABLE_ACTION_INTERVAL_TICK);

        return Optional.of(new VirtualChestInventory(this.plugin, this));
    }

    @Override
    public VirtualChestInventoryBuilder from(VirtualChestInventory value)
    {
        this.title = value.title;
        this.height = value.height;
        this.triggerItem = value.triggerItem;
        this.openActionCommand = value.openActionCommand;
        this.closeActionCommand = value.closeActionCommand;
        this.updateIntervalTick = value.updateIntervalTick;
        this.actionIntervalTick = value.acceptableActionIntervalTick.isPresent()
                ? Optional.of(value.acceptableActionIntervalTick.getAsInt()) : Optional.empty();
        this.items.clear();
        for (int i = 0; i < value.items.size(); i++)
        {
            this.items.putAll(SlotIndex.of(i), value.items.get(i));
        }
        return this;
    }

    @Override
    public VirtualChestInventoryBuilder reset()
    {
        this.height = 0;
        this.title = Text.of();
        this.updateIntervalTick = 0;
        this.actionIntervalTick = Optional.empty();
        this.triggerItem = new VirtualChestTriggerItem();
        this.items.clear();
        return this;
    }
}
