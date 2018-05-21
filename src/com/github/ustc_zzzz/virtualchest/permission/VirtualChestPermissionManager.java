package com.github.ustc_zzzz.virtualchest.permission;

import com.github.ustc_zzzz.virtualchest.VirtualChestPlugin;
import com.github.ustc_zzzz.virtualchest.action.VirtualChestActions;
import com.github.ustc_zzzz.virtualchest.unsafe.SpongeUnimplemented;
import org.slf4j.Logger;
import org.spongepowered.api.Sponge;
import org.spongepowered.api.entity.living.player.Player;
import org.spongepowered.api.service.context.Context;
import org.spongepowered.api.service.context.ContextCalculator;
import org.spongepowered.api.service.permission.PermissionService;
import org.spongepowered.api.service.permission.Subject;
import org.spongepowered.api.service.permission.SubjectData;

import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * @author ustc_zzzz
 */
public class VirtualChestPermissionManager implements ContextCalculator<Subject>
{
    private static final String CONTEXT_KEY = "virtualchest-action-uuid";

    private final VirtualChestPlugin plugin;
    private final Logger logger;

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

    public CompletableFuture<?> setIgnored(Player player, UUID actionUUID, Collection<String> permissions)
    {
        SubjectData data = player.getTransientSubjectData();
        Set<Context> contexts = Collections.singleton(new Context(CONTEXT_KEY, actionUUID.toString()));

        return this.clear(data, contexts)
                .thenCompose(succeed -> CompletableFuture.allOf(this.set(data, contexts, permissions)))
                .thenRun(() -> this.logIgnoredPermissionsAdded(permissions, actionUUID, player.getName()));
    }

    public CompletableFuture<?> clearIgnored(Player player, UUID actionUUID)
    {
        SubjectData data = player.getTransientSubjectData();
        Set<Context> contexts = Collections.singleton(new Context(CONTEXT_KEY, actionUUID.toString()));

        return this.clear(data, contexts)
                .thenRun(() -> this.logIgnoredPermissionsCleared(actionUUID, player.getName()));
    }

    private CompletableFuture<Boolean> clear(SubjectData data, Set<Context> contexts)
    {
        return SpongeUnimplemented.clearPermissions(this.plugin, data, contexts);
    }

    private CompletableFuture<Boolean> set(SubjectData data, Set<Context> contexts, String permission)
    {
        return SpongeUnimplemented.setPermission(this.plugin, data, contexts, permission);
    }

    private CompletableFuture<?>[] set(SubjectData data, Set<Context> set, Collection<String> permissions)
    {
        return permissions.stream().map(p -> this.set(data, set, p)).toArray(CompletableFuture[]::new);
    }

    private void logIgnoredPermissionsAdded(Collection<String> permissions, UUID actionUUID, String name)
    {
        this.logger.debug("Ignored {} permission(s) for action {} (player {}):", permissions.size(), actionUUID, name);
        permissions.forEach(permission -> this.logger.debug("- {} (true)", permission));
    }

    private void logIgnoredPermissionsCleared(UUID actionUUID, String name)
    {
        this.logger.debug("Cleared ignored permission(s) for action {} (player {}).", actionUUID, name);
    }

    @Override
    public void accumulateContexts(Subject subject, Set<Context> accumulator)
    {
        VirtualChestActions actions = this.plugin.getVirtualChestActions();
        for (UUID actionUUID : actions.getActivatedActions(subject.getIdentifier()))
        {
            SubjectData data = subject.getTransientSubjectData();
            Context context = new Context(CONTEXT_KEY, actionUUID.toString());
            Map<String, Boolean> permissions = data.getPermissions(Collections.singleton(context));

            this.logger.debug("Ignored {} permission(s) for action {} (context):", permissions.size(), actionUUID);
            permissions.forEach((permission, state) -> this.logger.debug("- {} ({})", permission, state));
            accumulator.add(context);
        }
    }

    @Override
    public boolean matches(Context context, Subject subject)
    {
        if (CONTEXT_KEY.equals(context.getKey()))
        {
            try
            {
                UUID value = UUID.fromString(context.getValue());
                VirtualChestActions actions = this.plugin.getVirtualChestActions();
                return actions.getActivatedActions(subject.getIdentifier()).contains(value);
            }
            catch (IllegalArgumentException e)
            {
                return false;
            }
        }
        return false;
    }
}
