package com.github.ustc_zzzz.virtualchest.placeholder;

import com.github.ustc_zzzz.virtualchest.VirtualChestPlugin;
import com.github.ustc_zzzz.virtualchest.translation.VirtualChestTranslation;
import ninja.leaping.configurate.commented.CommentedConfigurationNode;
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
public class VirtualChestPlaceholderParser
{
    private final VirtualChestTranslation translation;
    private final Map<String, Function<Player, String>> placeholders = new HashMap<>();

    private String playerPlaceholder = "{player}";
    private String worldPlaceholder = "{world}";
    private String onlinePlaceholder = "{online}";
    private String maxPlayersPlaceholder = "{max_players}";

    private boolean enableReplacementsInActions = true;

    public VirtualChestPlaceholderParser(VirtualChestPlugin plugin)
    {
        this.translation = plugin.getTranslation();
    }

    public Text parseItemText(Player player, String text)
    {
        for (Map.Entry<String, Function<Player, String>> entry : placeholders.entrySet())
        {
            text = text.replace(entry.getKey(), entry.getValue().apply(player));
        }
        return TextSerializers.FORMATTING_CODE.deserialize(text);
    }

    public String parseAction(Player player, String text)
    {
        if (this.enableReplacementsInActions)
        {
            for (Map.Entry<String, Function<Player, String>> entry : placeholders.entrySet())
            {
                text = text.replace(entry.getKey(), entry.getValue().apply(player));
            }
        }
        return text;
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
        this.enableReplacementsInActions = node.getNode("enable-replacements-in-actions").getBoolean(true);

        this.playerPlaceholder = node.getNode("player").getString("{player}");
        this.worldPlaceholder = node.getNode("world").getString("{world}");
        this.onlinePlaceholder = node.getNode("online").getString("{online}");
        this.maxPlayersPlaceholder = node.getNode("max-players").getString("{max_players}");

        pushPlaceholder(this.playerPlaceholder, this::replacePlayer);
        pushPlaceholder(this.worldPlaceholder, this::replaceWorld);
        pushPlaceholder(this.onlinePlaceholder, this::replaceOnline);
        pushPlaceholder(this.maxPlayersPlaceholder, this::replaceMaxPlayers);
    }

    public void saveConfig(CommentedConfigurationNode node)
    {
        node.getNode("enable-replacements-in-actions").setValue(this.enableReplacementsInActions)
                .setComment(node.getComment().orElse(this.translation
                        .take("virtualchest.config.placeholders.enableReplacementsInActions.comment").toPlain()));

        node.getNode("player").setValue(this.playerPlaceholder)
                .setComment(node.getComment().orElse(this.translation
                        .take("virtualchest.config.placeholders.player.comment").toPlain()));
        node.getNode("world").setValue(this.worldPlaceholder)
                .setComment(node.getComment().orElse(this.translation
                        .take("virtualchest.config.placeholders.world.comment").toPlain()));
        node.getNode("online").setValue(this.onlinePlaceholder)
                .setComment(node.getComment().orElse(this.translation
                        .take("virtualchest.config.placeholders.online.comment").toPlain()));
        node.getNode("max-players").setValue(this.maxPlayersPlaceholder)
                .setComment(node.getComment().orElse(this.translation
                        .take("virtualchest.config.placeholders.maxPlayers.comment").toPlain()));

        node.setComment(node.getComment().orElse(this.translation
                .take("virtualchest.config.placeholders.comment").toPlain()));
    }
}
