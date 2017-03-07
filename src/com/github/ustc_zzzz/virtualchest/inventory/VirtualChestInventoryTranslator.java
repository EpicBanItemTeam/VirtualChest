package com.github.ustc_zzzz.virtualchest.inventory;

import com.github.ustc_zzzz.virtualchest.VirtualChestPlugin;
import com.google.common.collect.ImmutableMap;
import com.google.common.reflect.TypeToken;
import org.spongepowered.api.data.*;
import org.spongepowered.api.data.persistence.DataTranslator;
import org.spongepowered.api.data.persistence.InvalidDataException;
import org.spongepowered.api.item.inventory.property.SlotPos;
import org.spongepowered.api.text.Text;
import org.spongepowered.api.text.serializer.TextSerializers;

import java.util.Map;
import java.util.Optional;

/**
 * @author ustc_zzzz
 */
public class VirtualChestInventoryTranslator implements DataTranslator<VirtualChestInventory>
{
    private static final int CONTENT_VERSION = 0;
    private final VirtualChestPlugin plugin;

    public VirtualChestInventoryTranslator(VirtualChestPlugin plugin)
    {
        this.plugin = plugin;
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
        Optional<DataContainer> triggerItem = Optional.empty();
        ImmutableMap.Builder<SlotPos, VirtualChestItem> itemsBuilder = ImmutableMap.builder();
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
            if (VirtualChestInventory.TRIGGER_ITEM.equals(key))
            {
                triggerItem = view.getView(VirtualChestInventory.TRIGGER_ITEM).map(DataView::copy);
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
            Optional<DataView> dataViewOptional = view.getView(key);
            if (dataViewOptional.isPresent())
            {
                VirtualChestItem item = VirtualChestItem.deserialize(plugin, dataViewOptional.get());
                itemsBuilder.put(slotPos, item);
            }
        }
        Text title = titleOptional.orElseThrow(() -> new InvalidDataException("Expected title"));
        Integer height = heightOptional.orElseThrow(() -> new InvalidDataException("Expected height"));
        ImmutableMap<SlotPos, VirtualChestItem> items = itemsBuilder.build();
        return new VirtualChestInventory(plugin, title, height, items, triggerItem);
    }

    @Override
    public DataContainer translate(VirtualChestInventory obj) throws InvalidDataException
    {
        MemoryDataContainer container = new MemoryDataContainer();
        container.set(Queries.CONTENT_VERSION, CONTENT_VERSION);
        container.set(VirtualChestInventory.TITLE, obj.title);
        container.set(VirtualChestInventory.HEIGHT, obj.height);
        obj.openItemPredicate.ifPresent(d -> container.set(VirtualChestInventory.TRIGGER_ITEM, d));
        for (Map.Entry<SlotPos, VirtualChestItem> entry : obj.items.entrySet())
        {
            DataContainer data = VirtualChestItem.serialize(plugin, entry.getValue());
            container.set(DataQuery.of(VirtualChestInventory.slotPosToKey(entry.getKey())), data);
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
