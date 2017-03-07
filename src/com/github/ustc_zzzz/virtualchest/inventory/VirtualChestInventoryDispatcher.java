package com.github.ustc_zzzz.virtualchest.inventory;

import com.github.ustc_zzzz.virtualchest.VirtualChestPlugin;
import org.spongepowered.api.entity.living.player.Player;
import org.spongepowered.api.item.inventory.Inventory;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * @author ustc_zzzz
 */
public class VirtualChestInventoryDispatcher
{
    private final VirtualChestPlugin plugin;

    private Map<String, VirtualChestInventory> inventories = new HashMap<>();

    public VirtualChestInventoryDispatcher(VirtualChestPlugin plugin)
    {
        this.plugin = plugin;
    }

    public void updateInventories(Map<String, VirtualChestInventory> newInventories, boolean purgeOldOnes)
    {
        if (purgeOldOnes)
        {
            inventories.clear();
        }
        inventories.putAll(newInventories);
    }

    public Set<String> listInventories()
    {
        return inventories.keySet();
    }

    public Optional<VirtualChestInventory> getInventory(String inventoryName)
    {
        return Optional.ofNullable(inventories.get(inventoryName));
    }

    public Optional<Inventory> createInventory(String name, Player player)
    {
        return getInventory(name).map(i -> i.createInventory(player));
    }
}
