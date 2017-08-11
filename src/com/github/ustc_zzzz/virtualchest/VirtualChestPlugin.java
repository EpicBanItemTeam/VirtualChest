package com.github.ustc_zzzz.virtualchest;

import com.github.ustc_zzzz.virtualchest.action.VirtualChestActions;
import com.github.ustc_zzzz.virtualchest.command.VirtualChestCommand;
import com.github.ustc_zzzz.virtualchest.command.VirtualChestCommandAliases;
import com.github.ustc_zzzz.virtualchest.economy.VirtualChestEconomyManager;
import com.github.ustc_zzzz.virtualchest.inventory.VirtualChestInventory;
import com.github.ustc_zzzz.virtualchest.inventory.VirtualChestInventoryBuilder;
import com.github.ustc_zzzz.virtualchest.inventory.VirtualChestInventoryDispatcher;
import com.github.ustc_zzzz.virtualchest.permission.VirtualChestPermissionManager;
import com.github.ustc_zzzz.virtualchest.placeholder.VirtualChestPlaceholderManager;
import com.github.ustc_zzzz.virtualchest.translation.VirtualChestTranslation;
import com.google.common.base.Charsets;
import com.google.common.base.Throwables;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.inject.Inject;
import ninja.leaping.configurate.commented.CommentedConfigurationNode;
import ninja.leaping.configurate.loader.ConfigurationLoader;
import org.slf4j.Logger;
import org.spongepowered.api.Sponge;
import org.spongepowered.api.command.CommandSource;
import org.spongepowered.api.config.ConfigDir;
import org.spongepowered.api.config.DefaultConfig;
import org.spongepowered.api.data.DataManager;
import org.spongepowered.api.data.persistence.InvalidDataException;
import org.spongepowered.api.entity.living.player.Player;
import org.spongepowered.api.event.Listener;
import org.spongepowered.api.event.Order;
import org.spongepowered.api.event.cause.Cause;
import org.spongepowered.api.event.filter.cause.First;
import org.spongepowered.api.event.game.GameReloadEvent;
import org.spongepowered.api.event.game.state.GamePostInitializationEvent;
import org.spongepowered.api.event.game.state.GamePreInitializationEvent;
import org.spongepowered.api.event.game.state.GameStartedServerEvent;
import org.spongepowered.api.event.item.inventory.InteractItemEvent;
import org.spongepowered.api.plugin.Dependency;
import org.spongepowered.api.plugin.Plugin;
import org.spongepowered.api.text.channel.MessageReceiver;
import org.spongepowered.plugin.meta.version.ComparableVersion;

import javax.net.ssl.HttpsURLConnection;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.util.Locale;
import java.util.Optional;

/**
 * @author ustc_zzzz
 */
@Plugin(id = VirtualChestPlugin.PLUGIN_ID, name = "VirtualChest", authors =
        {"ustc_zzzz"}, dependencies = @Dependency(id = "placeholderapi", optional = true),
        version = VirtualChestPlugin.VERSION, description = VirtualChestPlugin.DESCRIPTION)
public class VirtualChestPlugin
{
    public static final String PLUGIN_ID = "virtualchest";
    public static final String DESCRIPTION = "A sponge plugin providing virtual chest GUIs for menus.";
    public static final String VERSION = "@version@";

    public static final String API_URL = "https://api.github.com/repos/ustc-zzzz/VirtualChest/releases";
    public static final String GITHUB_URL = "https://github.com/ustc-zzzz/VirtualChest";
    public static final String WEBSITE_URL = "https://ore.spongepowered.org/zzzz/VirtualChest";

    public static final SimpleDateFormat RFC3339 = new SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH);
    public static final SimpleDateFormat ISO8601 = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.ENGLISH);


    @Inject
    private Logger logger;

    @Inject
    @ConfigDir(sharedRoot = false)
    private Path configDir;

    @Inject
    @DefaultConfig(sharedRoot = false)
    private ConfigurationLoader<CommentedConfigurationNode> config;

    private CommentedConfigurationNode rootConfigNode;

    private VirtualChestTranslation translation;

    private VirtualChestActions virtualChestActions;

    private VirtualChestCommand virtualChestCommand;

    private VirtualChestCommandAliases commandAliases;

    private VirtualChestEconomyManager economyManager;

    private VirtualChestInventoryDispatcher dispatcher;

    private VirtualChestPermissionManager permissionManager;

    private VirtualChestPlaceholderManager placeholderManager;

    private boolean doCheckUpdate = true;

    private void checkUpdate()
    {
        try
        {
            URL url = new URL(API_URL);
            HttpsURLConnection connection = (HttpsURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.getResponseCode();
            InputStreamReader reader = new InputStreamReader(connection.getInputStream(), Charsets.UTF_8);
            JsonObject jsonObject = new JsonParser().parse(reader).getAsJsonArray().get(0).getAsJsonObject();
            String version = jsonObject.get("tag_name").getAsString();
            if (version.startsWith("v"))
            {
                version = version.substring(1);
                String releaseName = jsonObject.get("name").getAsString();
                String releaseUrl = jsonObject.get("html_url").getAsString();
                String releaseDate = RFC3339.format(ISO8601.parse(jsonObject.get("published_at").getAsString()));
                if (new ComparableVersion(version).compareTo(new ComparableVersion(VERSION)) > Integer.MIN_VALUE)
                {
                    this.logger.warn("================================================================");
                    this.logger.warn("Found update: " + releaseName);
                    this.logger.warn("The update is released at: " + releaseDate);
                    this.logger.warn("You can get the latest version at: " + releaseUrl);
                    this.logger.warn("================================================================");
                }
            }
        }
        catch (Exception e)
        {
            // <strike>do not bother offline users</strike> maybe bothering them is a better choice
            this.logger.warn("Failed to check update", e);
        }
    }

    private void loadConfig() throws IOException
    {
        CommentedConfigurationNode root = config.load();
        this.doCheckUpdate = root.getNode(PLUGIN_ID, "check-update").getBoolean(true);

        this.commandAliases.loadConfig(root.getNode(PLUGIN_ID, "command-aliases"));
        this.dispatcher.loadConfig(root.getNode(PLUGIN_ID, "scan-dirs"));

        this.rootConfigNode = root;
    }

    private void saveConfig() throws IOException
    {
        CommentedConfigurationNode root = Optional.ofNullable(this.rootConfigNode).orElseGet(config::createEmptyNode);
        root.getNode(PLUGIN_ID, "check-update").setValue(this.doCheckUpdate);

        this.commandAliases.saveConfig(root.getNode(PLUGIN_ID, "command-aliases"));
        this.dispatcher.saveConfig(root.getNode(PLUGIN_ID, "scan-dirs"));

        config.save(root);
    }

    @Listener(order = Order.EARLY)
    public void onInteractItemPrimary(InteractItemEvent.Primary event, @First Player player)
    {
        String name = "";
        Optional<VirtualChestInventory> inventoryOptional = Optional.empty();
        for (String inventoryName : this.dispatcher.listInventories())
        {
            VirtualChestInventory inventory = this.dispatcher.getInventory(inventoryName).get();
            if (inventory.matchItemForOpeningWithPrimaryAction(event.getItemStack()))
            {
                if (player.hasPermission("virtualchest.open.self." + inventoryName))
                {
                    name = inventoryName;
                    inventoryOptional = Optional.of(inventory);
                    event.setCancelled(true);
                    break;
                }
            }
        }
        if (inventoryOptional.isPresent())
        {
            try
            {
                this.logger.debug("Player {} tries to create the GUI ({}) by primary action", player.getName(), name);
                player.openInventory(inventoryOptional.get().createInventory(player), Cause.source(this).build());
            }
            catch (InvalidDataException e)
            {
                this.logger.error("There is something wrong with the GUI configuration (" + name + ")", e);
            }
        }
    }

    @Listener(order = Order.EARLY)
    public void onInteractItemSecondary(InteractItemEvent.Secondary event, @First Player player)
    {
        String name = "";
        Optional<VirtualChestInventory> inventoryOptional = Optional.empty();
        for (String inventoryName : this.dispatcher.listInventories())
        {
            VirtualChestInventory inventory = this.dispatcher.getInventory(inventoryName).get();
            if (inventory.matchItemForOpeningWithSecondaryAction(event.getItemStack()))
            {
                if (player.hasPermission("virtualchest.open.self." + inventoryName))
                {
                    name = inventoryName;
                    inventoryOptional = Optional.of(inventory);
                    event.setCancelled(true);
                    break;
                }
            }
        }
        if (inventoryOptional.isPresent())
        {
            try
            {
                this.logger.debug("Player {} tries to create the GUI ({}) by secondary action", player.getName(), name);
                player.openInventory(inventoryOptional.get().createInventory(player), Cause.source(this).build());
            }
            catch (InvalidDataException e)
            {
                this.logger.error("There is something wrong with the GUI configuration (" + name + ")", e);
            }
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
            throw Throwables.propagate(e);
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
        this.translation = new VirtualChestTranslation(this);
        this.virtualChestActions = new VirtualChestActions(this);
        this.virtualChestCommand = new VirtualChestCommand(this);
        this.commandAliases = new VirtualChestCommandAliases(this);
        this.economyManager = new VirtualChestEconomyManager(this);
        this.dispatcher = new VirtualChestInventoryDispatcher(this);
        this.permissionManager = new VirtualChestPermissionManager(this);
        this.placeholderManager = new VirtualChestPlaceholderManager(this);
    }

    @Listener
    public void onStartingServer(GameStartedServerEvent event)
    {
        this.virtualChestCommand.init();
        this.economyManager.init();
        this.permissionManager.init();
        this.virtualChestActions.init();
        this.placeholderManager.init();
    }

    @Listener
    public void onStartedServer(GameStartedServerEvent event)
    {
        try
        {
            this.logger.info("Start loading config ...");
            this.loadConfig();
            if (this.doCheckUpdate)
            {
                new Thread(this::checkUpdate).start();
            }
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

    public VirtualChestEconomyManager getEconomyManager()
    {
        return this.economyManager;
    }

    public VirtualChestInventoryDispatcher getDispatcher()
    {
        return this.dispatcher;
    }

    public VirtualChestPermissionManager getPermissionManager()
    {
        return this.permissionManager;
    }

    public VirtualChestPlaceholderManager getPlaceholderManager()
    {
        return this.placeholderManager;
    }
}
