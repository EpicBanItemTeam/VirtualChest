package com.github.ustc_zzzz.virtualchest.api.event;

import com.github.ustc_zzzz.virtualchest.api.VirtualChest;
import com.github.ustc_zzzz.virtualchest.api.VirtualChestService;
import org.spongepowered.api.event.Event;
import org.spongepowered.api.util.annotation.NonnullByDefault;

/**
 * Representation of a event fired when VirtualChest is loaded when the server starts up, or fired
 * when a reload action (For example, <code>/virtualchest reload</code> is executed) is submitted.
 * The event will be fired on the main thread.
 * <p>
 * All of the operations (registering and unregistering things) will dynamically affect the return
 * value of {@link VirtualChestService#ids}, so it could be used for retrieving all of those which
 * are registered, which may be useful when you are registering.
 * <p>
 * The registration for the chest GUIs provided by VirtualChest itself will be finished completely
 * before this event is fired.
 *
 * @author ustc_zzzz
 * @see VirtualChest
 * @see VirtualChestService
 */
@NonnullByDefault
public interface VirtualChestLoadEvent extends Event
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
