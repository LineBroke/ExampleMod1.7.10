package com.SlotLock.slotlock.mixin;

import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.item.ItemStack;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.SlotLock.slotlock.SlotLockManager;

@Mixin(InventoryPlayer.class)
public abstract class MixinInventoryPlayer {

    @Shadow
    public ItemStack[] mainInventory;

    @Shadow
    public abstract int getInventoryStackLimit();

    /**
     * 自动拾取物品时，原版会找第一个空槽。
     *
     * 如果没有任何锁定槽，直接走原版逻辑。
     * 如果存在锁定槽，就跳过锁定槽，只返回未锁定的空槽。
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
                cir.setReturnValue(i);
                cir.cancel();
                return;
            }
        }

        cir.setReturnValue(-1);
        cir.cancel();
    }

    /**
     * 自动拾取物品时，原版会先尝试叠加到已有同类物品槽。
     *
     * 如果没有任何锁定槽，直接走原版逻辑。
     * 如果存在锁定槽，就跳过锁定槽，只允许叠加到未锁定槽。
     */
    @Inject(method = "storeItemStack", at = @At("HEAD"), cancellable = true)
    private void slotlock$storeItemStackOnlyUnlocked(ItemStack incoming, CallbackInfoReturnable<Integer> cir) {
        if (!SlotLockManager.hasAnyLock()) {
            return;
        }

        for (int i = 0; i < this.mainInventory.length; i++) {
            if (SlotLockManager.isLockedPlayerIndex(i)) {
                continue;
            }

            ItemStack current = this.mainInventory[i];

            if (canMerge(current, incoming)) {
                cir.setReturnValue(i);
                cir.cancel();
                return;
            }
        }

        cir.setReturnValue(-1);
        cir.cancel();
    }

    private boolean canMerge(ItemStack current, ItemStack incoming) {
        if (current == null || incoming == null) {
            return false;
        }

        if (current.getItem() != incoming.getItem()) {
            return false;
        }

        if (!current.isStackable()) {
            return false;
        }

        if (current.stackSize >= current.getMaxStackSize()) {
            return false;
        }

        if (current.stackSize >= this.getInventoryStackLimit()) {
            return false;
        }

        if (current.getHasSubtypes() && current.getItemDamage() != incoming.getItemDamage()) {
            return false;
        }

        if (!ItemStack.areItemStackTagsEqual(current, incoming)) {
            return false;
        }

        return true;
    }

}
