package com.github.ustc_zzzz.virtualchest.timings;

import co.aikar.timings.Timing;
import co.aikar.timings.Timings;
import com.github.ustc_zzzz.virtualchest.VirtualChestPlugin;
import org.spongepowered.api.Sponge;

/**
 * @author ustc_zzzz
 */
public final class VirtualChestTimings
{
    private static final Object plugin;

    static
    {
        // noinspection ConstantConditions
        plugin = Sponge.getPluginManager().getPlugin(VirtualChestPlugin.PLUGIN_ID).get().getInstance().get();
    }

    /* Order:
     *
     * - updateAndRefreshMappings
     *   - checkRequirements
     *     - prepareRequirementBindings
     *     - executeRequirementScripts
     *   - setItemInInventories
     *     - applyPlaceholders
     *     - deserializeItems
     */

    public static final Timing UPDATE_AND_REFRESH_MAPPINGS = Timings.of(plugin, "updateAndRefreshMappings");

    public static final Timing CHECK_REQUIREMENTS = Timings.of(plugin, "checkRequirements");

    public static final Timing PREPARE_REQUIREMENT_BINDINGS = Timings.of(plugin, "prepareRequirementBindings");

    public static final Timing EXECUTE_REQUIREMENT_SCRIPTS = Timings.of(plugin, "executeRequirementScripts");

    public static final Timing SET_ITEM_IN_INVENTORIES = Timings.of(plugin, "setItemInInventories");

    public static final Timing APPLY_PLACEHOLDERS = Timings.of(plugin, "applyPlaceholders");

    public static final Timing DESERIALIZE_ITEMS = Timings.of(plugin, "deserializeItems");

    private VirtualChestTimings()
    {
    }
}
