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
     * 原版找第一个空格时，会把锁定空格也当成可用。
     * 这里改成：只返回未锁定的空格。
     */
    @Inject(method = "getFirstEmptyStack", at = @At("HEAD"), cancellable = true)
    private void slotlock$getFirstUnlockedEmptyStack(CallbackInfoReturnable<Integer> cir) {
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
     * 原版捡物品时，会先找已有同类物品的格子进行叠加。
     * 比如锁定格里有 10 个石头，地上再捡石头，原版会直接叠进去。
     *
     * 这里改成：寻找可叠加格子时跳过锁定格。
     */
    @Inject(method = "storeItemStack", at = @At("HEAD"), cancellable = true)
    private void slotlock$storeItemStackOnlyUnlocked(ItemStack incoming, CallbackInfoReturnable<Integer> cir) {
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
