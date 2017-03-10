package com.github.ustc_zzzz.virtualchest;

import com.github.ustc_zzzz.virtualchest.action.VirtualChestActions;
import com.github.ustc_zzzz.virtualchest.command.VirtualChestCommand;
import com.github.ustc_zzzz.virtualchest.inventory.VirtualChestInventory;
import com.github.ustc_zzzz.virtualchest.inventory.VirtualChestInventoryDispatcher;
import com.github.ustc_zzzz.virtualchest.inventory.VirtualChestInventoryTranslator;
import com.github.ustc_zzzz.virtualchest.placeholder.VirtualChestPlaceholderParser;
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
import org.spongepowered.plugin.meta.version.ComparableVersion;

import javax.net.ssl.HttpsURLConnection;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.file.Path;
import java.util.Optional;

/**
 * @author ustc_zzzz
 */
@Plugin(id = VirtualChestPlugin.PLUGIN_ID, name = "VirtualChest", authors =
        {"ustc_zzzz"}, version = VirtualChestPlugin.VERSION, description = VirtualChestPlugin.DESCRIPTION)
public class VirtualChestPlugin
{
    public static final String PLUGIN_ID = "virtualchest";
    public static final String DESCRIPTION = "A plugin providing virtual chests";
    public static final String VERSION = "@version@";

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

    private VirtualChestInventoryDispatcher dispatcher;

    private VirtualChestPlaceholderParser placeholderParser;

    private boolean doCheckUpdate = true;

    private void checkUpdate()
    {
        try
        {
            URL url = new URL("https://api.github.com/repos/ustc-zzzz/VirtualChest/releases");
            HttpsURLConnection connection = (HttpsURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.getResponseCode();
            InputStreamReader reader = new InputStreamReader(connection.getInputStream(), Charsets.UTF_8);
            JsonObject jsonObject = new JsonParser().parse(reader).getAsJsonArray().get(0).getAsJsonObject();
            String version = jsonObject.get("tag_name").getAsString();
            if (version.startsWith("v"))
            {
                version = version.substring(1);
                String releaseUrl = jsonObject.get("html_url").getAsString();
                String releaseName = jsonObject.get("name").getAsString();
                if (new ComparableVersion(version).compareTo(new ComparableVersion(VERSION)) > 0)
                {
                    this.logger.warn("Found update: " + releaseName);
                    this.logger.warn("You can get the latest version at: " + releaseUrl);
                }
            }
        }
        catch (Exception ignored)
        {
            // do not bother offline users
        }
    }

    private void loadConfig() throws IOException
    {
        CommentedConfigurationNode root = config.load();
        this.doCheckUpdate = root.getNode(PLUGIN_ID, "check-update").getBoolean(true);

        this.dispatcher.loadConfig(root.getNode(PLUGIN_ID, "scan-dirs"));
        this.placeholderParser.loadConfig(root.getNode(PLUGIN_ID, "placeholders"));
        this.rootConfigNode = root;
    }

    private void saveConfig() throws IOException
    {
        CommentedConfigurationNode root = Optional.ofNullable(this.rootConfigNode).orElseGet(config::createEmptyNode);
        root.getNode(PLUGIN_ID, "check-update").setValue(this.doCheckUpdate);

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

    public VirtualChestInventoryDispatcher getDispatcher()
    {
        return this.dispatcher;
    }

    public VirtualChestPlaceholderParser getPlaceholderParser()
    {
        return this.placeholderParser;
    }
}
