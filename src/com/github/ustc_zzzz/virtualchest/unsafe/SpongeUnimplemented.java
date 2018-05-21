package com.github.ustc_zzzz.virtualchest.unsafe;

import org.objectweb.asm.Type;
import org.spongepowered.api.data.DataView;
import org.spongepowered.api.entity.living.player.Player;
import org.spongepowered.api.event.cause.Cause;
import org.spongepowered.api.item.inventory.Inventory;
import org.spongepowered.api.item.inventory.ItemStackSnapshot;
import org.spongepowered.api.item.inventory.Slot;
import org.spongepowered.api.item.inventory.property.SlotIndex;
import org.spongepowered.api.scheduler.Task;
import org.spongepowered.api.service.context.Context;
import org.spongepowered.api.service.permission.PermissionService;
import org.spongepowered.api.service.permission.SubjectData;
import org.spongepowered.api.util.Tristate;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collection;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/**
 * @author ustc_zzzz
 */
public class SpongeUnimplemented
{
    private static final MethodHandle GET_ITEM_STACK;
    private static final MethodHandle SET_ITEM_STACK;
    private static final MethodHandle UPDATE_HELD_ITEM;

    private static final MethodHandle SNAPSHOT_OF;
    private static final MethodHandle FROM_SNAPSHOT_TO_NATIVE;

    private static final MethodHandle PLAYER_CLOSE_INVENTORY;
    private static final MethodHandle PLAYER_OPEN_INVENTORY;

    private static final MethodHandle CAUSE_APPEND_SOURCE;
    private static final MethodHandle CAUSE_BUILD;

    private static final MethodHandle SUBJECT_DATA_CLEAR_PERMISSIONS;
    private static final MethodHandle SUBJECT_DATA_SET_PERMISSION;

    private static final MethodHandle GET_ITEM_COMPOUND;
    private static final MethodHandle TRANSLATE_DATA;
    private static final MethodHandle GET_INSTANCE;
    private static final MethodHandle ARE_NBT_EQUALS;

    public static Class<?> getItemEnchantmentClass()
    {
        try
        {
            try
            {
                return Class.forName("org.spongepowered.api.item.enchantment.Enchantment");
            }
            catch (ClassNotFoundException e)
            {
                return Class.forName("org.spongepowered.api.data.meta.ItemEnchantment");
            }
        }
        catch (ReflectiveOperationException e)
        {
            throw new UnsupportedOperationException("ItemEnchantment not found");
        }
    }

    public static String escapeString(String input)
    {
        // TODO: this method depends on apache commons
        // TODO: it should be replaced with an implementation by the plugin itself
        return org.apache.commons.lang3.StringEscapeUtils.escapeEcmaScript(input);
    }

    public static boolean isSlotInInventory(Slot slot, Inventory targetInventory)
    {
        return slot.parent().equals(targetInventory);
    }

    public static SlotIndex getSlotOrdinal(Slot slot)
    {
        Collection<SlotIndex> properties = slot.parent().getProperties(slot, SlotIndex.class);
        if (properties.isEmpty())
        {
            throw new UnsupportedOperationException("Not recognized");
        }
        return properties.iterator().next();
    }

    public static boolean isPermissionServiceProvidedBySponge(PermissionService permissionService)
    {
        return permissionService.getClass().getName().startsWith("org.spongepowered");
    }

    public static ItemStackSnapshot getItemHeldByMouse(Player player)
    {
        try
        {
            return (ItemStackSnapshot) SNAPSHOT_OF.invoke(GET_ITEM_STACK.invoke(player.getInventory()));
        }
        catch (Throwable throwable)
        {
            throw new UnsupportedOperationException(throwable);
        }
    }

    public static void setItemHeldByMouse(Player player, ItemStackSnapshot stack)
    {
        try
        {
            SET_ITEM_STACK.invoke(player.getInventory(), FROM_SNAPSHOT_TO_NATIVE.invoke(stack));
            UPDATE_HELD_ITEM.invoke(player);
        }
        catch (Throwable throwable)
        {
            throw new UnsupportedOperationException(throwable);
        }
    }

    public static Cause createCause(Object obj)
    {
        try
        {
            if (CAUSE_APPEND_SOURCE.type().parameterCount() > 2)
            {
                Object causeBuilder = CAUSE_APPEND_SOURCE.invoke(Cause.builder(), "Source", obj);
                return (Cause) CAUSE_BUILD.invoke(causeBuilder);
            }
            else
            {
                Object causeBuilder = CAUSE_APPEND_SOURCE.invoke(Cause.builder(), obj);
                return (Cause) CAUSE_BUILD.invoke(causeBuilder, getEventContext());
            }
        }
        catch (Throwable throwable)
        {
            throw new UnsupportedOperationException(throwable);
        }
    }

    public static void openInventory(Player player, Inventory inventory, Object causeObj)
    {
        try
        {
            if (PLAYER_OPEN_INVENTORY.type().parameterCount() > 2)
            {
                PLAYER_OPEN_INVENTORY.invoke(player, inventory, createCause(causeObj));
            }
            else
            {
                PLAYER_OPEN_INVENTORY.invoke(player, inventory);
            }
        }
        catch (Throwable throwable)
        {
            throw new UnsupportedOperationException(throwable);
        }
    }

    public static void closeInventory(Player player, Object causeObj)
    {
        try
        {
            if (PLAYER_CLOSE_INVENTORY.type().parameterCount() > 1)
            {
                PLAYER_CLOSE_INVENTORY.invoke(player, createCause(causeObj));
            }
            else
            {
                PLAYER_CLOSE_INVENTORY.invoke(player);
            }
        }
        catch (Throwable throwable)
        {
            throw new UnsupportedOperationException(throwable);
        }
    }

    public static CompletableFuture<Boolean> clearPermissions(Object plugin, SubjectData data, Set<Context> contexts)
    {
        try
        {
            Object result = SUBJECT_DATA_CLEAR_PERMISSIONS.invoke(data, contexts);
            if (result instanceof CompletableFuture)
            {
                @SuppressWarnings("unchecked")
                CompletableFuture<Boolean> future = (CompletableFuture) result;
                return future;
            }
            else
            {
                CompletableFuture<Boolean> future = new CompletableFuture<>();
                Task.builder().async().delayTicks(2).intervalTicks(2).execute(task ->
                {
                    if (data.getPermissions(contexts).isEmpty())
                    {
                        task.cancel();
                        future.complete((Boolean) result);
                    }
                }).submit(plugin);
                return future;
            }
        }
        catch (Throwable throwable)
        {
            throw new UnsupportedOperationException(throwable);
        }
    }

    public static CompletableFuture<Boolean> setPermission(Object plugin, SubjectData data, Set<Context> contexts, String permission)
    {
        try
        {
            Object result = SUBJECT_DATA_SET_PERMISSION.invoke(data, contexts, permission, Tristate.TRUE);
            if (result instanceof CompletableFuture)
            {
                @SuppressWarnings("unchecked")
                CompletableFuture<Boolean> future = (CompletableFuture) result;
                return future;
            }
            else
            {
                CompletableFuture<Boolean> future = new CompletableFuture<>();
                Task.builder().async().delayTicks(2).intervalTicks(2).execute(task ->
                {
                    if (data.getPermissions(contexts).getOrDefault(permission, Boolean.FALSE))
                    {
                        task.cancel();
                        future.complete((Boolean) result);
                    }
                }).submit(plugin);
                return future;
            }
        }
        catch (Throwable throwable)
        {
            throw new UnsupportedOperationException(throwable);
        }
    }

    public static boolean isNBTMatched(Optional<DataView> matcher, ItemStackSnapshot item)
    {
        try
        {
            Object nbt2 = ((Optional<?>) GET_ITEM_COMPOUND.invoke(FROM_SNAPSHOT_TO_NATIVE.invoke(item))).orElse(null);
            Object nbt1 = matcher.isPresent() ? TRANSLATE_DATA.invoke(GET_INSTANCE.invoke(), matcher.get()) : null;
            return (boolean) ARE_NBT_EQUALS.invoke(nbt1, nbt2, true);
        }
        catch (Throwable throwable)
        {
            throw new UnsupportedOperationException(throwable);
        }
    }

    static
    {
        MethodHandles.Lookup lookup = MethodHandles.publicLookup();

        GET_ITEM_STACK = getMethod(
                lookup, "net.minecraft.entity.player.InventoryPlayer", "func_70445_o");
        SET_ITEM_STACK = getMethod(
                lookup, "net.minecraft.entity.player.InventoryPlayer", "func_70437_b");
        UPDATE_HELD_ITEM = getMethod(
                lookup, "net.minecraft.entity.player.EntityPlayerMP", "func_71113_k");
        SNAPSHOT_OF = getMethod(
                lookup, "org.spongepowered.common.item.inventory.util.ItemStackUtil", "snapshotOf (Lnet/minecraft/item/ItemStack;)Lorg/spongepowered/api/item/inventory/ItemStackSnapshot;");
        FROM_SNAPSHOT_TO_NATIVE = getMethod(
                lookup, "org.spongepowered.common.item.inventory.util.ItemStackUtil", "fromSnapshotToNative (Lorg/spongepowered/api/item/inventory/ItemStackSnapshot;)Lnet/minecraft/item/ItemStack;");
        PLAYER_CLOSE_INVENTORY = getMethod(
                lookup, Player.class, "closeInventory (Lorg/spongepowered/api/event/cause/Cause;)Z", "closeInventory ()Z");
        PLAYER_OPEN_INVENTORY = getMethod(
                lookup, Player.class, "openInventory (Lorg/spongepowered/api/item/inventory/Inventory;Lorg/spongepowered/api/event/cause/Cause;)Ljava/util/Optional;", "openInventory (Lorg/spongepowered/api/item/inventory/Inventory;)Ljava/util/Optional;");
        CAUSE_APPEND_SOURCE = getMethod(
                lookup, Cause.Builder.class, "append (Ljava/lang/Object;)Lorg/spongepowered/api/event/cause/Cause$Builder;", "named (Ljava/lang/String;Ljava/lang/Object;)Lorg/spongepowered/api/event/cause/Cause$Builder;");
        CAUSE_BUILD = getMethod(
                lookup, Cause.Builder.class, "build");
        SUBJECT_DATA_CLEAR_PERMISSIONS = getMethod(
                lookup, SubjectData.class, "clearPermissions (Ljava/util/Set;)Z", "clearPermissions (Ljava/util/Set;)Ljava/util/concurrent/CompletableFuture;");
        SUBJECT_DATA_SET_PERMISSION = getMethod(
                lookup, SubjectData.class, "setPermission (Ljava/util/Set;Ljava/lang/String;Lorg/spongepowered/api/util/Tristate;)Z", "setPermission (Ljava/util/Set;Ljava/lang/String;Lorg/spongepowered/api/util/Tristate;)Ljava/util/concurrent/CompletableFuture;");
        GET_ITEM_COMPOUND = getMethod(
                lookup, "org.spongepowered.common.data.util.NbtDataUtil", "getItemCompound");
        TRANSLATE_DATA = getMethod(
                lookup, "org.spongepowered.common.data.persistence.NbtTranslator", "translateData");
        GET_INSTANCE = getMethod(
                lookup, "org.spongepowered.common.data.persistence.NbtTranslator", "getInstance");
        ARE_NBT_EQUALS = getMethod(
                lookup, "net.minecraft.nbt.NBTUtil", "func_181123_a");
    }

    private static Object getEventContext() throws ReflectiveOperationException
    {
        Class<?> eventContextClass = Class.forName("org.spongepowered.api.event.cause.EventContext");
        Field field = eventContextClass.getDeclaredField("EMPTY_CONTEXT");
        field.setAccessible(true);
        return field.get(null);
    }

    private static MethodHandle getMethod(MethodHandles.Lookup lookup, String clazz, String... methods)
    {
        try
        {
            return getMethod(lookup, Class.forName(clazz), methods);
        }
        catch (ClassNotFoundException e)
        {
            throw new UnsupportedOperationException("Class " + clazz + " not found", e);
        }
    }

    private static MethodHandle getMethod(MethodHandles.Lookup lookup, Class<?> clazz, String... methods)
    {
        for (String methodName : methods)
        {
            int spaceIndex = methodName.indexOf(' ');
            String desc = spaceIndex < 0 ? "" : methodName.substring(spaceIndex + 1);
            String name = spaceIndex < 0 ? methodName : methodName.substring(0, spaceIndex);
            for (Method m : clazz.getMethods())
            {
                try
                {
                    if (m.getName().equals(name) && (desc.isEmpty() || Type.getMethodDescriptor(m).equals(desc)))
                    {
                        return lookup.unreflect(m);
                    }
                }
                catch (Exception ignored)
                {
                    // just continue
                }
            }
        }
        throw new UnsupportedOperationException("Methods " + Arrays.asList(methods) + " not found");
    }

    private SpongeUnimplemented()
    {
    }
}
