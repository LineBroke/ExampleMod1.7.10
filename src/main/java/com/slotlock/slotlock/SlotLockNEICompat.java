package com.slotlock.slotlock;

import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;

import codechicken.nei.NEIClientUtils;

public final class SlotLockNEICompat {

    private SlotLockNEICompat() {}

    /**
     * 返回 true = 已经处理 NEI 给物品逻辑，原版 NEI giveStack 应该取消。
     * 返回 false = 不处理，放行 NEI 原逻辑。
     */
    public static boolean giveStackUnlockedOnly(ItemStack base, int amount, boolean infinite) {
        if (!SlotLockManager.hasAnyLock()) {
            return false;
        }

        if (base == null || base.getItem() == null || amount <= 0) {
            return true;
        }

        GuiContainer gui = NEIClientUtils.getGuiContainer();

        /*
         * 没有 GUI 时无法精确选择玩家槽位。
         * 为了不让 NEI 原逻辑把物品塞进锁定槽，这里直接拦截。
         */
        if (gui == null || gui.inventorySlots == null) {
            return true;
        }

        ItemStack remaining = base.copy();
        remaining.stackSize = amount;

        /*
         * 第一阶段：
         * 先合并到未锁定的同类非满槽。
         */
        mergeIntoUnlockedSlots(gui.inventorySlots, remaining);

        /*
         * 第二阶段：
         * 再放进未锁定空槽。
         */
        putIntoUnlockedEmptySlots(gui.inventorySlots, remaining);

        /*
         * 无论有没有完全放下，都取消 NEI 原 giveStack。
         * 否则剩余物品会走 NEI 原逻辑，然后可能进入锁定槽。
         */
        return true;
    }

    private static void mergeIntoUnlockedSlots(Container container, ItemStack remaining) {
        if (remaining == null || remaining.stackSize <= 0) {
            return;
        }

        /*
         * 优先主背包 9-35。
         */
        for (int i = 9; i < 36; i++) {
            mergeIntoPlayerIndex(container, i, remaining);

            if (remaining.stackSize <= 0) {
                return;
            }
        }

        /*
         * 最后快捷栏 0-8。
         */
        for (int i = 0; i < 9; i++) {
            mergeIntoPlayerIndex(container, i, remaining);

            if (remaining.stackSize <= 0) {
                return;
            }
        }
    }

    private static void mergeIntoPlayerIndex(Container container, int playerIndex, ItemStack remaining) {
        if (SlotLockManager.isLockedPlayerIndex(playerIndex)) {
            return;
        }

        Slot slot = findPlayerSlot(container, playerIndex);

        if (slot == null || !slot.getHasStack()) {
            return;
        }

        ItemStack target = slot.getStack();

        if (!canMerge(remaining, target)) {
            return;
        }

        int max = Math.min(target.getMaxStackSize(), slot.getSlotStackLimit());
        int space = max - target.stackSize;

        if (space <= 0) {
            return;
        }

        int move = Math.min(space, remaining.stackSize);

        ItemStack newStack = target.copy();
        newStack.stackSize += move;

        slot.putStack(newStack);

        /*
         * 关键：
         * 用 NEI 的 SET_SLOT 包精确设置这个 slot，
         * 而不是用 GIVE_ITEM 让服务端自己找空位。
         */
        NEIClientUtils.setSlotContents(slot.slotNumber, newStack, true);

        remaining.stackSize -= move;
    }

    private static void putIntoUnlockedEmptySlots(Container container, ItemStack remaining) {
        if (remaining == null || remaining.stackSize <= 0) {
            return;
        }

        /*
         * 优先主背包 9-35。
         */
        for (int i = 9; i < 36; i++) {
            putIntoPlayerIndex(container, i, remaining);

            if (remaining.stackSize <= 0) {
                return;
            }
        }

        /*
         * 最后快捷栏 0-8。
         */
        for (int i = 0; i < 9; i++) {
            putIntoPlayerIndex(container, i, remaining);

            if (remaining.stackSize <= 0) {
                return;
            }
        }
    }

    private static void putIntoPlayerIndex(Container container, int playerIndex, ItemStack remaining) {
        if (SlotLockManager.isLockedPlayerIndex(playerIndex)) {
            return;
        }

        Slot slot = findPlayerSlot(container, playerIndex);

        if (slot == null || slot.getHasStack()) {
            return;
        }

        ItemStack one = remaining.copy();

        int max = Math.min(one.getMaxStackSize(), slot.getSlotStackLimit());
        int move = Math.min(max, remaining.stackSize);

        if (move <= 0) {
            return;
        }

        one.stackSize = move;

        slot.putStack(one);

        /*
         * 精确设置未锁定槽。
         */
        NEIClientUtils.setSlotContents(slot.slotNumber, one, true);

        remaining.stackSize -= move;
    }

    private static Slot findPlayerSlot(Container container, int playerIndex) {
        for (Object object : container.inventorySlots) {
            if (!(object instanceof Slot)) {
                continue;
            }

            Slot slot = (Slot) object;

            if (!SlotLockManager.isPlayerInventorySlot(slot)) {
                continue;
            }

            if (SlotLockManager.getPlayerSlotIndex(slot) == playerIndex) {
                return slot;
            }
        }

        return null;
    }

    private static boolean canMerge(ItemStack source, ItemStack target) {
        if (source == null || target == null) {
            return false;
        }

        if (source.getItem() != target.getItem()) {
            return false;
        }

        if (!source.isStackable()) {
            return false;
        }

        if (target.stackSize >= target.getMaxStackSize()) {
            return false;
        }

        if (source.getHasSubtypes() && source.getItemDamage() != target.getItemDamage()) {
            return false;
        }

        if (!ItemStack.areItemStackTagsEqual(source, target)) {
            return false;
        }

        return true;
    }
}
