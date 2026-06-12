package com.slotlock.slotlock.mixin;

import net.minecraft.client.gui.GuiScreen;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.slotlock.slotlock.SlotLockManager;

import yalter.mousetweaks.handlers.ContainerContext;
import yalter.mousetweaks.handlers.WheelHandler;

@Mixin(value = WheelHandler.class, remap = false)
public abstract class MixinMouseTweaksWheelHandler {

    /**
     * MouseTweaks Wheel Tweak:
     *
     * 滚轮会模拟一串普通左键点击。
     * 如果 selectedSlot 是锁定槽，必须在 MouseTweaks 层直接取消。
     *
     * 不建议在 Container.slotClick 里粗暴拦 clickType == 0，
     * 因为 SlotLockAutoMover 也需要用普通左键 windowClick 来搬走异常进入锁定槽的物品。
     */
    @Inject(method = "handleWheelTweak", at = @At("HEAD"), cancellable = true, remap = false)
    private static void slotlock$cancelWheelTweakOnLockedSlot(GuiScreen currentScreen, Slot selectedSlot,
        ItemStack stackOnMouse, int slotCount, int wheel, ContainerContext context, CallbackInfo ci) {
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
