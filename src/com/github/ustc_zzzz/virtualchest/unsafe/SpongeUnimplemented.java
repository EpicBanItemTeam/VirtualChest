package com.github.ustc_zzzz.virtualchest.unsafe;

import com.google.common.base.Throwables;
import org.spongepowered.api.entity.living.player.Player;
import org.spongepowered.api.event.cause.Cause;
import org.spongepowered.api.item.inventory.Inventory;
import org.spongepowered.api.item.inventory.ItemStackSnapshot;
import org.spongepowered.api.item.inventory.Slot;
import org.spongepowered.api.item.inventory.property.SlotIndex;
import org.spongepowered.api.service.permission.PermissionService;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Optional;

/**
 * @author ustc_zzzz
 */
public class SpongeUnimplemented
{
    private static final Class<?> NMS_ENTITY_PLAYER_MP_CLASS;
    private static final Class<?> NMS_INVENTORY_PLAYER_CLASS;
    private static final Class<?> ITEM_STACK_UTIL_CLASS;
    private static final Class<?> NMS_ITEM_STACK_CLASS;

    private static final MethodHandle GET_ITEM_STACK;
    private static final MethodHandle SET_ITEM_STACK;
    private static final MethodHandle UPDATE_HELD_ITEM;

    private static final MethodHandle SNAPSHOT_OF;
    private static final MethodHandle FROM_SNAPSHOT_TO_NATIVE;

    private static final MethodHandle PLAYER_CLOSE_INVENTORY;
    private static final MethodHandle PLAYER_OPEN_INVENTORY;

    private static final MethodHandle CAUSE_APPEND_SOURCE;
    private static final MethodHandle CAUSE_BUILD;

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
                return (Cause) CAUSE_BUILD.invokeWithArguments(getCauseBuildArgs(causeBuilder));
            }
            else
            {
                Object causeBuilder = CAUSE_APPEND_SOURCE.invoke(Cause.builder(), obj);
                return (Cause) CAUSE_BUILD.invokeWithArguments(getCauseBuildArgs(causeBuilder));
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

    static
    {
        try
        {
            MethodHandles.Lookup lookup = MethodHandles.publicLookup();

            NMS_ITEM_STACK_CLASS = Class.forName("net.minecraft.item.ItemStack");
            NMS_ENTITY_PLAYER_MP_CLASS = Class.forName("net.minecraft.entity.player.EntityPlayerMP");
            NMS_INVENTORY_PLAYER_CLASS = Class.forName("net.minecraft.entity.player.InventoryPlayer");
            ITEM_STACK_UTIL_CLASS = Class.forName("org.spongepowered.common.item.inventory.util.ItemStackUtil");

            MethodType updateHeldItemType = MethodType.methodType(void.class);
            MethodType getItemStackType = MethodType.methodType(NMS_ITEM_STACK_CLASS);
            MethodType setItemStackType = MethodType.methodType(void.class, NMS_ITEM_STACK_CLASS);
            MethodType snapshotOfType = MethodType.methodType(ItemStackSnapshot.class, NMS_ITEM_STACK_CLASS);
            MethodType fromSnapshotToNativeType = MethodType.methodType(NMS_ITEM_STACK_CLASS, ItemStackSnapshot.class);

            GET_ITEM_STACK = lookup.findVirtual(NMS_INVENTORY_PLAYER_CLASS, "func_70445_o", getItemStackType);
            SET_ITEM_STACK = lookup.findVirtual(NMS_INVENTORY_PLAYER_CLASS, "func_70437_b", setItemStackType);
            UPDATE_HELD_ITEM = lookup.findVirtual(NMS_ENTITY_PLAYER_MP_CLASS, "func_71113_k", updateHeldItemType);
            SNAPSHOT_OF = lookup.findStatic(ITEM_STACK_UTIL_CLASS, "snapshotOf", snapshotOfType);
            FROM_SNAPSHOT_TO_NATIVE = lookup.findStatic(ITEM_STACK_UTIL_CLASS, "fromSnapshotToNative", fromSnapshotToNativeType);
            PLAYER_CLOSE_INVENTORY = getPlayerCloseInventoryMethod(lookup);
            PLAYER_OPEN_INVENTORY = getPlayerOpenInventoryMethod(lookup);
            CAUSE_APPEND_SOURCE = getCauseAppendSourceMethod(lookup);
            CAUSE_BUILD = getCauseBuildMethod(lookup);
        }
        catch (ReflectiveOperationException e)
        {
            throw Throwables.propagate(e);
        }
    }

    private static MethodHandle getPlayerOpenInventoryMethod(MethodHandles.Lookup lookup) throws ReflectiveOperationException
    {
        try
        {
            MethodType methodType = MethodType.methodType(Optional.class, Inventory.class);
            return lookup.findVirtual(Player.class, "openInventory", methodType);
        }
        catch (ReflectiveOperationException e)
        {
            MethodType methodType = MethodType.methodType(Optional.class, Inventory.class, Cause.class);
            return lookup.findVirtual(Player.class, "openInventory", methodType);
        }
    }

    private static MethodHandle getPlayerCloseInventoryMethod(MethodHandles.Lookup lookup) throws ReflectiveOperationException
    {
        try
        {
            MethodType methodType = MethodType.methodType(boolean.class);
            return lookup.findVirtual(Player.class, "closeInventory", methodType);
        }
        catch (ReflectiveOperationException e)
        {
            MethodType methodType = MethodType.methodType(boolean.class, Cause.class);
            return lookup.findVirtual(Player.class, "closeInventory", methodType);
        }
    }

    private static Object[] getCauseBuildArgs(Object object) throws ReflectiveOperationException
    {
        try
        {
            Class<?> eventContextClass = Class.forName("org.spongepowered.api.event.cause.EventContext");
            Field field = eventContextClass.getDeclaredField("EMPTY_CONTEXT");
            field.setAccessible(true);

            return Arrays.asList(object, field.get(null)).toArray();
        }
        catch (ReflectiveOperationException e)
        {
            return Collections.singletonList(object).toArray();
        }
    }

    private static MethodHandle getCauseBuildMethod(MethodHandles.Lookup lookup) throws ReflectiveOperationException
    {
        try
        {
            Class<?> eventContextClass = Class.forName("org.spongepowered.api.event.cause.EventContext");
            MethodType methodType = MethodType.methodType(Cause.class, eventContextClass);
            return lookup.findVirtual(Cause.Builder.class, "build", methodType);
        }
        catch (ReflectiveOperationException e)
        {
            return lookup.findVirtual(Cause.Builder.class, "build", MethodType.methodType(Cause.class));
        }
    }

    private static MethodHandle getCauseAppendSourceMethod(MethodHandles.Lookup lookup) throws ReflectiveOperationException
    {
        try
        {
            MethodType methodType = MethodType.methodType(Cause.Builder.class, String.class, Object.class);
            return lookup.findVirtual(Cause.Builder.class, "named", methodType);
        }
        catch (ReflectiveOperationException e)
        {
            MethodType methodType = MethodType.methodType(Cause.Builder.class, Object.class);
            return lookup.findVirtual(Cause.Builder.class, "append", methodType);
        }
    }

    private SpongeUnimplemented()
    {
    }
}
