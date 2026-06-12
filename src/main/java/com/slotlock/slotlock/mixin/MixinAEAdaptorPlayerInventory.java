package com.slotlock.slotlock.mixin;

import net.minecraft.item.ItemStack;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.slotlock.slotlock.SlotLockManager;

import appeng.util.inv.AdaptorPlayerInventory;

@Mixin(value = AdaptorPlayerInventory.class, remap = false)
public abstract class MixinAEAdaptorPlayerInventory {

    /**
     * AE2 从玩家背包扫描物品时，不让它看到锁定槽里的物品。
     *
     * 这主要用于：
     * - Crafting Terminal 自动取材料
     * - ME Terminal Space + 左键批量送入网络
     */
    @Inject(method = "getStackInSlot", at = @At("HEAD"), cancellable = true, remap = false)
    private void slotlock$hideLockedSlotStack(int slotIndex, CallbackInfoReturnable<ItemStack> cir) {
        if (SlotLockManager.isLockedPlayerIndex(slotIndex)) {
            cir.setReturnValue(null);
        }
    }

    /**
     * AE2 从玩家背包提取物品时，禁止从锁定槽扣除。
     */
    @Inject(method = "decrStackSize", at = @At("HEAD"), cancellable = true, remap = false)
    private void slotlock$preventExtractFromLockedSlot(int slotIndex, int amount,
        CallbackInfoReturnable<ItemStack> cir) {
        if (SlotLockManager.isLockedPlayerIndex(slotIndex)) {
            cir.setReturnValue(null);
        }
    }

    /**
     * AE2 关闭库存时也不要从锁定槽拿东西。
     */
    @Inject(method = "getStackInSlotOnClosing", at = @At("HEAD"), cancellable = true, remap = false)
    private void slotlock$preventClosingExtractFromLockedSlot(int slotIndex, CallbackInfoReturnable<ItemStack> cir) {
        if (SlotLockManager.isLockedPlayerIndex(slotIndex)) {
            cir.setReturnValue(null);
        }
    }

    /**
     * AE2 往玩家背包插入物品时，锁定槽不是合法目标。
     *
     * 这主要用于：
     * - ME 网络物品 shift-click 到玩家背包
     * - ME 网络 Space + 右键批量取出
     * - Crafting Terminal shift-click 合成结果
     */
    @Inject(method = "isItemValidForSlot", at = @At("HEAD"), cancellable = true, remap = false)
    private void slotlock$preventInsertIntoLockedSlot(int slotIndex, ItemStack stack,
        CallbackInfoReturnable<Boolean> cir) {
        if (SlotLockManager.isLockedPlayerIndex(slotIndex)) {
            cir.setReturnValue(Boolean.FALSE);
        }
    }

    /**
     * 兜底保护：
     * 如果 AE2 直接 setInventorySlotContents，也不能写入锁定槽。
     */
    @Inject(method = "setInventorySlotContents", at = @At("HEAD"), cancellable = true, remap = false)
    private void slotlock$preventSetLockedSlotContents(int slotIndex, ItemStack stack, CallbackInfo ci) {
        if (SlotLockManager.isLockedPlayerIndex(slotIndex)) {
            ci.cancel();
        }
    }
}
