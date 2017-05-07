package com.github.ustc_zzzz.virtualchest.placeholder;

import com.github.ustc_zzzz.virtualchest.VirtualChestPlugin;
import com.github.ustc_zzzz.virtualchest.translation.VirtualChestTranslation;
import ninja.leaping.configurate.commented.CommentedConfigurationNode;
import org.slf4j.Logger;
import org.spongepowered.api.Sponge;
import org.spongepowered.api.entity.living.player.Player;
import org.spongepowered.api.text.Text;
import org.spongepowered.api.text.serializer.TextSerializers;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * @author ustc_zzzz
 */
public class VirtualChestPlaceholderManager
{
    private final Logger logger;
    private final VirtualChestTranslation translation;
    private final Map<String, Function<Player, String>> placeholders = new HashMap<>();

    private String playerPlaceholder = "{player}";
    private String worldPlaceholder = "{world}";
    private String onlinePlaceholder = "{online}";
    private String maxPlayersPlaceholder = "{max_players}";

    private Parser parser;

    private boolean usePlaceholderAPI = true;
    private boolean enableReplacementsInActions = true;

    public VirtualChestPlaceholderManager(VirtualChestPlugin plugin)
    {
        this.logger = plugin.getLogger();
        this.translation = plugin.getTranslation();
    }

    public Text parseItemText(Player player, String text)
    {
        return TextSerializers.FORMATTING_CODE.deserialize(parser.replace(player, text, Function.identity()));
    }

    public String parseAction(Player player, String text)
    {
        return this.enableReplacementsInActions ? parser.replace(player, text, Function.identity()) : text;
    }

    private void pushPlaceholder(String placeholder, Function<Player, String> replacement)
    {
        placeholders.put(placeholder, replacement);
    }

    private String replacePlayer(Player player)
    {
        return player.getName();
    }

    private String replaceWorld(Player player)
    {
        return player.getWorld().getName();
    }

    private String replaceOnline(Player player)
    {
        return String.valueOf(Sponge.getServer().getOnlinePlayers().size());
    }

    private String replaceMaxPlayers(Player player)
    {
        return String.valueOf(Sponge.getServer().getMaxPlayers());
    }

    public void loadConfig(CommentedConfigurationNode node)
    {
        if (node.getNode("use-placeholderapi").isVirtual())
        {
            this.usePlaceholderAPI = node.getNode("enable-replacements-in-actions").isVirtual();
        }
        else
        {
            this.usePlaceholderAPI = node.getNode("use-placeholderapi").getBoolean(true);
        }

        if (this.usePlaceholderAPI)
        {
            try
            {
                parser = new PlaceholderAPIParser(Sponge.getServiceManager());
                return;
            }
            catch (Throwable t)
            {
                this.logger.info("Tries to load the PlaceholderAPI service ... ");
                this.logger.debug("Error occurred when the plugin tries to load the PlaceholderAPI service.", t);
                this.logger.warn("VirtualChest could not find the PlaceholderAPI service. ");
                this.logger.warn("Features related to PlaceholderAPI may not work normally. ");
                this.logger.warn("Maybe you should look for a PlaceholderAPI plugin and download it?");
            }
        }

        if (node.getNode("enable-replacements-in-actions").isVirtual())
        {
            parser = new UselessParser();
            return;
        }

        this.logger.info("Tries to use the placeholders provided by VirtualChest itself. ");
        this.logger.warn("The placeholders provided by VirtualChest itself are deprecated now. ");
        this.logger.warn("And this deprecated feature will be removed since VirtualChest v0.4.0. ");
        this.logger.warn("At that time VirtualChest will only use the PlaceholderAPI.");

        this.enableReplacementsInActions = node.getNode("enable-replacements-in-actions").getBoolean(true);

        this.playerPlaceholder = node.getNode("player").getString("{player}");
        this.worldPlaceholder = node.getNode("world").getString("{world}");
        this.onlinePlaceholder = node.getNode("online").getString("{online}");
        this.maxPlayersPlaceholder = node.getNode("max-players").getString("{max_players}");

        pushPlaceholder(this.playerPlaceholder, this::replacePlayer);
        pushPlaceholder(this.worldPlaceholder, this::replaceWorld);
        pushPlaceholder(this.onlinePlaceholder, this::replaceOnline);
        pushPlaceholder(this.maxPlayersPlaceholder, this::replaceMaxPlayers);

        parser = new BackportParser(this.placeholders);
    }

    public void saveConfig(CommentedConfigurationNode node)
    {
        String usePlaceholderAPIConfigComment = this.translation
                .take("virtualchest.config.placeholders.usePlaceholderAPI.comment").toPlain();
        if (this.usePlaceholderAPI || node.getNode("enable-replacements-in-actions").isVirtual())
        {
            node.getNode("use-placeholderapi").setValue(this.usePlaceholderAPI)
                    .setComment(node.getNode("use-placeholderapi").getComment().orElse(usePlaceholderAPIConfigComment));
            return;
        }

        usePlaceholderAPIConfigComment = usePlaceholderAPIConfigComment + '\n' + this.translation
                .take("virtualchest.config.placeholders.usePlaceholderAPI.comment.deprecated").toPlain();

        node.getNode("use-placeholderapi").setValue(false)
                .setComment(node.getNode("use-placeholderapi").getComment().orElse(usePlaceholderAPIConfigComment));

        node.getNode("enable-replacements-in-actions").setValue(this.enableReplacementsInActions)
                .setComment(node.getNode("enable-replacements-in-actions").getComment().orElse(this.translation
                        .take("virtualchest.config.placeholders.enableReplacementsInActions.comment").toPlain()));

        node.getNode("player").setValue(this.playerPlaceholder)
                .setComment(node.getNode("player").getComment().orElse(this.translation
                        .take("virtualchest.config.placeholders.player.comment").toPlain()));
        node.getNode("world").setValue(this.worldPlaceholder)
                .setComment(node.getNode("world").getComment().orElse(this.translation
                        .take("virtualchest.config.placeholders.world.comment").toPlain()));
        node.getNode("online").setValue(this.onlinePlaceholder)
                .setComment(node.getNode("online").getComment().orElse(this.translation
                        .take("virtualchest.config.placeholders.online.comment").toPlain()));
        node.getNode("max-players").setValue(this.maxPlayersPlaceholder)
                .setComment(node.getNode("max-players").getComment().orElse(this.translation
                        .take("virtualchest.config.placeholders.maxPlayers.comment").toPlain()));

        node.setComment(node.getComment().orElse(this.translation
                .take("virtualchest.config.placeholders.comment").toPlain()));
    }

    interface Parser
    {
        String replace(Player player, String text, Function<? super String, String> replacementTransformation);
    }
}
