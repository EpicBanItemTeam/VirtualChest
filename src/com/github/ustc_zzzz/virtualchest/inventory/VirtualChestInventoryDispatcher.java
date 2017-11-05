package com.github.ustc_zzzz.virtualchest.inventory;

import com.github.ustc_zzzz.virtualchest.VirtualChestPlugin;
import com.github.ustc_zzzz.virtualchest.translation.VirtualChestTranslation;
import com.google.common.collect.ImmutableList;
import com.google.common.reflect.TypeToken;
import ninja.leaping.configurate.commented.CommentedConfigurationNode;
import ninja.leaping.configurate.hocon.HoconConfigurationLoader;
import ninja.leaping.configurate.objectmapping.ObjectMappingException;
import org.slf4j.Logger;
import org.spongepowered.api.Sponge;
import org.spongepowered.api.asset.Asset;
import org.spongepowered.api.asset.AssetManager;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.*;

/**
 * @author ustc_zzzz
 */
public class VirtualChestInventoryDispatcher
{
    private final Logger logger;
    private final VirtualChestPlugin plugin;
    private final VirtualChestTranslation translation;

    private List<String> menuDirs = ImmutableList.of();
    private Map<String, VirtualChestInventory> inventories = new LinkedHashMap<>();

    public VirtualChestInventoryDispatcher(VirtualChestPlugin plugin)
    {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
        this.translation = plugin.getTranslation();
    }

    public void updateInventories(Map<String, VirtualChestInventory> newInventories, boolean purgeOldOnes)
    {
        if (purgeOldOnes)
        {
            inventories.clear();
        }
        for (Map.Entry<String, VirtualChestInventory> entry : newInventories.entrySet())
        {
            inventories.put(entry.getKey(), Objects.requireNonNull(entry.getValue()));
        }
    }

    public Set<String> listInventories()
    {
        return inventories.keySet();
    }

    public Optional<VirtualChestInventory> getInventory(String inventoryName)
    {
        return Optional.ofNullable(inventories.get(inventoryName));
    }

    public void loadConfig(CommentedConfigurationNode node) throws IOException
    {
        try
        {
            Path configDir = plugin.getConfigDir();
            Map<String, VirtualChestInventory> newOnes = new LinkedHashMap<>();
            this.menuDirs = ImmutableList.copyOf(node.getList(TypeToken.of(String.class), this::releaseExample));
            this.menuDirs.stream().map(configDir::resolve).forEach(p -> newOnes.putAll(this.scanDir(p.toFile())));
            this.updateInventories(newOnes, true);
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
                this.logger.warn("Cannot extract default chest GUI configurations", e);
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
                    String chestName = fileName.substring(0, fileName.lastIndexOf(".conf"));
                    try
                    {
                        HoconConfigurationLoader loader = HoconConfigurationLoader.builder().setFile(f).build();
                        CommentedConfigurationNode menuRoot = loader.load().getNode(VirtualChestPlugin.PLUGIN_ID);
                        VirtualChestInventory inventory = menuRoot.getValue(TypeToken.of(VirtualChestInventory.class));
                        newInventories.put(chestName, Objects.requireNonNull(inventory));
                    }
                    catch (Exception e)
                    {
                        this.logger.warn("Find error when reading a file (" + f.getAbsolutePath() +
                                "). Don't worry, we will skip this one and continue to read others", e);
                    }
                }
            }
        }
        return newInventories;
    }
}
