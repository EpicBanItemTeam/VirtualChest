package com.github.ustc_zzzz.virtualchest.command;

import com.github.ustc_zzzz.virtualchest.VirtualChestPlugin;
import com.github.ustc_zzzz.virtualchest.inventory.VirtualChestInventoryDispatcher;
import com.github.ustc_zzzz.virtualchest.translation.VirtualChestTranslation;
import com.google.common.collect.ImmutableList;
import org.spongepowered.api.command.CommandSource;
import org.spongepowered.api.command.args.ArgumentParseException;
import org.spongepowered.api.command.args.CommandArgs;
import org.spongepowered.api.command.args.CommandContext;
import org.spongepowered.api.command.args.CommandElement;
import org.spongepowered.api.entity.living.player.Player;
import org.spongepowered.api.text.Text;
import org.spongepowered.api.util.StartsWithPredicate;
import org.spongepowered.api.util.annotation.NonnullByDefault;

import javax.annotation.Nullable;
import java.util.List;
import java.util.function.Predicate;

/**
 * @author ustc_zzzz
 */
@NonnullByDefault
public class VirtualChestInventoryCommandElement extends CommandElement
{
    private final VirtualChestTranslation translation;
    private final VirtualChestInventoryDispatcher dispatcher;

    public VirtualChestInventoryCommandElement(VirtualChestPlugin plugin, Text key)
    {
        super(key);
        this.dispatcher = plugin.getDispatcher();
        this.translation = plugin.getTranslation();
    }

    @Nullable
    @Override
    protected String parseValue(CommandSource src, CommandArgs args) throws ArgumentParseException
    {
        String name = args.next();
        if (this.dispatcher.ids().contains(name) && this.hasPermission(src, name))
        {
            return name;
        }
        throw args.createError(this.translation.take("virtualchest.open.notExists", name));
    }

    @Override
    public List<String> complete(CommandSource src, CommandArgs args, CommandContext context)
    {
        String prefix = args.nextIfPresent().orElse("");
        Predicate<String> predicate = new StartsWithPredicate(prefix).and(n -> this.hasPermission(src, n));
        return ImmutableList.copyOf(this.dispatcher.ids().stream().filter(predicate).iterator());
    }

    private boolean hasPermission(CommandSource source, String inventoryName)
    {
        return !(source instanceof Player) || this.dispatcher.hasPermission((Player) source, inventoryName);
    }
}
