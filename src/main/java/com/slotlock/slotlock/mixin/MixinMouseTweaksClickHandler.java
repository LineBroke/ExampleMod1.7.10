package com.slotlock.slotlock.mixin;

import net.minecraft.client.gui.GuiScreen;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.slotlock.slotlock.SlotLockManager;

import yalter.mousetweaks.handlers.ClickHandler;
import yalter.mousetweaks.handlers.ContainerContext;

@Mixin(value = ClickHandler.class, remap = false)
public abstract class MixinMouseTweaksClickHandler {

    /**
     * MouseTweaks LMB tweak:
     *
     * 包括：
     * - 左键拖拽拾取同类物品
     * - Shift + 左键拖拽快速移动物品
     *
     * 如果滑过的是 SlotLock 锁定槽，直接取消 MouseTweaks 的模拟点击。
     */
    @Inject(method = "handleLMBTweak", at = @At("HEAD"), cancellable = true, remap = false)
    private void slotlock$cancelLMBTweakOnLockedSlot(GuiScreen currentScreen, Slot selectedSlot, ItemStack stackOnMouse,
        ItemStack targetStack, boolean shiftIsDown, ContainerContext context, CallbackInfo ci) {
        if (!SlotLockManager.hasAnyLock()) {
            return;
        }

        if (selectedSlot == null) {
            return;
        }

        if (SlotLockManager.isLocked(selectedSlot)) {
            ci.cancel();
        }
    }

    /**
     * MouseTweaks RMB tweak:
     *
     * 右键拖拽分配物品时，如果经过锁定槽，
     * 不允许 MouseTweaks 往锁定槽里放东西。
     */
    @Inject(method = "handleRMBTweak", at = @At("HEAD"), cancellable = true, remap = false)
    private void slotlock$cancelRMBTweakOnLockedSlot(GuiScreen currentScreen, Slot selectedSlot, ItemStack stackOnMouse,
        ItemStack targetStack, ContainerContext context, CallbackInfo ci) {
        if (!SlotLockManager.hasAnyLock()) {
            return;
        }

        if (selectedSlot == null) {
            return;
        }

        if (SlotLockManager.isLocked(selectedSlot)) {
            ci.cancel();
        }
    }
}
