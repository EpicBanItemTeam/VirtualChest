package com.github.ustc_zzzz.virtualchest.unsafe;

import com.google.common.base.Throwables;
import org.spongepowered.api.entity.living.player.Player;
import org.spongepowered.api.item.inventory.Inventory;
import org.spongepowered.api.item.inventory.ItemStackSnapshot;
import org.spongepowered.api.item.inventory.Slot;
import org.spongepowered.api.service.permission.PermissionService;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

/**
 * @author ustc_zzzz
 */
public class SpongeUnimplemented
{
    private static final Class<?> NMS_ENTITY_PLAYER_MP_CLASS;
    private static final Class<?> NMS_INVENTORY_PLAYER_CLASS;
    private static final Class<?> ITEM_STACK_UTIL_CLASS;
    private static final Class<?> NMS_ITEM_STACK_CLASS;
    private static final Class<?> SLOT_ADAPTER_CLASS;

    private static final MethodHandle GET_ORDINAL;

    private static final MethodHandle GET_ITEM_STACK;
    private static final MethodHandle SET_ITEM_STACK;
    private static final MethodHandle UPDATE_HELD_ITEM;

    private static final MethodHandle SNAPSHOT_OF;
    private static final MethodHandle FROM_SNAPSHOT_TO_NATIVE;

    public static boolean isSlotInInventory(Slot slot, Inventory targetInventory)
    {
        Inventory parent = slot.parent();
        // Sponge has changed the implementation of inventory since
        // SpongeVanilla: 1.11.2-6.0.0-BETA-239 (https://github.com/SpongePowered/SpongeVanilla/commit/e9b056b),
        // SpongeForge: 1.10.2-2254-5.2.0-BETA-2264 (https://github.com/SpongePowered/SpongeForge/commit/c57e4e7),
        // so there should be two ways to distinguish.
        return parent.equals(targetInventory) || parent.equals(targetInventory.first());
    }

    public static int getSlotOrdinal(Slot slot)
    {
        if (!SLOT_ADAPTER_CLASS.isInstance(slot))
        {
            throw new UnsupportedOperationException("Not recognized");
        }
        try
        {
            return (int) GET_ORDINAL.invoke(slot);
        }
        catch (Throwable throwable)
        {
            throw new UnsupportedOperationException(throwable);
        }
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

    static
    {
        try
        {
            MethodHandles.Lookup lookup = MethodHandles.publicLookup();

            NMS_ITEM_STACK_CLASS = Class.forName("net.minecraft.item.ItemStack");
            NMS_ENTITY_PLAYER_MP_CLASS = Class.forName("net.minecraft.entity.player.EntityPlayerMP");
            NMS_INVENTORY_PLAYER_CLASS = Class.forName("net.minecraft.entity.player.InventoryPlayer");
            ITEM_STACK_UTIL_CLASS = Class.forName("org.spongepowered.common.item.inventory.util.ItemStackUtil");
            SLOT_ADAPTER_CLASS = Class.forName("org.spongepowered.common.item.inventory.adapter.impl.slots.SlotAdapter");

            MethodType getOrdinalType = MethodType.methodType(int.class);
            MethodType updateHeldItemType = MethodType.methodType(void.class);
            MethodType getItemStackType = MethodType.methodType(NMS_ITEM_STACK_CLASS);
            MethodType setItemStackType = MethodType.methodType(void.class, NMS_ITEM_STACK_CLASS);
            MethodType snapshotOfType = MethodType.methodType(ItemStackSnapshot.class, NMS_ITEM_STACK_CLASS);
            MethodType fromSnapshotToNativeType = MethodType.methodType(NMS_ITEM_STACK_CLASS, ItemStackSnapshot.class);

            GET_ORDINAL = lookup.findVirtual(SLOT_ADAPTER_CLASS, "getOrdinal", getOrdinalType);
            GET_ITEM_STACK = lookup.findVirtual(NMS_INVENTORY_PLAYER_CLASS, "func_70445_o", getItemStackType);
            SET_ITEM_STACK = lookup.findVirtual(NMS_INVENTORY_PLAYER_CLASS, "func_70437_b", setItemStackType);
            UPDATE_HELD_ITEM = lookup.findVirtual(NMS_ENTITY_PLAYER_MP_CLASS, "func_71113_k", updateHeldItemType);
            SNAPSHOT_OF = lookup.findStatic(ITEM_STACK_UTIL_CLASS, "snapshotOf", snapshotOfType);
            FROM_SNAPSHOT_TO_NATIVE = lookup.findStatic(ITEM_STACK_UTIL_CLASS, "fromSnapshotToNative", fromSnapshotToNativeType);
        }
        catch (ReflectiveOperationException e)
        {
            throw Throwables.propagate(e);
        }
    }

    private SpongeUnimplemented()
    {
    }
}
