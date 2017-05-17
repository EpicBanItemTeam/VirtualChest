package com.github.ustc_zzzz.virtualchest.command;

import com.github.ustc_zzzz.virtualchest.VirtualChestPlugin;
import com.github.ustc_zzzz.virtualchest.translation.VirtualChestTranslation;
import com.google.common.base.Throwables;
import org.spongepowered.api.Sponge;
import org.spongepowered.api.command.*;
import org.spongepowered.api.command.args.CommandContext;
import org.spongepowered.api.command.args.GenericArguments;
import org.spongepowered.api.command.spec.CommandSpec;
import org.spongepowered.api.entity.living.player.Player;
import org.spongepowered.api.event.cause.Cause;
import org.spongepowered.api.event.cause.NamedCause;
import org.spongepowered.api.item.inventory.Inventory;
import org.spongepowered.api.text.Text;
import org.spongepowered.api.text.action.TextActions;
import org.spongepowered.api.text.format.TextColors;
import org.spongepowered.api.text.format.TextStyles;
import org.spongepowered.api.util.annotation.NonnullByDefault;

import java.net.MalformedURLException;
import java.net.URL;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.function.Supplier;

/**
 * @author ustc_zzzz
 */
@NonnullByDefault
public class VirtualChestCommand implements Supplier<CommandCallable>
{
    private static final String VERSION = VirtualChestPlugin.VERSION;
    private static final String SUBTITLE = VirtualChestPlugin.DESCRIPTION;
    private static final String GITHUB_URL = VirtualChestPlugin.GITHUB_URL;
    private static final String WEBSITE_URL = VirtualChestPlugin.WEBSITE_URL;

    private final VirtualChestPlugin plugin;
    private final VirtualChestTranslation translation;

    private final CommandCallable reloadCommand;
    private final CommandCallable listCommand;
    private final CommandCallable openCommand;
    private final CommandCallable versionCommand;

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

        this.versionCommand = CommandSpec.builder()
                .description(this.translation.take("virtualchest.version.description"))
                .arguments(GenericArguments.none())
                .executor(this::processVersionCommand).build();
    }

    public void init()
    {
        CommandManager commandManager = Sponge.getCommandManager();
        commandManager.register(this.plugin, this.get(), "virtualchest", "vchest", "vc");
    }

    private CommandResult processVersionCommand(CommandSource source, CommandContext args) throws CommandException
    {
        source.sendMessage(Text.of("================================================================"));
        source.sendMessage(this.translation.take("virtualchest.version.description.title", VERSION));
        source.sendMessage(this.translation.take("virtualchest.version.description.subtitle", SUBTITLE));
        source.sendMessage(Text.of("================================================================"));
        try
        {
            // RFC 3339
            Date releaseDate = new SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH).parse("@release_date@");
            String gitCommitHash = "@git_hash@";

            source.sendMessage(this.translation
                    .take("virtualchest.version.description.line1", releaseDate));
            source.sendMessage(this.translation
                    .take("virtualchest.version.description.line2", gitCommitHash));

            Text urlWebsite = Text.builder(WEBSITE_URL)
                    .color(TextColors.GREEN).style(TextStyles.BOLD)
                    .onClick(TextActions.openUrl(new URL(WEBSITE_URL))).build();
            Text urlGitHub = Text.builder(GITHUB_URL)
                    .color(TextColors.GREEN).style(TextStyles.BOLD)
                    .onClick(TextActions.openUrl(new URL(GITHUB_URL))).build();

            source.sendMessage(Text.join(this.translation
                    .take("virtualchest.version.description.line3", ""), urlWebsite));
            source.sendMessage(Text.join(this.translation
                    .take("virtualchest.version.description.line4", ""), urlGitHub));

            source.sendMessage(Text.of("================================================================"));
        }
        catch (MalformedURLException | ParseException e)
        {
            Throwables.propagate(e);
        }
        return CommandResult.success();
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
            if (args.getOne("extract-examples").isPresent())
            {
                this.plugin.getDispatcher().releaseExample();
            }
            this.plugin.onReload(() -> Cause.of(NamedCause.source(source)));
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
                .child(this.versionCommand, "version", "v")
                .build();
    }
}
