package com.github.ustc_zzzz.virtualchest.command;

import com.github.ustc_zzzz.virtualchest.VirtualChestPlugin;
import com.github.ustc_zzzz.virtualchest.inventory.VirtualChestInventory;
import com.github.ustc_zzzz.virtualchest.inventory.VirtualChestInventoryDispatcher;
import com.github.ustc_zzzz.virtualchest.translation.VirtualChestTranslation;
import org.spongepowered.api.command.CommandSource;
import org.spongepowered.api.command.args.ArgumentParseException;
import org.spongepowered.api.command.args.CommandArgs;
import org.spongepowered.api.command.args.CommandContext;
import org.spongepowered.api.command.args.CommandElement;
import org.spongepowered.api.entity.living.player.Player;
import org.spongepowered.api.text.Text;
import org.spongepowered.api.util.GuavaCollectors;
import org.spongepowered.api.util.StartsWithPredicate;
import org.spongepowered.api.util.Tuple;
import org.spongepowered.api.util.annotation.NonnullByDefault;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Optional;
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
    protected Object parseValue(CommandSource src, CommandArgs args) throws ArgumentParseException
    {
        String name = args.next();
        Optional<VirtualChestInventory> inventoryOptional = this.dispatcher.getInventory(name);
        return new Tuple<>(inventoryOptional.filter(inventory -> this.hasPermission(src, name)).orElseThrow(() ->
        {
            Text error = this.translation.take("virtualchest.open.notExists", name);
            return args.createError(error);
        }), name);
    }

    @Override
    public List<String> complete(CommandSource src, CommandArgs args, CommandContext context)
    {
        String prefix = args.nextIfPresent().orElse("");
        Predicate<String> predicate = new StartsWithPredicate(prefix).and(name -> this.hasPermission(src, name));
        return this.dispatcher.listInventories().stream().filter(predicate).collect(GuavaCollectors.toImmutableList());
    }

    private boolean hasPermission(CommandSource source, String inventoryName)
    {
        return !(source instanceof Player) || this.dispatcher.hasPermission((Player) source, inventoryName);
    }
}
