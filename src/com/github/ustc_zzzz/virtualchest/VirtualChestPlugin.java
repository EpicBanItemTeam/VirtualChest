package com.github.ustc_zzzz.virtualchest;

import com.github.ustc_zzzz.virtualchest.action.VirtualChestActionIntervalManager;
import com.github.ustc_zzzz.virtualchest.action.VirtualChestActions;
import com.github.ustc_zzzz.virtualchest.command.VirtualChestCommandAliases;
import com.github.ustc_zzzz.virtualchest.command.VirtualChestCommandManager;
import com.github.ustc_zzzz.virtualchest.economy.VirtualChestEconomyManager;
import com.github.ustc_zzzz.virtualchest.inventory.VirtualChestInventory;
import com.github.ustc_zzzz.virtualchest.inventory.VirtualChestInventoryBuilder;
import com.github.ustc_zzzz.virtualchest.inventory.VirtualChestInventoryDispatcher;
import com.github.ustc_zzzz.virtualchest.permission.VirtualChestPermissionManager;
import com.github.ustc_zzzz.virtualchest.placeholder.VirtualChestPlaceholderManager;
import com.github.ustc_zzzz.virtualchest.record.VirtualChestRecordManager;
import com.github.ustc_zzzz.virtualchest.script.VirtualChestJavaScriptManager;
import com.github.ustc_zzzz.virtualchest.translation.VirtualChestTranslation;
import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;
import de.randombyte.byteitems.api.ByteItemsService;
import ninja.leaping.configurate.commented.CommentedConfigurationNode;
import ninja.leaping.configurate.loader.ConfigurationLoader;
import org.bstats.sponge.Metrics;
import org.slf4j.Logger;
import org.spongepowered.api.Platform;
import org.spongepowered.api.Server;
import org.spongepowered.api.Sponge;
import org.spongepowered.api.command.CommandSource;
import org.spongepowered.api.config.ConfigDir;
import org.spongepowered.api.config.DefaultConfig;
import org.spongepowered.api.data.DataManager;
import org.spongepowered.api.entity.living.player.Player;
import org.spongepowered.api.event.Listener;
import org.spongepowered.api.event.Order;
import org.spongepowered.api.event.filter.cause.First;
import org.spongepowered.api.event.game.GameReloadEvent;
import org.spongepowered.api.event.game.state.GamePostInitializationEvent;
import org.spongepowered.api.event.game.state.GamePreInitializationEvent;
import org.spongepowered.api.event.game.state.GameStartedServerEvent;
import org.spongepowered.api.event.game.state.GameStartingServerEvent;
import org.spongepowered.api.event.item.inventory.InteractItemEvent;
import org.spongepowered.api.event.network.ClientConnectionEvent;
import org.spongepowered.api.plugin.Dependency;
import org.spongepowered.api.plugin.Plugin;
import org.spongepowered.api.plugin.PluginContainer;
import org.spongepowered.api.profile.GameProfile;
import org.spongepowered.api.text.channel.MessageReceiver;

import javax.annotation.Nullable;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Optional;

/**
 * @author ustc_zzzz
 */
@Plugin(name = "VirtualChest", id = VirtualChestPlugin.PLUGIN_ID,
        dependencies = {
                @Dependency(id = "spongeapi"),
                @Dependency(id = "placeholderapi", version = "[4.0,)"),
                @Dependency(id = "byte-items", version = "[2.3,)", optional = true)
        },
        authors = {"ustc_zzzz"}, version = VirtualChestPlugin.VERSION, description = VirtualChestPlugin.DESCRIPTION)
public class VirtualChestPlugin
{
    public static final String VERSION = "@version@";
    public static final String PLUGIN_ID = "virtualchest";
    public static final String DESCRIPTION = "A sponge plugin providing virtual chest GUIs for menus.";

    public static final String API_URL = "https://api.github.com/repos/ustc-zzzz/VirtualChest/releases";
    public static final String GITHUB_URL = "https://github.com/ustc-zzzz/VirtualChest";
    public static final String WEBSITE_URL = "https://ore.spongepowered.org/zzzz/VirtualChest";

    @Inject
    private Logger logger;

    @Inject
    private Metrics metrics;

    @Inject
    @ConfigDir(sharedRoot = false)
    private Path configDir;

    @Inject
    @DefaultConfig(sharedRoot = false)
    private ConfigurationLoader<CommentedConfigurationNode> config;

    private CommentedConfigurationNode rootConfigNode;

    private VirtualChestPluginUpdate update;

    private VirtualChestTranslation translation;

    private VirtualChestRecordManager recordManager;

    private VirtualChestActions virtualChestActions;

    private VirtualChestCommandManager virtualChestCommandManager;

    private VirtualChestCommandAliases commandAliases;

    private VirtualChestEconomyManager economyManager;

    private VirtualChestInventoryDispatcher dispatcher;

    private VirtualChestJavaScriptManager scriptManager;

    private VirtualChestPermissionManager permissionManager;

    private VirtualChestPlaceholderManager placeholderManager;

    private VirtualChestActionIntervalManager actionIntervalManager;

    @Nullable
    private Object byteItemsService = null;

    private boolean doCheckUpdate = true;

    private void loadConfig() throws IOException
    {
        CommentedConfigurationNode root = config.load();

        this.update.loadConfig(root.getNode(PLUGIN_ID, "check-update"));
        this.recordManager.loadConfig(root.getNode(PLUGIN_ID, "recording"));
        this.commandAliases.loadConfig(root.getNode(PLUGIN_ID, "command-aliases"));
        this.dispatcher.loadConfig(root.getNode(PLUGIN_ID, "scan-dirs"));
        this.actionIntervalManager.loadConfig(root.getNode(PLUGIN_ID, "acceptable-action-interval-tick"));

        this.rootConfigNode = root;
    }

    private void saveConfig() throws IOException
    {
        CommentedConfigurationNode root = Optional.ofNullable(this.rootConfigNode).orElseGet(config::createEmptyNode);

        this.update.saveConfig(root.getNode(PLUGIN_ID, "check-update"));
        this.recordManager.saveConfig(root.getNode(PLUGIN_ID, "recording"));
        this.commandAliases.saveConfig(root.getNode(PLUGIN_ID, "command-aliases"));
        this.dispatcher.saveConfig(root.getNode(PLUGIN_ID, "scan-dirs"));
        this.actionIntervalManager.saveConfig(root.getNode(PLUGIN_ID, "acceptable-action-interval-tick"));

        config.save(root);
    }

    @Listener(order = Order.EARLY)
    public void onInteractItemPrimary(InteractItemEvent.Primary event, @First Player player)
    {
        String name = "";
        for (String inventoryName : this.dispatcher.ids())
        {
            if (player.hasPermission("virtualchest.open.self." + inventoryName))
            {
                if (this.dispatcher.isInventoryMatchingPrimaryAction(inventoryName, event.getItemStack()))
                {
                    event.setCancelled(true);
                    name = inventoryName;
                    break;
                }
            }
        }
        if (!name.isEmpty())
        {
            this.logger.debug("Player {} tries to create the GUI ({}) by primary action", player.getName(), name);
            this.dispatcher.open(name, player);
        }
    }

    @Listener(order = Order.EARLY)
    public void onInteractItemSecondary(InteractItemEvent.Secondary event, @First Player player)
    {
        String name = "";
        for (String inventoryName : this.dispatcher.ids())
        {
            if (player.hasPermission("virtualchest.open.self." + inventoryName))
            {
                if (this.dispatcher.isInventoryMatchingSecondaryAction(inventoryName, event.getItemStack()))
                {
                    event.setCancelled(true);
                    name = inventoryName;
                    break;
                }
            }
        }
        if (!name.isEmpty())
        {
            this.logger.debug("Player {} tries to create the GUI ({}) by secondary action", player.getName(), name);
            this.dispatcher.open(name, player);
        }
    }

    @Listener
    public void onClientConnectionJoin(ClientConnectionEvent.Join event)
    {
        Server server = Sponge.getServer();
        if (server.getOnlineMode())
        {
            // prefetch the profile when a player joins the server
            // TODO: maybe we should also prefetch player profiles in offline mode
            GameProfile profile = event.getTargetEntity().getProfile();
            server.getGameProfileManager().fill(profile).thenRun(() ->
            {
                String message = "Successfully loaded the game profile for {} (player {})";
                this.logger.debug(message, profile.getUniqueId(), profile.getName().orElse("null"));
            });
        }
    }

    @Listener
    public void onReload(GameReloadEvent event)
    {
        try
        {
            MessageReceiver src = event.getCause().first(CommandSource.class).orElse(Sponge.getServer().getConsole());
            src.sendMessage(this.translation.take("virtualchest.reload.start"));
            this.loadConfig();
            this.saveConfig();
            src.sendMessage(this.translation.take("virtualchest.reload.finish"));
        }
        catch (IOException e)
        {
            throw new RuntimeException(e);
        }
    }

    @Listener
    public void onPreInitialization(GamePreInitializationEvent event)
    {
        DataManager dataManager = Sponge.getDataManager();
        dataManager.registerBuilder(VirtualChestInventory.class, new VirtualChestInventoryBuilder(this));
    }

    @Listener
    public void onPostInitialization(GamePostInitializationEvent event)
    {
        this.update = new VirtualChestPluginUpdate(this);
        this.translation = new VirtualChestTranslation(this);
        this.recordManager = new VirtualChestRecordManager(this);
        this.virtualChestActions = new VirtualChestActions(this);
        this.commandAliases = new VirtualChestCommandAliases(this);
        this.economyManager = new VirtualChestEconomyManager(this);
        this.dispatcher = new VirtualChestInventoryDispatcher(this);
        this.scriptManager = new VirtualChestJavaScriptManager(this);
        this.permissionManager = new VirtualChestPermissionManager(this);
        this.placeholderManager = new VirtualChestPlaceholderManager(this);
        this.virtualChestCommandManager = new VirtualChestCommandManager(this);
        this.actionIntervalManager = new VirtualChestActionIntervalManager(this);

        if (Sponge.getPluginManager().getPlugin("byte-items").isPresent())
        {
            byteItemsService = Sponge.getServiceManager().provide(ByteItemsService.class).get();
        }
    }

    @Listener
    public void onStartingServer(GameStartingServerEvent event)
    {
        this.recordManager.init();
        this.virtualChestCommandManager.init();
        this.economyManager.init();
        this.permissionManager.init();
        this.virtualChestActions.init();
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
            throw new RuntimeException(e);
        }
        this.addMetricsInformation();
    }

    private void addMetricsInformation()
    {
        PluginContainer p = Sponge.getPlatform().getContainer(Platform.Component.IMPLEMENTATION);
        this.metrics.addCustomChart(
                new Metrics.SingleLineChart("onlineInventories",
                        () -> this.dispatcher.ids().size()));
        this.metrics.addCustomChart(
                new Metrics.AdvancedPie("placeholderapiVersion",
                        () -> ImmutableMap.of(this.placeholderManager.getPlaceholderAPIVersion(), 1)));
        this.metrics.addCustomChart(
                new Metrics.DrilldownPie("platformImplementation",
                        () -> ImmutableMap.of(p.getName(), ImmutableMap.of(p.getVersion().orElse("unknown"), 1))));
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

    public VirtualChestRecordManager getRecordManager()
    {
        return this.recordManager;
    }

    public VirtualChestActions getVirtualChestActions()
    {
        return this.virtualChestActions;
    }

    public VirtualChestEconomyManager getEconomyManager()
    {
        return this.economyManager;
    }

    public VirtualChestInventoryDispatcher getDispatcher()
    {
        return this.dispatcher;
    }

    public VirtualChestJavaScriptManager getScriptManager()
    {
        return this.scriptManager;
    }

    public VirtualChestPermissionManager getPermissionManager()
    {
        return this.permissionManager;
    }

    public VirtualChestPlaceholderManager getPlaceholderManager()
    {
        return this.placeholderManager;
    }

    public VirtualChestActionIntervalManager getActionIntervalManager()
    {
        return this.actionIntervalManager;
    }

    @Nullable
    public Object getByteItemsService()
    {
        return byteItemsService;
    }
}
