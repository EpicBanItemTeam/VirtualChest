package com.github.ustc_zzzz.virtualchest.timings;

import co.aikar.timings.Timing;
import co.aikar.timings.Timings;
import com.github.ustc_zzzz.virtualchest.VirtualChestPlugin;
import com.github.ustc_zzzz.virtualchest.inventory.VirtualChestInventory;
import org.spongepowered.api.Sponge;

/**
 * @author ustc_zzzz
 */
public final class VirtualChestTimings
{
    /* Order:
     *
     * - updateAndRefreshMappings(name)
     *   - updateAndRefreshMapping(index)
     *     - checkRequirements(index)
     *       - prepareRequirementBindings
     *       - executeRequirementScript
     *     - setItemInInventory(index)
     *       - applyPlaceholders
     *       - deserializeItem
     */

    public static Timing updateAndRefreshMappings(String name)
    {
        return Timings.of(plugin, "updateAndRefreshMappings - " + name);
    }

    public static Timing updateAndRefreshMapping(String name, int i)
    {
        return Timings.of(plugin, "updateAndRefreshMapping - " + VirtualChestInventory.slotIndexToKey(i) + " in " + name);
    }

    public static Timing checkRequirements(String name, int i)
    {
        return Timings.of(plugin, "checkRequirements - " + VirtualChestInventory.slotIndexToKey(i) + " in " + name);
    }

    public static Timing prepareRequirementBindings()
    {
        return PREPARE_REQUIREMENT_BINDINGS;
    }

    public static Timing executeRequirementScript()
    {
        return EXECUTE_REQUIREMENT_SCRIPT;
    }

    public static Timing setItemInInventory(String name, int i)
    {
        return Timings.of(plugin, "setItemInInventory - " + VirtualChestInventory.slotIndexToKey(i) + " in " + name);
    }

    public static Timing applyPlaceholders()
    {
        return APPLY_PLACEHOLDERS;
    }

    public static Timing deserializeItem()
    {
        return DESERIALIZE_ITEM;
    }

    private static final Object plugin;

    static
    {
        // noinspection ConstantConditions
        plugin = Sponge.getPluginManager().getPlugin(VirtualChestPlugin.PLUGIN_ID).get().getInstance().get();
    }

    private static final Timing PREPARE_REQUIREMENT_BINDINGS = Timings.of(plugin, "prepareRequirementBindings");
    private static final Timing EXECUTE_REQUIREMENT_SCRIPT = Timings.of(plugin, "executeRequirementScript");
    private static final Timing APPLY_PLACEHOLDERS = Timings.of(plugin, "applyPlaceholders");
    private static final Timing DESERIALIZE_ITEM = Timings.of(plugin, "deserializeItem");

    private VirtualChestTimings()
    {
    }
}
