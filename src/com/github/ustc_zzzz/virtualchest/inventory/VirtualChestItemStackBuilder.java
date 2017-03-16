package com.github.ustc_zzzz.virtualchest.inventory;

import com.github.ustc_zzzz.virtualchest.VirtualChestPlugin;
import org.spongepowered.api.Sponge;
import org.spongepowered.api.data.DataManager;
import org.spongepowered.api.data.DataQuery;
import org.spongepowered.api.data.DataView;
import org.spongepowered.api.data.key.Keys;
import org.spongepowered.api.data.meta.ItemEnchantment;
import org.spongepowered.api.data.persistence.DataBuilder;
import org.spongepowered.api.data.persistence.InvalidDataException;
import org.spongepowered.api.entity.living.player.Player;
import org.spongepowered.api.item.Enchantment;
import org.spongepowered.api.item.inventory.ItemStack;
import org.spongepowered.api.item.inventory.ItemStackSnapshot;
import org.spongepowered.api.text.Text;
import org.spongepowered.api.util.annotation.NonnullByDefault;

import java.util.Optional;
import java.util.stream.Collectors;

/**
 * @author ustc_zzzz
 */
@NonnullByDefault
public class VirtualChestItemStackBuilder implements DataBuilder<ItemStack>
{
    public static final DataQuery LORE = Keys.ITEM_LORE.getQuery();
    public static final DataQuery DISPLAY_NAME = Keys.DISPLAY_NAME.getQuery();
    public static final DataQuery ENCHANTMENTS = Keys.ITEM_ENCHANTMENTS.getQuery();
    public static final DataQuery HIDE_ENCHANTMENTS = DataQuery.of("HideEnchantments");

    private static final DataManager DATA_MANAGER = Sponge.getDataManager();

    private final VirtualChestPlugin plugin;
    private final Player player;

    public VirtualChestItemStackBuilder(VirtualChestPlugin plugin, Player player)
    {
        this.plugin = plugin;
        this.player = player;
    }

    private Text parsePlaceholder(String text)
    {
        return this.plugin.getPlaceholderParser().parseItemText(player, text);
    }

    private ItemEnchantment deserializeItemEnchantment(String e)
    {
        int colonFirstIndex = e.indexOf(':'), colonLastIndex = e.lastIndexOf(':');
        int level = colonFirstIndex == colonLastIndex ? 1 : Integer.valueOf(e.substring(colonLastIndex + 1));
        String enchantmentId = colonFirstIndex == colonLastIndex ? e : e.substring(0, colonLastIndex);
        Optional<Enchantment> optional = Sponge.getRegistry().getType(Enchantment.class, enchantmentId);
        Enchantment enchantment = optional.orElseThrow(() -> new InvalidDataException("Invalid enchantment"));
        return new ItemEnchantment(enchantment, level);
    }

    @Override
    public Optional<ItemStack> build(DataView view) throws InvalidDataException
    {
        ItemStack itemStack = DATA_MANAGER.deserialize(ItemStack.class, view).orElseThrow(InvalidDataException::new);
        view.getStringList(LORE).ifPresent(l -> itemStack.offer(Keys.ITEM_LORE, l.stream()
                .map(this::parsePlaceholder).collect(Collectors.toList())));
        view.getString(DISPLAY_NAME).ifPresent(d -> itemStack.offer(Keys.DISPLAY_NAME, parsePlaceholder(d)));
        view.getStringList(ENCHANTMENTS).ifPresent(e -> itemStack.offer(Keys.ITEM_ENCHANTMENTS, e.stream()
                .map(this::deserializeItemEnchantment).collect(Collectors.toList())));
        view.getBoolean(HIDE_ENCHANTMENTS).ifPresent(h -> itemStack.offer(Keys.HIDE_ENCHANTMENTS, h));
        return Optional.of(itemStack);
    }
}
