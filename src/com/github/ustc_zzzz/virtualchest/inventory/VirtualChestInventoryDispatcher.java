package com.github.ustc_zzzz.virtualchest.inventory;

import com.github.ustc_zzzz.virtualchest.VirtualChestPlugin;
import com.github.ustc_zzzz.virtualchest.api.VirtualChest;
import com.github.ustc_zzzz.virtualchest.api.VirtualChestService;
import com.github.ustc_zzzz.virtualchest.translation.VirtualChestTranslation;
import com.github.ustc_zzzz.virtualchest.unsafe.SpongeUnimplemented;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Maps;
import com.google.common.reflect.TypeToken;
import ninja.leaping.configurate.commented.CommentedConfigurationNode;
import ninja.leaping.configurate.hocon.HoconConfigurationLoader;
import ninja.leaping.configurate.objectmapping.ObjectMappingException;
import org.slf4j.Logger;
import org.spongepowered.api.Sponge;
import org.spongepowered.api.asset.Asset;
import org.spongepowered.api.asset.AssetManager;
import org.spongepowered.api.entity.living.player.Player;
import org.spongepowered.api.event.cause.Cause;
import org.spongepowered.api.event.impl.AbstractEvent;
import org.spongepowered.api.item.inventory.Container;
import org.spongepowered.api.item.inventory.ItemStackSnapshot;
import org.spongepowered.api.util.Tuple;
import org.spongepowered.api.util.annotation.NonnullByDefault;

import java.io.File;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.nio.file.Path;
import java.util.*;

/**
 * @author ustc_zzzz
 */
@NonnullByDefault
public class VirtualChestInventoryDispatcher implements VirtualChestService
{
    private final Logger logger;
    private final VirtualChestPlugin plugin;
    private final VirtualChestTranslation translation;

    private List<String> menuDirs = ImmutableList.of();
    private Map<String, VirtualChest> inventories = new LinkedHashMap<>();
    private Map<UUID, Tuple<String, WeakReference<Container>>> containers = new HashMap<>();

    public VirtualChestInventoryDispatcher(VirtualChestPlugin plugin)
    {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
        this.translation = plugin.getTranslation();
        Sponge.getServiceManager().setProvider(plugin, VirtualChestService.class, this);
    }

    @Override
    public Set<String> ids()
    {
        return Collections.unmodifiableSet(inventories.keySet());
    }

    @Override
    public Optional<String> lookup(Player player)
    {
        UUID uuid = player.getUniqueId();
        Tuple<String, WeakReference<Container>> tuple = containers.get(uuid);
        return Optional.ofNullable(tuple).filter(t -> this.isInventoryOpening(player, t)).map(Tuple::getFirst);
    }

    @Override
    public boolean open(String id, Player player)
    {
        UUID uuid = player.getUniqueId();
        if (inventories.containsKey(id))
        {
            Tuple<String, WeakReference<Container>> tuple = containers.get(uuid);
            if (!Objects.nonNull(tuple) || !this.isInventoryOpening(player, id, tuple))
            {
                SpongeUnimplemented.openInventory(player, inventories.get(id).create(id, player), plugin);
                Container container = player.getOpenInventory().orElseThrow(IllegalStateException::new);
                containers.put(uuid, Tuple.of(id, new WeakReference<>(container)));
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean close(String id, Player player)
    {
        UUID uuid = player.getUniqueId();
        if (inventories.containsKey(id))
        {
            Tuple<String, WeakReference<Container>> tuple = containers.remove(uuid);
            if (!Objects.isNull(tuple) && this.isInventoryOpening(player, id, tuple))
            {
                SpongeUnimplemented.closeInventory(player, plugin);
                return true;
            }
        }
        return false;
    }

    public boolean update(String id, Player player)
    {
        UUID uuid = player.getUniqueId();

        if (inventories.containsKey(id))
        {
            Tuple<String, WeakReference<Container>> tuple = containers.get(uuid);
            if (!Objects.isNull(tuple) && this.isInventoryOpening(player, id, tuple))
            {
                // noinspection ConstantConditions
                inventories.get(id).getUpdaterFor(id, player).accept(tuple.getSecond().get().first());
                return true;
            }
        }
        return false;
    }

    public boolean isInventoryMatchingPrimaryAction(String id, ItemStackSnapshot item)
    {
        VirtualChest chest = inventories.get(id);
        return chest instanceof VirtualChestInventory && ((VirtualChestInventory) chest).matchPrimaryAction(item);
    }

    public boolean isInventoryMatchingSecondaryAction(String id, ItemStackSnapshot item)
    {
        VirtualChest chest = inventories.get(id);
        return chest instanceof VirtualChestInventory && ((VirtualChestInventory) chest).matchSecondaryAction(item);
    }

    public void loadConfig(CommentedConfigurationNode node) throws IOException
    {
        try
        {
            Path configDir = plugin.getConfigDir();
            Map<String, VirtualChestInventory> newOnes = new LinkedHashMap<>();
            this.menuDirs = ImmutableList.copyOf(node.getList(TypeToken.of(String.class), this::releaseExample));
            this.menuDirs.stream().map(configDir::resolve).forEach(p -> newOnes.putAll(this.scanDir(p.toFile())));
            this.updateInventories(newOnes);
            this.fireLoadEvent();
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
        boolean hasSelfUpdatePermission = player.hasPermission("virtualchest.update.self" + inventoryName);
        boolean hasOthersUpdatePermission = player.hasPermission("virtualchest.update.others" + inventoryName);
        return hasSelfPermission || hasOthersPermission || hasSelfUpdatePermission || hasOthersUpdatePermission;
    }

    public boolean isInventoryOpening(Player player, Container container)
    {
        Optional<Container> openInventory = player.getOpenInventory();
        return openInventory.isPresent() && container.equals(openInventory.get());
    }

    private boolean isInventoryOpening(Player player, Tuple<String, WeakReference<Container>> tuple)
    {
        Container container = tuple.getSecond().get();
        return Objects.nonNull(container) && this.isInventoryOpening(player, container);
    }

    private boolean isInventoryOpening(Player player, String id, Tuple<String, WeakReference<Container>> tuple)
    {
        String first = tuple.getFirst();
        return id.equals(first) && this.isInventoryOpening(player, tuple);
    }

    private void fireLoadEvent()
    {
        Sponge.getEventManager().post(new LoadEvent());
    }

    private void updateInventories(Map<String, VirtualChestInventory> newInventories)
    {
        inventories.clear();
        inventories.putAll(Maps.transformValues(newInventories, Objects::requireNonNull));
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

    private class LoadEvent extends AbstractEvent implements VirtualChest.LoadEvent
    {
        @Override
        public void unregister(String identifier)
        {
            inventories.remove(identifier);
        }

        @Override
        public void register(String identifier, VirtualChest chest)
        {
            inventories.put(identifier, chest);
        }

        @Override
        public Cause getCause()
        {
            return SpongeUnimplemented.createCause(plugin);
        }
    }
}
