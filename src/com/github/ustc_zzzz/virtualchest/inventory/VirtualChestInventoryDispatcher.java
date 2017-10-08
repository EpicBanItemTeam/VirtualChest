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
import org.spongepowered.api.command.CommandSource;
import org.spongepowered.api.entity.living.player.Player;
import org.spongepowered.api.service.permission.Subject;

import java.io.File;
import java.io.IOException;
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
                this.logger.warn("Cannot extract default chest GUI configurations", e);
            }
        }
        return Collections.singletonList(defaultMenuDir);
    }

    public boolean hasPermission(Player player, String inventoryName)
    {
        boolean hasSelfPermission = player.hasPermission("virtualchest.open.self." + inventoryName);
        boolean hasOthersPermission = player.hasPermission("virtualchest.open.others." + inventoryName);
        return hasSelfPermission || hasOthersPermission;
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
                        newInventories.put(chestName, menuRoot.getValue(TypeToken.of(VirtualChestInventory.class)));
                    }
                    catch (IOException | ObjectMappingException e)
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
