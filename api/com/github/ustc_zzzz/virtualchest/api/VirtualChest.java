package com.github.ustc_zzzz.virtualchest.api;

import com.github.ustc_zzzz.virtualchest.api.event.VirtualChestLoadEvent;
import org.spongepowered.api.entity.living.player.Player;
import org.spongepowered.api.item.inventory.Inventory;
import org.spongepowered.api.util.annotation.NonnullByDefault;

/**
 * Representation of a chest GUI.
 *
 * @author ustc_zzzz
 * @see VirtualChestLoadEvent
 */
@NonnullByDefault
@FunctionalInterface
public interface VirtualChest
{
    /**
     * Create an instance of {@link Inventory}.
     *
     * @param identifier the registered id
     * @param player     the player
     * @return the instance
     */
    Inventory create(String identifier, Player player);
}
