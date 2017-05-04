package com.github.ustc_zzzz.virtualchest.permission;

import com.github.ustc_zzzz.virtualchest.VirtualChestPlugin;
import com.google.common.collect.ImmutableSet;
import org.slf4j.Logger;
import org.spongepowered.api.command.CommandSource;
import org.spongepowered.api.entity.living.player.Player;
import org.spongepowered.api.service.context.Context;
import org.spongepowered.api.service.context.ContextCalculator;
import org.spongepowered.api.service.permission.Subject;
import org.spongepowered.api.service.permission.SubjectData;
import org.spongepowered.api.util.Tristate;

import java.util.*;

/**
 * @author ustc_zzzz
 */
public class VirtualChestPermissionManager implements ContextCalculator<Subject>
{
    private final VirtualChestPlugin plugin;
    private final Logger logger;
    private final Context contextInAction = new Context("virtualchest-action", "in-action");
    private final Context contextNotInAction = new Context("virtualchest-action", "not-in-action");

    private final Map<Player, Set<String>> playerIgnoredPermissions = new WeakHashMap<>();

    public VirtualChestPermissionManager(VirtualChestPlugin plugin)
    {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
    }

    public void setIgnoredPermissions(Player player, Collection<String> permissions)
    {
        Set<Context> contextSet = Collections.singleton(this.contextInAction);
        SubjectData data = player.getTransientSubjectData();
        Set<String> oldIgnoredPermissions = this.playerIgnoredPermissions.getOrDefault(player, Collections.emptySet());
        oldIgnoredPermissions.forEach(permission -> data.setPermission(contextSet, permission, Tristate.UNDEFINED));
        ImmutableSet<String> newIgnoredPermissions = ImmutableSet.copyOf(permissions);
        newIgnoredPermissions.forEach(permission -> data.setPermission(contextSet, permission, Tristate.TRUE));
        this.playerIgnoredPermissions.put(player, newIgnoredPermissions);
        this.logger.debug("Ignored permission(s) for player {}:", player.getName());
        permissions.forEach(permission -> this.logger.debug("- {}", permission));
    }

    @Override
    public void accumulateContexts(Subject calculable, Set<Context> accumulator)
    {
        Optional<CommandSource> sourceOptional = calculable.getCommandSource();
        if (sourceOptional.isPresent())
        {
            CommandSource source = sourceOptional.get();
            if (source instanceof Player)
            {
                Player player = (Player) source;
                if (this.plugin.getVirtualChestActions().isPlayerInAction(player))
                {
                    accumulator.add(this.contextInAction);
                    Map<String, Boolean> permissions = player
                            .getTransientSubjectData().getPermissions(Collections.singleton(this.contextInAction));
                    this.logger.debug("Ignored permission(s) for player {} (context):", player.getName());
                    permissions.forEach((permission, state) -> this.logger.debug("- {} ({})", permission, state));
                }
                else
                {
                    accumulator.add(this.contextNotInAction);
                }
            }
        }
    }

    @Override
    public boolean matches(Context context, Subject subject)
    {
        Optional<CommandSource> sourceOptional = subject.getCommandSource();
        if (sourceOptional.isPresent() && sourceOptional.get() instanceof Player)
        {
            Player player = (Player) sourceOptional.get();
            if (this.plugin.getVirtualChestActions().isPlayerInAction(player))
            {
                return this.contextInAction.equals(context);
            }
            else
            {
                return this.contextNotInAction.equals(context);
            }
        }
        return false;
    }
}
