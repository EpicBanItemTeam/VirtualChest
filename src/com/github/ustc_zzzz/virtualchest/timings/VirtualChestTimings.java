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
        return Timings.of(PLUGIN, "updateAndRefreshMappings - " + name);
    }

    public static Timing updateAndRefreshMapping(String name, int i)
    {
        return Timings.of(PLUGIN, "updateAndRefreshMapping - " + VirtualChestInventory.slotIndexToKey(i) + " in " + name);
    }

    public static Timing checkRequirements(String name, int i)
    {
        return Timings.of(PLUGIN, "checkRequirements - " + VirtualChestInventory.slotIndexToKey(i) + " in " + name);
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
        return Timings.of(PLUGIN, "setItemInInventory - " + VirtualChestInventory.slotIndexToKey(i) + " in " + name);
    }

    public static Timing applyPlaceholders()
    {
        return APPLY_PLACEHOLDERS;
    }

    public static Timing deserializeItem()
    {
        return DESERIALIZE_ITEM;
    }

    private static final Object PLUGIN;

    static
    {
        // noinspection ConstantConditions
        PLUGIN = Sponge.getPluginManager().getPlugin(VirtualChestPlugin.PLUGIN_ID).get().getInstance().get();
    }

    private static final Timing PREPARE_REQUIREMENT_BINDINGS = Timings.of(PLUGIN, "prepareRequirementBindings");
    private static final Timing EXECUTE_REQUIREMENT_SCRIPT = Timings.of(PLUGIN, "executeRequirementScript");
    private static final Timing APPLY_PLACEHOLDERS = Timings.of(PLUGIN, "applyPlaceholders");
    private static final Timing DESERIALIZE_ITEM = Timings.of(PLUGIN, "deserializeItem");

    private VirtualChestTimings()
    {
    }
}
