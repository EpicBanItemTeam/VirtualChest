package com.github.ustc_zzzz.virtualchest.command;

import com.github.ustc_zzzz.virtualchest.VirtualChestPlugin;
import com.github.ustc_zzzz.virtualchest.translation.VirtualChestTranslation;
import org.spongepowered.api.command.CommandCallable;
import org.spongepowered.api.command.CommandException;
import org.spongepowered.api.command.CommandResult;
import org.spongepowered.api.command.CommandSource;
import org.spongepowered.api.command.args.CommandContext;
import org.spongepowered.api.command.args.GenericArguments;
import org.spongepowered.api.command.spec.CommandSpec;
import org.spongepowered.api.entity.living.player.Player;
import org.spongepowered.api.event.cause.Cause;
import org.spongepowered.api.item.inventory.Inventory;
import org.spongepowered.api.text.Text;

import java.util.Collection;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;

/**
 * @author ustc_zzzz
 */
public class VirtualChestCommand implements Supplier<CommandCallable>
{
    private final VirtualChestPlugin plugin;
    private final VirtualChestTranslation translation;

    private final CommandCallable reloadCommand;
    private final CommandCallable listCommand;
    private final CommandCallable openCommand;

    public VirtualChestCommand(VirtualChestPlugin plugin)
    {
        this.plugin = plugin;
        this.translation = plugin.getTranslation();

        this.reloadCommand = CommandSpec.builder()
                .description(this.translation.take("virtualchest.reload.description"))
                .arguments(GenericArguments.optional(GenericArguments.literal(Text.of("extract-examples"), "extract-examples")))
                .executor(this::processReloadCommand).build();

        this.listCommand = CommandSpec.builder()
                .description(this.translation.take("virtualchest.list.description"))
                .arguments(GenericArguments.none())
                .executor(this::processListCommand).build();

        this.openCommand = CommandSpec.builder()
                .description(this.translation.take("virtualchest.open.description"))
                .arguments(GenericArguments.string(Text.of("name")), GenericArguments.playerOrSource(Text.of("player")))
                .executor(this::processOpenCommand).build();
    }

    private CommandResult processOpenCommand(CommandSource source, CommandContext args) throws CommandException
    {
        String name = args.<String>getOne("name").get();
        Collection<Player> players = args.getAll("player");
        for (Player player : players)
        {
            if (player.equals(source))
            {
                if (source.hasPermission("virtualchest.open.self." + name))
                {
                    this.openInventory(name, player);
                }
                else
                {
                    Text error = translation.take("virtualchest.open.self.noPermission", name);
                    throw new CommandException(error);
                }
            }
            else if (source instanceof Player)
            {
                if (source.hasPermission("virtualchest.open.others." + name))
                {
                    this.openInventory(name, player);
                }
                else
                {
                    Text error = translation.take("virtualchest.open.others.noPermission", name, player.getName());
                    throw new CommandException(error);
                }
            }
            else
            {
                this.openInventory(name, player);
            }
        }
        return CommandResult.success();
    }

    private CommandResult processListCommand(CommandSource source, CommandContext args) throws CommandException
    {
        if (source instanceof Player && !source.hasPermission("virtualchest.list"))
        {
            Text error = translation.take("virtualchest.list.noPermission", source.getName());
            throw new CommandException(error);
        }
        Set<String> inventories = this.plugin.getDispatcher().listInventories();
        source.sendMessage(translation.take("virtualchest.list.overview", inventories.size()));
        source.sendMessage(Text.of(String.join(", ", inventories)));
        return CommandResult.success();
    }

    private CommandResult processReloadCommand(CommandSource source, CommandContext args) throws CommandException
    {
        if (!source.hasPermission("virtualchest.reload"))
        {
            return CommandResult.empty();
        }
        else
        {
            this.plugin.onReload(Cause.source(this.plugin)::build);
            if (args.getOne("extract-examples").isPresent())
            {
                this.plugin.getDispatcher().releaseExample();
            }
            return CommandResult.success();
        }
    }

    private void openInventory(String name, Player player) throws CommandException
    {
        Optional<Inventory> inventoryOptional = plugin.getDispatcher().createInventory(name, player);
        if (inventoryOptional.isPresent())
        {
            player.openInventory(inventoryOptional.get(), Cause.source(plugin).build());
        }
        else
        {
            Text error = translation.take("virtualchest.open.notExists", name);
            throw new CommandException(error);
        }
    }

    @Override
    public CommandCallable get()
    {
        return CommandSpec.builder()
                .child(this.reloadCommand, "reload", "r")
                .child(this.listCommand, "list", "l")
                .child(this.openCommand, "open", "o")
                .build();
    }
}
