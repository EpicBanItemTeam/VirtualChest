package com.github.ustc_zzzz.virtualchest.api;

import org.spongepowered.api.entity.living.player.Player;
import org.spongepowered.api.event.Event;
import org.spongepowered.api.item.inventory.Inventory;
import org.spongepowered.api.util.annotation.NonnullByDefault;

/**
 * Representation of a chest GUI.
 *
 * @author ustc_zzzz
 * @see LoadEvent
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

    /**
     * Representation of a event, fired while VirtualChest is being loaded at the stage the server
     * is starting, or fired while a reload action (for example, <code>/virtualchest reload</code>
     * is executed) is submitted. The event will be fired on the main thread.
     * <p>
     * All of the operations (registering and unregistering the chest GUI) will dynamically affect
     * the return value of {@link VirtualChestService#ids}, so it could be used for retrieving all
     * of those which are registered, which may be useful when you are registering.
     * <p>
     * The registration for all of the chest GUIs provided by VirtualChest itself will be finished
     * completely before this event is fired.
     *
     * @author ustc_zzzz
     * @see VirtualChestService
     */
    @NonnullByDefault
    interface LoadEvent extends Event
    {
        /**
         * unregister an id and its corresponding chest GUI.
         *
         * @param identifier the id to be unregistered
         */
        void unregister(String identifier);

        /**
         * register an id and its corresponding chest GUI.
         *
         * @param identifier the id to be registered
         * @param chest      the corresponding chest GUI
         */
        void register(String identifier, VirtualChest chest);
    }
}
