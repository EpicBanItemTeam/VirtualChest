package com.github.ustc_zzzz.virtualchest.placeholder;

import com.github.ustc_zzzz.virtualchest.VirtualChestPlugin;
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
    private final VirtualChestPlugin virtualChestPlugin;
    private final Map<String, Function<Player, String>> placeholders = new HashMap<>();

    public VirtualChestPlaceholderParser(VirtualChestPlugin plugin)
    {
        this.virtualChestPlugin = plugin;
    }

    public Text parse(Player player, String text)
    {
        for (Map.Entry<String, Function<Player, String>> entry : placeholders.entrySet())
        {
            text = text.replace(entry.getKey(), entry.getValue().apply(player));
        }
        return TextSerializers.FORMATTING_CODE.deserialize(text);
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
        pushPlaceholder(node.getNode("player").getString("{player}"), this::replacePlayer);
        pushPlaceholder(node.getNode("world").getString("{world}"), this::replaceWorld);
        pushPlaceholder(node.getNode("online").getString("{online}"), this::replaceOnline);
        pushPlaceholder(node.getNode("max-players").getString("{max_players}"), this::replaceMaxPlayers);
    }

    public void saveConfig(CommentedConfigurationNode node)
    {
        node.getNode("player").setValue("{player}");
        node.getNode("world").setValue("{world}");
        node.getNode("online").setValue("{online}");
        node.getNode("max-players").setValue("{max_players}");
    }
}
