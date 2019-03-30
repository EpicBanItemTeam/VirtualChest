package com.github.ustc_zzzz.virtualchest.api;

import com.google.common.collect.ClassToInstanceMap;
import org.spongepowered.api.command.CommandResult;
import org.spongepowered.api.entity.living.player.Player;
import org.spongepowered.api.event.Event;
import org.spongepowered.api.item.inventory.ItemStackSnapshot;
import org.spongepowered.api.util.annotation.NonnullByDefault;

import java.lang.ref.WeakReference;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Predicate;

/**
 * @author ustc_zzzz
 */
@NonnullByDefault
@FunctionalInterface
public interface VirtualChestActionExecutor
{
    CompletableFuture<CommandResult> execute(CommandResult parent, String command, ClassToInstanceMap<Context> context);

    @NonnullByDefault
    interface Context
    {
        Class<PlayerContext> PLAYER = PlayerContext.class;

        Class<ActionUUIDContext> ACTION_UUID = ActionUUIDContext.class;

        Class<HandheldItemContext> HANDHELD_ITEM = HandheldItemContext.class;
    }

    @NonnullByDefault
    final class PlayerContext implements Context
    {
        private final WeakReference<Player> player;

        public PlayerContext()
        {
            this.player = new WeakReference<>(null);
        }

        public PlayerContext(Player player)
        {
            this.player = new WeakReference<>(player);
        }

        public Optional<Player> getPlayer()
        {
            return Optional.ofNullable(this.player.get());
        }
    }

    @NonnullByDefault
    final class ActionUUIDContext implements Context
    {
        private final UUID actionUniqueId;

        public ActionUUIDContext(UUID actionUUID)
        {
            this.actionUniqueId = actionUUID;
        }

        public UUID getActionUniqueId()
        {
            return this.actionUniqueId;
        }
    }

    @NonnullByDefault
    final class HandheldItemContext implements Context
    {
        private final Predicate<ItemStackSnapshot> handheldItem;

        public HandheldItemContext(Predicate<ItemStackSnapshot> handheldItem)
        {
            this.handheldItem = handheldItem;
        }

        public boolean matchItem(ItemStackSnapshot item)
        {
            return this.handheldItem.test(item);
        }
    }

    @NonnullByDefault
    interface LoadEvent extends Event
    {
        void register(String prefix, VirtualChestActionExecutor action);
    }
}
