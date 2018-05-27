package com.github.ustc_zzzz.virtualchest.action;

import com.github.ustc_zzzz.virtualchest.VirtualChestPlugin;
import com.github.ustc_zzzz.virtualchest.inventory.util.VirtualChestHandheldItem;
import com.github.ustc_zzzz.virtualchest.unsafe.SpongeUnimplemented;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import org.spongepowered.api.command.CommandResult;
import org.spongepowered.api.data.DataContainer;
import org.spongepowered.api.data.DataQuery;
import org.spongepowered.api.data.DataView;
import org.spongepowered.api.entity.living.player.Player;
import org.spongepowered.api.item.inventory.ItemStack;
import org.spongepowered.api.item.inventory.ItemStackSnapshot;
import org.spongepowered.api.item.inventory.Slot;
import org.spongepowered.api.util.Tristate;
import org.spongepowered.api.util.Tuple;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.IntStream;
import java.util.stream.Stream;

/**
 * @author ustc_zzzz
 */
public class VirtualChestActionDispatcher
{
    public static final DataQuery KEEP_INVENTORY_OPEN = DataQuery.of("KeepInventoryOpen");
    public static final DataQuery HANDHELD_ITEM = DataQuery.of("HandheldItem");
    public static final DataQuery COMMAND = DataQuery.of("Command");

    private static final char SEQUENCE_SPLITTER = ';';

    private final ImmutableList<VirtualChestHandheldItem> handheldItem;
    private final ImmutableList<Boolean> keepInventoryOpen;
    private final ImmutableList<List<String>> commands;

    private final ImmutableList<DataContainer> views;

    private final int size;

    public VirtualChestActionDispatcher(List<DataView> views)
    {
        this.size = views.size();

        ImmutableList.Builder<VirtualChestHandheldItem> handheldItemBuilder = ImmutableList.builder();
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

    public Tuple<Boolean, CompletableFuture<CommandResult>> runCommand(VirtualChestPlugin plugin,
                                                                       UUID actionUUID, Player player)
    {
        ItemStackSnapshot handheldItem = SpongeUnimplemented.getItemHeldByMouse(player);
        boolean isSomeoneSearchingInventory = false; // check if I should go for step 2
        boolean[] areSearchingInventory = new boolean[this.size]; // caches for step 2
        for (int i = 0; i < this.size; ++i) // step 1: check handheld item
        {
            VirtualChestHandheldItem itemTemplate = this.handheldItem.get(i);
            boolean isSearchingInventory = itemTemplate.isSearchingInventory();
            if (itemTemplate.matchItem(handheldItem)) // if it matches an item template, check its count
            {
                int size = handheldItem.getCount();
                if (isSearchingInventory) // search inventory to calculate total count
                {
                    for (Slot slot : player.getInventory().<Slot>slots())
                    {
                        Optional<ItemStack> stackOptional = slot.peek();
                        if (stackOptional.isPresent())
                        {
                            ItemStackSnapshot stackSnapshot = stackOptional.get().createSnapshot();
                            if (itemTemplate.matchItem(stackSnapshot))
                            {
                                size += stackSnapshot.getCount();
                            }
                        }
                    }
                }
                if (size >= itemTemplate.getCount()) // it's enough! do action now
                {
                    int repetition = itemTemplate.getRepetition(size);
                    String k1 = HANDHELD_ITEM.toString(), k2 = VirtualChestActions.ACTION_UUID_KEY;
                    ImmutableMap<String, Object> map = ImmutableMap.of(k1, itemTemplate, k2, actionUUID);
                    return Tuple.of(this.keepInventoryOpen.get(i), this.getAction(plugin, player, i, repetition, map));
                }
                areSearchingInventory[i] = false; // otherwise do not search inventory for it in step 2
            }
            else // if it does not match, search inventory in step 2 if user requests
            {
                areSearchingInventory[i] = isSearchingInventory;
                isSomeoneSearchingInventory = isSomeoneSearchingInventory || isSearchingInventory;
            }
        }
        if (isSomeoneSearchingInventory) // step 2: search inventory if no one matches the handheld item
        {
            int[] searchCounts = new int[this.size];
            for (Slot slot : player.getInventory().<Slot>slots())
            {
                Optional<ItemStack> stackOptional = slot.peek();
                if (stackOptional.isPresent())
                {
                    ItemStackSnapshot stackSnapshot = stackOptional.get().createSnapshot();
                    int stackQuantity = stackSnapshot.getCount(); // count for this slot
                    for (int i = 0; i < this.size; ++i)
                    {
                        if (areSearchingInventory[i])
                        {
                            VirtualChestHandheldItem itemTemplate = this.handheldItem.get(i);
                            if (itemTemplate.matchItem(stackSnapshot))
                            {
                                searchCounts[i] += stackQuantity;
                            }
                        }
                    }
                }
            }
            for (int i = 0; i < this.size; ++i) // check the templates orderly
            {
                int size = searchCounts[i];
                VirtualChestHandheldItem itemTemplate = this.handheldItem.get(i);
                if (size >= itemTemplate.getCount()) // it's enough! do action now
                {
                    int repetition = itemTemplate.getRepetition(size);
                    String k1 = HANDHELD_ITEM.toString(), k2 = VirtualChestActions.ACTION_UUID_KEY;
                    ImmutableMap<String, Object> map = ImmutableMap.of(k1, itemTemplate, k2, actionUUID);
                    return Tuple.of(this.keepInventoryOpen.get(i), this.getAction(plugin, player, i, repetition, map));
                }
            }
        }
        return Tuple.of(Boolean.TRUE, CompletableFuture.completedFuture(CommandResult.success()));
    }

    private CompletableFuture<CommandResult> getAction(VirtualChestPlugin plugin, Player player,
                                                       int index, int repetition, Map<String, Object> context)
    {
        List<String> commands = this.commands.get(index);
        VirtualChestActions a = plugin.getVirtualChestActions();
        Stream<Integer> rangeStream = IntStream.rangeClosed(0, repetition).boxed();
        return a.submitCommands(player, rangeStream.flatMap(i -> commands.stream()), context);
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

    private VirtualChestHandheldItem parseHandheldItem(Optional<DataView> optional)
    {
        // noinspection OptionalIsPresent
        if (optional.isPresent())
        {
            return new VirtualChestHandheldItem(optional.get());
        }
        else
        {
            return new VirtualChestHandheldItem();
        }
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
