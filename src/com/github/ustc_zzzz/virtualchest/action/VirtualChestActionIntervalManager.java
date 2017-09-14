package com.github.ustc_zzzz.virtualchest.action;

import com.github.ustc_zzzz.virtualchest.VirtualChestPlugin;
import com.github.ustc_zzzz.virtualchest.translation.VirtualChestTranslation;
import ninja.leaping.configurate.commented.CommentedConfigurationNode;
import org.spongepowered.api.entity.living.player.Player;

import java.io.IOException;
import java.util.Map;
import java.util.OptionalInt;
import java.util.WeakHashMap;

/**
 * @author ustc_zzzz
 */
public class VirtualChestActionIntervalManager
{
    private int acceptableActionIntervalTick = 0;
    private final VirtualChestTranslation translation;
    private final Map<Player, Long> tickFromLastAction = new WeakHashMap<>();

    public VirtualChestActionIntervalManager(VirtualChestPlugin plugin)
    {
        this.translation = plugin.getTranslation();
    }

    public boolean allowAction(Player player, OptionalInt acceptableActionIntervalTickForInventory)
    {
        boolean denyAction = false;
        long now = player.getWorld().getProperties().getTotalTime();
        if (!player.hasPermission("virtualchest.bypass") && this.tickFromLastAction.containsKey(player))
        {
            int coolDown = acceptableActionIntervalTickForInventory.orElse(this.acceptableActionIntervalTick);
            long boundary = this.tickFromLastAction.get(player) + coolDown;
            denyAction = now < boundary;
        }
        if (denyAction)
        {
            return false;
        }
        this.tickFromLastAction.put(player, now);
        return true;
    }

    public void onClosingInventory(Player player)
    {
        this.tickFromLastAction.remove(player);
    }

    public void loadConfig(CommentedConfigurationNode node) throws IOException
    {
        this.acceptableActionIntervalTick = node.getInt(0);
    }

    public void saveConfig(CommentedConfigurationNode node) throws IOException
    {
        node.setValue(this.acceptableActionIntervalTick)
                .setComment(node.getComment().orElse(this.translation
                        .take("virtualchest.config.acceptableActionIntervalTick.comment").toPlain()));
    }
}
