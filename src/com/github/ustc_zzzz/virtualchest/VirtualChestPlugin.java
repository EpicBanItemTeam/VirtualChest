package com.github.ustc_zzzz.virtualchest;

import com.github.ustc_zzzz.virtualchest.action.VirtualChestActions;
import com.github.ustc_zzzz.virtualchest.command.VirtualChestCommand;
import com.github.ustc_zzzz.virtualchest.inventory.VirtualChestInventory;
import com.github.ustc_zzzz.virtualchest.inventory.VirtualChestInventoryDispatcher;
import com.github.ustc_zzzz.virtualchest.inventory.VirtualChestInventoryTranslator;
import com.github.ustc_zzzz.virtualchest.translation.VirtualChestTranslation;
import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableList;
import com.google.common.reflect.TypeToken;
import com.google.inject.Inject;
import ninja.leaping.configurate.commented.CommentedConfigurationNode;
import ninja.leaping.configurate.hocon.HoconConfigurationLoader;
import ninja.leaping.configurate.loader.ConfigurationLoader;
import ninja.leaping.configurate.objectmapping.ObjectMappingException;
import org.slf4j.Logger;
import org.spongepowered.api.Sponge;
import org.spongepowered.api.config.ConfigDir;
import org.spongepowered.api.config.DefaultConfig;
import org.spongepowered.api.data.DataContainer;
import org.spongepowered.api.data.DataManager;
import org.spongepowered.api.data.persistence.DataTranslator;
import org.spongepowered.api.data.persistence.DataTranslators;
import org.spongepowered.api.entity.living.player.Player;
import org.spongepowered.api.event.Listener;
import org.spongepowered.api.event.Order;
import org.spongepowered.api.event.cause.Cause;
import org.spongepowered.api.event.filter.cause.First;
import org.spongepowered.api.event.game.GameReloadEvent;
import org.spongepowered.api.event.game.state.GameAboutToStartServerEvent;
import org.spongepowered.api.event.game.state.GamePreInitializationEvent;
import org.spongepowered.api.event.game.state.GameStartedServerEvent;
import org.spongepowered.api.event.item.inventory.InteractItemEvent;
import org.spongepowered.api.plugin.Plugin;
import org.spongepowered.api.util.GuavaCollectors;
import org.spongepowered.api.util.annotation.NonnullByDefault;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.*;

/**
 * @author ustc_zzzz
 */
@NonnullByDefault
@Plugin(id = VirtualChestPlugin.PLUGIN_ID, name = "VirtualChest", authors =
        {"ustc_zzzz"}, version = "@version@", description = VirtualChestPlugin.DESCRIPTION)
public class VirtualChestPlugin
{
    public static final String PLUGIN_ID = "virtualchest";
    public static final String DESCRIPTION = "A plugin providing virtual chests";

    @Inject
    private Logger logger;

    @Inject
    @ConfigDir(sharedRoot = false)
    private Path configDir;

    @Inject
    @DefaultConfig(sharedRoot = false)
    private ConfigurationLoader<CommentedConfigurationNode> config;
    private List<String> menuDirs = ImmutableList.of();

    private VirtualChestTranslation translation;
    private VirtualChestActions virtualChestActions;
    private VirtualChestCommand virtualChestCommand;
    private VirtualChestInventoryDispatcher dispatcher;

    private void loadConfig() throws IOException
    {
        CommentedConfigurationNode root = config.load();
        try
        {
            DataTranslator<VirtualChestInventory> translator = Sponge.getDataManager().getTranslator(VirtualChestInventory.class).get();
            this.menuDirs = root.getNode(PLUGIN_ID, "scan-dirs").getList(TypeToken.of(String.class), Collections.singletonList("menu/"));
            List<Path> dirs = this.menuDirs.stream().map(this.configDir::resolve).collect(GuavaCollectors.toImmutableList());
            Map<String, VirtualChestInventory> newInventories = new HashMap<>();
            for (Path path : dirs)
            {
                try
                {
                    File file = path.toFile();
                    if (file.isDirectory() || file.mkdirs())
                    {
                        for (File f : Optional.ofNullable(file.listFiles()).orElse(new File[0]))
                        {
                            String fileName = f.getName();
                            if (fileName.endsWith(".conf"))
                            {
                                fileName = fileName.substring(0, fileName.lastIndexOf(".conf"));
                                HoconConfigurationLoader loader = HoconConfigurationLoader.builder().setFile(f).build();
                                CommentedConfigurationNode menuRoot = loader.load().getNode(PLUGIN_ID);
                                DataContainer serializedMenu = DataTranslators.CONFIGURATION_NODE.translate(menuRoot);
                                VirtualChestInventory inventory = translator.translate(serializedMenu);
                                newInventories.put(fileName, inventory);
                            }
                        }
                    }
                }
                catch (IOException e)
                {
                    this.logger.warn("Find error when reading the file, skip this file and continue to read others", e);
                }
            }
            this.dispatcher.updateInventories(newInventories, true);
        }
        catch (ObjectMappingException e)
        {
            throw new IOException(e);
        }
    }

    private void saveConfig() throws IOException
    {
        CommentedConfigurationNode root = config.createEmptyNode();
        root.getNode(PLUGIN_ID, "scan-dirs").setValue(this.menuDirs);
        config.save(root);
    }

    @Listener(order = Order.EARLY)
    public void onInteractItemPrimary(InteractItemEvent.Primary event, @First Player player)
    {
        for (String inventoryName : this.dispatcher.listInventories())
        {
            VirtualChestInventory inventory = this.dispatcher.getInventory(inventoryName).get();
            if (inventory.matchItemForOpeningWithPrimaryAction(event.getItemStack()))
            {
                if (player.hasPermission("virtualchest.open.self." + inventoryName))
                {
                    player.openInventory(inventory.createInventory(player), Cause.source(this).build());
                    event.setCancelled(true);
                    break;
                }
            }
        }
    }

    @Listener(order = Order.EARLY)
    public void onInteractItemSecondary(InteractItemEvent.Secondary event, @First Player player)
    {
        for (String inventoryName : this.dispatcher.listInventories())
        {
            VirtualChestInventory inventory = this.dispatcher.getInventory(inventoryName).get();
            if (inventory.matchItemForOpeningWithSecondaryAction(event.getItemStack()))
            {
                if (player.hasPermission("virtualchest.open.self." + inventoryName))
                {
                    player.openInventory(inventory.createInventory(player), Cause.source(this).build());
                    event.setCancelled(true);
                    break;
                }
            }
        }
    }

    @Listener
    public void onReload(GameReloadEvent event)
    {
        try
        {
            this.logger.info("Start reloading ...");
            this.loadConfig();
            this.saveConfig();
            this.logger.info("Reloading complete.");
        }
        catch (IOException e)
        {
            throw Throwables.propagate(e);
        }
    }

    @Listener
    public void onPreInitialization(GamePreInitializationEvent event)
    {
        DataManager dataManager = Sponge.getDataManager();
        dataManager.registerTranslator(VirtualChestInventory.class, new VirtualChestInventoryTranslator(this));
    }

    @Listener
    public void onAboutToStartServer(GameAboutToStartServerEvent event)
    {
        this.translation = new VirtualChestTranslation(this);
        this.virtualChestActions = new VirtualChestActions(this);
        this.virtualChestCommand = new VirtualChestCommand(this);
        this.dispatcher = new VirtualChestInventoryDispatcher(this);
    }

    @Listener
    public void onStartingServer(GameStartedServerEvent event)
    {
        Sponge.getCommandManager().register(this, this.virtualChestCommand.get(), "virtualchest", "vchest", "vc");
    }

    @Listener
    public void onStartedServer(GameStartedServerEvent event)
    {
        try
        {
            this.logger.info("Start loading config ...");
            this.loadConfig();
            this.saveConfig();
            this.logger.info("Loading config complete.");
        }
        catch (IOException e)
        {
            throw Throwables.propagate(e);
        }
    }

    public Logger getLogger()
    {
        return this.logger;
    }

    public VirtualChestTranslation getTranslation()
    {
        return this.translation;
    }

    public VirtualChestActions getVirtualChestActions()
    {
        return this.virtualChestActions;
    }

    public VirtualChestInventoryDispatcher getDispatcher()
    {
        return this.dispatcher;
    }
}
