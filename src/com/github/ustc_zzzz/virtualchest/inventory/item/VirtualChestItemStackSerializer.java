package com.github.ustc_zzzz.virtualchest.inventory.item;

import com.github.ustc_zzzz.virtualchest.VirtualChestPlugin;
import com.github.ustc_zzzz.virtualchest.timings.VirtualChestTimings;
import com.github.ustc_zzzz.virtualchest.unsafe.SpongeUnimplemented;
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
import org.spongepowered.api.data.DataQuery;
import org.spongepowered.api.data.DataTransactionResult;
import org.spongepowered.api.data.DataView;
import org.spongepowered.api.data.Queries;
import org.spongepowered.api.data.key.Key;
import org.spongepowered.api.data.key.Keys;
import org.spongepowered.api.data.manipulator.mutable.RepresentedPlayerData;
import org.spongepowered.api.data.persistence.InvalidDataException;
import org.spongepowered.api.data.type.SkullTypes;
import org.spongepowered.api.data.value.BaseValue;
import org.spongepowered.api.entity.living.player.Player;
import org.spongepowered.api.item.ItemTypes;
import org.spongepowered.api.item.inventory.ItemStack;
import org.spongepowered.api.profile.GameProfile;
import org.spongepowered.api.profile.GameProfileManager;
import org.spongepowered.api.text.Text;
import org.spongepowered.api.text.serializer.TextSerializers;
import org.spongepowered.api.util.Coerce;

import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;

/**
 * @author ustc_zzzz
 */
public class VirtualChestItemStackSerializer implements BiFunction<Player, DataView, ItemStack>
{
    private static final Set<DataQuery> EXCEPTIONS;
    private static final TextSerializer TEXT_SERIALIZER = new TextSerializer();
    private static final GameProfileSerializer GAME_PROFILE_SERIALIZER = new GameProfileSerializer();
    private static final ItemEnchantmentSerializer ITEM_ENCHANTMENT_SERIALIZER = new ItemEnchantmentSerializer();
    private static final TypeToken<?> ITEM_ENCHANTMENT = TypeToken.of(SpongeUnimplemented.getItemEnchantmentClass());

    private static final Map<DataQuery, Key<?>> KEYS;

    static
    {
        EXCEPTIONS = ImmutableSet.of(DataQuery.of("UnsafeData"), DataQuery.of("Data"),
                DataQuery.of("Count"), DataQuery.of("ItemType"), DataQuery.of("UnsafeDamage"));
    }

    static
    {
        Map<DataQuery, Key<?>> keys = new LinkedHashMap<>();
        for (Key<?> key : Sponge.getRegistry().getAllOf(Key.class))
        {
            DataQuery query = key.getQuery();
            Key<?> keyPrevious = keys.get(query);
            if (Objects.isNull(keyPrevious) || !keyPrevious.getId().startsWith("sponge:")) // f**king duplicate queries
            {
                keys.put(query, key);
            }
        }
        KEYS = Collections.unmodifiableMap(keys);
    }

    private final TypeSerializerCollection serializers;
    private final VirtualChestPlugin plugin;

    VirtualChestItemStackSerializer(VirtualChestPlugin plugin)
    {
        this.plugin = plugin;
        this.serializers = TypeSerializers.getDefaultSerializers().newChild()
                .registerType(TypeToken.of(Text.class), TEXT_SERIALIZER)
                .registerType(ITEM_ENCHANTMENT, ITEM_ENCHANTMENT_SERIALIZER)
                .registerType(TypeToken.of(GameProfile.class), GAME_PROFILE_SERIALIZER);
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

    private ConfigurationNode applyPlaceholders(Player player, ConfigurationNode node)
    {
        if (node.hasListChildren())
        {
            for (ConfigurationNode child : node.getChildrenList())
            {
                this.applyPlaceholders(player, child);
            }
        }
        else if (node.hasMapChildren())
        {
            for (ConfigurationNode child : node.getChildrenMap().values())
            {
                this.applyPlaceholders(player, child);
            }
        }
        else
        {
            String value = node.getString("");
            String newValue = this.plugin.getPlaceholderManager().parseText(player, value);
            if (!value.equals(newValue))
            {
                node.setValue(newValue);
            }
        }
        return node;
    }

    @Override
    public ItemStack apply(Player player, DataView view) throws InvalidDataException
    {
        return this.deserializeItemFrom(this.applyPlaceholders(player, view));
    }

    private ConfigurationNode applyPlaceholders(Player player, DataView view)
    {
        VirtualChestTimings.applyPlaceholders().startTimingIfSync();
        try
        {
            return this.applyPlaceholders(player, this.convertToConfigurationNode(view));
        }
        catch (InvalidDataException e)
        {
            throw e;
        }
        catch (Exception e)
        {
            throw new InvalidDataException(e);
        }
        finally
        {
            VirtualChestTimings.applyPlaceholders().stopTimingIfSync();
        }
    }

    private ItemStack deserializeItemFrom(ConfigurationNode node)
    {
        VirtualChestTimings.deserializeItem().startTimingIfSync();
        try
        {
            ItemStack stack = Objects.requireNonNull(node.getValue(TypeToken.of(ItemStack.class)));
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
                    String message = "Cannot apply field '" + entry.getKey() + "' to the item, ignore it.";
                    this.plugin.getLogger().warn(message, e);
                }
            }
            return stack;
        }
        catch (InvalidDataException e)
        {
            throw e;
        }
        catch (Exception e)
        {
            throw new InvalidDataException(e);
        }
        finally
        {
            VirtualChestTimings.deserializeItem().stopTimingIfSync();
        }
    }

    private static final class TextSerializer implements TypeSerializer<Text>
    {
        @Override
        public Text deserialize(TypeToken<?> t, ConfigurationNode value) throws ObjectMappingException
        {
            String string = value.getString();
            return string == null ? null : TextSerializers.FORMATTING_CODE.deserialize(string);
        }

        @Override
        public void serialize(TypeToken<?> t, Text o, ConfigurationNode value) throws ObjectMappingException
        {
            if (o != null)
            {
                value.setValue(TextSerializers.FORMATTING_CODE.serialize(o));
            }
        }
    }

    private static final class ItemEnchantmentSerializer implements TypeSerializer<Object>
    {
        private Optional<Object> deserializeItemEnchantment(String s)
        {
            try
            {
                int colonFirst = s.indexOf(':'), colonIndex = s.lastIndexOf(':');
                String enchantmentId = colonFirst == colonIndex ? s : s.substring(0, colonIndex);
                int enchantmentLevel = colonFirst == colonIndex ? 1 : Coerce.toInteger(s.substring(colonIndex + 1));

                ConfigurationNode node = SimpleConfigurationNode.root(/* default deserializer */);
                node.getNode(Queries.ENCHANTMENT_ID.toString()).setValue(enchantmentId);
                node.getNode(Queries.LEVEL.toString()).setValue(enchantmentLevel);
                return Optional.ofNullable(node.getValue(ITEM_ENCHANTMENT));
            }
            catch (Exception e)
            {
                return Optional.empty();
            }
        }

        @Override
        public Object deserialize(TypeToken<?> t, ConfigurationNode value) throws ObjectMappingException
        {
            return Optional.ofNullable(value.getString()).flatMap(this::deserializeItemEnchantment).orElse(null);
        }

        @Override
        public void serialize(TypeToken<?> type, Object obj, ConfigurationNode value) throws ObjectMappingException
        {
            throw new ObjectMappingException(new UnsupportedOperationException());
        }
    }

    private static final class GameProfileSerializer implements TypeSerializer<GameProfile>
    {
        private static final String KEY_UUID = "UUID";
        private static final String KEY_NAME = "Name";
        private static final boolean IS_ONLINE_MODE_ENABLED = Sponge.getServer().getOnlineMode();
        private static final GameProfileManager GAME_PROFILE_MANAGER = Sponge.getServer().getGameProfileManager();

        private final Optional<GameProfile> nullProfile = getNullGameProfile();

        private Optional<GameProfile> getNullGameProfile()
        {
            // noinspection ConstantConditions
            return ItemStack.builder()
                    .itemType(ItemTypes.SKULL).quantity(1)
                    .add(Keys.SKULL_TYPE, SkullTypes.PLAYER).build()
                    .getOrCreate(RepresentedPlayerData.class).map(data -> data.owner().get());
        }

        private static GameProfile getFilledGameProfileOrElseFallback(GameProfile profile)
        {
            if (!IS_ONLINE_MODE_ENABLED)
            {
                return profile; // TODO: maybe we should also load player skins in offline mode
            }
            try
            {
                return GAME_PROFILE_MANAGER.fill(profile).get(50, TimeUnit.MILLISECONDS); // TODO: asynchronous action
            }
            catch (InterruptedException | ExecutionException | TimeoutException e)
            {
                return profile;
            }
        }

        private static UUID getUUIDByString(String uuidString) throws ObjectMappingException
        {
            try
            {
                return UUID.fromString(uuidString);
            }
            catch (IllegalArgumentException e)
            {
                throw new ObjectMappingException("Invalid UUID string: " + uuidString);
            }
        }

        @Override
        public GameProfile deserialize(TypeToken<?> type, ConfigurationNode value) throws ObjectMappingException
        {
            String name = value.getNode(KEY_NAME).getString(), uuid = value.getNode(KEY_UUID).getString();
            if (Objects.isNull(uuid))
            {
                return this.nullProfile.orElseThrow(() -> new ObjectMappingException("Empty profile is not allowed"));
            }
            return getFilledGameProfileOrElseFallback(GameProfile.of(getUUIDByString(uuid), name));
        }

        @Override
        public void serialize(TypeToken<?> type, GameProfile p, ConfigurationNode value) throws ObjectMappingException
        {
            if (!Objects.isNull(p) && !(nullProfile.isPresent() && nullProfile.get().equals(p)))
            {
                value.getNode(KEY_UUID).setValue(p.getUniqueId());
                p.getName().ifPresent(name -> value.getNode(KEY_NAME).setValue(name));
            }
        }
    }
}
