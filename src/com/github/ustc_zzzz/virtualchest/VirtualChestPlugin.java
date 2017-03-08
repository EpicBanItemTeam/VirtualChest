package com.github.ustc_zzzz.virtualchest;

import com.github.ustc_zzzz.virtualchest.action.VirtualChestActions;
import com.github.ustc_zzzz.virtualchest.command.VirtualChestCommand;
import com.github.ustc_zzzz.virtualchest.inventory.VirtualChestInventory;
import com.github.ustc_zzzz.virtualchest.inventory.VirtualChestInventoryDispatcher;
import com.github.ustc_zzzz.virtualchest.inventory.VirtualChestInventoryTranslator;
import com.github.ustc_zzzz.virtualchest.placeholder.VirtualChestPlaceholderParser;
import com.github.ustc_zzzz.virtualchest.translation.VirtualChestTranslation;
import com.google.common.base.Throwables;
import com.google.inject.Inject;
import ninja.leaping.configurate.commented.CommentedConfigurationNode;
import ninja.leaping.configurate.loader.ConfigurationLoader;
import org.slf4j.Logger;
import org.spongepowered.api.Sponge;
import org.spongepowered.api.config.ConfigDir;
import org.spongepowered.api.config.DefaultConfig;
import org.spongepowered.api.data.DataManager;
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

import java.io.IOException;
import java.nio.file.Path;

/**
 * @author ustc_zzzz
 */
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

    private VirtualChestTranslation translation;

    private VirtualChestActions virtualChestActions;

    private VirtualChestCommand virtualChestCommand;

    private VirtualChestInventoryDispatcher dispatcher;

    private VirtualChestPlaceholderParser placeholderParser;

    private void loadConfig() throws IOException
    {
        CommentedConfigurationNode root = config.load();
        this.dispatcher.loadConfig(root.getNode(PLUGIN_ID, "scan-dirs"));
        this.placeholderParser.loadConfig(root.getNode(PLUGIN_ID, "placeholders"));
    }

    private void saveConfig() throws IOException
    {
        CommentedConfigurationNode root = config.createEmptyNode();
        this.dispatcher.saveConfig(root.getNode(PLUGIN_ID, "scan-dirs"));
        this.placeholderParser.saveConfig(root.getNode(PLUGIN_ID, "placeholders"));
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
        this.placeholderParser = new VirtualChestPlaceholderParser(this);
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

    public Path getConfigDir()
    {
        return this.configDir;
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

    public VirtualChestPlaceholderParser getPlaceholderParser()
    {
        return this.placeholderParser;
    }
}
