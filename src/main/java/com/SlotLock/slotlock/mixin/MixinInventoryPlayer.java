package com.slotlock.slotlock.mixin;

import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.item.ItemStack;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.slotlock.slotlock.SlotLockManager;

@Mixin(InventoryPlayer.class)
public abstract class MixinInventoryPlayer {

    @Shadow
    public ItemStack[] mainInventory;

    /**
     * 原版找第一个空槽时，跳过锁定槽。
     *
     * 这可以影响自动拾取、/give、NEI cheat 等很多“自动进入背包”的逻辑。
     */
    @Inject(method = "getFirstEmptyStack", at = @At("HEAD"), cancellable = true)
    private void slotlock$getFirstUnlockedEmptyStack(CallbackInfoReturnable<Integer> cir) {
        if (!SlotLockManager.hasAnyLock()) {
            return;
        }

        for (int i = 0; i < this.mainInventory.length; i++) {
            if (SlotLockManager.isLockedPlayerIndex(i)) {
                continue;
            }

            if (this.mainInventory[i] == null) {
                cir.setReturnValue(Integer.valueOf(i));
                return;
            }
        }

        cir.setReturnValue(Integer.valueOf(-1));
    }

    /**
     * 原版找可叠加槽时，跳过锁定槽。
     */
    @Inject(method = "storeItemStack", at = @At("HEAD"), cancellable = true)
    private void slotlock$storeItemStackUnlockedOnly(ItemStack stack, CallbackInfoReturnable<Integer> cir) {
        if (!SlotLockManager.hasAnyLock()) {
            return;
        }

        for (int i = 0; i < this.mainInventory.length; i++) {
            if (SlotLockManager.isLockedPlayerIndex(i)) {
                continue;
            }

            ItemStack target = this.mainInventory[i];

            if (canMerge(stack, target)) {
                cir.setReturnValue(Integer.valueOf(i));
                return;
            }
        }

        cir.setReturnValue(Integer.valueOf(-1));
    }

    /**
     * 更强的保护：
     * 如果有锁定槽，就接管 addItemStackToInventory，
     * 手动只往未锁定槽里合并/放入。
     *
     * 这个主要用来处理 NEI cheat、/give、自动拾取这类绕过 GUI 点击的入口。
     */
    @Inject(method = "addItemStackToInventory", at = @At("HEAD"), cancellable = true)
    private void slotlock$addItemStackUnlockedOnly(ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        if (!SlotLockManager.hasAnyLock()) {
            return;
        }

        if (stack == null) {
            cir.setReturnValue(Boolean.FALSE);
            return;
        }

        int originalSize = stack.stackSize;

        if (originalSize <= 0) {
            cir.setReturnValue(Boolean.FALSE);
            return;
        }

        /*
         * 不可堆叠 / 损坏物品：
         * 只允许放进未锁定空槽。
         */
        if (stack.isItemDamaged()) {
            int empty = findUnlockedEmptySlot();

            if (empty < 0) {
                cir.setReturnValue(Boolean.FALSE);
                return;
            }

            this.mainInventory[empty] = ItemStack.copyItemStack(stack);
            this.mainInventory[empty].animationsToGo = 5;
            stack.stackSize = 0;

            cir.setReturnValue(Boolean.TRUE);
            return;
        }

        /*
         * 可堆叠物品：
         * 先合并到未锁定的同类非满槽。
         */
        while (stack.stackSize > 0) {
            int before = stack.stackSize;

            mergeIntoUnlockedStacks(stack);

            if (stack.stackSize <= 0) {
                break;
            }

            putIntoUnlockedEmptySlot(stack);

            if (stack.stackSize == before) {
                break;
            }
        }

        cir.setReturnValue(Boolean.valueOf(stack.stackSize < originalSize));
    }

    private void mergeIntoUnlockedStacks(ItemStack stack) {
        for (int i = 0; i < this.mainInventory.length; i++) {
            if (SlotLockManager.isLockedPlayerIndex(i)) {
                continue;
            }

            ItemStack target = this.mainInventory[i];

            if (!canMerge(stack, target)) {
                continue;
            }

            int max = Math.min(target.getMaxStackSize(), 64);
            int space = max - target.stackSize;

            if (space <= 0) {
                continue;
            }

            int move = Math.min(space, stack.stackSize);

            target.stackSize += move;
            target.animationsToGo = 5;
            stack.stackSize -= move;

            if (stack.stackSize <= 0) {
                return;
            }
        }
    }

    private void putIntoUnlockedEmptySlot(ItemStack stack) {
        int empty = findUnlockedEmptySlot();

        if (empty < 0) {
            return;
        }

        int move = Math.min(stack.stackSize, Math.min(stack.getMaxStackSize(), 64));

        ItemStack copy = stack.copy();
        copy.stackSize = move;
        copy.animationsToGo = 5;

        this.mainInventory[empty] = copy;
        stack.stackSize -= move;
    }

    private int findUnlockedEmptySlot() {
        /*
         * 优先主背包 9-35。
         * 最后才放快捷栏 0-8。
         */
        for (int i = 9; i < 36 && i < this.mainInventory.length; i++) {
            if (SlotLockManager.isLockedPlayerIndex(i)) {
                continue;
            }

            if (this.mainInventory[i] == null) {
                return i;
            }
        }

        for (int i = 0; i < 9 && i < this.mainInventory.length; i++) {
            if (SlotLockManager.isLockedPlayerIndex(i)) {
                continue;
            }

            if (this.mainInventory[i] == null) {
                return i;
            }
        }

        return -1;
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
