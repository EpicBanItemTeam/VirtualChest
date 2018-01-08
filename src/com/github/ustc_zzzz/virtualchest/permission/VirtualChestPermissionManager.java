package com.github.ustc_zzzz.virtualchest.permission;

import com.github.ustc_zzzz.virtualchest.VirtualChestPlugin;
import com.github.ustc_zzzz.virtualchest.unsafe.SpongeUnimplemented;
import org.slf4j.Logger;
import org.spongepowered.api.Sponge;
import org.spongepowered.api.command.CommandSource;
import org.spongepowered.api.entity.living.player.Player;
import org.spongepowered.api.service.context.Context;
import org.spongepowered.api.service.context.ContextCalculator;
import org.spongepowered.api.service.permission.PermissionService;
import org.spongepowered.api.service.permission.Subject;
import org.spongepowered.api.service.permission.SubjectData;
import org.spongepowered.api.util.Identifiable;

import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * @author ustc_zzzz
 */
public class VirtualChestPermissionManager implements ContextCalculator<Subject>
{
    private final VirtualChestPlugin plugin;
    private final Logger logger;
    private final Context contextInAction = new Context("virtualchest-action", "in-action");
    private final Context contextNotInAction = new Context("virtualchest-action", "not-in-action");

    private final Map<UUID, Set<String>> playerIgnoredPermissions = new HashMap<>();

    public VirtualChestPermissionManager(VirtualChestPlugin plugin)
    {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
    }

    public void init()
    {
        PermissionService permissionService = Sponge.getServiceManager().provideUnchecked(PermissionService.class);
        if (SpongeUnimplemented.isPermissionServiceProvidedBySponge(permissionService))
        {
            this.logger.warn("VirtualChest could not find the permission service. ");
            this.logger.warn("Features related to permissions may not work normally.");
        }
        else
        {
            permissionService.registerContextCalculator(this);
        }
    }

    public CompletableFuture<Void> setIgnored(Player player, Collection<String> permissions)
    {
        SubjectData data = player.getTransientSubjectData();
        Set<Context> contexts = Collections.singleton(this.contextInAction);

        return this.clearPermissions(data, contexts)
                .thenCompose(succeed -> CompletableFuture.allOf(this.setPermissions(permissions, data, contexts)))
                .thenRun(() -> this.addIgnoredPermissionsToLog(permissions, player.getUniqueId(), player.getName()));
    }

    private CompletableFuture<Boolean> clearPermissions(SubjectData data, Set<Context> contexts)
    {
        return SpongeUnimplemented.clearPermissions(data, contexts);
    }

    private CompletableFuture<?>[] setPermissions(Collection<String> permissions, SubjectData data, Set<Context> contexts)
    {
        return permissions.stream().map(p -> SpongeUnimplemented.setPermission(data, contexts, p)).toArray(CompletableFuture[]::new);
    }

    private void addIgnoredPermissionsToLog(Collection<String> permissions, UUID uuid, String playerName)
    {
        this.logger.debug("Ignored {} permission(s) for {} (player {}):", permissions.size(), uuid, playerName);
        permissions.forEach(permission -> this.logger.debug("- {}", permission));
    }

    @Override
    public void accumulateContexts(Subject calculable, Set<Context> accumulator)
    {
        Optional<CommandSource> sourceOptional = calculable.getCommandSource();
        if (sourceOptional.isPresent())
        {
            CommandSource source = sourceOptional.get();
            if (source instanceof Identifiable)
            {
                UUID uuid = ((Identifiable) source).getUniqueId();
                if (this.plugin.getVirtualChestActions().isPlayerActivated(uuid))
                {
                    accumulator.add(this.contextInAction);
                    SubjectData data = source.getTransientSubjectData();
                    Map<String, Boolean> permissions = data.getPermissions(Collections.singleton(this.contextInAction));
                    this.logger.debug("Ignored {} permission(s) for {} (context):", permissions.size(), uuid);
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
        if (sourceOptional.isPresent())
        {
            CommandSource source = sourceOptional.get();
            if (source instanceof Identifiable)
            {
                UUID uuid = ((Identifiable) source).getUniqueId();
                if (this.plugin.getVirtualChestActions().isPlayerActivated(uuid))
                {
                    return this.contextInAction.equals(context);
                }
                else
                {
                    return this.contextNotInAction.equals(context);
                }
            }
        }
        return false;
    }
}
