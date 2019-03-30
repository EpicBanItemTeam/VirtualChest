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
 * Representation of an action executor.
 *
 * @author ustc_zzzz
 * @see LoadEvent
 */
@NonnullByDefault
@FunctionalInterface
public interface VirtualChestActionExecutor
{
    /**
     * Execute the submitted action and return a result. Asynchronous execution is allowed.
     *
     * @param parent  parent result
     * @param command command suffix
     * @param context immutable context map
     * @return a completable future which will provide a {@link CommandResult}
     */
    CompletableFuture<CommandResult> execute(CommandResult parent, String command, ClassToInstanceMap<Context> context);

    /**
     * An empty interface whose implementations represent the contexts of a submitted action.
     * <p>
     * Those implementations can be retrieved from the immutable {@link ClassToInstanceMap} passed
     * as the last parameter of {@link #execute} method. The keys of the {@link ClassToInstanceMap}
     * can be either constructed directly or got from the static fields declared in the interface.
     * <p>
     * For example, the expression {@code context.getInstance(Context.PLAYER).getPlayer()} returns
     * an {@link Optional} which may contains a reference of a player. Considering that the action
     * may be submitted by the server console, or the player may become offline since the execution
     * is continuing (asynchronous execution may continue for several ticks), the {@link Optional}
     * may be empty and contains nothing.
     *
     * @author ustc_zzzz
     */
    @NonnullByDefault
    interface Context
    {
        Class<PlayerContext> PLAYER = PlayerContext.class;

        Class<ActionUUIDContext> ACTION_UUID = ActionUUIDContext.class;

        Class<HandheldItemContext> HANDHELD_ITEM = HandheldItemContext.class;
    }

    /**
     * @author ustc_zzzz
     */
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

    /**
     * @author ustc_zzzz
     */
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

    /**
     * @author ustc_zzzz
     */
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

    /**
     * Representation of a event, fired while VirtualChest is being loaded at the stage the server
     * is starting. The event will be fired on the main thread.
     * <p>
     * The registration for all of the action executors provided by VirtualChest itself is ensured
     * to be finished completely before this event is fired.
     *
     * @author ustc_zzzz
     */
    @NonnullByDefault
    interface LoadEvent extends Event
    {
        /**
         * Register a prefix and its corresponding action executor. Multiple action executors could
         * be registered to the same prefix and they will be executed orderly.
         *
         * @param prefix         the prefix to be registered
         * @param actionExecutor the corresponding action executor
         */
        void register(String prefix, VirtualChestActionExecutor actionExecutor);
    }
}
