package com.github.ustc_zzzz.virtualchest.inventory;

import com.github.ustc_zzzz.virtualchest.VirtualChestPlugin;
import com.github.ustc_zzzz.virtualchest.inventory.item.VirtualChestItem;
import com.github.ustc_zzzz.virtualchest.inventory.trigger.VirtualChestTriggerItem;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;
import com.google.common.reflect.TypeToken;
import org.spongepowered.api.data.*;
import org.spongepowered.api.data.persistence.DataTranslator;
import org.spongepowered.api.data.persistence.InvalidDataException;
import org.spongepowered.api.item.inventory.property.SlotPos;
import org.spongepowered.api.text.Text;
import org.spongepowered.api.text.serializer.TextSerializers;
import org.spongepowered.api.util.annotation.NonnullByDefault;

import java.util.*;
import java.util.stream.Collectors;

/**
 * @author ustc_zzzz
 */
@NonnullByDefault
public class VirtualChestInventoryTranslator implements DataTranslator<VirtualChestInventory>
{
    private static final int CONTENT_VERSION = 0;
    private final VirtualChestPlugin plugin;

    public VirtualChestInventoryTranslator(VirtualChestPlugin plugin)
    {
        this.plugin = plugin;
    }

    private Multimap<SlotPos, VirtualChestItem> translateItems(DataView view)
    {
        ImmutableMultimap.Builder<SlotPos, VirtualChestItem> itemsBuilder = ImmutableMultimap.builder();
        for (DataQuery key : view.getKeys(false))
        {
            String keyString = key.toString();
            if (keyString.startsWith(VirtualChestInventory.KEY_PREFIX))
            {
                SlotPos slotPos = VirtualChestInventory.keyToSlotPos(keyString);
                for (DataView dataView : getViewListOrSingletonList(key, view))
                {
                    VirtualChestItem item = VirtualChestItem.deserialize(plugin, dataView);
                    itemsBuilder.put(slotPos, item);
                }
            }
        }
        return itemsBuilder.build();
    }

    private VirtualChestTriggerItem translateTriggerItem(DataView view)
    {
        return view.getView(VirtualChestInventory.TRIGGER_ITEM)
                .map(VirtualChestTriggerItem::new).orElseGet(VirtualChestTriggerItem::new);
    }

    private Integer translateUpdateIntervalTick(DataView view)
    {
        return view.getInt(VirtualChestInventory.UPDATE_INTERVAL_TICK).orElse(0);
    }

    private Integer translateHeight(DataView view)
    {
        return view.getInt(VirtualChestInventory.HEIGHT)
                .orElseThrow(() -> new InvalidDataException("Expected height"));
    }

    private Text translateTitle(DataView view)
    {
        return view.getString(VirtualChestInventory.TITLE)
                .map(TextSerializers.FORMATTING_CODE::deserialize)
                .orElseThrow(() -> new InvalidDataException("Expected title"));
    }

    private List<DataView> getViewListOrSingletonList(DataQuery key, DataView view)
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

    @Override
    public TypeToken<VirtualChestInventory> getToken()
    {
        return TypeToken.of(VirtualChestInventory.class);
    }

    @Override
    public VirtualChestInventory translate(DataView view) throws InvalidDataException
    {
        return new VirtualChestInventory(
                plugin,
                translateTitle(view),
                translateHeight(view),
                translateItems(view),
                translateTriggerItem(view),
                translateUpdateIntervalTick(view));
    }

    @Override
    public DataContainer translate(VirtualChestInventory obj) throws InvalidDataException
    {
        MemoryDataContainer container = new MemoryDataContainer();
        container.set(Queries.CONTENT_VERSION, CONTENT_VERSION);
        container.set(VirtualChestInventory.TITLE, obj.title);
        container.set(VirtualChestInventory.HEIGHT, obj.height);
        container.set(VirtualChestInventory.UPDATE_INTERVAL_TICK, obj.updateIntervalTick);
        container.set(VirtualChestInventory.TRIGGER_ITEM, obj.triggerItem);
        for (Map.Entry<SlotPos, Collection<VirtualChestItem>> entry : obj.items.asMap().entrySet())
        {
            Collection<VirtualChestItem> items = entry.getValue();
            if (items.size() > 1)
            {
                List<DataContainer> dataList = items.stream()
                        .map(item -> VirtualChestItem.serialize(plugin, item)).collect(Collectors.toList());
                container.set(DataQuery.of(VirtualChestInventory.slotPosToKey(entry.getKey())), dataList);
            }
            else if (items.size() > 0)
            {
                DataContainer data = VirtualChestItem.serialize(plugin, items.iterator().next());
                container.set(DataQuery.of(VirtualChestInventory.slotPosToKey(entry.getKey())), data);
            }
        }
        return container;
    }

    @Override
    public String getId()
    {
        return VirtualChestPlugin.PLUGIN_ID + ":inventory";
    }

    @Override
    public String getName()
    {
        return "Virtual Chest Inventory Translator";
    }
}
