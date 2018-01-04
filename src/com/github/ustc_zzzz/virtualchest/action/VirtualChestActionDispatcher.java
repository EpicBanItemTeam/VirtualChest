package com.github.ustc_zzzz.virtualchest.action;

import com.github.ustc_zzzz.virtualchest.VirtualChestPlugin;
import com.github.ustc_zzzz.virtualchest.inventory.util.VirtualChestItemTemplateWithCount;
import com.github.ustc_zzzz.virtualchest.unsafe.SpongeUnimplemented;
import com.google.common.collect.ImmutableList;
import org.spongepowered.api.data.DataContainer;
import org.spongepowered.api.data.DataQuery;
import org.spongepowered.api.data.DataView;
import org.spongepowered.api.entity.living.player.Player;
import org.spongepowered.api.item.inventory.ItemStackSnapshot;
import org.spongepowered.api.util.Tristate;

import java.util.LinkedList;
import java.util.List;
import java.util.Optional;

/**
 * @author ustc_zzzz
 */
public class VirtualChestActionDispatcher
{
    private static final DataQuery KEEP_INVENTORY_OPEN = DataQuery.of("KeepInventoryOpen");
    private static final DataQuery HANDHELD_ITEM = DataQuery.of("HandheldItem");
    private static final DataQuery COMMAND = DataQuery.of("Command");

    private static final char SEQUENCE_SPLITTER = ';';

    private final ImmutableList<VirtualChestItemTemplateWithCount> handheldItem;
    private final ImmutableList<Boolean> keepInventoryOpen;
    private final ImmutableList<List<String>> commands;

    private final ImmutableList<DataContainer> views;

    private final int size;

    public VirtualChestActionDispatcher(List<DataView> views)
    {
        this.size = views.size();

        ImmutableList.Builder<VirtualChestItemTemplateWithCount> handheldItemBuilder = ImmutableList.builder();
        ImmutableList.Builder<Boolean> keepInventoryOpenBuilder = ImmutableList.builder();
        ImmutableList.Builder<List<String>> commandsBuilder = ImmutableList.builder();

        ImmutableList.Builder<DataContainer> dataContainerBuilder = ImmutableList.builder();

        for (DataView view : views)
        {
            handheldItemBuilder.add(this.parseHandheldItem(view.getView(HANDHELD_ITEM)));
            commandsBuilder.add(parseCommand(view.getString(COMMAND).orElse("")));
            keepInventoryOpenBuilder.add(view.getBoolean(KEEP_INVENTORY_OPEN).orElse(false));

            dataContainerBuilder.add(view.copy());
        }

        this.commands = commandsBuilder.build();
        this.handheldItem = handheldItemBuilder.build();
        this.keepInventoryOpen = keepInventoryOpenBuilder.build();

        this.views = dataContainerBuilder.build();
    }

    public boolean runCommand(VirtualChestPlugin plugin, Player player, List<String> ignoredPermissions)
    {
        ItemStackSnapshot handheldItem = SpongeUnimplemented.getItemHeldByMouse(player);
        for (int i = 0; i < this.size; ++i)
        {
            VirtualChestItemTemplateWithCount itemTemplate = this.handheldItem.get(i);
            if (itemTemplate.matchItem(handheldItem))
            {
                plugin.getVirtualChestActions().submitCommands(player, this.commands.get(i), ignoredPermissions);
                return this.keepInventoryOpen.get(i);
            }
        }
        return true;
    }

    public Optional<?> getObjectForSerialization()
    {
        switch (this.size)
        {
        case 0:
            return Optional.empty();
        case 1:
            return Optional.of(this.views.iterator().next());
        default:
            return Optional.of(this.views);
        }
    }

    private VirtualChestItemTemplateWithCount parseHandheldItem(Optional<DataView> optional)
    {
        return optional.map(VirtualChestItemTemplateWithCount::new).orElseGet(VirtualChestItemTemplateWithCount::new);
    }

    public static List<String> parseCommand(String commandSequence)
    {
        StringBuilder stringBuilder = new StringBuilder();
        List<String> commands = new LinkedList<>();
        Tristate isCommandFinished = Tristate.TRUE;
        for (char c : commandSequence.toCharArray())
        {
            if (c != SEQUENCE_SPLITTER)
            {
                if (isCommandFinished == Tristate.UNDEFINED)
                {
                    commands.add(stringBuilder.toString());
                    stringBuilder.setLength(0);
                }
                if (isCommandFinished != Tristate.FALSE && Character.isWhitespace(c))
                {
                    isCommandFinished = Tristate.TRUE;
                    continue;
                }
            }
            else if (isCommandFinished != Tristate.UNDEFINED)
            {
                isCommandFinished = Tristate.UNDEFINED;
                continue;
            }
            isCommandFinished = Tristate.FALSE;
            stringBuilder.append(c);
        }
        commands.add(stringBuilder.toString());
        return commands;
    }
}
