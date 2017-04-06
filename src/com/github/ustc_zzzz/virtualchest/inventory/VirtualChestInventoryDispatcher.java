package com.github.ustc_zzzz.virtualchest.inventory;

import com.github.ustc_zzzz.virtualchest.VirtualChestPlugin;
import com.github.ustc_zzzz.virtualchest.translation.VirtualChestTranslation;
import com.google.common.collect.ImmutableList;
import com.google.common.reflect.TypeToken;
import ninja.leaping.configurate.commented.CommentedConfigurationNode;
import ninja.leaping.configurate.hocon.HoconConfigurationLoader;
import ninja.leaping.configurate.objectmapping.ObjectMappingException;
import org.spongepowered.api.Sponge;
import org.spongepowered.api.asset.Asset;
import org.spongepowered.api.asset.AssetManager;
import org.spongepowered.api.data.DataContainer;
import org.spongepowered.api.data.persistence.DataTranslator;
import org.spongepowered.api.data.persistence.DataTranslators;
import org.spongepowered.api.data.persistence.InvalidDataException;
import org.spongepowered.api.entity.living.player.Player;
import org.spongepowered.api.item.inventory.Inventory;

import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * @author ustc_zzzz
 */
public class VirtualChestInventoryDispatcher
{
    private final VirtualChestPlugin plugin;
    private final VirtualChestTranslation translation;
    private final DataTranslator<VirtualChestInventory> inventoryTranslator;

    private List<String> menuDirs = ImmutableList.of();
    private Map<String, VirtualChestInventory> inventories = new LinkedHashMap<>();

    public VirtualChestInventoryDispatcher(VirtualChestPlugin plugin)
    {
        this.plugin = plugin;
        this.translation = plugin.getTranslation();
        this.inventoryTranslator = Sponge.getDataManager().getTranslator(VirtualChestInventory.class).get();
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
        this.plugin.getLogger().debug("Player {} tries to " +
                "create the chest GUI ({}) by a command", player.getName(), name);
        return getInventory(name).map(i -> i.createInventory(player));
    }

    public void loadConfig(CommentedConfigurationNode node) throws IOException
    {
        try
        {
            menuDirs = ImmutableList.copyOf(node.getList(TypeToken.of(String.class), this::releaseExample));
            Map<String, VirtualChestInventory> newOnes = new LinkedHashMap<>();
            menuDirs.stream().map(plugin.getConfigDir()::resolve).forEach(p -> newOnes.putAll(scanDir(p.toFile())));
            updateInventories(newOnes, true);
        }
        catch (ObjectMappingException e)
        {
            throw new IOException(e);
        }
    }

    public void saveConfig(CommentedConfigurationNode node) throws IOException
    {
        node.setValue(this.menuDirs)
                .setComment(node.getComment().orElse(this.translation
                        .take("virtualchest.config.scanDir.comment").toPlain()));
    }

    public List<String> releaseExample()
    {
        String defaultMenuDir = "menu/";
        File menuDir = this.plugin.getConfigDir().resolve(defaultMenuDir).toFile();
        if (menuDir.isDirectory() || menuDir.mkdirs())
        {
            try
            {
                AssetManager assetManager = Sponge.getAssetManager();
                Optional<Asset> example = assetManager.getAsset(this.plugin, "examples/example.conf");
                Optional<Asset> example2 = assetManager.getAsset(this.plugin, "examples/example2.conf");
                example.orElseThrow(IOException::new).copyToDirectory(menuDir.toPath());
                example2.orElseThrow(IOException::new).copyToDirectory(menuDir.toPath());
            }
            catch (IOException e)
            {
                this.plugin.getLogger().warn("Cannot extract default chest GUI configurations", e);
            }
        }
        return Collections.singletonList(defaultMenuDir);
    }

    private Map<String, VirtualChestInventory> scanDir(File file)
    {
        Map<String, VirtualChestInventory> newInventories = new LinkedHashMap<>();
        if (file.isDirectory() || file.mkdirs())
        {
            for (File f : Optional.ofNullable(file.listFiles()).orElse(new File[0]))
            {
                String fileName = f.getName();
                if (fileName.endsWith(".conf"))
                {
                    fileName = fileName.substring(0, fileName.lastIndexOf(".conf"));
                    try
                    {
                        HoconConfigurationLoader loader = HoconConfigurationLoader.builder().setFile(f).build();
                        CommentedConfigurationNode menuRoot = loader.load().getNode(VirtualChestPlugin.PLUGIN_ID);
                        DataContainer serializedMenu = DataTranslators.CONFIGURATION_NODE.translate(menuRoot);
                        VirtualChestInventory inventory = inventoryTranslator.translate(serializedMenu);
                        newInventories.put(fileName, inventory);
                    }
                    catch (IOException | InvalidDataException e)
                    {
                        String error = "Find error when reading the file (" + fileName + "). "
                                + "Don't worry, we will skip this one and continue to read others";
                        this.plugin.getLogger().warn(error, e);
                    }
                }
            }
        }
        return newInventories;
    }
}
