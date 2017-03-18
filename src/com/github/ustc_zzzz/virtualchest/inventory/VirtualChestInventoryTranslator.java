package com.github.ustc_zzzz.virtualchest.inventory;

import com.github.ustc_zzzz.virtualchest.VirtualChestPlugin;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.reflect.TypeToken;
import org.spongepowered.api.data.*;
import org.spongepowered.api.data.persistence.DataTranslator;
import org.spongepowered.api.data.persistence.InvalidDataException;
import org.spongepowered.api.item.inventory.property.SlotPos;
import org.spongepowered.api.text.Text;
import org.spongepowered.api.text.serializer.TextSerializers;
import org.spongepowered.api.util.GuavaCollectors;
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

    private List<DataView> getViewListOrSingletonList(DataQuery key, DataView view)
    {
        Optional<List<?>> listOptional = view.getList(key);
        if (listOptional.isPresent())
        {
            // ignore invalid data
            return listOptional.get().stream()
                    .map(d -> new MemoryDataContainer(DataView.SafetyMode.NO_DATA_CLONED).set(key, d).getView(key))
                    .filter(Optional::isPresent).map(Optional::get).collect(GuavaCollectors.toImmutableList());
        }
        else
        {
            return view.getView(key).map(Collections::singletonList).orElseGet(ImmutableList::of);
        }
    }

    @Override
    public TypeToken<VirtualChestInventory> getToken()
    {
        return TypeToken.of(VirtualChestInventory.class);
    }

    @Override
    public VirtualChestInventory translate(DataView view) throws InvalidDataException
    {
        Optional<Text> titleOptional = Optional.empty();
        Optional<Integer> heightOptional = Optional.empty();
        Optional<Integer> updateIntervalTickOptional = Optional.empty();
        Optional<DataContainer> triggerItemOptional = Optional.empty();
        ImmutableMultimap.Builder<SlotPos, VirtualChestItem> itemsBuilder = ImmutableMultimap.builder();
        for (DataQuery key : view.getKeys(false))
        {
            if (VirtualChestInventory.TITLE.equals(key))
            {
                titleOptional = view.getString(VirtualChestInventory.TITLE).map(TextSerializers.FORMATTING_CODE::deserialize);
                continue;
            }
            if (VirtualChestInventory.HEIGHT.equals(key))
            {
                heightOptional = view.getInt(VirtualChestInventory.HEIGHT);
                continue;
            }
            if (VirtualChestInventory.UPDATE_INTERVAL_TICK.equals(key))
            {
                updateIntervalTickOptional = view.getInt(VirtualChestInventory.UPDATE_INTERVAL_TICK);
                continue;
            }
            if (VirtualChestInventory.TRIGGER_ITEM.equals(key))
            {
                triggerItemOptional = view.getView(VirtualChestInventory.TRIGGER_ITEM).map(DataView::copy);
                continue;
            }
            SlotPos slotPos;
            try
            {
                slotPos = VirtualChestInventory.keyToSlotPos(key.toString());
            }
            catch (InvalidDataException ignored)
            {
                // invalid key, ignored
                continue;
            }
            List<DataView> dataViews = getViewListOrSingletonList(key, view);
            for (DataView dataView : dataViews)
            {
                VirtualChestItem item = VirtualChestItem.deserialize(plugin, dataView);
                itemsBuilder.put(slotPos, item);
            }
        }
        Text title = titleOptional.orElseThrow(() -> new InvalidDataException("Expected title"));
        Integer height = heightOptional.orElseThrow(() -> new InvalidDataException("Expected height"));
        ImmutableMultimap<SlotPos, VirtualChestItem> items = itemsBuilder.build();
        VirtualChestTriggerItem triggerItem = new VirtualChestTriggerItem(triggerItemOptional.orElseGet(MemoryDataContainer::new));
        int updateIntervalTick = updateIntervalTickOptional.orElse(0);
        return new VirtualChestInventory(plugin, title, height, items, triggerItem, updateIntervalTick);
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
