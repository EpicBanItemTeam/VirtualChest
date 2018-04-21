package com.github.ustc_zzzz.virtualchest.command;

import com.github.ustc_zzzz.virtualchest.VirtualChestPlugin;
import com.github.ustc_zzzz.virtualchest.inventory.VirtualChestInventory;
import com.github.ustc_zzzz.virtualchest.inventory.VirtualChestInventoryDispatcher;
import com.github.ustc_zzzz.virtualchest.translation.VirtualChestTranslation;
import com.github.ustc_zzzz.virtualchest.unsafe.SpongeUnimplemented;
import com.google.common.base.Throwables;
import org.slf4j.Logger;
import org.spongepowered.api.Sponge;
import org.spongepowered.api.command.*;
import org.spongepowered.api.command.args.CommandContext;
import org.spongepowered.api.command.args.GenericArguments;
import org.spongepowered.api.command.spec.CommandSpec;
import org.spongepowered.api.data.persistence.InvalidDataException;
import org.spongepowered.api.entity.living.player.Player;
import org.spongepowered.api.scheduler.Scheduler;
import org.spongepowered.api.scheduler.Task;
import org.spongepowered.api.text.Text;
import org.spongepowered.api.text.action.TextActions;
import org.spongepowered.api.text.format.TextColors;
import org.spongepowered.api.text.format.TextStyles;
import org.spongepowered.api.util.GuavaCollectors;
import org.spongepowered.api.util.Tuple;
import org.spongepowered.api.util.annotation.NonnullByDefault;

import java.net.MalformedURLException;
import java.net.URL;
import java.text.ParseException;
import java.util.Collection;
import java.util.Date;
import java.util.Set;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * @author ustc_zzzz
 */
@NonnullByDefault
public class VirtualChestCommandManager implements Supplier<CommandCallable>
{
    private static final String VERSION = VirtualChestPlugin.VERSION;
    private static final String SUBTITLE = VirtualChestPlugin.DESCRIPTION;
    private static final String GITHUB_URL = VirtualChestPlugin.GITHUB_URL;
    private static final String WEBSITE_URL = VirtualChestPlugin.WEBSITE_URL;

    private final Logger logger;
    private final VirtualChestPlugin plugin;
    private final VirtualChestTranslation translation;

    private final CommandCallable reloadCommand;
    private final CommandCallable listCommand;
    private final CommandCallable openCommand;
    private final CommandCallable versionCommand;

    public VirtualChestCommandManager(VirtualChestPlugin plugin)
    {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
        this.translation = plugin.getTranslation();

        this.reloadCommand = CommandSpec.builder()
                .description(this.translation.take("virtualchest.reload.description"))
                .arguments(GenericArguments.optional(
                        GenericArguments.literal(Text.of("extract-examples"), "extract-examples")))
                .executor(this::processReloadCommand).build();

        this.listCommand = CommandSpec.builder()
                .description(this.translation.take("virtualchest.list.description"))
                .arguments(GenericArguments.none())
                .executor(this::processListCommand).build();

        this.openCommand = CommandSpec.builder()
                .description(this.translation.take("virtualchest.open.description"))
                .arguments(GenericArguments.seq(
                        new VirtualChestInventoryCommandElement(this.plugin, Text.of("inventory")),
                        GenericArguments.playerOrSource(Text.of("player"))))
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
            Date releaseDate = VirtualChestPlugin.RFC3339.parse("@release_date@");
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
        // noinspection ConstantConditions
        Tuple<VirtualChestInventory, String> t = args.<Tuple<VirtualChestInventory, String>>getOne("inventory").get();
        Collection<Player> players = args.getAll("player");
        Scheduler spongeScheduler = Sponge.getScheduler();
        VirtualChestInventory inventory = t.getFirst();
        String inventoryName = t.getSecond();
        for (Player player : players)
        {
            if (player.equals(source))
            {
                if (!source.hasPermission("virtualchest.open.self." + inventoryName))
                {
                    String errorKey = "virtualchest.open.self.noPermission";
                    throw new CommandException(this.translation.take(errorKey, inventoryName));
                }
            }
            else if (source instanceof Player)
            {
                if (!source.hasPermission("virtualchest.open.others." + inventoryName))
                {
                    String errorKey = "virtualchest.open.others.noPermission";
                    throw new CommandException(this.translation.take(errorKey, inventoryName, player.getName()));
                }
            }
            Task.Builder builder = spongeScheduler.createTaskBuilder().name("VirtualChestCommandManager");
            builder.execute(task -> this.openInventory(inventory, inventoryName, player)).submit(this.plugin);
        }
        return CommandResult.success();
    }

    private CommandResult processListCommand(CommandSource source, CommandContext args) throws CommandException
    {
        VirtualChestInventoryDispatcher dispatcher = this.plugin.getDispatcher();
        if (source instanceof Player && !source.hasPermission("virtualchest.list"))
        {
            Text error = translation.take("virtualchest.list.noPermission", source.getName());
            throw new CommandException(error);
        }
        Set<String> inventories = dispatcher.listInventories();
        if (source instanceof Player)
        {
            Predicate<String> inventoryNamePredict = name -> dispatcher.hasPermission((Player) source, name);
            inventories = inventories.stream().filter(inventoryNamePredict).collect(GuavaCollectors.toImmutableSet());
        }
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
            this.plugin.onReload(() -> SpongeUnimplemented.createCause(source));
            return CommandResult.success();
        }
    }

    private void openInventory(VirtualChestInventory inventory, String name, Player player)
    {
        this.logger.debug("Player {} tries to create the GUI ({}) by a command", player.getName(), name);
        try
        {
            SpongeUnimplemented.openInventory(player, inventory.createInventory(player, name), this);
        }
        catch (InvalidDataException e)
        {
            this.logger.error("There is something wrong with the GUI configuration (" + name + ")", e);
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
