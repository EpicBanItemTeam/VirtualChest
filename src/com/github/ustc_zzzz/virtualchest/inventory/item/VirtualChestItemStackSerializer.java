package com.github.ustc_zzzz.virtualchest.inventory.item;

import com.github.ustc_zzzz.virtualchest.VirtualChestPlugin;
import com.github.ustc_zzzz.virtualchest.placeholder.VirtualChestPlaceholderParser;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.reflect.TypeToken;
import ninja.leaping.configurate.ConfigurationNode;
import ninja.leaping.configurate.ConfigurationOptions;
import ninja.leaping.configurate.SimpleConfigurationNode;
import ninja.leaping.configurate.objectmapping.ObjectMappingException;
import ninja.leaping.configurate.objectmapping.serialize.TypeSerializer;
import ninja.leaping.configurate.objectmapping.serialize.TypeSerializerCollection;
import ninja.leaping.configurate.objectmapping.serialize.TypeSerializers;
import org.spongepowered.api.Sponge;
import org.spongepowered.api.data.DataManager;
import org.spongepowered.api.data.DataQuery;
import org.spongepowered.api.data.DataTransactionResult;
import org.spongepowered.api.data.DataView;
import org.spongepowered.api.data.key.Key;
import org.spongepowered.api.data.meta.ItemEnchantment;
import org.spongepowered.api.data.persistence.InvalidDataException;
import org.spongepowered.api.data.value.BaseValue;
import org.spongepowered.api.entity.living.player.Player;
import org.spongepowered.api.item.Enchantment;
import org.spongepowered.api.item.inventory.ItemStack;
import org.spongepowered.api.text.Text;
import org.spongepowered.api.util.annotation.NonnullByDefault;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * @author ustc_zzzz
 */
@NonnullByDefault
public class VirtualChestItemStackSerializer implements Function<DataView, ItemStack>
{
    private static final Set<DataQuery> EXCEPTIONS;
    private static final TypeSerializer<ItemEnchantment> ITEM_ENCHANTMENT_SERIALIZER= new ItemEnchantmentSerializer();

    private static final DataManager DATA_MANAGER = Sponge.getDataManager();
    private static final Map<DataQuery, Key<?>> KEYS;

    static
    {
        EXCEPTIONS = ImmutableSet.of(DataQuery.of("UnsafeData"), DataQuery.of("Data"),
                DataQuery.of("Count"), DataQuery.of("ItemType"), DataQuery.of("UnsafeDamage"));
    }

    static
    {
        ImmutableMap.Builder<DataQuery, Key<?>> builder = ImmutableMap.builder();
        for (Key<?> key : Sponge.getRegistry().getAllOf(Key.class))
        {
            builder.put(key.getQuery(), key);
        }
        KEYS = builder.build();
    }

    private final TypeSerializerCollection serializers;
    private final VirtualChestPlugin plugin;

    VirtualChestItemStackSerializer(VirtualChestPlugin plugin, Player player)
    {
        this.plugin = plugin;
        this.serializers = TypeSerializers.getDefaultSerializers().newChild()
                .registerType(TypeToken.of(ItemEnchantment.class), ITEM_ENCHANTMENT_SERIALIZER)
                .registerType(TypeToken.of(Text.class), new TextSerializer(player, plugin.getPlaceholderParser()));
    }

    private <T, U extends BaseValue<T>> void deserializeForKeys(
            ConfigurationNode node, DataQuery dataQuery, BiConsumer<Key<U>, T> consumer) throws InvalidDataException
    {
        if (KEYS.containsKey(dataQuery))
        {
            try
            {
                @SuppressWarnings("unchecked")
                Key<U> key = (Key<U>) KEYS.get(dataQuery);
                @SuppressWarnings("unchecked")
                TypeToken<T> elementToken = (TypeToken<T>) key.getElementToken();
                consumer.accept(key, Optional.ofNullable(node.getValue(elementToken))
                        .orElseThrow(() -> new InvalidDataException("No value present")));
            }
            catch (ObjectMappingException e)
            {
                throw new InvalidDataException(e);
            }
        }
        else if (!EXCEPTIONS.contains(dataQuery))
        {
            throw new InvalidDataException("No matched query present");
        }
    }

    private ConfigurationNode convertToConfigurationNode(DataView view)
    {
        ConfigurationOptions configurationOptions = ConfigurationOptions.defaults().setSerializers(this.serializers);
        Map<?, ?> values = view.getMap(DataQuery.of()).orElseThrow(InvalidDataException::new);
        return SimpleConfigurationNode.root(configurationOptions).setValue(values);
    }

    @Override
    public ItemStack apply(DataView view) throws InvalidDataException
    {
        ItemStack stack = DATA_MANAGER.deserialize(ItemStack.class, view).orElseThrow(InvalidDataException::new);
        ConfigurationNode node = convertToConfigurationNode(view);
        for (Map.Entry<Object, ? extends ConfigurationNode> entry : node.getChildrenMap().entrySet())
        {
            try
            {
                this.deserializeForKeys(entry.getValue(), DataQuery.of(entry.getKey().toString()), (key, data) ->
                {
                    DataTransactionResult result = stack.offer(key, data);
                    if (!result.isSuccessful())
                    {
                        throw new InvalidDataException();
                    }
                });
            }
            catch (InvalidDataException e)
            {
                this.plugin.getLogger()
                        .warn("Cannot apply field '" + entry.getKey() + "' to the item, ignore it.", e);
            }
        }
        return stack;
    }

    private static final class TextSerializer implements TypeSerializer<Text>
    {
        private final Player player;
        private final VirtualChestPlaceholderParser placeholderParser;

        private TextSerializer(Player player, VirtualChestPlaceholderParser placeholderParser)
        {
            this.player = player;
            this.placeholderParser = placeholderParser;
        }

        @Override
        public Text deserialize(TypeToken<?> t, ConfigurationNode value) throws ObjectMappingException
        {
            return this.placeholderParser.parseItemText(this.player, value.getString());
        }

        @Override
        public void serialize(TypeToken<?> t, Text o, ConfigurationNode value) throws ObjectMappingException
        {
            throw new ObjectMappingException(new UnsupportedOperationException());
        }
    }

    private static final class ItemEnchantmentSerializer implements TypeSerializer<ItemEnchantment>
    {
        private Optional<ItemEnchantment> deserializeItemEnchantment(String e)
        {
            int colonFirstIndex = e.indexOf(':'), colonLastIndex = e.lastIndexOf(':');
            int level = colonFirstIndex == colonLastIndex ? 1 : Integer.valueOf(e.substring(colonLastIndex + 1));
            String enchantmentId = colonFirstIndex == colonLastIndex ? e : e.substring(0, colonLastIndex);
            Optional<Enchantment> enchantmentOptional = Sponge.getRegistry().getType(Enchantment.class, enchantmentId);
            return enchantmentOptional.map(enchantment -> new ItemEnchantment(enchantment, level));
        }

        @Override
        public ItemEnchantment deserialize(TypeToken<?> t, ConfigurationNode value) throws ObjectMappingException
        {
            return Optional.ofNullable(value.getString()).flatMap(this::deserializeItemEnchantment).orElse(null);
        }

        @Override
        public void serialize(TypeToken<?> t, ItemEnchantment o, ConfigurationNode value) throws ObjectMappingException
        {
            throw new ObjectMappingException(new UnsupportedOperationException());
        }
    }
}
